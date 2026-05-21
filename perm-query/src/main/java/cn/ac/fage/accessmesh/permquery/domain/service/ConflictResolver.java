package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.ConflictInfo;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.GrantedPermission;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.EntityBatchLoadAdapter;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConflictRuleMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 冲突解决领域服务
 * <p>
 * 负责检测权限冲突，返回冲突信息列表（不过滤权限条目）。
 * 支持两种冲突类型：
 * - 角色互斥（ROLE_MUTEX）：两个角色不能同时拥有
 * - 权限互斥（PERM_MUTEX）：两个操作权限不能同时授予
 * </p>
 */
@Service
public class ConflictResolver {

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final EntityBatchLoadAdapter entityBatchLoadAdapter;

    public ConflictResolver(PermissionConflictRuleMapper conflictRuleMapper,
                            EntityBatchLoadAdapter entityBatchLoadAdapter) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.entityBatchLoadAdapter = entityBatchLoadAdapter;
    }

    /**
     * 检测权限冲突
     * <p>
     * 返回检测到的冲突信息列表，调用方自行决定如何处理。
     * </p>
     *
     * @param tenantId 租户ID
     * @param entries  权限条目列表
     * @return 冲突信息列表
     */
    public List<ConflictInfo> detectConflicts(Long tenantId, List<GrantedPermission> entries) {
        if (tenantId == null || entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConflictInfo> conflicts = new ArrayList<>();

        // 1. 检测角色互斥冲突
        conflicts.addAll(detectRoleMutexConflicts(tenantId, entries));

        // 2. 检测权限互斥冲突
        conflicts.addAll(detectPermMutexConflicts(tenantId, entries));

        return conflicts;
    }

    /**
     * 检测角色互斥冲突
     *
     * @param tenantId 租户ID
     * @param entries  权限条目列表
     * @return 冲突信息列表
     */
    private List<ConflictInfo> detectRoleMutexConflicts(Long tenantId, List<GrantedPermission> entries) {
        // 收集所有角色ID
        Set<Long> roleIds = entries.stream()
            .map(GrantedPermission::roleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (roleIds.size() < 2) {
            return Collections.emptyList(); // 只有一个角色，不可能有冲突
        }

        // 加载角色互斥规则
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(tenantId, "MUTEX_ROLE");
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConflictInfo> conflicts = new ArrayList<>();
        for (PermissionConflictRule rule : rules) {
            Long roleId1 = rule.getFirstAbstractRoleId();
            Long roleId2 = rule.getSecondAbstractRoleId();

            if (roleIds.contains(roleId1) && roleIds.contains(roleId2)) {
                // 找出涉及冲突角色的权限条目
                List<GrantedPermission> entriesWithRole1 = entries.stream()
                    .filter(e -> Objects.equals(e.roleId(), roleId1))
                    .toList();
                List<GrantedPermission> entriesWithRole2 = entries.stream()
                    .filter(e -> Objects.equals(e.roleId(), roleId2))
                    .toList();

                // 生成冲突信息（每对条目一个冲突）
                for (GrantedPermission e1 : entriesWithRole1) {
                    for (GrantedPermission e2 : entriesWithRole2) {
                        conflicts.add(ConflictInfo.roleMutex(
                            roleId1, roleId2,
                            e1.permissionId(), e2.permissionId()
                        ));
                    }
                }
            }
        }

        return conflicts;
    }

    /**
     * 检测权限互斥冲突
     *
     * @param tenantId 租户ID
     * @param entries  权限条目列表
     * @return 冲突信息列表
     */
    private List<ConflictInfo> detectPermMutexConflicts(Long tenantId, List<GrantedPermission> entries) {
        if (entries.size() < 2) {
            return Collections.emptyList();
        }

        // 加载权限互斥规则
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(tenantId, "MUTEX_OP");
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConflictInfo> conflicts = new ArrayList<>();

        // 按资源类型分组
        Map<Integer, List<GrantedPermission>> byType = entries.stream()
            .filter(e -> e.resourceType() != null)
            .collect(Collectors.groupingBy(GrantedPermission::resourceType));

        for (PermissionConflictRule rule : rules) {
            Long opId1 = rule.getFirstOperationPermissionId();
            Long opId2 = rule.getSecondOperationPermissionId();
            Integer resourceType = rule.getResourceTypeValue();

            if (resourceType == null || opId1 == null || opId2 == null) {
                continue;
            }

            List<GrantedPermission> typeEntries = byType.get(resourceType);
            if (typeEntries == null || typeEntries.size() < 2) {
                continue;
            }

            // 加载操作权限以获取 binaryBit
            Map<Long, cn.ac.fage.accessmesh.permission.entity.OperationPermission> ops =
                entityBatchLoadAdapter.batchLoadOperations(tenantId, Set.of(opId1, opId2));

            cn.ac.fage.accessmesh.permission.entity.OperationPermission op1 = ops.get(opId1);
            cn.ac.fage.accessmesh.permission.entity.OperationPermission op2 = ops.get(opId2);

            if (op1 == null || op2 == null || op1.getBinaryBit() == null || op2.getBinaryBit() == null) {
                continue;
            }

            // 找出包含这两个操作位的权限条目（位运算匹配）
            long targetBit1 = op1.getBinaryBit();
            long targetBit2 = op2.getBinaryBit();
            List<GrantedPermission> entriesWithOp1 = typeEntries.stream()
                .filter(e -> (e.grantedBits() & targetBit1) != 0)
                .toList();
            List<GrantedPermission> entriesWithOp2 = typeEntries.stream()
                .filter(e -> (e.grantedBits() & targetBit2) != 0)
                .toList();

            // 生成冲突信息
            for (GrantedPermission e1 : entriesWithOp1) {
                for (GrantedPermission e2 : entriesWithOp2) {
                    conflicts.add(ConflictInfo.permMutex(
                        e1.permissionId(), e2.permissionId()
                    ));
                }
            }
        }

        return conflicts;
    }

    /**
     * 应用冲突标记到权限条目
     * <p>
     * 将冲突标记填充到 GrantedPermission 的 hasConflict 字段。
     * </p>
     *
     * @param entries   权限条目列表
     * @param conflicts 冲突信息列表
     * @return 填充了冲突标记的权限条目列表
     */
    public List<GrantedPermission> applyConflictMarks(List<GrantedPermission> entries,
                                                      List<ConflictInfo> conflicts) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        if (conflicts == null || conflicts.isEmpty()) {
            return entries;
        }

        // 收集有冲突的权限ID
        Set<Long> conflictedIds = new HashSet<>();
        for (ConflictInfo conflict : conflicts) {
            conflictedIds.add(conflict.firstPermissionId());
            conflictedIds.add(conflict.secondPermissionId());
        }

        return entries.stream()
            .map(entry -> {
                if (conflictedIds.contains(entry.permissionId())) {
                    return entry.withConflict(true);
                }
                return entry;
            })
            .toList();
    }

    /**
     * 过滤掉有冲突的权限条目
     * <p>
     * 用于需要过滤的场景，返回无冲突的权限条目。
     * </p>
     *
     * @param entries   权限条目列表
     * @param conflicts 冲突信息列表
     * @return 无冲突的权限条目列表
     */
    public List<GrantedPermission> filterConflicted(List<GrantedPermission> entries,
                                                    List<ConflictInfo> conflicts) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        if (conflicts == null || conflicts.isEmpty()) {
            return entries;
        }

        // 收集有冲突的权限ID
        Set<Long> conflictedIds = new HashSet<>();
        for (ConflictInfo conflict : conflicts) {
            conflictedIds.add(conflict.firstPermissionId());
            conflictedIds.add(conflict.secondPermissionId());
        }

        return entries.stream()
            .filter(entry -> !conflictedIds.contains(entry.permissionId()))
            .toList();
    }

    /**
     * 过滤角色互斥冲突（返回有效角色集合）
     *
     * @param tenantId        租户ID
     * @param effectiveRoleIds 有效角色ID集合
     * @return 过滤后的有效角色ID集合
     */
    public Set<Long> filterRoleMutex(Long tenantId, Set<Long> effectiveRoleIds) {
        if (tenantId == null || effectiveRoleIds == null || effectiveRoleIds.size() < 2) {
            return effectiveRoleIds != null ? effectiveRoleIds : Collections.emptySet();
        }

        // 加载角色互斥规则
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(tenantId, "MUTEX_ROLE");
        if (rules.isEmpty()) {
            return effectiveRoleIds;
        }

        Set<Long> result = new HashSet<>(effectiveRoleIds);
        for (PermissionConflictRule rule : rules) {
            Long roleId1 = rule.getFirstAbstractRoleId();
            Long roleId2 = rule.getSecondAbstractRoleId();

            if (result.contains(roleId1) && result.contains(roleId2)) {
                // 同时移除两个互斥角色
                result.remove(roleId1);
                result.remove(roleId2);
            }
        }

        return result;
    }
}