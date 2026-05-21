package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.ConflictInfo;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.GrantedPermission;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQuery;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQueryResult;
import cn.ac.fage.accessmesh.permquery.domain.repository.EntityLoadRepository;
import cn.ac.fage.accessmesh.permquery.domain.repository.PermissionEntryRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 权限查询管线
 * <p>
 * 核心编排服务，协调各领域服务完成权限查询流程。
 * 返回原始权限数据，调用方自行判断权限结果。
 * </p>
 *
 * <h3>查询流程</h3>
 * <ol>
 *   <li>解析用户角色</li>
 *   <li>解析资源类型和操作</li>
 *   <li>计算位掩码</li>
 *   <li>查询类型级权限（scopeAll=true）</li>
 *   <li>查询实例级权限</li>
 *   <li>评估条件（按选项）</li>
 *   <li>检测冲突（按选项）</li>
 *   <li>加载辅助实体（按选项）</li>
 *   <li>构建结果</li>
 * </ol>
 */
@Service
public class PermissionQueryPipeline {

    private final RoleResolutionService roleResolutionService;
    private final TypeResolver typeResolver;
    private final BitMaskCalculator bitMaskCalculator;
    private final PermissionEntryRepository permissionEntryRepository;
    private final EntityLoadRepository entityLoadRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final ConflictResolver conflictResolver;

    public PermissionQueryPipeline(RoleResolutionService roleResolutionService,
                                    TypeResolver typeResolver,
                                    BitMaskCalculator bitMaskCalculator,
                                    PermissionEntryRepository permissionEntryRepository,
                                    EntityLoadRepository entityLoadRepository,
                                    ConditionEvaluator conditionEvaluator,
                                    ConflictResolver conflictResolver) {
        this.roleResolutionService = roleResolutionService;
        this.typeResolver = typeResolver;
        this.bitMaskCalculator = bitMaskCalculator;
        this.permissionEntryRepository = permissionEntryRepository;
        this.entityLoadRepository = entityLoadRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.conflictResolver = conflictResolver;
    }

    /**
     * 执行权限查询管线
     * <p>
     * 返回完整权限数据，不做"允许/拒绝"决策。
     * </p>
     *
     * @param query 权限查询参数
     * @return 权限查询结果
     */
    public PermissionQueryResult execute(PermissionQuery query) {
        if (query == null) {
            return PermissionQueryResult.empty();
        }

        PermissionQueryResult.Builder builder = PermissionQueryResult.builder()
            .tenantId(query.tenantId());

        // 1. 解析角色
        Set<Long> roleIds = resolveRoleIds(query);
        builder.resolvedRoleIds(roleIds);
        if (roleIds.isEmpty()) {
            return builder.build(); // 无角色，返回空结果
        }

        // 2. 解析资源类型
        Set<Integer> resourceTypes = typeResolver.resolveResourceTypes(query);
        builder.resolvedResourceTypes(resourceTypes);

        // 3. 解析操作ID
        Set<Long> operationIds = typeResolver.resolveOperations(query);

        // 4. 计算位掩码
        Map<Integer, Long> bitMasks = bitMaskCalculator.calculate(query.tenantId(), resourceTypes, operationIds);

        // 5. 查询类型级权限（scopeAll=true）
        List<GrantedPermission> scopeAllPerms = List.of();
        if (query.options().queryScopeAll() && !bitMasks.isEmpty()) {
            scopeAllPerms = permissionEntryRepository.queryScopeAllPermissions(query.tenantId(), roleIds, bitMasks);
            builder.scopeAllPermissions(scopeAllPerms);
        }

        // 6. 查询实例级权限
        List<GrantedPermission> instancePerms = List.of();
        if (query.options().queryInstance() && !bitMasks.isEmpty()) {
            Set<Long> entityIds = resolveEntityIds(query);
            if (!entityIds.isEmpty()) {
                instancePerms = permissionEntryRepository.queryInstancePermissions(
                    query.tenantId(), roleIds, entityIds, bitMasks);
                builder.instancePermissions(instancePerms);
            }
        }

        // 7. 合并所有权限条目
        List<GrantedPermission> allPerms = Stream.concat(scopeAllPerms.stream(), instancePerms.stream())
            .toList();

        // 8. 评估条件（返回结果而非过滤）
        if (query.options().evaluateConditions() && !allPerms.isEmpty()) {
            Map<Long, Boolean> conditionResults = conditionEvaluator.evaluate(
                query.tenantId(), allPerms, query.options().context());
            builder.conditionResults(conditionResults);

            // 应用评估结果到权限条目（使用预计算的ID集合避免O(n*m)）
            List<GrantedPermission> evaluatedPerms = conditionEvaluator.applyEvaluationResults(allPerms, conditionResults);
            Set<Long> scopeAllPermIds = scopeAllPerms.stream()
                .map(GrantedPermission::permissionId)
                .collect(Collectors.toSet());
            Set<Long> instancePermIds = instancePerms.stream()
                .map(GrantedPermission::permissionId)
                .collect(Collectors.toSet());

            builder.scopeAllPermissions(evaluatedPerms.stream()
                .filter(p -> p.isScopeAll() || scopeAllPermIds.contains(p.permissionId()))
                .toList());
            builder.instancePermissions(evaluatedPerms.stream()
                .filter(p -> !p.isScopeAll() && instancePermIds.contains(p.permissionId()))
                .toList());
        }

        // 9. 检测冲突（返回信息而非过滤）
        if (query.options().evaluateConflicts() && !allPerms.isEmpty()) {
            List<ConflictInfo> conflicts = conflictResolver.detectConflicts(query.tenantId(), allPerms);
            builder.conflicts(conflicts);

            // 应用冲突标记到权限条目（使用预计算的ID集合避免O(n*m)）
            List<GrantedPermission> conflictMarkedPerms = conflictResolver.applyConflictMarks(allPerms, conflicts);
            Set<Long> scopeAllPermIds = scopeAllPerms.stream()
                .map(GrantedPermission::permissionId)
                .collect(Collectors.toSet());
            Set<Long> instancePermIds = instancePerms.stream()
                .map(GrantedPermission::permissionId)
                .collect(Collectors.toSet());

            builder.scopeAllPermissions(conflictMarkedPerms.stream()
                .filter(p -> p.isScopeAll() || scopeAllPermIds.contains(p.permissionId()))
                .toList());
            builder.instancePermissions(conflictMarkedPerms.stream()
                .filter(p -> !p.isScopeAll() && instancePermIds.contains(p.permissionId()))
                .toList());
        }

        // 10. 加载辅助实体信息
        loadAncillary(query, builder, allPerms, roleIds);

        return builder.build();
    }

    /**
     * 解析角色ID
     * <p>
     * 如果查询参数中已指定角色ID，直接使用；
     * 否则通过 userId 解析用户的有效角色。
     * </p>
     */
    private Set<Long> resolveRoleIds(PermissionQuery query) {
        // 如果已指定角色ID，直接使用
        if (query.roleIds() != null && !query.roleIds().isEmpty()) {
            return query.roleIds();
        }

        // 通过 userId 解析
        if (query.userId() == null) {
            return Collections.emptySet();
        }

        return roleResolutionService.resolve(query.tenantId(), query.userId());
    }

    /**
     * 解析资源实体ID
     * <p>
     * 如果查询参数中已指定实体ID，直接使用；
     * 否则通过 resourceCodes 解析。
     * </p>
     */
    private Set<Long> resolveEntityIds(PermissionQuery query) {
        // 如果已指定实体ID，直接使用
        if (query.resourceEntityIds() != null && !query.resourceEntityIds().isEmpty()) {
            return new HashSet<>(query.resourceEntityIds());
        }

        // 通过 typeResolver 解析
        return typeResolver.resolveResources(query);
    }

    /**
     * 加载辅助实体信息
     * <p>
     * 根据查询选项加载角色、资源、操作、条件等实体信息。
     * </p>
     */
    private void loadAncillary(PermissionQuery query, PermissionQueryResult.Builder builder,
                               List<GrantedPermission> allPerms, Set<Long> roleIds) {
        // 加载角色信息
        if (query.options().loadRoles() && roleIds != null && !roleIds.isEmpty()) {
            Map<Long, AbstractRole> roles = entityLoadRepository.loadRoles(query.tenantId(), roleIds);
            builder.roles(roles);
        }

        // 加载资源信息
        if (query.options().loadResources()) {
            Set<Long> queryEntityIds = query.resourceEntityIds() != null ? query.resourceEntityIds() : Set.of();
            Set<Long> allResourceIds = Stream.concat(
                allPerms.stream()
                    .map(GrantedPermission::resourceEntityId)
                    .filter(Objects::nonNull),
                queryEntityIds.stream()
            ).collect(Collectors.toSet());
            if (!allResourceIds.isEmpty()) {
                Map<Long, ResourceEntity> resources = entityLoadRepository.loadResources(query.tenantId(), allResourceIds);
                builder.resources(resources);
            }
        }

        // 加载操作信息
        if (query.options().loadOperations()) {
            // 收集 grantedBits 并按资源类型分组
            Map<Integer, Set<Long>> grantedBitsByType = allPerms.stream()
                .filter(p -> p.resourceType() != null && p.grantedBits() != 0)
                .collect(Collectors.groupingBy(
                    GrantedPermission::resourceType,
                    Collectors.mapping(GrantedPermission::grantedBits, Collectors.toSet())
                ));

            if (!grantedBitsByType.isEmpty()) {
                // 批量加载所有资源类型的操作权限（避免N+1）
                Set<Integer> allResourceTypes = grantedBitsByType.keySet();
                Map<Integer, List<OperationPermission>> allOpsByType =
                    entityLoadRepository.loadOperationsByResourceTypes(query.tenantId(), allResourceTypes);

                // 按资源类型过滤匹配的权限
                Map<Long, OperationPermission> operationMap = new LinkedHashMap<>();
                for (Map.Entry<Integer, Set<Long>> entry : grantedBitsByType.entrySet()) {
                    List<OperationPermission> ops = allOpsByType.getOrDefault(entry.getKey(), List.of());
                    for (OperationPermission op : ops) {
                        if (entry.getValue().contains(op.getBinaryBit())) {
                            operationMap.put(op.getId(), op);
                        }
                    }
                }
                builder.operations(operationMap);
            }
        }

        // 加载条件信息
        if (query.options().loadConditions()) {
            Set<Long> conditionIds = allPerms.stream()
                .map(GrantedPermission::conditionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            if (!conditionIds.isEmpty()) {
                Map<Long, PermissionCondition> conditions = entityLoadRepository.loadConditions(query.tenantId(), conditionIds);
                builder.conditions(conditions);
            }
        }
    }
}