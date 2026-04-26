package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantService;
import cn.ac.fage.accessmesh.permission.service.domain.*;
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
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

@Service
public class PermissionGrantServiceImpl implements PermissionGrantService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGrantServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final RolePermissionDomainService rolePermissionDomainService;
    private final PermissionVersionDomainService permissionVersionDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final OperationLogDomainService operationLogDomainService;
    private final UserRoleDomainService userRoleDomainService;
    private final ResourceDependencyDomainService resourceDependencyDomainService;

    public PermissionGrantServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                      ResourceEntityMapper resourceEntityMapper,
                                      RoleResourcePermissionMapper rolePermMapper,
                                      RolePermissionDomainService rolePermissionDomainService,
                                      PermissionVersionDomainService permissionVersionDomainService,
                                      PermissionChangeDomainService permissionChangeDomainService,
                                      OperationLogDomainService operationLogDomainService,
                                      UserRoleDomainService userRoleDomainService,
                                      ResourceDependencyDomainService resourceDependencyDomainService) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.rolePermMapper = rolePermMapper;
        this.rolePermissionDomainService = rolePermissionDomainService;
        this.permissionVersionDomainService = permissionVersionDomainService;
        this.permissionChangeDomainService = permissionChangeDomainService;
        this.operationLogDomainService = operationLogDomainService;
        this.userRoleDomainService = userRoleDomainService;
        this.resourceDependencyDomainService = resourceDependencyDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchGrant(Long tenantId, Long roleId, RoleGrantReq req) {
        // Validate role exists and enabled
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

        // Validate resources exist
        Set<Long> resourceIds = req.grants().stream()
            .map(RoleGrantReq.GrantItem::resourceEntityId).collect(Collectors.toSet());
        for (Long resId : resourceIds) {
            ResourceEntity res = resourceEntityMapper.selectOneById(resId);
            if (res == null || res.getDeleteFlag() != 0L) {
                throw new IllegalArgumentException("Resource not found: " + resId);
            }
        }

        // Build grant entries
        LocalDateTime now = LocalDateTime.now();
        List<RoleResourcePermission> toInsert = new ArrayList<>();
        for (RoleGrantReq.GrantItem item : req.grants()) {
            RoleResourcePermission rp = new RoleResourcePermission();
            rp.setTenantId(tenantId);
            rp.setAbstractRoleId(roleId);
            rp.setResourceEntityId(item.resourceEntityId());
            rp.setOperationPermissionId(item.operationPermissionId());
            rp.setResourceType(item.resourceType());
            rp.setDependOn(item.dependOn());
            rp.setCanManage(item.canManage() != null ? item.canManage() : false);
            rp.setConditionId(item.conditionId());
            rp.setGrantSource(GrantSource.MANUAL.getValue());
            rp.setCreatedAt(now);
            rp.setUpdatedAt(now);
            rp.setDeleteFlag(0L);
            toInsert.add(rp);
        }

        // Auto-grant dependencies
        List<RoleResourcePermission> autoGranted = resourceDependencyDomainService
            .autoGrantForInsert(tenantId, roleId, toInsert);
        toInsert.addAll(autoGranted);

        // Insert
        for (RoleResourcePermission rp : toInsert) {
            rolePermMapper.insert(rp);
        }

        // Increment version and invalidate caches after commit
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permissionVersionDomainService.increment(tenantId, roleId);
                    userRoleDomainService.invalidateRoleCacheByRole(tenantId, roleId);
                    operationLogDomainService.asyncRecord(
                        "role_resource_permission", "BATCH_GRANT",
                        "abstract_role", roleId,
                        "Granted " + toInsert.size() + " permissions to role " + roleId,
                        null, null, null, tenantId
                    );
                }
            });
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRevoke(Long tenantId, Long roleId, List<Long> permissionIds) {
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
                        null, null, null, tenantId
                    );
                }
            });
        }
    }
}
