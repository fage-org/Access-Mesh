---
doc_type: task
id: T-ADMIN-036
title: 菜单读取故障与无权限表达核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/frontend/login.md#session-permissions
  - docs/design/access-service-architecture.md
  - docs/design/access-service-api-contract.md
depends_on: []
acceptance:
  - "核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。"
  - "形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。"
  - "用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。"
  - "核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §三.5、§四公共 HTTP/列表；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

UserMenuQueryAppServiceImpl 对部分权限读取异常返回空权限或缩减菜单，安全保守但可能与真实无权混淆；前端已有菜单失败和重试设施，需判断是否覆盖后端吞错。

## 范围

对照菜单/用户信息响应、前端 menuLoadFailed 和现有重试，实证读取故障能否被用户区分，核对目录/纯展示菜单的降级。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐复用现有错误状态和重试入口，仅补后端被吞故障的表达；备选保持降级但清楚提示。任何新增字段/错误码/菜单响应行为先登记契约及 DTO 对账。

待用户逐项确认：保持缩减菜单并带状态，还是以加载失败返回；登录半成功、菜单重试与既有只读导航如何一致。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/frontend/login.md#session-permissions](../../docs/design/frontend/login.md#session-permissions)、[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

