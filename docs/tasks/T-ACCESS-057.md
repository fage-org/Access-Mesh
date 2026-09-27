---
doc_type: task
id: T-ACCESS-057
title: （ADM-T02）OPERATION_ADMISSION 阶段与新结果
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.3/§6.1/§7.2/§7.3/§7.4
  - docs/design/access-service-api-contract.md §25.3/§25.4/§25.6
  - docs/design/engine/implementation.md §3.3/§3.5/§3.6
  - docs/design/engine/core-flows.md
depends_on:
  - T-PERM-083
  - T-PERM-084
  - T-PERM-085
  - T-PERM-086
  - T-PERM-088
  - T-ACCESS-056
blocks: []
acceptance:
  - "ADMISSION_CANDIDATES 阶段：从已匹配路由要求取 type-operation→新鲜完整操作定义计算精确覆盖掩码（要求未知/损坏=接口层配置错误，不回退任意操作）；同批要求合并 type-mask 一次/分块读取 ALL+实例候选（新 selectAdmissionCandidatesByTypeMasks，不重载空 entityIds 含义）；子候选集中批量父结构核查（非法结构排除+诊断、CONTEXT_DEFERRED 标注、不评父条件不伪造父 matchedPermissionIds）"
  - "在线 ADMISSION 存在性短路仅因本用途无 PERM_MUTEX 才允许；拒绝项穷尽候选；FACTS 不按当前环境删条件分支（完整收集范围/条件身份/候选类别）；AdmissionResult 恒 finalCheckRequired=true；准入与普通目标/GRANT_LIST 混批按首版限制拒绝"
  - "N02/N03/N06~N10/N13 全绿＋N04/N05 准入半边（PERM_MUTEX 短路边界：准入不跨实例误拒、同实例真互斥可 MAY_ENTER 且审计不写互斥通过；业务半边实测归 T-ACCESS-061）（N12 本地投影×在线一致性归 T-ACCESS-059 端到端验收，不设本卡门槛）；N20：准入构建与在线只读新鲜操作定义目录/已批准安全目录，不消费旧 GRANT_LIST 评估结果与长 TTL 掩码缓存残留投影；来源语义（MANUAL/AUTO_DEP/AUTHORITY_ROOT 按真实覆盖参与、无来源特权、无条件与失败条件 OR 分支独立保留）锁定"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-27
---

# T-ACCESS-057 （ADM-T02）OPERATION_ADMISSION 阶段与新结果

## 背景

设计 §7.2~§7.4（报告临时编号 ADM-T02）。准入=「存在结构有效的覆盖候选且本行条件通过」，不是「存在已完整允许的实例」；不做跨实例/同实例最终 PERM_MUTEX 判定（N04/N05 反例锁死）。

## 范围

- 新阶段接入唯一 execute（复用原始事实结构、角色与条件能力）；审计事件独立标注 OPERATION_ADMISSION、不写「业务互斥已通过」（复用 T-PERM-088 证据结构）。
- 首版完整批量读候选再逻辑短路；流式 EXISTS 优化后置（须保留覆盖/结构/条件回源语义）。

## 当前口径

- 准入经唯一 execute 的 ADMISSION_CANDIDATES 阶段执行；操作定义固定新鲜数据库目录，候选与父结构分别合批读取。
- 审计范围、坏条件排除和配置错误优先级见[协议契约 §25.3/§25.4/§25.6](../design/access-service-api-contract.md#operation-admission-protocol)；实施落点见[引擎实现 §3.3/§3.5/§3.6](../design/engine/implementation.md#permission-query)。
- 本卡收口采用组合验证证据（2026-09-27 确认）：复用全量中已通过的其他单测、容器组与 heavy；仅修正测试夹具隔离后，完整复跑 QueryExecutionPgIT，并通过 reactor 补跑被模块失败阻断的 E2E。生产代码保持全量验证版本，不跳过任何验收轨道。

## 验收对照

- `AdmissionStagesTest`：N02/N03/N04/N05/N06/N07/N08/N09/N10/N13/N20 的准入行为、来源与条件分支；在线短路/拒绝穷尽、FACTS 完整性、混批与树展开拒绝、角色冲突审计、SQL 分块、配置故障优先级。
- `QueryExecutionPgIT`：准入候选 SQL 的 ALL/实例与租户/角色/类型/掩码过滤、空集守卫；真实父结构损坏排除、准入与业务父绑定对照、新鲜操作目录覆盖变更。
- `AdmissionConfigurationExceptionHandlerTest`：配置故障按 HTTP 200＋20071 返回服务端信封。

## 非目标 / 遗留

- 端点/快照/网关在 T-ACCESS-059；本地可下发条件分支的快照装配同在该卡。

## 完成记录

- 2026-09-27：ADMISSION_CANDIDATES 接入唯一 execute；新鲜操作目录、独立候选 SQL、批量父结构核查、本行条件求值/FACTS 保留、候选类别、配置故障接口映射与审计用途标注落地。设计引用章节及查询技能双副本已同步。
- 本地代码轨核对通过：租户/角色/类型掩码隔离、SQL 空集守卫与分块、父结构与运行时校验边界、条件 OR 分支、操作缓存隔离、异常与审计。测试夹具使用独立角色/资源，避免污染既有基线；无未处理缺陷或可裁剪项。
- 本地文档轨核对通过：契约 §25、设计 §5/§7、任务验收与实现范围一致；HTTP 200＋20071 服务端信封与网关 503 分层明确，端点/快照仍归 059；关键词扫描、链接锚点、技能双副本及任务/计划状态一致。存疑项均已按当前口径归位。
- 验证日期均为 2026-09-27，采用上文确认的组合证据：
  - `mvn test -T 1C`：执行 2237 项，2235 通过、2 项夹具污染失败、0 错误、0 跳过；heavy 用例执行并通过。E2E 被 access-service 模块失败阻断，未将该命令记为 BUILD SUCCESS。准入单测 `AdmissionStagesTest` 25 项及配置故障 HTTP 测试 1 项已通过。
  - `mvn test -pl access-service '-Dtest=QueryExecutionPgIT#should_readAdmissionAllAndInstanceWithStrictBoundaries_whenSqlInputsAreEmptyOrForeign+should_keepPerItemClosureAndSameTypeBoundary_whenTargetsMixInheritance'`：失败项隔离复跑 2 项通过，确认组合夹具污染；随后仅修测试数据，生产代码冻结。
  - `mvn test -pl access-service '-Dtest=QueryExecutionPgIT'`：修正后完整数据库类 24 项通过，0 失败/错误/跳过，BUILD SUCCESS。
  - `mvn test -pl e2e -am -T 1C '-Dtest=BasicRoleGrantVerticalSliceE2EIT,ExampleProtectedApiE2EIT' '-Dsurefire.failIfNoSpecifiedTests=false'`：跨服务 E2E 16 项通过，0 失败/错误/跳过，reactor BUILD SUCCESS。与前述有效证据合并覆盖全部收口轨道。
