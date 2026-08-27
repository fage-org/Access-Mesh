# 2026-08-27 归档批次：product-vertical-slice 计划收口 + improvement-plan 路线图归档

## 归档原因

T-ACCESS-026（验证证据登记与文档状态收口）完成，`product-vertical-slice-plan` 18 项任务全部 done（里程碑 A + B 均达成），其前置依赖的 `access-post-merge-plan` 三任务（T-ACCESS-013~015）亦早已全 done——两个计划满足各自归档条件（阶段性任务完成 + 稳定结论沉淀 `docs/design/` + CI 准入前置已由 T-ACCESS-017 最小 CI 落地关闭），按 project-rules §文档治理归档。

`improvement-plan.md`（项目级路线图）于同日归档：其诊断与四阶段拆分使命已完成（Phase 1 已归档、Phase 2-4 由 `docs/plans/frontend-phase2~4-plan.md` 独立承载），而 frontmatter 仍为 `status: active` 且 §2 完成度估算停留在 2026-06 时点（前端 ~5%、27 项测试），与 README 里程碑 A+B 交付状态（access-service 双轨 842 项测试）直接相反，构成对现状的误导；按归档条件「计划只剩历史追溯价值」收口。痛点 #3（自动授权）/ #4（动态数据权限）暂缓决策仍有效，作为决策溯源继续被 T-PERM-035/036 的 decision_refs 引用。

## 内容定位

| 文档 | 说明 |
|------|------|
| `product-vertical-slice-plan.md` | 产品垂直切片与试点加固计划（2026-08-22 立项 → 2026-08-27 收口，status: completed）。里程碑 A（T-ACCESS-021 BASIC_ROLE 授权垂直切片 E2E 八步全绿）与里程碑 B（试点加固 7 项 + example 接入 + 本收口）全部达成。含执行进度日志（模型收敛 → bootstrap → 前端真实登录 → E2E → 加固全程），仅作历史追溯 |
| `access-post-merge-plan.md` | access-service 归并后续强化计划（T-ACCESS-013 OAuth2 资源服务器 / T-ACCESS-014 操作日志覆盖 / T-ACCESS-015 菜单写链路对齐，全部 done；status: completed）。原「40 项 Docker 门控 CI 准入前置」由 T-ACCESS-017 最小 CI（GitHub Actions 两 job，以退出状态判定成功）落地关闭 |
| `improvement-plan.md` | 项目诊断与完善路线图（2026-06-06 v2.0，status: archived）。§2 完成度估算与 §3 痛点诊断为 2026-06 时点快照，不再反映现状；§3.1 痛点 #3/#4 暂缓决策（design-review §11 E4/Q7-B 对齐）仍作 T-PERM-035/036 决策溯源 |

## 归档自检清单执行记录

- [x] 源文件移动后已修复内部相对链接（`../tasks/` → `../../tasks/`、跨批次归档引用适配）。
- [x] 全仓 grep 旧路径无残留（任务卡 frontmatter `plan` 字段、docs/plans/README.md、docs/README.md、docs/tasks/README.md 看板均已同步指向归档位置或改为「（已归档）」标注）。
- [x] `docs/README.md` 执行计划索引与归档记录表已刷新（目录树新增本批次）。
- [x] 稳定结论已沉淀 `docs/design/`（模型收敛见 access-service-architecture §12（主体身份模型）/§13（资源类型注册表）、bootstrap 见 §14.7、E2E 见 §14.8、时间语义见 §16；本批次归档文档不再作为实现依据）。

**improvement-plan 追加归档自检补记（2026-08-27 双轨评审收口）**：

- [x] 源文件内部相对链接已修复（7 处：`../archive/2026-07-12/` → `../2026-07-12/`、`frontend-phase2/3/4-plan.md` → `../../plans/…`、`../tasks/README.md` → `../../tasks/…`、`../archive/2026-06-17/` → `../2026-06-17/`；首次归档时漏做，评审 P1 修复）。
- [x] 活文档旧路径残留已清（org-user-permission-contract.md 关联文档行改指归档位置并加「已归档」括注）。
- [x] `docs/README.md` 与 `docs/design/README.md` 归档批次表补 improvement-plan 条目（首次归档时漏改，评审 P2 修复）。

## 当前权威设计入口

- 归并后目标架构：`docs/design/access-service-architecture.md`
- 唯一权威 DDL：`docs/design/schema/access-service.sql`
- 外部验证与 CI 口径：CI 以 GitHub Actions（`.github/workflows/ci.yml`）退出状态判定成功；68 项为 2026-08-22 外部 Docker 主机历史验证基线（证据登记见任务卡 [T-ACCESS-026](../../tasks/T-ACCESS-026.md)）。
