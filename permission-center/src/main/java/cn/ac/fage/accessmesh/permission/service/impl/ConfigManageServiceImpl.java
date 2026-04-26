package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.entity.*;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.BizDomainTableDef.BIZ_DOMAIN;
import static cn.ac.fage.accessmesh.permission.entity.table.DomainConfigTableDef.DOMAIN_CONFIG;
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

    public ConfigManageServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                   BizDomainMapper bizDomainMapper,
                                   DomainConfigMapper domainConfigMapper,
                                   ServiceConfigMapper serviceConfigMapper,
                                   SystemConfigMapper systemConfigMapper) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.bizDomainMapper = bizDomainMapper;
        this.domainConfigMapper = domainConfigMapper;
        this.serviceConfigMapper = serviceConfigMapper;
        this.systemConfigMapper = systemConfigMapper;
    }

    // ===== TypeDefinition =====

    @Override
    @Transactional
    public TypeDefinitionResp createType(TypeCreateReq req, Long operatorId) {
        TypeDefinition type = new TypeDefinition();
        type.setTenantId(req.tenantId());
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
    public List<TypeDefinitionResp> listTypes(Long tenantId, Long bizDomainId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
            .and(TYPE_DEFINITION.DELETE_FLAG.eq(0));
        if (bizDomainId != null) {
            qw.and(TYPE_DEFINITION.BIZ_DOMAIN_ID.eq(bizDomainId).or(TYPE_DEFINITION.BIZ_DOMAIN_ID.isNull()));
        }
        return typeDefinitionMapper.selectListByQuery(qw)
            .stream().map(this::toTypeResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteType(Long tenantId, Long typeId, Long operatorId) {
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

    // ===== BizDomain =====

    @Override
    @Transactional
    public BizDomainResp createBizDomain(BizDomainCreateReq req, Long operatorId) {
        BizDomain domain = new BizDomain();
        domain.setTenantId(req.tenantId());
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
    @Transactional
    public void deleteBizDomain(Long tenantId, Long domainId, Long operatorId) {
        BizDomain domain = bizDomainMapper.selectOneById(domainId);
        if (domain != null && domain.getDeleteFlag() == 0L && domain.getTenantId().equals(tenantId)) {
            domain.setDeleteFlag(domain.getId());
            domain.setDeletedAt(LocalDateTime.now());
            bizDomainMapper.update(domain);
        }
    }

    // ===== DomainConfig =====

    @Override
    @Transactional
    public void upsertDomainConfig(DomainConfigReq req) {
        DomainConfig existing = domainConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(DOMAIN_CONFIG.TENANT_ID.eq(req.tenantId()))
                .and(DOMAIN_CONFIG.BIZ_DOMAIN_ID.eq(req.bizDomainId()))
                .and(DOMAIN_CONFIG.CONFIG_TYPE.eq(req.configType()))
                .and(DOMAIN_CONFIG.DELETE_FLAG.eq(0))
        );

        if (existing != null) {
            existing.setExtra(req.extra());
            existing.setUpdatedAt(LocalDateTime.now());
            domainConfigMapper.update(existing);
        } else {
            DomainConfig config = new DomainConfig();
            config.setTenantId(req.tenantId());
            config.setBizDomainId(req.bizDomainId());
            config.setConfigType(req.configType());
            config.setExtra(req.extra());
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            config.setDeleteFlag(0L);
            domainConfigMapper.insert(config);
        }
    }

    @Override
    public DomainConfigResp getDomainConfig(Long tenantId, Long bizDomainId, String configType) {
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
    public List<DomainConfigResp> listDomainConfigs(Long tenantId, Long bizDomainId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(DOMAIN_CONFIG.TENANT_ID.eq(tenantId))
            .and(DOMAIN_CONFIG.DELETE_FLAG.eq(0));
        if (bizDomainId != null) {
            qw.and(DOMAIN_CONFIG.BIZ_DOMAIN_ID.eq(bizDomainId));
        }
        return domainConfigMapper.selectListByQuery(qw)
            .stream().map(this::toDomainConfigResp).collect(Collectors.toList());
    }

    // ===== ServiceConfig =====

    @Override
    @Transactional
    public ServiceConfigResp createServiceConfig(ServiceConfigReq req, Long operatorId) {
        ServiceConfig config = new ServiceConfig();
        config.setTenantId(req.tenantId());
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
    @Transactional
    public void deleteServiceConfig(Long tenantId, String serviceCode, Long operatorId) {
        ServiceConfig config = serviceConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SERVICE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SERVICE_CONFIG.SERVICE_CODE.eq(serviceCode))
                .and(SERVICE_CONFIG.DELETE_FLAG.eq(0))
        );
        if (config != null) {
            config.setDeleteFlag(config.getId());
            config.setDeletedAt(LocalDateTime.now());
            serviceConfigMapper.update(config);
        }
    }

    // ===== SystemConfig =====

    @Override
    @Transactional
    public void upsertSystemConfig(SystemConfigReq req) {
        SystemConfig existing = systemConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYSTEM_CONFIG.TENANT_ID.eq(req.tenantId()))
                .and(SYSTEM_CONFIG.CONFIG_KEY.eq(req.configKey()))
                .and(SYSTEM_CONFIG.DELETE_FLAG.eq(0))
        );

        if (existing != null) {
            existing.setConfigValue(req.configValue());
            existing.setDescription(req.description());
            existing.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.update(existing);
        } else {
            SystemConfig config = new SystemConfig();
            config.setTenantId(req.tenantId());
            config.setConfigKey(req.configKey());
            config.setConfigValue(req.configValue());
            config.setDescription(req.description());
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            config.setDeleteFlag(0L);
            systemConfigMapper.insert(config);
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
            t.getTypeKey(), t.getTypeValue(), t.getName(),
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
}
