---
doc_type: task
id: T-ACCESS-159
title: 边界修复联合验收与计划归档
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/project-rules.md
  - docs/design/access-service-architecture.md
  - docs/design/engine/implementation.md
  - docs/ops/engineering-baseline.md
depends_on:
  - T-PERM-113
  - T-FE-081
  - T-PERM-114
  - T-PERM-115
  - T-ACCESS-151
  - T-ACCESS-152
  - T-ACCESS-153
  - T-FE-082
  - T-FE-083
  - T-ACCESS-154
  - T-ACCESS-155
  - T-PERM-116
  - T-ACCESS-156
  - T-PERM-117
  - T-ADMIN-036
  - T-PERM-118
  - T-PERM-119
  - T-PERM-120
  - T-PERM-121
  - T-ACCESS-157
  - T-ADMIN-037
  - T-FE-084
  - T-FE-085
  - T-ACCESS-158
acceptance:
  - "原报告快照及覆盖映射均有去向；未决方案为零，新增实现卡全部加入 tasks 数组与依赖。"
  - "缓存/分页/查询观测/审计饱和及获准结构改动有适用证据，保留的输入/快照/权限/会话边界有效。"
  - "根目录 mvn test -T 1C 整文件落盘，E2E/heavy 实跑且不带跳过开关；失败先隔离复跑定性；前端完成适用 pnpm 门禁与页面冒烟。"
  - "完成本地双轨核对和文档/链接/状态检查，不自动调用外部 AI；design_writeback 完成后按技能归档所有卡与证据。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） 建议的处理顺序、原报告全部范围；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

全部报告条目已有任务或既有载体后，需要对实际交付与用户决定做联合核对，不能将规划完成或历史测试通过当作修复完成。

## 范围

核对源报告映射、全部用户定案、依赖和设计回写；执行修复后的适用联合场景、本地代码/文档双轨核对及收口回归，修复索引并归档。

## 当前口径

依赖卡完成且新增实现任务已纳入计划后才启动。若某建议维持现状或暂缓，必须有明确用户决定及所属规范/已有载体；本卡不得替用户做裁剪或风险接受。全量只在收口运行，先停 9100 dev 服务，测试期间不改源码。

权威回写位置：[docs/design/project-rules.md](../../docs/design/project-rules.md)、[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)、[docs/ops/engineering-baseline.md](../../docs/ops/engineering-baseline.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 原报告快照及覆盖映射均有去向；未决方案为零，新增实现卡全部加入 tasks 数组与依赖。
- [ ] 缓存/分页/查询观测/审计饱和及获准结构改动有适用证据，保留的输入/快照/权限/会话边界有效。
- [ ] 根目录 mvn test -T 1C 整文件落盘，E2E/heavy 实跑且不带跳过开关；失败先隔离复跑定性；前端完成适用 pnpm 门禁与页面冒烟。
- [ ] 完成本地双轨核对和文档/链接/状态检查，不自动调用外部 AI；design_writeback 完成后按技能归档所有卡与证据。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

