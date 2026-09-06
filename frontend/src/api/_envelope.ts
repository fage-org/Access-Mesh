/**
 * 统一响应信封（对齐 common.model.R）。
 * <p>
 * AccessMesh 后端所有 HTTP 响应统一为 `{ code, message, data, requestId?, traceId? }` 结构，
 * code=200 视为成功；非 200 抛错由调用方 try/catch 处理。
 *
 * 使用：
 * ```ts
 * import { type R, unwrap } from "@/api/_envelope";
 *
 * const res = await http.request<R<UserItem>>(...);
 * return unwrap(res);
 * ```
 *
 * 当前调用方：`api/user-manage.ts`、`api/auth.ts`。
 * 新增 API 文件请优先复用本文件，避免每个 API 文件重复定义信封类型。
 */

/** 后端统一响应包装：code=200 为成功 */
export type R<T> = {
  code: number;
  message: string;
  data: T;
  requestId?: string;
  traceId?: string;
};

/**
 * 错误分类（T-FE-034 Q1：明确拒绝白名单，禁止靠 message 判断）。
 * - business：后端业务码 / 明确拒绝响应（400/401/403/409/422），服务端未提交，可安全重试
 * - timeout：ECONNABORTED / ETIMEDOUT / 408，服务端可能已提交
 * - network：ERR_NETWORK / 离线 / 无 response / 中途取消，服务端可能已提交
 * - unknown：无法识别来源，保守归结果未知
 */
export type ErrorKind = "business" | "timeout" | "network" | "unknown";

/**
 * 类型化错误（带 appCode / httpStatus / kind）。
 * 业务错经 unwrap 抛出（kind=business, appCode=R.code）；
 * http 层错误（axios）由 classifySaveError 按 code/status 分类，不靠 message。
 */
export class RequestError extends Error {
  readonly appCode: number | null;
  readonly httpStatus: number | null;
  readonly kind: ErrorKind;
  constructor(
    message: string,
    opts: {
      appCode?: number | null;
      httpStatus?: number | null;
      kind: ErrorKind;
    }
  ) {
    super(message);
    this.name = "RequestError";
    this.appCode = opts.appCode ?? null;
    this.httpStatus = opts.httpStatus ?? null;
    this.kind = opts.kind;
  }
}

/** 按 code 解包，非 200 抛 RequestError（kind=business, appCode=code），交由调用方 try/catch 处理 */
export function unwrap<T>(res: R<T>): T {
  if (res.code !== 200) {
    throw new RequestError(res.message || "请求失败", {
      appCode: res.code,
      kind: "business"
    });
  }
  return res.data;
}
