---
doc_type: task
id: T-ACCESS-084
title: OAuth2 公开客户端实现（PKCE-only 免 secret）
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§6.1 OAuth2 客户端注册/授权/换 token）
  - docs/design/schema/access-service.sql（sys_oauth2_client 表）
depends_on:
  - T-ACCESS-086
blocks: []
acceptance:
  - "客户端注册支持公开客户端类型（client_secret 可空或类型标记——DDL 迁移，存量库=销毁重建，T-ACCESS-086 前置）"
  - "token 端点公开客户端分支强制 PKCE（S256）免 secret；S256+无 secret 全链路用例（authorize→token→refresh）"
  - "confidential 客户端行为不回归（现有 example/e2e 消费方全绿）"
  - "redirect 注册拒绝根路径通配（isPathAllowed 收紧）用例"
  - "consent 页最小确认页落地或显式登记取舍；存量库迁移/重建口径写入 deployment.md"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-ACCESS-084 OAuth2 公开客户端实现

## 背景

authorize 支持 PKCE（S256/plain）与 RFC 8252 原生应用回调口径，但授权码换 token 无条件要求 client_secret（`OAuth2AppServiceImpl.java:421-428`）——公开客户端（无法安全保存密钥的移动 app/SPA）拿码后卡死；refresh 路径注释自相矛盾（「PKCE 公开客户端只需客户端 ID 和刷新令牌」）。redirect_uri 注册根路径 `/` 时任意路径放行（`:794-825`）。无 consent 确认页。

## 范围

公开客户端模型实现（注册类型+免 secret 分支+强制 PKCE）+redirect 收紧+consent 评估。

## 当前口径

实现公开客户端（2026-10-05 拍板 D13=B）：client 表 DDL 迁移（client_secret NOT NULL 调整或加类型列）=销毁重建，deployment.md 写明；token 端点公开分支强制 S256（不接受 plain 兜底）；consent 页随本卡设计（公开客户端场景标配，至少最小确认页或登记取舍）。

## 验收对照

- [ ] 公开客户端注册+DDL 迁移落地
- [ ] S256+无 secret 全链路用例
- [ ] confidential 不回归
- [ ] 根路径通配拒绝用例
- [ ] consent 落地或登记+迁移口径在册

## 非目标 / 遗留

- 设备授权流等其他 OAuth2 grant：不涉及。
