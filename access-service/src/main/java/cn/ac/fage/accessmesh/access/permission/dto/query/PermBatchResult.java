package cn.ac.fage.accessmesh.access.permission.dto.query;

import java.util.List;
import java.util.Set;

/**
 * 统一权限批量查询结果（T-PERM-061 A+ 形态）。
 * <p>
 * {@link PermBatchResult#outcomes} 与 {@link PermBatchQuery#items()} 按下标一一对齐
 * （原始输入序）；批量路径不装配辅助 map——matched 字段族由评估后条目派生。
 * 拒绝项 matched 字段族恒空集合。
 * </p>
 *
 * @param outcomes 逐 item 结果（与输入 items 等长、下标对齐）
 */
public record PermBatchResult(List<ItemOutcome> outcomes) {

    public PermBatchResult {
        outcomes = List.copyOf(outcomes != null ? outcomes : List.of());
    }

    /**
     * 单项批量判定结果。
     *
     * @param allowed 是否允许
     * @param reason  拒绝原因（允许时 null；词表与单条 forAuthCheck 一致）
     * @param matchedRoleIds 命中角色ID集合（拒绝时空集合）
     * @param matchedPermissionIds 命中权限ID集合（拒绝时空集合）
     */
    public record ItemOutcome(boolean allowed, String reason,
                              Set<Long> matchedRoleIds, Set<Long> matchedPermissionIds) {

        public ItemOutcome {
            matchedRoleIds = matchedRoleIds != null ? Set.copyOf(matchedRoleIds) : Set.of();
            matchedPermissionIds = matchedPermissionIds != null ? Set.copyOf(matchedPermissionIds) : Set.of();
        }

        /** 拒绝项工厂（matched 字段族恒空——拒绝项不回传内部行 ID）。 */
        public static ItemOutcome deny(String reason) {
            return new ItemOutcome(false, reason, Set.of(), Set.of());
        }

        /** 允许项工厂（matched 字段族由评估后条目派生）。 */
        public static ItemOutcome allow(Set<Long> matchedRoleIds, Set<Long> matchedPermissionIds) {
            return new ItemOutcome(true, null, matchedRoleIds, matchedPermissionIds);
        }
    }
}
