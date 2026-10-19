---
doc_type: task
id: T-ACCESS-136
title: 权限演示需求与实施契约定稿
status: done
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-service-api-contract.md
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
  status: done
last_updated: 2026-10-19
---

## 背景

将 example 收敛为有真实数据的权限演示样板，移除数据分析和业务页面。

## 范围

定稿字段/操作绑定/动态谓词、配置发布权限、内部定时任务上下文与恢复语义；建立 example 专属 API 契约和旧探针证据迁移安排。

定稿报表字段资源和目录版本、SUB_PERM 接入、动态列投影、导入与编辑权限、无可见字段响应及已完成产物边界。

接口字段细化集中在[example API 契约](../design/example-service-api-contract.md)，已覆盖元数据、发布、策略、导入、记录读写与任务/结果入口，已通过本地双轨核对，设计已 adopted；下游按依赖实施。

固定入口类型/主操作的边界已确认并回写[设计](../design/example-permission-demo.md#admission-binding)，不再保留未确认的主操作切换能力。

## 当前口径

CSV、本人/团队/部门、运行时动态配置、无业务页面、example 内部调度已确认，不重复提问。计划已获执行授权，本卡已完成契约定稿，下游代码任务按依赖推进。未决取舍按真实场景交用户决定，详细规则见[权限演示设计](../design/example-permission-demo.md)。

导入采用已确认的[请求号与重导契约](../design/example-permission-demo.md#import-idempotency)，API 与唯一约束据此细化。PG 复用现状：`ItInfra` 位于 access-service 测试源，绑定 access DDL 模板和 Redis 注册，不能直接成为 example 测试依赖；共享出口须只抽离实际复用的基建，保留各服务 schema 与测试执行轨道。

[设计检查](evidence/example-permission-demo/design-check.md)保留当前契约对照与检查边界，不替代本卡定稿验收。

[实施前核对](evidence/example-permission-demo/implementation-baseline.md)记录 SDK 接线、旧探针证据迁移安排和 PG 复用边界。测试依赖安全例外已限定扩展至 Example Service:test，规则与脚本同步且单测通过；其他模块不自动适用。向用户提问先解释背景，再给出具体数据、操作后果与推荐理由；待决期间继续推进不依赖该决定的事项。

## 验收对照

- [x] 按已确认的全行管理导入、策略原子生效和后台无用户 IP 固化契约，联合演示身份无隐式全量授权。
- [x] 明确报表字段独立资源身份、父上下文、行列组合及导入/查询/写入/任务字段权限契约。
- [x] 现役与目标区分，设计转 adopted；配置模型不含按本人/团队/部门写死的代码分支。
- [x] 动态配置管理权限、错误/版本语义及任务执行身份明确，example API 契约可实施。
- [x] 核定 T-PERM-036 准入与 PG 测试复用方式，更新依赖和例外边界。

## 非目标 / 遗留

不启动业务代码、DDL、运行服务或跨服务调度桥接。已确认的测试依赖例外仅同步安全检查脚本与规则，验证记录见实施前核对，不作为持久化实现完成的证据。

## 完成记录

2026-10-19 完成目标设计与 API 契约定稿、旧证据迁移安排、测试支持出口及有限安全例外同步。主代理本地技术/文档双轨核对完成，未遗留用户待决项；证据见[实施前核对](evidence/example-permission-demo/implementation-baseline.md)。`node --test tools/security/check-maven-audit.test.mjs`：5 passed / 0 failed / 0 skipped；链接、锚点、任务状态、接口路径与 `git diff --check` 通过。未运行 Maven/PG/E2E 或完整漏洞扫描，不宣称新业务实现已交付。
