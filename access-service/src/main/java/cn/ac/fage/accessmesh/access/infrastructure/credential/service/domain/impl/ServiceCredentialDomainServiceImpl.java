package cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.ServicePrincipal;
import cn.ac.fage.accessmesh.access.infrastructure.credential.entity.ServiceCredential;
import cn.ac.fage.accessmesh.access.infrastructure.credential.mapper.ServiceCredentialMapper;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import cn.dev33.satoken.secure.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 服务凭证领域服务实现（T-PERM-070）。
 * <p>
 * secret 生成与哈希：32 字节 SecureRandom → base64url（43 字符，URL-safe 无 +/），
 * 库内只存 BCrypt 哈希（sa-token {@link BCrypt}，同 sys_oauth2_client 先例）；
 * credentialId 16 字节熵（22 字符）——高熵不可枚举是三码细分安全性的前提。
 * </p>
 */
@Service
public class ServiceCredentialDomainServiceImpl implements ServiceCredentialDomainService {

    /** 凭证标识前缀（日志/配置中可识别类型）。 */
    static final String CREDENTIAL_ID_PREFIX = "sc-";
    /** 明文 secret 前缀（日志/配置中可识别类型；日志脱敏口径=见头值截断前缀）。 */
    static final String SECRET_PREFIX = "sk-";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ServiceCredentialMapper serviceCredentialMapper;
    private final ServiceConfigDomainService serviceConfigDomainService;

    public ServiceCredentialDomainServiceImpl(ServiceCredentialMapper serviceCredentialMapper,
                                              ServiceConfigDomainService serviceConfigDomainService) {
        this.serviceCredentialMapper = serviceCredentialMapper;
        this.serviceConfigDomainService = serviceConfigDomainService;
    }

    @Override
    public IssuedCredential issue(Long tenantId, String serviceCode, LocalDateTime expiresAt, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        String credentialId = CREDENTIAL_ID_PREFIX + URL_ENCODER.encodeToString(randomBytes(16));
        String plainSecret = SECRET_PREFIX + URL_ENCODER.encodeToString(randomBytes(32));

        ServiceCredential entity = new ServiceCredential();
        entity.setTenantId(tenantId);
        entity.setServiceCode(serviceCode);
        entity.setCredentialId(credentialId);
        entity.setSecretHash(BCrypt.hashpw(plainSecret));
        entity.setStatus(ServiceCredential.STATUS_ENABLED);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleteFlag(0L);
        try {
            serviceCredentialMapper.insert(entity);
            return new IssuedCredential(entity, plainSecret);
        } catch (DuplicateKeyException e) {
            // credential_id 全局唯一索引兜底（设计稿 §3.1「签发生成器保证 + 全局冲突检查」）：
            // 16 字节熵下碰撞概率 2^-128 不可达；且本方法运行于调用方事务内，PG 下事务已
            // abort、同事务重试不可能成功——命中即 fail-fast 上抛（理论不可达分支）
            throw new IllegalStateException(
                "credential_id 命中全局唯一索引（概率不可达，疑索引/数据异常）", e);
        }
    }

    @Override
    public VerifyResult verify(String credentialId, String secret) {
        if (credentialId == null || credentialId.isBlank() || secret == null || secret.isBlank()) {
            return VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        }
        ServiceCredential credential = serviceCredentialMapper.selectByCredentialId(credentialId.trim());
        if (credential == null) {
            return VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        }
        // 先比对 secret 再细分状态：三态细分（过期/停用）仅对持有正确 secret 的请求者
        // 暴露——半头/错误 secret 一律 INVALID，无凭证状态探测面
        //（损坏的 secret_hash〔非法 BCrypt 串〕checkpw 抛 IllegalArgumentException——DB
        // 数据异常场景，按凭证无效归一，不产生 500）
        try {
            if (!BCrypt.checkpw(secret, credential.getSecretHash())) {
                return VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
            }
        } catch (IllegalArgumentException e) {
            return VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        }
        if (credential.getStatus() != null && credential.getStatus() != ServiceCredential.STATUS_ENABLED) {
            return VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_DISABLED);
        }
        if (credential.getExpiresAt() != null && LocalDateTime.now().isAfter(credential.getExpiresAt())) {
            return VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_EXPIRED);
        }
        // 前置校验：凭证绑定的服务在 service_config 注册且启用（对齐 20055 门禁的服务注册段；
        // 服务停用/注销即同步通道一起断，同 sync 通道白名单语义）
        if (!ServiceConfigDomainService.isRegisteredAndEnabled(serviceConfigDomainService.selectByTenantAndServiceCode(
                credential.getTenantId(), credential.getServiceCode()))) {
            return VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_SERVICE_INACTIVE);
        }
        return VerifyResult.success(
            new ServicePrincipal(credential.getTenantId(), credential.getServiceCode(), credential.getCredentialId()));
    }

    @Override
    public ServiceCredential findById(Long tenantId, Long id) {
        return serviceCredentialMapper.selectByIdAndTenant(tenantId, id);
    }

    @Override
    public List<ServiceCredential> list(Long tenantId, String serviceCode) {
        if (serviceCode != null && !serviceCode.isBlank()) {
            return serviceCredentialMapper.selectByTenantAndServiceCode(tenantId, serviceCode.trim());
        }
        return serviceCredentialMapper.selectByTenantId(tenantId);
    }

    @Override
    public int changeStatus(Long tenantId, Long id, int targetStatus, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        // 停用时记 rotated_at=停用时刻（轮换停旧与管理员吊销同记，不区分动机）
        return serviceCredentialMapper.updateStatus(tenantId, id, targetStatus,
            targetStatus == ServiceCredential.STATUS_DISABLED ? now : null, operatorId, now);
    }

    @Override
    public int changeExpiresAt(Long tenantId, Long id, LocalDateTime expiresAt, Long operatorId) {
        if (expiresAt == null) {
            // null=不改；「清除过期时间」不提供（永不过期语义=签发新凭证时不设过期）
            return 0;
        }
        return serviceCredentialMapper.updateExpiresAt(tenantId, id, expiresAt, operatorId, LocalDateTime.now());
    }

    @Override
    public int remove(Long tenantId, Long id, Long operatorId) {
        return serviceCredentialMapper.softDelete(tenantId, id, operatorId, LocalDateTime.now());
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
