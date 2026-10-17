---
doc_type: task
id: T-ACCESS-103
title: 文档与流程对齐批（入门多租户口径/接入路由说明/CI 依赖扫描/时间条件时区提示）
status: done
plan: docs/archive/2026-10-17/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/extension-guide.md
  - docs/design/access-service-api-contract.md
  - .claude/rules/security-standards.md
depends_on:
  - T-ACCESS-105
blocks: []
acceptance:
  - "README 现行特性/限制段与现行多租户能力一致（支持运营开通、旧 tenantId 登录退役、升级按重建边界），不再同时宣称「只支持固定单租户」与「可开通租户」；Unreleased 段描述核对；CHANGELOG 历史版本段（v0.1.0）保留版本事实不改写，如需加历史说明仅以不改变原记录的方式标注"
  - "extension-guide.md 明确区分「接口操作准入映射」与「Gateway 转发路由」两个概念，补显式路由配置示例（Path/uri/metadata.serviceCode 与 basePath 对应关系），接入步骤不再暗示 sync-v2 会创建转发路由"
  - "ci.yml 增加依赖扫描入口（覆盖 Maven 解析结果与 pnpm 锁文件、保留报告、按现行阈值阻断），满足 security-standards §6 MUST 要求；规则内旧示例（action@main/JDK17）同步固定版本"
  - "时间条件输入界面补 UTC 语义说明（管理面与契约条件章一致措辞）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

P3×1 + P2×3 批（外部持续审查 2026-10-12~14 发现，主代理核实成立）：

- **入门口径矛盾**（P3）：README「只支持固定单租户」限制段与同页「平台开通租户」步骤并存；CHANGELOG 的旧限制位于 v0.1.0 历史版本段（`[0.1.0] - 2026-09-16` 节内），属版本事实，仅 README 现行段与 Unreleased 需核对。实际租户生命周期能力已交付（权威=tenant-lifecycle 设计，现行 controller/前端链路真实存在）。
- **接入指南路由混用**（P2）：extension-guide §2.1 把 sync-v2 描述为「一步创建 API 资源与 Gateway 路由映射」，但仓库 Gateway `discovery.locator.enabled=false`、仅两条静态路由、主源码零动态路由实现——新服务按指南完成后请求到不了业务服务（探针实证真实 SCG binder 不命中）。权限登记成功与网络路由可达是两个独立前提。
- **CI 无依赖扫描**（P2）：`.claude/rules/security-standards.md` §6 MUST 要求 CI 依赖扫描（CVE≥7.0 阻断+每周全量）；ci.yml 全文无任何扫描/报告/schedule 配置，规范与仓内流程各自假定。
- **时间条件无时区说明**（P2）：时间条件输入界面与文案未说明全链路 UTC 语义，管理员易把本地工作时段配置成 UTC 窗口。

## 范围

README/CHANGELOG/extension-guide/security-standards 对齐；ci.yml 扫描 job；前端时间条件提示文案。

## 当前口径

文档与流程对齐，不改运行时行为；扫描入口选适用工具并固定版本（不照搬规则内漂移示例）；不为补路由步骤自动启用全服务发现路由或动态路由平台。时区说明遵循「不做时区处理、全链路 UTC」定案。

## 验收对照

- [x] README 与 Unreleased 核对，历史 v0.1.0 记录保持；当前租户能力与重建边界一致。
- [x] 指南区分准入映射与转发路由，给出与现役配置一致的显式路由例子。
- [x] 扫描 job、周计划、固定工具版本与 Actions SHA、失败报告保存已接入源码；工作流 YAML 解析通过。
- [x] 时间条件输入直接提示 UTC 与不自动换算。
- [x] [GitHub Actions 真实运行](https://github.com/fage-org/Access-Mesh/actions/runs/39008776796)通过，Maven/pnpm 安全报告及元数据工件已保存并核对摘要；[验收证据](evidence/T-ACCESS-105/ci-verification-2026-10-17.json)可追溯。

## 非目标 / 遗留

存量依赖修复由 T-ACCESS-105 完成；批准风险例外继续受安全规范限制。显式 Gateway 拓扑定案不变。本任务的扫描入口为仓库 CI 的 push/PR/手动/每周计划，失败通过 CI 状态报告；未新增 Dependabot 等仓外流程。

## 完成记录

2026-10-17：实现与设计回写完成；[CI](https://github.com/fage-org/Access-Mesh/actions/runs/39008776796)对应提交 `f8f5ecbff30c930623ca54243e28aab46500b1eb`，前端 562 项与类型/lint/构建/44 组 DTO 对账、后端单测 2314 项及 Maven/pnpm 安全门禁均通过。报告工件 `12092321203` 已下载并核对 SHA-256，保留批准例外，不声称漏洞清零。完整后端容器/E2E/heavy 的本地 2870 项验收见 T-ACCESS-105 证据。

代码轨核对扫描范围、失败阻断、报告保存、每周调度与 UTC 提示；文档轨核对当前多租户能力、历史版本记录保留、转发路由与准入映射职责。无未处理的任务验收项。
