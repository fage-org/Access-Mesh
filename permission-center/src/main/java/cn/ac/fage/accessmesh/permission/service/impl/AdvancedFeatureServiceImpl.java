package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.entity.*;
import cn.ac.fage.accessmesh.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationLogTableDef.OPERATION_LOG;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionChangeLogTableDef.PERMISSION_CHANGE_LOG;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConditionTableDef.PERMISSION_CONDITION;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef.RESOURCE_DEPENDENCY;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class AdvancedFeatureServiceImpl implements AdvancedFeatureService {

    private final PermissionConditionMapper conditionMapper;
    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final ResourceDependencyMapper dependencyMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final PermissionVersionDomainService permissionVersionDomainService;
    private final UserRoleDomainService userRoleDomainService;

    public AdvancedFeatureServiceImpl(PermissionConditionMapper conditionMapper,
                                      PermissionConflictRuleMapper conflictRuleMapper,
                                      PermissionChangeLogMapper changeLogMapper,
                                      OperationLogMapper operationLogMapper,
                                      ResourceDependencyMapper dependencyMapper,
                                      ResourceEntityMapper resourceEntityMapper,
                                      RoleResourcePermissionMapper rolePermMapper,
                                      UserRoleMapper userRoleMapper,
                                      AbstractRoleMapper abstractRoleMapper,
                                      PermissionVersionDomainService permissionVersionDomainService,
                                      UserRoleDomainService userRoleDomainService) {
        this.conditionMapper = conditionMapper;
        this.conflictRuleMapper = conflictRuleMapper;
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.dependencyMapper = dependencyMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.rolePermMapper = rolePermMapper;
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.permissionVersionDomainService = permissionVersionDomainService;
        this.userRoleDomainService = userRoleDomainService;
    }

    // ===== PermissionCondition =====

    @Override
    @Transactional
    public ConditionResp createCondition(ConditionCreateReq req, Long operatorId) {
        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(req.tenantId());
        condition.setCode(req.code());
        condition.setName(req.name());
        condition.setConditionRules(req.conditionRules());
        condition.setEnabled(req.enabled() != null ? req.enabled() : true);
        condition.setDescription(req.description());
        condition.setCreatedBy(operatorId);
        condition.setCreatedAt(LocalDateTime.now());
        condition.setUpdatedAt(LocalDateTime.now());
        condition.setDeleteFlag(0L);
        conditionMapper.insert(condition);
        return toConditionResp(condition);
    }

    @Override
    public ConditionResp getCondition(Long tenantId, Long conditionId) {
        PermissionCondition condition = conditionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONDITION.ID.eq(conditionId))
                .and(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        );
        return condition != null ? toConditionResp(condition) : null;
    }

    @Override
    public List<ConditionResp> listConditions(Long tenantId) {
        return conditionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        ).stream().map(this::toConditionResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteCondition(Long tenantId, Long conditionId, Long operatorId) {
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition != null && condition.getDeleteFlag() == 0L && condition.getTenantId().equals(tenantId)) {
            condition.setDeleteFlag(condition.getId());
            condition.setDeletedAt(LocalDateTime.now());
            conditionMapper.update(condition);
        }
    }

    @Override
    @Transactional
    public void setConditionEnabled(Long tenantId, Long conditionId, boolean enabled, Long operatorId) {
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition != null && condition.getDeleteFlag() == 0L && condition.getTenantId().equals(tenantId)) {
            condition.setEnabled(enabled);
            condition.setUpdatedAt(LocalDateTime.now());
            conditionMapper.update(condition);
        }
    }

    // ===== PermissionConflictRule =====

    @Override
    @Transactional
    public ConflictRuleResp createConflictRule(ConflictRuleReq req, Long operatorId) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setTenantId(req.tenantId());
        rule.setBizDomainId(req.bizDomainId());
        rule.setConflictType(req.conflictType());
        rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        rule.setResourceTypeValue(req.resourceTypeValue());
        rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        rule.setDescription(req.description());
        rule.setCreatedBy(operatorId);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        rule.setDeleteFlag(0L);
        conflictRuleMapper.insert(rule);
        return toConflictRuleResp(rule);
    }

    @Override
    public ConflictRuleResp getConflictRule(Long tenantId, Long ruleId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.ID.eq(ruleId))
                .and(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );
        return rule != null ? toConflictRuleResp(rule) : null;
    }

    @Override
    public List<ConflictRuleResp> listConflictRules(Long tenantId) {
        return conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        ).stream().map(this::toConflictRuleResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneById(ruleId);
        if (rule != null && rule.getDeleteFlag() == 0L && rule.getTenantId().equals(tenantId)) {
            rule.setDeleteFlag(rule.getId());
            rule.setDeletedAt(LocalDateTime.now());
            conflictRuleMapper.update(rule);
        }
    }

    // ===== PermissionChangeLog =====

    @Override
    public List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit) {
        QueryWrapper qw = QueryWrapper.create()
            .where(PERMISSION_CHANGE_LOG.TENANT_ID.eq(tenantId));
        if (entityType != null) {
            qw.and(PERMISSION_CHANGE_LOG.ENTITY_TYPE.eq(entityType));
        }
        if (entityId != null) {
            qw.and(PERMISSION_CHANGE_LOG.ENTITY_ID.eq(entityId));
        }
        qw.orderBy(PERMISSION_CHANGE_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);

        return changeLogMapper.selectListByQuery(qw)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    // ===== OperationLog =====

    @Override
    public List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit) {
        QueryWrapper qw = QueryWrapper.create()
            .where(OPERATION_LOG.TENANT_ID.eq(tenantId));
        if (module != null) {
            qw.and(OPERATION_LOG.MODULE.eq(module));
        }
        if (action != null) {
            qw.and(OPERATION_LOG.ACTION.eq(action));
        }
        qw.orderBy(OPERATION_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);

        return operationLogMapper.selectListByQuery(qw)
            .stream().map(this::toOperationLogResp).collect(Collectors.toList());
    }

    // ===== ResourceDependency =====

    @Override
    @Transactional
    public ResourceDependencyResp createDependency(ResourceDependencyCreateReq req, Long operatorId) {
        ResourceDependency dep = new ResourceDependency();
        dep.setTenantId(req.tenantId());
        dep.setResourceEntityId(req.resourceEntityId());
        dep.setDependsOnResourceEntityId(req.dependsOnResourceEntityId());
        dep.setSourceOperationBits(req.sourceOperationBits());
        dep.setRequiredOperationBits(req.requiredOperationBits());
        dep.setAutoGrant(req.autoGrant() != null ? req.autoGrant() : true);
        dep.setDescription(req.description());
        dep.setCreatedBy(operatorId);
        dep.setCreatedAt(LocalDateTime.now());
        dep.setUpdatedAt(LocalDateTime.now());
        dep.setDeleteFlag(0L);
        dependencyMapper.insert(dep);
        return toDependencyResp(dep);
    }

    @Override
    public List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0));
        if (resourceEntityId != null) {
            qw.and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(resourceEntityId));
        }
        return dependencyMapper.selectListByQuery(qw)
            .stream().map(this::toDependencyResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDependency(Long tenantId, Long dependencyId, Long operatorId) {
        ResourceDependency dep = dependencyMapper.selectOneById(dependencyId);
        if (dep != null && dep.getDeleteFlag() == 0L && dep.getTenantId().equals(tenantId)) {
            dep.setDeleteFlag(dep.getId());
            dep.setDeletedAt(LocalDateTime.now());
            dependencyMapper.update(dep);
        }
    }

    @Override
    @Transactional
    public void batchSyncDependencies(Long tenantId, Long roleId, Long operatorId) {
        // Find all resources this role has permissions on
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // For each perm, check if there are dependency rules that should auto-grant
        for (RoleResourcePermission perm : perms) {
            // Find rules where this resource triggers a dependency
            List<ResourceDependency> deps = dependencyMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(perm.getResourceEntityId()))
                    .and(RESOURCE_DEPENDENCY.AUTO_GRANT.eq(true))
                    .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
            );

            for (ResourceDependency dep : deps) {
                // Check if already granted
                Long existing = rolePermMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                        .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                        .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(dep.getDependsOnResourceEntityId()))
                        .and(ROLE_RESOURCE_PERMISSION.GRANT_SOURCE.eq(GrantSource.AUTO_DEP.getValue()))
                        .and(ROLE_RESOURCE_PERMISSION.GRANT_DEP_ID.eq(dep.getId()))
                        .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
                ) != null ? 1L : null;

                if (existing == null) {
                    RoleResourcePermission autoRp = new RoleResourcePermission();
                    autoRp.setTenantId(tenantId);
                    autoRp.setAbstractRoleId(roleId);
                    autoRp.setResourceEntityId(dep.getDependsOnResourceEntityId());
                    autoRp.setOperationPermissionId(dep.getRequiredOperationBits());
                    autoRp.setGrantSource(GrantSource.AUTO_DEP.getValue());
                    autoRp.setGrantDepId(dep.getId());
                    autoRp.setCanManage(false);
                    autoRp.setCreatedAt(LocalDateTime.now());
                    autoRp.setUpdatedAt(LocalDateTime.now());
                    autoRp.setDeleteFlag(0L);
                    rolePermMapper.insert(autoRp);
                }
            }
        }
        permissionVersionDomainService.increment(tenantId, roleId);
    }

    // ===== GroupRole extra-roles =====

    @Override
    @Transactional
    public void addGroupRoleExtraRole(Long tenantId, Long groupId, Long basicRoleId, Long operatorId) {
        // Validate both roles exist and belong to tenant
        AbstractRole groupRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(groupId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (groupRole == null || !groupRole.getRoleType().equals(5)) { // GROUP_ROLE
            throw new IllegalArgumentException("Not a valid GROUP_ROLE: " + groupId);
        }

        AbstractRole basicRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(basicRoleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (basicRole == null) {
            throw new IllegalArgumentException("Basic role not found: " + basicRoleId);
        }

        // Create user_role link: the group role "contains" the basic role
        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(null); // Not assigned to a specific user
        ur.setTargetType("GROUP_ROLE");
        ur.setTargetId(groupId);
        ur.setRelationId(basicRoleId);
        ur.setCreatedAt(LocalDateTime.now());
        ur.setUpdatedAt(LocalDateTime.now());
        ur.setDeleteFlag(0L);
        userRoleMapper.insert(ur);

        // Invalidate caches for all users with this group role
        userRoleDomainService.invalidateRoleCacheByRole(tenantId, groupId);
    }

    @Override
    @Transactional
    public void removeGroupRoleExtraRole(Long tenantId, Long groupId, Long basicRoleId, Long operatorId) {
        UserRole ur = userRoleMapper.selectOneByQuery(
            QueryWrapper.create()
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
    public Set<Long> listGroupRoleExtraRoles(Long tenantId, Long groupId) {
        return userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TARGET_TYPE.eq("GROUP_ROLE"))
                .where(USER_ROLE.TARGET_ID.eq(groupId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        ).stream()
            .map(UserRole::getRelationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    // ===== Converters =====

    private ConditionResp toConditionResp(PermissionCondition c) {
        return new ConditionResp(
            c.getId(), c.getTenantId(), c.getCode(), c.getName(),
            c.getConditionRules(), c.getEnabled(), c.getDescription(), c.getCreatedAt()
        );
    }

    private ConflictRuleResp toConflictRuleResp(PermissionConflictRule r) {
        return new ConflictRuleResp(
            r.getId(), r.getTenantId(), r.getBizDomainId(), r.getConflictType(),
            r.getFirstOperationPermissionId(), r.getSecondOperationPermissionId(),
            r.getResourceTypeValue(), r.getFirstAbstractRoleId(), r.getSecondAbstractRoleId(),
            r.getDescription(), r.getCreatedAt()
        );
    }

    private ChangeLogResp toChangeLogResp(PermissionChangeLog c) {
        return new ChangeLogResp(
            c.getId(), c.getTenantId(), c.getBizDomainId(), c.getEntityType(),
            c.getEntityId(), c.getOperation(), c.getOldSnapshot(), c.getNewSnapshot(),
            c.getDiffSnapshot(), c.getAffectedAbstractUserIds(), c.getAffectedAbstractRoleIds(),
            c.getChangeReason(), c.getChangeSource(), c.getRequestId(), c.getCreatedAt()
        );
    }

    private OperationLogResp toOperationLogResp(OperationLog l) {
        return new OperationLogResp(
            l.getId(), l.getTenantId(), l.getModule(), l.getAction(),
            l.getTargetType(), l.getTargetId(), l.getSummary(), l.getOperatorId(),
            l.getOperatorName(), l.getIpAddress(), l.getRequestId(), l.getCreatedAt()
        );
    }

    private ResourceDependencyResp toDependencyResp(ResourceDependency d) {
        ResourceEntity src = resourceEntityMapper.selectOneById(d.getResourceEntityId());
        ResourceEntity dep = resourceEntityMapper.selectOneById(d.getDependsOnResourceEntityId());
        return new ResourceDependencyResp(
            d.getId(), d.getTenantId(), d.getResourceEntityId(),
            src != null ? src.getCode() : null,
            d.getDependsOnResourceEntityId(),
            dep != null ? dep.getCode() : null,
            d.getSourceOperationBits(), d.getRequiredOperationBits(),
            d.getAutoGrant(), d.getDescription(), d.getCreatedAt()
        );
    }
}
