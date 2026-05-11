package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManageServiceImplTest {

    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private AbstractRoleDomainService abstractRoleDomainService;
    @Mock private PermCacheDomainService permCacheDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private OperationLogDomainService operationLogDomainService;
    @Mock private PermissionChangeDomainService permissionChangeDomainService;
    @Mock private AuthorizationService authorizationService;
    @Mock private PermQueryEngine engine;

    private RoleManageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleManageServiceImpl(
            abstractRoleMapper,
            abstractRoleDomainService,
            permCacheDomainService,
            typeResolutionService,
            domainClassifyService,
            new ObjectMapper(),
            operationLogDomainService,
            permissionChangeDomainService,
            authorizationService,
            engine
        );
    }

    @Test
    void shouldShortCircuitRoleTreeWhenDomainDoesNotCoverRoleType() {
        when(domainClassifyService.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", ResourceTypeCode.ROLE))
            .thenReturn(false);

        assertEquals(List.of(), service.getRoleTree(1L, "OPS"));
        verifyNoInteractions(abstractRoleMapper);
    }
}