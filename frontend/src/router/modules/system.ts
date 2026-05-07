import { PERM_CODES } from "@/constants/permission";

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
        title: "用户管理",
        auths: [PERM_CODES.SYS_USER_VIEW]
      }
    },
    {
      path: "/system/org",
      name: "OrgManagement",
      component: () => import("@/views/system/org/index.vue"),
      meta: {
        title: "组织管理",
        auths: [PERM_CODES.SYS_ORG_VIEW]
      }
    },
    {
      path: "/system/menu",
      name: "MenuManagement",
      component: () => import("@/views/system/menu/index.vue"),
      meta: {
        title: "菜单管理",
        auths: [PERM_CODES.SYS_MENU_VIEW]
      }
    },
    {
      path: "/system/dict",
      name: "DictManagement",
      component: () => import("@/views/system/dict/index.vue"),
      meta: {
        title: "字典管理",
        auths: [PERM_CODES.SYS_DICT_VIEW]
      }
    },
    {
      path: "/system/notice",
      name: "NoticeManagement",
      component: () => import("@/views/system/notice/index.vue"),
      meta: {
        title: "系统通知",
        auths: [PERM_CODES.SYS_NOTICE_VIEW]
      }
    },
    {
      path: "/system/file",
      name: "FileManagement",
      component: () => import("@/views/system/file/index.vue"),
      meta: {
        title: "文件管理",
        auths: [PERM_CODES.SYS_FILE_VIEW]
      }
    },
    {
      path: "/system/job",
      name: "JobManagement",
      component: () => import("@/views/system/job/index.vue"),
      meta: {
        title: "定时任务",
        auths: [PERM_CODES.SYS_JOB_VIEW]
      }
    },
    {
      path: "/system/config",
      name: "ConfigManagement",
      component: () => import("@/views/system/config/index.vue"),
      meta: {
        title: "系统配置",
        auths: [PERM_CODES.SYS_CONFIG_VIEW]
      }
    }
  ]
} satisfies RouteConfigsTable;
