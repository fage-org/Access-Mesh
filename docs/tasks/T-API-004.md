---
doc_type: task
id: T-API-004
title: "可编辑字段显式清空协议贯通"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#clear-fields
  - docs/design/access-service-api-contract.md
  - docs/design/org-user-permission-contract.md
  - docs/design/frontend/type-definition.md
  - docs/design/frontend/service-interface-mapping.md
  - docs/design/project-rules.md
depends_on:
  []
blocks: []
acceptance:
  - "非空→显式清空→刷新仍空，未传/null保持原值，设置新值成功；各目标字段按矩阵有适用测试。"
  - "U006明确值+clear冲突、false以及type extra服务端键边界，清空不能破坏所有权/授权根。"
  - "JSONB/唯一手机号等真实数据库行为有PgIT证据；已有正常extraClear消费者不回退。"
  - "接口总册为字段唯一详细来源，前后端/SDK涉及面锁步；外部消费者迁移依实际盘点决定。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-API-004 可编辑字段显式清空协议贯通

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F009；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 用户phone/email，类型description/extra，服务basePath/description/extra，接口映射extra的写读矩阵。
- DTO xxxClear、领域值冲突规则、强制NULL写入与前端构造同批同步，类推相同模板。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#clear-fields)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U006：新值与Clear并存推荐拒绝；type extra可清部分必须先界定。零消费者优先同批切换，有真实外部消费者才决定窄兼容。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
