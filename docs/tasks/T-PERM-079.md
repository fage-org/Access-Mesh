---
doc_type: task
id: T-PERM-079
title: 自动授权批次风格与可读性清理（070/074/071 触达面）
status: proposed
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
last_updated: 2026-09-21
---

# T-PERM-079 自动授权批次风格与可读性清理

## 背景

2026-09-21 对自动授权序列（T-PERM-070/074/078/071）做了内部四任务评审 + claude 外评：四任务验收达标、P0-P2=0；风格专项交叉印证的结构性发现（可用但可读性/可维护性风险）经用户拍板**另立本任务、评审轮不修**（registry 2026-09-21 行）。发现明细以两份评审报告为准（内部四子代理 + claude 输出，存于会话记录），本卡 acceptance 已收录全部主项。

## 范围

access-service sync/resource/credential 触达面 + perm-sdk registration starter + 相关 PgIT。均为零行为重构；若实施中发现需行为变更（如 PgIT 拆档引入分轨标签），按决策提问协议单独拍板。

## 非目标 / 遗留

不改契约语义、不动 072/073 待实施面；resource_dependency.sync_key 保留列与 manifest 继承旧密钥边界为已定案，不在清理面。
