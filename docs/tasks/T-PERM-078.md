---
doc_type: task
id: T-PERM-078
title: 自动授权实施前协议与算法校准
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#auto-grant
  - docs/design/dependency-auto-grant.md#implementation-decisions
  - docs/design/dependency-auto-grant.md#integration
  - docs/design/dependency-auto-grant.md#materializer
  - docs/design/dependency-auto-grant.md#consistency
  - docs/design/dependency-auto-grant.md#explain
  - docs/design/access-service-api-contract.md
depends_on: []
blocks: []
acceptance:
  - "已采纳简化方向与取代关系明确：独立资源/依赖接口、可选 SDK 协调、写时物化、按需解释、不建全路径 support；不再要求重新选择是否采用。"
  - "设计 M1 完整收敛：FULL 发布新旧、空资源集、动态刷新、停用/恢复和重试都有确定规则，纯资源接入不承担依赖配置。"
  - "设计 M2 完整收敛：不同操作 NULL、条件 OR、共享来源撤销、被压制节点、非传递/相互覆盖及互斥反例有可检验预期；明确真实引擎验证方式。"
  - "设计 M3/M4/M5 收敛：同 owner 管理声明及存量迁移、编译/新增种子并发协议、explain/预览一致视图与正式接口边界明确。"
  - "071/072/073 验收与依赖按最终细化同步，设计及契约回写；未决细节未收敛不得 done，不把分析实验当产品交付。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-078 自动授权实施前协议与算法校准

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md) R001 与来源证明问题。简化方向已采纳，U009 的路径存储选择已解决；本卡继续承载实施前协议与算法细化，不因方向确认而冒充全部验收完成。

## 当前口径

实施依据为[自动授权设计](../design/dependency-auto-grant.md)，不是已被取代的候选评估稿。[M1～M5](../design/dependency-auto-grant.md#implementation-decisions)由本卡启动时逐项核实和决定，再回写权威设计与任务。未决项留到实施时决策，当前不要求用户逐项表态。

## 范围

校准独立资源/依赖发布生命周期、规范化与互斥、管理声明权限、并发协议、来源解释/预览协议；引用已有小图证据并设计真实链路验证，不建立平行实现任务。本卡为设计细化，产品代码由 071～073 实现。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

保持 scope_all/父继承种子边界，不自动解除 T-PERM-036/054 暂缓。无逐路径 support 及强制统一大清单的重新选型任务；若实施证明必须改变已采纳方向，另行明确决定并登记，不能静默回退。
