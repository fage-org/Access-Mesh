# 任务看板（Task Board）

本目录是 AccessMesh 任务的**唯一权威清单**。任务为原子执行单元，归属某个 [计划](../plans/)，并声明将改动的 [设计](../design/) 章节。

> 治理规则见 skill：`.claude/skills/design-plan-task-lifecycle/SKILL.md`。任务 ID 格式 `T-<DOMAIN>-<NNN>`，各领域独立递增、ID 冻结不回收。

## 领域计数器

| 领域 | 前缀 | 下一编号 |
|---|---|---|
| permission-center | `T-PERM` | 016 |
| admin-service | `T-ADMIN` | 001 |
| gateway | `T-GW` | 007 |
| 组织/用户（跨 admin+perm） | `T-ORG` | 001 |
| 跨服务 API 契约 | `T-API` | 001 |
| 前端 | `T-FE` | 001 |

> 新建任务时从对应领域取下一编号，计数器 +1。

## 任务总表

> 状态简写：⚙️=proposed / 🔨=in-progress / 👀=review / ✅=done / ❌=cancelled。回写：⏳=pending / ✓=done。

### permission-center（工作单 A 缓存失效 + 工作单 B scopeMode）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-PERM-001](T-PERM-001.md) | Gateway 缓存改快照模式（user → InterfaceSnapshot） | [perm-cache-invalidation](../plans/perm-cache-invalidation-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ⚙️ | ⏳ |
| T-PERM-002 | PermissionChangeContext ThreadLocal + AppService AOP afterCommit | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2 | T-PERM-001 | ⚙️ | ⏳ |
| T-PERM-003 | 删除 permission_version 表+实体+Service+Mapper+Controller+DTO（含存量 DROP TABLE migration） | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2；design/permission-center/overview.md；implementation.md §5.1/5.2 | — | ⚙️ | ⏳ |
| T-PERM-004 | 删除 4 处 permissionVersionDomainService.increment 调用 | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ⚙️ | ⏳ |
| T-PERM-005 | 删除缓存目录 PermCacheCatalog.PERMISSION_VERSION + key 后缀 :{permissionVersion} | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ⚙️ | ⏳ |
| T-PERM-006 | Redis pub/sub 广播 PermInvalidateEvent（topic: perm:invalidate）+ Gateway 订阅器 | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2 | T-PERM-001 | ⚙️ | ⏳ |
| T-PERM-007 | 同步修订 overview/core-flows/implementation/api-contract/coding-standards §5（代码层一致性核对） | perm-cache-invalidation | design/permission-center/{overview,core-flows,implementation,api-contract}.md | T-PERM-003 | ⚙️ | ⏳ |
| T-PERM-008 | Gateway 失效标记与订阅恢复策略（待设计 S-006，规范明确后补） | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.4 | T-GW-005（S-006 设计）| ⚙️ | ⏳ |
| T-PERM-009 | 定义 scopeMode 枚举（INSTANCE/ALL/NONE）+ 响应结构 {allowed,scopeMode,items[],scopeTypeCodes[]} | [scope-mode-migration](../plans/scope-mode-migration-plan.md) | design/permission-center-v3.5-design.md §3 | — | ⚙️ | ⏳ |
| T-PERM-010 | api-contract.md §6.7 query-scopes 响应改造（scopeAll → scopeMode） | scope-mode-migration | design/permission-center/api-contract.md §6.7 | T-PERM-009 | ⚙️ | ⏳ |
| T-PERM-011 | api-contract.md §6.4-6.10/§10.8 等约 30+ 处 scopeAll 全量推广到 scopeMode | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ⚙️ | ⏳ |
| T-PERM-012 | 管理端授权配置/排查页响应改造（role-resource-permission save/grant、permission-view） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ⚙️ | ⏳ |
| T-PERM-013 | schema scope_all 字段保留（仅内部存储），协议层映射逻辑实现 | scope-mode-migration | design/schema/permission-center.sql | T-PERM-009 | ⚙️ | ⏳ |
| T-PERM-014 | 同步修订 api-contract.md 顶部 scopeMode 迁移注记（移除注记改为正式定义） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-010, T-PERM-011 | ⚙️ | ⏳ |
| T-PERM-015 | 前端 hasPerms / Perms 组件适配 scopeMode 三分支 | scope-mode-migration | design/permission-center-v3.5-design.md §3 | T-PERM-009 | ⚙️ | ⏳ |

### gateway（工作单 C 失联兜底）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| T-GW-001 | gateway.perm.fail-mode 配置项（closed/open/stale-allow，默认 closed）+ stale-grace-seconds | [gateway-fail-mode](../plans/gateway-fail-mode-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ⚙️ | ⏳ |
| T-GW-002 | fail-closed 实现：perm-center 不可达 → 403/503 拒绝 | gateway-fail-mode | design/services/gateway.md | T-GW-001 | ⚙️ | ⏳ |
| [T-GW-003](T-GW-003.md) | stale-allow 实现：用过期未驱逐快照续命，超 stale-grace-seconds 转 closed | gateway-fail-mode | design/services/gateway.md | T-PERM-001（快照模式）, T-GW-001 | ⚙️ | ⏳ |
| T-GW-004 | 监控指标：unreachable.count / fallback.{closed,open,stale}.count + WARN + Prometheus 告警 | gateway-fail-mode | design/services/gateway.md | T-GW-002 | ⚙️ | ⏳ |
| T-GW-005 | 失效标记与订阅恢复策略设计（S-006 待设计项，规范产出） | gateway-fail-mode | design/permission-center-v3.5-design.md §9.4 | T-PERM-006（广播事件载荷）| ⚙️ | ⏳ |
| T-GW-006 | 集成测试基线："杀 permission-center → Gateway 应 503" | gateway-fail-mode | — | T-GW-002 | ⚙️ | ⏳ |

> 注：T-PERM-008（代码侧 Gateway 失效标记）依赖 T-GW-005（设计侧 S-006 规范）产出，二者构成"设计先行 → 代码落地"链。

## 依赖告警（dangling）

> 当被依赖的任务 `cancelled` 或设计被 `superseded` 时，下游任务在此登记，等待重连。

_（暂无）_

## 设计变更待核对

> 当设计文件 `status` 变为 `superseded` 或章节实质变更时，`design_refs` 指向它的任务在此登记，等待核对验收与回写目标是否仍成立。

_（暂无）_

## 已完成（done，待计划归档时清理）

_（暂无 — user-role-proxy-fix 的 M1-M13/S1-S3 为已完成待验收，将在该计划迁移时登记）_

---

## 字段说明

- **设计引用**：任务将改动的 `docs/design/...#章节` 锚点；任务 `done` 前必须回写这些章节。
- **依赖**：`depends_on` 的前置任务 ID；下游任务在前置 `done`/`cancelled` 前不应进 `done`。
- **状态**：`proposed`(⚙️) / `in-progress`(🔨) / `review`(👀) / `done`(✅) / `cancelled`(❌) / `archived`
- **回写**：设计回写状态 `pending`(⏳) / `done`(✓)；`done` 是任务 `done` 的前置条件。

## 新建任务流程

1. 取领域下一编号，计数器 +1，ID 冻结。
2. 总表加行。
3. 复杂任务（多步/独立决策/多验收条目）→ 开 `docs/tasks/<ID>.md` 独立文件，套用任务 frontmatter 模板（见 skill §2.3）。
4. 填 `plan` / `design_refs` / `depends_on`（防循环）/ `acceptance`。
5. 在所属 plan 的 `tasks:[]` 加该 ID，同步计划正文任务清单快照。
6. 若声明了 `depends_on`，检查无循环依赖。
