---
doc_type: task
id: T-PERM-069
title: API 类型内部来源收紧——种子声明 SYNC+access-service，管理面资源 CRUD 20055（Q-008 转出）
status: done
plan: —（Q-008 单卡转出）
domain: permission-center
design_refs:
  - docs/design/schema/access-service.sql#17 区块 type_definition 种子声明（UPDATE 语句 + extra COMMENT）
  - docs/design/access-service-api-contract.md#§12（20055 只读行）
  - docs/design/access-service-api-contract.md#§13（类型所有权声明段）
  - docs/design/access-service-api-contract.md#§19.1（类型级所有权门禁）
  - docs/design/access-service-api-contract.md#§5 主体保护段（resource 侧类型级所有权句）
  - docs/design/access-service-architecture.md#资源侧类型级所有权（T-PERM-052 段）
  - docs/design/architecture.md#服务间交互矩阵（接口资源注册行）
  - docs/design/services/example-service.md#接入路径
  - docs/design/extension-guide.md#声明约束
depends_on: []
blocks: []
acceptance:
  - "定案（2026-09-18 用户拍板「仅 API 收紧」）：API 类型种子声明 SYNC+access-service——API 资源行唯一事实入口=service-config/sync 接口声明通道（契约 §19.8）+bootstrap 固定图，管理面资源 CRUD 20055 enforcement（堵手工 MANUAL 行口径外通道与 FULL 清理不覆盖的孤儿行）；SERVICE 维持 MANAGED——新 SERVICE 行唯一通道=管理面手工建行（按服务实例级授权的目标行），收紧即零 writer 死局，登记理由入 registry"
  - "DDL 种子：UPDATE 声明 IN 列表加 'API'；type_definition.extra 的 COMMENT 与 UPDATE 块注释同批改写（七类事实链路句保留 + API 单列口径句）"
  - "代码口径：OwnershipGuard.validateExtraDeclaration 的 API 硬拒保留、注释与异常理据改写为「种子钉死」口径；rejectIfSyncOwned 内部来源 20055 文案补「服务接口同步（service-config/sync）」入口；类 Javadoc 与 LocalProjectionOwner Javadoc 对齐；ResourceTypeOwnershipGuardTest API 拒绝用例 DisplayName 理据更新"
  - "回归锁（旧种子下失败）：PgIT 断言 API 类型 rejectIfSyncManagedType 抛 20055 且文案含服务接口同步入口（对齐 TypeDefinitionProjectionPgIT 先例，真实 DDL 种子驱动）"
  - "不受影响佐证：service-config/sync 接口声明通道（E2E ExampleProtectedApiE2EIT 第③步）与 bootstrap（AccessBootstrapPgIT）既有用例维持绿——两通道领域直写不经管理面门禁"
  - "存量核查：dev 库 MANUAL API 行查询与处置登记（86 行=bootstrap 固定图清单全集对账、清单外野行预期零）"
  - "文档同批：契约总册 §5 保护段/§12/§13/§19.1/§19.8/§21.2 第 8 条 + §19.8 enforcement 注记；architecture 服务间交互矩阵行；example-service 接入路径行；extension-guide 声明约束句与快读表锚点；AGENTS 权限中心实现提醒；E2E 注释两处（「恒 MANAGED」句改写）"
  - "Q-008 收敛：pending-problems 条目随本卡 done 收敛入已收敛索引表"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-18
---

# T-PERM-069 API 类型内部来源收紧（Q-008 转出）

## 背景

Q-008（2026-09-13 收编登记，源自 T-PERM-052 已知边界）：USER/ORG/MENU/ROLE 等事实链路类型种子已声明 SYNC+access-service，SERVICE/API 固定图种子行维持 MANAGED——管理面手工 CRUD 允许，靠 bootstrap 启动固定图校验兜底。

2026-09-18 代码级盘点（方向决策依据）：两类型情况不同质——

- **API**：已有钦定唯一事实入口（契约 §19.8 service-config/sync 接口声明通道，E2E 第③步实证；领域直写不经管理面门禁）；管理面手工建行（maintain_source=MANUAL）是口径外通道，且 FULL diff 清理与服务删除级联均只覆盖 SERVICE_SYNC 行，MANUAL 行成永久孤儿。
- **SERVICE**：新 SERVICE 行唯一通道=管理面手工建行（bootstrap 只种 access-service 一行；service-config save/sync 均不产 SERVICE 资源行）；该行是「按服务实例级授权」（如 SERVICE:SYNC_INTERFACE 挂具体服务）的目标对象——收紧即零 writer 死局，需配套新事实链（service-config 联动建行）才可收紧，超出本题范围。

用户拍板：**仅 API 收紧**（SERVICE 维持 MANAGED）。

## 范围

- `docs/design/schema/access-service.sql`：type_definition 种子 UPDATE 的 IN 列表加 `'API'`；UPDATE 块注释与 `type_definition.extra` COMMENT 增补 API 单列口径句。
- `ResourceTypeOwnershipGuard`：`validateExtraDeclaration` API 硬拒分支理据改写（行为不变——经类型定义接口对 API 的 SYNC 声明仍拒绝，理由由「事实 writer 双写」改为「所有权由种子钉死」）；`rejectIfSyncOwned` 内部来源 20055 文案补服务接口同步入口；类 Javadoc 事实链路清单句对齐。
- `LocalProjectionOwner`：类 Javadoc resource 侧段对齐。
- `ResourceTypeOwnershipGuardTest`：API 拒绝用例 DisplayName 理据更新（断言不变）。
- `ServiceConfigCascadePgIT`：新增 API 类型 20055 回归锁（真实 DDL 种子驱动，旧种子下失败）。
- 文档同批：契约总册（§5 保护段/§12/§13/§19.1/§19.8/§21.2 第 8 条）、`architecture.md` 服务间交互矩阵行、`example-service.md` 接入路径行、`extension-guide.md` 声明约束句与快读表锚点、`AGENTS.md` 权限中心实现提醒、`e2e/ExampleProtectedApiE2EIT` 注释两处。
- 存量核查：dev 库 MANUAL API 行对账（86 行=bootstrap 固定图清单全集、1 行 SERVICE_SYNC，零野行）与 runbook 登记订正语句。
- `docs/pending-problems.md` Q-008 收敛；`decision-registry.md` 定案行。

## 当前口径

- API 类型所有权=SYNC + syncSourceService=access-service（种子声明，is_system 钉死不可变更 20056）；API 资源行写入方=bootstrap 固定图种子 + service-config/sync 接口声明通道（SERVICE_SYNC）两通道领域直写；管理面 resource-entity create/batch-create/update/move/remove 对 API 类型一律 20055（含存量 MANUAL 行——full-sync 清理不覆盖、管理面不可改删，故需存量核查）。
- SERVICE 类型维持缺省 MANAGED：管理面手工 CRUD 是新 SERVICE 行唯一通道（按服务实例级授权目标行），维持 T-PERM-052「非事实链路公共类型」归类。
- 外部 resource-entity/sync 对 API 的拒绝语义不变（来源不匹配 RESOURCE_TYPE_OWNERSHIP_DENIED）。

## 验收对照

见 frontmatter `acceptance`；全部满足后回写 design_refs 指向章节并收敛 Q-008。

## 非目标 / 遗留

- SERVICE 类型收紧与配套事实链（service-config/save 联动 upsert SERVICE 资源行）不在本卡——用户拍板维持 MANAGED；未来重启评估时以「配套事实链」为前置（registry 2026-09-18 行已钉）。
- 存量库（DDL 已执行的 dev/部署库）类型种子迁移语句：随 rebuild-runbook 既有「重建库」纪律处理（项目未发布无滚动升级面），本卡在 DDL 与 runbook 侧登记核对（dev 库已于 2026-09-18 直接应用订正 UPDATE）。

## 完成记录

- 2026-09-18 收口。方向决策：AskUserQuestion「仅 API 收紧」用户拍板（SERVICE 维持 MANAGED 零 writer 死局实证见背景）。
- 回归证据：定向 `mvn test -pl access-service -Dtest='ResourceTypeOwnershipGuardTest,ResourceManageAppServiceImplTest,TypeDefinitionAppServiceImplTest,ServiceConfigCascadePgIT,TypeDefinitionProjectionPgIT,ResourceOperationKeyPgIT,AccessServiceSchemaPostgresTest,AccessServiceSchemaH2Test'`（2026-09-18，30 单测 + 26 容器项全绿）；回归锁双向实证——临时还原旧种子（删模板库强制按旧 DDL 重建）跑 `ServiceConfigCascadePgIT#apiTypeSeedShouldRejectManagementCrudWhileServiceStaysManaged` 失败（Expecting code to raise a throwable）、恢复新种子后 26 项全绿；收口全量 `mvn test -T 1C`（含 E2E 八模块）1721 项 0 失败、BUILD SUCCESS（/tmp/tperm069-full.log，2026-09-18）。
- 双轨本地评审（代码轨+文档轨并行子代理）P0=0；P1×3/P2×5/P3×5 全处置：extension-guide §2 六步清单残留「恒 MANAGED」句改写、§6.3 死锚点簇（本批新引入 6 处+历史 5 处：extension-guide 快读表/schema service_config·sync_metadata 注释/runbook-full-sync）、architecture §4 L144 补 T-PERM-069 增补句（体例对齐三先例）、registry/runbook 登记补齐、「Q-008 收敛」超前措辞改「定案」、任务卡锚点与 acceptance 表述修正。
- 存量核查：dev 库 86 行 MANUAL API 资源全为 bootstrap 固定图清单（apiRoutes 恰 86 条对账）+1 行 SERVICE_SYNC，零口径外野行；野行检测 SQL 预期命中=目标接口 my-info（bootstrap 设计即无映射）。订正 UPDATE 与检测语句登记 rebuild-runbook（T-PERM-048 先例行形态）。
- Q-008 收敛入 pending-problems 已收敛索引；定案登记 decision-registry 2026-09-18 行。
