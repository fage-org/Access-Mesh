---
doc_type: task
id: T-ACCESS-043
title: 跨能力 mapper 收敛批次①——既有服务直换 + 死边清理（9 边）
status: done
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
  status: done
last_updated: 2026-09-15
---

## 背景

Q-009（2026-09-13 登记）转出：冻结白名单 19 类 30 边全量收敛至零（计划 [capability-mapper-convergence-plan](../plans/capability-mapper-convergence-plan.md)，定案见 decision-registry 2026-09-15 行）。批次①是零语义判断面的直换与死边清理，先行降风险。

## 范围

计划文件「批次①」表 9 边 + SubjectDomainService 新增 selectEnabledRoleIds（含用例）+ 测试装配适配（计划清单批次①节）。

## 非目标 / 遗留

- 其余 21 边归后续批次；不触碰 9 边之外的任何 mapper 依赖；不改 SQL/XML。

## 完成记录

2026-09-15 收口。9 边全部落地：E5 死注入删除（字段/构造参数/import）；grant#1/#12 → `SubjectDomainService.selectValidRoleById`；grant#4/#9 → `ResourceEntityDomainService.selectValidByIds`；U1 四点 → `selectValidRolesByIds`×3 + 新增 `selectEnabledRoleIds`（接口+实现+DB 侧过滤保持）；U3 六点 → `UserOrgDomainService.findByUserId/findByUserIds/findByOrgIds`（启用既有注入）；R6 四点 → writer 私有 `requireUserProjectionId/batchResolveUserProjectionIds`（requireType 保留保 TYPE_CODE_NOT_FOUND fail-fast；行读取经 TypeResolutionService 直查同事务可见，javadoc 标注禁接结果缓存）；E9 内联 FQCN → `SubjectDomainService.selectValidRolesByIds`。装配方 `LocalProjectionDomainServiceImpl` writer 构造同步改线。

测试装配适配 8 文件：UserManageAppServiceImplTest、UserAppServiceResetPasswordGateTest、OperationLogRuntimeContextAppServiceTest、ConflictRuleAppServiceImplTest、PermissionGrantAppServiceImplTest、PermissionGrantPlanDomainServiceImplTest、GrantOriginDomainServiceImplTest、LocalProjectionDomainServiceImplTest（stub 换挂 8 处，装配方自身读保留）。

白名单同 commit 删 9 行（30→21 边/19→15 类），shape 断言与负向自证样例行同步（改取 App→PermissionConditionMapper 首行）。

回归证据：`mvn test -pl access-service -DskipTestcontainers=true` → Tests run: 1249, Failures: 0, Errors: 0（2026-09-15，日志 t043_unit2.log）；`mvn test -pl access-service` → 双 fork 1249 + 210 全绿 BUILD SUCCESS（2026-09-15，日志 t043_full_module.log；运行前核 9100 端口空闲）。设计回写：capability-structure §8.4 豁免 6 表下补收敛进度注记。
