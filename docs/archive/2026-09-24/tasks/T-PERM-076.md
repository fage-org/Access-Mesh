---
doc_type: task
id: T-PERM-076
title: "资源批量创建复合身份一致性"
status: done
plan: docs/archive/2026-09-24/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#resource-key
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md
  - docs/design/schema/access-service.sql
depends_on:
  []
blocks: []
acceptance:
  - "PROJECT/default/X与DOC/default/X、同类型不同codeType均可按契约分别创建；同完整键仍识别重复。"
  - "存量和批内重复共享完整键处理，部分成功不因一个重复项变整批SQL失败。"
  - "PgIT验证真实唯一键/返回ID，所有权和同类型父边约束不放宽；无需新幂等表。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-22
---

# T-PERM-076 资源批量创建复合身份一致性

## 背景

承接[评审证据](../../../archive/2026-09-20/comprehensive-review.md)的 F006；基线与静态/动态证据强度见该记录。实施与验证见完成记录。

## 范围

- ResourceManageAppServiceImpl批量查重、ResourceEntityDomainService/Mapper及完整资源业务键。
- 本批重复项、部分成功和成功响应身份，与单条创建对齐。

## 当前口径

方案唯一入口：[IAM闭环方案](../../../design/iam-task-closure.md#resource-key)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：无独立设计取舍；实现先核实证据，按推荐最小方案与现行约束执行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 完成记录（2026-09-22）

- **核实**：F006 三面全实证——批量查重仅按 tenant+code（`findExistingCodes` 链丢 type/codeType 两维：同码跨类型/跨 codeType 误拒 + 批内同完整键双双落库撞 `uk_resource_entity` 整批 SQL 失败）；`insertBatch` 不回填自增主键（投影轨 `BatchAdminUserProjectionWriter` 回查先例实证注释），批量成功响应 id 恒 null；畸形项（code/name 空白，items 不级联 Bean Validation 的既定拍板）原样落库以 NOT NULL 违例连坐整批。
- **实现**：完整键三元组查重（`TripleKey` 元组键 + 一次 `selectByTypesAndCodesAndCodeTypes` 跨类型查询笛卡尔超集+内存精确比对，remove 键解析同款）——存量与本批已接受项同享身份、批内同完整键首项胜出逐项跳过；畸形项宽容收集；成功响应经完整键回查校准主键；`findExistingCodes` 查询链四处（接口/实现/Mapper/XML）退役删除。单条 create 语义不变，无新幂等表，所有权门禁与同类型父边约束零改动。
- **回归锁**：`ResourceManageAppServiceImplTest` T-PERM-076 节用例旧实现下实证红（批内重复 `<1> but was <2>`、id 回查 `<101> but was <null>`、畸形项 `<1> but was <5>`）；`ResourceBatchCreateCompositeIdentityPgIT` 真库验证唯一键放行/拒绝、返回主键可回查、API 类型 20055 不放宽、全空类型码真守卫链路（全绿与否以当轮 surefire 报告为准）。
- **用户拍板（当轮 registry 登记）**：①畸形项本卡顺手修；②CHANGELOG 补 [Unreleased] Fixed 条目；③全批类型码 null/空白批 NPE（双轨评审 P3）本卡顺手修。
- **双轨评审处置（P0-P2=0）**：代码轨 P3——全空类型码批 `Map.of().get(null)` NPE（存量缺陷，与畸形项收集主题相邻）→ 用户拍板顺手修（主循环 null 防护落既有「未知类型」分支，定点红跑实证+PgIT 真守卫链路用例）；文档轨 P3——registry/iam-task-closure 引述 T-ADMIN-028 先例措辞与被引行原文不一致 → 事实性修正统一为「安全收紧类补先例扩展适用于正确性收紧类」。requireNonNull 异常形式与跳过原因不回传两项核实后维持现状（评审倾向一致，无行为变更）。
- **claude 外评处置（P0-P2=0，P3×2 全采纳，registry 同日行）**：P3-1 三元组拼接键非单射（code/codeType 含 `:` 塌缩）→ 6 个生产调用点（本卡 4 新 + remove 键解析 2 存量同根因）改 `TripleKey` 元组键，塌缩回归锁×2 旧实现下实证红；P3-2 任务卡计数漂移 → 按去计数化删计数。存量观察①登记 Q-031（sync codeType 归一不 trim）；`BusinessKeyUtil` 单射化属 golden 锁键面变更不在本卡射程。
- **设计回写**：iam-task-closure §2.5 转已实施+实施口径；契约 §12.1 规则表补「create/batch-create 查重身份」行；CHANGELOG Fixed；registry 2026-09-22 收口行；plan/看板状态同步。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
