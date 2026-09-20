/**
 * 路由级 UX 门禁（T-FE-056，2026-09-19 拍板③、2026-09-20 用户拍板落点全屏
 * /access-denied）。
 * <p>
 * 门禁集合 = 后端 menus 树 path 集（含 children 递归，与侧栏可见性同源不分叉）
 * ∪ 公共路由白名单（remaining.ts 全部节点自动纳入 + 公共错误页显式登记）
 * ∪ 显式动作路由映射（showLink:false 不进菜单的业务路由按权限串判定放行）。
 * 门禁是 UX 层不是安全层：越权仍由后端 403 兜底；守卫状态机 failed（首次加载
 * 失败）fail-open 放行（判定在 router/index.ts 守卫，本模块只回答「path 是否
 * 在集合内」）。menus path 与前端路由 path 同形（BootstrapGraphDefinition 菜单
 * 种子 /welcome、/system/user 等与 modules/*.ts 路由 path 一致，无前缀差异）。
 */
import { hasPerms } from "@/utils/auth";
import type { UserMenuRoute } from "@/api/auth";
import { useUserStoreHook } from "@/store/modules/user";
import { PERMISSION_GRANT_PERMS } from "@/views/perm/grant/utils/perms";
import remainingRouter from "./modules/remaining";

/**
 * 显式动作路由映射（非菜单业务路由，T-PERM-037 口径演进）：path → 进入所需权限串
 * （hasPerms 单源判定）。/perm/grant 经 perm.ts showLink:false 不进菜单——纯 menus
 * 白名单会封死授权页（codex sol 外评 P1）；门禁=矩阵查看 ROLE:VIEW，与三处入口
 * 按钮门禁同源（T-FE-055 grant-entry.ts）——权限串经 PERMISSION_GRANT_PERMS
 * SSOT 引用（外评 P3：直写字面量与入口按钮判定形成两份副本，改名时静默分叉）。
 * 新增动作路由在此登记。
 */
const ACTION_ROUTE_PERMS: Record<string, string> = {
  "/perm/grant": PERMISSION_GRANT_PERMS.ROLE_VIEW
};

/**
 * 公共错误页（error.ts 模块路由，不在 remaining.ts）按「新增公共路由显式登记」
 * 口径纳入白名单：守卫 VITE_HIDE_HOME 分支会重定向 /error/404、页面内异常跳转
 * /error/500——不登记则这些目标被门禁拦截（403 落点再拦即自环）。与 T-FE-046
 * forceResetAllowPaths 的公共错误页清单同款口径。
 */
const EXPLICIT_PUBLIC_PATHS = ["/error/403", "/error/404", "/error/500"];

/** 含参数段的 path 转前缀匹配的字面部分："/redirect/:path(.*)" → "/redirect/" */
function paramPathPrefix(path: string): string | null {
  const idx = path.indexOf(":");
  return idx === -1 ? null : path.slice(0, idx);
}

/**
 * 公共路由白名单：remaining.ts 全部节点 path（递归含 children——/redirect/:path(.*)
 * 参数形态转前缀，漏配会拦死标签刷新链路 lay-tag onFresh 的
 * router.replace("/redirect"+fullPath)）∪ 显式登记的公共错误页。
 */
const PUBLIC_ROUTE_MATCHERS = (() => {
  const exact = new Set<string>(EXPLICIT_PUBLIC_PATHS);
  const prefixes: string[] = [];
  const walk = (nodes: Array<{ path?: string; children?: unknown[] }>) => {
    for (const node of nodes ?? []) {
      if (typeof node.path === "string" && node.path) {
        const prefix = paramPathPrefix(node.path);
        if (prefix === null) exact.add(node.path);
        else if (prefix) prefixes.push(prefix);
      }
      if (node.children?.length) walk(node.children as typeof nodes);
    }
  };
  walk(remainingRouter as typeof remainingRouter & { children?: unknown[] }[]);
  return { exact, prefixes };
})();

/** path 是否公共路由（白名单内不判门禁、守卫不等待初始化） */
export function isPublicRoute(path: string): boolean {
  return (
    PUBLIC_ROUTE_MATCHERS.exact.has(path) ||
    PUBLIC_ROUTE_MATCHERS.prefixes.some(p => path.startsWith(p))
  );
}

/** 后端 menus 树 → path 集（含 children 递归）。每次导航现算：menus 更新即集合
 * 更新（能力刷新入口 refreshSessionCapability 回写 menus 后门禁随会话权限即时
 * 收敛，无快照失联形态——T-FE-055 capability 快照改响应式派生同款定案） */
export function collectMenuPaths(menus: Array<UserMenuRoute>): Set<string> {
  const paths = new Set<string>();
  const walk = (nodes: Array<UserMenuRoute>) => {
    for (const node of nodes ?? []) {
      if (typeof node.path === "string" && node.path) paths.add(node.path);
      if (node.children?.length) walk(node.children);
    }
  };
  walk(menus ?? []);
  return paths;
}

/**
 * path 是否在门禁集合内（公共白名单 ∨ menus path 集 ∨ 显式动作路由映射）。
 * 供守卫在状态机 loaded 态调用；failed/未初始化形态的放行策略由守卫决定。
 */
export function isRouteAllowed(path: string): boolean {
  if (isPublicRoute(path)) return true;
  if (collectMenuPaths(useUserStoreHook().menus).has(path)) return true;
  const required = ACTION_ROUTE_PERMS[path];
  return required != null ? hasPerms(required) : false;
}
