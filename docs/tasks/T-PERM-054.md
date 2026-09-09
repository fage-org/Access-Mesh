---
doc_type: task
id: T-PERM-054
title: 手工 API 映射绑定非 API 资源的处置——暂缓（关联权限自动授权方向待讨论）
status: proposed
plan: docs/plans/design-audit-followup-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.4
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "启动门禁（暂缓卡）：**方向已定（2026-09-09 registry 登记：API 不单独授权、接口权限由操作权限关联派生——授操作权限即有接口权限，网关按 {资源类型:操作} 映射 API 鉴定），方案未定**；方案讨论定案后重写本卡验收并解除暂缓；讨论范围见『讨论范围』三问（第 1/2 问的方向已有上述定案倾向，具体机制与入口语义仍待设计）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-PERM-054 手工 API 映射绑定非 API 资源的处置——暂缓（关联权限自动授权方向已定、方案未定）

> 状态：proposed（**暂缓执行**，2026-09-05 设计体检 P2-3 问题二，定案单独立题暂缓；2026-09-09 统一引擎 grill 定案补方向：API 不单独授权、接口权限由操作权限关联派生，维持暂缓待方案设计）
> 依赖：无

## 背景

`addApiMapping`/`updateApiMapping` 仅校验资源存在不校验资源类型，非 API 类型资源（如 MENU）可建映射；运行时 `forInterfaceCheck` 固定 `Set.of("API")+ACCESS` 类型过滤 → 非 API 绑定映射为永远 deny 的死配置（fail-closed，无越权通道，定级 P2）。

机制上一刀切「入口拒绝非 API 资源」即可收口，但 2026-09-05 定案不简单以入口拒绝收口：**「映射到菜单/其他类型资源 → 该资源权限联动接口权限」与 resource_dependency 的依赖自动补全授权同族**，属「关联权限自动授权」设计方向，值得整体讨论后再定入口语义。

## 讨论范围（启动时展开）

1. 入口是否收紧为「仅 API 类型资源」为基础语义；
2. 非 API 映射是否赋予「该资源授权联动放行对应路由」的语义（与 resource_dependency 自动补全的关系：复用/区分/互斥）；
3. 存量核对（现绑非 API 资源的映射，预期为零，发现即处置）。

## 范围

- 暂缓期无实现交付；定案后重写验收。
