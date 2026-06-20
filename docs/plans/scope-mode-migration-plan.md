---
doc_type: plan
title: scopeMode 协议迁移（工作单 B）
status: proposed
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md
  - docs/design/permission-center/api-contract.md
tasks:
  - T-PERM-009
  - T-PERM-010
  - T-PERM-011
  - T-PERM-012
  - T-PERM-013
  - T-PERM-014
  - T-PERM-015
acceptance: "B-1~B-7 全完成；api-contract.md 无残留 scopeAll 作为协议字段（内部存储字段除外）；调用方按 scopeMode 三分支编程"
last_updated: 2026-06-20
---

# scopeMode 协议迁移计划（工作单 B）

> 状态：待启动
> 关联设计：[../design/permission-center-v3.5-design.md](../design/permission-center-v3.5-design.md) §3 数据权限契约
> 关联评审（已归档）：[../archive/2026-06-17/design-review.md](../archive/2026-06-17/design-review.md) §4.2 工作单 B
> 关联审计：S-005（scopeMode 全量推广，A 决策）

## 目标

落实 design-review §4.2 工作单 B 决策（方案 B2）：

1. **协议层防呆**：返回体由 `{allowed, items[], scopeAll}` 改为 `{allowed, scopeMode, items[], scopeTypeCodes[]}`
2. **scopeMode 三值枚举**：`INSTANCE | ALL | NONE`，强制调用方按枚举三分支编程（编译期暴露遗漏分支）
3. **全量推广**（审计 S-005=A）：覆盖运行时鉴权口 + 管理端授权配置 + 排查页所有 `scopeAll` 出现处（api-contract.md 约 30+ 处）
4. **不做新老兼容**（design-review §B-1 决策）：项目未上线，直接换

## 非目标

- 不做 Java SDK helper（B3 决策：接入方未必用 QueryWrapper / 未必是 Java，通用平台定位）
- 不做端到端黄金路径测试（B4 决策：延后到 example-service，后者暂不实现）
- 不改 schema `scope_all` 字段（仅内部存储，协议层映射为 scopeMode）

## 任务清单（引用 [../tasks/README.md](../tasks/README.md) 看板）

| 任务 ID | 标题 | 关联决策 | 状态 |
|---|---|---|---|
| [T-PERM-009](../tasks/T-PERM-009.md) | 定义 `scopeMode` 枚举 + 响应结构改造 | B2 | ⚙️ |
| T-PERM-010 | api-contract.md §6.7 query-scopes 响应改造 | B2 / S-005 | ⚙️ |
| T-PERM-011 | api-contract.md 约 30+ 处 `scopeAll` 全量推广到 `scopeMode` | S-005=A | ⚙️ |
| T-PERM-012 | 管理端授权配置 / 排查页响应改造 | S-005=A | ⚙️ |
| T-PERM-013 | schema `scope_all` 字段保留（内部存储），协议层映射逻辑实现 | B2 | ⚙️ |
| T-PERM-014 | 同步修订 api-contract.md 顶部 scopeMode 迁移注记（移除注记改为正式定义）| S-005 | ⚙️ |
| T-PERM-015 | 前端 hasPerms / Perms 组件适配 scopeMode 三分支 | B2 | ⚙️ |

## 准入条件

- [ ] design-review §11 暂缓解除（A/B/C 已重启）
- [ ] api-contract.md scopeMode 迁移注记已登记（2026-06-20 审计完成）

## 当前进度

- 文档层：api-contract.md 顶部已加 scopeMode 迁移注记（2026-06-20 审计 S-005，登记决策待工作单 B 派生）
- 协议层：**未启动**（api-contract.md 30+ 处仍 scopeAll boolean）
- 代码层：**未启动**

## 归档条件

- B-1 ~ B-7 全部完成
- api-contract.md 无残留 `scopeAll` 作为协议字段（内部存储字段除外）
- 调用方按 scopeMode 三分支编程，编译期暴露遗漏分支
