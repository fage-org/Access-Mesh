package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcDomainScopeBinding;
import org.dromara.permission.domain.PcDomainRelationConfig;
import org.dromara.permission.domain.PcDomainScopeConfig;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.mapper.PcDomainScopeBindingMapper;
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
    private final PcDomainScopeBindingMapper domainScopeBindingMapper;

    @Override
    public Long resolveGrantBizDomainId(Long requestedBizDomainId, PcAbstractRole role, PcResourceEntity resource) {
        Long roleBizDomainId = role == null ? null : role.getBizDomainId();
        Long resourceBizDomainId = resource == null ? null : resource.getBizDomainId();
        if (requestedBizDomainId != null) {
            validateEntityDomain(requestedBizDomainId, roleBizDomainId, "role");
            validateEntityDomain(requestedBizDomainId, resourceBizDomainId, "resource");
            return requestedBizDomainId;
        }
        if (roleBizDomainId != null && resourceBizDomainId != null && !roleBizDomainId.equals(resourceBizDomainId)) {
            throw new PermissionServiceException(PermissionErrorCode.DOMAIN_SCOPE_NOT_ALLOWED,
                "role/resource domain mismatch");
        }
        return roleBizDomainId != null ? roleBizDomainId : resourceBizDomainId;
    }

    @Override
    public void validateGrantScope(Long tenantId, Long bizDomainId, PcAbstractRole role, PcResourceEntity resource, PcOperationPermission operation) {
        Long resolvedBizDomainId = resolveGrantBizDomainId(bizDomainId, role, resource);
        if (resolvedBizDomainId == null) {
            return;
        }
        validateEntityAccess(tenantId, resolvedBizDomainId, role.getBizDomainId(), "ROLE", role.getId(), "role");
        validateEntityAccess(tenantId, resolvedBizDomainId, resource.getBizDomainId(), "RESOURCE", resource.getId(), "resource");
        validateEntityAccess(tenantId, resolvedBizDomainId, null, "OPERATION", operation.getId(), "operation");
        validateScopeConfig(tenantId, resolvedBizDomainId, "ROLE_TYPE", Long.valueOf(role.getRoleType()));
        validateScopeConfig(tenantId, resolvedBizDomainId, "RESOURCE_TYPE",
            resource.getResourceType() == null ? null : Long.valueOf(resource.getResourceType()));
        validateScopeConfig(tenantId, resolvedBizDomainId, "OPERATION", operation.getId());
        validateRelationConfig(tenantId, resolvedBizDomainId, "ROLE_RESOURCE", Long.valueOf(role.getRoleType()),
            resource.getResourceType() == null ? null : Long.valueOf(resource.getResourceType()));
    }

    private void validateEntityDomain(Long expectedBizDomainId, Long entityBizDomainId, String entityName) {
        if (entityBizDomainId != null && !expectedBizDomainId.equals(entityBizDomainId)) {
            throw new PermissionServiceException(PermissionErrorCode.DOMAIN_SCOPE_NOT_ALLOWED,
                entityName + " not in bizDomainId=" + expectedBizDomainId);
        }
    }

    private void validateEntityAccess(Long tenantId, Long bizDomainId, Long entityBizDomainId,
                                      String boundType, Long boundEntityId, String entityName) {
        validateEntityDomain(bizDomainId, entityBizDomainId, entityName);
        if (entityBizDomainId == null && !isBoundToDomain(tenantId, bizDomainId, boundType, boundEntityId)) {
            throw new PermissionServiceException(PermissionErrorCode.DOMAIN_SCOPE_NOT_ALLOWED,
                entityName + " not bound to bizDomainId=" + bizDomainId);
        }
    }

    private boolean isBoundToDomain(Long tenantId, Long bizDomainId, String boundType, Long boundEntityId) {
        if (boundEntityId == null) {
            return false;
        }
        PcDomainScopeBinding binding = domainScopeBindingMapper.selectOne(new LambdaQueryWrapper<PcDomainScopeBinding>()
            .eq(PcDomainScopeBinding::getTenantId, tenantId)
            .eq(PcDomainScopeBinding::getBizDomainId, bizDomainId)
            .eq(PcDomainScopeBinding::getBoundType, boundType)
            .eq(PcDomainScopeBinding::getBoundEntityId, boundEntityId)
            .eq(PcDomainScopeBinding::getDeleteFlag, PermissionConstants.NOT_DELETED));
        return binding != null;
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
