// mock 登录与用户菜单（fake server，VITE_MOCK_LOGIN 开关控制注册）
//
// 注意：
// - 这是 fake server 仅模拟前端 UX 隐藏，**真实拒绝以 access-service 后端 permissionValidator 为准**。
// - perm 串字面量从 `views/system/user/utils/perms.ts`（SSOT）反向导入，禁止再硬编码。
//   契约见 `docs/design/org-user-permission-contract.md` §4。
// - T-FE-041：默认关闭（真实 /auth 链路经 Gateway）；响应壳已对齐 PermResult 契约。
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
import {
  TYPE_DEF_PERMS as TP,
  TYPE_DEF_PERM_LIST,
  TYPE_DEF_VIEW_PERMS
} from "../src/views/system/type-def/utils/perms";
import {
  SYSTEM_CONFIG_PERMS as SCP,
  SYSTEM_CONFIG_PERM_LIST,
  SYSTEM_CONFIG_VIEW_PERMS
} from "../src/views/system/config/utils/perms";
import {
  BIZ_DOMAIN_PERMS as BDP,
  BIZ_DOMAIN_PERM_LIST,
  BIZ_DOMAIN_VIEW_PERMS
} from "../src/views/system/biz-domain/utils/perms";
import {
  SERVICE_INTERFACE_PERMS as SIP,
  SERVICE_INTERFACE_PERM_LIST,
  SERVICE_INTERFACE_VIEW_PERMS
} from "../src/views/system/service-interface/utils/perms";
import {
  RESOURCE_OPERATION_PERMS as ROP,
  RESOURCE_OPERATION_PERM_LIST,
  RESOURCE_OPERATION_VIEW_PERMS
} from "../src/views/system/resource-operation/utils/perms";
import {
  CONDITION_PERMS as CP,
  CONDITION_PERM_LIST
} from "../src/views/system/permission-condition/utils/perms";
import {
  CONFLICT_RULE_PERMS as CRP,
  CONFLICT_RULE_PERM_LIST,
  CONFLICT_RULE_VIEW_PERMS
} from "../src/views/system/conflict-rule/utils/perms";
import {
  RESOURCE_DEPENDENCY_PERMS as RDP,
  RESOURCE_DEPENDENCY_PERM_LIST,
  RESOURCE_DEPENDENCY_VIEW_PERMS
} from "../src/views/system/resource-dependency/utils/perms";
import { PERMISSION_GRANT_PERM_LIST } from "../src/views/perm/grant/utils/perms";
import { OPERATION_LOG_VIEW_PERMS } from "../src/views/system/operation-log/utils/perms";
import { PERMISSION_CHANGE_LOG_VIEW_PERMS } from "../src/views/system/permission-change-log/utils/perms";

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
 *
 * 7.1 操作日志页门禁为独立 `OPERATION_LOG:VIEW`（T-PERM-025 审计分离，2026-08-28）——
 * 四账号全量补入 OPERATION_LOG_VIEW_PERMS（对齐旧复用口径下全员可查的行为；mock 模拟 UX，
 * 不模拟最小权限；审计员 auditor 必须能查日志）。
 *
 * 7.2 权限变更日志页独立 PERMISSION_CHANGE_LOG:VIEW 门禁（T-PERM-032 审计分离，2026-08-29）——
 * 四账号全量补入 PERMISSION_CHANGE_LOG_VIEW_PERMS（对齐旧复用口径下全员可查的行为；mock 模拟 UX，
 * 不模拟最小权限；审计员 auditor 必须能查日志）。
 *
 * 7.3 权限排查页不引入独立权限码（T-PERM-033 设计定案，2026-08-29：原预案
 * PERMISSION_QUERY:VIEW 否决——权限码结构为「资源:操作」，PERMISSION_QUERY 是操作描述而非资源）——
 * API 门禁为被查目标实例 USER:VIEW/ROLE:VIEW，页面级 UI 门 = USER:VIEW 或 ROLE:VIEW 任一命中；
 * 四账号经 ORG_USER_VIEW_PERMS（含 USER:VIEW）与 ROLE_MANAGE_VIEW_PERMS（含 ROLE:VIEW）
 * 已持有，本矩阵无需增配。
 *
 * 5.1 业务域页涉及**两个资源类型**门禁：
 * - biz-domain list/detail 门禁 `DOMAIN:VIEW`（独立资源类型 DOMAIN，后端 listBizDomains/getBizDomain 校验）。
 *   种子接入已收口（T-PERM-026）：DDL CRUD 预置组已覆盖 DOMAIN VIEW（原「种子缺失」为误报），
 *   真实缺口 bootstrap 固定图已补 DOMAIN:VIEW。
 *   本矩阵为所有账号预置 DOMAIN:VIEW（业务域是基础设施，各角色均可见列表）。
 * - biz-domain create/update/remove + domain-config save/remove 门禁 `SYSTEM_CONFIG:MANAGE`（复用，与 6.2 同源）。
 * - domain-config list/detail 门禁 `SYSTEM_CONFIG:VIEW`（复用）。
 * admin 全权（含 CONFIG_SAVE=MANAGE）；sec 配置管理同源全权（CONFIG_SAVE）；hr/auditor 只读（DOMAIN:VIEW + CONFIG_VIEW）。
 *
 * 5.2 服务与接口映射页：
 * - `SERVICE:VIEW` 可查看服务和接口；`SERVICE:MANAGE` 可维护服务；
 *   `SERVICE:SYNC_INTERFACE` 和 `SERVICE:MANAGE_API_MAPPING` 分别控制 FULL 同步和手工映射。
 * - sec 负责安全边界，拥有服务接口的完整维护权；hr/auditor 保持只读。
 *
 * 4.1 权限授予页（T-FE-036 v3 + T-FE-040 v3.1 + T-ACCESS-021 口径收敛）：无新增权限串——
 * - 配权门禁 `ROLE:VIEW`/`ROLE:MANAGE`（目标抽象角色）；数据源门禁 `ORG:VIEW` 等（组织入口
 *   二期）。资源树/操作列的 RESOURCE:VIEW/OPERATION:VIEW 为后端接口门禁，非页面 capability
 *   （真实 /auth/user-menu 权限串白名单不含这两个码，mock 不为授权页提供）。
 * - 🔧 T-FE-040 v3.1（S5）：条件查看全租户开放（2026-08-08 产品确认），`CONDITION:VIEW` 不再
 *   登记进角色矩阵（VIEW 列移除）；hr/auditor 不再持有条件读取串。
 * - admin 经 PERMISSION_GRANT_PERM_LIST 显式登记（与既有清单重复项无害，hasPerms 为 includes 判定）；
 *   sec 配权 RW（ROLE:MANAGE）；hr/auditor 只读（ROLE:VIEW）。
 */
const ROLE_PERM_MATRIX: Record<string, readonly string[]> = {
  admin: [
    ...ORG_USER_PERM_LIST,
    ...ROLE_MANAGE_PERM_LIST,
    ...TYPE_DEF_PERM_LIST,
    ...SYSTEM_CONFIG_PERM_LIST,
    ...BIZ_DOMAIN_PERM_LIST,
    ...SERVICE_INTERFACE_PERM_LIST,
    ...RESOURCE_OPERATION_PERM_LIST,
    ...CONDITION_PERM_LIST,
    ...CONFLICT_RULE_PERM_LIST,
    ...RESOURCE_DEPENDENCY_PERM_LIST,
    ...PERMISSION_GRANT_PERM_LIST,
    ...OPERATION_LOG_VIEW_PERMS,
    ...PERMISSION_CHANGE_LOG_VIEW_PERMS
  ],
  /** HR/组织人事管理员：A/B/D 全权 + C 只读（不分配功能角色）+ 2.2 只读角色 + 6.1 只读类型 + 6.2 只读配置 */
  hr: [
    ...ORG_USER_VIEW_PERMS,
    ...ROLE_MANAGE_VIEW_PERMS,
    ...TYPE_DEF_VIEW_PERMS,
    ...SYSTEM_CONFIG_VIEW_PERMS,
    ...OPERATION_LOG_VIEW_PERMS,
    ...PERMISSION_CHANGE_LOG_VIEW_PERMS,
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
    P.POSITION_ASSIGN,
    ...BIZ_DOMAIN_VIEW_PERMS,
    ...SERVICE_INTERFACE_VIEW_PERMS,
    ...RESOURCE_OPERATION_VIEW_PERMS,
    ...CONFLICT_RULE_VIEW_PERMS,
    ...RESOURCE_DEPENDENCY_VIEW_PERMS
  ],
  /** IT/安全管理员：C 区写权（功能角色分配/回收）+ 2.2 角色 CRUD + 配权 + 额外角色 add/remove + 6.1 类型定义 CRUD，其余只读。
   *  B1 后 EDIT/DELETE 均为 ROLE:MANAGE，去重为 ROLE_ADD + ROLE_EDIT(MANAGE)。
   *  额外角色 add/remove 对齐后端 ASSIGN/REVOKE（评审 P1-额外角色），sec 与 C 功能角色分配同源故全权。
   *  类型定义 EDIT/DELETE 同为 TYPE_DEFINITION:MANAGE，去重为 TYPE_ADD + TYPE_EDIT(MANAGE)。
   *  系统配置仅 save 走 MANAGE，sec 配置管理同源故全权。 */
  sec: [
    ...ORG_USER_VIEW_PERMS,
    ...ROLE_MANAGE_VIEW_PERMS,
    ...TYPE_DEF_VIEW_PERMS,
    ...SYSTEM_CONFIG_VIEW_PERMS,
    ...OPERATION_LOG_VIEW_PERMS,
    ...PERMISSION_CHANGE_LOG_VIEW_PERMS,
    P.USER_ROLE_ASSIGN,
    P.USER_ROLE_REVOKE,
    RP.ROLE_ADD,
    RP.ROLE_EDIT,
    RP.ROLE_ASSIGN,
    RP.ROLE_REVOKE,
    TP.TYPE_ADD,
    TP.TYPE_EDIT,
    TP.TYPE_DELETE,
    SCP.CONFIG_SAVE,
    BDP.DOMAIN_VIEW,
    BDP.CONFIG_SAVE,
    SIP.SERVICE_VIEW,
    SIP.SERVICE_MANAGE,
    SIP.SERVICE_SYNC,
    SIP.MAPPING_MANAGE,
    ROP.RESOURCE_VIEW,
    ROP.RESOURCE_ADD,
    ROP.RESOURCE_EDIT,
    ROP.RESOURCE_DELETE,
    ROP.RESOURCE_MOVE,
    ROP.OPERATION_VIEW,
    ROP.OPERATION_ADD,
    ROP.OPERATION_EDIT,
    ROP.OPERATION_DELETE,
    CP.CONDITION_ADD,
    CP.CONDITION_EDIT,
    CP.CONDITION_DELETE,
    CRP.CONFLICT_RULE_VIEW,
    CRP.CONFLICT_RULE_ADD,
    CRP.CONFLICT_RULE_EDIT,
    CRP.CONFLICT_RULE_DELETE,
    RDP.RESOURCE_DEPENDENCY_VIEW,
    RDP.RESOURCE_DEPENDENCY_ADD,
    RDP.RESOURCE_DEPENDENCY_EDIT,
    RDP.RESOURCE_DEPENDENCY_DELETE,
    RDP.RESOURCE_DEPENDENCY_SYNC
  ],
  /** 审计员：全只读 */
  auditor: [
    ...ORG_USER_VIEW_PERMS,
    ...ROLE_MANAGE_VIEW_PERMS,
    ...TYPE_DEF_VIEW_PERMS,
    ...SYSTEM_CONFIG_VIEW_PERMS,
    ...BIZ_DOMAIN_VIEW_PERMS,
    ...SERVICE_INTERFACE_VIEW_PERMS,
    ...RESOURCE_OPERATION_VIEW_PERMS,
    ...CONFLICT_RULE_VIEW_PERMS,
    ...RESOURCE_DEPENDENCY_VIEW_PERMS,
    ...OPERATION_LOG_VIEW_PERMS,
    ...PERMISSION_CHANGE_LOG_VIEW_PERMS
  ]
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
  return {
    // 响应对齐后端 LoginResp（PermResult.data）
    // 显式假串前缀，避免被误认为真 JWT
    accessToken: `mock-token-${username}`,
    refreshToken: null,
    expiresIn: 7200,
    tokenType: "Bearer",
    userId: 1,
    username,
    tenantId: 1,
    forceResetPwd: false,
    // 以下为前端 store 兼容字段（真后端 LoginResp 不含，mock 供 setToken 参考）
    avatar: profile.avatar,
    nickname: profile.nickname,
    roles: [username === "admin" ? "admin" : "common"],
    permissions: ROLE_PERM_MATRIX[username],
    expires: "2030/10/30 00:00:00"
  };
}

/**
 * T-FE-041 mock 开关：默认 false（.env.development VITE_MOCK_LOGIN），mock 不注册任何路由，
 * 真实 /auth 链路全程经 vite proxy → Gateway。仅前端联调需要 mock 登录时置 true；
 * 此时响应壳已对齐 PermResult 契约，前端代码零分支。
 * <p>
 * 注意：fake-server 中间件先于 vite proxy 执行，开关打开时 /auth/user-menu 会被本 mock
 * 拦截（无法同时使用真实登录 + mock 菜单）。
 */
const MOCK_LOGIN_ENABLED = process.env.VITE_MOCK_LOGIN === "true";

export default defineFakeRoute(
  MOCK_LOGIN_ENABLED
    ? [
        {
          /**
           * mock 验证码（T-FE-041 评审修复）：登录页始终请求 /auth/captcha，
           * 缺本路由则"纯 mock 联调"仍依赖真实后端。返回假 ID + SVG 占位图
           * （mock 登录不校验验证码，任意 4 位输入即可）。
           */
          url: "/auth/captcha",
          method: "post",
          response: () => {
            const svg =
              '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40"><rect width="100%" height="100%" fill="#f0f2f5"/><text x="60" y="25" text-anchor="middle" font-family="monospace" font-size="14" fill="#909399">MOCK</text></svg>';
            return {
              code: 200,
              message: "ok",
              data: {
                captchaId: `mock-captcha-${Date.now()}`,
                image: `data:image/svg+xml;base64,${Buffer.from(svg).toString("base64")}`
              }
            };
          }
        },
        {
          url: "/auth/login",
          method: "post",
          response: ({ body }) => {
            const username = body?.username as string;
            // 未知账号显式拒绝，避免静默放行掩盖真后端 401（联调时切真后端不会被误命中 mock）
            if (!username || !KNOWN_USERS.includes(username)) {
              console.warn(
                `[mock/login] 未知账号 "${username}"，拒绝登录。可用账号：${KNOWN_USERS.join(", ")}`
              );
              return {
                code: 10001,
                message: `账号或密码错误（mock 已知账号：${KNOWN_USERS.join(", ")}）`,
                data: null
              };
            }
            return {
              code: 200,
              message: "ok",
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
           * <p>
           * 响应壳与真后端 `PermResult<UserMenuResp>`（code=200/message/data）一致 ——
           * 前端 store/user.ts 通过 `unwrap` 解包。
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
            const permissions =
              ROLE_PERM_MATRIX[username] ?? ROLE_PERM_MATRIX.admin;
            return {
              code: 200,
              message: "ok",
              data: {
                // mock 暂不下发菜单树（纯静态路由模式下菜单由本地 modules 渲染）；
                // 真后端此处会返回完整 DIR/MENU 树
                menus: [],
                roles: [profile.nickname ?? username],
                permissions
              }
            };
          }
        }
      ]
    : []
);
