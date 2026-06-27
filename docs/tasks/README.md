# 任务看板（Task Board）

本目录是 AccessMesh 任务的**唯一权威清单**。任务为原子执行单元，归属某个 [计划](../plans/)，并声明将改动的 [设计](../design/) 章节。

> 治理规则见 skill：`.claude/skills/design-plan-task-lifecycle/SKILL.md`。任务 ID 格式 `T-<DOMAIN>-<NNN>`，各领域独立递增、ID 冻结不回收。

## 领域计数器

| 领域 | 前缀 | 下一编号 |
|---|---|---|
| permission-center | `T-PERM` | 019 |
| admin-service | `T-ADMIN` | 020 |
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
| [T-PERM-001](T-PERM-001.md) | Gateway 缓存改快照模式（user → InterfaceSnapshot） | [perm-cache-invalidation](../plans/perm-cache-invalidation-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ✅ | ✓ |
| T-PERM-002 | PermissionChangeContext ThreadLocal + AppService AOP afterCommit | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2 | T-PERM-001 | ✅ | ✓ |
| T-PERM-003 | 删除 permission_version 表+实体+Service+Mapper+Controller+DTO（存量环境 DROP TABLE 为外部 DBA/运维动作，仓库无 migration 框架） | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2；design/permission-center/overview.md；implementation.md §5.1/5.2 | — | ✅ | ✓ |
| T-PERM-004 | 删除 4 处 permissionVersionDomainService.increment 调用 | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ✅ | ✓ |
| T-PERM-005 | 删除缓存目录 PermCacheCatalog.PERMISSION_VERSION + key 后缀 :{permissionVersion} | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ✅ | ✓ |
| [T-PERM-006](T-PERM-006.md) | Gateway 订阅 perm:invalidate topic，按 tenant+serviceCodes/userIds evict 本地 INTERFACE_SNAPSHOT（roleIds-only 事件按租户级安全清理） | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | T-PERM-018 | ✅ | ✓ |
| T-PERM-007 | 同步修订 overview/core-flows/implementation/api-contract/coding-standards §5（代码层一致性核对） | perm-cache-invalidation | design/permission-center/{overview,core-flows,implementation,api-contract}.md | T-PERM-003 | ✅ | ✓ |
| T-PERM-008 | Gateway 失效标记与订阅恢复策略（S-006 已设计，规范见 gateway.md §快照失效标记与订阅恢复） | perm-cache-invalidation | design/services/gateway.md §快照失效标记与订阅恢复 | T-GW-005（S-006 设计 ✅）| ⚙️ | ⏳ |
| T-PERM-009 | scopeMode 4 态枚举(DENIED/INSTANCE/ALL/EMPTY) + QueryScopesResp 分类模型重构(按 resourceType×operation 分桶) | [scope-mode-migration](../plans/scope-mode-migration-plan.md) | design/permission-center-v3.5-design.md §3 | T-PERM-003 | ✅ | ✓ |
| T-PERM-010 | api-contract.md §6.7 query-scopes 响应改造（scopeAll → scopeMode）— 范围已合并进 T-PERM-009 完成（§6.7 已回写 scopeMode 四态） | scope-mode-migration | design/permission-center/api-contract.md §6.7 | T-PERM-009 | ✅ | ✓ |
| T-PERM-011 | api-contract.md §6.4-6.10 / §10 第 8 条等约 30+ 处 scopeAll 全量推广到 scopeMode | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ✅ | ✓ |
| T-PERM-012 | 管理端授权配置/排查页响应改造（role-resource-permission save/grant、permission-view） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ✅ | ✓ |
| T-PERM-013 | schema scope_all 字段保留（仅内部存储），协议层映射逻辑实现 | scope-mode-migration | design/schema/permission-center.sql | T-PERM-009 | ✅ | ✓ |
| T-PERM-014 | 同步修订 api-contract.md 顶部 scopeMode 迁移注记（移除注记改为正式定义） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-010, T-PERM-011 | ✅ | ✓ |
| T-PERM-015 | 前端 ScopeMode 类型定义 + composable（hasPerms/Perms 不涉及 L2 数据权限，无需改造） | scope-mode-migration | design/permission-center-v3.5-design.md §3 | T-PERM-009 | ✅ | ✓ |
| [T-PERM-017](T-PERM-017.md) | 条件权限 Gateway 侧重评（部分下发 gateway_evaluable + 未下发回退 check-interface） | perm-cache-invalidation | design/services/gateway.md；v3.5 §7.2 | T-PERM-002, T-PERM-018 | ✅ | ✓ |
| [T-PERM-018](T-PERM-018.md) | 缓存下沉——移除 INTERFACE_SNAPSHOT(L2)/permissionVersion，激活 ROLE_PERM_SNAPSHOT engine 读缓存，扩展失效事件 serviceCodes | perm-cache-invalidation | v3.5 §5.1/§7.2；api-contract §6.x | T-PERM-002 | ✅ | ✓ |

### gateway（工作单 C 失联兜底）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| T-GW-001 | gateway.perm.fail-mode 配置项（closed/open/stale-allow，默认 closed）+ stale-grace-seconds | [gateway-fail-mode](../plans/gateway-fail-mode-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ⚙️ | ⏳ |
| T-GW-002 | fail-closed 实现：perm-center 不可达 → 403/503 拒绝 | gateway-fail-mode | design/services/gateway.md | T-GW-001 | ⚙️ | ⏳ |
| [T-GW-003](T-GW-003.md) | stale-allow 实现：用过期未驱逐快照续命，超 stale-grace-seconds 转 closed | gateway-fail-mode | design/services/gateway.md | T-PERM-001（快照模式）, T-GW-001 | ⚙️ | ⏳ |
| T-GW-004 | 监控指标：unreachable.count / fallback.{closed,open,stale}.count + WARN + Prometheus 告警 | gateway-fail-mode | design/services/gateway.md | T-GW-002 | ⚙️ | ⏳ |
| T-GW-005 | 失效标记与订阅恢复策略设计（S-006 规范产出，已完成） | gateway-fail-mode | design/services/gateway.md §快照失效标记与订阅恢复 | T-PERM-006（广播事件载荷）| ✅ | ✓ |
| T-GW-006 | 集成测试基线："杀 permission-center → Gateway 应 503" | gateway-fail-mode | — | T-GW-002 | ⚙️ | ⏳ |

> 注：T-PERM-008（代码侧 Gateway 失效标记）依赖 T-GW-005（设计侧 S-006 规范）产出，二者构成"设计先行 → 代码落地"链。

### admin-service

_当前无活跃 T-ADMIN 任务。`T-ADMIN-001~019`（用户角色代理修复第一、二轮）已全部完成并归档，见下方"已完成"区。_

> EXT-7（PermissionCheckAppServiceImpl.batchCheck 逐条循环）/ EXT-8（SyncTaskDomainServiceImpl.enqueueAll 逐条 insert）为 DEFERRED 无主项（审计 S-024），未纳入本批任务，待单独立项。

---

## 建议执行顺序

依据：①评审定级（A/B/C 为 P0）②依赖解锁价值 ③验收闭环优先 ④无依赖可立即并行。

### P0 — 验收闭环（✅ 已完成 2026-06-20）

`T-ADMIN-001~016`：16 项验收 + 设计回写完成，转 ✅ done。计划 `user-role-proxy-fix` 满足归档条件（待执行归档至 `docs/archive/`，看板清理 16 项至"已完成"区）。无新开发。

### P1 — 工作单 A 缓存失效（安全风险，第 1 周首位）

按依赖解锁顺序：

1. `T-PERM-001` 快照模式 — ✅ done（枢纽，解锁 002/006/T-GW-003）
2. `T-PERM-003` 删 permission_version — ✅ done（解锁 004/005/007）
3. `T-PERM-002` AOP afterCommit — ✅ done
4. `T-PERM-006` Redis 广播+订阅器 — ✅ done（消费 T-PERM-018 serviceCodes 载荷；解锁 T-GW-005 设计）
5. `T-PERM-004` 删 increment — ✅ done
6. `T-PERM-005` 删缓存目录条目 — ✅ done
7. `T-PERM-007` 文档一致性核对 — ✅ done（2026-06-27）
8. `T-PERM-008` 失效标记代码 ← T-GW-005 ✅（S-006 设计已完成，可启动）

### P2 — 工作单 B scopeMode（数据泄露风险，与 A 完全并行）

1. `T-PERM-009` 枚举+响应结构 — ✅ done（枢纽）
2. `T-PERM-010` §6.7 改造 — ✅ done（范围合并进 T-PERM-009）
3. `T-PERM-011` 30+处全量推广 → ✅ done
4. `T-PERM-012` 管理端/排查页 → ✅ done
5. `T-PERM-013` schema 映射逻辑 ← 009 → ✅ done
6. `T-PERM-015` 前端 ScopeMode 类型 + composable ← 009 → ✅ done
7. `T-PERM-014` 迁移注记→正式定义 → ✅ done

### P3 — 工作单 C Gateway 兜底（依赖 A）

1. `T-GW-001` fail-mode 配置 — 无依赖，可与 A 并行启动
2. `T-GW-002` fail-closed ← 001
3. `T-GW-003` stale-allow ← T-PERM-001(快照)+T-GW-001（A 落地后才能做）
4. `T-GW-004` 监控指标 ← 002
5. `T-GW-005` S-006 设计 ← T-PERM-006(广播载荷) → ✅ done（2026-06-28）
6. `T-GW-006` 集成测试 ← 002
7. `T-PERM-008` 失效标记代码 ← T-GW-005（回到 A 链收尾）

### 不排期（待立项）

EXT-7（batchCheck 逐条循环）/ EXT-8（enqueueAll 逐条 insert）— 审计 S-024 无主，性能项，待单独立项。

### 已完成的三个枢纽

`T-PERM-001`（A 链根）+ `T-PERM-002`（AOP afterCommit）+ `T-PERM-003`（A 链删version根）+ `T-PERM-004/005`（删 increment / 缓存目录残留）+ `T-PERM-006`（A 链广播订阅）+ `T-PERM-007`（文档一致性核对）+ `T-PERM-009`（B 链根）+ `T-PERM-011`（scopeMode 契约全量推广）+ `T-PERM-012`（管理端/排查页响应改造）+ `T-PERM-013`（协议层 scopeAll→scopeMode 映射）+ `T-PERM-014`（scopeMode 正式定义收尾）+ `T-PERM-015`（前端 ScopeMode 类型 + composable）均已完成。`T-PERM-008` 仍等待 T-GW-005 设计。

## 依赖告警（dangling）

> 当被依赖的任务 `cancelled` 或设计被 `superseded` 时，下游任务在此登记，等待重连。

_（暂无）_

## 设计变更待核对

> 当设计文件 `status` 变为 `superseded` 或章节实质变更时，`design_refs` 指向它的任务在此登记，等待核对验收与回写目标是否仍成立。

_（暂无）_

## 已完成（done，待计划归档时清理）

### user-role-proxy-fix（已归档 2026-06-20）

`T-ADMIN-001~016`（M1-M13 + S1-S3）：用户角色代理修复，全部 ✅ done + ✓ 回写。计划已归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。验收：代码级核验 + 247 tests 0 failures；设计回写：M2 补 api-contract、M13 补 admin-permission-sync §11.1，其余经核对已涵盖。

| ID 区间 | 内容 |
|---|---|
| T-ADMIN-001~004 | M1-M4 DTO 放宽 + 跨字段校验 + 门禁码 ROLE:MANAGE |
| T-ADMIN-005~007 | M5-M7 业务键导向 + 删 parseRoleId + 透传 validFrom/To |
| T-ADMIN-008~010 | M8-M10 UserOrgKeys helper 收敛 relationKey |
| T-ADMIN-011~012 | M11-M12 OrgVisibilityService 可见性裁剪 |
| T-ADMIN-013 | M13 user_role 孤儿延迟补偿 |
| T-ADMIN-014~016 | S1-S3 前端 perm 串 + 种子核实 + 契约测试 |

### user-role-proxy-fix-round2（已归档 2026-06-20）

第二轮审查 4 项 P1/P2 修复，全部 ✅ done + ✓ 回写。计划归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。测试：247 tests 0 failures。

| ID | 内容 |
|---|---|
| T-ADMIN-017 | P1-1 删除 assign/revoke 重复 ROLE:MANAGE 预检（交 perm 兜底）|
| T-ADMIN-018 | P1-2 getUser 加组织可见性裁剪 |
| T-PERM-016 | P2-1 UserRolesResp 增 relationExternalId + getUserRoles 批量解析 |
| T-ADMIN-019 | P2-2 deleteUser 批量 orgMap 消除 N+1 |

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
