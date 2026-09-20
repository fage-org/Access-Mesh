import { initRouter } from "@/router/utils";
import { useUserStoreHook } from "@/store/modules/user";

export type MenuReloadOutcome = "recovered" | "still-failed" | "still-empty";

/**
 * menu-retry 页「重新加载/检查菜单」重载编排（T-FE-015 预清重取 + T-FE-056 失败回滚）。
 * <p>
 * 预清 store 内存态使 initRouter 走重取分支（T-FE-015 设计定案：失败 fail-closed
 * 空菜单 + 可重试，不持久化、不回退全量静态菜单）。门禁落地后（T-FE-056 双轨评审
 * P3-1，2026-09-20 用户拍板直接修）失败分支回滚预清快照：刷新失败时状态机维持
 * loaded 用旧 path 集判定（状态迁移表「menus 保留旧值」），不回滚则门禁集合塌缩为
 * 仅公共白名单、业务路由全拦 403 形成锁死——违背「门禁失效的最坏结果=回到现状，
 * 不产生新锁死」承诺。拉取成功但空（零权限/权限全撤的新事实）**不**回滚——成功
 * 路径的事实覆盖旧快照。
 * <p>
 * 会话终结（Q-020）：initRouter 抛 SessionExpiredError 原样上抛，由页面 catch 留痕。
 */
export async function reloadMenusWithRollback(): Promise<MenuReloadOutcome> {
  const userStore = useUserStoreHook();
  const prevMenus = userStore.menus;
  userStore.SET_MENUS([]);
  await initRouter();
  const store = useUserStoreHook();
  if (store.menus.length > 0) return "recovered";
  if (store.menuLoadFailed) {
    if (prevMenus.length > 0) store.SET_MENUS(prevMenus);
    return "still-failed";
  }
  return "still-empty";
}
