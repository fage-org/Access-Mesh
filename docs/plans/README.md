# 执行计划索引

本目录用于存放仍在推进、仍有跟踪价值，但不应作为权威设计契约的执行计划、核对清单和阶段路线图。

## 当前计划

| 文档 | 类型 | 状态 | 说明 |
|------|------|------|------|
| ~~design-review-2026-06-17~~ | （已归档）| — | 设计评审已完成并归档至 [../archive/2026-06-17/](../archive/2026-06-17/)。评审结论沉淀至 `docs/design/`，工作单 A/B/C 派生为下列 P0 落地计划，工作单 D/E/F 暂缓。详见归档批次 README |
| [archive/2026-06/](archive/2026-06/) | 历史归档 | OBSOLETED | v3.0~v3.3 设计演进：双轨 AND + sys_menu.operations 元数据化 + manifest 中心化等历史范式。v3.4（is_entry/sensitivity_level 过度设计）已被 v3.5 取代，通过 git history 追溯。**仅作历史追溯，不再作为开发依据** |
| [improvement-plan.md](improvement-plan.md) | 项目级路线图 | 进行中 | 前端、API 核对、核心能力补齐和联调的分阶段完善计划 |
| ~~org-user-page-impl-plan~~ | （已归档）| — | 「组织与用户」融合页 P0/P1/P2 三阶段全 100%，联动验收（T-ADMIN-001~019）已完成，2026-06-21 归档至 [../archive/2026-06-21/](../archive/2026-06-21/)。权威契约以 `design/org-user-permission-contract.md` v1.2 + `design/services/admin-service-api-contract.md` v1.0 为准 |
| ~~user-role-proxy-fix-plan~~ | （已归档）| — | 用户角色代理修复第一轮（M1-M13+S1-S3）+ 第二轮（P1-1/P1-2/P2-1/P2-2）均已完成验收 + 设计回写，2026-06-20 归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。任务 `T-ADMIN-001~019` + `T-PERM-016` 见 [../tasks/README.md](../tasks/README.md) 已完成区。EXT-7/EXT-8 DEFERRED 无主（审计 S-024）|
| [perm-cache-invalidation-plan.md](perm-cache-invalidation-plan.md) | P0 落地计划（工作单 A）| 进行中 | 权限缓存失效改造：Gateway 快照模式 + ThreadLocal 影响范围收集 + 删除 permission_version + Redis pub/sub 广播。关联审计 S-001/S-006。任务 T-PERM-001·002·003·004·005·006·017·018 done / 007·008 proposed |
| [scope-mode-migration-plan.md](scope-mode-migration-plan.md) | P0 落地计划（工作单 B）| 进行中 | scopeMode 三值枚举协议全量推广（运行时 + 管理端 + 排查页），替换 scopeAll boolean。关联审计 S-005。任务 T-PERM-009·010 done / 011~015 proposed |
| [gateway-fail-mode-plan.md](gateway-fail-mode-plan.md) | P0 落地计划（工作单 C）| 待启动 | Gateway 失联兜底：三模 fail-mode（closed/open/stale-allow）+ 监控指标。关联审计 S-006；依赖工作单 A 快照模式。任务 T-GW-001~006 全 proposed |
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
