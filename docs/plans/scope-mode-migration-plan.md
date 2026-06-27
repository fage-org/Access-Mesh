---
doc_type: plan
title: scopeMode 协议迁移（工作单 B）
status: active
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
acceptance: "B-1~B-7 全完成；api-contract.md 无残留 scopeAll 作为协议字段（内部存储字段除外）；调用方按 scopeMode 明确分支编程"
last_updated: 2026-06-27
---

# scopeMode 协议迁移计划（工作单 B）

> 状态：进行中（T-PERM-009·010·011·014 done / 012·013·015 proposed）。任务状态快照见下表，**权威清单以 [../tasks/README.md](../tasks/README.md) 看板为准**。
> 关联设计：[../design/permission-center-v3.5-design.md](../design/permission-center-v3.5-design.md) §3 数据权限契约
> 关联评审（已归档）：[../archive/2026-06-17/design-review.md](../archive/2026-06-17/design-review.md) §4.2 工作单 B
> 关联审计：S-005（scopeMode 全量推广，A 决策）

## 目标

落实 design-review §4.2 工作单 B 决策（方案 B2）：

1. **协议层防呆**：对外协议字段统一使用 `scopeMode`，不再暴露旧 boolean 范围字段
2. **scopeMode 分支模型**：`auth/query-scopes.scopeGroups[]` 使用 `DENIED | INSTANCE | ALL | EMPTY` 四态；授权请求、授权配置响应、接口快照项、`query-resources` 和 `effective-permissions` 等权限事实列表项只使用 `INSTANCE | ALL`
3. **全量推广**（审计 S-005=A）：覆盖运行时鉴权口 + 管理端授权配置 + 排查页所有旧范围 boolean 字段出现处（api-contract.md 约 30+ 处）
4. **不做新老兼容**（design-review §B-1 决策）：项目未上线，直接换

## 非目标

- 不做 Java SDK helper（B3 决策：接入方未必用 QueryWrapper / 未必是 Java，通用平台定位）
- 不做端到端黄金路径测试（B4 决策：延后到 example-service，后者暂不实现）
- 不改 schema `scope_all` 字段（仅内部存储，协议层映射为 scopeMode）

## 任务清单（引用 [../tasks/README.md](../tasks/README.md) 看板）

> 状态简写：⚙️=proposed / 🔨=in-progress / 👀=review / ✅=done。本表为快照，权威状态以看板为准。

| 任务 ID | 标题 | 关联决策 | 状态 |
|---|---|---|---|
| [T-PERM-009](../tasks/T-PERM-009.md) | 定义 `scopeMode` 枚举 + 响应结构改造 | B2 | ✅ |
| T-PERM-010 | api-contract.md §6.7 query-scopes 响应改造（范围已合并进 T-PERM-009 完成，§6.7 已回写） | B2 / S-005 | ✅ |
| T-PERM-011 | api-contract.md 约 30+ 处 `scopeAll` 全量推广到 `scopeMode` | S-005=A | ✅ |
| T-PERM-012 | 管理端授权配置 / 排查页响应改造 | S-005=A | ⚙️ |
| T-PERM-013 | schema `scope_all` 字段保留（内部存储），协议层映射逻辑实现 | B2 | ⚙️ |
| T-PERM-014 | 同步修订 api-contract.md 顶部 scopeMode 迁移注记（移除注记改为正式定义）| S-005 | ✅ |
| T-PERM-015 | 前端 hasPerms / Perms 组件适配 scopeMode 响应四态分支 | B2 | ⚙️ |

## 准入条件

- [ ] design-review §11 暂缓解除（A/B/C 已重启）
- [x] api-contract.md scopeMode 正式协议定义已回写（2026-06-27 T-PERM-011/T-PERM-014）

## 当前进度

- 文档层：api-contract.md 顶部已改为 scopeMode 正式协议定义（2026-06-27 T-PERM-011/T-PERM-014），对外目标契约统一 `scopeMode`；内部存储仍保留 `scope_all`
- 代码层：T-PERM-009 ✅ done—— `ScopeMode` 4 态枚举（DENIED/INSTANCE/ALL/EMPTY）已定义、`QueryScopesResp` 重构为按 `(resourceTypeCode, operationCode)` 分桶的分类模型，135 tests 0 failures，api-contract §6.7 回写 done
- 协议层：T-PERM-010 ✅（范围合并进 T-PERM-009，§6.7 已回写 query-scopes 四态）；T-PERM-011 ✅ 已完成全量推广：api-contract.md 中旧 boolean 范围字段不再作为对外协议字段出现，请求侧只允许 `INSTANCE/ALL`，权限事实列表项只使用 `INSTANCE/ALL`，`query-scopes.scopeGroups[]` 使用四态；T-PERM-014 ✅ 已完成顶部正式定义收尾
- 前端适配：**未启动**（T-PERM-015 proposed）

## 归档条件

- B-1 ~ B-7 全部完成
- api-contract.md 无残留 `scopeAll` 作为协议字段（内部存储字段除外）
- 调用方按 scopeMode 明确分支编程，编译期暴露遗漏分支
