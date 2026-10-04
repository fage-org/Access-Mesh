---
doc_type: task
id: T-PERM-105
title: 退役自动授权闲置 grant_dep_id 列与实体字段
status: done
plan: —
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md §3.4
  - docs/design/schema/access-service.sql
depends_on:
  - T-PERM-103
blocks: []
acceptance:
  - "删除当前 schema 的 role_resource_permission.grant_dep_id 及列注释，删除实体字段和通用授权行复制点"
  - "扫描当前生产代码、测试、规范与技能引用；只保留有明确用途的历史追溯，不恢复迁移支持"
  - "按当前 schema 重建，不提供旧库迁移/回滚 SQL；原样 PG schema 与自动授权相关测试通过"
  - "推导、撤销及 explain 行为不变，完成后回写设计、AGENTS 和相关活引用"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-PERM-105 退役自动授权闲置 grant_dep_id 列与实体字段

## 背景

[T-PERM-103](T-PERM-103.md) 已确认删除闲置列。旧字段表达单条依赖规则来源，不能表达当前多来源推导；现有推导与清理不使用该列，来源由 explain 提供。

## 范围

当前 schema、`RoleResourcePermission.grantDepId`、`PermissionGrantPlanDomainServiceImpl` 通用复制点及有效引用。该卡为原清单计划定案后另立的实施任务，不计入原计划的任务清单。

## 当前口径

2026-10-04 确认删除；当前库采用重建，不做历史迁移。列、实体字段与通用复制点一并退役，来源通过 explain 推导。

## 非目标 / 遗留

- 不改变 AUTO_DEP 物化、解释或多来源撤销语义。
- 不增加来源表或替代占位字段。

## 验收对照

- [x] 删除当前 schema 的 role_resource_permission.grant_dep_id 及列注释，删除实体字段和通用授权行复制点
- [x] 扫描当前生产代码、测试、规范与技能引用；只保留有明确用途的历史追溯，不恢复迁移支持
- [x] 按当前 schema 重建，不提供旧库迁移/回滚 SQL；原样 PG schema 与自动授权相关测试通过
- [x] 推导、撤销及 explain 行为不变，完成后回写设计、AGENTS 和相关活引用

## 完成记录

2026-10-04：实现、设计回写与本地代码/文档双轨评审完成。`mvn test -T 1C` 全量 2521 项，0 失败/错误/跳过（含 E2E 29 项及 heavy）；前端 `pnpm test` 492 项、build/typecheck/lint 通过。定向回归、反例实证、退役测试映射及部署边界见[综合验收](evidence/checklist-followup/final-audit.md)。
