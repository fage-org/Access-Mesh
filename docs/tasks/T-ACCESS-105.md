---
doc_type: task
id: T-ACCESS-105
title: 存量依赖漏洞核实与高危扫描阻断清理
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: cross-service
design_refs:
  - .claude/rules/security-standards.md
  - docs/ops/engineering-baseline.md
depends_on: []
blocks: []
acceptance:
  - "逐项核实 Maven 与 pnpm 扫描报告中的高危阻断，区分受影响依赖与实际可触发条件"
  - "兼容升级完成必要回归；跨大版本、替换工具或接受风险等取舍先经用户决策"
  - "按当前高危阈值在 CI 完成扫描并保存报告，不用降低阈值或未批准忽略项获得通过"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-17
---

## 背景

T-ACCESS-103 接入扫描前执行 `pnpm audit --json`，2026-10-17 当前锁文件报告 high=77、critical=2；计数为公告报告口径，不等同于独立包数或可利用漏洞数。示例包括 tinypool（GHSA-85c8-ppgw-ccpr）与 @vue/server-renderer（GHSA-g2v6-rqmx-r4w6）。原始报告见 [pnpm 扫描报告](evidence/T-ACCESS-105/pnpm-audit-2026-10-17.json)。Maven 阻断以实际扫描报告补充核实。

## 范围

存量依赖报告核实、安全升级、必要验证与 CI 扫描报告。与扫描入口接线 T-ACCESS-103 分开验收。

## 当前口径

依赖修复单列后续任务，当前批次不自行升级依赖。保持安全规范的高危阻断；T-ACCESS-103 与所属计划在阻断清理和 CI 验收前不标完成。未经确认不忽略漏洞、不降低阈值。

## 验收对照

见 frontmatter；由原始报告、升级差异、测试和 CI 工件共同证明。

## 非目标 / 遗留

不假定所有公告都能在本产品运行时触发；如需接受风险，先提供调用路径与数据示例交用户决策。
