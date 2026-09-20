import { refreshSessionCapability } from "@/router/utils";
import { useUserStoreHook } from "@/store/modules/user";
import { isSessionTerminated } from "@/utils/auth";
import {
  notifySessionExpiredOnce,
  SessionExpiredError
} from "@/utils/session-expired";

export type MenuReloadOutcome = "recovered" | "still-failed" | "still-empty";

/**
 * menu-retry 页「重新加载/检查菜单」重载编排（T-FE-056 外评处置，2026-09-20
 * 用户拍板：不预清、改用会话能力刷新入口强制重取）。
 * <p>
 * 原 T-FE-015 形态（预清 SET_MENUS([]) 使 initRouter 走重取分支，失败时快照回滚）
 * 在门禁落地后暴露三形态（claude P3-1/codex P3 同根因）：①预清后请求在途期间
 * menus 空+状态 loaded，门禁集合塌缩为仅公共白名单，在途导航被误拦；②失败回滚
 * 只恢复 menus 不恢复侧栏——门禁旧集与「加载失败」占位侧栏分裂；③401 时响应
 * 拦截器已 logOut 清空 store，回滚分支仍把旧菜单写回已登出的会话并弹失真提示。
 * 改用 refreshSessionCapability（入口本身无条件发起 user-menu 强制重取；失败时
 * menus/wholeMenus/门禁状态机全部保留旧态——T-FE-048 统一能力刷新语义），三形态
 * 一步消除，预清/快照回滚逻辑整体退役。
 * <p>
 * 会话终结双判（对齐 initRouter 会话终结分支语义，Q-020）：前置——点击重试时
 * 本地凭证已无/已过期，统一提示+logOut+抛 SessionExpiredError；后置——拉取 401
 * 形态（响应拦截器已提示并 logOut）不按陈旧菜单状态弹失真业务提示。
 */
export async function reloadSessionMenus(): Promise<MenuReloadOutcome> {
  if (isSessionTerminated()) {
    notifySessionExpiredOnce();
    await useUserStoreHook().logOut();
    throw new SessionExpiredError();
  }
  try {
    await refreshSessionCapability();
  } catch {
    if (isSessionTerminated()) {
      // 401 型终结：拦截器已提示「会话已过期」并 logOut——不弹「菜单加载仍失败」失真提示
      throw new SessionExpiredError();
    }
    return "still-failed";
  }
  return useUserStoreHook().menus.length > 0 ? "recovered" : "still-empty";
}
