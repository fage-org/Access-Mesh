---
doc_type: task
id: T-ACCESS-138
title: 演示业务记录与 CSV 导入
status: proposed
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-service-api-contract.md#import
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#data
  - docs/design/example-permission-demo.md#import-idempotency
depends_on:
  - T-ACCESS-140
acceptance:
  - "导入/撤销仅全行编辑 ALL 的管理身份可执行；局部 INSTANCE 即使覆盖当前批次也拒绝，撤销不可替换来源报表。"
  - "导入缺任何提交列的编辑权整批拒绝；新列先登记授权，预览/错误不回显无读权值，维护权不替代字段权。"
  - "坏行/未知字段/超限整批拒绝，无部分事实；同 importKey 重试不重复计数，同号换文件/报表拒绝。"
  - "撤销后旧 importKey 只返回已撤销状态；新号可重新导入，仍校验权限及有效记录唯一性；同号并发或提交后响应丢失不重复落库。"
  - "同租户有效 recordCode 唯一，导入/撤销竞争结果原子，跨租户同码隔离。"
  - "记录与导入结果重启保留，重置只作用指定演示范围；无启动隐式灌数据。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-19
---

## 背景

真实权限过滤需要可核对的业务记录和操作者身份数据。

## 范围

固定演示 CSV、真实用户 ID 回填脚本、校验/入库/幂等/批次查询/撤销；资源登记复用先行元数据阶段，不新增逐记录 IAM 资源。导入/撤销同时要求维护权、来源报表 DATA_EDIT、行编辑格 ALL 及涉及字段编辑权；INSTANCE 覆盖当前全部记录也不替代 ALL，不按角色名称豁免。

导入绑定目标报表；先准备字段元数据、发布和授权，再确认数据。模板、预览、确认入库分别遵循字段权限。

脚本生成并在网络重试中复用导入请求号；请求号与文件摘要直接归属批次，具体行为见[导入请求号与重导](../design/example-permission-demo.md#import-idempotency)，不新增通用幂等组件。

## 当前口径

取消销售人员管理和影子用户绑定流程；导入 ownerUserId 等业务归属使用场景包真实身份。普通请求主体/租户仍来自验签上下文，不由 CSV 或 DTO 决定。 详细规则见[权限演示设计](../design/example-permission-demo.md)；计划已 active，本卡等待前置任务完成后实施。

## 验收对照

- [ ] 导入/撤销仅全行编辑 ALL 的管理身份可执行；局部 INSTANCE 即使覆盖当前批次也拒绝，撤销不可替换来源报表。
- [ ] 导入缺任何提交列的编辑权整批拒绝；新列先登记授权，预览/错误不回显无读权值，维护权不替代字段权。
- [ ] 坏行/未知字段/超限整批拒绝，无部分事实；同 importKey 重试不重复计数，同号换文件/报表拒绝。
- [ ] 撤销后旧 importKey 只返回已撤销状态；新号可重新导入，仍校验权限及有效记录唯一性；同号并发或提交后响应丢失不重复落库。
- [ ] 同租户有效 recordCode 唯一，导入/撤销竞争结果原子，跨租户同码隔离。
- [ ] 记录与导入结果重启保留，重置只作用指定演示范围；无启动隐式灌数据。

## 非目标 / 遗留

不做统计分析、人员界面、组织同步或外部数据源。
