# 执行计划索引

本目录用于存放仍在推进、仍有跟踪价值，但不应作为权威设计契约的执行计划、核对清单和阶段路线图。

## 当前计划

| 文档 | 类型 | 状态 | 说明 |
|------|------|------|------|
| ~~design-review-2026-06-17~~ | （已归档）| — | 设计评审已完成并归档至 [../archive/2026-06-17/](../archive/2026-06-17/)。评审结论沉淀至 `docs/design/`，工作单 A/B/C 已完成并归档，工作单 D/E/F 暂缓项已拆分至 [design-review-def-followup-plan.md](design-review-def-followup-plan.md)。详见归档批次 README |
| [archive/2026-06/](archive/2026-06/) | 历史归档 | OBSOLETED | v3.0~v3.3 设计演进：双轨 AND + sys_menu.operations 元数据化 + manifest 中心化等历史范式。v3.4（is_entry/sensitivity_level 过度设计）已被 v3.5 取代，通过 git history 追溯。**仅作历史追溯，不再作为开发依据** |
| ~~improvement-plan~~ | （已归档）| — | 项目级路线图：诊断与四阶段拆分使命完成（Phase 1 已归档、Phase 2-4 由独立 plan 承载），§2 完成度为 2026-06 时点快照，2026-08-27 归档至 [../archive/2026-08-27/](../archive/2026-08-27/)；痛点 #3/#4 暂缓决策溯源仍被 T-PERM-035/036 引用 |
| ~~access-service-merge-plan~~ | （已归档）| — | `admin-service` + `permission-center` 归并为模块化单体 `access-service`；T-ACCESS-001~012 全部 done（2026-08-22），计划归档至 [../archive/2026-08-22/](../archive/2026-08-22/)。权威约束见 `design/access-service-architecture.md` |
| ~~access-post-merge-plan~~ | （已归档）| — | 归并后续强化 T-ACCESS-013~015 全部 done（2026-08-22），CI 准入前置由 T-ACCESS-017 最小 CI 关闭，计划随 T-ACCESS-026 归档至 [../archive/2026-08-27/](../archive/2026-08-27/) |
| ~~product-vertical-slice-plan~~ | （已归档）| — | 产品垂直切片与试点加固（2026-08-22 立项 → 2026-08-27 收口）：风险基线全量核实立项、2026-08-23 评审修订：模型收敛（引擎显式资源 API → 统一主体 ID B-lite → 资源类型五组合并 → USER/ROLE 投影）→ 空库 bootstrap（固定租户 1 + 幂等 runner）→ 前端真实登录+导航收敛 → BASIC_ROLE E2E 垂直切片 + README 回写（里程碑 A）→ 试点加固 + example 接入（Gateway 主线）+ 证据收口（里程碑 B，硬门禁：全部 B 任务 depends_on T-ACCESS-021）。最小 CI 前置并入 T-ACCESS-017。18 项任务（T-ACCESS-016~026、T-PERM-042/043、T-ORG-001、T-ADMIN-022~024、T-GW-007、T-FE-041、T-API-001；T-FE-042 cancelled 并入 T-FE-041） |
| ~~frontend-phase1-plan~~ | （已归档）| — | 前端 Phase 1（13 页 mock 驱动 + API 核对）2026-07-12 完成，见 [../archive/2026-07-12/](../archive/2026-07-12/)；🔧❌ 清单登记为 Phase 2 后端任务 T-PERM-022~034 |
| [frontend-phase2-plan.md](frontend-phase2-plan.md) | 前端 Phase 2 执行编排 | proposed | 自动授权 + API 改造 + 动态数据权限。任务 T-PERM-022~041（022~034 逐页后端改造 + 035/036 暂缓 + 037 共性收尾 + 040/041 授权链收口）。⚠️ 自动授权/动态数据权限受 design-review §11 暂缓门禁 |
| ~~frontend-phase3-plan~~ | （已归档）| — | 前端 Phase 3 前后端联调（2026-06-29 立项 → 2026-09-04 收口）：T-FE-015~022 + T-FE-037 九任务全 done（menus 接线/固定图扩容/逐页 mock 退役/keyword CAST 系统修复/组织二期入口），2026-09-07 归档门禁（统一全页导航/F5/直达 URL 冒烟 21/21）执行通过后归档至 [../archive/2026-09-07/](../archive/2026-09-07/) |
| [frontend-phase4-plan.md](frontend-phase4-plan.md) | 前端 Phase 4 执行编排 | proposed | 扩展验证 + 代码清理 + 测试 + 文档。任务 T-FE-023/T-ADMIN-020/T-PERM-038/039 |
| [permission-grant-record-level-editing-proposal.md](permission-grant-record-level-editing-proposal.md) | 权限授予交互提案 | **confirmed（2026-08-08 确认）** | 条件、再授予与子权限按具体父授权记录编辑（记录级聚焦编辑）。D1~D6 决策结论见提案 §11.1；权威设计已回写（`permission-grant.md` v3.1 + `api-contract.md` §6.5.2），实施完成后归档 |
| ~~permission-grant-ux-refactor-plan~~ | （已归档）| - | 权限授予页授权弹窗与右栏变更重构。因交互不满意，v1+v2 两套页面 2026-07-26 整体删除重做；设计文档归档至 [../archive/2026-07-26/](../archive/2026-07-26/)，任务 T-FE-024~026 保持 done（产出废弃）、T-FE-027/028 cancelled（`ReConditionEditor` 保留，`ReConditionPicker` 已删）。plan 归档至 [archive/2026-07/](archive/2026-07/) |
| ~~permission-grant-v2-plan~~ | （已归档）| - | 权限授予页 V2（方案A多条件分支模型）。因交互不满意，v1+v2 两套页面 2026-07-26 整体删除重做；三份设计文档归档至 [../archive/2026-07-26/](../archive/2026-07-26/)，任务 T-FE-029~034 保持 done（产出废弃）、T-FE-035 cancelled。plan 归档至 [archive/2026-07/](archive/2026-07/) |
| [design-review-def-followup-plan.md](design-review-def-followup-plan.md) | 设计评审 D/E/F 后续任务拆分 | proposed | D/E/F 暂缓项拆分为 `T-PERM-019~021`；冲突项已标记，执行前必须确认。T-PERM-020 已收口（2026-08-28）；T-PERM-019 已 2026-09-05 重基线（D1 完成/D3 废注解/D2 收敛） |
| [design-audit-followup-plan.md](design-audit-followup-plan.md) | codex 项目级设计体检处置批次 | proposed | 2026-09-05 体检新发现 P1×1+P2×2 拆为 T-PERM-052/053/054、T-API-002、T-ACCESS-029；预置五题定案回写既有卡与 §14.2；T-PERM-054 暂缓（2026-09-09 方向已定、方案未定，见 registry） |
| ~~[permission-query-unification-plan.md](permission-query-unification-plan.md)~~ | 权限查询统一引擎重构编排（已归档） | archived | 2026-09-10 四任务全 done 归档（T-PERM-057/T-API-003/T-PERM-058/T-PERM-059）；终态设计 `design/permission-center/query-engine-unification.md`（已转 superseded，落地并入 implementation §3） |
| ~~product-positioning-landing-plan~~ | （已归档）| — | 产品定位落地：三档叙事整改 + 名实对齐收尾。T-ACCESS-027/028 全 done（2026-08-28）；2026-08-28 定位定案：开源通用 IAM（三档口径入口 `design/README`）。2026-09-07 归档至 [../archive/2026-09-07/](../archive/2026-09-07/) |
| ~~org-user-page-impl-plan~~ | （已归档）| — | 「组织与用户」融合页 P0/P1/P2 三阶段全 100%，联动验收（T-ADMIN-001~019）已完成，2026-06-21 归档至 [../archive/2026-06-21/](../archive/2026-06-21/)。权威契约以 `design/org-user-permission-contract.md` v1.2 + `design/services/admin-service-api-contract.md` v1.0 为准 |
| ~~user-role-proxy-fix-plan~~ | （已归档）| — | 用户角色代理修复（M1-M13+S1-S3 主线 + P1/P2 补充批次）均已完成验收 + 设计回写，2026-06-20 归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。任务 `T-ADMIN-001~019` + `T-PERM-016` 见 [../tasks/README.md](../tasks/README.md) 已完成区。EXT-7/EXT-8 DEFERRED 无主（审计 S-024）|
| ~~perm-cache-invalidation-plan~~ | （已归档）| — | 工作单 A 权限缓存失效改造已完成并归档至 [../archive/2026-06-28/](../archive/2026-06-28/)。权威契约以 `docs/design/permission-center-v3.5-design.md` §7.2 + `docs/design/services/gateway.md` 为准 |
| ~~scope-mode-migration-plan~~ | （已归档）| — | 工作单 B scopeMode 协议迁移已完成并归档至 [../archive/2026-06-28/](../archive/2026-06-28/)。权威契约以 `docs/design/permission-center-v3.5-design.md` §3 + `docs/design/permission-center/api-contract.md` 为准 |
| ~~gateway-fail-mode-plan~~ | （已归档）| — | 工作单 C Gateway 失联兜底已完成并归档至 [../archive/2026-06-28/](../archive/2026-06-28/)。权威契约以 `docs/design/services/gateway.md` §失联兜底模式 / §快照失效标记与订阅恢复为准 |
| ~~api-gap-analysis~~ | （已归档）| — | API 核对清单的 16 个 🔧 接口经代码核实已由 admin-service 实现，与 org-user-page P1=100% 一致；gap 跟踪目的达成，2026-06-21 归档至 [../archive/2026-06-21/](../archive/2026-06-21/)。契约权威以 `docs/design/services/admin-service-api-contract.md` v1.0 为准 |

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

归档后必须在归档批次目录新增或更新 `README.md`，说明归档原因、原始文档定位和当前权威设计入口。

## 归档/迁移自检

执行计划归档、迁移或拆分时，必须完成以下检查：

- [ ] 源文件移动到新位置后，已修复文件内部相对链接。
- [ ] 全仓 grep 旧路径、旧文件名和旧入口描述，无非预期残留。
- [ ] `docs/README.md` 的目录树、推荐阅读顺序、执行计划索引和归档批次表已同步刷新。
- [ ] `docs/design/README.md` 的权威来源、目录说明和归档批次表已同步刷新。
- [ ] 稳定结论已沉淀到 `docs/design/`；归档文档不再作为实现依据。
- [ ] 旧目录若已无文件，已删除空目录或明确保留原因。
