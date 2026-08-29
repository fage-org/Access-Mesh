package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.ServiceConfigAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务配置应用服务实现类
 * <p>
 * 提供服务配置的CRUD操作和服务接口同步。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class ServiceConfigAppServiceImpl implements ServiceConfigAppService {

    private final ServiceConfigMapper serviceConfigMapper;
    private final PermQueryEngine engine;
    private final ResourceApiMappingMapper resourceApiMappingMapper;
    private final SyncTypeGuard syncTypeGuard;
    private final ResourceSyncHandler resourceSyncHandler;
    private final TypeResolutionService typeResolutionService;
    private final ResourceManageAppService resourceManageAppService;

    /**
     * 构造函数注入依赖
     *
     * @param serviceConfigMapper      服务配置数据访问层
     * @param engine                   权限查询引擎
     * @param resourceApiMappingMapper 资源API映射数据访问层
     * @param syncTypeGuard            同步类型白名单守卫（保存边界校验 extra.syncTypes 结构）
     * @param resourceSyncHandler      资源同步处理器（服务删除级联清理 SERVICE_SYNC 资源）
     * @param typeResolutionService    类型解析服务（级联清理解析 API 资源类型值）
     * @param resourceManageAppService 资源管理应用服务（apis 复用映射列表的门禁与资源字段补全）
     */
    public ServiceConfigAppServiceImpl(ServiceConfigMapper serviceConfigMapper,
                                        PermQueryEngine engine,
                                        ResourceApiMappingMapper resourceApiMappingMapper,
                                        SyncTypeGuard syncTypeGuard,
                                        ResourceSyncHandler resourceSyncHandler,
                                        TypeResolutionService typeResolutionService,
                                        ResourceManageAppService resourceManageAppService) {
        this.serviceConfigMapper = serviceConfigMapper;
        this.engine = engine;
        this.resourceApiMappingMapper = resourceApiMappingMapper;
        this.syncTypeGuard = syncTypeGuard;
        this.resourceSyncHandler = resourceSyncHandler;
        this.typeResolutionService = typeResolutionService;
        this.resourceManageAppService = resourceManageAppService;
    }

    /**
     * 创建或更新服务配置
     * <p>
     * 根据服务编码创建新配置或更新已有配置。
     * 服务配置用于管理微服务的元数据信息，包括服务名称、基础路径等。
     * 需要SERVICE_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        配置请求，包含服务编码、名称、基础路径、描述、状态等
     * @param operatorId 操作者ID，可选
     * @return 服务配置响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "SERVICE_CONFIG_SAVE", targetType = "service_config", targetId = "#req.serviceCode()", summary = "'save service config ' + #req.serviceCode()")
    public ServiceConfigResp saveServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
        }

        // 保存边界校验：extra.syncTypes 结构不合法会在运行时被解释为空白名单导致全部同步 SECURITY_DENIED
        // （fail-closed 的必要运行配置，写入时尽早暴露；运行时校验仍保留，防止绕过接口改库）
        try {
            syncTypeGuard.validateSyncTypesExtra(req.extra());
        } catch (IllegalArgumentException e) {
            throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                PermissionErrorCode.INVALID_PARAM.getMessage() + ": " + e.getMessage());
        }

        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, req.serviceCode());
        if (config == null) {
            config = new ServiceConfig();
            config.setTenantId(tenantId);
            config.setServiceCode(req.serviceCode());
            config.setName(req.name());
            config.setBasePath(req.basePath());
            config.setDescription(req.description());
            config.setStatus(req.status() != null ? req.status() : 1);
            config.setExtra(req.extra());
            config.setCreatedBy(operatorId);
            LocalDateTime now = LocalDateTime.now();
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            config.setDeleteFlag(0L);
            serviceConfigMapper.insert(config);
            return toServiceConfigResp(config);
        }
        if (req.name() != null) config.setName(req.name());
        if (req.basePath() != null) config.setBasePath(req.basePath());
        if (req.description() != null) config.setDescription(req.description());
        if (req.status() != null) config.setStatus(req.status());
        if (req.extra() != null) config.setExtra(req.extra());
        config.setUpdatedAt(LocalDateTime.now());
        serviceConfigMapper.update(config);
        return toServiceConfigResp(config);
    }

    /**
     * 获取服务配置详情
     * <p>
     * 根据服务编码查询服务配置的完整信息。
     * 需要SERVICE_VIEW权限。
     * </p>
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 服务配置响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCode, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE:" + serviceCode);
        }

        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, serviceCode);
        return config != null ? toServiceConfigResp(config) : null;
    }

    /**
     * 查询服务配置列表
     * <p>
     * 查询租户下所有服务配置。
     * 需要SERVICE_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 服务配置响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public List<ServiceConfigResp> listServiceConfigs(Long tenantId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE");
        }

        return serviceConfigMapper.selectByTenantId(tenantId).stream().map(this::toServiceConfigResp).collect(Collectors.toList());
    }

    /**
     * 批量删除服务配置
     * <p>
     * 批量软删除服务配置，并级联清理该服务的关联数据（T-PERM-027，§7.2 设计定案）：
     * 同事务内软删该服务全部 API 映射（含 MANUAL 维护来源）与该服务 SERVICE_SYNC
     * 自动维护的孤立 API 资源（跨服务手工映射引用的资源保留），事务提交后经
     * {@code markServiceCodes} 广播 Gateway 本地快照失效。整批失败整批不变更。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要SERVICE_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        配置ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "SERVICE_CONFIG_REMOVE", targetType = "service_config", targetId = "", summary = "'batch remove service configs'")
    public void deleteServiceConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
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

        List<ServiceConfig> entities = serviceConfigMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream()
            .map(ServiceConfig::getId)
            .collect(Collectors.toSet());
        Set<String> serviceCodes = entities.stream()
            .map(ServiceConfig::getServiceCode)
            .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        serviceConfigMapper.softDeleteBatch(tenantId, new ArrayList<>(validIds), now);

        // 级联①：软删该服务全部映射（含 MANUAL；服务已删，其路由不再存在，映射即死路径）
        List<ResourceApiMapping> mappings = resourceApiMappingMapper.selectValidByServiceCodes(tenantId, serviceCodes);
        List<Long> mappingIds = mappings.stream()
            .map(ResourceApiMapping::getId)
            .collect(Collectors.toList());
        if (!mappingIds.isEmpty()) {
            resourceApiMappingMapper.softDeleteBatch(tenantId, mappingIds, now);
        }

        // 级联②：软删该服务 SERVICE_SYNC 自动维护的孤立 API 资源（FULL diff 同边界；
        // 被其他服务手工映射引用的资源保留）
        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", ResourceTypeCode.API);
        int deletedResources = apiType != null
            ? resourceSyncHandler.cleanupServiceOwnedResources(tenantId, serviceCodes, apiType)
            : 0;

        // 级联③：Gateway 本地快照失效广播（映射已变，perm 未变不 markRoles）
        PermissionChangeContext.markServiceCodes(tenantId, serviceCodes);

        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " service_config row(s), "
            + mappingIds.size() + " api mapping(s), " + deletedResources + " synced api resource(s)");
    }

    /**
     * 查询服务的API映射列表
     * <p>
     * 查询指定服务下已注册的API接口映射。
     * API映射用于接口级权限校验，定义HTTP方法、路径模式与资源实体的关联。
     * 需要该服务的SERVICE_VIEW权限。
     * T-PERM-027：委托 {@link ResourceManageAppService#listApiMappings}（同层复用），
     * 统一 SERVICE:VIEW 实例门禁与响应的资源业务字段补全。
     * </p>
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return API映射响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public List<ApiMappingResp> listServiceApis(Long tenantId, String serviceCode) {
        return resourceManageAppService.listApiMappings(tenantId, null, serviceCode);
    }

    /**
     * 将ServiceConfig实体转换为响应对象
     *
     * @param c 服务配置实体
     * @return 服务配置响应对象
     */
    private ServiceConfigResp toServiceConfigResp(ServiceConfig c) {
        return new ServiceConfigResp(
            c.getId(), c.getTenantId(), c.getServiceCode(),
            c.getName(), c.getBasePath(), c.getDescription(),
            c.getStatus(), c.getExtra(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
