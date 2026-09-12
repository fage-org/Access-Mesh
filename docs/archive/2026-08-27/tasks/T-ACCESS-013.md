---
doc_type: task
id: T-ACCESS-013
title: OAuth2 资源服务器与 scope 授权模型（委托令牌访问业务 API 的显式开放）
status: done
plan: docs/archive/2026-08-27/access-post-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#6-可信请求上下文与安全策略
  - docs/design/services/admin-service-api-contract.md
depends_on:
  - T-ACCESS-012
blocks: []
acceptance:
  - "OAuth2 委托令牌访问业务 API 由显式配置的路径白名单 + scope 校验控制（默认拒绝）"
  - "JWT 载荷的 client_id/scope/audience 参与授权判定；scope → 权限映射采用独立映射模型（2026-08-22 定案：scope 保持 OAuth2 委托范围语义、与平台权限体系正交，不接入 PermQueryEngine；原'语义一致'措辞随决策修订）"
  - "撤销令牌（黑名单）对开放路径生效，路径限定不产生绕过"
  - "文档回写：架构文档 §6 的 OAuth2 JWT 适用范围更新为开放路径清单"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-22
---

# T-ACCESS-013 OAuth2 资源服务器与 scope 授权模型

## 背景

T-ACCESS-004 将 OAuth2 JWT 认证分支精确限定为 `/auth/oauth2/userinfo` 单一端点（唯一消费方），委托令牌不得触达管理接口或其他端点。若未来需要"OAuth2 令牌访问业务 API"（如第三方应用按 scope 调接口），必须显式实现资源服务器能力，避免以"全路径 USER"或通配路径方式放开。

## 范围

- 将 JWT 认证分支的路径白名单配置化（默认仅 `/auth/oauth2/userinfo`；**不得默认放开 `/auth/oauth2/**` 通配**——前缀匹配会覆盖 authorize 等非资源端点，开放路径必须逐项显式配置并经 scope/audience 校验）。
- JWT 载荷 `client_id`/`scope`/`audience` 参与授权判定。
- scope → 权限映射（独立映射模型，2026-08-22 用户决策）。
- 黑名单（撤销）对开放路径持续生效。

## 非目标

- 不改变平台用户会话（uuid）认证路径。
- 不引入第三方 OAuth2 服务端（本任务只做资源服务器侧）。

## 完成记录

**实施日期**：2026-08-22。**用户决策 10 项**（三批次，全部采纳）：

1. **scope→权限映射：独立映射**——开放路径每条声明 `requiredScopes`，令牌 scope（空格分隔委托范围）⊇ 所需 scope 才放行；不接入 PermQueryEngine（委托主体是客户端而非用户，与平台权限正交）。验收措辞随决策修订。
2. **audience 来源：客户端注册配置加列**——`sys_oauth2_client.audiences`（逗号分隔资源服务器标识），非空时签发写入 JWT `aud` claim（List 形态）；开放路径声明 audience 强制校验；种子客户端补 `access-service`。
3. **白名单形态：application.yml 静态配置**——`access.oauth2.resource-paths`（全量替换语义），默认仅 `/auth/oauth2/userinfo`；变更需发版审查。
4. **落地范围：access-service 机制 + 同步改 Gateway 透传**——默认零业务路径开放；Gateway 新增 `OAuth2PassthroughFilter` + `gateway.oauth2.passthrough-paths`（默认空）。
5. **client_id 判定：动态启用校验 + 可选 clientIds 限定**——验签时经 `OAuth2ClientDomainService.findActiveByClientId` 唯一索引点查（不经缓存保证禁用立即失效）→ 客户端禁用/删除即 401；白名单项可声明 `clientIds` 细化限定（不满足 403）。
6. **上下文语义：委托字段，维持 USER**——`RequestContext` 增第五要素 `delegatedClientId`（仅 OAuth2 JWT 分支非 null），operatorId=JWT loginId；审计经 `AccessRequestContext.getDelegatedClientId()` 区分第三方委托调用。
7. **audience 严格度：userinfo 豁免 + 业务路径强制**——`/auth/oauth2/userinfo` 默认不校验 audience（旧令牌无 aud 兼容）；其他开放路径令牌 aud 必须包含声明的受众（缺失/不匹配 403）。
8. **文档回写：架构 §6 + 任务卡 + 补契约**——admin-service-api-contract.md 补 §8 OAuth2 章节此前 OAuth2 契约仅存在于归档文档）。
9. **路径匹配：Ant 通配 + 启动防护**——支持 `/api/example/**` 通配；启动 fail-fast 防护：不得覆盖 `/auth/**` 会话端点（userinfo/user-menu/oauth2/authorize）与 `/api/perm/**`（内部凭证双认证冲突）。
10. **分层规范：同层横向调用全局放开**（2026-08-22 定案）——project-rules §8.2 删除禁止条款，三个既有例外登记（授权域/query 包/审计门面）废止；本任务拦截器（infrastructure）注入 admin 域 `OAuth2ClientDomainService` 依此合规；跨域 Mapper 直读边界与跳层禁令不变，`QueryBoundaryArchitectureTest` AppService 白名单断言删除、数据边界断言保留。

**授权链**（`RequestContextInterceptor.authenticateOAuth2Jwt`）：验签（HS256+loginType+超时）→ 必填 claim（loginId/jti/client_id，缺失 401）→ 撤销黑名单（`oauth2:blacklist:<jti>`，对全部开放路径生效）→ 客户端启用动态校验（禁用 401）→ 路径门禁（clientIds/scope 子集/audience，不满足 403）→ 绑定 `RequestContext.delegatedUser`。未配置路径上委托令牌默认拒绝（落会话分支 → 401）。

**验证**：access-service `mvn test` **661 通过 0 失败 40 跳过**（Docker 门控 Postgres 基线；评审修复后 664）；gateway **80 通过 0 失败**（评审修复后 83）。新增：`RequestContextInterceptorTest`（37，含 T-ACCESS-013 门禁 9 例：客户端禁用 401/缺 client_id 401/scope 不足 403/audience 不匹配 403/业务路径无 aud 403/clientIds 限定 403/Ant 通配命中/旧令牌 userinfo 豁免/全门禁通过）、`OAuth2ResourcePathPropertiesTest`（8：默认值/通配/启动防护 4 负向/合法配置/空清单；评审修复后 11，+业务路径缺 scopes/缺 audience/userinfo 豁免 3 例）、`OAuth2AudienceClaimTest`（4：aud 写入/未配置不写/空白不写/刷新链路）、`OAuth2PassthroughFilterTest`（4；评审修复后 7，+uuid 会话不透传/无头不透传/非 Bearer 不透传 3 例）；适配：`SecurityMatrixIT`（+禁用客户端 401；非开放路径 JWT 401 由单测覆盖——IT mock 环境无 Sa-Token filter 链，带 Authorization 头调 isLogin 抛 SaTokenContextException→advice 400，无法表达会话分支，已注释登记）、`QueryBoundaryArchitectureTest`（删 AppService 白名单断言）。

**文档回写**：架构文档 §6（操作者绑定规则 + 开放路径清单六点 + §6.2 矩阵新行 + Gateway 双侧路径口径部署约束）；admin-service-api-contract.md §8（授权端点 5 个契约 + JWT 载荷 + 资源服务器门禁 + 客户端 CRUD 含 audiences）；gateway.md（OAuth2 透传章节）；project-rules §8.2 + AGENTS.md + access-service-architecture §2 边界规则 + frontend/permission-grant.md（同层调用放开同步）；DDL `sys_oauth2_client.audiences` 列 + 种子。

**范围外登记**：操作日志落 delegatedClientId 列（operation_log 无该列，当前经日志 MDC/上下文可查，后续审计增强任务）；example-service 资源服务器能力（空壳服务无业务 API，未来接入需引入验签能力）。

**评审修复（2026-08-22 外部评审，P1×2 + P3×1，全部核实属实并修复）**：

- **P1（透传未限定 JWT，平台会话可绕过 Gateway 权限）**：`OAuth2PassthroughFilter` 原仅按路径设 skipAuth——透传路径上平台 uuid 会话令牌（无 '.'）不进下游 JWT 分支、被共享 Redis 会话分支接受，同时跳过了 Gateway `PermissionFilter` 接口鉴权，违反"不改变平台用户会话认证路径"非目标。修复：过滤器增加 Bearer 三段式 JWT 形态识别（与下游 JWT 分支同口径，不验签——伪造 JWT 透传后下游验签 401）；非 JWT（uuid 会话/无头/非 Bearer）不设 skipAuth，走正常 AuthTokenFilter 会话校验 + PermissionFilter 快照鉴权。新增用例：命中路径 + uuid 会话令牌不透传、无 Authorization 不透传、非 Bearer 前缀不透传（评审要求的"普通会话仍经 Gateway 鉴权"回归锚点）。
- **P1（业务开放规则可缺失 scope/audience，静默放行）**：启动校验原只查路径与保留命名空间，业务路径可不声明 `requiredScopes`/`audience`——空值运行时直接跳过两项授权门禁，与决策 1（每条声明 requiredScopes）/决策 7（业务路径强制 audience）冲突。修复：`afterPropertiesSet` 追加强制校验——非 userinfo 豁免路径必须声明 `requiredScopes`（非空）与 `audience`（非空白），缺失启动失败 fail-fast；userinfo（`DEFAULT_USERINFO_PATH` 精确匹配）豁免口径不变。新增 3 用例：缺 scopes 失败/缺 audience 失败/userinfo 豁免合法；既有合法业务路径用例与通配用例补齐双门禁示范。
- **P3（部署注释引用错误配置项）**：access-service application.yml 注释误写 `gateway.whitelist.paths`，修正为 `gateway.oauth2.passthrough-paths`（与架构文档/gateway.md/契约三处权威口径一致），并补业务路径强制双门禁与"仅 JWT 透传"说明。

**修复后验证**：access-service `mvn test` **664 通过 0 失败 40 跳过**（Docker 门控基线不变）；gateway **83 通过 0 失败**。设计回写同步：架构文档 §6（启动防护双门禁 + Gateway 透传仅 JWT 语义）、gateway.md、admin-service-api-contract.md §8.2、双侧 application.yml 注释。

**复评修复（2026-08-22 第二轮评审，P1×1 + P2×1 + P3×1，全部核实属实并修复）**：

- **P1（空注册 scopes 等同允许申请任意 scope）**：`validateScope` 原对客户端注册 scopes 为 null/空白直接放行（存量"无限制"语义）——客户端配置 audiences 后可在 authorize 声明任意 scope，签发 JWT 通过业务路径 requiredScopes 校验，构成授权链缺口。修复：空注册 + 非空请求 scope → `OAUTH2_SCOPE_INVALID`（空注册不解释为无限制）；空 scope 请求仍放行——签发的无 scope 令牌因业务路径 requiredScopes 强制非空而访问不了任何业务路径，仅可访问 userinfo 豁免端点。新增 `OAuth2ScopeValidationTest`（4 例：null 注册拒绝/空白注册拒绝/双空放行/已注册子集放行回归）。
- **P2（/api/perm/** 启动防护样本法不完备）**：原以配置模式匹配 `/api/perm`、`/api/perm/**` 两个字面量样本——`/api/**/sync` 不匹配样本却运行时命中 `/api/perm/abstract-user/sync`（内部空间为无限路径集合，样本法不完备；会话端点为 3 个有限具体路径，样本法对其完备保留）。修复：新增静态前缀保守判定 `mayCoverInternalApiPath`——模式第一个通配符（*/?）前的静态前缀为空、为 `/api/perm` 的字符前缀、或以 `/api/perm/` 开头即拒绝；`/api/**/sync`、`/api/*`、`/**`、`/api/perm/abstract-user/**`、`/api/per?/**` 形态均拦截，无关节务通配（`/api/example/**`、`/example/**`）不受影响。防护用例扩至 15（+绕过形态 4 组、无关节务放行 1 组）。
- **P3（类 javadoc 残留旧配置名）**：`OAuth2ResourcePathProperties` 类级说明误写 `gateway.whitelist.paths`，修正为 `gateway.oauth2.passthrough-paths`（全库 grep 确认清零）。

**复评修复后验证**：access-service 受影响 9 个测试类 92 通过 0 失败（全量回归见下）；新增/调整用例：`OAuth2ScopeValidationTest`（4）、`OAuth2ResourcePathPropertiesTest`（11→15）。设计回写同步：架构 §6（静态前缀判定口径）、契约 §8.1.1/§8.2（空注册拒绝 + 防护判定措辞）。

**复评修复（2026-08-22 第三轮评审，P2×1，核实属实并修复）**：

- **P2（URI 模板变量绕过启动防护）**：实证核实 AntPathMatcher（spring-core 6.1.5）段内正则**确实支持** `{name}`/`{name:regex}` 模板变量——`/api/{module}/abstract-user/sync` 运行时匹配 `/api/perm/abstract-user/sync` 为 true，而 `mayCoverInternalApiPath` 原只识别 `*`/`?`，静态前缀取到全串 → 三条件均不满足 → 放行，缺口成立。修复：通配符截断集合加入 `{`（模板变量出现在段位置且 regex 不跨段，与 `*` 同为段级通配，按同级保守处理）；`/api/{module}/**`、`/api/{m:[a-z]+}/**` 等形态拦截，无关节务路径的 `{var}` 模板（`/example/{id}`，运行时有效配置）不受影响。防护测试 15→17（+模板变量覆盖内部空间拦截 1、无关模板放行与 AntPathMatcher {var} 语义锚点 1）。

**第三轮修复后验证**：access-service `mvn test` **674 通过 0 失败 40 跳过**；gateway 83 不受影响。设计回写同步：架构 §6（启动防护通配符口径补 `{`）。
