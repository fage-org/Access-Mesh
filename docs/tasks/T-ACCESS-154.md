---
doc_type: task
id: T-ACCESS-154
title: 认证通道矩阵与 OAuth2 职责核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/services/gateway.md
  - docs/design/browser-session-authentication.md
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

来源：原报告（外评快照按定规不入库） §二 gateway、§三.1、§四登录；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

匿名、Cookie、Bearer、OAuth2、M2M 有独立前提；自有 OAuth2 将授权码、刷新、客户端状态、密码指纹、租户代次和注销联动，原报告未指出新的协议漏洞。

## 范围

逐入口对照身份互斥/白名单/放行矩阵及已有测试，定位 OAuth2 不变量的实现归属与实际重复；结合 Redis 往返和在线回源占比判断是否需要测量。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐先补缺失的边界表达和独有行为证据，再按稳定协议阶段整理具体实现。备选为保持实现仅校准导航；没有证据不新增认证策略框架或统一万能登录入口。

待用户逐项确认：哪些协议阶段需要拆分、哪些矩阵缺口属于交付范围及性能观测成本；不因类大或认证形态多直接立重构结论。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/services/gateway.md](../../docs/design/services/gateway.md)、[docs/design/browser-session-authentication.md](../../docs/design/browser-session-authentication.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

