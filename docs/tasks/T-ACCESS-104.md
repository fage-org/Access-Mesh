---
doc_type: task
id: T-ACCESS-104
title: 无权删除全拒反馈修正（角色与资源批删链）
status: done
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
depends_on: []
blocks: []
acceptance:
  - "角色与资源两条批删链（RoleManageAppServiceImpl.deleteRoles / ResourceManageAppServiceImpl.deleteResources）：请求含有效目标但全部被权限拒绝时，服务端明确返回拒绝（非 markSkip 静默成功）；「目标不存在」保持幂等跳过语义（回归锁区分三态：全拒/全不存在/混合，旧实现下全拒用例失败）"
  - "前端 role 与 resource-operation 两处删除反馈消费新结果口径，不再对全拒未删提示成功；混合批量消费已删除/无权跳过/不存在的目标明细"
  - "无权拒绝路径进入现行操作审计取证（核对 markSkip 的跳过语义是否仍适用）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

P2（外部持续审查发现并合并登记，主代理核实成立）：管理员持部分 ROLE:MANAGE/RESOURCE:MANAGE 时可查看但无权管理的目标上，删除按钮由聚合权限门控仍可点击。`RoleManageAppServiceImpl.java:378-394` 过滤无权角色，permittedIds 为空时 `OperationLogRuntimeContext.markSkip()` 后直接返回，Controller 返回 `R.ok()`；`ResourceManageAppServiceImpl.java:620-652` 的资源删除同形态（全拒 markSkip 返回）。前端 `role/utils/hook.ts:215-220` 与 `resource-operation/utils/hook.ts:267-270` 无条件提示删除成功——单项操作未发生却显示成功，脚本按 code=200 误判已完成；markSkip 同时使拒绝不进入入口级操作审计。服务删除入口有 SERVICE 类型级 MANAGE 硬拒绝，不属本形态。

## 范围

角色/资源批删的服务端全拒语义、两处前端反馈、审计取证核对；契约对应删除端点语义回写。

## 当前口径

存在有效目标但全被权限拒绝时明确返回拒绝；「目标不存在」幂等语义保留；混合批量保留删除有权部分的语义，响应返回已删除、无权跳过、不存在的请求目标明细，前端据此区分完整成功、部分成功与无可删除目标；级联规则保持。当前没有接入方，无旧响应兼容负担。优先复用现有权限校验/异常响应/审计机制，不引入通用批量状态机或后台删除任务；不修改契约 §10.3 级联根/类型所有者的既有删除规则。

## 验收对照

- [x] 角色与资源全拒抛 SecurityException；全不存在幂等返回明细；混合仅删除有权部分。
- [x] 响应区分 deleted/denied/notFound，请求目标去重；级联与所有权守卫保持。
- [x] 前端按真实结果反馈，拒绝不标审计跳过；HTTP 签名快照与 DTO 对账已同步。

## 非目标 / 遗留

越权写入不涉及（无权目标从未被删除）；服务登记删除（SERVICE 硬拒绝形态）不在范围。

## 完成记录

2026-10-17：`mvn test -T 1C` 全 reactor 通过，包含 E2E 与 heavy；本次生成的 Surefire XML 共 2860 个 testcase，失败/错误/跳过均为 0（按 XML 实际 testcase 计数，避免嵌套测试汇总行差异）。
前端 `pnpm test` 50 文件、562 项通过；`pnpm typecheck`、`pnpm lint:check`、`pnpm build` 与 `pnpm contracts:check`（44 组）通过。
两条全拒用例在旧行为下失败；三态单测、真实操作日志上下文与签名快照同步通过。全量发现的旧跳过审计断言与旧 Void 快照均经隔离复现、按已定案契约更新，定向 11 项通过后完成全量验证。

代码轨核对全拒/不存在/混合、完整键与去重、最终级联集回报、审计未跳过及前端结果消费；文档轨核对两端点签名、返回明细、级联/所有权规则与 DTO 对账。两轨无未解决 P0–P2 或待决策项；未引入无调用方机制。
