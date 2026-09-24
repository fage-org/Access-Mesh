package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.resource.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import com.mybatisflex.core.util.UpdateEntity;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.resource.service.ServiceConfigAppService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.sync.guard.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    private static final Logger log = LoggerFactory.getLogger(ServiceConfigAppServiceImpl.class);

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

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCode.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
        }

        // 保存边界校验：extra.syncTypes 结构不合法会在运行时被解释为空白名单导致全部同步 SECURITY_DENIED
        // （fail-closed 的必要运行配置，写入时尽早暴露；运行时校验仍保留，防止绕过接口改库）
        try {
            syncTypeGuard.validateSyncTypesExtra(req.extra());
        } catch (IllegalArgumentException e) {
            throw new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(),
                AccessErrorCode.PERM_INVALID_PARAM.getMessage() + ": " + e.getMessage());
        }

        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, req.serviceCode());
        if (config == null) {
            // 创建分支不接受清空标志（T-API-004）：新建无既有值可清，字段缺省即为空——
            // 携带即失败暴露调用方语义错位（与 @AssertTrue 冲突拒绝同族 fail-fast）。
            // 刻意置于门禁后的创建分支内判定（claude 外评 2026-09-24 裁决）：分支归属须读库，
            // 预判上移到门禁前会构成服务存在性 oracle（无 MANAGE 者凭 20044/403 差异探测 serviceCode
            // 是否已登记，与 T-ADMIN-029「先门禁后存在性」先例冲突；T-PERM-067「值域先于门禁」
            // 仅适用不触库的纯参数校验，不适配 upsert 双分支语义）
            if (Boolean.TRUE.equals(req.basePathClear()) || Boolean.TRUE.equals(req.descriptionClear())
                || Boolean.TRUE.equals(req.extraClear())) {
                throw new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(),
                    AccessErrorCode.PERM_INVALID_PARAM.getMessage()
                        + ": 创建服务配置不接受 basePathClear/descriptionClear/extraClear（新建字段直接省略即为空）");
            }
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
        boolean basePathClear = Boolean.TRUE.equals(req.basePathClear());
        boolean descriptionClear = Boolean.TRUE.equals(req.descriptionClear());
        boolean extraClear = Boolean.TRUE.equals(req.extraClear());
        if (req.name() != null) config.setName(req.name());
        if (basePathClear) {
            config.setBasePath(null);
        } else if (req.basePath() != null) {
            config.setBasePath(req.basePath());
        }
        if (descriptionClear) {
            config.setDescription(null);
        } else if (req.description() != null) {
            config.setDescription(req.description());
        }
        if (req.status() != null) config.setStatus(req.status());
        if (extraClear) {
            // extra 清空语义（U006 拍板）=撤销 extra.syncTypes 同步白名单声明：
            // 该服务 user/role 同步通道全拒（fail-closed），可逆（重新提交 extra 即恢复）
            config.setExtra(null);
        } else if (req.extra() != null) {
            config.setExtra(req.extra());
        }
        config.setUpdatedAt(LocalDateTime.now());
        if (basePathClear || descriptionClear || extraClear) {
            // 清空须强制写列（T-API-004）：update(entity) 默认忽略 null 字段，
            // UpdateEntity 代理记录 set(null) 为显式更新列（role extraClear 同款）
            ServiceConfig patch = UpdateEntity.of(ServiceConfig.class);
            patch.setId(config.getId());
            patch.setName(config.getName());
            patch.setBasePath(config.getBasePath());
            patch.setDescription(config.getDescription());
            patch.setStatus(config.getStatus());
            patch.setExtra(config.getExtra());
            patch.setUpdatedAt(config.getUpdatedAt());
            serviceConfigMapper.update(patch);
        } else {
            serviceConfigMapper.update(config);
        }
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
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCode, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE:" + serviceCode);
        }

        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, serviceCode);
        return config != null ? toServiceConfigResp(config) : null;
    }

    /**
     * 查询服务配置列表
     * <p>
     * 查询租户下所有服务配置。
     * 类型级 SERVICE:VIEW 通过 → 全量返回（类型级保留全量语义）；
     * 否则 T-ACCESS-052 实例准入：持任一 SERVICE 实例 VIEW（含继承覆盖，如 MANAGE
     * 继承 VIEW 位）者可进入，结果按可见实例裁剪（listApiMappings 同款批量判定先例）；
     * 无任何可见实例仍 403（fail-closed，目录=职责范围）。
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
        List<ServiceConfig> all = serviceConfigMapper.selectByTenantId(tenantId);
        if (engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCode.VIEW)) {
            return all.stream().map(this::toServiceConfigResp).collect(Collectors.toList());
        }
        Set<String> allCodes = all.stream()
            .map(ServiceConfig::getServiceCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> deniedCodes = allCodes.isEmpty() ? Set.of()
            : engine.getDeniedResourceCodes(tenantId, operatorId, ResourceTypeCode.SERVICE, allCodes, OperationCode.VIEW);
        if (deniedCodes.size() == allCodes.size()) {
            throw new SecurityException("Permission denied: VIEW on SERVICE");
        }
        return all.stream()
            .filter(c -> !deniedCodes.contains(c.getServiceCode()))
            .map(this::toServiceConfigResp)
            .collect(Collectors.toList());
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

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCode.MANAGE)) {
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
        // 被其他服务手工映射引用的资源保留）。API 类型值缺失（type_definition 种子异常）时
        // 不阻断删除但留痕——残留孤儿资源属惰性数据，种子事故需可观测
        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", ResourceTypeCode.API);
        int deletedResources;
        if (apiType != null) {
            deletedResources = resourceSyncHandler.cleanupServiceOwnedResources(tenantId, serviceCodes, apiType);
        } else {
            log.warn("service remove cascade skipped resource cleanup: resource_type API not found, tenantId={}, serviceCodes={}",
                tenantId, serviceCodes);
            deletedResources = 0;
        }

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
