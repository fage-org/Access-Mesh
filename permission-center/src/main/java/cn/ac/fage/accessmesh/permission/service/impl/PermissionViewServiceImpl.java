package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp.RoleGrantInfo;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp.PermissionItem;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp.OperationView;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp.ResourcePermissionView;
import cn.ac.fage.accessmesh.permission.entity.*;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class PermissionViewServiceImpl implements PermissionViewService {

    private final AbstractUserMapper abstractUserMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final UserRoleDomainService userRoleDomainService;

    public PermissionViewServiceImpl(AbstractUserMapper abstractUserMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     ResourceEntityMapper resourceEntityMapper,
                                     OperationPermissionMapper operationPermissionMapper,
                                     RoleResourcePermissionMapper rolePermMapper,
                                     UserRoleDomainService userRoleDomainService) {
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.rolePermMapper = rolePermMapper;
        this.userRoleDomainService = userRoleDomainService;
    }

    @Override
    public UserPermissionViewResp getUserPermissions(Long tenantId, Long userId) {
        AbstractUser user = abstractUserMapper.selectOneById(userId);
        if (user == null) return null;

        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, null);
        if (roleIds.isEmpty()) {
            return new UserPermissionViewResp(userId, user.getName(), tenantId, Collections.emptyList());
        }

        // Get all role-resource-permissions for user's roles
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .where(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Group by resource
        Map<Long, List<RoleResourcePermission>> byResource = perms.stream()
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));

        List<ResourcePermissionView> resourceViews = new ArrayList<>();
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : byResource.entrySet()) {
            ResourceEntity resource = resourceEntityMapper.selectOneById(entry.getKey());
            if (resource == null) continue;

            List<OperationView> operations = entry.getValue().stream()
                .map(p -> {
                    OperationPermission op = operationPermissionMapper.selectOneById(p.getOperationPermissionId());
                    return new OperationView(
                        p.getOperationPermissionId(),
                        op != null ? op.getCode() : null,
                        op != null ? op.getName() : null,
                        p.getConditionId(),
                        p.getGrantSource()
                    );
                })
                .collect(Collectors.toList());

            // Collect role names
            Set<Long> applicableRoleIds = entry.getValue().stream()
                .map(RoleResourcePermission::getAbstractRoleId).collect(Collectors.toSet());
            List<String> roleNames = applicableRoleIds.stream()
                .map(abstractRoleMapper::selectOneById)
                .filter(Objects::nonNull)
                .map(AbstractRole::getName)
                .collect(Collectors.toList());

            resourceViews.add(new ResourcePermissionView(
                resource.getId(), resource.getCode(), resource.getName(),
                resource.getResourceType(), operations, roleNames
            ));
        }

        return new UserPermissionViewResp(userId, user.getName(), tenantId, resourceViews);
    }

    @Override
    public ResourcePermissionViewResp getResourcePermissions(Long tenantId, Long resourceEntityId) {
        ResourceEntity resource = resourceEntityMapper.selectOneById(resourceEntityId);
        if (resource == null) return null;

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        Map<Long, List<RoleResourcePermission>> byRole = perms.stream()
            .collect(Collectors.groupingBy(RoleResourcePermission::getAbstractRoleId));

        List<RoleGrantInfo> roleInfos = new ArrayList<>();
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : byRole.entrySet()) {
            AbstractRole role = abstractRoleMapper.selectOneById(entry.getKey());
            List<String> opCodes = entry.getValue().stream()
                .map(p -> {
                    OperationPermission op = operationPermissionMapper.selectOneById(p.getOperationPermissionId());
                    return op != null ? op.getCode() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            roleInfos.add(new RoleGrantInfo(
                entry.getKey(),
                role != null ? role.getName() : null,
                role != null ? role.getRoleType() : null,
                opCodes,
                entry.getValue().get(0).getGrantSource()
            ));
        }

        return new ResourcePermissionViewResp(
            resourceEntityId, resource.getCode(), resource.getName(), roleInfos
        );
    }

    @Override
    public RolePermissionViewResp getRolePermissions(Long tenantId, Long roleId, boolean expandSub) {
        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        if (role == null) return null;

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        List<PermissionItem> items = perms.stream()
            .map(p -> {
                ResourceEntity resource = resourceEntityMapper.selectOneById(p.getResourceEntityId());
                OperationPermission op = operationPermissionMapper.selectOneById(p.getOperationPermissionId());
                return new PermissionItem(
                    p.getId(),
                    p.getResourceEntityId(),
                    resource != null ? resource.getCode() : null,
                    resource != null ? resource.getName() : null,
                    p.getResourceType(),
                    p.getOperationPermissionId(),
                    op != null ? op.getCode() : null,
                    op != null ? op.getName() : null,
                    p.getDependOn(),
                    p.getConditionId(),
                    p.getCanManage(),
                    p.getGrantSource()
                );
            })
            .collect(Collectors.toList());

        return new RolePermissionViewResp(roleId, role.getName(), role.getRoleType(), items);
    }
}
