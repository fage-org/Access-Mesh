package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceDependencyDomainServiceImplTest {

    @Mock
    private ResourceDependencyMapper dependencyMapper;
    @Mock
    private RoleResourcePermissionMapper rolePermMapper;
    @Mock
    private ResourceEntityMapper resourceEntityMapper;
    @Mock
    private OperationPermissionMapper operationPermissionMapper;
    @Mock
    private PermissionVersionDomainService permissionVersionDomainService;

    private ResourceDependencyDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceDependencyDomainServiceImpl(
            dependencyMapper,
            rolePermMapper,
            resourceEntityMapper,
            operationPermissionMapper,
            permissionVersionDomainService
        );
    }

    @Test
    void autoGrantForInsertShouldPreferExactBinaryBitForRequiredOperation() {
        RoleResourcePermission sourcePerm = new RoleResourcePermission();
        sourcePerm.setTenantId(1L);
        sourcePerm.setAbstractRoleId(20L);
        sourcePerm.setResourceEntityId(100L);
        sourcePerm.setResourceType(1);
        sourcePerm.setGrantedBits(8L);

        ResourceDependency dependency = new ResourceDependency();
        dependency.setId(30L);
        dependency.setResourceEntityId(100L);
        dependency.setDependsOnResourceEntityId(200L);
        dependency.setRequiredOperationBits(1L);
        dependency.setAutoGrant(true);

        OperationPermission sourceManage = operation(11L, 1, "MANAGE", 8L, 1L);
        OperationPermission targetManage = operation(21L, 2, "MANAGE", 8L, 1L);
        OperationPermission targetView = operation(22L, 2, "VIEW", 1L, 0L);

        ResourceEntity targetResource = new ResourceEntity();
        targetResource.setId(200L);
        targetResource.setResourceType(2);

        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(sourceManage));
        when(dependencyMapper.selectAutoGrantByResourceIds(1L, Set.of(100L))).thenReturn(List.of(dependency));
        when(rolePermMapper.selectExistingAutoGrants(1L, 20L, Set.of(200L), Set.of(30L))).thenReturn(List.of());
        when(resourceEntityMapper.selectValidById(1L, 200L)).thenReturn(targetResource);
        when(resourceEntityMapper.selectOneById(200L)).thenReturn(targetResource);
        when(operationPermissionMapper.selectByEffectiveBitsMatch(1L, 2, 1L))
            .thenReturn(List.of(targetManage, targetView));

        List<RoleResourcePermission> autoGranted = service.autoGrantForInsert(1L, 20L, List.of(sourcePerm));

        assertEquals(1, autoGranted.size());
        assertEquals(1L, autoGranted.get(0).getGrantedBits());
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