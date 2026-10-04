---
doc_type: task
id: T-PERM-100
title: sync 通道 codeType 归一与存量空白行处置
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §19（sync 同步通道）
depends_on: []
blocks: []
acceptance:
  - "sync/full-sync 写入 codeType 与管理面同口径归一（trim；空值回退沿用契约 §19 现行口径，非本卡新拍板项），同步写入 \" BIZ \" 后管理面按 BIZ 可达（回归锁实证旧实现下不可达）"
  - "存量带空白 codeType 行处置定案（订正语句入 runbook 或维持现状+登记，按 decision-question-protocol 举例上报用户后拍板），不假称只修新写入就消除了存量"
  - "detail/update/remove 业务键寻址链路对归一后形态可达；20004 误报或同码另建不再发生"
  - "契约 §19 写入/寻址归一口径同步"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-PERM-100 sync 通道 codeType 归一与存量空白行处置

## 背景

承接 [Q-031](../../../pending-problems.md#q-031)：sync/full-sync 的 codeType 仅归一空值不 trim；管理创建与 `ResourceKeyReq.normalizedCodeType` 会 trim。同步写入 `" BIZ "` 后，管理面按 `BIZ` 查询不到——同步自查找仍可达，但 detail/update/remove 业务键不可达，可能返回 20004 或另建同码资源。

## 范围

sync/full-sync 写入归一统一为管理面同口径；存量带空白行处置定案（含 runbook 订正语句或明确维持现状）；契约 §19 口径同步。

## 当前口径

写入侧与寻址侧归一必须同源；差异只在 sync 通道。空值回退口径沿用现行契约（不为本任务改语义）。

## 非目标 / 遗留

- code 本身的归一策略（维持现状，问题仅登记 codeType）。

## 实现记录（2026-10-03）

**调查修正**：登记前提「差异只在 sync 通道」不完全成立——`TypeResolutionServiceImpl`（resolveResourceId 单条 :188 / batchResolveResourceIds 批量 :363 两处查找键）对 codeType 同样只做空值回退不 trim，授权 INSTANCE 解析、依赖只读 API（hasDependencyCycle/explain）、菜单、管理面与 sync 父解析共用该入口（`GrantRecordKey.codeType` 无 @Pattern，带空白入参为活口）；管理面 detail/update/remove 走 `ResourceKeyReq.normalizedCodeType()`（trim）不受影响。

**两项拍板（2026-10-03 用户）**：
1. 存量处置＝不提供订正 SQL：当前无部署环境、无存量带空白行（dev 库未运行未核，dev/演示数据全为干净 default，遇脏行走既有重建库流程）；若未来出现存量，未订正行的后果口径＝按归一寻址永久不可达、下次上游 full-sync 新代次经差异校准软删换 id 重建（显式授权引用旧实体 id 断链）。
2. 寻址侧一并 trim（超原验收范围的扩面）：`TypeResolutionServiceImpl` 两处查找键补 trim，与写入侧同源；结果 Map 键保持调用方原参形态（原参与归一入参调用方各自与请求键自洽配对）。

**实现面**：
- `ResourceEntitySyncReq` 增 `normalizeCodeType(String)` 静态单源（null/空白→default、trim，常量复用 `ResourceKeyReq.CODE_TYPE_DEFAULT`）+ 两 record 各增 `normalizedCodeType()`/`normalizedParentCodeType()`；
- `ResourceEntitySyncAppServiceImpl` 消费点全量替换（full-sync 阶段 A 父请求/阶段 B 批量索引收集/阶段 C 业务键与父键、applyItemSync 写入与父解析共 6 处形态 + 阶段 A 一处死局部变量顺带清除；`DEFAULT_CODE_TYPE` 常量随替换退役删除）；
- `ResourcePublicationNormalizer.codeType()` 改走 `ResourceEntitySyncReq.normalizeCodeType`——businessKey、payload 指纹同源归一，同代次同内容（仅空白差异）重发为幂等 STALE 而非 CONFLICT；
- `TypeResolutionServiceImpl` 单/批两处查找键 trim + 接口 javadoc 同步。

**红跑实证（HEAD 下）**：单测 3 红——写入归一 captor 断言（落库 `" BIZ "`）、批量寻址 trim（expected 100 got null）、单条寻址 trim（同）；PgIT 4 红——落库 code_type 断言 + 寻址 miss、同码两行（active 2≠1）、full-sync item/parent 落库、发布指纹 CONFLICT（staleCount 0≠1）。修复后全绿。

**定向回归**：单测轨 1810 项 0 失败；`ResourcePublicationPgIT` 16/16；关联容器组（SyncFailureAtomicity/DependencyLifecycle/ResourceBatchCreateCompositeIdentity/ResourceOperationKey PgIT）exit=0。

**契约回写**：§12.1 codeType 规则行（归一口径+寻址同源范围）、§19.1 幂等业务键 bullet、§19.2 父链 bullet、§19.7 通用规则（codeType 归一先于 businessKey 拼接与发布指纹）。

## 收口记录（2026-10-03）

- 双轨本地评审（主代理直跑）：代码轨 P0-P2=0（归一语义与 `ResourceKeyReq` 完全一致、8 处消费点全量替换、TypeResolution 批量结果键保持原参四消费方配对自洽、DUPLICATE_BUSINESS_KEY 归一语义与契约 §19.2.1「规范化后重复业务键」原文自洽）；文档轨 P0-P2=0（契约四处回写、schema 注释无需联动——归一为 API 层语义非存储语义）；过度设计可裁剪=0（仅 record 方法+常量复用）；存疑待决策=0（两项拍板先行落地）。
- 残留清扫（外评处置后改写为实际覆盖清单）：codeType 空白回退不 trim 的残留原为四处——`QueryReadSupport.defaultCodeType`（判定面引擎装载，随外评处置收口见下节）、manifest 通道（`PermissionManifestReq.ResourceKey` 构造器 + `DependencyCompilationDomainService.loadInputs` 直查装载，随外评处置收口见下节）、`DependencyAppServiceImpl:223`（回退值仅传入 `resolveResourceId`，chokepoint 内部归一已覆盖，无害）、`PermissionGrantPlanDomainServiceImpl:678`（INSTANCE 必填校验非归一，chokepoint 已覆盖）；`PermissionQueryAppServiceImpl` 清单过滤 equalsIgnoreCase 为显示面第三套口径（非寻址），维持现状。normalizer 残留 raw 访问器均为不可变拷贝构造。
- 收口全量回归 `-T 1C`（含 E2E/heavy）两轮：首轮 BUILD SUCCESS 4880 项 0 失败；外评处置扩面后复跑 BUILD SUCCESS 4884 项 0 失败 0 错误。

## 外评处置记录（2026-10-03，claude + codex sol 双通道）

- 结论汇总：claude P0-P2=0、P3×2；codex sol P2×1。双通道事实面一致——存在两条**不经 TypeResolution** 的寻址路径未被归一覆盖：①判定面引擎装载 `QueryReadSupport.defaultCodeType`（check/batch-check 的 ByCode 身份、queryScopes 父判定）；②依赖 manifest 编译通道（`PermissionManifestReq.ResourceKey` 构造器不 trim + `DependencyCompilationDomainService.loadInputs` 直查 mapper 原值比对）。主代理逐条亲核成立，认 codex P2 定级：写入侧已归一、消费侧留原值——「授权可保存但运行时 check 误拒 / manifest 报 RESOURCE_MISSING」为本次修复放大的分叉（修复前全链 raw 自洽）。
- claude 引用滑点（核实后指出）：将 r2-unified-query-and-admission.md 引作「活跃定案」（其称引擎 ByCode 不 trim 为 E05 定案），该稿 frontmatter 实为 superseded，活跃权威 engine/implementation.md 无「ByCode 不 trim」明文。
- 处置拍板（2026-10-03 用户）：**同源收口**——①`QueryReadSupport.defaultCodeType` 单点 trim（引擎全部装载与匹配键路径一处修复）；②`PermissionManifestNormalizer` 在目标去重、声明构造与双指纹前对 source/target 归一（perm-common DTO 不动，装载侧归一；带空白与干净形态为同一声明身份）。
- 红跑双证：`QueryReadSupportTest.should_resolveWhitespaceCodeTypeByNormalizedIdentity` 1 红（旧实现按原始 " BIZ " 装载 miss）；`PermissionManifestPgIT.shouldCompileWhitespaceCodeTypeDeclarations_byNormalizedAddressing` 1 红（旧实现 RESOURCE_MISSING + 指纹冲突）。修复后两类 25/25、9/9 绿，manifest/compiler/query 相关单测组全绿。
- 文档同步：契约 §12.1 括注改为实际覆盖枚举（含判定面引擎装载与 manifest 装载）、§19.10 补 codeType 归一句；本卡残留清扫句按实际范围改写。
