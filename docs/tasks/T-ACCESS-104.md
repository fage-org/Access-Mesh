---
doc_type: task
id: T-ACCESS-104
title: 无权删除全拒反馈修正（角色与资源批删链）
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
depends_on: []
blocks: []
acceptance:
  - "角色与资源两条批删链（RoleManageAppServiceImpl.deleteRoles / ResourceManageAppServiceImpl.deleteResources）：请求含有效目标但全部被权限拒绝时，服务端明确返回拒绝（非 markSkip 静默成功）；「目标不存在」保持幂等跳过语义（回归锁区分三态：全拒/全不存在/混合，旧实现下全拒用例失败）"
  - "前端 role 与 resource-operation 两处删除反馈消费新结果口径，不再对全拒未删提示成功；混合批量的实际删除/跳过结果披露口径在实施时确认（R<Void> 或结果体，按契约章裁定）"
  - "无权拒绝路径进入现行操作审计取证（核对 markSkip 的跳过语义是否仍适用）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P2（外部持续审查发现并合并登记，主代理核实成立）：管理员持部分 ROLE:MANAGE/RESOURCE:MANAGE 时可查看但无权管理的目标上，删除按钮由聚合权限门控仍可点击。`RoleManageAppServiceImpl.java:378-394` 过滤无权角色，permittedIds 为空时 `OperationLogRuntimeContext.markSkip()` 后直接返回，Controller 返回 `R.ok()`；`ResourceManageAppServiceImpl.java:620-652` 的资源删除同形态（全拒 markSkip 返回）。前端 `role/utils/hook.ts:215-220` 与 `resource-operation/utils/hook.ts:267-270` 无条件提示删除成功——单项操作未发生却显示成功，脚本按 code=200 误判已完成；markSkip 同时使拒绝不进入入口级操作审计。服务删除入口有 SERVICE 类型级 MANAGE 硬拒绝，不属本形态。

## 范围

角色/资源批删的服务端全拒语义、两处前端反馈、审计取证核对；契约对应删除端点语义回写。

## 当前口径

存在有效目标但全被权限拒绝时明确返回拒绝；「目标不存在」幂等语义保留；混合批量是否部分成功及结果披露形态按契约章在实施时确认。优先复用现有权限校验/异常响应/审计机制，不引入通用批量状态机或后台删除任务；不修改契约 §10.3 级联根/类型所有者的既有删除规则。

## 验收对照

见 frontmatter `acceptance`；三态回归锁中「全拒」用例须旧实现下失败。

## 非目标 / 遗留

越权写入不涉及（无权目标从未被删除）；服务登记删除（SERVICE 硬拒绝形态）不在范围。
