---
doc_type: task
id: T-ACCESS-078
title: 真实 Mapper 测试最小上下文试点
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#whole-suite
depends_on:
  - T-ACCESS-069
blocks: []
acceptance:
  - "单类 A/B 比较记录相同数据/环境、创建与执行成本，未靠跳过数据或少跑案例提速"
  - "真实 XML/参数绑定错误仍由目标断言发现，原有效行为证据无丢失"
  - "容器/方言/全局状态隔离保持，与代表邻类共跑无串扰"
  - "试点采用或维持整上下文均有明确收益/风险依据，设计全仓建议回写；不为完成任务强行推广"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-078 真实 Mapper 测试最小上下文试点

## 背景

[全仓评估证据](evidence/T-ACCESS-069/whole-suite-assessment.md)发现明确成本候选。按[实施方案](../../../design/testing-simplification.md#whole-suite)完成单类试点；采用范围与验证边界见完成记录。

## 范围

1. 以 AdminXmlPaginationPgIT 为单类试点，盘点真实 MyBatis-Flex、数据源、XML、TypeHandler、插件及事务依赖，比较整应用启动与仅装配实际 SQL 依赖的成本。
2. 仍用 ItInfra、原样 PostgreSQL、原容器 execution、独立类库及每类 DynamicPropertySource；不共享上下文或开启 JUnit 线程并行。
3. 保留所有原 XML 分页/过滤/稳定排序/伴生 count 案例，证明参数绑定、映射和实际插件未被手工 SQL 或 mock 替代；保留应用级装配与安全接线测试。
4. 仅在收益与等价性有证据后决定扩展。LocalProjectionBatchSqlIT 和 QueryReadSupportPgIT 有领域/缓存依赖，不能因名字带 SQL 自动纳入。

## 当前口径

按[证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证要求](../../../design/testing-simplification.md#validation)执行。现役[测试规范](../../../../.claude/rules/testing-standards.md)继续约束实现；技能安装不是本卡启动前置。开始前重新核对 HEAD 和当前定向报告，旧报告仅用于候选排序。

## 验收对照

- [x] 单类 A/B 比较记录相同数据/环境、创建与执行成本，未靠跳过数据或少跑案例提速
- [x] 真实 XML/参数绑定错误仍由目标断言发现，原有效行为证据无丢失
- [x] 容器/方言/全局状态隔离保持，与代表邻类共跑无串扰
- [x] 试点采用或维持整上下文均有明确收益/风险依据，设计全仓建议回写；不为完成任务强行推广

## 非目标 / 遗留

不新增通用测试框架，不整体切换 PG 轨道，不缩小 heavy 场景，不改生产扫描/配置。


## 完成记录

2026-10-03：采纳 AdminXmlPaginationPgIT 单类最小上下文，原 8 个案例不变；A/B、真实映射对照、错误 XML 与同 JVM 邻类 14 项通过见[证据](evidence/T-ACCESS-078/mapper-context-evidence.md)。不向其他 PG 类自动推广。
