---
doc_type: task
id: T-ACCESS-080
title: 剩余外部同步凭证化与共享密钥外部退出
status: proposed
plan: —
domain: cross-service
design_refs:
  - docs/design/service-authentication.md §3.5
  - docs/design/access-service-api-contract.md §19/§24
depends_on:
  - T-ACCESS-079
blocks: []
acceptance:
  - "盘点并凭证化剩余 abstract-user/abstract-role/user-role sync 与 full-sync，精确路径单源和 SDK 镜像同步"
  - "保留既有类型/来源/保留业务键守卫；凭证绑定租户与服务，不信任跨租户自报头"
  - "确认所有外部业务调用不再依赖共享密钥，再关闭旧纯服务通道并安排内部密钥轮换；未满足条件不声称已消除外部共享密钥风险"
  - "Gateway/内部设施仍保留内部互信密钥；不以全仓密钥使用清零作为不可能的退出判据"
  - "部署与 SDK 指南明确验收节点和当前差异，不保留永久模式开关，不虚设日历退役日"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-04
---

# T-ACCESS-080 剩余外部同步凭证化与共享密钥外部退出

## 背景

运行时四查询凭证化不代表所有同步端点均已迁移；[T-ACCESS-068](../archive/2026-10-04/tasks/T-ACCESS-068.md) 明确须待外部旧调用清零才能回收外部分发的共享密钥。

## 范围

剩余用户、角色、绑定同步族，以及外部服务移除共享密钥的验收与运维安排；不扩大为内部平台密钥退役。

## 当前口径

沿现有服务认证分期约束逐步凭证化。完成时间由“端点与 SDK 接线→外部调用迁移验收→旧纯服务通道关闭→内部密钥轮换”事件顺序确定；四运行查询的同批硬切只适用于 T-ACCESS-079，不自动扩成全部历史调用兼容承诺的撤销。

## 非目标 / 遗留

- 不改变同步业务契约，不增加新的服务查询范围配置。
- 不删除 Gateway 与内部设施所需的内部互信认证。
