---
doc_type: task
id: T-PERM-089
title: （R2-T10）迁移 check/batch/管理门禁/getDenied
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.5/§9.1
depends_on:
  - T-PERM-085
  - T-PERM-088
blocks: []
acceptance:
  - "PermissionCheckAppServiceImpl.check/batchCheck、AdminPermissionValidatorImpl、ResourceManage/TypeDefinition 等直接门禁、getDeniedResourceCodes/getDeniedEntityIds 全部经新 execute；batchCheck 禁止循环 N 次公开 execute；外层职责保留（主体业务键解析、SELF 缺省、原序/重复项、请求级父上下文）"
  - "语义变化消费面按计划附录 A.3 实际清单逐面确认「跨 item 冲突从全拒变各自判」可接受并留差异记录（旧四消费面口径已由 A.3 勘正：「资源依赖」「权限树 ID 轨」两面不存在，清单以 A.3 为准＝设计 §6.5 getDenied 行勘误口径）"
  - "X03 等价差分：除已登记预期修复（PQ-01/06、FACTS 完整性、空角色契约、同源首次读取复用）外全部保持——差分锚来自 T-PERM-081 基线"
design_writeback:
  required: true
  status: completed
last_updated: 2026-09-27
---

# T-PERM-089 （R2-T10）迁移 check/batch/管理门禁/getDenied

## 背景

设计 §6.5 消费者迁移矩阵前三类 + getDenied（报告临时编号 R2-T10）。迁移在明确的调用方边界选择新/旧一次，不让真实请求完整跑两次有副作用鉴权再比较。

## 范围

- check/batchCheck/getDenied 双轨、AdminPermissionValidatorImpl（当前操作者、SecurityException 与技术错误分界）、资源/类型直接门禁（CODE/ENTITY_ID 分型）。
- ID 轨批量管理门禁按原下标映射回输入 ID，不把 ID 转业务码。

## 非目标 / 遗留

- 范围四态与 LEGACY_API 集合在 T-PERM-090；旧快照/转授/视图在 T-PERM-091。

## 完成记录（2026-09-27）

**交付物**：

- 引擎 Bean 化：`engine/query/QueryEngineConfiguration`（同包装配 `QueryExecutionEngine`；Clock=进程本地，EPP 强制 UTC；metrics=noop——Micrometer 绑定随 T-PERM-094）。
- 判定面薄门面 `engine/query/QueryGate`：四方法（`hasPermissionByCode`/`hasPermissionByEntityId`/`getDeniedResourceCodes`/`getDeniedEntityIds`）签名沿旧入口，内部逐目标独立 DECISION item 一次 execute；口径=旧 forValidate 拉平（EVALUATE+ENFORCE、SELF_AND_ANCESTORS、TypeFallback.ALLOW、clientIp 自动装配）；空输入零引擎调用。§9.4 架构锁入 `QueryBoundaryArchitectureTest`（仅依赖 engine.query+infrastructure/JDK/Spring）。
- check/batchCheck：`PermissionCheckAppServiceImpl` 适配层直构 `QueryRequest`——外层职责保留（resolveUserId/USER_NOT_FOUND、原序重复项〔item key=输入下标〕、请求级单父上下文〔同值 ParentRequirement 挂全批 item〕、请求级评估时刻=RunState 单时钟）；`PermResultUtils.toAuthCheckResp` 改 `DecisionResult`→`AuthCheckResp` 纯转换（conditionEvaluated 从保留事实派生，零额外 I/O）；checkInterface 留旧（T-PERM-090）。
- 消费面切换：A.2/A.3 全部生产调用点（17 文件）+ `AdminPermissionValidatorImpl`（SecurityException/技术错误分界保留）+ `PermissionViewAppServiceImpl:71` 查看他人门禁（主视图管线留旧至 091）。旧引擎生产消费者仅剩 090/091 目标。
- 指令面回写：`permission-coding-standards` rule（§2 铁律换 QueryGate）、`permission-query-pipeline`（v6.0.0）与 `accessmesh-patterns`（v1.2.0）双副本同步（diff 验证）、AGENTS.md 硬约束行+指针两行、OperationCode Javadoc 用法示例。
- 测试：`QueryGateTest` 新增（请求形状/拒绝投影/空输入）；`PermissionCheckAppServiceImplTest` 重写（外部响应断言保留+适配形状锁：两档选择/inheritMode 映射/空白编码归一/保留键 500/原序重复项）；管理面单测 mock 面 33 文件换 QueryGate；characterization 三件门面链改写（MutexSemantics：getDenied*/hasPermission→QueryGate、queryBatch 对照极→batchCheck 服务面、D02→TargetSet 多 clause 单 item 直构、⑧ 审计锁改 ConflictEvidence 形态〔两目标同规则=两条 item 级证据行〕）；`R2BaselineFixture.insertUserWithRoles` external_id 对齐基线口径（=id 字符串，服务面主体解析所需）。

**四项用户拍板（2026-09-27，正文见设计 §6.5 实施注）**：门面命名 QueryGate；本卡 metrics noop（094 接 Micrometer）；check/batchCheck 空白 resourceCode 归一 TYPE_LEVEL；外部 context 顶层保留键直接 500（clientIp 仍按 SDK 契约提取为受信 IP）。

**A.3 逐面差异记录**：「跨 item 冲突从全拒变各自判」经 T-PERM-095 已为现行生产行为，本卡迁移不引入新语义；A.3 全部 14 调用点（资源树可见性 :436、API 映射三处 :936/:984/:1001、删除门禁、角色候选写守卫×3、类型/条件/组织/服务配置可见性等）随门面切换等价承载，容器轨 characterization（InstanceGate/TargetModeClosure/PermissionCharacterization/MutexSemantics D01）对拍绿。

**X03 等价差分记录**（已登记预期修复外新增微差，均无证据消费面）：①外部 context 顶层 `timestamp` 键不再透传进条件评估（用户拍板 500 的衍生面：键在场即拒）；②顶层 null 值键静默过滤（CallerContext T-PERM-082 契约）；③空白编码归一 TYPE_LEVEL 的角落差异——无父上下文时仅拒绝原因变（DEPENDENT_NOT_IN_PARENT_CONTEXT→NO_PERMISSION）；有父上下文且父命中且主体仅有 depend_on 子 scopeAll 行时为判定结论收紧（旧 INSTANCE 路径可经子行放行→新 TYPE_LEVEL 丢弃子行拒；fail-closed 方向，外评 claude P3 勘正补记）；④空白父编码归一无父的角落差异——旧链路父类型 scopeAll 命中时可经 depend_on 子行放行→新口径子行一律 fail-closed 拒（同③收紧方向）。

**验证**：单测轨道 1580 绿；容器定向组绿（QuerySemanticsBaseline 17/17、MutexSemantics 6/6、InstanceGate 4/4、TargetModeClosure 6/6、PermissionCharacterization 3/3、BatchAuthCheck 11/11、QueryExecution 16/16）；架构锁 8/8；收口全量 `mvn test -T 1C`（含 E2E/heavy）结果见下方回归行。

**回归（收口形态）**：`mvn test -T 1C` 全模块 BUILD SUCCESS（E2E 两垂直切片含）；首轮 39 errors 定性=AutoGrant 两 PgIT 的 @SpyBean `when()` 打桩反模式（旧引擎 null 主体容忍掩盖，换门面后真实调用 NPE 显形），改 doReturn 形态后隔离复跑绿、全量复跑绿。外评处置后末轮全量复跑 BUILD SUCCESS（11 模块含 E2E/heavy）。

**双轨本地评审**：代码轨/文档轨主代理直跑；P2×1（spy 打桩反模式，已修）；过度设计可裁剪项=0；存疑上报=0。

**外部评审（2026-09-27，用户点名 claude+grok 双通道，全程无子代理）**：claude P2×1+P3×2+可裁剪×1；grok P2×1（与 claude 同根因、面更宽）+存量观察×2。双通道结论由主代理逐条代码级核实后统一处置（同根因合并）：
- **P2 成立（双通道同根因：空白业务码进 ByCode 触发整单结构拒绝 500）**——四个入口面：①check/batchCheck 父编码空白（DTO 对 `parentResourceCode` 无约束，`ServiceConfigGetReq.serviceCode` 仅 `@NotNull` 同理可达门面）；②门面 `hasPermissionByCode` 空白码；③`getDeniedResourceCodes` 集合含空白元素拖垮整批；④对照面：空白目标码拍板已归一但父编码/门面未对称处理。修法按既有拍板模式统一收口（事实性最小修正，未新增用户决策）：父编码/父类型空白→归一无父；门面空白码→归一类型级（门面无父上下文，与旧 INSTANCE 不可解析形态可观测等价）；getDenied 空白元素→逐个 fail-closed 拒绝不进引擎；回归锁三条（QueryGateTest×2+PermissionCheckAppServiceImplTest×1）。
- **P3 成立（X03 记录③不完整）**：空白编码+父上下文命中+仅 depend_on 子 scopeAll 行=判定结论收紧（旧 ALLOW→新 DENY），非仅 reason 变化——差异记录③已改写、新增④（空白父编码同向收紧）。
- **P3 成立（3 个 PgIT 字段名 `permQueryEngine` 类型已换 QueryGate）**：改名 `queryGate`（InstanceGate/PermissionCharacterization/FirstAdminUserTrack）。
- **可裁剪项成立**：`checkOutput()` 与 `OutputSpec.kept()` 同形——改用公共工厂。
- **存量观察采纳**：`QueryExecutionEngine` 类注释「暂不注册 Bean」过时——已更新；TargetModeClosure/BatchAuthCheck 旧引擎直连面维持（090/091/092 范围，不扩围）。
- 处置后验证：单测轨 1584 绿；改名 PgIT+三件基线资产定向绿；收口全量复跑结果见回归行（末轮）。
- 双通道矛盾项：无。
