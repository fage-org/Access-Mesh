---
doc_type: task
id: T-ACCESS-076
title: 测试精简综合验收与设计归位
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#validation
depends_on:
  - T-ACCESS-067
  - T-ACCESS-070
  - T-ACCESS-071
  - T-ACCESS-072
  - T-ACCESS-073
  - T-ACCESS-074
  - T-ACCESS-075
  - T-ACCESS-077
  - T-ACCESS-078
  - T-FE-063
blocks: []
acceptance:
  - "当前有效契约的证据并集保留，无未处理的独有证据丢失或活跃悬空引用"
  - "最终 Maven 含 PG/E2E/heavy 以及前端验证通过，关键轨无静默跳过，日志/命令/日期/计数可追溯"
  - "收益与验证局限可复核，不以参数化声明减少冒充执行或耗时减少"
  - "双轨结论处置完毕，设计/规则/任务/索引一致；未采纳建议未被整体标 adopted"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-076 测试精简综合验收与设计归位

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#validation)与[执行计划](../testing-simplification-plan.md)。实施与最终综合验收已完成，真实证据见完成记录。

T-ACCESS-067 保持原计划归属，本卡以外部依赖消费其时序改造结果；不复制或另建同范围任务。最终回归须覆盖全部依赖的最终改动；回归后若相关源码或测试再次变更，须重新判断受影响验证，不沿用早于改动的日志验收。

## 范围

1. 核对各批最小映射、实际执行与支持裁决；汇总项目测试技能在真实新增和删测任务中的输入、复用/新增/删除行为与效果（没有新增缺口时不为评测制造无价值测试），若实施期没有真实新增或删测任务使用技能，分别记录未试用及原因，只报告已完成的样题评测，不宣称真实任务效果已验证，也不补造无价值测试；重新搜索活跃引用和新增重复机制，统计包含 helper/参数表在内的净维护变化。
2. 按 AGENTS 执行最终 mvn test -T 1C 与前端独立测试；Maven 期间不改源码，完整日志落盘，失败先隔离定性，确认 E2E/heavy/PG 实际运行。
3. 按相同环境/轨道/缓存条件比较基线，分别报告净源码、案例、wall time 与上下文/容器启动；不能测的指标注明未测，不编造收益。
4. 进行主代理本地代码轨、文档轨评审；将已采纳规则归当前规范，设计候选写终态，未实施或被否决项明确处置，满足生命周期后归档计划与任务。

## 当前口径

本批已删除测试类；最终完整回归前先清理旧编译产物，再执行既定收口命令，避免 target/test-classes 遗留类制造错误发现集合。

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] 当前有效契约的证据并集保留，无未处理的独有证据丢失或活跃悬空引用
- [x] 最终 Maven 含 PG/E2E/heavy 以及前端验证通过，关键轨无静默跳过，日志/命令/日期/计数可追溯
- [x] 收益与验证局限可复核，不以参数化声明减少冒充执行或耗时减少
- [x] 双轨结论处置完毕，设计/规则/任务/索引一致；未采纳建议未被整体标 adopted

## 非目标 / 遗留

不因未达到数量配额追加删测；外部 AI 评审仅在用户显式要求时执行。


## 完成记录

2026-10-04：完整后端 2429 testcase 零失败/错误/跳过，前端 41 文件/484 案例及类型检查、构建通过；代码/文档双轨与输入冻结、候选映射核对完成。详见[最终验收](evidence/T-ACCESS-076/final-audit.md)。
