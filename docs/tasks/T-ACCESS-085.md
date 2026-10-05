---
doc_type: task
id: T-ACCESS-085
title: 审计门禁与留痕补齐（login-log 转正+失败留痕）
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§17.3 job 族门禁口径/login-log 契约）
  - docs/design/services/access-service.md（audit 能力包）
depends_on:
  - T-ACCESS-086
blocks: []
acceptance:
  - "login-log/page 注册进 bootstrap 固定图（新 API 行）+服务层补 OPERATION_LOG:VIEW 门禁；经 Gateway 持权限可查、无权限 403（存量库升级须重建，deployment.md 注明）"
  - "OperationLogAspect 在 proceed 异常时记录失败再上抛（@OperationLog 写端点覆盖）；无 SERVICE:MANAGE 会话直连 service-credential/create →403+operation_log 落一条"
  - "responseCode 记录真值（不再硬编码 200）用例"
  - "Gateway DENY 拒绝（PermissionFilter:245 不达下游）的取证出口设计落地或显式登记"
  - "bootstrap 种子写入审计与 PII 统一脱敏层：评估结论登记（至少登记，不强制实现）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-ACCESS-085 审计门禁与留痕补齐

## 背景

login-log 服务层零业务门禁（`LoginLogController.java:51-58` 仅认证+租户上下文；对照 operation-log/change-log 各有 VIEW 门禁）——经 Gateway 恒 403（未注册固定图）、纯会话直连 403、仅内部密钥通道可达，属纵深防御缺口（口径同 job 族，契约 §17.3）。操作日志只记成功：OperationLogAspect 记录点在 proceed 成功路径、responseCode 硬编码 200、request_body 恒 NULL（T-ACCESS-025 停用）。bootstrap 种子写入零审计；审计侧 PII 无统一脱敏层。

## 范围

login-log 转正（固定图路由+门禁）、两类审计留痕（服务层失败留痕+拒绝尝试审计）、responseCode 真值。审计三表保留策略与导出不在本卡（维持现状，Q-061）。

## 当前口径

login-log 注册进固定图供管理员经 Gateway 使用（2026-10-05 拍板 D2=②），服务层复用 OPERATION_LOG:VIEW（零新增权限码）。留痕分两类：服务层切面 catch 记失败再上抛（覆盖 @OperationLog 写端点）；拒绝尝试审计最小改=服务层业务门禁拒绝处显式留痕（复用 requestId），Gateway 层拒绝的取证出口随路由注册一并设计。本卡固定图加行以 T-ACCESS-086 备份规程为前置（存量库 fail-fast 重建）。

## 验收对照

- [ ] login-log 经 Gateway 可查+门禁生效
- [ ] 写端点失败留痕（指明端点的用例）
- [ ] responseCode 真值用例
- [ ] Gateway 拒绝取证出口落地或登记
- [ ] bootstrap 审计与 PII 脱敏评估登记

## 非目标 / 遗留

- 审计三表导出端点/分区/TTL、sys_login_log.location 死列：Q-061。
- request_body 恢复写入（T-ACCESS-025 停用决策不推翻）。
