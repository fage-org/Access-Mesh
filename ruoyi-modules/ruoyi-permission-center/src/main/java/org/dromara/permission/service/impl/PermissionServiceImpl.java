package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.handler.ResourceTypeHandler;
import org.dromara.permission.handler.ResourceTypeHandlerRegistry;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.event.PermissionWriteRefreshEventPublisher;
import org.dromara.permission.mapper.*;
import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.DependencyGap;
import org.dromara.permission.model.permission.DenyReason;
import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.GrantResult;
import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionCheckRequest;
import org.dromara.permission.model.permission.PermissionCheckResult;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.PermissionSnapshot;
import org.dromara.permission.model.permission.PermissionVersionQueryRequest;
import org.dromara.permission.model.permission.PermissionVersionResult;
import org.dromara.permission.model.permission.ResolvedRole;
import org.dromara.permission.model.permission.RevokePermissionRequest;
import org.dromara.permission.model.permission.RevokeResult;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
import org.dromara.permission.model.permission.SnapshotEntry;
import org.dromara.permission.model.permission.SnapshotRequest;
import org.dromara.permission.model.permission.UserRoleBatchAssignRequest;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
import org.dromara.permission.model.permission.ValidationResult;
import org.dromara.permission.service.*;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final int DEPTH_LIMIT = 5;

    private final RoleResolverService roleResolverService;
    private final OperationInheritanceService operationInheritanceService;
    private final DomainScopeValidator domainScopeValidator;
    private final ChangeLogService changeLogService;
    private final PermissionVersionService permissionVersionService;
    private final ResourceTypeHandlerRegistry resourceTypeHandlerRegistry;
    private final PermissionBridgeSupport permissionBridgeSupport;
    private final PermissionWriteRefreshEventPublisher permissionWriteRefreshEventPublisher;
    private final PcResourceEntityMapper resourceEntityMapper;
    private final PcOperationPermissionMapper operationPermissionMapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PcPermissionConditionMapper permissionConditionMapper;
    private final PcPermissionConflictRuleMapper permissionConflictRuleMapper;
    private final PcResourceDependencyMapper resourceDependencyMapper;
    private final PcAbstractRoleMapper abstractRoleMapper;
    private final PcAbstractUserMapper abstractUserMapper;
    private final PcUserRoleMapper userRoleMapper;

    @Override
    public PermissionCheckResult check(PermissionCheckRequest request) {
        validateCheckRequest(request);
        PermissionContext ctx = new PermissionContext(request.getTenantId(), request.getAbstractUserId(),
            request.getBizDomainId(), request.getInheritMode(), request.getContext());
        ctx.setAction("check");
        ctx.setChangeSource("API");
        ctx.setConditionEvaluatorResolver(resourceType ->
            resourceTypeHandlerRegistry.getHandler(request.getTenantId(), resourceType).getConditionEvaluator());
        List<ResolvedRole> roles = roleResolverService.resolve(ctx.getTenantId(), ctx.getUserId(), ctx.getBizDomainId());
        ctx.setRoles(roles);
        if (roles.isEmpty()) {
            return PermissionCheckResult.denied(DenyReason.NO_ROLE, Collections.emptyList(), Collections.emptyList());
        }
        PcResourceEntity resource = permissionBridgeSupport.loadResource(request.getTenantId(), request.getResourceEntityId());
        PcOperationPermission operation = permissionBridgeSupport.loadOperation(request.getTenantId(), request.getOperationPermissionId());
        permissionBridgeSupport.validateResourceOperationType(resource, operation);
        ctx.setResource(resource);
        ctx.setOperation(operation);
        ResourceTypeHandler handler = resourceTypeHandlerRegistry.getHandler(ctx.getTenantId(), resource.getResourceType());
        ctx.setExpandedResourceIds(handler.getInheritanceExpander().expand(resource.getId(), ctx.getInheritMode(), ctx));

        List<Long> roleIds = roles.stream().map(ResolvedRole::getRoleId).collect(Collectors.toList());
        List<MatchedPermission> matched = handler.getPermissionMatcher()
            .match(new HashSet<>(roleIds), ctx.getExpandedResourceIds(), request.getOperationPermissionId(), ctx);
        if (matched.isEmpty()) {
            return PermissionCheckResult.denied(DenyReason.NO_PERMISSION, Collections.emptyList(), Collections.emptyList());
        }
        List<MatchedPermission> allMatchedPermissions = new ArrayList<>(matched);
        Map<Long, PcOperationPermission> grantedOperations = permissionBridgeSupport.loadOperationsByIds(
            matched.stream().map(MatchedPermission::getOperationId).collect(Collectors.toSet()), ctx.getTenantId());
        Map<Long, PcResourceEntity> resources = permissionBridgeSupport.loadResourcesByIds(
            matched.stream().map(MatchedPermission::getResourceId).collect(Collectors.toSet()), ctx.getTenantId());
        Map<Long, PcPermissionCondition> conditions = permissionBridgeSupport.loadConditionsByIds(
            matched.stream().map(MatchedPermission::getConditionId).filter(Objects::nonNull).collect(Collectors.toSet()));
        ctx.setOperations(grantedOperations);
        ctx.setResources(resources);
        ctx.setConditions(conditions);
        permissionBridgeSupport.attachPermissionMetadata(matched, resources, grantedOperations, conditions);
        matched = operationInheritanceService.filterByInheritance(matched, operation, grantedOperations);
        permissionBridgeSupport.attachPermissionMetadata(matched, resources, grantedOperations, conditions);
        if (matched.isEmpty()) {
            return PermissionCheckResult.denied(DenyReason.NO_PERMISSION, Collections.emptyList(), Collections.emptyList());
        }
        List<MatchedPermission> effectivePermissions = allMatchedPermissions.stream()
            .filter(permission -> handler.getConditionEvaluator().evaluate(permission, ctx))
            .collect(Collectors.toList());
        Set<Long> effectivePermissionIds = effectivePermissions.stream()
            .map(MatchedPermission::getPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        matched = matched.stream()
            .filter(permission -> permission.getPermissionId() != null && effectivePermissionIds.contains(permission.getPermissionId()))
            .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return PermissionCheckResult.denied(DenyReason.CONDITION_FAIL, Collections.emptyList(), Collections.emptyList());
        }
        List<ConflictDetail> conflicts = handler.getConflictDetector().detect(effectivePermissions, ctx);
        ctx.setDetectedConflicts(conflicts);
        matched = permissionBridgeSupport.removeConflicted(matched, conflicts);
        if (matched.isEmpty()) {
            return PermissionCheckResult.denied(DenyReason.CONFLICT, conflicts, Collections.emptyList());
        }
        if (Boolean.TRUE.equals(request.getCheckDependency())) {
            DependencyCheckResult dependencyResult = handler.getDependencyChecker()
                .check(request.getResourceEntityId(), request.getOperationPermissionId(), ctx);
            if (!dependencyResult.isSatisfied()) {
                return PermissionCheckResult.denied(DenyReason.DEPENDENCY_FAIL, conflicts, dependencyResult.getGaps());
            }
        }
        ctx.setMatchedPermissions(matched);
        return PermissionCheckResult.granted(matched, conflicts);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantResult grant(GrantPermissionRequest request) {
        validateGrantRequest(request);
        PermissionContext ctx = createContext(request.getTenantId(), null, request.getBizDomainId(), request.getRequestId(),
            resolveChangeSource(request.getChangeSource()), "grant");
        ctx.setVersionRemark("grant");
        PcAbstractRole role = loadRole(request.getTenantId(), request.getAbstractRoleId());
        PcResourceEntity resource = permissionBridgeSupport.loadResource(request.getTenantId(), request.getResourceEntityId());
        PcOperationPermission operation = permissionBridgeSupport.loadOperation(request.getTenantId(), request.getOperationPermissionId());
        Long resolvedBizDomainId = domainScopeValidator.resolveGrantBizDomainId(request.getBizDomainId(), role, resource);
        applyRoleToContext(ctx, role);
        fillResourceOperation(ctx, resource, operation);
        permissionBridgeSupport.validateResourceOperationType(resource, operation);
        permissionBridgeSupport.assertGrantConditionApproved(request);
        domainScopeValidator.validateGrantScope(ctx.getTenantId(), resolvedBizDomainId, role, resource, operation);
        ValidationResult validationResult = resourceTypeHandlerRegistry.getHandler(ctx.getTenantId(), resource.getResourceType())
            .getGrantValidator().validate(request, ctx);
        if (!validationResult.isValid()) {
            return GrantResult.rejected(validationResult.getReasons());
        }

        LocalDateTime now = LocalDateTime.now();
        PcRoleResourcePermission existing = findLatestRolePermission(
            request.getTenantId(), request.getAbstractRoleId(), request.getResourceEntityId(), request.getOperationPermissionId());
        if (existing != null && PermissionConstants.NOT_DELETED.equals(existing.getDeleteFlag())
            && sameGrantState(existing, request)) {
            return GrantResult.noOp(existing.getId());
        }
        Object oldSnapshot = existing == null ? null : snapshotPermission(existing);
        Long permissionId;
        if (existing != null) {
            restorePermission(existing, request, now);
            roleResourcePermissionMapper.updateById(existing);
            permissionId = existing.getId();
        } else {
            PcRoleResourcePermission entity = new PcRoleResourcePermission();
            entity.setTenantId(request.getTenantId());
            entity.setAbstractRoleId(request.getAbstractRoleId());
            entity.setResourceEntityId(request.getResourceEntityId());
            entity.setOperationPermissionId(request.getOperationPermissionId());
            entity.setCanManage(Boolean.TRUE.equals(request.getCanManage()));
            entity.setConditionId(request.getConditionId());
            entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            roleResourcePermissionMapper.insert(entity);
            permissionId = entity.getId();
            existing = entity;
        }
        List<Long> affectedUserIds = resolveAffectedUserIdsForRole(request.getTenantId(), request.getAbstractRoleId(), now);
        logGrantChange(ctx, request, resolvedBizDomainId, permissionId, oldSnapshot, existing, affectedUserIds);
        bumpVersion(ctx, "role_resource_permission", permissionId);
        return GrantResult.success(permissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RevokeResult revoke(RevokePermissionRequest request) {
        validateRevokeRequest(request);
        PermissionContext ctx = createContext(request.getTenantId(), null, null, request.getRequestId(),
            resolveChangeSource(request.getChangeSource()), "revoke");
        ctx.setVersionRemark("revoke");
        PcRoleResourcePermission existing = findActiveRolePermission(
            request.getTenantId(), request.getAbstractRoleId(), request.getResourceEntityId(), request.getOperationPermissionId());
        if (existing == null) {
            return RevokeResult.noOp(null);
        }
        LocalDateTime now = LocalDateTime.now();
        PcAbstractRole role = findRoleIncludingDeleted(request.getTenantId(), request.getAbstractRoleId());
        PcResourceEntity resource = findResourceIncludingDeleted(request.getTenantId(), request.getResourceEntityId());
        Long resolvedBizDomainId = resolveRevokeBizDomainId(role, resource);
        if (role != null) {
            applyRoleToContext(ctx, role);
        }
        if (resource != null) {
            ctx.setResource(resource);
        }
        Object oldSnapshot = snapshotPermission(existing);
        PermissionAuditSupport.markDeleted(existing, existing.getId(), now);
        roleResourcePermissionMapper.updateById(existing);
        List<Long> affectedUserIds = resolveAffectedUserIdsForRole(request.getTenantId(), request.getAbstractRoleId(), now);
        logRevokeChange(ctx, request, resolvedBizDomainId, existing.getId(), oldSnapshot, affectedUserIds);
        bumpVersion(ctx, "role_resource_permission", existing.getId());
        return RevokeResult.success(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantRolePermissions(RolePermissionBatchGrantRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractRoleId() == null
            || request.getItems() == null || request.getItems().isEmpty()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        PermissionContext ctx = createContext(request.getTenantId(), null, null, request.getRequestId(),
            resolveChangeSource(request.getChangeSource()), "grantRolePermissions");
        ctx.setVersionRemark("grant");
        PcAbstractRole role = loadRole(request.getTenantId(), request.getAbstractRoleId());
        applyRoleToContext(ctx, role);
        List<Map<String, Object>> grantedItems = new ArrayList<>();
        Set<Long> changedBizDomainIds = new LinkedHashSet<>();
        for (RolePermissionBatchGrantRequest.RolePermissionGrantItem item : request.getItems()) {
            if (item.getResourceEntityId() == null || item.getOperationPermissionId() == null) {
                throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "grant item missing resource or operation");
            }
            PcResourceEntity resource = permissionBridgeSupport.loadResource(request.getTenantId(), item.getResourceEntityId());
            Long resolvedBizDomainId = domainScopeValidator.resolveGrantBizDomainId(null, role, resource);
            GrantPermissionRequest grantRequest = new GrantPermissionRequest();
            grantRequest.setTenantId(request.getTenantId());
            grantRequest.setAbstractRoleId(request.getAbstractRoleId());
            grantRequest.setResourceEntityId(item.getResourceEntityId());
            grantRequest.setOperationPermissionId(item.getOperationPermissionId());
            grantRequest.setCanManage(item.getCanManage());
            grantRequest.setConditionId(item.getConditionId());
            grantRequest.setBizDomainId(resolvedBizDomainId);
            grantRequest.setRequestId(request.getRequestId());
            grantRequest.setChangeSource(request.getChangeSource());
            grantRequest.setChangeReason(request.getChangeReason());
            GrantResult result = grant(grantRequest);
            if (!result.isSuccess()) {
                throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST,
                    String.join("; ", result.getRejectReasons()));
            }
            if (result.isChanged()) {
                grantedItems.add(buildRolePermissionBatchItem(item));
                if (resolvedBizDomainId != null) {
                    changedBizDomainIds.add(resolvedBizDomainId);
                }
            }
        }
        if (!grantedItems.isEmpty()) {
            List<Long> affectedUserIds = resolveAffectedUserIdsForRole(request.getTenantId(), request.getAbstractRoleId(), LocalDateTime.now());
            logChange(ctx, new ChangeLogParam()
                .setBizDomainId(resolveBatchBizDomainId(changedBizDomainIds))
                .setEntityType("batch_role_resource_permission")
                .setEntityId(request.getAbstractRoleId())
                .setOperation("BATCH_GRANT")
                .setNewSnapshot(buildRolePermissionBatchSnapshot(request.getAbstractRoleId(), grantedItems))
                .setAffectedAbstractUserIds(affectedUserIds)
                .setAffectedAbstractRoleIds(List.of(request.getAbstractRoleId()))
                .setChangeReason(request.getChangeReason())
                .setRequestId(request.getRequestId())
                .setChangeSource(resolveChangeSource(request.getChangeSource())));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeRolePermissions(RolePermissionBatchRevokeRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractRoleId() == null
            || request.getItems() == null || request.getItems().isEmpty()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        PermissionContext ctx = createContext(request.getTenantId(), null, null, request.getRequestId(),
            resolveChangeSource(request.getChangeSource()), "revokeRolePermissions");
        ctx.setVersionRemark("revoke");
        PcAbstractRole role = findRoleIncludingDeleted(request.getTenantId(), request.getAbstractRoleId());
        if (role != null) {
            applyRoleToContext(ctx, role);
        }
        List<Map<String, Object>> revokedItems = new ArrayList<>();
        Set<Long> changedBizDomainIds = new LinkedHashSet<>();
        for (RolePermissionBatchRevokeRequest.RolePermissionRevokeItem item : request.getItems()) {
            if (item.getResourceEntityId() == null || item.getOperationPermissionId() == null) {
                throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "revoke item missing resource or operation");
            }
            PcResourceEntity resource = findResourceIncludingDeleted(request.getTenantId(), item.getResourceEntityId());
            Long resolvedBizDomainId = resolveRevokeBizDomainId(role, resource);
            RevokePermissionRequest revokeRequest = new RevokePermissionRequest();
            revokeRequest.setTenantId(request.getTenantId());
            revokeRequest.setAbstractRoleId(request.getAbstractRoleId());
            revokeRequest.setResourceEntityId(item.getResourceEntityId());
            revokeRequest.setOperationPermissionId(item.getOperationPermissionId());
            revokeRequest.setRequestId(request.getRequestId());
            revokeRequest.setChangeSource(request.getChangeSource());
            revokeRequest.setChangeReason(request.getChangeReason());
            RevokeResult result = revoke(revokeRequest);
            if (result.isChanged()) {
                revokedItems.add(buildRolePermissionBatchItem(item));
                if (resolvedBizDomainId != null) {
                    changedBizDomainIds.add(resolvedBizDomainId);
                }
            }
        }
        if (!revokedItems.isEmpty()) {
            List<Long> affectedUserIds = resolveAffectedUserIdsForRole(request.getTenantId(), request.getAbstractRoleId(), LocalDateTime.now());
            logChange(ctx, new ChangeLogParam()
                .setBizDomainId(resolveBatchBizDomainId(changedBizDomainIds))
                .setEntityType("batch_role_resource_permission")
                .setEntityId(request.getAbstractRoleId())
                .setOperation("BATCH_REVOKE")
                .setOldSnapshot(buildRolePermissionBatchSnapshot(request.getAbstractRoleId(), revokedItems))
                .setAffectedAbstractUserIds(affectedUserIds)
                .setAffectedAbstractRoleIds(List.of(request.getAbstractRoleId()))
                .setChangeReason(request.getChangeReason())
                .setRequestId(request.getRequestId())
                .setChangeSource(resolveChangeSource(request.getChangeSource())));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserRoles(UserRoleBatchAssignRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractUserId() == null
            || request.getRoleIds() == null || request.getRoleIds().isEmpty()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        validateUserRoleValidityWindow(request.getValidFrom(), request.getValidTo());
        PermissionContext ctx = createContext(request.getTenantId(), request.getAbstractUserId(), null, request.getRequestId(),
            resolveChangeSource(request.getChangeSource()), "assignUserRoles");
        ctx.setVersionRemark("assign-user-roles");
        PcAbstractUser user = abstractUserMapper.selectOne(new LambdaQueryWrapper<PcAbstractUser>()
            .eq(PcAbstractUser::getTenantId, request.getTenantId())
            .eq(PcAbstractUser::getId, request.getAbstractUserId())
            .eq(PcAbstractUser::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (user == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "user not found");
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> changedRoleIds = new ArrayList<>();
        for (Long roleId : request.getRoleIds()) {
            if (roleId == null) {
                throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "roleId cannot be null");
            }
            PcAbstractRole role = loadRole(request.getTenantId(), roleId);
            ctx.getRoles().add(toResolvedRole(role));
            PcUserRole existing = findLatestUserRole(request.getTenantId(), request.getAbstractUserId(), role.getId());
            if (existing != null) {
                if (PermissionConstants.NOT_DELETED.equals(existing.getDeleteFlag())
                    && sameUserRoleState(existing, request)) {
                    continue;
                }
                existing.setValidFrom(request.getValidFrom());
                existing.setValidTo(request.getValidTo());
                existing.setDeleteFlag(PermissionConstants.NOT_DELETED);
                existing.setDeletedAt(null);
                existing.setDeletedBy(null);
                existing.setUpdatedAt(now);
                userRoleMapper.updateById(existing);
            } else {
                PcUserRole entity = new PcUserRole();
                entity.setTenantId(request.getTenantId());
                entity.setAbstractUserId(request.getAbstractUserId());
                entity.setAbstractRoleId(role.getId());
                entity.setValidFrom(request.getValidFrom());
                entity.setValidTo(request.getValidTo());
                entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                userRoleMapper.insert(entity);
            }
            changedRoleIds.add(roleId);
        }
        if (!changedRoleIds.isEmpty()) {
            Map<String, Object> newSnapshot = buildUserRoleBatchSnapshot(request, changedRoleIds);
            logChange(ctx, new ChangeLogParam()
                .setEntityType("batch_user_role")
                .setEntityId(request.getAbstractUserId())
                .setOperation("BATCH_ASSIGN")
                .setNewSnapshot(newSnapshot)
                .setAffectedAbstractUserIds(List.of(request.getAbstractUserId()))
                .setAffectedAbstractRoleIds(new ArrayList<>(changedRoleIds))
                .setChangeReason(request.getChangeReason())
                .setRequestId(request.getRequestId())
                .setChangeSource(resolveChangeSource(request.getChangeSource())));
            bumpVersion(ctx, "user_role", request.getAbstractUserId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeUserRoles(UserRoleBatchRevokeRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractUserId() == null
            || request.getRoleIds() == null || request.getRoleIds().isEmpty()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        PermissionContext ctx = createContext(request.getTenantId(), request.getAbstractUserId(), null, request.getRequestId(),
            resolveChangeSource(request.getChangeSource()), "revokeUserRoles");
        ctx.setVersionRemark("revoke-user-roles");
        LocalDateTime now = LocalDateTime.now();
        List<Long> changedRoleIds = new ArrayList<>();
        for (Long roleId : request.getRoleIds()) {
            ResolvedRole resolvedRole = new ResolvedRole();
            resolvedRole.setRoleId(roleId);
            ctx.getRoles().add(resolvedRole);
            PcUserRole existing = findActiveUserRole(request.getTenantId(), request.getAbstractUserId(), roleId);
            if (existing != null) {
                PermissionAuditSupport.markDeleted(existing, existing.getId(), now);
                userRoleMapper.updateById(existing);
                changedRoleIds.add(roleId);
            }
        }
        if (!changedRoleIds.isEmpty()) {
            Map<String, Object> oldSnapshot = buildUserRoleBatchSnapshot(request, changedRoleIds);
            logChange(ctx, new ChangeLogParam()
                .setEntityType("batch_user_role")
                .setEntityId(request.getAbstractUserId())
                .setOperation("BATCH_REVOKE")
                .setOldSnapshot(oldSnapshot)
                .setAffectedAbstractUserIds(List.of(request.getAbstractUserId()))
                .setAffectedAbstractRoleIds(new ArrayList<>(changedRoleIds))
                .setChangeReason(request.getChangeReason())
                .setRequestId(request.getRequestId())
                .setChangeSource(resolveChangeSource(request.getChangeSource())));
            bumpVersion(ctx, "user_role", request.getAbstractUserId());
        }
    }

    @Override
    public PermissionSnapshot buildSnapshot(SnapshotRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractUserId() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        return doBuildSnapshot(request);
    }

    @Override
    public PermissionVersionResult queryVersion(PermissionVersionQueryRequest request) {
        if (request == null || request.getTenantId() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        PermissionContext ctx = createContext(request.getTenantId(), null, null, null, "API", "queryVersion");
        PcPermissionVersion current = permissionVersionService.queryCurrentVersion(ctx.getTenantId());
        PermissionVersionResult result = new PermissionVersionResult();
        result.setTenantId(current.getTenantId());
        result.setVersionNo(current.getVersionNo());
        result.setVersionToken(permissionVersionService.buildVersionToken(ctx.getTenantId(), current.getVersionNo()));
        result.setTriggerEntityType(current.getTriggerEntityType());
        result.setTriggerEntityId(current.getTriggerEntityId());
        result.setRemark(current.getRemark());
        return result;
    }

    private void validateCheckRequest(PermissionCheckRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractUserId() == null
            || request.getResourceEntityId() == null || request.getOperationPermissionId() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
    }

    private void validateGrantRequest(GrantPermissionRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractRoleId() == null
            || request.getResourceEntityId() == null || request.getOperationPermissionId() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
    }

    private void validateRevokeRequest(RevokePermissionRequest request) {
        if (request == null || request.getTenantId() == null || request.getAbstractRoleId() == null
            || request.getResourceEntityId() == null || request.getOperationPermissionId() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
    }

    private PcAbstractRole loadRole(Long tenantId, Long roleId) {
        PcAbstractRole role = abstractRoleMapper.selectOne(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, tenantId)
            .eq(PcAbstractRole::getId, roleId)
            .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (role == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "role not found");
        }
        return role;
    }

    private PcAbstractRole findRoleIncludingDeleted(Long tenantId, Long roleId) {
        if (tenantId == null || roleId == null) {
            return null;
        }
        return abstractRoleMapper.selectOne(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, tenantId)
            .eq(PcAbstractRole::getId, roleId));
    }

    private PcResourceEntity loadResource(Long tenantId, Long resourceId) {
        PcResourceEntity resource = resourceEntityMapper.selectOne(new LambdaQueryWrapper<PcResourceEntity>()
            .eq(PcResourceEntity::getTenantId, tenantId)
            .eq(PcResourceEntity::getId, resourceId)
            .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (resource == null) {
            throw new PermissionServiceException(PermissionErrorCode.RESOURCE_NOT_FOUND);
        }
        return resource;
    }

    private PcResourceEntity findResourceIncludingDeleted(Long tenantId, Long resourceId) {
        if (tenantId == null || resourceId == null) {
            return null;
        }
        return resourceEntityMapper.selectOne(new LambdaQueryWrapper<PcResourceEntity>()
            .eq(PcResourceEntity::getTenantId, tenantId)
            .eq(PcResourceEntity::getId, resourceId));
    }

    private PcOperationPermission loadOperation(Long tenantId, Long operationId) {
        PcOperationPermission operation = operationPermissionMapper.selectOne(new LambdaQueryWrapper<PcOperationPermission>()
            .eq(PcOperationPermission::getTenantId, tenantId)
            .eq(PcOperationPermission::getId, operationId)
            .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (operation == null) {
            throw new PermissionServiceException(PermissionErrorCode.OPERATION_NOT_FOUND);
        }
        return operation;
    }

    private void validateResourceOperationType(PcResourceEntity resource, PcOperationPermission operation) {
        if (operation.getResourceType() != null && !Objects.equals(operation.getResourceType(), resource.getResourceType())) {
            throw new PermissionServiceException(PermissionErrorCode.RESOURCE_OPERATION_TYPE_MISMATCH);
        }
    }

    private ValidationResult validateGrant(GrantPermissionRequest request, PcResourceEntity resource, PcOperationPermission operation) {
        if (operation.getResourceType() != null && !Objects.equals(operation.getResourceType(), resource.getResourceType())) {
            return ValidationResult.fail(PermissionErrorCode.RESOURCE_OPERATION_TYPE_MISMATCH.getMessage());
        }
        if (request.getConditionId() != null) {
            PcPermissionCondition condition = permissionConditionMapper.selectOne(new LambdaQueryWrapper<PcPermissionCondition>()
                .eq(PcPermissionCondition::getTenantId, request.getTenantId())
                .eq(PcPermissionCondition::getId, request.getConditionId())
                .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (condition == null || !PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus())) {
                throw new PermissionServiceException(PermissionErrorCode.CONDITION_NOT_APPROVED);
            }
        }
        return ValidationResult.ok();
    }

    private Set<Long> expandResourceIds(PcResourceEntity resource, InheritMode inheritMode) {
        Set<Long> ids = new HashSet<>();
        ids.add(resource.getId());
        if (inheritMode == InheritMode.NONE || resource.getPath() == null || resource.getPath().isBlank()) {
            return ids;
        }
        if (inheritMode == InheritMode.CHILDREN || inheritMode == InheritMode.BOTH) {
            resourceEntityMapper.selectList(new LambdaQueryWrapper<PcResourceEntity>()
                    .eq(PcResourceEntity::getTenantId, resource.getTenantId())
                    .likeRight(PcResourceEntity::getPath, resource.getPath() + "/")
                    .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED))
                .forEach(entity -> ids.add(entity.getId()));
        }
        if (inheritMode == InheritMode.PARENT || inheritMode == InheritMode.BOTH) {
            String[] pathIds = resource.getPath().split("/");
            for (String pathId : pathIds) {
                if (!pathId.isBlank()) {
                    ids.add(Long.valueOf(pathId));
                }
            }
        }
        return ids;
    }

    private List<PcRoleResourcePermission> loadRolePermissions(Long tenantId, Collection<Long> roleIds, Set<Long> resourceIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcRoleResourcePermission> query = new LambdaQueryWrapper<PcRoleResourcePermission>()
            .eq(PcRoleResourcePermission::getTenantId, tenantId)
            .in(PcRoleResourcePermission::getAbstractRoleId, roleIds)
            .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (resourceIds != null && !resourceIds.isEmpty()) {
            query.in(PcRoleResourcePermission::getResourceEntityId, resourceIds);
        }
        return roleResourcePermissionMapper.selectList(query);
    }

    private List<MatchedPermission> toMatchedPermissions(List<PcRoleResourcePermission> grants) {
        return grants.stream().map(grant -> {
            MatchedPermission permission = new MatchedPermission();
            permission.setPermissionId(grant.getId());
            permission.setRoleId(grant.getAbstractRoleId());
            permission.setResourceId(grant.getResourceEntityId());
            permission.setOperationId(grant.getOperationPermissionId());
            permission.setConditionId(grant.getConditionId());
            permission.setCanManage(grant.getCanManage());
            return permission;
        }).collect(Collectors.toList());
    }

    private Map<Long, PcOperationPermission> loadOperationsByIds(Set<Long> ids, Long tenantId) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return operationPermissionMapper.selectList(new LambdaQueryWrapper<PcOperationPermission>()
                .eq(PcOperationPermission::getTenantId, tenantId)
                .in(PcOperationPermission::getId, ids)
                .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED))
            .stream().collect(Collectors.toMap(PcOperationPermission::getId, operation -> operation));
    }

    private Map<Long, PcResourceEntity> loadResourcesByIds(Set<Long> ids, Long tenantId) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return resourceEntityMapper.selectList(new LambdaQueryWrapper<PcResourceEntity>()
                .eq(PcResourceEntity::getTenantId, tenantId)
                .in(PcResourceEntity::getId, ids)
                .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED))
            .stream().collect(Collectors.toMap(PcResourceEntity::getId, resource -> resource));
    }

    private Map<Long, PcPermissionCondition> loadConditionsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return permissionConditionMapper.selectBatchIds(ids).stream()
            .filter(condition -> PermissionConstants.NOT_DELETED.equals(condition.getDeleteFlag()))
            .collect(Collectors.toMap(PcPermissionCondition::getId, condition -> condition));
    }

    private List<MatchedPermission> filterByCondition(List<MatchedPermission> matchedPermissions, Map<String, Object> evalContext) {
        if (matchedPermissions.isEmpty()) {
            return matchedPermissions;
        }
        Set<Long> conditionIds = matchedPermissions.stream().map(MatchedPermission::getConditionId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PcPermissionCondition> conditions = loadConditionsByIds(conditionIds);
        return matchedPermissions.stream().filter(permission -> {
            if (permission.getConditionId() == null) {
                return true;
            }
            PcPermissionCondition condition = conditions.get(permission.getConditionId());
            return isConditionSatisfied(condition, evalContext);
        }).collect(Collectors.toList());
    }

    private boolean includeInSnapshot(MatchedPermission permission, Boolean includeConditional,
                                      Map<Long, PcPermissionCondition> conditions) {
        if (permission.getConditionId() == null) {
            return true;
        }
        if (!Boolean.TRUE.equals(includeConditional)) {
            return false;
        }
        PcPermissionCondition condition = conditions.get(permission.getConditionId());
        return condition != null && PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus());
    }

    private boolean isConditionSatisfied(PcPermissionCondition condition, Map<String, Object> evalContext) {
        if (condition == null || !PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus())) {
            return false;
        }
        if (condition.getExpression() == null || condition.getExpression().isBlank()) {
            return true;
        }
        Boolean resolved = resolveConditionValue(condition, evalContext);
        return Boolean.TRUE.equals(resolved);
    }

    private Boolean resolveConditionValue(PcPermissionCondition condition, Map<String, Object> evalContext) {
        String expression = condition.getExpression();
        if ("true".equalsIgnoreCase(expression)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(expression)) {
            return Boolean.FALSE;
        }
        if (evalContext == null || evalContext.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (condition.getCode() != null && !condition.getCode().isBlank()) {
            keys.add(condition.getCode());
            keys.add("condition:" + condition.getCode());
        }
        if (expression != null && !expression.isBlank()) {
            keys.add(expression);
            keys.add("condition:" + expression);
        }
        for (String key : keys) {
            Object value = evalContext.get(key);
            Boolean normalized = normalizeBoolean(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private Boolean normalizeBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            if ("true".equalsIgnoreCase(str)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(str)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private List<ConflictDetail> detectConflicts(Long tenantId, PcResourceEntity resource,
                                                 List<MatchedPermission> matchedPermissions) {
        if (matchedPermissions.isEmpty()) {
            return new ArrayList<>();
        }
        List<PcPermissionConflictRule> rules = permissionConflictRuleMapper.selectByTenantAndResourceType(tenantId, resource.getResourceType());
        Set<Long> matchedOpIds = matchedPermissions.stream().map(MatchedPermission::getOperationId).collect(Collectors.toSet());
        List<ConflictDetail> details = new ArrayList<>();
        for (PcPermissionConflictRule rule : rules) {
            boolean firstMatched = matchedOpIds.contains(rule.getFirstOperationPermissionId());
            boolean secondMatched = matchedOpIds.contains(rule.getSecondOperationPermissionId());
            boolean reverseMatched = secondMatched && firstMatched;
            if ((firstMatched && secondMatched) || reverseMatched) {
                ConflictDetail detail = new ConflictDetail();
                detail.setConflictRuleId(rule.getId());
                detail.setResourceId(resource.getId());
                detail.setFirstOperationId(rule.getFirstOperationPermissionId());
                detail.setSecondOperationId(rule.getSecondOperationPermissionId());
                details.add(detail);
            }
        }
        return details;
    }

    private List<ConflictDetail> detectConflictsForSnapshot(Long tenantId, List<MatchedPermission> matchedPermissions,
                                                            Map<Long, PcResourceEntity> resources) {
        Map<Long, List<MatchedPermission>> byResource = matchedPermissions.stream().collect(Collectors.groupingBy(MatchedPermission::getResourceId));
        List<ConflictDetail> conflicts = new ArrayList<>();
        for (Map.Entry<Long, List<MatchedPermission>> entry : byResource.entrySet()) {
            PcResourceEntity resource = resources.get(entry.getKey());
            if (resource == null) {
                continue;
            }
            Set<Long> opIds = entry.getValue().stream().map(MatchedPermission::getOperationId).collect(Collectors.toSet());
            for (PcPermissionConflictRule rule : permissionConflictRuleMapper.selectByTenantAndResourceType(tenantId, resource.getResourceType())) {
                if (opIds.contains(rule.getFirstOperationPermissionId()) && opIds.contains(rule.getSecondOperationPermissionId())) {
                    ConflictDetail detail = new ConflictDetail();
                    detail.setConflictRuleId(rule.getId());
                    detail.setResourceId(resource.getId());
                    detail.setFirstOperationId(rule.getFirstOperationPermissionId());
                    detail.setSecondOperationId(rule.getSecondOperationPermissionId());
                    conflicts.add(detail);
                }
            }
        }
        return conflicts;
    }

    private List<MatchedPermission> removeConflicted(List<MatchedPermission> matchedPermissions, List<ConflictDetail> conflicts, Long targetOperationId) {
        if (conflicts.isEmpty()) {
            return matchedPermissions;
        }
        Set<Long> deniedOps = conflicts.stream()
            .filter(conflict -> Objects.equals(conflict.getFirstOperationId(), targetOperationId)
                || Objects.equals(conflict.getSecondOperationId(), targetOperationId))
            .flatMap(conflict -> List.of(conflict.getFirstOperationId(), conflict.getSecondOperationId()).stream())
            .collect(Collectors.toSet());
        return matchedPermissions.stream().filter(permission -> !deniedOps.contains(permission.getOperationId())).collect(Collectors.toList());
    }

    private DependencyCheckResult checkDependencies(PermissionContext ctx, Long resourceEntityId, Long operationPermissionId,
                                                    List<Long> roleIds, Set<String> visited, int depth) {
        if (depth >= DEPTH_LIMIT) {
            return DependencyCheckResult.fail(List.of(new DependencyGap(resourceEntityId, operationPermissionId)));
        }
        String visitKey = resourceEntityId + ":" + operationPermissionId;
        if (!visited.add(visitKey)) {
            return DependencyCheckResult.ok();
        }
        List<PcResourceDependency> dependencies = resourceDependencyMapper.selectList(new LambdaQueryWrapper<PcResourceDependency>()
            .eq(PcResourceDependency::getTenantId, ctx.getTenantId())
            .eq(PcResourceDependency::getResourceEntityId, resourceEntityId)
            .and(wrapper -> wrapper.isNull(PcResourceDependency::getSourceOperationPermissionId)
                .or().eq(PcResourceDependency::getSourceOperationPermissionId, operationPermissionId))
            .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (dependencies.isEmpty()) {
            return DependencyCheckResult.ok();
        }
        List<DependencyGap> gaps = new ArrayList<>();
        for (PcResourceDependency dependency : dependencies) {
            PcOperationPermission requiredOperation = loadOperation(ctx.getTenantId(), dependency.getRequiredOperationPermissionId());
            List<PcRoleResourcePermission> grants = loadRolePermissions(ctx.getTenantId(), roleIds, Collections.singleton(dependency.getDependsOnResourceEntityId()));
            Map<Long, PcOperationPermission> operations = loadOperationsByIds(
                grants.stream().map(PcRoleResourcePermission::getOperationPermissionId).collect(Collectors.toSet()), ctx.getTenantId());
            List<MatchedPermission> matched = operationInheritanceService.filterByInheritance(
                toMatchedPermissions(grants), requiredOperation, operations);
            matched = filterByCondition(matched, ctx.getEvalContext());
            if (matched.isEmpty()) {
                gaps.add(new DependencyGap(dependency.getDependsOnResourceEntityId(), dependency.getRequiredOperationPermissionId()));
                continue;
            }
            DependencyCheckResult nested = checkDependencies(ctx, dependency.getDependsOnResourceEntityId(),
                dependency.getRequiredOperationPermissionId(), roleIds, visited, depth + 1);
            if (!nested.isSatisfied()) {
                gaps.addAll(nested.getGaps());
            }
        }
        return gaps.isEmpty() ? DependencyCheckResult.ok() : DependencyCheckResult.fail(gaps);
    }

    private PermissionSnapshot doBuildSnapshot(SnapshotRequest request) {
        PermissionContext ctx = createContext(request.getTenantId(), request.getAbstractUserId(), request.getBizDomainId(), null, "API", "buildSnapshot");
        List<ResolvedRole> roles = roleResolverService.resolve(ctx.getTenantId(), ctx.getUserId(), ctx.getBizDomainId());
        ctx.setRoles(roles);

        PermissionSnapshot snapshot = new PermissionSnapshot();
        snapshot.setTenantId(request.getTenantId());
        snapshot.setAbstractUserId(request.getAbstractUserId());
        snapshot.setBizDomainId(request.getBizDomainId());
        PcPermissionVersion version = permissionVersionService.queryCurrentVersion(request.getTenantId());
        snapshot.setVersionToken(permissionVersionService.buildVersionToken(request.getTenantId(), version.getVersionNo()));
        if (roles.isEmpty()) {
            return snapshot;
        }
        List<Long> roleIds = roles.stream().map(ResolvedRole::getRoleId).collect(Collectors.toList());
        List<MatchedPermission> matchedPermissions = permissionBridgeSupport.loadMatchedPermissions(request.getTenantId(), roleIds, null);
        Map<Long, PcResourceEntity> resources = permissionBridgeSupport.loadResourcesByIds(matchedPermissions.stream()
            .map(MatchedPermission::getResourceId).collect(Collectors.toSet()), request.getTenantId());
        Map<Long, PcOperationPermission> operations = permissionBridgeSupport.loadOperationsByIds(matchedPermissions.stream()
            .map(MatchedPermission::getOperationId).collect(Collectors.toSet()), request.getTenantId());
        Map<Long, PcPermissionCondition> conditions = permissionBridgeSupport.loadConditionsByIds(matchedPermissions.stream()
            .map(MatchedPermission::getConditionId).filter(Objects::nonNull).collect(Collectors.toSet()));
        ctx.setResources(resources);
        ctx.setOperations(operations);
        ctx.setConditions(conditions);
        permissionBridgeSupport.attachPermissionMetadata(matchedPermissions, resources, operations, conditions);
        matchedPermissions = permissionBridgeSupport.filterSnapshotPermissions(matchedPermissions, request.getIncludeConditional());

        Map<Integer, List<MatchedPermission>> byResourceType = matchedPermissions.stream()
            .collect(Collectors.groupingBy(MatchedPermission::getResourceType, LinkedHashMap::new, Collectors.toList()));
        List<ConflictDetail> conflicts = new ArrayList<>();
        List<SnapshotEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, List<MatchedPermission>> entry : byResourceType.entrySet()) {
            ResourceTypeHandler handler = resourceTypeHandlerRegistry.getHandler(ctx.getTenantId(), entry.getKey());
            List<ConflictDetail> typeConflicts = handler.getConflictDetector().detect(entry.getValue(), ctx);
            conflicts.addAll(typeConflicts);
            Set<String> conflictedKeys = permissionBridgeSupport.buildConflictKeys(typeConflicts);
            List<MatchedPermission> filtered = entry.getValue().stream()
                .filter(permission -> !conflictedKeys.contains(permission.getResourceId() + ":" + permission.getOperationId()))
                .collect(Collectors.toList());
            entries.addAll(handler.getSnapshotAssembler().assemble(filtered, ctx));
        }
        snapshot.setConflicts(conflicts);
        snapshot.setEntries(entries);
        return snapshot;
    }

    private PermissionContext createContext(Long tenantId, Long userId, Long bizDomainId, String requestId,
                                            String changeSource, String action) {
        PermissionContext ctx = new PermissionContext(tenantId, userId, bizDomainId, InheritMode.NONE, null);
        ctx.setConditionEvaluatorResolver(resourceType ->
            resourceTypeHandlerRegistry.getHandler(tenantId, resourceType).getConditionEvaluator());
        ctx.setRequestId(requestId);
        ctx.setChangeSource(changeSource);
        ctx.setAction(action);
        return ctx;
    }

    private void applyRoleToContext(PermissionContext ctx, PcAbstractRole role) {
        ctx.getRoles().add(toResolvedRole(role));
    }

    private void fillResourceOperation(PermissionContext ctx, PcResourceEntity resource, PcOperationPermission operation) {
        ctx.setResource(resource);
        ctx.setOperation(operation);
    }

    private ResolvedRole toResolvedRole(PcAbstractRole role) {
        ResolvedRole resolvedRole = new ResolvedRole();
        resolvedRole.setRoleId(role.getId());
        resolvedRole.setBizDomainId(role.getBizDomainId());
        resolvedRole.setRoleType(role.getRoleType());
        return resolvedRole;
    }

    private void logChange(PermissionContext ctx, ChangeLogParam param) {
        changeLogService.log(param
            .setTenantId(ctx.getTenantId())
            .setBizDomainId(param.getBizDomainId() == null ? ctx.getBizDomainId() : param.getBizDomainId())
            .setRequestId(param.getRequestId() == null ? ctx.getRequestId() : param.getRequestId())
            .setChangeSource(param.getChangeSource() == null ? ctx.getChangeSource() : param.getChangeSource()));
    }

    private void bumpVersion(PermissionContext ctx, String triggerEntityType, Long triggerEntityId) {
        PcPermissionVersion version = permissionVersionService.bumpVersion(
            ctx.getTenantId(), triggerEntityType, triggerEntityId, ctx.getVersionRemark());
        permissionWriteRefreshEventPublisher.publish(ctx, version);
    }

    private void logGrantChange(PermissionContext ctx, GrantPermissionRequest request, Long bizDomainId,
                                Long permissionId, Object oldSnapshot, PcRoleResourcePermission entity,
                                List<Long> affectedUserIds) {
        logChange(ctx, new ChangeLogParam()
            .setBizDomainId(bizDomainId)
            .setEntityType("role_resource_permission")
            .setEntityId(permissionId)
            .setOperation(oldSnapshot == null ? "INSERT" : "UPDATE")
            .setOldSnapshot(oldSnapshot)
            .setNewSnapshot(snapshotPermission(entity))
            .setAffectedAbstractUserIds(affectedUserIds)
            .setAffectedAbstractRoleIds(List.of(request.getAbstractRoleId()))
            .setChangeReason(request.getChangeReason())
            .setRequestId(request.getRequestId())
            .setChangeSource(resolveChangeSource(request.getChangeSource())));
    }

    private void logRevokeChange(PermissionContext ctx, RevokePermissionRequest request, Long bizDomainId,
                                 Long permissionId, Object oldSnapshot, List<Long> affectedUserIds) {
        logChange(ctx, new ChangeLogParam()
            .setBizDomainId(bizDomainId)
            .setEntityType("role_resource_permission")
            .setEntityId(permissionId)
            .setOperation("DELETE")
            .setOldSnapshot(oldSnapshot)
            .setNewSnapshot(null)
            .setAffectedAbstractUserIds(affectedUserIds)
            .setAffectedAbstractRoleIds(List.of(request.getAbstractRoleId()))
            .setChangeReason(request.getChangeReason())
            .setRequestId(request.getRequestId())
            .setChangeSource(resolveChangeSource(request.getChangeSource())));
    }

    private Map<String, Object> buildUserRoleBatchSnapshot(UserRoleBatchAssignRequest request, List<Long> roleIds) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("abstractUserId", request.getAbstractUserId());
        snapshot.put("roleIds", roleIds);
        snapshot.put("assignments", roleIds.stream().map(roleId -> {
            Map<String, Object> assignment = new HashMap<>();
            assignment.put("abstractRoleId", roleId);
            assignment.put("validFrom", request.getValidFrom());
            assignment.put("validTo", request.getValidTo());
            return assignment;
        }).collect(Collectors.toList()));
        snapshot.put("validFrom", request.getValidFrom());
        snapshot.put("validTo", request.getValidTo());
        return snapshot;
    }

    private Map<String, Object> buildUserRoleBatchSnapshot(UserRoleBatchRevokeRequest request, List<Long> roleIds) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("abstractUserId", request.getAbstractUserId());
        snapshot.put("roleIds", roleIds);
        snapshot.put("assignments", roleIds.stream().map(roleId -> {
            Map<String, Object> assignment = new HashMap<>();
            assignment.put("abstractRoleId", roleId);
            return assignment;
        }).collect(Collectors.toList()));
        return snapshot;
    }

    private PcRoleResourcePermission findActiveRolePermission(Long tenantId, Long roleId, Long resourceId, Long operationId) {
        return roleResourcePermissionMapper.selectOne(new LambdaQueryWrapper<PcRoleResourcePermission>()
            .eq(PcRoleResourcePermission::getTenantId, tenantId)
            .eq(PcRoleResourcePermission::getAbstractRoleId, roleId)
            .eq(PcRoleResourcePermission::getResourceEntityId, resourceId)
            .eq(PcRoleResourcePermission::getOperationPermissionId, operationId)
            .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
    }

    private PcRoleResourcePermission findLatestRolePermission(Long tenantId, Long roleId, Long resourceId, Long operationId) {
        List<PcRoleResourcePermission> records = roleResourcePermissionMapper.selectList(new LambdaQueryWrapper<PcRoleResourcePermission>()
            .eq(PcRoleResourcePermission::getTenantId, tenantId)
            .eq(PcRoleResourcePermission::getAbstractRoleId, roleId)
            .eq(PcRoleResourcePermission::getResourceEntityId, resourceId)
            .eq(PcRoleResourcePermission::getOperationPermissionId, operationId)
            .orderByDesc(PcRoleResourcePermission::getId));
        return records.isEmpty() ? null : records.get(0);
    }

    private PcUserRole findActiveUserRole(Long tenantId, Long userId, Long roleId) {
        return userRoleMapper.selectOne(new LambdaQueryWrapper<PcUserRole>()
            .eq(PcUserRole::getTenantId, tenantId)
            .eq(PcUserRole::getAbstractUserId, userId)
            .eq(PcUserRole::getAbstractRoleId, roleId)
            .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
    }

    private PcUserRole findLatestUserRole(Long tenantId, Long userId, Long roleId) {
        List<PcUserRole> records = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
            .eq(PcUserRole::getTenantId, tenantId)
            .eq(PcUserRole::getAbstractUserId, userId)
            .eq(PcUserRole::getAbstractRoleId, roleId)
            .orderByDesc(PcUserRole::getId));
        return records.isEmpty() ? null : records.get(0);
    }

    private boolean sameGrantState(PcRoleResourcePermission existing, GrantPermissionRequest request) {
        return Objects.equals(Boolean.TRUE.equals(request.getCanManage()), Boolean.TRUE.equals(existing.getCanManage()))
            && Objects.equals(request.getConditionId(), existing.getConditionId())
            && PermissionConstants.NOT_DELETED.equals(existing.getDeleteFlag());
    }

    private boolean sameUserRoleState(PcUserRole existing, UserRoleBatchAssignRequest request) {
        return Objects.equals(existing.getValidFrom(), request.getValidFrom())
            && Objects.equals(existing.getValidTo(), request.getValidTo())
            && PermissionConstants.NOT_DELETED.equals(existing.getDeleteFlag());
    }

    private void validateUserRoleValidityWindow(LocalDateTime validFrom, LocalDateTime validTo) {
        if (validFrom != null && validTo != null && validFrom.isAfter(validTo)) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "validFrom must be <= validTo");
        }
    }

    private Long resolveBatchBizDomainId(Set<Long> bizDomainIds) {
        return bizDomainIds.size() == 1 ? bizDomainIds.iterator().next() : null;
    }

    private Long resolveRevokeBizDomainId(PcAbstractRole role, PcResourceEntity resource) {
        Long roleBizDomainId = role == null ? null : role.getBizDomainId();
        Long resourceBizDomainId = resource == null ? null : resource.getBizDomainId();
        if (roleBizDomainId != null && resourceBizDomainId != null && !Objects.equals(roleBizDomainId, resourceBizDomainId)) {
            return null;
        }
        return roleBizDomainId != null ? roleBizDomainId : resourceBizDomainId;
    }

    private List<Long> resolveAffectedUserIdsForRole(Long tenantId, Long roleId, LocalDateTime now) {
        if (tenantId == null || roleId == null) {
            return Collections.emptyList();
        }
        List<PcUserRole> assignments = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
            .eq(PcUserRole::getTenantId, tenantId)
            .eq(PcUserRole::getAbstractRoleId, roleId)
            .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED)
            .and(wrapper -> wrapper.isNull(PcUserRole::getValidFrom).or().le(PcUserRole::getValidFrom, now))
            .and(wrapper -> wrapper.isNull(PcUserRole::getValidTo).or().ge(PcUserRole::getValidTo, now))
            .orderByAsc(PcUserRole::getAbstractUserId));
        if (assignments == null || assignments.isEmpty()) {
            return Collections.emptyList();
        }
        return assignments.stream()
            .map(PcUserRole::getAbstractUserId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    private void restorePermission(PcRoleResourcePermission existing, GrantPermissionRequest request, LocalDateTime now) {
        existing.setCanManage(Boolean.TRUE.equals(request.getCanManage()));
        existing.setConditionId(request.getConditionId());
        existing.setDeleteFlag(PermissionConstants.NOT_DELETED);
        existing.setDeletedAt(null);
        existing.setDeletedBy(null);
        existing.setUpdatedAt(now);
    }

    private Map<String, Object> buildRolePermissionBatchSnapshot(Long abstractRoleId, List<Map<String, Object>> items) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("abstractRoleId", abstractRoleId);
        snapshot.put("permissions", items);
        snapshot.put("resourceEntityIds", items.stream()
            .map(item -> (Long) item.get("resourceEntityId"))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList()));
        snapshot.put("operationPermissionIds", items.stream()
            .map(item -> (Long) item.get("operationPermissionId"))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList()));
        return snapshot;
    }

    private Map<String, Object> buildRolePermissionBatchItem(RolePermissionBatchGrantRequest.RolePermissionGrantItem item) {
        Map<String, Object> snapshotItem = new HashMap<>();
        snapshotItem.put("resourceEntityId", item.getResourceEntityId());
        snapshotItem.put("operationPermissionId", item.getOperationPermissionId());
        snapshotItem.put("canManage", item.getCanManage());
        snapshotItem.put("conditionId", item.getConditionId());
        return snapshotItem;
    }

    private Map<String, Object> buildRolePermissionBatchItem(RolePermissionBatchRevokeRequest.RolePermissionRevokeItem item) {
        Map<String, Object> snapshotItem = new HashMap<>();
        snapshotItem.put("resourceEntityId", item.getResourceEntityId());
        snapshotItem.put("operationPermissionId", item.getOperationPermissionId());
        return snapshotItem;
    }

    private Map<String, Object> snapshotPermission(PcRoleResourcePermission permission) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", permission.getId());
        snapshot.put("tenantId", permission.getTenantId());
        snapshot.put("abstractRoleId", permission.getAbstractRoleId());
        snapshot.put("resourceEntityId", permission.getResourceEntityId());
        snapshot.put("operationPermissionId", permission.getOperationPermissionId());
        snapshot.put("canManage", permission.getCanManage());
        snapshot.put("conditionId", permission.getConditionId());
        snapshot.put("deleteFlag", permission.getDeleteFlag());
        return snapshot;
    }

    private String resolveChangeSource(String changeSource) {
        return changeSource == null || changeSource.isBlank() ? "API" : changeSource;
    }
}
