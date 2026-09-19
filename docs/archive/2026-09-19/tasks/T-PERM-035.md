---
doc_type: task
id: T-PERM-035
title: 自动授权（resolveAutoGrants + autoGrantForInsert + 循环依赖检测）
status: cancelled
plan: —（2026-09-14 脱出 frontend-phase2 随计划归档）
domain: permission-center
design_refs:
  - docs/design/dependency-auto-grant.md（取代依据；原 engine/core-flows §12、implementation、契约 §12.4 回写义务已转移至 T-PERM-072/073）
depends_on:
  - T-PERM-034
blocks: []
acceptance:
  - "执行门禁：design-review §11 E4 决策 auto-grant 保留 TODO + 排期 Phase X（未排期）；进入 in-progress 前必须 PM 重申解除暂缓"
  - "resolveAutoGrants：根据授权资源解析依赖链，计算需补全的权限"
  - "autoGrantForInsert：批量授权时自动补全依赖权限"
  - "循环依赖检测：防止 A→B→A 死循环"
  - "依赖冲突处理：两个依赖路径对同一资源定义不同操作级别"
  - "区分手动/自动授权（GrantSource.MANUAL vs AUTO_DEP）"
  - "撤销授权级联处理：手动授权撤销时，仅当无其他路径依赖才撤销自动授权"
  - "设计回写：自动授权流程回写 engine/core-flows.md §12 场景九"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-19
---

# T-PERM-035 自动授权

> 状态：cancelled（2026-09-19）
> 被新设计取代：实例级依赖与自动授权 v1 设计定稿（docs/design/dependency-auto-grant.md + service-authentication.md，adopted，2026-09-19 用户确认定稿）——本卡旧口径（resolveAutoGrants/autoGrantForInsert）由拆分卡 T-PERM-070（前置·服务认证）/071（声明层）/072（物化）/073（观测）承接；原 design-review §11 E4 暂缓门禁随定稿解除。

## decision_refs（暂缓依据，非实现依据）

> 归档文档仅作决策溯源，**不作为实现依据**，不进入 design_refs 回写范围。

- `docs/archive/2026-06-17/design-review.md` §11 E4 — auto-grant 保留 TODO + 排期 Phase X（未排期）
- `docs/archive/2026-08-27/improvement-plan.md` §3.1 痛点 #3 — 已标暂缓（2026-06-20 审计 S-026；计划本体已归档，暂缓决策仍有效）

## 背景

自动授权是权限平台核心差异化能力：授予角色 A「查看订单」时，系统自动补全其依赖权限（「登录系统」「查看菜单」），而非管理员逐条手动授予。对应已归档 improvement-plan（[archive/2026-08-27/](../archive/2026-08-27/improvement-plan.md)）§3.1 痛点 #3。

## 当前状态

- `resource_dependency` 表已设计（access-service.sql）
- `PermissionGrantAppServiceImpl` 原 `autoGrantForInsert` TODO 已随旧写入口端点退役移除（2026-08-27）；T-PERM-035 实现时在 apply-grant-plan 唯一写入口链路新增
- `PermissionGrantDomainService` / Impl TODO：`自动授权解析（resolveAutoGrants）`（L13/L39）

## 实现路径（已归档 improvement-plan §2.1）

1. 分析 resource_dependency 表结构和现有依赖数据
2. resolveAutoGrants：解析依赖链，计算需补全权限
3. autoGrantForInsert：批量授权时补全
4. 循环依赖检测
5. 依赖冲突处理
6. 区分 GrantSource.MANUAL vs AUTO_DEP
7. 撤销级联处理

## 验收标准

见 acceptance。冲突项有明确设计决策记录，设计回写完成后才允许 done。
