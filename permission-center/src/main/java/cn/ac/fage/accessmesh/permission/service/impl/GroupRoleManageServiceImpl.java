package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleSummaryResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.GroupRoleManageService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef;

@Service
public class GroupRoleManageServiceImpl implements GroupRoleManageService {

    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TypeResolutionService typeResolutionService;
    private final UserRoleDomainService userRoleDomainService;
    private final PermQueryEngine engine;

    public GroupRoleManageServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                       UserRoleMapper userRoleMapper,
                                       TypeResolutionService typeResolutionService,
                                       UserRoleDomainService userRoleDomainService,
                                       PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleMapper = userRoleMapper;
        this.typeResolutionService = typeResolutionService;
        this.userRoleDomainService = userRoleDomainService;
        this.engine = engine;
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

        engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, groupId, OperationCodeConstants.ASSIGN);

        AbstractRole groupRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(groupId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        Integer groupRoleTypeValue = typeResolutionService.resolveTypeValue(tenantId, "role_type", PermConstants.TargetType.GROUP_ROLE);
        if (groupRole == null || groupRoleTypeValue == null || !groupRoleTypeValue.equals(groupRole.getRoleType())) {
            throw new IllegalArgumentException("Not a valid GROUP_ROLE: " + req.groupRoleExternalId());
        }

        AbstractRole basicRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(basicRoleId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (basicRole == null) {
            throw new IllegalArgumentException("Basic role not found: " + req.basicRoleExternalId());
        }

        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(null);
        ur.setTargetType(PermConstants.TargetType.GROUP_ROLE);
        ur.setTargetId(groupId);
        ur.setRelationId(basicRoleId);
        LocalDateTime now = LocalDateTime.now();
        ur.setCreatedAt(now);
        ur.setUpdatedAt(now);
        ur.setDeleteFlag(0L);
        userRoleMapper.insert(ur);

        // 缓存失效（事务提交后执行）
        final Long groupIdForCache = groupId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    userRoleDomainService.invalidateRoleCacheByRole(tenantId, groupIdForCache);
                }
            });
        }
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

        engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, groupId, OperationCodeConstants.REVOKE);

        UserRole ur = userRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .where(UserRoleTableDef.USER_ROLE.TARGET_TYPE.eq(PermConstants.TargetType.GROUP_ROLE))
                .and(UserRoleTableDef.USER_ROLE.TARGET_ID.eq(groupId))
                .and(UserRoleTableDef.USER_ROLE.RELATION_ID.eq(basicRoleId))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
        );
        if (ur != null) {
            ur.setDeleteFlag(ur.getId());
            ur.setDeletedAt(LocalDateTime.now());
            userRoleMapper.update(ur);
            // 缓存失效（事务提交后执行）
            final Long tenantIdForCache = tenantId;
            final Long groupIdForCache = groupId;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        userRoleDomainService.invalidateRoleCacheByRole(tenantIdForCache, groupIdForCache);
                    }
                });
            }
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
                .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .where(UserRoleTableDef.USER_ROLE.TARGET_TYPE.eq(PermConstants.TargetType.GROUP_ROLE))
                .where(UserRoleTableDef.USER_ROLE.TARGET_ID.eq(groupId))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
        ).stream()
            .map(UserRole::getRelationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (basicRoleIds.isEmpty()) {
            return List.of();
        }
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.in(basicRoleIds))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        ).stream()
            .map(r -> new RoleSummaryResp(
                r.getId(),
                typeResolutionService.resolveTypeCode(tenantId, "role_type", r.getRoleType()),
                r.getExternalId(),
                r.getName()))
            .collect(Collectors.toList());
    }
}
