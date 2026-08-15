package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.RoleManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 功能角色代理：本地调用 permission AppService，不再 Feign。
 */
@Service
@Primary
public class RoleProxyServiceImpl implements RoleProxyService {

    private static final Logger log = LoggerFactory.getLogger(RoleProxyServiceImpl.class);
    private static final List<String> FUNCTIONAL_ROLE_TYPES = List.of("BASIC_ROLE", "GROUP_ROLE", "PERSONAL");
    private static final Map<String, String> ROLE_TYPE_LABELS = Map.of(
        "BASIC_ROLE", "基础角色",
        "GROUP_ROLE", "分组角色",
        "PERSONAL", "个人角色",
        "ORG", "组织角色",
        "POSITION", "岗位角色"
    );
    private static final List<String> EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES = List.of(
        AdminResourceType.ORG,
        AdminResourceType.USER,
        AdminResourceType.ROLE,
        AdminResourceType.NOTICE,
        AdminResourceType.JOB,
        AdminResourceType.DICT,
        AdminResourceType.DICT_DATA,
        AdminResourceType.CONFIG,
        AdminResourceType.OAUTH2_CLIENT,
        AdminResourceType.FILE,
        AdminResourceType.ORG_TREE_CONFIG,
        AdminResourceType.SYNC_TASK,
        "ROLE"
    );

    private final RoleManageAppService roleManageAppService;
    private final UserManageAppService userManageAppService;
    private final PermissionViewAppService permissionViewAppService;
    private final PermissionGrantAppService permissionGrantAppService;
    private final UserOrgDomainService userOrgDomainService;
    private final OrgDomainService orgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final LocalProjectionGuard localProjectionGuard;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    public RoleProxyServiceImpl(RoleManageAppService roleManageAppService,
                                UserManageAppService userManageAppService,
                                PermissionViewAppService permissionViewAppService,
                                PermissionGrantAppService permissionGrantAppService,
                                UserOrgDomainService userOrgDomainService,
                                OrgDomainService orgDomainService,
                                AdminPermissionValidator permissionValidator,
                                LocalProjectionGuard localProjectionGuard,
                                TypeResolutionService typeResolutionService,
                                PermQueryEngine engine) {
        this.roleManageAppService = roleManageAppService;
        this.userManageAppService = userManageAppService;
        this.permissionViewAppService = permissionViewAppService;
        this.permissionGrantAppService = permissionGrantAppService;
        this.userOrgDomainService = userOrgDomainService;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.localProjectionGuard = localProjectionGuard;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
    }

    @Override
    public List<RoleListItemResp> listRoles(List<String> roleTypeCodes) {
        permissionValidator.checkTypeLevel(AdminResourceType.ROLE, AdminOperationCode.VIEW);
        List<String> typeCodes = (roleTypeCodes != null && !roleTypeCodes.isEmpty())
            ? roleTypeCodes
            : FUNCTIONAL_ROLE_TYPES;
        // T-ACCESS-005 评审 P2（用户决策：显式拒绝）：显式传入 ORG/POSITION 时拒绝，
        // 防止通过 /role/list 绕过"仅功能角色"约束暴露本地投影角色
        for (String typeCode : typeCodes) {
            if (!FUNCTIONAL_ROLE_TYPES.contains(typeCode)) {
                throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                    "仅支持功能角色类型: BASIC_ROLE/GROUP_ROLE/PERSONAL，收到: " + typeCode);
            }
        }
        Long tenantId = TenantContextHolder.getTenantId();
        List<RoleResp> roles = roleManageAppService.listRoles(
            tenantId, null, null, typeCodes, null, 0, 200);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
            .map(r -> new RoleListItemResp(
                r.roleTypeCode(),
                r.externalId(),
                r.name(),
                ROLE_TYPE_LABELS.getOrDefault(r.roleTypeCode(), r.roleTypeCode())
            ))
            .collect(Collectors.toList());
    }

    @Override
    public Long createRoleForOrg(String roleName, Long orgId, Long tenantId) {
        permissionValidator.checkTypeLevel(AdminResourceType.ROLE, AdminOperationCode.CREATE);
        SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        localProjectionGuard.rejectReservedRoleType(resolveOrgRoleTypeCode(org));
        throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode(),
            "组织/岗位角色只能由组织写入投影产生");
    }

    @Override
    public void grantMenuToRole(Long tenantId, Long roleId, Long menuId, String opCode) {
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ROLE, String.valueOf(roleId), AdminOperationCode.GRANT);
        RoleResp role = roleManageAppService.getRole(tenantId, roleId);
        if (role == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "角色不存在");
        }
        localProjectionGuard.rejectReservedRoleType(role.roleTypeCode());
        ApplyGrantPlanReq req = new ApplyGrantPlanReq(
            null,
            role.roleTypeCode(),
            role.externalId(),
            new ApplyGrantPlanReq.GrantPlan(
                List.of(new ApplyGrantPlanReq.CreateItem(
                    new ApplyGrantPlanReq.GrantRecordKey(
                        AdminResourceType.MENU,
                        String.valueOf(menuId),
                        "default",
                        opCode,
                        ScopeMode.INSTANCE,
                        null,
                        Boolean.FALSE
                    ),
                    null,
                    List.of()
                )),
                List.of(),
                List.of()
            )
        );
        permissionGrantAppService.applyGrantPlan(tenantId, req);
    }

    @Override
    public void revokeMenuFromRole(Long tenantId, Long roleId, Long menuId) {
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ROLE, String.valueOf(roleId), AdminOperationCode.GRANT);
        RoleResp role = roleManageAppService.getRole(tenantId, roleId);
        if (role == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "角色不存在");
        }
        localProjectionGuard.rejectReservedRoleType(role.roleTypeCode());
        List<RolePermissionItemResp> items = permissionGrantAppService.listPermissions(
            tenantId,
            new RolePermissionListReq(null, role.roleTypeCode(), role.externalId(), AdminResourceType.MENU, true));
        String menuCode = String.valueOf(menuId);
        List<Long> removeIds = items.stream()
            .filter(item -> menuCode.equals(item.resourceCode()))
            .map(RolePermissionItemResp::id)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
        if (removeIds.isEmpty()) {
            return;
        }
        permissionGrantAppService.applyGrantPlan(tenantId, new ApplyGrantPlanReq(
            null,
            role.roleTypeCode(),
            role.externalId(),
            new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), removeIds)
        ));
    }

    @Override
    public UserInfoResp loadUserRolesAndPermissions(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<SysUserOrg> userOrgs = userOrgDomainService.findByUserId(tenantId, userId);
        List<UserInfoResp.OrgInfo> orgInfos = userOrgs.stream()
            .map(uo -> new UserInfoResp.OrgInfo(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());
        return new UserInfoResp(userId, null, null, null, null, null, null,
            fetchUserRoles(tenantId, userId), fetchUserPermissions(tenantId, userId), orgInfos);
    }

    @Override
    public List<UserRoleItemResp> listUserRoles(Long userId) {
        permissionValidator.checkInstanceLevel(
            AdminResourceType.USER, String.valueOf(userId), AdminOperationCode.VIEW);
        Long tenantId = TenantContextHolder.getTenantId();
        UserRolesResp result = userManageAppService.getUserRoles(
            tenantId, new UserRoleListReq(LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId)));
        if (result == null || result.roles() == null) {
            return List.of();
        }
        List<Long> orgIdsToLookup = result.roles().stream()
            .filter(r -> "POSITION".equals(r.roleTypeCode()) && r.relationExternalId() != null)
            .map(r -> Long.valueOf(r.relationExternalId()))
            .collect(Collectors.toList());
        Map<Long, SysOrg> orgMap = orgIdsToLookup.isEmpty()
            ? Map.of()
            : orgDomainService.batchSelectValidByIdsMap(tenantId, new java.util.HashSet<>(orgIdsToLookup));
        return result.roles().stream()
            .map(r -> {
                String relationOrgName = null;
                if ("POSITION".equals(r.roleTypeCode()) && r.relationExternalId() != null) {
                    SysOrg org = orgMap.get(Long.valueOf(r.relationExternalId()));
                    relationOrgName = org != null ? org.getName() : null;
                }
                return new UserRoleItemResp(
                    r.roleTypeCode(),
                    r.roleExternalId(),
                    r.roleName(),
                    ROLE_TYPE_LABELS.getOrDefault(r.roleTypeCode(), r.roleTypeCode()),
                    r.roleTypeCode(),
                    r.relationId(),
                    relationOrgName,
                    r.validFrom(),
                    r.validTo()
                );
            })
            .collect(Collectors.toList());
    }

    @Override
    public void assignRole(Long userId, String roleTypeCode, String roleExternalId,
                           LocalDateTime validFrom, LocalDateTime validTo) {
        if (LocalProjectionOwner.isReservedRoleType(roleTypeCode)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "ORG/POSITION 角色请通过组织归属接口分配，不支持直接分配角色");
        }
        Long tenantId = TenantContextHolder.getTenantId();
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem(
                LocalProjectionOwner.SUBJECT_ADMIN_USER,
                String.valueOf(userId),
                null,
                roleTypeCode,
                roleExternalId,
                null,
                validFrom,
                validTo
            )));
        userManageAppService.assignRole(tenantId, req);
    }

    @Override
    public void revokeRole(Long userId, String roleTypeCode, String roleExternalId) {
        if (LocalProjectionOwner.isReservedRoleType(roleTypeCode)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "ORG/POSITION 角色请通过组织归属接口回收，不支持直接回收角色");
        }
        Long tenantId = TenantContextHolder.getTenantId();
        userManageAppService.revokeRolesBatch(tenantId, new UserRoleBatchRevokeReq(List.of(
            new UserRoleBatchRevokeReq.RevokeItem(
                LocalProjectionOwner.SUBJECT_ADMIN_USER,
                String.valueOf(userId),
                null,
                roleTypeCode,
                roleExternalId,
                null
            ))));
    }

    private List<UserInfoResp.RoleInfo> fetchUserRoles(Long tenantId, Long userId) {
        try {
            UserRolesResp result = userManageAppService.getUserRoles(
                tenantId, new UserRoleListReq(LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId)));
            if (result != null && result.roles() != null) {
                return result.roles().stream()
                    .map(r -> new UserInfoResp.RoleInfo(null, r.roleName()))
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user roles for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }

    private List<String> fetchUserPermissions(Long tenantId, Long userId) {
        try {
            UserEffectivePermissionCodesReq req = new UserEffectivePermissionCodesReq(
                LocalProjectionOwner.SUBJECT_ADMIN_USER,
                String.valueOf(userId),
                EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES
            );
            UserEffectivePermissionCodesResp result =
                permissionViewAppService.getEffectivePermissionCodes(tenantId, req);
            if (result != null && result.permissions() != null) {
                return new ArrayList<>(result.permissions());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user permissions for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }

    @Override
    public Set<Long> filterAllowedMenuIds(Long userId, Collection<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Set.of();
        }
        Long tenantId = TenantContextHolder.getTenantId();
        Long abstractUserId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId));
        if (abstractUserId == null) {
            return Set.of();
        }
        // T-ACCESS-005 评审 P2：一次 engine.getDeniedIds 批量查询，替代逐菜单单查 N 次
        Set<Long> denied = engine.getDeniedIds(
            tenantId, abstractUserId, AdminResourceType.MENU, new LinkedHashSet<>(menuIds), "VIEW");
        Set<Long> allowed = new LinkedHashSet<>(menuIds);
        allowed.removeAll(denied);
        return allowed;
    }

    private static String resolveOrgRoleTypeCode(SysOrg org) {
        String orgType = org.getOrgType();
        if ("2".equals(orgType) || "POSITION".equalsIgnoreCase(orgType)) {
            return "POSITION";
        }
        return "ORG";
    }
}
