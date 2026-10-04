---
doc_type: task
id: T-ACCESS-081
title: 示例共享实例按租户选择服务凭证
status: done
plan: —
domain: cross-service
design_refs:
  - docs/design/service-authentication.md §3.5
  - docs/design/services/example-service.md
  - docs/design/access-service-api-contract.md §24.3/25.7
  - docs/design/architecture.md §4.5.1
  - docs/design/access-service-architecture.md
  - docs/design/extension-guide.md §2.3
depends_on:
  - T-ACCESS-079
blocks: []
acceptance:
  - "共享 example 实例按可信请求租户选择独立凭证；未配置租户拒绝且不调用远端、不回落固定凭证"
  - "SDK 四运行时查询支持显式凭证请求头，既有单请求体方法保持；POST/JSON/端点集合不变"
  - "配置映射不可变、凭证不泄露到文本表示，非空映射须声明 TLS 信任域；不引入动态签发、存储或轮换框架"
  - "并发租户请求与异步导出执行时点重查不串凭证；验证实际 Feign 请求头与不同租户业务结果"
  - "部署配置和当前设计改为共享多租户；原单租户安排仅保留归档追溯"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-081 示例共享实例按租户选择服务凭证

## 背景

T-ACCESS-079 的固定单租户示例安排已于 2026-10-04 被方案 B 取代；单份凭证仍只绑定一个租户，平台认证模型不变。

## 范围

example 的凭证配置与最终检查、SDK 显式头重载、对应测试、接入/部署及当前规范。

## 当前口径

已确认共享实例按网关验签租户选凭证，异步任务沿捕获租户重查；具体契约见服务认证 §3.5。实现采用静态配置映射与请求级参数，不改全局拦截器状态。

## 非目标 / 遗留

不新增凭证管理页面、自动签发/轮换、数据库配置中心或缓存；不开放跨租户通用凭证。

## 验收对照

- [x] 共享实例按可信租户选择独立凭证，无映射立即拒绝，不回落默认凭证。
- [x] SDK 四查询显式凭证重载通过实际 Feign 请求验证，原单 DTO 入口保持。
- [x] 不可变配置与 secret 文本隐藏、配置校验通过。
- [x] 并发、异步及真实双租户业务检查通过；吊销后拒绝。
- [x] 当前设计、配置与部署说明回写完成。

## 完成记录

2026-10-04：设计回写和本地双轨评审完成；定向单测 32 项、共享示例 E2E 12 项通过；`mvn test -T 1C` 全量 2530 项通过，0 失败/错误/跳过（含 E2E/heavy）。详细反例、测试与部署边界见[验收证据](evidence/T-ACCESS-081/verification.md)。
