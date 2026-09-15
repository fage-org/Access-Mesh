---
doc_type: task
id: T-ACCESS-043
title: 跨能力 mapper 收敛批次①——既有服务直换 + 死边清理（9 边）
status: in-progress
plan: docs/plans/capability-mapper-convergence-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-capability-structure.md#8-架构断言重建设计（§8.4 豁免 6 冻结白名单行缩减）
depends_on: []
blocks: []
acceptance:
  - "9 条边收敛：E5 死注入删除；grant 两处 selectValidById→SubjectDomainService.selectValidRoleById；grant 两处 selectValidByIds→ResourceEntityDomainService.selectValidByIds；UserManage 4 点（3 处 selectValidRolesByIds + 新增 selectEnabledRoleIds）；UserApp 6 点→UserOrgDomainService.findBy*；UserRoleProjectionWriter 4 点→TypeResolutionService.resolveUserId/batchResolveUserIds；ConflictRule 内联 FQCN→selectValidRolesByIds"
  - "FROZEN_WHITELIST 同 commit 删 9 行，frozenWhitelistShapeIsLocked 计数改 21 边/15 消费类；负向自证样例行随删改取剩余首行"
  - "R6 语义红线：bindUserOrg 读 createUser 同事务新 abstract_user 行——替代路径行读取直查（类型值 TYPE_VALUE 缓存为既有形态），不引入结果缓存"
  - "mvn test -pl access-service 全绿（含容器组）；计划文件「测试装配适配清单」批次①列出的测试构造/verify 改挂完成"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-15
---

## 背景

Q-009（2026-09-13 登记）转出：冻结白名单 19 类 30 边全量收敛至零（计划 [capability-mapper-convergence-plan](../plans/capability-mapper-convergence-plan.md)，定案见 decision-registry 2026-09-15 行）。批次①是零语义判断面的直换与死边清理，先行降风险。

## 范围

计划文件「批次①」表 9 边 + SubjectDomainService 新增 selectEnabledRoleIds（含用例）+ 测试装配适配（计划清单批次①节）。

## 非目标 / 遗留

- 其余 21 边归后续批次；不触碰 9 边之外的任何 mapper 依赖；不改 SQL/XML。

## 完成记录

（进行中）
