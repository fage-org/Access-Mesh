---
doc_type: task
id: T-ADMIN-028
title: "OAuth2 授权码客户端关联校验"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#oauth
  - docs/design/access-service-architecture.md
  - docs/design/access-service-api-contract.md
depends_on:
  []
blocks: []
acceptance:
  - "同租户A/B客户端的授权码不可交叉兑换；合法A兑换保留scope/audience/tenant关联。"
  - "PKCE、redirect、过期、重复兑换及失败code消费语义经适用反例核实，敏感值不写日志。"
  - "现有测试夹具可重现旧实现缺口；若工具限制动态步骤，记录阻塞并保留任务未验收状态。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ADMIN-028 OAuth2 授权码客户端关联校验

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F002；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- OAuth2AppServiceImpl 授权码签发/兑换、现有客户端认证、PKCE及错误处理的关联校验。
- 复用本地OAuth2测试夹具形成跨客户端负向行为锁；不操作真实第三方账号。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#oauth)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：无独立设计取舍；实现先核实证据，按推荐最小方案与现行约束执行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
