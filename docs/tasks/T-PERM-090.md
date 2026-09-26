---
doc_type: task
id: T-PERM-090
title: （R2-T11）迁移范围与 LEGACY_API 接口集合
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.2/§6.4/§6.5/§6.6
depends_on:
  - T-PERM-086
  - T-PERM-087
  - T-PERM-089
blocks: []
acceptance:
  - "queryScopes 四态与父对象存在性（OBJECT_KEY_NOT_FOUND 外层返回）保持；matchedParentOperations 取 087 的 ResultDetails.parentCheck.matchedOperationCodes，按基线逐字段对拍，不重跑父判断；ScopeCoverageProjector 只消费结果与已装载定义；queryResources/有效权限码/可见资源投影保持「原 GRANT_LIST 评估→白名单/排除 API/domain/codeType/展示/分页」后置序"
  - "LEGACY_API checkInterface 经新 execute 表达共同集合语义（注册门禁在先、全部匹配 API 组成一个 TARGET_SET、不拆项 OR）；父对象存在≠父权限允许语义保持"
  - "S01~S04：快照 API:VIEW 不覆盖 ACCESS 不下发为放行依据；API scopeAll 只展开目标服务 enabled 注册路由（不产生任意通配）；无条件与各 conditionId 分支保留；坏条件维持有条件与回源 fail-closed 不变无条件"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-26
---

# T-PERM-090 （R2-T11）迁移范围与 LEGACY_API 接口集合

## 背景

设计 §6.2/§6.4/§6.6（报告临时编号 R2-T11）。LEGACY_API 是迁移期规则非终态；legacy 语义也应通过新 R2 表达（不需要旧引擎）。

## 范围

- queryScopes/queryResources/有效权限码/可见资源投影迁移；物理子孙展开在授权集合评估之后（不把展示后代提前加入互斥候选）。
- checkInterface 与 interfaceSnapshot/SnapshotAssembler 的 legacy 适配（API:ACCESS 覆盖位=ACCESS 位 ∪ inheritMask 覆盖位，设计 §6.6 精确口径）。

## 非目标 / 遗留

- 新在线 interface-admission 与新快照在 T-ACCESS-059；旧协议退役在 T-ACCESS-062。
