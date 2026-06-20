package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionGrantDomainService.GrantCheckKey;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGrantDomainServiceImplTest {

    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private SubjectDomainService subjectDomainService;
    @Mock
    private OperationPermissionMapper operationPermissionMapper;
    @Mock
    private RoleResourcePermissionMapper roleResourcePermissionMapper;

    private PermissionGrantDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantDomainServiceImpl(
            typeResolutionService,
            subjectDomainService,
            operationPermissionMapper,
            roleResourcePermissionMapper
        );
    }

    @Test
    void canGrantPermissionShouldAllowInheritedGrantCoverage() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission manageOp = operation(102L, 1, "MANAGE", 8L, 1L);

        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(viewOp, manageOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, null), 100L));

        RoleResourcePermission perm = new RoleResourcePermission();
        perm.setAbstractRoleId(20L);
        perm.setResourceEntityId(100L);
        perm.setResourceType(1);
        perm.setGrantedBits(8L);
        perm.setCanGrant(true);
        perm.setScopeAll(false);

        when(roleResourcePermissionMapper.selectValidByRoleIds(1L, Set.of(20L))).thenReturn(List.of(perm));

        boolean allowed = service.canGrantPermission(1L, 10L, "MENU", "sys:user", "VIEW", false, null);

        assertTrue(allowed);
    }

    @Test
    void shouldSoftDeleteAndCascadeWithoutVersionIncrement() {
        RoleResourcePermission perm = new RoleResourcePermission();
        perm.setId(501L);
        perm.setAbstractRoleId(20L);
        perm.setResourceEntityId(200L);
        perm.setDeleteFlag(0L);

        when(roleResourcePermissionMapper.selectValidByIds(1L, 20L, List.of(501L)))
            .thenReturn(List.of(perm));

        service.revokePermissions(1L, 20L, List.of(501L));

        verify(roleResourcePermissionMapper).softDeleteBatch(eq(1L), eq(List.of(501L)), any());
        verify(roleResourcePermissionMapper).cascadeSoftDeleteChildren(eq(1L), eq(List.of(501L)), any());
        // T-PERM-003：permissionVersionDomainService 已从本服务移除，无 version 增量可校验。
        // 此处仅验证软删 + 级联子项软删两条核心行为。
    }

    private OperationPermission operation(Long id, Integer resourceType, String code, Long binaryBit, Long inheritMask) {
        OperationPermission operationPermission = new OperationPermission();
        operationPermission.setId(id);
        operationPermission.setResourceType(resourceType);
        operationPermission.setCode(code);
        operationPermission.setBinaryBit(binaryBit);
        operationPermission.setInheritMask(inheritMask);
        operationPermission.setDeleteFlag(0L);
        return operationPermission;
    }
}
