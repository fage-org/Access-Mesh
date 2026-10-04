---
doc_type: plan
title: 测试证据精简与维护成本优化计划
status: archived
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md
  - docs/design/project-rules.md
tasks:
  - T-ACCESS-069
  - T-ACCESS-070
  - T-ACCESS-071
  - T-ACCESS-072
  - T-ACCESS-073
  - T-ACCESS-074
  - T-ACCESS-075
  - T-ACCESS-077
  - T-ACCESS-078
  - T-FE-063
  - T-ACCESS-076
acceptance: "候选完成裁决与证据闭合；当前契约不退化；完整后端与前端回归有实跑证据；设计与规范归位"
last_updated: 2026-10-04
---

# 测试证据精简与维护成本优化计划

## 目标

依据[实施设计](../../design/testing-simplification.md)，先确保证据可信，再减少固定等待、不必要的启动和重复表达，保留当前有效行为和稳定架构证据。以维护成本与实际运行证据评估效果，不预设删文件/方法/耗时比例。

## 非目标

除明确确认的两条旧库支持退出及对应提示外，不改生产功能、其他支持范围、CI、依赖版本、测试隔离与并发拓扑。

## 准入条件

- 启动时确认设计适用范围与实施基线，按 T-ACCESS-069 更新候选映射。
- 按现役规范先处理有据修正，T-ACCESS-070 的技能安装不作为所有任务硬前置；具体规则冲突在相应操作前就近消歧。
- 历史删除须满足[支持边界](../../design/testing-simplification.md#support-boundary)；待定时只阻止对应删除，不阻塞其他独立批次。
- 与现有问题清单计划的关系以 T-ACCESS-069/073 的增量核对为准，不给全部既有业务任务增加无关依赖。

## 任务清单

状态与依赖以[任务看板](../../tasks/README.md)和任务 frontmatter 为准。

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-069](tasks/T-ACCESS-069.md) | 测试精简基线与候选范围确认 | ✅ | — |
| [T-ACCESS-070](tasks/T-ACCESS-070.md) | 测试规范消歧与项目测试技能接入 | ✅ | T-ACCESS-069 |
| [T-ACCESS-071](tasks/T-ACCESS-071.md) | Java 测试同结构表达与夹具精简 | ✅ | T-ACCESS-069 |
| [T-ACCESS-072](tasks/T-ACCESS-072.md) | Schema 证据迁至 PG 与连接烟测去重 | ✅ | T-ACCESS-069 |
| [T-ACCESS-073](tasks/T-ACCESS-073.md) | 历史迁移支持裁决与测试资产闭合 | ✅ | T-ACCESS-069 |
| [T-ACCESS-074](tasks/T-ACCESS-074.md) | QueryMapper 契约测试去除文本窗口耦合 | ✅ | T-ACCESS-069 |
| [T-ACCESS-075](tasks/T-ACCESS-075.md) | E2E 公共支持代码精简 | ✅ | T-ACCESS-069 |
| [T-ACCESS-077](tasks/T-ACCESS-077.md) | 网关重连测试可控调度与等待成本优化 | ✅ | T-ACCESS-069 |
| [T-ACCESS-078](tasks/T-ACCESS-078.md) | 真实 Mapper 测试最小上下文试点 | ✅ | T-ACCESS-069 |
| [T-FE-063](tasks/T-FE-063.md) | 前端测试按能力复用与参数表达精简 | ✅ | T-ACCESS-069 |
| [T-ACCESS-076](tasks/T-ACCESS-076.md) | 测试精简综合验收与设计归位 | ✅ | T-ACCESS-067, T-ACCESS-070, T-ACCESS-071, T-ACCESS-072, T-ACCESS-073, T-ACCESS-074, T-ACCESS-075, T-ACCESS-077, T-ACCESS-078, T-FE-063 |

建议顺序：报告/基线与 PG 证据可信 → 既有会话时序及网关重连等待 → 单类 Mapper 上下文试点 → 局部夹具/表达精简 → 规则与技能沉淀、历史范围裁决 → 综合验收。依赖允许的独立批次可以分别执行；不同任务不得在 Maven 运行期间并发修改被编译源码。

外部依赖 [T-ACCESS-067](tasks/T-ACCESS-067.md) 保持归属[问题清单计划](pending-problems-clearance-plan.md)；本计划引用其结果，不重复登记。Mapper 试点允许维持原状并给出依据，不强制全仓推广。

## 归档条件

所有任务 done/cancelled，取消任务完成依赖重连；验收、回写和[设计验证要求](../../design/testing-simplification.md#validation)满足，当前规则与历史来源有清晰落点。按文档生命周期迁移计划、任务及附属证据并更新索引。

历史支持决定保留时，T-ACCESS-073 可完成“不退出”的有据裁决；不为达成精简数量强制删测。

## 当前进度

2026-10-04：所有所属任务完成，外部依赖 T-ACCESS-067 完成；完整后端/前端验收与设计归位见 T-ACCESS-076，计划及所属任务/证据归档。

