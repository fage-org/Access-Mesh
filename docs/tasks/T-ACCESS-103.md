---
doc_type: task
id: T-ACCESS-103
title: 文档与流程对齐批（入门多租户口径/接入路由说明/CI 依赖扫描/时间条件时区提示）
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/extension-guide.md
  - docs/design/access-service-api-contract.md
depends_on: []
blocks: []
acceptance:
  - "README 现行特性/限制段与现行多租户能力一致（支持运营开通、旧 tenantId 登录退役、升级按重建边界），不再同时宣称「只支持固定单租户」与「可开通租户」；Unreleased 段描述核对；CHANGELOG 历史版本段（v0.1.0）保留版本事实不改写，如需加历史说明仅以不改变原记录的方式标注"
  - "extension-guide.md 明确区分「接口操作准入映射」与「Gateway 转发路由」两个概念，补显式路由配置示例（Path/uri/metadata.serviceCode 与 basePath 对应关系），接入步骤不再暗示 sync-v2 会创建转发路由"
  - "ci.yml 增加依赖扫描入口（覆盖 Maven 解析结果与 pnpm 锁文件、保留报告、按现行阈值阻断），满足 security-standards §6 MUST 要求；规则内旧示例（action@main/JDK17）同步固定版本"
  - "时间条件输入界面补 UTC 语义说明（管理面与契约条件章一致措辞）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
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

见 frontmatter `acceptance`；扫描 job 在 CI 真实跑通一次并留报告工件。

## 非目标 / 遗留

显式 Gateway 拓扑定案不变；Dependabot 等仓外告警承接方式在卡内注明（若组织级已配，注明入口即可）。
