const Layout = () => import("@/layout/index.vue");

export default {
  path: "/system",
  name: "System",
  component: Layout,
  redirect: "/system/user",
  meta: {
    icon: "ri/settings-3-fill",
    title: "系统管理",
    rank: 1
  },
  children: [
    {
      path: "/system/user",
      name: "UserManagement",
      component: () => import("@/views/system/user/index.vue"),
      meta: {
        title: "用户管理"
      }
    },
    {
      path: "/system/org",
      name: "OrgManagement",
      component: () => import("@/views/system/org/index.vue"),
      meta: {
        title: "组织管理"
      }
    }
  ]
} satisfies RouteConfigsTable;
