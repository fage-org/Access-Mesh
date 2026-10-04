---
doc_type: task
id: T-ADMIN-033
title: 业务字段空值与长度校验对齐列宽
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §7（user）+ §8（org）+ §17.3（notice）
depends_on: []
blocks: []
acceptance:
  - "org 更新：orgName 保留 null=不更新语义、拒空白串（非 @NotBlank 误拒 null）；创建/更新 orgName/code、notice title、user username/name/phone/email 创建+更新全部按 sys_org/sys_user/sys_notice 列宽补 @Size 上限"
  - "超长输入经 DTO 校验前置拒绝，Controller @Valid 与统一异常处理接线为 HTTP 400/90001；数据库拒写风险不再由本族超长输入触发（验证边界见完成证据）"
  - "组织名空串写入口闭合：空名父不再通过 DTO 校验（旧校验放行空白的回归证据已验证）；存量空名处理定案（订正 runbook 或登记维持，按 decision-question-protocol 举例上报用户后拍板），不假称拒新输入就清理了旧数据"
  - "OrgForm 的 fallback 误显「根组织」形态随空串闭合复核；契约 §7/§8/§17.3 字段约束表同步"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ADMIN-033 业务字段空值与长度校验对齐列宽

## 背景

承接 [Q-018](../../../pending-problems.md#q-018)：三类缺口——① OrgUpdateReq.orgName 无非空白校验、updateOrg 只判非 null 可写空串（创建有 @NotBlank、前端有 required/min），空名父在信息卡为空、OrgForm fallback 还可能误显「根组织」；② UserCreateReq 的 username/name/phone/email 与 UserUpdateReq 的 name/phone/email 无列宽上限（sys_user VARCHAR 64/128/32/128），更新 phone/email 已有空白 Pattern 但不限长；③ OrgCreate/UpdateReq 的 orgName/code、NoticeCreate/UpdateReq.title 无上限。超长非空输入直接写库被拒、通用异常 500/99999、事务回滚，无静默截断。

## 范围

org/user/notice 三族创建+更新 DTO 的空白与长度校验；存量空名定案；契约约束表同步。字段格式限制与长度上限是不同契约——格式校验（已有 Pattern）不动。

## 当前口径

校验按目标列宽（schema 为唯一权威）；「null=不更新、空白=拒绝」的更新语义与 T-API-004 六字段口径同构但不扩撤其已交付范围。

存量处置沿用当前无部署环境、开发脏数据走既有重建库流程的边界，不新增订正 SQL（2026-10-04 确认）。新写入校验不代表历史空名已被清理；保留脏库时 OrgForm 的空名 fallback 仍可能显示「根组织」。

## 非目标 / 遗留

- 可选字段显式清空通道（Q-043 → T-API-005 定案，含 system-config description null 跳过问题）。

## 验收对照

- [x] org 更新：orgName 保留 null=不更新语义、拒空白串（非 @NotBlank 误拒 null）；创建/更新 orgName/code、notice title、user username/name/phone/email 创建+更新全部按 sys_org/sys_user/sys_notice 列宽补 @Size 上限
- [x] 超长输入经 DTO 校验前置拒绝，Controller @Valid 与统一异常处理接线为 HTTP 400/90001；数据库拒写风险不再由本族超长输入触发（验证边界见完成证据）
- [x] 组织名空串写入口闭合：空名父不再通过 DTO 校验（旧校验放行空白的回归证据已验证）；存量空名处理定案（订正 runbook 或登记维持，按 decision-question-protocol 举例上报用户后拍板），不假称拒新输入就清理了旧数据
- [x] OrgForm 的 fallback 误显「根组织」形态随空串闭合复核；契约 §7/§8/§17.3 字段约束表同步

## 完成记录

2026-10-04：定向验证与本地代码/文档双轨核对完成；完整 `mvn test -T 1C` 共 2462 项，零失败/错误/跳过，包含 E2E 与 heavy。[定向证据](evidence/T-ADMIN-033/verification.md)，[完整回归](evidence/pending-problems-clearance/batch-2026-10-04.md)。
