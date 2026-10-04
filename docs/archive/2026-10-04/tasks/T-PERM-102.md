---
doc_type: task
id: T-PERM-102
title: 外部查询不开放 TRACE 的适配边界核验
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/engine/implementation.md §3.5（条件评估、互斥与审计证据——TRACE 输出）
depends_on: []
blocks: []
acceptance:
  - "核对全部生产查询适配层固定 trace=false，外部 DTO 不透传 TRACE；以实际请求捕获验证边界"
  - "明确外部查询不开放 TRACE，不增加无调用方的诊断权限或引擎身份机制"
  - "implementation §3.5 与查询契约说明内部测试诊断边界；未来新增真实诊断入口须先定义身份与授权"
  - "内部 TRACE 复用真实阶段、零额外查询的既有验证保持；不假称当前外部请求存在诊断数据泄露"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-PERM-102 外部查询不开放 TRACE 的适配边界核验

## 背景

承接 [Q-045](../../../pending-problems.md#q-045)：普通 execute(trace=true) 可返回真实角色、权限 ID、互斥命中和父绑定证据；设计要求敏感诊断授权，门禁交付按 T-PERM-088 安排暂缓（2026-09-26 拍板「暂不考虑敏感字段问题，先记录」）。若适配层把 trace 参数直通外部请求且未授权，调用方可探测他人授权结构及条件归属。早期引擎无生产消费者时无该暴露面；089+ 已接线后本约束需兑现。

## 范围

生产适配边界盘点、验证与契约归位；TRACE 数据结构和内部测试能力不变。

## 当前口径

当前生产调用全部固定 trace=false，外部 DTO 没有 trace 字段，OutputSpec.full() 没有生产调用方。外部查询不开放 TRACE，补充适配边界验证；未来出现真实诊断入口时再定义调用身份与诊断授权，不新增空置门禁机制（2026-10-04 确认）。原验收中的「旧实现外部直通」场景不可达，不作为缺陷复现实证。

## 非目标 / 遗留

- TRACE 输出内容裁剪（门禁解决访问面，不裁数据）。

## 验收对照

- [x] 核对全部生产查询适配层固定 trace=false，外部 DTO 不透传 TRACE；以实际请求捕获验证边界
- [x] 明确外部查询不开放 TRACE，不增加无调用方的诊断权限或引擎身份机制
- [x] implementation §3.5 与查询契约说明内部测试诊断边界；未来新增真实诊断入口须先定义身份与授权
- [x] 内部 TRACE 复用真实阶段、零额外查询的既有验证保持；不假称当前外部请求存在诊断数据泄露

## 完成记录

2026-10-04：定向验证与本地代码/文档双轨核对完成；完整 `mvn test -T 1C` 共 2462 项，零失败/错误/跳过，包含 E2E 与 heavy。[定向证据](evidence/T-PERM-102/verification.md)，[完整回归](evidence/pending-problems-clearance/batch-2026-10-04.md)。
