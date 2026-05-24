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

    /**
     * 构造函数注入依赖
     *
     * @param resourceSyncHandler    资源同步处理器
     * @param mappingSyncHandler     API映射同步处理器
     * @param serviceConfigMapper    服务配置数据访问层
     * @param typeResolutionService  类型解析服务
     * @param strategyFactory        同步策略工厂
     * @param engine                 权限查询引擎
     */
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

    /**
     * 同步服务接口与资源API映射
     * <p>
     * 根据同步模式（FULL/INCREMENTAL）同步服务接口定义与资源API映射关系。
     * 使用策略模式处理不同同步模式的具体逻辑。
     * 需要SERVICE_SYNC_INTERFACE权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      同步请求，包含服务编码、同步模式、接口列表等
     * @return 同步结果响应
     * @throws SecurityException 无权限时抛出
     */
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

    /**
     * 验证同步权限
     *
     * @param tenantId   租户ID
     * @param operatorId 操作者ID
     * @param req        同步请求
     * @throws SecurityException 无权限时抛出
     */
    private void validatePermission(Long tenantId, Long operatorId, ServiceConfigSyncReq req) {
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationCodeConstants.SYNC_INTERFACE)) {
            throw new SecurityException("Permission denied: SYNC_INTERFACE on SERVICE:" + req.serviceCode());
        }
    }

    /**
     * 准备服务配置
     * <p>
     * 查询并更新服务配置的基础路径（如果请求中提供）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        同步请求
     * @param operatorId 操作者ID
     * @return 服务配置实体
     * @throws BizException 服务配置不存在时抛出
     */
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

    /**
     * 规范化基础路径
     * <p>
     * 确保路径以/开头，不以/结尾。
     * </p>
     *
     * @param basePath 原始基础路径
     * @return 规范化后的路径
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
