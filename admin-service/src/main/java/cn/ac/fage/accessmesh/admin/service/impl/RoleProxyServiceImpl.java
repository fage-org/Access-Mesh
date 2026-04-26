package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef.SYS_MENU;
import static cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;

/**
 * Proxy between admin-service and permission-center.
 * Translates admin-domain concepts (users, orgs, menus) into
 * permission-center primitives (roles, resources, checks).
 */
@Service
public class RoleProxyServiceImpl implements RoleProxyService {

    private static final Logger log = LoggerFactory.getLogger(RoleProxyServiceImpl.class);

    private final PermissionFeignClient permissionFeignClient;
    private final SysMenuMapper menuMapper;
    private final SysUserOrgMapper userOrgMapper;

    public RoleProxyServiceImpl(PermissionFeignClient permissionFeignClient,
                                SysMenuMapper menuMapper,
                                SysUserOrgMapper userOrgMapper) {
        this.permissionFeignClient = permissionFeignClient;
        this.menuMapper = menuMapper;
        this.userOrgMapper = userOrgMapper;
    }

    @Override
    public Long createRoleForOrg(String roleName, Long orgId, Long tenantId) {
        var result = permissionFeignClient.createRole(roleName, orgId, tenantId);
        return result.data();
    }

    @Override
    public void grantMenuToRole(Long roleId, Long menuId) {
        permissionFeignClient.grantMenuToRole(roleId, menuId);
    }

    @Override
    public void revokeMenuFromRole(Long roleId, Long menuId) {
        permissionFeignClient.revokeMenuFromRole(roleId, menuId);
    }

    @Override
    public UserInfoResp loadUserRolesAndPermissions(Long userId) {
        // Fetch user's org affiliations
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create().where(SYS_USER_ORG.USER_ID.eq(userId)).and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        // Fetch all active menus for this tenant
        List<SysMenu> menus = menuMapper.selectListByQuery(
            QueryWrapper.create().where(SYS_MENU.DELETE_FLAG.eq(0)).and(SYS_MENU.STATUS.eq(1))
        );
        List<String> perms = menus.stream()
            .filter(m -> m.getPermCode() != null)
            .map(SysMenu::getPermCode)
            .collect(Collectors.toList());

        List<UserInfoResp.RoleInfo> roles = List.of();
        List<UserInfoResp.OrgInfo> orgInfos = userOrgs.stream()
            .map(uo -> new UserInfoResp.OrgInfo(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());

        return new UserInfoResp(userId, null, null, null, null, null, null, roles, perms, orgInfos);
    }
}
