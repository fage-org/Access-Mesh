# 路由定义

```typescript
// router/modules/user.ts
export default {
  path: "/user",
  name: "User",
  component: () => import("@/layout/index.vue"),
  meta: { title: "用户管理", icon: "ri:user-line", rank: 0 },
  children: [
    {
      path: "/user/list",
      name: "UserList",
      component: () => import("@/views/user/list/index.vue"),
      meta: { title: "用户列表" }
    },
    {
      path: "/user/detail/:id",
      name: "UserDetail",
      component: () => import("@/views/user/detail/index.vue"),
      meta: { title: "用户详情", showLink: false }
    },
    {
      path: "/user/org",
      name: "OrgManage",
      component: () => import("@/views/user/org/index.vue"),
      meta: { title: "组织管理" }
    }
  ]
};

// router/modules/permission.ts
export default {
  path: "/permission",
  name: "Permission",
  component: () => import("@/layout/index.vue"),
  meta: { title: "权限管理", icon: "ri:shield-keyhole-line", rank: 1 },
  children: [
    {
      path: "/permission/role",
      name: "RoleManage",
      component: () => import("@/views/permission/role/index.vue"),
      meta: { title: "角色管理" }
    },
    {
      path: "/permission/resource",
      name: "ResourceManage",
      component: () => import("@/views/permission/resource/index.vue"),
      meta: { title: "资源管理" }
    },
    {
      path: "/permission/operation",
      name: "OperationManage",
      component: () => import("@/views/permission/operation/index.vue"),
      meta: { title: "操作权限" }
    },
    {
      path: "/permission/grant",
      name: "PermissionGrant",
      component: () => import("@/views/permission/grant/index.vue"),
      meta: { title: "权限授权" }
    },
    {
      path: "/permission/user-role",
      name: "UserRole",
      component: () => import("@/views/permission/user-role/index.vue"),
      meta: { title: "用户角色" }
    }
  ]
};

// router/modules/integration.ts
export default {
  path: "/integration",
  name: "Integration",
  component: () => import("@/layout/index.vue"),
  meta: { title: "服务集成", icon: "ri:links-line", rank: 2 },
  children: [
    {
      path: "/integration/service",
      name: "ServiceConfig",
      component: () => import("@/views/integration/service/index.vue"),
      meta: { title: "服务管理" }
    },
    {
      path: "/integration/api-mapping",
      name: "ApiMapping",
      component: () => import("@/views/integration/api-mapping/index.vue"),
      meta: { title: "接口映射" }
    },
    {
      path: "/integration/dependency",
      name: "ResourceDependency",
      component: () => import("@/views/integration/dependency/index.vue"),
      meta: { title: "资源依赖" }
    }
  ]
};

// router/modules/audit.ts
export default {
  path: "/audit",
  name: "Audit",
  component: () => import("@/layout/index.vue"),
  meta: { title: "审计排查", icon: "ri:search-eye-line", rank: 3 },
  children: [
    {
      path: "/audit/permission-view",
      name: "PermissionView",
      component: () => import("@/views/audit/permission-view/index.vue"),
      meta: { title: "权限排查" }
    },
    {
      path: "/audit/operation-log",
      name: "OperationLog",
      component: () => import("@/views/audit/operation-log/index.vue"),
      meta: { title: "操作日志" }
    },
    {
      path: "/audit/change-log",
      name: "ChangeLog",
      component: () => import("@/views/audit/change-log/index.vue"),
      meta: { title: "变更日志" }
    }
  ]
};

// router/modules/system.ts
export default {
  path: "/system",
  name: "System",
  component: () => import("@/layout/index.vue"),
  meta: { title: "系统配置", icon: "ri:settings-3-line", rank: 4 },
  children: [
    {
      path: "/system/type-definition",
      name: "TypeDefinition",
      component: () => import("@/views/system/type-definition/index.vue"),
      meta: { title: "类型定义" }
    },
    {
      path: "/system/biz-domain",
      name: "BizDomain",
      component: () => import("@/views/system/biz-domain/index.vue"),
      meta: { title: "业务域" }
    },
    {
      path: "/system/domain-config",
      name: "DomainConfig",
      component: () => import("@/views/system/domain-config/index.vue"),
      meta: { title: "域配置" }
    },
    {
      path: "/system/condition",
      name: "Condition",
      component: () => import("@/views/system/condition/index.vue"),
      meta: { title: "权限条件" }
    },
    {
      path: "/system/conflict-rule",
      name: "ConflictRule",
      component: () => import("@/views/system/conflict-rule/index.vue"),
      meta: { title: "冲突规则" }
    },
    {
      path: "/system/settings",
      name: "SystemSettings",
      component: () => import("@/views/system/settings/index.vue"),
      meta: { title: "系统设置" }
    }
  ]
};
```
