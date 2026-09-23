package cn.ac.fage.accessmesh.access.role.service.impl;

import cn.ac.fage.accessmesh.access.role.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.role.service.UserRoleQueryAppService;
import cn.ac.fage.accessmesh.access.role.mapper.UserRoleQueryMapper;
import cn.ac.fage.accessmesh.access.role.dto.projection.OrgBriefProjection;
import cn.ac.fage.accessmesh.access.role.dto.projection.UserRoleProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class UserRoleQueryAppServiceImpl implements UserRoleQueryAppService {

    private static final String ROLE_TYPE_KEY = "role_type";
    private static final Map<String, String> ROLE_TYPE_LABELS = Map.of(
        "BASIC_ROLE", "基础角色",
        "GROUP_ROLE", "分组角色",
        "PERSONAL", "个人角色",
        "ORG", "组织角色",
        "POSITION", "岗位角色"
    );

    private final AdminPermissionValidator permissionValidator;
    private final TypeResolutionService typeResolutionService;
    private final UserRoleQueryMapper userRoleQueryMapper;

    public UserRoleQueryAppServiceImpl(AdminPermissionValidator permissionValidator,
                                    TypeResolutionService typeResolutionService,
                                    UserRoleQueryMapper userRoleQueryMapper) {
        this.permissionValidator = permissionValidator;
        this.typeResolutionService = typeResolutionService;
        this.userRoleQueryMapper = userRoleQueryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRoleItemResp> listUserRoles(Long userId) {
        permissionValidator.checkInstanceLevel(
            ResourceTypeCode.USER, String.valueOf(userId), OperationCode.VIEW);
        Long tenantId = TenantContextHolder.getTenantId();
        Long abstractUserId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_LOCAL_USER, String.valueOf(userId));
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
                && LocalProjectionOwner.ROLE_POSITION.equals(codeByValue.get(p.targetRoleType()))
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
                if (LocalProjectionOwner.ROLE_POSITION.equals(roleTypeCode) && p.relationExternalId() != null) {
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
}
