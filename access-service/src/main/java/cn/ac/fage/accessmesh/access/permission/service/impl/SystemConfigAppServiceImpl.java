package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SystemConfigResp;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.SystemConfigAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.JsonValidationUtils;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置应用服务实现类
 * <p>
 * 提供系统配置的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class SystemConfigAppServiceImpl implements SystemConfigAppService {

    /**
     * 配置键合法命名空间前缀（access-service-architecture §5.2）。
     * 存量种子键已迁移至 admin.*；新增键必须携带三前缀之一，防止无命名空间键扩散。
     */
    private static final String[] ALLOWED_CONFIG_KEY_PREFIXES = {"admin.", "permission.", "access."};

    private final SystemConfigMapper systemConfigMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param systemConfigMapper 系统配置数据访问层
     * @param engine             权限查询引擎
     */
    public SystemConfigAppServiceImpl(SystemConfigMapper systemConfigMapper,
                                       PermQueryEngine engine) {
        this.systemConfigMapper = systemConfigMapper;
        this.engine = engine;
    }

    /**
     * 创建或更新系统配置
     * <p>
     * 根据配置键创建新配置或更新已有配置。
     * 系统配置存储全局系统设置，以JSON格式保存配置值。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      配置请求，包含配置键、配置值（JSON）、描述
     * @return 配置响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "SYSTEM_CONFIG_UPSERT", targetType = "system_config", targetId = "#req.configKey()", summary = "'upsert system config ' + #req.configKey()")
    public SystemConfigResp upsertSystemConfig(Long tenantId, SystemConfigReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to manage system config");
        }

        validateConfigKeyNamespace(req.configKey());
        JsonValidationUtils.validateJson(req.configValue());

        SystemConfig existing = systemConfigMapper.selectByConfigKey(tenantId, req.configKey());

        if (existing != null) {
            existing.setConfigValue(req.configValue());
            existing.setDescription(req.description());
            existing.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.update(existing);
            return toSystemConfigResp(existing);
        } else {
            SystemConfig config = new SystemConfig();
            config.setTenantId(tenantId);
            config.setConfigKey(req.configKey());
            config.setConfigValue(req.configValue());
            config.setDescription(req.description());
            // is_system NOT NULL：API 创建固定租户自定义 false（系统内置仅走种子；
            // 修复归并遗留缺陷——原实现未设置导致 insert 违反非空约束，T-PERM-024 PgIT 发现）
            config.setIsSystem(false);
            LocalDateTime now = LocalDateTime.now();
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            config.setDeleteFlag(0L);
            systemConfigMapper.insert(config);
            return toSystemConfigResp(config);
        }
    }

    /**
     * 获取系统配置详情
     * <p>
     * 根据配置键查询系统配置的完整信息。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId  租户ID
     * @param configKey 配置键
     * @return 配置响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public SystemConfigResp getSystemConfig(Long tenantId, String configKey) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        SystemConfig config = systemConfigMapper.selectByConfigKey(tenantId, configKey);
        return config != null ? toSystemConfigResp(config) : null;
    }

    /**
     * 按条件统计有效系统配置数量
     * <p>
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（configKey/description LIKE，大小写敏感）
     * @return 有效行数
     */
    @Override
    @Transactional(readOnly = true)
    public long countSystemConfigs(Long tenantId, String keyword) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }
        return systemConfigMapper.countByCondition(tenantId, normalize(keyword));
    }

    /**
     * 按条件分页查询系统配置
     * <p>
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（configKey/description LIKE，大小写敏感）
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 配置响应列表（ORDER BY config_key, id）
     */
    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResp> listSystemConfigs(Long tenantId, String keyword, int offset, int limit) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return systemConfigMapper.selectPageByCondition(tenantId, normalize(keyword), limit, offset)
            .stream().map(this::toSystemConfigResp).collect(Collectors.toList());
    }

    /**
     * 过滤参数规整：空白串归一为 null（与 SQL <if> 判空语义一致）
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 校验配置键命名空间前缀合法（T-ACCESS-007 §5.2）。
     * <p>
     * 配置键必须携带 admin./permission./access. 三前缀之一；存量种子键已迁移至 admin.*，
     * 此校验约束新增键与显式创建路径，防止无命名空间键在 system_config 中扩散。
     * </p>
     *
     * @param configKey 配置键
     * @throws BizException 键为 null/空或不以合法前缀开头时抛出
     */
    private void validateConfigKeyNamespace(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new BizException(PermissionErrorCode.CONFIG_KEY_NAMESPACE_INVALID.getCode(),
                PermissionErrorCode.CONFIG_KEY_NAMESPACE_INVALID.getMessage());
        }
        for (String prefix : ALLOWED_CONFIG_KEY_PREFIXES) {
            if (configKey.startsWith(prefix)) {
                return;
            }
        }
        throw new BizException(PermissionErrorCode.CONFIG_KEY_NAMESPACE_INVALID.getCode(),
            PermissionErrorCode.CONFIG_KEY_NAMESPACE_INVALID.getMessage());
    }

    /**
     * 将SystemConfig实体转换为响应对象
     *
     * @param c 系统配置实体
     * @return 系统配置响应对象
     */
    private SystemConfigResp toSystemConfigResp(SystemConfig c) {
        return new SystemConfigResp(
            c.getId(), c.getTenantId(), c.getConfigKey(),
            c.getConfigValue(), c.getDescription(), c.getUpdatedAt()
        );
    }
}
