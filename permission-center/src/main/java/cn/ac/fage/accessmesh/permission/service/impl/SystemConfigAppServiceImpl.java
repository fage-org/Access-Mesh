package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.resp.SystemConfigResp;
import cn.ac.fage.accessmesh.permission.entity.SystemConfig;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.permission.service.SystemConfigAppService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.JsonValidationUtils;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
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

    public SystemConfigAppServiceImpl(SystemConfigMapper systemConfigMapper,
                                       PermQueryEngine engine) {
        this.systemConfigMapper = systemConfigMapper;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResp> listSystemConfigs(Long tenantId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return systemConfigMapper.selectByTenantId(tenantId).stream().map(this::toSystemConfigResp).collect(Collectors.toList());
    }

    private SystemConfigResp toSystemConfigResp(SystemConfig c) {
        return new SystemConfigResp(
            c.getId(), c.getTenantId(), c.getConfigKey(),
            c.getConfigValue(), c.getDescription(), c.getUpdatedAt()
        );
    }
}
