---
doc_type: task
id: T-ACCESS-042
title: URL 单命名空间统一 /api/access/**——外部路径=服务路径（Q-001 转出）
status: in-progress
plan: ""
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md#2-通用规范（URL 形态总述）
  - docs/design/access-service-capability-structure.md#6（URL 风格表面项）
  - docs/design/services/gateway.md
  - docs/design/engine/core-flows.md（SDK 流程 URL）
depends_on: []
blocks: []
acceptance:
  - "全链路单形态：37 控制器、前端 94 端点、SDK 17 Feign 端点、bootstrap 固定图 86 条 ApiRoute、契约总册五处路径一律 /api/access/<资源>/**（example 为 /api/example/**），无 admin/perm 家族段、无双前缀、无裸路径例外"
  - "Gateway 三路由收敛为两条（/api/access/**→access-service、/api/example/**→example-service，无 StripPrefix），auth-routes 删除；白名单 /api/access/auth/** 放行范围与旧 /auth/** 逐字等价"
  - "user-role/list 双轨碰撞处置：管理轨端点为 /api/access/user-role/view、权限轨 list 保留；全服务无路径映射冲突（启动成功即证）"
  - "拦截器边界语义化：InternalApiSecretInterceptor 与 HeaderSignatureInterceptor 覆盖 /api/access/**；公开登录端点（captcha/login/login:sms/logout 除外集）服务层匿名语义不变"
  - "HttpApiPathSnapshotTest EXPECTED_PATHS/EXPECTED_SIGNATURES 全量重写为新形态并作为端点全集基准；旧形态（裸管理面路径与 /api/perm/**）入 RETIRED 负向断言"
  - "全量回归 mvn test -T 1C（E2E 必跑）全绿；dev 冒烟（重建库+bootstrap 重种+登录链路）通过；dev Nacos gateway.yml 覆盖键核对（不可达记「未核」）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-15
---

## 背景

Q-001（2026-09-13 登记）：access-service 两套 URL 风格并存——管理面 15 控制器裸路径（/user、/role、/auth…）与权限面 22 控制器 /api/perm/** 前缀；同一端点在契约册、前端（Gateway 外部形态 /admin/**、/perm/api/perm/** 双前缀）、SDK（直连服务形态）三处不同形，调用方与文档心智分裂。

2026-09-15 用户拍板目标形态（AskUserQuestion 两问 + 自由组合「1+3，不要有 admin 和 perm，统一为 access」），本卡转出实施。

## 范围

- access-service 37 控制器 @RequestMapping、拦截器 pattern（SecurityWebMvcConfig/RequestContextInterceptor/OAuth2ResourcePathProperties）、Gateway 路由与白名单与回源路径、bootstrap 固定图 86 条 ApiRoute、perm-sdk 17 Feign 端点与 SYNC_PATH_MARKER、example-service javadoc/e2e 注册路径。
- 前端 94 端点字面量 + vite proxy 四条收敛为单条 /api + http 白名单 + mock/login.ts + URL 契约锁 spec + 注释占位。
- 测试：HttpApiPathSnapshotTest 重写、后端内联 URL ~180 处、e2e 2 文件、gateway 测试。
- 文档：契约总册（两 URL 家族总述改单命名空间叙事，200 处 URL）、capability-structure §6、services/gateway.md、engine 文档、frontend 页面文档、runbook、AGENTS.md 口径句。

## 当前口径

**目标形态**：全链路单形态 `/api/<服务命名空间>/**`，外部路径 = 服务路径：

| 现状（服务内形态） | 目标形态 |
|---|---|
| 管理面裸路径 /user、/org、/menu、/user-org、/role、/file、/dict、/notice、/job、/login-log、/org-tree-config | /api/access/<同名资源> |
| /auth、/auth/oauth2、/oauth2/client | /api/access/auth、/api/access/auth/oauth2、/api/access/oauth2/client |
| 权限面 /api/perm/<资源>（18 业务 + 4 sync 共基） | /api/access/<资源> |
| /user-role/list（管理轨 AdminUserRoleController） | /api/access/user-role/view（唯一子路径改名） |
| example /api/example/demo/**（外部 /example/api/example/...） | /api/example/demo/**（外部=服务路径） |
| SDK 17 条 /api/perm/* | /api/access/* |

**定案与裁决**：

1. 无 admin/perm 家族段（用户拍板「1+3 统一为 access」）；登录族一并迁入 /api 根；一次性切换不留兼容（未部署零存量；API 资源业务键 `METHOD:路径` 随路径变=资源身份变，dev 重建库 + bootstrap 重种，无数据迁移）。
2. user-role/list 双轨碰撞：管理轨（全类型角色视图+显示字段，门禁 USER:VIEW，仅前端用户管理页消费）改名 `view`；权限轨 `list`（持有角色+组继承，与 assign/revoke/sync 同族读写，SDK 消费）保留。
3. 拦截器边界语义化（安全收紧）：InternalApiSecretInterceptor（无有效 X-Internal-Secret 即 403）与 HeaderSignatureInterceptor 由 /api/perm/**、/api/** 扩为 /api/access/**——管理面/登录族从「可直连服务」收紧为「仅经 Gateway 或持密服务可达」（Gateway 对全部转发请求注入密钥，合法流量不受影响），实现 T-ACCESS-004「覆盖所有受控路径」原意；白名单 /auth/** → /api/access/auth/** 放行范围逐字等价（userinfo/user-menu 在服务层仍要求会话）。
4. 外部服务经 service-config 声明接口不得占用 /api/access 命名空间（Gateway 按第二段路由，外部声明值天然不可达——文档注明约束，不加代码门禁）。
5. operation_log 存量 URI 为旧形态：dev 数据随重建库消失，不迁移。

## 验收对照

见 frontmatter `acceptance`；全部满足后回写 design_refs 并转 done。

## 非目标 / 遗留

- 不做旧路径兼容期/双路由（未部署零存量）。
- 不合并/重构 user-role 双轨查询语义本身（仅路径改名，语义收敛另议）。
- 不动 /actuator、/public、/captcha 白名单遗留段（无消费者，不扩范围）。
- FileAppServiceImpl "/files/" 文件 URL 存库前缀与本命名空间无关，不动。
