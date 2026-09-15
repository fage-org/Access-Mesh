# 执行计划索引

本目录用于存放仍在推进、仍有跟踪价值，但不应作为权威设计契约的执行计划、核对清单和阶段路线图。

## 当前计划

| 文档 | 类型 | 状态 | 说明 |
|------|------|------|------|
| ~~capability-mapper-convergence-plan~~ | （已归档）| — | Q-009 跨能力 mapper 直读收敛：T-ACCESS-043~046 四批全 done（30 边→0、白名单退役零容忍），2026-09-15 归档至 [../archive/2026-09-15/](../archive/2026-09-15/)（四卡随迁 tasks/）；定案见 decision-registry 同日两行 |
| ~~design-review-2026-06-17~~ | （已归档）| — | 设计评审已完成并归档至 [../archive/2026-06-17/](../archive/2026-06-17/)。评审结论沉淀至 `docs/design/`，工作单 A/B/C 已完成并归档，工作单 D/E/F 暂缓项已拆分至 design-review-def-followup-plan（该计划亦已于 2026-09-12 收口归档）。详见归档批次 README |
| [../archive/2026-06-18/](../archive/2026-06-18/) | 历史归档 | OBSOLETED | v3.0~v3.3 设计演进：双轨 AND + sys_menu.operations 元数据化 + manifest 中心化等历史范式。v3.4（is_entry/sensitivity_level 过度设计）已被 v3.5 取代，通过 git history 追溯。原侧挂 `plans/archive/2026-06/`，2026-09-12 并入统一归档目录。**仅作历史追溯，不再作为开发依据** |
| ~~improvement-plan~~ | （已归档）| — | 项目级路线图：诊断与四阶段拆分使命完成（Phase 1 已归档、Phase 2-4 由独立 plan 承载），§2 完成度为 2026-06 时点快照，2026-08-27 归档至 [../archive/2026-08-27/](../archive/2026-08-27/)；痛点 #3/#4 暂缓决策溯源仍被 T-PERM-035/036 引用 |
| ~~access-service-merge-plan~~ | （已归档）| — | `admin-service` + `permission-center` 归并为模块化单体 `access-service`；T-ACCESS-001~012 全部 done（2026-08-22），计划归档至 [../archive/2026-08-22/](../archive/2026-08-22/)。权威约束见 `design/access-service-architecture.md` |
| ~~access-post-merge-plan~~ | （已归档）| — | 归并后续强化 T-ACCESS-013~015 全部 done（2026-08-22），CI 准入前置由 T-ACCESS-017 最小 CI 关闭，计划随 T-ACCESS-026 归档至 [../archive/2026-08-27/](../archive/2026-08-27/) |
| ~~product-vertical-slice-plan~~ | （已归档）| — | 产品垂直切片与试点加固（2026-08-22 立项 → 2026-08-27 收口）：风险基线全量核实立项、2026-08-23 评审修订：模型收敛（引擎显式资源 API → 统一主体 ID B-lite → 资源类型五组合并 → USER/ROLE 投影）→ 空库 bootstrap（固定租户 1 + 幂等 runner）→ 前端真实登录+导航收敛 → BASIC_ROLE E2E 垂直切片 + README 回写（里程碑 A）→ 试点加固 + example 接入（Gateway 主线）+ 证据收口（里程碑 B，硬门禁：全部 B 任务 depends_on T-ACCESS-021）。最小 CI 前置并入 T-ACCESS-017。18 项任务（T-ACCESS-016~026、T-PERM-042/043、T-ORG-001、T-ADMIN-022~024、T-GW-007、T-FE-041、T-API-001；T-FE-042 cancelled 并入 T-FE-041） |
| ~~frontend-phase1-plan~~ | （已归档）| — | 前端 Phase 1（13 页 mock 驱动 + API 核对）2026-07-12 完成，见 [../archive/2026-07-12/](../archive/2026-07-12/)；🔧❌ 清单登记为 Phase 2 后端任务 T-PERM-022~034 |
| ~~frontend-phase2-plan~~ | （已归档）| — | 前端 Phase 2 核心功能补齐 + 后端接口改造（2026-06-29 立项 → 2026-09-14 归档）：T-PERM-022~034/037/040/041 逐页与共性后端改造全 done、T-FE-036/038~040 + T-ADMIN-021 配套全 done；暂缓项 T-PERM-035/036 脱出挂任务看板（design-review §11 暂缓门禁不变），21 张任务卡随迁归档目录。见 [../archive/2026-09-14/](../archive/2026-09-14/) |
| ~~frontend-phase3-plan~~ | （已归档）| — | 前端 Phase 3 前后端联调（2026-06-29 立项 → 2026-09-04 收口）：T-FE-015~022 + T-FE-037 九任务全 done（menus 接线/固定图扩容/逐页 mock 退役/keyword CAST 系统修复/组织二期入口），2026-09-07 归档门禁（统一全页导航/F5/直达 URL 冒烟 21/21）执行通过后归档至 [../archive/2026-09-07/](../archive/2026-09-07/) |
| ~~frontend-phase4-plan~~ | （已归档） | — | 前端 Phase 4（扩展验证 + 代码清理 + 测试 + 文档）五任务全 done（2026-09-12 批次收口：全量回归含 E2E 全绿 + 双轨评审处置完成），同日随批次归档至 [../archive/2026-09-12/](../archive/2026-09-12/)；扩展指南产出 `design/extension-guide.md`（评审稿待确认） |
| ~~permission-grant-record-level-editing-proposal~~ | （已归档） | — | 权限授予记录级聚焦编辑决策记录。2026-08-08 确认（D1~D6 见提案 §11.1），权威设计已回写（`permission-grant.md` v3.1 + `api-contract.md` §6.5.2），实施任务 T-FE-040 done；2026-09-12 归档至 [../archive/2026-09-12/](../archive/2026-09-12/) |
| ~~permission-grant-ux-refactor-plan~~ | （已归档）| - | 权限授予页授权弹窗与右栏变更重构。因交互不满意，v1+v2 两套页面 2026-07-26 整体删除重做；设计文档归档至 [../archive/2026-07-26/](../archive/2026-07-26/)，任务 T-FE-024~026 保持 done（产出废弃）、T-FE-027/028 cancelled（`ReConditionEditor` 保留，`ReConditionPicker` 已删）。plan 与任务卡 2026-09-12 归档至 [../archive/2026-07-26/](../archive/2026-07-26/)（卡住 `tasks/` 子目录） |
| ~~permission-grant-v2-plan~~ | （已归档）| - | 权限授予页 V2（方案A多条件分支模型）。因交互不满意，v1+v2 两套页面 2026-07-26 整体删除重做；三份设计文档归档至 [../archive/2026-07-26/](../archive/2026-07-26/)，任务 T-FE-029~034 保持 done（产出废弃）、T-FE-035 cancelled。plan 与任务卡 2026-09-12 归档至 [../archive/2026-07-26/](../archive/2026-07-26/)（卡住 `tasks/` 子目录） |
| ~~design-review-def-followup-plan.md~~ | （已归档） | — | 设计评审 D/E/F 后续任务拆分。三任务全 done（T-PERM-019 2026-09-07 / T-PERM-020 2026-08-28 / T-PERM-021 2026-09-12——F1.b 盘点另立 T-PERM-065），计划随末卡收口归档至 [../archive/2026-09-12/](../archive/2026-09-12/design-review-def-followup-plan.md)（三卡随迁 tasks/） |
| ~~design-audit-followup-plan~~ | （已归档）| — | codex 项目级设计体检处置批次（2026-09-05 立项 → 2026-09-14 归档）：T-PERM-052/053、T-API-002、T-ACCESS-029 四执行任务全 done；暂缓卡 T-PERM-054 脱出挂任务看板（2026-09-09 方向已定、等方案定案），四卡随迁。见 [../archive/2026-09-14/](../archive/2026-09-14/) |
| ~~access-capability-fusion-plan~~ | （已归档） | — | access-service 能力包融合（2026-09-13 立项 → 2026-09-14 收口归档）：T-ACCESS-032~041 十任务全 done——032 设计定稿（§8 归属清单十项裁决）、033 机械迁移（17 顶层包落地）、034~039 平行设施归零与概念收口（操作码/字段消减/system_config 单入口/错误码/缓存目录/契约总册）、040 契约深合一、041 规则与技能口径收尾；验收五条达成、三通道外评处置后用户确认定稿，归档至 [../archive/2026-09-14/](../archive/2026-09-14/)（十卡随迁 `tasks/`）。目标设计 [../design/access-service-capability-structure.md](../design/access-service-capability-structure.md) 维持 **adopted** 作现行结构契约，定案见 decision-registry 2026-09-13/14 行 |
| ~~[permission-query-unification-plan.md](../archive/2026-09-12/permission-query-unification-plan.md)~~ | 权限查询统一引擎重构编排（已归档） | archived | 2026-09-10 四任务全 done 归档（T-PERM-057/T-API-003/T-PERM-058/T-PERM-059），2026-09-12 物理归档至 [../archive/2026-09-12/](../archive/2026-09-12/)（任务卡随迁 `tasks/`）；终态设计已转 superseded（落地并入 `engine/implementation.md` §3，原文件留 permission-center 目录待归档） |
| ~~product-positioning-landing-plan~~ | （已归档）| — | 产品定位落地：三档叙事整改 + 名实对齐收尾。T-ACCESS-027/028 全 done（2026-08-28）；2026-08-28 定位定案：开源通用 IAM（三档口径入口 `design/README`）。2026-09-07 归档至 [../archive/2026-09-07/](../archive/2026-09-07/) |
| ~~org-user-page-impl-plan~~ | （已归档）| — | 「组织与用户」融合页 P0/P1/P2 三阶段全 100%，联动验收（T-ADMIN-001~019）已完成，2026-06-21 归档至 [../archive/2026-06-21/](../archive/2026-06-21/)。权威契约以 `design/org-user-permission-contract.md` v1.2 + `design/access-service-api-contract.md`（原 admin 册已并入总册）为准 |
| ~~user-role-proxy-fix-plan~~ | （已归档）| — | 用户角色代理修复（M1-M13+S1-S3 主线 + P1/P2 补充批次）均已完成验收 + 设计回写，2026-06-20 归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。任务 `T-ADMIN-001~019` + `T-PERM-016` 见 [../tasks/README.md](../tasks/README.md) 已完成区。原 EXT-7/EXT-8 DEFERRED 无主（审计 S-024）已处置：EXT-7 立项 T-PERM-061、EXT-8 失效（宿主随内部同步子系统删除），2026-09-11 见任务看板 |
| ~~perm-cache-invalidation-plan~~ | （已归档）| — | 工作单 A 权限缓存失效改造已完成并归档至 [../archive/2026-06-28/](../archive/2026-06-28/)。权威契约以 `docs/design/permission-center-v3.5-design.md` §7.2 + `docs/design/services/gateway.md` 为准 |
| ~~scope-mode-migration-plan~~ | （已归档）| — | 工作单 B scopeMode 协议迁移已完成并归档至 [../archive/2026-06-28/](../archive/2026-06-28/)。权威契约以 `docs/design/permission-center-v3.5-design.md` §3 + `docs/design/access-service-api-contract.md`（契约总册）为准 |
| ~~gateway-fail-mode-plan~~ | （已归档）| — | 工作单 C Gateway 失联兜底已完成并归档至 [../archive/2026-06-28/](../archive/2026-06-28/)。权威契约以 `docs/design/services/gateway.md` §失联兜底模式 / §快照失效标记与订阅恢复为准 |
| ~~api-gap-analysis~~ | （已归档）| — | API 核对清单的 16 个 🔧 接口经代码核实已由 admin-service 实现，与 org-user-page P1=100% 一致；gap 跟踪目的达成，2026-06-21 归档至 [../archive/2026-06-21/](../archive/2026-06-21/)。契约权威以 `docs/design/access-service-api-contract.md`（原 admin 册 v1.0 已并入总册）为准 |

## 管理规则

| 类型 | 目录 | 是否作为实现依据 |
|------|------|------------------|
| 当前有效设计、规范、API 契约 | `docs/design/` | 是 |
| 仍在推进的执行计划、核对清单、阶段路线图 | `docs/plans/` | 否；只能作为任务跟踪材料 |
| 已完成或已合并的过程文档 | `docs/archive/YYYY-MM-DD/` | 否；仅用于历史追溯 |

执行计划可以引用 `docs/design/` 中的权威设计，但不得在计划内私自形成与设计文档不同的契约。若执行过程中发现契约需要变化，先更新对应设计文档，再继续实现或回写计划。

## 新增计划模板

新增执行计划时建议包含：

```markdown
# 标题

> 状态：进行中 / 已完成待归档 / 暂停
> 关联设计：`docs/design/...`

## 目标

## 非目标

## 阶段拆分

## 验收标准

## 当前进度

## 归档条件
```

## 归档条件

满足任一条件时，应把计划移动到 `docs/archive/YYYY-MM-DD/`，并更新 `docs/README.md` 与 `docs/design/README.md` 中的引用：

- 阶段性任务已完成，且稳定结论已沉淀到 `docs/design/`。
- 计划被新计划替代。
- 计划只剩历史追溯价值。

计划归档时其 `tasks:[]` 全部任务卡随迁至同目录 `tasks/` 子目录；终态任务卡（done/cancelled 且无活跃计划归属）随归档批次单卡归档。规则、目录布局与自检见 `.claude/skills/design-plan-task-lifecycle/SKILL.md` §6.5（2026-09-12 定案）。

归档后必须在归档批次目录新增或更新 `README.md`，说明归档原因、原始文档定位和当前权威设计入口。

## 归档/迁移自检

执行计划归档、迁移或拆分时，必须完成以下检查：

- [ ] 源文件移动到新位置后，已修复文件内部相对链接。
- [ ] 全仓 grep 旧路径、旧文件名和旧入口描述，无非预期残留。
- [ ] `docs/README.md` 的目录树、推荐阅读顺序、执行计划索引和归档批次表已同步刷新。
- [ ] `docs/design/README.md` 的权威来源、目录说明和归档批次表已同步刷新。
- [ ] 稳定结论已沉淀到 `docs/design/`；归档文档不再作为实现依据。
- [ ] 旧目录若已无文件，已删除空目录或明确保留原因。
