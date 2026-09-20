import type { RouteRecordName } from "vue-router";

export type cacheType = {
  mode: string;
  name?: RouteRecordName;
};

export type positionType = {
  startIndex?: number;
  length?: number;
};

export type appType = {
  sidebar: {
    opened: boolean;
    withoutAnimation: boolean;
    // 判断是否手动点击Collapse
    isClickCollapse: boolean;
  };
  layout: string;
  device: string;
  viewportSize: { width: number; height: number };
};

export type multiType = {
  path: string;
  name: string;
  meta: any;
  query?: object;
  params?: object;
};

export type setType = {
  title: string;
  fixedHeader: boolean;
  hiddenSideBar: boolean;
};

export type userType = {
  avatar?: string;
  username?: string;
  nickname?: string;
  roles?: Array<string>;
  permissions?: Array<string>;
  /** 后端下发的菜单树（v1.4 双轨并行：菜单可见性轨道） */
  menus?: Array<any>;
  /**
   * 最近一次 /user-menu 拉取是否失败（T-FE-049）：
   * 区分「加载失败」与「拉取成功但账号无菜单」（menus.length===0 两态），
   * 供 initRouter 占位项与登录页诚实提示分支消费；不持久化（F5 重取后自然更新）
   */
  menuLoadFailed?: boolean;
  /**
   * 会话门禁状态机（T-FE-056，2026-09-19 拍板③）：路由级 UX 门禁的守卫判定依据。
   * uninitialized=会话内从未拉取；loading=首次拉取在途；loaded=至少成功一次（零权限
   * 账号亦为 loaded——menus 空但门禁集合有效=仅公共白名单+显式动作路由映射）；
   * failed=首次拉取失败（守卫 fail-open 放行，越权由后端 403 兜底）。已 loaded 会话的
   * 后续刷新失败维持 loaded（门禁不因刷新抖动静默失效，守卫继续用旧 path 集判定）。
   * 不持久化（F5 重取后自然推进）；logOut 重置 uninitialized
   */
  menuGateStatus?: "uninitialized" | "loading" | "loaded" | "failed";
  isRemembered?: boolean;
  loginDay?: number;
};
