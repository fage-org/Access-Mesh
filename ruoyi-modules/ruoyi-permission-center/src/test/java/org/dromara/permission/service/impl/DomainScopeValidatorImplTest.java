package org.dromara.permission.service.impl;

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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class DomainScopeValidatorImplTest {

    @Mock
    private PcDomainScopeConfigMapper domainScopeConfigMapper;
    @Mock
    private PcDomainRelationConfigMapper domainRelationConfigMapper;

    @InjectMocks
    private DomainScopeValidatorImpl validator;

    @Test
    void validateGrantScope_usesRoleResourceOnly() {
        when(domainScopeConfigMapper.selectList(any())).thenReturn(List.of(
            scope("ROLE_TYPE", 1L),
            scope("RESOURCE_TYPE", 2L),
            scope("OPERATION", 30L)
        ));
        when(domainRelationConfigMapper.selectList(any())).thenReturn(List.of(relation("ROLE_RESOURCE", 1L, 2L)));

        assertDoesNotThrow(() -> validator.validateGrantScope(1L, 10L, role(20L, 10L, 1), resource(30L, 10L, 2), operation(30L)));
    }

    @Test
    void validateGrantScope_roleResourceMismatch_throws() {
        when(domainScopeConfigMapper.selectList(any())).thenReturn(List.of(
            scope("ROLE_TYPE", 1L),
            scope("RESOURCE_TYPE", 2L),
            scope("OPERATION", 30L)
        ));
        when(domainRelationConfigMapper.selectList(any())).thenReturn(List.of(relation("ROLE_RESOURCE", 1L, 9L)));

        PermissionServiceException ex = assertThrows(PermissionServiceException.class,
            () -> validator.validateGrantScope(1L, 10L, role(20L, 10L, 1), resource(30L, 10L, 2), operation(30L)));

        assertEquals(PermissionErrorCode.DOMAIN_SCOPE_NOT_ALLOWED, ex.getErrorCode());
    }

    private PcDomainScopeConfig scope(String scopeType, Long scopeRefId) {
        PcDomainScopeConfig config = new PcDomainScopeConfig();
        config.setTenantId(1L);
        config.setBizDomainId(10L);
        config.setScopeType(scopeType);
        config.setScopeRefId(scopeRefId);
        config.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return config;
    }

    private PcDomainRelationConfig relation(String relationType, Long leftRefId, Long rightRefId) {
        PcDomainRelationConfig config = new PcDomainRelationConfig();
        config.setTenantId(1L);
        config.setBizDomainId(10L);
        config.setRelationType(relationType);
        config.setLeftRefId(leftRefId);
        config.setRightRefId(rightRefId);
        config.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return config;
    }

    private PcAbstractRole role(Long id, Long bizDomainId, Integer roleType) {
        PcAbstractRole role = new PcAbstractRole();
        role.setId(id);
        role.setTenantId(1L);
        role.setBizDomainId(bizDomainId);
        role.setRoleType(roleType);
        role.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return role;
    }

    private PcResourceEntity resource(Long id, Long bizDomainId, Integer resourceType) {
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(id);
        resource.setTenantId(1L);
        resource.setBizDomainId(bizDomainId);
        resource.setResourceType(resourceType);
        resource.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return resource;
    }

    private PcOperationPermission operation(Long id) {
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(id);
        operation.setTenantId(1L);
        operation.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return operation;
    }
}
