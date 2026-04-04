package org.dromara.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.*;
import org.dromara.permission.mapper.*;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.PermissionSyncService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.dromara.permission.service.support.PermissionTreePathManager;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用同步服务实现：幂等写入用户、角色、资源及关联
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionSyncServiceImpl implements PermissionSyncService {

    private static final long NOT_DELETED = 0L;
    private static final int BATCH_LIMIT = 500;

    private final PcAbstractUserMapper abstractUserMapper;
    private final PcAbstractRoleMapper abstractRoleMapper;
    private final PcResourceEntityMapper resourceEntityMapper;
    private final PcOperationPermissionMapper operationPermissionMapper;
    private final PcPermissionConditionMapper permissionConditionMapper;
    private final PcUserRoleMapper userRoleMapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PermissionChangeLogService permissionChangeLogService;
    private final PermissionTreePathManager treePathManager;
    private final TypeDefinitionReader typeDefinitionReader;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncUsers(SyncUsersReq req) {
        if (CollUtil.isEmpty(req.getItems())) {
            return;
        }
        if (req.getItems().size() > BATCH_LIMIT) {
            throw new ServiceException("单批 users 数量不得超过 " + BATCH_LIMIT);
        }
        Long tenantId = req.getTenantId();
        Integer userType = req.getUserType();
        typeDefinitionReader.assertTypeValueExists(tenantId, null, "user_type", userType, "无效的用户类型");
        LocalDateTime now = LocalDateTime.now();

        List<String> externalIds = req.getItems().stream()
            .map(SyncUsersReq.SyncUserItem::getExternalId).collect(Collectors.toList());
        Map<String, PcAbstractUser> existingMap = abstractUserMapper.selectList(
            new LambdaQueryWrapper<PcAbstractUser>()
                .eq(PcAbstractUser::getTenantId, tenantId)
                .eq(PcAbstractUser::getUserType, userType)
                .in(PcAbstractUser::getExternalId, externalIds)
                .eq(PcAbstractUser::getDeleteFlag, NOT_DELETED)
        ).stream().collect(Collectors.toMap(PcAbstractUser::getExternalId, Function.identity(), (a, b) -> a));

        int insertCount = 0;
        int updateCount = 0;
        for (SyncUsersReq.SyncUserItem item : req.getItems()) {
            PcAbstractUser existing = existingMap.get(item.getExternalId());
            if (existing != null) {
                existing.setName(item.getName());
                if (item.getExtra() != null) {
                    existing.setExtra(item.getExtra());
                }
                existing.setUpdatedAt(now);
                abstractUserMapper.updateById(existing);
                updateCount++;
            } else {
                PcAbstractUser entity = new PcAbstractUser();
                entity.setTenantId(tenantId);
                entity.setUserType(userType);
                entity.setExternalId(item.getExternalId());
                entity.setName(item.getName());
                entity.setExtra(item.getExtra() != null ? item.getExtra() : "{}");
                entity.setDeleteFlag(NOT_DELETED);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                abstractUserMapper.insert(entity);
                insertCount++;
            }
        }
        String operation = buildOperation(insertCount, updateCount);
        permissionChangeLogService.writeChangeLog(tenantId, null, "sync_user", 0L, operation, null, req, null, "API");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncRoles(SyncRolesReq req) {
        if (CollUtil.isEmpty(req.getItems())) {
            return;
        }
        if (req.getItems().size() > BATCH_LIMIT) {
            throw new ServiceException("单批 roles 数量不得超过 " + BATCH_LIMIT);
        }
        Long tenantId = req.getTenantId();
        Long bizDomainId = req.getBizDomainId();
        LocalDateTime now = LocalDateTime.now();

        List<String> externalIds = req.getItems().stream()
            .map(SyncRolesReq.SyncRoleItem::getExternalId).collect(Collectors.toList());
        Map<String, PcAbstractRole> existingMap = abstractRoleMapper.selectList(
            buildBizDomainQuery(new LambdaQueryWrapper<PcAbstractRole>(), PcAbstractRole::getTenantId,
                PcAbstractRole::getBizDomainId, tenantId, bizDomainId)
                .in(PcAbstractRole::getExternalId, externalIds)
                .eq(PcAbstractRole::getDeleteFlag, NOT_DELETED)
        ).stream().collect(Collectors.toMap(PcAbstractRole::getExternalId, Function.identity(), (a, b) -> a));

        List<String> parentExternalIds = req.getItems().stream()
            .map(SyncRolesReq.SyncRoleItem::getParentExternalId)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        Map<String, PcAbstractRole> parentMap = new HashMap<>();
        if (CollUtil.isNotEmpty(parentExternalIds)) {
            parentMap = abstractRoleMapper.selectList(
                buildBizDomainQuery(new LambdaQueryWrapper<PcAbstractRole>(), PcAbstractRole::getTenantId,
                    PcAbstractRole::getBizDomainId, tenantId, bizDomainId)
                    .in(PcAbstractRole::getExternalId, parentExternalIds)
                    .eq(PcAbstractRole::getDeleteFlag, NOT_DELETED)
            ).stream().collect(Collectors.toMap(PcAbstractRole::getExternalId, Function.identity()));
        }

        List<SyncRolesReq.SyncRoleItem> sorted = topoSortRoles(req.getItems());

        int insertCount = 0;
        int updateCount = 0;
        for (SyncRolesReq.SyncRoleItem item : sorted) {
            Long parentId = null;
            String parentPath = null;
            if (StrUtil.isNotBlank(item.getParentExternalId())) {
                PcAbstractRole parent = parentMap.get(item.getParentExternalId());
                if (parent == null) {
                    parent = existingMap.get(item.getParentExternalId());
                }
                if (parent != null) {
                    parentId = parent.getId();
                    parentPath = parent.getPath();
                }
            }

            PcAbstractRole existing = existingMap.get(item.getExternalId());
            if (existing != null) {
                typeDefinitionReader.assertTypeValueExists(tenantId, bizDomainId, "role_type", item.getRoleType(), "无效的角色类型");
                String oldPath = existing.getPath();
                existing.setRoleType(item.getRoleType());
                existing.setName(item.getName());
                existing.setParentId(parentId);
                existing.setSortOrder(item.getSortOrder());
                if (item.getExtra() != null) {
                    existing.setExtra(item.getExtra());
                }
                existing.setPath(treePathManager.buildPath(parentPath, existing.getId()));
                existing.setUpdatedAt(now);
                abstractRoleMapper.updateById(existing);
                treePathManager.refreshRoleSubtreePaths(tenantId, oldPath, existing.getPath());
                parentMap.put(item.getExternalId(), existing);
                updateCount++;
            } else {
                typeDefinitionReader.assertTypeValueExists(tenantId, bizDomainId, "role_type", item.getRoleType(), "无效的角色类型");
                PcAbstractRole entity = new PcAbstractRole();
                entity.setTenantId(tenantId);
                entity.setBizDomainId(bizDomainId);
                entity.setRoleType(item.getRoleType());
                entity.setExternalId(item.getExternalId());
                entity.setName(item.getName());
                entity.setParentId(parentId);
                entity.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                entity.setExtra(item.getExtra() != null ? item.getExtra() : "{}");
                entity.setDeleteFlag(NOT_DELETED);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                abstractRoleMapper.insert(entity);
                entity.setPath(treePathManager.buildPath(parentPath, entity.getId()));
                abstractRoleMapper.updateById(entity);
                existingMap.put(item.getExternalId(), entity);
                parentMap.put(item.getExternalId(), entity);
                insertCount++;
            }
        }
        String operation = buildOperation(insertCount, updateCount);
        permissionChangeLogService.writeChangeLog(tenantId, bizDomainId, "sync_role", 0L, operation, null, req, null, "API");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncResources(SyncResourcesReq req) {
        if (CollUtil.isEmpty(req.getItems())) {
            return;
        }
        if (req.getItems().size() > BATCH_LIMIT) {
            throw new ServiceException("单批 resources 数量不得超过 " + BATCH_LIMIT);
        }
        Long tenantId = req.getTenantId();
        Long bizDomainId = req.getBizDomainId();
        Integer resourceType = req.getResourceType();
        LocalDateTime now = LocalDateTime.now();

        List<String> codes = req.getItems().stream()
            .map(SyncResourcesReq.SyncResourceItem::getCode).collect(Collectors.toList());
        Map<String, PcResourceEntity> existingMap = resourceEntityMapper.selectList(
            buildBizDomainQuery(new LambdaQueryWrapper<PcResourceEntity>(), PcResourceEntity::getTenantId,
                PcResourceEntity::getBizDomainId, tenantId, bizDomainId)
                .in(PcResourceEntity::getCode, codes)
                .eq(PcResourceEntity::getDeleteFlag, NOT_DELETED)
        ).stream().collect(Collectors.toMap(PcResourceEntity::getCode, Function.identity(), (a, b) -> a));

        List<String> parentCodes = req.getItems().stream()
            .map(SyncResourcesReq.SyncResourceItem::getParentCode)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        Map<String, PcResourceEntity> parentMap = new HashMap<>();
        if (CollUtil.isNotEmpty(parentCodes)) {
            parentMap = resourceEntityMapper.selectList(
                buildBizDomainQuery(new LambdaQueryWrapper<PcResourceEntity>(), PcResourceEntity::getTenantId,
                    PcResourceEntity::getBizDomainId, tenantId, bizDomainId)
                    .eq(resourceType != null, PcResourceEntity::getResourceType, resourceType)
                    .in(PcResourceEntity::getCode, parentCodes)
                    .eq(PcResourceEntity::getDeleteFlag, NOT_DELETED)
            ).stream().collect(Collectors.toMap(PcResourceEntity::getCode, Function.identity()));
        }

        List<SyncResourcesReq.SyncResourceItem> sorted = topoSortResources(req.getItems());

        int insertCount = 0;
        int updateCount = 0;
        for (SyncResourcesReq.SyncResourceItem item : sorted) {
            Long parentId = null;
            String parentPath = null;
            if (StrUtil.isNotBlank(item.getParentCode())) {
                PcResourceEntity parent = parentMap.get(item.getParentCode());
                if (parent == null) {
                    parent = existingMap.get(item.getParentCode());
                }
                if (parent != null) {
                    parentId = parent.getId();
                    parentPath = parent.getPath();
                }
            }

            PcResourceEntity existing = existingMap.get(item.getCode());
            if (existing != null) {
                typeDefinitionReader.assertTypeValueExists(tenantId, bizDomainId, "resource_type", resourceType, "无效的资源类型");
                String oldPath = existing.getPath();
                existing.setName(item.getName());
                existing.setParentId(parentId);
                existing.setResourceType(resourceType);
                existing.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                if (item.getExtra() != null) {
                    existing.setExtra(item.getExtra());
                }
                existing.setPath(treePathManager.buildPath(parentPath, existing.getId()));
                existing.setUpdatedAt(now);
                resourceEntityMapper.updateById(existing);
                treePathManager.refreshResourceSubtreePaths(tenantId, oldPath, existing.getPath());
                parentMap.put(item.getCode(), existing);
                updateCount++;
            } else {
                typeDefinitionReader.assertTypeValueExists(tenantId, bizDomainId, "resource_type", resourceType, "无效的资源类型");
                PcResourceEntity entity = new PcResourceEntity();
                entity.setTenantId(tenantId);
                entity.setBizDomainId(bizDomainId);
                entity.setParentId(parentId);
                entity.setCode(item.getCode());
                entity.setName(item.getName());
                entity.setResourceType(resourceType);
                entity.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                entity.setExtra(item.getExtra() != null ? item.getExtra() : "{}");
                entity.setDeleteFlag(NOT_DELETED);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                resourceEntityMapper.insert(entity);
                entity.setPath(treePathManager.buildPath(parentPath, entity.getId()));
                resourceEntityMapper.updateById(entity);
                existingMap.put(item.getCode(), entity);
                parentMap.put(item.getCode(), entity);
                insertCount++;
            }
        }
        String operation = buildOperation(insertCount, updateCount);
        permissionChangeLogService.writeChangeLog(tenantId, bizDomainId, "sync_resource", 0L, operation, null, req, null, "API");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncUserRoles(SyncUserRolesReq req) {
        if (CollUtil.isEmpty(req.getItems())) {
            return;
        }
        if (req.getItems().size() > BATCH_LIMIT) {
            throw new ServiceException("单批 user-roles 数量不得超过 " + BATCH_LIMIT);
        }
        Long tenantId = req.getTenantId();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Map<Integer, PcAbstractUser>> userCache = preloadUsers(tenantId, req.getItems());
        Map<String, PcAbstractRole> roleCache = preloadRoles(tenantId, req.getItems());

        // 预加载已有user-role关联，避免N+1查询
        Set<Long> allUserIds = new HashSet<>();
        Set<Long> allRoleIds = new HashSet<>();
        for (SyncUserRolesReq.SyncUserRoleItem item : req.getItems()) {
            PcAbstractUser user = resolveUser(userCache, item.getUserExternalId(), item.getUserType());
            if (user != null) {
                allUserIds.add(user.getId());
            }
            PcAbstractRole role = roleCache.get(item.getRoleExternalId());
            if (role != null) {
                allRoleIds.add(role.getId());
            }
        }
        Map<String, PcUserRole> existingUserRoleMap = new HashMap<>();
        if (!allUserIds.isEmpty() && !allRoleIds.isEmpty()) {
            List<PcUserRole> existingList = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
                .eq(PcUserRole::getTenantId, tenantId)
                .in(PcUserRole::getAbstractUserId, allUserIds)
                .in(PcUserRole::getAbstractRoleId, allRoleIds)
                .eq(PcUserRole::getDeleteFlag, NOT_DELETED));
            for (PcUserRole ur : existingList) {
                existingUserRoleMap.put(ur.getAbstractUserId() + "_" + ur.getAbstractRoleId(), ur);
            }
        }

        Set<String> seen = new HashSet<>();
        int insertCount = 0;
        int updateCount = 0;
        for (SyncUserRolesReq.SyncUserRoleItem item : req.getItems()) {
            PcAbstractUser user = resolveUser(userCache, item.getUserExternalId(), item.getUserType());
            if (user == null) {
                log.warn("syncUserRoles: abstract_user not found for tenantId={}, userType={}, externalId={}",
                    tenantId, item.getUserType(), item.getUserExternalId());
                continue;
            }
            PcAbstractRole role = roleCache.get(item.getRoleExternalId());
            if (role == null) {
                log.warn("syncUserRoles: abstract_role not found for tenantId={}, externalId={}",
                    tenantId, item.getRoleExternalId());
                continue;
            }
            String key = user.getId() + "_" + role.getId();
            if (!seen.add(key)) {
                continue;
            }
            PcUserRole existing = existingUserRoleMap.get(user.getId() + "_" + role.getId());
            if (existing != null) {
                existing.setValidFrom(item.getValidFrom());
                existing.setValidTo(item.getValidTo());
                existing.setUpdatedAt(now);
                userRoleMapper.updateById(existing);
                updateCount++;
            } else {
                PcUserRole ur = new PcUserRole();
                ur.setTenantId(tenantId);
                ur.setAbstractUserId(user.getId());
                ur.setAbstractRoleId(role.getId());
                ur.setValidFrom(item.getValidFrom());
                ur.setValidTo(item.getValidTo());
                ur.setDeleteFlag(NOT_DELETED);
                ur.setCreatedAt(now);
                ur.setUpdatedAt(now);
                userRoleMapper.insert(ur);
                insertCount++;
            }
        }

        if (Boolean.TRUE.equals(req.getDeleteNotInList())) {
            if (!allUserIds.isEmpty()) {
                List<PcUserRole> list = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
                    .eq(PcUserRole::getTenantId, tenantId)
                    .in(PcUserRole::getAbstractUserId, allUserIds)
                    .eq(PcUserRole::getDeleteFlag, NOT_DELETED));
                for (PcUserRole ur : list) {
                    String k = ur.getAbstractUserId() + "_" + ur.getAbstractRoleId();
                    if (!seen.contains(k)) {
                        logicDeleteUserRole(ur, now);
                    }
                }
            }
        }
        String operation = buildOperation(insertCount, updateCount);
        permissionChangeLogService.writeChangeLog(tenantId, null, "sync_user_role", 0L, operation, null, req, null, "API");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncRolePermissions(SyncRolePermissionsReq req) {
        if (CollUtil.isEmpty(req.getItems())) {
            return;
        }
        Long tenantId = req.getTenantId();
        Long bizDomainId = req.getBizDomainId();
        PcAbstractRole role = abstractRoleMapper.selectOne(
            buildBizDomainQuery(new LambdaQueryWrapper<PcAbstractRole>(), PcAbstractRole::getTenantId,
                PcAbstractRole::getBizDomainId, tenantId, bizDomainId)
                .eq(PcAbstractRole::getExternalId, req.getRoleExternalId())
                .eq(PcAbstractRole::getDeleteFlag, NOT_DELETED));
        if (role == null) {
            throw new ServiceException("abstract_role not found: tenantId=" + tenantId + ", roleExternalId=" + req.getRoleExternalId());
        }
        Long abstractRoleId = role.getId();
        LocalDateTime now = LocalDateTime.now();

        List<String> resourceCodes = req.getItems().stream()
            .map(SyncRolePermissionsReq.SyncRolePermissionItem::getResourceCode)
            .distinct().collect(Collectors.toList());
        Map<String, PcResourceEntity> resourceMap = resourceEntityMapper.selectList(
            buildBizDomainQuery(new LambdaQueryWrapper<PcResourceEntity>(), PcResourceEntity::getTenantId,
                PcResourceEntity::getBizDomainId, tenantId, bizDomainId)
                .in(PcResourceEntity::getCode, resourceCodes)
                .eq(PcResourceEntity::getDeleteFlag, NOT_DELETED)
        ).stream().collect(Collectors.toMap(PcResourceEntity::getCode, Function.identity(), (a, b) -> a));

        List<String> operationCodes = req.getItems().stream()
            .map(SyncRolePermissionsReq.SyncRolePermissionItem::getOperationCode)
            .distinct().collect(Collectors.toList());
        Map<String, PcOperationPermission> opMap = operationPermissionMapper.selectList(
            new LambdaQueryWrapper<PcOperationPermission>()
                .eq(PcOperationPermission::getTenantId, tenantId)
                .in(PcOperationPermission::getCode, operationCodes)
                .eq(PcOperationPermission::getDeleteFlag, NOT_DELETED)
        ).stream().collect(Collectors.toMap(PcOperationPermission::getCode, Function.identity(), (a, b) -> a));

        List<String> conditionCodes = req.getItems().stream()
            .map(SyncRolePermissionsReq.SyncRolePermissionItem::getConditionCode)
            .filter(StrUtil::isNotBlank)
            .distinct().collect(Collectors.toList());
        Map<String, PcPermissionCondition> condMap = new HashMap<>();
        if (CollUtil.isNotEmpty(conditionCodes)) {
            condMap = permissionConditionMapper.selectList(
                new LambdaQueryWrapper<PcPermissionCondition>()
                    .eq(PcPermissionCondition::getTenantId, tenantId)
                    .in(PcPermissionCondition::getCode, conditionCodes)
                    .eq(PcPermissionCondition::getDeleteFlag, NOT_DELETED)
            ).stream().collect(Collectors.toMap(PcPermissionCondition::getCode, Function.identity(), (a, b) -> a));
        }

        List<PcRoleResourcePermission> existingPerms = roleResourcePermissionMapper.selectList(
            new LambdaQueryWrapper<PcRoleResourcePermission>()
                .eq(PcRoleResourcePermission::getTenantId, tenantId)
                .eq(PcRoleResourcePermission::getAbstractRoleId, abstractRoleId)
                .eq(PcRoleResourcePermission::getDeleteFlag, NOT_DELETED));
        Map<String, PcRoleResourcePermission> existingPermMap = existingPerms.stream()
            .collect(Collectors.toMap(
                rrp -> rrp.getAbstractRoleId() + "_" + rrp.getResourceEntityId() + "_" + rrp.getOperationPermissionId(),
                Function.identity(), (a, b) -> a));

        Set<String> seen = new HashSet<>();
        int insertCount = 0;
        int updateCount = 0;
        for (SyncRolePermissionsReq.SyncRolePermissionItem item : req.getItems()) {
            PcResourceEntity resource = resourceMap.get(item.getResourceCode());
            if (resource == null) {
                log.warn("syncRolePermissions: resource_entity not found for tenantId={}, code={}", tenantId, item.getResourceCode());
                continue;
            }
            PcOperationPermission op = opMap.get(item.getOperationCode());
            if (op == null) {
                log.warn("syncRolePermissions: operation_permission not found for tenantId={}, code={}", tenantId, item.getOperationCode());
                continue;
            }
            Long conditionId = null;
            if (StrUtil.isNotBlank(item.getConditionCode())) {
                PcPermissionCondition cond = condMap.get(item.getConditionCode());
                if (cond != null) {
                    conditionId = cond.getId();
                }
            }
            String key = abstractRoleId + "_" + resource.getId() + "_" + op.getId();
            if (!seen.add(key)) {
                continue;
            }
            PcRoleResourcePermission existing = existingPermMap.get(key);
            if (existing != null) {
                existing.setCanManage(Boolean.TRUE.equals(item.getCanManage()));
                existing.setConditionId(conditionId);
                existing.setUpdatedAt(now);
                roleResourcePermissionMapper.updateById(existing);
                updateCount++;
            } else {
                PcRoleResourcePermission rrp = new PcRoleResourcePermission();
                rrp.setTenantId(tenantId);
                rrp.setAbstractRoleId(abstractRoleId);
                rrp.setResourceEntityId(resource.getId());
                rrp.setOperationPermissionId(op.getId());
                rrp.setCanManage(Boolean.TRUE.equals(item.getCanManage()));
                rrp.setConditionId(conditionId);
                rrp.setDeleteFlag(NOT_DELETED);
                rrp.setCreatedAt(now);
                rrp.setUpdatedAt(now);
                roleResourcePermissionMapper.insert(rrp);
                insertCount++;
            }
        }

        if (Boolean.TRUE.equals(req.getDeleteNotInList())) {
            for (PcRoleResourcePermission rrp : existingPerms) {
                String k = rrp.getAbstractRoleId() + "_" + rrp.getResourceEntityId() + "_" + rrp.getOperationPermissionId();
                if (!seen.contains(k)) {
                    logicDeleteRoleResourcePermission(rrp, now);
                }
            }
        }
        String operation = buildOperation(insertCount, updateCount);
        permissionChangeLogService.writeChangeLog(tenantId, bizDomainId, "sync_role_permission", 0L, operation, null, req, null, "API");
    }

    // ===================== 私有辅助方法 =====================

    private void logicDeleteUserRole(PcUserRole ur, LocalDateTime now) {
        PermissionAuditSupport.markDeleted(ur, ur.getId(), now);
        userRoleMapper.updateById(ur);
    }

    private void logicDeleteRoleResourcePermission(PcRoleResourcePermission rrp, LocalDateTime now) {
        PermissionAuditSupport.markDeleted(rrp, rrp.getId(), now);
        roleResourcePermissionMapper.updateById(rrp);
    }

    /**
     * 构建 bizDomainId 的查询条件：为 null 时用 IS NULL，否则用 =
     */
    @SuppressWarnings("all")
    private <T> LambdaQueryWrapper<T> buildBizDomainQuery(
        LambdaQueryWrapper<T> wrapper,
        com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> tenantGetter,
        com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> bizDomainGetter,
        Long tenantId, Long bizDomainId) {
        wrapper.eq(tenantGetter, tenantId);
        if (bizDomainId != null) {
            wrapper.eq(bizDomainGetter, bizDomainId);
        } else {
            wrapper.isNull(bizDomainGetter);
        }
        return wrapper;
    }

    private String buildOperation(int insertCount, int updateCount) {
        if (insertCount > 0 && updateCount > 0) {
            return "INSERT_UPDATE";
        } else if (updateCount > 0) {
            return "UPDATE";
        } else if (insertCount > 0) {
            return "INSERT";
        }
        return "NOOP";
    }

    /**
     * 批量预加载 users，按 (externalId, userType) 索引
     */
    private Map<String, Map<Integer, PcAbstractUser>> preloadUsers(Long tenantId, List<SyncUserRolesReq.SyncUserRoleItem> items) {
        Set<String> externalIds = items.stream()
            .map(SyncUserRolesReq.SyncUserRoleItem::getUserExternalId)
            .collect(Collectors.toSet());
        if (externalIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PcAbstractUser> users = abstractUserMapper.selectList(new LambdaQueryWrapper<PcAbstractUser>()
            .eq(PcAbstractUser::getTenantId, tenantId)
            .in(PcAbstractUser::getExternalId, externalIds)
            .eq(PcAbstractUser::getDeleteFlag, NOT_DELETED));
        Map<String, Map<Integer, PcAbstractUser>> result = new HashMap<>();
        for (PcAbstractUser u : users) {
            result.computeIfAbsent(u.getExternalId(), k -> new HashMap<>()).put(u.getUserType(), u);
        }
        return result;
    }

    /**
     * 批量预加载 roles，按 externalId 索引
     */
    private Map<String, PcAbstractRole> preloadRoles(Long tenantId, List<SyncUserRolesReq.SyncUserRoleItem> items) {
        Set<String> externalIds = items.stream()
            .map(SyncUserRolesReq.SyncUserRoleItem::getRoleExternalId)
            .collect(Collectors.toSet());
        if (externalIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PcAbstractRole> roles = abstractRoleMapper.selectList(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, tenantId)
            .in(PcAbstractRole::getExternalId, externalIds)
            .eq(PcAbstractRole::getDeleteFlag, NOT_DELETED));
        return roles.stream().collect(Collectors.toMap(PcAbstractRole::getExternalId, Function.identity(), (a, b) -> a));
    }

    private PcAbstractUser resolveUser(Map<String, Map<Integer, PcAbstractUser>> cache, String externalId, Integer userType) {
        Map<Integer, PcAbstractUser> byType = cache.get(externalId);
        return byType != null ? byType.get(userType) : null;
    }

    /**
     * 按 parentExternalId 拓扑排序：父在前、子在后，无依赖或找不到父的排最前
     */
    private List<SyncRolesReq.SyncRoleItem> topoSortRoles(List<SyncRolesReq.SyncRoleItem> items) {
        Map<String, SyncRolesReq.SyncRoleItem> byId = items.stream()
            .collect(Collectors.toMap(SyncRolesReq.SyncRoleItem::getExternalId, Function.identity(), (a, b) -> a));
        Set<String> batchIds = byId.keySet();
        List<SyncRolesReq.SyncRoleItem> result = new ArrayList<>(items.size());
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (SyncRolesReq.SyncRoleItem item : items) {
            topoVisitRole(item, byId, batchIds, visiting, visited, result);
        }
        return result;
    }

    private void topoVisitRole(SyncRolesReq.SyncRoleItem item,
                               Map<String, SyncRolesReq.SyncRoleItem> byId,
                               Set<String> batchIds, Set<String> visiting, Set<String> visited,
                               List<SyncRolesReq.SyncRoleItem> result) {
        if (visited.contains(item.getExternalId())) {
            return;
        }
        if (!visiting.add(item.getExternalId())) {
            throw new ServiceException("角色层级存在循环依赖: " + item.getExternalId());
        }
        if (StrUtil.isNotBlank(item.getParentExternalId()) && batchIds.contains(item.getParentExternalId())) {
            topoVisitRole(byId.get(item.getParentExternalId()), byId, batchIds, visiting, visited, result);
        }
        visiting.remove(item.getExternalId());
        visited.add(item.getExternalId());
        result.add(item);
    }

    private List<SyncResourcesReq.SyncResourceItem> topoSortResources(List<SyncResourcesReq.SyncResourceItem> items) {
        Map<String, SyncResourcesReq.SyncResourceItem> byCode = items.stream()
            .collect(Collectors.toMap(SyncResourcesReq.SyncResourceItem::getCode, Function.identity(), (a, b) -> a));
        Set<String> batchCodes = byCode.keySet();
        List<SyncResourcesReq.SyncResourceItem> result = new ArrayList<>(items.size());
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (SyncResourcesReq.SyncResourceItem item : items) {
            topoVisitResource(item, byCode, batchCodes, visiting, visited, result);
        }
        return result;
    }

    private void topoVisitResource(SyncResourcesReq.SyncResourceItem item,
                                   Map<String, SyncResourcesReq.SyncResourceItem> byCode,
                                   Set<String> batchCodes, Set<String> visiting, Set<String> visited,
                                   List<SyncResourcesReq.SyncResourceItem> result) {
        if (visited.contains(item.getCode())) {
            return;
        }
        if (!visiting.add(item.getCode())) {
            throw new ServiceException("资源层级存在循环依赖: " + item.getCode());
        }
        if (StrUtil.isNotBlank(item.getParentCode()) && batchCodes.contains(item.getParentCode())) {
            topoVisitResource(byCode.get(item.getParentCode()), byCode, batchCodes, visiting, visited, result);
        }
        visiting.remove(item.getCode());
        visited.add(item.getCode());
        result.add(item);
    }
}
