---
doc_type: design
title: AccessMesh 扩展指南（接入与二次开发全景）
status: adopted（2026-09-12 用户确认定稿；双通道外评两轮处置收口）
domain: common
design_refs:
  - docs/design/architecture.md
  - docs/design/access-service-api-contract.md
last_reviewed: 2026-10-06
---

# AccessMesh 扩展指南（接入与二次开发全景）

> 本文是面向**接入方与二次开发者**的场景驱动导引：业务服务如何接入鉴权、如何声明自有权限维度、用户/角色体系如何适配、管理台如何加页面。契约细节不在本文重复——每节指向唯一权威文档。
>
> 2026-09-12 T-FE-023 立项时用户定案：指南定位为**跨端全景**（本文件，落 design 根），取代原 design_refs 的 `frontend/extension-guide.md`（纯前端口径，已放弃）。

## 1. 扩展面总览

| # | 扩展面 | 回答的场景提问 | 权威契约 | 验证资产 |
|---|---|---|---|---|
| 1 | 业务服务接入 | 「我的服务怎么接入 AccessMesh 鉴权？」 | api-contract §19.8；`services/example-service.md` | `ExampleProtectedApiE2EIT` |
| 2 | 自有资源类型 | 「我要按门店/项目/单据控制权限，怎么建模？」 | api-contract §13/§19.2/§19.1/§19.8；类型所有权声明见 api-contract §13 | `CustomResourceTypeSlicePgIT`（本指南配套） |
| 3 | 主体/角色体系 | 「我的用户/角色体系与标准设计不一致」 | api-contract §19.4 + §19.9（syncTypes 白名单） | AbstractUserSyncAppServiceTest 等单测组 |
| 4 | 条件与范围 | 「时间/IP 限制、数据范围怎么配？边界在哪？」 | api-contract §5.6/§6.7；core-flows | 条件双轨制用例组（T-PERM-048） |
| 5 | 前端页面 | 「我想在管理台加自己的页面」 | `docs/design/frontend/README.md` 及各页面设计 | 前端 view hook 测试组 |

**架构原则**：AccessMesh 的扩展模型是**数据声明式**，不是代码插件式。除前端页面外，所有扩展通过「声明类型 + 同步数据 + 配置授权」完成，不要求接入方编写平台内代码。无权限判定 SPI 插槽——判定语义由统一引擎（`QueryGate`/`QueryExecutionEngine`）唯一承载（见 §6 能力边界）。

**开始之前（全部场景的最小前置）**：

1. **环境初始化**：空库按 `docs/design/access-service-rebuild-runbook.md` 重建数据库，并以 `ACCESS_BOOTSTRAP_ENABLED=true` + `ACCESS_BOOTSTRAP_ADMIN_PASSWORD` 启动 access-service——自动种子首管理员（`admin`，tenantId=1）与管理用功能角色；未启用则空库无管理员，下述管理链全部 401。既有库固定图升级须按 runbook 重建。
2. **管理 API 均需管理员会话身份**并过对应门禁：如 service-config 写操作=SERVICE:MANAGE、type-definition/create=TYPE_DEFINITION:CREATE、apply-grant-plan=ROLE:MANAGE。授权写入口**不收服务身份**（服务身份无操作者，一律 403）。
3. **授权页入口**在角色管理页「权限授予/查看权限」入口按钮（文案按 ROLE:MANAGE 二分，T-FE-055；授权路由不在侧栏单独暴露）。
4. **被授权主体**：场景二第 ⑥ 步判定需要一个有角色的用户——可经管理台组织与用户页创建用户并挂角色，或把权限授给既有功能角色（`BASIC_ROLE` **类型**下的 `bootstrap-admin` 等角色）再绑用户。

## 2. 场景一：业务服务接入接口鉴权（example 模式）

完整活例见 `docs/design/services/example-service.md` 与 E2E 测试 `ExampleProtectedApiE2EIT`。

**接入主线一览**（T-ACCESS-053，每环节均可在上表验证资产中找到自动化证据。③服务身份不占 §2.1 步骤序号——管理台接入链不需要它；§2.1 步 5 防直调为服务侧加固项，不属主线环节）：

```text
① 注册服务（service-config/save）
② 声明接口（service-config/sync-v2 FULL——登记 API、路由映射与业务操作要求）
③ 服务身份（§2.2：查询与同步统一使用 per-service 凭证）
④ 授予业务权限（§2.1 步 3；网关操作准入与业务最终检查见 §2.4）
⑤ 实际调用（§2.1 步 4；业务前端持会话令牌经 Gateway 访问；未授权 403 → 授权后 30 秒内 200）
⑥ 撤销与恢复（§2.1 步 6：删除授权行 30 秒内回 403，重授恢复——撤权与授权同窗口）
```

### 2.1 接入步骤

1. **注册服务**：管理台「服务+接口映射」页（`POST /api/access/service-config/save`）登记 `serviceCode`/`name`/`status=1`。
2. **声明接口**：`POST /api/access/service-config/sync-v2`（FULL 模式）上报接口清单，每个 ApiItem 必填 `requiredPermission`（如 REPORT:VIEW，业务类型及操作须先存在）——一步创建 **API 资源**与 **Gateway 路由映射**（`pathPattern = basePath + path`，行归属标记 `maintainSource=SERVICE_SYNC`）。API 类型由系统种子声明 SYNC+access-service（T-PERM-069），本通道与 bootstrap 固定图即唯一事实入口——**不要**走 `resource-entity/sync` 通道（外部来源不匹配，会被 `RESOURCE_TYPE_OWNERSHIP_DENIED` 拒绝；资源管理面手工 CRUD 亦 20055）。
3. **授权**：经管理台授权页对目标角色授予路由要求对应的业务资源权限（例如 REPORT 实例的 VIEW）。API 类型仅用于接口登记，不单独授权。授权写入口仍须用户身份与 ROLE:MANAGE，不收服务身份。
4. **请求链路**：业务前端持平台会话令牌（`Authorization: Bearer <token>`，sa-token）经 **Gateway (8080)** 访问业务接口；Gateway 做**操作准入快照本地判定**（T-ACCESS-059：路由要求→候选分支，条件不可本地评估回源 `interface-admission` 在线判定）并对可下发条件做本地重评。准入 MAY_ENTER 不等于允许——业务服务必须以实际目标做完整实例鉴权；授权生效受 Gateway 快照刷新窗口约束（上界 30s）。
5. **服务侧防直调**：Servlet 业务服务引入 perm-client starter 即自动装配 GatewaySignatureFilter；携带用户/租户身份头而签名缺失、错误、超窗或密钥未配置时拒绝（HTTP 200 + 30003）。无身份头的请求仍由业务入口身份检查与实际对象鉴权拒绝；验签不替代这些检查或网络隔离。
6. **撤销与恢复**（T-ACCESS-053 补全，与授权同源）：撤销=授权页删除该条授权行（唯一删除语义 `apply-grant-plan` 的 `removes` 段；旧 `revoke` 端点已物理删除）——撤权生效受与授权相同的 30 秒陈旧窗口约束，窗口内接口回到 403；恢复=对同一业务资源重授对应操作（撤销为软删，重授即新建行），同样 30 秒内生效。回归锁：`ExampleProtectedApiE2EIT` 第⑧步（撤销→403→重授→200）。

### 2.2 服务身份：业务凭证与平台内部互信

外部业务服务的运行时查询与同步接口统一使用 `X-Credential-Id` + `X-Credential-Secret`。管理员先注册服务，再经 `POST /api/access/service-credential/create` 签发；明文 secret 只回显一次。凭证绑定租户和服务，服务端忽略自报租户/服务头；停用、过期或服务停用立即拒绝。精确端点与错误码见[契约 §24](access-service-api-contract.md)。凭证不开放管理写能力，同步 `sourceService` 必须匹配凭证所属服务，原类型/来源守卫继续生效。

`PERM_INTERNAL_SECRET` 仅在 Gateway/access-service 等平台内部设施之间分发。业务 SDK 已移除旧共享密钥注入器，旧纯服务同步及自报服务查询通道拒绝；签名用户态与 Gateway 内部查询保持。部署时的内部密钥轮换见[部署指南](../ops/deployment.md)。

### 2.3 SDK 接线

- `perm-client-spring-boot-starter` 通过 `PermissionFeignClient` 调用远程查询/同步；`FeignCredentialInterceptor` 仅对精确 M2M 清单注入凭证。配置 `perm.credential-id`、`perm.credential-secret` 和 `perm.allow-insecure`（true=单信任域明文 hop 可接受；false=跨边界要求 TLS，须由部署保障）。无需配置全局内部密钥。
- example-service 共享多租户部署：按可信请求租户从 `example.permission.tenant-credentials` 选择独立凭证，通过运行时查询的显式头重载传递；没有配置的租户拒绝，不回落全局固定凭证。非空映射须声明 `example.permission.allow-insecure`；异步导出也按捕获租户重新选择。配置示例见服务认证 §3.5。
- Gateway 仍负责接口操作准入，业务服务负责实际对象最终检查。非 Java 服务可直接按契约发送凭证头，不必使用 SDK；管理面 Feign 方法仍需另行提供有效用户身份，服务凭证不能替代。
- Gateway 的准入过滤器在 gateway 服务中维护；空壳 perm-gateway starter 已删除。可选 registration starter 只负责依赖发布。
- SDK 默认操作码为 VIEW/UPDATE/DELETE，原 EDIT 已退役。旧 PermContext/PermCheckReq/PermCheckResp 无消费者，已删除；运行时请求使用当前 AuthCheckReq/BatchAuthCheckReq 等 DTO。

`perm.client.enabled` 默认 true：类路径引入 starter 即启用 Feign 客户端，Servlet 应用同时获得验签过滤器；设 false 会同时关闭这两部分，不能把关闭开关当作保留验签的方式。非 Servlet 应用不注册 Servlet 过滤器。签名配置为 `perm.client.signature.secret`（缺省从 ACCESSMESH_SIGNATURE_SECRET 获取，必须与 Gateway 同源）和 `perm.client.signature.valid-seconds`（默认 300，验签时钟偏差窗口）；原 example.signature 配置名随收编退役。

默认 `PermissionFeignClient` 使用服务发现名 access-service。启用发现模式时，消费方需有 loadbalancer 与可用发现客户端（本仓为 Nacos），并配置相同注册中心/命名空间；starter 不私自为接入方指定注册中心。没有服务发现的环境直接使用 Spring Cloud OpenFeign URL 配置：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          access-service:
            url: https://access.internal.example
perm:
  client:
    signature:
      secret: ${ACCESSMESH_SIGNATURE_SECRET}
  credential-id: ${SERVICE_CREDENTIAL_ID}
  credential-secret: ${SERVICE_CREDENTIAL_SECRET}
  allow-insecure: false
```

URL 指向服务根地址，不附加 /api/access（方法映射已包含完整路径）。此方式不需要 Nacos/loadbalancer；Servlet SDK 自动装配测试同时验证直连 URL 可创建 Feign 客户端。凭证只自动注入 common 的 M2mCredentialEndpoints 方法+路径精确清单，服务端、Gateway 与 SDK 同源。perm-common 对 common/OpenFeign 使用 provided 编译依赖，两个 SDK starter 已提供实际运行依赖；仅引用 DTO 的程序无需因此引入服务端 Web/cache 实现。

### 2.4 接口权限与业务权限是两层（易混点）

接入方最常问的「我给角色授了业务资源权限，为什么调接口还是 403」——**同一份业务授权，两层判定职责不同**：

| 层 | 判定者 | 权限对象 | 效果 |
|---|---|---|---|
| 接口层 | Gateway（**操作准入快照本地判定**，T-ACCESS-059；条件不可本地评估回源 `interface-admission`） | 路由声明的业务操作（requiredPermission，如 `REPORT:VIEW`） | 请求能否**过网关到达业务服务**——无覆盖候选一律 403；API:ACCESS 不再参与（062 退役面） |
| 业务层 | 业务服务自己（调 `auth/check` 查询后按结果分支） | 业务资源类型的自有操作（如 `REPORT:VIEW`，见 §3 建模） | 业务服务**收到请求后**如何处理——两层是先后关系不是替代关系 |

接口准入已按方案 A 交付：路由绑定业务操作，网关从业务授权寻找准入候选，业务服务继续对实际目标做最终鉴权；不生成 API:ACCESS 授权，旧协议已随 T-ACCESS-062 退役。业务侧按 scopeMode 动态生成 SQL 数据过滤仍归 T-PERM-036（暂缓），不能由接口准入替代。

### 2.5 失败形态判别

调用方依次检查 **HTTP 状态 → 信封 code → 业务结果**，不能把 HTTP 200 或 `code=200` 单独当作授权/同步成功。以下是现行行为，分类状态码保持 [工程规范 §3.3](project-rules.md#33-全局-exceptionhandler-处理顺序) 的既定口径。

| HTTP / 响应 | 发生层与含义 | 接入方处置 |
|---|---|---|
| 200，code 为业务错误码 | `BizException`；业务规则、对象或权限拒绝。例如 example `30004` 表示业务最终检查未通过，`30003` 表示身份签名无效 | 读取 code/message/requestId；按目标资源、角色与参数排查，不盲目重试或放行 |
| 200，code 为系统错误码 | 显式 `SystemException`；不是成功，消息统一为系统异常 | 保留 requestId，按依赖故障排障；写请求按幂等契约重试 |
| 400，code=90001 或 400 | Bean Validation/请求 JSON/结构参数校验失败 | 修正输入。字符串 extra 中的非法 JSON 等业务校验可能是上一行 200+业务码，须按具体契约判断 |
| 403，code=403 | 服务层 `SecurityException` 或认证仲裁的身份/白名单拒绝 | 核对服务凭证范围、操作者和管理权限；不通过重试绕过 |
| 500，code=99999 | 未捕获异常的兜底 | 按 requestId 联系平台排障，不能解释为“无权限” |
| Gateway 401 / 403 | 401=平台会话无效；权限过滤器 403=接口准入拒绝，请求尚未进入业务服务 | 401 重新登录；403 先查服务接口映射及类型/操作候选授权。若 403 **无 JSON 信封**，先按部署基线查 CORS |
| Gateway 502 / 503 | 上游或鉴权依赖不可用、配置故障；失败关闭 | 先恢复依赖或修正映射；不能当作明确业务拒绝，也不能回退放行 |
| 200，code=200，data.allowed=false | 权限查询成功执行，但检查结果拒绝 | 根据 reason 排查条件、互斥、角色或父上下文；业务动作不得执行 |
| 200，code=200，data.accepted=false | sync/full-sync 请求已完成分类，但同步事实未被接受，可能为 SECURITY_DENIED/RESOURCE_TYPE_OWNERSHIP_DENIED 等 | 继续查看 retryClass/reason；FULL 检查 itemResults，遵守 [同步重试表](../ops/runbook-full-sync.md#4-响应分类与重试决策表) |
| 200，code=200，data.accepted=true、stale=true、applied=false | 同步旧版本被幂等接纳但未应用；不是再次写成功 | 不重放旧事件，核对源版本；区分“接纳”“应用”“清理”三个结果 |

同一使用场景的三种拒绝：用户没有接口所需候选授权，停在 Gateway 真 403；已有类型/操作候选却对 `report-2` 没有实例权限，example 返回 200+30004；服务凭证有效但向不属于自己维护的资源类型发送同步，返回 200+200+accepted=false。前两者是用户操作路径，第三者是服务同步路径，不能共用“HTTP 200 就成功”的分支。

监控至少分别统计传输失败、非成功信封和业务结果拒绝。记录 requestId、端点、错误码与原因；不记录 Authorization、服务凭证 secret 或完整敏感请求体。

## 3. 场景二：自有资源类型（自定义数据权限维度）

「按门店/按项目/按单据控制权限」的答案：**声明自有资源类型 + 同步资源实例 + 授权**。配套回归锁：`access-service` 容器测试 `CustomResourceTypeSlicePgIT`（声明→操作定义→服务身份同步→授权→引擎判定的完整链路）。

### 3.1 第一步：选择所有权模式

每个 resource_type 单一所有权，声明于 `type_definition.extra`：

| 模式 | 语义 | 适用 |
|---|---|---|
| `MANAGED`（缺省） | 管理台手工 CRUD 维护资源 | 资源量小、人工维护（如自定义目录） |
| `SYNC` | 声明来源服务（`syncSourceService`）独占同步，管理面只读（写操作 20055） | 资源事实在业务系统里（订单、门店、项目） |

声明约束（api-contract §13 类型所有权声明段 + §19.1 同步入口门禁）：SYNC 来源必须为已注册、未软删、`status=1` 的服务；类型下存在有效资源行时声明不可变更（20056），**系统预置类型（is_system=true）所有权声明一律钉死不可变更**（20056）；API 类型禁止经类型定义接口声明 SYNC——所有权由 DDL 种子钉死 SYNC+access-service（T-PERM-069），唯一事实入口=service-config 接口声明通道+bootstrap 固定图（管理面资源 CRUD 20055）。

### 3.2 完整链路（六步，SYNC 模式）

以下六步是 **SYNC 模式**（资源事实在业务系统，§3.1 表右行）的链路。**MANAGED 模式（缺省）不走此链路**：其写入口是管理面资源 CRUD（`POST /api/access/resource-entity/create|batch-create|update|move|remove`，管理台「资源+操作定义」页）——**禁止**走 `resource-entity/sync|full-sync`（类型非 SYNC 一律 `RESOURCE_TYPE_OWNERSHIP_DENIED`，且信封 `code=200` 但 `accepted=false`，只看 HTTP 状态会误判成功）；两模式互斥方向由 20055/所有权门禁双向焊死。

```text
① 注册服务（service-config/save，status=1）
② 声明类型（type-definition/create：typeKey=resource_type + typeCode=自有码
   + extra={"managedMode":"SYNC","syncSourceService":"<服务码>"}）
   ——创建即自动预置 CRUD 四操作（CREATE/VIEW/UPDATE/DELETE，位 1/2/4/8）
③ 按需追加自定义操作（operation-permission/create：resourceTypeCode=自有码 + code
   + binaryBit——操作位空间按类型隔离，binaryBit 类型内唯一且须为正数单比特（2^0..2^62，共 63 位），避开预置位）
④ 同步资源（resource-entity/sync 单条 UPSERT/DISABLE/DELETE 或 full-sync 全量 diff；
   服务身份请求头见 §2.2；DISABLE 为幂等停用，非删除）
⑤ 授权（管理台授权页 apply-grant-plan，或 API：roleTypeCode+roleExternalId
   + key{resourceTypeCode, resourceCode, codeType, operationCode, scopeMode}）
⑥ 判定（业务侧 auth/check：subjectTypeCode + subjectExternalId + resourceTypeCode=自有码
   + resourceCode + operationCode → allowed/reason；主体类型须匹配——本地登录用户是
   LOCAL_USER（user_type=3），USER（=1）是族内另一类型，外部同步主体用自有 subject_type）
```

### 3.3 门禁与错误码速查

| 错误 | 触发面 | 含义 |
|---|---|---|
| `RESOURCE_TYPE_OWNERSHIP_DENIED` | sync/full-sync 入口 | 类型非 SYNC / 来源不匹配 / 来源服务未注册或停用 |
| `20055 RESOURCE_EXTERNALLY_MAINTAINED` | 管理面 create/update/move/remove | SYNC 类型管理面只读 |
| `20056 TYPE_OWNERSHIP_CHANGE_CONFLICT` | 声明变更/类型删除 | 类型下有有效资源行；系统预置类型（is_system）声明一律钉死 |
| `20040 GRANT_CANNOT_DELEGATE` | apply-grant-plan | 授予者未持有覆盖目标键的可转授权限（新类型首笔授权见 §3.5） |
| `PUBLICATION_GENERATION_STALE/CONFLICT` | 资源发布/manifest FULL | 旧代次拒绝或同代次内容冲突；按契约 §19.2.1 保留快照重试或重新采集发布 |
| `20069 OPERATION_REFERENCED_BY_GRANTS` | 操作位变更/删除 | 操作被授权或接口准入映射引用，先清理引用；不可通过改位绕开现有授权 |
| `20005 / 20044` | 操作码解析/清单校验 | 未知操作码 fail-closed / 畸形清单零副作用 |

### 3.4 资源树与父子关系

资源父子边限同类型（T-PERM-068，2026-09-17 Q-007 定案）：sync/full-sync 的 `parentResourceTypeCode` 缺省按 item/scope 自身类型解析、显式异类型被拒（`NON_RETRYABLE`/`PARENT_TYPE_MISMATCH`）；管理面 create/batch-create/move 同口径（20053）；角色域 ORG/POSITION 容器树的结构性跨类型是另一域形态、与资源域无关。SYNC 类型资源出现在管理面资源树（读路径不受限），授权页按类型出矩阵。两个易混概念：**自动授权（依赖补全）**使用 MANIFEST 声明与编译图，物化已随 072 落地（旧 autoGrant 开关已退役，见 §6）；**`depend_on` 子权限**（授权行挂主权限的子权限机制）是在役能力——写侧经 apply-grant-plan 的 `parentPermissionId`/`children` 声明，判定时必须提供真实父上下文 `parentResourceTypeCode/parentResourceCode/parentCodeType/parentOperationCodes`，由引擎确认父权限实际通过且命中相同父行；未传父上下文的子行不计入。契约见 §18.1/§18.6，不能把子行的存在直接当成可用权限。

### 3.5 首笔授权引导（创建即建授权根，T-PERM-062）

授权委托校验（`checkCanGrant`）**严格无旁路**：授予者必须已持有覆盖目标键且 `canGrant=true`、无条件的授权行。bootstrap 固定图只覆盖**种子类型**——若无生命周期钩子，一个全新自定义类型在创建后**没有任何人能经 apply-grant-plan 完成首笔授权**（一律 20040 `GRANT_CANNOT_DELEGATE`）。

**T-PERM-062（2026-09-12 定案）后该缺口在产品内自举闭环**，接入方按 §3.2 走完「创建类型 → 追加操作 → 授权页」即可首授，**无需部署方种子或手工 SQL**：

- **创建即建基座**：`type-definition/create` 同事务向「类型所有者角色」写 CRUD 四操作位首授行（`grant_source=AUTHORITY_ROOT`，类型级 scopeAll + 可转授）；所有者缺省引导角色 `bootstrap-admin`，可经请求字段 `ownerRoleTypeCode/ownerRoleExternalId` 指定（roleTypeCode 仅接受 BASIC_ROLE 功能角色；类型定义页「所有者角色」选择器同入口），指针持久化于 `type_definition.extra.grantOriginRole`。
- **追加操作自动补种**：后续经 `operation-permission/create` 追加的操作（如 `EXPORT` 位 16）同事务向同一所有者补种——不会出现「CRUD 能授、EXPORT 仍 20040」。请求的 `inheritMask` 可省略（服务端归一为 0，与显式 0 等价，T-PERM-077）。
- **所有者可迁移**：`type-definition/update` 变更 `extra.grantOriginRole` = 同事务「先清后种」迁移（旧所有者种子清理、新所有者补齐全部操作位）；所有者角色受删除守卫保护：最终删除集命中显式或缺省所有者时整批拒绝 20073；需先迁移所有者，再删除旧角色。种子行在授权页只读（20061），类型删除时级联清理。**注意 extra 为整串替换语义**：经 API 迁移时提交的 extra 必须保留现有 `managedMode`/`syncSourceService` 声明键——只提交 `grantOriginRole` 等于删掉所有权声明（类型下有资源行时 20056 拒绝、无资源行时隐式切回 MANAGED）；未携带 `grantOriginRole` 键则保留现值。管理台「所有者角色」选择器自动 merge 进现有 extra，无此风险。
- **可发现性**：所有者的成员在授权页可见这些种子行（标注「授权根」），并可**向其他角色转授时**收窄为实例级授权（种子行本身只读 20061，不可就地改删）。20040 的 reason 分两种：非所有者成员对**已有授权根**的类型发起授权 → `NO_PERMISSION`/`NO_GRANT_RIGHT`（你的持有面不够——找所有者角色成员操作或加入该角色）；`TYPE_GRANT_ORIGIN_MISSING` 仅出现在**自定义类型且租户内零条可转授行**时（种子行被直改库清除——产品链路内种子不可销毁，正常运维不应出现；去类型定义页确认/重指所有者）。产品删除入口以 20073 保护所有者；只有绕过产品的直接改库才可能破坏引用，此类恢复须先核对类型指针和授权事实，再重指所有者。

回归锁：`CustomResourceTypeSlicePgIT` 全链路固化——创建即落 4 条种子、追加操作补种第 5 条、管理员直接首授成功（原「部署方种子后放行」步骤已随修复退役）、非所有者仍 20040、种子行改删 20061、所有者迁移清理+补齐、零授权根时 reason=TYPE_GRANT_ORIGIN_MISSING。

## 4. 场景三：主体/角色体系适配

「接入方用户体系与标准设计不一致」的两条路：

1. **本地主体**：平台自管用户（管理台组织与用户页创建，`LOCAL_USER`）；适合接入方把账号体系交给 AccessMesh。
2. **自有主体类型 + 用户同步通道**：接入方在 `service_config.extra.syncTypes.subjectTypeCodes` 白名单声明自有 subject_type，经 `POST /api/access/abstract-user/sync` 同步主体（同 §2.2 服务身份）。角色同理：自有 role_type + `POST /api/access/abstract-role/sync`（`roleTypeCodes` 白名单）。

边界：内置事实链路类型（USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION 等）已声明为 access-service 内部 SYNC——**外部同步一律拒绝**，需要差异化建模时请声明自有类型（如 `BI_MENU`），不要试图写公共类型。

## 5. 场景四：条件与范围权限

### 5.1 条件（时间/IP 类环境断言）

- **封闭操作符集**（填条件时的 `type` 代码名）：`DATE_RANGE`（日期区间）、`TIME_RANGE`（时段）、`IP_WHITELIST`（IP 白名单）、`IP_BLACKLIST`（IP 黑名单），共 4 类。**不可自定义条件操作符**（求值器 `ConditionEvalUtils` 为 perm-common 静态实现，Gateway 与 access-service 共享同语义；无扩展插槽；填白名单外的类型名评估 fail-closed 恒拒绝）。
- 主权限挂条件时不可转授（`canGrant=false`，违反返回 20041）；子权限不能独立挂条件或设置可转授（20043），其生效依赖真实父上下文的判定。条件允许集只描述可求值规则，不授予角色、资源或操作权限。
- **双轨制**（T-PERM-048）：管理页条件（source=MANAGED，独立 CRUD、可复用）vs 授权 INLINE 内联条件（随授权记录声明，仅 source=INLINE）。
- 时钟语义：`evaluatedAt` 由引擎统一注入（跨进程一致性靠 NTP，业务粒度按天/小时）。

### 5.2 范围权限（scopeMode 四态）

资源范围判定四态（契约见 api-contract §6.7）：`INSTANCE`（单实例）/ `ALL`（类型全量）/ `DENIED`（无操作权限）/ `EMPTY`（有权限但条件/互斥过滤后无数据——返回空结果不发 SQL）。注意**授权配置侧只用 `INSTANCE`/`ALL` 二态**（apply-grant-plan 的 scopeMode），`DENIED`/`EMPTY` 是查询响应侧的判定结果。

### 5.3 特殊判定逻辑（外部审批等）怎么落地

无代码级判定插槽（§6）。推荐路径：审批流转在接入方系统内完成后，由**持有 ROLE:MANAGE 且对目标键有可转授覆盖的用户身份**（管理台会话）经授权写入口（apply-grant-plan）落授权——权限生效路径与人工授权完全一致，可审计、可回收。授权写入口不收服务身份（服务身份无操作者，403；服务身份适用 §2.2 查询类 API 与 §3.2 第 ④ 步同步写通道）。

> **独立依赖接入**：[简化设计](dependency-auto-grant.md)保留独立资源 sync/按类型 full-sync；纯资源接入无需 manifest 或 registration starter。有依赖需求才独立发布 manifest，SDK 协调可选，不强制大清单。registration 的配置与调用见契约 §19.10.1 和 [example-service 示例](../../example-service/examples/permission-manifest.md)；角色物化（072）与来源解释/预览/对账（073）均已交付——不把自动授权与 API 派生混为一项能力。

## 6. 能力边界（不可扩展项清单）

| 项 | 状态 | 说明 |
|---|---|---|
| 条件操作符扩展 | **封闭** | 仅 4 类内置操作符，无 SPI |
| 权限源扩展 | **不存在** | 统一引擎唯一事实源（role_perm_entry + 同事务投影）；历史上设想的「权限源策略」已随 T-PERM-057 统一引擎重构收编 |
| 自动授权（依赖补全） | **已交付** | 凭证（070）、声明编译/资源共序/SDK/迁移（071）、角色物化（072）、来源解释/授撤预览/声明诊断/对账（073）均收口（2026-09-21）；旧写入口和 autoGrant 已退役。管理员视角：授权页「预览影响」看授撤连带影响（仅供参考）、依赖页查声明状态与角色来源 DAG、任务管理页按需触发自动授权对账（种子默认停用） |
| 动态数据权限端到端 | **延后** | T-PERM-036 延后至 example-service 演示；scopeMode→SQL 映射契约已定（api-contract §6.7） |
| 内置事实链路类型写入 | **禁止** | USER/ORG/MENU/ROLE 等归 access-service 内部 SYNC，外部同步一律拒（§4） |
| 异常告警通知渠道 | **不做** | 异步异常可观测性由 log.error 承载；钉钉/邮件等告警渠道不在开源 IAM 核心范围（T-PERM-038 定性），接入方经日志采集侧自行对接 |
| resource-dependency 管理写入 | **已关闭** | 管理页只读；所属服务经独立 manifest 发布，旧 create/update/remove/batch-sync 不再提供 |

## 7. 场景五：前端页面扩展（管理台二开）

管理台基于 pure-admin-thin（Vue 3 + Element Plus）。新增一个管理页面的标准模式（以现有 13 页为活例）：

1. **权限串声明**：`src/views/system/<page>/utils/perms.ts` 导出 `<PAGE>_PERM_LIST`（操作码常量，对齐后端 `OperationCode`，engine.constant 唯一常量源）。
2. **路由注册**：`src/router/modules/*.ts` 路由项 `meta` 引用 PERM_LIST（按钮级 `auths` / 页面级门禁）。注意侧栏菜单已切后端派生（T-FE-015）：**可见性按菜单形态派生（见下条），`meta.showLink` 不再控制侧栏**。
3. **菜单种子**：sys_menu 行，三条通道按场景选：① bootstrap 固定图种子（平台内置页与默认树，`BootstrapGraphDefinition.menuSeeds()`——固定图**版本升级**须按 rebuild-runbook 重建库，与单加一条菜单无关）；② 后端管理 API `POST /api/access/menu/create`（**已实现**，runbook 记载直连 access-service 的造数用法；注意 Gateway 固定图未注册 `/api/access/menu/**` 路由——经 Gateway 调用会 403，须直连（T-ACCESS-042 起直连 `/api/access/**` 须携带 `X-Internal-Secret` 头，见 rebuild-runbook））；③ 管理台菜单管理**页面**尚未开发（前端无 menu 页），二开者当前走 ①/②。菜单可见性按形态分三支：**挂接资源的业务菜单** = 用户对该资源持有任一有效操作权限（类型级 scopeAll 或实例授权，∃op 派生），无持有面 fail-closed 不可见（自定义页要按权隐藏必须挂资源类型）；**纯展示菜单**（resource_type 为空）= 全员可见；**目录 DIR** = 恒候选（有可见子节点才渲染）。菜单行不单独授 MENU 码、无需逐菜单授权。
4. **API 层**：`src/api/<page>.ts`——全部 POST + JSON Request DTO（禁 GET/RESTful，project-rules §API），响应统一信封 `{code, data, message}`。
5. **页面分组**（可选）：业务域页为资源类型配置 `CLASSIFY`（domain_config），管理查询按域过滤（ALL/GLOBAL_PLUS/DOMAIN_ONLY 三模式）。

前端工程约束（pnpm/构建/布局）见 `docs/design/frontend/README.md` 与前端 rules（frontend-coding-standards、frontend-layout-patterns、css-design-system）。

## 8. 验证资产索引

| 资产 | 轨道 | 覆盖 |
|---|---|---|
| `CustomResourceTypeSlicePgIT` | access-service 容器组 | 场景二完整链路（本指南 §3 的回归锁；含 T-PERM-062 授权根锁组：创建即落种子/追加操作补种/非所有者 20040/种子行改删 20061/所有者迁移/零授权根 reason=TYPE_GRANT_ORIGIN_MISSING；负向组：裸用户 NO_ROLE / 未同步资源 fail-closed） |
| `ExampleProtectedApiE2EIT` | e2e 模块 | 场景一完整链路（注册→接口声明→403→授权→30s 内生效→**撤销→403→重授恢复**〔T-ACCESS-053 第⑧步〕；含 T-PERM-070 凭证认证链〔第⑦步〕） |
| `BasicRoleGrantVerticalSliceE2EIT` | e2e 模块 | 内置类型授权垂直切片（bootstrap→建号→授权→判定） |

> 新增扩展面相关改造时，若改变本指南描述的链路步骤或门禁语义，须同步更新本文与对应验证资产（文档治理：指南为导引层，契约变更仍以 api-contract 为准先行）。
