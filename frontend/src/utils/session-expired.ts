import { message } from "@/utils/message";
import { useUserStoreHook } from "@/store/modules/user";

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

/**
 * 会话终结统一处置三件套（T-FE-056 复评 P3-1 处置，2026-09-20 用户拍板公共层统一）：
 * 提示（去重窗口防多通道双弹）+ logOut 清会话跳登录 + 抛 SessionExpiredError。
 * 原三处手写副本（http 请求短路 / initRouter 会话终结分支 / menu-retry 重试编排
 * 前置与后置判）语义不等价——menu-retry 后置判曾只抛不提示不清会话，「在途跨过
 * 本地到期点+非 401 失败」形态下点击无反馈；统一为本函数单源。logOut 的本地清理
 * 与跳登录在其同步段完成（fire-and-forget，T-FE-054 定案），本函数不 await——
 * throw 时序与原三副本等价；http 短路分支在 Promise executor 内调用，executor
 * 内 throw 被 Promise 构造转为 reject，等价于原 reject 形态。
 */
export function terminateLocalSession(): never {
  notifySessionExpiredOnce();
  useUserStoreHook().logOut();
  throw new SessionExpiredError();
}
