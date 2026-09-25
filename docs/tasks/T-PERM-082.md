---
doc_type: task
id: T-PERM-082
title: （R2-T03）新请求/结果模型与合法组合
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §2/§3.1/§3.3/§4.1
depends_on:
  - T-PERM-080
blocks: []
acceptance:
  - "QueryRequest/QueryItem/四种 Selection（TYPE_LEVEL/TARGET_SET/GRANT_LIST/OPERATION_ADMISSION）/三种 ResultForm/OutputSpec/EvaluationCoverage 模型与不可变构造、受控工厂落地；合法组合表与首版混批约束执行前整体结构校验"
  - "C01~C08 契约单测全绿：空请求零权限 I/O；DECISION+PRESERVE/SKIP 结构错误；GRANT_LIST+DECISION 混批结构错误；Roles(empty) 返回 NO_ROLE 不回退登录用户；重复 key/空 type/空 op 校验错误；防御性复制；attributes 不可伪造保留键"
  - "三结果不互冒充：GrantSetResult 无 allowed()，AdmissionResult 不实现最终授权布尔且恒 finalCheckRequired=true"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-082 （R2-T03）新请求/结果模型与合法组合

## 背景

设计 §2/§3 的内部契约模型与 §4.1 职责划分（报告临时编号 R2-T03）。内部 QueryRequest 不直接作为 Controller JSON DTO；外部业务键由适配层转换。

## 范围

- record 模型 + sealed Selection/ResourceRef + 合法组合矩阵 + 混批约束的结构校验（QueryValidationException 与外部参数错误映射分界，§3.4）。
- RunState 生命周期骨架（单次执行态、注入 Clock 固定服务端时刻；不含跨请求复用）。
- 时区拍板落码：不引入 ZoneId 抽象，评估时刻沿进程本地时钟（设计 §2.2 定稿句）。

## 非目标 / 遗留

- 不动旧执行体（迁移在 T-PERM-089+）；本卡产物与旧引擎并行存在直至 T-PERM-092。
