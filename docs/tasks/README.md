# 任务看板（Task Board）

本目录是 AccessMesh 任务的**唯一权威清单**。任务为原子执行单元，归属某个 [计划](../plans/)，并声明将改动的 [设计](../design/) 章节。

> 治理规则见 skill：`.claude/skills/design-plan-task-lifecycle/SKILL.md`。任务 ID 格式 `T-<DOMAIN>-<NNN>`，各领域独立递增、ID 冻结不回收。

## 领域计数器

| 领域 | 前缀 | 下一编号 |
|---|---|---|
| permission-center | `T-PERM` | 040 |
| admin-service | `T-ADMIN` | 021 |
| gateway | `T-GW` | 007 |
| 组织/用户（跨 admin+perm） | `T-ORG` | 001 |
| 跨服务 API 契约 | `T-API` | 001 |
| 前端 | `T-FE` | 025 |

> 新建任务时从对应领域取下一编号，计数器 +1。

## 任务总表

> 状态简写：⚙️=proposed / 🔨=in-progress / 👀=review / ✅=done / ❌=cancelled。回写：⏳=pending / ✓=done。

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
| T-PERM-022 | 2.2 角色管理后端（abstract-role/* CRUD/树/移动；🔧detail 切业务键二元组 roleTypeCode+externalId，废弃旧 RoleDetailReq[带 domainCode 遗留]；🔧move 加父子类型兼容校验 roleTypeCode 一致；🔧tree 返回 delete_flag=0 全部有效角色[status 只作展示，新增 selectValidRoleTree 替换 selectEnabledRoleTree]，禁用角色不再从树消失；功能角色聚合可选 admin-service） | [frontend-phase2](../plans/frontend-phase2-plan.md) | api-contract §5.2/§6.10.3；implementation §2.1；design/frontend/role-manage.md §8 | T-FE-002 | ⚙️ | ⏳ |
| T-PERM-023 | 6.1 类型定义后端（type-definition/*；🔧typeValue 自动分配[T-PERM-019 D1 漂移收敛：TypeCreateReq 移除 @NotNull typeValue，服务端 tenant+typeKey 内 max+1 分配、软删不复用]；🔧list 返回分页结构+支持 typeKey/keyword/pageNum/pageSize 参数[TypeListReq 现 仅 domainCode 未生效，/list 返回全量 ItemsResp 无分页，前端已本地过滤分页]；🔧create 补 typeCode 入参或服务端生成[TypeCreateReq 现无 typeCode 字段，createType 未 setTypeCode 潜在 bug]；🔧create 移除 isSystem 字段[DESIGN_DRIFT，系统预置只走初始化种子，不可由 API 创建，前端已不透传]；🔧resource_type 创建联动预置 operation_permission[结合 T-PERM-028 确认范围]） | frontend-phase2 | api-contract §5.1；implementation §2.6；design/frontend/type-definition.md §8 | T-FE-003 | ⚙️ | ⏳ |
| T-PERM-024 | 6.2 系统配置后端（system-config/*；🔧api-contract §5.8 补 system-config 专属字段契约[现状仅 3 行路径表格，无字段表/请求示例，字段由 DTO 落地]；🔧SYSTEM_CONFIG 权限种子预置 VIEW+MANAGE[schema 无 INSERT 为该资源类型预置操作位，联调时可能权限判定为空]；🔧config_value JSONB↔entity String 映射确认[entity 声明 String，MyBatis-Flex+驱动序列化，确认无截断/转义]） | frontend-phase2 | api-contract §5.8 | T-FE-004 | ⚙️ | ⏳ |
| T-PERM-025 | 7.1 操作日志后端（operation-log/list；🔧api-contract §5.8 路径错误修正[写 /api/perm/operation-log/list，后端实际 /api/perm/log/operation/list]+补 operation-log 字段契约[现状仅 1 行表格，无字段表/请求示例]；🔧OperationLogListReq 补筛选维度 operatorId/createdAt 时间范围/targetType[现状只 module/action，schema 有字段且有索引未暴露]；🔧SYSTEM_CONFIG:VIEW 复用审计语义确认[LogQueryAppServiceImpl 无独立 OPERATION_LOG 权限码，复用致有系统配置 VIEW 即可查全部日志，确认是否独立 OPERATION_LOG:VIEW]） | frontend-phase2 | api-contract §5.8/§6.10.6；implementation §2.3 | T-FE-005 | ⚙️ | ⏳ |
| T-PERM-026 | 5.1 业务域后端（biz-domain/* + domain-config/*；🔧DOMAIN 权限种子缺失[schema 无 INSERT 为 DOMAIN 资源类型预置 VIEW 操作位，联调全账号 403]；🔧BizDomainResp 缺 global 字段[entity/schema 有 global 全局域语义，Resp 不返回致前端无法区分/禁删全局域]；🔧detail/update 切业务键 code[现用 IdReq/BizDomainUpdateReq 内部主键 domainId，schema uk_biz_domain 已保证 tenant+code 唯一]；🔧list 补分页参数[现 EmptyReq 全量 ItemsResp 无 keyword/pageNum/pageSize]；🔧AppServiceImpl configType 注释不全[只提 CLASSIFY/SUB_PERM，schema 列 5 种 SCOPE/RELATION/BINDING/SUB_PERM/CLASSIFY]；🔧extra JSONB↔String 映射确认[同 T-PERM-024 configValue]） | frontend-phase2 | api-contract §5.1/§5.6；implementation §2.7 | T-FE-006 | ⚙️ | ⏳ |
| T-PERM-027 | 5.2 服务+接口映射后端（service-config/* + resource-api-mapping/* + sync；🔧service list 补筛选分页；🔧service remove 级联处理映射/自动资源并广播失效[Controller 注释与 AppService 实现不一致]；🔧apis 补资源业务字段/树形结构；🔧sync DTO 收敛为仅 FULL；🔧mapping list 补 SERVICE:VIEW 校验；🔧mapping 切资源业务键/选择器；🔧补服务最近同步元数据） | frontend-phase2 | api-contract §5.4/§6.3/§6.10.4；design/frontend/service-interface-mapping.md §7 | T-FE-007 | ⚙️ | ⏳ |
| T-PERM-028 | 3.1 资源+操作定义后端（resource-entity/* + operation-permission/*；🔧detail/update/move/remove 切业务键[resource: resourceTypeCode+code+codeType；operation: resourceTypeCode+code，schema uk_resource_entity/uk_operation_permission_typed 已保证唯一]；🔧RESOURCE:VIEW/OPERATION:VIEW 种子缺失[resource-entity list/tree 与 operation-permission list 未见 VIEW 校验，schema 无 INSERT 预置操作位，联调全账号 403]；🔧bigint 字段 JSON 序列化为 string[operation-permission.binaryBit/inheritMask 为 63 位 bigint 列，Jackson 默认序列化为 number 致前端 JSON.parse 在 >2^53 丢精度；DTO 加 @JsonSerialize(ToStringSerializer.class) 或改 String 类型，前端切 BigInt 全链路+el-input 文本输入；T-FE-008 已用 BigInt 运算解决 32 位截断（2^53 内精确），63 位彻底方案作为全项目 bigint 序列化策略首例；🔧update 可选字段 extra 支持 null 清空语义[现 if(extra!=null) 不更新致前端 form.extra||null 清空传 null 被当不更新；extra JSONB 列不可用空串清空，需后端区分不更新vs清空为null]） | frontend-phase2 | api-contract §5.3/§6.2.2；implementation §2.9；design/frontend/resource-operation.md §8 | T-FE-008 | ⚙️ | ⏳ |
| T-PERM-029 | 3.2 权限条件后端（permission-condition/*） | frontend-phase2 | api-contract §5.6；implementation §2.5 | T-FE-009 | ⚙️ | ⏳ |
| T-PERM-030 | 3.3 冲突规则后端（conflict-rule/* 含 detect；🔧detail/update/remove 用内部主键 id[冲突规则无业务键如 code，id 即唯一标识，切业务键诉求弱，后端评估]；🔧list 无分页无 VIEW 校验[全量 ItemsResp，listConflictRules 未校验 CONFLICT_RULE:VIEW，种子可能缺失致联调全账号 403]；🔧ConflictRuleResp 缺 updatedAt[只有 createdAt]；❌ConflictRuleDetailReq 死代码[Controller 实际用 IdReq{id}，DTO 未使用，后端清理]；🔧conflictType 注释不一致[实体注 MUTEX_OP/MUTEX_ROLE，enum 为 ROLE_MUTEX/PERM_MUTEX，修正注释]；🔧detect 无权限校验[public 方法，评估补 VIEW 校验]；✅P1 已修 4 项[update UpdateChain 全量覆盖+rtv 清空/detect NULL 漏报 SQL 修正/uk_conflict_rule_perm 加 rtv 列+isDuplicate 业务去重+first<second 规范化/update 全量替换契约+conflictType @NotBlank+CONFLICT_RULE_DUPLICATE 错误码，2026-07-11]） | frontend-phase2 | api-contract §5.6；implementation §2.4；design/frontend/conflict-rule.md §4 | T-FE-010 | ⚙️ | ⏳ |
| T-PERM-031 | 3.4 资源依赖后端（resource-dependency/* 含 graph/check/batch-sync；🔧Resp 字段不全[缺 sourceResourceTypeCode/targetResourceTypeCode/资源 name/operationCodes/ownerServiceCode/maintainSource/updatedAt，前端 bits->码+id->资源映射兜底]；🔧UpdateReq 缺 sourceResourceCode/targetResourceCode 无法切换资源对[前端全量替换契约提交，真后端忽略]；🔧list/graph/check 无权限校验+list 无分页仅按 resourceEntityId 过滤；🔧graph 返回扁平列表非图结构；🔧DEPENDENCY 权限种子缺失[schema 无 INSERT 预置操作位，联调全账号 403]；🔧maintainSource 枚举不一致[DTO SERVICE/MANUAL vs schema ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC]；🔧batch-sync FULL diff 只比 sourceCode+targetCode 未比 sourceOperationCodes 同资源对多触发操作可能误删；🔧全局操作全链路修复[阶段1已完成：resolveOperationId/batchResolveOperationIds 专属优先+全局fallback+无效resourceTypeCode不降级跨类型任取+新增selectGlobalByCode/selectGlobalByCodes，2026-07-11；阶段2-4待做：PermQueryEngine:646缓存合并全局/:659 resolveBitMasks全局操作适用所有类型，PermissionGrantDomainServiceImpl:151/155/159授权含全局操作，OperationAppServiceImpl create/update全局位↔专属位冲突双向校验+schema uk_operation_permission_global补bit约束；验收：专属正常解析/专属缺失回退全局/同码并存专属优先/无效类型不跨类型误解析/全局操作能授予查询鉴权回显/全局位与专属位冲突拒绝创建更新]） | frontend-phase2 | api-contract §5.6/§6.9；core-flows §12 | T-FE-011 | ⚙️ | ⏳ |
| T-PERM-032 | 7.2 变更日志后端（permission-change-log/list + diff_snapshot） | frontend-phase2 | api-contract §5.8/§6.8；implementation §2.3 | T-FE-012 | ⚙️ | ⏳ |
| T-PERM-033 | 4.2 权限查询后端（auth/query-resources / query-scopes / permission-view/* + treeMode） | frontend-phase2 | api-contract §5.7/§6.6/§6.7/§6.8；implementation §3/§7.5 | T-FE-013 | ⚙️ | ⏳ |
| T-PERM-034 | 4.1 权限授予后端（role-resource-permission/save + add-child/children） | frontend-phase2 | api-contract §5.5/§6.4/§6.5；implementation §4 | T-FE-014 | ⚙️ | ⏳ |
| [T-PERM-035](T-PERM-035.md) | 自动授权（resolveAutoGrants + autoGrantForInsert + 循环依赖检测）— ⚠️ design-review §11 E4 暂缓未排期 | [frontend-phase2](../plans/frontend-phase2-plan.md) | core-flows §12；implementation §4；api-contract | T-FE-014, T-PERM-034 | ⚙️ | ⏳ |
| [T-PERM-036](T-PERM-036.md) | 动态数据权限端到端验证（scopeMode → SQL 映射链路）— ⚠️ design-review §11 Q7/B 暂缓（延后 example-service） | frontend-phase2 | api-contract §6.7；core-flows；implementation | T-FE-013, T-PERM-033 | ⚙️ | ⏳ |
| [T-PERM-037](T-PERM-037.md) | 跨页共性接口改造 + api-contract 回写收尾 | frontend-phase2 | api-contract；implementation | T-PERM-022~034 | ⚙️ | ⏳ |
| T-PERM-038 | 全局 TODO 收口（improvement-plan 附录 A） | [frontend-phase4](../plans/frontend-phase4-plan.md) | architecture；implementation | — | ⚙️ | ⏳ |
| T-PERM-039 | 测试补充（permission-center 新增改造接口测试） | frontend-phase4 | testing-standards | T-PERM-037 | ⚙️ | ⏳ |

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

_当前无活跃 T-ADMIN 任务。`T-ADMIN-001~019`（用户角色代理修复第一、二轮）已全部完成并归档，见下方"已完成"区。_

> EXT-7（PermissionCheckAppServiceImpl.batchCheck 逐条循环）/ EXT-8（SyncTaskDomainServiceImpl.enqueueAll 逐条 insert）为 DEFERRED 无主项（审计 S-024），未纳入本批任务，待单独立项。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| T-ADMIN-020 | admin-service CRUD 代码清理（痛点 #6，低优先级） | [frontend-phase4](../plans/frontend-phase4-plan.md) | architecture；services/admin-service.md | — | ⚙️ | ⏳ |

### 前端（前端 Phase 1/3/4 拆分）

> 来源：`docs/plans/improvement-plan.md` §4 各 Phase 拆分。Phase 1 archived（2026-07-12 归档），Phase 3/4 proposed。页面任务 design_refs 先指后端契约，UI 设计随任务回写到 `docs/design/frontend/<page>.md`。

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
| T-FE-009 | 3.2 权限条件页（通用条件模板 CRUD；conditionRules 可视化编辑器 {logic,items[]} 4 类型 DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST + gatewayEvaluable 开关 T-PERM-017；CONDITION:VIEW/CREATE/UPDATE/DELETE 三档独立非 MANAGE 对齐后端；后端 list 无分页前端本地过滤；🔧 清单 6 项登记 T-PERM-029） | frontend-phase1 | api-contract §5.6；design/frontend/permission-condition.md | - | ✅ | ✓ |
| [T-FE-010](T-FE-010.md) | 3.3 冲突规则页（ROLE_MUTEX 角色互斥/PERM_MUTEX 权限互斥 CRUD + 冲突检测对话框仅操作权限对双向匹配；表格名称映射加载 role/operation/type-def；CONFLICT_RULE:VIEW/CREATE/UPDATE/DELETE 三档独立非 MANAGE 对齐后端；🔧 清单 6 项登记 T-PERM-030） | frontend-phase1 | api-contract §5.6；design/frontend/conflict-rule.md | T-FE-001 | ✅ | ✓ |
| [T-FE-011](T-FE-011.md) | 3.4 资源依赖页（依赖 CRUD + 依赖图 echarts graph + 环检测；资源类型下拉+资源下拉联动；bits->操作码位运算拆解+id->资源映射应对 Resp 字段不全；编辑资源对可改全量替换 Q3=B；DEPENDENCY:VIEW/CREATE/UPDATE/DELETE 三档独立非 MANAGE 对齐后端+SYNC(batch-sync P0 标 TODO Q5=B)；🔧 清单 8 项登记 T-PERM-031） | frontend-phase1 | api-contract §5.6/§6.9；design/frontend/resource-dependency.md | T-FE-001, T-FE-008 | ✅ | ✓ |
| [T-FE-012](T-FE-012.md) | 7.2 权限变更日志页（diff 快照 + before/after + 影响评估） | frontend-phase1 | api-contract §5.8/§6.8；design/frontend/permission-change-log.md | T-FE-001 | ✅ | ✅ |
| [T-FE-013](T-FE-013.md) | 4.2 权限查询/校验页（多维度查询 + 权限解释 + 拒绝原因） | frontend-phase1 | api-contract §5.7/§6.6/§6.7/§6.8；design/frontend/permission-query.md | T-FE-001 | ✅ | ✅ |
| [T-FE-014](T-FE-014.md) | 4.1 权限授予页（选角色→勾资源树→操作矩阵→绑条件→批量保存） | frontend-phase1 | api-contract §5.5/§6.4/§6.5；design/frontend/permission-grant.md | T-FE-001, T-FE-002, T-FE-008 | ✅ | ✅ |
| T-FE-015 | Phase 3 联调：组织与用户（2.1 mock→真实接口） | [frontend-phase3](../plans/frontend-phase3-plan.md) | api-contract；admin-service-api-contract | T-PERM-037 | ⚙️ | ⏳ |
| T-FE-016 | Phase 3 联调：角色管理（2.2） | frontend-phase3 | api-contract | T-FE-002, T-PERM-022 | ⚙️ | ⏳ |
| T-FE-017 | Phase 3 联调：资源/操作定义（3.1） | frontend-phase3 | api-contract | T-FE-008, T-PERM-028 | ⚙️ | ⏳ |
| T-FE-018 | Phase 3 联调：权限授予（4.1） | frontend-phase3 | api-contract | T-FE-014, T-PERM-034 | ⚙️ | ⏳ |
| T-FE-019 | Phase 3 联调：权限查询/校验（4.2） | frontend-phase3 | api-contract | T-FE-013, T-PERM-033 | ⚙️ | ⏳ |
| T-FE-020 | Phase 3 联调：条件/冲突规则（3.2/3.3） | frontend-phase3 | api-contract | T-FE-009, T-FE-010, T-PERM-029, T-PERM-030 | ⚙️ | ⏳ |
| T-FE-021 | Phase 3 联调：业务域配置（5.1） | frontend-phase3 | api-contract | T-FE-006, T-PERM-026 | ⚙️ | ⏳ |
| T-FE-022 | Phase 3 联调：系统/服务配置（6.x/5.2） | frontend-phase3 | api-contract | T-FE-003, T-FE-004, T-FE-007, T-PERM-023, T-PERM-024, T-PERM-027 | ⚙️ | ⏳ |
| T-FE-023 | Phase 4：SPI 策略扩展验证 + 扩展指南（design/frontend/extension-guide.md） | [frontend-phase4](../plans/frontend-phase4-plan.md) | architecture；design/frontend/extension-guide.md | — | ⚙️ | ⏳ |
| T-FE-024 | ReConditionPicker + ReConditionEditor 条件选择/编辑组件抽取 | [frontend-phase4](../plans/frontend-phase4-plan.md) | design/frontend/permission-condition.md；permission-grant.md | T-FE-001, T-FE-009, T-FE-014 | ⚙️ | ⏳ |

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

### 不排期（待立项）

EXT-7（batchCheck 逐条循环）/ EXT-8（enqueueAll 逐条 insert）— 审计 S-024 无主，性能项，待单独立项。

### 已完成的三个枢纽

`T-PERM-001`（A 链根）+ `T-PERM-002`（AOP afterCommit）+ `T-PERM-003`（A 链删version根）+ `T-PERM-004/005`（删 increment / 缓存目录残留）+ `T-PERM-006`（A 链广播订阅）+ `T-PERM-007`（文档一致性核对）+ `T-PERM-008`（Gateway 失效标记与订阅恢复代码）+ `T-PERM-009`（B 链根）+ `T-PERM-011`（scopeMode 契约全量推广）+ `T-PERM-012`（管理端/排查页响应改造）+ `T-PERM-013`（协议层 scopeAll→scopeMode 映射）+ `T-PERM-014`（scopeMode 正式定义收尾）+ `T-PERM-015`（前端 ScopeMode 类型 + composable）均已完成。`T-GW-005`（S-006 失效标记设计）已完成。

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
