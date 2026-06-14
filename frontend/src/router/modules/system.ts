import { ORG_USER_PERM_LIST } from "@/views/system/user/utils/perms";

const Layout = () => import("@/layout/index.vue");

export default {
  path: "/system",
  name: "System",
  component: Layout,
  redirect: "/system/user",
  meta: {
    icon: "ep/setting",
    title: "系统管理",
    rank: 10
  },
  children: [
    {
      path: "/system/user",
      name: "SystemUser",
      component: () => import("@/views/system/user/index.vue"),
      meta: {
        icon: "ep/user",
        title: "组织与用户",
        // 单一事实源派生：见 views/system/user/utils/perms.ts
        auths: [...ORG_USER_PERM_LIST]
      }
    }
  ]
} satisfies RouteConfigsTable;
