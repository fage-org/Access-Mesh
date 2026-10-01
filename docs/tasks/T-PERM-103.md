---
doc_type: task
id: T-PERM-103
title: grant_dep_id 保留列处置评估
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md §3.4（自动授权结果）
depends_on: []
blocks: []
acceptance:
  - "评估产出拍板：维持零读零写保留（默认候选，维持 2026-09-21 T-PERM-072 保留安排）或纳入 schema 清理窗口退役；按 decision-question-protocol 上报用户"
  - "若维持保留：列注释与 §3.4 诊断口径复核（单字段不能表达多条依赖边与种子来源的表述仍在册），Q-022 按「维持现状定案」收敛 closed"
  - "若退役：DROP 列为外部 DBA/运维动作（仓库无 migration 框架，T-PERM-003 先例），订正语句入 runbook，实施另立新号"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-PERM-103 grant_dep_id 保留列处置评估

## 背景

承接 [Q-022](../pending-problems.md#q-022)：role_resource_permission.grant_dep_id 及实体字段不读不写；单字段不能表达同一 AUTO_DEP 行的多条依赖边与种子来源，列注释已改保留诊断口径。既有安排明确保留，本卡是把「未来再评估」兑现为显式拍板，防止占位无限期滞留。

## 范围

定案卡：评估+拍板+口径复核；退役实施（若拍板退役）另立。

## 当前口径

无功能缺陷，纯占位处置。默认候选=维持保留。

## 非目标 / 遗留

- AUTO_DEP 语义本身（dependency-auto-grant.md 定案口径不动）。
