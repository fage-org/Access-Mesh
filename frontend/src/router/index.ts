// import "@/utils/sso";
import Cookies from "js-cookie";
import { getConfig } from "@/config";
import NProgress from "@/utils/progress";
import { buildHierarchyTree } from "@/utils/tree";
import remainingRouter from "./modules/remaining";
import { useMultiTagsStoreHook } from "@/store/modules/multiTags";
import { usePermissionStoreHook } from "@/store/modules/permission";
import {
  isUrl,
  openLink,
  cloneDeep,
  isAllEmpty,
  storageLocal
} from "@pureadmin/utils";
import {
  ascending,
  getTopMenu,
  initRouter,
  getHistoryMode,
  findRouteByPath,
  handleAliveRoute,
  formatTwoStageRoutes,
  formatFlatteningRoutes
} from "./utils";
import {
  type Router,
  type RouteRecordRaw,
  type RouteComponent,
  createRouter
} from "vue-router";
import { isPublicRoute, isRouteAllowed } from "./gate";
import { useUserStoreHook } from "@/store/modules/user";
import {
  type DataInfo,
  userKey,
  removeToken,
  multipleTabsKey
} from "@/utils/auth";

/** 自动导入全部静态路由，无需再手动引入！匹配 src/router/modules 目录（任何嵌套级别）中具有 .ts 扩展名的所有文件，除了 remaining.ts 文件
 * 如何匹配所有文件请看：https://github.com/mrmlnc/fast-glob#basic-syntax
 * 如何排除文件请看：https://cn.vitejs.dev/guide/features.html#negative-patterns
 */
const modules: Record<string, any> = import.meta.glob(
  ["./modules/**/*.ts", "!./modules/**/remaining.ts"],
  {
    eager: true
  }
);

/** 原始静态路由（未做任何处理） */
const routes = [];

Object.keys(modules).forEach(key => {
  routes.push(modules[key].default);
});

/** 导出处理后的静态路由（三级及以上的路由全部拍成二级） */
export const constantRoutes: Array<RouteRecordRaw> = formatTwoStageRoutes(
  formatFlatteningRoutes(buildHierarchyTree(ascending(routes.flat(Infinity))))
);

/** 初始的静态路由，用于退出登录时重置路由 */
const initConstantRoutes: Array<RouteRecordRaw> = cloneDeep(constantRoutes);

/** 用于渲染菜单，保持原始层级 */
export const constantMenus: Array<RouteComponent> = ascending(
  routes.flat(Infinity)
).concat(...remainingRouter);

/** 不参与菜单的路由 */
export const remainingPaths = Object.keys(remainingRouter).map(v => {
  return remainingRouter[v].path;
});

/** 创建路由实例 */
export const router: Router = createRouter({
  history: getHistoryMode(import.meta.env.VITE_ROUTER_HISTORY),
  routes: constantRoutes.concat(...(remainingRouter as any)),
  strict: true,
  scrollBehavior(to, from, savedPosition) {
    return new Promise(resolve => {
      if (savedPosition) {
        return savedPosition;
      } else {
        if (from.meta.saveSrollTop) {
          const top: number =
            document.documentElement.scrollTop || document.body.scrollTop;
          resolve({ left: 0, top });
        }
      }
    });
  }
});

/** 记录已经加载的页面路径 */
const loadedPaths = new Set<string>();

/** 重置已加载页面记录 */
export function resetLoadedPaths() {
  loadedPaths.clear();
}

/** 重置路由 */
export function resetRouter() {
  router.clearRoutes();
  for (const route of initConstantRoutes.concat(...(remainingRouter as any))) {
    router.addRoute(route);
  }
  router.options.routes = formatTwoStageRoutes(
    formatFlatteningRoutes(buildHierarchyTree(ascending(routes.flat(Infinity))))
  );
  usePermissionStoreHook().clearAllCachePage();
  resetLoadedPaths();
}

/** 路由白名单 */
const whiteList = ["/login"];

/**
 * 强制改密阻断放行清单（T-FE-046）：forceResetPwd=true 时除下列路径外全部
 * redirect /change-password。放行=改密页 + 登录页 + 公共错误页（remaining.ts
 * 全屏错误页 + error.ts 模块三页 + /menu-retry 两态着陆页）。注意不含
 * /redirect：真实标签刷新导航是 "/redirect"+fullPath 参数化路径（精确匹配
 * 恒不中），裸 /redirect 命中 Layout 父记录会把侧栏壳放给阻断人群（T-FE-046
 * 双轨评审代码轨 P2——阻断态拦下 /redirect/** 与拦其目标语义自洽）。阻断是
 * 导航层 UX 门禁而非安全边界——后端仍是最终授权边界（改密页自身经会话认证
 * 可达，业务接口由后端 403 兜底，T-PERM-037 口径）。
 */
const forceResetAllowPaths = [
  "/change-password",
  "/login",
  "/access-denied",
  "/server-error",
  "/menu-retry",
  "/error/403",
  "/error/404",
  "/error/500"
];

const { VITE_HIDE_HOME } = import.meta.env;

/**
 * 路由级权限口径（T-FE-053 定基、T-FE-056 挂门禁）：本仓路由权限=后端 menus 派生
 * ——侧栏可见性（T-FE-015）与路由级 UX 门禁（T-FE-056：menus 树 path 集 ∪ 公共
 * 路由白名单 ∪ 显式动作路由映射，判定见 router/gate.ts）同源不分叉；不使用
 * pure-admin 模板的 meta.roles 前端白名单（全仓零声明，死分支已删）。门禁是 UX
 * 层不是安全层：状态机 failed（首次加载失败）fail-open 放行，越权仍由后端 403 兜底。
 * 守卫纪律：调用 next() 的分支随即离开守卫（return 或块末），单次导航 next 至多
 * 调用一次；例外：externalLink 分支不调 next（模板原状，openLink 新开标签承载交互）。
 * 强制改密阻断（T-FE-046）：标记存 localStorage userKey（登录写入、改密成功
 * 置 false、登出清除），守卫每次导航重读——跨标签经共享存储自然生效，无需广播。
 */
router.beforeEach((to: ToRouteType, _from, next) => {
  to.meta.loaded = loadedPaths.has(to.path);

  if (!to.meta.loaded) {
    NProgress.start();
  }

  if (to.meta?.keepAlive) {
    handleAliveRoute(to, "add");
    // 页面整体刷新和点击标签页刷新
    if (_from.name === undefined || _from.name === "Redirect") {
      handleAliveRoute(to);
    }
  }
  const userInfo = storageLocal().getItem<DataInfo<number>>(userKey);
  const externalLink = isUrl(to?.name as string);
  if (!externalLink) {
    to.matched.some(item => {
      if (!item.meta.title) return "";
      const Title = getConfig().Title;
      if (Title) document.title = `${item.meta.title} | ${Title}`;
      else document.title = item.meta.title as string;
    });
  }
  /** 如果已经登录并存在登录信息后不能跳转到路由白名单，而是继续保持在当前页面 */
  function toCorrectRoute() {
    whiteList.includes(to.fullPath) ? next(_from.fullPath) : next();
  }
  /**
   * 冷启动初始化完成后的补标签与兜底重导航（旧刷新分支 then 段原样迁移；路由已
   * 全静态化，isAllEmpty(to.name) 仅在冷启动 pathMatch 注册前手输未知路径可达）。
   * 仅放行分支调用——被拦导航不落地当前 to，补标签/重导航无意义
   */
  function handleColdStartRoute(inited: Router) {
    if (!useMultiTagsStoreHook().getMultiTagsCache) {
      const { path } = to;
      const route = findRouteByPath(path, inited.options.routes[0].children);
      getTopMenu(true);
      // query、params模式路由传参数的标签页不在此处处理
      if (route && route.meta?.title) {
        if (isAllEmpty(route.parentId) && route.meta?.backstage) {
          // 此处为动态顶级路由（目录）
          const { path, name, meta } = route.children[0];
          useMultiTagsStoreHook().handleTags("push", {
            path,
            name,
            meta
          });
        } else {
          const { path, name, meta } = route;
          useMultiTagsStoreHook().handleTags("push", {
            path,
            name,
            meta
          });
        }
      }
    }
    // 确保动态路由完全加入路由列表并且不影响静态路由（动态路由刷新时router.beforeEach可能会触发两次，第一次触发动态路由还未完全添加，第二次动态路由才完全添加到路由列表，如果需要在router.beforeEach做一些判断可以在to.name存在的条件下去判断，这样就只会触发一次）
    if (isAllEmpty(to.name)) router.push(to.fullPath);
  }
  /**
   * initRouter 抛会话终结（T-FE-054/Q-020）时仅留痕防 unhandled rejection——
   * 统一层已提示并 logOut 跳登录，导航中止
   */
  function warnSessionTerminated(err: unknown) {
    console.warn("[router] initRouter 会话已终结，导航中止", err);
  }
  /**
   * 门禁判定分派（T-FE-056，2026-09-20 拍板拦截落点=全屏 /access-denied）：
   * 公共路由不判门禁不等待（冷启动仍后台建侧栏，阻断人群除外——改密成功进系统
   * 的导航自然触发）；业务路由 loaded/failed 即时判定；uninitialized/loading
   * 等待初始化完成后再判定（防冷启动深链绕过——旧实现 initRouter 完成后仅
   * to.name 为空才重导航，静态路由有名即漏判；initRouter 内 single-flight，
   * 并发导航共享同一次拉取）。failed fail-open 放行（门禁是 UX 层不是安全层，
   * 越权仍由后端 403 兜底）
   */
  function gateOrAllow() {
    const userStore = useUserStoreHook();
    if (isPublicRoute(to.path)) {
      if (
        userStore.menuGateStatus === "uninitialized" &&
        !userInfo.forceResetPwd
      ) {
        initRouter().then(handleColdStartRoute).catch(warnSessionTerminated);
      }
      toCorrectRoute();
      return;
    }
    const decide = (inited?: Router) => {
      // 仅 loaded 真判定；failed（首载失败）与等待后仍非 loaded 的不可达形态一律
      // fail-open 放行——门禁失效的最坏结果=回到现状（路由全可达+后端 403 兜底），
      // 不产生新锁死
      if (userStore.menuGateStatus !== "loaded" || isRouteAllowed(to.path)) {
        toCorrectRoute();
        // 冷启动放行后补标签/兜底重导航（inited=initRouter resolve 的路由实例，
        // 与模板原刷新分支同源；SPA 内导航不补）
        if (inited) handleColdStartRoute(inited);
      } else {
        next({ path: "/access-denied" });
      }
    };
    if (
      userStore.menuGateStatus === "uninitialized" ||
      userStore.menuGateStatus === "loading"
    ) {
      initRouter().then(decide).catch(warnSessionTerminated);
      return;
    }
    decide();
  }
  if (Cookies.get(multipleTabsKey) && userInfo) {
    // 强制改密阻断（T-FE-046）：forceResetPwd=true 只放行改密页/登录页/公共
    // 错误页，其余路由 redirect /change-password；改密成功清标记后放行
    if (userInfo.forceResetPwd && !forceResetAllowPaths.includes(to.path)) {
      next({ path: "/change-password" });
      return;
    }
    // 开启隐藏首页后在浏览器地址栏手动输入首页welcome路由则跳转到404页面
    if (VITE_HIDE_HOME === "true" && to.fullPath === "/welcome") {
      next({ path: "/error/404" });
      return;
    }
    if (_from?.name) {
      // name为超链接
      if (externalLink) {
        openLink(to?.name as string);
        NProgress.done();
      } else {
        gateOrAllow();
      }
    } else {
      // 刷新/冷启动：由 gateOrAllow 按状态机分派（uninitialized/loading 等待初始化
      // 完成后判定，不再先放行后补导航）
      gateOrAllow();
    }
  } else {
    if (to.path !== "/login") {
      if (whiteList.indexOf(to.path) !== -1) {
        next();
        return;
      } else {
        removeToken();
        next({ path: "/login" });
        return;
      }
    } else {
      next();
      return;
    }
  }
});

router.afterEach(to => {
  loadedPaths.add(to.path);
  NProgress.done();
});

export default router;
