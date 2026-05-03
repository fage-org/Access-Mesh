package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.ServiceInterfaceSyncService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncModeStrategy;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncModeStrategyFactory;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncResult;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.ac.fage.accessmesh.permission.entity.table.ServiceConfigTableDef.SERVICE_CONFIG;

/**
 * Implementation of ServiceInterfaceSyncService.
 * Uses strategy pattern to handle different sync modes.
 */
@Service
public class ServiceInterfaceSyncServiceImpl implements ServiceInterfaceSyncService {

    private final ResourceSyncHandler resourceSyncHandler;
    private final MappingSyncHandler mappingSyncHandler;
    private final ServiceConfigMapper serviceConfigMapper;
    private final AuthorizationService authorizationService;
    private final OperationLogDomainService operationLogDomainService;
    private final TypeResolutionService typeResolutionService;
    private final SyncModeStrategyFactory strategyFactory;
    private final ResourcePermissionValidator permissionValidator;

    public ServiceInterfaceSyncServiceImpl(
            ResourceSyncHandler resourceSyncHandler,
            MappingSyncHandler mappingSyncHandler,
            ServiceConfigMapper serviceConfigMapper,
            AuthorizationService authorizationService,
            OperationLogDomainService operationLogDomainService,
            TypeResolutionService typeResolutionService,
            SyncModeStrategyFactory strategyFactory,
            ResourcePermissionValidator permissionValidator) {
        this.resourceSyncHandler = resourceSyncHandler;
        this.mappingSyncHandler = mappingSyncHandler;
        this.serviceConfigMapper = serviceConfigMapper;
        this.authorizationService = authorizationService;
        this.operationLogDomainService = operationLogDomainService;
        this.typeResolutionService = typeResolutionService;
        this.strategyFactory = strategyFactory;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigSyncResp syncInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // Permission validation
        validatePermission(tenantId, operatorId);

        // Get and prepare service config
        ServiceConfig config = prepareServiceConfig(tenantId, req, operatorId);

        // Get base path
        String basePath = normalizeBasePath(
            req.basePath() != null && !req.basePath().isBlank() ? req.basePath() : config.getBasePath()
        );

        // Resolve API type
        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "API");
        if (apiType == null) {
            throw new IllegalArgumentException("resource_type API not found");
        }

        // Create sync context
        SyncContext context = SyncContext.of(tenantId, config, req, operatorId, basePath, apiType);

        // Get strategy and execute
        SyncModeStrategy strategy = strategyFactory.getStrategy(req.syncMode());
        SyncResult result = strategy.execute(context, resourceSyncHandler, mappingSyncHandler);

        // Log the result
        logSyncResult(tenantId, req.serviceCode(), result, operatorId);

        return result.toResponse();
    }

    /**
     * Validate permission for sync operation.
     */
    private void validatePermission(Long tenantId, Long operatorId) {
        if (!permissionValidator.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", null, OperationType.MANAGE)) {
            throw new SecurityException("No permission to sync service interfaces");
        }
    }

    /**
     * Prepare service config - get existing or validate existence.
     */
    private ServiceConfig prepareServiceConfig(Long tenantId, ServiceConfigSyncReq req, Long operatorId) {
        ServiceConfig config = serviceConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SERVICE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SERVICE_CONFIG.SERVICE_CODE.eq(req.serviceCode()))
                .and(SERVICE_CONFIG.DELETE_FLAG.eq(0))
        );

        if (config == null) {
            throw new IllegalArgumentException("ServiceConfig not found: " + req.serviceCode());
        }

        // Update base path if provided
        if (req.basePath() != null && !req.basePath().isBlank()) {
            config.setBasePath(req.basePath());
            config.setUpdatedAt(LocalDateTime.now());
            serviceConfigMapper.update(config);
        }

        return config;
    }

    /**
     * Log sync result.
     */
    private void logSyncResult(Long tenantId, String serviceCode, SyncResult result, Long operatorId) {
        operationLogDomainService.asyncRecord(
            "perm",
            "service-interface-sync",
            serviceCode,
            tenantId,
            String.format("sync result: createdResources=%d, createdMappings=%d, updatedMappings=%d, " +
                          "deletedResources=%d, deletedMappings=%d",
                result.getCreatedResources(),
                result.getCreatedMappings(),
                result.getUpdatedMappings(),
                result.getDeletedResources(),
                result.getDeletedMappings()),
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * Normalize base path.
     */
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