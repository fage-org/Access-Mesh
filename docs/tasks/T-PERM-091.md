---
doc_type: task
id: T-PERM-091
title: （R2-T12）迁移旧快照、转授、视图与配置
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.3/§6.4/§6.5
depends_on:
  - T-PERM-087
  - T-PERM-088
  - T-PERM-090
blocks: []
acceptance:
  - "checkCanGrant：GRANT_LIST+FACTS、DATABASE（bypassPermSnapshot 直查不回填语义保持）、PRESERVE+SKIP、无展示展开、额外装载待授类型-操作定义；转授四例 T01~T04 全绿——同一条真实授权同行验证资格（不拼接两行）、无目标类型授权时 NO_PERMISSION≠INVALID_OPERATION、运行时祖先可用不自动扩大转授、refineGrantOriginMissing 留在领域层"
  - "PermissionViewAppServiceImpl 迁移：类型页「任意有效操作」语义（instanceIdsByType 不含子孙扩展）保持；具体 A/B 菜单仍分别绑定 REPORT_A/REPORT_B；权限码全量聚合不因分页漏有效操作"
  - "角色配置 Roles+SELF+DISALLOW（防 scopeAll 混进配置清单）；查看者管理门禁与被查看角色可用性两判定不因配置展示删除带条件授权"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-091 （R2-T12）迁移旧快照、转授、视图与配置

## 背景

设计 §6.3/§6.4（报告临时编号 R2-T12）。读引擎不反调写侧（授权计划/物化服务）；转授领域继续判断「同一条真实授权」的 canGrant/无条件/操作覆盖/范围。

## 范围

- SnapshotAssembler legacy 投影、PermissionGrantDomainServiceImpl、PermissionViewAppServiceImpl、配置/解释/PermViewAssembler（按真实用途选 FACTS 或 DECISION+TRACE，删除旧结果依赖，不机械套 GRANT_LIST）。

## 非目标 / 遗留

- 菜单类型页语义升级（如按操作准入精化）不在本卡——沿用现行「任意有效操作」语义，方案 A 的菜单面不在设计范围。
