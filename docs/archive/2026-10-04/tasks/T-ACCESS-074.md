---
doc_type: task
id: T-ACCESS-074
title: QueryMapper 契约测试去除文本窗口耦合
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#candidates
  - docs/design/access-service-capability-structure.md#84-架构断言重建设计五测试能力口径
depends_on: 
  - T-ACCESS-069
blocks: []
acceptance:
  - "每条结构失败能定位文件/statement；格式变化或相邻语句不再误匹配"
  - "关键语义有实际 PG 证据，空集合守卫不能借用其他 statement 的文本"
  - "针对删除过滤/守卫等相关错误变体产生目标断言失败，未引入生产 SQL 行为变更"
  - "设计 §4 回写结构/行为证据职责与最终测试位置"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-074 QueryMapper 契约测试去除文本窗口耦合

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#candidates)与[执行计划](../testing-simplification-plan.md)。结构检查按 statement 实施；真实 PG 证据以本卡实跑记录为准。

## 范围

1. 仅针对现有 QueryMapperXmlContractTest 与 org/menu/role QueryMapper，按 statement/XML 节点限定结构检查，移除任意字符窗口与跨 statement 命中。
2. 保留只读、显式租户、显式投影和当前非分页等稳定结构约束；不因业务样例通过删除结构边界。
3. 租户隔离、空集合、有效期边界、缺失关联目标 LEFT JOIN 的语义先定位现有真实 mapper/PG 用例；有缺口则就近补齐后再删除脆弱表达。

## 当前口径

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] 每条结构失败能定位文件/statement；格式变化或相邻语句不再误匹配
- [x] 关键语义有实际 PG 证据，空集合守卫不能借用其他 statement 的文本
- [x] 针对删除过滤/守卫等相关错误变体产生目标断言失败，未引入生产 SQL 行为变更
- [x] 设计 §4 回写结构/行为证据职责与最终测试位置

## 非目标 / 遗留

不开发通用 SQL 解析平台，不扩为全仓 mapper 改造。


## 完成记录

2026-10-03：statement 结构检查与真实 PG 边界已完成，最终 6 + 3 testcase 零失败/错误/跳过；格式与过滤/守卫错误变体见[验收证据](evidence/T-ACCESS-074/query-mapper-evidence.md)。未改生产 SQL。
