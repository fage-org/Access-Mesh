---
doc_type: task
id: T-ACCESS-138
title: 演示业务记录与 CSV 导入
status: proposed
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#data
depends_on:
  - T-ACCESS-140
acceptance:
  - "导入/撤销仅全行编辑 ALL 的管理身份可执行；局部 INSTANCE 即使覆盖当前批次也拒绝，撤销不可替换来源报表。"
  - "导入缺任何提交列的编辑权整批拒绝；新列先登记授权，预览/错误不回显无读权值，维护权不替代字段权。"
  - "坏行/未知字段/超限整批拒绝，无部分事实；相同文件重试不重复计数。"
  - "同租户有效 recordCode 唯一，导入/撤销竞争结果原子，跨租户同码隔离。"
  - "记录与导入结果重启保留，重置只作用指定演示范围；无启动隐式灌数据。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-18
---

## 背景

真实权限过滤需要可核对的业务记录和操作者身份数据。

## 范围

固定演示 CSV、真实用户 ID 回填脚本、校验/入库/幂等/批次查询/撤销，导入后资源发布接线。导入/撤销同时要求维护权、来源报表 DATA_EDIT、行编辑格 ALL 及涉及字段编辑权；INSTANCE 覆盖当前全部记录也不替代 ALL，不按角色名称豁免。

导入绑定目标报表；先准备字段元数据、发布和授权，再确认数据。模板、预览、确认入库分别遵循字段权限。

## 当前口径

取消销售人员管理和影子用户绑定流程；导入 ownerUserId 等业务归属使用场景包真实身份。普通请求主体/租户仍来自验签上下文，不由 CSV 或 DTO 决定。 详细规则见[权限演示设计](../design/example-permission-demo.md)；计划为 proposed，本次只设计。

## 验收对照

- [ ] 导入/撤销仅全行编辑 ALL 的管理身份可执行；局部 INSTANCE 即使覆盖当前批次也拒绝，撤销不可替换来源报表。
- [ ] 导入缺任何提交列的编辑权整批拒绝；新列先登记授权，预览/错误不回显无读权值，维护权不替代字段权。
- [ ] 坏行/未知字段/超限整批拒绝，无部分事实；相同文件重试不重复计数。
- [ ] 同租户有效 recordCode 唯一，导入/撤销竞争结果原子，跨租户同码隔离。
- [ ] 记录与导入结果重启保留，重置只作用指定演示范围；无启动隐式灌数据。

## 非目标 / 遗留

不做统计分析、人员界面、组织同步或外部数据源。
