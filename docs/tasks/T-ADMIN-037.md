---
doc_type: task
id: T-ADMIN-037
title: 平台公告与文件容量边界核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/access-service-api-contract.md
  - docs/design/frontend/file-management.md
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

来源：原报告（外评快照按定规不入库） §三.17、§五数据库；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

公告本人查询读取全部可见记录；文件下载整文件 readAllBytes，单文件上限不能限制并发内存。字典锁、任务和有限系统配置均有真实用途，不能当残留删除。

## 范围

实证公告积累与文件并发的真实成本、分页/筛选语义、下载响应/权限/失败清理及本地存储单实例前提；核对软删与 JSONB 数据增长，区分已接受范围。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐先确认支持规模，再选择公告分页/数量边界或文件流式传输等局部方案。备选保持现状并明确限制；不先建网盘、对象存储平台、任意配置中心或全面数据库分区。

待用户逐项确认：公告是否改分页及 API/UI 兼容；文件是否改流式、并发限制和部署承诺；数据清理/索引只有证据后另定。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)、[docs/design/frontend/file-management.md](../../docs/design/frontend/file-management.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

