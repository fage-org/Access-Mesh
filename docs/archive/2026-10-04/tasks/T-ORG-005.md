---
doc_type: task
id: T-ORG-005
title: 组织/岗位动作码判定入口覆盖定案
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/org-user-permission-contract.md §5（关系动作归属契约）
  - docs/design/access-service-api-contract.md §8（org 能力）
depends_on: []
blocks: []
acceptance:
  - "创建用户保留 USER:CREATE，挂普通组织/岗位分别使用 MANAGE_MEMBER/ASSIGN_POSITION_USER，不新增操作码或端点"
  - "目标类型与默认树范围在同一既有 SYS_ORG 锁内读取，事务与投影保持"
  - "岗位可见性裁剪维持 T-ACCESS-055 的既有 ORG:VIEW 边界，影响面与候选门禁不扩改"
  - "组织关系契约 §5 与总册创建用户门禁表同步，有限成员管理员回归与真实投影链通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ORG-005 组织/岗位动作码判定入口覆盖定案

## 背景

承接 [Q-032](../../../pending-problems.md#q-032)（合并 Q-034）：两处入口覆盖缺口——创建用户并挂组织用 ORG:UPDATE（已有用户挂载走成员动作码，仅持 MANAGE_MEMBER 的管理员不能一步创建并挂载）；岗位可见性裁剪 filterVisibleOrgIds 固定 VIEW（org 树读面按 orgType 分发 VIEW/VIEW_POSITION），影响成员候选池与 user/delete 默认树可见性校验。有限管理员操作可能过严；VIEW 裁剪可能让岗位成员进候选池但有候选门禁，不直接推导越权。

## 范围

定案卡：盘点+逐项拍板+契约修订；实施视拍板范围（超出另立）。

## 当前口径

T-ACCESS-055 对②的维持现状拍板是既定边界；本卡在其内收敛，不重开已拍选项。

创建用户仍要求 USER:CREATE；带 orgId 的挂载按组织/岗位分别使用 MANAGE_MEMBER / ASSIGN_POSITION_USER，不再要求编辑组织的 UPDATE（2026-10-04 确认）。沿用现有 OrgOperationCodeMapper，不增加新操作码或端点，实施纳入本卡。

## 非目标 / 遗留

- 候选门禁本身（另有口径）。

## 验收对照

- [x] 创建用户保留 USER:CREATE，挂普通组织/岗位分别使用 MANAGE_MEMBER/ASSIGN_POSITION_USER，不新增操作码或端点
- [x] 目标类型与默认树范围在同一既有 SYS_ORG 锁内读取，事务与投影保持
- [x] 岗位可见性裁剪维持 T-ACCESS-055 的既有 ORG:VIEW 边界，影响面与候选门禁不扩改
- [x] 组织关系契约 §5 与总册创建用户门禁表同步，有限成员管理员回归与真实投影链通过

## 完成记录

2026-10-04：定向、代码轨与文档轨核对完成；最终 `mvn test -T 1C` 2468 项零失败/错误/跳过，含 E2E 与 heavy。前端 487 项及 typecheck/lint/build 通过。[定向证据](evidence/T-ORG-005/verification.md)，[最终验收](evidence/pending-problems-clearance/final-audit.md)。
