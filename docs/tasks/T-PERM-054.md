---
doc_type: task
id: T-PERM-054
title: 手工 API 映射绑定非 API 资源处置——方案 A 落地收口
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §7/§8.1
  - docs/design/access-service-api-contract.md §12.2
  - docs/design/schema/access-service.sql
depends_on:
  - T-ACCESS-058
  - T-ACCESS-061
blocks: []
acceptance:
  - "原卡三问全部闭合：①入口语义=required_operation_id 显式业务操作引用（接口→业务 type-operation），取代「仅 API 类型资源」一刀切与「绑定即联动」两种旧候选；②联动语义=方案 A 两层判定（网关操作准入 MAY_ENTER+业务实例最终鉴权），不再授 API:ACCESS；③存量核对=运行库盘点处置完成（T-ACCESS-061 盘点清单为准）"
  - "存量非 API 映射逐条盘点处置：显式找到登记 API 并补准入操作，或清理；不凭旧菜单类型猜 VIEW；不确认的数据不启新模式"
  - "非 API 死配置消灭：新模式下不存在「绑定即可达」或「绑定即恒 deny」通道（缺 required operation 的登记=配置故障阻断，不静默）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-054 手工 API 映射绑定非 API 资源处置——方案 A 落地收口

## 背景

原问题（2026-09-05 设计体检 P2-3）：`addApiMapping`/`updateApiMapping` 仅校验资源存在不校验类型，非 API 类型可建映射；运行时 `forInterfaceCheck` 固定 `Set.of("API")+ACCESS` 过滤——非 API 绑定为恒 deny 死配置（fail-closed 无越权，P2）。

方向定案（2026-09-09 registry）：API 不单独授权、接口权限由操作权限关联派生。**方案定稿（2026-09-25）**：统一设计 `r2-unified-query-and-admission.md`（v3.1 adopted）落地方案 A，本卡解除暂缓并归入计划 r2-query-engine-and-admission。

## 范围

本卡为**收口监督卡**：映射模型/同步/管理面实施在 T-ACCESS-058，业务最终检查与存量盘点执行在 T-ACCESS-061，API 独立授权退役在 T-ACCESS-062；本卡验收=原三问闭合+存量处置完成+死配置通道消灭。

## 非目标 / 遗留

- 不恢复「映射到菜单/其他类型资源→该资源权限联动放行路由」的依赖自动补全式语义（与 resource_dependency 的关系已在设计 §8.1 拍板：不编译为 resource_dependency、不借 depend_on 表达 API 关联）。
