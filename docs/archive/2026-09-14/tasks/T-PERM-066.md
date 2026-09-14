---
doc_type: task
id: T-PERM-066
title: operationCodeKey 族大小写口径统一——raw 严格化（Q-003 转出）
status: done
plan:
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md#§2.4/§2.5（入参大写集中注记）
  - docs/design/engine/implementation.md#§8.2
depends_on: []
blocks: []
acceptance:
  - 入站 DTO operationCode 族 + 同链路 resourceTypeCode 族字段全部带 @Pattern("^[A-Z][A-Z0-9_]*$")：SDK 单源 5 Req（AuthCheckReq / BatchAuthCheckReq+AuthCheckItem / QueryScopesReq / QueryResourcesReq / UserEffectivePermissionCodesReq）+ access-service 4 Req（ApplyGrantPlanReq.GrantRecordKey / ResourceDependencyCreateReq / ResourceDependencyUpdateReq / DependencyBatchSyncReq.DependencySyncItem）
  - 定义侧锁死：OperationCreateReq / OperationUpdateReq / OperationKeyReq(.KeysReq 级联) 的 code+resourceTypeCode 与 TypeCreateReq.typeCode 同款 @Pattern（typeKey 不动）
  - 授权域归一退役：PermissionGrantDomainServiceImpl（13 处 toUpperCase）与 PermissionGrantPlanDomainServiceImpl（normalize()/normalizeTypeValues() 及调用点）全部删除，grep 零残留（2026-08-30 Locale 行不受影响——整点退役非 Locale 化）
  - 回归锁：每个改造面的 DTO 级小写拒绝用例（旧实现无注解必红）；PermCommonReqContractTest 注解签名快照同批更新
  - 契约总册 §2.4 操作行 + §2.5 入参大写集中注记（覆盖面/边界/守卫/SDK 生效说明）+ implementation §8.2 待统一登记改定案终态；Q-003 收敛入已收敛索引
  - 全量回归含 E2E 绿（收口形态 -T 1C）
design_writeback:
  required: true
  status: done
last_updated: 2026-09-14
---

## 背景

Q-003（2026-09-13 登记，历史登记收编自 2026-09-07「保持现状待统一」定案）：operationCodeKey 族大小写口径两域不一致——授权域（PermissionGrantDomainServiceImpl / PermissionGrantPlanDomainServiceImpl）对入参与 DB 装载码统一 `toUpperCase(Locale.ROOT)`（Plan 域还 `trim()`），`apply-grant-plan` 提交 `code="view"`、`resourceTypeCode="data"` 甚至 `" view "` 均可匹配大写定义成功；查询/解析域（ResolveContext / TypeResolutionServiceImpl / PermissionQueryAppServiceImpl / Dependency / check 族）裸拼键 + SQL 原值等值，同一份小写走 check/dependency 链路 20005 fail-closed 拒绝。操作码定义 create（OperationCreateReq.code）与类型码 create（TypeCreateReq.typeCode）均无大小写约束——小写定义今天可建（未部署无存量）。

盘点补充事实：管理前端全大写字面量或服务器回传（往返一致）；SDK `AuthCheckReq` 等是外部调用方小写风险主入口；Req DTO 已单源 perm-common（T-PERM-065），注解服务端与 SDK 同批生效。

## 范围

**入站 DTO @Pattern（9 文件）**：
- SDK perm-common：AuthCheckReq（resourceTypeCode / parentResourceTypeCode / operationCode / parentOperationCodes 元素）、BatchAuthCheckReq（parentResourceTypeCode / parentOperationCodes 元素 + AuthCheckItem.resourceTypeCode / operationCode）、QueryScopesReq（parentResourceTypeCode / parentOperationCodes / scopeResourceTypeCodes / scopeOperationCodes 元素）、QueryResourcesReq（resourceTypeCodes / operationCodes 元素）、UserEffectivePermissionCodesReq（resourceTypeCodes 元素）
- access-service：ApplyGrantPlanReq.GrantRecordKey（resourceTypeCode / operationCode，children 经 @Valid 级联）、ResourceDependencyCreateReq / ResourceDependencyUpdateReq（source/targetResourceTypeCode + sourceOperationCodes / requiredOperationCodes 元素）、DependencyBatchSyncReq.DependencySyncItem（同 dependency 双 Req 形态）

**定义侧锁死（4 文件）**：OperationCreateReq / OperationUpdateReq / OperationKeyReq 的 code 与 resourceTypeCode；TypeCreateReq.typeCode（可选字段，@Pattern 仅在非 null 时生效）。

**归一退役（2 文件）**：PermissionGrantDomainServiceImpl（13 处 toUpperCase）+ PermissionGrantPlanDomainServiceImpl（normalize() 方法及 4 处调用点）。

**测试与快照**：新增 DTO 级小写拒绝回归锁（Validator 直测，BatchEntrySizeValidationTest 先例形态）；PermCommonReqContractTest 注解签名快照更新（AuthCheckReq×4 / BatchAuthCheckReq×2 / AuthCheckItem×2 条目）。

## 当前口径

定案 A **raw 严格化**（2026-09-14 用户拍板，registry 同日行）：
- 两域统一 raw 裸拼，大小写由 DTO 边界保证；小写入参全链 400/90001 一致拒绝（`MethodArgumentNotValidException` → GlobalErrorCode.VALIDATION_FAILED，project-rules §异常映射）。
- 弃归一宽松化理由与边界见 registry 行；roleTypeCode / subjectTypeCode / domainCode / typeKey 扩面不做。

## 验收对照

1. ✅ 入站 DTO @Pattern：SDK 5 Req（AuthCheckReq/BatchAuthCheckReq+AuthCheckItem/QueryScopesReq/QueryResourcesReq/UserEffectivePermissionCodesReq）+ access-service 4 Req（ApplyGrantPlanReq.GrantRecordKey/ResourceDependencyCreateReq/ResourceDependencyUpdateReq/DependencyBatchSyncReq.DependencySyncItem）——`OperationCodeCaseValidationTest` 7 用例逐面锁定（2026-09-14，旧实现无注解必红）。
2. ✅ 定义侧锁死：OperationCreateReq/OperationUpdateReq/OperationKeyReq 的 code+resourceTypeCode、TypeCreateReq.typeCode（可选非空才校验；typeKey 未动）——同测试 `definitionReqRejectsLowercaseCodes` 覆盖。
3. ✅ 授权域归一退役：PermissionGrantDomainServiceImpl 13 处 toUpperCase 全删（`rg toUpperCase` 零命中）；PermissionGrantPlanDomainServiceImpl normalize()/normalizeTypeValues() 方法与全部调用点删除（Locale import 一并清）；顺带删除 TypeDefinitionAppServiceImpl 显式 typeCode 分支的恒等 trim（@Pattern 保证无首尾空白）。
4. ✅ 回归锁与快照：`OperationCodeCaseValidationTest` 7/7 绿；`PermCommonReqContractTest` 2/2 绿（8 条注解签名快照同批更新）；DependencyAppServiceImplTest 两处计数断言随新约束更新（" " 类型码额外触发 @Pattern，6→8，35/35 绿）。
5. ✅ 契约总册 §2.4 操作行 + §2.5 T-PERM-066 集中注记（覆盖面/边界/守卫/SDK 生效说明）；implementation §8.2 登记待统一改定案终态 + last_reviewed 链头更新。
6. ✅ 全量回归含 E2E（收口形态 `-T 1C`，2026-09-14）：BUILD SUCCESS 06:31——perm-common 30 / common 65+15+10+106 / access-service 单测 1239（含本批新增 7）+ 容器 210 / e2e 14，零失败（日志整文件落盘解析）。

过程证据（2026-09-14）：`mvn install -pl perm-sdk/perm-common -DskipTests` 后 `mvn test -pl access-service -DskipTestcontainers=true` 1239 tests / 2 failures（均为 DependencyAppServiceImplTest 计数断言，随新约束更新后 35/35 绿）；`mvn test -pl perm-sdk/perm-common,perm-sdk/perm-client-spring-boot-starter,gateway` BUILD SUCCESS（perm-common 30 + starter/gateway 15 绿）。

## 非目标 / 遗留

- OrgQuery.operationCode（VIEW/CREATE 白名单 10008）、check-interface 固定 ACCESS、DomainClassifyServiceImpl typeCode 归一（classify 匹配语义非键构造）——均维持既有口径。
- 仅含 resourceTypeCode 的纯查询面（OperationListReq、ResourceListReq、ResourceDependencyCheckReq 等）不加 @Pattern——两域一致 raw 非不对称面，避免滑向 A+（全码族锁，用户未选）。
- QueryScopesReq / QueryResourcesReq / UserEffectivePermissionCodesReq 不在 `PermCommonReqContractTest.GUARDED_CLASSES`（T-PERM-065 建守卫时即未纳入，javadoc 声明句为证）——本批 @Pattern 由 `OperationCodeCaseValidationTest` 行为锁覆盖；GUARDED_CLASSES 扩容（签名级快照漂移检测补位）登记为后续轻量项。
- SDK 消费方客户端侧若启用 bean validation 会随注解前置拒绝小写——契约 §2.5 注记已说明。

## 完成记录

- 实施（2026-09-14）：13 个 Req DTO 加 @Pattern（SDK 单源 5 + access-service 8，列表字段全部容器元素注解、嵌套 record 经 @Valid 级联）；授权域归一退役（PermissionGrantDomainServiceImpl 13 处 toUpperCase + Plan 域 normalize/normalizeTypeValues/Locale import）；TypeDefinitionAppServiceImpl 显式 typeCode 分支恒等 trim 顺带删除。
- 双轨评审（2026-09-14，代码轨 + 文档轨子代理并行）：代码轨零 P0-P2（P3×3——恒等 trim 已修；Query 三 Req 不在 GUARDED_CLASSES 判定为 T-PERM-065 既有范围且行为锁已补位，扩容登记遗留；ResourceDependencyCheckReq 未锁为定案边界忠实执行）；文档轨 P1×1（决策落点引用旧册死锚点 §5.3/§5.5/§6.1/§6.7/§6.9，registry/任务卡/看板四处统一改指总册 §2.4/§2.5）+ P2×3（契约 last_reviewed 补链头；§2.5 边界句补「仅含 resourceTypeCode 的纯查询/CRUD 面不锁」+复数形枚举+标题泛化；registry 2026-09-07 历史行补【已统一】时点标记，line 29 先例）+ P3 措辞四项（toUpperCase 计数 13、定义侧描述补 resourceTypeCode 等）——全部处置。
- 定案与登记：decision-registry 2026-09-14 行（raw 严格化三项 + 弃归一化理由 + 边界）；契约总册 §2.4/§2.5 集中注记；implementation §8.2 定案终态 + last_reviewed 链头；Q-003 收敛入已收敛索引。
- 验证链：`mvn install -pl perm-sdk/perm-common -DskipTests`（SNAPSHOT 上游刷新）→ 单测轨道 1239/0（DependencyAppServiceImplTest 两处计数断言随新约束 6→8）→ `mvn test -T 1C` 收口形态 BUILD SUCCESS 06:31 全绿（数字见验收对照第 6 条）。
