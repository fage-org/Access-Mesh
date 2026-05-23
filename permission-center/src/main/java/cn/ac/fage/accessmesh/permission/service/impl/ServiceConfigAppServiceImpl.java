package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.permission.service.ServiceConfigAppService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final OperationLogDomainService operationLogDomainService;
    private final ResourceApiMappingMapper resourceApiMappingMapper;

    public ServiceConfigAppServiceImpl(ServiceConfigMapper serviceConfigMapper,
                                        PermQueryEngine engine,
                                        OperationLogDomainService operationLogDomainService,
                                        ResourceApiMappingMapper resourceApiMappingMapper) {
        this.serviceConfigMapper = serviceConfigMapper;
        this.engine = engine;
        this.operationLogDomainService = operationLogDomainService;
        this.resourceApiMappingMapper = resourceApiMappingMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceConfigResp saveServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
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

    @Override
    @Transactional(readOnly = true)
    public ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCode, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE:" + serviceCode);
        }

        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, serviceCode);
        return config != null ? toServiceConfigResp(config) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceConfigResp> listServiceConfigs(Long tenantId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE");
        }

        return serviceConfigMapper.selectByTenantId(tenantId).stream().map(this::toServiceConfigResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServiceConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
        }

        if (ids == null || ids.isEmpty()) {
            return;
        }

        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            return;
        }

        List<ServiceConfig> entities = serviceConfigMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            return;
        }

        Set<Long> validIds = entities.stream()
            .map(ServiceConfig::getId)
            .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        serviceConfigMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

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

    @Override
    @Transactional(readOnly = true)
    public List<ApiMappingResp> listServiceApis(Long tenantId, String serviceCode) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCode, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE:" + serviceCode);
        }
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

    private ServiceConfigResp toServiceConfigResp(ServiceConfig c) {
        return new ServiceConfigResp(
            c.getId(), c.getTenantId(), c.getServiceCode(),
            c.getName(), c.getBasePath(), c.getDescription(),
            c.getStatus(), c.getExtra(), c.getCreatedAt()
        );
    }
}
