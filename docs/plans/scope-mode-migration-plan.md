# scopeMode 协议迁移计划（工作单 B）

> 状态：待启动
> 关联设计：[design-review-2026-06-17.md](design-review-2026-06-17.md) §4.2 工作单 B
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

## 任务清单（引用 design-review §4.2）

| # | 任务 | 关联决策 |
|---|---|---|
| B-1 | 定义 `scopeMode` 枚举（INSTANCE/ALL/NONE）+ 响应结构 `{allowed, scopeMode, items[], scopeTypeCodes[]}` | B2 |
| B-2 | api-contract.md §6.7 query-scopes 响应改造（scopeAll → scopeMode）| B2 / S-005 |
| B-3 | api-contract.md §6.4-6.10 / §10.8 等约 30+ 处 `scopeAll` 全量推广到 `scopeMode` | S-005=A |
| B-4 | 管理端授权配置 / 排查页响应改造（role-resource-permission save/grant、permission-view 等）| S-005=A |
| B-5 | schema `scope_all` 字段保留（仅内部存储），协议层映射逻辑实现 | B2 |
| B-6 | 同步修订 api-contract.md 顶部 scopeMode 迁移注记（审计 S-005 已登记，本任务实施时移除注记改为正式定义）| S-005 |
| B-7 | 前端 hasPerms / Perms 组件适配 scopeMode 三分支（如涉及）| B2 |

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
