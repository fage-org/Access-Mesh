---
doc_type: design
title: 公共服务认证模块（per-service credential）
status: adopted
domain: access-service
last_reviewed: 2026-09-19
---

# 公共服务认证模块（per-service credential）设计

> **状态口径**：`adopted`（2026-09-19 随 [dependency-auto-grant.md](dependency-auto-grant.md) 同批用户确认定稿，registry 同日行）。实现载体为 **T-PERM-070 前置任务卡**（原 T-PERM-035 已 cancelled，实现序列由 070~073 承接）。
>
> **定位**：机器对机器（M2M）服务身份认证是**多通道共用的平台能力**（资源同步通道、manifest 依赖声明通道、未来任何服务身份端点），不隶属单一特性——本稿从 dependency-auto-grant §9 升格拆出，含现有认证系统的完整盘点。

---

## 1. 目标与非目标

**目标**：为接入业务系统建立 per-service 身份认证——替代"全局共享密钥 + 自报服务码/租户头"的现行 M2M 信任模型，把单点失陷半径从"全部服务、全部租户"收窄到"单个服务"。

**非目标**：

- 用户侧认证零改动（Sa-Token 会话 / OAuth2 authorization_code+PKCE / JWT 维持现状）；
- 不做签名制（nonce / 验签 / 时钟容忍）——2026-09-19 重评定案：内网 TLS 下重放幂等 full-sync 无害、库泄露防护 BCrypt 哈希与公钥制等价；
- 不借道 OAuth2 `client_credentials`（**无发放实现**——token 端点仅 authorization_code；schema 中 `internal-service` 种子行的 `grant_types='client_credentials'` 与列注释为历史预留、未被任何发放路径消费。且 OAuth2 client 是"应用代用户"的另一套身份语义——权限注册是 control plane 的 M2M 语义）；
- 不改 20055 类型所有权门禁（它继续作为资源通道第二道防线）。

---

## 2. 现状盘点：认证体系全景（代码级锚点）

平台现有**三类信任主体 + 一层上下文绑定**，无任何 per-service 身份：

| # | 信任模型 | 主体 | 证据锚点 |
|---|---|---|---|
| ① | Sa-Token 会话（仅 Bearer 头，Cookie 通道已关） | 管理面/前端用户 | RequestContextInterceptor（USER 绑定：session tenantId+operatorId）；2026-09-08 Cookie 双向关闭定案 |
| ② | OAuth2 authorization_code + PKCE / refresh token + JWT HS256 | 外部应用代用户 | sys_oauth2_client.client_secret（BCrypt）；OAuth2JwtSupport（HS256 强度护栏）；**client_credentials 无发放实现**（schema L111 列注释与 L125 `internal-service` 种子行为历史预留、未被消费） |
| ③ | X-Internal-Secret 全局共享密钥 | 内网基础设施互信 | Spring 配置 `perm.internal-secret`（环境变量，**不落库**）；Gateway `InternalSecretFilter`（GlobalFilter，配置非空时**无条件向所有下游请求注入**）+ `PermissionClient` 直连带密；SDK `FeignInternalSyncInterceptor` 注入 `X-Internal-Secret`+`X-Service-Code`（不覆盖调用方显式声明） |
| ④ | SERVICE 上下文绑定（非独立认证） | ③通过后的自报身份 | 密钥验证（`InternalApiSecretInterceptor`，常量时间比对，失败 403；注册于 `SecurityWebMvcConfig.addInterceptors`，excludePathPatterns 精确豁免会话入口族）→ `X-Service-Code`/`X-Tenant-Id` **自报头**绑定（SignatureVerifier 仅数字解析、无签名）→ `AccessRequestContext.service(tenantId, serviceCode)` |

**现状问题（本模块要解决的）**：

```text
信任等式：持有全局密钥 ⇒ 可以任意服务身份、任意租户身份调用全部同步面
  ├─ 密钥分发现状：Gateway/内部设施/每个接入方（SDK 拦截器要求接入方自行配置该密钥）——
  │   FeignInternalSyncInterceptorTest 实证接入方配置即持有
  ├─ 自报头无签名：serviceCode/tenantId 均为调用方声明，唯一防线是 20055 类型所有权门禁
  │   （校验"该服务在该租户注册+类型归属"——依赖通道 batch-sync 连这道都没有，已核实）
  └─ 单点失陷=全体失陷：任一接入方镜像/CI 泄露密钥即全局失守，且无法按服务吊销
```

---

## 3. 目标模型：per-service 静态凭证（公共模块）

### 3.1 存储（`service_credential` 表，DDL 草案）

```sql
CREATE TABLE service_credential (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    service_code   VARCHAR(128) NOT NULL,   -- 凭证绑定服务（须为 service_config 已注册有效服务）
    credential_id  VARCHAR(64) NOT NULL,    -- 线上传输标识（非自增，签发时生成）
    secret_hash    VARCHAR(128) NOT NULL,   -- BCrypt 哈希（只存哈希；验证走常量时间 BCrypt 比对）
    status         SMALLINT NOT NULL DEFAULT 1,
    rotated_at     TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ
    -- 审计/软删列同全仓惯例
);
-- uk：credential_id WHERE delete_flag = 0（**全局唯一**——认证先于租户解析（tenant 由凭证行派生），
--     credential_id 必须跨租户唯一，否则两租户同 id 定位歧义=跨租户越权面；签发生成器保证+全局冲突检查）
-- idx：(tenant_id, service_code, status) WHERE delete_flag = 0（"该服务有效凭证"查询）
```

- 粒度 = **tenant + service**（service_config 按 (tenant_id, service_code) 注册，凭证绑定该行）；
- 同服务**多张有效凭证并存**（轮换平滑：新凭证验证生效后再停旧）。

### 3.2 认证链（服务端）

```text
请求头：X-Credential-Id + X-Credential-Secret（TLS 传输）
验证：
  ① credential 行定位（credential_id **全局唯一**（§3.1 uk），status 有效、未过期）
  ② BCrypt 常量时间比对 secret
  ③ 前置校验：凭证绑定的 service 在 service_config 注册且未停用（对齐 20055 门禁的服务注册段）
  ④ 绑定 SERVICE 上下文：tenantId + serviceCode 均由凭证行派生
     ——tenant+service 绑定是凭证自身的属性，不再自报 X-Service-Code / X-Tenant-Id（消除自报头信任面）
```

- 实现形态：**单一认证仲裁器**（重构 order=1 拦截器位为 ServiceAuthArbiter，内含双策略）——Spring MVC 拦截器链是 AND 语义（任一 preHandle=false 即终止链），"两拦截器并列注册、任一通过"**不可直接实现**。完整仲裁状态表：

  ```text
  credential 完整凭证头（自报头/密钥头并存时一律不采信）→ 凭证验证成功 → ServicePrincipal(CREDENTIAL)
  credential 半头 / 错误凭证                            → 403，禁止降级回落旧密钥
  无凭证头 + 旧密钥 + 已验签用户头                       → USER 绑定（既有用户链，行为不变）
  无凭证头 + 旧密钥 + 无用户头                           → 旧 SERVICE 绑定（attribute+自报头，过渡期）
  无凭证头 + 无密钥                                      → 403（既有行为）
  ```

- order 链输入输出（order=1 仲裁器 → order=2 HeaderSignatureInterceptor → order=3 RequestContextInterceptor）：order=1 产出 `ServicePrincipal(CREDENTIAL)` 或既有 `ATTR_INTERNAL_AUTHENTICATED`（旧密钥路径）；order=2 签名验证仅服务**用户链**（凭证请求无签名头，跳过不拒）；order=3 消费规则=**凭证路径只认 ServicePrincipal**（忽略 X-Service-Code/X-Tenant-Id 自报头），**用户/旧密钥路径维持既有绑定**（internalAuthenticated + signatureVerified + userId / 自报头）——不是"全部请求只消费 principal"（那会把管理请求错误绑定为 SERVICE，管理 API 大面积 403）。验证成功产出的 ServicePrincipal 为服务端内存对象，调用方无法伪造（负向回归锁=「凭证头 + 自报头并存时以凭证为准」）。
- **服务端端点白名单**：`authMethod=CREDENTIAL` 的请求在仲裁器之后强制执行"认证方式 × 精确路径"白名单（resource-entity/sync、full-sync、permission-manifest/full-sync），白名单外一律 403——**不依赖 Gateway 拦截，SDK 直连同样受限**（防直连调 /auth/query-resources、abstract-user/full-sync 等超范围端点扩大凭证能力半径）；白名单清单**单源落 `common` 模块**（Gateway 与 access-service 唯一共同依赖；不可变 method+exact-path 策略与匹配器——勿放 perm-common，Gateway 不依赖它），Gateway M2M 放行（§3.3）与服务端强制消费同一份，防两处漂移。
- TLS（**信任域模型**，过度设计重评裁剪 2026-09-19）：部署拓扑定义**信任边界**——跨信任边界的 hop（如接入方在客户内网、平台在云端的跨网形态）**必须 TLS**（starter endpoint / Gateway 路由 / SDK 直连三处配置校验 secure scheme，跨边界部署配 HTTP 拒启，护栏先例 ForwardHeadersStrategyGuard）；单机/纯内网 compose 部署**整体视为一个信任域**，显式 `allow-insecure` 配置键 + Spring profile 声明（与现状全局密钥同一内网信任假设，不新增内部证书签发/轮换运维负担）。`X-Credential-Secret` 为可重放 bearer secret（无签名/nonce），信任域边界即其明文暴露边界。

### 3.3 两类接入形态

| 形态 | 链路 | 要求 |
|---|---|---|
| 经 Gateway | 接入方 → Gateway → access-service | 三处配套：①凭证头加入 Gateway 透传/清洗策略（不被清洗；`InternalSecretFilter` 收窄为"无凭证头时兜底注入"）；②**M2M 放行链**——`AuthTokenFilter`（用户认证）对非白名单、无 Bearer 的请求**直接 401**，仅透传凭证到不了 access-service：新增早于用户认证的 M2M 识别——**完整凭证头 + 精确 M2M 路径清单**（resource-entity/sync、full-sync、permission-manifest/full-sync）→ 置 skipAuth 语义跳过用户认证与用户权限过滤，仅透传、由 access-service 仲裁器终验；**禁止把 /api/access/** 整体加入白名单**；③测试矩阵：经 Gateway 两步同步成功、缺头/半头/错凭证拒绝、管理端点不被凭证旁路 |
| SDK 直连 | 接入方 starter → access-service（Feign/Nacos） | SDK 凭证头注入拦截器（`FeignInternalSyncInterceptor` 扩展或并列新拦截器，落 perm-common 供 client/registration 两 starter 共用）；受 §3.2 **服务端凭证端点白名单**约束（直连不能越出 M2M 通道；负向测试=直连调 /auth/query-resources 等管理/查询端点拒绝） |

### 3.4 凭证生命周期（管理面）

- 签发：管理员在 **service-config 管理面扩展**（`/api/access/service-credential/create / update / remove / list`，全部 POST）；create 响应回传明文 secret **仅一次**（服务端只存哈希）；
- 轮换：签发新凭证（并存）→ 分发到业务系统 → 验证生效 → 停旧凭证；无缝切换；
- 停用/过期：立即失效（认证链 status/expires_at 判定）；
- 门禁：挂 service-config 管理面同族权限码（具体排号随任务卡落契约总册）。

### 3.5 分期与退役判据

```text
阶段一（本模块落地）：凭证=新增认证形态，资源同步通道与 manifest 通道均接受；
                      X-Internal-Secret 维持可用（Gateway/内部设施互信不受影响）
阶段二（端点逐个迁移）：白名单从阶段一端点集（resource-entity/sync、full-sync、
                        permission-manifest/full-sync）逐端点扩展至全部 sync 族
                        （abstract-user / abstract-role / user-role 的 sync+full-sync、
                        service-config/sync——主体/角色/成员/接口声明同步全部纳入凭证）；
                        退役判据=**仍依赖旧密钥的端点清零**（原「全部服务持凭证」判据在白名单
                        不含全部 sync 端点时永不可达）；SDK 拦截器默认注入凭证头
```

**上线序（服务端先行，向后兼容，替代"同批发布"旧口径）**：①先发布 access-service 仲裁器——无凭证头的存量调用方（Gateway 注入密钥、SDK 注入密钥+自报头）行为零变化（状态表第 3/4 行）；携带凭证头的请求直接走凭证链生效。②后发布 Gateway 改动（凭证头透传、`InternalSecretFilter` 收窄为「无凭证头才兜底注入」、M2M 放行链）与新版 SDK——窗口期"凭证头 + 注入密钥并存"由服务端仲裁器**凭证优先**规则消解（§3.2 状态表第 1 行），不存在"凭证绑定被旁路"的空窗，无需不可原子实现的同批发布。

---

## 4. 错误码与契约（占位）

凭证无效/过期/停用 → 403 + 新错误码（perm 段顺延排号，随任务卡登记契约总册）；`service_config` 停用服务 → 沿用既有拒绝口径。业务键构造唯一入口 `BusinessKeyUtil`。

---

## 5. 文档关系

| 事项 | 说明 |
|---|---|
| [dependency-auto-grant.md](dependency-auto-grant.md) §9 | 消费方：manifest 通道强制凭证认证（身份=凭证绑定 tenant+service，请求不收 serviceCode）；资源同步通道同时接受 |
| 任务结构 | 独立前置任务卡（T-PERM-035 拆卡时单列，先于 035A 交付） |
| 现状盘点证据 | 见 §2 锚点列（拦截器/过滤器/SDK 拦截器/OAuth2 grant 盘点均为 2026-09-19 代码级核实） |
