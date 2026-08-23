---
doc_type: task
id: T-API-001
title: example 单受保护接口接入（Gateway 主线）与 Starter 名实对齐
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/services/example-service.md
  - docs/design/services/gateway.md
  - docs/design/architecture.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "example-service 实现一个经 Gateway 路由与鉴权保护的业务 API：未授权 403 → 在 access-service 授权后 200（证明业务服务接入路径真实可用，不再是启动骨架）；错误码用 3xxxx 段"
  - "接口保护由 Gateway 承担（规范 §2.4 服务内不重复鉴权）：不调用 PermissionFeignClient，不为演示新增接口扫描器、@PermResource、自动注册或服务内二次鉴权"
  - "POM 依赖瘦身：删除单接口不消费的 perm-client、perm-data、MyBatis-Flex、PostgreSQL、Redis、MapStruct、JSqlParser 依赖（实施时逐项核对实际消费，确有消费者保留并登记理由）"
  - "architecture.md §4.4 Starter 能力表与 §4.5.1 修正：perm-client 仅描述已实现的 Feign 能力（checkAuth/batchCheckAuth 等远程查询与 X-Internal-Secret 身份透传），移除未实现的「反射扫描接口+@PermResource 增强、全量幂等注册」表述；perm-data 已有「规划中未实现」标注保持"
  - "perm-data starter 标记 experimental/unavailable 或从 README 特性表移除（当前为空配置类，名实不符）；README 特性表逐项与实际对齐：example 单接口已交付、报表数据范围/动态 SQL 权限明确标注未交付"
  - "单测 + 一次真实链路验证（依赖 T-ACCESS-021 环境，403→200 证据登记）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-API-001 example 单受保护接口接入（Gateway 主线）与 Starter 名实对齐

## 背景

example-service 仍是启动骨架（仅 Application 类，无任何 Controller）；perm-data starter 为空配置类；architecture.md §4.4 能力表对 perm-client 宣称「反射扫描接口+@PermResource 增强、全量幂等注册」、§4.5.1 列出 @PermResource/PermissionClient.hasPermission/DataPermissionInterceptor——实际 starter 仅为 Feign 自动配置（@EnableFeignClients + 身份透传拦截器，提供 checkAuth/batchCheckAuth 等远程方法），扫描/注解/自动注册能力不存在；README 特性表宣称「三个 Starter 快速集成」可用，名实不符。接口级鉴权由 Gateway 承担（规范 §2.4），业务服务无需 perm-client 即可被保护； Gateway 403→200 证明的是接入路径而非 starter 消费。

## 范围

- 一个经 Gateway 保护的 example 业务 API（3xxxx 错误码）。
- example POM 依赖瘦身（含未消费的 perm-client）。
- architecture.md Starter 能力表与 README 特性表名实对齐。

## 当前口径

- Gateway 是接口鉴权唯一执行点；example 不做服务内二次鉴权。
- 未消费的 starter 依赖直接删除，不为「证明 Starter」制造调用；starter 能力文档只描述已实现部分。

## 非目标 / 遗留

- 不实现接口扫描、@PermResource、自动注册框架（有真实消费者后另行评估）。
- 不做报表数据范围、动态 SQL 数据权限（T-PERM-036 关联，仍暂缓）。
- 不为 example 建管理 UI。
