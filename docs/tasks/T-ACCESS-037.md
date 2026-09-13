---
doc_type: task
id: T-ACCESS-037
title: system_config 单入口化（admin /config 退役）
status: done
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§5.3
  - docs/design/access-service-api-contract.md#§17.3（/config 未成册登记位——原 admin 册无 /config 契约段，T-ACCESS-040 实核 0 命中；退役时同步收口该登记行）
  - docs/design/access-service-api-contract.md#§17.2（system-config；T-ACCESS-040 重挂总册）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-040
blocks: []
acceptance:
  - "admin /config 入口退役：ConfigController + ConfigService(Impl) + DTO + 测试删除；契约段（新册）移除"
  - "system_config 管理单入口 /api/perm/system-config（前端唯一消费方，T-PERM-024 收口形态维持）"
  - "门禁口径核对：SYSTEM_CONFIG 操作码消费面收敛到 perm 入口口径，无悬空门禁"
  - "负向验收：/config/* 端点不存在；全量回归绿"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

system_config 表双管理入口：admin /config（ConfigServiceImpl，UPDATE/DELETE 门禁）与 perm /api/perm/system-config（MANAGE 门禁）。消费面核实（2026-09-13）：前端只调 perm 入口，admin 入口在 frontend/e2e/gateway 主代码零消费——僵尸端点，双门禁语义与双契约描述。

## 范围

退役 admin 入口全链 + 契约回写 + 门禁消费面核对。

## 当前口径

- admin ConfigServiceImpl 的 DELETE 门禁（SYSTEM_CONFIG:DELETE）消费随端点消亡；SYSTEM_CONFIG:DELETE 为 CRUD 预置种子（CROSS JOIN 全类型），**不删操作码本身**——034 的 USER:MANAGE 清理不涉此类预置码。
- depends_on 含 040：契约回写落新册，不写旧册。

## 完成记录（2026-09-13）

### 主链删除（5 文件）

- `platform/controller/ConfigController.java`（/config：page/detail/update/delete 四端点）
- `platform/service/ConfigAppService.java` + `impl/ConfigAppServiceImpl.java`
- `platform/dto/req/ConfigUpdateReq.java`、`platform/dto/resp/ConfigResp.java`

### 孤儿面清扫（「无消费即删」政策，T-ADMIN-024 先例）

- **SystemConfigMapper admin 侧五方法**：`softDeleteBatch` / `selectPageByTenantId` / `countByTenantId` / `selectListByIdsAndTenantId` / `selectOneByIdAndTenantId`（主代码唯一消费方为 ConfigAppServiceImpl；XML 五语句 + 「T-ACCESS-002 归并」注记块同步删除；perm 侧 `selectByConfigKey`/`selectByTenantId`/`countByCondition`/`selectPageByCondition` + BaseMapper 原生方法维持）。
- **AdminErrorCode 两码退役**：`CONFIG_NOT_FOUND`(10701)、`CONFIG_SYSTEM_IMMUTABLE`(10702)（零消费即删；`ErrorCodeContractTest` RETIRED_ADMIN_NAMES 登记防码值复用，与 MENU_PERM_CODE_EXISTS 同机制）。

### 测试适配（4 文件）

- `HttpApiPathSnapshotTest`：EXPECTED_PATHS 与签名快照各删 4 条 `/config/*`；`RETIRED_PATHS` 增补四条（capability-structure §8.4「037 执行时增补」指令兑现）；javadoc 退役史追加 T-ACCESS-037 行。**即负向验收锁**——双快照双向比对 + `retiredPaths_haveNoControllerMappings` 零映射断言，控制器复活即红（T-ADMIN-024/T-PERM-043 同款双件套先例）。
- `ErrorCodeContractTest`：RETIRED_ADMIN_NAMES += 两码（带 T-ACCESS-037 注记）。
- `AdminPageRespShapeTest`：删 `ConfigManualCountAssembly` 嵌套类（3 用例）；PageResp hasNext 手工 count/offset 装配语义由 OrgTreeConfig 嵌套类继续锁定（非末页/整除末页/零集短路三边界均在），javadoc 注明唯一载体。
- `AdminXmlPaginationPgIT`（容器轨）：删 system-config 切片用例与 config service 元数据边界用例（2 用例）+ configService/systemConfigMapper 注入 + 死 import（PageReq/PageResp/TenantContextHolder + clearTenant 仪式）；javadoc ①计数 8→7、元数据边界语义指向 AdminPageRespShapeTest unit 轨。
- `AppServiceOperationLogCoverageTest` 零改动（033 外评已预除全局总量魔数，注释明示 037 删 ConfigAppServiceImpl 合法减员）。

### 门禁口径核对（验收 3）

删除后主代码 `ResourceTypeCode.SYSTEM_CONFIG` 消费面 = SystemConfigAppServiceImpl（VIEW/MANAGE）、DomainConfigAppServiceImpl（VIEW/MANAGE）、BizDomainAppServiceImpl（MANAGE×3）、UserMenuQueryAppServiceImpl（菜单可见性）、bootstrap 固定图（VIEW/MANAGE 授予 + 系统配置菜单种子）——全部 perm 侧口径；`SYSTEM_CONFIG:UPDATE/DELETE` 代码引用零残留（grep 实证），无悬空门禁。操作码本身不删（当前口径：CRUD 预置种子；bootstrap 固定图本无 UPDATE/DELETE 位）。

### 消费面复核（复验任务卡 2026-09-13 核实）

`/config` 路径字面量在 frontend/src、e2e、gateway、example-service、access-service resources 零命中（plain grep 复验）；`ConfigController|ConfigAppService|ConfigUpdateReq|ConfigResp` 全仓主代码零残留。

### 契约回写（总册，不写旧册）

- §17.3：族清单 5→4 组（/config 出列，dict/notice/job/login-log 余四组），新增退役注记块（删除面/单入口收敛/负向锁/操作码处置四要点）。
- 覆盖面说明（行首）：六组→五组 + 退役指引（行首含 /auth 登录族故计数差一）。
- §17.2：新增「单入口口径」注记（T-ACCESS-037，无迁移端点、无兼容层）。

### 验证链

- 定向三契约测试 17/17 绿（HttpApiPathSnapshot 7 + ErrorCodeContract 7 + AdminPageRespShape 3）。
- 单测轨道 `-DskipTestcontainers=true`：1228 用例 0 失败 0 错误。
- 全量收口 `mvn test -T 1C`（含 E2E）：BUILD SUCCESS（06:36）——perm-common 30 / common 65 / starter 15 / example 10 / gateway 106 / access-service 单测 1228 / 容器组 210 / e2e 14，全轨道 0 失败 0 错误；容器组 212→210 与删除的 AdminXmlPaginationPgIT 两用例精确对应。
- 双轨评审处置后复跑：HttpApiPathSnapshotTest（含新增 RETIRED_PATHS 四条）+ ErrorCodeContractTest 定向全绿（见评审处置节）。

### 外部评审处置（2026-09-13，claude + grok 双通道，均 read-only + 禁子代理）

评审范围 `2c25c2403..ad896c1dc`（9c8d1a2f9 本任务主体 + ad896c1dc 036 遗留注释修正）。claude（默认模型，stdout 全文报告）P0-P2 零 + P3×1；grok（grok-4.6 xhigh）P0-P2 零 + P3×1；两通道零矛盾，八项专项清单全过、复核 ①-⑥ 本地处置均确认修对、`ad896c1dc` 注释表述与两测试载荷/断言吻合（含 GlobalExceptionHandler 双通道同码 :91-94/:143-147 引证）。处置：

- **claude P3（采纳，已修）**：任务卡与计划进度行「§17.3 族清单 6→5」计数归属错误——§17.3 自身清单 5→4（dict/notice/job/login-log），6→5 是行首覆面说明（含 /auth 登录族）的变更，同卡两行对同一事实两种归属。两处已改为「§17.3 5→4 / 覆面说明 6→5」并注明计数差一原因。
- **grok P3（采纳，已修）**：pending-problems.md frontmatter `counter` 未随 Q-010 登记上调（仍 Q-009，下一号将与 Q-010 撞号破坏 ID 冻结协议）——已改 Q-010。
- **存量观察（两通道，登记不处置）**：①`SystemConfigAppServiceImpl.upsertSystemConfig` update 分支无 `is_system` 守卫——「系统内置配置不可改」在全仓已无运行时强制（admin 侧 CONFIG_SYSTEM_IMMUTABLE 随僵尸端点消亡、perm 侧从未有守卫；可达状态集本任务前后不变，非放大）——待用户拍板处置方向；②AdminPageRespShapeTest 零集短路「不查列表」性质未被严格锁定（Mockito 默认空列表可通过，T-ADMIN-027 既有形态）；③同文件 javadoc 边界用例枚举与三用例形态不完全对应（既有表述）；④AdminErrorCode:9 类头摘要散文仍列「配置」类（107xx 段已空并加退役注记，轻微不称）；⑤T-PERM-053 完成记录保留「归因钉死」历史字样（时点快照，修正谱系以 036 卡闭环句为准）；⑥ErrorCodeContractTest 退役名换新码值复活不拦（既有机制边界，卡内已登记）。

### 存量观察（不在本任务范围）

- `SystemConfigMapper.selectByTenantId` 为既有零消费方法（perm 侧列表走 `selectPageByCondition`；本次盘点实核主+测试零调用点，双轨评审独立复核属实）——先于本任务存在的死代码，非 /config 链产物，未随本任务删除（避免范围蔓延）；已登记 `docs/pending-problems.md` **Q-010**（随下次触达该 mapper 的任务顺带删除，不单独立任务）。

### 评审处置（本地双轨，2026-09-13）

代码轨 P0-P2 零 + P3×3，文档轨 P2×1 + P3×3，逐条亲核后处置：

- **P2/P3 收敛主项（两轨同发现，文档轨升 P2 定性）**：`HttpApiPathSnapshotTest.RETIRED_PATHS` 未增补 `/config` 四条——capability-structure §8.4:482 对 037 有明示指令（「RETIRED_PATHS 清单不变；037 执行时增补 `/config/*` 四条」），初版仅删双快照未走 T-ADMIN-024/T-PERM-043 先例的双件套形态。**已修**：RETIRED_PATHS 补四条（带 T-ACCESS-037 注释）+ `retiredPaths_haveNoControllerMappings` @DisplayName 族枚举同步 + 任务卡/契约总册 §17.3/计划进度行三处「负向锁」叙述按双件套实际形态订正。
- **P3（代码轨/文档轨同发现）**：契约总册 §2.5 行 139 以已退役 `/config/delete` 作 admin 批删不设限的现行活例——**已修**：改例 `/dict/type/delete`（政策句与 registry 2026-09-12 T-PERM-065 拍板口径不动，纯举例漂移）。
- **P3（代码轨）**：AdminErrorCode 类头 javadoc 107xx 分段行指向已空段、未按 10111 先例加退役注记——**已修**：按先例补「10701/10702 已退役（原 admin /config 端点错误，入口整链删除后码值不复用，退役登记见 ErrorCodeContractTest）」。
- **P3（文档轨）**：任务卡门禁核对节「BizDomainAppServiceImpl（MANAGE×4）」计数失实——**已修**：改 ×3（:89/:226/:261 实数）。
- **P3（文档轨，评估不改）**：capability-structure §8.2 dto/service 行无 controller 行同款退役注记——史册按 033 时点事实记录，controller 行注记已提供退役指引锚点，§5.3+§8.4+总册 §17.3 三处登记闭环，不改。
- **存量观察（代码轨）**：RETIRED_ADMIN_NAMES 分支不拦截「退役枚举名以新码值复活」（如重新引入 CONFIG_NOT_FOUND(10999) 静默通过）——MENU_PERM_CODE_EXISTS 退役时即存在的机制边界，需复活者显式改退役清单才可造出，维持现状。
- **存疑处置**：文档轨建议 selectByTenantId 死方法登记 pending-problems——按 skill「任务卡非目标/遗留延期项」登记规则落地 Q-010（登记动作遵循既有治理机制，非设计取舍）。验证链模块级计数粒度（超出 036 先例）经评估保留本卡（有先例垫底），后续任务卡收敛到 036 粒度。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不改 system_config 表结构与三命名空间 fail-closed 语义（T-ACCESS-007 形态维持）。
