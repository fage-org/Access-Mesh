import { ORG_USER_PERM_LIST } from "@/views/system/user/utils/perms";
import { ROLE_MANAGE_PERM_LIST } from "@/views/system/role/utils/perms";
import { TYPE_DEF_PERM_LIST } from "@/views/system/type-def/utils/perms";
import { SYSTEM_CONFIG_PERM_LIST } from "@/views/system/config/utils/perms";
import { OPERATION_LOG_PERM_LIST } from "@/views/system/operation-log/utils/perms";
import { BIZ_DOMAIN_PERM_LIST } from "@/views/system/biz-domain/utils/perms";
import { SERVICE_INTERFACE_PERM_LIST } from "@/views/system/service-interface/utils/perms";
import { RESOURCE_OPERATION_PERM_LIST } from "@/views/system/resource-operation/utils/perms";
// 🔧 T-FE-040 v3.1（S5）：条件查看全租户开放，路由 auths 门禁移除，不再引用 CONDITION_PERM_LIST
import { CONFLICT_RULE_PERM_LIST } from "@/views/system/conflict-rule/utils/perms";
import { RESOURCE_DEPENDENCY_PERM_LIST } from "@/views/system/resource-dependency/utils/perms";
import { PERMISSION_CHANGE_LOG_PERM_LIST } from "@/views/system/permission-change-log/utils/perms";
import { PERMISSION_QUERY_PERM_LIST } from "@/views/system/permission-query/utils/perms";

const Layout = () => import("@/layout/index.vue");

/**
 * 系统管理路由（T-FE-041 静态注册 + T-FE-015 菜单后端派生）。
 * <p>
 * T-FE-015（2026-08-31）起侧栏菜单由 /auth/user-menu 下发的 menus 树直接渲染
 * （sys_menu bootstrap 种子，可见性 = v3.5 §4.1 ∃op 派生），路由 meta.showLink
 * 不再控制侧栏——本文件只承担路由注册与页面元信息：菜单不可见 ≠ 路由不可达，
 * 越权直达 URL 由后端 VIEW 403 兜底。各子页 showLink:false 为 T-FE-041 导航收敛期
 * 历史标记（保留不动，已无侧栏语义）；「长期隐藏 9 页」口径随 2026-08-31 设计定案
 * 放开（14 页菜单一次种全，普通用户无授权不可见）。
 */
export default {
  path: "/system",
  name: "System",
  component: Layout,
  redirect: "/system/user",
  meta: {
    icon: "ep/setting",
    title: "系统管理",
    showLink: false,
    rank: 10
  },
  children: [
    {
      path: "/system/user",
      name: "SystemUser",
      component: () => import("@/views/system/user/index.vue"),
      meta: {
        showLink: false, // T-FE-041 Phase 3 开放清单（导航收敛，路由保留）
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
        showLink: false, // T-FE-041 Phase 3 开放清单（导航收敛，路由保留）
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
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
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
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
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
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
        icon: "ep/document",
        title: "操作日志",
        // 单一事实源派生：见 views/system/operation-log/utils/perms.ts
        // 操作日志独立 OPERATION_LOG:VIEW 门禁（T-PERM-025 审计分离）
        auths: [...OPERATION_LOG_PERM_LIST]
      }
    },
    {
      path: "/system/biz-domain",
      name: "SystemBizDomain",
      component: () => import("@/views/system/biz-domain/index.vue"),
      meta: {
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
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
        showLink: false, // T-FE-041 Phase 3 开放清单（导航收敛，路由保留）
        icon: "ep/connection",
        title: "服务与接口",
        // 单一事实源派生：见 views/system/service-interface/utils/perms.ts
        auths: [...SERVICE_INTERFACE_PERM_LIST]
      }
    },
    {
      path: "/system/resource-operation",
      name: "SystemResourceOperation",
      component: () => import("@/views/system/resource-operation/index.vue"),
      meta: {
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
        icon: "ep/coins",
        title: "资源与操作",
        // 单一事实源派生：见 views/system/resource-operation/utils/perms.ts
        // 资源树 CRUD 门禁 RESOURCE:VIEW/CREATE/MANAGE；操作权限 CRUD 门禁 OPERATION:VIEW/CREATE/MANAGE
        auths: [...RESOURCE_OPERATION_PERM_LIST]
      }
    },
    {
      path: "/system/permission-condition",
      name: "SystemPermissionCondition",
      component: () => import("@/views/system/permission-condition/index.vue"),
      meta: {
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
        icon: "ep/key",
        title: "权限条件"
        // 🔧 T-FE-040 v3.1（S5）：条件查看全租户开放（2026-08-08 产品确认），路由不再做读取门禁；
        // 写权限 CREATE/UPDATE/DELETE 三档由页面按钮 v-if 门控（见 views/system/permission-condition/utils/perms.ts）
      }
    },
    {
      path: "/system/conflict-rule",
      name: "SystemConflictRule",
      component: () => import("@/views/system/conflict-rule/index.vue"),
      meta: {
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
        icon: "ep/warn-triangle-filled",
        title: "冲突规则",
        // 单一事实源派生：见 views/system/conflict-rule/utils/perms.ts
        // 冲突规则门禁 CONFLICT_RULE:VIEW/CREATE/UPDATE/DELETE（读 VIEW + 写三档独立，非 MANAGE，T-PERM-030 收口口径）
        auths: [...CONFLICT_RULE_PERM_LIST]
      }
    },
    {
      path: "/system/resource-dependency",
      name: "SystemResourceDependency",
      component: () => import("@/views/system/resource-dependency/index.vue"),
      meta: {
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
        icon: "ep/share",
        title: "资源依赖",
        // 单一事实源派生：见 views/system/resource-dependency/utils/perms.ts
        // 资源依赖 CRUD 门禁 DEPENDENCY:VIEW/CREATE/UPDATE/DELETE（三档独立，非 MANAGE，对齐后端）
        // SYNC 权限码已定义但 batch-sync P0 标 TODO，不暴露按钮
        auths: [...RESOURCE_DEPENDENCY_PERM_LIST]
      }
    },
    {
      path: "/system/permission-change-log",
      name: "SystemPermissionChangeLog",
      component: () => import("@/views/system/permission-change-log/index.vue"),
      meta: {
        showLink: false, // T-FE-041 长期隐藏（导航收敛，路由保留）
        icon: "ep/history",
        title: "权限变更日志",
        // 单一事实源派生：见 views/system/permission-change-log/utils/perms.ts
        // 变更日志独立 PERMISSION_CHANGE_LOG:VIEW 门禁（T-PERM-032 审计分离）
        auths: [...PERMISSION_CHANGE_LOG_PERM_LIST]
      }
    },
    {
      path: "/system/permission-query",
      name: "SystemPermissionQuery",
      component: () => import("@/views/system/permission-query/index.vue"),
      meta: {
        showLink: false, // T-FE-041 Phase 3 开放清单（导航收敛，路由保留）
        icon: "ep/key",
        title: "权限排查",
        // 单一事实源派生：见 views/system/permission-query/utils/perms.ts
        // T-PERM-033 设计定案：无独立排查码，页面门 = USER:VIEW 或 ROLE:VIEW 任一命中（SSOT 见 utils/perms.ts）
        auths: [...PERMISSION_QUERY_PERM_LIST]
      }
    }
  ]
} satisfies RouteConfigsTable;
