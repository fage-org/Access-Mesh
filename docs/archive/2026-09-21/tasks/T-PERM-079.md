---
doc_type: task
id: T-PERM-079
title: 自动授权批次风格与可读性清理（070/074/071 触达面）
status: done
plan: —（无所属计划；2026-09-21 四任务内外评审转出）
domain: access-service
design_refs: []   # 工程改进，无设计回写面（先例：T-ACCESS-030/031）
depends_on: []
blocks: []
acceptance:
  - "doSyncOneInternal 参数重构：ResourceEntitySyncAppServiceImpl（12 参）与 UserRoleSyncAppServiceImpl（11 参）的预解析组/开关收为参数组 record（如 PrefetchedItem/FullSyncPreload），调用点消除 5 个裸 null 与 3 个裸 true；两文件同批。"
  - "状态/来源字面量常量化：RESOLVED/REJECTED/SUCCESS/PARTIAL/FAILED/MANIFEST/MANIFEST_UNCHANGED 等散布 4 文件 17 处收敛为单源常量；retryClass 改对常量比较（消除「改 reason 字面量静默降级 NON_RETRYABLE」耦合，DDL CHECK 注释同批）。"
  - "「服务已注册且启用」判据收敛 ServiceConfigDomainService 一处，5 调用点（ResourceTypeOwnershipGuard×2、SyncTypeGuard、ServiceCredentialAppServiceImpl、ServiceCredentialDomainServiceImpl、PermissionManifestAppServiceImpl）统一。"
  - "分批落库样板（for+offset+SQL_BATCH_SIZE 三行循环，13 处）提公共 helper；SQL_BATCH_SIZE 三处重复声明收敛单源。"
  - "var 风格定调：全仓 41 处 var 中 37 处来自 071 批——要么改回显式类型对齐全仓主流，要么作为风格决策登记 registry 后全仓统一；二选一不并存。"
  - "次要项同批：ServiceAuthArbiter 两处 JSON 403 信封提取；doSyncOne/doSyncOneInternal 改名表意；AutoGrantEngineContractPgIT 魔法值常量化（910 区段注释）；PgIT 硬编码锁键前缀收敛 TreeWriteLockSupport.lockKey；恒假租户校验（PermissionManifestAppServiceImpl:65）删除或改显式 fail-closed；SDK 格式化（SCHEMA_VERSION 常量/内联 FQCN/逗号空格）；ResourcePublicationPgIT 拆档（日常轨跳过超大规模清理用例）。"
  - "全部为可读性重构零行为变化；改后定向重跑受影响测试，收口跑全量含 E2E。"
design_writeback:
  required: false
  status: none
last_updated: 2026-09-21（done：七项验收全落地+两项拍板 registry 登记+收口全量三段全绿）
---

# T-PERM-079 自动授权批次风格与可读性清理

## 背景

2026-09-21 对自动授权序列（T-PERM-070/074/078/071）做了内部四任务评审 + claude 外评：四任务验收达标、P0-P2=0；风格专项交叉印证的结构性发现（可用但可读性/可维护性风险）经用户拍板**另立本任务、评审轮不修**（registry 2026-09-21 行）。发现明细以两份评审报告为准（内部四子代理 + claude 输出，存于会话记录），本卡 acceptance 已收录全部主项。

## 范围

access-service sync/resource/credential 触达面 + perm-sdk registration starter + 相关 PgIT。均为零行为重构；若实施中发现需行为变更（如 PgIT 拆档引入分轨标签），按决策提问协议单独拍板。

## 非目标 / 遗留

不改契约语义、不动 072/073 待实施面；resource_dependency.sync_key 保留列与 manifest 继承旧密钥边界为已定案，不在清理面。

## 完成记录（2026-09-21）

**两项用户拍板（registry 同日登记）**：①var 局部变量风格**允许**——生产与测试均可用，071 批存量（生产约 54 处）不回改，后续评审不得以 var 报风格问题；②超大规模清理用例**拆档**——`ResourcePublicationHeavyPgIT` 挂 `testcontainers-heavy` 双标签，pom 新增 `-DskipHeavyIT`（默认 false 收口必跑），日常全量形态命令扩展为 `mvn test -T 1C -DskipE2E=true -DskipHeavyIT=true`（ci.yml 单测 job 现行两开关不受影响）。

**①参数组重构+改名（两文件同批）**：`ResourceEntitySyncAppServiceImpl.doSyncOneInternal`（12 参）的 6 个预解析/开关散参收为私有 record `FullSyncPrefetch`（NONE 静态形态=单条路径无预载）；`UserRoleSyncAppServiceImpl.doSyncOneInternal`（11 参）收为 `FullSyncPreload`（resolved 开关+三项预解析 ID+预载 existing+归属/批内互斥/元数据三个批级 Map）。调用点裸 null/裸 true 全部消除；`doSyncOne/doSyncOneInternal` 改名 `applySingleSync/applyItemSync`（表意 single 入口/单项核心）。

**②状态/来源字面量常量化**：compile_status → 实体常量 `PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED/_REJECTED`（9 处消费点换用）；sync_status → `ServiceManifestSync.SYNC_STATUS_SUCCESS/_PARTIAL/_FAILED`（manifest 写入/幂等判定 + resource_publication_state.last_full_status 共域消费）；maintain_source → `ResourceDependency.MAINTAIN_SOURCE_MANIFEST`；编译拒绝原因 → `DependencyCompiler.REASON_*` 六常量（产出口径单源）；`retryClass` 改对常量比较（消除「改 reason 措辞静默降级 NON_RETRYABLE」耦合）；schema 两处 CHECK 约束补 `--` 注释钉「代码侧唯一引用点」（纯 SQL 注释，无 DB 对象变更，不触迁移同步面）。

**③服务启用判据收敛**：`ServiceConfigDomainService.isRegisteredAndEnabled(ServiceConfig)` 行级 static 唯一出口（行存在且 status=1；软删行经查询谓词 delete_flag=0 天然排除——守卫侧原 deleteFlag 分支为死分支删除）。6 调用点统一「取行+行级判定」形态：ResourceTypeOwnershipGuard×2、SyncTypeGuard、PermissionManifestAppServiceImpl、ServiceCredentialAppServiceImpl/DomainServiceImpl。**附带修复**：SyncTypeGuard 由直读 `ServiceConfigMapper`（跨包 mapper 直读）改为经 ServiceConfigDomainService；PermissionManifestAppServiceImpl 同步换域服务依赖。不做查询+判定合并的便捷重载——接口 default 方法会被 Mockito 整体拦截破坏既有测试桩（实施中实证）。

**④分批样板收敛**：新建 `infrastructure/util/SqlBatches`（BATCH_SIZE=500 单源声明 + forEach 分批消费），22 处 for+offset 循环（活树实际数，任务卡登记 13 处为评审时点）收敛，6 处私有批大小常量声明删除（SQL_BATCH_SIZE×5+RESOURCE_BATCH_SIZE×1；对账 ROLE_BATCH_SIZE=200 为语义不同的独立批值保留）。

**⑤var**：登记 registry 允许（见拍板①），零代码改动。

**⑥次要项**：ServiceAuthArbiter 两处 403 JSON 信封提取 `writeForbiddenJson` 共用；`AutoGrantEngineContractPgIT` 魔法值常量化（CUSTOM_TYPE_VALUE_BASE=910 注释钉段位依据=终值分配表种子段最大 31+管理面 max+1 顺延、ROLE_TYPE_BASIC_ROLE=6/USER_TYPE_USER=1、BIT_VIEW/UPDATE/EXPORT、TYPE_CODE_PREFIX）；`TreeWriteLockSupport` 新增公开静态 `lockKey(tenantId, target)`（内部与 TreeCycleHardeningPgIT/SyncFailureAtomicityPgIT 两处裸拼共用同一 key 空间）；PermissionManifestAppServiceImpl 恒假租户校验删除（实证：TenantContextHolder.getTenantId 委托 AccessRequestContext，控制器传入与方法内读为同一来源自比较死代码；PermissionManifestPgIT 的 SERVICE_IDENTITY_REQUIRED 断言走 service==null 分支不受影响）；SDK `PermissionRegistrationPublisher` 格式化（SCHEMA_VERSION 常量取代魔法数 1、内联 java.util.* FQCN 改 import、逗号空格）；ResourcePublicationPgIT 拆档（15→12 用例，heavy 1 用例独立类）。测试适配：SyncTypeGuardTest mock 换域服务（已删行用例改注查询谓词语义）、ServiceConfigAppServiceImplTest 用真实域服务实现包 mock mapper、ResourceTypeOwnershipGuardTest 已删行分支改注死分支删除语义。

**验证**：定向单测 117/117（九类：SyncTypeGuard/OwnershipGuard/ServiceConfigApp/ResourceEntitySync/UserRoleSync/FullSyncResponseContract/DependencyCompiler/DependencyApp/ServiceCredentialApp）；容器轨定向 95/95（14 类含 heavy 377s 全绿、AutoGrant 引擎契约 6/物化 19/洞察 16/manifest 8/发布 12/级联 4/凭证 3/锁树 5/原子性 8/SecurityMatrix 14）；SDK 14/14；skipHeavyIT 开关双向验证（默认 heavy 跑、true 时 0 tests 排除）。收口全量回归 `mvn test -T 1C` 三段完成全绿：上游模块（common/gateway/sdk/example）第一轮 SUCCESS；access-service 第二轮（-rf 续跑）SUCCESS——单测轨 305 + 容器组 1308 全绿（含 heavy）；e2e 补跑（`-pl e2e -am`）15 全绿（BasicRoleGrant 8 + ExampleProtectedApi 7）。第一轮 access-service 仅 AutoGrantMigrationPgIT 1 失败——schema CHECK 注释未同步迁移脚本（快照比对含注释已知坑），`docs/ops/auto-grant-migrate-071.sql` 两处注释同批镜像后隔离复跑 6/6 绿。

**claude 外评处置（2026-09-21，claude 通道默认模型 astron-code-latest，P0-P2 零、P3×2、过度设计无、专项清单六条全过）**：①P3-1 AutoGrantEngineContractPgIT 常量名实不符（shouldKeepConditionsSpecificToEachCanonicalOperation 的 EXPORT 按值保真替换落成 BIT_UPDATE——位值在本文件按用例任意分配、常量名暗含操作绑定）**直修**：EXPORT 用例改用 BIT_EXPORT（4→8，掩码 0 无覆盖关系、测试语义不变），另收敛三处替换遗漏的裸位值字面量 2 → BIT_VIEW；②P3-2 本卡 done 未按 registry 2026-09-12 归档制「无所属计划终态单卡即行单卡归档」**直修**：本卡迁 `docs/archive/2026-09-21/tasks/` + 批次 README + 归档记录表/目录树/看板链接同步。存量观察两条不处置：manifest 服务启用判定位于树锁之后（072「锁下移门禁后」修复未覆盖该入口，存量未被本次放大）、对账 ROLE_BATCH_SIZE=200 手写循环保留（卡片已声明独立语义）。评审对四条主链（参数组/分批/常量化/判据）零行为等价性逐点确认，excludedGroups 空串语义经 surefire 3.5.4 字节码核实为无排除。
