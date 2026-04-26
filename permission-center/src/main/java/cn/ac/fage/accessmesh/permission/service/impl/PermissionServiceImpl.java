package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import cn.ac.fage.accessmesh.permission.service.domain.*;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    private final AbstractUserMapper abstractUserMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final UserRoleDomainService userRoleDomainService;
    private final PermissionConflictDomainService permissionConflictDomainService;
    private final PermissionConditionDomainService permissionConditionDomainService;
    private final RolePermissionDomainService rolePermissionDomainService;

    public PermissionServiceImpl(AbstractUserMapper abstractUserMapper,
                                 ResourceEntityMapper resourceEntityMapper,
                                 OperationPermissionMapper operationPermissionMapper,
                                 RoleResourcePermissionMapper rolePermMapper,
                                 UserRoleDomainService userRoleDomainService,
                                 PermissionConflictDomainService permissionConflictDomainService,
                                 PermissionConditionDomainService permissionConditionDomainService,
                                 RolePermissionDomainService rolePermissionDomainService) {
        this.abstractUserMapper = abstractUserMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.rolePermMapper = rolePermMapper;
        this.userRoleDomainService = userRoleDomainService;
        this.permissionConflictDomainService = permissionConflictDomainService;
        this.permissionConditionDomainService = permissionConditionDomainService;
        this.rolePermissionDomainService = rolePermissionDomainService;
    }

    @Override
    public PermCheckResp checkPermission(PermCheckReq req) {
        // Simplified SDK entry: maps to full auth chain
        try {
            AuthCheckReq fullReq = new AuthCheckReq(
                null, // tenantId extracted from token in gateway
                req.userId(),
                parseLongOrNull(req.resourceId()),
                resolveOperationId(req),
                null, null, null, null
            );
            AuthCheckResp resp = check(fullReq);
            return resp.allowed() ? PermCheckResp.allow() : PermCheckResp.deny(resp.reason());
        } catch (Exception e) {
            log.warn("Permission check failed for user={} resource={}: {}",
                req.userId(), req.resourceId(), e.getMessage());
            return PermCheckResp.deny("CHECK_ERROR");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthCheckResp check(AuthCheckReq req) {
        // Step 1: User status check
        AbstractUser user = abstractUserMapper.selectOneById(req.abstractUserId());
        if (user == null || user.getDeleteFlag() != 0L) {
            return AuthCheckResp.deny("USER_NOT_FOUND");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return AuthCheckResp.deny("USER_DISABLED");
        }

        // Step 2: Resolve effective roles (with cache)
        Set<Long> effectiveRoleIds = userRoleDomainService
            .resolveEffectiveRoles(req.tenantId(), req.abstractUserId(), req.bizDomainId());
        if (effectiveRoleIds.isEmpty()) {
            return AuthCheckResp.deny("NO_ROLE");
        }

        // Step 3: Role mutex filter
        Set<Long> validRoleIds = permissionConflictDomainService
            .filterRoleMutex(req.tenantId(), effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return AuthCheckResp.deny("NO_ROLE");
        }

        // Step 4: Query authorization entries
        List<RolePermSnapshot.RolePermEntry> entries = queryMatchedEntries(
            req.tenantId(), validRoleIds, req.resourceEntityId(),
            req.operationPermissionId(), req.inheritMode());
        if (entries.isEmpty()) {
            return AuthCheckResp.deny("NO_PERMISSION");
        }

        // Step 5: Condition evaluation
        Map<String, Object> context = new HashMap<>();
        if (req.clientIp() != null) {
            context.put("clientIp", req.clientIp());
        }
        List<RolePermSnapshot.RolePermEntry> passedEntries = permissionConditionDomainService
            .evaluate(req.tenantId(), entries, context);
        if (passedEntries.isEmpty()) {
            return AuthCheckResp.deny("CONDITION_NOT_MET");
        }

        // Step 6: Permission mutex filter
        List<RolePermSnapshot.RolePermEntry> finalEntries = permissionConflictDomainService
            .filterPermMutex(req.tenantId(), passedEntries);
        if (finalEntries.isEmpty()) {
            return AuthCheckResp.deny("PERMISSION_CONFLICT");
        }

        ResourceEntity resource = resourceEntityMapper.selectOneById(req.resourceEntityId());
        OperationPermission op = operationPermissionMapper.selectOneById(req.operationPermissionId());
        return AuthCheckResp.allow(
            req.resourceEntityId(),
            resource != null ? resource.getCode() : null,
            op != null ? op.getCode() : null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BatchAuthCheckResp batchCheck(BatchAuthCheckReq req) {
        List<AuthCheckItemResult> results = new ArrayList<>();
        for (BatchAuthCheckReq.AuthCheckItem item : req.items()) {
            AuthCheckReq singleReq = new AuthCheckReq(
                req.tenantId(), req.abstractUserId(),
                item.resourceEntityId(), item.operationPermissionId(),
                item.bizDomainId(), item.codeType(), item.inheritMode(),
                req.clientIp()
            );
            AuthCheckResp resp = check(singleReq);
            results.add(new AuthCheckItemResult(
                item.resourceEntityId(), item.operationPermissionId(),
                resp.allowed(), resp.reason()
            ));
        }
        return new BatchAuthCheckResp(req.tenantId(), req.abstractUserId(), results);
    }

    private List<RolePermSnapshot.RolePermEntry> queryMatchedEntries(Long tenantId, Set<Long> roleIds,
                                                                      Long resourceEntityId, Long operationPermissionId,
                                                                      String inheritMode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
            .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));

        // If inheritMode is set, expand resource tree
        if ("PARENT".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            List<Long> parentIds = getAncestorIds(tenantId, resourceEntityId);
            if (!parentIds.isEmpty()) {
                qw.and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(parentIds));
            }
        }
        if ("CHILDREN".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            List<Long> childIds = getDescendantIds(tenantId, resourceEntityId);
            if (!childIds.isEmpty()) {
                qw.or(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(childIds));
            }
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(qw);

        // Filter by operation bitwise match
        OperationPermission targetOp = operationPermissionMapper.selectOneById(operationPermissionId);
        if (targetOp == null) {
            return Collections.emptyList();
        }

        Map<Long, OperationPermission> opCache = new HashMap<>();
        opCache.put(operationPermissionId, targetOp);

        List<RolePermSnapshot.RolePermEntry> entries = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = opCache.computeIfAbsent(
                perm.getOperationPermissionId(), operationPermissionMapper::selectOneById);
            if (grantedOp == null) continue;

            // Bitwise match: (grantedOp.binaryBit | grantedOp.inheritMask) & targetOp.binaryBit != 0
            long effectiveBits = (grantedOp.getBinaryBit() != null ? grantedOp.getBinaryBit() : 0L)
                | (grantedOp.getInheritMask() != null ? grantedOp.getInheritMask() : 0L);
            long targetBit = targetOp.getBinaryBit() != null ? targetOp.getBinaryBit() : 0L;
            if ((effectiveBits & targetBit) != 0) {
                entries.add(new RolePermSnapshot.RolePermEntry(
                    perm.getResourceEntityId(), null, perm.getResourceType(),
                    perm.getOperationPermissionId(), grantedOp.getCode(), null,
                    perm.getCanManage(), perm.getConditionId(), perm.getConditionId() != null,
                    perm.getDependOn()
                ));
            }
        }
        return entries;
    }

    private List<Long> getAncestorIds(Long tenantId, Long resourceEntityId) {
        List<Long> ids = new ArrayList<>();
        Long current = resourceEntityId;
        while (current != null) {
            ResourceEntity entity = resourceEntityMapper.selectOneById(current);
            if (entity == null || entity.getDeleteFlag() != 0L || !entity.getTenantId().equals(tenantId)) break;
            if (entity.getParentId() != null) {
                ids.add(entity.getParentId());
                current = entity.getParentId();
            } else {
                break;
            }
        }
        return ids;
    }

    private List<Long> getDescendantIds(Long tenantId, Long resourceEntityId) {
        List<Long> ids = new ArrayList<>();
        collectDescendants(tenantId, resourceEntityId, ids);
        return ids;
    }

    private void collectDescendants(Long tenantId, Long parentId, List<Long> result) {
        List<ResourceEntity> children = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .where(RESOURCE_ENTITY.PARENT_ID.eq(parentId))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        for (ResourceEntity child : children) {
            result.add(child.getId());
            collectDescendants(tenantId, child.getId(), result);
        }
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return null; }
    }

    private Long resolveOperationId(PermCheckReq req) {
        // If resourceId is an operation permission code, look it up
        if (req.permissionKey() != null && !req.permissionKey().isBlank()) {
            OperationPermission op = operationPermissionMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION.CODE.eq(req.permissionKey()))
            );
            if (op != null) return op.getId();
        }
        return null;
    }
}
