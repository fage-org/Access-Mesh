---
doc_type: task
id: T-PERM-035
title: 自动授权（resolveAutoGrants + autoGrantForInsert + 循环依赖检测）
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/permission-center/api-contract.md
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
  - "设计回写：自动授权流程回写 core-flows.md §12 场景九"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-29
---

# T-PERM-035 自动授权

> 状态：proposed
> ⚠️ 执行门禁：design-review §11 E4 — auto-grant 保留 TODO + 排期 Phase X（未排期）。进入 in-progress 前必须 PM 重申解除暂缓。

## decision_refs（暂缓依据，非实现依据）

> 归档文档仅作决策溯源，**不作为实现依据**，不进入 design_refs 回写范围。

- `docs/archive/2026-06-17/design-review.md` §11 E4 — auto-grant 保留 TODO + 排期 Phase X（未排期）
- `docs/plans/improvement-plan.md` §3.1 痛点 #3 — 已标暂缓（2026-06-20 审计 S-026）

## 背景

自动授权是权限平台核心差异化能力：授予角色 A「查看订单」时，系统自动补全其依赖权限（「登录系统」「查看菜单」），而非管理员手动逐条授予。对应 improvement-plan §3.1 痛点 #3。

## 当前状态

- `resource_dependency` 表已设计（access-service.sql）
- `PermissionGrantAppServiceImpl` TODO：`自动授予依赖权限（autoGrantForInsert）`（L130）
- `PermissionGrantDomainService` / Impl TODO：`自动授权解析（resolveAutoGrants）`（L13/L39）

## 实现路径（improvement-plan §2.1）

1. 分析 resource_dependency 表结构和现有依赖数据
2. resolveAutoGrants：解析依赖链，计算需补全权限
3. autoGrantForInsert：批量授权时补全
4. 循环依赖检测
5. 依赖冲突处理
6. 区分 GrantSource.MANUAL vs AUTO_DEP
7. 撤销级联处理

## 验收标准

见 acceptance。冲突项有明确设计决策记录，设计回写完成后才允许 done。
