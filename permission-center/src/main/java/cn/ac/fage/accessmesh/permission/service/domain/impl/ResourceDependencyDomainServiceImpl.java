package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceDependencyDomainService;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资源依赖领域服务实现类
 * <p>
 * 提供资源依赖关系的自动授权和清理功能。
 * 资源依赖定义了权限级联规则：当用户对源资源执行某操作时，
 * 如果触发依赖条件，系统自动授予目标资源的相应权限。
 * 自动授予的权限标记grantSource为AUTO_DEP，便于追踪和清理。
 * 核心功能包括：
 * - processDependencies：处理单个权限授予的依赖触发
 * - cleanupDependencies：清理权限撤销时的级联权限
 * - autoGrantForInsert：批量插入时预计算自动授权
 * 版本递增在事务提交后执行，防止缓存被回滚数据污染。
 * </p>
 * <p>
 * 注意：grantedBits 存储 OperationPermission.binaryBit 值。
 * </p>
 */
@Service
public class ResourceDependencyDomainServiceImpl implements ResourceDependencyDomainService {

    private static final Logger log = LoggerFactory.getLogger(ResourceDependencyDomainServiceImpl.class);

    private final ResourceDependencyMapper dependencyMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final PermissionVersionDomainService permissionVersionDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param dependencyMapper            资源依赖数据访问层
     * @param rolePermMapper              角色权限数据访问层
     * @param resourceEntityMapper        资源实体数据访问层
     * @param operationPermissionMapper   操作权限数据访问层
     * @param permissionVersionDomainService 权限版本领域服务
     */
    public ResourceDependencyDomainServiceImpl(ResourceDependencyMapper dependencyMapper,
                                                RoleResourcePermissionMapper rolePermMapper,
                                                ResourceEntityMapper resourceEntityMapper,
                                                OperationPermissionMapper operationPermissionMapper,
                                                PermissionVersionDomainService permissionVersionDomainService) {
        this.dependencyMapper = dependencyMapper;
        this.rolePermMapper = rolePermMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.permissionVersionDomainService = permissionVersionDomainService;
    }

    /**
     * 处理资源依赖触发
     * <p>
     * 当授予某资源的权限时，检查是否存在依赖规则需要自动授权。
     * 遍历源资源的所有依赖规则，如果操作位触发条件满足且autoGrant为true，
     * 自动授予目标资源的相应权限。
     * </p>
     *
     * @param tenantId        租户ID
     * @param roleId          角色ID
     * @param resourceEntityId 源资源实体ID
     * @param operationBits   授予的操作位掩码（binaryBit）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processDependencies(Long tenantId, Long roleId, Long resourceEntityId, Long operationBits) {
        List<ResourceDependency> deps = dependencyMapper.selectByResourceEntityId(tenantId, resourceEntityId);

        for (ResourceDependency dep : deps) {
            if (dep.getAutoGrant() != null && dep.getAutoGrant()
                && isTriggered(dep.getSourceOperationBits(), operationBits)) {
                autoGrantDependency(tenantId, roleId, dep);
            }
        }
    }

    /**
     * 清理资源依赖级联权限
     * <p>
     * 当撤销某资源的权限时，清理所有由此资源自动授权的级联权限。
     * 查询grantSource为AUTO_DEP且源资源匹配的权限记录，批量软删除。
     * 使用批量软删除优化性能，避免逐条更新。
     * 版本递增在事务提交后执行。
     * </p>
     *
     * @param tenantId        租户ID
     * @param roleId          角色ID
     * @param resourceEntityId 源资源实体ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupDependencies(Long tenantId, Long roleId, Long resourceEntityId) {
        List<RoleResourcePermission> autoGrants = rolePermMapper.selectAutoGrantsByResource(tenantId, roleId, resourceEntityId);

        Set<Long> affectedRoles = new HashSet<>();
        // 批量软删除（性能优化：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        if (!autoGrants.isEmpty()) {
            List<Long> idsToDelete = autoGrants.stream()
                .map(RoleResourcePermission::getId)
                .collect(java.util.stream.Collectors.toList());
            rolePermMapper.softDeleteBatch(tenantId, idsToDelete, now);
            affectedRoles.add(roleId);
        }

        if (!affectedRoles.isEmpty()) {
            // 版本递增（事务提交后执行）
            final Set<Long> affectedRolesForCache = affectedRoles;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        permissionVersionDomainService.batchIncrement(tenantId, affectedRolesForCache);
                    }
                });
            }
        }
    }

    /**
     * 批量插入时预计算自动授权
     * <p>
     * 在批量授予权限前，预先计算需要自动授权的依赖权限。
     * 使用批量查询和缓存优化，避免嵌套循环中的N+1查询问题：
     * 1. 预加载操作权限缓存用于O(1)查找
     * 2. 批量查询所有相关依赖规则
     * 3. 预加载现有自动授权记录避免重复授权
     * 返回需要额外插入的自动授权权限列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param toInsert 待插入的权限列表
     * @return 需要自动授权的权限列表
     */
    @Override
    public List<RoleResourcePermission> autoGrantForInsert(Long tenantId, Long roleId, List<RoleResourcePermission> toInsert) {
        List<RoleResourcePermission> autoGranted = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 收集所有待授权的资源ID
        Set<Long> resourceIds = new HashSet<>();
        for (RoleResourcePermission rp : toInsert) {
            if (rp.getResourceEntityId() != null) {
                resourceIds.add(rp.getResourceEntityId());
            }
        }
        if (resourceIds.isEmpty()) {
            return autoGranted;
        }

        // 性能优化：预加载操作权限缓存用于嵌套循环O(1)查找
        Map<String, OperationPermission> opPermCache = new HashMap<>();
        // 加载所有操作权限用于 binaryBit 匹配
        if (!toInsert.isEmpty()) {
            Set<Integer> resourceTypes = toInsert.stream()
                .map(RoleResourcePermission::getResourceType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            for (Integer resourceType : resourceTypes) {
                for (OperationPermission op : operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType)) {
                    opPermCache.put(resourceType + ":" + op.getBinaryBit(), op);
                }
            }
        }

        // 查找源资源在待授权资源中的依赖规则
        List<ResourceDependency> deps = dependencyMapper.selectAutoGrantByResourceIds(tenantId, resourceIds);

        // 预加载所有现有自动授权记录，避免嵌套循环中的N+1查询
        Set<Long> targetResourceIds = deps.stream()
            .map(ResourceDependency::getDependsOnResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> depIds = deps.stream()
            .map(ResourceDependency::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, Map<Long, RoleResourcePermission>> existingAutoGrants = targetResourceIds.isEmpty() ? Map.of()
            : rolePermMapper.selectExistingAutoGrants(tenantId, roleId, targetResourceIds, depIds)
            .stream().collect(Collectors.groupingBy(
                RoleResourcePermission::getResourceEntityId,
                Collectors.toMap(RoleResourcePermission::getGrantDepId, Function.identity(), (a, b) -> a)
            ));

        for (ResourceDependency dep : deps) {
            for (RoleResourcePermission rp : toInsert) {
                if (Objects.equals(rp.getResourceEntityId(), dep.getResourceEntityId())
                    && isTriggered(dep.getSourceOperationBits(), getEffectiveOpBitsFromGrantedBits(rp.getGrantedBits(), rp.getResourceType(), opPermCache))) {
                    // 使用预加载缓存检查是否已授权（无查询）
                    Map<Long, RoleResourcePermission> resourceGrants = existingAutoGrants.get(dep.getDependsOnResourceEntityId());
                    boolean alreadyGranted = resourceGrants != null && resourceGrants.containsKey(dep.getId());

                    if (!alreadyGranted) {
                        Long requiredOpBinaryBit = resolveOperationPermissionBinaryBit(
                            tenantId, dep.getDependsOnResourceEntityId(), dep.getRequiredOperationBits(), null);
                        if (requiredOpBinaryBit == null) {
                            continue;
                        }
                        RoleResourcePermission autoRp = new RoleResourcePermission();
                        autoRp.setTenantId(tenantId);
                        autoRp.setAbstractRoleId(roleId);
                        autoRp.setResourceEntityId(dep.getDependsOnResourceEntityId());
                        autoRp.setGrantedBits(requiredOpBinaryBit);
                        autoRp.setResourceType(resolveResourceType(dep.getDependsOnResourceEntityId()));
                        autoRp.setDependOn(null);
                        autoRp.setScopeAll(false);
                        autoRp.setCanGrant(false);
                        autoRp.setConditionId(null);
                        autoRp.setGrantSource(GrantSource.AUTO_DEP.getValue());
                        autoRp.setGrantDepId(dep.getId());
                        autoRp.setCreatedAt(now);
                        autoRp.setUpdatedAt(now);
                        autoRp.setDeleteFlag(0L);
                        autoGranted.add(autoRp);
                    }
                }
            }
        }

        return autoGranted;
    }

    /**
     * 判断操作位是否触发依赖条件
     * <p>
     * 使用位运算检查授予的操作位是否包含依赖规则要求的操作位。
     * 如果sourceBits为null，默认触发所有依赖。
     * </p>
     *
     * @param sourceBits   依赖规则要求的源操作位
     * @param operationBits 授予的操作位掩码（binaryBit 或 effectiveBits）
     * @return 是否触发依赖
     */
    private boolean isTriggered(Long sourceBits, Long operationBits) {
        if (sourceBits == null) return true;
        return (sourceBits & operationBits) != 0;
    }

    /**
     * 从 grantedBits 获取有效操作位
     * <p>
     * 根据 grantedBits（binaryBit）从缓存中查找 OperationPermission，
     * 返回其 effectiveBits（binaryBit | inheritMask）。
     * </p>
     *
     * @param grantedBits 授予的 binaryBit
     * @param cache       预加载的操作权限缓存
     * @return 有效操作位，不存在返回0
     */
    private Long getEffectiveOpBitsFromGrantedBits(Long grantedBits, Integer resourceType, Map<String, OperationPermission> cache) {
        if (grantedBits == null) return 0L;
        OperationPermission operation = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(cache, resourceType, grantedBits);
        return operation == null ? 0L : operation.getEffectiveBits();
    }

    /**
     * 自动授权依赖权限
     * <p>
     * 当依赖触发条件满足时，自动授予目标资源的权限。
     * 创建的权限记录标记grantSource为AUTO_DEP，grantDepId为依赖规则ID。
     * 版本递增在事务提交后执行。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param dep      触发的依赖规则
     */
    private void autoGrantDependency(Long tenantId, Long roleId, ResourceDependency dep) {
        Long requiredOpBinaryBit = resolveOperationPermissionBinaryBit(
            tenantId, dep.getDependsOnResourceEntityId(), dep.getRequiredOperationBits(), null);
        if (requiredOpBinaryBit == null) {
            return;
        }

        if (rolePermMapper.countByCompositeKey(tenantId, roleId, dep.getDependsOnResourceEntityId(), requiredOpBinaryBit) > 0) {
            return;
        }
        log.info("Auto-granting dependency: role={}, resource={}, binaryBit={}", roleId, dep.getDependsOnResourceEntityId(), requiredOpBinaryBit);

        RoleResourcePermission rp = new RoleResourcePermission();
        rp.setTenantId(tenantId);
        rp.setAbstractRoleId(roleId);
        rp.setResourceEntityId(dep.getDependsOnResourceEntityId());
        rp.setGrantedBits(requiredOpBinaryBit);
        rp.setResourceType(resolveResourceType(dep.getDependsOnResourceEntityId()));
        rp.setScopeAll(false);
        rp.setGrantSource(GrantSource.AUTO_DEP.getValue());
        rp.setGrantDepId(dep.getId());
        rp.setCanGrant(false);
        LocalDateTime now = LocalDateTime.now();
        rp.setCreatedAt(now);
        rp.setUpdatedAt(now);
        rp.setDeleteFlag(0L);
        rolePermMapper.insert(rp);
        // 版本递增（事务提交后执行）
        final Long roleIdForCache = roleId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permissionVersionDomainService.increment(tenantId, roleIdForCache);
                }
            });
        }
        log.info("Auto-granted dependency: role={}, resource={}", roleId, dep.getDependsOnResourceEntityId());
    }

    /**
     * 解析操作权限的 binaryBit 值
     * <p>
     * 根据资源类型和操作位掩码查找匹配的操作权限的 binaryBit。
     * 使用SQL位运算过滤，避免全表加载。
     * 如果requiredBits为空或零，返回fallbackBinaryBit。
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceEntityId 资源实体ID
     * @param requiredBits    要求的操作位掩码
     * @param fallbackBinaryBit 失败时的回退 binaryBit
     * @return binaryBit 值，未找到返回fallback
     */
    private Long resolveOperationPermissionBinaryBit(Long tenantId, Long resourceEntityId, Long requiredBits, Long fallbackBinaryBit) {
        if (requiredBits == null || requiredBits == 0L) {
            return fallbackBinaryBit;
        }
        Integer resourceType = null;
        if (resourceEntityId != null) {
            ResourceEntity resource = resourceEntityMapper.selectValidById(tenantId, resourceEntityId);
            if (resource != null) {
                resourceType = resource.getResourceType();
            }
        }
        // 使用SQL位运算过滤避免全表加载
        List<OperationPermission> matchingOps = operationPermissionMapper.selectByEffectiveBitsMatch(
            tenantId, resourceType, requiredBits);
        if (matchingOps.isEmpty()) {
            return fallbackBinaryBit;
        }
        for (OperationPermission operation : matchingOps) {
            if (Objects.equals(operation.getBinaryBit(), requiredBits)) {
                return operation.getBinaryBit();
            }
        }
        for (OperationPermission operation : matchingOps) {
            if (Objects.equals(operation.getEffectiveBits(), requiredBits)) {
                return operation.getBinaryBit();
            }
        }
        return fallbackBinaryBit;
    }

    private Integer resolveResourceType(Long resourceEntityId) {
        if (resourceEntityId == null) return null;
        ResourceEntity resource = resourceEntityMapper.selectOneById(resourceEntityId);
        return resource != null ? resource.getResourceType() : null;
    }
}