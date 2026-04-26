package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.OperationListReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserPermissionViewResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.perm.common.enums.DefaultOpCode;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;

/**
 * Proxy between admin-service and permission-center.
 * Translates admin-domain concepts (users, orgs, menus) into
 * permission-center primitives (roles, resources, checks).
 */
@Service
public class RoleProxyServiceImpl implements RoleProxyService {

    private static final Logger log = LoggerFactory.getLogger(RoleProxyServiceImpl.class);

    private static final int RESOURCE_TYPE_MENU = 1;

    /**
     * Cache: tenantId -> (opCode -> operationPermissionId).
     * Populated lazily on first grant for each tenant.
     */
    private final Map<Long, Map<String, Long>> opCodeCache = new ConcurrentHashMap<>();

    private final PermissionFeignClient permissionFeignClient;
    private final SysUserOrgMapper userOrgMapper;

    public RoleProxyServiceImpl(PermissionFeignClient permissionFeignClient,
                                SysUserOrgMapper userOrgMapper) {
        this.permissionFeignClient = permissionFeignClient;
        this.userOrgMapper = userOrgMapper;
    }

    @Override
    public Long createRoleForOrg(String roleName, Long orgId, Long tenantId) {
        RoleCreateReq req = new RoleCreateReq(
            tenantId, null, null, 2, // roleType=2 (org role)
            String.valueOf(orgId), roleName, null, null
        );
        PermResult<Long> result = permissionFeignClient.createRole(req);
        if (result == null || result.data() == null) {
            throw new IllegalStateException("Failed to create role for org: " + orgId);
        }
        return result.data();
    }

    @Override
    public void grantResourceToRole(Long tenantId, Long roleId, Long resourceId, String opCode) {
        Long operationPermissionId = resolveOperationPermissionId(tenantId, opCode);
        RoleGrantReq req = new RoleGrantReq(
            tenantId, roleId,
            List.of(new RoleGrantReq.GrantItem(resourceId, operationPermissionId, RESOURCE_TYPE_MENU, null, false, null))
        );
        PermResult<Void> result = permissionFeignClient.batchGrant(req);
        if (result == null || result.code() != 200) {
            throw new IllegalStateException("Failed to grant resource " + resourceId + " to role " + roleId);
        }
    }

    @Override
    public void revokeResourceFromRole(Long tenantId, Long roleId, Long resourceId) {
        // Need permission ID to revoke. Use a workaround: query user permissions or use resource ID.
        // The batch-revoke API expects permissionIds (role_resource_permission table IDs), not resource IDs.
        // For now, log the limitation — this requires a query to map resourceId → permission IDs.
        throw new UnsupportedOperationException(
            "revokeResourceFromRole requires mapping resourceId to permission IDs. "
            + "Consider using permission-center's revoke API with permission ID lookup."
        );
    }

    @Override
    public UserInfoResp loadUserRolesAndPermissions(Long userId) {
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create().where(SYS_USER_ORG.USER_ID.eq(userId)).and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        List<UserInfoResp.OrgInfo> orgInfos = userOrgs.stream()
            .map(uo -> new UserInfoResp.OrgInfo(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());

        // Fetch roles from permission-center via /api/user/roles
        // The userId here needs to match the externalId used during user sync.
        // For now, return empty roles until user sync is properly wired.
        List<UserInfoResp.RoleInfo> roles = List.of();

        // Fetch permissions from permission-center via /api/permission-view/user
        // Also needs tenantId — derive from first org or skip if not available
        List<String> permissions = List.of();

        return new UserInfoResp(userId, null, null, null, null, null, null, roles, permissions, orgInfos);
    }

    /**
     * Resolve opCode (e.g. "VIEW") to operationPermissionId for a given tenant.
     * Cached per tenant to avoid repeated Feign calls.
     */
    private Long resolveOperationPermissionId(Long tenantId, String opCode) {
        Map<String, Long> tenantOps = opCodeCache.computeIfAbsent(tenantId, this::loadOperations);
        Long opId = tenantOps.get(opCode);
        if (opId == null) {
            throw new IllegalStateException("Operation code '" + opCode + "' not found for tenant " + tenantId);
        }
        return opId;
    }

    private Map<String, Long> loadOperations(Long tenantId) {
        OperationListReq req = new OperationListReq(tenantId, RESOURCE_TYPE_MENU);
        PermResult<List<OperationPermissionResp>> result = permissionFeignClient.listOperations(req);
        if (result == null || result.data() == null) {
            throw new IllegalStateException("Failed to list operations for tenant " + tenantId);
        }
        return result.data().stream()
            .collect(Collectors.toMap(OperationPermissionResp::code, OperationPermissionResp::id));
    }
}
