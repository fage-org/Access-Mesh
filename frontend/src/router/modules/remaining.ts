const Layout = () => import("@/layout/index.vue");

export default [
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/login/index.vue"),
    meta: {
      title: "登录",
      showLink: false
    }
  },
  // 全屏403（无权访问）页面
  {
    path: "/access-denied",
    name: "AccessDenied",
    component: () => import("@/views/error/403.vue"),
    meta: {
      title: "403",
      showLink: false
    }
  },
  // 全屏500（服务器出错）页面
  {
    path: "/server-error",
    name: "ServerError",
    component: () => import("@/views/error/500.vue"),
    meta: {
      title: "500",
      showLink: false
    }
  },
  // 会话恢复失败重试页（T-FE-015：user-menu 重取失败 fail-closed 空菜单时，
  // 侧栏占位项跳转至此；T-FE-049 起两态——「菜单加载失败，点击重试」与
  // 「当前账号无可用菜单」占位项均着陆此页，页内按 menuLoadFailed 自适应）
  {
    path: "/menu-retry",
    name: "MenuLoadRetry",
    component: () => import("@/views/error/menu-retry.vue"),
    meta: {
      // 中性值：着陆页两态（加载失败/无可用菜单）共用此路由，meta.title 派生
      // document.title（router/index.ts）与标签显示——单态文案会对零菜单态失真
      title: "菜单不可用",
      showLink: false
    }
  },
  {
    path: "/redirect",
    component: Layout,
    meta: {
      title: "加载中...",
      showLink: false
    },
    children: [
      {
        path: "/redirect/:path(.*)",
        name: "Redirect",
        component: () => import("@/layout/redirect.vue")
      }
    ]
  }
] satisfies Array<RouteConfigsTable>;
