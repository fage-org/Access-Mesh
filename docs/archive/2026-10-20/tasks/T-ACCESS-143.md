---
doc_type: task
id: T-ACCESS-143
title: example 内部自定义定时任务与恢复
status: done
plan: docs/archive/2026-10-20/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-service-api-contract.md#tasks
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#scheduled-tasks
depends_on:
  - T-ACCESS-141
acceptance:
  - "自动、手动和重试均不恢复提交 IP 或使用任务主机 IP，条件结果由现役引擎决定，不自行扩大拒绝或放行。"
  - "任务仅本人维护/触发/查看，执行重查 TRIGGER 和数据权限；定时/手动共用入口，拒绝借他人任务身份执行。"
  - "任务执行及未完成重试重查字段权限，字段撤销或目录不兼容变化不沿用旧列；终态结果不重新取数补列。"
  - "任务和执行状态重启后保留，未完成记录按明确语义恢复，同计划时刻不重复产出。"
  - "任务停用、用户/凭证失效、执行前撤权和技术错误正确区分，重试同键且有上限。"
  - "任务配置变更只影响未来触发，旧尝试不能覆盖新结果，条件上下文不伪造用户当前环境。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-20
---

## 背景

用户选择 example 内部调度，用任务演示持久化、重试、执行时权限与幂等。

## 范围

任务定义/cron/启停/手动触发、固定快照执行目标、稳定执行键、持久化状态、尝试号、有限重试、启动恢复和结果清理。

任务配置与执行记录纳入可选字段清单及实际输出列，恢复与重试复用同一行列权限入口。

实现 PENDING/RUNNING 与业务终态、有限技术重试和重启恢复，使用稳定字段身份和执行键，禁止旧请求头或令牌充当后台身份。

## 当前口径

不接 access-service 任务管理。任务主体固定为可信创建者，配置不得指定任意用户/URL/方法/SQL。单实例，调度执行复用 T-ACCESS-141 的同一业务入口。 详细规则见[权限演示设计](../../../design/example-permission-demo.md)；计划已 active，前置快照已完成，本卡实现、定向与标准完整回归已完成。

## 验收对照

- [x] 自动、手动和重试均不恢复提交 IP 或使用任务主机 IP，条件结果由现役引擎决定，不自行扩大拒绝或放行。
- [x] 任务仅本人维护/触发/查看，执行重查 TRIGGER 和数据权限；定时/手动共用入口，拒绝借他人任务身份执行。
- [x] 任务执行及未完成重试重查字段权限，字段撤销或目录不兼容变化不沿用旧列；终态结果不重新取数补列。
- [x] 任务和执行状态重启后保留，未完成记录按明确语义恢复，同计划时刻不重复产出。
- [x] 任务停用、用户/凭证失效、执行前撤权和技术错误正确区分，重试同键且有上限。
- [x] 任务配置变更只影响未来触发，旧尝试不能覆盖新结果，条件上下文不伪造用户当前环境。

## 非目标 / 遗留

不复制 access 的任务平台，不做跨服务桥接、多节点调度或遗漏时段全量补发。

<a id="task-ip-context-limit"></a>
**定时任务 IP 条件限制（2026-10-18 确认）**

现象：用户在线请求带可信 clientIp，可能满足报表或范围授权的 IP 条件；同一用户的定时任务没有可证明其当前网络位置的上下文，按本计划不传 clientIp，该 IP 条件不满足。若没有其他有效授权，任务依引擎结果进入 DENIED/EMPTY，不产生数据结果；有其他有效授权时仍按正常合并语义处理。

当前处置：按[任务条件上下文](../../../design/example-permission-demo.md#scheduled-tasks)作为已确认的功能限制保留，不将其视为尚未处理的实现故障；本卡验收验证在线与后台结果的差异。

后续重访条件：出现确需后台按可信网络/执行环境条件授权的业务场景时，另行明确任务来源、执行环境与用户环境的语义及可信采集方式，再决定是否支持。现有旧一次性导出保存提交 IP 的行为不随本卡自动修改。权威规则见[任务条件上下文](../../../design/example-permission-demo.md#scheduled-tasks)；本卡已承载问题与验收，不重复登记待解决问题编号。

## 完成记录

2026-10-20 完成本人任务管理、UTC cron、冻结执行、启停与恢复；行为与双轨核对见[验收记录](evidence/example-permission-demo/T-ACCESS-143.md)。标准根 `mvn test -T 1C` 含 E2E/heavy，结果计数及耗时见该记录。
