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
  isRemembered?: boolean;
  loginDay?: number;
};
