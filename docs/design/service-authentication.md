---
doc_type: design
title: 公共服务认证模块（per-service credential）
status: adopted
domain: access-service
last_reviewed: 2026-10-14
---

# 公共服务认证模块（per-service credential）设计

> **状态口径**：`adopted`（2026-09-19 同批定稿依据见[原定案全文](../archive/2026-09-20/auto-grant-prior-decisions.md)；自动授权部分取代关系见[自动授权当前设计](dependency-auto-grant.md)）。本公共模块由 **T-PERM-070** 实现并已完成；自动授权由 **T-PERM-078** 先细化协议，再沿 **T-PERM-071～073** 实施，完整前置依赖以[任务看板](../tasks/README.md)为准。
>
> **定位**：机器对机器（M2M）服务身份认证是**多通道共用的平台能力**（资源同步通道、manifest 依赖声明通道、未来任何服务身份端点），不隶属单一特性——本稿从 dependency-auto-grant §9 升格拆出，含现有认证系统的完整盘点。

---

## 1. 目标与非目标

**目标**：为接入业务系统建立 per-service 身份认证——替代"全局共享密钥 + 自报服务码/租户头"的现行 M2M 信任模型，把单点失陷半径从"全部服务、全部租户"收窄到"单个服务"。

**非目标**：

- 用户侧认证零改动（Sa-Token 会话 / OAuth2 authorization_code+PKCE / JWT 维持现状）；
- 不做签名制（nonce / 验签 / 时钟容忍）——2026-09-19 重评定案：采用受控信任域内 bearer secret + TLS；BCrypt 只保护库中哈希，明文 secret 泄露后可重构凭证权限范围内的查询/同步请求，并非仅幂等重放无害；轮换与停用见 [部署基线](../ops/deployment.md#11-密钥轮换)；
- 不借道 OAuth2 `client_credentials`（**无发放实现**——token 端点仅 authorization_code；grant_types 列含该可选值但零发放路径消费，历史预置的 `internal-service` 种子行已随「客户端不预置」定案删除。且 OAuth2 client 是"应用代用户"的另一套身份语义——权限注册是 control plane 的 M2M 语义）；
- 不改 20055 类型所有权门禁（它继续作为资源通道第二道防线）。

---

## 2. 认证体系与原共享密钥风险

> 本节保留 T-PERM-070 立项时的背景盘点，不代表当前尚无服务凭证；现行已交付协议见契约总册 §24，阶段与实施差异见 §3.5。

原共享密钥模型只有用户身份、平台密钥与自报 SERVICE 上下文，尚无 per-service 凭证：

| # | 信任模型 | 主体 | 证据锚点 |
|---|---|---|---|
| ① | Sa-Token 会话（仅 Bearer 头，Cookie 通道已关） | 管理面/前端用户 | RequestContextInterceptor（USER 绑定：session tenantId+operatorId）；2026-09-08 Cookie 双向关闭定案 |
| ② | OAuth2 authorization_code + PKCE / refresh token + JWT HS256 | 外部应用代用户 | sys_oauth2_client.client_secret（BCrypt；T-ACCESS-084 起 PUBLIC 客户端 secret=NULL、强制 S256，DDL CHECK 锁形态）；OAuth2JwtSupport（HS256 强度护栏）；**client_credentials 无发放实现**（grant_types 可选值零消费；历史 `internal-service` 种子行已随「客户端不预置」定案删除） |
| ③ | X-Internal-Secret 全局共享密钥 | 内网基础设施互信 | Spring 配置 `perm.internal-secret`（环境变量，**不落库**）；Gateway `InternalSecretFilter`（GlobalFilter，T-GW-014 起仅对**实际路由目的为 access-service**〔判据=路由 URI host〕且无凭证头的请求注入——路由到其他业务下游的用户请求不携带该头，路由属性缺失 fail-closed 不注入）+ `PermissionClient` 直连带密；业务 SDK 仅发送服务凭证，不再分发该平台密钥 |
| ④ | SERVICE 上下文绑定（非独立认证） | ③通过后的自报身份 | 密钥验证（`InternalApiSecretInterceptor`，常量时间比对，失败 403；注册于 `SecurityWebMvcConfig.addInterceptors`，excludePathPatterns 精确豁免会话入口族）→ `X-Service-Code`/`X-Tenant-Id` **自报头**绑定（SignatureVerifier 仅数字解析、无签名）→ `AccessRequestContext.service(tenantId, serviceCode)` |

**原模型风险（外部通道已由 §3.5 取代，真实旧密钥仍须轮换）**：

```text
信任等式：持有全局密钥 ⇒ 可以任意服务身份、任意租户身份调用全部同步面
  ├─ 密钥分发现状：Gateway/内部设施/每个接入方（SDK 拦截器要求接入方自行配置该密钥）——
  │   原 SDK 共享密钥配置使接入方持有（T-ACCESS-080 已退役）
  ├─ 自报头无签名：serviceCode/tenantId 均为调用方声明，唯一防线是 20055 类型所有权门禁
  │   （校验"该服务在该租户注册+类型归属"——依赖通道 batch-sync 连这道都没有，已核实；
  │   该通道后随 T-PERM-071 依赖 MANIFEST 独占写入整体退役，本节为 070 立项时现状）
  └─ 单点失陷=全体失陷：任一接入方镜像/CI 泄露密钥即全局失守，且无法按服务吊销
```

---

## 3. per-service 静态凭证（公共模块）

### 3.1 存储（`service_credential` 表）

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
验证（2026-09-20 实施拍板修订：secret 比对先于状态细分——三态细分仅对持有正确 secret 的请求者暴露，半头/错误 secret 一律 20065 无凭证状态探测面；[历史定案原文](../archive/2026-09-26/decision-registry-before.md) 同日行）：
  ① credential 行定位（credential_id **全局唯一**（§3.1 uk）；行不存在 → 20065）
  ② BCrypt 常量时间比对 secret（失败 → 20065）
  ③ 状态/过期细分：停用 → 20067；已过期 → 20066
  ④ 前置校验：凭证绑定的 service 在 service_config 注册且未停用（→ 20068；对齐 20055 门禁的服务注册段）
  ⑤ 绑定 SERVICE 上下文：tenantId + serviceCode 均由凭证行派生
     ——tenant+service 绑定是凭证自身的属性，不再自报 X-Service-Code / X-Tenant-Id（消除自报头信任面）
```

- 实现形态：**单一认证仲裁器**（重构 order=1 拦截器位为 ServiceAuthArbiter，内含双策略）——Spring MVC 拦截器链是 AND 语义（任一 preHandle=false 即终止链），"两拦截器并列注册、任一通过"**不可直接实现**。完整仲裁状态表：

  ```text
  credential 完整凭证头（自报头/密钥头并存时一律不采信）→ 凭证验证成功 → ServicePrincipal(CREDENTIAL)
  credential 半头 / 错误凭证                            → 403，禁止降级回落旧密钥
  无凭证头 + 旧密钥 + 已验签用户头                       → USER 绑定（既有用户链，行为不变）
  无凭证头 + 内部密钥 + 无用户头                         → 仅无自报服务身份的平台内部查询；同步及自报服务通道拒绝
  无凭证头 + 无密钥                                      → 403（既有行为）
  ```

- order 链输入输出（order=1 仲裁器 → order=2 HeaderSignatureInterceptor → order=3 RequestContextInterceptor）：order=1 产出 `ServicePrincipal(CREDENTIAL)` 或既有 `ATTR_INTERNAL_AUTHENTICATED`（旧密钥路径）；order=2 签名验证仅服务**用户链**（凭证请求无签名头，跳过不拒）；order=3 消费规则=**凭证路径只认 ServicePrincipal**（忽略 X-Service-Code/X-Tenant-Id 自报头），**用户链维持验签绑定**（internalAuthenticated + signatureVerified + userId）；无用户的平台内部查询不接受自报服务编码——不是"全部请求只消费 principal"（那会把管理请求错误绑定为 SERVICE，管理 API 大面积 403）。验证成功产出的 ServicePrincipal 为服务端内存对象，调用方无法伪造（负向回归锁=「凭证头 + 自报头并存时以凭证为准」）。
- **服务端端点白名单**：`authMethod=CREDENTIAL` 的请求在仲裁器之后强制执行"认证方式 × 精确路径"白名单（清单=M2mCredentialEndpoints 单源：四运行时查询+操作准入两端点+全部 sync 族，现登记见契约 §24），白名单外一律 403——**不依赖 Gateway 拦截，SDK 直连同样受限**（防直连调清单外管理端点扩大凭证能力半径）；白名单清单**单源落 `common` 模块**（不可变 method+exact-path 策略与匹配器；Gateway 现直接依赖 perm-common——旧「勿放 perm-common，Gateway 不依赖它」理由已随本批模块重接线失效，单源留 common 出于最小传递面：T-ACCESS-100 后 common 已无 Web/cache 实现传递面〔Web 栈 optional、缓存件 optional、零 META-INF 注册〕，纯 DTO 消费者引入 common 无装配副作用，2026-10-06 订正口径经 T-ACCESS-100 进一步收敛），Gateway M2M 放行（§3.3）与服务端强制消费同一份，防两处漂移。
- TLS（**信任域模型**；2026-09-20 实施拍板修订为**启动声明式护栏**——原「starter endpoint / Gateway 路由 / SDK 直连三处配置校验 secure scheme」假定的直配 URL 形态与实仓不符：三处均为 Nacos 服务发现形态、无静态 URL 可启动校验）：SDK 配置凭证（perm.credential-id/secret）时**必须显式声明 `perm.allow-insecure`**（三态：true=单信任域明文 hop 可接受 / false=跨边界期望 TLS，均为有效声明；**缺省拒启**）——护栏为纯声明不校验实际地址；Gateway→access-service 内网 hop 属平台信任域内部不校验（同部署单元，与现状全局密钥同一内网信任假设）。`X-Credential-Secret` 为可重放 bearer secret（无签名/nonce），信任域边界即其明文暴露边界。

### 3.3 两类接入形态

| 形态 | 链路 | 要求 |
|---|---|---|
| 经 Gateway | 接入方 → Gateway → access-service | 三处配套：①凭证头加入 Gateway 透传/清洗策略（不被清洗；`InternalSecretFilter` 收窄为"无凭证头且路由目的为 access-service 才注入"〔T-GW-014，取代此前"无凭证头即兜底注入"〕）；②**M2M 放行链**——`AuthTokenFilter`（用户认证）对非白名单、无 Bearer 的请求**直接 401**，仅透传凭证到不了 access-service：新增早于用户认证的 M2M 识别——**完整凭证头 + 精确 M2M 路径清单**（M2mCredentialEndpoints 单源清单）→ 置 skipAuth 语义跳过用户认证与用户权限过滤，仅透传、由 access-service 仲裁器终验；**禁止把 /api/access/** 整体加入白名单**；③测试矩阵：经 Gateway 两步同步成功、缺头/半头/错凭证拒绝、管理端点不被凭证旁路 |
| SDK 直连 | 接入方 starter → access-service（Feign/Nacos） | `FeignCredentialInterceptor`（perm-common，供 client/registration 两 starter 共用）；受 §3.2 **服务端凭证端点白名单**约束（直连不能越出 M2M 通道；负向测试=直连调 /service-credential/list 等管理端点拒绝）。T-ACCESS-098 两项收口：①路径匹配=真实路径按**完整值**与 M2M 清单匹配（不子串/不 indexOf；仅相对路径形态——feign 执行序拦截器先于 target 应用，绝对 URL 不可达拦截器），URL 其他位置（其他路径前缀、query 参数值）出现 /api/access/ 子串不注入；②装配边界=拦截器经 `PermFeignClientsConfiguration` 挂载到 PermissionFeignClient/PermissionManifestClient 各自的 **@FeignClient(configuration=...) 子上下文**——不得以 @Component/@Import 注册进宿主上下文（spring-cloud 按 beansOfTypeIncludingAncestors 收集，宿主 bean 会命中宿主全部 Feign 客户端）；**注入以目标接口为唯一判据**（外评 P2-1 处置：拦截器按 feign configKey〔接口简名#方法〕仅认 PermissionFeignClient/PermissionManifestClient——Feign 子上下文按 contextId 共享，宿主 FeignClientBuilder/同 contextId 客户端可进入权限客户端子上下文，contextId 隔离不足以兜底）；fail-fast（半配/缺信任域声明）时点随装配边界移至权限客户端 bean 创建（子上下文刷新，通常仍在启动期）。T-ACCESS-100 依赖传递边界（外部宿主零侵入）：SDK 传递闭包=common（瘦身后：jackson/commons-lang3/guava/validation/autoconfigure，Web 栈 optional、零日志实现、零 META-INF 装配注册）+ perm-common + spring-boot-starter + openfeign（client）/loadbalancer（registration）；`slf4j-api` 由 SDK 以 provided 显式声明、宿主供应；starter 不带任何 `spring-boot-starter-logging` 排除（排除项经 Maven 就近仲裁可反向剪掉宿主自选后端）。宿主四项形态不被改变：默认日志后端、JVM 默认时区、全局异常处理契约、Web 栈选型——回归锁=`sdk-host-probe` 模块四把锁（红跑证实旧边界下全失败），细则见 [工程规范 §5.5](project-rules.md#55-依赖传递边界common--权限-sdkt-access-100-收口) |

### 3.4 凭证生命周期（管理面）

- 签发：管理员在 **service-config 管理面扩展**（`/api/access/service-credential/create / update / remove / list`，全部 POST）；create 响应回传明文 secret **仅一次**（服务端只存哈希）；
- 轮换：签发新凭证（并存）→ 分发到业务系统 → 验证生效 → 停旧凭证；无缝切换；
- 停用/过期：立即失效（认证链 status/expires_at 判定）；
- 门禁：挂 service-config 管理面同族权限码（具体排号随任务卡落契约总册）。

### 3.5 外部凭证化与退役边界

外部业务服务的运行时查询与全部现役同步通道统一使用 per-service 凭证（T-ACCESS-079/080，2026-10-04）。服务端/Gateway/SDK 三方消费同一份 `M2mCredentialEndpoints`（common 单源，SDK 直调不再维护镜像清单——2026-10-06 逐任务评审清扫镜像措辞）；清单见契约 §24。无永久兼容开关。

运行时 `auth/check`、`batch-check`、`query-resources`、`query-scopes` 允许查询凭证所属租户的任意主体与资源。tenantId/serviceCode 由凭证派生，自报头不改变身份；被查询用户不是操作者，不赋予管理写权限，不新增每服务查询范围配置。查询继续复用现有引擎。

同步的 `sourceService` 必须匹配凭证身份；abstract-user/abstract-role/user-role 的 sync/full-sync 继续执行服务类型白名单、保留业务键和来源守卫。资源同步继续类型所有权门禁。`service-config/sync-v2` 保留管理员 SERVICE:SYNC_INTERFACE 门禁，凭证只能维护自身服务；旧 `/sync` 已退役。

`interface-admission`/`interface-admission-snapshot` 的凭证调用仍限定请求 serviceCode 等于凭证服务。Gateway 保留内部密钥的平台查询形态（无自报 X-Service-Code）。签名用户态保持原验证；无用户的同步请求和携带自报服务编码的旧共享密钥请求均拒绝。

示例服务采用共享实例、多租户独立凭证（T-ACCESS-081，2026-10-04 方案 B 确认，取代 T-ACCESS-079 的固定单租户安排）：按网关验签租户从 `example.permission.tenant-credentials` 选择凭证，通过 SDK 运行时查询显式头重载传入本次请求；无映射立即拒绝 30004，不回落全局固定凭证。配置非空时必须显式声明 `example.permission.allow-insecure`，含义沿 §3.2。异步任务按捕获的租户在执行时选择凭证并重新鉴权，不修改全局拦截器状态、不使用 ThreadLocal。映射由部署方维护，必须配对凭证真实租户；不新增签发/轮换框架或跨租户凭证。实现与验收由 [T-ACCESS-081](../archive/2026-10-04/tasks/T-ACCESS-081.md) 承载。

示例配置（实际 secret 由部署环境/Nacos 注入，不提交仓库）：

```yaml
example:
  permission:
    allow-insecure: true # 本地单信任域；跨边界填 false 并部署 TLS
    tenant-credentials:
      "1":
        credential-id: ${TENANT_1_CREDENTIAL_ID}
        credential-secret: ${TENANT_1_CREDENTIAL_SECRET}
      "7":
        credential-id: ${TENANT_7_CREDENTIAL_ID}
        credential-secret: ${TENANT_7_CREDENTIAL_SECRET}
```

映射不可变，配置缺少 secret/租户键非法/有映射而未声明信任域则启动失败；映射为空可以启动但拒绝全部业务最终检查。配置变更通过重启服务生效，不提供自动签发、存储、热刷新或轮换框架。SDK 的既有单 DTO 方法仍支持固定凭证调用方；新增重载只对 check/batch-check/query-resources/query-scopes 追加显式 `X-Credential-Id`/`X-Credential-Secret` 参数，HTTP JSON 请求体和端点不变。

发布边界：当前无部署且已确认无仓外旧调用方（2026-10-04），查询和剩余同步端点、SDK、示例同批切换，取代原阶段一兼容窗口。验收节点为端点与 SDK 接线、仓内外旧业务调用清零、旧纯服务通道关闭、部署时轮换平台内部密钥。轮换操作见 [部署指南](../ops/deployment.md)，未执行真实轮换前不声称旧密钥副本已失效；Gateway/内部设施继续使用内部互信密钥，不以全仓密钥使用清零为退出标准。

---

## 4. 错误码与契约（已登记）

凭证认证失败 403 错误码四枚（2026-09-20 拍板三码细分 + 服务停用码）：**20065** `SERVICE_CREDENTIAL_INVALID`（定位失败/secret 错误/半头/白名单外路径——仲裁器对白名单外完整凭证头统一落本码，T-ACCESS-053 实测确认）/ **20066** `SERVICE_CREDENTIAL_EXPIRED` / **20067** `SERVICE_CREDENTIAL_DISABLED` / **20068** `SERVICE_CREDENTIAL_SERVICE_INACTIVE`（凭证有效但绑定服务未注册/停用——「沿用既有拒绝口径」的仲裁器细分形态）。端点契约、仲裁状态表、M2M 白名单与 SDK 配置键全部登记于契约总册 §24（T-PERM-070 落地，实现语义以该章为准）。业务键构造唯一入口 `BusinessKeyUtil`。

---

## 5. 文档关系

| 事项 | 说明 |
|---|---|
| [dependency-auto-grant.md](dependency-auto-grant.md) §9 | 消费方：manifest 通道强制凭证认证（身份=凭证绑定 tenant+service，请求不收 serviceCode）；资源同步通道同时接受 |
| 任务结构 | 独立前置任务卡（T-PERM-035 拆卡时单列，先于 T-PERM-071 交付） |
| 现状盘点证据 | 见 §2 锚点列（拦截器/过滤器/SDK 拦截器/OAuth2 grant 盘点均为 2026-09-19 代码级核实） |

SDK 凭证注入已直接消费 common 的 M2mCredentialEndpoints（HTTP 方法 + 精确路径），不再维护第二份清单。perm-client 默认随类路径启用 Feign，Servlet 场景同时装配 GatewaySignatureFilter；非 Servlet 不装配过滤器。30003 由 SDK PermClientErrorCode 定义，保留原示例编号。配置与无服务发现接线见 [接入指南 §2.3](extension-guide.md#23-sdk-接线)。
