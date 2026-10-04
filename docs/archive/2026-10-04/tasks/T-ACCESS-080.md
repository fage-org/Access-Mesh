---
doc_type: task
id: T-ACCESS-080
title: 剩余外部同步凭证化与共享密钥外部退出
status: done
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
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-080 剩余外部同步凭证化与共享密钥外部退出

## 背景

运行时四查询凭证化不代表所有同步端点均已迁移；[T-ACCESS-068](T-ACCESS-068.md) 明确须待外部旧调用清零才能回收外部分发的共享密钥。

## 范围

剩余用户、角色、绑定同步族，以及外部服务移除共享密钥的验收与运维安排；不扩大为内部平台密钥退役。

## 当前口径

2026-10-04 确认剩余同步接口无仓外旧调用方，与 SDK 同批切换，关闭旧共享密钥加自报头的纯服务通道，不保留兼容开关。仓内调用与测试须全部切换并验收；内部密钥轮换写入部署步骤，Gateway/内部设施继续使用内部互信密钥，不声称已执行真实部署轮换。

## 非目标 / 遗留

- 不改变同步业务契约，不增加新的服务查询范围配置。
- 不删除 Gateway 与内部设施所需的内部互信认证。

## 验收对照

- [x] 盘点并凭证化剩余 abstract-user/abstract-role/user-role sync 与 full-sync，精确路径单源和 SDK 镜像同步
- [x] 保留既有类型/来源/保留业务键守卫；凭证绑定租户与服务，不信任跨租户自报头
- [x] 确认所有外部业务调用不再依赖共享密钥，再关闭旧纯服务通道并安排内部密钥轮换；未满足条件不声称已消除外部共享密钥风险
- [x] Gateway/内部设施仍保留内部互信密钥；不以全仓密钥使用清零作为不可能的退出判据
- [x] 部署与 SDK 指南明确验收节点和当前差异，不保留永久模式开关，不虚设日历退役日

## 完成记录

2026-10-04：实现、设计回写与本地代码/文档双轨评审完成。`mvn test -T 1C` 全量 2521 项，0 失败/错误/跳过（含 E2E 29 项及 heavy）；前端 `pnpm test` 492 项、build/typecheck/lint 通过。定向回归、反例实证、退役测试映射及部署边界见[综合验收](evidence/checklist-followup/final-audit.md)。
