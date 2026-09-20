package cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain;

import cn.ac.fage.accessmesh.access.infrastructure.ServicePrincipal;
import cn.ac.fage.accessmesh.access.infrastructure.credential.entity.ServiceCredential;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务凭证领域服务（T-PERM-070，service-authentication.md §3.2/§3.4）。
 * <p>
 * 两条消费链：①认证仲裁器（{@code ServiceAuthArbiter}）verify——凭证头验证唯一入口；
 * ②管理面 CRUD（签发/改期/停用/删除）。服务注册状态校验经 resource 包
 * {@code ServiceConfigDomainService}（同层横向调用，对齐 ResourceTypeOwnershipGuard 先例）。
 * </p>
 */
public interface ServiceCredentialDomainService {

    /**
     * 签发凭证：服务端生成 credential_id（sc- + 22 字符 base64url）与 secret
     * （sk- + 43 字符 base64url），库内只存 BCrypt 哈希；明文 secret 仅随本方法返回一次。
     * 同服务多凭证并存（轮换窗口），不设数量上限（2026-09-20 拍板）。
     *
     * @param tenantId    租户
     * @param serviceCode 绑定服务（调用方已完成注册+启用校验）
     * @param expiresAt   过期时间（null=永不过期）
     * @param operatorId  操作者（审计）
     * @return 落库实体 + 明文 secret（仅此一次回显）
     */
    IssuedCredential issue(Long tenantId, String serviceCode, LocalDateTime expiresAt, Long operatorId);

    /**
     * 凭证验证（仲裁器唯一入口）。验证顺序（泄露面最小化——三态细分仅对持有正确
     * secret 的请求者暴露）：行定位（不存在→{@code SERVICE_CREDENTIAL_INVALID}）→
     * BCrypt 常量比对（失败→{@code SERVICE_CREDENTIAL_INVALID}）→ 状态/过期细分
     * （{@code SERVICE_CREDENTIAL_DISABLED}/{@code SERVICE_CREDENTIAL_EXPIRED}）→
     * 服务注册+启用（{@code SERVICE_CREDENTIAL_SERVICE_INACTIVE}）。
     *
     * @param credentialId 线上凭证标识（全局唯一定位）
     * @param secret       明文 secret
     * @return 成功携带 principal（tenantId/serviceCode 由凭证行派生）；失败携带错误码
     */
    VerifyResult verify(String credentialId, String secret);

    /** 管理面按主键定位（租户内；不存在/已删返回 null）。 */
    ServiceCredential findById(Long tenantId, Long id);

    /** 管理面列表：serviceCode 非空按服务过滤，null 全租户（均含停用/过期行）。 */
    List<ServiceCredential> list(Long tenantId, String serviceCode);

    /**
     * 启停凭证（轮换收尾=停旧）。停用时记录 rotated_at=当前时刻（停用时间戳，轮换/吊销
     * 同记——凭证系统不区分两种停用动机）。
     *
     * @return 实际更新行数（0=行不存在/已删，调用方按 20065 处理）
     */
    int changeStatus(Long tenantId, Long id, int targetStatus, Long operatorId);

    /**
     * 改期（2026-09-20 拍板：update 支持 status + expiresAt）。expiresAt 传 null=不改
     * （「清除过期时间」不提供——需要永不过期语义请签发新凭证时不设过期）。
     *
     * @return 实际更新行数（0=行不存在/已删）
     */
    int changeExpiresAt(Long tenantId, Long id, LocalDateTime expiresAt, Long operatorId);

    /** 软删除凭证（delete_flag=id）。 */
    int remove(Long tenantId, Long id, Long operatorId);

    /** 签发结果：落库实体 + 明文 secret（仅签发时可见，无任何回查通道）。 */
    record IssuedCredential(ServiceCredential entity, String plainSecret) {}

    /** 验证结果：成功 principal 非空；失败 failure 为细分错误码（20065~20068）。 */
    record VerifyResult(ServicePrincipal principal, AccessErrorCode failure) {

        public static VerifyResult success(ServicePrincipal principal) {
            return new VerifyResult(principal, null);
        }

        public static VerifyResult failure(AccessErrorCode code) {
            return new VerifyResult(null, code);
        }

        public boolean success() {
            return principal != null;
        }
    }
}
