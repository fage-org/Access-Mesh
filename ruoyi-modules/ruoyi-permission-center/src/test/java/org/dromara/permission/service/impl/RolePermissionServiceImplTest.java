package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class RolePermissionServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long ROLE_ID = 200L;
    private static final Long RESOURCE_ID = 300L;
    private static final Long OP_ID = 400L;

    @Mock
    private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private PcResourceEntityMapper resourceEntityMapper;
    @Mock
    private PcOperationPermissionMapper operationPermissionMapper;

    @InjectMocks
    private RolePermissionServiceImpl service;

    @Test
    void list_normal_returnsWithResourceNameAndOperationName() {
        RolePermissionListReq req = new RolePermissionListReq();
        req.setTenantId(TENANT);
        req.setAbstractRoleId(ROLE_ID);

        PcRoleResourcePermission rrp = buildPermission(1L, TENANT, ROLE_ID, RESOURCE_ID, OP_ID);
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rrp));

        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(RESOURCE_ID);
        resource.setName("用户管理");
        resource.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(resourceEntityMapper.selectBatchIds(anyCollection())).thenReturn(List.of(resource));

        PcOperationPermission op = new PcOperationPermission();
        op.setId(OP_ID);
        op.setName("查看");
        op.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(operationPermissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(op));

        List<RolePermissionVo> result = service.list(req);

        assertEquals(1, result.size());
        assertEquals("用户管理", result.get(0).getResourceName());
        assertEquals("查看", result.get(0).getOperationName());
    }

    @Test
    void list_deletedResource_resourceNameIsNull() {
        RolePermissionListReq req = new RolePermissionListReq();
        req.setTenantId(TENANT);
        req.setAbstractRoleId(ROLE_ID);

        PcRoleResourcePermission rrp = buildPermission(1L, TENANT, ROLE_ID, RESOURCE_ID, OP_ID);
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rrp));

        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(RESOURCE_ID);
        resource.setName("用户管理");
        resource.setDeleteFlag(999L);
        when(resourceEntityMapper.selectBatchIds(anyCollection())).thenReturn(List.of(resource));

        PcOperationPermission op = new PcOperationPermission();
        op.setId(OP_ID);
        op.setName("查看");
        op.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(operationPermissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(op));

        List<RolePermissionVo> result = service.list(req);

        assertEquals(1, result.size());
        assertNull(result.get(0).getResourceName());
        assertEquals("查看", result.get(0).getOperationName());
    }

    private PcRoleResourcePermission buildPermission(Long id, Long tenantId, Long roleId,
                                                     Long resourceId, Long opId) {
        PcRoleResourcePermission rrp = new PcRoleResourcePermission();
        rrp.setId(id);
        rrp.setTenantId(tenantId);
        rrp.setAbstractRoleId(roleId);
        rrp.setResourceEntityId(resourceId);
        rrp.setOperationPermissionId(opId);
        rrp.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return rrp;
    }
}
