# 跨服务设计索引

本目录用于存放跨越多个服务边界的设计及仍需保留原位的 superseded 追溯文档。当前 `access-service` 归并权威设计位于 [`../access-service-architecture.md`](../access-service-architecture.md)。

| 主题 | 文档 | 说明 |
|------|------|------|
| admin-service 与 permission-center 同步（已归档） | [admin-permission-sync.md](../../archive/2026-08-15/admin-permission-sync.md) | `status: superseded`；旧异步同步设计，仅历史追溯。替代设计为 `access-service` 单库强事务 |

本目录中的 `adopted` 文档面向长期有效的契约、数据流、安全边界和一致性约束；`superseded` 文档仅为保持链接可解析和历史追溯而保留。阶段任务、迁移检查或验收清单不放在本目录；仍在推进的计划放在 `../../plans/`，已完成的过程文档归档到 `../../archive/YYYY-MM-DD/`。

即使主题相同，新一轮执行计划也应另起 `../../plans/<topic>-plan-vN.md` 或更贴近任务名的计划文件；不要把 S9、S10 之类的过程步骤继续追加到本目录的设计文档中。
