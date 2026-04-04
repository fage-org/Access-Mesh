package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcDomainRelationConfig;
import org.dromara.permission.domain.PcDomainScopeConfig;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.mapper.PcDomainRelationConfigMapper;
import org.dromara.permission.mapper.PcDomainScopeConfigMapper;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.DomainScopeValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DomainScopeValidatorImpl implements DomainScopeValidator {

    private final PcDomainScopeConfigMapper domainScopeConfigMapper;
    private final PcDomainRelationConfigMapper domainRelationConfigMapper;

    @Override
    public void validateGrantScope(Long tenantId, Long bizDomainId, PcAbstractRole role, PcResourceEntity resource, PcOperationPermission operation) {
        if (bizDomainId == null) {
            return;
        }
        validateEntityDomain(bizDomainId, role.getBizDomainId(), "role");
        validateEntityDomain(bizDomainId, resource.getBizDomainId(), "resource");
        validateScopeConfig(tenantId, bizDomainId, "ROLE_TYPE", Long.valueOf(role.getRoleType()));
        validateScopeConfig(tenantId, bizDomainId, "RESOURCE_TYPE",
            resource.getResourceType() == null ? null : Long.valueOf(resource.getResourceType()));
        validateScopeConfig(tenantId, bizDomainId, "OPERATION", operation.getId());
        validateRelationConfig(tenantId, bizDomainId, "ROLE_RESOURCE", Long.valueOf(role.getRoleType()),
            resource.getResourceType() == null ? null : Long.valueOf(resource.getResourceType()));
    }

    private void validateEntityDomain(Long expectedBizDomainId, Long entityBizDomainId, String entityName) {
        if (entityBizDomainId != null && !expectedBizDomainId.equals(entityBizDomainId)) {
            throw new PermissionServiceException(PermissionErrorCode.DOMAIN_SCOPE_NOT_ALLOWED,
                entityName + " not in bizDomainId=" + expectedBizDomainId);
        }
    }

    private void validateScopeConfig(Long tenantId, Long bizDomainId, String scopeType, Long scopeRefId) {
        if (scopeRefId == null) {
            return;
        }
        List<PcDomainScopeConfig> configs = domainScopeConfigMapper.selectList(new LambdaQueryWrapper<PcDomainScopeConfig>()
            .eq(PcDomainScopeConfig::getTenantId, tenantId)
            .eq(PcDomainScopeConfig::getBizDomainId, bizDomainId)
            .eq(PcDomainScopeConfig::getScopeType, scopeType)
            .eq(PcDomainScopeConfig::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (!configs.isEmpty() && configs.stream().noneMatch(config -> scopeRefId.equals(config.getScopeRefId()))) {
            throw new PermissionServiceException(PermissionErrorCode.DOMAIN_SCOPE_NOT_ALLOWED,
                scopeType + "=" + scopeRefId);
        }
    }

    private void validateRelationConfig(Long tenantId, Long bizDomainId, String relationType, Long leftRefId, Long rightRefId) {
        if (leftRefId == null || rightRefId == null) {
            return;
        }
        List<PcDomainRelationConfig> configs = domainRelationConfigMapper.selectList(new LambdaQueryWrapper<PcDomainRelationConfig>()
            .eq(PcDomainRelationConfig::getTenantId, tenantId)
            .eq(PcDomainRelationConfig::getBizDomainId, bizDomainId)
            .eq(PcDomainRelationConfig::getRelationType, relationType)
            .eq(PcDomainRelationConfig::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (!configs.isEmpty() && configs.stream().noneMatch(config ->
            leftRefId.equals(config.getLeftRefId()) && rightRefId.equals(config.getRightRefId()))) {
            throw new PermissionServiceException(PermissionErrorCode.DOMAIN_SCOPE_NOT_ALLOWED,
                relationType + "(" + leftRefId + "," + rightRefId + ")");
        }
    }
}
