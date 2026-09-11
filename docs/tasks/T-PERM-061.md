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
  - "设计定稿（2026-09-11 v2，claude+codex 双外评 P1×3+P2×6+P3×2 全核实处置后）：A+ 形态 = 分组 + 请求级共享装载，挂真实 SQL 路径（scopeAll/实例行/ResolveContext/闭包/互斥静态数据全部 DB 新鲜读，不引入 ROLE_PERM_SNAPSHOT 陈旧语义）；评估粒度不变量 = scopeAll 段组内一次（子集与 item 无关）+ 实例段逐 item（子集按闭包集切，PERM_MUTEX 集合语义）"
  - "三项定案（2026-09-11 用户拍板）：A=evaluatedAt 请求级单一时刻（可观察行为变化接受，回写契约注记+时间窗边界测试）；B=notifyPermConflict 组级去重（(组,冲突规则) 一条，detail 携带命中 item 数，登记口径+通知次数锁）；C=items 缺 @Valid 已随批修复（commit 1cebf1ad9）"
  - "回归要求：引擎级等价差分测试（同一 fixtures 逐 item 旧路径 vs 批量新路径逐项对拍 allowed/reason/matched）+ 覆盖掩码互斥锁（两单 bit 行经 computeCoveringBitMask 同场构造）+ 继承闭包归属锁（授父查子）+ scopeAll 短路优先锁（code 不可解析仍 allowed）+ NO_ROLE/输出顺序/拒绝项空 matched 锁；现有 PermissionCheckAppServiceImplTest mock 了 engine 须改 stub 到新入口（不作为等价证据）"
  - "性能项非阻断：当前无已知大 item 批量消费方；SDK batch-check 契约不变（a2 时刻语义需补 api-contract §6.1 批量口径注记）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-11
---

# T-PERM-061 EXT-7：batchCheck 逐条 engine.query 收敛——A+ 形态

> 状态：proposed（2026-09-11 立项；同日设计定稿 v2——双外评处置 + 三项决策闭合，实施未开始）
> 依赖：无硬依赖

## 背景

审计 S-024 登记的 EXT-7 性能项：`PermissionCheckAppServiceImpl.batchCheck` 对 `req.items()` 逐条构造 `PermQuery.forAuthCheck` 调 `engine.query`。**forAuthCheck → TYPE_LEVEL/INSTANCE 管线不经过任何快照缓存**（`loadRolePermEntriesWithCache` 唯一调用方是 LIST 模式 queryList:270），每次管线的真实 IO（双外评逐项实证）：

角色解析（EFFECTIVE_ROLES L2_ONLY 10s，冷 miss 多条 SQL）；类型码/操作码解析（无缓存直查）；目标操作装载 selectValidByIds（无缓存直查，先于 OPERATION_PERMISSIONS_BY_TYPE 缓存执行）；scopeAll 行 selectScopeAllPermsByBitsBatch（无缓存直查）；实例行 selectInstancePermsByBitsBatch（无缓存直查）；条件规则 CONDITION_RULES（L2_ONLY，conditionCache 每次 evaluate 调用新建）；PERM_MUTEX 规则 + 操作索引按类型循环（无缓存直查）；code→entity 按类型循环（无缓存直查）；继承闭包 CTE；父判定（惰性触发时**递归构造父 PermQuery 走完整管线**）。热路径单 item ≈ 2-3× Redis + 6+K 条 SQL；带父上下文最坏再加一整条递归管线。

场景实底：大 N 场景（统一搜索/资产目录）按 (类型,操作,修饰符) 分组后组内同构、组数小；真逐项异构（门户/预检）N≤20；组数上界 = 类型×操作×修饰符组合（类型租户可扩展，最坏≈item 数）——设计目标为「装载共享=常数、组内判定=内存」，不承诺组数次管线。纯行级过滤 SDK 引导面是 query-resources 正查询；batch-check 定位是验证形态。

## 设计定稿（v2）

### 三层结构

**共享装载（BatchEvalContext，引擎内部，全部 DB 新鲜读）**：① roleIds ×1 +「已尝试解析」显式状态（resolveRoleIds:782/resolveEntityIds:839 预置钩子只在非空时生效，空集注入会重新触发解析）；② ResolveContext 扩容（全 item 类型∪父类型、全 (type,op) 对含父操作集一次解析）；③ scopeAll 行 ×1（全组 BitMaskEntry 合并一次 SQL）；④ 实例行 ×1（全组目标∪闭包合并一次 SQL）；⑤ 闭包映射 ×1（全 item entityId 一次 CTE → Map<targetId,Set<closureId>>，多 item 共享祖先时同一祖先行归入多个 item）；⑥ entity 预解析 ×1（保留返回 Map 按键取——resolveEntityIds:846 现状 values() 合并丢弃 key 对应关系，跨 item 不可复用）；⑦ 父判定 ×1（惰性保留；父查询注入共享 roleIds，checkParentResource:415 先例）；⑧ PERM_MUTEX 静态数据 ×1（规则一次、操作索引 O(distinct types)）；⑨ 条件规则预取 ×1（**必需非可选**——conditionCache 每次调用新建）；⑩ evaluatedAt 请求级钉一次（定案 a2）。

**分组判定**：分组键 (targetMode, resourceTypeCode, operationCode, codeType, domainCode, inheritMode)；parentResource 请求级共享不进键。scopeAll 段组内一次评估（SQL 不引用 entityId/code，子集与 item 无关，逐元素等价可证）；实例段逐 item（item 子集 = 行.resourceEntityId ∈ item 闭包集 且位掩码命中）逐 item 条件+互斥评估。PERM_MUTEX 集合语义反例（v2 修正构造）：同组两 item 各持不同单 bit 行（如 VIEW 与 ADMIN），computeCoveringBitMask 把覆盖目标位的操作位纳入掩码 → 两行同场构成互斥对 → 合并评估双 denied、逐 item 评估各自 allowed（一行复合位不可达：精确查表 + DDL 单操作位 CHECK + 写入口单 bit）。存量语义记录：无精确 op 匹配的 granted_bits 行被静默排除出互斥判定，拆分不可用「位掩码命中」近似「opIds 命中」。

**结果拆分**：NO_ROLE（共享角色集空→全批同因，与 USER_NOT_FOUND 同层前置）；ResultSlot(originalIndex,...) 按原始输入序输出（响应无 item 关联字段）；拒绝项 matched 字段族必须空列表（raw 条目仅内部推导 reason 不回传）；reason 优先级对齐 queryInstanceMode 分支序——组 scopeAll 评估通过→allowed（无论 code 可解析与否，短路优先）＞code 预解析失败+scopeAllEvaluatedEmpty→CONDITION_NOT_MET_OR_CONFLICT＞code 预解析失败→NO_PERMISSION＞评估前有子集评估后空→CONDITION_NOT_MET_OR_CONFLICT＞dependentOnlyExcluded→DEPENDENT_NOT_IN_PARENT_CONTEXT＞评估前子集空→NO_PERMISSION。

**互斥通知（定案 b2）**：组级去重——(组, 冲突规则) 一条审计行，detail 携带命中 item 数；scopeAll 段组内一次评估天然一次通知，实例段逐 item 评估的结果按组聚合去重后通知。

### 等价性论证

装载共享不改变任何判定输入集合构成；scopeAll 段子集与 item 无故组内一次安全；实例段逐 item 子集与现状逐条同构（闭包集切分保祖先行归属）；评估粒度是语义的一部分（反例证明合并必不等价）。evaluatedAt 定案 a2 为唯一接受的显式行为变化（时间窗批内一致化）。

### 改动面

| 位置 | 改动 | 量级 |
|---|---|---|
| PermQueryEngine | queryBatch 批量入口 + BatchEvalContext（合并装载/闭包映射/共享父判定/mutex 静态数据/条件预取）+「已尝试」状态 | 中 |
| ResolveContext / TypeResolutionService | 请求级扩容 | 小 |
| PermissionConflictDomainService | 规则/操作索引预载注入（计算保持逐 item）+ 通知组级去重 | 小 |
| PermissionCheckAppServiceImpl | batchCheck 编排重写（分组+注入+ResultSlot 拆分） | 中 |
| api-contract §6.1 | a2 时刻语义补批量口径注记（实施时） | 小 |

### 设计评审记录（2026-09-11）

- claude 外评（用户触发，read-only plan 模式）：P1×3+P2×2+P3×1，全部代码级核实成立——v1 快照挂载点错误（INSTANCE 路径不走 ROLE_PERM_SNAPSHOT）、组内合并评估矛盾、闭包切分丢祖先行、反例 2 构造不可达、reason 树漏 NO_ROLE、账本/引用漂移。处置=v2 全面修订。
- codex 外评（用户触发，luna max + read-only 沙箱，会话 01a09053-2491-7911-9671-e2875135553d）：P1×1+P2×6+P3×1，与 claude 核心同源；独有贡献=空集哨兵/scopeAll 惰性短路破坏/evaluatedAt 可观察/通知语义/父类型预载/输出顺序与空 matched。处置=并入 v2。
- 双评共同结论：「N 次全量管线→常数装载」目标可达，但必须换挂载点（真实 SQL 路径）并显式定案评估粒度与三项语义决策。

## 非目标 / 遗留

- 单条 `check()` 不并入批量入口（留待实测证据）。
- 存量观察登记（双评，不属本卡实施范围）：`PermQueryEngine.resolveEntityIds:846` 按 values() 合并丢弃 key 对应关系（单条路径无害，跨 item 批量复用须改 Map 按键取）；`PermissionConflictDomainServiceImpl:171` PERM_MUTEX 规则查询不消费 `resource_type_value` 列（当前靠 op id 类型归属隐式限定，无误判面）；复合 granted_bits 行被静默排除出互斥判定（存量语义，设计已记）。
- `BatchAuthCheckReq.items` 双副本缺 `@Valid` 已随评审处置修复（commit 1cebf1ad9，RED→GREEN，deny→400 线格式变化随提交生效）。
