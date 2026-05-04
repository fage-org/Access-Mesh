package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionVersionQueryReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionVersionResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionVersionService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

@Service
public class PermissionVersionServiceImpl implements PermissionVersionService {

    private final TypeResolutionService typeResolutionService;
    private final AbstractRoleMapper abstractRoleMapper;
    private final RoleResourcePermissionMapper rolePermMapper;

    public PermissionVersionServiceImpl(TypeResolutionService typeResolutionService,
                                        AbstractRoleMapper abstractRoleMapper,
                                        RoleResourcePermissionMapper rolePermMapper) {
        this.typeResolutionService = typeResolutionService;
        this.abstractRoleMapper = abstractRoleMapper;
        this.rolePermMapper = rolePermMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionVersionResp queryVersion(Long tenantId, PermissionVersionQueryReq req) {
        Long roleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId == null) {
            return new PermissionVersionResp(null, req.roleTypeCode(), req.roleExternalId(), 0L);
        }

        AbstractRole role = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(roleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        String roleTypeCode = req.roleTypeCode();
        String roleExternalId = req.roleExternalId();

        // Compute version as max(updatedAt epoch millis) of active permissions for this role.
        // Fallback to 0 if no permissions.
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        long version = perms.stream()
            .filter(p -> p.getUpdatedAt() != null)
            .mapToLong(p -> p.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC))
            .max()
            .orElse(0L);

        return new PermissionVersionResp(roleId, roleTypeCode, roleExternalId, version);
    }
}
