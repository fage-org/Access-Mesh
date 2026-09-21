package cn.ac.fage.accessmesh.access.grant.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授撤影响预览响应（T-PERM-073，契约 §11.4.1）。
 *
 * <p>预览仅供参考（advisory 恒 true）：基于当前种子得到 beforeDesired、计划假想状态得到
 * afterDesired，added/removed/retained 是计划导致的变化；现有漂移由 driftDetected 表示，
 * 不计入计划影响。保存按服务端事务内最新事实重算，不要求与预览一致。</p>
 *
 * @param advisory        恒 true（仅供参考，不构成保存凭证）
 * @param viewedAt        一致视图读取时间
 * @param removed         计划导致回收的自动事实（无其他显式来源支持）
 * @param added           计划导致新增的自动事实
 * @param retained        受本计划影响但仍有其他显式来源支持的自动事实
 * @param removedTotal    removed 组完整数量（maxItems 只截断展示，分组数字以本字段为准）
 * @param addedTotal      added 组完整数量
 * @param retainedTotal   retained 组完整数量
 * @param totalCount      三组完整数量之和（maxItems 只截断展示）
 * @param truncated       输出数小于 totalCount 时 true——空数组不能独立解释为无影响
 * @param driftDetected   现有 desired 与 actual AUTO_DEP 已有漂移（非计划导致）
 */
public record GrantPlanPreviewResp(
    boolean advisory,
    LocalDateTime viewedAt,
    List<FactElement> removed,
    List<FactElement> added,
    List<FactElement> retained,
    long removedTotal,
    long addedTotal,
    long retainedTotal,
    long totalCount,
    boolean truncated,
    boolean driftDetected
) {

    /** 影响事实元素：完整事实键 + 根显式来源引用（不返回完整路径组合）。 */
    public record FactElement(FactKey fact, List<SeedRef> seeds) {}

    /** 完整逻辑事实键（资源业务键 + canonical 操作码 + 条件身份）。 */
    public record FactKey(ResourceKey resource, String operationCode, ConditionRef conditionRef) {}

    /** 资源业务键（codeType 归一为非空默认值；防御面资源已软删时字段为 null）。 */
    public record ResourceKey(String resourceTypeCode, String resourceCode, String codeType) {}

    /**
     * 条件身份：NONE（无条件）/ EXISTING（既有条件，conditionId + 可见描述 conditionCode）/
     * PREVIEW_INLINE（预览临时身份，requestItemRef 如 creates[0]、updates[1]，未分配物理 ID）。
     */
    public record ConditionRef(String kind, Long conditionId, String conditionCode, String requestItemRef) {

        public static ConditionRef none() {
            return new ConditionRef("NONE", null, null, null);
        }

        public static ConditionRef existing(Long conditionId, String conditionCode) {
            return new ConditionRef("EXISTING", conditionId, conditionCode, null);
        }

        public static ConditionRef previewInline(String requestItemRef) {
            return new ConditionRef("PREVIEW_INLINE", null, null, requestItemRef);
        }
    }

    /** 根显式来源引用：现有显式授权返回 permissionId，预览新建来源返回请求条目位置——严格二选一。 */
    public record SeedRef(Long permissionId, String requestItemRef) {}
}
