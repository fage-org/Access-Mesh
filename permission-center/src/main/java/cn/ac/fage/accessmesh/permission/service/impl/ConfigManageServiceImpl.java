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
import cn.ac.fage.accessmesh.permission.entity.*;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.BizDomainTableDef.BIZ_DOMAIN;
import static cn.ac.fage.accessmesh.permission.entity.table.DomainConfigTableDef.DOMAIN_CONFIG;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.ServiceConfigTableDef.SERVICE_CONFIG;
import static cn.ac.fage.accessmesh.permission.entity.table.SystemConfigTableDef.SYSTEM_CONFIG;
import static cn.ac.fage.accessmesh.permission.entity.table.TypeDefinitionTableDef.TYPE_DEFINITION;

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

    public ConfigManageServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                   BizDomainMapper bizDomainMapper,
                                   DomainConfigMapper domainConfigMapper,
                                   ServiceConfigMapper serviceConfigMapper,
                                   SystemConfigMapper systemConfigMapper,
                                   ResourceEntityMapper resourceEntityMapper,
                                   ResourceApiMappingMapper resourceApiMappingMapper,
                                   TypeResolutionService typeResolutionService,
                                   OperationLogDomainService operationLogDomainService,
                                   AuthorizationService authorizationService) {
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
    }

    // ===== TypeDefinition =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TypeDefinitionResp createType(Long tenantId, TypeCreateReq req, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check - TYPE_DEFINITION management requires SYSTEM admin
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to create type definition");
        }

        TypeDefinition type = new TypeDefinition();
        type.setTenantId(tenantId);
        type.setBizDomainId(req.bizDomainId());
        type.setTypeKey(req.typeKey());
        type.setTypeValue(req.typeValue());
        type.setName(req.name());
        type.setDescription(req.description());
        type.setIsSystem(req.isSystem() != null ? req.isSystem() : false);
        type.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        type.setExtra(req.extra());
        type.setCreatedBy(operatorId);
        type.setCreatedAt(LocalDateTime.now());
        type.setUpdatedAt(LocalDateTime.now());
        type.setDeleteFlag(0L);
        typeDefinitionMapper.insert(type);
        return toTypeResp(type);
    }

    @Override
    public TypeDefinitionResp getType(Long tenantId, Long typeId) {
        TypeDefinition type = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.ID.eq(typeId))
                .and(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );
        return type != null ? toTypeResp(type) : null;
    }

    @Override
    public List<TypeDefinitionResp> listTypes(Long tenantId, String domainCode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
            .and(TYPE_DEFINITION.DELETE_FLAG.eq(0));
        if (domainCode != null && !domainCode.isBlank()) {
            Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (bizDomainId == null) {
                return List.of();
            }
            qw.and(TYPE_DEFINITION.BIZ_DOMAIN_ID.eq(bizDomainId).or(TYPE_DEFINITION.BIZ_DOMAIN_ID.isNull()));
        }
        return typeDefinitionMapper.selectListByQuery(qw)
            .stream().map(this::toTypeResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long tenantId, Long typeId, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to delete type definition");
        }

        TypeDefinition type = typeDefinitionMapper.selectOneById(typeId);
        if (type != null && type.getDeleteFlag() == 0L && type.getTenantId().equals(tenantId)) {
            if (Boolean.TRUE.equals(type.getIsSystem())) {
                throw new IllegalStateException("Cannot delete system type: " + typeId);
            }
            type.setDeleteFlag(type.getId());
            type.setDeletedAt(LocalDateTime.now());
            typeDefinitionMapper.update(type);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTypesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to delete type definitions");
        }

        if (ids == null || ids.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            deleteType(tenantId, id, operatorId);
            n++;
        }
        if (n > 0) {
            operationLogDomainService.asyncRecord(
                "perm",
                "type-definition-remove",
                "BATCH",
                tenantId,
                "batch soft-delete type_definition, count=" + n + ", ids=" + ids,
                operatorId,
                null,
                null,
                tenantId
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to update type definition");
        }

        TypeDefinition type = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.ID.eq(req.typeId()))
                .and(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );
        if (type == null) throw new IllegalArgumentException("Type not found: " + req.typeId());
        if (req.bizDomainId() != null) type.setBizDomainId(req.bizDomainId());
        if (req.name() != null) type.setName(req.name());
        if (req.description() != null) type.setDescription(req.description());
        if (req.sortOrder() != null) type.setSortOrder(req.sortOrder());
        if (req.extra() != null) type.setExtra(req.extra());
        type.setUpdatedAt(LocalDateTime.now());
        typeDefinitionMapper.update(type);
        return toTypeResp(type);
    }

    // ===== BizDomain =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to create biz domain");
        }

        BizDomain domain = new BizDomain();
        domain.setTenantId(tenantId);
        domain.setCode(req.code());
        domain.setName(req.name());
        domain.setDescription(req.description());
        domain.setCreatedBy(operatorId);
        domain.setCreatedAt(LocalDateTime.now());
        domain.setUpdatedAt(LocalDateTime.now());
        domain.setDeleteFlag(0L);
        bizDomainMapper.insert(domain);
        return toBizDomainResp(domain);
    }

    @Override
    public BizDomainResp getBizDomain(Long tenantId, Long domainId) {
        BizDomain domain = bizDomainMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(BIZ_DOMAIN.ID.eq(domainId))
                .and(BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BIZ_DOMAIN.DELETE_FLAG.eq(0))
        );
        return domain != null ? toBizDomainResp(domain) : null;
    }

    @Override
    public List<BizDomainResp> listBizDomains(Long tenantId) {
        return bizDomainMapper.selectListByQuery(
            QueryWrapper.create()
                .where(BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BIZ_DOMAIN.DELETE_FLAG.eq(0))
        ).stream().map(this::toBizDomainResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBizDomain(Long tenantId, Long domainId, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to delete biz domain");
        }

        BizDomain domain = bizDomainMapper.selectOneById(domainId);
        if (domain != null && domain.getDeleteFlag() == 0L && domain.getTenantId().equals(tenantId)) {
            domain.setDeleteFlag(domain.getId());
            domain.setDeletedAt(LocalDateTime.now());
            bizDomainMapper.update(domain);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to delete biz domains");
        }

        if (ids == null || ids.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            deleteBizDomain(tenantId, id, operatorId);
            n++;
        }
        if (n > 0) {
            operationLogDomainService.asyncRecord(
                "perm",
                "biz-domain-remove",
                "BATCH",
                tenantId,
                "batch soft-delete biz_domain, count=" + n + ", ids=" + ids,
                operatorId,
                null,
                null,
                tenantId
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to update biz domain");
        }

        BizDomain domain = bizDomainMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(BIZ_DOMAIN.ID.eq(req.domainId()))
                .and(BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BIZ_DOMAIN.DELETE_FLAG.eq(0))
        );
        if (domain == null) throw new IllegalArgumentException("BizDomain not found: " + req.domainId());
        if (req.name() != null) domain.setName(req.name());
        if (req.description() != null) domain.setDescription(req.description());
        domain.setUpdatedAt(LocalDateTime.now());
        bizDomainMapper.update(domain);
        return toBizDomainResp(domain);
    }

    // ===== DomainConfig =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DomainConfigResp upsertDomainConfig(Long tenantId, DomainConfigReq req) {
        // Permission check for config operations
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to manage domain config");
        }

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        if (bizDomainId == null) {
            throw new IllegalArgumentException("Unknown domainCode: " + req.domainCode());
        }
        DomainConfig existing = domainConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(DOMAIN_CONFIG.TENANT_ID.eq(tenantId))
                .and(DOMAIN_CONFIG.BIZ_DOMAIN_ID.eq(bizDomainId))
                .and(DOMAIN_CONFIG.CONFIG_TYPE.eq(req.configType()))
                .and(DOMAIN_CONFIG.DELETE_FLAG.eq(0))
        );

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
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            config.setDeleteFlag(0L);
            domainConfigMapper.insert(config);
            return toDomainConfigResp(config);
        }
    }

    @Override
    public DomainConfigResp getDomainConfig(Long tenantId, String domainCode, String configType) {
        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
        if (bizDomainId == null) {
            return null;
        }
        DomainConfig config = domainConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(DOMAIN_CONFIG.TENANT_ID.eq(tenantId))
                .and(DOMAIN_CONFIG.BIZ_DOMAIN_ID.eq(bizDomainId))
                .and(DOMAIN_CONFIG.CONFIG_TYPE.eq(configType))
                .and(DOMAIN_CONFIG.DELETE_FLAG.eq(0))
        );
        return config != null ? toDomainConfigResp(config) : null;
    }

    @Override
    public List<DomainConfigResp> listDomainConfigs(Long tenantId, String domainCode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(DOMAIN_CONFIG.TENANT_ID.eq(tenantId))
            .and(DOMAIN_CONFIG.DELETE_FLAG.eq(0));
        if (domainCode != null && !domainCode.isBlank()) {
            Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (bizDomainId == null) {
                return List.of();
            }
            qw.and(DOMAIN_CONFIG.BIZ_DOMAIN_ID.eq(bizDomainId));
        }
        return domainConfigMapper.selectListByQuery(qw)
            .stream().map(this::toDomainConfigResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDomainConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            DomainConfig config = domainConfigMapper.selectOneById(id);
            if (config != null && Objects.equals(tenantId, config.getTenantId()) && config.getDeleteFlag() == 0L) {
                config.setDeleteFlag(config.getId());
                config.setDeletedAt(LocalDateTime.now());
                domainConfigMapper.update(config);
                n++;
            }
        }
        operationLogDomainService.asyncRecord(
            "perm",
            "domain-config-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + n + " domain_config row(s), ids=" + ids,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    // ===== ServiceConfig =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigResp saveServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to save service config");
        }

        ServiceConfigResp existing = getServiceConfig(tenantId, req.serviceCode());
        if (existing == null) {
            return createServiceConfig(tenantId, req, operatorId);
        }
        ServiceConfig config = serviceConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SERVICE_CONFIG.TENANT_ID.eq(tenantId))
                .where(SERVICE_CONFIG.SERVICE_CODE.eq(req.serviceCode()))
                .and(SERVICE_CONFIG.DELETE_FLAG.eq(0))
        );
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigResp createServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
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
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        config.setDeleteFlag(0L);
        serviceConfigMapper.insert(config);
        return toServiceConfigResp(config);
    }

    @Override
    public ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode) {
        ServiceConfig config = serviceConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SERVICE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SERVICE_CONFIG.SERVICE_CODE.eq(serviceCode))
                .and(SERVICE_CONFIG.DELETE_FLAG.eq(0))
        );
        return config != null ? toServiceConfigResp(config) : null;
    }

    @Override
    public List<ServiceConfigResp> listServiceConfigs(Long tenantId) {
        return serviceConfigMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SERVICE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SERVICE_CONFIG.DELETE_FLAG.eq(0))
        ).stream().map(this::toServiceConfigResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServiceConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "SYSTEM_CONFIG", "MANAGE")) {
            throw new SecurityException("No permission to delete service configs");
        }

        if (ids == null || ids.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            ServiceConfig config = serviceConfigMapper.selectOneById(id);
            if (config != null && Objects.equals(tenantId, config.getTenantId()) && config.getDeleteFlag() == 0L) {
                config.setDeleteFlag(config.getId());
                config.setDeletedAt(LocalDateTime.now());
                serviceConfigMapper.update(config);
                n++;
            }
        }
        operationLogDomainService.asyncRecord(
            "perm",
            "service-config-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + n + " service_config row(s), ids=" + ids,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigSyncResp syncServiceInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId) {
        if (!"FULL".equalsIgnoreCase(req.syncMode())) {
            throw new IllegalArgumentException("Only FULL syncMode is supported");
        }
        ServiceConfig serviceConfig = serviceConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SERVICE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SERVICE_CONFIG.SERVICE_CODE.eq(req.serviceCode()))
                .and(SERVICE_CONFIG.DELETE_FLAG.eq(0))
        );
        if (serviceConfig == null) {
            throw new IllegalArgumentException("ServiceConfig not found: " + req.serviceCode());
        }
        if (req.basePath() != null && !req.basePath().isBlank()) {
            serviceConfig.setBasePath(req.basePath());
            serviceConfig.setUpdatedAt(LocalDateTime.now());
            serviceConfigMapper.update(serviceConfig);
        }
        String basePath = normalizeBasePath(
            req.basePath() != null && !req.basePath().isBlank() ? req.basePath() : serviceConfig.getBasePath()
        );
        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "API");
        if (apiType == null) {
            throw new IllegalArgumentException("resource_type API not found");
        }

        int createdResources = 0;
        int createdMappings = 0;
        int updatedMappings = 0;
        int deletedResources = 0;
        int deletedMappings = 0;

        java.util.Set<String> incomingRouteResourceKeys = new java.util.HashSet<>();
        java.util.Set<Long> activeSyncedResourceIds = new java.util.HashSet<>();
        for (ServiceConfigSyncReq.GroupItem group : req.groups()) {
            for (ServiceConfigSyncReq.ApiItem api : group.apis()) {
                String fullPath = joinPath(basePath, api.path());
                String routeResourceKey = api.httpMethod().toUpperCase() + "|" + fullPath + "|" + api.resourceCode();
                incomingRouteResourceKeys.add(routeResourceKey);
                String syncKey = req.serviceCode() + "|" + api.resourceCode();
                ResourceEntity resource = resourceEntityMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                        .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(apiType))
                        .and(RESOURCE_ENTITY.CODE.eq(api.resourceCode()))
                        .and(RESOURCE_ENTITY.CODE_TYPE.eq("default"))
                        .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                );
                if (resource == null) {
                    resource = new ResourceEntity();
                    resource.setTenantId(tenantId);
                    resource.setResourceType(apiType);
                    resource.setCode(api.resourceCode());
                    resource.setCodeType("default");
                    resource.setName(api.name());
                    resource.setPath(fullPath);
                    resource.setStatus(1);
                    resource.setSortOrder(0);
                    resource.setOwnerServiceCode(req.serviceCode());
                    resource.setMaintainSource("SERVICE_SYNC");
                    resource.setSyncKey(syncKey);
                    resource.setExtra("{}");
                    resource.setCreatedBy(operatorId);
                    resource.setCreatedAt(LocalDateTime.now());
                    resource.setUpdatedAt(LocalDateTime.now());
                    resource.setDeleteFlag(0L);
                    resourceEntityMapper.insert(resource);
                    createdResources++;
                } else {
                    if (!"SERVICE_SYNC".equals(resource.getMaintainSource())
                        || resource.getOwnerServiceCode() == null
                        || !req.serviceCode().equals(resource.getOwnerServiceCode())) {
                        throw new IllegalStateException("resourceCode already maintained by non-sync source: " + api.resourceCode());
                    }
                    resource.setName(api.name());
                    resource.setPath(fullPath);
                    resource.setStatus(1);
                    resource.setSyncKey(syncKey);
                    resource.setUpdatedAt(LocalDateTime.now());
                    resourceEntityMapper.update(resource);
                }
                activeSyncedResourceIds.add(resource.getId());

                ResourceApiMapping mapping = resourceApiMappingMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                        .and(RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.eq(resource.getId()))
                        .and(RESOURCE_API_MAPPING.SERVICE_CODE.eq(req.serviceCode()))
                        .and(RESOURCE_API_MAPPING.HTTP_METHOD.eq(api.httpMethod().toUpperCase()))
                        .and(RESOURCE_API_MAPPING.PATH_PATTERN.eq(fullPath))
                        .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
                );
                if (mapping == null) {
                    mapping = new ResourceApiMapping();
                    mapping.setTenantId(tenantId);
                    mapping.setResourceEntityId(resource.getId());
                    mapping.setServiceCode(req.serviceCode());
                    mapping.setHttpMethod(api.httpMethod().toUpperCase());
                    mapping.setPathPattern(fullPath);
                    mapping.setMatchOrder(0);
                    mapping.setEnabled(true);
                    mapping.setExtra("{\"syncKey\":\"" + syncKey + "\"}");
                    mapping.setCreatedBy(operatorId);
                    mapping.setCreatedAt(LocalDateTime.now());
                    mapping.setUpdatedAt(LocalDateTime.now());
                    mapping.setDeleteFlag(0L);
                    resourceApiMappingMapper.insert(mapping);
                    createdMappings++;
                } else {
                    mapping.setEnabled(true);
                    mapping.setExtra("{\"syncKey\":\"" + syncKey + "\"}");
                    mapping.setUpdatedAt(LocalDateTime.now());
                    resourceApiMappingMapper.update(mapping);
                    updatedMappings++;
                }
            }
        }

        java.util.List<ResourceApiMapping> existingMappings = resourceApiMappingMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(RESOURCE_API_MAPPING.SERVICE_CODE.eq(req.serviceCode()))
                .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        );

        // Batch load all resources (avoid N+1)
        Set<Long> mappingResourceIds = existingMappings.stream()
            .map(ResourceApiMapping::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = mappingResourceIds.isEmpty() ? Map.of()
            : resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_ENTITY.ID.in(mappingResourceIds))
                    .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        for (ResourceApiMapping mapping : existingMappings) {
            ResourceEntity resource = resourceMap.get(mapping.getResourceEntityId());
            if (resource == null) {
                continue;
            }
            if (!"SERVICE_SYNC".equals(resource.getMaintainSource())
                || !req.serviceCode().equals(resource.getOwnerServiceCode())) {
                continue;
            }
            String routeKey = mapping.getHttpMethod().toUpperCase() + "|" + mapping.getPathPattern();
            String resourceCode = resource.getCode();
            String routeResourceKey = routeKey + "|" + resourceCode;
            if (!incomingRouteResourceKeys.contains(routeResourceKey)) {
                mapping.setDeleteFlag(mapping.getId());
                mapping.setDeletedAt(LocalDateTime.now());
                resourceApiMappingMapper.update(mapping);
                deletedMappings++;
            }
        }

        java.util.List<ResourceEntity> apiResources = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(apiType))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        java.util.List<ResourceEntity> syncedResources = apiResources.stream()
            .filter(resource -> "SERVICE_SYNC".equals(resource.getMaintainSource())
                && req.serviceCode().equals(resource.getOwnerServiceCode()))
            .toList();

        // Batch load mappings for all synced resources to avoid N+1 query
        Set<Long> syncedResourceIds = syncedResources.stream()
            .map(ResourceEntity::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, List<ResourceApiMapping>> mappingsByResourceId = syncedResourceIds.isEmpty() ? Map.of()
            : resourceApiMappingMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.in(syncedResourceIds))
                    .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.groupingBy(ResourceApiMapping::getResourceEntityId));

        for (ResourceEntity resource : syncedResources) {
            List<ResourceApiMapping> remainMappings = mappingsByResourceId.getOrDefault(resource.getId(), List.of());
            if (remainMappings.isEmpty()) {
                resource.setDeleteFlag(resource.getId());
                resource.setDeletedAt(LocalDateTime.now());
                resourceEntityMapper.update(resource);
                deletedResources++;
            }
        }

        return new ServiceConfigSyncResp(
            createdResources, createdMappings, updatedMappings, deletedResources, deletedMappings
        );
    }

    @Override
    public List<ApiMappingResp> listServiceApis(Long tenantId, String serviceCode) {
        return resourceApiMappingMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(RESOURCE_API_MAPPING.SERVICE_CODE.eq(serviceCode))
                .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        ).stream().map(mapping -> new ApiMappingResp(
            mapping.getId(),
            mapping.getTenantId(),
            mapping.getBizDomainId(),
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

    // ===== SystemConfig =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemConfigResp upsertSystemConfig(Long tenantId, SystemConfigReq req) {
        SystemConfig existing = systemConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYSTEM_CONFIG.TENANT_ID.eq(tenantId))
                .and(SYSTEM_CONFIG.CONFIG_KEY.eq(req.configKey()))
                .and(SYSTEM_CONFIG.DELETE_FLAG.eq(0))
        );

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
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            config.setDeleteFlag(0L);
            systemConfigMapper.insert(config);
            return toSystemConfigResp(config);
        }
    }

    @Override
    public SystemConfigResp getSystemConfig(Long tenantId, String configKey) {
        SystemConfig config = systemConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYSTEM_CONFIG.TENANT_ID.eq(tenantId))
                .and(SYSTEM_CONFIG.CONFIG_KEY.eq(configKey))
                .and(SYSTEM_CONFIG.DELETE_FLAG.eq(0))
        );
        return config != null ? toSystemConfigResp(config) : null;
    }

    @Override
    public List<SystemConfigResp> listSystemConfigs(Long tenantId) {
        return systemConfigMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYSTEM_CONFIG.TENANT_ID.eq(tenantId))
                .and(SYSTEM_CONFIG.DELETE_FLAG.eq(0))
        ).stream().map(this::toSystemConfigResp).collect(Collectors.toList());
    }

    // ===== Converters =====

    private TypeDefinitionResp toTypeResp(TypeDefinition t) {
        return new TypeDefinitionResp(
            t.getId(), t.getTenantId(), t.getBizDomainId(),
            t.getTypeKey(), t.getTypeCode(), t.getTypeValue(), t.getName(),
            t.getDescription(), t.getIsSystem(), t.getSortOrder(),
            t.getExtra(), t.getCreatedAt()
        );
    }

    private BizDomainResp toBizDomainResp(BizDomain d) {
        return new BizDomainResp(
            d.getId(), d.getTenantId(), d.getCode(),
            d.getName(), d.getDescription(), d.getCreatedAt()
        );
    }

    private DomainConfigResp toDomainConfigResp(DomainConfig c) {
        return new DomainConfigResp(
            c.getId(), c.getTenantId(), c.getBizDomainId(),
            c.getConfigType(), c.getExtra(), c.getUpdatedAt()
        );
    }

    private ServiceConfigResp toServiceConfigResp(ServiceConfig c) {
        return new ServiceConfigResp(
            c.getId(), c.getTenantId(), c.getServiceCode(),
            c.getName(), c.getBasePath(), c.getDescription(),
            c.getStatus(), c.getExtra(), c.getCreatedAt()
        );
    }

    private SystemConfigResp toSystemConfigResp(SystemConfig c) {
        return new SystemConfigResp(
            c.getId(), c.getTenantId(), c.getConfigKey(),
            c.getConfigValue(), c.getDescription(), c.getUpdatedAt()
        );
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

    private String joinPath(String basePath, String path) {
        String p = path == null ? "" : path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return (basePath + p).replaceAll("//+", "/");
    }

}
