---
doc_type: task
id: T-ACCESS-029
title: bootstrap 固定图授权收缩通道——软删墓碑三分判定
status: done
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
  status: done
last_updated: 2026-09-05
---

# T-ACCESS-029 bootstrap 固定图授权收缩通道——软删墓碑三分判定

> 状态：done（2026-09-05 收口）
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

## 完成记录

- 2026-09-05 实施：mapper `RoleResourcePermissionMapper.selectSoftDeletedByRoleIds`（镜像 `selectValidByRoleIds`，`delete_flag != 0`）；`BootstrapSeedWriter.findSoftDeletedGrants` 诊断例外方法（按角色查软删授权历史全集，身份键匹配在调用方内存按 `GrantIdentity` 等值完成——scopeAll 行 `resource_entity_id` 为 NULL，SQL `= NULL` 恒不命中故不下推 SQL）；`AccessBootstrapInitializer` 授权缺行改三分判定 `classifyMissingGrants`（缺行 + 墓碑 → WARN 列明授权键放行不补回 / 缺行 + 无历史 → 冲突消息注明「无软删墓碑」维持 fail-fast；墓碑查询仅在实际存在缺行时执行一次）；缺行冲突与墓碑告警共用 `grantIdentityDesc` 键描述（scopeAll 行不再拼接尾部 null 字样）。文档：architecture §14.2 升级边界扩写（「新版新增条目缺行且无墓碑仍拒启」不自动补权 + 资源实体删除重建换 `resource_entity_id` 的墓碑判定边界 + runbook 交叉引用）+ last_reviewed 注记；rebuild-runbook 前置条件补三分语义、旧图升级行补注（升级/重建边界）、新增「墓碑 WARN 现象」处置行（误删恢复=授权页重授；全瘫=SQL 复活墓碑行或重建库）。回归锁 `AccessBootstrapPgIT` 17/17（原 16 + 新 1，真实 PostgreSQL/Redis 容器）：新增 Order(5) 墓碑缺行放行用例（软删 `OPERATION_LOG:VIEW` scopeAll——`resource_entity_id=NULL` 身份键 NULL 语义回归锚——与 `POST:/admin/user/create` API 实例 ACCESS 各一条；Log4j2 内嵌捕获 appender 断言 WARN 列明两条授权键、initialize 不抛、两身份键有效行数仍 0=不补回；旧实现缺行一律 fail-fast，本用例必失败）；原缺行用例（现 Order(7)）构造方式由软删改硬删 `DELETE`——三分判定下软删缺行走墓碑 WARN 放行，硬删/残缺才是「无任何历史」拒启分支的构造方式，断言「授权缺失」维持。access-service 全量回归绿。
- 2026-09-05 双轨评审收口批次（代码正确性与安全边界 + 规范符合性与文档一致性两轨并行只读，无 P0/P1/P2 发现；三项 P3 文案修正用户定案全采纳）：`GrantIdentity`/`GrantKey` record Javadoc 旧口径订正（缺行处置指向 classifyMissingGrants 三分）；漂移 WARN 文案统一 `grantIdentityDesc`（scopeAll 行不再拼 `@ALLnull` 尾巴，与缺行/墓碑告警同构——既有问题顺手修）；runbook 墓碑行键格式改真实形态示例（`30#bits=2@ALL` / `3#bits=16@instance197`）+ 复活 SQL 补尾分号。评审撤回一项（AGENTS.md 编号漏列系代理误引，实际 T-ACCESS-029 在列）；维持现状两项（mapper 新方法无自动防扩散守卫=与既有 mapper 方法同水位、`queryForMap` 单行假设破坏时 fail-loud 不假绿）。用户确认**墓碑身份键粒度维持定案**：同身份键（资源/范围+类型）即豁免、不含操作位——升级场景新增同类型同范围操作位缺行命中旧墓碑 WARN 放行为既定语义。范围外登记：`frontend-phase3-plan.md` frontmatter status（proposed）与 Phase 3 收官宣告不一致，属仓库治理项非本任务引入。定向 `AccessBootstrapPgIT` 17/17 复绿。
