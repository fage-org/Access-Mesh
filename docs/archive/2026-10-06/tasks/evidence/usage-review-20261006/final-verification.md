# 使用者视角修复计划验收证据

日期：2026-10-06。分支 feat-permission-center；起点 3da35ce271fcdc816735b9f3e633ee162d3d1f8c。24 项任务在同一工作树实施，未使用子代理，未推送或部署。

## 整体验证

最终完整回归通过，24 项任务验收完成。首次全量在单测阶段发现 5 项旧夹具/契约断言未同步：SDK 模板漏 POST、OAuth2 基线缺 grantTypes；未进入容器/E2E，未算通过。定性并修正后，隔离 37 项通过，再运行完整回归。

- `mvn clean install -DskipTests`：全部 10 个 reactor 模块成功，1 分 41 秒，刷新上游 SNAPSHOT 并清除已退役类文件。
- `mvn test -T 1C`：2658 项，0 失败/错误/跳过，18:34 min (Wall Clock)；不带 skipE2E/skipHeavyIT。启动前 9100 无监听，运行期间冻结源码。
- `pnpm -C frontend lint:check`、`typecheck`：均通过。
- `pnpm -C frontend test:run`：44 文件、508 项全部通过。
- `pnpm -C frontend build`：成功，45.87 秒。
- `pnpm -C frontend contracts:check`：35 组字段集合一致。
- 本机验证，不声称已触发远程 GitHub CI；本批 UI 验证为前端测试、类型/构建及真实 Gateway API 链，未声称浏览器人工全流程验收。

完整原始输出位于忽略目录 `.tmp/usage-review/`（final-clean-install.log、final-full.log、final-full-retry.log、final-full-pass.log、final-isolated-*.log、frontend-*.log、final-dto-check.log）。本文件保留可复现命令、结果与边界，原始日志不进入源码库。

## 任务与最小行为证据

| 任务 | 实现与验证落点 |
|---|---|
| T-ACCESS-082 | 密码重置目标会话吊销；OAuth2CredentialGenerationTest/PlatformSessionAbsoluteTimeoutTest/RequestContextInterceptorTest：改密前授权码、刷新、JWT 拒绝，固定链到期，毫秒 EFF 不越界 |
| T-ACCESS-083 | AuthLoginLockTest：未知账号与错误密码同码，验证码计入锁定；重置强度前后端用例；SMS 注册及路径快照清除 |
| T-ACCESS-084 | OAuth2 公开客户端/S256/一次性码/机密客户端回归、Schema PG 约束、flow.spec.ts 同意/拒绝与本地登录续接；根路径只匹配根，非根段前缀按用户决定保留 |
| T-ACCESS-085 | OperationLogAspectTest、AuditReadMaskingTest、ServiceCredentialPgIT、PermissionFilterTest：失败码/回滚后留痕、只掩码明确敏感值、内部审计身份与 requestId 查询、发送失败仍拒绝 |
| T-ACCESS-086 | 隔离 PG 备份恢复/密码重置/墓碑恢复及密码认证演练；Compose 参数与健康依赖、JSON stdout |
| T-ACCESS-087 | 成员用户名/手机号过滤 hook 17 项，PERSONAL 口径与创建指引，死 DTO/import 删除，业务域页用途提示 |
| T-ACCESS-088 | Gateway 403 / example 200+30004 / sync accepted=false 对照现有 E2E/PG 行为；契约与接入指南失败表 |
| T-ACCESS-089 | tools/build.ps1 example-service 实跑 install -am 成功（24.492 秒）；CI 耗时及退出码、分层守护与大类评估见 engineering-baseline.md |
| T-ACCESS-090 | MybatisFlexTenantConfigTest、DualTenantSameCodeIsolationPgIT：生成 SQL 查改隔离、缺上下文拒绝、插入填充；启动前盘点 44 文件见 flex-tenant-inventory.md |
| T-PERM-106 | PermissionGrantPlanDomainServiceImplTest/AccessBootstrapPgIT：种子改删/子权限拒绝、普通转授可维护、旧未标记图拒启；前端 grant-plan 只读 |
| T-PERM-107 | QueryStagesTest/CheckFamilyWireShapeTest：条件/互斥/混合拒绝，实际互斥优先，单批字段一致，不开放 TRACE |
| T-PERM-108 | 同步契约/runbook 按实际逐族 DELETE 与完整 FULL 语义校准；现有同步 PG/E2E 拒绝用例 |
| T-FE-064 | grant hook 保存/失效/恢复及跨账号租户角色类型隔离，恢复仅提示；欢迎页使用真实会话，固定租户说明 |
| T-FE-065 | SyncStatusPgIT：真实门禁/双租户/空 FULL；BasicRoleGrantVerticalSliceE2EIT 管理三入口；凭证单次 secret 不进入列表 |
| T-FE-066 | 只读 lint、类型、Vitest、构建；HTTP 类型锁拒绝非 POST 与 get；CI push/PR 触发 |
| T-API-007 | 自动配置元数据加载测试证明 Servlet 验签注册/禁用/非 Servlet 形态；Feign URL 无发现可用，日志依赖 provider 正确 |
| T-API-008 | FeignCredentialInterceptorTest 复用 common M2M 清单并拒绝 GET；旧模型/EDIT/空 starter 删除，GatewayResponse 共用 R 字段 |
| T-API-009 | 35 组 Java/TS/SDK/文档字段对账；RolePermissionItemResp 由 perm-common 单源；明确未锁类型/可空性等边界 |
| T-API-010 | PaginationPolicyTest 14 项、ReportControllerTest/ValidationTest 27 项：200/201、hasNext、极大页码无整数溢出；示例请求迁移 pageNum/pageSize |
| T-API-011 | UserOrgTypeWireTest + 用户/组织定向 42 项，orgType Integer；前端常量收敛且保留审计动作与 HTTP 方法独立词义 |
| T-API-012 | JSON 64 KiB UTF-8/单根边界与真实类型创建 HTTP 200+20044；审计快照严格 JSON 不套输入小上限 |
| T-API-013 | ErrorCodeIndexTest：140 个当前枚举值全部索引且章节锚点存在；五未成册家族导航、权限码双向跳转、登录日志正式条款 |
| T-GW-011 | GatewayApplicationConfigTest：死白名单/死策略配置移除，未注册仍拒绝 |
| T-GW-012 | SaTokenConfigParityTest：Spring 解析绑定后的共有9键、单侧用途与未知键；值/新键故障注入能失败 |

## 定向验证与反例

定向 Maven 均用 `mvn test -pl <模块> -am -Dtest=<测试类> -Dsurefire.failIfNoSpecifiedTests=false -DskipTestcontainers=true -DskipE2E=true`；真实 PG 选择相应类并启用容器轨。详见对应日志：audit-green 85、auth-green-final 56、seed-green 99、gateway-json-green 141、user-orgtype-green 42、sdk-final 40、sync-status-green 29、tenant-green-retry 38（0 跳过）、final-boundaries-green 16、example-pagination-green 27、pagination-overflow-green 14 项通过。数字为各次选择集，不能相加冒充独立总数。

旧行为失败证据：授权代际/拒绝解释见 wave1；种子、认证锁定、读脱敏、分页200、JSON多根、orgType、草稿暂存、公开客户端与SDK自动配置均先有行为反例再实现。example-pagination-red 7 项中4失败（旧字段/上限不符）；pagination-overflow-red 新增2反例失败（负偏移及hasNext溢出），修正后分别27/14全绿。环境探测造成容器跳过的第一次运行不计作通过，tenant-green-retry 明确零跳过。

## 复审与接受边界

本地双轨结论：先代码、后文档独立只读核对；事实性缺陷已直接修正。无新增待决设计项，无新增无调用场景抽象。用户选择均回写现役契约：客户端 TTL 作为整链期限、实际互斥优先、旧种子备份重建、分页超限400与示例字段统一、PERSONAL不扩页、最小授权确认、普通用户名/IP保留、外部JSON64KiB、信封code审计、Gateway拒绝复用操作日志、根路径收紧而非根前缀保留。

历史工作树/根日志/根缓存删除因自动审批两次返回 blocked by policy，随后用户明确移出本次验收；没有执行删除，既有备份保留。其他维护内容已完成。Q-059~Q-063 既定延期范围继续保留；Q-064/065 分别记录既存缺失路径错误分类和大类拆分，未以本计划名义扩大实现。

容器完整运行发现 4 类旧 PG 夹具共 6 处缺少可信租户上下文，18 项隔离重现（排除一次 Docker 探测导致的跳过）后仅补测试绑定/清理，18 项全部通过且零跳过；生产保持缺上下文拒绝。该次 heavy 实跑成功 403.9 秒，其余容器无失败，但不能替代最终完整成功。

读脱敏附加反例：原实现把 JSON 数字 ID 替换为未加引号的掩码，AuditReadMaskingTest 以 JsonParseException 失败。修复后 AuditReadMaskingTest + SensitiveDataUtilsTest 25 项通过；完整回归另锁数组和根文本节点，保留数字/字段名与内嵌 JSON 结构。

最终统计由 Surefire 全量日志的执行汇总、逐类结果与 XML 实际 testcase 条目三者交叉核对，均为 2658 项；不能只加 XML 根 tests 属性（嵌套类可能为 0 而仍含 testcase）。

最终收口检查：技能双副本一致；已退役模型和示例旧分页 class 不在干净构建产物中；任务、计划、看板与证据随 2026-10-06 批次归档。没有推送、发布或修改部署库。

本次备份/认证演练的两个自建临时 PostgreSQL 容器已移除；ItInfra 可复用测试容器及用户原有服务保持原状。
