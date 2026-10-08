---
doc_type: task
id: T-ACCESS-097
title: OAuth2 客户端租户内唯一改造
status: done
plan: —
domain: access-service
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/access-service-api-contract.md
  - docs/design/tenant-lifecycle.md#tenant-login
depends_on: []
acceptance:
  - uk_oauth2_client_id 改为 (tenant_id, client_id) 复合唯一，权威 DDL 同步更新
  - authorize/token/refresh 与 JWT 校验链的客户端解析全部带租户，跨租户同名客户端互不可见、不误绑
  - 重复注册仅对租户内重复提示"已存在"，不再泄露他租户占用情况
  - 契约总册 OAuth2 章、e2e 与相关 PgIT 适配，全量回归通过
design_writeback:
  required: true
  status: done
last_updated: 2026-10-08
---

## 背景

[Q-069](../../pending-problems.md#q-069)：`sys_oauth2_client.client_id` 现为全局唯一（索引不含 tenant_id）。历史上单租户期撞名场景不存在；租户生命周期批次使"各租户自行注册客户端"成为常态后，两个租户注册同名客户端（如都想用 `admin-web`）必然冲突，且重复注册错误向租户管理员泄露"该标识已被别处占用"。

## 范围

权威 DDL 唯一索引改造；OAuth2AppServiceImpl/RequestContextInterceptor 等客户端解析链（findActiveByClientId、getValidClient、code/refresh/JWT 分支的 clientId 校验）全部改带租户解析；重复注册错误语义；契约与测试适配。

## 当前口径

2026-10-08 用户拍板：client_id 改**租户内唯一**（两租户可同名），不采用"全局唯一"口径。沿用空库重建政策，不提供存量库在线迁移工具。

同日用户拍板（方案分叉）：匿名 token/refresh 端点解析顺序取**方案 A 预读两段式**——先按 clientId 取跨租户启用候选列表做存在性判定，预读（只读不删）授权码/刷新令牌记录取租户后选行，secret/grant 校验在凭据消费之前（secret 错误不烧码，凭据消费/存活语义与全局唯一期零漂移；已知例外：「码无效 + secret 也错」交叉情形错误码由 CLIENT_INVALID 变 CODE_INVALID——预读必须先于 secret 才能定租户，属方案 A 固有取舍，契约 §6.1.2 已固化口径），消费后执行 binding/redirect/PKCE 等既有校验；选不出行（码/令牌租户下无该 clientId 启用行）拒绝且凭据不消费。

## 实施记录

- DDL：`uk_oauth2_client_id` 改 `(tenant_id, client_id)`（WHERE delete_flag=0 部分索引形态不变）；列注释改「租户内唯一」。
- 解析链：
  - `OAuth2ClientDomainService.findActiveByClientId(Long tenantId, String clientId)`（authorize 会话租户 / JWT 载荷 claim 租户）+ 新增 `findActiveListByClientId(String clientId)`（匿名端点候选列表）；Mapper `selectActiveByClientId` 加租户条件、新增 `selectActiveListByClientId`（ORDER BY tenant_id 定序）。
  - `OAuth2AppServiceImpl.prepareAuthorization`：会话租户先取（null 拒绝），带租户单查；原「行租户与会话比对」由查询限定取代。
  - `tokenByAuthorizationCode` / `refreshToken`：方案 A 顺序重构（候选列表 → 预读凭据定租户并提前 set TenantContextHolder/OperationLogRuntimeContext → 选行 → secret/grant → GET+DEL 消费 → binding/redirect/PKCE/epoch/user/代际）。消费读重新解析为权威数据（码/令牌数据不可变，预读仅定租户）。
  - `RequestContextInterceptor.authenticateOAuth2Jwt`：tenant_id claim 解析提前至客户端查询前（缺失/无效含 "0" → 401），带租户点查；原「claim 与行租户比对」由查询限定取代。
- 错误语义：注册查重 `selectByClientId(tenantId, clientId)` 既有代码已带租户，复合索引后「他租户占用」不再触发 DB 冲突（注册成功即无泄露面）；10801「客户端标识已存在」仅租户内重复出现，措辞无需变。
- 审计兜底：`recordOauth2Failure` 的 clientId 反查租户改列表语义——恰 1 行取其租户、跨租户同名多行归属不唯一跳过并 warn（tenant_id NOT NULL 不写虚构归属；预读成功后失败审计已按码/令牌租户归属，fallback 仅覆盖预读前失败）。
- 顺带订正：`tenant-lifecycle.md` 初始化表「当前 SQL 将 admin-web 等客户端种入租户 1」过时表述（schema/代码均零种子）；`access-service-architecture.md` §6 授权链「唯一索引点查」表述与「种子客户端 audiences=access-service」残留。

## 测试

- 既有单测适配 14 类（authorize 链单查带租户、token/refresh 链列表+预读双段、审计断言面收窄 never().set）；`shouldRejectAuthorize_whenClientBelongsToAnotherTenant` 改锁「会话租户下查无此客户端」（mock 模拟带租户查询过滤语义）。
- 新增 `Oauth2ClientTenantUniquenessPgIT`（3 用例：两租户同名注册+租户内重复 10801 / token 兑换按码租户选行〔他租户 secret 拒绝+自身 secret 原码兑换成功+重放拒绝〕 / userinfo JWT 按载荷租户解析）。红跑实证：旧全局唯一 DDL 下 3 用例全红（insert 唯一冲突），新 DDL 下全绿。
- e2e：`ExampleProtectedApiE2EIT` 新增 Order(10)「两租户同名 OAuth2 客户端并存，兑换链按凭据租户隔离不误绑」（平台运营开第二租户→两租户同名注册→他租户 secret 兑码拒绝→自身 secret 原码兑换成功→userinfo 200；Order(9) 停用租户 1 后 adminToken 已失效，用例内重登）。

## 外部评审处置（2026-10-08，claude + codex sol 双通道）

两通道并行评审 `13a4eff14..758f27a0d`（评审过程件不入仓库）。claude：0 P0-P2 + 2 P3；codex sol：1 P2 + 0 P3；逐条核实全部属实，处置如下：

- **codex P2（属实，用户拍板方案 a 结构化身份）**：开放路径 `clientIds` 白名单仍按裸 clientId 字符串比较，租户内唯一后他租户同名客户端可借白名单名通过第三道门禁（数据面无越权、默认配置未配白名单不受影响，被稀释的是「平台运营方批准特定客户端」的准入精度）。处置：条目改 `tenantId:clientId` 结构化身份，按已解析客户端行精确比较（`RequestContextInterceptor`）；条目格式启动 fail-fast 校验（裸格式=静默全拒，配置错误启动期暴露，`OAuth2ResourcePathProperties`）；契约 §6.2 同步；`RequestContextInterceptorTest` 加结构化命中（红跑实证：裸名字比较下该用例红）与他租户同名拒绝两用例、`OAuth2ResourcePathPropertiesTest` 加条目格式正负两用例。
- **claude P3-1（属实，事实性文档修正）**：方案 A 使「码无效+secret 也错」交叉情形错误码由 CLIENT_INVALID 变 CODE_INVALID（预读必须先于 secret 才能定租户，提案时已向用户披露的固有取舍）；任务卡「零漂移」表述就交叉情形不精确。处置：契约 §6.1.2 新增「校验顺序与错误码优先级」段固化口径，任务卡「当前口径」精确化。
- **claude P3-2 / codex 专项缺口（属实，两通道独立发现同一缺口，事实性修复）**：`OAuth2LoginLogTest` 密钥错误用例适配时漏接预读 GET stub，实际走「码无效」分支、secret 校验从未执行，secret-mismatch 失败审计失去单元锁。处置：补 `valueOperations.get` stub 返回码数据 + `failReason="client secret mismatch"` 与租户归属断言；红跑实证（无 stub 形态下新断言红）。
- **claude 存量观察（属实，并入同批种子残留清扫）**：`service-authentication.md` 两处引用 schema 中已删除的 `internal-service` 种子行——本批清扫此前已订正 tenant-lifecycle/architecture 同类残留，本文件漏网。处置：两处改为「grant_types 可选值零消费；历史种子行已随客户端不预置定案删除」现状口径。
- **通道间一致项**：token/refresh 匿名端点按错误码可探测「clientId 是否在任意租户注册」的存在性信号——两通道均定性为全局唯一期同形态存量、非本次变更引入或放大（Q-069 验收范围为注册面），不处置不立项。
- 过度设计可裁剪项：两通道均报「无」。

## 验收对照

- [x] 复合唯一索引与解析链改造。
- [x] 错误语义不泄露他租户存在性。
- [x] 契约、e2e、PgIT 回写与适配（全量回归收口形态 -T 1C 含 E2E/heavy BUILD SUCCESS，reactor 全模块 0 失败）。

## 非目标 / 遗留

不做存量库在线迁移（环境重建由部署方按重建规程执行）；不改 OAuth2 客户端注册的产品形态。
