---
doc_type: task
id: T-PERM-082
title: （R2-T03）新请求/结果模型与合法组合
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §2/§3.1/§3.3/§4.1
depends_on:
  - T-PERM-080
blocks: []
acceptance:
  - "QueryRequest/QueryItem/四种 Selection（TYPE_LEVEL/TARGET_SET/GRANT_LIST/OPERATION_ADMISSION）/三种 ResultForm/OutputSpec/EvaluationCoverage 模型与不可变构造、受控工厂落地；合法组合表与首版混批约束执行前整体结构校验；I07：同一事务先写后新 execute 创建新 RunState、不复用上次事实"
  - "C01~C08 契约单测全绿：空请求零权限 I/O（C01）；DECISION+PRESERVE/SKIP 结构错误（C02）；GRANT_LIST+DECISION 混批结构错误（C03）；Roles(empty) 返回 NO_ROLE 不回退登录用户（C04）；重复 key/空 type/空 op 校验错误（C05）；两个不同 key 同目标等长同序各自返回（C06）；防御性复制（C07）；attributes 不可伪造保留键（C08）；R03：Roles 主体角色视角不暗中解析用户、不补加角色（Roles({R1,R2}) 互斥对事实完整返回、不因 ROLE_MUTEX 清空——§2.2 定稿口径）"
  - "三结果不互冒充：GrantSetResult 无 allowed()，AdmissionResult 不实现最终授权布尔且恒 finalCheckRequired=true"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-25
---

# T-PERM-082 （R2-T03）新请求/结果模型与合法组合

## 背景

设计 §2/§3 的内部契约模型与 §4.1 职责划分（报告临时编号 R2-T03）。内部 QueryRequest 不直接作为 Controller JSON DTO；外部业务键由适配层转换。

## 范围

- record 模型 + sealed Selection/ResourceRef + 合法组合矩阵 + 混批约束的结构校验（QueryValidationException 与外部参数错误映射分界，§3.4）。
- RunState 生命周期骨架（单次执行态、注入 Clock 固定服务端时刻；不含跨请求复用）。
- 时区拍板落码：不引入 ZoneId 抽象，评估时刻沿进程本地时钟（设计 §2.2 定稿句）。

## 非目标 / 遗留

- 不动旧执行体（迁移在 T-PERM-089+）；本卡产物与旧引擎并行存在直至 T-PERM-092。
- R03 事实完整性半边与 C06 判定版分别随 T-PERM-086/085 补锁（断言 as() 已标注翻转式契约）；I07 写后复读三态记忆版随 T-PERM-084/085 数据面落地补强。
- User 主体解析（有效角色+ROLE_MUTEX 共同入口）与全部判定阶段随 T-PERM-084~086 / T-ACCESS-057（ADMISSION_CANDIDATES）落地，骨架期该区域 fail-closed 抛 UnsupportedOperationException。

## 完成记录

**交付（2026-09-25，`engine/query` 新包与旧引擎并行，零生产消费者、旧执行体零改动）**：

- 契约模型：`QueryRequest`/`QueryItem`（受控工厂 decision/facts/grantListFacts/admission/admissionFacts）、Subject 封闭变体 `User`/`Roles`、`CallerContext`（保留键 clientIp/evaluatedAt/timestamp 顶层拒绝＋JSON 值域〔不可变数值白名单〕＋循环引用拒绝＋深层防御性复制）、`ReadOptions`（ListGrantRead DATABASE/ROLE_SNAPSHOT，缺省沿现行 ROLE_SNAPSHOT）、`TypeOperation`/`ResourceRef(ByCode/ByEntityId)`/`TargetClause`/`ParentRequirement`/`Inheritance`/`TypeFallback`、四 Selection 封闭（TypeLevel/TargetSet/GrantList/OperationAdmission）、`Evaluation`（full/evaluateSkip/preserveSkip/preserveEnforce）、`ResultForm`/`OutputSpec`（minimal→full 工厂族）、`QueryResult`＋三结果（DecisionResult/GrantSetResult/AdmissionResult——GrantSetResult 无 allowed()、AdmissionResult 恒 finalCheckRequired=true 且 ALLOW⇔reason 构造契约焊死）、`ResultDetails`、`EvaluationCoverage`（嵌套枚举）与 `Stage`、`QueryValidationException`。
- 结构校验（`QueryRequestValidator`，包私有）：合法组合矩阵全行（§2.5）＋首版混批约束（GRANT_LIST 单项独占、OPERATION_ADMISSION 纯批且同批同结果形式、至多一个不同父要求）＋§2.6 结构面（空 requirements/clauses、空类型/操作、非法实体 ID、重复 key、id 正数、null 组件、父要求空操作集拒绝、FACTS≥KEPT），执行前整体拒绝零权限 I/O；未知 type/operation/code 不在校验面（运行时按 item 未命中，不过度拒绝）。
- 骨架执行器（`QueryExecutionEngine`，迁移期暂名——拍板①）：结构校验→空 items 零权限 I/O 短路→创建 `RunState`（注入 Clock 固定服务端时刻，无 ZoneId 抽象——时区拍板落码）→主体解析一次（Roles 原样采用＋EXPLICIT_ROLES＝R03 主体半边；User 随 084/085）→`Roles(empty)` 按结果形式返回 NO_ROLE（C04）→未实现判定阶段抛 UnsupportedOperationException fail-closed（拍板②）→finally 释放运行态；I07 以两次 execute executionId 互异锁运行态不复用。
- 契约单测三件（`QueryContractModelsTest`/`QueryRequestValidationTest`/`QueryExecutionEngineTest`，纯 JUnit 单测轨）：C01~C08/R03/I07 全覆盖，按拍板③锁结构半边＋as() 翻转式标注（R03 事实半边→T-PERM-086、C06 判定半边→T-PERM-085、I07 记忆版→T-PERM-084/085）。

**三项拍板（registry 2026-09-25 同日行）**：①骨架独立新类 `engine/query`（暂名 QueryExecutionEngine、暂不注册 Spring bean、零消费者、旧 PermQueryEngine 零改动，终名随 T-PERM-092 定，设计 §4.1 已补迁移期注＝本卡 design_writeback）；②未实现判定阶段 UOE fail-closed；③R03/C06 锁结构半边＋翻转式标注。同批减法拍板：SkipReason.NOT_APPLICABLE 零引用删除（随阶段落地卡按需加回）。

**双轨评审处置（代码轨 P2×1+P3×3、文档轨 P3×4，全数逐条核实成立、全采纳直修）**：代码轨——P2 CallerContext 数值分支按引用放行任意可变 Number（AtomicInteger 等非 JSON 值、可变且绕过有限值检查）→收敛不可变数值白名单＋回归锁；P3×3＝null 集合/null 元素构造边界三通道不一致（javadoc 钉死「null=编程错误 NPE、空集合=结构错误」）、「至多一父」按字面判等的寻址等价边界（javadoc 注记调用方归一）、C03 GRANT_LIST+DECISION validator 级负向锁缺失（补断言）。文档轨——P3×4＝前向引用归属修正（ADMISSION_CANDIDATES→T-ACCESS-057〔ADM-T02〕，非 R2 085~088）、acceptance C 编号枚举补 C06 对齐测试口径（沿 T-PERM-061 先例）、设计 §4.1 迁移期注（design_writeback）、三项拍板 registry 当轮登记。计划附录 A.7 不入册三测试类（零旧执行体符号接触、非迁移资产——清点册判据：入册条件为旧符号接触或差分基线锚）。

**claude 外评处置（2026-09-25，通道=claude headless plan、实际生效模型=deepseek-flash[1m]〔banner 实证；本机 settings 声明 astron-code-latest 存在漂移〕、禁子代理；P0-P2=0、P3×3 逐条代码级核实全成立、全采纳直修，专项五项清单全过——合法组合矩阵逐行/混批三条/C 项覆盖与半边归属/隔离与收口一致性/双轨处置复核均无新错）**：①P3 noRoleCoverage 硬编码 EXPLICIT_ROLES——运行态已记录真实解析方式但未消费（User 主体落地后 NO_ROLE 覆盖事实会失真且现有测试结构上不可见），改消费 `run.subjectResolution()`（取值处 requireNonNull）＋补 DISALLOW 分支 skippedStages 用例（此前仅 ALLOW 有锁）；②P3 CallerContext javadoc「null 键（含嵌套）静默过滤」与实现不符（嵌套非 String 键实为拒绝；所引 PermEvalContext 先例也仅顶层浅过滤）——javadoc 改为「顶层 null 键与各层 null 值过滤、嵌套非 String 键拒绝」＋补嵌套 null 键断言＋为未来条件展平站点写明「保留键仅顶层」语义保持句；③P3 normalize 用例 Roles 行传 `Set.of()` 未测 null（判别力为零且与 Roles 契约 NPE 相反）——改为契约 record（Roles/TypeLevel/TargetSet/ParentRequirement）null 集合与 null 元素 NPE 边界锁＋Roles 源集合突变复制断言补齐。同批减法拍板：OutputSpec.withExtraOperationKeys 零调用删除（沿 NOT_APPLICABLE 先例随需要加回）。存量观察两条不处置：PermEvalContext（顶层浅过滤）与 CallerContext（递归深严校验）对同一 attributes 接受面分叉=迁移期设计内差异（§2.2 新契约从严），随 T-PERM-089~091 适配层统一；本地 surefire-reports 目录多轮残留致 testcase 合计与单轮聚合差 1，非单轮事实（全量日志分模块聚合为准）。定向复跑全绿（三测试类合计 52 用例，当轮 surefire 报告为准）。

**codex sol 复评处置（2026-09-25，通道=codex exec、模型=gpt-6-sol×xhigh〔banner 核对一致〕、禁子代理、read-only 沙箱；P0-P2=0、P3×1 逐条代码级核实成立直修；上轮 claude 四组修复确认落位、两删除符号零残留、零消费者复扫通过、过度设计与存量观察均无）**：P3 OutputSpec canonical 构造允许 `factDetail=null` 而校验器只拒 `FactDetail.NONE`——FACTS 项以 null 档位绕过「至少 KEPT」结构约束（§3.3），投影接入后会在读档位处失败——校验器补「任一结果形式 factDetail 非空」前置拒绝＋canonical 通道负向锁；类推扫描全包其余经 canonical 构造可达校验的枚举组件（inheritance/typeFallback/resultForm/evaluation）均已既有 null 检查，唯一缝隙即本处。评审注「本地顶层 surefire 报告 tests=0 不能复核计数」属 @Nested 分桶报告既有口径（外层类恒 0，以 XML testcase 为准）与残留目录混叠，非缺口。定向复跑全绿（三测试类合计 53 用例，当轮 surefire 报告为准）。

**回归证据**：定向三契约测试类全绿（2026-09-25，当轮 surefire 报告为准）；收口全量回归 `mvn test -T 1C`（含 E2E/heavy）：BUILD SUCCESS，2026-09-25，总耗时 16:28——access-service 单测组 1448（含本卡三契约测试类 49）＋容器组 362＋E2E 16，全模块 0 失败 0 错误 0 跳过（日志整文件 `/tmp/t082-full-regression.log`）。
