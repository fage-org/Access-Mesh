package cn.ac.fage.accessmesh.access.engine.query;

import java.util.List;
import java.util.Set;

/**
 * 互斥冲突证据（T-PERM-088，设计 §6.1）。
 * <p>
 * 按 execution＋实际内部 item＋stage＋ruleRef 聚合的最小证据单元：重复输入的不同 key
 * 各自成证（不按 entity 误去重），同规则跨 scope／instance 阶段按 stage 维分别成证；
 * 共享父被多项引用为一条父证据关联全部受影响根项，不虚构多次父冲突。根 execute 统一
 * 受控提交一次；纯规则计算与父项自身不发日志。{@code stage=null} 表示主体解析阶段的
 * 角色互斥命中（此时 {@code affectedRootItemKeys} 为全部根项 key）。内部模型，
 * 不未经版本化扩散到普通 SDK（外部错误映射在适配层保持）。
 * </p>
 *
 * @param executionId                所属执行 ID
 * @param ruleRef                    命中规则引用（PERM 规则 ID 或角色对）
 * @param evaluationItemId           实际内部 item 标识（根项 key 或父项内部 ID）
 * @param affectedRootItemKeys       受影响根项 key（主体解析证据=全部根项）
 * @param stage                      命中阶段；null=主体解析（ROLE_MUTEX）
 * @param actualConflictingOperationIds 实际冲突操作权限 ID（角色对证据为空）
 * @param completion                 提交时执行完成度
 */
public record ConflictEvidence(String executionId, RuleRef ruleRef, String evaluationItemId,
                               List<String> affectedRootItemKeys, Stage stage,
                               Set<Long> actualConflictingOperationIds, Completion completion) {

    public ConflictEvidence {
        ruleRef = java.util.Objects.requireNonNull(ruleRef);
        affectedRootItemKeys = affectedRootItemKeys == null ? List.of() : List.copyOf(affectedRootItemKeys);
        // LinkedHashSet 保插入序：两端操作/受影响键的审计摘要确定可断言（Set.copyOf 顺序不定）
        actualConflictingOperationIds = actualConflictingOperationIds == null || actualConflictingOperationIds.isEmpty()
            ? Set.of() : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(actualConflictingOperationIds));
        java.util.Objects.requireNonNull(completion);
    }

    /** 提交时执行完成度（§6.1）。 */
    public enum Completion {

        /** 执行完整完成后的受控提交。 */
        COMPLETE,

        /** 执行中途技术失败，仅提交此前已确认阶段的证据（不覆盖主异常）。 */
        EXECUTION_ERROR_AFTER_CONFIRMED_STAGE
    }

    /** 规则引用：PERM 互斥规则 ID 或角色互斥对。 */
    public sealed interface RuleRef permits PermRuleId, RolePair {}

    /** PERM_MUTEX 规则引用。 */
    public record PermRuleId(long ruleId) implements RuleRef {}

    /** ROLE_MUTEX 角色对引用（角色对↔规则一一对应，配对证据无损，见 T-PERM-083）。 */
    public record RolePair(long firstRoleId, long secondRoleId) implements RuleRef {}
}
