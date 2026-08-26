---
doc_type: design
title: Example Service 设计
status: adopted
domain: example-service
last_reviewed: 2026-06-20
---

# Example Service 设计

本文档是 example-service 的精简设计入口。旧版完整设计已归档到 `../../archive/2026-04-28/example-service-design.full.md`，仅用于追溯。

## 职责边界

- 在核心主线稳定后，提供 AccessMesh 权限中心真实接入示例。
- 覆盖接口鉴权、菜单权限、按钮权限、范围权限、条件权限、权限查询等典型场景。
- 提供 Spring Boot starter、普通 Java SDK 和其他语言接口文档的参考集成方式与验证样板。
- 不作为生产业务系统模板的强制实现，只用于验证和展示权限中心能力。

## 已交付：单受保护接口（T-API-001）

- `POST /api/example/demo/hello`（身份回显接口）：入参 `{name}`，返回问候语 + Gateway `HeaderEnrichFilter` 注入的 `X-User-Id`/`X-Tenant-Id` 回显；`name` 空白拒绝 30001、身份头缺失拒绝 30002（example 业务域错误码段 30001-39999，`ExampleErrorCode`）。
- 接口级鉴权完全由 Gateway 承担（规范 §2.4 服务内不重复鉴权）：Gateway `/example/**` 路由（StripPrefix=1、`serviceCode=example-service`）按接口快照放行/拒绝；业务服务不引入 perm-client/perm-data，无服务内二次鉴权。
- 接入路径（E2E `ExampleProtectedApiE2EIT` 钉死）：业务服务经 `/api/perm/resource-entity/sync`（X-Internal-Secret + X-Service-Code 身份、service_config `syncTypes` 白名单）注册 API 资源 → 管理员创建 `resource_api_mapping`（外部路径 `/example/api/example/demo/hello`）→ 授予角色 `API:ACCESS` → 403 变 200。
- 依赖瘦身：POM 删除 perm-client、perm-data、openfeign、MyBatis-Flex、PostgreSQL、Redis、MapStruct、JSqlParser（均无消费方）；保留 common（统一响应体/全局异常处理器）、web、validation、nacos、log4j2。无数据源、无缓存消费（`accessmesh.cache.enabled=false`）。
- 菜单/按钮/范围/条件权限等其余演示场景仍为规划（见上表），随核心主线后续任务补齐。

## 演示场景

| 场景               | 目标                                                           |
| ------------------ | -------------------------------------------------------------- |
| 服务注册与接口同步 | 展示业务服务如何向 access-service 全量同步接口资源               |
| 接口权限           | 展示 Gateway + access-service 接口级鉴权                        |
| 菜单/按钮权限      | 展示前端资源和操作权限控制                                     |
| 报表范围权限       | 展示 `query-scopes`、`DIRECT ∪ DEPENDENT`、`scopeMode=ALL`     |
| 条件权限           | 展示时间、IP 等条件评估                                        |
| 权限查询           | 展示 `auth/check`、`auth/query-resources`、`auth/query-scopes` |

## 报表范围权限推荐模型

- 报表建模为主资源，例如 `report:sales`。
- 部门、城市、门店、数据集建模为范围资源，例如 `data:dept:A`。
- 推荐使用主资源业务数据动作：`DATA_READ`、`DATA_EDIT`。
- 直接范围权限表示通用范围能力，例如 A 部门主管拥有 `data:dept:A + DATA_READ`。
- 子权限表示当前报表下额外范围，例如仅在销售报表下允许查看 B 部门数据。
- 全量范围通过对外 `scopeMode=ALL` 表达，不使用 `data:all` 这类特殊资源编码。

## SDK 参考

- Spring Boot 项目：以 `perm-client-spring-boot-starter` 为核心，结合 `perm-data-spring-boot-starter`、`perm-gateway-spring-boot-starter` 提供自动配置式接入参考。
- 普通 Java 项目：提供轻量 client SDK，复用稳定鉴权和权限查询契约，不依赖 Spring Boot 自动配置。
- 其他语言项目：通过稳定 HTTP API 契约和接入文档对接，不要求依赖 Java SDK。

当前 starter 模块是接入形态与能力边界的参考实现；最终交付形态需要在核心主线稳定后再统一收敛与裁决。

具体契约以 `../permission-center/api-contract.md` 为准。

## 数据库

example-service 表结构以 `../schema/example-service.sql` 为准。本文档不重复维护字段、索引、约束。
