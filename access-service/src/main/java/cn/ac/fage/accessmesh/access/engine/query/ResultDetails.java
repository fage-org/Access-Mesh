package cn.ac.fage.accessmesh.access.engine.query;

import java.util.List;
import java.util.Set;

/**
 * 结果可选块（T-PERM-082，设计 §3.3）。
 * <p>
 * 用 loadedSections 区分「没有请求」与「请求后为空」；不返回 RunState、
 * ORM 可变实体或缓存对象。拒绝项公开命中集保持空。
 * 事实行（GrantFact/StageFacts）与展示条目（PresentationEntry）随 T-PERM-086/087 扩展。
 * </p>
 *
 * @param loadedSections        本次装载的输出块集合；null 归一为空集
 * @param matchedRoleIds        命中角色 id；null 归一为空
 * @param matchedPermissionIds  命中权限 id；null 归一为空
 */
public record ResultDetails(Set<DetailSection> loadedSections, List<Long> matchedRoleIds,
                            List<Long> matchedPermissionIds) {

    public ResultDetails {
        loadedSections = loadedSections == null ? Set.of() : Set.copyOf(loadedSections);
        matchedRoleIds = matchedRoleIds == null ? List.of() : List.copyOf(matchedRoleIds);
        matchedPermissionIds = matchedPermissionIds == null ? List.of() : List.copyOf(matchedPermissionIds);
    }

    /** 空详情（拒绝/短路项缺省形态）。 */
    public static ResultDetails empty() {
        return new ResultDetails(Set.of(), List.of(), List.of());
    }

    /** 输出块（与 OutputSpec 请求面对齐）。 */
    public enum DetailSection {

        /** 命中 ID 块。 */
        MATCHED_IDS,

        /** 评估后事实块。 */
        FACTS_KEPT,

        /** 上下文绑定后原始事实块。 */
        FACTS_RAW,

        /** 描述块。 */
        DESCRIPTIONS,

        /** 有效操作展开块。 */
        EFFECTIVE_OPERATIONS,

        /** 展示父/子展开块。 */
        PRESENTATION,

        /** TRACE 块（仅受权诊断）。 */
        TRACE
    }
}
