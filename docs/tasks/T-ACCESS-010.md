---
doc_type: task
id: T-ACCESS-010
title: 切换 Gateway、SDK、Nacos和部署配置
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#2-目标工程与部署单元
  - docs/design/access-service-architecture.md#9-api-sdk-与生态切换
  - docs/design/architecture.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-004
  - T-ACCESS-005
  - T-ACCESS-008
blocks: []
acceptance:
  - "Gateway 的 /admin/**、/perm/**、/auth/** 路由目标统一为 lb://access-service，原路径行为不变"
  - "perm-sdk 外部 Feign 目标统一为 access-service；access-service 内部不依赖 perm-sdk 调用自身"
  - "Nacos、Docker Compose、应用名、端口、日志、指标、服务配置和缓存失效载荷统一为 access-service"
  - "serviceCode/ownerCode 中代表两个旧服务的值统一为 access-service，外部来源服务身份保持各自 serviceCode"
  - "根 Maven 聚合删除 admin-service 与 permission-center 模块，旧启动类、运行配置和源码目录移除"
  - "不创建旧服务名、旧端口、空壳模块或转发代理；非归档代码和配置无旧服务发现目标"
  - "Gateway 与 perm-sdk 契约测试证明旧 HTTP 契约继续可用"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-10
---

# T-ACCESS-010 切换 Gateway、SDK、Nacos和部署配置

## 背景

工程内部归并完成后，必须一次性切换所有运行入口并删除旧部署单元，避免形成长期三服务并存状态。

## 范围

- 切换 Gateway、SDK、注册发现、容器和观测标识。
- 更新根构建并删除旧服务模块。
- 验证保留接口契约，不提供部署兼容层。

## 完成记录

（待实施后填写。）
