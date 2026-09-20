import { message } from "@/utils/message";

/** 会话已过期统一提示文案（T-FE-054：本地过期短路 reject 的 Error.message 同源） */
export const SESSION_EXPIRED_MESSAGE = "会话已过期，请重新登录";

/** 提示去重窗口：与 403 能力刷新窗口同量级（10s），并发短路/401 混合触发只弹一次 */
export const SESSION_EXPIRED_NOTIFY_WINDOW_MS = 10_000;

let notifiedUntil = 0;

/**
 * 会话已过期统一提示（T-FE-054，2026-09-20 拍板双分支统一）：本地过期短路、
 * 响应 401、initRouter 无凭证三通道共用，窗口去重防并发请求同刻短路时提示轰炸。
 * 独立小模块的原因：去重状态放 http 会新增 router/utils → http 反向边成环（既有边
 * http → router/utils）；放 router/utils 属职责错位（提示去重不属路由工具）——故落第三处。
 */
export function notifySessionExpiredOnce() {
  const now = Date.now();
  if (now < notifiedUntil) return;
  notifiedUntil = now + SESSION_EXPIRED_NOTIFY_WINDOW_MS;
  message(SESSION_EXPIRED_MESSAGE, { type: "warning" });
}

/**
 * 会话已终结信号（Q-020 收口，2026-09-20 拍板）：initRouter 无凭证分支抛出——
 * 统一层已提示并走 logOut 跳登录，调用方（menu-retry retry 等非导航入口）catch
 * 后跳过按陈旧菜单状态的业务提示，不再误报「仍无可用菜单，请联系管理员」。
 */
export class SessionExpiredError extends Error {
  constructor() {
    super(SESSION_EXPIRED_MESSAGE);
    this.name = "SessionExpiredError";
  }
}
