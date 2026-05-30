# 2026-05-30 归档

## 归档文件

| 文件 | 说明 | 归档原因 |
|------|------|----------|
| `permission-check-flow.puml` | 重构前权限查询 5 条独立链路时序图 | 架构已统一为 PermQueryEngine |
| `permission-unified-plan.md` | 统一权限查询引擎实施完成记录 (v3) | 内容已合并到 `docs/design/permission-center/` |
| `permission-unified-query-plan.puml` | 统一查询引擎 v2 架构时序图 | 架构已在 `implementation.md` 中描述 |
| `frontend-integration.md` | 前端集成计划（pure-admin-thin 方案） | 前端项目已删除，需重新设计 |
| `permission-center-refactor-impact-analysis.md` | DDD 重构跨模块影响分析 | Phase 1-5 重构已完成，问题已修复或过时 |
| `service-layer-review.md` | Service 层重构审查（Phase 1-5 完成总结） | 重构已完成，内容已合并到 `implementation.md` |

## 内容去向

- **重构收益数据**（代码行数变化、入口迁移状态）→ 已合并到 `service-layer-review.md`（已归档）
- **引擎核心类清单** → 已合并到 `implementation.md` §3.2
- **PlantUML 时序图** → 历史参考，如需查看请翻阅 git 历史
- **auto-grant TODO** → 已合并到 `implementation.md` §4.2 批量授权执行链路
- **Gateway 权限门禁变更** → 已合并到 `core-flows.md` §9

## 历史背景

这些文档记录了 2026-04-28 ~ 2026-05-24 期间权限中心从"5 条独立查询链路"到"统一 PermQueryEngine"的重构过程。
重构前的问题：
- 5 条路径各自实现角色解析、DB 查询、评估逻辑
- `PermissionServiceImpl` 达 1923 行，大量重复代码
- 4 个不同地方跑几乎相同的 SQL
- 3 套评估逻辑、3 种 DTO 组装方式

重构后：
- 统一为 `PermQueryEngine.query(PermQuery) → PermResult`
- `PermissionServiceImpl` 降至 ~841 行
- 调用方代码从 30-230 行降至 12-25 行
- 调用深度从 4-6 层降至 2 层
