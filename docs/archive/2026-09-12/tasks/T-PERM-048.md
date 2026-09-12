---
doc_type: task
id: T-PERM-048
title: 权限条件实例投影与双轨制——管理页条件 vs 授权页内联条件（来源字段 + resource_entity 投影 + UI）
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.6
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "CONDITION 无 resource_entity 实例投影、role_resource_permission.resource_entity_id 引用 resource_entity.id 空间——实例级授权无从配置（授权页选不到、DB 配不进）；T-PERM-029 期间（2026-08-30 设计定案）写门禁先收窄为类型级（scope_all，与 OPERATION/SYSTEM_CONFIG 同款），实例投影为目标态、时机另定（本任务）"
  - "条件双轨制（2026-08-30 产品构想，原样登记）：条件分两类——①权限条件页面管理的条件：只能在权限条件页面管理，授权页面只能**引用**；②授权页面配置的条件：在权限条件页面**查不到也不能管理**，只能在授权页面更改。permission_condition 表需新增来源字段区分两类；两页 UI 交互同步更新（权限条件页按来源过滤，授权页支持内联条件的创建/更改）"
  - "CONDITION→resource_entity 实例投影链路：条件创建/删除同步登记/清理 resource_entity 行（resourceType=CONDITION），授权页资源选择器按双轨规则纳入可引用条件；投影落地后评估 CONDITION 写门禁是否从类型级升级实例级（含既有 scope_all 授权兼容）"
  - "CONFLICT_RULE 域同款门禁 ID 空间错位（ConflictRuleAppServiceImpl 编码轨传内部 id，实例级同样无从配置）已随 T-PERM-030 于 2026-08-30 同口径收口（类型级，bootstrap 固定图同步补 CONFLICT_RULE 四档）；CONDITION 侧剩余范围为实例投影与双轨制本体"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-11（收口 done；完成记录见下）
---

# T-PERM-048 权限条件实例投影与双轨制——管理页条件 vs 授权页内联条件

> 状态：✅ done（2026-09-11 收口；五项设计定案见 decision-registry 同日行）
> 依赖：无硬依赖

## 完成记录（2026-09-11）

**实施批次（六 commit）**：

1. `eede76ac8` docs：五项定案落盘（registry）+ schema（source 列 + CHECK + SYNC 族第七类型 CONDITION）
2. `ed115f5b6` CONDITION 投影链路（LocalProjectionDomainService 三方法：upsert/softDelete/backfill 仅 MANAGED 附野行 WARN）+ ConditionSource 枚举 + bootstrap 自愈 + OwnershipGuard/LocalProjectionOwner 七类型口径
3. `b383299f0` 管理页轨：update/remove 升实例级门禁（scope_all 零破坏）、删除引用守卫 20059（挂靠引用+投影行实例授权两类）、双轨防线 20060 三面、list includeInline 双轨、投影生灭同事务、规则校验收敛 assertConditionRulesValid（双轨共享）+ 回归锁 10 例
4. `b180a056c` grant-plan 内联轨：InlineConditionDef（GrantRecordKey/UpdateItem 挂载、二选一互斥）、conditionCode 引用轨值域焊死 MANAGED（20060）、同事务创建/就地编辑/引用归零回收（PreparedGrantPlan.inlineRecycleCandidates → apply 末段）、20043 覆盖内联 + 回归锁 9 例 + 签名快照更新
5. `988a06129` 前端两页：授权页条件控件三态（picker 过滤 MANAGED + 「内联条件…」入口 + ReConditionEditor 就地编辑/预载/确认前校验）、显式复制内联深拷贝（1:1 不复制引用）、grant-plan 纯函数全链 inlineCondition（noop 过滤含内联在场）、矩阵条纹/节点摘要同口径 + 回归锁 8 例（222 vitest 全绿）
6. `2489cf085` 容器验收 ConditionProjectionDualTrackPgIT 六例（真实 PG+Redis：backfill 幂等仅 MANAGED/投影生灭+回滚/实例门禁三态/守卫 20059 两类/内联全生命周期含失败零残留/20060 三面+引用焊点）

**验收对账**（对照 acceptance 四条）：

- 投影链路 ✅：条件写路径同事务维护 resource_entity(CONDITION)（code=条件 code、status 镜像 enabled——停用自动隐出授权资源树）；bootstrap 自愈补种存量；删除同事务清理投影（守卫通过后）。
- 双轨制 ✅：source 列 CHECK 焊死；管理页条件仅条件页 CRUD（实例级门禁）；内联条件仅授权页随记录更改（20060 三面 + 引用轨焊死 + 生命周期同事务）；两页 UI 按来源口径（条件页零改动天然隔离 / 授权页 picker 过滤 + 内联入口）。
- 门禁升级评估 ✅（定案④升实例级）：update/delete 实例级 CONDITION:UPDATE/DELETE@{code}，create 维持类型级；scope_all 存量授权 passesScopeAll 天然覆盖全部实例零破坏（PgIT 实证）。
- CONFLICT_RULE 侧 ✅（T-PERM-030 已收口，无剩余范围）。

**设计回写**：api-contract（§5.6 契约要点重写 + §6.5.1 内联轨契约 + §5.1/§6.2.2 六→七类型 + frontmatter）、schema（commit ①）、access-service-architecture（§4.3 CONDITION 入族 + §12.3 投影编码条目）、implementation §2.5（内联生命周期扩展）、design/frontend 两页（condition §9 / grant §1.2 双轨分工）、rebuild-runbook FAQ（存量库 ALTER+补投影+野行检测）、decision-registry 同日行。

## 背景

T-PERM-029 收口期间发现 CONDITION 写门禁的「实例级」声称系 ID 空间错位：permission_condition.id 被传入 resource_entity.id 语义的引擎轨（曾先后以编码轨传 id 字符串、实体轨传 id 两种形态存在），而实例级授权的两环节（配置侧 resource_entity 选择器选不到、存储侧 resource_entity_id 空间对不上）均不真实。2026-08-30 设计定案：写门禁收窄为类型级（现口径），实例投影为目标态另立本任务。

## 产品构想（2026-08-30 登记，已随 2026-09-11 五项定案落地收口）

条件分两类，管理边界互斥：

| 类别 | 创建/管理入口 | 另一侧的可见性 |
|---|---|---|
| 管理页条件 | 仅权限条件页面（CRUD） | 授权页面只能引用，不能创建/更改 |
| 授权页内联条件 | 仅授权页面（配置时内联创建/更改） | 权限条件页面查不到也不能管理 |

## 关联

- T-PERM-029（本任务起因，门禁口径收窄为类型级）
- T-PERM-030（CONFLICT_RULE 同款门禁错位，已随其 2026-08-30 收口为类型级；CONDITION 实例投影与双轨制仍归本任务）
