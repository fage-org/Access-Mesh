package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
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
import cn.ac.fage.accessmesh.permission.entity.table.ServiceConfigTableDef;

/**
 * 服务接口同步服务实现类
 * <p>
 * 提供服务接口与资源API映射的同步功能。
 * 使用策略模式处理不同的同步模式（FULL、INCREMENTAL等）。
 * 同步流程：
 * 1. 权限校验（需要SYSTEM_CONFIG_MANAGE权限）
 * 2. 获取/准备服务配置
 * 3. 解析基础路径和API资源类型
 * 4. 创建同步上下文
 * 5. 根据同步模式选择策略执行同步
 * 6. 记录同步结果日志
 * </p>
 * <p>
 * TODO: 构造函数依赖过多(8个)，违反单一职责原则。
 * 建议：拆分接口同步和批量处理职责。
 * 优先级：P3（低优先级，可关注但不强制整改）。
 * </p>
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
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param resourceSyncHandler       资源同步处理器
     * @param mappingSyncHandler        API映射同步处理器
     * @param serviceConfigMapper       服务配置数据访问层
     * @param authorizationService      授权服务
     * @param operationLogDomainService 操作日志领域服务
     * @param typeResolutionService     类型解析服务
     * @param strategyFactory           同步模式策略工厂
     * @param engine                    权限查询引擎
     */
    // TODO: 构造函数依赖过多(8个)，违反单一职责原则
    // 建议：拆分接口同步/批量处理职责
    // 优先级：P3（低优先级，可关注但不强制整改）
    public ServiceInterfaceSyncServiceImpl(
            ResourceSyncHandler resourceSyncHandler,
            MappingSyncHandler mappingSyncHandler,
            ServiceConfigMapper serviceConfigMapper,
            AuthorizationService authorizationService,
            OperationLogDomainService operationLogDomainService,
            TypeResolutionService typeResolutionService,
            SyncModeStrategyFactory strategyFactory,
            PermQueryEngine engine) {
        this.resourceSyncHandler = resourceSyncHandler;
        this.mappingSyncHandler = mappingSyncHandler;
        this.serviceConfigMapper = serviceConfigMapper;
        this.authorizationService = authorizationService;
        this.operationLogDomainService = operationLogDomainService;
        this.typeResolutionService = typeResolutionService;
        this.strategyFactory = strategyFactory;
        this.engine = engine;
    }

    /**
     * 同步服务接口
     * <p>
     * 根据服务配置和同步请求同步资源实体和API映射。
     * 使用策略模式支持不同的同步模式。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        同步请求，包含服务编码、同步模式、接口列表等
     * @param operatorId 操作者ID，可选
     * @return 同步响应，包含创建/更新的资源和映射数量统计
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 服务配置不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigSyncResp syncInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        validatePermission(tenantId, operatorId);

        // 获取并准备服务配置
        ServiceConfig config = prepareServiceConfig(tenantId, req, operatorId);

        // 获取基础路径
        String basePath = normalizeBasePath(
            req.basePath() != null && !req.basePath().isBlank() ? req.basePath() : config.getBasePath()
        );

        // 解析API资源类型
        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", ResourceTypeCode.API);
        if (apiType == null) {
            throw new IllegalArgumentException("resource_type API not found");
        }

        // 创建同步上下文
        SyncContext context = SyncContext.of(tenantId, config, req, operatorId, basePath, apiType);

        // 获取策略并执行
        SyncModeStrategy strategy = strategyFactory.getStrategy(req.syncMode());
        SyncResult result = strategy.execute(context, resourceSyncHandler, mappingSyncHandler);

        // 记录同步结果日志
        logSyncResult(tenantId, req.serviceCode(), result, operatorId);

        return result.toResponse();
    }

    /**
     * 验证同步操作权限
     * <p>
     * 检查操作者是否有SYSTEM_CONFIG的MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operatorId 操作者ID
     * @throws SecurityException 无权限时抛出
     */
    private void validatePermission(Long tenantId, Long operatorId) {
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to sync service interfaces");
        }
    }

    /**
     * 准备服务配置
     * <p>
     * 获取已存在的服务配置，验证服务编码有效性。
     * 如果请求中提供basePath，更新服务配置的基础路径。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        同步请求
     * @param operatorId 操作者ID
     * @return 服务配置实体
     * @throws IllegalArgumentException 服务配置不存在时抛出
     */
    private ServiceConfig prepareServiceConfig(Long tenantId, ServiceConfigSyncReq req, Long operatorId) {
        ServiceConfig config = serviceConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ServiceConfigTableDef.SERVICE_CONFIG.TENANT_ID.eq(tenantId))
                .and(ServiceConfigTableDef.SERVICE_CONFIG.SERVICE_CODE.eq(req.serviceCode()))
                .and(ServiceConfigTableDef.SERVICE_CONFIG.DELETE_FLAG.eq(0))
        );

        if (config == null) {
            throw new IllegalArgumentException("ServiceConfig not found: " + req.serviceCode());
        }

        // 如果请求中提供了basePath，更新服务配置
        if (req.basePath() != null && !req.basePath().isBlank()) {
            config.setBasePath(req.basePath());
            config.setUpdatedAt(LocalDateTime.now());
            serviceConfigMapper.update(config);
        }

        return config;
    }

    /**
     * 记录同步结果日志
     * <p>
     * 异步记录同步操作的详细结果，包括创建/更新/删除的资源数量和映射数量。
     * </p>
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @param result     同步结果
     * @param operatorId 操作者ID
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
     * 规范化基础路径
     * <p>
     * 处理基础路径格式：
     * - 前导/：确保路径以/开头
     * - 尾部/：移除尾部斜杠
     * - 空值：返回空字符串
     * </p>
     *
     * @param basePath 基础路径
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