package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
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
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantService;
import cn.ac.fage.accessmesh.permission.service.domain.*;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
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
                                      AuthorizationService authorizationService) {
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

        // Operator authorization check
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.canManageRole(tenantId, operatorId, roleId)) {
            throw new SecurityException("Operator " + operatorId + " lacks MANAGE permission for role " + roleId);
        }

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
        if (role.getStatus() != 1) {
            throw new IllegalStateException("Role is disabled: " + roleId);
        }

        List<RoleGrantReq.GrantAddItem> addItems = req.add() == null ? List.of() : req.add();
        List<RoleGrantReq.GrantUpdateItem> updateItems = req.update() == null ? List.of() : req.update();
        List<Long> removeItems = req.remove() == null ? List.of() : req.remove();

        Set<Long> resourceIds = addItems.stream()
            .filter(item -> !Boolean.TRUE.equals(item.scopeAll()))
            .map(item -> typeResolutionService.resolveResourceId(
                tenantId,
                item.resourceTypeCode(),
                item.resourceCode(),
                item.codeType(),
                req.domainCode()
            ))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceById = new HashMap<>();
        for (Long resId : resourceIds) {
            ResourceEntity res = resourceEntityMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_ENTITY.ID.eq(resId))
                    .and(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            );
            if (res == null) {
                throw new IllegalArgumentException("Resource not found: " + resId);
            }
            resourceById.put(resId, res);
        }

        LocalDateTime now = LocalDateTime.now();
        List<RoleResourcePermission> toInsert = new ArrayList<>();
        for (RoleGrantReq.GrantAddItem item : addItems) {
            boolean scopeAll = Boolean.TRUE.equals(item.scopeAll());
            Long resourceEntityId = null;
            if (!scopeAll) {
                resourceEntityId = typeResolutionService.resolveResourceId(
                    tenantId,
                    item.resourceTypeCode(),
                    item.resourceCode(),
                    item.codeType(),
                    req.domainCode()
                );
                if (resourceEntityId == null) {
                    throw new IllegalArgumentException("resource not found: " + item.resourceCode());
                }
            }

            Long operationId = typeResolutionService.resolveOperationId(
                tenantId, item.operationCode(), item.resourceTypeCode()
            );
            OperationPermission operation = operationPermissionMapper.selectOneById(operationId);
            if (operation == null || operation.getDeleteFlag() != 0L || !tenantId.equals(operation.getTenantId())) {
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
            Integer finalResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", item.resourceTypeCode());
            if (finalResourceType == null) {
                throw new IllegalArgumentException("resourceTypeCode not found: " + item.resourceTypeCode());
            }
            if (operation.getResourceType() != null && !operation.getResourceType().equals(finalResourceType)) {
                throw new IllegalArgumentException("resourceType does not match operationPermission resource type");
            }
            rp.setResourceType(finalResourceType);
            rp.setScopeAll(scopeAll);
            rp.setCanManage(item.canManage() != null ? item.canManage() : false);
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

        for (Long permId : removeItems) {
            RoleResourcePermission rp = rolePermMapper.selectOneById(permId);
            if (rp != null && rp.getDeleteFlag() == 0L && rp.getAbstractRoleId().equals(roleId)
                && rp.getTenantId().equals(tenantId)) {
                rp.setDeleteFlag(rp.getId());
                rp.setDeletedAt(now);
                rolePermMapper.update(rp);
            }
        }

        for (RoleGrantReq.GrantUpdateItem updateItem : updateItems) {
            if (updateItem.id() == null) {
                continue;
            }
            RoleResourcePermission existing = rolePermMapper.selectOneById(updateItem.id());
            if (existing == null || existing.getDeleteFlag() != 0L || !tenantId.equals(existing.getTenantId())
                || !roleId.equals(existing.getAbstractRoleId())) {
                continue;
            }
            if (updateItem.canManage() != null) {
                existing.setCanManage(updateItem.canManage());
            }
            if (updateItem.conditionCode() != null) {
                if (updateItem.conditionCode().isBlank()) {
                    existing.setConditionId(null);
                } else {
                    PermissionCondition condition = permissionConditionMapper.selectOneByQuery(
                        QueryWrapper.create()
                            .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                            .and(PERMISSION_CONDITION.CODE.eq(updateItem.conditionCode()))
                            .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
                    );
                    if (condition == null) {
                        throw new IllegalArgumentException("conditionCode not found: " + updateItem.conditionCode());
                    }
                    existing.setConditionId(condition.getId());
                }
            }
            existing.setUpdatedAt(now);
            rolePermMapper.update(existing);
        }

        for (RoleResourcePermission rp : toInsert) {
            rolePermMapper.insert(rp);
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
        if (!authorizationService.canManageRole(tenantId, operatorId, roleId)) {
            throw new SecurityException("Operator " + operatorId + " lacks MANAGE permission for role " + roleId);
        }

        List<Long> permissionIds = req.permissionIds() == null ? List.of() : req.permissionIds();
        LocalDateTime now = LocalDateTime.now();

        for (Long permId : permissionIds) {
            RoleResourcePermission rp = rolePermMapper.selectOneById(permId);
            if (rp != null && rp.getDeleteFlag() == 0L && rp.getAbstractRoleId().equals(roleId)
                && rp.getTenantId().equals(tenantId)) {
                // Soft delete
                rp.setDeleteFlag(rp.getId());
                rp.setDeletedAt(now);
                rolePermMapper.update(rp);

                // Cascade soft delete sub-permissions
                List<RoleResourcePermission> children = rolePermMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(ROLE_RESOURCE_PERMISSION.DEPEND_ON.eq(permId))
                        .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
                );
                for (RoleResourcePermission child : children) {
                    child.setDeleteFlag(child.getId());
                    child.setDeletedAt(now);
                    rolePermMapper.update(child);
                }
            }
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
        if (!authorizationService.hasPermissionOnRole(tenantId, operatorId, roleId, "VIEW")) {
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
        RoleResourcePermission parent = rolePermMapper.selectOneById(req.permissionId());
        if (parent == null || parent.getDeleteFlag() != 0L || !tenantId.equals(parent.getTenantId())) {
            return List.of();
        }

        // Operator authorization check - VIEW permission required
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.hasPermissionOnRole(tenantId, operatorId, parent.getAbstractRoleId(), "VIEW")) {
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
        RoleResourcePermission parent = rolePermMapper.selectOneById(req.parentPermissionId());
        if (parent == null || parent.getDeleteFlag() != 0L || !tenantId.equals(parent.getTenantId())) {
            throw new IllegalArgumentException("parentPermissionId not found");
        }

        // Operator authorization check
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.canManageRole(tenantId, operatorId, parent.getAbstractRoleId())) {
            throw new SecurityException("Operator " + operatorId + " lacks MANAGE permission for role " + parent.getAbstractRoleId());
        }

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
        List<RoleResourcePermission> inserted = new ArrayList<>();
        List<PermissionChangeDomainService.ChangeLogEntry> changeLogs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (RolePermissionAddChildReq.ChildItem child : req.children()) {
            Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", child.resourceTypeCode());
            if (resourceType == null) {
                throw new IllegalArgumentException("resourceTypeCode not found: " + child.resourceTypeCode());
            }
            if (subPermConfig != null && subPermConfig.getExtra() != null && !subPermConfig.getExtra().isBlank()
                && !parseAllowedTypeCodes(subPermConfig.getExtra()).contains(child.resourceTypeCode().trim().toUpperCase())) {
                throw new IllegalArgumentException("resourceTypeCode not allowed by SUB_PERM config: " + child.resourceTypeCode());
            }
            Long operationId = typeResolutionService.resolveOperationId(tenantId, child.operationCode(), child.resourceTypeCode());
            if (operationId == null) {
                throw new IllegalArgumentException("operationCode not found: " + child.operationCode());
            }
            boolean scopeAll = Boolean.TRUE.equals(child.scopeAll());
            if (!scopeAll && (child.resourceCode() == null || child.resourceCode().isBlank())) {
                throw new IllegalArgumentException("resourceCode is required when scopeAll=false");
            }
            Long resourceId = scopeAll ? null :
                typeResolutionService.resolveResourceId(tenantId, child.resourceTypeCode(), child.resourceCode(), child.codeType(), null);
            if (!scopeAll && resourceId == null) {
                throw new IllegalArgumentException("resource not found: " + child.resourceCode());
            }
            Long conditionId = null;
            if (child.conditionCode() != null && !child.conditionCode().isBlank()) {
                PermissionCondition condition = permissionConditionMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                        .and(PERMISSION_CONDITION.CODE.eq(child.conditionCode()))
                        .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
                );
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
            rp.setCanManage(Boolean.TRUE.equals(child.canManage()));
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
        RoleResourcePermission child = rolePermMapper.selectOneById(req.permissionId());
        if (child == null || child.getDeleteFlag() != 0L || !tenantId.equals(child.getTenantId())) {
            throw new IllegalArgumentException("child permission not found");
        }

        // Operator authorization check
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.canManageRole(tenantId, operatorId, child.getAbstractRoleId())) {
            throw new SecurityException("Operator " + operatorId + " lacks MANAGE permission for role " + child.getAbstractRoleId());
        }

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
        return perms.stream().map(perm -> {
            ResourceEntity resource = perm.getResourceEntityId() == null ? null : resourceEntityMapper.selectOneById(perm.getResourceEntityId());
            OperationPermission operation = operationPermissionMapper.selectOneById(perm.getOperationPermissionId());
            String resourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", perm.getResourceType());
            PermissionCondition condition = perm.getConditionId() == null ? null : permissionConditionMapper.selectOneById(perm.getConditionId());
            return new RolePermissionItemResp(
                perm.getId(),
                resourceTypeCode,
                resource == null ? null : resource.getCode(),
                resource == null ? null : resource.getCodeType(),
                resource == null ? null : resource.getName(),
                operation == null ? null : operation.getCode(),
                perm.getCanManage(),
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
}
