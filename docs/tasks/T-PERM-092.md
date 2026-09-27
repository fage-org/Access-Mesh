---
doc_type: task
id: T-PERM-092
title: （R2-T13）删除旧执行体与四旧 DTO
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §9.1/§9.4
depends_on:
  - T-PERM-089
  - T-PERM-090
  - T-PERM-091
blocks: []
acceptance:
  - "PermQuery/PermBatchQuery/PermResult/PermBatchResult 与独立编排删除；PermResultUtils 改为新结果到既有外部响应的纯转换或删除（不先重建旧 PermResult 再转换）；X04：生产引用为零（直接调用/方法引用/反射/序列化/测试/文档全查），RolePermEntry 仅作 ROLE_PERM_SNAPSHOT 缓存载荷边界例外明确"
  - "架构测试：getDenied 名称可留作薄门面，但禁止其注入权限 Mapper、解析角色或调用条件/互斥服务"
  - "R2 完成条件闭合：本卡在仍有 LEGACY_API 服务时也可完成（legacy 语义已在新 execute 内表达，无永久双执行）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-27
---

# T-PERM-092 （R2-T13）删除旧执行体与四旧 DTO

## 背景

设计 §9.1 字段迁移不能只包门面、§9.4 删除与回退检查（报告临时编号 R2-T13）。两个完成条件之一：本卡完成=R2 入口统一（≠T-PERM-054 完成）。

## 范围

- 删除与全仓残留清扫（含 mock 层与文档现在时残留）；回写时把 R2 引擎终态章节按现行规范并入 engine/implementation.md（设计稿对应章节标注已并入；设计稿整体转 superseded 在计划完结归档时，准入面回写由 ADM 系列卡承担）。
- 文档治理清扫（2026-09-27 用户拍板挂本卡）：T-PERM-089/090 任务卡完成记录与计划「当前进度」区对应条目的过程叙事（用户拍板选项清单、外评通道处置流水、复评轮次定位）按 project-rules §文档治理改写为终态事实（091 卡已于 2026-09-27 同型清扫，先例形态见该卡完成记录）。

## 非目标 / 遗留

- API 独立授权退役与 legacy 协议退役=T-ACCESS-062（第二个完成条件）。
- `docs/tasks/T-PERM-036.md`（proposed）验收叙述仍含旧引擎链路——该卡推进时须按新 execute 口径改写验收（计划 A.8 登记，本卡不触达）。

## 完成记录（2026-09-27）

- **删除面（git rm）**：`engine/core/PermQueryEngine`、`engine/core/ResolveContext`（旧引擎专属类型预解析上下文，唯一消费方=旧引擎；新引擎解析记忆在 `QueryReadSupport` 请求级 RunState）、四旧 DTO `engine/dto/{PermQuery,PermResult,PermBatchQuery,PermBatchResult}`、`grant/enums/TargetMode`（旧目标三态枚举，删除后零消费方）；旧引擎自有单测 `engine/core/PermQueryEngineTest`、`engine/dto/PermResultTest`（A.7：旧断言随本卡删除；新核心行为锁=085~088 测试族＋characterization PgIT）。
- **保留边界（核实依据）**：`PermEvalContext` 非四旧 DTO——新引擎 `RunState` 生产消费（评估输入展平）＋条件评估链，保留；`BatchConditionEvaluator`/`BatchPermMutexEvaluator` 为新引擎 `CandidateEvaluator` 与 rule 域服务共同消费，保留；`RolePermEntry`＝ROLE_PERM_SNAPSHOT 缓存载荷边界例外（Javadoc 补终态注记：新引擎 GRANT_LIST 读路径消费，禁止连带删除目录条目——计划 A.5）；`PermResultUtils` 维持新结果纯转换终态（toAuthCheckResp/toCheckInterfaceResp，Javadoc 去「旧 PermResult」措辞）。
- **收编**：`PermQuery.inheritClosureOf` → `PermissionCheckAppServiceImpl` 私有静态（2026-09-27 定案：适配层私有——与本类既有空白码归一/父上下文归一同居一处，`Inheritance` 枚举不接触 check 族线格式词汇）。
- **X04 退役锁**：`QueryBoundaryArchitectureTest` 新增双向锁——旧执行体/四旧 DTO/TargetMode/ResolveContext 七类 FQCN 主源码再现即红；QueryGate 薄门面约束（§9.4：禁止注入权限 Mapper/解析角色/条件互斥服务）沿 T-PERM-089 既有锁。
- **characterization/引擎测试清面改写（A.7：断言与事实集保留）**：
  - `AuthorizationChangeInvalidationPgIT`：forUserView 尾断言改直构 execute（GRANT_LIST＋Evaluation.full＋OutputSpec.kept），`matchedPermissionIds` 保持 permissionId 断言粒度（无 P、有 P2）。
  - `TargetModeClosurePgIT`：inheritMode 接通/depend_on 父上下文两用例改 check 服务面（AuthCheckReq inheritMode/父四字段）；两用例主体改 user_type=1（服务面 resolveUserId 按 "USER" 类型值精确匹配，admin 型不可解析——唯一事实集变化为测试主体类型，角色/授权事实零变化）。
  - `AutoGrantEngineContractPgIT`：check/batch 改服务面、视图断言改 `PermissionViewAppService.getEffectiveResourceAccess`（互斥两端同场双丢→instanceIdsByType 空）；审计 times(1)×3 段验证维持（新引擎 ConflictEvidence 形态下成立）；夹具 resource_entity 插入补回 TENANT 参数（重写转录丢失，定向复跑发现即修）。
  - `BatchAuthCheckPgIT`：①-⑪全量驱动面改 batchCheck 服务面（PermBatchQuery.Item→AuthCheckItem，inheritClosure→inheritMode）；②共享计数锁 mapper 方法名不变（新引擎 QueryReadSupport 复用同款 selectScopeAllPermsByBitsBatch/selectInstancePermsByBitsBatch/闭包 CTE）；⑧审计锁按 T-PERM-088 已定案形态改写（两 item 同规则冲突=两条 item 级 CONFLICT_DETECTED 证据行〔rule=/stage=INSTANCE/completion=COMPLETE/item=下标〕；旧 (组,ruleId) 合并单行 hitItemCount=2 形态随旧执行体退场）；父判定装配补测改服务面父四字段。
  - `QueryExecutionPgIT`：两处 matchLegacy* 差分用例转直接 golden 断言（对拍对象删除，新断言集=原新引擎侧断言原样）；getDenied 对拍改 QueryGate；legacy 字段删除。
  - `MutexSemanticsCharacterizationPgIT`/`R2BaselineFixture`：注释清扫（「旧 forAuthCheck 形态」措辞改现行语义描述；时钟钉住注记改 RunState 单时钟口径）。
- **指令面回写**：`permission-query-pipeline` skill v7.0.0 终态重写（旧引擎组件行/工厂方法预设表/旧内部流程节删除，引擎管线节改现行唯一管线）双副本同步 diff 验证；`permission-coding-standards` rule §2 迁移期存量行→退役终态、§17 已删除类表补三行（PermQueryEngine/ResolveContext、四旧 DTO＋inheritClosureOf 收编、TargetMode→Selection）；AGENTS.md 硬约束行＋skills 表行。
- **设计回写**：`engine/implementation.md` §3 族整体重写为 R2 终态（§3.1 两层入口/§3.2 请求与结果模型类册/§3.3 分阶段管线/§3.4 两语义拆分/§3.5 条件互斥与审计证据/§3.6 结果模型/§3.8 对外接口/§3.9 边界声明与回归面/§3.10 批量判定现行形态），§2.10 ResolveContext 标记删除，§5.3/§7.2-7.4/缓存表换 QueryGate/execute 口径；设计稿 `r2-unified-query-and-admission.md` §2~§5 章头标注「已并入 implementation.md」（整体转 superseded 随计划归档）。
- **活文档现在时残留清扫**：overview（鉴权入口节改两层入口+消费面表）、core-flows §7（管线图改 execute 分阶段）、access-service-architecture（3 处）、api-contract 总册＋services/admin-service-api-contract（门禁入口/scope 排除/§18.2 批量口径审计句）、capability-structure（引擎叙述＋归属清单终态注记）、dependency-auto-grant（流程图/验证记录/管理门禁）、extension-guide、iam-task-closure（复用清单/共同判定口径）、org-user-permission-contract（架构图/实证/甲层表）、permission-center-v3.5 IR-1.1（铁律改 QueryGate+execute）、project-rules §8.4（3 处）、schema 注释（2 处种子注释）、frontend（source-chain.ts×3/perms.ts/permission-grant.md×3）。带日期历史句/superseded 册/evidence 固定链接/任务卡历史行不改动。
- **文档治理清扫（本卡范围第 2 条）**：089 卡「四项用户拍板」清单→「定案口径」、外部评审通道流水（claude/grok 计数、双通道矛盾、同根因合并框架）→处置结果事实；090 卡外评通道流水（grok max-turns 提限重跑等）→处置结果事实、「复评处置」节→「追加处置」（轮次定位去除）；计划进度区 089/090 两行与 090 复评行同型清扫。
- **验证**：定向容器组 8 类全绿（QuerySemanticsBaseline 23/MutexSemantics 6/TargetModeClosure 6/AutoGrantEngineContract 6/AuthorizationChangeInvalidation 3/QueryExecution 15/BatchAuthCheck 11/QueryBoundaryArchitecture 8+退役锁 1）；单测轨道 `-DskipTestcontainers=true` 1562 绿；收口全量 `mvn test -T 1C`（含 E2E/heavy）结果见下方回归行。
- **回归（收口形态）**：`mvn test -T 1C` 全模块 BUILD SUCCESS（含 E2E 两垂直切片与 heavy 容器组，0 失败，日志整文件 `/tmp/t092-full-regression.log`）；回归中段评审处置改写 TargetModeClosurePgIT 夹具（seeder 收敛带参重载），改后定向复跑 6/6 绿。

## 追加处置（2026-09-27）

- **文档现在时残留清零（X04「文档全查」补漏）**：implementation.md §7.6 条 3（同册自相矛盾处）；契约总册 §18.2 批量口径 queryBatch 句；frontend/permission-grant.md（PermQuery.inheritParents 措辞→判定面继承/展示面展开两语义）；project-rules §1.1（PermResult→DecisionResult/GrantSetResult）；capability-structure 归属表 ResolveContext 行补删除注记；pending-problems Q-044 消费方清单去 PermQueryEngine；`BusinessKeyUtil` 三处 Javadoc 现役消费方失实（perm-sdk 模块——初版清扫面漏登记）；`BatchPermMutexEvaluator` hitItemCount 注释；`AccessCacheCatalog` ROLE_PERM_SNAPSHOT 条目 forUserView 措辞；`AuthCheckReq` inheritMode 词表（CHILDREN→NONE/PARENT/BOTH，对齐契约 §18.2——存量注释错，实现本就只认 PARENT/BOTH）；`ConditionAppServiceImpl` passesScopeAll 措辞（A.4 点名方法的注释残留）；frontend/.env.development 响应壳措辞。
- **死键删除**：`BusinessKeyUtil.permEntrySourceKey`/`inheritedEntryKey` 唯一生产调用方为已删除旧引擎，按 T-PERM-090 scopeItemKey 同款先例注销＋ParityTest 两用例删除＋implementation.md §8.1 补收清单同步（含 090 期漏改的 scopeItemKey 残留提及）。
- **死通知口删除**：`BatchPermMutexEvaluator.notifyHits`＋`MutexHit` 唯一调用方为已删除旧引擎 queryBatch/getDenied ledger flush——接口方法、`PermissionConflictDomainServiceImpl` 实现与 Javadoc、两用例删除；计算/描述面（compute/describeRules）保留（新引擎 CandidateEvaluator/QueryAuditCollector 消费）。
- **格式与指令面修正**：PermissionCheckAppServiceImpl Javadoc 与签名并行拆行（本卡机械编辑引入）；CandidateEvaluator 双 import 并行拆行（T-PERM-088 同型类推）；skill「相关文件」ConditionEvalUtils 路径改 perm-common 实际位置（双副本同步）；UserMenuQueryAppService 类注释改正为真实消费路径（菜单判定经 PermissionViewAppService GRANT_LIST＋子孙扩展，非 QueryGate）。
- **退役锁负向自证**：`checkRetiredClassesAbsent` 抽为可复用规则＋测试源夹具 `engine.dto.PermResult` 占用退役 FQCN 专用导入断言必红（§8.4 负向自证先例形态）；同形改名/测试源复活不在主源码 FQCN 锁覆盖内（非目标）。
- **存量观察处置**：UserRoleWriteProjectionPgIT 字段名 permQueryEngine→queryGate（15 用点）；带日期历史句/superseded 册/任务卡历史行/T-PERM-036（非目标登记）维持不动。
- **A.8 不回补**：A.8 为时点清点册、092 为该清单末张清扫卡，perm-sdk 与 pending-problems 扩展面以本卡记录为准，计划归档条件核对同此。
- **单条互斥支线裁剪（2026-09-27 定案）**：`PermissionConflictDomainService.filterPermMutex`/`computePermMutex` 及专属实现体（`computePermMutexInternal`、`PermMutexComputationInternal`、`notifyPermConflict` 逐次通知口）删除——判定面全量切 execute 后零生产调用，六个单测用例随删；PERM_MUTEX 剔除语义唯一实现＝请求级批量评估器（两端同场双丢/单端不生效/I01 空规则短路/真实 triggeredRuleIds 由 `BatchPermMutexEvaluatorTest` 锁定，ConflictEvidence 审计形态由 `QueryAuditAndTraceTest` 锁定）；`BatchPermMutexEvaluator` Javadoc 与 implementation.md §2.4 接口册语义锚改自述口径。角色面 `computeRoleMutex`/`filterRoleMutex` 有生产消费（`resolveJudgementRoleIds` 主体解析），不在裁剪范围。
- **终态管线图父绑定语义勘正**：implementation.md §3.3 与 core-flows §7——depend_on 子行随装载进入（读侧不排除），父绑定（bind）先于条件评估与互斥（TYPE_LEVEL/无父上下文＝纯排除，T-PERM-058 只认主授权同口径；带父上下文目标集保留匹配父权限的子行，含 scopeAll 子行）；原图把父过滤列于互斥之后且称「读侧排除」，与 `evaluateStage` 实际顺序不符。
- **指令/契约现在时残留勘正**：permission-coding-standards §19 检查清单条 6（`engine.query()`→QueryGate 判定面门面口径）；契约总册 §15.1 权限门禁句（passesScopeAll→类型级（scopeAll）判定措辞）。
- **处置后验证**：定向三类全绿（PermissionConflictDomainServiceImplTest〔角色面保留用例〕/BatchPermMutexEvaluatorTest/QueryAuditAndTraceTest，合计 38 项 0 失败）；处置后收口全量 `mvn test -T 1C`（含 E2E/heavy）BUILD SUCCESS 0 失败（日志 `/tmp/t092-full-regression-r3.log`）。
