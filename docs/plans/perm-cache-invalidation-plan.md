# 权限缓存失效改造计划（工作单 A）

> 状态：待启动
> 关联设计：[../design/permission-center-v3.5-design.md](../design/permission-center-v3.5-design.md) §7.2 缓存一致性总线
> 关联评审（已归档）：[../archive/2026-06-17/design-review.md](../archive/2026-06-17/design-review.md) §4.1 工作单 A
> 关联审计：S-001（删除 permission_version，B 决策）/ S-006（Gateway 失效标记，待设计）

## 目标

落实 design-review §4.1 工作单 A 决策（方案 A'）：

1. **Gateway 改为快照模式**：缓存 key 从 `(user, path) → bool` 改为 `user → InterfaceSnapshot`，鉴权时本地内存匹配
2. **写路径用 `PermissionChangeContext`（ThreadLocal）+ AOP**：DomainService 显式登记影响范围，AppService 入口 AOP 在事务提交后统一处理
3. **完全删除 `permission_version` 机制**：表 / 实体 / Service / Mapper / Controller / DTO / 4 处 increment 调用 / 缓存目录条目 / 文档段落
4. **失效广播**：Redis pub/sub 主动推送 `PermInvalidateEvent` + TTL（30-60s）兜底

## 非目标

- 不做持久化 outbox 重投（S-003 决策 A：Redis pub/sub + TTL 兜底，无持久化重投）
- 不做 Gateway 失效标记与订阅恢复策略的完整规范（S-006 待设计，本计划仅占位）
- 不改 L1/L2 权限模型语义（仅改缓存失效驱动机制）

## 任务清单（引用 design-review §4.1）

| # | 任务 | 关联决策 |
|---|---|---|
| A-1 | Gateway 缓存改快照模式（`user → InterfaceSnapshot`）| A'-1 |
| A-2 | `PermissionChangeContext` ThreadLocal + AppService AOP afterCommit | A'-2 |
| A-3 | 删除 `permission_version` 表 + 实体 + Service + Mapper + Controller + DTO；**含存量环境 `DROP TABLE IF EXISTS permission_version CASCADE` migration 脚本**（权威 schema 文件不含破坏性语句，回滚由此任务承载）| A'-3 / S-001 |
| A-4 | 删除 4 处 `permissionVersionDomainService.increment` 调用 | A'-3 / S-001 |
| A-5 | 删除缓存目录 `PermCacheCatalog.PERMISSION_VERSION` + key 后缀 `:{permissionVersion}` | A'-3 |
| A-6 | Redis pub/sub 广播 `PermInvalidateEvent`（topic: `perm:invalidate`）+ Gateway 订阅器 | A'-4 |
| A-7 | 同步修订 overview.md / core-flows.md / implementation.md / api-contract.md interface-snapshot / coding-standards §5（文档层审计已部分完成，本任务核对代码层一致性）| S-001 |
| A-8 | Gateway 失效标记与订阅恢复策略（**待设计 S-006**，本任务占位，规范明确后补）| S-006 |

## 准入条件

- [ ] design-review §11 暂缓解除（A/B/C 已重启）
- [ ] S-006 Gateway 失效标记待设计方案明确（或接受 A-8 后补）
- [ ] schema-actual-state 盘点报告（检测预发/灰度是否已 ALTER v3.4 残留字段）

## 当前进度

- 文档层：S-001 决策已反映到 overview/core-flows/implementation/api-contract/coding-standards（2026-06-20 审计落地）
- schema 层：`permission-center.sql` permission_version 表 CREATE 已注释化 + OBSOLETED 说明（2026-06-20）；**存量环境 DROP TABLE 回滚由本计划 A-3 的 migration 脚本承载**（权威 schema 文件不含破坏性语句）
- 代码层：**未启动**（PermissionVersion* 类、4 处 increment 调用、Gateway 缓存模型均未改）

## 归档条件

- A-1 ~ A-7 全部完成（A-8 待 S-006 设计明确后单独跟踪）
- 代码层无 `permission_version` / `PermissionVersionDomainService.increment` 残留
- Gateway 快照模式 + Redis 广播端到端验证通过
