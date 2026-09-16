---
doc_type: task
id: T-ACCESS-042
title: URL 单命名空间统一 /api/access/**——外部路径=服务路径（Q-001 转出）
status: done
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
  status: done
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
3. 拦截器边界语义化（安全收紧，豁免清单经运行时鉴权链实证后精确化）：InternalApiSecretInterceptor（无有效 X-Internal-Secret 即 403）由 /api/perm/** 扩为 `/api/access/**`，**精确豁免会话入口族**（captcha、login、login/sms、logout、userinfo、user-menu、oauth2/**——保留公开/Sa-Token 会话/JWT 自有信任模型，密钥拦截会架空服务层会话分支与 JWT 分支）；运行时鉴权六端点（check/batch-check/check-interface/query-resources/query-scopes/interface-snapshot）**维持覆盖**——与迁移前 /api/perm/auth/* 覆盖关系逐端点对应（INTERNAL_AUTHENTICATED 属性由密钥拦截器写入，全豁免会使签名拦截器按 tenant-only 403 拒绝服务凭证调用，CustomResourceTypeSlicePgIT 实证后收窄）。HeaderSignatureInterceptor 同步 `/api/access/**`；管理面从「可直连服务」收紧为「仅经 Gateway 或持密服务可达」。白名单 /auth/** → /api/access/auth/ 会话入口族精确清单（外评 P2 处置收窄——整族放行会罩住运行时鉴权六端点，非旧形态等价替换）。
4. OAuth2 内部凭证路径重叠守卫退役（实施期裁决）：`OAuth2ResourcePathProperties.mayCoverInternalApiPath` 与 INTERNAL_API_PREFIX 删除——单命名空间后 userinfo 等开放路径必然位于 /api/access/**，而合法流量恒经 Gateway 携带密钥、OAuth2 JWT 验证在密钥拦截器之后独立执行，「双认证机制冲突」前提失效；会话端点覆盖防护保留，四组守卫用例改锁新语义（旧实现下必红）。
5. 无操作者显式拒绝（实施期修复）：`AdminPermissionValidatorImpl.currentOperatorId` 空操作者归一为 null，`checkAndThrow` 对 SERVICE 上下文/未登录直连显式 403——旧实现落 StpUtil 环境性异常 500（管理面并入密钥边界后该形态新可达，收口为干净拒绝）。
6. 外部服务经 service-config 声明接口不得占用 /api/access 命名空间（Gateway 按第二段路由，外部声明值天然不可达——文档注明约束，不加代码门禁）。
7. operation_log 存量 URI 为旧形态：dev 数据随重建库消失，不迁移。
8. dev Nacos gateway.yml 覆盖键：2026-09-15 认证查询全量配置 totalCount=0——无覆盖键，仓库 yml 即权威（已核为「无」，非「未核」）。

## 验收对照

见 frontmatter `acceptance`；全部满足后回写 design_refs 并转 done。

## 非目标 / 遗留

- 不做旧路径兼容期/双路由（未部署零存量）。
- 不合并/重构 user-role 双轨查询语义本身（仅路径改名，语义收敛另议）。
- 不动 /actuator、/public、/captcha 白名单遗留段（无消费者，不扩范围）。
- FileAppServiceImpl "/files/" 文件 URL 存库前缀与本命名空间无关，不动。

## 完成记录

- **实施**：2026-09-15，四批提交 670143b66（①后端+Gateway+SDK+固定图）/ a5eb03f80（②前端）/ 834db1394（③文档）+ bf4234e2d（④容器轨/e2e 密钥边界适配）+ 双轨评审处置批（见下）。
- **回归证据**：`mvn test -T 1C`（2026-09-15，收口形态含 E2E）1699 tests / 0 Failures / 0 Errors / 0 Skipped，BUILD SUCCESS；前端 `pnpm typecheck` 0 错、`pnpm test` vitest 14 文件 229 用例全绿、`pnpm build` 3.43MB 成功。
- **dev 冒烟**（2026-09-15，重建库+bootstrap 重种 86 资源/85 映射/135 授权/14 菜单）：经 Gateway 全链路——captcha 200 → admin login 200（expiresIn 7200）→ user-menu 200（2 菜单/50 权限）→ user/page 200 → biz-domain/list 200 → org/tree 200 → user-role/view 200；旧路径 /admin/user/page 404 负向；直连快照探针 85 条 allowedApis 全 /api/access/** 形态。
- **Nacos 核对**：dev Nacos 认证查询全量配置 totalCount=0——无 gateway.yml 覆盖键，仓库 yml 即权威（已核为「无」）。
- **双轨评审处置**（2026-09-15，代码轨 0P0/1P1 + 文档轨 1P0/3P1，交叉命中同一核心缺陷，逐条核实全修）：①契约总册 user-role/view 改名漏改（P0/P1 交叉，§10.1/§4 门禁表/§5 注记/§22 决策表/附录 A 七处）；②access-service-architecture 三处半改句（路由尾部/OAuth2 守卫段/双侧口径句）+ architecture 权威指针句 + biz-domain L176 + project-rules 三处标签；③前端 23 文件注释残留（含 spec 头注释与断言相反的主动误导处）+ 后端 11 文件注释/javadoc 残留；④GatewayApplicationConfigTest 白名单负向锁补齐 + 断言文案修正；⑤OAuth2 退役回归锁 javadoc「旧必红」表述过强修正（deep 组精确路径旧实现下亦通过）；⑥registry 定案行「登录族收紧」半句与终态相反——修正并补实施期裁决（豁免清单精确化/守卫退役/门禁 403 收口）；⑦P3 顺手：runbook-full-sync §8 旧册锚点改指总册 §19.x、e2e 两处注释、任务卡过程措辞。存疑三项（allPathsUnderApiNamespace 收紧否/注释清扫时机/契约 §10.1 修法）均按「修法唯一最小无取舍」直接处置，未升级提问。
- **实施期裁决四项**已入「当前口径」节并登记 registry 2026-09-15 行（含收口修正）。

## 外部评审轮（2026-09-15，claude + grok 双通道）

- **通道形态**：claude（默认模型 astron-code-latest，plan 模式，--disallowedTools Task Workflow）/ grok（grok-4.6 xhigh，--sandbox read-only --no-subagents），共用提示词（模板 v2 + 五项专项清单），评审范围 c61637656..e482a71cc。
- **结论**：claude P0×1 + P2×1 + P3×3；grok P0×1 + P2×0 + P3×0。P0 双通道交叉命中（前端登录三端点双前缀）；白名单项两通道分歧（grok 判不报/fail-closed 成立，claude 报 P2），主代理亲核裁决采纳收窄。
- **处置（全修）**：①P0——本地清扫批 c8fccb6e6 子串规则 `/auth/...` 命中已迁移串产生 `/api/access/api/access/` 双前缀 15 处（auth.ts 三真实请求 + http 白名单 + 注释），登录链整体 404；typecheck/vitest 不锁 URL 字面量而漏网、dev 冒烟跑在清扫批之前——15 处全修 + 新增 `frontend/src/api/auth.spec.ts` 契约锁（三端点精确断言 + api 目录双前缀负向扫描，旧形态必红）。②P2——Gateway 白名单收窄为会话入口族精确清单（yml + GatewayProperties 默认值 + 测试负向断言：六运行时端点不得被白名单匹配）；收窄后匿名打运行时端点经 Gateway 干净 401（冒烟实证），不再依赖租户头清洗的偶然防线。③P3——allPathsUnderApiNamespace 收紧为 startsWith("/api/access/")；OAuth2ResourcePathProperties 增开放路径被内部凭证分支遮蔽的启动 WARN（不拒启）；两通道存量注释清单 12 文件扫尾（arch doc 3 条路由残句/e2e 注释/BootstrapGraphDefinition/SDK 测试 javadoc/.env.development/gateway 两 filter javadoc/user-manage 注释等）。④grok 专项矩阵逐端点核验拦截器×四形态行为与定案一致（采信为实证通过面）。
- **修复后验证**：前端 typecheck 0 错 + vitest 15 文件全绿（含新 auth.spec）+ build 3.43MB；gateway 全模块 106 全绿；access 定向 36 全绿；dev 冒烟重做——captcha 200 → login 200（7200）→ user-menu 200（2 菜单/50 权限）→ user/page 200 → 运行时端点匿名 401（负向）；收口全量回归 `mvn test -T 1C`（含 E2E）1699 tests / 0F / 0E / BUILD SUCCESS（本轮 209 项容器组 Skipped 系并行负载下 testcontainers Docker 探测瞬时抖动——`mvn test -pl access-service` 隔离复跑 210/210 全绿零跳过，抖动定性非缺陷）。
