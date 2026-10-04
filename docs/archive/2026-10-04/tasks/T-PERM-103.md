---
doc_type: task
id: T-PERM-103
title: grant_dep_id 保留列处置评估
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md §3.4（自动授权结果）
depends_on: []
blocks: []
acceptance:
  - "明确 grant_dep_id 去留并回写自动授权设计 §3.4"
  - "退役实施另立新号，当前库走重建，不做历史迁移/回滚"
  - "区分删除目标与当前字段仍存在的实施差异，不改变自动授权语义"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-PERM-103 grant_dep_id 保留列处置评估

## 背景

承接 [Q-022](../../../pending-problems.md#q-022)：role_resource_permission.grant_dep_id 原指向触发自动授权的 resource_dependency.id；单字段不能表达当前同一 AUTO_DEP 行的多条依赖边与种子来源。当前推导与清理不读该列，物化器不赋值，仅通用授权行复制原样携带。

## 范围

定案卡：评估+拍板+口径复核；退役实施（若拍板退役）另立。

## 当前口径

闲置列确定删除（2026-10-04 确认），实施由 [T-PERM-105](T-PERM-105.md) 承接；当前库重建，不提供历史迁移或回滚 SQL。自动授权设计 §3.4 明确退役目标及实施前列仍存在的差异。

## 非目标 / 遗留

- AUTO_DEP 语义本身（dependency-auto-grant.md 定案口径不动）。

## 验收对照

- [x] 原字段用途与现有零业务消费已核对，通用复制点未漏记。
- [x] 删除决定、重建库边界及实施差异回写 §3.4。
- [x] 新分配 T-PERM-105 承接实施，未把定案当成字段已删除。

## 完成记录

2026-10-04：纯定案任务，未修改 schema 或运行时代码。旧 DDL 列注释核实原用途，当前源码搜索核对唯一复制点；代码轨与文档轨检查无未处理发现。业务回归不适用于本卡定案，字段删除验证归 T-PERM-105。
