---
doc_type: task
id: T-PERM-078
title: 自动授权实施前协议与算法校准
status: done
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
  status: done
last_updated: 2026-09-21
---

# T-PERM-078 自动授权实施前协议与算法校准

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md) R001 与来源证明问题。简化方向已采纳，U009 的路径存储选择已解决；本卡继续承载实施前协议与算法细化，不因方向确认而冒充全部验收完成。

## 当前口径

实施依据为[自动授权设计](../design/dependency-auto-grant.md)，不是已被取代的候选评估稿。[M1～M5](../design/dependency-auto-grant.md#implementation-decisions)按实施前决策核实并回写权威设计与任务。M3 管理写边界、M1 的 FULL 代次拒旧/完整空资源清单/停用不影响传播、M2 保留事实并精确去重及 M5 参考性预览与按最新事实保存已确定；存量迁移、真实引擎验证及其余未决协议继续收敛，不因定案宣称本卡验收完成。

**M1 接入边界已定**：混用 FULL 的资源 scope 中，增量与 FULL 共同携带发布源确定的可比较顺序，平台提供双向旧请求防护；纯增量 scope 可保持现役协议。正式字段、首次切换、跨键乱序与部分失败重试已细化于设计 §4.4.1 与契约 §19.2.1，由 071 实施验证。真实反例是 FULL 42 采集 ORDER-1001 后，增量新增 ORDER-1002，晚到 FULL 不得删掉 ORDER-1002；反向延迟增量也不得重建较新 FULL 已清理的事实。设计唯一正文见 §4.4，不再保留候选方案。

**迁移证据与交付边界**：当前工作区没有可连接的应用数据库，不能据测试库推断部署零存量。按设计 §10.1 保全旧依赖原表，新编译表从空图开始且只能由所属服务 manifest 产出；升级脚本遇有效历史 AUTO_DEP 在改表前中止。部署时必须重查实际库；071 交付迁移脚本与旧 schema 场景回归，本目标不操作未知部署环境。

## 范围

校准独立资源/依赖发布生命周期、规范化与互斥、管理声明权限、并发协议、来源解释/预览协议；引用已有小图证据并设计真实链路验证，不建立平行实现任务。本卡为设计细化，产品代码由 071～073 实现。

## 验收对照

- M1：A 已登记，资源发布字段、跨键乱序、原快照重试、一次切换、动态发布调用方及 manifest 正式协议已细化；部署迁移按 §10.1 的保全与执行门禁实施。
- M2：`mvn test -pl access-service -Dtest=AutoGrantEngineContractPgIT`（2026-09-21）6 tests，0 failures / 0 errors / 0 skipped；验证真实引擎消费候选事实、互斥审计通知及条件身份，§6.3.1 已列共享来源撤销与中间传播的精确输入输出；未替代 072 的推导与共享来源撤销验收。
- M4：设计 §8 明确复用租户 RESOURCE_ENTITY 共同锁及入口矩阵，已点名现役混合类型批删反序由 071 先修；实现侧逐调用点核对和确定性交错验证为 071/072 硬验收。
- M5：单次只读一致视图、只读计划准备与 INLINE 临时身份、正式 explain/preview 嵌套形状和截断规则已写入设计及契约。
- M3：旧依赖保全、禁止自动启用、有效历史 AUTO_DEP 中止及目标库执行前核查已明确，071 验收必须覆盖真实旧 schema 夹具；设计校准验收完成；迁移程序由 071 实施验证。

只读迁移盘点 SQL 已在 AutoGrantEngineContractPgIT 专用数据库执行成功（2026-09-21），仅验证语法与候选数据可读，不作为实际部署数据结论。

本地双轨已核实并修正文档校准项：现役混合类型批删反序点名与承接、新接口嵌套形状/截断、孤立 actual 解释节点。生产锁序修复归 071，不将文档改动当已修复。

## 非目标 / 遗留

保持 scope_all/父继承种子边界，不自动解除 T-PERM-036/054 暂缓。无逐路径 support 及强制统一大清单的重新选型任务；若实施证明必须改变已采纳方向，另行明确决定并登记，不能静默回退。

## 完成记录

2026-09-21 完成实施前协议与算法校准。M1～M5 及迁移保全/执行门禁已回写设计、目标契约和 071～073 验收；未执行部署迁移，未将编译/物化/界面能力写为已交付。

- `mvn test -T 1C`：1797 tests，0 failures / 0 errors / 0 skipped，包含 E2E；完整日志 `.tmp-perm078-full.log`。
- 全量后仅把引擎校准测试的异步审计 spy 改为同步审计边界 mock，并类推修正 BatchAuthCheckPgIT；`mvn test -pl access-service -Dtest=AutoGrantEngineContractPgIT,BatchAuthCheckPgIT`：17 tests，0 failures / 0 errors / 0 skipped。引擎、条件评估与 SQL 仍为真实实现，验证审计通知而非异步持久化。
- 本地双轨评审全部处置并复核：解释全集计数、混合类型批删反序承接、嵌套线格式/截断及异步测试竞态；无必须用户决策项。文档检查与 `git diff --check` 通过。
