package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    @Override
    public PermCheckResp checkPermission(PermCheckReq req) {
        return PermCheckResp.allow();
    }

    @Override
    public Long createRole(String roleName, Long orgId, Long tenantId) {
        // TODO: persist role in permission_db
        log.info("createRole: role={}, org={}, tenant={}", roleName, orgId, tenantId);
        return 0L;
    }

    @Override
    public void grantMenuToRole(Long roleId, Long menuId) {
        log.info("grantMenuToRole: role={}, menu={}", roleId, menuId);
    }

    @Override
    public void revokeMenuFromRole(Long roleId, Long menuId) {
        log.info("revokeMenuFromRole: role={}, menu={}", roleId, menuId);
    }

    @Override
    public List<String> getUserPermissions(Long userId) {
        // TODO: query role-permission-resource graph
        return List.of();
    }
}
