package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.cache.OperationCodeCacheManager;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 角色代理服务实现类
 * <p>
 * admin-service与permission-center之间的代理层，负责概念转换。
 * 将admin-domain概念（用户、组织、菜单）转换为permission-center概念（角色、资源、权限）。
 * 提供组织角色创建、菜单权限授予/撤销、用户角色和权限加载等功能。
 * 通过Feign调用permission-center服务，使用双层缓存（L1 Caffeine + L2 Redis）缓存操作码映射。
 * </p>
 */
@Service
public class RoleProxyServiceImpl implements RoleProxyService {

    private static final Logger log = LoggerFactory.getLogger(RoleProxyServiceImpl.class);

    private static final int RESOURCE_TYPE_MENU = 1;

    private final PermissionFeignClient permissionFeignClient;
    private final UserOrgDomainService userOrgDomainService;
    private final MenuDomainService menuDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final OperationCodeCacheManager opCodeCacheManager;

    /**
     * 构造函数注入依赖
     *
     * @param permissionFeignClient 权限中心Feign客户端
     * @param userOrgDomainService 用户组织关联领域服务
     * @param menuDomainService 菜单领域服务
     * @param permissionValidator 权限校验器
     * @param opCodeCacheManager 操作码缓存管理器，双层缓存
     */
    public RoleProxyServiceImpl(PermissionFeignClient permissionFeignClient,
                            UserOrgDomainService userOrgDomainService,
                            MenuDomainService menuDomainService,
                            AdminPermissionValidator permissionValidator,
                            OperationCodeCacheManager opCodeCacheManager) {
        this.permissionFeignClient = permissionFeignClient;
        this.userOrgDomainService = userOrgDomainService;
        this.menuDomainService = menuDomainService;
        this.permissionValidator = permissionValidator;
        this.opCodeCacheManager = opCodeCacheManager;
    }

    /**
     * 为组织创建角色
     * <p>
     * 在permission-center创建组织专属角色(ORG_ROLE类型)。
     * 执行类型级权限校验(CREATE)。
     * 角色外部ID为组织ID，用于关联组织与角色。
     * </p>
     *
     * @param roleName 角色名称
     * @param orgId 组织ID
     * @param tenantId 租户ID
     * @return 创建的角色ID
     * @throws SystemException 创建角色失败
     */
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

    /**
     * 为角色授予菜单权限
     * <p>
     * 将指定菜单的指定操作权限授予组织角色。
     * 执行实例级权限校验(GRANT)。
     * 通过MenuDomainService获取菜单，调用permission-center批量授权接口。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId 角色ID
     * @param menuId 菜单ID
     * @param opCode 操作码（如VIEW）
     * @throws BizException 菜单不存在或未同步到权限中心
     * @throws SystemException 授权失败
     */
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

    /**
     * 从角色撤销菜单权限
     * <p>
     * 撤销组织角色对指定菜单的所有权限。
     * 执行实例级权限校验(REVOKE)。
     * 先查询角色现有权限，过滤出匹配菜单的权限项，再调用批量撤销接口。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId 角色ID
     * @param menuId 菜单ID
     * @throws BizException 菜单不存在或未同步到权限中心
     * @throws SystemException 撤销失败
     */
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

    /**
     * 加载用户角色和权限
     * <p>
     * 获取用户的组织关联、角色和按钮级权限。
     * 通过UserOrgDomainService获取用户组织关联。
     * 角色和权限通过permission-center获取（待实现）。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息响应，包含组织、角色、权限列表
     */
    @Override
    public UserInfoResp loadUserRolesAndPermissions(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<SysUserOrg> userOrgs = userOrgDomainService.findByUserId(tenantId, userId);

        List<UserInfoResp.OrgInfo> orgInfos = userOrgs.stream()
            .map(uo -> new UserInfoResp.OrgInfo(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());

        // Fetch roles from permission-center via /api/user/roles
        List<UserInfoResp.RoleInfo> roles = List.of();

        // Fetch permissions from permission-center via /api/permission-view/user
        List<String> permissions = List.of();

        return new UserInfoResp(userId, null, null, null, null, null, null, roles, permissions, orgInfos);
    }

    /**
     * 解析操作码为操作权限ID
     * <p>
     * 将操作码（如VIEW）转换为permission-center的操作权限ID。
     * 使用双层缓存（L1 Caffeine + L2 Redis）确保多实例一致性。
     * </p>
     *
     * @param tenantId 租户ID
     * @param opCode 操作码
     * @return 操作权限ID
     * @throws IllegalStateException 操作码不存在
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
     * 从permission-center加载操作码列表
     * <p>
     * 通过Feign调用permission-center获取MENU资源类型的操作列表。
     * 返回操作码到操作权限ID的映射。
     * BiFunction签名：(tenantId, key) -> Map<String, Long>
     * </p>
     *
     * @param tenantId 租户ID
     * @param key 缓存键（与tenantId相同）
     * @return 操作码到ID的映射
     * @throws IllegalStateException 加载失败
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