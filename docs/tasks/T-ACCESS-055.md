---
doc_type: task
id: T-ACCESS-055
title: "核心用户任务组合验收与文档收口"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md
  - docs/design/extension-guide.md
  - docs/quickstart.md
  - docs/design/access-service-architecture.md
depends_on:
  - T-ORG-002
  - T-ADMIN-028
  - T-PERM-074
  - T-PERM-075
  - T-PERM-076
  - T-FE-057
  - T-FE-058
  - T-API-004
  - T-ADMIN-029
  - T-FE-059
  - T-PERM-077
  - T-ACCESS-052
  - T-ACCESS-053
blocks: []
acceptance:
  - "首启/普通用户/有限管理员/服务接入/类型首授追加操作/条件变更/授权撤销/岗位恢复/同步重试/根拒删各有实际结果。"
  - "双租户相同业务码使用现有隔离基建构造完整类型/身份/角色/资源夹具，验证跨读/跨写/授权隔离；不把夹具初始化宣称租户开通UI。"
  - "有限管理员由UI/API合法赋权后实际操作页面；未跑或受限项目明确未验收，不能仅凭全量绿通过。"
  - "按AGENTS完成后端全量含E2E、前端适用检查与本地双轨，完整输出落盘；权威文档回写、所有F有处置证据。"
  - "验收证据记录步骤、预期、实际、缺口、提交基线与环境；各修复任务负责能在旧行为下失败的最小回归，本卡只补组合场景、不复制单元矩阵。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ACCESS-055 核心用户任务组合验收与文档收口

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 S001～S012、全部F的组合证据；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 复用现有测试资产补核心跨模块组合和必要浏览器场景，形成可追溯验收证据，避免重写逐模块测试。
- 核对已解决/已知/暂缓声明与产品入口和接入文档一致；不自动启动036/054或自动授权实现。

## 当前口径

总体设计见 [IAM 闭环方案](../design/iam-task-closure.md)；本卡 frontmatter acceptance 为组合验收唯一清单，设计 §7.1 只回链本卡。沿各前置任务已定口径验证，涉及现行定案变化时先核准并回写权威来源，再实施。

**待决与启动核实**：本卡继承各前置任务已解决的U项；不替它们做最终产品裁决。条件/双租户无法运行时记录具体阻塞，不取消该验收要求。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
