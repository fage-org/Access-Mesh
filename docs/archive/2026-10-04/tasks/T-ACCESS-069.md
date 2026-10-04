---
doc_type: task
id: T-ACCESS-069
title: 测试精简基线与候选范围确认
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#assessment
  - docs/design/testing-simplification.md#support-boundary
depends_on: []
blocks: []
acceptance:
  - "实施基线、候选路径与最小映射可追溯，计数口径区分声明、展开案例与 skip"
  - "直接相关轨道具备实际运行基线或明确 not run/环境条件；拟删除候选的原证据在实施删除前补齐"
  - "071 自动授权迁移与 062 API 授权退役分别记录支持边界及 T-ACCESS-066 接触面；未定事项未被写成已采纳"
  - "更新设计 §1/§3 与候选映射，后续卡可按独立准入推进"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-069 测试精简基线与候选范围确认

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#assessment)与[执行计划](../testing-simplification-plan.md)。实施已启动；方案按章节采纳，行为验证以实际报告为准。

## 范围

1. 复用[全仓固定评估证据](evidence/T-ACCESS-069/whole-suite-assessment.md)与机器快照，重新固定实施提交与工作树范围，核对附件之后的增量；从实际 POM、Vitest 与报告建立轨道/发现基线，不沿用附件估算。现存报告根属性合计与 testcase 子元素不一致，须以当次隔离输出核对嵌套/参数化、重试与 skip，不能只求和 testsuite@tests。
2. 为本批候选建立 case/assert 级最小映射与收益记录模板；核查 OAuth2、查询、common、数据库、前端和 E2E 的具体候选，不建立全仓永久台账。
3. 分别核对 071 自动授权旧依赖迁移与 062 API 授权退役的现行采纳依据、部署前提、生产提示与恢复入口，各自输出保留/退出/待定结论及来源；退出一条不授权删除另一条。待定时仅阻止 T-ACCESS-073 对应资产的删除，其他工作可继续。

## 当前口径

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] 实施基线、候选路径与最小映射可追溯，计数口径区分声明、展开案例与 skip
- [x] 直接相关轨道具备实际运行基线或明确 not run/环境条件；拟删除候选的原证据在实施删除前补齐
- [x] 071 自动授权迁移与 062 API 授权退役分别记录支持边界及 T-ACCESS-066 接触面；未定事项未被写成已采纳
- [x] 更新设计 §1/§3 与候选映射，后续卡可按独立准入推进

实施基线、首批真实运行与其他轨道 not run 边界、支持裁决及本地两轨核验见[实施基线](evidence/T-ACCESS-069/implementation-baseline.md)。每批删除仍须补齐本批原证据与接替验证。

## 非目标 / 遗留

不执行测试精简、生产修改或技能安装；不为其他在办正确性任务冻结全仓。

