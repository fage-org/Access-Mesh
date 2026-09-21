/**
 * 授撤影响预览纯展示函数（T-PERM-073，契约 §11.4.1 / 设计 §12 M5）。
 *
 * 预览仅供参考（advisory）：removed/added/retained 是计划导致的自动权限变化，
 * retained 指出仍被其他显式来源支持的事实；失败必须显式标识为「无法预览」，
 * 不得展示为零影响；截断与现有漂移各自独立提示。
 */
import type {
  GrantPlanPreviewResp,
  PreviewFactElement,
  PreviewConditionRef,
  PreviewSeedRef
} from "@/api/permission-grant";

/** 条件身份展示：NONE=无条件；EXISTING=条件 code；PREVIEW_INLINE=新内联（条目位置） */
export function conditionLabel(ref: PreviewConditionRef): string {
  if (ref.kind === "NONE") return "无条件";
  if (ref.kind === "PREVIEW_INLINE") {
    return `新内联条件（${ref.requestItemRef ?? "?"}）`;
  }
  return ref.conditionCode
    ? `条件 ${ref.conditionCode}`
    : `条件 #${ref.conditionId}`;
}

/** 事实展示：TYPE/code · OP · 条件 */
export function previewFactLabel(element: PreviewFactElement): string {
  const r = element.fact.resource;
  const resource = `${r.resourceTypeCode ?? "?"}/${r.resourceCode ?? "?"}`;
  const op = element.fact.operationCode ?? "组合位";
  return `${resource} · ${op} · ${conditionLabel(element.fact.conditionRef)}`;
}

/** 根来源展示：现有显式授权 #id / 预览新建条目 creates[i] */
export function seedLabel(seed: PreviewSeedRef): string {
  if (seed.permissionId != null) return `显式授权 #${seed.permissionId}`;
  return `新建条目 ${seed.requestItemRef ?? "?"}`;
}

/** 全部影响为空（真零影响——服务端 totalCount=0 才可信；截断时三组空 ≠ 无影响） */
export function hasNoImpact(resp: GrantPlanPreviewResp | null): boolean {
  if (!resp) return false;
  return (
    resp.totalCount === 0 &&
    resp.removed.length === 0 &&
    resp.added.length === 0 &&
    resp.retained.length === 0
  );
}

/** 预览失败文案：不吞错误、不降级为零影响 */
export function previewFailureText(error: unknown): string {
  const message =
    error && typeof error === "object" && "message" in error
      ? String((error as { message?: unknown }).message ?? "")
      : "";
  return message ? `无法预览：${message}` : "无法预览，请稍后重试";
}

/** M5 边界说明（UI 当场说明，不把自动授权存续展示为运行时必然放行） */
export const PREVIEW_BOUNDARY_NOTES: string[] = [
  "预览仅供参考：并发授权或依赖声明变更会使实际结果不同，保存按服务端最新事实重新校验与重算。",
  "自动权限是独立目标权限：不表达「仅访问源资源时临时可用」，运行时互斥结果不构成用途限制。",
  "类型级（全部资源）授权与父资源继承得到的权限不触发自动授权，仅实例级直接授权参与推导。",
  "源/目标/中间资源停用不暂停自动授权传播；恢复无需重建。撤销显式授权或删除资源仍按规则重算。"
];
