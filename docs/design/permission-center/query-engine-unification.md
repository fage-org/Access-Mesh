---
doc_type: design
title: 权限查询统一引擎（演进终态设计）
status: evolution
domain: permission-center
last_reviewed: 2026-09-09
---

# 权限查询统一引擎（演进终态设计）

> **定案说明**：本文件为 2026-09-09 用户 grill 访谈定案（Q1-Q15）+ 双子代理评审修订（v2）的终态设计。已定案、未实施——实施任务：T-PERM-057（引擎统一重构）、T-API-003（check 族结果记录回传）、T-PERM-058（depend_on 单点闭合设计）、T-PERM-059（权限视图/排查删除重设计）。**落地时本文件内容并入 `implementation.md` §3 并转约束（adopted），本文件随之 superseded**；当前实现仍以 `implementation.md` §3 现状描述为准。定案已按口径主题分列登记 `decision-registry.md`（2026-09-09 五行）。

## 1. 现状与动机

当前权限查询散在**五套执行形态**，语义分叉：

1. `PermQueryEngine.query()` 主干（6 个工厂入参形态）；
2. `getDeniedResourceCodes`/`getDeniedEntityIds` 手写批量拒绝管线（绕过 query()，语义等价、代码独立）；
3. `/query-resources` 的 `expandResourceScope`——AppService 层对引擎树展开的重复实现（`includeChildren`/`includeInherited` 请求参数 opt-in）；
4. `deleteRoles` 局部级联规则（级联根有权=整棵子树可删，项目规则「父级有权限子级即有权限」唯一判定面落点）；
5. `PermissionGrantDomainServiceImpl` 的 canGrant/授权传递校验——直查 mapper + 自建 covers() 位评估，不评估条件与互斥（2026-09-09 评审发现，原「四套」口径漏计）。

核心缺口：**判定面继承缺位**。引擎 `expandByInheritMode` 只对已加载条目做展示性克隆（`resource_entity_id IN (请求目标)` 查询先行，目标无授权行时展开分支不可达）——「授权在父资源、查子资源」在判定面必拒；`/auth/check` 的 `inheritMode` 参数全链接线但对单目标判定结论无效；`GoldenFixturePgIT` 在测试里手工循环 `selfAndAncestors` 模拟「父授权覆盖子」；`deleteRoles` 以局部规则补丁该语义。

另有两级互斥过滤点不一致（角色互斥 `filterRoleMutex` 仅快照与权限树调用；条目互斥 `filterPermMutex` 在引擎管线；canGrant 管线两者皆无）与评估深度不一致（单点门禁 forValidate 不评估条件/冲突，批量管线评估）——统一引擎一并收口。

## 2. 引擎形态

**一个引擎、一套入参、一个结果模型；多入口 = 参数预设的封装。** 五套执行形态全部收编。

语义统一、实现分层：入参带目标集（类型+实例/编码）→ SQL 下推精确查；不带 → 按角色全量拉。判定面查询天然带目标（走下推），清单/视图查询天然不带（走全量）；调用方无感知，引擎内部按入参选查询形状。

树维度与操作维度在实例查询里是同一 SQL 的两个正交 WHERE（`resource_entity_id IN (闭包)` × `granted_bits & 掩码 != 0`），无组合爆炸；反向覆盖不成立（授 P:EDIT 查 C:MANAGE 不命中，位覆盖单向）。

## 3. 入参模型（终版）

| 入参 | 形态 | 口径 |
|---|---|---|
| 角色集合 | `roleIds` | 引擎只认角色；userId→角色解析放入口封装层（解析+缓存，Q3 定案） |
| 资源类型 | `resourceTypeCodes` **集合** | 单类型场景传单元素集；操作位空间按类型隔离不变（2026-08-30 定案维持） |
| 目标实例 | 编码或实体 id，可选 | 有=判定/下推，无=全量清单 |
| 操作集合 | `operationCodes` | **位覆盖常开不可关**（MANAGE⊇VIEW、自定义操作经 inheritMask 继承 ACCESS 是引擎固有语义，做成开关=授权语义残缺） |
| 主资源上下文 | 类型+编码+操作，可选 | 数据范围面一等入参（depend_on 子权限判定依赖；query-scopes 既有 parentResource\* 参数族收编）；单点面闭合语义归 T-PERM-058 设计 |
| 条件评估 | 三态：评估 / 不评估 / 标记下发 | 标记态即既有 `markConditionsOnly`（条件在网关用真实请求上下文评，T-PERM-017 C3） |
| 冲突过滤 | 开关 | 入参化（Q5 定案，非常开）；默认按入口：运行时面开、配置面关 |
| 判定面继承 | 目标闭包方向 | 作用在**查询前**扩大目标集（见 §5）；默认值见 §6 矩阵 |
| 展示面展开 | NONE/CHILDREN/PARENTS/BOTH | 作用在**查询后**克隆结果行（`grantSource=INHERITED`）；默认关、参数显式开 |

补充维度归属（Q6 收编后声明）：`domainCode`（域分类过滤）与 `codeType` 留在结果组装层（引擎结果模型不感知域分类，避免引擎对 DomainClassifyService 的依赖方向反转）——实施时若证明有下推收益再入参化，属实现优化非语义变更。

## 4. 管线阶段

```
0  入口封装：userId → roleIds（缓存）
1  解析：类型值 / 操作 id / 位掩码（位覆盖并入掩码，常开）
2  角色互斥过滤（冲突开关开时；归属见 §10.1）
3  scopeAll 类型级查询
4  目标解析 + 判定面闭包（{目标} ∪ 祖先链入查询）
5  实例查询（目标下推 / 角色全量）
6  条件评估（三态）
7  条目级冲突过滤（开关）
8  展示面展开（参数开时结果克隆）
9  操作投影展开（位覆盖投影轨，展示面消费）
10 结果装配：双轨（原始授权行 + 覆盖投影）+ 辅助 map + 判定结论
```

## 5. 两语义拆分（Q1 定案）

- **判定面继承（目标闭包）**：查目标 X 时把 X∪祖先链（方向按参数）作为查询目标集——改变 allowed/denied 结果。作用于查询前。
- **展示面展开（条目克隆）**：结果记录按父子关系克隆增加（INHERITED 标记）——不改变判定，只改变返回集合内容。作用于查询后。
- **清单面无判定面继承**（评审修订）：判定面继承需要目标集，清单查询（query-resources 等）无目标——「显式开」只能实现为对授权实例集做子孙扩展，与展示面展开 CHILDREN 计算完全重合。清单面的树扩展统一归口**展示面展开**轨道（现状 `includeChildren`/`includeInherited` 契约字段语义不变，收编进引擎）。
- 菜单/资源可见性等读过滤面的继承落位：形态上等价于「对授权集做子孙扩展后逐目标 contains」（一次扩展而非逐目标闭包），实施细节归 T-PERM-057，语义=判定面继承。

## 6. 判定面继承默认值矩阵（Q12 定案 + 评审修订）

| 入口 | 判定面继承默认 | 展示面展开默认 |
|---|---|---|
| 管理面写门禁（code/entityId 两轨） | **开** | 不适用 |
| 读过滤面（菜单可见/组织可见/日志过滤） | **开** | 不适用 |
| /auth/check、batch-check（SDK 契约） | **关** + 可选参数显式开（inheritMode 参数从「对单点判定结论无效」接通为目标闭包真实语义；item 级参数粒度既有） | 不适用 |
| 网关/快照 | 天然关（API 类型扁平无树） | 不适用 |
| 清单/视图面（query-resources 等） | **不适用**（无目标集） | 关 + 参数显式开 |

行为差异提示（runbook FAQ 随 T-PERM-057 回写）：读过滤面开继承=可见集放大（授父组织 VIEW → 子组织行可见）；管理面条件评估拉平=部分写门禁变严（挂时间窗/IP 条件的授权从恒过改为评估后判定）。

## 7. 条件与冲突评估口径

- **条件评估三态**：评估（运行时/门禁面默认）/ 不评估（配置视图面默认——看原始配置）/ 标记下发（快照专用）。
- **管理面门禁拉平为评估**（Q13 定案）：现状单点 forValidate 不评估、批量管线评估的矛盾消除；同一挂条件授权单点放行/批量拒绝的分叉不复存在。
- **冲突过滤入参化**（Q5 定案）：默认运行时面开、配置面关；两级过滤点（角色互斥在角色解析层、条目互斥在条目层）的统一归属见 §10.1。
- **写门禁条件上下文装配**（评审补充）：拉平后含 IP 类条件的授权在写门禁处需要 clientIp 来源（入口封装层从请求上下文装配）或明确空上下文 fail 语义——T-PERM-057 设计定案。

## 8. 结果模型（双轨）

- **原始授权行轨**：命中/全量的 role_resource_permission 记录（roleId、permissionId、resourceEntityId、grantedBits、scopeAll、grantSource、canGrant、conditionId、dependOn）。
- **覆盖投影轨**：按 effectiveBits 展开的最终可用操作投影（`effectiveOperationEntries` 既有形态）——权限串/用户视图消费。
- 辅助 map（resourceMap/operationMap/roleMap，按 include 标志）+ 判定结论（allowed / denied 集合）。
- canGrant 字段入结果模型（吸收原直查管线的授权传递校验面）。

## 9. 消费面归位

| 消费面 | 统一后形态 |
|---|---|
| 网关接口级鉴权 | **快照架构保留**（Q7 定案：业务请求零 RPC 本地判定 + 条件网关评 + TTL/广播失效 + FALLBACK check-interface）；快照构建=引擎标记态结果（API 类型+ACCESS 覆盖位）+ resource_api_mapping join 的入口封装。ACCESS 单一位维持（Q8 现状不改） |
| /auth/check、batch-check、check-interface | **结果记录全量回传**（Q10+D1 定案，推翻 T-API-002 的 check 族裁剪——三端点同口径恢复内部 id 字段族；Query\* 六字段裁剪维持）；实施 T-API-003 |
| 数据权限 query-scopes | 主资源上下文一等入参；DIRECT∪DEPENDENT 语义保留；四态分组维持 |
| 登录权限串 | 维持 TYPE:OP + 实例折算（Q11 定案）；粒度差（串=类型粒度、门禁=实例粒度）为已知事实独立登记；「与网关一致」终态 = 关联派生方向（T-PERM-054）落地后在 {类型:操作} 基座对齐 |
| 菜单/报表可见性 | 实例级精确（现状 getEffectiveResourceAccess 已满足 REPORT:VIEW A/B 分实例）+ 判定面继承默认开（授父分组 VIEW → 子报表菜单可见） |
| 管理面写门禁（两轨）+ canGrant 校验 | 统一引擎入口封装；canGrant 直查管线收编（评审补充归位） |
| 读过滤（组织可见/日志过滤） | 统一引擎；判定面继承默认开 |
| 权限视图/排查 | **删除重设计**（Q14 定案，范围待定归 T-PERM-059）：七端点（effective-permissions/resource-users/role-permissions/effective-roles/resource-tree/explain/recent-changes）+ 前端排查页 + `/query-permission-tree`（D2 定案：零外部消费端点并入评估）；`effective-permission-codes`（登录串端点）**不在删除面** |
| 快照构建 | 引擎标记态查询 + SnapshotAssembler join，网关消费（见上行） |

## 10. 实现边界与已知设计要点（T-PERM-057 设计输入）

1. **两级互斥归属矩阵**：userId→角色解析收进入口封装层后，角色互斥（filterRoleMutex）在封装层统一还是调用方自理必须定案——现状仅快照与权限树过滤角色互斥、其余面只走条目互斥、canGrant 两者皆无，不定义则现状不一致被固化或静默改变。
2. **批量拒绝闭包回映射**：`computeInstanceDenied` 用条目实体构允许集再对目标 contains——继承开启后条目挂在祖先实体上，请求目标不在允许集会被误判 DENIED；批量路径必须把闭包成员命中映射回请求目标（实现成败点）。
3. **闭包边界**：跨类型父链（resource_entity.parent_id 无类型约束，ROLE/ORG/MENU/RESOURCE 四族共表）是否止步于与目标同类型；软删祖先截断（CTE 过滤 delete_flag=0）与投影软删级联的语义自洽性——显式定界。
4. **闭包实现**：递归 CTE 上溯（镜像 `selectDescendantIdsBatch` 的 UNION 去重防环先例，T-PERM-044）；不走 `selectAllValid` 全量图（管理 API 每调用 1-3 门禁，逐次加载全租户资源不可接受）。
5. **缓存键不变**：ROLE_PERM_SNAPSHOT（原始角色权限行）、OPERATION_PERMISSIONS_BY_TYPE（位掩码）、EFFECTIVE_ROLES（归封装层）、网关 gw:interface-snapshot 均不因闭包下推改变键与失效。ORG_VISIBILITY 已由 PermissionChangeAspect flush 无条件租户级 evictAll 覆盖（组织写路径均带 @PermissionChange+markRoles）——继承后可见闭包语义确变但失效机制已闭合，无需新增联动（评审核实，原「补联动失效」为不存在的洞）。
6. **deleteRoles 局部口径回归**：目标∪祖先闭包下逐子孙校验与「级联根有权=整棵可删」结论等价（子孙祖先链必含级联根）；局部注释与 role-manage §8/api-contract §5.2 锚点随落地改写。
7. **GoldenFixturePgIT 收敛**：`selfAndAncestors` 手工模拟改回单点判定（引擎原生闭包后 golden 断言语义一致）。
8. **OAuth2 委托链路显式排除**：OAuth2 资源服务器链路不接入统一引擎（2026-08-22 用户决策维持），implementation §3 落排除声明防重构误接入。
9. **回归面**：hasPermissionByCode/getDeniedResourceCodes 调用点 22 文件全量门禁回归 + 容器轨 + E2E 收口（`mvn test -T 1C`）+ runbook 升级 FAQ（读面可见集变大/写门禁变严的存量行为差异）。

## 11. 实施任务映射

| 任务 | 范围 | 依赖 |
|---|---|---|
| T-PERM-057 | 引擎统一重构主体（§2-§8、§10 全部；含原 T-PERM-045 范围） | — |
| T-API-003 | check 族三端点结果记录全量回传（§9 第 2 行；推翻 T-API-002 check 族部分） | —（建议 057 后实施，非硬依赖） |
| T-PERM-058 | depend_on 子权限单点门禁闭合设计（§3 主资源上下文的单点面语义） | T-PERM-057 |
| T-PERM-059 | 权限视图/排查删除重设计（§9 第 8 行，范围待定） | — |

关联既有任务：T-PERM-054（关联权限自动授权方向）——2026-09-09 方向定案「API 不单独授权、接口权限由操作权限关联派生」，方案未定维持暂缓；T-PERM-045 已取消（范围并入 T-PERM-057）。
