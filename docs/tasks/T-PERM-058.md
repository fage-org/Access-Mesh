---
doc_type: task
id: T-PERM-058
title: depend_on 子权限单点门禁闭合设计——单点查询的主资源上下文语义
status: done
plan: docs/plans/permission-query-unification-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§3
  - docs/design/permission-center/api-contract.md#§6.1
  - docs/design/permission-center/api-contract.md#§6.6
depends_on:
  - T-PERM-057
blocks: []
acceptance:
  - "缝隙定案：现状 selectInstancePermsByBitsBatch 不看 depend_on——单点查子权限行绕过父绑定照放行（query-scopes 面有主资源上下文判定、单点面没有）；设计产出闭合语义并定案"
  - "设计定案：可选 parentResourceTypeCode/parentResourceCode/parentCodeType/parentOperationCodes 四字段（与 query-scopes 同名对齐）；不传时 depend_on 行不计入（fail-closed，用户拍板）"
  - "线格式：/auth/check、batch-check 的主资源上下文入参 + api-contract §6.1 回写；depend_on 行 id 不进线格式（引擎内部解析）"
  - "实施定案：设计定案与引擎实施一并收口（用户拍板，不拆独立小卡）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-10
---

# T-PERM-058 depend_on 子权限单点门禁闭合设计

> 状态：done（2026-09-10 设计定案 + 引擎实施一并收口——用户拍板「一并实施」）
> 依赖：T-PERM-057（✅）

## 背景

子权限（depend_on）语义为「只在特定业务实例上下文生效」：query-scopes 面已实现主资源上下文判定（父权限命中集合内的子行才参与范围计算，父行条件不过时子行自动失效）。**单点面（/auth/check、hasPermissionByCode）不感知主资源上下文**——直查数据范围类型会绕过父绑定（基础授权=福建、子权限=广东挂 depend_on=报表A:VIEW 时，单点问广东照放行，正确语义应为「在报表A上下文里才放行」）。

## 范围

- 闭合语义设计定案：parentContext 可选入参形态 + 不传时 depend_on 行计入/不计入的取舍（fail-closed 方向 vs 兼容现状）。
- 定案后落 api-contract 与 implementation §3（query-engine-unification.md 已并入，T-PERM-057 落地）。

## 实现记录（2026-09-10 收口）

### 现状核实（缝隙比任务卡登记的宽——共四个面）

子权限行的授权语义「只在父权限命中的主资源上下文内生效」（api-contract §6.7 DEPENDENT 公式）此前**只有 query-scopes 一条路径实现**（引擎 LIST 带 parentResource 过滤）；其余全部查询面经代码逐一核实均不看 depend_on：

| 查询面 | 证据 | 缝隙 |
|---|---|---|
| /auth/check、batch-check（SDK） | `selectInstancePermsByBitsBatch` SQL 不筛 depend_on | 单点查子行实例直接放行（任务卡登记的原缝隙） |
| 管理面写门禁 + TYPE_LEVEL | 同上 + `selectScopeAllPermsByBitsBatch` 也不筛；且写侧不禁止子行 scopeMode=ALL（`assertChildKeyAttributes` 只锁 conditionCode/canGrant）→ depend_on 非空 + scope_all=true 行可经 apply-grant-plan 造出，命中类型级门禁 | 一行子权限放开整个类型级门禁（比实例缝隙更宽） |
| Gateway 接口快照 + check-interface | SnapshotAssembler 实例级条目不筛 dependOn；Gateway 全源码零 dependOn 引用 | API 类型子行进快照照放行（SUB_PERM 允许时） |
| query-resources 清单 | `buildQueryResourcesResponse` 不筛 dependOn | 子行实例作为独立 INSTANCE 条目输出，清单语义不实 |

**「当时为什么没出事」**：bootstrap 无 SUB_PERM 默认种子——租户不显式配置 domain_config SUB_PERM（按父类型所属域）时策略=ALLOW_NONE（CONFIG_MISSING），授权页无法创建任何子权限行；种子环境零子行数据，四个缝隙全部零触发。

### 四项实施定案（2026-09-10 用户拍板，registry 登记）

1. **单点面 fail-closed**：/auth/check、batch-check 增可选 parentResourceTypeCode/parentResourceCode/parentCodeType/parentOperationCodes（与 query-scopes 父入参同名对齐；type/code 成对提供，半传 400）；不传时 depend_on 行不参与判定，拒绝原因 DEPENDENT_NOT_IN_PARENT_CONTEXT（与 NO_PERMISSION 区分）。
2. **TYPE_LEVEL 仅读侧排除**（二次确认维持）：类型级 SQL 结果内存排除 depend_on 行，不动写链路——scopeAll 子行形态保留，生效面为 LIST 父上下文与带主资源上下文的 INSTANCE（父命中时计入，父判定真实执行不越权；claude 外评 P3-2 口径修正）。曾讨论「父不能类型级」限制——经核实无效（缝隙在子行自身 scope_all 标志，与父形态无关）且误伤合法形态（父=类型级全量+子=补充实例）。
3. **清单/快照两处排除**：query-resources 组装与 SnapshotAssembler 排除 depend_on 行（组装层排除而非引擎 LIST 层——canGrant 委托链零行为差（子行 canGrant 恒 false）、permission-view/登录权限串维持现状（T-PERM-059 范围/非目标））。
4. **一并实施**。

### 引擎实施（全部内存过滤，SQL 零改动）

- `queryInstanceMode`：scopeAll 前置与实例查询两阶段各过 `filterDependentEntries`——无子行原样返回（零开销）、无父上下文排除（fail-closed）、有父上下文经 `LazyParentCheck` 惰性父判定（checkParentResource 复用，单次 query 内两阶段共享一次查询），子行要求 dependOn ∈ 父命中权限集；父判定经 forAuthCheck 递归本引擎、自身无父上下文=只认父的主授权（单层语义自动获得）。拒绝原因优先级：条件/冲突拒绝 > DEPENDENT_NOT_IN_PARENT_CONTEXT > NO_PERMISSION（子行排除致空与评估清空区分）。
- `queryTypeLevel`：scopeAll 结果排除 depend_on 行（拍板 2）。
- `passesScopeAll` / `computeInstanceDenied`（getDenied\* 便捷入口管线）：两处查询结果排除 depend_on 行（无父上下文概念，fail-closed 同口径）。
- 组装层：`buildQueryResourcesResponse` 与 `SnapshotAssembler.buildSnapshot` 排除 depend_on 行。
- DTO 双副本四文件（access-service + perm-common 的 AuthCheckReq/BatchAuthCheckReq）加四可选字段 + `@AssertTrue` 成对校验；PermissionCheckAppServiceImpl.check/batchCheck 透传（batch-check 为请求级父上下文，与 query-scopes 对齐）。

### 回归锁

- 引擎单测 5 例（PermQueryEngineTest）：4 例旧实现下失败（无父上下文拒 / 父未命中拒 / TYPE_LEVEL scopeAll 子行拒 / getDeniedEntityIds 子行进拒绝集）+ 1 例正向锁（父命中放行——旧实现亦通过，防过度收紧；claude 外评 P3-1 措辞修正）。
- 契约快照：CheckFamilyWireShapeTest 增 checkRequestShapesAreFrozenWithParentContext（双副本同形 + 新四字段）+ PermissionFeignClientContractTest 增 checkRequestDtoFieldsAreFrozen（SDK 侧）。
- 组装层：SnapshotAssemblerTest.shouldExcludeDependentEntriesFromSnapshot（仅子行=空快照、主行不误伤）+ PermissionQueryAppServiceImplTest.shouldExcludeDependentEntriesFromQueryResourcesItems。
- 真库：TargetModeClosurePgIT.dependentPermSinglePointContextSemantics（JDBC 造子行/UPDATE 造 scopeAll 子行，三断言覆盖拍板 1/2）。

### 双轨评审处置（2026-09-10）

- **P1（两轨共报，用户拍板）**：parentOperationCodes「缺省=父任意授权命中即可」为实现不支持的错误承诺（引擎对空操作集不发父判定查询、父判定必不命中）——按 query-scopes 同名 @NotEmpty 必填先例收敛：四 DTO 增 isParentOperationsPresent @AssertTrue（给出父上下文时操作集必填非空）+ api-contract §6.1 措辞修正 + 成对/操作集双用例（BatchEntrySizeValidationTest）。
- **P2 直接修**：①getDeniedEntityIdsMustRejectDependentOnlyEntries 假锁（Mockito 默认空列表使评估清空子行、新旧实现断言都过）——补 lenient 透传 stub（新实现短路不消费、旧实现消费后断言失败=真锁）；②四 DTO isParentContextConsistent 包私有改 public（对齐 MenuCreateReq 先例，消除 Hibernate Validator getter 拾取不确定性）+ 半传用例锁生效性。
- **P3 直接修**：任务卡 design_refs 补 api-contract §6.6（与 README 行一致）、acceptance 终态化、定案小节标题对齐「实施定案」口径、plan last_updated 同步。
- **登记不修**：batchCheck 请求级父上下文逐 item 重复父判定（仅子行形态触发、批量上限 1000 封顶，复用需跨 item 缓存机制，收益不匹配）；孤立 parentCodeType/parentOperationCodes（无 type/code）静默忽略（fail-closed 方向、契约未定义边角）。

### claude 外评处置（2026-09-10，默认模型）

P0=0、P1=0、P2×2、P3×5；核心语义与四项定案逐条相符、无第五面（queryScopeAll/queryInstance 全部调用点核实覆盖、check-interface 与 cloneWithInherited 展开副本均经同一过滤、AUTO_DEP 独立列 grant_dep_id 不受影响）。

- **P2-1 直接修**：parentOperationCodes 含 null 元素（JSON 反序列化可造）→ Set.copyOf NPE → 500——PermEvalContext.filterValid 同款缺陷类；两处转换改 toOperationCodeSet（null 元素过滤，与「未识别操作码 fail-closed」口径一致，弃元素级 @NotBlank——会把可拒绝入参变 400 且与静默 fail-closed 口径分叉）。
- **P2-2 直接修**：四 DTO parentOperationCodes 补 @Size(max=1000)（与 query-scopes 同名口径对齐；逐元素进 SQL IN 绑定）+ 超限用例。
- **P3-1 直接修**：本卡「回归锁」措辞改「4 例旧实现下失败 + 1 例正向锁」（例 2 父命中放行在旧实现下亦通过——正向锁性质）。
- **P3-2 直接修**：「scopeAll 子行唯一生效面为 LIST 父上下文内」口径修正——实现为「LIST 与带主资源上下文的 INSTANCE」（INSTANCE 带父上下文时 scopeAll 子行经 filterDependentEntries 保留即类型级短路放行；行为自洽不越权，registry 同步）。
- **P3-3 直接修**：api-contract §6.1 注记补「类型级门禁面拒绝原因为 NO_PERMISSION」（TYPE_LEVEL 无 DEPENDENT 分支，SDK 按 reason 分支时勿漏判）。
- **P3-5 直接修**：runbook 补升级后行为差异 FAQ 行（三面：单点/类型级子行放行→拒绝、清单/快照不呈现子行、半传/空操作集 400；存量零子行实际影响为零）。
- **P3-4 维持**：batch-check 逐 item 重复父判定（双轨评审已登记不修，claude 复核确认量级有界）。
- **存疑项 5 维持现状**：空白串父上下文被当作已给出→父判定不命中 fail-closed 拒绝（安全方向，不为措辞差异改行为面）。

### codex luna max 外评（2026-09-10，处置后复评）

对处置后提交 f1e1165e3 整体评审（read-only，462,902 tokens）：**未发现具有明确证据的生产级缺陷，P0-P3 全零**。两轮评审含专项清单逐条执行：depend_on 唯一写链路（apply-grant-plan）事务闭合、queryScopeAll/queryInstance 全部调用点过滤覆盖、清单/快照/Gateway fallback/便捷门禁/缓存链路均符合定案口径。评审勿再报已定案四面（registry「再报直接撤回」档）。

### 验证

引擎/契约/组装层单测 + TargetModeClosurePgIT 真库全绿；评审处置后受影响测试类复跑全绿；收口时全量回归 `mvn test -T 1C`（含 E2E）。

### 设计回写

api-contract §6.1（入参示例 + T-PERM-058 注记：单点语义/batch-check 请求级/便捷入口与类型级门禁口径）+ §6.6（子权限行不进清单面 + 快照同口径）+ frontmatter；implementation §3.1（便捷入口 depend_on 口径 + forUserView 不在引擎层排除的边界与理由）+ §3.3（三态判别补 depend_on 处理）+ 管线图 + 遗留清单移除本项 + frontmatter；permission-query-pipeline skill 双副本 5.1.0（工厂表 forAuthCheck 注记 + 流程图 filterDependentEntries/TYPE_LEVEL 排除）。

## 非目标 / 遗留

- AUTO_DEP 依赖自动补全的写入侧接线（生产无产出通道，写入侧概念另行定案，不属查询面）。
- permission-view 系与登录权限串的 depend_on 呈现口径（T-PERM-059 删除重设计范围 / 非目标不动）。
- scopeAll 子行写侧是否禁止（scopeMode 必须 INSTANCE）：定案维持形态保留（读侧排除已闭合越权面），如后续发现该形态零使用再评估禁止。
