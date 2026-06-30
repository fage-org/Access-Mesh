// 根据角色动态生成路由 / 模拟前端按钮级 perm 门控（fake server）
//
// 注意：
// - 这是 fake server 仅模拟前端 UX 隐藏，**真实拒绝以 admin-service 后端 permissionValidator 为准**。
// - perm 串字面量从 `views/system/user/utils/perms.ts`（SSOT）反向导入，禁止再硬编码。
//   契约见 `docs/design/org-user-permission-contract.md` §4。
import { defineFakeRoute } from "vite-plugin-fake-server/client";
import {
  ORG_USER_PERMS as P,
  ORG_USER_PERM_LIST,
  ORG_USER_VIEW_PERMS
} from "../src/views/system/user/utils/perms";
import {
  ROLE_MANAGE_PERMS as RP,
  ROLE_MANAGE_PERM_LIST,
  ROLE_MANAGE_VIEW_PERMS
} from "../src/views/system/role/utils/perms";

/**
 * 角色 → perm 串清单。基于 AccessMesh 平台特性 + 契约 §7 业务域委派原则，
 * 体现「身份管理 ⊥ 权限委派」职责分离：
 *
 * | 用户名  | 现实对应          | A 组织 | B 用户身份 | C 功能角色 | D 岗位 | 2.2 角色管理 |
 * |---------|-------------------|--------|------------|------------|--------|--------------|
 * | admin   | 超管              | RW     | RW         | RW         | RW     | RW           |
 * | hr      | HR/组织人事管理员 | RW     | RW         | RO         | RW     | RO           |
 * | sec     | IT/安全管理员     | RO     | RO         | RW         | RO     | RW           |
 * | auditor | 审计员            | RO     | RO         | RO         | RO     | RO           |
 *
 * 「hr 不能发权 / sec 不能动身份」即契约 §6 红线之外的延伸：发权与身份目录由不同岗位掌握。
 * 角色管理（2.2）职责与 C 功能角色分配同源，故 sec（安全管理员）拥有角色 CRUD + 配权，
 * hr 仅只读角色（身份目录维护者不动角色定义）。
 *
 * admin 用全清单（而非 `*:*:*` 通配），便于联调时验证 perm 串拼写与矩阵覆盖度；
 * 通配测试可用其他独立账号承载。
 */
const ROLE_PERM_MATRIX: Record<string, readonly string[]> = {
  admin: [...ORG_USER_PERM_LIST, ...ROLE_MANAGE_PERM_LIST],
  /** HR/组织人事管理员：A/B/D 全权 + C 只读（不分配功能角色）+ 2.2 只读角色 */
  hr: [
    ...ORG_USER_VIEW_PERMS,
    ...ROLE_MANAGE_VIEW_PERMS,
    P.ORG_ADD,
    P.ORG_EDIT,
    P.ORG_DELETE,
    P.ORG_MEMBER,
    P.USER_ADD,
    P.USER_EDIT,
    P.USER_DELETE,
    P.USER_ENABLE,
    P.USER_RESET_PWD,
    P.POSITION_ADD,
    P.POSITION_EDIT,
    P.POSITION_DELETE,
    P.POSITION_ASSIGN
  ],
  /** IT/安全管理员：C 区写权（功能角色分配/回收）+ 2.2 角色 CRUD + 配权，其余只读。
   *  B1 后 EDIT/DELETE/GRANT 均为 ROLE:MANAGE，去重为 ROLE_ADD + ROLE_GRANT(MANAGE)。 */
  sec: [
    ...ORG_USER_VIEW_PERMS,
    ...ROLE_MANAGE_VIEW_PERMS,
    P.USER_ROLE_ASSIGN,
    P.USER_ROLE_REVOKE,
    RP.ROLE_ADD,
    RP.ROLE_GRANT
  ],
  /** 审计员：全只读 */
  auditor: [...ORG_USER_VIEW_PERMS, ...ROLE_MANAGE_VIEW_PERMS]
};

/** 已知账号 profile（avatar/nickname），其他字段统一拼装 */
const ROLE_PROFILES: Record<string, { avatar: string; nickname: string }> = {
  admin: {
    avatar: "https://avatars.githubusercontent.com/u/44761321",
    nickname: "超级管理员"
  },
  hr: {
    avatar: "https://avatars.githubusercontent.com/u/52823142",
    nickname: "组织人事管理员"
  },
  sec: {
    avatar: "https://avatars.githubusercontent.com/u/52823142",
    nickname: "安全管理员"
  },
  auditor: {
    avatar: "https://avatars.githubusercontent.com/u/52823142",
    nickname: "审计员"
  }
};

const KNOWN_USERS = Object.keys(ROLE_PERM_MATRIX);

function buildLoginPayload(username: string) {
  const profile = ROLE_PROFILES[username];
  const permissions = ROLE_PERM_MATRIX[username];
  return {
    avatar: profile.avatar,
    username,
    nickname: profile.nickname,
    roles: [username === "admin" ? "admin" : "common"],
    permissions,
    // 显式假串前缀，避免被误认为真 JWT
    accessToken: `mock-token-${username}`,
    refreshToken: `mock-refresh-${username}`,
    expires: "2030/10/30 00:00:00"
  };
}

export default defineFakeRoute([
  {
    url: "/login",
    method: "post",
    response: ({ body }) => {
      const username = body?.username as string;
      // 未知账号显式拒绝，避免静默放行掩盖真后端 401（联调时切真后端不会被误命中 mock）
      if (!username || !KNOWN_USERS.includes(username)) {
        console.warn(
          `[mock/login] 未知账号 "${username}"，拒绝登录。可用账号：${KNOWN_USERS.join(", ")}`
        );
        return {
          success: false,
          message: `账号或密码错误（mock 已知账号：${KNOWN_USERS.join(", ")}）`
        };
      }
      return {
        success: true,
        data: buildLoginPayload(username)
      };
    }
  },
  {
    /**
     * v1.4 双轨并行下发入口（mock）。
     * <p>
     * 真后端从 token 解析 userId 后查权限中心；mock 无 token 解码逻辑，
     * 通过 token 字段 `mock-token-{username}` 反查 ROLE_PERM_MATRIX。
     * 切真后端时本 mock 自动让位（fake server 仅在未配置真接口时生效）。
     * <p>
     * 响应壳必须与真后端 `PermResult<UserMenuResp>`（code=200/message/data）一致 ——
     * 前端 store/user.ts 通过 `unwrap` 解包，旧的 `{ success, data }` 壳会被当作 code 缺失抛错。
     */
    url: "/auth/user-menu",
    method: "post",
    response: ({ headers }) => {
      const auth = (headers?.authorization ??
        headers?.Authorization ??
        "") as string;
      // Authorization 形如 "Bearer mock-token-{username}"
      const match = /mock-token-([a-z0-9_-]+)/i.exec(auth);
      const username = match?.[1] ?? "admin";
      const profile = ROLE_PROFILES[username] ?? ROLE_PROFILES.admin;
      const permissions = ROLE_PERM_MATRIX[username] ?? ROLE_PERM_MATRIX.admin;
      return {
        code: 200,
        message: "ok",
        data: {
          // mock 暂不下发菜单树（前端路由由 /get-async-routes 提供，菜单可见性轨道仍走旧路径）；
          // 真后端此处会返回完整 DIR/MENU 树
          menus: [],
          roles: [profile.nickname ?? username],
          permissions
        }
      };
    }
  }
]);
