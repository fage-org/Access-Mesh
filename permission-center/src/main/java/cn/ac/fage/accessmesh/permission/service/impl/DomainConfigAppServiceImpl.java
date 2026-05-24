package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.service.DomainConfigAppService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
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
 * 域配置应用服务实现类
 * <p>
 * 提供域配置的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class DomainConfigAppServiceImpl implements DomainConfigAppService {

    private final DomainConfigMapper domainConfigMapper;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    public DomainConfigAppServiceImpl(DomainConfigMapper domainConfigMapper,
                                       TypeResolutionService typeResolutionService,
                                       PermQueryEngine engine) {
        this.domainConfigMapper = domainConfigMapper;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "domain-config-upsert", targetType = "domain_config", targetId = "#result.id()", summary = "'upsert domain config ' + #req.domainCode() + ':' + #req.configType()")
    public DomainConfigResp upsertDomainConfig(Long tenantId, DomainConfigReq req) {
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

    @Override
    @Transactional(readOnly = true)
    public DomainConfigResp getDomainConfig(Long tenantId, String domainCode, String configType) {
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

    @Override
    @Transactional(readOnly = true)
    public List<DomainConfigResp> listDomainConfigs(Long tenantId, String domainCode) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "domain-config-remove", targetType = "BATCH", targetId = "", summary = "'batch remove domain configs'")
    public void deleteDomainConfigsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete domain configs");
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

        List<DomainConfig> entities = domainConfigMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream()
            .map(DomainConfig::getId)
            .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        domainConfigMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " domain_config row(s)");
    }

    private DomainConfigResp toDomainConfigResp(DomainConfig c) {
        return new DomainConfigResp(
            c.getId(), c.getTenantId(), c.getBizDomainId(),
            c.getConfigType(), c.getExtra(), c.getUpdatedAt()
        );
    }
}
