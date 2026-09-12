---
doc_type: task
id: T-ADMIN-025
title: 文件夹级授权（bizType 即文件夹实例，全链路 CREATE/VIEW/DELETE）
status: done
plan: docs/archive/2026-08-27/product-vertical-slice-plan.md
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
depends_on: [T-ADMIN-023]
blocks: []
acceptance:
  - "文件夹实例产生：bootstrap 预置 default/avatar/document/image 四文件夹 ADMIN_FILE resource_entity 投影（空库即可授权）+ 上传新 bizType 时惰性登记投影（首次出现即成为可授权实例），无需管理界面"
  - "ADMIN_FILE 加入资源创建保留清单（2026-09-05 定案）：在既有 {USER,ORG,MENU,ROLE} 基础上新增（若 T-PERM-051 已先行落地则含 TYPE_DEFINITION）——文件夹实例仅允许 bootstrap 预置 + 上传惰性登记两条事实链产出，人工不得经 resource-entity 管理入口手工构造（手工 MANUAL 行与惰性登记 upsert 冲突，资源同步双向所有权 T-PERM-052 口径同向）"
  - "门禁全链路升级为文件夹实例级：upload=CREATE（目标文件夹；bizType 无投影的新文件夹首传按类型级 CREATE/scopeAll 判定后惰性登记）、detail/download=VIEW（文件所属文件夹）、delete=DELETE（按文件夹批量，resourceCode 从文件 ID 迁移为 bizType）、page 按可见文件夹过滤（scopeAll 命中全量返回，否则批量解析可见文件夹集合 + SQL bucket_name IN 过滤，无可见文件夹返回空页）"
  - "契约回写：admin-service-api-contract §4.7 门禁档位从类型级过渡升级为文件夹实例级（§4.7 已预留 VIEW 档位说明衔接）；架构文档 §15 演进方向更新；schema 注释同步"
  - "单测 + PG 用例：文件夹级授权放行/拒绝（含 fail-closed：无投影 bizType 拒绝）、page 过滤正确性、上传惰性登记、bootstrap 预置四文件夹"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-06
---

# T-ADMIN-025 文件夹级授权

> `plan` 字段（product-vertical-slice-plan.md）仅为来源溯源：本任务**不在该计划 `tasks:` 闭包内**
> （不阻塞 T-ACCESS-026 与该计划按 18 项收口），推进与收口按任务卡生命周期独立进行，必要时另立计划承载。
> 2026-09-05 设计体检复核确认既有设计口径，并定案 ADMIN_FILE 保留清单补强（acceptance 第 2 条）。

## 背景

T-ADMIN-023 执行中用户提出文件夹粒度授权设想（"用户甲只能上传到 A 和 B 文件夹"），经决策**另立本任务**（安全加固任务不膨胀交付面）。现状（T-ADMIN-023 终态）：文件全接口已按 `ADMIN_FILE` 门禁，但 detail/page/download 为类型级 VIEW 过渡、delete 实例级 resourceCode=文件 ID——ADMIN_FILE 无投影机制，实例级授权条目实际配不出来，放行全由 scopeAll 决定。

## 设计口径

- 粒度贯穿：**全链路 CREATE/VIEW/DELETE**（上传、查看、下载、page 过滤、删除均按文件夹隔离）。
- 文件夹实例源：**预置 + 惰性登记**——bootstrap 预置 default/avatar/document/image 四文件夹投影，上传新 bizType 惰性登记，无管理界面。
- bizType 格式白名单（`^[A-Za-z0-9_-]{1,32}$`）已在 T-ADMIN-023 落地，是文件夹 code 合法性的既有保证。

## 范围

- 上传惰性登记投影（upsert `resource_entity(ADMIN_FILE, code=bizType)`，事务内）+ bootstrap 预置四文件夹。
- 门禁全链路迁移与 page 过滤管线（见 acceptance 第 3 条；批量 `getDeniedResourceCodes` + mapper 按 bucket_name 集合过滤）。
- delete 的 resourceCode 语义迁移（文件 ID → bizType 集合，去重）。
- 契约/架构/schema 回写。

## 当前口径

- 文件夹 = `sys_file.bucket_name` = `resource_entity(ADMIN_FILE).code`，单事实源为投影表；不建文件夹管理界面。
- 历史存量文件的 bucketName 若不满足格式白名单（白名单前落库的脏数据）：无投影 → 实例级校验 fail-closed 拒绝，不自动修复。
- page 的 bizType 请求参数指向无权文件夹时静默返回空（过滤语义，非 403）。
- 新文件夹需先有一次上传才产生实例、才可在授权页配置实例级授权（保留清单补强的代价）；**后续优化点登记（2026-09-05 登记，暂不设计）**：预登记机制方向——为尚未出现文件的文件夹预先创建实例以便提前授权，启动时另行设计。

## 非目标 / 遗留

- 不做文件夹管理界面（改名/禁用/删除文件夹）。
- 不做单文件粒度授权（文件夹即最小粒度）。
- 不解决多实例共享存储（见架构 §15，独立演进方向）。
- 符号链接逃逸（T-ADMIN-023 评审登记）：securePath 为词法 normalize、不解析 symlink，存储根内被植入指向外部的 symlink 仍可逃逸——单实例可信本地盘档位下接受；若本任务或后续放开存储根写入面，须显式评估（如 `toRealPath` 校验）。

## 完成记录（2026-09-06）

### 实现面

- **投影登记**：`LocalProjectionDomainService.ensureAdminFileFolder(tenantId, folderCode, name)`（insert-if-absent：有效行已存在 no-op 返回 id——名称以首建为准，惰性登记传 code 不回写覆盖预置标签；软删墓碑不复活，部分唯一索引允许重插新行）。两条产出链：bootstrap 预置（`BootstrapGraphDefinition.adminFileFolderSeeds()` 四文件夹，`AccessBootstrapInitializer.initialize` 三状态检测**之前**幂等执行——no-op 路径同样补种旧库、冲突路径随事务回滚；**不参与固定图检测**：文件夹无管理面写入口，缺失只可能是库先于本特性存在，无运营意图可保护，对齐 MENU/ORG 投影豁免口径）；上传惰性登记（`FileServiceImpl.uploadFile` 门禁放行后、物理落盘与元数据插入之前，同事务——评审批次补强：登记失败（含并发首传冲突）时物理文件尚未写入，不留孤儿）。
- **门禁迁移**（全部走引擎 forValidate 的 scopeAll 短路，无需双跳判定）：upload=CREATE 目标文件夹实例级（归一化 bizType；无投影新文件夹首传仅类型级/scopeAll 授权者放行，实例授权者 fail-closed 403 且不登记不留痕）；detail/download=VIEW 文件所属文件夹实例级（**先取元数据后判定**——文件不存在先报 10501，门禁键=bucket_name）；delete=DELETE 按有效文件 bucket 去重集合批量判定（resourceCode 从文件 ID 迁移）；page=过滤语义非门禁（可见文件夹全集=有效文件覆盖的 distinct bucket_name，bizType 参数先收窄；引擎批量判定后始终按可见集 bucket_name IN 过滤——评审批次补强：拒绝集为空（scopeAll/实例授权覆盖全集）时可见集=全集仍走 IN，NULL bucket 历史行在任何授权形态下不可见，与 detail/download 的 fail-closed 口径统一；无可见返回空页——无权 bizType 静默空页）。
- **门面扩展**：`AdminPermissionValidator.getDeniedResourceCodes(type, codes, op)`（非抛出批量判定，供过滤类调用方；主体缺失 SecurityException 与抛出型同语义；引擎语义与 checkBatchInstanceLevel 完全一致）；`checkBatchInstanceLevel` 重构为委托该新方法（消重复）。
- **跨域架构合规**（AccessServiceArchitectureTest：admin 禁依赖 permission）：admin 域消费投影登记能力经端口反转——`admin.security.AdminFileFolderRegistrar` 接口 + `application.security.AdminFileFolderRegistrarImpl` 实现（委托 LocalProjectionDomainService），与 AdminPermissionValidator 同款形态（AccessServiceArchitectureTest 5/5 锁定）。
- **mapper**：`selectFilesByCondition/countFilesByCondition` 增 `buckets` 参数（null=不过滤）；新增 `selectDistinctBucketNames(tenantId, bizType)`。
- **保留清单落点（acceptance 第 2 条，T-PERM-052 机制）**：schema 种子 UPDATE 的 IN 列表增 `ADMIN_FILE`（SYNC+access-service，is_system 预置类型合法声明）——外部 sync 入口拒绝（来源不匹配）、管理面 create/batch-create/update/move/remove 一律 20055；T-PERM-051 未落地故 TYPE_DEFINITION 不含。主树注释口径同步（LocalProjectionOwner/ResourceTypeOwnershipGuard/ResourceManageAppServiceImpl/TypeDefinitionAppServiceImpl 四处「四类」→事实链路类型含 ADMIN_FILE）。
- **缓存面核查**：PermCacheCatalog 八条目（EFFECTIVE_ROLES/ROLE_PERM_SNAPSHOT/TYPE_VALUE/TYPE_CODE/CONDITION_RULES/ROLE_MUTEX_RULE/OPERATION_PERMISSIONS_BY_TYPE/ORG_VISIBILITY）均不缓存资源行列表；code→entity 解析（batchResolveResourceIds）无缓存——惰性登记即时可见，无需失效登记。

### 测试（RED→GREEN）

- RED 实证：`FileServiceSecurityPgIT.viewEndpointsDeniedWithoutGrant` 的 page 期望改为 200 空页，旧实现下失败（`expected: 200 but was: 403`）后开始实现。
- `FileServiceImplTest` 重写扩展至 24 用例：文件夹实例级 VIEW 门禁（detail/download，bucket 为门禁键）、page 过滤四态（空全集不调引擎/scopeAll 不加 bucket 过滤/部分可见 bucket IN/全不可见空页）+ bizType 收窄、upload 惰性登记（放行 ensure 调用、拒绝不登记不落元数据）、delete 门禁 bucket 去重（同夹多文件单键）与拒绝不软删；路径安全/删除顺序用例全数保留。
- `FileServiceSecurityPgIT` 扩至 11 用例（新增第 ④ 段 6 例，真实 PG+Redis+登录态）：文件夹 VIEW 隔离（page 只见本夹、detail 本夹放行他夹 403、脏桶不外泄）、page bizType 无权静默空页、upload 实例 CREATE 放行 + 无投影新文件夹 fail-closed 403 不留痕（无投影行/无元数据行断言）、scopeAll 惰性登记首传建号且重复不重号、delete 本夹放行/他夹 403 不软删/脏桶拒绝。
- `AccessBootstrapPgIT` 增断言：状态① 四文件夹投影 4 行（owner=access-service）+ 状态② no-op 幂等仍 4 行。
- `AdminXmlPaginationPgIT` file 段：buckets IN 过滤 + distinct 桶全集断言（现有 tenant 复用，调用点适配新签名）。
- 回归锁定面：`ResourceTypeOwnershipGuardTest` 18/0、`LocalProjectionDomainServiceImplTest` 26/0、`AccessBootstrapRunnerTest` 3/0、`AccessServiceArchitectureTest` 5/5（架构边界含新端口形态）。

### 设计取舍记录

- bootstrap 预置走幂等 ensure 而非固定图三状态成员：文件夹无管理面写入口（类型 SYNC 后资源 CRUD 20055），缺失只可能是库先于特性存在，无运营意图可保护——对齐固定图对 MENU/ORG 投影「不校验不修复、upsert 语义自然补齐」的既有豁免口径；固定图成员方案会使存量库重启即 fail-fast。
- page 文件夹全集取自 sys_file distinct bucket_name（而非投影表）：过滤对象是文件行，空文件夹对结果无贡献；且 admin 域不读 permission 域 Mapper（QueryBoundaryArchitectureTest）。
- detail/download 先取行后判定（10501 先于 403 暴露文件存在性）：门禁键=bucket_name 只能从元数据取，模型强制；无投影脏桶对受限用户仍 fail-closed。
- ensure 已存在行不回写名称：惰性登记传 code、预置传标签，回写会让先建者被后到者覆盖。

### 双轨评审处置（2026-09-06）

代码轨 14 项实证通过（实参序/IN 空集闭合/NULL bucket delete 侧防护/门禁序/引擎语义/端口合规/bootstrap 交互/编译）；2 项缺陷已修：
- **P2 并发窗口**：ensureAdminFileFolder 的 select-then-insert 并发首传同一新 bizType 时后提交方撞 `uk_resource_entity`，DuplicateKeyException 裸传播（500 + 物理文件孤儿）。处置：登记前移至物理落盘之前（失败不留孤儿）；端口层 catch DKE 转译 `10502 文件夹登记并发冲突，请重试上传`（「DB 唯一索引兜底转同码」先例 + PG 同事务 aborted 态不可吞掉重查；错误码按「application 跨域错误按入口领域选码」规则落 admin 段；AdminFileFolderRegistrarImplTest 锁定；登记先于落盘经 InOrder 断言锁定）。
- **P3 NULL bucket 自洽性**：拒绝集为空时 page 不加 bucket 过滤，NULL bucket 历史行可见但 detail/download 403（page 可见/detail 拒绝缝隙，违背契约「脏桶落入不可见侧」句）。处置：page 始终按可见集 IN 过滤（可见集=全集时同），NULL 行任何授权形态不可见（单测 pageFilesFiltersByUniverseWhenScopeAll 锁定）。
文档轨 14 项实证通过；「四类型→五类型」口径清扫补全 9 处（主树注释 LocalProjectionGuard/ResourceManageAppServiceImpl 批量门禁行/ResourceEntitySyncAppServiceImpl、admin contract §4.1 两处+§6 验收第 8 条、core-flows、default-org-tree-user-lifecycle、AGENTS.md 所有权段）；任务卡过程措辞三处修订（取舍节标题、端口反转过程流水、回归 /tmp 指针改内联）；看板活跃行收窄（020 仅存）。存疑项处置：registry 不补登 2026-09-05 ADMIN_FILE 保留清单定案（任务级、正文载体完整、协议禁跨轮补记——维持不补）；contract §4.1 历史注记链内「取代后口径句」按当前语义改五类型（与 114/955 同批）。

### 回归

- 定向：FileServiceImplTest 24/0；FileServiceSecurityPgIT 11/11；AccessBootstrapPgIT 17/0；AdminXmlPaginationPgIT 10/0；AccessServiceArchitectureTest 5/5。
- 全量（2026-09-06 `mvn test` 全仓，日志整文件落盘后聚合解析）：修后终跑 access-service 单测组 1077/0、容器组 167/0 全绿（评审修复批次后复跑，此前一轮的 `AuthorizationChangeInvalidationPgIT` 负载时序失败隔离复跑 3/3 绿、未复现）；example-service SUCCESS、perm-client-starter 14/0 SUCCESS；gateway 93/1——`GatewayInvalidationRaceTest`（竞态时序用例）整模块运行失败、单类复跑 3/3 绿，且本任务改动面经 git 枚举 100% 位于 access-service 与 docs，gateway/common/perm-sdk 字节码与 HEAD 一致，定性为环境负载抖动。首轮全量曾因「跑动期间并发修改源码」产生一批假失败（隔离复跑全绿），已按既有教训以干净串行重跑定案。

### 文档回写

- `admin-service-api-contract.md` §4.7：门禁档位全表升级（upload 文件夹实例级+惰性登记、detail/download 文件夹实例级、page 过滤语义、delete bucket 去重），原「类型级 VIEW 过渡」注记废止并登记落地口径；frontmatter last_reviewed 前插注记。
- `access-service-architecture.md`：§15 演进方向改为「已由 T-ADMIN-025 落地」+ 机制摘要；§4.3 内部来源统一条目增 ADMIN_FILE（五类型口径）。
- `permission-center/api-contract.md`：§5.1/§6.2.2 事实链路「四类型」→「五类型」+ ADMIN_FILE 两条产出链说明；frontmatter 注记。
- `schema/access-service.sql`：种子 UPDATE IN 列表增 ADMIN_FILE + 注释（含文件管理事实链产出方式）；type_definition.extra 注释五类型口径；新增 sys_file.bucket_name 列注释（文件夹实例判定键）。
