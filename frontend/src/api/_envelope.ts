/**
 * 统一响应信封（对齐 common.model.PermResult）。
 * <p>
 * AccessMesh 后端所有 HTTP 响应统一为 `{ code, message, data, requestId?, traceId? }` 结构，
 * code=200 视为成功；非 200 抛错由调用方 try/catch 处理。
 *
 * 使用：
 * ```ts
 * import { type PermResult, unwrap } from "@/api/_envelope";
 *
 * const res = await http.request<PermResult<UserItem>>(...);
 * return unwrap(res);
 * ```
 *
 * 当前调用方：`api/user-manage.ts`、`api/auth.ts`。
 * 新增 API 文件请优先复用本文件，避免每个 API 文件重复定义信封类型。
 */

/** 后端统一响应包装：code=200 为成功 */
export type PermResult<T> = {
  code: number;
  message: string;
  data: T;
  requestId?: string;
  traceId?: string;
};

/** 按 code 解包，非 200 抛错，交由调用方 try/catch 处理 */
export function unwrap<T>(res: PermResult<T>): T {
  if (res.code !== 200) {
    throw new Error(res.message || "请求失败");
  }
  return res.data;
}
