---
doc_type: plan
title: 产品定位落地（三档叙事整改 + 名实对齐收尾）
status: completed
domain: cross-service
design_refs:
  - README.md
  - docs/README.md
  - docs/design/README.md
  - docs/design/architecture.md
  - docs/design/access-service-architecture.md
  - docs/design/permission-center/overview.md
  - docs/design/permission-center/implementation.md
tasks:
  - T-ACCESS-027
  - T-ACCESS-028
acceptance: "产品定位口径（开源通用 IAM）回写进文档体系；adopted 文档按「当前可用 / 已规划 / 仅演进方向」三档完成叙事整改；perm-data 空装配模块删除后构建与文档口径一致。"
last_updated: 2026-08-28
---

# 产品定位落地（三档叙事整改 + 名实对齐收尾）

> 状态：active（2026-08-28 立项）
> 来源：2026-08-27 外部评审遗留项（产品边界分层叙事与维护债，此前仅会话登记；2026-08-28 定案后立项）

## 设计定案（2026-08-28）

- **产品定位 = 开源通用 IAM**：维持「通用多租户访问控制平台」身份，将来可能开源或给其他团队使用。多租户、SDK 接入、类型体系保持「当前可用 / 已规划」档，仅真正未做的进「演进方向」档。
- **暂缓能力维持暂缓**：自动授权（写入口 20048 拒绝 true，预留禁用）与动态数据权限端到端验证（延后 example-service）继续搁置，由 T-PERM-035/036 承载，等重申后重启。

## 目标

1. 按三档口径重整 adopted 文档叙事（T-ACCESS-027）：对外文档（README/架构/权限中心概念与实现）中每项能力声明可明确归档——当前可用 / 已规划 / 仅演进方向；未实现能力不得以现在时态表述。
2. 名实对齐收尾（T-ACCESS-028）：删除 perm-data 空装配模块（空 `PermDataAutoConfiguration`，无任何 Bean、无使用方），SDK 面仅保留 perm-common / perm-client / perm-gateway。

## 非目标

- 不实现任何新能力（自动授权、动态数据权限维持暂缓）。
- 不做多租户架构改造（定位维持现状，仅叙事分档）。
- example-service.sql 演示库 DDL 维持「保留供未来演示数据场景」既有决策（T-API-001 遗留登记），不处置。

## 任务清单

| 任务 | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-027](../tasks/T-ACCESS-027.md) | 产品定位定稿回写与文档三档叙事整改 | ✅ | — |
| [T-ACCESS-028](../tasks/T-ACCESS-028.md) | perm-data 空装配模块删除 | ✅ | — |

## 当前进度

- 2026-08-28：立项；定位口径定案（开源通用 IAM）；T-ACCESS-028 当日执行收口；T-ACCESS-027 当日执行收口（三档口径入口落位 design/README、architecture §4.2/§4.3 演示模块三档重写、决策过程标注清扫 27 处，完成记录见任务卡）。
- 两任务全部 done，计划 completed；物理归档条件已满足，待后续归档批次执行（含归档自检）。

## 归档条件

- T-ACCESS-027/028 全部 done；三档口径在 `docs/design/` 与 README 落地；`docs/tasks/README.md` 与 `docs/plans/README.md` 同步。
