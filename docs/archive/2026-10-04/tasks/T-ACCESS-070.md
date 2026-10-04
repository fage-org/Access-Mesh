---
doc_type: task
id: T-ACCESS-070
title: 测试规范消歧与项目测试技能接入
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#rules-skill
  - docs/design/project-rules.md#测试适用性覆盖
depends_on: 
  - T-ACCESS-069
blocks: []
acceptance:
  - "规则与技能无冲突，未降低现役安全/隔离/测试门槛"
  - "两个技能目录及 references 内容一致，AGENTS 指针和相对链接有效"
  - "新增/删测样题的流程试用分别记录输入、实际行为和结果；格式检查与行为评测分开，本卡完成不等待 T-ACCESS-076"
  - "设计 §5 回写最终职责与规范落点"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-070 测试规范消歧与项目测试技能接入

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#rules-skill)与[执行计划](../testing-simplification-plan.md)。实施已启动；方案按章节采纳，行为验证以实际报告为准。

## 范围

1. 按设计 §5 修订 testing-standards 的适用边界，保留有效轨道/隔离/覆盖率约束；TDD、Mock、DTO/配置、独立性、架构证据等就近消歧。
2. 以附件主技能和检查表为输入，使用 skill-creator 完成项目化技能；修订其中笼统的授权限制，引用当前权威，不复制硬规则。
3. 完成双副本、相对引用与 AGENTS 指针；用新增与删测样题试用流程，记录能否识别已有证据、共享 fixture 消费者和不可替代的真实 PG 证据；真实实施任务的试用效果由 T-ACCESS-076 汇总，不是本卡完成前置；未发生真实试用时如实记为未试用，不补造测试或宣称行为效果已经验证。

## 当前口径

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] 规则与技能无冲突，未降低现役安全/隔离/测试门槛
- [x] 两个技能目录及 references 内容一致，AGENTS 指针和相对链接有效
- [x] 新增/删测样题的流程试用分别记录输入、实际行为和结果；格式检查与行为评测分开，本卡完成不等待 T-ACCESS-076
- [x] 设计 §5 回写最终职责与规范落点

## 非目标 / 遗留

本卡启动包含规则/技能落地；本次建卡不等于已安装。不得改 CI 或引入测试依赖。


## 完成记录

2026-10-03：规则消歧与项目技能双副本已落地；格式、链接、主代理流程样题和本地两轨核验见[技能验收](evidence/T-ACCESS-070/skill-validation.md)。真实任务效果仍由 T-ACCESS-076 汇总。
