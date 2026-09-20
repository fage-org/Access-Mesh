---
doc_type: task
id: T-ACCESS-053
title: "首次服务接入与撤销验证路径简化"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#onboarding
  - docs/design/extension-guide.md
  - docs/quickstart.md
  - docs/ops/deployment.md
  - docs/design/service-authentication.md
  - docs/design/services/example-service.md
  - docs/design/access-service-api-contract.md
depends_on:
  - T-GW-010
blocks: []
acceptance:
  - "全新隔离环境沿文档无需猜缺失步骤，普通用户访问受保护接口允许/拒绝及撤销后结果有证据。"
  - "新凭证与旧内部密钥端点适用面和租户来源准确；U008明确是否需要进一步统一，不用扩大白名单掩盖。"
  - "业务类型扩展与授权根可沿文档完成；API ACCESS双层现状、T-PERM-054派生方向与T-PERM-036暂缓清楚区分。"
  - "未使用的新增框架/配置删除或不引入；既有可复用样例消费方保持兼容。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ACCESS-053 首次服务接入与撤销验证路径简化

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 接入体验建议、S002/S012；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 沿现有example/E2E形成服务注册、接口声明、路由、身份配置、API与业务权限、真实访问和撤销的可复制主线。
- 盘点重复配置/说明与实际消费者，优先统一文档/脚本，给失败下一步而非另造安装器。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#onboarding)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U008：是否统一两套服务身份的运行时能力？推荐先完成当前路径与诊断；若扩新凭证，先决定主体/资源查询范围。T-PERM-054/036暂缓未因本卡自动解除。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
