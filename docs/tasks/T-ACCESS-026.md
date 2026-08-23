---
doc_type: task
id: T-ACCESS-026
title: 验证证据登记与文档状态收口（含 post-merge 归档）
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/architecture.md
  - docs/plans/access-post-merge-plan.md
  - docs/design/project-rules.md
depends_on: [T-API-001, T-ADMIN-022, T-PERM-043, T-ADMIN-023, T-GW-007, T-ACCESS-024, T-ACCESS-025, T-ADMIN-024]
blocks: []
acceptance:
  - "外部主机 Docker 验证证据正式登记：Docker 版本、执行命令、提交 SHA（7b7cf3254 等）、测试结果；68 项计数作为 2026-08-22 历史验证证据登记，不做数量同步机制（CI 已由 T-ACCESS-017 落地并以退出状态判定成功）"
  - "access-post-merge-plan 按其归档条件收口归档（其 CI 准入前置由 T-ACCESS-017 最小 CI 全绿关闭），归档自检清单执行"
  - "README 状态口径复核：T-ACCESS-021 已完成核心切片回写，本任务核对其与 example 接入（T-API-001）结论一致，补齐未交付能力清单"
  - "「一项任务只保留一个权威状态字段」复核：全量扫描 plans/tasks 无相互矛盾的状态表述（proposed 任务全 done、准入已关闭仍挂起等模式）"
  - "README 快速开始与 T-ACCESS-020 产物一致（compose 命令可执行）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ACCESS-026 验证证据登记与文档状态收口

## 背景

文档与证据现状：README 状态已于 2026-08-23 先行改为真实中间状态（T-ACCESS-021 完成时更新为切片完成口径）；access-post-merge-plan 三任务全 done 但计划 proposed、准入（40 项 Docker 测试）未关闭且计数已过期（实际 68 项）；外部主机 Docker 验证无正式登记；CI 已由 T-ACCESS-017 前置落地（单测强制、容器门控按需）。本任务在里程碑 B 尾部做证据与文档状态最终收口。

## 范围

- 验证证据登记、post-merge 归档、状态字段一致性复核、README 口径复核。

## 当前口径

- CI 与测试数量口径：以退出状态判定成功；68 只作为 2026-08-22 历史验证证据，不维护计数同步。
- 本任务是证据与文档收口，不新增工程化能力。

## 非目标 / 遗留

- 不建设 Grafana/Prometheus 部署、告警编排、日志采集平台。
- 不做多分支流水线/发布流水线。
