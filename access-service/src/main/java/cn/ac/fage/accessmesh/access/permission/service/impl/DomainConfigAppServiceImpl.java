package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.DomainConfigAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.JsonValidationUtils;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 域配置应用服务实现类
 * <p>
 * 提供域配置的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class DomainConfigAppServiceImpl implements DomainConfigAppService {

    private final DomainConfigMapper domainConfigMapper;
    private final BizDomainMapper bizDomainMapper;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param domainConfigMapper      域配置数据访问层
     * @param bizDomainMapper         业务域数据访问层（save 域解析 FOR UPDATE 行锁，T-PERM-046）
     * @param typeResolutionService   类型解析服务
     * @param engine                  权限查询引擎
     */
    public DomainConfigAppServiceImpl(DomainConfigMapper domainConfigMapper,
                                       BizDomainMapper bizDomainMapper,
                                       TypeResolutionService typeResolutionService,
                                       PermQueryEngine engine) {
        this.domainConfigMapper = domainConfigMapper;
        this.bizDomainMapper = bizDomainMapper;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
    }

    /**
     * 创建或更新域配置
     * <p>
     * 根据域编码和配置类型，创建新配置或更新已有配置。
        * 当前支持的配置类型包括CLASSIFY（资源类型分类）和SUB_PERM（子权限配置）。
     * extra 为 JSON 字符串，写入前经 {@link JsonValidationUtils} 语法校验
     * （非法 JSON 拒绝，system-config configValue 同范式，T-PERM-026 补齐）。
     * T-PERM-046：①域解析走 FOR UPDATE 域行锁（selectByCodeForUpdate），与 biz-domain
     * remove 的删除保护校验互斥——并发 remove 提交后此处解析不到软删域（20017），
     * 反向持锁插入的配置会被 remove 的引用检查看到（20051 拒删），孤儿配置窗口闭合；
     * ②insert 并发双插窗口由 uk_domain_config 唯一索引兜底（DIVE 映射 20058 提示重试）。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      配置请求，包含域编码、配置类型、扩展JSON
     * @return 配置响应
     * @throws SecurityException       无权限时抛出
     * @throws BizException            域不存在（20017）/ 并发保存冲突（20058）时抛出
     * @throws IllegalArgumentException extra 非法 JSON 时抛出（统一异常处理映射 code=400 参数错误）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "DOMAIN_CONFIG_UPSERT", targetType = "domain_config", targetId = "#result.id()", summary = "'upsert domain config ' + #req.domainCode() + ':' + #req.configType()")
    public DomainConfigResp upsertDomainConfig(Long tenantId, DomainConfigReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to manage domain config");
        }

        JsonValidationUtils.validateJson(req.extra());

        // T-PERM-046：域行锁解析（写事务内）——FOR UPDATE 锁住目标域行直到本事务结束，
        // 与 biz-domain remove 的 selectValidByIdsForUpdate 互斥（锁内读写串行，
        // 后进锁者可见先进锁者已提交的软删/配置）
        BizDomain domain = (req.domainCode() == null || req.domainCode().isBlank())
            ? null
            : bizDomainMapper.selectByCodeForUpdate(tenantId, req.domainCode());
        if (domain == null) {
            throw new BizException(PermissionErrorCode.DOMAIN_NOT_FOUND.getCode(), "Unknown domainCode: " + req.domainCode());
        }
        Long bizDomainId = domain.getId();
        DomainConfig existing = domainConfigMapper.selectValidByTypeString(tenantId, bizDomainId, req.configType());

        if (existing != null) {
            existing.setExtra(req.extra());
            existing.setUpdatedAt(LocalDateTime.now());
            domainConfigMapper.update(existing);
            return toDomainConfigResp(existing);
        } else {
            DomainConfig config = new DomainConfig();
            config.setTenantId(tenantId);
            config.setBizDomainId(bizDomainId);
            config.setConfigType(req.configType());
            config.setExtra(req.extra());
            LocalDateTime now = LocalDateTime.now();
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            config.setDeleteFlag(0L);
            try {
                domainConfigMapper.insert(config);
            } catch (DataIntegrityViolationException e) {
                // T-PERM-046：uk_domain_config 并发兜底——check-then-insert 窗口内并发
                // 同键保存（domainCode+configType）后落库者命中唯一索引，转 20058 提示重试
                // （重试时另一事务已提交，selectValidByTypeString 命中转 update 分支）
                if (isUniqueViolationOn(e, "\"uk_domain_config\"")) {
                    throw new BizException(PermissionErrorCode.DOMAIN_CONFIG_CONCURRENT_CONFLICT.getCode(),
                        "Concurrent save on same domainCode+configType, please retry: "
                            + req.domainCode() + ":" + req.configType());
                }
                throw e;
            }
            return toDomainConfigResp(config);
        }
    }

    /**
     * 获取域配置详情
     * <p>
     * 根据域编码和配置类型查询域配置的完整信息。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 域编码
     * @param configType 配置类型
     * @return 配置响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public DomainConfigResp getDomainConfig(Long tenantId, String domainCode, String configType) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
        if (bizDomainId == null) {
            return null;
        }
        DomainConfig config = domainConfigMapper.selectValidByTypeString(tenantId, bizDomainId, configType);
        return config != null ? toDomainConfigResp(config) : null;
    }

    /**
     * 查询域配置列表
     * <p>
     * 查询租户下所有域配置，或按域编码过滤。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 域编码，可选过滤条件
     * @return 配置响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public List<DomainConfigResp> listDomainConfigs(Long tenantId, String domainCode) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        if (domainCode != null && !domainCode.isBlank()) {
            Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (bizDomainId == null) {
                return List.of();
            }
            return domainConfigMapper.selectByTenantAndDomainId(tenantId, bizDomainId)
                .stream().map(this::toDomainConfigResp).collect(Collectors.toList());
        }
        return domainConfigMapper.selectByTenantId(tenantId)
            .stream().map(this::toDomainConfigResp).collect(Collectors.toList());
    }

    /**
     * 批量删除域配置
     * <p>
     * 批量软删除域配置。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        配置ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "DOMAIN_CONFIG_REMOVE", targetType = "domain_config", targetId = "", summary = "'batch remove domain configs'")
    public void deleteDomainConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete domain configs");
        }

        if (ids == null || ids.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<DomainConfig> entities = domainConfigMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream()
            .map(DomainConfig::getId)
            .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        domainConfigMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " domain_config row(s)");
    }

    /**
     * 将DomainConfig实体转换为响应对象
     *
     * @param c 域配置实体
     * @return 域配置响应对象
     */
    private DomainConfigResp toDomainConfigResp(DomainConfig c) {
        return new DomainConfigResp(
            c.getId(), c.getTenantId(), c.getBizDomainId(),
            c.getConfigType(), c.getExtra(), c.getUpdatedAt()
        );
    }

    /**
     * PG 唯一约束违反消息含约束名，沿 cause 链匹配（BizDomainAppService 同模式）
     */
    private boolean isUniqueViolationOn(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains(constraintName)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
