---
doc_type: task
id: T-PERM-078
title: 自动授权实施前协议与算法校准
status: in-progress
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
last_updated: 2026-09-21
---

# T-PERM-078 自动授权实施前协议与算法校准

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md) R001 与来源证明问题。简化方向已采纳，U009 的路径存储选择已解决；本卡继续承载实施前协议与算法细化，不因方向确认而冒充全部验收完成。

## 当前口径

实施依据为[自动授权设计](../design/dependency-auto-grant.md)，不是已被取代的候选评估稿。[M1～M5](../design/dependency-auto-grant.md#implementation-decisions)按实施前决策核实并回写权威设计与任务。M3 管理写边界、M1 的 FULL 代次拒旧/完整空资源清单/停用不影响传播、M2 保留事实并精确去重及 M5 参考性预览与按最新事实保存已确定；存量迁移、真实引擎验证及其余未决协议继续收敛，不因定案宣称本卡验收完成。

**M1 必须决定的接入边界**：资源 FULL 的发布代次与增量 sync 的逐业务键 `syncVersion` 当前不可比较。`ResourceEntitySyncReq` 仅有 `SyncVersionRef`，`ResourceEntityFullSyncReq` 当前仅 scope/items；FULL 缺失集通过 `seenBusinessKeyHashes` 判断，遍历 scope metadata 后直接标 DELETED/软删，省略项没有可用于版本比较的请求项。资源树锁只串行化服务端事务，不能改变请求在源侧采集的时间顺序。

以仓库真实接入测试 `CustomResourceTypeSlicePgIT` 的 `e2e-order-service / E2E_ORDER / ORDER-1001` 链路为基础：FULL 42 已采集仅含 ORDER-1001，随后源侧新增 ORDER-1002 并通过单条 UPSERT 成功；FULL 42 后到，虽然没有比它更新的 FULL，仍会将 ORDER-1002 当缺失项删除。反向交错同样存在：删除资源后的 FULL 已完成，延迟的旧 UPSERT 没有共同快照顺序，可能重建刚清理的事实。这个问题已有本卡验收承接，不重复登记 pending-problems。

待选择：①混用 FULL 的资源 scope 中，增量与 FULL 共同携带发布源确定的可比较顺序，平台实现双向旧请求防护，纯增量 scope 可保持现役协议；②增量请求形状保持现役，由接入方在采集 FULL 前暂停该 scope 的变更/增量，排空在途请求，FULL 完成后再恢复，平台只保证已定 FULL 对 FULL 拒旧。两者分别增加协议迁移成本或源侧协调/暂停成本，尚未采纳任何选项；不得据此实现新字段或降低一致性承诺。

**迁移证据边界**：本机 Docker 当前仅见 ItInfra 测试 PG/Redis，默认应用数据库 5432 没有监听；这些测试库不能证明部署环境旧依赖数据为空。旧依赖仍不得静默转为自动生效或直接清空，实际数据核查与迁移继续由本卡/071 承接。

## 范围

校准独立资源/依赖发布生命周期、规范化与互斥、管理声明权限、并发协议、来源解释/预览协议；引用已有小图证据并设计真实链路验证，不建立平行实现任务。本卡为设计细化，产品代码由 071～073 实现。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

保持 scope_all/父继承种子边界，不自动解除 T-PERM-036/054 暂缓。无逐路径 support 及强制统一大清单的重新选型任务；若实施证明必须改变已采纳方向，另行明确决定并登记，不能静默回退。
