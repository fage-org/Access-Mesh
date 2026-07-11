import { ORG_USER_PERM_LIST } from "@/views/system/user/utils/perms";
import { ROLE_MANAGE_PERM_LIST } from "@/views/system/role/utils/perms";
import { TYPE_DEF_PERM_LIST } from "@/views/system/type-def/utils/perms";
import { SYSTEM_CONFIG_PERM_LIST } from "@/views/system/config/utils/perms";
import { OPERATION_LOG_PERM_LIST } from "@/views/system/operation-log/utils/perms";
import { BIZ_DOMAIN_PERM_LIST } from "@/views/system/biz-domain/utils/perms";
import { SERVICE_INTERFACE_PERM_LIST } from "@/views/system/service-interface/utils/perms";

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
    },
    {
      path: "/system/role",
      name: "SystemRole",
      component: () => import("@/views/system/role/index.vue"),
      meta: {
        icon: "ep/user-filled",
        title: "角色管理",
        // 单一事实源派生：见 views/system/role/utils/perms.ts
        auths: [...ROLE_MANAGE_PERM_LIST]
      }
    },
    {
      path: "/system/type-def",
      name: "SystemTypeDef",
      component: () => import("@/views/system/type-def/index.vue"),
      meta: {
        icon: "ep/files",
        title: "类型定义",
        // 单一事实源派生：见 views/system/type-def/utils/perms.ts
        auths: [...TYPE_DEF_PERM_LIST]
      }
    },
    {
      path: "/system/config",
      name: "SystemConfig",
      component: () => import("@/views/system/config/index.vue"),
      meta: {
        icon: "ep/tools",
        title: "系统配置",
        // 单一事实源派生：见 views/system/config/utils/perms.ts
        auths: [...SYSTEM_CONFIG_PERM_LIST]
      }
    },
    {
      path: "/system/operation-log",
      name: "SystemOperationLog",
      component: () => import("@/views/system/operation-log/index.vue"),
      meta: {
        icon: "ep/document",
        title: "操作日志",
        // 单一事实源派生：见 views/system/operation-log/utils/perms.ts
        // 操作日志复用 SYSTEM_CONFIG:VIEW 门禁（后端无独立 OPERATION_LOG 权限码）
        auths: [...OPERATION_LOG_PERM_LIST]
      }
    },
    {
      path: "/system/biz-domain",
      name: "SystemBizDomain",
      component: () => import("@/views/system/biz-domain/index.vue"),
      meta: {
        icon: "ep/office-building",
        title: "业务域",
        // 单一事实源派生：见 views/system/biz-domain/utils/perms.ts
        // biz-domain list/detail 门禁 DOMAIN:VIEW（独立资源类型）；domain-config 子区 + biz-domain 写操作门禁 SYSTEM_CONFIG:VIEW/MANAGE
        auths: [...BIZ_DOMAIN_PERM_LIST]
      }
    },
    {
      path: "/system/service-interface",
      name: "SystemServiceInterface",
      component: () => import("@/views/system/service-interface/index.vue"),
      meta: {
        icon: "ep/connection",
        title: "服务与接口",
        // 单一事实源派生：见 views/system/service-interface/utils/perms.ts
        auths: [...SERVICE_INTERFACE_PERM_LIST]
      }
    }
  ]
} satisfies RouteConfigsTable;
