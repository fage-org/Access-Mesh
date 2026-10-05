---
doc_type: task
id: T-API-007
title: SDK 集成安全收编与文档前提
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/extension-guide.md（§2.1 签名过滤器/§2.3 服务发现前提）
  - docs/design/service-authentication.md（M2M 通道）
depends_on: []
blocks: []
acceptance:
  - "GatewaySignatureFilter（HMAC-SHA256/常量时间比较/300s 窗/密钥未配置 fail-closed）收编进 perm-client starter：引入即得验签"
  - "extension-guide §2.3 补 Nacos/loadbalancer 前提+url 覆盖示例（无服务发现环境首日集成不阻塞）"
  - "perm-gateway 空壳 starter 删除+gateway 搭车日志依赖改直接声明+README「已使用」表述修正"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-API-007 SDK 集成安全收编与文档前提

## 背景

安全关键的网关签名校验过滤器只在 example-service（约 120 行），perm-sdk 三个 starter 均不提供——每个 Java 接入方手工复制安全代码，复制走样（时间窗常量不同源/漏常量时间比较/密钥未配置放行）直接弱化身份防线且从外部不可见。@FeignClient(name="access-service") 硬编码服务发现名，extension-guide 不提发现前提——不用 Nacos 的接入方配好凭证后首调即「服务名无法解析」。perm-gateway starter 是空壳（2 个类、零消费、gateway pom 引它只为搭车传 Logback），README 宣传「已使用」失实。

## 范围

验签过滤器收编进 SDK、文档前提补齐、空壳模块删除。

## 当前口径

过滤器为纯 servlet+@Value 可直接迁移（收编后 example 改为消费 starter）；perm-gateway 删除（2026-10-05 拍板 D15=A），README 改口。

## 验收对照

- [ ] starter 引入即得验签（example 复用验证）
- [ ] guide 前提+url 覆盖示例补齐（照走可通）
- [ ] 空壳删除+构建绿+README 修正

## 非目标 / 遗留

- perm-gateway 实现为真鉴权插件：不实施（无消费方牵引，另立项）。
