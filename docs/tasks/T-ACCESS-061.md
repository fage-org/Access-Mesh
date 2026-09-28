---
doc_type: task
id: T-ACCESS-061
title: （ADM-T06）逐服务业务最终检查与模式切换
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §8.6/§8.6a/§9.3
  - docs/design/access-service-api-contract.md §25.7
  - docs/ops/runbook-service-mode-switch.md
depends_on:
  - T-ACCESS-059
  - T-ACCESS-060
blocks: []
acceptance:
  - "§8.6 业务入口最终检查表逐路由落地（example-service 先行）：实际资源+对应操作（查 A 不得按 B 取数）、批量逐目标独立 DECISION 项（全拒或允许子集由业务明确，不能任一允许放行整批）、列表/搜索范围与 total 同口径、CREATE=TYPE_LEVEL（实例准入不授予类型创建权）、上下文子权限传真实父+业务引擎验证父授权绑定、异步作业明确提交与执行时点鉴权、直连/内部调用同验身份"
  - "迁移资格=最终检查的代码位置+反向拒绝测试（不是 businessChecked=true 配置）；没有逐路由证明的服务不切新模式；N01/N04/N05/N24/N25/N26/N27 实测绿"
  - "运行库盘点执行：非 API 手工映射、同路由/重叠路径多要求、缺业务操作登记、各服务独立 API 授权、同步归属、无最终业务门禁路由——非 API 存量映射显式找到登记 API 并补准入操作（不凭旧菜单类型猜 VIEW），不确认的数据不启新模式"
  - "e2e 模块新增垂直切片：网关准入 MAY_ENTER+业务最终拒绝/放行双路（网关准入与业务实例检查为两次执行、不共享跨 HTTP RunState）；服务级暂停切换 runbook（暂停→确认逐路由最终检查→切模式→全节点确认新版本+清旧缓存→恢复）演练证据"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-28
---

# T-ACCESS-061 （ADM-T06）逐服务业务最终检查与模式切换

## 背景

设计 §8.6/§9.3（报告临时编号 ADM-T06）。B 请求到达业务服务是方案 A 预期；主体/租户取自可信认证链、实际目标从业务请求解析。继承模式与产品约定显式对齐（菜单后代可见不推导默认 SELF 已开祖先）。

## 范围

- example-service 接入改造与 e2e 垂直切片；暂停切换 runbook 与演练；临时强制在线灰度的容量预算与退出条件（如使用）。

## 非目标 / 遗留

- 不停流全节点代次切换协议不在本卡（设计 §8.5「另计成本」）；AUTHORITY_ROOT 等受保护行清理由 T-ACCESS-062 受控迁移。

## 完成记录（2026-09-28 收口）

### 当前口径（三项定案）

1. **检查客户端形态=B：引入 perm-client SDK starter**——example-service 消费 `perm-client-spring-boot-starter`（Feign）调 `auth/check`/`auth/batch-check`/`auth/query-resources`；服务认证=SDK 既有内部密钥通道（`FeignInternalSyncInterceptor`，零 SDK 改动），`X-Tenant-Id` 由 example 侧 `FeignTenantHeaderInterceptor` 从调用上下文注入（SDK 约定租户头由调用方负责）；修订 AGENTS.md 旧定案「example 不消费运行时鉴权 SDK starter」（接口级鉴权仍由 Gateway 承担）。**e2e 子进程注意**：example 新增 starter 后经 e2e 测试类路径传染到 access/gateway 子进程（env 设 PERM_INTERNAL_SECRET 触发拦截器条件装配、perm.service-code 无值启动即炸）——两类 e2e 的 access/gateway 子进程参数加 `--perm.client.enabled=false`（非消费进程显式关闭）。
2. **运行库盘点载体=A：重建 dev 运行库后盘点**——dev 库（docker accessmesh-postgresql）已隔代陈旧（缺 058/059 列之外 resource_entity 类型号整体漂移，HEAD 代码不可跑），按标准重建三步（DROP SCHEMA+DDL+FLUSHALL+`ACCESS_BOOTSTRAP_ENABLED=true` 重种）拉回 HEAD 后执行六类盘点留档（见下「运行库盘点执行记录」）。
3. **N27 上限形态=A：不新增数值上限常量**——「超限显式技术失败」由既有机制承载（网关 WebClient 256KB 解码上限→503 fail-closed、5s 回源截止、构建重试 3 次上限）；heavy 用例锁大规模结构性质（不截断/不逐实例/按类别合并），不拍板魔法数。

### 验收对照

**① §8.6 检查表逐路由落地（example-service 先行）**——`ReportController` 七路由 + 既有 `/hello`：

| 路由 | 检查表行 | 最终检查形态 |
|---|---|---|
| `POST /api/example/report/view` | 查看/预览 | check(EXAMPLE, reportCode, VIEW)——检查目标=请求实际码，允许后返回同一报表（查 A 不按 B 取数） |
| `POST /api/example/report/batch-view` | 独立批量 | batch-check 逐项 DECISION；拒绝项零数据（name/content=null）只回 reason；任一允许不放行整批 |
| `POST /api/example/report/list` | 列表/搜索 | query-resources 范围集合→同口径过滤→分页；total 与 items 恒同范围 |
| `POST /api/example/report/create` | CREATE | check(EXAMPLE, **null**, CREATE)=TYPE_LEVEL；实例 CREATE 授权过网关候选但业务拒绝（E2E ⑦ 实证） |
| `POST /api/example/report/sub-view` | 上下文子权限 | check 子资源携带真实父（EXAMPLE/parentCode/[VIEW]，T-PERM-058 字段族）；无父/错父引擎拒绝 |
| `POST /api/example/report/export/submit`+`/export/status` | 异步作业 | 提交时点 check EXPORT；执行时点重查同一目标（`ExportJobRunner` 调度线程，租户/主体提交时捕获显式传入）；撤权窗口→作业终态 DENIED |
| `POST /api/example/demo/hello`（既有） | —（无资源目标） | 最终检查定位=身份头存在性（30002）+签名校验（30003，`GatewaySignatureFilter`） |

主体纪律：主体/租户恒取自已验签 `X-User-Id`/`X-Tenant-Id` 头，**请求 DTO 无主体/租户字段**（N25）；业务拒绝=信封 30004（HTTP 200，与网关准入 403 形成层次区分）；鉴权不可用=fail-closed 30005。

**② 迁移资格（代码位置+反向拒绝测试）**——逐路由反向拒绝测试 `ReportControllerTest` 20 用例（每路由允许+拒绝双态、目标逐参 verify、批量拒绝项零数据、TYPE_LEVEL null 码、错父、撤权窗口执行时点 DENIED、fail-closed 30005、身份头缺失 30002、作业归属校验、业务数据租户隔离）；`ReportControllerValidationTest` 3 用例（HTTP 层 @Valid 反例：空名/空码/超批量上限→400+90001，业务层零调用）；`BusinessPermCheckerTest` 9 用例（主体纪律/父上下文透传/batch 对齐/INSTANCE 码收集/ALL 全量投影/clientIp 透传/fail-closed 三形态/租户上下文 set-clear 生命周期）；`FeignTenantHeaderInterceptorTest` 2 用例。**N01/N04/N05/N24/N25/N26/N27 实测绿**：

- **N01/N24/N25（E2E ③④⑤⑥）**：双路——网关对 `/report/view` 恒 MAY_ENTER（report-1 VIEW 候选），业务 report-1 放行/report-2 30004（跨 HTTP 两次执行不共享 RunState）；批量混入逐项；列表仅 report-1（total=1）；直连伪造身份头 30003。
- **N24 异步半边（E2E ⑨）**：提交检查→执行 DONE；提交后撤权（4s 窗口）→执行时点重查 DENIED；撤权后候选转移（EXPORT→report-2）下网关放行、业务提交时点 30004。
- **N04/N05（E2E ⑫）**：PERM_MUTEX(VIEW×UPDATE) 规则下，UPDATE@report-2（覆盖 VIEW 的另一端、跨实例）→view report-1 不误拒；UPDATE 再授 report-1（同实例两端齐备）→业务 check 拒绝 30004（D03 语义经真实 check 端点实证；准入恒 MAY_ENTER——057「准入不做互斥判定」的运行时对偶）。**实现注记**：互斥第二端必须**覆盖**被检操作（设计原文「UPDATE 覆盖 VIEW」）——无继承的 EXPORT(16) 不构成同场两端（首轮用例红的根因，非引擎缺陷）。
- **N26**：父子同刻评估为引擎侧现行语义（a2 定案，MutexSemanticsCharacterizationPgIT 等 D 系锚）；业务半边=sub-view 传真实父字段（单测 verify+E2E 对父放行/错父拒绝）。
- **N27（`InterfaceAdmissionHeavyPgIT`，testcontainers-heavy）**：300 启用路由×2001 授权行——①routes 全量 300 条不截断；②候选按「类型-操作×条件身份×候选类别」合并恰 3 分支（1000 VIEW 实例+1000 EXPORT 实例+1 EXPORT 类型级），不逐实例展开；③在线准入按候选存在性一次判定（VIEW 候选全来自 1000 实例行→MAY_ENTER，不逐实例最终鉴权——实例级判定归业务）。

**③ 运行库盘点执行记录**（dev 库重建到 HEAD+bootstrap 重种后执行，六类查询全文见 runbook §三）：

| 盘点项 | 结果（实跑，查询全文=runbook §三） |
|---|---|
| ① 各服务映射分布/非 API 手工映射 | access-service 105 条映射全部引用 API 登记实体（api_ref=105/105，dangling=0）；example-service 接入映射由 e2e 内自建（本库无）——**非 API 手工映射零存量** |
| ② 同路由/重叠路径多要求 | 精确路由重复=0（重叠路径歧义另由快照构建期 20070 拦截，059 已锁） |
| ③ 缺业务操作登记（启用无 required_operation_id） | 0（059 后固定图全带操作引用；query-scopes 种子映射已停用） |
| ④ 各服务独立 API 授权（API 类型授权行） | **107 行**（租户 1/角色 1=admin：bootstrap 固定图按 apiRoutes 派生的 API:ACCESS 实例授权 105 路由+2 目标接口）——OPERATION_ADMISSION 下网关不消费（access-service 路由准入要求=业务类型操作，与 API:ACCESS 无关，无陈旧放行面）；**T-ACCESS-062 受控清理面（N30）**，本卡登记不动 |
| ⑤ 同步归属 | 映射 maintain_source=BOOTSTRAP 单一来源 105 条（无 SERVICE_SYNC/MANUAL 混写） |
| ⑥ 无最终业务门禁路由 | access-service 自身 105 路由=059 定案③「服务层 QueryGate 门禁同码」（双层同码即最终门禁）；example-service 七路由=本卡①的代码位置+反向测试——**双服务逐路由均有最终门禁** |

盘点执行注记：六查询初版①④误以 type_definition.id 作 join 键（实体/授权行 resource_type 存 **type_value**），首跑在重建库上暴露（④ 假命中 SYSTEM_CONFIG 两行）——已修正为 type_value 语义并重跑（runbook §三同批修正）；真实发现=④ 的 107 行 legacy API 授权（上表处置）。重建前旧库为隔代种子（类型号整体漂移），按定案②直接重建不作原地迁移。

**④ e2e 垂直切片**——`ExampleBusinessFinalCheckE2EIT`（11 用例全绿，一次起栈）：①~② 登记+授权 → ③~⑩ 业务半边全景（N01/N24/N25/CREATE/子权限/异步/N04/N05）→ ⑪ **暂停切换 runbook 演练**（`status=0` 暂停→代次+1→请求 403〔空快照 DENY〕→`apiAuthMode=LEGACY_API` 切模式→代次再+1→仍 403→`status=1+OPERATION_ADMISSION` 恢复→代次再+1→30 秒内回 200；`g0<g1<g2<g3` 单调断言、恢复以库为准不等 TTL）。runbook 文档=`docs/ops/runbook-service-mode-switch.md`（迁移资格核对+五步切换+六查询盘点+参考接入形态）。

**「临时强制在线」灰度**：T-ACCESS-060 拍板不落地，范围行「如使用」条件不成立——无容量预算项。

### 验证证据

- example-service 单测 47 用例全绿（ReportController 20+ReportControllerValidation 3+BusinessPermChecker 9+FeignTenantHeaderInterceptor 2+既有 13）。
- `InterfaceAdmissionHeavyPgIT` 2/2（testcontainers-heavy）。
- `ExampleBusinessFinalCheckE2EIT` 11/11；`ExampleProtectedApiE2EIT` 回归通过（`--perm.client.enabled=false` 修订后）。
- 全量收口回归 `mvn test -T 1C`（E2E+heavy 必跑）：见收口提交记录。

### 文档回写

契约 §25.7 落地记录段；设计 §8.6a 落地记录；`docs/ops/runbook-service-mode-switch.md`（新）；`example-service.md`（§8.6 路由族+SDK 边界改写+演示场景表）；AGENTS.md（SDK 定案修订+e2e 子进程注意）；docker-compose（example 补 PERM_INTERNAL_SECRET）；计划进度区+README 任务行。

### 外评修正（2026-09-28 收口后，评审基线 27b961e88→f7768f1d6）

- **P1×2（数据隔离）**：`export/status` 补作业归属校验（租户+用户与提交时不符=与不存在同口径拒绝，防按递增 jobId 枚举他人导出内容；e2e ⑨ 提交者=查询者同人不受影响）；`ReportStore` 按租户分区存取（种子每租户惰性一份——资源实体按租户登记、业务数据同口径，同码跨租户互不可见）。
- **P2×6**：全量授权（`scopeMode=ALL`，条目 resourceCode=null）由 `BusinessPermChecker.Scope.all` 表达（旧实现按码收集恒空集）；准入快照构建补终校验（独立 `selectAuthState` 复读启停+模式——代次比对只覆盖首次代次读之后的变更，入口校验与首次代次读之间切模式/停用须在返回前拦下，归 060 面的完备修正）；前端新建服务默认 `apiAuthMode` 改 `OPERATION_ADMISSION`（与后端创建缺省一致，`ServiceForm` 编辑 fallback 同批）；可信 clientIp 经网关重建 `X-Forwarded-For` 传入 checker 三个调用面（SDK 契约键 `clientIp`，与网关 `PermissionClient` 同款；缺失不传=IP 条件归引擎 fail-closed），异步作业提交时捕获、执行时点重放（与租户/主体同构）；报表入口 `@Valid` 补齐（HTTP 层反例=`ReportControllerValidationTest`：空名/空码/超批量上限→400+90001、业务层零调用）；撤权窗口单测改 mock answer 按调用序号定序（提交时点 check 恒先于执行时点——submit 内同步完成，无 10ms 延迟余量竞态；E2E 4s 窗口属跨进程固有形态——撤权经真实管理面+失效广播，本机回环余量充足，维持）。
- **文档轨**：契约 §25 章定位「端点未实现」与 §25.1「bootstrap 登记 LEGACY_API」两处陈旧叙述修正（bootstrap 现行登记 OPERATION_ADMISSION）；最坏陈旧算式补构建耗时项（事实读取→generatedAt 间事实年龄继续增长：10s+5s+15s=30s 压线达标 0 余量；契约 §25.4/双侧 catalog Javadoc/SNAPSHOT_TTL 常量注释/060 卡/计划/README 同批）；runbook 盘点 SQL ①②补租户/服务分组维度、④类型联接补租户条件（跨租户 join 多行放大计数）；`example-service.md` 接入路径段改 sync-v2+EXAMPLE:VIEW 现行口径（旧 /sync+API:ACCESS 不再用于新链接入）；059/060/061 卡过程性拍板小节与外评处置章节按文档治理改写为当前口径（r2 §8.6a「（用户拍板）」注记同批去除）。
- 验证：example-service 47/47（含归属/隔离/ALL/clientIp/@Valid 新回归锁）；access-service `PermissionAdmissionAppServiceImplTest` 5/5（终校验三态+重试+失败关闭）、`InterfaceAdmissionPgIT` 19/19（真库新语句+终校验路径）。
