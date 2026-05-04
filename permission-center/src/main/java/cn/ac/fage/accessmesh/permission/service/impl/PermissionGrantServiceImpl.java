package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionChildrenReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionRemoveChildReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantService;
import cn.ac.fage.accessmesh.permission.service.domain.*;
import cn.ac.fage.accessmesh.permission.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.DomainConfigTableDef.DOMAIN_CONFIG;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConditionTableDef.PERMISSION_CONDITION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

@Service
public class PermissionGrantServiceImpl implements PermissionGrantService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGrantServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final DomainConfigMapper domainConfigMapper;
    private final PermissionConditionMapper permissionConditionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final RolePermissionDomainService rolePermissionDomainService;
    private final PermissionVersionDomainService permissionVersionDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final OperationLogDomainService operationLogDomainService;
    private final UserRoleDomainService userRoleDomainService;
    private final ResourceDependencyDomainService resourceDependencyDomainService;
    private final TypeResolutionService typeResolutionService;
    private final AuthorizationService authorizationService;
    private final OperationPermissionDomainService operationPermissionDomainService;
    private final AbstractRoleDomainService abstractRoleDomainService;
    private final ResourcePermissionValidator permissionValidator;

    public PermissionGrantServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                      ResourceEntityMapper resourceEntityMapper,
                                      OperationPermissionMapper operationPermissionMapper,
                                      DomainConfigMapper domainConfigMapper,
                                      PermissionConditionMapper permissionConditionMapper,
                                      RoleResourcePermissionMapper rolePermMapper,
                                      RolePermissionDomainService rolePermissionDomainService,
                                      PermissionVersionDomainService permissionVersionDomainService,
                                      PermissionChangeDomainService permissionChangeDomainService,
                                      OperationLogDomainService operationLogDomainService,
                                      UserRoleDomainService userRoleDomainService,
                                      ResourceDependencyDomainService resourceDependencyDomainService,
                                      TypeResolutionService typeResolutionService,
                                      AuthorizationService authorizationService,
                                      OperationPermissionDomainService operationPermissionDomainService,
                                      AbstractRoleDomainService abstractRoleDomainService,
                                      ResourcePermissionValidator permissionValidator) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.domainConfigMapper = domainConfigMapper;
        this.permissionConditionMapper = permissionConditionMapper;
        this.rolePermMapper = rolePermMapper;
        this.rolePermissionDomainService = rolePermissionDomainService;
        this.permissionVersionDomainService = permissionVersionDomainService;
        this.permissionChangeDomainService = permissionChangeDomainService;
        this.operationLogDomainService = operationLogDomainService;
        this.userRoleDomainService = userRoleDomainService;
        this.resourceDependencyDomainService = resourceDependencyDomainService;
        this.typeResolutionService = typeResolutionService;
        this.authorizationService = authorizationService;
        this.operationPermissionDomainService = operationPermissionDomainService;
        this.abstractRoleDomainService = abstractRoleDomainService;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RolePermissionItemResp> batchGrant(Long tenantId, RoleGrantReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode()
        );
        if (roleId == null) {
            throw new IllegalArgumentException("Role not found by business key");
        }

        // Operator authorization check - MANAGE permission on role
        Long operatorId = OperatorContext.getOperatorId();
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationType.MANAGE);

        boolean hasChanges = (req.add() != null && !req.add().isEmpty())
            || (req.update() != null && !req.update().isEmpty())
            || (req.remove() != null && !req.remove().isEmpty());
        if (!hasChanges) {
            throw new IllegalArgumentException("At least one of add/update/remove is required");
        }

        AbstractRole role = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(roleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (role == null) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }
        if (role.getStatus() != PermissionConstants.ENABLED_STATUS) {
            throw new IllegalStateException("Role is disabled: " + roleId);
        }

        List<RoleGrantReq.GrantAddItem> addItems = req.add() == null ? List.of() : req.add();
        List<RoleGrantReq.GrantUpdateItem> updateItems = req.update() == null ? List.of() : req.update();
        List<Long> removeItems = req.remove() == null ? List.of() : req.remove();

        // ===== SECURITY CHECK: Validate grant permissions for each add item =====
        // Operator must have the permission AND canGrant=true to grant it to others
        if (!addItems.isEmpty()) {
            Set<AuthorizationService.GrantCheckKey> grantKeys = addItems.stream()
                .map(item -> new AuthorizationService.GrantCheckKey(
                    item.resourceTypeCode(),
                    item.resourceCode(),
                    item.operationCode(),
                    Boolean.TRUE.equals(item.scopeAll())
                ))
                .collect(Collectors.toSet());

            Map<String, AuthorizationService.GrantCheckResult> grantResults =
                authorizationService.checkGrantPermissionsBatch(tenantId, operatorId, grantKeys, req.domainCode());

            // Check each add item
            for (RoleGrantReq.GrantAddItem item : addItems) {
                String permKey = buildGrantKey(item);
                AuthorizationService.GrantCheckResult result = grantResults.get(permKey);

                if (result == null || !result.canGrant()) {
                    String reason = result != null ? result.reason() : "UNKNOWN";
                    throw new SecurityException(String.format(
                        "Operator %s cannot grant permission %s:%s:%s (scopeAll=%s). Reason: %s. " +
                        "Operator must have the permission with canGrant=true.",
                        operatorId, item.resourceTypeCode(),
                        item.resourceCode() == null ? "*" : item.resourceCode(),
                        item.operationCode(), item.scopeAll(), reason
                    ));
                }
            }
        }

        // ===== SECURITY CHECK: Validate grant permissions for update items that change canGrant =====
        // If operator wants to set canGrant=true, they must already have canGrant=true on that permission
        // Collect all updateItem IDs that need canGrant=true check
        Set<Long> updatePermIds = updateItems.stream()
            .filter(item -> item.id() != null && Boolean.TRUE.equals(item.canGrant()))
            .map(RoleGrantReq.GrantUpdateItem::id)
            .collect(Collectors.toSet());

        if (!updatePermIds.isEmpty()) {
            // Batch query existing permissions
            List<RoleResourcePermission> existingPerms = rolePermMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                    .and(ROLE_RESOURCE_PERMISSION.ID.in(updatePermIds))
                    .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            );
            Map<Long, RoleResourcePermission> existingPermMap = existingPerms.stream()
                .collect(Collectors.toMap(RoleResourcePermission::getId, p -> p));

            // Batch query resource entities
            Set<Long> resourceIds = existingPerms.stream()
                .map(RoleResourcePermission::getResourceEntityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, ResourceEntity> resourceMap = resourceIds.isEmpty() ? Map.of()
                : resourceEntityMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(RESOURCE_ENTITY.ID.in(resourceIds))
                        .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

            // Batch query operation permissions
            Set<Long> operationIds = existingPerms.stream()
                .map(RoleResourcePermission::getOperationPermissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, OperationPermission> operationMap = operationIds.isEmpty() ? Map.of()
                : operationPermissionMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(OPERATION_PERMISSION.ID.in(operationIds))
                ).stream().collect(Collectors.toMap(OperationPermission::getId, op -> op));

            // Batch resolve resource type codes
            Set<Integer> resourceTypeValues = existingPerms.stream()
                .map(RoleResourcePermission::getResourceType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

            // Check each update item
            for (RoleGrantReq.GrantUpdateItem updateItem : updateItems) {
                if (updateItem.id() == null || !Boolean.TRUE.equals(updateItem.canGrant())) {
                    continue;
                }

                RoleResourcePermission existing = existingPermMap.get(updateItem.id());
                if (existing == null) {
                    continue;
                }

                // Get resource info from pre-loaded maps
                ResourceEntity resource = existing.getResourceEntityId() == null ? null
                    : resourceMap.get(existing.getResourceEntityId());
                OperationPermission operation = operationMap.get(existing.getOperationPermissionId());
                String resourceTypeCode = resourceTypeCodeMap.get(existing.getResourceType());

                // Check if operator can grant this permission
                boolean canGrant = authorizationService.canGrantPermission(
                    tenantId, operatorId, resourceTypeCode,
                    resource == null ? null : resource.getCode(),
                    operation == null ? null : operation.getCode(),
                    Boolean.TRUE.equals(existing.getScopeAll()),
                    req.domainCode()
                );

                if (!canGrant) {
                    throw new SecurityException(String.format(
                        "Operator %s cannot set canGrant=true on permission id=%s. " +
                        "Operator must have the permission with canGrant=true.",
                        operatorId, updateItem.id()
                    ));
                }
            }
        }
        // ===== END SECURITY CHECK =====

        // ===== Batch resolution to avoid N+1 queries =====
        // 1. Batch resolve resource IDs
        List<ResourceResolveRequest> resourceRequests = addItems.stream()
            .filter(item -> !Boolean.TRUE.equals(item.scopeAll()))
            .map(item -> new ResourceResolveRequest(
                item.resourceTypeCode(),
                item.resourceCode(),
                item.codeType(),
                req.domainCode()))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resourceIdMap = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        // 2. Batch resolve operation IDs by resource type
        Map<String, Set<String>> operationCodesByType = addItems.stream()
            .collect(Collectors.groupingBy(
                RoleGrantReq.GrantAddItem::resourceTypeCode,
                Collectors.mapping(RoleGrantReq.GrantAddItem::operationCode, Collectors.toSet())
            ));
        Map<String, Map<String, Long>> operationIdMapByType = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : operationCodesByType.entrySet()) {
            Map<String, Long> opMap = typeResolutionService.batchResolveOperationIds(tenantId, entry.getKey(), entry.getValue());
            operationIdMapByType.put(entry.getKey(), opMap);
        }

        // 3. Batch resolve resource type values
        Set<String> resourceTypeCodes = addItems.stream()
            .map(RoleGrantReq.GrantAddItem::resourceTypeCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, Integer> resourceTypeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", resourceTypeCodes);

        Set<Long> resourceIds = resourceIdMap.values().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Batch load resources to avoid N+1 query
        Map<Long, ResourceEntity> resourceById = resourceIds.isEmpty() ? Map.of()
            : resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_ENTITY.ID.in(resourceIds))
                    .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        // Validate all resources exist
        for (Long resId : resourceIds) {
            if (!resourceById.containsKey(resId)) {
                throw new IllegalArgumentException("Resource not found: " + resId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<RoleResourcePermission> toInsert = new ArrayList<>();
        for (RoleGrantReq.GrantAddItem item : addItems) {
            boolean scopeAll = Boolean.TRUE.equals(item.scopeAll());
            Long resourceEntityId = null;
            if (!scopeAll) {
                ResourceResolveKey resKey = new ResourceResolveKey(
                    item.resourceTypeCode(), item.resourceCode(), item.codeType(), req.domainCode());
                resourceEntityId = resourceIdMap.get(resKey);
                if (resourceEntityId == null) {
                    throw new IllegalArgumentException("resource not found: " + item.resourceCode());
                }
            }

            Map<String, Long> opMap = operationIdMapByType.getOrDefault(item.resourceTypeCode(), Map.of());
            Long operationId = opMap.get(item.operationCode());
            OperationPermission operation = operationId != null
                ? operationPermissionDomainService.selectValidById(tenantId, operationId) : null;
            if (operation == null) {
                throw new IllegalArgumentException("operationCode not found: " + item.operationCode());
            }
            Long conditionId = null;
            if (item.conditionCode() != null && !item.conditionCode().isBlank()) {
                PermissionCondition condition = permissionConditionMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                        .and(PERMISSION_CONDITION.CODE.eq(item.conditionCode()))
                        .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
                );
                if (condition == null || condition.getDeleteFlag() != 0L || !tenantId.equals(condition.getTenantId())) {
                    throw new IllegalArgumentException("conditionCode not found: " + item.conditionCode());
                }
                conditionId = condition.getId();
            }
            RoleResourcePermission rp = new RoleResourcePermission();
            rp.setTenantId(tenantId);
            rp.setAbstractRoleId(roleId);
            rp.setResourceEntityId(scopeAll ? null : resourceEntityId);
            rp.setOperationPermissionId(operationId);
            Integer finalResourceType = resourceTypeValueMap.get(item.resourceTypeCode());
            if (finalResourceType == null) {
                throw new IllegalArgumentException("resourceTypeCode not found: " + item.resourceTypeCode());
            }
            if (operation.getResourceType() != null && !operation.getResourceType().equals(finalResourceType)) {
                throw new IllegalArgumentException("resourceType does not match operationPermission resource type");
            }
            rp.setResourceType(finalResourceType);
            rp.setScopeAll(scopeAll);
            rp.setCanGrant(item.canGrant() != null ? item.canGrant() : false);
            rp.setConditionId(conditionId);
            rp.setGrantSource(GrantSource.MANUAL.getValue());
            rp.setCreatedAt(now);
            rp.setUpdatedAt(now);
            rp.setDeleteFlag(0L);
            toInsert.add(rp);
        }

        List<RoleResourcePermission> autoGranted = resourceDependencyDomainService
            .autoGrantForInsert(tenantId, roleId, toInsert);
        toInsert.addAll(autoGranted);

        // Batch soft delete permissions to avoid N+1 query
        if (!removeItems.isEmpty()) {
            rolePermissionDomainService.revokePermissions(tenantId, roleId, removeItems);
        }

        // ===== Batch process update items to avoid N+1 queries =====
        Set<Long> updateIds = updateItems.stream()
            .map(RoleGrantReq.GrantUpdateItem::id)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (!updateIds.isEmpty()) {
            // Batch query existing permissions
            List<RoleResourcePermission> existingPerms = rolePermMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                    .and(ROLE_RESOURCE_PERMISSION.ID.in(updateIds))
                    .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            );
            Map<Long, RoleResourcePermission> existingPermMap = existingPerms.stream()
                .collect(Collectors.toMap(RoleResourcePermission::getId, p -> p));

            // Batch query permission conditions
            Set<String> conditionCodes = updateItems.stream()
                .map(RoleGrantReq.GrantUpdateItem::conditionCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
            Map<String, PermissionCondition> conditionMap = conditionCodes.isEmpty() ? Map.of()
                : permissionConditionMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                        .and(PERMISSION_CONDITION.CODE.in(conditionCodes))
                        .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
                ).stream().collect(Collectors.toMap(PermissionCondition::getCode, c -> c));

            // Process each update item with pre-loaded data
            for (RoleGrantReq.GrantUpdateItem updateItem : updateItems) {
                if (updateItem.id() == null) {
                    continue;
                }
                RoleResourcePermission existing = existingPermMap.get(updateItem.id());
                if (existing == null) {
                    continue;
                }
                if (updateItem.canGrant() != null) {
                    existing.setCanGrant(updateItem.canGrant());
                }
                if (updateItem.conditionCode() != null) {
                    if (updateItem.conditionCode().isBlank()) {
                        existing.setConditionId(null);
                    } else {
                        PermissionCondition condition = conditionMap.get(updateItem.conditionCode());
                        if (condition == null) {
                            throw new IllegalArgumentException("conditionCode not found: " + updateItem.conditionCode());
                        }
                        existing.setConditionId(condition.getId());
                    }
                }
                existing.setUpdatedAt(now);
                rolePermMapper.update(existing);
            }
        }

        if (!toInsert.isEmpty()) {
            rolePermMapper.insertBatch(toInsert);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permissionVersionDomainService.increment(tenantId, roleId);
                    userRoleDomainService.invalidateRoleCacheByRole(tenantId, roleId);
                    operationLogDomainService.asyncRecord(
                        "role_resource_permission", "BATCH_GRANT",
                        "abstract_role", roleId,
                        "save granted role perms add=" + addItems.size() + ", update=" + updateItems.size() + ", remove=" + removeItems.size(),
                        operatorId, null, null, tenantId
                    );
                }
            });
        }

        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        return toItemRespList(tenantId, allPerms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRevoke(Long tenantId, BatchRevokeReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode()
        );
        if (roleId == null) {
            throw new IllegalArgumentException("Role not found by business key");
        }

        // Operator authorization check
        Long operatorId = OperatorContext.getOperatorId();
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationType.MANAGE);

        List<Long> permissionIds = req.permissionIds() == null ? List.of() : req.permissionIds();
        LocalDateTime now = LocalDateTime.now();

        for (Long permId : permissionIds) {
            rolePermissionDomainService.revokePermissionWithCascade(tenantId, roleId, permId, now);
        }

        // Increment version and invalidate caches after commit
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permissionVersionDomainService.increment(tenantId, roleId);
                    userRoleDomainService.invalidateRoleCacheByRole(tenantId, roleId);
                    operationLogDomainService.asyncRecord(
                        "role_resource_permission", "BATCH_REVOKE",
                        "abstract_role", roleId,
                        "Revoked " + permissionIds.size() + " permissions from role " + roleId,
                        operatorId, null, null, tenantId
                    );
                }
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionItemResp> listPermissions(Long tenantId, RolePermissionListReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode()
        );
        if (roleId == null) {
            return List.of();
        }

        // Operator authorization check - VIEW permission required
        Long operatorId = OperatorContext.getOperatorId();
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationType.VIEW)) {
            return List.of();
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        return toItemRespList(tenantId, perms);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionItemResp> listChildren(Long tenantId, RolePermissionChildrenReq req) {
        RoleResourcePermission parent = rolePermissionDomainService.selectValidById(tenantId, null, req.permissionId());
        if (parent == null) {
            return List.of();
        }

        // Operator authorization check - VIEW permission required
        Long operatorId = OperatorContext.getOperatorId();
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, parent.getAbstractRoleId(), OperationType.VIEW)) {
            return List.of();
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(parent.getAbstractRoleId()))
                .and(ROLE_RESOURCE_PERMISSION.DEPEND_ON.eq(req.permissionId()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        return toItemRespList(tenantId, perms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RolePermissionItemResp> addChildren(Long tenantId, RolePermissionAddChildReq req) {
        RoleResourcePermission parent = rolePermissionDomainService.selectValidById(tenantId, null, req.parentPermissionId());
        if (parent == null) {
            throw new IllegalArgumentException("parentPermissionId not found");
        }

        // Operator authorization check
        Long operatorId = OperatorContext.getOperatorId();
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.ROLE, parent.getAbstractRoleId(), OperationType.MANAGE);

        if (parent.getDependOn() != null) {
            throw new IllegalArgumentException("parentPermissionId must be a top-level permission");
        }
        ResourceEntity parentResource = parent.getResourceEntityId() == null ? null : resourceEntityMapper.selectOneById(parent.getResourceEntityId());
        DomainConfig subPermConfig = null;
        if (parentResource != null && parentResource.getBizDomainId() != null) {
            subPermConfig = domainConfigMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(DOMAIN_CONFIG.TENANT_ID.eq(tenantId))
                    .and(DOMAIN_CONFIG.BIZ_DOMAIN_ID.eq(parentResource.getBizDomainId()))
                    .and(DOMAIN_CONFIG.CONFIG_TYPE.eq("SUB_PERM"))
                    .and(DOMAIN_CONFIG.DELETE_FLAG.eq(0))
            );
        }

        // ===== Batch resolution to avoid N+1 queries =====
        List<RolePermissionAddChildReq.ChildItem> children = req.children();
        if (children.isEmpty()) {
            return List.of();
        }

        // 1. Batch resolve resource type values
        Set<String> resourceTypeCodes = children.stream()
            .map(RolePermissionAddChildReq.ChildItem::resourceTypeCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, Integer> resourceTypeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", resourceTypeCodes);

        // 2. Batch resolve operation IDs
        // Group by resourceTypeCode for batch resolution
        Map<String, Set<String>> operationCodesByType = children.stream()
            .filter(c -> c.operationCode() != null && !c.operationCode().isBlank())
            .collect(Collectors.groupingBy(
                RolePermissionAddChildReq.ChildItem::resourceTypeCode,
                Collectors.mapping(RolePermissionAddChildReq.ChildItem::operationCode, Collectors.toSet())
            ));
        Map<String, Map<String, Long>> operationIdMapByType = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : operationCodesByType.entrySet()) {
            Map<String, Long> opMap = typeResolutionService.batchResolveOperationIds(tenantId, entry.getKey(), entry.getValue());
            operationIdMapByType.put(entry.getKey(), opMap);
        }

        // 3. Batch resolve resource IDs for non-scopeAll items
        List<ResourceResolveRequest> resourceRequests = children.stream()
            .filter(c -> !Boolean.TRUE.equals(c.scopeAll()) && c.resourceCode() != null && !c.resourceCode().isBlank())
            .map(c -> new ResourceResolveRequest(c.resourceTypeCode(), c.resourceCode(), c.codeType(), null))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resourceIdMap = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        // 4. Batch query permission conditions
        Set<String> conditionCodes = children.stream()
            .map(RolePermissionAddChildReq.ChildItem::conditionCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, PermissionCondition> conditionMap = conditionCodes.isEmpty() ? Map.of()
            : permissionConditionMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                    .and(PERMISSION_CONDITION.CODE.in(conditionCodes))
                    .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.toMap(PermissionCondition::getCode, c -> c));

        List<RoleResourcePermission> inserted = new ArrayList<>();
        List<PermissionChangeDomainService.ChangeLogEntry> changeLogs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (RolePermissionAddChildReq.ChildItem child : children) {
            Integer resourceType = resourceTypeValueMap.get(child.resourceTypeCode());
            if (resourceType == null) {
                throw new IllegalArgumentException("resourceTypeCode not found: " + child.resourceTypeCode());
            }
            if (subPermConfig != null && subPermConfig.getExtra() != null && !subPermConfig.getExtra().isBlank()
                && !parseAllowedTypeCodes(subPermConfig.getExtra()).contains(child.resourceTypeCode().trim().toUpperCase())) {
                throw new IllegalArgumentException("resourceTypeCode not allowed by SUB_PERM config: " + child.resourceTypeCode());
            }
            Map<String, Long> opMap = operationIdMapByType.getOrDefault(child.resourceTypeCode(), Map.of());
            Long operationId = opMap.get(child.operationCode());
            if (operationId == null) {
                throw new IllegalArgumentException("operationCode not found: " + child.operationCode());
            }
            boolean scopeAll = Boolean.TRUE.equals(child.scopeAll());
            if (!scopeAll && (child.resourceCode() == null || child.resourceCode().isBlank())) {
                throw new IllegalArgumentException("resourceCode is required when scopeAll=false");
            }
            Long resourceId = null;
            if (!scopeAll) {
                ResourceResolveKey resKey = new ResourceResolveKey(child.resourceTypeCode(), child.resourceCode(), child.codeType(), null);
                resourceId = resourceIdMap.get(resKey);
                if (resourceId == null) {
                    throw new IllegalArgumentException("resource not found: " + child.resourceCode());
                }
            }
            Long conditionId = null;
            if (child.conditionCode() != null && !child.conditionCode().isBlank()) {
                PermissionCondition condition = conditionMap.get(child.conditionCode());
                if (condition == null) {
                    throw new IllegalArgumentException("conditionCode not found: " + child.conditionCode());
                }
                conditionId = condition.getId();
            }
            RoleResourcePermission rp = new RoleResourcePermission();
            rp.setTenantId(tenantId);
            rp.setAbstractRoleId(parent.getAbstractRoleId());
            rp.setResourceEntityId(resourceId);
            rp.setOperationPermissionId(operationId);
            rp.setResourceType(resourceType);
            rp.setDependOn(parent.getId());
            rp.setScopeAll(scopeAll);
            rp.setCanGrant(Boolean.TRUE.equals(child.canGrant()));
            rp.setConditionId(conditionId);
            rp.setGrantSource(GrantSource.MANUAL.getValue());
            rp.setCreatedAt(now);
            rp.setUpdatedAt(now);
            rp.setDeleteFlag(0L);
            rolePermMapper.insert(rp);
            inserted.add(rp);
            changeLogs.add(new PermissionChangeDomainService.ChangeLogEntry(
                "role_resource_permission", rp.getId(), "ADD", null, "child-added", "{}", null,
                new Long[]{parent.getAbstractRoleId()}
            ));
        }
        permissionVersionDomainService.increment(tenantId, parent.getAbstractRoleId());
        userRoleDomainService.invalidateRoleCacheByRole(tenantId, parent.getAbstractRoleId());
        permissionChangeDomainService.record(new PermissionChangeDomainService.ChangeLogContext(
            tenantId, null, operatorId, null, "MANUAL", "add-child"
        ), changeLogs);
        return toItemRespList(tenantId, inserted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeChild(Long tenantId, RolePermissionRemoveChildReq req) {
        RoleResourcePermission child = rolePermissionDomainService.selectValidById(tenantId, null, req.permissionId());
        if (child == null) {
            throw new IllegalArgumentException("child permission not found");
        }

        // Operator authorization check
        Long operatorId = OperatorContext.getOperatorId();
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.ROLE, child.getAbstractRoleId(), OperationType.MANAGE);

        if (child.getDependOn() == null) {
            throw new IllegalArgumentException("permission is not a child");
        }
        child.setDeleteFlag(child.getId());
        child.setDeletedAt(LocalDateTime.now());
        rolePermMapper.update(child);
        permissionVersionDomainService.increment(tenantId, child.getAbstractRoleId());
        userRoleDomainService.invalidateRoleCacheByRole(tenantId, child.getAbstractRoleId());
        permissionChangeDomainService.record(new PermissionChangeDomainService.ChangeLogContext(
            tenantId, null, operatorId, null, "MANUAL", "remove-child"
        ), List.of(new PermissionChangeDomainService.ChangeLogEntry(
            "role_resource_permission", child.getId(), "REMOVE", "child-exists", null, "{}", null,
            new Long[]{child.getAbstractRoleId()}
        )));
    }

    private List<RolePermissionItemResp> toItemRespList(Long tenantId, List<RoleResourcePermission> perms) {
        if (perms.isEmpty()) {
            return List.of();
        }
        // Batch load resource entities to avoid N+1 queries
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = resourceIds.isEmpty() ? Map.of() :
            resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_ENTITY.ID.in(resourceIds))
                    .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            ).stream().collect(java.util.stream.Collectors.toMap(ResourceEntity::getId, r -> r));

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> operationIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, OperationPermission> operationMap = operationIds.isEmpty() ? Map.of() :
            operationPermissionMapper.selectListByQuery(
                QueryWrapper.create().where(cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION.ID.in(operationIds))
            ).stream().collect(java.util.stream.Collectors.toMap(OperationPermission::getId, op -> op));

        // Batch load permission conditions to avoid N+1 queries
        Set<Long> conditionIds = perms.stream()
            .map(RoleResourcePermission::getConditionId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, PermissionCondition> conditionMap = conditionIds.isEmpty() ? Map.of() :
            permissionConditionMapper.selectListByQuery(
                QueryWrapper.create().where(PERMISSION_CONDITION.ID.in(conditionIds))
            ).stream().collect(java.util.stream.Collectors.toMap(PermissionCondition::getId, c -> c));

        // Batch resolve resource type codes (avoid N+1)
        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        return perms.stream().map(perm -> {
            ResourceEntity resource = perm.getResourceEntityId() == null ? null : resourceMap.get(perm.getResourceEntityId());
            OperationPermission operation = operationMap.get(perm.getOperationPermissionId());
            String resourceTypeCode = resourceTypeCodeMap.get(perm.getResourceType());
            PermissionCondition condition = perm.getConditionId() == null ? null : conditionMap.get(perm.getConditionId());
            return new RolePermissionItemResp(
                perm.getId(),
                resourceTypeCode,
                resource == null ? null : resource.getCode(),
                resource == null ? null : resource.getCodeType(),
                resource == null ? null : resource.getName(),
                operation == null ? null : operation.getCode(),
                perm.getCanGrant(),
                condition == null ? null : condition.getCode(),
                Boolean.TRUE.equals(perm.getScopeAll()),
                perm.getDependOn()
            );
        }).toList();
    }

    private Set<String> parseAllowedTypeCodes(String extra) {
        String normalized = extra.replace("[", "").replace("]", "").replace("\"", "");
        Set<String> codes = new HashSet<>();
        for (String token : normalized.split(",")) {
            String value = token.trim();
            if (!value.isEmpty()) {
                codes.add(value.toUpperCase());
            }
        }
        return codes;
    }

    /**
     * Build permission key for grant check matching AuthorizationServiceImpl format.
     */
    private String buildGrantKey(RoleGrantReq.GrantAddItem item) {
        return String.format("%s:%s:%s:%s",
            item.resourceTypeCode(),
            item.resourceCode() == null ? "*" : item.resourceCode(),
            item.operationCode(),
            Boolean.TRUE.equals(item.scopeAll()) ? "ALL" : "SPECIFIC");
    }
}
