---
doc_type: task
id: T-ACCESS-141
title: 有界授权快照导出与结果领取
status: proposed
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#scheduled-tasks
  - docs/design/example-permission-demo.md#operations
depends_on:
  - T-ACCESS-140
  - T-PERM-036
acceptance:
  - "新后台执行不携带用户 IP，缺上下文由引擎判 DENIED/EMPTY，时间条件使用实际执行时间；旧一次性导出协议保持。"
  - "显式列、筛选与排序冻结稳定字段身份，删后同名不重绑；EMPTY 无产物，结果与 SUCCESS 按执行键及 attempt 原子提交。"
  - "执行前重查行列权限，数据和 CSV 表头仅含授权字段；显式选列撤权拒绝，完成后结果不补列并沿既有领取边界。"
  - "执行前撤权/停用/配置异常时不产出数据，不复用提交时范围；实际策略版本可追溯。"
  - "结果受租户/主体/接口准入和期限约束，CSV 转义正确，超限不截断成功。"
  - "同执行键只提交一份结果，技术失败可重试，旧尝试不能覆盖新结果。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-18
---

## 背景

沿用原异步导出主题，最小结果载体用于证明执行时权限重查及归属限制。

## 范围

持久化执行请求、可信主体/报表/筛选、执行前重新鉴权、动态策略版本、有界原始记录快照/CSV、数据库结果与本人领取。

冻结可选列清单，执行时使用当前字段目录/策略/字段权限，记录输出列和版本，禁止 SELECT * 后隐藏。

使用统一持久化执行状态，事务提交前失败可重试，提交后确认丢失只读既有结果；不重建成功产物。

## 当前口径

缩减文件卷/大文件产品化范围；有界结果可直接存 PG。保留现役已完成导出归提交者的边界。执行函数由内部调度任务复用，不另建一套调度平台。 详细规则见[权限演示设计](../design/example-permission-demo.md)；计划为 proposed，本次只设计。

## 验收对照

- [ ] 新后台执行不携带用户 IP，缺上下文由引擎判 DENIED/EMPTY，时间条件使用实际执行时间；旧一次性导出协议保持。
- [ ] 显式列、筛选与排序冻结稳定字段身份，删后同名不重绑；EMPTY 无产物，结果与 SUCCESS 按执行键及 attempt 原子提交。
- [ ] 执行前重查行列权限，数据和 CSV 表头仅含授权字段；显式选列撤权拒绝，完成后结果不补列并沿既有领取边界。
- [ ] 执行前撤权/停用/配置异常时不产出数据，不复用提交时范围；实际策略版本可追溯。
- [ ] 结果受租户/主体/接口准入和期限约束，CSV 转义正确，超限不截断成功。
- [ ] 同执行键只提交一份结果，技术失败可重试，旧尝试不能覆盖新结果。

## 非目标 / 遗留

不建文件管理页面、独立存储平台或销售统计导出。
