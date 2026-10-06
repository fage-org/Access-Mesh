---
doc_type: task
id: T-ACCESS-085
title: 审计门禁与留痕补齐（login-log 转正+失败留痕）
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§17.3 job 族门禁口径/login-log 契约）
  - docs/design/access-service-architecture.md（audit 能力包/审计章节）
  - docs/design/frontend/operation-log.md（requestId 检索与读脱敏）
  - docs/ops/deployment.md（拒绝取证）
depends_on:
  - T-ACCESS-086
blocks: []
acceptance:
  - "login-log/page 注册进 bootstrap 固定图（新 API 行）+服务层补 OPERATION_LOG:VIEW 门禁；经 Gateway 持权限可查、无权限 403（存量库升级须重建，deployment.md 注明）"
  - "OperationLogAspect 在 proceed 异常时记录失败再上抛（@OperationLog 写端点覆盖）；无 SERVICE:MANAGE 会话直连 service-credential/create →403+operation_log 落一条"
  - "responseCode 记录真值（不再硬编码 200）用例"
  - "Gateway DENY 拒绝（PermissionFilter:245 不达下游，服务层切面无法覆盖）的取证出口随 login-log 路由注册一并设计并**落地**（拒绝留痕可见，非仅登记）"
  - "bootstrap 种子写入审计：评估结论登记（不强制实现）"
  - "PII 统一脱敏层**落地**：登录/操作/变更日志的读取与响应转换层统一脱敏（密码路径 username、摘要 SpEL、diff_snapshot.extra 三处来源面），保留审计原始记录"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-ACCESS-085 审计门禁与留痕补齐

## 背景

login-log 服务层零业务门禁（`LoginLogController.java:51-58` 仅认证+租户上下文；对照 operation-log/change-log 各有 VIEW 门禁）——经 Gateway 恒 403（未注册固定图）、纯会话直连 403、仅内部密钥通道可达，属纵深防御缺口（口径同 job 族，契约 §17.3）。操作日志只记成功：OperationLogAspect 记录点在 proceed 成功路径、responseCode 硬编码 200、request_body 恒 NULL（T-ACCESS-025 停用）。bootstrap 种子写入零审计；审计侧 PII 无统一脱敏层。

## 范围

login-log 转正（固定图路由+门禁）、两类审计留痕（服务层失败留痕+拒绝尝试审计）、responseCode 记录公开信封 code（2026-10-06 用户选择 A）：成功 200、业务异常业务码、安全拒绝 403、兜底 99999，不新增 HTTP 状态列。审计三表保留策略与导出不在本卡（维持现状，Q-061）。

## 当前口径

login-log 注册进固定图供管理员经 Gateway 使用（2026-10-05 拍板 D2=②），服务层复用 OPERATION_LOG:VIEW（零新增权限码）。留痕分两类：服务层切面 catch 记失败再上抛（覆盖 @OperationLog 写端点）；拒绝尝试审计最小改=服务层业务门禁拒绝处显式留痕（复用 requestId），Gateway 拒绝采用平台内取证（2026-10-06 用户选择最小 B）：新增仅平台内部互信可调用的审计写入口，复用 operation_log 与既有管理页，补 requestId 检索；Gateway 发送失败兜底告警但不改变原 403，不引入 MQ、新表或日志系统。PII 统一脱敏层为交付项（2026-10-06 用户选择 B）：读取/响应转换层仅掩码手机号、邮箱、凭据等明确敏感值，普通用户名及完整 IP 保留用于排障；JSON 敏感字段与普通摘要文本同源处理，保留数据库原始记录，不实现可配置规则引擎；bootstrap 审计为评估登记。本卡固定图加行以 T-ACCESS-086 备份规程为前置（存量库 fail-fast 重建）。

## 验收对照

- [x] login-log 经 Gateway 可查+门禁生效
- [x] 写端点失败留痕（指明端点的用例）
- [x] responseCode 真值用例
- [x] Gateway 拒绝留痕落地（随路由②一并交付）
- [x] bootstrap 审计评估登记
- [x] PII 统一脱敏层落地（三日志读取面，保留原始记录）

## 非目标 / 遗留

- 审计三表导出端点/分区/TTL、sys_login_log.location 死列：Q-061。
- request_body 恢复写入（T-ACCESS-025 停用决策不推翻）。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
