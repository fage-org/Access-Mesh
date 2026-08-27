# 2026-08-27 归档批次：product-vertical-slice 计划收口

## 归档原因

T-ACCESS-026（验证证据登记与文档状态收口）完成，`product-vertical-slice-plan` 18 项任务全部 done（里程碑 A + B 均达成），其前置依赖的 `access-post-merge-plan` 三任务（T-ACCESS-013~015）亦早已全 done——两个计划满足各自归档条件（阶段性任务完成 + 稳定结论沉淀 `docs/design/` + CI 准入前置已由 T-ACCESS-017 最小 CI 落地关闭），按 project-rules §文档治理归档。

## 内容定位

| 文档 | 说明 |
|------|------|
| `product-vertical-slice-plan.md` | 产品垂直切片与试点加固计划（2026-08-22 立项 → 2026-08-27 收口，status: completed）。里程碑 A（T-ACCESS-021 BASIC_ROLE 授权垂直切片 E2E 八步全绿）与里程碑 B（试点加固 7 项 + example 接入 + 本收口）全部达成。含执行进度日志（模型收敛 → bootstrap → 前端真实登录 → E2E → 加固全程），仅作历史追溯 |
| `access-post-merge-plan.md` | access-service 归并后续强化计划（T-ACCESS-013 OAuth2 资源服务器 / T-ACCESS-014 操作日志覆盖 / T-ACCESS-015 菜单写链路对齐，全部 done；status: completed）。原「40 项 Docker 门控 CI 准入前置」由 T-ACCESS-017 最小 CI（GitHub Actions 两 job，以退出状态判定成功）落地关闭 |

## 归档自检清单执行记录

- [x] 源文件移动后已修复内部相对链接（`../tasks/` → `../../tasks/`、跨批次归档引用适配）。
- [x] 全仓 grep 旧路径无残留（任务卡 frontmatter `plan` 字段、docs/plans/README.md、docs/README.md、docs/tasks/README.md 看板均已同步指向归档位置或改为「（已归档）」标注）。
- [x] `docs/README.md` 执行计划索引与归档记录表已刷新（目录树新增本批次）。
- [x] 稳定结论已沉淀 `docs/design/`（模型收敛见 access-service-architecture §12（主体身份模型）/§13（资源类型注册表）、bootstrap 见 §14.7、E2E 见 §14.8、时间语义见 §16；本批次归档文档不再作为实现依据）。

## 当前权威设计入口

- 归并后目标架构：`docs/design/access-service-architecture.md`
- 唯一权威 DDL：`docs/design/schema/access-service.sql`
- 外部验证与 CI 口径：CI 以 GitHub Actions（`.github/workflows/ci.yml`）退出状态判定成功；68 项为 2026-08-22 外部 Docker 主机历史验证基线（证据登记见任务卡 [T-ACCESS-026](../../tasks/T-ACCESS-026.md)）。
