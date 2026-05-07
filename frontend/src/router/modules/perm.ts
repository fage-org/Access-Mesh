import { PERM_CODES } from "@/constants/permission";

const Layout = () => import("@/layout/index.vue");

export default {
  path: "/perm",
  name: "Perm",
  component: Layout,
  redirect: "/perm/role",
  meta: {
    icon: "ri/shield-keyhole-fill",
    title: "权限中心",
    rank: 2
  },
  children: [
    {
      path: "/perm/role",
      name: "PermRole",
      component: () => import("@/views/perm/role/index.vue"),
      meta: {
        title: "角色管理",
        auths: [PERM_CODES.SYS_ROLE_VIEW]
      }
    },
    {
      path: "/perm/role/permission",
      name: "PermRolePermission",
      component: () => import("@/views/perm/role/permission.vue"),
      meta: {
        title: "角色权限配置",
        auths: [PERM_CODES.SYS_ROLE_ASSIGN_PERM],
        showLink: false
      }
    },
    {
      path: "/perm/user-role",
      name: "PermUserRole",
      component: () => import("@/views/perm/user-role/index.vue"),
      meta: {
        title: "用户角色分配",
        auths: [PERM_CODES.SYS_USER_ASSIGN_ROLE]
      }
    },
    {
      path: "/perm/resource",
      name: "PermResource",
      component: () => import("@/views/perm/resource/index.vue"),
      meta: {
        title: "资源管理",
        auths: [PERM_CODES.PERM_RESOURCE_VIEW]
      }
    },
    {
      path: "/perm/service",
      name: "PermService",
      component: () => import("@/views/perm/service/index.vue"),
      meta: {
        title: "服务配置",
        auths: [PERM_CODES.PERM_SERVICE_VIEW]
      }
    }
  ]
} satisfies RouteConfigsTable;
