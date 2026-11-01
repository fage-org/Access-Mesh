---
doc_type: task
id: T-PERM-116
title: SDK 协议与客户端职责边界核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/service-authentication.md
  - docs/design/extension-guide.md
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

来源：原报告（外评快照按定规不入库） §二 perm-common／client／registration、§七 SDK 管理接口；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

perm-common 含协议与 Feign 传输适配；PermissionFeignClient 混有运行时、同步和管理方法，各方法认证前提不同；registration 有真实显式发布消费者。

## 范围

枚举真实消费方、认证前提、宿主装配及依赖边；核对按包分层和拆小接口是否能降低误用，识别未消费管理方法的兼容承诺。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐先明确协议/算法/传输包边界与方法身份说明，若需拆接口仍留同一 starter。备选仅补文档；不新拆 Maven 模块、不默认删除 SDK 公共方法，registration 保持显式发布。

待用户逐项确认：接口拆分和兼容保留方式、零消费管理 API 是否仍受支持；用户确认前保留方法和两个宿主探针。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/service-authentication.md](../../docs/design/service-authentication.md)、[docs/design/extension-guide.md](../../docs/design/extension-guide.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

