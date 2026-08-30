---
doc_type: task
id: T-PERM-048
title: 权限条件实例投影与双轨制——管理页条件 vs 授权页内联条件（来源字段 + resource_entity 投影 + UI）
status: proposed
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
  - "CONFLICT_RULE 域同款门禁 ID 空间错位（ConflictRuleAppServiceImpl 编码轨传内部 id，实例级同样无从配置）随本任务或 T-PERM-030 对齐收口（同口径定案：类型级或投影）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-30
---

# T-PERM-048 权限条件实例投影与双轨制——管理页条件 vs 授权页内联条件

> 状态：proposed（T-PERM-029 评审登记，2026-08-30 设计定案：投影为目标态、本次不实现）
> 依赖：无硬依赖（双轨制为产品侧构想，落地前需细化来源字段 schema 与两页交互设计）

## 背景

T-PERM-029 收口期间发现 CONDITION 写门禁的「实例级」声称系 ID 空间错位：permission_condition.id 被传入 resource_entity.id 语义的引擎轨（曾先后以编码轨传 id 字符串、实体轨传 id 两种形态存在），而实例级授权的两环节（配置侧 resource_entity 选择器选不到、存储侧 resource_entity_id 空间对不上）均不真实。2026-08-30 设计定案：写门禁收窄为类型级（现口径），实例投影为目标态另立本任务。

## 产品构想（2026-08-30，原样登记待细化）

条件分两类，管理边界互斥：

| 类别 | 创建/管理入口 | 另一侧的可见性 |
|---|---|---|
| 管理页条件 | 仅权限条件页面（CRUD） | 授权页面只能引用，不能创建/更改 |
| 授权页内联条件 | 仅授权页面（配置时内联创建/更改） | 权限条件页面查不到也不能管理 |

- 数据表：permission_condition 新增来源字段（区分两类）。
- UI：权限条件页按来源过滤只展示管理页条件；授权页条件选择器支持引用管理页条件 + 内联创建/更改授权页条件。
- 投影：落地时同步建 CONDITION→resource_entity 投影链路，再评估实例级门禁升级。

## 关联

- T-PERM-029（本任务起因，门禁口径收窄为类型级）
- T-PERM-030（CONFLICT_RULE 同款门禁错位，待同口径收口）
