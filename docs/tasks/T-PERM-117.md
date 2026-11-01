---
doc_type: task
id: T-PERM-117
title: 主体组织写入与 FULL 同步规模核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/redis-transaction-locks.md
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

来源：原报告（外评快照按定规不入库） §三.3/4/13；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

创建/编辑/绑定/同步共用租户级主体生命周期边界；FULL 在共同锁内预载、应用和校准，组织还联动角色/资源/成员，org_type 兼容标签与数值。

## 范围

枚举竞争写入口、锁序、锁内工作与现有观测；定位 FULL 规模与重试说明，核对组织联动统一入口和 org_type 的真实写入/兼容集合。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐先测锁等待、持锁时长和批次规模，明确支持边界后再考虑细锁。FULL 不分批提交，尚不存在的主体与漂移清理需共同边界；org_type 推荐收敛新增写入，保留既有读取兼容。

待用户逐项确认：是否增加观测及其范围、支持规模/执行时间目标、是否改变锁粒度、org_type 的统一写入格式；不得把现有串行锁自动视为吞吐缺陷。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/default-org-tree-user-lifecycle.md](../../docs/design/default-org-tree-user-lifecycle.md)、[docs/design/redis-transaction-locks.md](../../docs/design/redis-transaction-locks.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

