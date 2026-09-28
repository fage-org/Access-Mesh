---
doc_type: design
title: Example Service 设计
status: adopted
domain: example-service
last_reviewed: 2026-09-28   # T-ACCESS-061：§8.6 业务最终检查七路由参考族落地（perm-client SDK 消费拍板+内部密钥通道+反向拒绝测试）、SDK 接入边界改写；此前 2026-09-23（T-ACCESS-053）
---

# Example Service 设计

本文档是 example-service 的精简设计入口。旧版完整设计已归档到 `../../archive/2026-04-28/example-service-design.full.md`，仅用于追溯。

## 职责边界

- 在核心主线稳定后，提供 AccessMesh 权限中心真实接入示例。
- 覆盖接口鉴权、菜单权限、按钮权限、范围权限、条件权限、权限查询等典型场景。
- 提供 Spring Boot starter、普通 Java SDK 和其他语言接口文档的参考集成方式与验证样板。
- 不作为生产业务系统模板的强制实现，只用于验证和展示权限中心能力。

## 已交付：单受保护接口（T-API-001）与业务最终检查路由族（T-ACCESS-061）

- `POST /api/example/demo/hello`（身份回显接口）：入参 `{name}`，返回问候语 + Gateway `HeaderEnrichFilter` 注入的 `X-User-Id`/`X-Tenant-Id` 回显；`name` 空白拒绝 30001、身份头缺失拒绝 30002（example 业务域错误码段 30001-39999，`ExampleErrorCode`）。
- 接口级鉴权（第一层）由 Gateway 承担（规范 §2.4）：Gateway `Path=/api/example/**` 路由（无 StripPrefix、`serviceCode=example-service`，T-ACCESS-042 单命名空间）按操作准入快照放行/拒绝（MAY_ENTER=存在类型-操作覆盖候选，非最终许可）。
- **业务最终检查（第二层，T-ACCESS-061 §8.6 逐路由参考实现）**：`ReportController` 七路由覆盖检查表全部七行——`view`（实际资源+对应操作）、`batch-view`（独立批量逐项 DECISION）、`list`（`query-resources` 范围过滤+分页 total 同口径）、`create`（TYPE_LEVEL）、`sub-view`（depend_on 真实父上下文）、`export/submit`+`export/status`（异步作业提交与执行时点各自鉴权）；`/hello` 最终检查定位=身份头存在性。主体/租户恒取自已验签身份头（请求 DTO 无主体/租户字段）；业务拒绝=信封 **30004**、鉴权服务不可用=fail-closed **30005**；反向拒绝测试=`ReportControllerTest`（20 用例）+`ReportControllerValidationTest`（3 用例 HTTP 层反例）（迁移资格载体——契约 §25.7）。
- 接入路径（E2E `ExampleProtectedApiE2EIT` ③~⑧ 钉死；T-ACCESS-059/061 现行口径）：管理员经 Gateway 先建业务类型与实例（`type-definition/create` 建 `EXAMPLE`、`resource-entity/create` 建示例报表实例），再调 `/api/access/service-config/sync-v2`（FULL 接口声明，ApiItem 必填 `requiredPermission={resourceTypeCode:EXAMPLE, operationCode:VIEW}`）一步创建 API 登记资源与 `resource_api_mapping`（owner=example-service、maintainSource=SERVICE_SYNC、pathPattern=basePath+path=外部路径）→ 授予角色业务操作授权（`EXAMPLE:VIEW`）→ 网关操作准入放行（MAY_ENTER）→ 业务最终检查放行 → 200。旧 `/sync`（v1）协议不携带操作引用，不再用于新链接入；`API:ACCESS` 授权行自 T-ACCESS-059 起不参与网关判定（legacy 资源语义，062 受控清理面）。API 登记资源的唯一事实入口仍是 service-config 声明通道（API 类型种子声明 SYNC+access-service，T-PERM-069：外部同步来源不匹配一律 RESOURCE_TYPE_OWNERSHIP_DENIED、资源管理面手工 CRUD 20055）。
- 依赖形态（T-ACCESS-061 用户拍板修订）：POM 重新引入 `perm-client-spring-boot-starter`（Feign——业务最终检查调用 `auth/check` 族端点；服务认证=内部密钥通道 `FeignInternalSyncInterceptor`，`X-Tenant-Id` 由本服务 `FeignTenantHeaderInterceptor` 从调用上下文注入）；仍无数据源、无缓存消费（`accessmesh.cache.enabled=false`）。曾删除的 perm-data、MyBatis-Flex、PostgreSQL、Redis、MapStruct、JSqlParser 维持删除。
- 菜单/按钮/范围/条件权限等其余演示场景仍为规划（状态见下方演示场景表），随核心主线后续任务补齐。
- 错误码子段约定：30001-30099 为演示接口（demo）相关错误（`ExampleErrorCode` 代码注释为登记处），30001+ 段位分配随新资源扩展时在代码枚举中登记。
- **身份签名校验（`GatewaySignatureFilter`）**：复算 Gateway `SignatureEnrichFilter` 注入的 `X-User-Signature`（HMAC-SHA256(secret, userId|tenantId|timestamp)，常量时间比较），时效窗 `example.signature.valid-seconds`（默认 300s，与 access-service `perm.signature.valid-seconds` 运维同调）；携带身份头但签名缺失/不匹配/超窗的请求拒绝信封 **30003**。密钥经 `example.signature.secret`（默认取环境变量 `ACCESSMESH_SIGNATURE_SECRET`，须与 Gateway 同源）——未配置时 fail-closed（凡携带身份头的请求一律拒绝，启动日志 ERROR 提示）。信任边界的根本保障仍是网络隔离（业务服务仅 Gateway 可达），签名校验是纵深防御/直连自证示例。

## 演示场景

| 场景               | 目标                                                           | 状态 |
| ------------------- | -------------------------------------------------------------- | ---- |
| 服务注册与接口同步 | 展示业务服务如何向 access-service 全量同步接口资源               | ✅ 已交付（service-config/sync 声明通道；E2E ③） |
| 接口权限           | 展示 Gateway + access-service 接口级鉴权                        | ✅ 已交付（403→授权→200→**撤销→403→重授恢复**完整主线，T-ACCESS-053；E2E ④~⑥+⑧） |
| 服务身份（凭证）   | 展示 per-service 凭证签发/认证/轮换吊销（T-PERM-070）           | ✅ 已交付（E2E ⑦ 凭证认证链；两套身份适用面见 extension-guide §2.2） |
| 菜单/按钮权限      | 展示前端资源和操作权限控制                                     | 规划 |
| 报表范围权限       | 展示 `query-scopes`、`DIRECT ∪ DEPENDENT`、`scopeMode=ALL`     | 规划 |
| 条件权限           | 展示时间、IP 等条件评估                                        | 规划 |
| 业务最终检查       | 展示 §8.6 两层判定：网关准入 MAY_ENTER + 业务实例级最终检查（N01 双路/批量/列表/CREATE/子权限/异步/N25） | ✅ 已交付（T-ACCESS-061，E2E `ExampleBusinessFinalCheckE2EIT`；模式切换 runbook 演练⑪） |
| 权限查询           | 展示 `auth/check`、`auth/query-resources`、`auth/query-scopes` | ✅ 已交付（业务最终检查路由族经 SDK 消费 check/batch-check/query-resources，T-ACCESS-061） |

## 报表范围权限推荐模型

- 报表建模为主资源，例如 `report:sales`。
- 部门、城市、门店、数据集建模为范围资源，例如 `data:dept:A`。
- 推荐使用主资源业务数据动作：`DATA_READ`、`DATA_EDIT`。
- 直接范围权限表示通用范围能力，例如 A 部门主管拥有 `data:dept:A + DATA_READ`。
- 子权限表示当前报表下额外范围，例如仅在销售报表下允许查看 B 部门数据。
- 全量范围通过对外 `scopeMode=ALL` 表达，不使用 `data:all` 这类特殊资源编码。

## SDK 接入边界

> 接口级鉴权继续由 Gateway 承担。**业务最终检查自 T-ACCESS-061（2026-09-28 用户拍板）消费 `perm-client-spring-boot-starter`**（Feign 调 `auth/check`/`auth/batch-check`/`auth/query-resources`，内部密钥通道；e2e 子进程拓扑：access/gateway 子进程须 `--perm.client.enabled=false` 防测试类路径传染）。T-PERM-071 可选 registration starter 维持默认关闭，仅在显式配置后独立发布依赖；示例与 profile 见 [permission-manifest.md](../../../example-service/examples/permission-manifest.md)。资源同步不因此强制使用 SDK。

- Spring Boot 项目（规划）：以 `perm-client-spring-boot-starter` 为核心（当前仅为 Feign 远程查询 SDK，见 architecture §4.4/§4.5.1 名实对齐口径），`perm-gateway-spring-boot-starter` 供网关使用。数据权限参考实现属演进方向，未提供模块。
- 普通 Java 项目（规划）：提供轻量 client SDK，复用稳定鉴权和权限查询契约，不依赖 Spring Boot 自动配置。
- 其他语言项目：通过稳定 HTTP API 契约和接入文档对接，不要求依赖 Java SDK。

当前 starter 模块是接入形态与能力边界的参考实现；最终交付形态需要在核心主线稳定后再统一收敛与裁决。

具体契约以 `../access-service-api-contract.md`（契约总册）为准。

## 数据库

本服务当前无数据源（T-API-001 依赖瘦身删除 PostgreSQL/MyBatis-Flex）。`../schema/example-service.sql` 暂无消费方，保留供未来演示数据场景；启用时需重新引入数据源依赖并在本文档恢复表结构口径。
