---
doc_type: problems
title: 待解决问题清单
counter: Q-055           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-10-01
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：决定（含“不解决”拍板）当轮归所属权威规范或任务，本文件只更新问题状态与引用；不复制规则、例外正文或转出后的实施细节。统一协议见 [文档治理](design/project-rules.md#decision-governance)，主题定位见[定案入口](design/decision-registry.md)。

## 未收敛问题

**阅读约定**：按根因或可共同处理的范围合并，子项各自保留证据、影响与既有边界。旧编号的去向见文末索引；编号合并不代表缺陷修复。原始登记细节见 [合并前快照](archive/2026-09-30/pending-problems-before-consolidation.md)，外部报告核实见 [核实记录](archive/2026-09-30/logic-review-verification.md)。本次整理没有重新运行业务复现。

<a id="q-050"></a>
## Q-050 操作位写入与准入目录校验边界不一致

- **状态**：converted
- **登记**：2026-09-30
- **来源**：外部逻辑报告 B-6；[核实记录](archive/2026-09-30/logic-review-verification.md)
- **关联**：[T-PERM-077](archive/2026-09-24/tasks/T-PERM-077.md)；[准入协议](design/access-service-api-contract.md#operation-admission-protocol)；P2；转出 [T-PERM-098](tasks/T-PERM-098.md)

**现象与证据**：[OperationAppServiceImpl.createOperation/updateOperation](../access-service/src/main/java/cn/ac/fage/accessmesh/access/type/service/impl/OperationAppServiceImpl.java) 未限制 binaryBit 为单比特，operation_permission 表也无对应 CHECK。自定义类型同事务补种 AUTHORITY_ROOT，会被其单比特 CHECK 拦住；内置类型跳过补种，可追加不重复的非幂位。[QueryExecutionEngine.prepareAdmissionClauses](../access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/query/QueryExecutionEngine.java) 则拒绝覆盖当前要求的坏位行。

**影响与边界**：直接 API 向内置类型写位 3 后，与其有效位相交的准入要求可能触发 20071，相关快照构建失败；不影响所有类型或所有要求。前端已有幂位校验。T-PERM-077 的“不新增数值校验”取舍仍有效，本项是之后新增严格准入消费的影响；inheritMask 的负数表示不是错误。

**设想方向（未定案）**：明确目录写入与准入消费的一致性边界；调整写约束前须核对原取舍并修订契约。

<a id="q-049"></a>
## Q-049 非法 cron 保存成功但调度失败

- **状态**：converted
- **登记**：2026-09-30
- **来源**：外部逻辑报告 B-5；[核实记录](archive/2026-09-30/logic-review-verification.md)
- **关联**：T-ACCESS-054 job 最小运营边界；P2；转出 [T-ADMIN-032](tasks/T-ADMIN-032.md)

**现象与证据**：[JobAppServiceImpl.createJob/updateJob](../access-service/src/main/java/cn/ac/fage/accessmesh/access/platform/service/impl/JobAppServiceImpl.java) 未在写入前解析 cron；scheduleJob 构造 trigger 失败只记日志。Spring 6.1.5 CronTrigger 构造时立即解析，JobCreateReq 的 @NotBlank 仅拒空白、不验语法。

**影响与边界**：运维调用创建启用任务时传非空非法 cron，API 成功、库中启用，但未注册调度；对账也无法修正。更新会先取消当前实例旧调度，其他实例可能仍按旧 cron 执行。默认种子只开放 VIEW/TRIGGER/ENABLE，无任务管理 UI。

**设想方向（未定案）**：在写入及撤销旧调度前校验 cron，明确注册失败的可观测结果。

<a id="q-048"></a>
## Q-048 菜单创建、删除缺树写互斥

- **状态**：converted
- **登记**：2026-09-30
- **来源**：外部逻辑报告 B-4；[核实记录](archive/2026-09-30/logic-review-verification.md)
- **关联**：[树写锁约束](design/access-service-architecture.md#tree-write-lock)；P2；转出 [T-ADMIN-031](tasks/T-ADMIN-031.md)

**现象与证据**：[MenuWriteAppServiceImpl](../access-service/src/main/java/cn/ac/fage/accessmesh/access/menu/service/impl/MenuWriteAppServiceImpl.java) createMenu 读父后插入、deleteMenu 查子后软删，均无 SYS_MENU 锁；updateMenu 已在首次读取前持锁。DomainService 未补锁，Controller 写入口仍存在。

**影响**：有写权限的并发删父、挂子调用可留下指向软删父的存活子节点，正常树无法从根到达。当前无菜单管理 UI，风险限于满足门禁的写调用；未做并发复现。

**设想方向（未定案）**：创建、删除与更新统一在首次树读取前持 SYS_MENU 锁。

<a id="q-047"></a>
## Q-047 角色重新指派静默忽略窗口或关系变更

- **状态**：converted
- **登记**：2026-09-30
- **来源**：外部逻辑报告 B-3；[核实记录](archive/2026-09-30/logic-review-verification.md)
- **关联**：契约总册 §10 user-role 分配；P2；转出 [T-ADMIN-030](tasks/T-ADMIN-030.md)

**现象与证据**：[UserManageAppServiceImpl.assignRole/assignRolesBatch](../access-service/src/main/java/cn/ac/fage/accessmesh/access/user/service/impl/UserManageAppServiceImpl.java) 按 userId+roleId 去重，命中即跳过。[UserRoleMapper.xml](../access-service/src/main/resources/mapper/role/UserRoleMapper.xml) 装载既有绑定不滤有效期；uk_user_role 含 relationId，但内存去重未区分。assign 接收窗口，batch-assign 新建固定无限期，均不更新旧行。

**影响**：过期绑定重新指派返回成功但仍失效，未来窗口及 relationId 变更也可能被吞；先撤销再分配可恢复。不能据 sync 多重集推导管理面必须支持多窗口。

**设想方向（未定案）**：区分相同绑定重试与窗口/关系变更，明确更新或拒绝语义。

<a id="q-045"></a>
## Q-045 TRACE 敏感诊断输出缺授权门禁

- **状态**：converted
- **登记**：2026-09-26
- **来源**：[T-PERM-088](archive/2026-10-01/tasks/T-PERM-088.md) 的诊断门禁暂缓安排
- **关联**：[engine/implementation.md](design/engine/implementation.md) §3.5（TRACE 输出）；原登记锚 r2-unified-query-and-admission.md §3.3/§6.1（该稿 2026-10-01 转 superseded 随计划归档）；转出 [T-PERM-102](tasks/T-PERM-102.md)

**现象与证据**：普通 execute(trace=true) 可返回真实角色、权限 ID、互斥命中和父绑定证据；设计要求敏感诊断授权，门禁交付按 T-PERM-088 安排暂缓。

**影响与边界**：若适配层把 trace 参数直通外部请求且未授权，调用方可探测他人授权结构及条件归属。早期引擎无生产消费者时无该暴露面；后续接线不自动证明门禁已补齐。

**设想方向（未定案）**：在引擎或适配层显式诊断鉴权，避免外部 DTO 无门禁直通；暂缓安排保持原范围。

<a id="q-044"></a>
## Q-044 资源复合拼接键碰撞

- **状态**：converted→closed（随任务收敛，条目移文末已收敛索引）
- **登记**：2026-09-26
- **来源**：T-PERM-084 同型核对；外部报告 B-9；[核实记录](archive/2026-09-30/logic-review-verification.md)
- **关联**：[T-PERM-084](archive/2026-10-01/tasks/T-PERM-084.md)；engine/implementation.md 业务键元组边界；P2；转出 [T-PERM-096](tasks/T-PERM-096.md)（后端半边）+ [T-FE-060](tasks/T-FE-060.md)（前端半边）

**现象与证据**：code/codeType 可含分隔符，但内存索引把字段直接拼成字符串；不同元组因此同键，数据库完整元组唯一性不拦此形态。证据表与影响边界见[合并前快照](archive/2026-09-30/pending-problems-before-consolidation.md)与 [核实记录](archive/2026-09-30/logic-review-verification.md)；修复终态见已收敛索引 2026-10-01 行。

<a id="q-043"></a>
## Q-043 可选字段缺显式清空通道

- **状态**：converted
- **登记**：2026-09-24
- **来源**：T-API-004 同型盘点；外部报告 B-11；[核实记录](archive/2026-09-30/logic-review-verification.md)
- **关联**：契约总册 §2.7、project-rules §7.6；字段校验见 [Q-018](#q-018)；转出 [T-API-005](tasks/T-API-005.md)

**现象与证据**：

| 字段族 | 未覆盖点 |
|---|---|
| condition / menu | ConditionUpdateReq.description、MenuUpdateReq.path/icon 等：null 跳过，无清空通道。 |
| OAuth2 client | grantTypes/redirectUris/scopes/audiences 等：清空通道和各字段的清空安全语义待明确。 |
| system-config | config hook 将空描述发 null；SystemConfigAppServiceImpl.upsertSystemConfig 以 update(entity) 回写。MyBatis-Flex 1.11.7 默认忽略 null，旧描述保留，内存组装的保存响应还可能与重新查询不同。 |
| role/resource extra | 契约总册 §2.7 挂接本问题：既有 extraClear 仅对齐冲突拒绝，空白拒绝维持既有宽松「随 Q-043 同型矩阵后续收敛」（2026-10-01 codex 复评补登，原登记遗漏半边）。 |

**影响与边界**：清空提交可能成功却保留旧值；未断言实际用户是否操作过。T-API-004 已交付范围不扩撤；system-config 契约 §17.2 未定义清空语义，不能预定为空串协议。

**设想方向（未定案）**：按各域确定字段语义，再贯通 DTO、真实 NULL 写入、表单与契约；OAuth2 客户端字段须逐项核对安全后果。

<a id="q-040"></a>
## Q-040 服务凭证与运行时查询仍需两套身份

- **状态**：converted
- **登记**：2026-09-23
- **来源**：[T-ACCESS-053](archive/2026-09-24/tasks/T-ACCESS-053.md) 维持现状、后续处理安排
- **关联**：[service-authentication.md §3.5](design/service-authentication.md)；extension-guide §2.2；T-ACCESS-059；转出 [T-ACCESS-068](tasks/T-ACCESS-068.md)

**现象与证据**：per-service 凭证覆盖同步/manifest 通道，auth/check、batch-check、query-resources、query-scopes 仍要求 X-Internal-Secret 与服务/租户头；凭证不能直接替代该查询身份。

**影响与边界**：接入方维护两套配置与失效语义，查询面仍承担全局共享密钥失陷风险。新准入端点接线不代表本项自动收敛；T-ACCESS-053 的阶段边界保持。

**设想方向（未定案）**：按阶段二规划逐端点凭证化，先定义服务可查询的主体/资源范围、租户派生及调用能力。

<a id="q-038"></a>
## Q-038 树过滤后的节点完整性与展示不一致

- **状态**：converted
- **登记**：2026-09-23
- **来源**：T-ACCESS-052、T-FE-050 存量观察；合并 Q-039、Q-019（原登记见 [快照](archive/2026-09-30/pending-problems-before-consolidation.md)）
- **关联**：T-ACCESS-052、T-FE-050；转出 [T-ACCESS-065](tasks/T-ACCESS-065.md)

**现象与证据**：

| 子问题 | 证据与实际后果 |
|---|---|
| 父缺失导致子树丢失 | TreeBuilder.buildTrees（infrastructure/util/TreeBuilder.java:57-67）只从 parentId==null 起建树；父被 status/enabledOnly 过滤而子保留时，子支不可达。实例裁剪的 V∪祖先链已规避该维度，状态/类型过滤仍可触发。 |
| 树与列表状态不同 | ResourceEntityMapper.xml 的 selectResourceTree 固定 status=1，selectResourceListPaged/Count 不滤状态；停用资源在管理列表可见、授权树不可见。 |
| 父组织名依赖过滤后树 | user/index.vue 信息卡与 openOrgForm 从 orgTreePanelRef.orgTree 查父名；orgType=1 筛选或权限排除父节点时，显示“未知”。OrgResp 未提供 parentOrgName。 |

**影响与边界**：数据和权限过滤后的展示不完整，可能隐藏启用子节点或丢失父名；未发现由这些路径产生的越权。状态是否应统一、父缺失是否提升为根属不同子项，不能用一次修改假称全部解决。

**设想方向（未定案）**：共同核对过滤、祖先保留、树构建及父名回退；逐子项确定策略，公共 TreeBuilder 改动须覆盖各消费树。

<a id="q-037"></a>
## Q-037 停用主体入口失败并静默清空授权草稿

- **状态**：converted
- **登记**：2026-09-22
- **来源**：T-FE-057 入口侧同型盘点
- **关联**：T-PERM-022 主体树过滤边界；T-FE-057；转出 [T-FE-061](tasks/T-FE-061.md)

**现象与证据**：角色管理页、组织信息卡允许点击停用主体的授予入口，而 SubjectTreePanel 过滤停用主体；授予 hook 的 preset 找不到节点后 resetAll，未经过 confirmDiscardIfDirty。

**影响与边界**：提示“未找到”而非“已停用”，keep-alive 页在途草稿可能丢失。PositionTab 岗位入口已用禁用态和 tooltip 收敛，本项保留其余入口。

**设想方向（未定案）**：入口状态提示对齐既有先例，并评估 preset 失败前的草稿保护。

<a id="q-032"></a>
## Q-032 组织、岗位动作码未覆盖全部判定入口

- **状态**：converted
- **登记**：2026-09-22
- **来源**：T-ORG-003 同型核对；合并 Q-034（原登记见 [快照](archive/2026-09-30/pending-problems-before-consolidation.md)）
- **关联**：org-user-permission-contract；[T-ACCESS-055](archive/2026-09-24/tasks/T-ACCESS-055.md)；转出 [T-ORG-005](tasks/T-ORG-005.md)

**现象与证据**：

| 入口 | 差异与后果 |
|---|---|
| 创建用户并挂组织 | UserWriteAppServiceImpl.createUser 带 orgId 时用 ORG:UPDATE；已有用户挂载经成员动作码解析。仅持 MANAGE_MEMBER 的管理员可挂已有用户，却不能一步创建并挂载；契约该入口当前与裸 UPDATE 实现一致。 |
| 岗位可见性裁剪 | OrgVisibilityQueryAppServiceImpl.filterVisibleOrgIds 固定 VIEW，org 树读面则按 orgType 分发 VIEW/VIEW_POSITION；影响成员候选池及 user/delete 默认树可见性校验。 |

**影响与边界**：有限管理员操作可能过严；VIEW 裁剪也可能让岗位成员进入候选池，但另有候选门禁，不直接推导越权。T-ACCESS-055 已明确维持岗位裁剪现状并留待专门处理：改为精化码会改变按 VIEW 配权的部门管理员候选池，不能把该安排当作未决选项重新选择。

**设想方向（未定案）**：统一盘点 OrgOperationCodeMapper 的入口覆盖；创建挂载与岗位裁剪分别明确契约、验证影响，再实施。

<a id="q-031"></a>
## Q-031 同步资源 codeType 与业务键归一不一致

- **状态**：converted
- **登记**：2026-09-22
- **来源**：T-PERM-076 存量观察
- **关联**：ResourceEntitySyncAppServiceImpl、ResourceKeyReq；转出 [T-PERM-100](tasks/T-PERM-100.md)

**现象与证据**：sync/full-sync 的 codeType 仅归一空值，不 trim；管理创建和 ResourceKeyReq.normalizedCodeType 会 trim。同步写入 `" BIZ "` 后，管理面按 `BIZ` 查询不到。

**影响**：同步自查找仍可达，detail/update/remove 业务键不可达，可能返回 20004或另建同码资源。

**设想方向（未定案）**：统一写入/寻址归一，单独核对已有带空白行的处理，不能只修新写入就视为存量消失。

<a id="q-029"></a>
## Q-029 OAuth2 委托链的租户与用户状态校验缺口

- **状态**：converted
- **登记**：2026-09-22
- **来源**：T-ADMIN-028 存量观察；合并 Q-030（原登记见 [快照](archive/2026-09-30/pending-problems-before-consolidation.md)）
- **关联**：[T-ADMIN-028](archive/2026-09-24/tasks/T-ADMIN-028.md)；转出 [T-ADMIN-034](tasks/T-ADMIN-034.md)

**现象与证据**：

| 校验面 | 证据与实际后果 |
|---|---|
| 授权时租户匹配 | OAuth2AppServiceImpl.authorize 全局解析 clientId，不比对客户端租户与会话租户；码/令牌租户取会话，其他租户合法客户端可得到本租户用户委托。实际入口为平台会话发起的 API，仓内无 authorize 前端消费者。 |
| 委托用户有效性 | RequestContextInterceptor.authenticateOAuth2Jwt 动态检查客户端启用、不查用户状态；授权码兑换及 refresh 也未查记录中的用户有效性。禁用/删除用户后，已签令牌可在 TTL 内继续用，刷新链可延长至刷新令牌到期。 |

**影响与边界**：分别影响委托租户隔离与撤权即时性；开放资源路径默认仅 userinfo，配置扩展会扩大暴露面。token/refresh 匿名入口全局查客户端有其前提，不能一律改为按会话租户过滤。

**设想方向（未定案）**：分别确定 authorize 租户约束、资源访问/兑换/刷新用户状态校验及 TTL 接受边界；合并承载不替代各子项验收。

<a id="q-028"></a>
## Q-028 主体组展开存在重复遍历

- **状态**：converted
- **登记**：2026-09-22
- **来源**：T-PERM-075 可裁剪项，安排后续轻量处理
- **关联**：SubjectDomainServiceImpl；转出 T-PERM-101（看板行，[计划](plans/pending-problems-clearance-plan.md)组四）

**现象与证据**：expandAllSubtree/resolveGroupRolesAllSubtreeBatch 与 expandInMemory/resolveGroupRolesBatch 遍历重复，差异在禁用剪枝：写守卫要原始持有，运行时解析要有效持有。

**影响与边界**：维护重复，无行为缺陷；相反的剪枝语义必须保留。

**设想方向（未定案）**：参数化共享遍历，验证剪枝/不剪枝两种结果等价。

<a id="q-027"></a>
## Q-027 新增分组角色未展开成员检查互斥

- **状态**：converted
- **登记**：2026-09-22
- **来源**：T-PERM-075 写守卫留观项
- **关联**：UserManageAppServiceImpl.rejectRoleMutexOnAssign；sync/full-sync BIND；转出 [T-PERM-097](tasks/T-PERM-097.md)

**现象与证据**：已有角色 Y、规则互斥 X/Y 时，绑定子树含 X 的组 G；新增侧只把 G 入 postState，未展开 X，写守卫放行。持有侧组展开已覆盖，本项只缺新增侧。

**影响与边界**：运行时展开后 X/Y 同场被双删，保存时无提示并使原有 Y 失效；运行时 fail-closed 兜底，不构成放行越权。

**设想方向（未定案）**：新增组目标展开子树并继承窗口，assign/batch-assign/sync/full-sync 同步核对。

<a id="q-024"></a>
## Q-024 组织树配置允许根节点范围重叠

- **状态**：converted
- **登记**：2026-09-21
- **来源**：T-ORG-002 最小守卫范围之外的留观项
- **关联**：default-org-tree-user-lifecycle.md §7/§7.1；转出 [T-ORG-004](tasks/T-ORG-004.md)

**现象与证据**：createOrgTreeConfig 可把现有树的中间节点设为另一树根，未检查祖先/后代重叠。多个根同时命中时，resolveTreeRootExternalId(s) 的结果依赖遍历顺序。

**影响与边界**：同步树归属解析可能错桶；创建非默认配置不直接改变默认身份池。当前无 create UI，入口为有权限的 API；T-ORG-002 最小守卫安排保持。

**设想方向（未定案）**：新建时统一核对所有现有树根的祖先/后代关系，确定存量重叠的处理。

<a id="q-023"></a>
## Q-023 删除类型所有者角色缺引用守卫或提示

- **状态**：converted
- **登记**：2026-09-21
- **来源**：[T-PERM-072](archive/2026-09-24/tasks/T-PERM-072.md) 留观项
- **关联**：RoleManageAppServiceImpl.deleteRoles；type_definition.extra.grantOriginRole；转出 [T-PERM-099](tasks/T-PERM-099.md)

**现象与证据**：删除所有者角色会回收其 AUTHORITY_ROOT，而类型保留；无引用守卫或提示，之后无人能通过该类型首授/转授资格检查。

**影响与边界**：类型授权入口锁死，但可用 updateType 迁移所有者并重建授权根恢复；删除时管理员不知后果。

**设想方向（未定案）**：删除前检查引用，确定拒绝并先迁移或警告放行语义。

<a id="q-022"></a>
## Q-022 grant_dep_id 保留为零读零写列

- **状态**：converted
- **登记**：2026-09-21
- **来源**：[T-PERM-072](archive/2026-09-24/tasks/T-PERM-072.md) 保留列安排
- **关联**：dependency-auto-grant.md §3.4；[历史依据](archive/2026-09-26/decision-registry-before.md) 2026-09-21；转出 [T-PERM-103](tasks/T-PERM-103.md)

**现象与证据**：role_resource_permission.grant_dep_id 及 RoleResourcePermission.grantDepId 不读不写。单字段不能表达同一 AUTO_DEP 行的多条依赖边与种子来源，列注释已改为保留诊断口径。

**影响与边界**：维护占位，无功能缺陷；既有安排明确保留，不因合并整理撤销。

**设想方向（未定案）**：未来单边诊断需求或 schema 清理窗口再评估。

<a id="q-021"></a>
## Q-021 菜单种子图标未完整注册

- **状态**：converted
- **登记**：2026-09-19
- **来源**：T-FE-049 存量观察
- **关联**：BootstrapGraphDefinition；frontend/src/components/ReIcon/src/offlineIcon.ts；转出 T-FE-062（看板行，[计划](plans/pending-problems-clearance-plan.md)组三）

**现象与证据**：种子使用 ep/xxx 斜杠键，经 useRenderIcon 走离线 storage 查找；coins/connection/document/files/history/key/office-building/setting/share 等未注册，home-filled 已注册。

**影响与边界**：菜单标题可见、图标为空，无功能或权限影响；机制链已核对，浏览器实际渲染待验证。占位项 warning-filled/menu 已处理，不混入待办。

**设想方向（未定案）**：统一种子与离线注册集合，验证实际渲染。

<a id="q-018"></a>
## Q-018 业务字段空值、长度校验与存储约束不一致

- **状态**：converted
- **登记**：2026-09-19
- **来源**：T-FE-050；合并 Q-052 外部报告 B-8（原登记见 [快照](archive/2026-09-30/pending-problems-before-consolidation.md)）
- **关联**：[字段清空协议 Q-043](#q-043)；长度核实见 [记录](archive/2026-09-30/logic-review-verification.md)；转出 [T-ADMIN-033](tasks/T-ADMIN-033.md)

**现象与证据**：

| 子问题 | 写入口与约束 |
|---|---|
| 组织名空串 | OrgUpdateReq.orgName 无非空白校验，OrgWriteAppServiceImpl.updateOrg 只判非 null，可写空串；创建有 @NotBlank、前端有 required/min。空名父在信息卡为空，OrgForm 的 fallback 还可能误显为“根组织”。 |
| 用户字段长度 | UserCreateReq 的 username/name/phone/email 与 UserUpdateReq 的 name/phone/email 无列宽上限；sys_user 对应 VARCHAR(64/128/32/128)。更新 phone/email 已有空白 Pattern，但不限制长度。 |
| 组织、公告长度 | OrgCreateReq/OrgUpdateReq 的 orgName/code 无上限（sys_org VARCHAR(128/64)）；NoticeCreateReq/NoticeUpdateReq.title 无上限（VARCHAR(256)）。 |

**影响与边界**：空名损害展示语义；超长非空输入直接写库被拒，通用异常返回 HTTP 500/99999，事务回滚、无静默截断。字段格式限制与长度上限是不同契约；清空可选字段的问题仍由 Q-043 承载。

**设想方向（未定案）**：创建/更新按列宽校验；组织名更新保留 null=不更新、拒空白，不能直接 @NotBlank 而误拒 null。确定存量空名处理，不假称拒新输入就清理了旧数据。

<a id="q-015"></a>
## Q-015 设计、契约与代码注释未同步现行实现

- **状态**：converted
- **登记**：2026-09-19
- **来源**：T-GW-009、T-ORG-002、T-ACCESS-053、T-FE-058；合并 Q-026/041/042/051/053/054/055（原证据见 [快照](archive/2026-09-30/pending-problems-before-consolidation.md)）
- **关联**：现行契约/设计及代码；运行时身份边界仍见 Q-040，清空语义仍见 Q-043；转出 [T-ACCESS-066](tasks/T-ACCESS-066.md)

**现象与证据**：

| 漂移主题 | 待订正位置与当前依据 |
|---|---|
| Gateway 白名单 | architecture.md 与 access-service-architecture.md 仍把整族 /api/access/auth/** 描述为白名单，OAuth2 段还有笼统密钥豁免句；现行精确入口范围见 gateway.md 与运行时配置。 |
| 过滤器排序 | SignatureEnrichFilter.getOrder 注释称在 InternalSecretFilter 前；实际 -40 先于 -35。Q-055 是 Q-015 原补充证据的重复登记。 |
| 组织根节点 | schema/access-service.sql 的 sys_org.parent_id 注释写 NULL=根；bootstrap/createOrg 新写与启动校验用 0。COMMENT/迁移快照需联动核对。 |
| SDK 身份总览 | architecture.md 的 perm-client starter 行漏 FeignCredentialInterceptor；T-PERM-070 已交付，service-authentication 与 extension-guide 为两套身份依据。 |
| 用户角色端点 | org-user-permission-contract.md 的管理查询仍指 /user-role/list；T-ACCESS-042 后应指 /user-role/view，list 是另一个在役响应模型。 |
| 旧准入协议 | adopted 的 permission-center-v3.5-design.md §7 仍正向描述已删除的 InterfaceSnapshotResp、三态 matcher 与 check-interface；现行见契约 §25、新准入四态链。需标替代，不撤销该册其他有效语义。 |
| 自动授权状态 | PermissionGrantDomainService/Impl 的 resolveAutoGrants TODO 称未实现；现已有 AutoGrantMaterializationDomainService 与写入口同事务重算。 |
| 系统配置页面 | frontend/system-config.md §4 与 operation-log.md 对比句仍描述本地分页/mock 保存；当前 usePagedList、真实 API 和后端 PageResp 已生效。description 清空契约待 Q-043 确定。 |

**影响与边界**：误导入口、身份、状态与执行序理解；本组仅为文档/注释漂移，未把相应运行时缺陷标为已修。白名单等安全语义以现行权威和实现核对，不从陈旧文字推出运行时绕过。

**设想方向（未定案）**：按主题集中校正文档/注释及连带引用；需要新契约的部分保持关联问题，不在文档清扫中定案。

<a id="q-014"></a>
## Q-014 会话、网关测试依赖固定 sleep 构造时序

- **状态**：converted
- **登记**：2026-09-18
- **来源**：[T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) 同型盘点
- **关联**：testing-standards §10.3；Q-013 已收敛形态；转出 [T-ACCESS-067](tasks/T-ACCESS-067.md)

**现象与证据**：PlatformSessionIdleTimeoutTest、PlatformSessionAbsoluteTimeoutTest、AuthTokenFilterTest 用 sleep(1200) 拼活跃/超时时间轴；绝对超时用例 t≈3.6s 的成功断言距 4s 边界仅约 400ms。idle/网关续期余量较宽。

**影响与边界**：模块并行负载下延迟拉伸可能制造假失败；历史全量尚未实证击穿这些用例，不能视为可豁免的新回归。

**设想方向（未定案）**：选择可控会话时间或有界轮询，保留原行为断言，避免固定余量。

## 已收敛（含合并索引；详情在关联问题/任务或历史来源）

> closed（合并）只关闭旧登记编号，实际问题由关联 open 条目继续承载；修复、重复与合并分别注明，不回收编号。合并依据为 2026-09-30 本次“重复或类似问题精练合并”要求，原证据见 [合并前快照](archive/2026-09-30/pending-problems-before-consolidation.md)。

| Q-ID | 标题 | 收敛形态 | 关联 | 收敛日期 |
|---|---|---|---|---|
| Q-044 | 资源复合拼接键碰撞 | closed（T-PERM-096+T-FE-060 done：后端三内存索引（共享批量解析 resourceLookup、转授 resourceEntityIdByKey/结果映射）改结构化 record 元组，checkCanGrant 结果键=GrantCheckKey 本身；BusinessKeyUtil 四拼接构造器退役（resourceCodeTypeKey/resourceTripleCodeKey/grantCheckKey + 类推清扫零调用死方法 resourceTripleValueKey），golden 锁与对外协议键不动；前端 grant-keys.ts 单源 JSON 元组编码收口授权决策五段键/资源三段/树节点键/矩阵行键/主体树角色键 + 类推 changeGroupKey（inlineName 自由文本）；碰撞对回归锁双端旧实现实证红（后端 expected 100 got 200 与 duplicate element、前端 8 红）；元组边界口径入 engine/implementation §8.4、键约定入 permission-grant.md §2.3；类推核实 roleKey/subjectKey/roleProjectionIndexKey 无碰撞形态（受限段+尾段自由文本），apiRouteResourceKey（path 中段自由文本）理论碰撞面属「内部消费者全量迁移分开评估」范围另行决策） | [T-PERM-096](tasks/T-PERM-096.md)、[T-FE-060](tasks/T-FE-060.md) | 2026-10-01 |
| <a id="q-055"></a>Q-055 | 签名过滤器顺序注释 | closed（重复登记，按 2026-09-30 本次合并要求归入 Q-015；问题仍 open） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-054"></a>Q-054 | 系统配置前端设计陈旧 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；问题仍 open） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-053"></a>Q-053 | 自动授权旧 TODO | closed（注释漂移同族，按 2026-09-30 本次合并要求归入 Q-015；问题仍 open） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-052"></a>Q-052 | 业务字段缺长度校验 | closed（写入口字段校验同族，按 2026-09-30 本次合并要求归入 Q-018；问题仍 open） | [Q-018](#q-018) | 2026-09-30 |
| <a id="q-051"></a>Q-051 | v3.5 旧准入设计未标替代 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；问题仍 open） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-042"></a>Q-042 | 用户角色查询端点旧指代 | closed（契约指代漂移同族，按 2026-09-30 本次合并要求归入 Q-015；问题仍 open） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-041"></a>Q-041 | SDK 总览漏服务凭证拦截器 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；问题仍 open） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-039"></a>Q-039 | 资源树与列表状态过滤不同 | closed（树过滤与展示同族，按 2026-09-30 本次合并要求归入 Q-038；问题仍 open） | [Q-038](#q-038) | 2026-09-30 |
| <a id="q-034"></a>Q-034 | 岗位可见性未按 VIEW_POSITION 精化 | closed（动作码入口覆盖同族，按 2026-09-30 本次合并要求归入 Q-032；问题仍 open） | [Q-032](#q-032) | 2026-09-30 |
| <a id="q-030"></a>Q-030 | OAuth2 客户端与会话租户未匹配 | closed（委托链校验同族，按 2026-09-30 本次合并要求归入 Q-029；问题仍 open） | [Q-029](#q-029) | 2026-09-30 |
| <a id="q-026"></a>Q-026 | 组织根节点 DDL 注释不一致 | closed（注释漂移同族，按 2026-09-30 本次合并要求归入 Q-015；问题仍 open） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-019"></a>Q-019 | 过滤后树无法解析父组织名 | closed（树过滤与展示同族，按 2026-09-30 本次合并要求归入 Q-038；问题仍 open） | [Q-038](#q-038) | 2026-09-30 |
| Q-046 | 旧 /sync 写路径在 OPERATION_ADMISSION 下「能删不能增」——新路由整批 20071 且错误码指错方向 | closed（2026-09-28 随 T-ACCESS-062 收敛——登记时拍板的设想方向②落地：`POST /api/access/service-config/sync` 端点、`ServiceConfigSyncReq` DTO、`InterfaceSyncDefinition.from` v1 适配与前端「仅接口登记（旧协议）」选项整体删除（404 负向锁=LegacyInterfaceRetirementTest），接口声明唯一入口=sync-v2；契约 §25.1 同批改写） | [T-ACCESS-062](archive/2026-10-01/tasks/T-ACCESS-062.md) | 2026-09-28 |
| Q-033 | 契约总册 org CRUD 门禁行/正文未带岗位精化码 | closed（2026-09-24 随 T-ACCESS-055 doc-only 收敛——§4 门禁表三行+§8.4~§8.6 补「按目标 orgType 解析精化码（岗位 *_POSITION）」注记，与 org-user-permission-contract 对齐；[历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行；正文条目 2026-09-25 补迁本索引） | [T-ACCESS-055](archive/2026-09-24/tasks/T-ACCESS-055.md) | 2026-09-24 |
| Q-036 | PositionTab 展示面两处存量：位置列恒「-」与成员加载失败落空态 | closed（T-FE-058 done：①index.vue 传 org-tree prop 修复父路径解析；②展开区三态区分（成员列表/失败占位+重试/暂无成员），失败不再误显空态） | [T-FE-058](archive/2026-09-24/tasks/T-FE-058.md) | 2026-09-23 |
| Q-035 | 新增岗位弹窗 initialData.parentOrgId 通道失效——上级恒默认根组织 | closed（T-FE-058 done：openCreatePositionDialog 改传 parentOrgId/parentOrgName prop 对齐 index.vue 先例；浏览器实测上级预选「默认组织」、不手选直接提交创建成功） | [T-FE-058](archive/2026-09-24/tasks/T-FE-058.md) | 2026-09-23 |
| Q-025 | UserOrgAppServiceImpl 读面 resolveDefaultTreeOrgIds 私有副本与新共享入口并存 | closed（2026-09-22 随 T-ORG-003 收敛：换绑 OrgTreeConfigDomainService.resolveDefaultTreeOrgIds 共享入口并删除私有副本——[历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-21 行绑定的收敛时机兑现；退化根（配置在而根失联）由共享入口空返回统一折算 ORG_TREE_CONFIG_NOT_FOUND，正常形态两实现等价；顺带清无调用方死 helper isPositionOrg） | [T-ORG-003](archive/2026-09-24/tasks/T-ORG-003.md) | 2026-09-22 |
| Q-016 | logOut 本地清理被服务端注销 await 推迟 + T-FE-048 两变体（同会话并发无 single-flight、跨会话旧响应覆盖） | closed（T-FE-054 done：2026-09-20 AskUserQuestion 四问拍板「注销改 fire-and-forget」——四子项全收口：①注销请求发出即不等（黑洞挂 ≤10s 消除）②本地清理同步段完成、注销完成后不再补清理（新登录凭据竞态消除，回归锁含红跑态 try/finally 收尾防污染）③refreshSessionCapability 入口内共享在途 Promise single-flight（指纹=accessToken，同会话并发只发一次 user-menu）④refreshUserMenu 回写（Pinia+userKey）与侧栏重建前代际守卫（旧会话响应〔成功/失败〕不污染新会话）。原登记行方向 B〔令牌代际守卫保留 await〕随拍板弃用；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-20 行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| Q-020 | /menu-retry 页会话过期后「重新检查菜单」按陈旧状态提示（本地凭证已无时不发请求） | closed（T-FE-054 done：2026-09-20 拍板「随本卡收口」——initRouter 开头无凭证分支：统一提示「会话已过期」+ logOut 跳登录 + 抛 SessionExpiredError，menu-retry retry() catch 后不再按陈旧 menuLoadFailed 弹失真业务提示。判定挂在会话能力初始化统一入口（initRouter），非 menu-retry 单点判 token——2026-09-19「不做单入口判空」口径的落地形态；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| Q-017 | user/index.vue 死解构 + 组织点击双请求（useUserManage 双实例各发一次 /user/page） | closed（T-FE-051 done：2026-09-19 AskUserQuestion 拍板「就地化」——index.vue 删除整个 useUserManage 实例（未消费解构整体清零），selectedOrgId 就地化本地 ref，onOrgChange 不再调 loadTable，成员表加载由 MemberTab watch(orgId)→onSearch 链路独占（点组织单请求、挂载即首载一次）；重复点击同一节点行为不变（watch 值不变不触发，原 index 实例刷新本就无人消费）。同卡两项拍板之二：handleToggleStatus 移入 hook 透后端 error.message。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行） | [T-FE-051](archive/2026-09-20/tasks/T-FE-051.md) | 2026-09-19 |
| Q-013 | TaskExecutionLeaseConcurrencyTest 剩余两个裸 sleep(1200) 方法未改有界轮询 | closed（T-ACCESS-051 done：takeoverAfterExpiryPreventsOldHolderFromOverwriting 改 5s 有界轮询至 tryClaim 接管成功、takeoverReexecutesWithSameIdempotencyKey 改每轮扫描+终态检查（断言语义均不变），终态条件抽 isTerminal 与 awaitTerminal 共用；双轨评审零 P0-P2；定向容器轨 10/10 绿 + 收口全量含 E2E 1724 项 0 失败。纪律出处 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-06/2026-09-16 行；评审上报三处同族裸 sleep 登记 Q-014） | [T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) | 2026-09-18 |
| Q-008 | SERVICE/API 固定图种子行维持 MANAGED，是否声明内部来源收紧 | closed（T-PERM-069 done：2026-09-18 用户拍板「仅 API 收紧」——①API 种子声明 SYNC+access-service，唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，管理面资源 CRUD 20055（回归锁旧种子下实证失败）；②SERVICE 维持 MANAGED（新行唯一通道=管理面手工建行做按服务实例级授权，收紧即零 writer 死局，重启评估须以 service-config 联动建行配套为前置）；存量 dev 库 86 行 MANUAL 全为固定图零野行、订正语句登记 runbook；双轨评审全处置、全量含 E2E 1721 项 0 失败。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-18 行） | [T-PERM-069](archive/2026-09-18/tasks/T-PERM-069.md) | 2026-09-18 |
| Q-007 | sync 通道跨类型父子边是否收紧为同类型父边 | closed（T-PERM-068 done：三定案全落地——①sync/full-sync 显式异类型父边 NON_RETRYABLE/PARENT_TYPE_MISMATCH（先于父解析与版本写入）+ 缺省回填同类型（契约 §19.2 原意兑现，半传静默解挂漂移同步修复）；②管理面 create/batch-create 对齐 move 20053 + 单条裸 parentId 补存在性/类型校验；③判定面闭包止步与 remove 跨类型级联守卫保留作 DB 直写脏数据防线。10 回归锁旧实现下实证失败；全量含 E2E 1716 项 0 失败。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-17 行） | [T-PERM-068](archive/2026-09-17/tasks/T-PERM-068.md) | 2026-09-17 |
| Q-006 | ORG_VISIBILITY 缓存 key 改名后的滚动发布双命名空间失效 | closed（T-ACCESS-048 done：ORG_VISIBILITY_LEGACY evict-only 别名 + flush 同批双 evictAll + 未知覆盖键启动 WARN + 三处回归锁；部署镜像日志实证 legacy evict 生效；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-16 处置行，机制入 dual-layer-cache-framework skill 双副本。滚动发布过渡窗口结束后删除别名与第二次 evictAll 即回退面。2026-09-24 T-ACCESS-054 终结：用户确认无 pre-T-ACCESS-048 构建实例在跑（U011 拍板），别名条目+第二次 evictAll+三处回归锁整体删除，AccessCacheCatalogBoundaryTest 升反射精确集双向锁（9 条目），机制模式留 skill 双副本供未来 catalog 改名复用） | [T-ACCESS-048](archive/2026-09-16/tasks/T-ACCESS-048.md)、[T-ACCESS-054](archive/2026-09-24/tasks/T-ACCESS-054.md) | 2026-09-16 |
| Q-009 | 存量跨能力 mapper 直读收敛（19 类 30 边冻结白名单的后续消化） | closed（T-ACCESS-043~046 done：四批全量收敛 30 边至零、白名单退役为零容忍绝对禁断、负向自证改测试源集夹具；全量回归含 E2E 绿 + 双轨评审；定案与硬契约见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-15 两行） | [capability-mapper-convergence-plan](archive/2026-09-15/capability-mapper-convergence-plan.md)（T-ACCESS-043~046，已归档） | 2026-09-15 |
| Q-001 | URL 路径风格统一（admin 裸路径 vs perm 前缀路径） | closed（T-ACCESS-042 done：全链路单命名空间 /api/access/**——外部=服务路径、无 Gateway StripPrefix、无 admin/perm 家族段；登录族并入 /api/access/auth/**；user-role/list 双轨碰撞管理轨改名 view；一次性切换零兼容。定案与实施期裁决见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-15 行） | [T-ACCESS-042](archive/2026-09-16/tasks/T-ACCESS-042.md) | 2026-09-15 |
| Q-002 | USER 写入口自身豁免的范围限定 | closed（T-PERM-067 done：收窄为档案字段——档案字段豁免保留、启停/删除不豁免（admin 轨 /user/update 自禁对齐 CANNOT_DISABLE_SELF 硬禁 + perm 轨死分支语义统一）、/user/reset-password 定位自助改密通道；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-14 行；盘点修正=可达暴露面全在 admin 轨） | [T-PERM-067](archive/2026-09-14/tasks/T-PERM-067.md) | 2026-09-14 |
| Q-003 | operationCodeKey 族大小写口径不一致（授权域归一 vs 查询域裸拼） | closed（T-PERM-066 done：raw 严格化——入站 DTO @Pattern 大写 400/90001 + 定义侧锁死 + 授权域归一退役两域统一 raw；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-14 行，契约总册 §2.5 集中注记） | [T-PERM-066](archive/2026-09-14/tasks/T-PERM-066.md) | 2026-09-14 |
| Q-004 | BusinessKeys / SyncKeyCodec 命名偏离 XxxUtil 规范 | closed（2026-09-14 轻量清扫批次：`BusinessKeys`→`BusinessKeyUtil`、`SyncKeyCodec`→`SyncKeyCodecUtil`，按 project-rules §6.2「去掉末尾 s」规则机械改名；代码+测试+XML 注释+skills 双副本+AGENTS+活设计文档（34+17 文件）同批替换，[历史定案原文](archive/2026-09-26/decision-registry-before.md) 带日期历史行不改写；golden 锁测试随类更名 `BusinessKeyUtilParityTest`） | [2026-09-14 批次四](archive/2026-09-14/README.md) | 2026-09-14 |
| Q-011 | 资源依赖页（3.4）页面级真实联调与 mock 退役缺口 | closed（T-FE-044 done 且验收覆盖：Gateway +6 端点 + mock 退役 + 六场景冒烟；联调并修复 api 路径 /perm 前缀缺陷——登记时「api 层已按契约对齐」断言的路径部分被证伪，URL 契约锁 6 用例钉住） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| Q-012 | mock/refreshToken.ts 模板死文件（拦截虚构端点、零调用） | closed（随 Q-011 并入 T-FE-044 顺带删除） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| Q-010 | SystemConfigMapper.selectByTenantId 零消费死方法 | closed（随 2026-09-14 轻量清扫批次顺带删除：接口方法 + XML 语句；全仓零调用 T-ACCESS-037 已双轨核实，删除后 SystemConfigAppServiceImplTest 10/10 绿；无任务卡载体，登记口径即顺带删） | —（2026-09-14 归档清扫批次，见 archive/2026-09-14/README.md 批次二） | 2026-09-14 |
| Q-005 | 权限视图/排查页删除后新形态重做 | closed（重复——任务层已有安排载体：T-FE-043 随卡归档「新形态另立任务」+ T-PERM-059 定案③「另立任务」；2026-09-13 用户裁定：已有任务载体的事项不登记问题清单，重做启动时从看板计数器取号） | T-PERM-059（done）、T-FE-043（cancelled，已归档） | 2026-09-13 |
