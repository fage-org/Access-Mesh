export default [
  {
    path: "/platform/login",
    name: "PlatformLogin",
    component: () => import("@/views/platform/login.vue"),
    meta: { title: "平台运营登录", platform: true, showLink: false }
  },
  {
    path: "/platform/change-password",
    name: "PlatformChangePassword",
    component: () => import("@/views/platform/change-password.vue"),
    meta: { title: "平台账号改密", platform: true, showLink: false }
  },
  {
    path: "/platform",
    name: "PlatformConsole",
    component: () => import("@/views/platform/layout.vue"),
    redirect: "/platform/tenants",
    meta: { title: "平台运营", platform: true, showLink: false },
    children: [
      {
        path: "/platform/tenants",
        name: "PlatformTenants",
        component: () => import("@/views/platform/tenants.vue"),
        meta: { title: "租户", platform: true, showLink: false }
      },
      {
        path: "/platform/accounts",
        name: "PlatformAccounts",
        component: () => import("@/views/platform/accounts.vue"),
        meta: { title: "平台账号", platform: true, showLink: false }
      },
      {
        path: "/platform/audit",
        name: "PlatformAudit",
        component: () => import("@/views/platform/audit.vue"),
        meta: { title: "平台审计", platform: true, showLink: false }
      }
    ]
  }
];
