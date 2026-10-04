---
doc_type: task
id: T-ACCESS-079
title: 运行时查询四端点与 SDK 服务凭证同批切换
status: done
plan: —
domain: cross-service
design_refs:
  - docs/design/service-authentication.md §3.5
  - docs/design/access-service-api-contract.md §24
  - docs/design/architecture.md §4.5.1
  - docs/design/services/example-service.md
depends_on:
  - T-ACCESS-068
blocks: []
acceptance:
  - "check/batch-check/query-resources/query-scopes 接受有效服务凭证，租户/服务固定由凭证派生；本租户查询能力不增加资源类型配置"
  - "四端点的旧共享密钥加自报服务头纯服务通道关闭；Gateway 内部互信及既有签名用户态边界保持"
  - "M2M 端点单源、SDK 镜像、Gateway、client starter 与 example-service 同批接线，不新增永久兼容开关"
  - "只消费已凭证化端点的 SDK 配置不再强制业务方提供全局内部密钥；未迁同步通道不得静默获得凭证能力"
  - "覆盖正确凭证、跨租户伪造、无效/停用/过期凭证、服务停用、四查询正常行为与旧纯服务身份拒绝；SDK API 变化刷新 SNAPSHOT 后验收下游"
  - "实现后回写当前差异；当前无部署，四查询服务端/SDK/示例作为同批切换单元，不承诺旧客户端兼容窗口"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-079 运行时查询四端点与 SDK 服务凭证同批切换

## 背景

[T-ACCESS-068](T-ACCESS-068.md) 已确定查询能力与发布边界，原计划只交付规划，本卡承接实施。

## 范围

运行时四查询端点、认证与 M2M 精确路径清单、SDK 镜像与示例接线。既有服务凭证验证和权限查询引擎复用，不新增查询权限框架。

## 当前口径

有效凭证允许本租户任意主体/资源的授权查询，沿现有端点参数与引擎语义，不赋予管理写能力。当前无部署环境，四查询端点、SDK 与示例同批硬切（2026-10-04 确认）；服务端、SDK 镜像与示例已完成同批接线。

示例服务采用单租户部署（2026-10-04 确认）：显式配置凭证所属租户，业务最终检查前与网关验签租户比对，不匹配立即拒绝；不建设多租户凭证路由，原租户发头 ThreadLocal 随切换退役。

## 非目标 / 遗留

- 剩余同步端点及外部分发的共享密钥回收归 T-ACCESS-080。
- 不移除 Gateway 内部互信密钥，不改变用户会话/OAuth2 身份模型。

## 验收对照

- [x] check/batch-check/query-resources/query-scopes 接受有效服务凭证，租户/服务固定由凭证派生；本租户查询能力不增加资源类型配置
- [x] 四端点的旧共享密钥加自报服务头纯服务通道关闭；Gateway 内部互信及既有签名用户态边界保持
- [x] M2M 端点单源、SDK 镜像、Gateway、client starter 与 example-service 同批接线，不新增永久兼容开关
- [x] 只消费已凭证化端点的 SDK 配置不再强制业务方提供全局内部密钥；未迁同步通道不得静默获得凭证能力
- [x] 覆盖正确凭证、跨租户伪造、无效/停用/过期凭证、服务停用、四查询正常行为与旧纯服务身份拒绝；SDK API 变化刷新 SNAPSHOT 后验收下游
- [x] 实现后回写当前差异；当前无部署，四查询服务端/SDK/示例作为同批切换单元，不承诺旧客户端兼容窗口

## 完成记录

2026-10-04：实现、设计回写与本地代码/文档双轨评审完成。`mvn test -T 1C` 全量 2521 项，0 失败/错误/跳过（含 E2E 29 项及 heavy）；前端 `pnpm test` 492 项、build/typecheck/lint 通过。定向回归、反例实证、退役测试映射及部署边界见[综合验收](evidence/checklist-followup/final-audit.md)。
