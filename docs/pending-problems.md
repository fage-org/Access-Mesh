---
doc_type: problems
title: 待解决问题清单
counter: Q-058           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-10-04
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：决定（含“不解决”拍板）当轮归所属权威规范或任务，本文件只更新问题状态与引用；不复制规则、例外正文或转出后的实施细节。统一协议见 [文档治理](design/project-rules.md#decision-governance)，主题定位见[定案入口](design/decision-registry.md)。

## 未收敛问题

**阅读约定**：按根因或可共同处理的范围合并，子项各自保留证据、影响与既有边界。旧编号的去向见文末索引；编号合并不代表缺陷修复。原始登记细节见 [合并前快照](archive/2026-09-30/pending-problems-before-consolidation.md)，外部报告核实见 [核实记录](archive/2026-09-30/logic-review-verification.md)。本次整理没有重新运行业务复现。

<a id="q-058"></a>
## Q-058 MenuDomainService 两个零调用树写方法（deleteWithChildren/insertBatch）

- **状态**：open
- **登记**：2026-10-03
- **来源**：T-ADMIN-031 收口后外评（claude 存量观察，逐条核实成立）
- **关联**：[T-ADMIN-031](archive/2026-10-04/tasks/T-ADMIN-031.md)（发现载体）；P3（死代码，无运行时缺陷）

**现象与证据**：`MenuDomainService.deleteWithChildren`（接口 :148 / impl :267）与 `insertBatch(List<SysMenu>)`（:241 / :414）全仓零调用（含测试）。前者是无锁的树批量软删 API——按 §17.1「Controller 写入口统一持锁」纪律，DomainService 层不持锁，未来误用将绕过菜单树写互斥产生孤儿窗口（Q-048 同型）。

**影响与边界**：当前死代码，无运行时影响；风险为未来误用面。menu 三个合法写入口（createMenu/updateMenu/deleteMenu）全部持 SYS_MENU 锁。

**设想方向（未定案）**：删除两个死方法（对齐 Q-010 死方法顺带清理先例），或保留并加「仅限持锁编排内调用」注记；随清单批次评估。

<a id="q-056"></a>
## Q-056 apiRouteResourceKey 拼接存在 Q-044 同型理论碰撞面

- **状态**：open
- **登记**：2026-10-01
- **来源**：T-PERM-096 类推扫描（用户拍板登记）；[Q-044](#q-044) 同型
- **关联**：[T-PERM-096](archive/2026-10-04/tasks/T-PERM-096.md)（类推发现记录）；P3（理论可构造、现实未发生）

**现象与证据**：`BusinessKeyUtil.apiRouteResourceKey(method, path, resourceCode)` 以 `|` 拼三段做映射同步的内存索引，消费点 [MappingSyncHandlerImpl.java:79/90/95](../access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/domain/impl/MappingSyncHandlerImpl.java)（存量行索引、incoming 活跃集、按资源 id 拼回查）。`pathPattern` 为 URL 自由文本（`|` 是合法 URL 字符无需转义）、`resourceCode` 自由文本且处尾段之前——与 Q-044 已修的「多个自由文本段相邻拼接」同形态。

**影响与边界**：碰撞需同时满足「某行 path 含 `|`」且「同方法下另一行字段恰可拼出同串」（如 `POST|/api/x|y` 与 path=`/api/x`、resourceCode=`y` 的行），当前实际注册路径未出现该形态；后果为同步过期清理误判（漏删/误删映射行），不涉及权限判定面。修法方向可循 Q-044 元组化先例（record 键或 percent-encode 中段）；属 Q-044 设想「内部消费者全量迁移分开评估」范围，随问题清单批次排期。

## 已收敛（含合并索引；详情在关联问题/任务或历史来源）

> closed（合并）只关闭旧登记编号，实际问题由关联 open 条目继续承载；修复、重复与合并分别注明，不回收编号。合并依据为 2026-09-30 本次“重复或类似问题精练合并”要求，原证据见 [合并前快照](archive/2026-09-30/pending-problems-before-consolidation.md)。

| Q-ID | 标题 | 收敛形态 | 关联 | 收敛日期 |
|---|---|---|---|---|
| <a id="q-032"></a>Q-032 | 创建挂载成员动作码与岗位裁剪边界 | closed（关联任务已验收，已确认的独立边界保持） | [T-ORG-005](archive/2026-10-04/tasks/T-ORG-005.md) | 2026-10-04 |
| <a id="q-038"></a>Q-038 | 树过滤展示根、资源状态与父组织名称 | closed（关联任务已验收，已确认的独立边界保持） | [T-ACCESS-065](archive/2026-10-04/tasks/T-ACCESS-065.md) | 2026-10-04 |
| <a id="q-015"></a>Q-015 | 设计、契约与代码注释漂移 | closed（文档/注释主题核对与原样 schema 验证完成；无本卡运行时行为变更） | [T-ACCESS-066](archive/2026-10-04/tasks/T-ACCESS-066.md) | 2026-10-04 |
| <a id="q-040"></a>Q-040 | 服务凭证覆盖运行时查询规划 | closed（运行时查询及剩余同步凭证接线已落地；真实密钥轮换按部署步骤执行） | [T-ACCESS-068](archive/2026-10-04/tasks/T-ACCESS-068.md)、[T-ACCESS-079](archive/2026-10-04/tasks/T-ACCESS-079.md)、[T-ACCESS-080](archive/2026-10-04/tasks/T-ACCESS-080.md) | 2026-10-04 |
| <a id="q-043"></a>Q-043 | 可选字段显式清空协议 | closed（逐域边界、DTO、真实 NULL 与已有表单已由 T-API-006 实施） | [T-API-005](archive/2026-10-04/tasks/T-API-005.md)、[T-API-006](archive/2026-10-04/tasks/T-API-006.md) | 2026-10-04 |
| <a id="q-045"></a>Q-045 | 外部查询不开放 TRACE 的适配边界 | closed（关联任务已验收，范围与证据见任务） | [T-PERM-102](archive/2026-10-04/tasks/T-PERM-102.md) | 2026-10-04 |
| <a id="q-028"></a>Q-028 | 主体组共享遍历等价重构 | closed（关联任务已验收，范围与证据见任务） | [T-PERM-101](archive/2026-10-04/tasks/evidence/T-PERM-101/verification.md) | 2026-10-04 |
| <a id="q-024"></a>Q-024 | 树根重叠守卫与并发互斥 | closed（关联任务已验收，范围与证据见任务） | [T-ORG-004](archive/2026-10-04/tasks/T-ORG-004.md) | 2026-10-04 |
| <a id="q-018"></a>Q-018 | 业务字段空白与列宽校验 | closed（关联任务已验收，范围与证据见任务） | [T-ADMIN-033](archive/2026-10-04/tasks/T-ADMIN-033.md) | 2026-10-04 |
| <a id="q-049"></a>Q-049 | cron 写前校验与本实例注册观测 | closed（关联任务已验收，范围与证据见任务） | [T-ADMIN-032](archive/2026-10-04/tasks/T-ADMIN-032.md) | 2026-10-04 |
| <a id="q-029"></a>Q-029 | OAuth2 委托租户与用户有效性 | closed（同租户及每次请求动态用户检查已由 T-ADMIN-035 实施） | [T-ADMIN-034](archive/2026-10-04/tasks/T-ADMIN-034.md)、[T-ADMIN-035](archive/2026-10-04/tasks/T-ADMIN-035.md) | 2026-10-04 |
| <a id="q-022"></a>Q-022 | grant_dep_id 闲置列去留评估 | closed（删除定案与 T-PERM-105 实施已落地） | [T-PERM-103](archive/2026-10-04/tasks/T-PERM-103.md)、[T-PERM-105](archive/2026-10-04/tasks/T-PERM-105.md) | 2026-10-04 |
| <a id="q-037"></a>Q-037 | 停用主体入口失败并静默清空授权草稿 | closed（入口禁用提示；失效预选确认放弃，取消保留草稿） | [T-FE-061](archive/2026-10-04/tasks/T-FE-061.md) | 2026-10-04 |
| <a id="q-021"></a>Q-021 | 菜单种子图标未完整注册 | closed（T-FE-062：离线注册补齐，保留既有键；coins/history 使用 coin/clock 图形） | [T-FE-062 证据](archive/2026-10-04/tasks/evidence/T-FE-062/verification.md) | 2026-10-04 |
| <a id="q-014"></a>Q-014 | 会话、网关测试依赖固定 sleep 构造时序 | closed（真实到期轮询、续写验证及同族固定余量清扫完成；完整并行回归通过，跨层安全预算按明确范围保留） | [T-ACCESS-067](archive/2026-10-04/tasks/T-ACCESS-067.md) | 2026-10-04 |
| <a id="q-057"></a>Q-057 | 用户角色分配入口的 roleKey/subjectKey 拼接与 roleTypeDomainKey+split 反解可构造碰撞 | 已修复（随 T-PERM-104 收敛，2026-10-03 拍板 C 双管齐下）：三写入口标识码 `subjectTypeCode`/`roleTypeCode`/`domainCode` 补 @Pattern（^[A-Z][A-Z0-9_]*$，与建域/建类型入口同款；`domainCode` 空串从「视为 null」改拒 400——全局域须显式传 null）+ 服务层分组/回读键改 record 元组键（split 反解删除，`BusinessKeyUtil.subjectKey/roleKey/roleTypeDomainKey` 三键退役）；核实修正登记口径两处（subjectKey 侧无静默碰撞随批统一元组化、batch-assign 链路无暴露——注解为入口族一致性对齐）；碰撞对回归锁旧实现实证红 2（「期望异常未抛」=静默错配形态）+ @Pattern 用例红 3；契约 §10.4 落账 | [T-PERM-104](archive/2026-10-04/tasks/T-PERM-104.md) | 2026-10-03 |
| <a id="q-048"></a><a id="q-048-closed"></a>Q-048 | 菜单创建、删除缺树写互斥 | 已修复；createMenu/deleteMenu 补 SYS_MENU 树写锁（锁先于门禁与首次树读取，对齐 updateMenu 与 §17.1 锁序），并发「删父+挂子」两方向交错分别收敛为 10201/10204；契约与验证见关联任务 | [T-ADMIN-031](archive/2026-10-04/tasks/T-ADMIN-031.md) | 2026-10-03 |
| <a id="q-031"></a>Q-031 | 同步资源 codeType 与业务键归一不一致 | 已修复；契约与验证见关联任务（T-PERM-100 两拍板：存量=无部署无存量不订正、寻址侧 TypeResolution 一并 trim 扩面；写入/寻址/发布指纹同源归一，契约 §12.1/§19.1/§19.2/§19.7） | [T-PERM-100](archive/2026-10-04/tasks/T-PERM-100.md) | 2026-10-03 |
| <a id="q-023"></a>Q-023 | 删除类型所有者角色缺引用守卫或提示 | 已修复；契约与验证见关联任务（T-PERM-099 三拍板：硬守卫整批拒绝+覆盖缺省引用+sync 通道扩面收口；契约 §10.3/§13.1/§19.4） | [T-PERM-099](archive/2026-10-04/tasks/T-PERM-099.md) | 2026-10-03 |
| <a id="q-050"></a>Q-050 | 操作位写入与准入目录校验边界不一致 | 已修复；契约与验证见关联任务 | [T-PERM-098](archive/2026-10-04/tasks/T-PERM-098.md) | 2026-10-03 |
| <a id="q-047"></a>Q-047 | 角色重新指派静默忽略窗口或关系变更 | 已修复；契约与验证见关联任务 | [T-ADMIN-030](archive/2026-10-04/tasks/T-ADMIN-030.md) | 2026-10-02 |
| <a id="q-027"></a>Q-027 | 新增分组角色未展开成员检查互斥 | closed（2026-10-02 随 T-PERM-097 收敛——实施核实任务前提与现实断层并经用户拍板改卡：危害场景要求新增行 `target_type='GROUP_ROLE'`，该形态自 T-PERM-043 起无任何写入方（现行写入口全部写死 `'ROLE'`），现行唯一活口（assign/sync 绑存量组角色）产出行在写守卫与运行时**一致不展开**、不发生 Y 静默失效，实际后果为零权限假绑定。改卡落地「绑定面入口收紧」：assign/batch-assign（字符串层+role_type=5 值层双保险）与 user-role sync/full-sync（scope 级、先于服务-类型白名单）拒绑 GROUP_ROLE（20022，对齐角色面先例），revoke 保留为存量行清理通道；持有侧/运行时组展开零改动；碰撞对红跑单测 5+PG 1 旧实现实证红。原「新增侧展开子树」设想随 role_inclusion 单事实源立项〔T-PERM-043 双事实源技术债〕另行处理） | [T-PERM-097](archive/2026-10-04/tasks/T-PERM-097.md) | 2026-10-02 |
| <a id="q-044"></a>Q-044 | 资源复合拼接键碰撞 | closed（T-PERM-096+T-FE-060 done：后端三内存索引（共享批量解析 resourceLookup、转授 resourceEntityIdByKey/结果映射）改结构化 record 元组，checkCanGrant 结果键=GrantCheckKey 本身；BusinessKeyUtil 四拼接构造器退役（resourceCodeTypeKey/resourceTripleCodeKey/grantCheckKey + 类推清扫零调用死方法 resourceTripleValueKey），golden 锁与对外协议键不动；前端 grant-keys.ts 单源 JSON 元组编码收口授权决策五段键/资源三段/树节点键/矩阵行键/主体树角色键 + 类推 changeGroupKey（inlineName 自由文本）；碰撞对回归锁双端旧实现实证红（后端 expected 100 got 200 与 duplicate element、前端 8 红）；元组边界口径入 engine/implementation §8.4、键约定入 permission-grant.md §2.3；类推核实 roleProjectionIndexKey 无碰撞形态（受限段+尾段自由文本）；roleKey/subjectKey 初判「无碰撞」经收口后外评订正（分配入口无 @Pattern+split 反解，登记 Q-057）、apiRouteResourceKey（path 中段自由文本）理论碰撞面登记 Q-056，两者均属「内部消费者全量迁移分开评估」范围随清单批次排期） | [T-PERM-096](archive/2026-10-04/tasks/T-PERM-096.md)、[T-FE-060](archive/2026-10-04/tasks/T-FE-060.md) | 2026-10-01 |
| <a id="q-055"></a>Q-055 | 签名过滤器顺序注释 | closed（重复登记，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-054"></a>Q-054 | 系统配置前端设计陈旧 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-053"></a>Q-053 | 自动授权旧 TODO | closed（注释漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-052"></a>Q-052 | 业务字段缺长度校验 | closed（写入口字段校验同族，按 2026-09-30 本次合并要求归入 Q-018；合并本身不代表修复，当前处置见关联问题） | [Q-018](#q-018) | 2026-09-30 |
| <a id="q-051"></a>Q-051 | v3.5 旧准入设计未标替代 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-042"></a>Q-042 | 用户角色查询端点旧指代 | closed（契约指代漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-041"></a>Q-041 | SDK 总览漏服务凭证拦截器 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-039"></a>Q-039 | 资源树与列表状态过滤不同 | closed（树过滤与展示同族，按 2026-09-30 本次合并要求归入 Q-038；合并本身不代表修复，当前处置见关联问题） | [Q-038](#q-038) | 2026-09-30 |
| <a id="q-034"></a>Q-034 | 岗位可见性未按 VIEW_POSITION 精化 | closed（动作码入口覆盖同族，按 2026-09-30 本次合并要求归入 Q-032；合并本身不代表修复，当前处置见关联问题） | [Q-032](#q-032) | 2026-09-30 |
| <a id="q-030"></a>Q-030 | OAuth2 客户端与会话租户未匹配 | closed（委托链校验同族，按 2026-09-30 本次合并要求归入 Q-029；合并本身不代表修复，当前处置见关联问题） | [Q-029](#q-029) | 2026-09-30 |
| <a id="q-026"></a>Q-026 | 组织根节点 DDL 注释不一致 | closed（注释漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-019"></a>Q-019 | 过滤后树无法解析父组织名 | closed（树过滤与展示同族，按 2026-09-30 本次合并要求归入 Q-038；合并本身不代表修复，当前处置见关联问题） | [Q-038](#q-038) | 2026-09-30 |
| <a id="q-046"></a>Q-046 | 旧 /sync 写路径在 OPERATION_ADMISSION 下「能删不能增」——新路由整批 20071 且错误码指错方向 | closed（2026-09-28 随 T-ACCESS-062 收敛——登记时拍板的设想方向②落地：`POST /api/access/service-config/sync` 端点、`ServiceConfigSyncReq` DTO、`InterfaceSyncDefinition.from` v1 适配与前端「仅接口登记（旧协议）」选项整体删除（404 负向锁=LegacyInterfaceRetirementTest），接口声明唯一入口=sync-v2；契约 §25.1 同批改写） | [T-ACCESS-062](archive/2026-10-01/tasks/T-ACCESS-062.md) | 2026-09-28 |
| <a id="q-033"></a>Q-033 | 契约总册 org CRUD 门禁行/正文未带岗位精化码 | closed（2026-09-24 随 T-ACCESS-055 doc-only 收敛——§4 门禁表三行+§8.4~§8.6 补「按目标 orgType 解析精化码（岗位 *_POSITION）」注记，与 org-user-permission-contract 对齐；[历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行；正文条目 2026-09-25 补迁本索引） | [T-ACCESS-055](archive/2026-09-24/tasks/T-ACCESS-055.md) | 2026-09-24 |
| <a id="q-036"></a>Q-036 | PositionTab 展示面两处存量：位置列恒「-」与成员加载失败落空态 | closed（T-FE-058 done：①index.vue 传 org-tree prop 修复父路径解析；②展开区三态区分（成员列表/失败占位+重试/暂无成员），失败不再误显空态） | [T-FE-058](archive/2026-09-24/tasks/T-FE-058.md) | 2026-09-23 |
| <a id="q-035"></a>Q-035 | 新增岗位弹窗 initialData.parentOrgId 通道失效——上级恒默认根组织 | closed（T-FE-058 done：openCreatePositionDialog 改传 parentOrgId/parentOrgName prop 对齐 index.vue 先例；浏览器实测上级预选「默认组织」、不手选直接提交创建成功） | [T-FE-058](archive/2026-09-24/tasks/T-FE-058.md) | 2026-09-23 |
| <a id="q-025"></a>Q-025 | UserOrgAppServiceImpl 读面 resolveDefaultTreeOrgIds 私有副本与新共享入口并存 | closed（2026-09-22 随 T-ORG-003 收敛：换绑 OrgTreeConfigDomainService.resolveDefaultTreeOrgIds 共享入口并删除私有副本——[历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-21 行绑定的收敛时机兑现；退化根（配置在而根失联）由共享入口空返回统一折算 ORG_TREE_CONFIG_NOT_FOUND，正常形态两实现等价；顺带清无调用方死 helper isPositionOrg） | [T-ORG-003](archive/2026-09-24/tasks/T-ORG-003.md) | 2026-09-22 |
| <a id="q-016"></a>Q-016 | logOut 本地清理被服务端注销 await 推迟 + T-FE-048 两变体（同会话并发无 single-flight、跨会话旧响应覆盖） | closed（T-FE-054 done：2026-09-20 AskUserQuestion 四问拍板「注销改 fire-and-forget」——四子项全收口：①注销请求发出即不等（黑洞挂 ≤10s 消除）②本地清理同步段完成、注销完成后不再补清理（新登录凭据竞态消除，回归锁含红跑态 try/finally 收尾防污染）③refreshSessionCapability 入口内共享在途 Promise single-flight（指纹=accessToken，同会话并发只发一次 user-menu）④refreshUserMenu 回写（Pinia+userKey）与侧栏重建前代际守卫（旧会话响应〔成功/失败〕不污染新会话）。原登记行方向 B〔令牌代际守卫保留 await〕随拍板弃用；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-20 行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| <a id="q-020"></a>Q-020 | /menu-retry 页会话过期后「重新检查菜单」按陈旧状态提示（本地凭证已无时不发请求） | closed（T-FE-054 done：2026-09-20 拍板「随本卡收口」——initRouter 开头无凭证分支：统一提示「会话已过期」+ logOut 跳登录 + 抛 SessionExpiredError，menu-retry retry() catch 后不再按陈旧 menuLoadFailed 弹失真业务提示。判定挂在会话能力初始化统一入口（initRouter），非 menu-retry 单点判 token——2026-09-19「不做单入口判空」口径的落地形态；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| <a id="q-017"></a>Q-017 | user/index.vue 死解构 + 组织点击双请求（useUserManage 双实例各发一次 /user/page） | closed（T-FE-051 done：2026-09-19 AskUserQuestion 拍板「就地化」——index.vue 删除整个 useUserManage 实例（未消费解构整体清零），selectedOrgId 就地化本地 ref，onOrgChange 不再调 loadTable，成员表加载由 MemberTab watch(orgId)→onSearch 链路独占（点组织单请求、挂载即首载一次）；重复点击同一节点行为不变（watch 值不变不触发，原 index 实例刷新本就无人消费）。同卡两项拍板之二：handleToggleStatus 移入 hook 透后端 error.message。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行） | [T-FE-051](archive/2026-09-20/tasks/T-FE-051.md) | 2026-09-19 |
| <a id="q-013"></a>Q-013 | TaskExecutionLeaseConcurrencyTest 剩余两个裸 sleep(1200) 方法未改有界轮询 | closed（T-ACCESS-051 done：takeoverAfterExpiryPreventsOldHolderFromOverwriting 改 5s 有界轮询至 tryClaim 接管成功、takeoverReexecutesWithSameIdempotencyKey 改每轮扫描+终态检查（断言语义均不变），终态条件抽 isTerminal 与 awaitTerminal 共用；双轨评审零 P0-P2；定向容器轨 10/10 绿 + 收口全量含 E2E 1724 项 0 失败。纪律出处 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-06/2026-09-16 行；评审上报三处同族裸 sleep 登记 Q-014） | [T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) | 2026-09-18 |
| <a id="q-008"></a>Q-008 | SERVICE/API 固定图种子行维持 MANAGED，是否声明内部来源收紧 | closed（T-PERM-069 done：2026-09-18 用户拍板「仅 API 收紧」——①API 种子声明 SYNC+access-service，唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，管理面资源 CRUD 20055（回归锁旧种子下实证失败）；②SERVICE 维持 MANAGED（新行唯一通道=管理面手工建行做按服务实例级授权，收紧即零 writer 死局，重启评估须以 service-config 联动建行配套为前置）；存量 dev 库 86 行 MANUAL 全为固定图零野行、订正语句登记 runbook；双轨评审全处置、全量含 E2E 1721 项 0 失败。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-18 行） | [T-PERM-069](archive/2026-09-18/tasks/T-PERM-069.md) | 2026-09-18 |
| <a id="q-007"></a>Q-007 | sync 通道跨类型父子边是否收紧为同类型父边 | closed（T-PERM-068 done：三定案全落地——①sync/full-sync 显式异类型父边 NON_RETRYABLE/PARENT_TYPE_MISMATCH（先于父解析与版本写入）+ 缺省回填同类型（契约 §19.2 原意兑现，半传静默解挂漂移同步修复）；②管理面 create/batch-create 对齐 move 20053 + 单条裸 parentId 补存在性/类型校验；③判定面闭包止步与 remove 跨类型级联守卫保留作 DB 直写脏数据防线。10 回归锁旧实现下实证失败；全量含 E2E 1716 项 0 失败。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-17 行） | [T-PERM-068](archive/2026-09-17/tasks/T-PERM-068.md) | 2026-09-17 |
| <a id="q-006"></a>Q-006 | ORG_VISIBILITY 缓存 key 改名后的滚动发布双命名空间失效 | closed（T-ACCESS-048 done：ORG_VISIBILITY_LEGACY evict-only 别名 + flush 同批双 evictAll + 未知覆盖键启动 WARN + 三处回归锁；部署镜像日志实证 legacy evict 生效；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-16 处置行，机制入 dual-layer-cache-framework skill 双副本。滚动发布过渡窗口结束后删除别名与第二次 evictAll 即回退面。2026-09-24 T-ACCESS-054 终结：用户确认无 pre-T-ACCESS-048 构建实例在跑（U011 拍板），别名条目+第二次 evictAll+三处回归锁整体删除，AccessCacheCatalogBoundaryTest 升反射精确集双向锁（9 条目），机制模式留 skill 双副本供未来 catalog 改名复用） | [T-ACCESS-048](archive/2026-09-16/tasks/T-ACCESS-048.md)、[T-ACCESS-054](archive/2026-09-24/tasks/T-ACCESS-054.md) | 2026-09-16 |
| <a id="q-009"></a>Q-009 | 存量跨能力 mapper 直读收敛（19 类 30 边冻结白名单的后续消化） | closed（T-ACCESS-043~046 done：四批全量收敛 30 边至零、白名单退役为零容忍绝对禁断、负向自证改测试源集夹具；全量回归含 E2E 绿 + 双轨评审；定案与硬契约见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-15 两行） | [capability-mapper-convergence-plan](archive/2026-09-15/capability-mapper-convergence-plan.md)（T-ACCESS-043~046，已归档） | 2026-09-15 |
| <a id="q-001"></a>Q-001 | URL 路径风格统一（admin 裸路径 vs perm 前缀路径） | closed（T-ACCESS-042 done：全链路单命名空间 /api/access/**——外部=服务路径、无 Gateway StripPrefix、无 admin/perm 家族段；登录族并入 /api/access/auth/**；user-role/list 双轨碰撞管理轨改名 view；一次性切换零兼容。定案与实施期裁决见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-15 行） | [T-ACCESS-042](archive/2026-09-16/tasks/T-ACCESS-042.md) | 2026-09-15 |
| <a id="q-002"></a>Q-002 | USER 写入口自身豁免的范围限定 | closed（T-PERM-067 done：收窄为档案字段——档案字段豁免保留、启停/删除不豁免（admin 轨 /user/update 自禁对齐 CANNOT_DISABLE_SELF 硬禁 + perm 轨死分支语义统一）、/user/reset-password 定位自助改密通道；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-14 行；盘点修正=可达暴露面全在 admin 轨） | [T-PERM-067](archive/2026-09-14/tasks/T-PERM-067.md) | 2026-09-14 |
| <a id="q-003"></a>Q-003 | operationCodeKey 族大小写口径不一致（授权域归一 vs 查询域裸拼） | closed（T-PERM-066 done：raw 严格化——入站 DTO @Pattern 大写 400/90001 + 定义侧锁死 + 授权域归一退役两域统一 raw；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-14 行，契约总册 §2.5 集中注记） | [T-PERM-066](archive/2026-09-14/tasks/T-PERM-066.md) | 2026-09-14 |
| <a id="q-004"></a>Q-004 | BusinessKeys / SyncKeyCodec 命名偏离 XxxUtil 规范 | closed（2026-09-14 轻量清扫批次：`BusinessKeys`→`BusinessKeyUtil`、`SyncKeyCodec`→`SyncKeyCodecUtil`，按 project-rules §6.2「去掉末尾 s」规则机械改名；代码+测试+XML 注释+skills 双副本+AGENTS+活设计文档（34+17 文件）同批替换，[历史定案原文](archive/2026-09-26/decision-registry-before.md) 带日期历史行不改写；golden 锁测试随类更名 `BusinessKeyUtilParityTest`） | [2026-09-14 批次四](archive/2026-09-14/README.md) | 2026-09-14 |
| <a id="q-011"></a>Q-011 | 资源依赖页（3.4）页面级真实联调与 mock 退役缺口 | closed（T-FE-044 done 且验收覆盖：Gateway +6 端点 + mock 退役 + 六场景冒烟；联调并修复 api 路径 /perm 前缀缺陷——登记时「api 层已按契约对齐」断言的路径部分被证伪，URL 契约锁 6 用例钉住） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| <a id="q-012"></a>Q-012 | mock/refreshToken.ts 模板死文件（拦截虚构端点、零调用） | closed（随 Q-011 并入 T-FE-044 顺带删除） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| <a id="q-010"></a>Q-010 | SystemConfigMapper.selectByTenantId 零消费死方法 | closed（随 2026-09-14 轻量清扫批次顺带删除：接口方法 + XML 语句；全仓零调用 T-ACCESS-037 已双轨核实，删除后 SystemConfigAppServiceImplTest 10/10 绿；无任务卡载体，登记口径即顺带删） | —（2026-09-14 归档清扫批次，见 archive/2026-09-14/README.md 批次二） | 2026-09-14 |
| <a id="q-005"></a>Q-005 | 权限视图/排查页删除后新形态重做 | closed（重复——任务层已有安排载体：T-FE-043 随卡归档「新形态另立任务」+ T-PERM-059 定案③「另立任务」；2026-09-13 用户裁定：已有任务载体的事项不登记问题清单，重做启动时从看板计数器取号） | T-PERM-059（done）、T-FE-043（cancelled，已归档） | 2026-09-13 |
