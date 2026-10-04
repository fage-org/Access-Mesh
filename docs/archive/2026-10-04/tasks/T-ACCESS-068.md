---
doc_type: task
id: T-ACCESS-068
title: 服务凭证覆盖运行时查询端点阶段二规划
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: cross-service
design_refs:
  - docs/design/service-authentication.md §3.5（分期与退役判据）
depends_on: []
blocks: []
acceptance:
  - "产出阶段二规划定案：auth/check、batch-check、query-resources、query-scopes 四端点（现要求 X-Internal-Secret 与服务/租户头）逐端点凭证化路径、服务可查询的主体/资源范围、租户派生与调用能力定义"
  - "两套身份并存的过渡窗口、全局共享密钥失陷风险的收敛判据与退役时间线写入 §3.5 分期表"
  - "T-ACCESS-053 的阶段边界维持（本卡不推翻其维持现状拍板，只规划后续阶段）；SDK/网关接线影响面盘点随规划产出"
  - "实施不在本卡（阶段二立项目另拆新号）；规划按 decision-question-protocol 举例上报用户确认后，service-authentication §3.5 更新为定稿口径"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-068 服务凭证覆盖运行时查询端点阶段二规划

## 背景

承接 [Q-040](../../../pending-problems.md#q-040)：per-service 凭证（T-PERM-070）覆盖同步/manifest 通道，但 auth/check、batch-check、query-resources、query-scopes 仍要求 X-Internal-Secret 与服务/租户头——凭证不能直接替代该查询身份。接入方维护两套配置与失效语义，查询面仍承担全局共享密钥失陷风险。新准入端点接线（T-ACCESS-059）不代表本项自动收敛。

## 范围

定案/规划卡：阶段二范围定义与分期修订；不含实施。

## 当前口径

阶段一（T-PERM-070）已交付同步/manifest 双通道；阶段二按端点逐个规划，先定义服务可查询的主体/资源范围与租户派生。

check、batch-check、query-resources、query-scopes 向有效服务凭证开放本租户任意主体/资源的授权查询，禁止跨租户；不新增服务查询范围配置。租户/服务来自凭证绑定，查询中的用户字段是被查询主体而非操作者，不能借此调用管理写入口（2026-10-04 确认）。

当前无部署，四查询端点与 SDK/示例同批硬切，不留永久兼容开关；阶段一既有边界在实施前保持。其余用户/角色/绑定同步仍需凭证化，全部外部业务调用不再依赖共享密钥后才关闭旧纯服务通道并回收外部分发密钥；Gateway 内部互信保留。时间线按验收节点，不虚设日期（2026-10-04 确认）。实施分为 [T-ACCESS-079](T-ACCESS-079.md) 与 [T-ACCESS-080](T-ACCESS-080.md)。

## 非目标 / 遗留

- 阶段二实施（另立新号）。

## 验收对照

- [x] 四端点逐一确定凭证身份、租户派生与查询能力，见服务认证 §3.5。
- [x] 当前无部署下四查询同批硬切；旧阶段边界与 Gateway 内部互信保留。
- [x] 剩余同步迁移、外部旧调用清零和密钥回收先后条件已定义。
- [x] M2M 单源、SDK 镜像、Gateway、example-service 影响面纳入实施卡。
- [x] T-ACCESS-079/080 承接实施，本卡不声称旧密钥风险已退出。

## 完成记录

2026-10-04：代码轨只读核对凭证精确注入清单、M2M 单源与 BusinessPermChecker 的四类调用；文档轨核对范围/发布/退出条件及阶段一有效边界。纯规划任务无运行时改动，行为验证归后续实施卡。
