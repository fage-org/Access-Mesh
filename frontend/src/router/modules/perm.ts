import { PERMISSION_GRANT_PERM_LIST } from "@/views/perm/grant/utils/perms";

const Layout = () => import("@/layout/index.vue");

/**
 * 权限域路由（4.1 权限授予 v3，T-FE-036）。
 * 入口：角色管理页（2.2）"权限授予"按钮（组织入口二期挂 2.1）；菜单不展示（showLink: false）。
 * 路由约定：/perm/grant?subjectType=ROLE|ORG（同一页面组件，subjectType 驱动主体数据源与标题；
 * PERSONAL 预留，首期不挂路由）。
 */
export default {
  path: "/perm",
  name: "Perm",
  component: Layout,
  redirect: "/perm/grant",
  meta: {
    icon: "ep/key",
    title: "权限管理",
    showLink: false,
    rank: 11
  },
  children: [
    {
      path: "/perm/grant",
      name: "PermGrant",
      component: () => import("@/views/perm/grant/index.vue"),
      meta: {
        icon: "ep/key",
        title: "权限授予",
        showLink: false,
        // 保持路由可激活（keepAlive 切换主体不丢草稿）
        keepAlive: true,
        // 单一事实源派生：见 views/perm/grant/utils/perms.ts
        auths: [...PERMISSION_GRANT_PERM_LIST]
      }
    }
  ]
} satisfies RouteConfigsTable;
