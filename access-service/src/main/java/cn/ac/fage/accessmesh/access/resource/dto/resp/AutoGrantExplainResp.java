package cn.ac.fage.accessmesh.access.resource.dto.resp;

import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色自动授权来源解释响应（T-PERM-073，契约 §12.3.1）。
 *
 * <p>单次只读一致视图生成的共享逻辑 DAG：节点以完整逻辑事实键标识（不依赖物理 AUTO_DEP
 * 主键），边为直接推导关系（含实际触发操作与声明引用）。desired 与 actual 漂移显式标识，
 * 不把「应生成」当「已生效」；源事实权限不表示用户当前必然 allowed。</p>
 *
 * @param viewedAt        一致视图读取时间（不构成跨请求冻结承诺）
 * @param nodes           输出节点（nodeKey 按响应内 FactKey 元组排序分配，如 n1；仅本响应内引用）
 * @param edges           直接推导边（两端都已输出的边，按起点/终点逻辑键排序取前 maxEdges 条）
 * @param totalNodeCount  选定子图完整节点数（无 target=推导图与孤立 actual 节点并集），非已输出数
 * @param totalEdgeCount  选定子图完整边数，非已输出数
 * @param truncated       达到 maxDepth/maxNodes/maxEdges 任一限额时 true（不影响完整推导）
 * @param driftDetected   全角色 desired 与 actual AUTO_DEP 存在漂移
 */
public record AutoGrantExplainResp(
    LocalDateTime viewedAt,
    List<Node> nodes,
    List<Edge> edges,
    long totalNodeCount,
    long totalEdgeCount,
    boolean truncated,
    boolean driftDetected
) {

    /**
     * 逻辑事实节点。
     *
     * @param nodeKey            展示 ID（n1 起，按本响应 FactKey 元组排序分配）
     * @param fact               完整逻辑事实键（explain 不出现 PREVIEW_INLINE）
     * @param explicitSeed       显式种子节点（MANUAL 实例主授权）
     * @param seedRefs           显式种子引用（仅 explicitSeed 节点非空）
     * @param desired            完整重算应生成该自动事实
     * @param actualPermissionIds 对应有效 AUTO_DEP 行 ID（空数组=无实际行，不能以 null 表示读取失败）
     */
    public record Node(String nodeKey, GrantPlanPreviewResp.FactKey fact, boolean explicitSeed,
                       List<GrantPlanPreviewResp.SeedRef> seedRefs, boolean desired,
                       List<Long> actualPermissionIds) {}

    /** 直接推导边：实际触发操作 + 声明引用（排序去重）。 */
    public record Edge(String fromNodeKey, String toNodeKey, String triggerOperationCode,
                       List<DeclarationRef> declarationRefs) {}

    /** 编译声明引用（聚合边可由多条声明并集贡献）。 */
    public record DeclarationRef(Long declarationId, String declarationKey, String sourceService) {}
}
