# 2026-06-28 归档批次

## 归档原因

设计评审（2026-06-17）派生的三个 P0 工作单计划全部完成，任务均已 done + 回写，归档条件满足。

## 归档内容

| 文档 | 类型 | 完成情况 | 权威设计入口 |
|------|------|----------|-------------|
| [perm-cache-invalidation-plan.md](perm-cache-invalidation-plan.md) | 工作单 A：权限缓存失效改造 | T-PERM-001·002·003·004·005·006·007·008·017·018 全 done | [permission-center-v3.5-design.md](../../design/permission-center-v3.5-design.md) §7.2；[gateway.md](../../design/services/gateway.md) |
| [scope-mode-migration-plan.md](scope-mode-migration-plan.md) | 工作单 B：scopeMode 协议迁移 | T-PERM-009·010·011·012·013·014·015 全 done | [permission-center-v3.5-design.md](../../design/permission-center-v3.5-design.md) §3；[api-contract.md](../../design/permission-center/api-contract.md) |
| [gateway-fail-mode-plan.md](gateway-fail-mode-plan.md) | 工作单 C：Gateway 失联兜底 | T-GW-001·002·003·004·005·006 全 done（T-GW-006 重新界定为独立仓库测试服务） | [gateway.md](../../design/services/gateway.md) §失联兜底模式 / §快照失效标记与订阅恢复 |

## 权威设计定位

归档文档**仅作历史追溯，不作为实现依据**。稳定结论已沉淀至：

- `docs/design/permission-center-v3.5-design.md` §3 数据权限契约 / §7.2 缓存一致性总线
- `docs/design/permission-center/api-contract.md` scopeMode 正式协议定义
- `docs/design/permission-center/overview.md` / `core-flows.md` / `implementation.md` 缓存失效模型同步
- `docs/design/services/gateway.md` 失联兜底模式 / 快照失效标记与订阅恢复
- `docs/design/permission-center/coding-standards.md` §5 代码层一致性核对

## 任务看板

权威任务清单见 [docs/tasks/README.md](../../tasks/README.md)，归档文件内的任务状态快照可能滞后。
