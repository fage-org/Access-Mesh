package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SystemConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.SystemConfigAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.JsonValidationUtils;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
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
    @OperationLog(module = "perm", action = "system-config-upsert", targetType = "system_config", targetId = "#req.configKey()", summary = "'upsert system config ' + #req.configKey()")
    public SystemConfigResp upsertSystemConfig(Long tenantId, SystemConfigReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to manage system config");
        }

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
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        SystemConfig config = systemConfigMapper.selectByConfigKey(tenantId, configKey);
        return config != null ? toSystemConfigResp(config) : null;
    }

    /**
     * 查询系统配置列表
     * <p>
     * 查询租户下所有系统配置。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 配置响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResp> listSystemConfigs(Long tenantId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return systemConfigMapper.selectByTenantId(tenantId).stream().map(this::toSystemConfigResp).collect(Collectors.toList());
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
