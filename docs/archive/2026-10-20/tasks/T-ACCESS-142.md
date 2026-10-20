---
doc_type: task
id: T-ACCESS-142
title: 权限场景包与持久化验收收口
status: done
plan: docs/archive/2026-10-20/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/architecture.md
  - docs/design/services/example-service.md
  - docs/design/example-service-api-contract.md#tasks
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#scenarios
  - docs/design/example-permission-demo.md#plan-boundary
depends_on:
  - T-ACCESS-143
  - T-PERM-036
acceptance:
  - "场景包验证局部行权限不能导入/撤销、策略单次原子生效、在线 IP 条件与后台无 IP 的判定区别。"
  - "完整执行设计中的同一用户联合主线，记录行/列集合、任务状态与执行键；故障前后和撤权生效以确定性机制验证。"
  - "场景包覆盖同名字段跨报表隔离、行列交集、隐藏列推测拒绝、导入字段拒绝、字段动态变更与任务列重查。"
  - "授权/撤权/条件/互斥/父上下文/租户与凭证反例有可重复请求和可核对结果，失败关闭。"
  - "配置扩展 regionCode 无代码/重启，读写范围一致；重启保存数据/策略/任务/成功结果并不重复执行。"
  - "必要回归与本地双轨完成，现役设计/API/schema/使用指南回写，任务计划归档。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-20
---

## 背景

以可重复场景证明权限能力，避免只检查 HTTP 成功或返回演示文本。

## 范围

API/脚本样例、真实身份/资源创建与清理、正反例期望 ID、动态配置无代码变更验证、正式包重启/幂等和全量回归。

加入报表字段权限的 API 正反例及实际响应/CSV 表头/SQL 投影证据，确认无新增页面或独立权限引擎。

把场景从数据准备串到在线行列过滤、定时执行、撤权、恢复和去重，不以独立场景清单或 HTTP 200 替代闭环。

## 当前口径

移除业务页面和分析验收，不依赖已取消 T-FE-079。使用既有 IAM 管理台和脚本，按任务范围复用现有引擎证据；收口 mvn test -T 1C 含 E2E/heavy。 详细规则见[权限演示设计](../../../design/example-permission-demo.md)；计划已 active，前置任务已完成，联合主线、正式包、标准完整回归与双轨核对均已完成。

## 验收对照

- [x] 场景包验证局部行权限不能导入/撤销、策略单次原子生效、在线 IP 条件与后台无 IP 的判定区别。
- [x] 完整执行设计中的同一用户联合主线，记录行/列集合、任务状态与执行键；故障前后和撤权生效以确定性机制验证。
- [x] 场景包覆盖同名字段跨报表隔离、行列交集、隐藏列推测拒绝、导入字段拒绝、字段动态变更与任务列重查。
- [x] 授权/撤权/条件/互斥/父上下文/租户与凭证反例有可重复请求和可核对结果，失败关闭。
- [x] 配置扩展 regionCode 无代码/重启，读写范围一致；重启保存数据/策略/任务/成功结果并不重复执行。
- [x] 必要回归与本地双轨完成，现役设计/API/schema/使用指南回写，任务计划归档。

## 非目标 / 遗留

不建设业务页面，不自动发布或操作既有候选栈，不承诺跨版本升级及生产高可用。

## 完成记录

2026-10-20 完成同一用户联合主线、可执行 JAR 启动/重启、动态配置产物与进程观测、脚本及文档一致性核对。标准根 `mvn test -T 1C`：3,148 passed / 0 failed / 0 error / 0 skipped，23:30 min，包含 E2E/heavy；正式包定向 20 项全部通过。状态分类修正的失败/通过证据、完整回归的先前失败及隔离结果均保存在[最终验收](evidence/example-permission-demo/T-ACCESS-142.md)，未计入通过结果或掩盖。
