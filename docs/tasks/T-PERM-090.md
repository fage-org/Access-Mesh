---
doc_type: task
id: T-PERM-090
title: （R2-T11）迁移范围与 LEGACY_API 接口集合
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.2/§6.4/§6.5/§6.6
depends_on:
  - T-PERM-086
  - T-PERM-087
  - T-PERM-089
blocks: []
acceptance:
  - "queryScopes 四态与父对象存在性（OBJECT_KEY_NOT_FOUND 外层返回）保持；matchedParentOperations 取 087 的 ResultDetails.parentCheck.matchedOperationCodes，按基线逐字段对拍，不重跑父判断；ScopeCoverageProjector 只消费结果与已装载定义；queryResources 保持「原 GRANT_LIST 评估→白名单/排除 API/domain/codeType/展示/分页」后置序（有效权限码/可见资源投影随 2026-09-27 边界勘正归 T-PERM-091 验收）"
  - "LEGACY_API checkInterface 经新 execute 表达共同集合语义（注册门禁在先、全部匹配 API 组成一个 TARGET_SET、不拆项 OR）；父对象存在≠父权限允许语义保持"
  - "S01~S04：快照 API:VIEW 不覆盖 ACCESS 不下发为放行依据；API scopeAll 只展开目标服务 enabled 注册路由（不产生任意通配）；无条件与各 conditionId 分支保留；坏条件维持有条件与回源 fail-closed 不变无条件"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-27
---

# T-PERM-090 （R2-T11）迁移范围与 LEGACY_API 接口集合

## 背景

设计 §6.2/§6.4/§6.6（报告临时编号 R2-T11）。LEGACY_API 是迁移期规则非终态；legacy 语义也应通过新 R2 表达（不需要旧引擎）。

## 范围

- queryScopes/queryResources 迁移；物理子孙展开在授权集合评估之后（不把展示后代提前加入互斥候选）。
- checkInterface 与 interfaceSnapshot/SnapshotAssembler 的 legacy 适配（API:ACCESS 覆盖位=ACCESS 位 ∪ inheritMask 覆盖位，设计 §6.6 精确口径）。
  （边界勘正 2026-09-27 执行时用户拍板：原范围行「有效权限码/可见资源投影」与 091 卡验收第 2/3 条交叠——PermissionViewAppServiceImpl 归 T-PERM-091；快照面归本卡，与 A.1 #3/4/5/6 消费点一致。）

## 非目标 / 遗留

- 新在线 interface-admission 与新快照在 T-ACCESS-059；旧协议退役在 T-ACCESS-062。

## 完成记录（2026-09-27）

- **迁移面（A.1 #3/4/5/6 全部切新 execute）**：
  - queryScopes（`PermissionQueryAppServiceImpl:267`）＝GRANT_LIST＋父要求（ByCode）＋EVALUATE/ENFORCE＋RAW_AND_KEPT；四态组装交 `ScopeCoverageProjector` 纯投影；父对象存在性外层预检查保持（OBJECT_KEY_NOT_FOUND）；NO_ROLE/PARENT_DENIED → NO_PERMISSION＋全 DENIED 分组；matchedParentOperations 直取 `ResultDetails.parentCheck.matchedOperationCodes`（不重跑父判断）。
  - queryResources（`:104`）＝GRANT_LIST＋EVALUATE/ENFORCE＋FACTS；includeChildren/includeInherited → OutputSpec 展示展开 CHILDREN/PARENTS/BOTH（判定与展示分离）；depend_on 行仍装配后隐藏（T-PERM-058）；展开行=PresentationEntry 按源授权关联、grantSource=INHERITED（沿旧克隆口径）。
  - checkInterface（`PermissionCheckAppServiceImpl:181`）＝一个 API:ACCESS TARGET_SET 共同集合（注册门禁在先、全部匹配 API 组成单 item、SELF、TypeFallback.ALLOW=旧 INSTANCE 的 scopeAll 回退形态；全部映射无实体引用退 TYPE_LEVEL）；`toCheckInterfaceResp` 改新 DecisionResult 纯转换。
  - interfaceSnapshot（`:423`）＝GRANT_LIST＋PRESERVE/ENFORCE＋FACTS（沿旧 markConditionsOnly＋evaluateConflicts 形态，设计 §6.6）；角色解析含互斥双删由 User 主体内部完成；`SnapshotAssembler.buildSnapshot` 改消费 `List<GrantFact>`（S01~S04 断言不变）。
- **验收证据**：X03 基线 `QuerySemanticsBaselinePgIT` 17 用例全绿（范围四态族 3＋快照投影族 3＋check 族 11，真服务真库对拍）；单测轨道 1587 绿；收口全量 `mvn test -T 1C` 11 模块 BUILD SUCCESS（含 E2E 与 heavy）。
- **适配层退化归一（沿 089 口径）**：queryScopes 父操作集过滤 null 后空＝NO_PERMISSION 整表拒（旧引擎空父操作集同形）；范围类型/操作 null 元素组合直接 DENIED 分组不进引擎（旧解析落空同形）；空白元素由 DTO @Pattern 400 拦截。`CallerContext.fromCallerMap` 公共工厂收编 089/090 两处私有副本。
- **X03 差异记录（均无证据消费面）**：① matchedParentOperations 多元素时顺序由旧 Set 迭代序变 ParentCheckSummary 排序序；② queryResources 树展开行序由「原始集中+克隆追加」变按源事实交错（分组聚合后 items 顺序可能不同，响应为集合语义）；③ context 顶层 evaluatedAt/timestamp 键旧 PermEvalContext 静默收入 attributes、新 CallerContext 结构拒绝（初沿 089 拍板 500 扩展到三面；外评处置修订〔2026-09-27 用户拍板〕：经本地 advice 改 400 VALIDATION_FAILED＋契约总册三处示例同步删 timestamp，tenantId 非正数同根因同改）；④ interfaceSnapshot 无角色短路由适配层预解析变引擎内解析（execute 必发生一次，响应等价）；⑤ 范围类型/操作元素全为 null 时提前返回 reason=null（旧引擎会跑父判定失败返回 NO_PERMISSION——两侧均为全 DENIED 分组，scopeMode 行为一致，外评补充登记）。
- **连带回写**：`OperationDefinition.toCacheRow` 公开予包外结果消费方复用 OperationPermissionUtils 位运算族；permission-query-pipeline skill 双副本、permission-coding-standards rule、AGENTS.md 硬约束行同批更新；090/091 两卡范围行按用户拍板勘正（快照面归 090、视图归 091）；设计 §6.5 就地实施注补记。
- **外部评审处置（2026-09-27，claude+grok 双通道；grok 首跑撞 max-turns 40 上限未出报告，提限 120 重跑成）**：claude P2×1＋P3×1，grok P0~P3=0。①claude P2 成立（已核实）：保留键结构拒绝落全局兜底 500 与契约总册三处示例（§18.2/§18.5/§18.6 本身带 context.timestamp）冲突——用户拍板改 400：access-service 本地 advice `QueryValidationExceptionHandler`（@Order(0)，QueryValidationException→400 VALIDATION_FAILED，tenantId 非正数同根因同改）＋契约三处示例删 timestamp＋§18.1 共享注记；claude「镜像册 permission-center/api-contract.md 需同步」部分撤回（该册 superseded 留原位不改写）。②claude P3 成立：`BusinessKeyUtil.scopeItemKey` 死代码（唯一生产调用方随本卡删除）连同 ParityTest 用例删除。③两通道矛盾裁决（用户拍板保留）：checkInterface 空实体退 TYPE_LEVEL 分支——claude=保留（旧引擎空目标集 scopeAll 命中放行的行为等价必要件）、grok=裁剪（不可达场景），保留 claude 论证。④grok 存量观察登记不处置：queryResources ops 集合要求授予码本身在请求 operationCodes 内（旧组装原样搬运，覆盖操作单独持有不出行）、engine/implementation.md §3.7-3.8 旧入口口径（A.8 列 092 回写面）、契约 §18.4 快照 ALL 示例陈旧（T-PERM-017 起即如此）。⑤claude 补充微差入 X03 清单（全 null 元素提前返回 reason=null）。回归：单测轨道 1588 绿（含新增 advice 回归锁）。
- **复评处置（2026-09-27，用户贴回双轨结论，主代理逐条代码级核实）**：P2×2＋P3×2 全部成立，无存疑项与可裁剪项。①P2 advice 回归锁补强为 HTTP 层——旧锁直调 advice 方法不经 HTTP 语义（摘 `@ResponseStatus`＝R 信封仍 200、优先级破坏＝落 common GlobalExceptionHandler 的 Exception 兜底 500 均不红），改 MockMvc standalone（先例 RetiredRoleApiContractTest/SyncEndpointAuthIT）：stub 控制器走真实触发路径（CallerContext 保留键构造拒绝）、双 advice 反序挂载（common 兜底在前，`@Order(0)` 摘除即被兜底吞掉变 500），断言 400＋VALIDATION_FAILED 信封。②P2 指令面残留——rule §3 Engine 层「统一使用 PermQueryEngine」、§8 示例字段、§17 替代表两行、§19 示例与 skill 禁止事项行（双副本）仍引导旧引擎，全部换 QueryGate 判定面口径。③P3 口径回写补注——设计 §6.5 089 实施注与 089 卡拍板行的「保留键 500」补注本卡外评处置已改 400。④P3 边界勘正漏改收尾——本卡验收行删 091 归属两面（有效权限码/可见资源投影），089 卡遗留行补记旧快照已随本卡交付。复评实证通过项（batchCheck 单次 execute/原序/请求级父上下文、checkInterface 共同集合、快照条件分支与启用路由展开）确认维持。
