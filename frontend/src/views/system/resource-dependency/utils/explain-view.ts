/**
 * 依赖诊断展示纯函数（T-PERM-073）。
 *
 * explain 输出共享逻辑 DAG：节点=完整事实键（不依赖物理授权 ID）、边=直接推导关系。
 * 展示约束：截断/读取失败不得解释为「无来源」；desired≠已生效；孤立 actual 节点
 * desired=false 不伪造支持关系。选中节点沿直接边逐段展开（本地过滤，未截断时无需再请求）。
 */
import type {
  AutoGrantExplainResp,
  ExplainEdge,
  ExplainFactKey,
  ExplainNode,
  ExplainSeedRef,
  ExplainDeclarationRef
} from "@/api/resource-dependency";

/** 条件身份展示（explain 无 PREVIEW_INLINE） */
export function explainConditionLabel(fact: ExplainFactKey): string {
  const ref = fact.conditionRef;
  if (ref.kind === "NONE") return "无条件";
  return ref.conditionCode
    ? `条件 ${ref.conditionCode}`
    : `条件 #${ref.conditionId}`;
}

/** 事实展示：TYPE/code · OP · 条件 */
export function explainFactLabel(fact: ExplainFactKey): string {
  const r = fact.resource;
  return `${r.resourceTypeCode ?? "?"}/${r.resourceCode ?? "?"} · ${
    fact.operationCode ?? "组合位"
  } · ${explainConditionLabel(fact)}`;
}

/** 种子引用展示 */
export function explainSeedLabel(seed: ExplainSeedRef): string {
  if (seed.permissionId != null) return `显式授权 #${seed.permissionId}`;
  return `预览条目 ${seed.requestItemRef ?? "?"}`;
}

/** 节点状态标签集合（desired/actual 的四象限：普通推导、已生效、缺行漂移、孤立存量） */
export function nodeStatus(node: ExplainNode): {
  label: string;
  type: "success" | "warning" | "info" | "danger";
} {
  if (node.explicitSeed) return { label: "显式种子", type: "info" };
  if (node.desired && node.actualPermissionIds.length > 0) {
    return { label: "已生效", type: "success" };
  }
  if (node.desired) return { label: "应有未落库", type: "warning" };
  return { label: "无来源存量", type: "danger" };
}

/** 选中节点时沿直接边逐段展开：返回与该节点直接相连的边（本地过滤）。 */
export function edgesTouchedBy(
  edges: ExplainEdge[],
  nodeKey: string
): ExplainEdge[] {
  return edges.filter(
    edge => edge.fromNodeKey === nodeKey || edge.toNodeKey === nodeKey
  );
}

/** nodeKey -> 节点索引（边渲染 from/to 标签用） */
export function nodeIndex(
  resp: AutoGrantExplainResp
): Map<string, ExplainNode> {
  return new Map(resp.nodes.map(node => [node.nodeKey, node]));
}

/** 声明引用展示 */
export function declarationLabel(refs: ExplainDeclarationRef[]): string {
  if (refs.length === 0) return "—";
  return refs
    .map(ref => `${ref.declarationKey}（${ref.sourceService}）`)
    .join("、");
}
