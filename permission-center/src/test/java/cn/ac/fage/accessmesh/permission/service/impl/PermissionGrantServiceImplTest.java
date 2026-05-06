package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceDependencyDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGrantServiceImplTest {

    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private DomainConfigMapper domainConfigMapper;
    @Mock private PermissionConditionMapper permissionConditionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private RolePermissionDomainService rolePermissionDomainService;
    @Mock private PermissionVersionDomainService permissionVersionDomainService;
    @Mock private PermissionChangeDomainService permissionChangeDomainService;
    @Mock private OperationLogDomainService operationLogDomainService;
    @Mock private UserRoleDomainService userRoleDomainService;
    @Mock private ResourceDependencyDomainService resourceDependencyDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private AuthorizationService authorizationService;
    @Mock private OperationPermissionDomainService operationPermissionDomainService;
    @Mock private AbstractRoleDomainService abstractRoleDomainService;
    @Mock private PermQueryEngine engine;

    private PermissionGrantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantServiceImpl(
            abstractRoleMapper, resourceEntityMapper, operationPermissionMapper, domainConfigMapper, permissionConditionMapper,
            rolePermMapper, rolePermissionDomainService, permissionVersionDomainService, permissionChangeDomainService,
            operationLogDomainService, userRoleDomainService, resourceDependencyDomainService, typeResolutionService,
            authorizationService, operationPermissionDomainService, abstractRoleDomainService, engine
        );
    }

    @Test
    @Disabled("Requires OperatorContext mock setup")
    void shouldRejectSubPermByExactCodeMatch() {
        RoleResourcePermission parent = new RoleResourcePermission();
        parent.setId(10L);
        parent.setTenantId(1L);
        parent.setDeleteFlag(0L);
        parent.setAbstractRoleId(20L);
        parent.setResourceEntityId(100L);
        when(rolePermMapper.selectOneById(10L)).thenReturn(parent);

        ResourceEntity parentRes = new ResourceEntity();
        parentRes.setId(100L);
        parentRes.setBizDomainId(99L);
        when(resourceEntityMapper.selectOneById(100L)).thenReturn(parentRes);

        DomainConfig config = new DomainConfig();
        config.setExtra("USER,DEPT");
        when(domainConfigMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(config);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "SER")).thenReturn(3);

        RolePermissionAddChildReq req = new RolePermissionAddChildReq(
            10L, List.of(new RolePermissionAddChildReq.ChildItem("SER", "x", "default", "VIEW", false, null, null))
        );

        assertThrows(BizException.class, () -> service.addChildren(1L, req));
    }

    @Test
    @Disabled("Requires OperatorContext mock setup")
    void shouldRequireResourceCodeWhenScopeAllFalse() {
        RoleResourcePermission parent = new RoleResourcePermission();
        parent.setId(10L);
        parent.setTenantId(1L);
        parent.setDeleteFlag(0L);
        parent.setAbstractRoleId(20L);
        when(rolePermMapper.selectOneById(10L)).thenReturn(parent);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "DATA")).thenReturn(4);
        when(typeResolutionService.resolveOperationId(1L, "DATA_READ", "DATA")).thenReturn(11L);

        RolePermissionAddChildReq req = new RolePermissionAddChildReq(
            10L, List.of(new RolePermissionAddChildReq.ChildItem("DATA", null, "default", "DATA_READ", false, null, null))
        );

        assertThrows(BizException.class, () -> service.addChildren(1L, req));
    }
}