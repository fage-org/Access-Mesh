package cn.ac.fage.accessmesh.access.permission.vo;

import java.util.List;

/**
 * 权限互斥过滤结果（T-PERM-033 explain DTO 扩展）。
 * <p>
 * 在 {@code filterPermMutex} 的过滤语义之外，保留被互斥规则丢弃的条目及其命中规则，
 * 供权限排查视图展示「本可命中但被互斥规则移除」的条目。只读排查路径使用，
 * 不触发冲突通知。
 * </p>
 *
 * @param survivors 通过互斥过滤的条目
 * @param drops     被互斥规则丢弃的条目及命中规则详情
 */
public record MutexFilterResult(
    List<RolePermEntry> survivors,
    List<MutexDrop> drops
) {

    /**
     * 被互斥规则丢弃的单条条目
     *
     * @param entry              被丢弃的授权条目
     * @param ruleId             命中的冲突规则ID
     * @param firstOperationCode  规则一侧操作码
     * @param secondOperationCode 规则另一侧操作码
     */
    public record MutexDrop(
        RolePermEntry entry,
        Long ruleId,
        String firstOperationCode,
        String secondOperationCode
    ) {}
}
