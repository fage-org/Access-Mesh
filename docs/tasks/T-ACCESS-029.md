---
doc_type: task
id: T-ACCESS-029
title: bootstrap 固定图授权收缩通道——软删墓碑三分判定
status: proposed
plan: docs/plans/design-audit-followup-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md#§14.2
depends_on: []
blocks: []
acceptance:
  - "授权缺行三分判定（2026-09-05 定案，架构 §14.2 已登记）：空库照常初始化；缺行 + 同身份键存在软删墓碑（delete_flag=id 历史行）→ WARN（列明具体授权键）放行、不补回；缺行 + 无任何历史记录 → 维持 fail-fast（初始化残缺/键被占用/硬删）"
  - "墓碑查询限定诊断例外：BootstrapSeedWriter 增加按身份键查含软删行的授权历史方法，仅 bootstrap 校验内部使用，不外泄为通用「查历史软删」查询面；**身份键 NULL 语义（复评审补）**：GrantIdentity 的 resource_entity_id 为 NULL 的 scopeAll 行按 `IS NULL` 匹配（SQL `= NULL` 恒不命中）"
  - "升级路径口径成文（不改行为）：固定图随版本增长时「新版新增条目缺行且无墓碑仍拒启」的现状语义与 runbook 处置步骤写入架构文档（rebuild-runbook 交叉引用）；**墓碑判定边界（复评审补）**：资源实体删除后重建会换 resource_entity_id，旧墓碑身份键不匹配新行 → 仍按缺行 fail-fast（判定合理，随升级口径一并成文）"
  - "回归锁（须在旧实现下失败）：三种库状态各自启动结果——正常 no-op / 墓碑缺行放行（旧实现 fail-fast，须失败）/ 无历史缺行拒启；WARN 内容断言列明缺失授权键"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-05
---

# T-ACCESS-029 bootstrap 固定图授权收缩通道——软删墓碑三分判定

> 状态：proposed（§14.2 待议清单 2026-09-05 定案承接实现）
> 依赖：无

## 背景

现状授权缺行一律 fail-fast（`AccessBootstrapInitializer` 缺行判定），管理端合法整行撤销固定图授权（软删）后 `bootstrap=true` 重启即拒启，与初始化残缺不可区分——固定图授权没有合法收缩通道（§14.2 未终案项）。

## 设计口径（2026-09-05 定案）

- **墓碑三分**：缺行 + 软删墓碑 = 管理端撤销过 → WARN 放行不补回；缺行 + 无历史 = 残缺 → 维持 fail-fast。
- 墓碑可靠性依据：bootstrap 单事务创建（崩溃整体回滚不留半图）；`role_resource_permission` 应用层软删路径即管理端（apply-grant-plan removes + 级联），`softDeleteBatch` 物理保留行（`SET delete_flag=id`）。
- 已知取舍（定案接受）：**误删与故意撤销不可区分**（同「属性漂移放行」先例，误删后仅 WARN、权限不补回）；**全瘫场景**（撤销全部管理 API 授权锁死）仍需人工恢复——现状 fail-fast 同样不恢复权限，只是更吵的报警器且搭上服务起不来。

## 范围

- 三分判定 + 墓碑查询 + 升级口径成文 + 回归锁。

## 非目标

- 不做撤销白名单、不做自动补回、不改「属性漂移放行」既有语义；
- 不做固定图→租户初始化引擎（演进方向已登记 §14.2，另行立项）。
