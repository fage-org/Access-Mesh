package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleSummaryResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.GroupRoleManageService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class GroupRoleManageServiceImpl implements GroupRoleManageService {

    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TypeResolutionService typeResolutionService;
    private final UserRoleDomainService userRoleDomainService;
    private final ResourcePermissionValidator permissionValidator;

    public GroupRoleManageServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                       UserRoleMapper userRoleMapper,
                                       TypeResolutionService typeResolutionService,
                                       UserRoleDomainService userRoleDomainService,
                                       ResourcePermissionValidator permissionValidator) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleMapper = userRoleMapper;
        this.typeResolutionService = typeResolutionService;
        this.userRoleDomainService = userRoleDomainService;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.groupDomainCode());
        if (groupId == null) {
            throw new IllegalArgumentException("Group role not found: " + req.groupRoleExternalId());
        }
        Long basicRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.basicRoleTypeCode(), req.basicRoleExternalId(), req.basicDomainCode());
        if (basicRoleId == null) {
            throw new IllegalArgumentException("Basic role not found: " + req.basicRoleExternalId());
        }

        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.ROLE, groupId, OperationType.ASSIGN);

        AbstractRole groupRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(groupId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        Integer groupRoleTypeValue = typeResolutionService.resolveTypeValue(tenantId, "role_type", "GROUP_ROLE");
        if (groupRole == null || groupRoleTypeValue == null || !groupRoleTypeValue.equals(groupRole.getRoleType())) {
            throw new IllegalArgumentException("Not a valid GROUP_ROLE: " + req.groupRoleExternalId());
        }

        AbstractRole basicRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(basicRoleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (basicRole == null) {
            throw new IllegalArgumentException("Basic role not found: " + req.basicRoleExternalId());
        }

        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(null);
        ur.setTargetType("GROUP_ROLE");
        ur.setTargetId(groupId);
        ur.setRelationId(basicRoleId);
        ur.setCreatedAt(LocalDateTime.now());
        ur.setUpdatedAt(LocalDateTime.now());
        ur.setDeleteFlag(0L);
        userRoleMapper.insert(ur);

        userRoleDomainService.invalidateRoleCacheByRole(tenantId, groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.groupDomainCode());
        if (groupId == null) {
            throw new IllegalArgumentException("Group role not found: " + req.groupRoleExternalId());
        }
        Long basicRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.basicRoleTypeCode(), req.basicRoleExternalId(), req.basicDomainCode());
        if (basicRoleId == null) {
            throw new IllegalArgumentException("Basic role not found: " + req.basicRoleExternalId());
        }

        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.ROLE, groupId, OperationType.REVOKE);

        UserRole ur = userRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .where(USER_ROLE.TARGET_TYPE.eq("GROUP_ROLE"))
                .and(USER_ROLE.TARGET_ID.eq(groupId))
                .and(USER_ROLE.RELATION_ID.eq(basicRoleId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        );
        if (ur != null) {
            ur.setDeleteFlag(ur.getId());
            ur.setDeletedAt(LocalDateTime.now());
            userRoleMapper.update(ur);
            userRoleDomainService.invalidateRoleCacheByRole(tenantId, groupId);
        }
    }

    @Override
    public List<RoleSummaryResp> listGroupRoleExtraRoles(Long tenantId, GroupRoleExtraRolesListReq req) {
        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.domainCode());
        if (groupId == null) {
            return List.of();
        }
        Set<Long> basicRoleIds = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .where(USER_ROLE.TARGET_TYPE.eq("GROUP_ROLE"))
                .where(USER_ROLE.TARGET_ID.eq(groupId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        ).stream()
            .map(UserRole::getRelationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (basicRoleIds.isEmpty()) {
            return List.of();
        }
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .where(ABSTRACT_ROLE.ID.in(basicRoleIds))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        ).stream()
            .map(r -> new RoleSummaryResp(
                r.getId(),
                typeResolutionService.resolveTypeCode(tenantId, "role_type", r.getRoleType()),
                r.getExternalId(),
                r.getName()))
            .collect(Collectors.toList());
    }
}