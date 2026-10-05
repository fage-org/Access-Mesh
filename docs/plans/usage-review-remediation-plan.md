---
doc_type: plan
title: 使用者视角评审问题修复计划
status: active
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/project-rules.md
  - docs/design/engine/implementation.md
  - docs/design/schema/access-service.sql
  - docs/design/service-authentication.md
  - docs/design/extension-guide.md
  - docs/ops/deployment.md
  - docs/design/access-service-rebuild-runbook.md
tasks:
  - T-ACCESS-082
  - T-ACCESS-083
  - T-ACCESS-084
  - T-ACCESS-085
  - T-ACCESS-086
  - T-ACCESS-087
  - T-ACCESS-088
  - T-ACCESS-089
  - T-ACCESS-090
  - T-PERM-106
  - T-PERM-107
  - T-PERM-108
  - T-FE-064
  - T-FE-065
  - T-FE-066
  - T-API-007
  - T-API-008
  - T-API-009
  - T-API-010
  - T-API-011
  - T-API-012
  - T-API-013
  - T-GW-011
  - T-GW-012
acceptance: "24 项任务全部 done（或经用户拍板 cancelled）；评审 86 条问题对应处置全部落地或显式登记暂不；破坏性契约变更（分页统一/orgType 统一/OAuth2 公开客户端）的迁移说明与定案取代登记完成；固定图加行类任务存量库重建口径写入 deployment.md"
last_updated: 2026-10-05
---

# 使用者视角评审问题修复计划

## 目标

把 2026-10-04~05 使用者视角全量评审（86 条核实成立问题，三源复核+双通道计划评审+用户逐条拍板 D1~D18）转为可执行任务并收口。任务分组按「同修复链路/同文件/同机制」合并；全部方向性分叉已于 2026-10-05 拍板落定，任务按当前口径直接执行。

## 非目标

- 排障能力新形态重做（登记待重启，Q-060）、审计导出与保留策略（Q-061）、未成册五族补册（Q-062，等接口稳定）、国际化、租户开通/运营（Q-063）——均不在本计划。
- 能力守卫与最后管理员保护（Q-059）延后。
- codegen 统一 DTO 生成、审批（4-eye）机制：路线图登记，不实施。

## 准入条件

- [x] 86 条问题三源复核终局（61 完全成立/22 基本成立/3 核心改写）。
- [x] 计划经 claude / codex sol 双通道评审并采纳全部 14 项修订。
- [x] D1~D18 全部用户拍板（2026-10-05）。
- [x] 评审核实终局（86 条）与覆盖矩阵落仓证据件：[evidence/usage-review-20261005/](../tasks/evidence/usage-review-20261005/)（评审终局 `usage-review-final-86.md` + 拍板落定版 `fix-plan-v3-decided.md`——86 条→24 任务单点归属以证据件矩阵为准）。

## 任务清单

按波次编排（波次=执行编排，优先级=任务标签，两者独立；详细范围见各任务卡）：

**Wave 1（安全+解锁后续）**

| ID | 标题 | 状态 |
|---|---|---|
| [T-ACCESS-082](../tasks/T-ACCESS-082.md) | 密码重置会话吊销与凭据代际闭环 | ⚙️ |
| [T-ACCESS-086](../tasks/T-ACCESS-086.md) | 备份恢复与运维基线 | ⚙️ |
| [T-PERM-107](../tasks/T-PERM-107.md) | 权限拒绝解释字段修正 | ⚙️ |

**Wave 2（P2 主体）**

| ID | 标题 | 状态 |
|---|---|---|
| [T-PERM-106](../tasks/T-PERM-106.md) | 管理员种子行锁死与转授前提声明 | ⚙️ |
| [T-ACCESS-083](../tasks/T-ACCESS-083.md) | 认证链防护收敛 | ⚙️ |
| [T-ACCESS-085](../tasks/T-ACCESS-085.md) | 审计门禁与留痕补齐 | ⚙️ |
| [T-FE-065](../tasks/T-FE-065.md) | 管理界面补齐（凭证/登录日志/同步状态） | ⚙️ |
| [T-API-009](../tasks/T-API-009.md) | DTO 契约同步对账机制 | ⚙️ |
| [T-API-010](../tasks/T-API-010.md) | 分页口径统一 200+hasNext | ⚙️ |
| [T-FE-066](../tasks/T-FE-066.md) | 前端工程门禁进 CI | ⚙️ |
| [T-PERM-108](../tasks/T-PERM-108.md) | 同步对接方体验补齐 | ⚙️ |
| [T-ACCESS-090](../tasks/T-ACCESS-090.md) | 租户条件接线与容量基线 | ⚙️ |

**Wave 3（重任务）**

| ID | 标题 | 状态 |
|---|---|---|
| [T-ACCESS-084](../tasks/T-ACCESS-084.md) | OAuth2 公开客户端实现 | ⚙️ |
| [T-API-007](../tasks/T-API-007.md) | SDK 集成安全收编与文档前提 | ⚙️ |
| [T-ACCESS-087](../tasks/T-ACCESS-087.md) | 管理面一致性杂项收敛 | ⚙️ |
| [T-API-011](../tasks/T-API-011.md) | orgType 线格式统一 Integer | ⚙️ |
| [T-ACCESS-088](../tasks/T-ACCESS-088.md) | 安全语义失败形态判别表 | ⚙️ |

**打磨池（P3）**

| ID | 标题 | 状态 |
|---|---|---|
| [T-FE-064](../tasks/T-FE-064.md) | 管理台登录与会话打磨 | ⚙️ |
| [T-API-008](../tasks/T-API-008.md) | SDK 与公共模块卫生 | ⚙️ |
| [T-API-012](../tasks/T-API-012.md) | 扩展面文档与 JSON 校验收紧 | ⚙️ |
| [T-ACCESS-089](../tasks/T-ACCESS-089.md) | 维护者工程基线 | ⚙️ |
| [T-API-013](../tasks/T-API-013.md) | 契约册导航与错误码索引 | ⚙️ |
| [T-GW-011](../tasks/T-GW-011.md) | Gateway 白名单死路由与死配置清理 | ⚙️ |
| [T-GW-012](../tasks/T-GW-012.md) | sa-token 双端配置对照测试 | ⚙️ |

## 归档条件

24 项任务全部 done/cancelled；破坏性变更迁移说明与定案取代登记（分页统一取代「专属上限优先」、orgType 统一、OAuth2 公开客户端 DDL）完成；Q-059~Q-063 五项延后登记可追溯。

## 当前进度

2026-10-05 立项：86 条评审问题 → 24 任务（覆盖矩阵单点归属）；D1~D18 拍板结论已写入各任务卡「当前口径」。执行自 Wave 1 起按依赖推进（固定图加行类任务以 T-ACCESS-086 为前置）。

## 全局约束（成本放大器）

任何需新增 bootstrap 固定图行的改动（API 路由/操作码/菜单种子行——本计划涉及 T-ACCESS-085 login-log 路由、T-FE-065 页面菜单行）对存量库=重启 fail-fast（先例 T-ADMIN-029/T-ACCESS-054，处置=重建库）——T-ACCESS-086 备份规程须先行；预览期（可重建环境）不受此约束。
