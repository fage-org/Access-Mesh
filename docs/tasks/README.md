# 任务看板（Task Board）

本目录是 AccessMesh 任务的**唯一权威清单**。任务为原子执行单元，归属某个 [计划](../plans/)，并声明将改动的 [设计](../design/) 章节。

> 治理规则见 skill：`.claude/skills/design-plan-task-lifecycle/SKILL.md`。任务 ID 格式 `T-<DOMAIN>-<NNN>`，各领域独立递增、ID 冻结不回收。

## 领域计数器

| 领域 | 前缀 | 下一编号 |
|---|---|---|
| access-service 归并（跨服务） | `T-ACCESS` | 015 |
| permission-center | `T-PERM` | 042 |
| admin-service | `T-ADMIN` | 022 |
| gateway | `T-GW` | 007 |
| 组织/用户（跨 admin+perm） | `T-ORG` | 001 |
| 跨服务 API 契约 | `T-API` | 001 |
| 前端 | `T-FE` | 041 |

> 新建任务时从对应领域取下一编号，计数器 +1。

## 任务总表

> 状态简写：⚙️=proposed / 🔨=in-progress / 👀=review / ✅=done / ❌=cancelled。回写：⏳=pending / ✓=done。

> **当前后端前置门禁（2026-08-12）**：`T-ACCESS-001~012` 完成前，尚未开始的 `T-PERM-*` / `T-ADMIN-*` 后端功能任务保持 proposed，不得在旧模块上进入 `in-progress`。T-ACCESS-012 完成逐卡重基线后，Phase 2 后端任务才能按各自依赖推进；前端真接口联调再等待对应 Phase 2 后端任务完成。纯前端 mock/UI 工作可继续。

### access-service 归并（当前后端前置）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ACCESS-001](T-ACCESS-001.md) | 建立 access-service 工程骨架并物理归并源码 | [access-service-merge](../plans/access-service-merge-plan.md) | design/access-service-architecture.md §2/§3/§9；project-rules | — | ✅ | ✓ |
| [T-ACCESS-002](T-ACCESS-002.md) | 建立 access_db 最终 DDL 并收敛持久层模型 | access-service-merge | access-service-architecture §4/§5/§8.1；schema/access-service.sql（本任务产出） | T-ACCESS-001 | ✅ | ✓ |
| [T-ACCESS-003](T-ACCESS-003.md) | 收敛单数据源、MyBatis、Redis、JSON等运行基础配置 | access-service-merge | access-service-architecture §2/§6/§7.1；project-rules；gateway | T-ACCESS-001, T-ACCESS-002 | ✅ | ✓ |
| [T-ACCESS-004](T-ACCESS-004.md) | 实现可信请求上下文和统一安全策略矩阵 | access-service-merge | access-service-architecture §6；admin/permission API 契约；gateway | T-ACCESS-003 | ✅ | ✓ |
| [T-ACCESS-005](T-ACCESS-005.md) | 实现强事务权限投影并删除内部同步子系统 | access-service-merge | access-service-architecture §3/§4；permission core-flows；用户生命周期；admin-service-api-contract §3/§4/§6/§7 | T-ACCESS-002, T-ACCESS-004 | ✅ | ✓ |
| [T-ACCESS-006](T-ACCESS-006.md) | 建立跨域只读查询模型 | access-service-merge | access-service-architecture §3；org-user/permission 契约 | T-ACCESS-002, T-ACCESS-005 | ✅ | ✓ |
| [T-ACCESS-007](T-ACCESS-007.md) | 合并系统配置与操作审计并落实日志事务分级 | access-service-merge | access-service-architecture §5.2/§8.2；project-rules | T-ACCESS-002, T-ACCESS-004 | ✅ | ✓ |
| [T-ACCESS-008](T-ACCESS-008.md) | 统一缓存并实现多实例失效及30秒安全边界 | access-service-merge | access-service-architecture §7；project-rules §12；v3.5 §7.2；gateway | T-ACCESS-003, T-ACCESS-005 | ✅ | ✓ |
| [T-ACCESS-009](T-ACCESS-009.md) | 建立数据库任务租约、幂等和异步执行治理 | access-service-merge | access-service-architecture §8；admin-service | T-ACCESS-002, T-ACCESS-004 | ✅ | ✓ |
| [T-ACCESS-010](T-ACCESS-010.md) | 切换 Gateway、SDK、Nacos和部署配置 | access-service-merge | access-service-architecture §2/§9；architecture；gateway | T-ACCESS-004, T-ACCESS-005, T-ACCESS-008 | ⚙️ | ⏳ |
| [T-ACCESS-011](T-ACCESS-011.md) | 完成契约、回滚、架构、空库和双实例验收 | access-service-merge | access-service-architecture §10；项目与 API 契约；gateway | T-ACCESS-006, T-ACCESS-007, T-ACCESS-009, T-ACCESS-010 | ⚙️ | ⏳ |
| [T-ACCESS-012](T-ACCESS-012.md) | 删除残留引用、回写设计并重基线任务看板 | access-service-merge | access-service-architecture；architecture；project-rules；服务/API/权限设计；旧 schema；文档索引 | T-ACCESS-011 | ⚙️ | ⏳ |
| [T-ACCESS-013](T-ACCESS-013.md) | OAuth2 资源服务器与 scope 授权模型（委托令牌访问业务 API 显式开放） | access-service-merge | access-service-architecture §6；admin-service-api-contract | T-ACCESS-012 | ⚙️ | ⏳ |
| [T-ACCESS-014](T-ACCESS-014.md) | admin/application 域 AppService 操作日志强制覆盖 | access-service-merge | access-service-architecture §8.2；project-rules | T-ACCESS-007 | ⚙️ | ⏳ |

### permission-center（工作单 A 缓存失效 + 工作单 B scopeMode + 工作单 D/E/F 待确认 + 前端 Phase 1/2/4 后端任务）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-PERM-001](T-PERM-001.md) | Gateway 缓存改快照模式（user → InterfaceSnapshot） | [perm-cache-invalidation](../archive/2026-06-28/perm-cache-invalidation-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ✅ | ✓ |
| T-PERM-002 | PermissionChangeContext ThreadLocal + AppService AOP afterCommit | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2 | T-PERM-001 | ✅ | ✓ |
| T-PERM-003 | 删除 permission_version 表+实体+Service+Mapper+Controller+DTO（存量环境 DROP TABLE 为外部 DBA/运维动作，仓库无 migration 框架） | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2；design/permission-center/overview.md；implementation.md §5.1/5.2 | — | ✅ | ✓ |
| T-PERM-004 | 删除 4 处 permissionVersionDomainService.increment 调用 | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ✅ | ✓ |
| T-PERM-005 | 删除缓存目录 PermCacheCatalog.PERMISSION_VERSION + key 后缀 :{permissionVersion} | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ✅ | ✓ |
| [T-PERM-006](T-PERM-006.md) | Gateway 订阅 perm:invalidate topic，按 tenant+serviceCodes/userIds evict 本地 INTERFACE_SNAPSHOT（roleIds-only 事件按租户级安全清理） | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | T-PERM-018 | ✅ | ✓ |
| T-PERM-007 | 同步修订 overview/core-flows/implementation/api-contract/coding-standards §5（代码层一致性核对） | perm-cache-invalidation | design/permission-center/{overview,core-flows,implementation,api-contract}.md | T-PERM-003 | ✅ | ✓ |
| T-PERM-008 | Gateway 失效标记与订阅恢复策略（S-006 已设计，规范见 gateway.md §快照失效标记与订阅恢复） | perm-cache-invalidation | design/services/gateway.md §快照失效标记与订阅恢复 | T-GW-005（S-006 设计 ✅）| ✅ | ✓ |
| T-PERM-009 | scopeMode 4 态枚举(DENIED/INSTANCE/ALL/EMPTY) + QueryScopesResp 分类模型重构(按 resourceType×operation 分桶) | [scope-mode-migration](../archive/2026-06-28/scope-mode-migration-plan.md) | design/permission-center-v3.5-design.md §3 | T-PERM-003 | ✅ | ✓ |
| T-PERM-010 | api-contract.md §6.7 query-scopes 响应改造（scopeAll → scopeMode）— 范围已合并进 T-PERM-009 完成（§6.7 已回写 scopeMode 四态） | scope-mode-migration | design/permission-center/api-contract.md §6.7 | T-PERM-009 | ✅ | ✓ |
| T-PERM-011 | api-contract.md §6.4-6.10 / §10 第 8 条等约 30+ 处 scopeAll 全量推广到 scopeMode | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ✅ | ✓ |
| T-PERM-012 | 管理端授权配置/排查页响应改造（role-resource-permission save/grant、permission-view） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ✅ | ✓ |
| T-PERM-013 | schema scope_all 字段保留（仅内部存储），协议层映射逻辑实现 | scope-mode-migration | design/schema/permission-center.sql | T-PERM-009 | ✅ | ✓ |
| T-PERM-014 | 同步修订 api-contract.md 顶部 scopeMode 迁移注记（移除注记改为正式定义） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-010, T-PERM-011 | ✅ | ✓ |
| T-PERM-015 | 前端 ScopeMode 类型定义 + composable（hasPerms/Perms 不涉及 L2 数据权限，无需改造） | scope-mode-migration | design/permission-center-v3.5-design.md §3 | T-PERM-009 | ✅ | ✓ |
| [T-PERM-017](T-PERM-017.md) | 条件权限 Gateway 侧重评（部分下发 gateway_evaluable + 未下发回退 check-interface） | perm-cache-invalidation | design/services/gateway.md；v3.5 §7.2 | T-PERM-002, T-PERM-018 | ✅ | ✓ |
| [T-PERM-018](T-PERM-018.md) | 缓存下沉——移除 INTERFACE_SNAPSHOT(L2)/permissionVersion，激活 ROLE_PERM_SNAPSHOT engine 读缓存，扩展失效事件 serviceCodes | perm-cache-invalidation | v3.5 §5.1/§7.2；api-contract §6.x | T-PERM-002 | ✅ | ✓ |
| [T-PERM-019](T-PERM-019.md) | 工作单 D：防呆机制（type_value 自动分配、业务键封装、AppliesTo、SyncHandler 版本声明） | [design-review-def-followup](../plans/design-review-def-followup-plan.md) | design-review §11；api-contract；core-flows；implementation；schema；admin sync | — | ⚙️ | ⏳ |
| [T-PERM-020](T-PERM-020.md) | 工作单 E：清理预设能力（domain_config 旧配置、PermQuery 工厂、RocketMQ 脚注、auto-grant TODO；含冲突标记） | [design-review-def-followup](../plans/design-review-def-followup-plan.md) | design-review §11；api-contract；core-flows；implementation；schema；architecture | — | ⚙️ | ⏳ |
| [T-PERM-021](T-PERM-021.md) | 工作单 F：文档准确性与代码简化（指标自动化、DTO 单源、ownership、日志链路、full-sync runbook；含冲突标记） | [design-review-def-followup](../plans/design-review-def-followup-plan.md) | design-review §11；api-contract；implementation；schema；admin sync；project-rules | — | ⚙️ | ⏳ |
| T-PERM-022 | 2.2 角色管理后端（见任务卡） | [frontend-phase2](../plans/frontend-phase2-plan.md) | api-contract §5.2/§6.10.3；implementation §2.1；design/frontend/role-manage.md §8 | T-FE-002 | ⚙️ | ⏳ |
| T-PERM-023 | 6.1 类型定义后端（见任务卡） | frontend-phase2 | api-contract §5.1；implementation §2.6；design/frontend/type-definition.md §8 | T-FE-003 | ⚙️ | ⏳ |
| T-PERM-024 | 6.2 系统配置后端（见任务卡） | frontend-phase2 | api-contract §5.8 | T-FE-004 | ⚙️ | ⏳ |
| T-PERM-025 | 7.1 操作日志后端（见任务卡） | frontend-phase2 | api-contract §5.8/§6.10.6；implementation §2.3 | T-FE-005 | ⚙️ | ⏳ |
| T-PERM-026 | 5.1 业务域后端（见任务卡） | frontend-phase2 | api-contract §5.1/§5.6；implementation §2.7 | T-FE-006 | ⚙️ | ⏳ |
| T-PERM-027 | 5.2 服务+接口映射后端（见任务卡） | frontend-phase2 | api-contract §5.4/§6.3/§6.10.4；design/frontend/service-interface-mapping.md §7 | T-FE-007 | ⚙️ | ⏳ |
| T-PERM-028 | 3.1 资源+操作定义后端（resource-entity/* + operation-permission/*；🔧detail/update/move/remove 切业务键[resource: resourceTypeCode+code+codeType；operation: resourceTypeCode+code，schema uk_resource_entity/uk_operation_permission_typed 已保证唯一]；🔧RESOURCE:VIEW/OPERATION:VIEW 种子缺失[resource-entity list/tree 与 operation-permission list 未见 VIEW 校验，schema 无 INSERT 预置操作位，联调全账号 403]；🔧bigint 字段 JSON 序列化为 string[operation-permission.binaryBit/inheritMask 为 63 位 bigint 列，Jackson 默认序列化为 number 致前端 JSON.parse 在 >2^53 丢精度；DTO 加 @JsonSerialize(ToStringSerializer.class) 或改 String 类型，前端切 BigInt 全链路+el-input 文本输入；T-FE-008 已用 BigInt 运算解决 32 位截断（2^53 内精确），63 位彻底方案作为全项目 bigint 序列化策略首例；🔧update 可选字段 extra 支持 null 清空语义[现 if(extra!=null) 不更新致前端 form.extra||null 清空传 null 被当不更新；extra JSONB 列不可用空串清空，需后端区分不更新vs清空为null]） | frontend-phase2 | api-contract §5.3/§6.2.2；implementation §2.9；design/frontend/resource-operation.md §8 | T-FE-008 | ⚙️ | ⏳ |
| T-PERM-029 | 3.2 权限条件后端（见任务卡） | frontend-phase2 | api-contract §5.6；implementation §2.5 | T-FE-009 | ⚙️ | ⏳ |
| T-PERM-030 | 3.3 冲突规则后端（见任务卡） | frontend-phase2 | api-contract §5.6；implementation §2.4；design/frontend/conflict-rule.md §4 | T-FE-010 | ⚙️ | ⏳ |
| T-PERM-031 | 3.4 资源依赖后端（见任务卡） | frontend-phase2 | api-contract §5.6/§6.9；core-flows §12 | T-FE-011 | ⚙️ | ⏳ |
| T-PERM-032 | 7.2 变更日志后端（见任务卡） | frontend-phase2 | api-contract §5.8/§6.8；implementation §2.3 | T-FE-012 | ⚙️ | ⏳ |
| T-PERM-033 | 4.2 权限查询后端（见任务卡） | frontend-phase2 | api-contract §5.7/§6.6/§6.7/§6.8；implementation §3/§7.5 | T-FE-013 | ⚙️ | ⏳ |
| [T-PERM-034](T-PERM-034.md) | 4.1 权限授予后端（见任务卡） | frontend-phase2 | api-contract §5.5/§6.4/§6.5/§6.5.1/**§6.5.2**；implementation §4/§7.7；core-flows §6；permission-grant.md §12；permission-center.sql | T-PERM-031 | ⚙️ | ⏳ |
| [T-PERM-035](T-PERM-035.md) | 自动授权（resolveAutoGrants + autoGrantForInsert + 循环依赖检测）— ⚠️ design-review §11 E4 暂缓未排期 | [frontend-phase2](../plans/frontend-phase2-plan.md) | core-flows §12；implementation §4；api-contract | T-PERM-034 | ⚙️ | ⏳ |
| [T-PERM-036](T-PERM-036.md) | 动态数据权限端到端验证（scopeMode → SQL 映射链路）— ⚠️ design-review §11 Q7/B 暂缓（延后 example-service） | frontend-phase2 | api-contract §6.7；core-flows；implementation | T-FE-013, T-PERM-033 | ⚙️ | ⏳ |
| [T-PERM-037](T-PERM-037.md) | 跨页共性接口改造 + api-contract 回写收尾 | frontend-phase2 | api-contract；implementation | T-PERM-022~034 | ⚙️ | ⏳ |
| T-PERM-038 | 全局 TODO 收口（improvement-plan 附录 A） | [frontend-phase4](../plans/frontend-phase4-plan.md) | architecture；implementation | — | ⚙️ | ⏳ |
| T-PERM-039 | 测试补充（permission-center 新增改造接口测试） | frontend-phase4 | testing-standards | T-PERM-037 | ⚙️ | ⏳ |
| [T-PERM-040](T-PERM-040.md) | 4.1 权限授予单资源类型后端支持 | [frontend-phase2](../plans/frontend-phase2-plan.md) | | T-PERM-028, T-PERM-034 | ⚙️ | ⏳ |
| [T-PERM-041](T-PERM-041.md) | 主权限条件不变量（20041 不可转授 + 20042 启用状态） | [frontend-phase2](../plans/frontend-phase2-plan.md) | | T-PERM-034 | ⚙️ | ⏳ |

### gateway（工作单 C 失联兜底）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| T-GW-001 | gateway.permission.fail-mode 配置项（closed/open/stale-allow，默认 closed）+ stale-grace-seconds | [gateway-fail-mode](../archive/2026-06-28/gateway-fail-mode-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ✅ | ✓ |
| T-GW-002 | fail-closed 实现：perm-center 不可达 → 403/503 拒绝 | gateway-fail-mode | design/services/gateway.md | T-GW-001 | ✅ | ✓ |
| [T-GW-003](T-GW-003.md) | stale-allow 实现：用过期未驱逐快照续命，超 stale-grace-seconds 转 closed | gateway-fail-mode | design/services/gateway.md | T-PERM-001（快照模式）, T-GW-001 | ✅ | ✓ |
| T-GW-004 | 监控指标：unreachable.count / fallback.{closed,open,stale}.count + WARN + Prometheus 告警 | gateway-fail-mode | design/services/gateway.md | T-GW-002 | ✅ | ✓ |
| T-GW-005 | 失效标记与订阅恢复策略设计（S-006 规范产出，已完成） | gateway-fail-mode | design/services/gateway.md §快照失效标记与订阅恢复 | T-PERM-006（广播事件载荷）| ✅ | ✓ |
| [T-GW-006](T-GW-006.md) | 集成测试基线："杀 permission-center → Gateway 应 503"（重新界定：不在项目内做集成测试，改为独立仓库测试服务） | gateway-fail-mode | — | T-GW-002 | ✅ | ✓ |

> 注：T-PERM-008（代码侧 Gateway 失效标记）依赖 T-GW-005（设计侧 S-006 规范）产出，二者构成"设计先行 → 代码落地"链。

### admin-service

_当前活跃 T-ADMIN 任务：`T-ADMIN-020/021`（见下表）。`T-ADMIN-001~019`（用户角色代理修复）已全部完成并归档，见下方"已完成"区。_

> EXT-7（PermissionCheckAppServiceImpl.batchCheck 逐条循环）/ EXT-8（SyncTaskDomainServiceImpl.enqueueAll 逐条 insert）为 DEFERRED 无主项（审计 S-024），未纳入本批任务，待单独立项。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| T-ADMIN-020 | admin-service CRUD 代码清理（痛点 #6，低优先级） | [frontend-phase4](../plans/frontend-phase4-plan.md) | architecture；services/admin-service.md | — | ⚙️ | ⏳ |
| T-ADMIN-021 | org-tree 扩展 includePositions（组织+岗位一体树，授权页主体树数据源；P2-3，2026-08-01 立项） | [frontend-phase2](../plans/frontend-phase2-plan.md) | design/frontend/permission-grant.md §9；services/admin-service.md | —（联调任务 T-FE-037 汇集（二期）） | ⚙️ | ⏳ |

### 前端（前端 Phase 1/3/4 拆分）

> 来源：`docs/plans/improvement-plan.md` §4 各 Phase 拆分。Phase 1 archived（2026-07-12 归档），Phase 3/4 proposed。页面任务 design_refs 先指后端契约，UI 设计随任务回写到 `docs/design/frontend/<page>.md`。

> ⚠️ **权限授予页 v1/v2 产物废弃（2026-07-26）；T-FE-018 已于 2026-08-01 按 v3 恢复待排期**：因对现有交互不满意，v1（`permission-grant`）+ v2（`permission-grant-v2`）两套页面及专属代码（`PermissionSummaryCell` / `ChildPermissionInline` / `RePermissionCell` / `ReConditionPicker` / `permission-grant-types` / `api/permission-grant` / `mock/permission-grant`）已删除，4 份设计文档归档至 `archive/2026-07-26/`，两个 plan 归档至 `plans/archive/2026-07/`。下表 T-FE-014 / T-FE-024~026 / T-FE-029~034 保持 ✅（历史完成事实）但产出代码已废弃；T-FE-027 / T-FE-028 / T-FE-035 标 ❌ cancelled（不再恢复）；**T-FE-018 已恢复 ⚙️ 待排期（2026-08-01，v3 设计 `design/frontend/permission-grant.md`，依赖见表格行）**。注：`ReConditionEditor` / `condition-rules` 保留，仍被 `permission-condition` 页使用。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-FE-001](T-FE-001.md) | 跨页组件抽象池（清单维护 + 派生子任务） | [frontend-phase1](../plans/frontend-phase1-plan.md) | design/frontend/README.md | — | ✅ | — |
| T-FE-002 | 2.2 角色管理页（5 种角色类型 CRUD，本页仅消费功能角色） | frontend-phase1 | api-contract §5.2/§6.10.3；design/frontend/role-manage.md | T-FE-001 | ✅ | ✓ |
| T-FE-003 | 6.1 类型定义页（type_definition code↔value 映射 CRUD） | frontend-phase1 | api-contract §5.1；design/frontend/type-definition.md | — | ✅ | ✓ |
| T-FE-004 | 6.2 系统配置页（租户级 key-value 配置字典；任务原标题「分组表单」校正——后端/schema 无 config_group 字段，为扁平键值表，仅 list/detail/save 3 端点，save upsert 幂等无删除） | frontend-phase1 | api-contract §5.8；design/frontend/system-config.md | — | ✅ | ✓ |
| T-FE-005 | 7.1 操作日志页（只读查询：module/action 服务端分页筛选 + 抽屉详情；后端无 detail 接口，OperationLogResp 已含全字段；复用 SYSTEM_CONFIG:VIEW 门禁，无独立 OPERATION_LOG 权限码；路径对齐后端实际 /api/perm/log/operation/list 而非契约 §5.8 写错的 /api/perm/operation-log/list） | frontend-phase1 | api-contract §5.8；design/frontend/operation-log.md | — | ✅ | ✓ |
| T-FE-006 | 5.1 业务域页（主从：BizDomain CRUD 主表 + DomainConfig 域配置子表含 CLASSIFY；biz-domain list/detail 门禁 DOMAIN:VIEW 独立资源类型新增矩阵；domain-config 子区门禁 SYSTEM_CONFIG:VIEW/MANAGE；configType 列全 5 种 SCOPE/RELATION/BINDING/SUB_PERM/CLASSIFY） | frontend-phase1 | api-contract §5.1/§5.6；design/frontend/biz-domain.md | — | ✅ | ✓ |
| T-FE-007 | 5.2 服务+接口映射页（服务注册 + 接口同步 + API 映射；FULL 同步边界 + SERVICE 四类 perm 门控 + mock 交互；API 🔧 清单已登记 T-PERM-027） | frontend-phase1 | api-contract §5.4/§6.3/§6.10.4；design/frontend/service-interface-mapping.md | — | ✅ | ✓ |
| T-FE-008 | 3.1 资源+操作定义页（资源树 CRUD + 操作定义 + 关联） | frontend-phase1 | api-contract §5.3；design/frontend/resource-operation.md | — | ✅ | ✓ |
| T-FE-009 | 3.2 权限条件页（通用条件模板 CRUD；conditionRules 可视化编辑器 {logic,items[]} 4 类型 DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST + gatewayEvaluable 开关 T-PERM-017；~~CONDITION:VIEW~~ **设计确认移除（读取全租户开放，2026-08-08 产品确认），待 v3.1 实施任务落地（当前实现仍保留路由/loadList/canCondition 门禁）** → CREATE/UPDATE/DELETE 三档独立非 MANAGE 对齐后端；后端 list 无分页前端本地过滤；🔧 清单 6 项登记 T-PERM-029） | frontend-phase1 | api-contract §5.6；design/frontend/permission-condition.md | - | ✅ | ✓ |
| [T-FE-010](T-FE-010.md) | 3.3 冲突规则页（ROLE_MUTEX 角色互斥/PERM_MUTEX 权限互斥 CRUD + 冲突检测对话框仅操作权限对双向匹配；表格名称映射加载 role/operation/type-def；CONFLICT_RULE:VIEW/CREATE/UPDATE/DELETE 三档独立非 MANAGE 对齐后端；🔧 清单 6 项登记 T-PERM-030） | frontend-phase1 | api-contract §5.6；design/frontend/conflict-rule.md | T-FE-001 | ✅ | ✓ |
| [T-FE-011](T-FE-011.md) | 3.4 资源依赖页（依赖 CRUD + 依赖图 echarts graph + 环检测；资源类型下拉+资源下拉联动；bits->操作码位运算拆解+id->资源映射应对 Resp 字段不全；编辑资源对可改全量替换 Q3=B；DEPENDENCY:VIEW/CREATE/UPDATE/DELETE 三档独立非 MANAGE 对齐后端+SYNC(batch-sync P0 标 TODO Q5=B)；🔧 清单 8 项登记 T-PERM-031） | frontend-phase1 | api-contract §5.6/§6.9；design/frontend/resource-dependency.md | T-FE-001, T-FE-008 | ✅ | ✓ |
| [T-FE-012](T-FE-012.md) | 7.2 权限变更日志页（diff 快照 + before/after + 影响评估） | frontend-phase1 | api-contract §5.8/§6.8；design/frontend/permission-change-log.md | T-FE-001 | ✅ | ✅ |
| [T-FE-013](T-FE-013.md) | 4.2 权限查询/校验页（多维度查询 + 权限解释 + 拒绝原因） | frontend-phase1 | api-contract §5.7/§6.6/§6.7/§6.8；design/frontend/permission-query.md | T-FE-001 | ✅ | ✅ |
| [T-FE-014](T-FE-014.md) | 4.1 权限授予页（选角色→勾资源树→操作矩阵→绑条件→批量保存） | frontend-phase1 | api-contract §5.5/§6.4/§6.5；design/frontend/permission-grant.md | T-FE-001, T-FE-002, T-FE-008 | ✅ | ✅ |
| T-FE-015 | Phase 3 联调：组织与用户（2.1 mock→真实接口） | [frontend-phase3](../plans/frontend-phase3-plan.md) | api-contract；admin-service-api-contract | T-PERM-037 | ⚙️ | ⏳ |
| T-FE-016 | Phase 3 联调：角色管理（2.2） | frontend-phase3 | api-contract | T-FE-002, T-PERM-022 | ⚙️ | ⏳ |
| T-FE-017 | Phase 3 联调：资源/操作定义（3.1） | frontend-phase3 | api-contract | T-FE-008, T-PERM-028 | ⚙️ | ⏳ |
| [T-FE-018](T-FE-018.md) | Phase 3 联调：权限授予（4.1）- 角色联调（首期） | [frontend-phase3](../plans/frontend-phase3-plan.md) | | T-FE-036, T-FE-038, T-FE-039, **T-FE-040**, T-PERM-040, T-PERM-041, T-PERM-034, T-PERM-022/028/029/031 | ⚙️ | ⏳ |
| [T-FE-037](T-FE-037.md) | Phase 3 联调：权限授予（4.1）- 组织联调（二期） | [frontend-phase3](../plans/frontend-phase3-plan.md) | api-contract；design/frontend/permission-grant.md（v3） | T-FE-018, T-ADMIN-021 | ⚙️ | ⏳ |
| T-FE-019 | Phase 3 联调：权限查询/校验（4.2） | frontend-phase3 | api-contract | T-FE-013, T-PERM-033 | ⚙️ | ⏳ |
| T-FE-020 | Phase 3 联调：条件/冲突规则（3.2/3.3） | frontend-phase3 | api-contract | T-FE-009, T-FE-010, T-PERM-029, T-PERM-030 | ⚙️ | ⏳ |
| T-FE-021 | Phase 3 联调：业务域配置（5.1） | frontend-phase3 | api-contract | T-FE-006, T-PERM-026 | ⚙️ | ⏳ |
| T-FE-022 | Phase 3 联调：系统/服务配置（6.x/5.2） | frontend-phase3 | api-contract | T-FE-003, T-FE-004, T-FE-007, T-PERM-023, T-PERM-024, T-PERM-027 | ⚙️ | ⏳ |
| T-FE-023 | Phase 4：SPI 策略扩展验证 + 扩展指南（design/frontend/extension-guide.md） | [frontend-phase4](../plans/frontend-phase4-plan.md) | architecture；design/frontend/extension-guide.md | — | ⚙️ | ⏳ |
| [T-FE-024](T-FE-024.md) | ReConditionPicker + ReConditionEditor + ChildPermissionInline 条件/子权限组件抽取 | [frontend-phase4](../plans/frontend-phase4-plan.md) | design/frontend/permission-condition.md；permission-grant.md | T-FE-001, T-FE-009, T-FE-014 | ⚙️ | ✅ |
| [T-FE-025](T-FE-025.md) | 权限授予中栏资源权限概览与授权入口 | [permission-grant-ux-refactor](../plans/archive/2026-07/permission-grant-ux-refactor-plan.md) | design/frontend/permission-grant.md §16.3/§16.8 | T-FE-014 | ⚙️ | ✅ |
| [T-FE-026](T-FE-026.md) | 权限授予授权弹窗（批量授权任务） | [permission-grant-ux-refactor](../plans/archive/2026-07/permission-grant-ux-refactor-plan.md) | design/frontend/permission-grant.md §16.4/§16.8 | T-FE-024, T-FE-025 | ✅ | ✅ |
| [T-FE-028](T-FE-028.md) | 权限授予右栏本次变更记录 | [permission-grant-ux-refactor](../plans/archive/2026-07/permission-grant-ux-refactor-plan.md) | ~~permission-grant.md §16.5/§16.8~~（已删） | T-FE-026 | ❌ | ⏳ |
| [T-FE-029](T-FE-029.md) | 权限授予V2页面骨架+路由+三栏+角色树+能力门控 | [permission-grant-v2](../plans/archive/2026-07/permission-grant-v2-plan.md) | design/frontend/permission-grant-{state-model,interaction}.md | - | ✅ | ⏳ |
| [T-FE-030](T-FE-030.md) | 方案A前端模型（GrantVariantId+replay+聚合摘要） | permission-grant-v2 | design/frontend/permission-grant-state-model.md §0/§1/§2.2/§7.1 | T-FE-029 | ✅ | ⏳ |
| [T-FE-031](T-FE-031.md) | V2中栏直接操作矩阵+单元格聚合摘要+分支列表就地展开 | permission-grant-v2 | design/frontend/permission-grant-interaction.md §2/§4.1/§4.2；state-model §6.2 | T-FE-030 | ✅ | ⏳ |
| [T-FE-032](T-FE-032.md) | V2授权交互（点击/添加分支/逐分支编辑撤销/批量新增分支）+R11 | permission-grant-v2 | design/frontend/permission-grant-{interaction,state-model}.md §3.4/§4.1/§4.3 | T-FE-031 | ✅ | ✅ |
| [T-FE-033](T-FE-033.md) | V2子权限矩阵展开（parentVariantId）+两步保存+条件清除wire | permission-grant-v2 | design/frontend/permission-grant-{state-model,interaction,error-flow}.md §4.2 | T-FE-032 | ✅ | ✅ |
| [T-FE-034](T-FE-034.md) | V2保存前总览+失败两子态+STALE_WITH_CHILD_FAILURE+fetchBaseline+离开保护 | permission-grant-v2 | design/frontend/permission-grant-{state-model,error-flow,interaction}.md §2/§2.5/§4.5 | T-FE-033 | ✅ | ✅ |
| [T-FE-035](T-FE-035.md) | 扩展V2 transport（多条件+失败模拟）+失格降级+回归验证+设计回写 | [permission-grant-v2](../plans/archive/2026-07/permission-grant-v2-plan.md) | design/frontend/permission-grant-{error-flow,state-model}.md §2.8/§8 | T-FE-034 | ❌ | ⏳ |
| [T-FE-027](T-FE-027.md) | 权限授予三栏状态整合、回归验证与设计回写 | [permission-grant-ux-refactor](../plans/archive/2026-07/permission-grant-ux-refactor-plan.md) | ~~permission-grant.md §16.6~§16.9~~（已删） | T-FE-025, T-FE-026, T-FE-028 | ❌ | ⏳ |
| [T-FE-036](T-FE-036.md) | 4.1 权限授予页重设计（v3：查看为主+操作中心授权弹窗+详情层+变更清单；范围：状态机四态/GoldenFixture 6 用例/Step3 多选/组织入口二期；DoD：api-contract 对齐/引擎 fixtures 比对/四态状态机/交互先行） | [frontend-phase2](../plans/frontend-phase2-plan.md) | design/frontend/permission-grant.md（v3）；api-contract §5.5/§6.4/§6.5/§6.5.1 | T-FE-001, T-FE-002, T-FE-008, T-FE-009 | ✅ | ✓ |
| [T-FE-038](T-FE-038.md) | 4.1 权限授予页单类型矩阵上下文 | [frontend-phase2](../plans/frontend-phase2-plan.md) | design/frontend/permission-grant.md §2.2/§3.1/§3.2/§3.5/§3.6/§11/§13.2/§13.4；api-contract §5.1/§5.3/§6.4 | T-FE-036 | ✅ | ✅ |
| [T-FE-039](T-FE-039.md) | 4.1 矩阵图标正交状态模型与图标精简 | [frontend-phase2](../plans/frontend-phase2-plan.md) | design/frontend/permission-grant.md §3.3/§6.2/§11（S2/S5/S6/S10/S11）/§13.5；api-contract §6.5.1（20041 配套） | T-FE-038 | ✅ | ✅ |
| [T-FE-040](T-FE-040.md) | 4.1 授权弹窗 v3.1 记录级聚焦编辑（决策记录已确认：焦点生命周期/显式复制/停用条件/CONDITION:VIEW 移除/子权限记录级入口/节点摘要；mock-first） | [frontend-phase2](../plans/frontend-phase2-plan.md) | design/frontend/permission-grant.md（v3.1）；api-contract §6.5.1/§6.5.2；plans/permission-grant-record-level-editing-proposal.md | T-FE-039 | ✅ | ✓ |

---

## 建议执行顺序

依据：①评审定级（A/B/C 为 P0）②依赖解锁价值 ③验收闭环优先 ④无依赖可立即并行。

### 当前后端门禁 — access-service 归并（proposed）

1. `T-ACCESS-001 → T-ACCESS-002 → T-ACCESS-003`：建立单模块、单库和单运行基础设施。
2. `T-ACCESS-004` 后分流：`T-ACCESS-005/007/009`；`T-ACCESS-005` 完成后推进 `T-ACCESS-006/008`。
3. `T-ACCESS-010` 完成生态切换；`T-ACCESS-011` 汇总全量与双实例验收。
4. `T-ACCESS-012` 回写设计并逐卡重基线旧后端任务。

门禁期间，尚未开始的旧后端任务保持 proposed；T-ACCESS-012 重基线完成后 Phase 2 后端任务才可按依赖启动，前端真接口联调等待各自依赖的 Phase 2 后端任务完成；纯 mock/UI 任务不受影响。

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
8. `T-PERM-008` 失效标记代码 ← T-GW-005 ✅（已完成：stale store / InvalidationMarker / in-flight 去重 / 订阅恢复全清）

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
6. `T-GW-006` 集成测试 ← 002（重新界定：不在项目内做集成测试，改为独立仓库测试服务）
7. `T-PERM-008` 失效标记代码 ← T-GW-005（已完成，回到 A 链收尾）

### P4 — 工作单 D/E/F（proposed，执行前确认）

1. `T-PERM-019` 工作单 D 防呆机制：整体无硬冲突，但 `typeValue` 外部入参描述和软删不复用保证方式存在 `DESIGN_DRIFT`，需先确认并回写设计。
2. `T-PERM-020` 工作单 E 清理预设：`forValidate` / `forResourceCheck` 与当前管理校验和 `auth/query-resources` 设计冲突；RocketMQ 与 auto-grant 已被后续设计收敛；执行前必须确认 E2 替代设计。
3. `T-PERM-021` 工作单 F 文档准确性与代码简化：ownership 字段删除、`request_id NOT NULL` 均存在当前设计约束，且 `requestId`/`traceId` 语义需先收敛；执行前必须确认 F1.c/F1.d。

### P5 — 前端 Phase 1（archived，2026-07-12 归档）

> D/E/F 重启前提「前端 Phase 1 收尾」的关键路径。按 improvement-plan §4.1 三批次（简单→复杂）推进。Phase 1 **不改后端**，各页前端任务在 API 核对中产出 🔧❌ 清单，登记为 Phase 2 后端任务 T-PERM-022~034。

**第 1 批 🟢🟡**：`T-FE-002` 角色管理 → `T-FE-003` 类型定义 → `T-FE-004` 系统配置 → `T-FE-005` 操作日志
**第 2 批 🟡**：`T-FE-006` 业务域 → `T-FE-007` 服务+接口 → `T-FE-008` 资源+操作 → `T-FE-009` 权限条件
**第 3 批 🔴**：`T-FE-010` 冲突规则 → `T-FE-011` 资源依赖 → `T-FE-012` 变更日志 → `T-FE-013` 权限查询 → `T-FE-014` 权限授予

配套：`T-FE-001` 跨页组件抽象池（贯穿，2+ 页确认后派生组件子任务）。后端任务 T-PERM-022~034 归 Phase 2（depends_on 对应前端，等清单产出）。

### P6 — 前端 Phase 2/3/4（proposed，待 Phase 2 启动）

- `T-PERM-022~034` 逐页后端接口改造 ← 各 Phase 1 前端任务（🔧❌ 清单）
- `T-PERM-037` 跨页共性接口改造 + api-contract 回写收尾 ← T-PERM-022~034（不重复逐页改造）
- `T-PERM-035` 自动授权 / `T-PERM-036` 动态数据权限 — ⚠️ design-review §11 暂缓，需 PM 重申
- `T-FE-015~022` Phase 3 联调 ← Phase 1 + Phase 2
- `T-FE-023` 扩展验证 / `T-ADMIN-020` 代码清理 / `T-PERM-038` TODO 收口 / `T-PERM-039` 测试补充 — Phase 4

### P7 — 权限授予授权弹窗与右栏变更重构（❌ 已取消 2026-07-26）

> 页面交互不满意，v1+v2 两套整体删除重做。T-FE-024~026 保持 done（产出已废），T-FE-027/028 cancelled；**T-FE-018 已于 2026-08-01 按 v3 恢复待排期（不再 cancelled，见表格行）**。详见上方"前端"段废弃说明。plan 已归档至 `plans/archive/2026-07/permission-grant-ux-refactor-plan.md`。

1. `T-FE-024` 条件/子权限组件抽取（含 ChildPermissionDrawer 内联化）← T-FE-014（外部前置，可与 T-FE-025 并行）
2. `T-FE-025` 中栏资源权限概览 + 授权入口 ← T-FE-014
3. `T-FE-026` 授权弹窗（批量授权任务）← T-FE-024, T-FE-025
4. `T-FE-028` 右栏本次变更记录 ← T-FE-026
5. `T-FE-027` 三栏状态整合、回归与设计回写 ← T-FE-025, T-FE-026, T-FE-028
6. `T-FE-018` 真接口联调 ← T-FE-027, T-PERM-034（**被 2026-08-01 重设计决策取代**：T-FE-018 已按 v3 恢复，实际依赖 = T-FE-036 + T-PERM-034 + T-PERM-022/028/029/031；组织二期 T-FE-037 另依赖 T-ADMIN-021，见 phase3-plan；T-PERM-031 全局操作阶段 2-4 为联调前置）

准入门禁：~~`design/frontend/permission-grant.md` §16.8 R1~R11~~（设计文档已归档至 `docs/archive/2026-07-26/`）。plan 已归档取消（2026-07-26），T-FE-024~028 随页面删除废弃/取消。

### P8 - 权限授予 V2（方案A多条件分支模型，❌ 已取消 2026-07-26）

> 页面交互不满意，v1+v2 两套整体删除重做。T-FE-029~034 保持 done（产出已废），T-FE-035 cancelled。详见上方"前端"段废弃说明。plan 已归档至 `plans/archive/2026-07/permission-grant-v2-plan.md`。

1. `T-FE-029` V2 页面骨架+路由+三栏+角色树+能力门控 ✅ ← -
2. `T-FE-030` 方案A前端模型（GrantVariantId+replay+聚合摘要） ✅ ← T-FE-029
3. `T-FE-031` 中栏矩阵+单元格聚合摘要+分支列表就地展开 ✅ ← T-FE-030
4. `T-FE-032` 授权交互（点击/添加分支/逐分支编辑撤销/批量新增分支）+R11 ← T-FE-031
5. `T-FE-033` 子权限矩阵（parentVariantId）+两步保存+条件清除wire ← T-FE-032
6. `T-FE-034` 保存前总览+失败两子态+STALE_WITH_CHILD_FAILURE+fetchBaseline+离开保护 ← T-FE-033
7. `T-FE-035` 扩展V2 transport（多条件+失败模拟）+失格降级+回归验证+设计回写 ← T-FE-034

准入门禁：~~已关闭~~（plan 已归档取消，2026-07-26）。详见 [归档 plan](../plans/archive/2026-07/permission-grant-v2-plan.md)。v1+v2 两套页面整体删除重做，T-FE-029~034 保持 done（产出废弃）、T-FE-035 cancelled。

### 不排期（待立项）

EXT-7（batchCheck 逐条循环）/ EXT-8（enqueueAll 逐条 insert）— 审计 S-024 无主，性能项，待单独立项。

### 已完成的三个枢纽

`T-PERM-001`（A 链根）+ `T-PERM-002`（AOP afterCommit）+ `T-PERM-003`（A 链删version根）+ `T-PERM-004/005`（删 increment / 缓存目录残留）+ `T-PERM-006`（A 链广播订阅）+ `T-PERM-007`（文档一致性核对）+ `T-PERM-008`（Gateway 失效标记与订阅恢复代码）+ `T-PERM-009`（B 链根）+ `T-PERM-011`（scopeMode 契约全量推广）+ `T-PERM-012`（管理端/排查页响应改造）+ `T-PERM-013`（协议层 scopeAll→scopeMode 映射）+ `T-PERM-014`（scopeMode 正式定义收尾）+ `T-PERM-015`（前端 ScopeMode 类型 + composable）均已完成。`T-GW-005`（S-006 失效标记设计）已完成。

## 依赖告警（dangling）

> 当被依赖的任务 `cancelled` 或设计被 `superseded` 时，下游任务在此登记，等待重连。

_（暂无）_

## 设计变更待核对

> 当设计文件 `status` 变为 `superseded` 或章节实质变更时，`design_refs` 指向它的任务在此登记，等待核对验收与回写目标是否仍成立。

| 设计变更 | 受影响任务 | 核对状态 | 处理要求 |
|---|---|---|---|
| `docs/design/schema/` 四份旧 DDL（admin-service.sql / permission-center.sql / seed-admin-operations.sql / seed-perm-operations.sql）标记 superseded，权威 DDL 为 access-service.sql（2026-08-12，T-ACCESS-002） | T-ACCESS-012；T-PERM-019/020/021/034/041（proposed 待重基线） | 已登记；T-ACCESS-012 收口时物理归档 | 实现与测试以 `schema/access-service.sql` 为唯一依据；T-ACCESS-012 将旧文件引用改指 access-service.sql 并物理归档；T-PERM-* 待重基线任务由 T-ACCESS-012 统一更新 design_refs（T-PERM-013 为 done 历史事实不改） |
| `design/access-service-architecture.md` §9 与 `design/project-rules.md` §1.2 明确归并后的错误码归属（2026-08-12） | T-ACCESS-001、T-ACCESS-011、T-ACCESS-012 | T-ACCESS-001/011 已重基线；待实施与验收 | 既有管理域 `1xxxx`、权限域 `2xxxx` 原值保留并继续按领域新增；`access.application` 按对外入口所属领域取码，公共技术失败使用 `9xxxx`；禁止合并枚举、重编号或新增 `4xxxx` 段。T-ACCESS-001 保留领域枚举，T-ACCESS-011 扫描验收，T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §7.2/§10 补充授权失效后的陈旧回填防护（2026-08-12） | T-ACCESS-008、T-ACCESS-011、T-ACCESS-012 | T-ACCESS-008 已实施（2026-08-21，`beginRead` 令牌 + 剩余 TTL + 闩锁竞态测试 + 双 skill 同步 v3.0.0）；T-ACCESS-011 待验收 | 授权 L2 miss 在数据库读取前记录单调时钟起点，回填只能使用从该起点计算的剩余 catalog TTL，预算耗尽不写入，批量/重试不得重置；T-ACCESS-008 扩展受 catalog 上限约束的单次 TTL SPI 并补闩锁竞态测试，实施完成时同步两份缓存 skill，T-ACCESS-011 验收，T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §6.1 明确平台用户会话与服务身份认证边界（2026-08-12） | T-ACCESS-003、T-ACCESS-004、T-ACCESS-011、T-ACCESS-012 | T-ACCESS-003 已实施并回写 §6.1（2026-08-13 实施回写、2026-08-14 评审修订）；T-ACCESS-004/011 已重基线；待实施与验收 | Sa-Token 只承载平台用户会话，固定 2 小时绝对有效期和 30 分钟无操作有效期，Gateway 与 access-service 共享兼容且唯一的 Token/会话配置与键命名空间（T-ACCESS-003 落实：token-name=Authorization、token-prefix=Bearer、token-style=uuid、timeout=7200、active-timeout=1800；会话键 `Authorization:login:*`；两端一致性由部署配置约束，代码不实现跨进程启动校验——用户决策；LoginResp.expiresIn 单一来源=sa-token.timeout——评审 P2；Gateway 配置由 bootstrap.yml 迁移 application.yml + spring.config.import，修复 7 个启动缺陷——评审 P1）；OAuth2 客户端令牌保留客户端自定义有效期，perm-sdk、sync/full-sync 继续使用服务签名或内部凭证。T-ACCESS-004 落实安全矩阵，T-ACCESS-011 验收，T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §7.2/§10 将授权陈旧窗口预算由 `15+15` 修订为包含回源时间的 `10+5+15`（2026-08-11） | T-ACCESS-008、T-ACCESS-011、T-ACCESS-012 | T-ACCESS-008 已实施（2026-08-21，`PermCacheBoundaryValidator`/`GatewayCacheBoundaryValidator` 启动强制 10s/5s/15s + 截止/30s 边界测试）；T-ACCESS-011 待验收 | T-ACCESS-008 实现并校验授权 L2≤10秒、Gateway 全链路回源截止≤5秒、Gateway L1≤15秒，整个回源流程及重试共享截止时间，超时不写缓存且 fail-closed；T-ACCESS-011 注入接近/超过5秒延迟验证边界；T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §7.2 取消 `design/services/gateway.md` 的可切换 fail-mode、open 与 stale-allow（2026-08-11） | T-GW-001、T-GW-003、T-GW-004、T-GW-005、T-PERM-008 | T-ACCESS-008 已实施并回写 gateway.md（2026-08-21） | done 保持历史完成事实、不重开；T-ACCESS-008 已删除 `gateway.permission.fail-mode`、open/stale-allow 实现、stale store 及相关指标和告警，Gateway 权限回源失败固定 fail-closed；保留失效代际、回源并发防护和订阅重连全量清空等仍有效能力。T-GW-002 与 T-PERM-001/006/017 的 fail-closed、快照、广播、本地重评主体语义经扫描仍有效，实施时核对广义 `gateway.md` 引用 |
| `design/services/admin-service-api-contract.md` §3、§4 各写接口同步动作/当前差距及 §6/§7 的 `sys_sync_task` 契约被 `design/access-service-architecture.md` §4 取代（2026-08-10） | T-ACCESS-005、T-ACCESS-011 | T-ACCESS-005 已回写（2026-08-15）；T-ACCESS-011 待验收 | T-ACCESS-005 已改为同事务本地权限投影并退役 `/admin/sync-task/*`；T-ACCESS-011 对其余外部 API 做兼容回归并对退役接口做不存在负向验收。 |
| `design/cross-service/admin-permission-sync.md` 被 `design/access-service-architecture.md` 取代（2026-08-10） | T-PERM-019、T-PERM-021 | 待重基线 | 旧内部同步、SyncHandler/ownership/full-sync runbook 范围不得继续实施；由 T-ACCESS-012 逐卡更新设计引用、删除重叠范围或取消并重连依赖 |
| `design/frontend/permission-grant.md` §16 取代中栏矩阵直接编辑和右栏双 Tab（2026-07-12） | T-FE-014 | 历史基线已核对 | T-FE-014 保持 done，§15 保留其验收与实现记录，不重新打开任务 |
| 同上 | T-FE-018 | ~~已重连，执行前待确认~~ **被 2026-08-01 重设计决策取代** | ~~`depends_on` 已增加 T-FE-027；联调验收必须以 §16 新交互为准，不得回退旧矩阵~~（v3 重设计后 T-FE-018 恢复待排期，实际依赖 = T-FE-036 + T-PERM-034 + T-PERM-022/028/029/031；组织二期 T-FE-037 另依赖 T-ADMIN-021，见 phase3-plan；§16 为 v1 旧章节，已归档） |
| 同上 | T-FE-024 | 已确认 | T-FE-024 先于 T-FE-026；`AdditionalSettingDialog` 组件抽取 + `ChildPermissionDrawer` 内联化归 T-FE-024，授权弹窗编排归 T-FE-026 |
| 同上 | T-FE-025~028 | 已确认 | §16.8 R1~R11 全部已确认；R10 子权限逐项配置、R11 重叠语义已回写；plan 已 active |
| `design/frontend/permission-grant.md` 及三份补充整体归档（2026-07-26） | T-FE-014/018/024~028/029~035 | 已废弃 | 页面交互不满意，v1+v2 两套删除重做；设计文档归档至 `archive/2026-07-26/`，plan 归档；done 任务保持历史事实，未 done 任务 cancelled。详见"前端"段废弃说明 |
| 重设计立项（2026-08-01） | T-FE-036 | 已立项 | 15 项决策评审收敛（根因：查看与授予任务混淆 + 继承关系未体现），新设计回写 `design/frontend/permission-grant.md`（v3）；T-FE-018 恢复待排期（depends_on=T-FE-036, T-PERM-034）；T-PERM-034 范围更新（+grantSource/grantedBits 暴露） |

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

4 项 P1/P2 修复全部 ✅ done + ✓ 回写。计划归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。测试：247 tests 0 failures。

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
