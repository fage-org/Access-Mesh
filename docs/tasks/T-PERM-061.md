---
doc_type: task
id: T-PERM-061
title: EXT-7：batchCheck 逐条 engine.query 收敛——分组 + 请求级共享装载（A+ 形态）
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§3
  - docs/design/permission-center/api-contract.md#§6.1
depends_on: []
blocks: []
acceptance:
  - "背景（审计 S-024 EXT-7，2026-09-11 立项核实）：PermissionCheckAppServiceImpl.batchCheck（:118）逐 item 构造 PermQuery.forAuthCheck 调 engine.query——每 item 一次完整管线，主体级数据重复装载 N 次"
  - "设计定稿（2026-09-11 v4，用户确认，经多轨外部评审逐条核实处置）：A+ = 分组 + 请求级共享装载，挂真实 SQL 路径（不引入 ROLE_PERM_SNAPSHOT 陈旧语义）；核心不变量 = 合并 SQL 切回投影谓词表（类型+组掩码+inheritClosure 分档闭包+targetMode 分轨 depend 过滤）+ 评估粒度（scopeAll 段组内一次/实例段逐 item）与顺序（depend_on 过滤→条件→互斥）+ 空目标集守卫（禁 CTE IN()/实例 SQL 无界扫描）+ reason 树双轨规格 + 条件四态增量快照 fail-close + 父判定审计桶口径；设计正文已回写 implementation §3.10"
  - "三项定案（2026-09-11 用户确认）：A=evaluatedAt 请求级单一时刻（唯一非空 PermEvalContext 强制注入全链，禁 now() 回退）；B=notifyPermConflict 组级去重（(组,ruleId) ledger，detail 按实际命中规则集构造携 hitItemCount，短路 return 前 flush）；C=items 缺 @Valid 已随批修复（commit 1cebf1ad9）"
  - "回归要求（§六 ①-⑪ 全量为准）：容器轨等价差分（GoldenFixturePgIT/TargetModeClosurePgIT 先例，query()×N vs queryBatch 对拍）+ 共享计数锁（N=10 与 N=100 下 mapper 调用次数不变）+ 投影谓词否定锁（默认模式授父查子 deny/TYPE_LEVEL+depend_on deny/跨类型位泄漏 deny）+ 空目标集批（1000 项上限形态）200 全 deny 禁 500 + 互斥真锁（VIEW 行+UPDATE 行（inherit_mask 覆盖 VIEW）+VIEW↔UPDATE 规则）+ reason 边界（幽灵 code+仅子行 scopeAll→DEPENDENT_NOT_IN_PARENT_CONTEXT）+ 时间窗边界锁 + 通知次数/内容锁 + 禁用条件 fail-close 两轨锁 + 条件-互斥顺序锁 + 条件增量快照次数锁；matched 按集合比较；现有引擎 mock 测试改 stub 新入口（不作等价证据）"
  - "性能项非阻断：当前无已知大 item 批量消费方；SDK batch-check 契约不变（a2 时刻语义实施时补 api-contract §6.1 批量口径注记）；实施含 permission-center-coding-standards「engine.query() 或四个显式入口」句扩写与 permission-query-pipeline skill 双副本（.claude/.agents）逐 item 示例同步"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-11
---

# T-PERM-061 EXT-7：batchCheck 逐条 engine.query 收敛——A+ 形态

> 状态：proposed（2026-09-11 立项；同日**设计定稿（v4，用户确认）**——经多轨外部评审（claude/codex-luna/grok/codex-sol）逐条核实处置，处置详情见 decision-registry 2026-09-11 T-PERM-061 行；设计已回写 implementation §3.10，实施未开始）
> 依赖：无硬依赖

## 背景

审计 S-024 登记的 EXT-7 性能项：`PermissionCheckAppServiceImpl.batchCheck` 对 `req.items()` 逐条构造 `PermQuery.forAuthCheck` 调 `engine.query`。**forAuthCheck → TYPE_LEVEL/INSTANCE 管线不经过任何快照缓存**（`loadRolePermEntriesWithCache` 唯一调用方是 LIST 模式 queryList），每次管线真实 IO：角色解析（EFFECTIVE_ROLES L2，冷 miss 多 SQL）；类型/操作码解析（无缓存直查）；目标操作装载 selectValidByIds（无缓存直查）；scopeAll 行 + 实例行（无缓存直查）；条件规则（L2，conditionCache 每次 evaluate 新建）；PERM_MUTEX 规则与操作索引（无缓存直查，按类型循环）；code→entity（无缓存直查，按类型循环）；继承闭包 CTE；父判定（惰性触发时递归完整管线）。热路径单 item ≈ 2-3× Redis + 6+K 条 SQL。

场景实底：大 N 场景按 (类型,操作,修饰符) 分组后组内同构、组数小；真逐项异构 N≤20；组数上界=类型×操作×修饰符组合（类型租户可扩展，最坏≈item 数）——设计目标为「装载共享=常数、组内判定=内存」。纯行级过滤 SDK 引导面是 query-resources 正查询；batch-check 定位是验证形态。

## 设计定稿（v4，2026-09-11 用户确认；已回写 implementation §3.10）

### 一、共享装载（BatchEvalContext，引擎内部，全部 DB 新鲜读）

**BatchEvalContext 为 per-request 实例、经方法参数/查询对象传递——禁止落在引擎字段**（PermQueryEngine 是 @Component 单例，实例字段会跨请求串数据）：

1. **roleIds ×1** + 「已尝试解析」显式状态（resolveRoleIds/resolveEntityIds/resolveOperationIds 三处预置钩子均 nonEmpty 才生效——空集注入会重新触发解析；roleIds 侧空集本就整批 NO_ROLE 前置返回，实体侧必需显式状态）。
2. **类型与操作解析 ×1**：全 item 类型（不含父类型——见 7）与全 (type,op) 对一次解析（经 ResolveContext 扩容或等价批量入口）。
3. **scopeAll 行 ×1**：全组 BitMaskEntry 合并一次 selectScopeAllPermsByBitsBatch（SQL 多类型参数化，谓词为 (resource_type, granted_bits&mask) OR 链）。
4. **实例行 ×1（分段化装载）**：仅对 **scopeAll 段评估未放行的组**，合并其目标∪闭包一次 selectInstancePermsByBitsBatch——scopeAll 命中即短路是既有优化（引擎类注释「scopeAll 优先匹配：匹配后跳过实例级查询」），批量路径按组两阶段保持：先 scopeAll 评估 → 未放行组才做实例装载/评估。
5. **闭包映射 ×1（按档分用）**：对 4 中未放行组且 **inheritClosure=true** 的 item 目标，一次 selectSelfAndAncestorClosureBatch → `Map<targetId, Set<closureId>>`；**false 档 item（含默认缺省——forAuthCheck 不置 inheritClosure，null 落 false）闭包集恒为 {targetId}，不得消费 CTE 映射**（现状 queryEntityIds 仅 true 档扩闭包；共享映射不分档=默认模式获得祖先继承=越权放行方向）。
6. **entity 预解析 ×1**：全部实例 item 的 code 合并一次 batchResolveResourceIds，**按返回 Map 键取**（resolveEntityIds 现状 values() 合并丢弃 key——存量缺陷，批量不可复用该形态）；「已尝试」状态含全 miss。
7. **父判定 ×1（惰性保留）**：共享 LazyParentCheck（仅当某组命中集确含子行才触发第一次执行）；共享只经既有注入面——`checkParentResource` 的 `setRoleIds` + 已钉住 evalContext；**父类型/父操作解析不并入共享 ResolveContext**（父判定每请求仅一次，其内部解析天然一次；prepareResolveContext 无注入点，强并入需改 PermQuery/签名且无收益）。父判定递归内部 resolveMatchedOperationCodes 的操作解析为已知残余 IO（正确性无差，实施时可评估带上下文的内部入口，非必须）。
8. **PERM_MUTEX 静态数据 ×1**：规则一次装载；操作索引按 distinct 类型 O(K)；**只共享装载，不共享计算**（集合语义见下）。
9. **请求级增量条件快照（四态建模）**：**需新增批量条件快照接口**（PermissionConditionDomainService 扩展）。快照形态 = `conditionId → LoadedRules 四态（OK / NOT_FOUND / DISABLED / INVALID）`，**仅 enabled=true 且解析成功的规则作为 OK 写入 CONDITION_RULES 正缓存（putBatch 带 beginRead 剩余 TTL）**；缺失/禁用/解析失败记录请求级失败状态，评估 fail-close（与单条 loadRules 语义一致——selectValidByIds 只滤租户+软删不滤 enabled，朴素批量会把禁用条件当有效规则入缓存=权限绕过方向）。**增量装载**：scopeAll 段、实例段、父判定各自在行集到手后只批量加载**新出现**的 conditionId（每阶段至多一批次 getBatch+miss 回源；同 ID 请求内至多回源一次）——分段化与「预取 ×1」存在数据依赖环（scopeAll 放行判定需要条件、实例行条件 ID 要实例装载后才知、父判定条件 ID 要父查询后才知），严格 ×1 不可达；验收口径 = 每阶段至多一次 + 同 ID 请求内至多回源一次，配缓存/Mapper 次数锁。
10. **evaluatedAt（定案 a2）**：批量入口构造**唯一且非空**的 `PermEvalContext(ip, now(), attrs)` 挂全链——全部 item 评估、父判定递归、条件评估共用同一实例；**禁止**各 item 经 fromCallerMap 后再进 query() 各自重钉（query() 的 evaluatedAt==null 分支会各自 now()，定案落空）；批量路径禁止 now() 回退（toEvalMap null 分支不触达）。
11. **不调 loadAncillary**：批量路径直接消费条目集合构造 AuthCheckItemResult（只要 allowed/reason/matchedRoleIds/matchedPermissionIds——均由条目派生，不需要 resourceMap/operationMap/roleMap）；规避 loadAncillary 首行无条件 resolveOperationIdsForAncillary 的逐 item 无缓存 SQL。

**空目标集守卫**：可解析 entityId 并集为空（纯 TYPE_LEVEL 批——resourceCode=null 是文档化合法输入；或全幽灵 code 批）时**不调用**闭包 CTE（foreach 空集生成 `IN ()` = PG 语法错误 500）与实例 SQL（`<if>` 空集静默丢实体过滤 = 无界全量行装载），各 item 走目标空 reason 树；Java 侧 queryInstance 对空 entityIds 直接返回 List.of() 不依赖 XML 行为。闭包总规模（1000×深度）维持 registry 2026-09-10 已登记改进项口径（PG 可承受，分片待真实负载证据）。

### 二、分组与判定

分组键：**(targetMode, resourceTypeCode, operationCode, codeType, domainCode, inheritMode)**；parentResource 请求级共享不进键。

**合并 SQL 切回投影谓词不变量**（超集 SQL 切回各组/item 的收窄必须与现状逐条路径逐谓词同构——预置操作经 CROSS JOIN 各类型同四位（VIEW=2 等），位值跨类型数值相同，缺类型谓词即跨组泄漏）：

| 段 | 投影谓词（全部满足） |
|---|---|
| 组 scopeAll 子集 | `resourceType == 组类型` AND `(granted_bits & 组 coveringMask) != 0`；TYPE_LEVEL 组再 **`dependOn == null`（无条件丢子行，不走父上下文过滤——queryTypeLevel 现状）**；INSTANCE 组再 filterDependentEntries（无父剥子行/有父按父命中集留） |
| item 实例子集 | `resourceType == 组类型` AND 位掩码命中；`entityId ∈ (item.inheritClosure ? cteClosure[target] ∪ {target} : {target})`；再 filterDependentEntries |
| 空并集 | 可解析 entityId 为空 → 不调用 CTE 与实例 SQL（见上守卫） |

**评估粒度不变量**：scopeAll 段组内一次评估（子集与 item 无关）；实例段逐 item（PERM_MUTEX 集合语义——filterPermMutex 对传入条目整体算 opIds，规则两端都在场才冲突且两端全丢；合并评估必不等价）。**评估顺序不变量**：每个投影子集固定 `depend_on 过滤 → 条件评估 → PERM_MUTEX 计算`（现状序：depend_on 过滤在调用方——TYPE_LEVEL 组 queryTypeLevel:161 无条件丢子行、INSTANCE 组 filterDependentEntries:194/:232；条件→互斥在 evaluateIfNeeded:921-928；新批量路径不得重排）——反例：同一资源 VIEW 行挂不满足条件 + 无条件 UPDATE 行（inherit_mask 覆盖 VIEW）+ VIEW↔UPDATE 互斥规则：现状先摘 VIEW → 互斥两端不齐 → UPDATE 仍放行 VIEW；若先算互斥 → 两行全丢 = false deny + 虚假冲突审计。可达反例（修正构造）：同组两 item 各持不同单 bit 行（如 VIEW 行与 UPDATE 行，UPDATE 的 inherit_mask=2 覆盖 VIEW 位 → 查 VIEW 时 coveringMask 把 UPDATE 位纳入）构成互斥对——逐 item 评估各自 allowed、合并评估双 denied。**MANUAL 行单 bit 依据 = DDL CHECK 为来源条件式（非 MANUAL 不受约束）+ 当前全部写入口仅 MANUAL 单 bit 两条（apply-grant-plan/bootstrap）；未来 AUTO_DEP 落地是复合位唯一潜在来源**。存量语义记录：无精确 op 匹配的 granted_bits 行被静默排除出互斥判定（findIndexedByResourceTypeAndBinaryBit 精确查表 null → filter 掉）——不可用「位掩码命中」近似「opIds 命中」；**computeInstanceDenied（getDenied 族的并集互斥回映射）不可复用于 check 族实例段**。

### 三、结果拆分（reason 树双轨规格）

- **NO_ROLE**：共享角色集空 → 全批 reason=NO_ROLE（query 入口层，与 USER_NOT_FOUND 同层前置）。
- **输出顺序**：`ResultSlot(originalIndex, rawEntries, evaluatedEntries, reason)` 按原始输入序输出（响应无 item 关联字段）。
- **拒绝项 matched 字段族必须空列表**（raw 条目仅内部推导 reason 不回传内部 ID）。
- **INSTANCE 轨**（两段各自对齐 queryInstanceMode 分支序）：
  - 目标空（code 预解析失败）：`scopeAllEvaluatedEmpty` → CONDITION_NOT_MET_OR_CONFLICT ＞ `dependentOnlyExcluded` → DEPENDENT_NOT_IN_PARENT_CONTEXT ＞ NO_PERMISSION；
  - 目标非空且评估清空：`(instanceMatchedBeforeEval || scopeAllEvaluatedEmpty)` → CONDITION_NOT_MET_OR_CONFLICT ＞ `dependentOnlyExcluded` → DEPENDENT_NOT_IN_PARENT_CONTEXT ＞ NO_PERMISSION。
  - 组 scopeAll 评估通过 → 该组全部 item allowed（无论 code 可解析与否，短路优先）。
- **TYPE_LEVEL 轨**：`scopeAllMatchedBeforeEval ? CONDITION_NOT_MET_OR_CONFLICT : NO_PERMISSION` 二值；depend_on 子行被静默丢弃（:161 无条件过滤），**永不产出 DEPENDENT_NOT_IN_PARENT_CONTEXT、无目标不可解析分支**。
- 数据时点登记：READ COMMITTED 下批量化把「逐 item 各语句各看各的」变为「批内一次装载同源」——与 a2 同向的行为变化，随定案接受（非缺陷）。

### 四、互斥通知（定案 b2 聚合机制）

- **计算与通知解耦**：filterPermMutex 现状检出冲突即通知（通知与计算绑死）且 detail 用「任一端点命中」OR 过滤（loose，跨 item 并集放大误报）——批量层**不复用**该聚合原语。
- 批量层维护 **`(组, ruleId) → 命中 originalIndex 列表` ledger**：scopeAll 段（组内一次）与实例段（逐 item）分桶写入；**scopeAll 短路 return 前必须 flush**（否则短路放行路径漏记）。
- 通知形态：每 (组, ruleId) 一条审计行，detail 由**实际命中规则集**（first ∈ opIds && second ∈ opIds 的 AND 判定）构造 + `hitItemCount`（item 去重、段间合并）；**次数锁**按 (组, ruleId) 计、**内容锁**断言未触发规则不出现在 detail。
- **父判定审计桶（登记口径）**：共享父判定（每请求至多触发一次）经既有 `query(parentQuery)` 递归，其内部互斥通知**维持现有形态、不入 ledger**——理由：每请求 ≤1 次无 N→1 去重需求；纳入 ledger 需把冲突快照/ledger sink 穿透递归 query（与「父判定共享只经既有注入面」决策冲突，工程面不成比例）；其 detail 端点 OR 宽松过滤为存量缺陷维持现状（登记于遗留节）。

### 五、改动面

| 位置 | 改动 | 量级 |
|---|---|---|
| PermQueryEngine | queryBatch 批量入口 + BatchEvalContext（合并装载/分段化/闭包分档/共享父判定/mutex 静态数据）+ 投影谓词切分 +「已尝试」状态 + queryInstance 空 entityIds 守卫 | 中 |
| ResolveContext / TypeResolutionService | 请求级扩容（全类型/全 op 对一次解析） | 小 |
| PermissionConditionDomainService | **批量条件快照接口**（getBatch+beginRead+putBatch） | 小 |
| PermissionConflictDomainService | 规则/操作索引预载注入 + 计算通知解耦 + (组,ruleId) ledger 聚合 | 小-中 |
| PermissionCheckAppServiceImpl | batchCheck 编排重写（分组+注入+ResultSlot 拆分+唯一 PermEvalContext） | 中 |
| api-contract §6.1 / permission-center-coding-standards | a2 批量口径注记（随实施）+「engine.query() 或四个显式入口」句扩写（随实施） | 小 |
| permission-query-pipeline skill 双副本（.claude/.agents） | batchCheck 逐 item 示例改 queryBatch 形态（随实施，双副本同步） | 小 |

（implementation §3.10 回写已随定稿完成。）

### 六、回归锁计划（v4）

容器轨落位（GoldenFixturePgIT/TargetModeClosurePgIT 先例）：①**等价差分**——同一 fixtures 上 query()×N vs queryBatch 逐 item 对拍 allowed/reason/matched（按集合比较）；②**共享计数锁**——N=10 与 N=100 下 selectScopeAllPermsByBitsBatch/selectInstancePermsByBitsBatch/selectSelfAndAncestorClosureBatch 调用次数不变（防批量入口内部仍循环 query() 的假绿）；③**投影谓词否定锁**——默认模式（不传 inheritMode）授父查子 deny / TYPE_LEVEL item + 仅 depend_on scopeAll + 请求级父上下文 deny / 同批 MENU VIEW+USER scopeAll VIEW 混合 → MENU item deny（跨类型位泄漏）；④空目标集批（纯 TYPE_LEVEL 1000 项 / 全幽灵 code）→ 200 全 deny 禁 500；⑤互斥真锁（VIEW 行+UPDATE 行+VIEW↔UPDATE 规则，断言逐 item 评估双 allowed）；⑥reason 边界——幽灵 code+仅子行 scopeAll+无父上下文 → DEPENDENT_NOT_IN_PARENT_CONTEXT；⑦时间窗边界锁（mockStatic now() 依次 t1/t2，断言批内一致——旧逐 item 路径下红）；⑧通知次数锁+内容锁；⑨**禁用条件 fail-close 锁**（scopeAll/实例两轨：授权行挂 enabled=false 且规则体可评估为真的条件 → 必须 deny，禁止批量快照把禁用条件当有效——RED 在朴素批量实现下失败）；⑩**条件-互斥顺序锁**（VIEW 行挂不满足条件 + 无条件 UPDATE 行（inherit_mask 覆盖 VIEW）+ VIEW↔UPDATE 互斥 → 断言条件先摘、互斥不成立、UPDATE 仍放行且零通知——锁「depend_on→条件→互斥」序）；⑪**条件增量快照次数锁**（每阶段至多一批次、同 conditionId 请求内至多回源一次）。现有 PermissionCheckAppServiceImplTest mock 引擎须改 stub 到新入口（不作等价证据）。

### 七、随批修复与存量登记（终态）

- 随批修复（2026-09-11，随设计评审批次落地）：`BatchAuthCheckReq.items` 双副本补 @Valid 嵌套级联（commit 1cebf1ad9，空白嵌套字段 deny→400，独立评审零缺陷）；SDK 副本级联用例补强 blank operationCode+null 元素（commit eaea2d75a，防双副本漂移）。
- 评审处置全程（各轮发现→修复映射、撤回裁决）登记于 decision-registry 2026-09-11 T-PERM-061 行（v2/v3/v4/定稿四行），此处不复制过程流水。

## 非目标 / 遗留

- 单条 `check()` 不并入批量入口（留待实测证据；与批量在时间窗边界的分叉已由 a2 接受）。
- 存量观察登记（评审累积，不属本卡实施范围）：resolveEntityIds values() 合并丢 key（单条无害）；PERM_MUTEX 规则查询不消费 resource_type_value 列（靠 op id 类型归属，无误判面）；复合 granted_bits 行静默排除出互斥判定（唯一潜在来源=未来 AUTO_DEP 落地）；notifyPermConflict detail 的端点 OR 过滤（b2 聚合机制以 AND 命中集绕开，单条路径维持现状）；`TypeResolutionServiceImpl.batchResolveDomainIds` 结果计算后未参与过滤（批量解析路径不校验 domainCode 存在性，与单条 resolveResourceId 的域存在性检查不一致——待后续核实处置）。
