---
doc_type: task
id: T-ACCESS-084
title: OAuth2 公开客户端实现（PKCE-only 免 secret）
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§6.1 OAuth2 客户端注册/授权/换 token）
  - docs/design/schema/access-service.sql（sys_oauth2_client 表）
  - docs/ops/deployment.md（OAuth2 公开客户端版本升级）
  - docs/design/frontend/login.md（授权确认与登录续接）
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
  status: done
last_updated: 2026-10-06
---

# T-ACCESS-084 OAuth2 公开客户端实现

## 背景

authorize 支持 PKCE（S256/plain）与 RFC 8252 原生应用回调口径，但授权码换 token 无条件要求 client_secret（`OAuth2AppServiceImpl.java:421-428`）——公开客户端（无法安全保存密钥的移动 app/SPA）拿码后卡死；refresh 路径注释自相矛盾（「PKCE 公开客户端只需客户端 ID 和刷新令牌」）。redirect_uri 注册根路径 `/` 时任意路径放行（`:794-825`）。无 consent 确认页。

## 范围

公开客户端模型实现（注册类型+免 secret 分支+强制 PKCE）+redirect 收紧+consent 评估。

## 当前口径

实现公开客户端（2026-10-05 拍板 D13=B）：client 表增加 client_type，client_secret 可空并按类型加 CHECK；旧开发库备份后重建，deployment.md 写明；token 端点公开分支强制 S256（不接受 plain 兜底）；consent 页交付最小同意/拒绝流程（2026-10-06 用户选择 A）：复用平台登录，显示服务端核验的客户端与 scope；同意后签码并返回已登记回调，拒绝不签码。不做历史授权管理、记住同意或复杂权限选择。回调匹配按 2026-10-06 用户选择 A：只取消根路径的任意子路径放行，保留非根路径前缀段匹配与既有查询参数处理，不扩为完整 URI 精确匹配；修正错误的 RFC 归因。

## 验收对照

- [x] 公开客户端注册+DDL 迁移落地
- [x] S256+无 secret 全链路用例
- [x] confidential 不回归
- [x] 根路径通配拒绝用例
- [x] consent 落地或登记+迁移口径在册

## 非目标 / 遗留

- 设备授权流等其他 OAuth2 grant：不涉及。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
