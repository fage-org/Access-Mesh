---
doc_type: task
id: T-ACCESS-136
title: 权限演示需求与实施契约定稿
status: proposed
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#scope
  - docs/design/example-permission-demo.md#dynamic-policy
  - docs/design/example-permission-demo.md#scheduled-tasks
depends_on: []
acceptance:
  - "按已确认的全行管理导入、策略原子生效和后台无用户 IP 固化契约，联合演示身份无隐式全量授权。"
  - "明确报表字段独立资源身份、父上下文、行列组合及导入/查询/写入/任务字段权限契约。"
  - "现役与目标区分，设计转 adopted；配置模型不含按本人/团队/部门写死的代码分支。"
  - "动态配置管理权限、错误/版本语义及任务执行身份明确，example API 契约可实施。"
  - "核定 T-PERM-036 准入与 PG 测试复用方式，更新依赖和例外边界。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-18
---

## 背景

将 example 收敛为有真实数据的权限演示样板，移除数据分析和业务页面。

## 范围

定稿字段/操作绑定/动态谓词、配置发布权限、内部定时任务上下文与恢复语义；建立 example 专属 API 契约和旧探针证据迁移安排。

定稿报表字段资源和目录版本、SUB_PERM 接入、动态列投影、导入与编辑权限、无可见字段响应及已完成产物边界。

整体检查的导入范围、策略草稿裁剪和任务 IP 语义已确认，按需求正文落实详细契约，不重复提问；API/DDL 等实施细节未定稿不把设计标 adopted。

## 当前口径

CSV、本人/团队/部门、运行时动态配置、无业务页面、example 内部调度已确认，不重复提问。其余细节依真实样例定稿，本次只设计，不实施。 详细规则见[权限演示设计](../design/example-permission-demo.md)；计划为 proposed，本次只设计。

[设计检查](evidence/example-permission-demo/design-check.md)保留当前契约对照与检查边界，不替代本卡定稿验收。

## 验收对照

- [ ] 按已确认的全行管理导入、策略原子生效和后台无用户 IP 固化契约，联合演示身份无隐式全量授权。
- [ ] 明确报表字段独立资源身份、父上下文、行列组合及导入/查询/写入/任务字段权限契约。
- [ ] 现役与目标区分，设计转 adopted；配置模型不含按本人/团队/部门写死的代码分支。
- [ ] 动态配置管理权限、错误/版本语义及任务执行身份明确，example API 契约可实施。
- [ ] 核定 T-PERM-036 准入与 PG 测试复用方式，更新依赖和例外边界。

## 非目标 / 遗留

不启动代码、DDL、运行服务或跨服务调度桥接。
