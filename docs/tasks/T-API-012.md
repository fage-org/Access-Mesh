---
doc_type: task
id: T-API-012
title: 扩展面文档对齐与 JSON 校验收紧
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/extension-guide.md（§2/§3 对齐修订）
  - docs/design/access-service-api-contract.md（extra 校验语义）
depends_on: []
blocks: []
acceptance:
  - "extension-guide 四处滞后修订对齐契约（所有者角色删除守卫/条件权限定语/depend_on 父上下文/操作位上限与 20069）"
  - "公共 JSON 校验能力升级：JsonValidationUtils 启用 FAIL_ON_TRAILING_TOKENS 或等价整段校验（现为裸 readTree，\"{} {}\" 不报错）"
  - "升级后统一接入 type extra 与同款消费面（system-config/domain-config/条件规则/审计快照）+长度上限"
  - "\"{} {}\" 得到 HTTP 200+code=20044 明确参数错误（§3.3 分类状态码），不再兜底 500；单根合法 JSON 照常通过"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-API-012 扩展面文档对齐与 JSON 校验收紧

## 背景

extension-guide 四处滞后于实现与契约；type extra 有语法前置校验但非整串校验——尾随多根（`extra="{} {}"`）穿透 readTree 前置校验，原样字符串绑定进 PG jsonb 被拒，落兜底 500；无长度上限。同款裸 readTree 消费面多处（system-config/domain-config/条件规则/审计快照）。

## 范围

guide 四处修订、校验工具升级、消费面统一接入与长度上限。

## 当前口径

先升级公共校验能力（整段解析语义——FAIL_ON_TRAILING_TOKENS 或 readValue+nextToken 判空），再统一接入各消费面；错误形态遵循 §3.3 分类状态码（参数错误=HTTP 200+code=20044）。

## 验收对照

- [ ] guide 四处对齐
- [ ] 工具升级（尾随拒绝语义实证）
- [ ] 消费面统一接入+长度上限
- [ ] "{} {}"→200+20044 用例；单根通过

## 非目标 / 遗留

- HTTP 400 化的参数错误方言：不涉及（§3.3 既有定案）。
