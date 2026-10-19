---
doc_type: task
id: T-ACCESS-139
title: 演示资源登记与可靠发布
status: done
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-service-api-contract.md#metadata
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#storage
  - docs/design/example-permission-demo.md#operations
  - docs/design/access-service-api-contract.md#resource-incremental-replay
  - docs/design/schema/access-service.sql
depends_on:
  - T-ACCESS-137
acceptance:
  - "字段资源归属当前报表且同名跨表独立；SUB_PERM 显式接线，不用跨类型资源树父边；删后同名不复活旧授权。"
  - "类型/操作/资源/接口登记有可重复脚本，资源所有权和首授起点正确。"
  - "响应丢失或重启后按原载荷/版本重放；旧事件不覆盖新状态。"
  - "资源未就绪或配置不完整不放行，服务凭证不调用用户身份管理写接口。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-19
---

## 背景

权限演示中的报表和范围资源必须与真实业务对象及配置对应。

## 范围

租户首次类型/操作/所有权与服务凭证接入；完整接口声明；业务对象和范围资源的待发布事件、版本、重放、状态反馈。

资源纯增量同步补齐同版本同内容的严格重放确认，作为发布响应丢失后恢复的前置；保留 FULL/发布代次的现行规则。

本卡同时承接最小报表及字段元数据创建/读取/维护 API，供后续策略配置和导入定位；这些入口使用明确的元数据管理门禁，不读取业务字段值。普通读者的授权字段目录由 T-ACCESS-140 实现。

登记报表字段资源、业务归属和目录版本，处理新增/停用/删除发布及父授权上下文。

## 当前口径

使用现有服务凭证和 SYNC 通道，Feign 在本地事务外。策略管理与业务数据维护是独立授权；保持源版本和数据库事实一致，不自动扩大客户授权。详细规则见[权限演示设计](../design/example-permission-demo.md)与[类型用途契约](../design/example-service-api-contract.md#resource-type-purposes)；T-ACCESS-137 已完成，实现、定向验证与包含 E2E/heavy 的完整回归已验收；证据见[验证记录](evidence/example-permission-demo/T-ACCESS-139.md)。

## 验收对照

- [x] 字段资源归属当前报表且同名跨表独立；SUB_PERM 显式接线，不用跨类型资源树父边；删后同名不复活旧授权。
- [x] 类型/操作/资源/接口登记有可重复脚本，资源所有权和首授起点正确。
- [x] 响应丢失或重启后按原载荷/版本重放；旧事件不覆盖新状态。
- [x] 资源未就绪或配置不完整不放行，服务凭证不调用用户身份管理写接口。

## 非目标 / 遗留

不建设 FULL 对账、MQ 或跨服务任务桥接。

## 完成记录

2026-10-19 完成元数据管理、资源用途隔离、事务内冻结事件、有限重试与严格重放确认；本地代码/文档双轨核对通过。根 reactor `mvn test -T 1C`（附本机 PowerShell 脚本实跑参数）为 3,052 pass / 0 fail / 0 error / 0 skip，17:37 min，含 E2E/heavy。详细边界与证据见[验证记录](evidence/example-permission-demo/T-ACCESS-139.md)。
