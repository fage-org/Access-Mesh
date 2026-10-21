---
doc_type: task
id: T-ACCESS-145
title: 资源归属与引用实施契约定稿
status: proposed
plan: docs/plans/resource-association-plan.md
domain: cross-service
design_refs:
  - docs/design/resource-association.md#contract-gate
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md#permission-query
  - docs/design/frontend/permission-grant.md
  - docs/design/example-service-api-contract.md
  - docs/design/example-permission-demo.md
depends_on: []
acceptance:
  - "G1–G4 均有可执行的终态规则、具体正反场景及权威落点，无未决产品语义流入下游。"
  - "契约覆盖资源/关系维护分权、主资源来源资格、类型模式切换、树继承、ALL/DIRECT/DEPENDENT、解绑再绑及父 ALL 来源收集。"
  - "API 字段、错误分类、分页/批量与响应预算、增量/FULL 版本和清理边界、SDK 与消费者适配已登记；DDL 约束和锁序可证明，未声称 CHECK 能跨表核基数。"
  - "查询/保存/自动依赖/准入快照/管理与同步/删除清理/种子等入口逐项标适用或不适用；example 共享范围从 SYNC 改为 MANAGED 的接线明确。"
  - "本文件与契约、引擎、前端、example 设计完成目标口径回写，依赖图仍无环，后续卡验收与定稿一致。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-21
---

## 背景

确认专属与通用两类资源的维护分工后，需要把影响授权效果的剩余语义落成可实施契约，防止按展示过滤替代运行时约束。

## 范围

完成目标设计 G1–G4 的场景走查、接口/错误/预算/身份定义、DDL 约束方案、写入与读取覆盖表；写回所属规范，保持目标与已实现状态可辨。此卡是后续代码任务的共同准入。

## 当前口径

已确认的类型性质、来源分工、操作复用和原入口不重复决策；G1/G2 的 ALL、DIRECT、父来源及解绑复用取舍按真实场景交用户确认。技术细化按现有组件与工程规范解决，不另设审批流程。只有文档定稿，不执行后端、前端或数据库变更。

详细规则以[资源关系设计](../design/resource-association.md)及上列 design_refs 为准。本卡处于 proposed；当前用户授权为制定计划和任务，不代表已启动本卡实施。

## 验收对照

- [ ] G1–G4 均有可执行的终态规则、具体正反场景及权威落点，无未决产品语义流入下游。
- [ ] 契约覆盖资源/关系维护分权、主资源来源资格、类型模式切换、树继承、ALL/DIRECT/DEPENDENT、解绑再绑及父 ALL 来源收集。
- [ ] API 字段、错误分类、分页/批量与响应预算、增量/FULL 版本和清理边界、SDK 与消费者适配已登记；DDL 约束和锁序可证明，未声称 CHECK 能跨表核基数。
- [ ] 查询/保存/自动依赖/准入快照/管理与同步/删除清理/种子等入口逐项标适用或不适用；example 共享范围从 SYNC 改为 MANAGED 的接线明确。
- [ ] 本文件与契约、引擎、前端、example 设计完成目标口径回写，依赖图仍无环，后续卡验收与定稿一致。

验证层：仅文档场景、代码对照、链接/锚点和依赖检查；不以业务测试替代语义定稿。

## 非目标 / 遗留

不重新设计 IAM 产品，不实施业务代码或可执行 DDL；后续工作分别由同计划实现卡承接。
