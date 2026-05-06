package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.cache.OperationCodeCacheManager;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.OperationListReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserPermissionViewResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.perm.common.enums.DefaultOpCode;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Proxy between admin-service and permission-center.
 * Translates admin-domain concepts (users, orgs, menus) into
 * permission-center primitives (roles, resources, checks).
 */
@Service
public class RoleProxyServiceImpl implements RoleProxyService {

    private static final Logger log = LoggerFactory.getLogger(RoleProxyServiceImpl.class);

    private static final int RESOURCE_TYPE_MENU = 1;

    private final PermissionFeignClient permissionFeignClient;
    private final SysUserOrgMapper userOrgMapper;
    private final MenuDomainService menuDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final OperationCodeCacheManager opCodeCacheManager;

    public RoleProxyServiceImpl(PermissionFeignClient permissionFeignClient,
                                SysUserOrgMapper userOrgMapper,
                                MenuDomainService menuDomainService,
                                AdminPermissionValidator permissionValidator,
                                OperationCodeCacheManager opCodeCacheManager) {
        this.permissionFeignClient = permissionFeignClient;
        this.userOrgMapper = userOrgMapper;
        this.menuDomainService = menuDomainService;
        this.permissionValidator = permissionValidator;
        this.opCodeCacheManager = opCodeCacheManager;
    }

    @Override
    public Long createRoleForOrg(String roleName, Long orgId, Long tenantId) {
        // Permission check - type-level CREATE on ADMIN_ROLE
        permissionValidator.checkTypeLevel(AdminResourceType.ROLE, AdminOperationCode.CREATE);

        RoleCreateReq req = new RoleCreateReq(
            null, // bizDomainId
            null, // parentId
            "ORG_ROLE", // roleTypeCode
            String.valueOf(orgId), // externalId
            roleName,
            null, // sortOrder
            null // extra
        );
        PermResult<Map<String, Object>> result = permissionFeignClient.createRole(req);
        if (result == null || result.data() == null) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to create role for org: " + orgId);
        }
        Object idObj = result.data().get("id");
        return idObj != null ? Long.valueOf(idObj.toString()) : null;
    }

    @Override
    public void grantMenuToRole(Long tenantId, Long roleId, Long menuId, String opCode) {
        // Permission check - instance-level GRANT on ADMIN_ROLE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ROLE,
            String.valueOf(roleId),
            AdminOperationCode.GRANT
        );

        // 获取菜单信息（通过 DomainService，符合分层规范）
        SysMenu menu = menuDomainService.selectValidById(tenantId, menuId);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        Long resourceId = menu.getPermResourceId();
        if (resourceId == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), "菜单尚未同步到权限中心");
        }

        // 调用 permission-center 授权
        // 使用正确的 RoleGrantReq 结构
        RoleGrantReq.GrantAddItem addItem = new RoleGrantReq.GrantAddItem(
            "MENU",           // resourceTypeCode
            String.valueOf(menuId), // resourceCode
            "ID",             // codeType
            opCode,           // operationCode
            Boolean.FALSE,    // scopeAll
            Boolean.FALSE,    // canGrant
            null              // conditionCode
        );

        RoleGrantReq req = new RoleGrantReq(
            null,             // domainCode
            "ORG_ROLE",       // roleTypeCode
            String.valueOf(roleId), // roleExternalId
            List.of(addItem), // add
            List.of(),        // update
            List.of()         // remove
        );

        try {
            PermResult<Map<String, Object>> result = permissionFeignClient.batchGrant(req);
            if (result == null || result.code() != 200) {
                log.warn("Failed to grant menu to role: roleId={}, menuId={}, opCode={}", roleId, menuId, opCode);
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to grant menu permission");
            }
            log.info("Granted menu to role: roleId={}, menuId={}, opCode={}", roleId, menuId, opCode);
        } catch (Exception e) {
            log.error("Error granting menu to role: roleId={}, menuId={}, error={}", roleId, menuId, e.getMessage());
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to grant menu permission: " + e.getMessage());
        }
    }

    @Override
    public void revokeMenuFromRole(Long tenantId, Long roleId, Long menuId) {
        // Permission check - instance-level REVOKE on ADMIN_ROLE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ROLE,
            String.valueOf(roleId),
            AdminOperationCode.REVOKE
        );

        // 获取菜单信息（通过 DomainService，符合分层规范）
        SysMenu menu = menuDomainService.selectValidById(tenantId, menuId);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        Long resourceId = menu.getPermResourceId();
        if (resourceId == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), "菜单尚未同步到权限中心");
        }

        // 调用 permission-center 撤销权限
        try {
            // 1. 查询角色的现有权限
            UserPermissionViewReq viewReq = new UserPermissionViewReq(
                "ROLE",            // targetType
                "ORG_ROLE",        // subjectTypeCode
                String.valueOf(roleId), // subjectExternalId
                null,              // domainCode
                null,              // roleTypeCode
                null,              // roleExternalId
                List.of("MENU"),   // resourceTypeCodes
                null,              // operationCodes
                null,              // resourceKeyword
                null,              // sourceRoleExternalId
                Boolean.FALSE,     // includeScopes
                Boolean.FALSE,     // includeApiResources
                Boolean.FALSE,     // includeSourceRoles
                null,              // sourceRoleLimit
                1,                 // pageNum
                100                // pageSize
            );

            PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>> viewResult = 
                permissionFeignClient.getEffectivePermissions(viewReq);
            
            if (viewResult == null || viewResult.data() == null) {
                log.warn("Failed to query permissions for role: roleId={}, menuId={}", roleId, menuId);
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to query existing permissions");
            }

            // 2. 从结果中过滤出匹配 menuId 的权限项，并提取 permissionIds
            List<Long> permissionIds = new ArrayList<>();
            String targetResourceCode = String.valueOf(menuId);
            
            PermissionEffectivePermissionsResp<Map<String, Object>> respData = viewResult.data();
            if (respData.items() != null) {
                for (Map<String, Object> item : respData.items()) {
                    Object resourceCodeObj = item.get("resourceCode");
                    if (resourceCodeObj != null && targetResourceCode.equals(resourceCodeObj.toString())) {
                        Object permissionIdsObj = item.get("matchedPermissionIds");
                        if (permissionIdsObj instanceof List<?> ids) {
                            for (Object idObj : ids) {
                                if (idObj != null) {
                                    permissionIds.add(Long.valueOf(idObj.toString()));
                                }
                            }
                        }
                    }
                }
            }

            // 3. 如果没有权限需要撤销，直接返回
            if (permissionIds.isEmpty()) {
                log.info("No permissions found to revoke for menu: roleId={}, menuId={}", roleId, menuId);
                return;
            }

            // 4. 调用 permission-center 的批量撤销接口
            BatchRevokeReq revokeReq = new BatchRevokeReq(
                null,                      // domainCode
                "ORG_ROLE",                // roleTypeCode
                String.valueOf(roleId),    // roleExternalId
                permissionIds              // permissionIds
            );

            PermResult<Void> result = permissionFeignClient.batchRevoke(revokeReq);
            if (result == null || result.code() != 200) {
                log.warn("Failed to revoke menu from role: roleId={}, menuId={}, permissionCount={}", 
                    roleId, menuId, permissionIds.size());
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to revoke menu permission");
            }
            
            log.info("Revoked menu from role: roleId={}, menuId={}, permissionCount={}", 
                roleId, menuId, permissionIds.size());

        } catch (BizException | SystemException e) {
            // 重新抛出已知异常
            throw e;
        } catch (Exception e) {
            log.error("Error revoking menu from role: roleId={}, menuId={}, error={}", roleId, menuId, e.getMessage());
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to revoke menu permission: " + e.getMessage());
        }
    }

    @Override
    public UserInfoResp loadUserRolesAndPermissions(Long userId) {
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create().where(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId)).and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
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
     * Uses dual-layer cache (L1 Caffeine + L2 Redis) for multi-instance consistency.
     */
    private Long resolveOperationPermissionId(Long tenantId, String opCode) {
        Map<String, Long> ops = opCodeCacheManager.get(tenantId, tenantId, this::loadOperations);
        Long opId = ops.get(opCode);
        if (opId == null) {
            throw new IllegalStateException("Operation code '" + opCode + "' not found for tenant " + tenantId);
        }
        return opId;
    }

    /**
     * Load operations from permission-center.
     * BiFunction signature: (tenantId, key) -> Map<String, Long>
     */
    private Map<String, Long> loadOperations(Long tenantId, Long key) {
        OperationListReq req = new OperationListReq("MENU"); // resourceTypeCode
        PermResult<Map<String, Object>> result = permissionFeignClient.listOperations(req);
        if (result == null || result.data() == null) {
            throw new IllegalStateException("Failed to list operations for tenant " + tenantId);
        }
        // The response is a Map, extract operation list from it
        Object itemsObj = result.data().get("items");
        if (itemsObj instanceof List<?> items) {
            Map<String, Long> ops = new HashMap<>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    Object codeObj = map.get("code");
                    Object idObj = map.get("id");
                    if (codeObj != null && idObj != null) {
                        ops.put(codeObj.toString(), Long.valueOf(idObj.toString()));
                    }
                }
            }
            return ops;
        }
        return Map.of();
    }
}
