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
  - "设计 M1 完整收敛：FULL 已定发布源递增代次、平台拒旧与原代次重试，允许完整空资源清单仅清本同步范围，资源停用/恢复不改变自动传播；补齐代次协议、并发/部分失败、与单条 sync 交错及客户端迁移、动态刷新调用方；纯资源接入不承担依赖配置。"
  - "设计 M2 已定保留各操作/条件事实、仅完整事实键精确去重，不做覆盖压缩；不同操作 NULL、条件 OR、共享来源撤销、中间事实传播、非传递/相互覆盖及互斥反例有可检验预期，完成所需真实引擎验证，不以定案替代验证。"
  - "设计 M3/M4/M5 收敛：M3 已定 MANIFEST 唯一写入、关闭手工管理写入口，M5 已定参考性预览与按最新事实保存，影响变化不要求再确认；存量迁移仍需核实，编译/新增种子并发协议、explain/预览单次一致视图与正式接口边界明确。"
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

实施依据为[自动授权设计](../design/dependency-auto-grant.md)，不是已被取代的候选评估稿。[M1～M5](../design/dependency-auto-grant.md#implementation-decisions)按实施前决策核实并回写权威设计与任务。M3 管理写边界、M1 的 FULL 代次拒旧/完整空资源清单/停用不影响传播、M2 保留事实并精确去重及 M5 参考性预览与按最新事实保存已确定；存量迁移、真实引擎验证及其余未决协议继续收敛，不因定案宣称本卡验收完成。

## 范围

校准独立资源/依赖发布生命周期、规范化与互斥、管理声明权限、并发协议、来源解释/预览协议；引用已有小图证据并设计真实链路验证，不建立平行实现任务。本卡为设计细化，产品代码由 071～073 实现。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

保持 scope_all/父继承种子边界，不自动解除 T-PERM-036/054 暂缓。无逐路径 support 及强制统一大清单的重新选型任务；若实施证明必须改变已采纳方向，另行明确决定并登记，不能静默回退。
