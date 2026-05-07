// 模拟后端动态生成路由
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/**
 * 模拟用户菜单响应
 * 与后端 UserMenuResp.java 结构一致
 * roles：页面级别权限，这里模拟二种 "admin"、"common"
 * permissions：按钮级别权限
 */
const permissionRouter = {
  path: "/permission",
  meta: {
    title: "权限管理",
    icon: "ep:lollipop",
    rank: 10,
    showLink: true,
    keepAlive: false
  },
  children: [
    {
      path: "/permission/page/index",
      name: "PermissionPage",
      component: "permission/page/index",
      meta: {
        title: "页面权限",
        roles: ["admin", "common"],
        showLink: true,
        keepAlive: false
      }
    },
    {
      path: "/permission/button",
      meta: {
        title: "按钮权限",
        roles: ["admin", "common"],
        showLink: true,
        keepAlive: false
      },
      children: [
        {
          path: "/permission/button/router",
          component: "permission/button/index",
          name: "PermissionButtonRouter",
          meta: {
            title: "路由返回按钮权限",
            auths: [
              "permission:btn:add",
              "permission:btn:edit",
              "permission:btn:delete"
            ],
            showLink: true,
            keepAlive: false
          }
        },
        {
          path: "/permission/button/login",
          component: "permission/button/perms",
          name: "PermissionButtonLogin",
          meta: {
            title: "登录接口返回按钮权限",
            showLink: true,
            keepAlive: false
          }
        }
      ]
    }
  ]
};

export default defineFakeRoute([
  {
    url: "/auth/user-menu",
    method: "post",
    response: () => {
      return {
        success: true,
        data: {
          menus: [permissionRouter],
          roles: ["admin", "common"],
          permissions: [
            "permission:btn:add",
            "permission:btn:edit",
            "permission:btn:delete",
            "system:user:create",
            "system:user:edit",
            "system:user:delete"
          ]
        }
      };
    }
  }
]);
