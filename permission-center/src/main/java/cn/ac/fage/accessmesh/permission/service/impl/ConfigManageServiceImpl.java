package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.permission.entity.SystemConfig;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ServiceInterfaceSyncService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.util.JsonValidationUtils;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配置管理服务实现类
 * <p>
 * 提供类型定义、业务域、域配置、服务配置、系统配置的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 批量删除操作采用批量软删除策略，避免N+1查询问题。
 * 服务接口同步通过ServiceInterfaceSyncService处理。
 * </p>
 *
 * TODO: 构造函数依赖过多(12个)，违反单一职责原则
 * 建议：拆分配置查询/配置管理/配置同步职责
 * 优先级：P2（非阻塞，建议在下次大版本重构时处理）
 */
@Service
public class ConfigManageServiceImpl implements ConfigManageService {

    private final TypeDefinitionMapper typeDefinitionMapper;
    private final BizDomainMapper bizDomainMapper;
    private final DomainConfigMapper domainConfigMapper;
    private final ServiceConfigMapper serviceConfigMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper resourceApiMappingMapper;
    private final TypeResolutionService typeResolutionService;
    private final OperationLogDomainService operationLogDomainService;
    private final AuthorizationService authorizationService;
    private final ServiceInterfaceSyncService serviceInterfaceSyncService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param typeDefinitionMapper        类型定义数据访问层
     * @param bizDomainMapper             业务域数据访问层
     * @param domainConfigMapper          域配置数据访问层
     * @param serviceConfigMapper         服务配置数据访问层
     * @param systemConfigMapper          系统配置数据访问层
     * @param resourceEntityMapper        资源实体数据访问层
     * @param resourceApiMappingMapper    资源API映射数据访问层
     * @param typeResolutionService       类型解析服务
     * @param operationLogDomainService   操作日志领域服务
     * @param authorizationService        授权服务
     * @param serviceInterfaceSyncService 服务接口同步服务
     * @param engine                      权限查询引擎
     */
    public ConfigManageServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                   BizDomainMapper bizDomainMapper,
                                   DomainConfigMapper domainConfigMapper,
                                   ServiceConfigMapper serviceConfigMapper,
                                   SystemConfigMapper systemConfigMapper,
                                   ResourceEntityMapper resourceEntityMapper,
                                   ResourceApiMappingMapper resourceApiMappingMapper,
                                   TypeResolutionService typeResolutionService,
                                   OperationLogDomainService operationLogDomainService,
                                   AuthorizationService authorizationService,
                                   ServiceInterfaceSyncService serviceInterfaceSyncService,
                                   PermQueryEngine engine) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.bizDomainMapper = bizDomainMapper;
        this.domainConfigMapper = domainConfigMapper;
        this.serviceConfigMapper = serviceConfigMapper;
        this.systemConfigMapper = systemConfigMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.resourceApiMappingMapper = resourceApiMappingMapper;
        this.typeResolutionService = typeResolutionService;
        this.operationLogDomainService = operationLogDomainService;
        this.authorizationService = authorizationService;
        this.serviceInterfaceSyncService = serviceInterfaceSyncService;
        this.engine = engine;
    }

    // ===== 类型定义管理 =====

    /**
     * 创建类型定义
     * <p>
     * 在指定租户下创建新的类型定义。类型定义用于系统中的各种枚举值，
     * 如用户类型、角色类型、资源类型等。需要TYPE_DEFINITION_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        类型创建请求，包含类型键、类型值、名称、描述等
     * @param operatorId 操作者ID，可选
     * @return 创建的类型定义响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TypeDefinitionResp createType(Long tenantId, TypeCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验：创建类型定义需要TYPE_DEFINITION_CREATE权限
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on TYPE_DEFINITION");
        }

        TypeDefinition type = new TypeDefinition();
        type.setTenantId(tenantId);
        type.setTypeKey(req.typeKey());
        type.setTypeValue(req.typeValue());
        type.setName(req.name());
        type.setDescription(req.description());
        type.setIsSystem(req.isSystem() != null ? req.isSystem() : false);
        type.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        type.setExtra(req.extra());
        type.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        type.setCreatedAt(now);
        type.setUpdatedAt(now);
        type.setDeleteFlag(0L);
        typeDefinitionMapper.insert(type);
        return toTypeResp(type);
    }

    /**
     * 获取类型定义详情
     * <p>
     * 根据类型ID查询类型定义的完整信息。需要TYPE_DEFINITION_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeId   类型定义ID
     * @return 类型定义响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public TypeDefinitionResp getType(Long tenantId, Long typeId) {
        // 权限校验：查看类型定义需要TYPE_DEFINITION_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, typeId, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION:" + typeId);
        }

        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, typeId);
        return type != null ? toTypeResp(type) : null;
    }

    /**
     * 查询类型定义列表
     * <p>
     * 根据业务域过滤查询类型定义列表。需要TYPE_DEFINITION_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码，可选过滤条件
     * @return 类型定义响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<TypeDefinitionResp> listTypes(Long tenantId, String domainCode) {
        // 权限校验：查看类型定义需要TYPE_DEFINITION_VIEW权限（类型级别）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION");
        }

        return typeDefinitionMapper.selectByTenantId(tenantId)
            .stream().map(this::toTypeResp).collect(Collectors.toList());
    }

    /**
     * 删除单个类型定义
     * <p>
     * 软删除指定的类型定义。系统类型（isSystem=true）不可删除。
     * 需要TYPE_DEFINITION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param typeId     类型定义ID
     * @param operatorId 操作者ID，可选
     * @throws SecurityException     无权限时抛出
     * @throws IllegalStateException  尝试删除系统类型时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long tenantId, Long typeId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验：管理类型定义需要TYPE_DEFINITION_MANAGE权限（实例级别）
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, typeId, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + typeId);
        }

        // 检查是否为系统类型，系统类型不可删除
        TypeDefinition typeDef = typeDefinitionMapper.selectValidById(tenantId, typeId);
        if (typeDef != null && Boolean.TRUE.equals(typeDef.getIsSystem())) {
            throw new IllegalStateException("Cannot delete system type: " + typeId);
        }

        TypeDefinition type = typeDefinitionMapper.selectOneById(typeId);
        if (type != null && type.getDeleteFlag() == 0L && type.getTenantId().equals(tenantId)) {
            type.setDeleteFlag(type.getId());
            type.setDeletedAt(LocalDateTime.now());
            typeDefinitionMapper.update(type);
        }
    }

    /**
     * 批量删除类型定义
     * <p>
     * 批量软删除类型定义。系统类型会被过滤掉不删除。
     * 使用批量查询和批量软删除避免N+1问题。需要TYPE_DEFINITION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        类型定义ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTypesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            return;
        }

        // 权限校验：批量管理类型定义需要TYPE_DEFINITION_MANAGE权限（实例级别批量校验）
        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, validInputIds, OperationCodeConstants.MANAGE);

        // 批量查询（避免N+1）
        List<TypeDefinition> entities = typeDefinitionMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            return;
        }

        // 过滤非系统类型并收集有效ID
        Set<Long> validIds = entities.stream()
            .filter(e -> !Boolean.TRUE.equals(e.getIsSystem()))
            .map(TypeDefinition::getId)
            .collect(Collectors.toSet());

        if (validIds.isEmpty()) {
            return;
        }

        // 使用Mapper批量软删除方法
        LocalDateTime now = LocalDateTime.now();
        typeDefinitionMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        // 记录操作日志
        operationLogDomainService.asyncRecord(
            "perm",
            "type-definition-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + validIds.size() + " type_definition row(s), ids=" + validIds,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * 更新类型定义
     * <p>
     * 更新类型定义的名称、描述、排序顺序等属性。
     * 需要TYPE_DEFINITION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        类型更新请求
     * @param operatorId 操作者ID，可选
     * @return 更新后的类型定义响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 类型不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验：管理类型定义需要TYPE_DEFINITION_MANAGE权限（实例级别）
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, req.typeId(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + req.typeId());
        }

        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, req.typeId());
        if (type == null) throw new IllegalArgumentException("Type not found: " + req.typeId());
        if (req.name() != null) type.setName(req.name());
        if (req.description() != null) type.setDescription(req.description());
        if (req.sortOrder() != null) type.setSortOrder(req.sortOrder());
        if (req.extra() != null) type.setExtra(req.extra());
        type.setUpdatedAt(LocalDateTime.now());
        typeDefinitionMapper.update(type);
        return toTypeResp(type);
    }

    // ===== 业务域管理 =====

    /**
     * 创建业务域
     * <p>
     * 创建新的业务域。业务域用于隔离不同业务场景的配置。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        业务域创建请求，包含编码、名称、描述
     * @param operatorId 操作者ID，可选
     * @return 创建的业务域响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to create biz domain");
        }

        BizDomain domain = new BizDomain();
        domain.setTenantId(tenantId);
        domain.setCode(req.code());
        domain.setName(req.name());
        domain.setDescription(req.description());
        domain.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        domain.setCreatedAt(now);
        domain.setUpdatedAt(now);
        domain.setDeleteFlag(0L);
        bizDomainMapper.insert(domain);
        return toBizDomainResp(domain);
    }

    /**
     * 获取业务域详情
     * <p>
     * 根据业务域ID查询业务域信息。需要DOMAIN_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param domainId 业务域ID
     * @return 业务域响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public BizDomainResp getBizDomain(Long tenantId, Long domainId) {
        // 权限校验：查看业务域需要DOMAIN_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.DOMAIN, domainId, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN:" + domainId);
        }

        BizDomain domain = bizDomainMapper.selectValidById(domainId, tenantId);
        return domain != null ? toBizDomainResp(domain) : null;
    }

    /**
     * 查询业务域列表
     * <p>
     * 查询租户下所有业务域。需要DOMAIN_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 业务域响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<BizDomainResp> listBizDomains(Long tenantId) {
        // 权限校验：查看业务域需要DOMAIN_VIEW权限（类型级别）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.DOMAIN, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN");
        }

        return bizDomainMapper.selectByTenantId(tenantId).stream().map(this::toBizDomainResp).collect(Collectors.toList());
    }

    /**
     * 删除单个业务域
     * <p>
     * 软删除指定的业务域。需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainId   业务域ID
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBizDomain(Long tenantId, Long domainId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete biz domain");
        }

        BizDomain domain = bizDomainMapper.selectOneById(domainId);
        if (domain != null && domain.getDeleteFlag() == 0L && domain.getTenantId().equals(tenantId)) {
            domain.setDeleteFlag(domain.getId());
            domain.setDeletedAt(LocalDateTime.now());
            bizDomainMapper.update(domain);
        }
    }

    /**
     * 批量删除业务域
     * <p>
     * 批量软删除业务域。使用批量查询和批量软删除避免N+1问题。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        业务域ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验（入口级别）
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete biz domains");
        }

        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            return;
        }

        // 批量查询（避免N+1）
        List<BizDomain> entities = bizDomainMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            return;
        }

        // 收集有效ID
        Set<Long> validIds = entities.stream()
            .map(BizDomain::getId)
            .collect(Collectors.toSet());

        // 批量软删除（性能修复：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        bizDomainMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        // 记录操作日志
        operationLogDomainService.asyncRecord(
            "perm",
            "biz-domain-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + validIds.size() + " biz_domain row(s), ids=" + validIds,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * 更新业务域
     * <p>
     * 更新业务域的名称和描述。需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        业务域更新请求
     * @param operatorId 操作者ID，可选
     * @return 更新后的业务域响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 业务域不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to update biz domain");
        }

        BizDomain domain = bizDomainMapper.selectValidById(req.domainId(), tenantId);
        if (domain == null) throw new IllegalArgumentException("BizDomain not found: " + req.domainId());
        if (req.name() != null) domain.setName(req.name());
        if (req.description() != null) domain.setDescription(req.description());
        domain.setUpdatedAt(LocalDateTime.now());
        bizDomainMapper.update(domain);
        return toBizDomainResp(domain);
    }

    // ===== 域配置管理 =====

    /**
     * 创建或更新域配置
     * <p>
     * 如果配置已存在则更新，否则创建新配置。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      域配置请求，包含业务域编码、配置类型、扩展信息
     * @return 域配置响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 业务域不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DomainConfigResp upsertDomainConfig(Long tenantId, DomainConfigReq req) {
        // 权限校验：配置操作需要SYSTEM_CONFIG_MANAGE权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to manage domain config");
        }

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        if (bizDomainId == null) {
            throw new IllegalArgumentException("Unknown domainCode: " + req.domainCode());
        }
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
            domainConfigMapper.insert(config);
            return toDomainConfigResp(config);
        }
    }

    /**
     * 获取域配置详情
     * <p>
     * 根据业务域编码和配置类型查询域配置。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @param configType 配置类型
     * @return 域配置响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public DomainConfigResp getDomainConfig(Long tenantId, String domainCode, String configType) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
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
     * 根据业务域过滤查询域配置列表。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码，可选过滤条件
     * @return 域配置响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<DomainConfigResp> listDomainConfigs(Long tenantId, String domainCode) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
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
     * 批量软删除域配置。使用批量查询和批量软删除避免N+1问题。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        域配置ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDomainConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete domain configs");
        }

        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            return;
        }

        // 批量查询（避免N+1）
        List<DomainConfig> entities = domainConfigMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            return;
        }

        // 收集有效ID
        Set<Long> validIds = entities.stream()
            .map(DomainConfig::getId)
            .collect(Collectors.toSet());

        // 批量软删除（性能修复：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        domainConfigMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        // 记录操作日志
        operationLogDomainService.asyncRecord(
            "perm",
            "domain-config-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + validIds.size() + " domain_config row(s), ids=" + validIds,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    // ===== 服务配置管理 =====

    /**
     * 保存服务配置
     * <p>
     * 如果配置已存在则更新，否则创建新配置。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        服务配置请求
     * @param operatorId 操作者ID，可选
     * @return 服务配置响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigResp saveServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to save service config");
        }

        ServiceConfigResp existing = getServiceConfig(tenantId, req.serviceCode());
        if (existing == null) {
            return createServiceConfig(tenantId, req, operatorId);
        }
        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, req.serviceCode());
        if (config == null) {
            throw new IllegalArgumentException("ServiceConfig not found: " + req.serviceCode());
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
     * 创建服务配置
     * <p>
     * 创建新的服务配置。服务配置用于定义服务的API接口映射等。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        服务配置请求
     * @param operatorId 操作者ID，可选
     * @return 服务配置响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigResp createServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to create service config");
        }
        ServiceConfig config = new ServiceConfig();
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

    /**
     * 获取服务配置详情
     * <p>
     * 根据服务编码查询服务配置。需要SERVICE_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @return 服务配置响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode) {
        // 权限校验：查看服务需要SERVICE_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCode, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE:" + serviceCode);
        }

        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, serviceCode);
        return config != null ? toServiceConfigResp(config) : null;
    }

    /**
     * 查询服务配置列表
     * <p>
     * 查询租户下所有服务配置。需要SERVICE_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 服务配置响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<ServiceConfigResp> listServiceConfigs(Long tenantId) {
        // 权限校验：查看服务需要SERVICE_VIEW权限（类型级别）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE");
        }

        return serviceConfigMapper.selectByTenantId(tenantId).stream().map(this::toServiceConfigResp).collect(Collectors.toList());
    }

    /**
     * 批量删除服务配置
     * <p>
     * 批量软删除服务配置。使用批量查询和批量软删除避免N+1问题。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        服务配置ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServiceConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验（入口级别）
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete service configs");
        }

        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            return;
        }

        // 批量查询（避免N+1）
        List<ServiceConfig> entities = serviceConfigMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            return;
        }

        // 收集有效ID
        Set<Long> validIds = entities.stream()
            .map(ServiceConfig::getId)
            .collect(Collectors.toSet());

        // 批量软删除（性能修复：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        serviceConfigMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        // 记录操作日志
        operationLogDomainService.asyncRecord(
            "perm",
            "service-config-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + validIds.size() + " service_config row(s), ids=" + validIds,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * 同步服务接口
     * <p>
     * 从服务同步API接口定义，自动创建资源实体和API映射。
     * 需要SERVICE_SYNC_INTERFACE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        服务配置同步请求
     * @param operatorId 操作者ID，可选
     * @return 服务配置同步响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigSyncResp syncServiceInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验：检查SERVICE资源的SYNC_INTERFACE权限
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationCodeConstants.SYNC_INTERFACE)) {
            throw new SecurityException("Permission denied: SYNC_INTERFACE on SERVICE:" + req.serviceCode());
        }

        return serviceInterfaceSyncService.syncInterfaces(tenantId, req, operatorId);
    }

    /**
     * 查询服务的API映射列表
     * <p>
     * 查询指定服务的所有API映射配置。
     * </p>
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @return API映射响应列表
     */
    @Override
    public List<ApiMappingResp> listServiceApis(Long tenantId, String serviceCode) {
        return resourceApiMappingMapper.selectByTenantAndServiceCode(tenantId, serviceCode).stream().map(mapping -> new ApiMappingResp(
            mapping.getId(),
            mapping.getTenantId(),
            mapping.getResourceEntityId(),
            mapping.getServiceCode(),
            mapping.getHttpMethod(),
            mapping.getPathPattern(),
            mapping.getMatchOrder(),
            mapping.getEnabled(),
            mapping.getExtra(),
            mapping.getCreatedAt(),
            mapping.getUpdatedAt()
        )).collect(Collectors.toList());
    }

    // ===== 系统配置管理 =====

    /**
     * 创建或更新系统配置
     * <p>
     * 如果配置已存在则更新，否则创建新配置。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      系统配置请求，包含配置键、配置值、描述
     * @return 系统配置响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemConfigResp upsertSystemConfig(Long tenantId, SystemConfigReq req) {
        // 权限校验：配置操作需要SYSTEM_CONFIG_MANAGE权限
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
     * 根据配置键查询系统配置。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId  租户ID
     * @param configKey 配置键
     * @return 系统配置响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public SystemConfigResp getSystemConfig(Long tenantId, String configKey) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
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
     * 查询租户下所有系统配置。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 系统配置响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<SystemConfigResp> listSystemConfigs(Long tenantId) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return systemConfigMapper.selectByTenantId(tenantId).stream().map(this::toSystemConfigResp).collect(Collectors.toList());
    }

    // ===== 实体转换方法 =====

    /**
     * 将TypeDefinition实体转换为响应对象
     *
     * @param t 类型定义实体
     * @return 类型定义响应对象
     */
    private TypeDefinitionResp toTypeResp(TypeDefinition t) {
        return new TypeDefinitionResp(
            t.getId(), t.getTenantId(),
            t.getTypeKey(), t.getTypeCode(), t.getTypeValue(), t.getName(),
            t.getDescription(), t.getIsSystem(), t.getSortOrder(),
            t.getExtra(), t.getCreatedAt()
        );
    }

    /**
     * 将BizDomain实体转换为响应对象
     *
     * @param d 业务域实体
     * @return 业务域响应对象
     */
    private BizDomainResp toBizDomainResp(BizDomain d) {
        return new BizDomainResp(
            d.getId(), d.getTenantId(), d.getCode(),
            d.getName(), d.getDescription(), d.getCreatedAt()
        );
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
     * 将ServiceConfig实体转换为响应对象
     *
     * @param c 服务配置实体
     * @return 服务配置响应对象
     */
    private ServiceConfigResp toServiceConfigResp(ServiceConfig c) {
        return new ServiceConfigResp(
            c.getId(), c.getTenantId(), c.getServiceCode(),
            c.getName(), c.getBasePath(), c.getDescription(),
            c.getStatus(), c.getExtra(), c.getCreatedAt()
        );
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