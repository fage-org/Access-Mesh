package cn.ac.fage.accessmesh.access.application.query.impl;

import cn.ac.fage.accessmesh.access.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.application.query.UserRoleQueryService;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserRoleQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.projection.FunctionalRoleProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.OrgBriefProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserRoleProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户角色组合查询实现（跨域只读）。
 * <p>
 * 角色类型码与 abstract_role.role_type 值的映射以 type_definition（role_type）为权威，
 * 经 TypeResolutionService 批量解析；数据读取全部经 {@link UserRoleQueryMapper}，
 * 方法标注只读事务。
 * </p>
 */
@Service
public class UserRoleQueryServiceImpl implements UserRoleQueryService {

    private static final String ROLE_TYPE_KEY = "role_type";
    private static final List<String> FUNCTIONAL_ROLE_TYPES = List.of("BASIC_ROLE", "GROUP_ROLE", "PERSONAL");
    private static final Map<String, String> ROLE_TYPE_LABELS = Map.of(
        "BASIC_ROLE", "基础角色",
        "GROUP_ROLE", "分组角色",
        "PERSONAL", "个人角色",
        "ORG", "组织角色",
        "POSITION", "岗位角色"
    );
    private static final int ROLE_LIST_LIMIT = 200;

    private final AdminPermissionValidator permissionValidator;
    private final TypeResolutionService typeResolutionService;
    private final UserRoleQueryMapper userRoleQueryMapper;

    public UserRoleQueryServiceImpl(AdminPermissionValidator permissionValidator,
                                    TypeResolutionService typeResolutionService,
                                    UserRoleQueryMapper userRoleQueryMapper) {
        this.permissionValidator = permissionValidator;
        this.typeResolutionService = typeResolutionService;
        this.userRoleQueryMapper = userRoleQueryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleListItemResp> listRoles(List<String> roleTypeCodes) {
        permissionValidator.checkTypeLevel(AdminResourceType.ROLE, AdminOperationCode.VIEW);
        List<String> typeCodes = (roleTypeCodes != null && !roleTypeCodes.isEmpty())
            ? roleTypeCodes
            : FUNCTIONAL_ROLE_TYPES;
        // 显式传入 ORG/POSITION 时拒绝，防止通过 /role/list 绕过"仅功能角色"约束暴露本地投影角色
        for (String typeCode : typeCodes) {
            if (!FUNCTIONAL_ROLE_TYPES.contains(typeCode)) {
                throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                    "仅支持功能角色类型: BASIC_ROLE/GROUP_ROLE/PERSONAL，收到: " + typeCode);
            }
        }
        Long tenantId = TenantContextHolder.getTenantId();
        // 类型码 → 值；任一未注册 → 空列表（与 permission 域 listRoles 的 matchNone 语义一致）
        Map<String, Integer> valueByCode = typeResolutionService.batchResolveTypeValues(
            tenantId, ROLE_TYPE_KEY, new LinkedHashSet<>(typeCodes));
        List<Integer> typeValues = new ArrayList<>();
        for (String typeCode : typeCodes) {
            Integer value = valueByCode.get(typeCode);
            if (value == null) {
                return List.of();
            }
            typeValues.add(value);
        }
        Map<Integer, String> codeByValue = invert(valueByCode);
        List<FunctionalRoleProjection> roles =
            userRoleQueryMapper.selectFunctionalRoles(tenantId, typeValues, null, 0, ROLE_LIST_LIMIT);
        return roles.stream()
            .map(r -> {
                String roleTypeCode = codeByValue.get(r.roleType());
                return new RoleListItemResp(
                    roleTypeCode,
                    r.externalId(),
                    r.name(),
                    roleTypeCode != null ? ROLE_TYPE_LABELS.getOrDefault(roleTypeCode, roleTypeCode) : null
                );
            })
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRoleItemResp> listUserRoles(Long userId) {
        permissionValidator.checkInstanceLevel(
            AdminResourceType.USER, String.valueOf(userId), AdminOperationCode.VIEW);
        Long tenantId = TenantContextHolder.getTenantId();
        Long abstractUserId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId));
        if (abstractUserId == null) {
            return List.of();
        }
        List<UserRoleProjection> projections =
            userRoleQueryMapper.selectUserRoleProjections(tenantId, abstractUserId, LocalDateTime.now());
        if (projections.isEmpty()) {
            return List.of();
        }
        // 角色类型值 → 码批量解析
        Set<Integer> roleTypeValues = projections.stream()
            .map(UserRoleProjection::targetRoleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> codeByValue = roleTypeValues.isEmpty()
            ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, ROLE_TYPE_KEY, roleTypeValues);
        // POSITION 所属组织名批量补查（relationExternalId 即 sys_org.id 的字符串形式）。
        // 判定以 target 角色的解析类型码为准（user_role.target_type 恒为 ROLE/GROUP_ROLE，不承载岗位语义）
        Set<Long> orgIds = projections.stream()
            .filter(p -> p.targetRoleType() != null
                && "POSITION".equals(codeByValue.get(p.targetRoleType()))
                && p.relationExternalId() != null)
            .map(p -> Long.valueOf(p.relationExternalId()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> orgNameById = orgIds.isEmpty()
            ? Map.of()
            : userRoleQueryMapper.selectOrgBriefsByIds(tenantId, orgIds).stream()
                .collect(Collectors.toMap(OrgBriefProjection::id, OrgBriefProjection::name, (a, b) -> a, LinkedHashMap::new));
        return projections.stream()
            .map(p -> {
                String roleTypeCode = p.targetRoleType() != null ? codeByValue.get(p.targetRoleType()) : null;
                String relationOrgName = null;
                // 与组织名补查过滤同判定依据：target 角色解析类型码（target_type 原始值不承载岗位语义）
                if ("POSITION".equals(roleTypeCode) && p.relationExternalId() != null) {
                    relationOrgName = orgNameById.get(Long.valueOf(p.relationExternalId()));
                }
                return new UserRoleItemResp(
                    roleTypeCode,
                    p.roleExternalId(),
                    p.roleName(),
                    roleTypeCode != null ? ROLE_TYPE_LABELS.getOrDefault(roleTypeCode, roleTypeCode) : null,
                    roleTypeCode,
                    p.relationId(),
                    relationOrgName,
                    p.validFrom(),
                    p.validTo()
                );
            })
            .collect(Collectors.toList());
    }

    private static Map<Integer, String> invert(Map<String, Integer> valueByCode) {
        Map<Integer, String> codeByValue = new HashMap<>();
        valueByCode.forEach((code, value) -> codeByValue.put(value, code));
        return codeByValue;
    }
}
