---
doc_type: plan
title: 权限缓存失效改造（工作单 A）
status: active
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md
  - docs/design/permission-center/overview.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/services/gateway.md
tasks:
  - T-PERM-001
  - T-PERM-002
  - T-PERM-003
  - T-PERM-004
  - T-PERM-005
  - T-PERM-006
  - T-PERM-007
  - T-PERM-008
  - T-PERM-017
  - T-PERM-018
acceptance: "A-1~A-7 全完成；代码无 permission_version 残留；Gateway 快照模式 + Redis 广播端到端验证通过（A-8 待 S-006 设计明确后单独跟踪）"
last_updated: 2026-06-27
---

# 权限缓存失效改造计划（工作单 A）

> 状态：进行中（T-PERM-001·002·003·004·005·006·007·017·018 done / 008 proposed）。任务状态快照见下表，**权威清单以 [../tasks/README.md](../tasks/README.md) 看板为准**。
> 关联设计：[../design/permission-center-v3.5-design.md](../design/permission-center-v3.5-design.md) §7.2 缓存一致性总线
> 关联评审（已归档）：[../archive/2026-06-17/design-review.md](../archive/2026-06-17/design-review.md) §4.1 工作单 A
> 关联审计：S-001（删除 permission_version，B 决策）/ S-006（Gateway 失效标记，待设计）

## 目标

落实 design-review §4.1 工作单 A 决策（方案 A'）：

1. **Gateway 改为快照模式**：缓存 key 从 `(user, path) → bool` 改为 `user → InterfaceSnapshot`，鉴权时本地内存匹配
2. **写路径用 `PermissionChangeContext`（ThreadLocal）+ AOP**：DomainService 显式登记影响范围，AppService 入口 AOP 在事务提交后统一处理
3. **完全删除 `permission_version` 机制**：表 / 实体 / Service / Mapper / Controller / DTO / 4 处 increment 调用 / 缓存目录条目 / key 后缀 / 文档段落
4. **失效广播**：Redis pub/sub 主动推送 `PermInvalidateEvent` + TTL（30-60s）兜底

## 非目标

- 不做持久化 outbox 重投（S-003 决策 A：Redis pub/sub + TTL 兜底，无持久化重投）
- 不做 Gateway 失效标记与订阅恢复策略的完整规范（S-006 待设计，本计划仅占位）
- 不改 L1/L2 权限模型语义（仅改缓存失效驱动机制）

## 任务清单（引用 [../tasks/README.md](../tasks/README.md) 看板）

> 状态简写：⚙️=proposed / 🔨=in-progress / 👀=review / ✅=done。本表为快照，权威状态以看板为准。

| 任务 ID | 标题 | 关联决策 | 状态 |
|---|---|---|---|
| [T-PERM-001](../tasks/T-PERM-001.md) | Gateway 缓存改快照模式（`user → InterfaceSnapshot`）| A'-1 | ✅ |
| T-PERM-002 | `PermissionChangeContext` ThreadLocal + AppService AOP afterCommit | A'-2 | ✅ |
| T-PERM-003 | 删除 `permission_version` 表 + 实体 + Service + Mapper + Controller + DTO；存量环境 `DROP TABLE permission_version` 为外部 DBA/运维动作（当前仓库无 migration 框架） | A'-3 / S-001 | ✅ |
| T-PERM-004 | 删除 4 处 `permissionVersionDomainService.increment` 调用 | A'-3 / S-001 | ✅ |
| T-PERM-005 | 删除缓存目录 `PermCacheCatalog.PERMISSION_VERSION` + key 后缀 `:{permissionVersion}` | A'-3 | ✅ |
| [T-PERM-006](../tasks/T-PERM-006.md) | Gateway 订阅 `perm:invalidate`，按 tenant+serviceCodes/userIds evict 本地 INTERFACE_SNAPSHOT（roleIds-only 事件按租户级安全清理） | A'-4 | ✅ |
| T-PERM-007 | 同步修订 overview/core-flows/implementation/api-contract/coding-standards §5（代码层一致性核对）| S-001 | ✅ |
| T-PERM-008 | Gateway 失效标记与订阅恢复策略（S-006 已设计，规范见 gateway.md §快照失效标记与订阅恢复；依赖 T-GW-005 ✅）| S-006 | ⚙️ |
| [T-PERM-017](../tasks/T-PERM-017.md) | 条件权限 Gateway 侧重评（部分下发 `gateway_evaluable` + 未下发回退 check-interface）| 工作单 A 扩展 | ✅ |
| [T-PERM-018](../tasks/T-PERM-018.md) | 缓存下沉——移除 INTERFACE_SNAPSHOT(L2)/permissionVersion，激活 ROLE_PERM_SNAPSHOT engine 读缓存，扩展失效事件 serviceCodes | A'-5（T-PERM-018 派生）| ✅ |

## 准入条件

- [ ] design-review §11 暂缓解除（A/B/C 已重启）
- [ ] ~~S-006 Gateway 失效标记待设计方案明确~~（2026-06-28 T-GW-005 已完成设计）
- [ ] schema-actual-state 盘点报告（检测预发/灰度是否已 ALTER v3.4 残留字段）

## 当前进度

- 文档层：S-001 决策已反映到 overview/core-flows/implementation/api-contract/coding-standards（2026-06-20 审计落地）
- schema 层：`permission-center.sql` permission_version 表 CREATE 已注释化 + OBSOLETED 说明（2026-06-20）；当前仓库未启用 Flyway/Liquibase 等 migration 框架，存量环境如已存在 `permission_version` 表，需由 DBA/运维按发布流程外部执行 `DROP TABLE permission_version`，权威 schema 文件不含破坏性语句。
- 代码层：
  - T-PERM-001 ✅ Gateway 快照模式已落地（commit de5d2cb26 + 4bf27f8fb）
  - T-PERM-002 ✅ PermissionChangeContext + AOP afterCommit 已落地（commit 12c734c32 + 1e363945b）——业务侧 15 处手写 TransactionSynchronizationManager 全部消除（P1-B 达标），Redis 发布端 PermInvalidationPublisher 已就位
  - T-PERM-003 ✅ 删 permission_version 已落地
  - T-PERM-017 ✅ 条件权限 Gateway 侧重评已落地（2026-06-25）——`gateway_evaluable` 写入门禁、`conditionRules` 内联、Gateway 本地重评、未下发条件 fallback check-interface、条件变更 serviceCodes 广播登记已完成；补齐 logic 白名单与 null/空串防御。
  - T-PERM-006 ✅ Redis 广播订阅器已落地（2026-06-27）——广播契约 `PermInvalidateEvent` 下沉到 `perm-common` 供发布端/订阅端共享；permission-center 通过 `StringRedisTemplate.convertAndSend("perm:invalidate", json)` 发布；Gateway `PermInvalidationSubscriber` 使用 reactive Redis 订阅并调用 `InterfaceSnapshotCacheInvalidator` 清本地 Caffeine 快照。失效粒度：`serviceCodes` 非空按租户+服务清、`userIds` 非空按租户+用户清、仅 `roleIds` 非空时 Gateway 无本地反查能力，按租户级安全清理；TTL 继续兜底。
  - T-PERM-018 ✅ 缓存下沉已落地（2026-06-20，3 commit）——移除 INTERFACE_SNAPSHOT(L2)/permissionVersion/notModified，激活 ROLE_PERM_SNAPSHOT engine 读缓存（getBatch/putBatch/空列表缓存），扩展失效事件 serviceCodes，资源软删双重登记 + API mapping/sync 全路径 markServiceCodes。RolePermEntry 提升顶层 record。C10 边界：仅发布 serviceCodes 载荷，Gateway 订阅侧已由 T-PERM-006 闭环。mvn test 143 passed
  - T-PERM-004 ✅ increment 残留核验完成（2026-06-27）——主源码已无 `permissionVersionDomainService.increment` / `PermissionVersionDomainService` / `buildInterfacePermissionVersion` 残留；同步删除 T-PERM-003 遗留的 `PermissionVersion` 实体源码，避免 `permission_version` 生产实体继续生成。
  - T-PERM-005 ✅ 缓存目录/key 后缀残留清理完成（2026-06-27）——主源码已无 `PermCacheCatalog.PERMISSION_VERSION`、`perm:permission-version` 与 `:{permissionVersion}` key 构建；缓存配置与 skill 示例改为现行 `effective-roles` / `role-perm-snapshot`。
  - T-PERM-007 ✅ 文档与代码层一致性核对完成（2026-06-27）——overview/core-flows/implementation/api-contract/coding-standards 已同步至当前缓存失效模型：@PermissionChange + PermissionChangeContext afterCommit 统一 evict/broadcast、permission_version/INTERFACE_SNAPSHOT(L2)/notModified 移除、ROLE_PERM_SNAPSHOT 读缓存与 serviceCodes 广播边界；同步清理代码注释中的“版本递增”残留。
  - T-PERM-008 未启动


## 归档条件

- A-1 ~ A-7 全部完成（A-8 待 S-006 设计明确后单独跟踪）
- 代码层无 `permission_version` / `PermissionVersionDomainService.increment` 残留
- Gateway 快照模式 + Redis 广播端到端验证通过
