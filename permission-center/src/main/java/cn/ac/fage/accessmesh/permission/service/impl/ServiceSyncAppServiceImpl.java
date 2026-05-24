package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.permission.service.ServiceSyncAppService;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncModeStrategy;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncModeStrategyFactory;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncResult;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 服务同步应用服务实现类
 * <p>
 * 提供服务接口与资源API映射的同步功能。
 * 使用策略模式处理不同的同步模式。
 * </p>
 */
@Service
public class ServiceSyncAppServiceImpl implements ServiceSyncAppService {

    private final ResourceSyncHandler resourceSyncHandler;
    private final MappingSyncHandler mappingSyncHandler;
    private final ServiceConfigMapper serviceConfigMapper;
    private final TypeResolutionService typeResolutionService;
    private final SyncModeStrategyFactory strategyFactory;
    private final PermQueryEngine engine;

    public ServiceSyncAppServiceImpl(
            ResourceSyncHandler resourceSyncHandler,
            MappingSyncHandler mappingSyncHandler,
            ServiceConfigMapper serviceConfigMapper,
            TypeResolutionService typeResolutionService,
            SyncModeStrategyFactory strategyFactory,
            PermQueryEngine engine) {
        this.resourceSyncHandler = resourceSyncHandler;
        this.mappingSyncHandler = mappingSyncHandler;
        this.serviceConfigMapper = serviceConfigMapper;
        this.typeResolutionService = typeResolutionService;
        this.strategyFactory = strategyFactory;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "service-interface-sync", targetType = "service_config", targetId = "#req.serviceCode()", summary = "'sync result: createdResources=' + #result.createdResources() + ', createdMappings=' + #result.createdMappings() + ', updatedMappings=' + #result.updatedMappings() + ', deletedResources=' + #result.deletedResources() + ', deletedMappings=' + #result.deletedMappings()")
    public ServiceConfigSyncResp syncInterfaces(Long tenantId, ServiceConfigSyncReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        validatePermission(tenantId, operatorId, req);

        ServiceConfig config = prepareServiceConfig(tenantId, req, operatorId);

        String basePath = normalizeBasePath(
            req.basePath() != null && !req.basePath().isBlank() ? req.basePath() : config.getBasePath()
        );

        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", ResourceTypeCode.API);
        if (apiType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "resource_type API not found");
        }

        SyncContext context = SyncContext.of(tenantId, config, req, operatorId, basePath, apiType);

        SyncModeStrategy strategy = strategyFactory.getStrategy(req.syncMode());
        SyncResult result = strategy.execute(context, resourceSyncHandler, mappingSyncHandler);

        return result.toResponse();
    }

    private void validatePermission(Long tenantId, Long operatorId, ServiceConfigSyncReq req) {
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationCodeConstants.SYNC_INTERFACE)) {
            throw new SecurityException("Permission denied: SYNC_INTERFACE on SERVICE:" + req.serviceCode());
        }
    }

    private ServiceConfig prepareServiceConfig(Long tenantId, ServiceConfigSyncReq req, Long operatorId) {
        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, req.serviceCode());

        if (config == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "ServiceConfig not found: " + req.serviceCode());
        }

        if (req.basePath() != null && !req.basePath().isBlank()) {
            config.setBasePath(req.basePath());
            config.setUpdatedAt(LocalDateTime.now());
            serviceConfigMapper.update(config);
        }

        return config;
    }

    private String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return "";
        }
        String path = basePath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
