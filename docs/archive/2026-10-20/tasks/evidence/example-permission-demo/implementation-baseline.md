# 权限演示实施前核对

日期：2026-10-19。源码基线：`6a5701957217df2e4b8da961950e9dce8eb32298`。本文件是 T-ACCESS-136 的静态核对证据和迁移安排，不替代设计/API 契约，也不证明新业务行为测试已通过。计划已启动，实施契约已 adopted；业务实现由后续任务验证。

## SDK 与现有业务接线

| 已核实对象 | 当前事实 | 实施影响 |
|---|---|---|
| [PermissionFeignClient](../../../../../../perm-sdk/perm-client-spring-boot-starter/src/main/java/cn/ac/fage/accessmesh/perm/client/feign/PermissionFeignClient.java) | `queryScopes` 已有显式 `X-Credential-Id` / `X-Credential-Secret` 重载 | 按可信租户选择凭证，复用 SDK，不自造 HTTP 客户端或查询 DTO |
| [QueryScopesReq](../../../../../../perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/QueryScopesReq.java) | 主资源上下文、父操作、范围类型和范围操作均有现成字段；请求体没有租户字段 | 本地核实报表身份后组装上下文；客户端不能替换主体、租户或父报表 |
| [QueryScopesResp](../../../../../../perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/resp/QueryScopesResp.java) | 父命中操作与按类型/操作分格的四态分别返回；实例项携带业务编码及 codeType | 行、字段两类分别消费，不能把所有 items 合并成一组权限；先识别整体拒绝，再校验预期格完整性 |
| [BusinessPermChecker](../../../../../../example-service/src/main/java/cn/ac/fage/accessmesh/example/perm/BusinessPermChecker.java) | 目前只有 check、batch-check 和 query-resources 接线；`accessibleScope` 仅保存 all 与业务码集合 | 不能把它直接当作行列四态执行器；新 query-scopes 适配保留类型、操作和 codeType，复用已有凭证选择与调用失败处理边界 |
| [ExampleErrorCode](../../../../../../example-service/src/main/java/cn/ac/fage/accessmesh/example/enums/ExampleErrorCode.java) | 30004 表示业务权限拒绝，30005 表示鉴权不可用；30001 的当前文案专指 hello 的 name 参数 | 不将新策略配置错误伪装成权限不足或空数据；新业务错误码仍须在 API 定稿时分配，不能直接泛用 30001 |
| [ExportJobRunner](../../../../../../example-service/src/main/java/cn/ac/fage/accessmesh/example/report/ExportJobRunner.java) | 现有一次性内存导出捕获提交 clientIp | 新后台执行按已确认设计省略 IP，不能无区别复用旧导出上下文；旧路径的独有行为暂保留 |

查询消费的权威依据为[契约 §18.6](../../../../../design/access-service-api-contract.md#contract-section-18-6)与[演示行列设计](../../../../../design/example-permission-demo.md#field-permissions)。`USER_NOT_FOUND` / `OBJECT_KEY_NOT_FOUND` 可以合法地返回空格集合；不能未经整体原因分类就把这类响应当成传输故障。响应异常、缺格或未知映射也不能降级为 ALL。

## 旧探针证据保留与迁移安排

以下测试方法已静态核对存在，执行状态均为 **not run**。目前不删除旧路由、测试、示例脚本或权限清单；新路径的等价证据实际通过前，旧证据继续有效。

| 行为 | 现有证据 | 对应新任务与接替门槛 |
|---|---|---|
| 实际目标检查、拒绝不返回数据 | `ReportControllerTest.view_checksActualTargetAndReturnsSameReport`、`view_deniedByFinalCheck_throws30004`；`ExampleBusinessFinalCheckE2EIT.step3_dualPathAdmissionPassesBusinessDifferentiates` | 139/140 的真实报表与行列查询验收，036 验证网关准入不替代业务最终检查 |
| 批量逐项读、全拒绝无泄漏 | `batchView_mixedTargets_perItemIndependent`、`batchView_allDenied_noDataLeak`；E2E `step4_batchMixedTargetsPerItemIndependent` | 140/036 保留批量读的逐项结果；不得用批量写整批原子语义替代这项证据 |
| 列表范围与 total 一致 | `list_filtersByScope_totalSameScope`、`list_allScope_returnsAllReportsNotFilteredToEmpty`、`list_emptyScope_returnsEmptyWithZeroTotal`；E2E `step5_listScopeSameCaliber` | 140 用真实 SQL 证明过滤、投影、total 同口径；原内存过滤测试不能证明 SQL 正确 |
| 类型级创建门禁 | `create_checksTypeLevel`、`create_denied_throws30004`；E2E `step7_createRequiresTypeLevel` | 139 真实报表创建经相同门禁，失败不落业务数据或待发布事件 |
| 真实父上下文 | `subView_passesRealParentContext`、`subView_wrongParent_denied`；E2E `step8_subViewRealParentBinding` | 140/036 用报表甲/乙字段隔离验证；只测试类型码相同不足以接替 |
| 导出提交后撤权、依赖故障 | `export_revokedBetweenSubmitAndExecution_deniedAtExecutionTime`、`export_checkUnavailableAtExecution_failsClosed`；E2E `step9_exportSubmitAndExecutionTimeRecheck` | 141/143 用执行闸门覆盖提交与实际取数之间的权限变化；新后台无 IP 的差异另测，不改写旧 IP 行为 |
| 他人结果与不存在一致拒绝、跨租户同码隔离 | `exportStatus_foreignJob_sameResponseAsNotFound`、`view_sameCode_crossTenantIsolated`；E2E `sharedInstance_selectsTenantCredentialForActualFinalCheck` | 137/141/143 分别验证数据库隔离和任务/结果归属，不能仅比较 HTTP 200 |
| HTTP 入参与分页 | `ReportControllerValidationTest` 的空编码、批量超限、分页参数用例 | 保留低成本 HTTP 边界证据；新接口分页沿现行统一契约，不用数据库用例重复穷举输入格式 |

源码入口：[ReportControllerTest](../../../../../../example-service/src/test/java/cn/ac/fage/accessmesh/example/controller/ReportControllerTest.java)、[ReportControllerValidationTest](../../../../../../example-service/src/test/java/cn/ac/fage/accessmesh/example/controller/ReportControllerValidationTest.java)、[ExampleBusinessFinalCheckE2EIT](../../../../../../e2e/src/test/java/cn/ac/fage/accessmesh/e2e/ExampleBusinessFinalCheckE2EIT.java)。hello 的签名/信封/管理端口与首次接入证据继续保留，不能因新增业务族就认定其过时。

## 持久化与测试基建边界

- [现有 example POM](../../../../../../example-service/pom.xml)没有数据源、MyBatis-Flex 或 Testcontainers；[旧 schema](../../../../../design/schema/example-service.sql)仍是含外部数据源和 BI 查询配置的旧模型，不能原样启用。表结构替换归 137，本次不执行 DDL。
- [ItInfra](../../../../../../access-service/src/test/java/cn/ac/fage/accessmesh/access/it/ItInfra.java)在 access-service 测试源码中，启动时一起启动 PG/Redis，并按 access schema 创建模板、调用 `initialize_tenant_baseline(1)`；即使 `fromTemplate=false` 也先经过全局启动。直接调用该方法不是 example 的独立 PG 支持出口。
- 可复用的是容器生命周期、fork 槽位互斥、类库隔离与 JDBC 连接设施；access 模板和租户种子、Redis 注册属于原消费者。共享出口方案不能让 example 加入 IAM 内部表依赖，也不能把 access 测试包作为依赖发布。具体承载方式仍待 136 定稿，137 实施。
- access POM 已将普通测试和 `testcontainers` execution 隔离；example 尚无对应 execution。新 PG 轨须遵守 `skipTestcontainers`、heavy 标签及进程隔离规则，不开启线程级类并行。
- [现有应用启动测试](../../../../../../example-service/src/test/java/cn/ac/fage/accessmesh/example/ExampleServiceApplicationTest.java)当前以“无数据源也可启动”为前提。137 引入必需持久化后须调整其装配方式，同时保留健康检查隔离、信封、签名和错误处理行为；不能仅删掉启动测试来规避数据库依赖。
- E2E 当前只给 access 初始化业务数据库，example 使用内存数据。137 之后须在现有 E2E 设施中为 example 提供独立库及账号，再启动真实服务；跨服务验证仍只放 `e2e`，并通过 reactor 解析三服务类路径。

### 测试依赖风险范围

[安全规范](../../../../../../.claude/rules/security-standards.md)与[审计脚本](../../../../../../tools/security/check-maven-audit.mjs)已按 2026-10-19 用户确认，将 Docker HttpCore 临时例外限定扩展到 `Example Service:test`，原 `Access Service:test` / `E2E Tests:test` 保留。组件版本、CVE、可信 Docker、生产路径禁用、新正式版本重新核查等条件保持；新共享测试模块不自动获得例外。

2026-10-19 读取 [Maven Central 元数据](https://repo.maven.apache.org/maven2/com/github/docker-java/docker-java-transport-zerodep/maven-metadata.xml)，`latest` / `release` 均为 `3.7.1`；此事实仅表示本次查询未发现更新正式版，不证明其他传输依赖无修复路径。未变更依赖版本或 CI。137 引入依赖后仍须核查真实解析路径，不将规则单测替代依赖审计。

执行 `node --test tools/security/check-maven-audit.test.mjs`：2026-10-19，先加入 example 测试路径，旧实现按未批准路径拒绝（5 tests，3 pass / 2 fail）；实现限定放行后 5 pass / 0 fail / 0 skip。既有用例补充 `Example Service:compile` 与允许/未知路径混合的拒绝输入；组件/版本/新版本门禁保持测试覆盖。没有运行完整漏洞扫描，不声称风险已修复。

## 验证边界

已完成源码、POM、契约、规则的静态对照及文档链接检查；安全例外范围的 Node 单测 **passed**，见上节命令与计数。Maven、PG、E2E、完整安全扫描均 **not run**。上述源码位置代表当前可复用资产，迁移表代表待兑现安排，不作为下游业务任务已完成的依据；T-ACCESS-136 完成范围仅为契约与实施准入。

## 本地双轨核对

范围：T-ACCESS-136 的目标设计、API 契约、任务/计划入口及安全例外脚本差异。主代理按技术轨、文档轨先后核对，无子代理、外评或全量业务回归。

**技术轨**：安全检查保持 `.every` 验证所有依赖路径、精确内嵌组件/版本、非空证据、新正式版本重新阻断；仅加入用户批准的 Example Service:test。正反单测先失败后通过。新业务契约静态覆盖身份来源、行列四态、稳定字段身份、原子批次、版本/CAS、提交后响应丢失、旧 attempt 防覆盖、完成结果领取；不把这些静态结论标为业务验收通过。

已收敛 **P2**：[固定主操作边界](../../../../../design/example-permission-demo.md#admission-binding)经用户确认。现役 [FULL 接口清单](../../../../../../example-service/examples/first-access-interfaces.json)中的 requiredPermission 与业务检查保持固定映射；新 API 已移除可编辑 parentOperationCode/exportOperationCode 及报表创建的主类型输入，保留动态行列配置。无未处理的技术缺陷或用户待决项。

**文档轨**：设计与当前内存实现有明确区分；T-ACCESS-136 契约已 adopted、任务 done；T-ACCESS-137 开始实施，其余按依赖等待。导入请求号、recordCode 定位、安全例外边界和执行预算均归所属规范，任务只引用和验收。新 API 路径形态/去重、清空协议、设计引用和本地链接/锚点检查通过；`git diff --check` 通过。该轨独立核对固定入口的用户决定已同步到设计、API 与状态入口，无可编辑主操作残留。

**减法核对**：不新增 rowId、通用幂等平台、逐记录 IAM 发布事件、独立文件系统或发布型测试支持模块。共享测试源码只服务已存在的 access 和本计划 example 两个消费者，资源发布复用现役增量 sync，CSV 采用一次有界导入。当前未发现需要另行裁决的新增无调用方机制；主操作联动机制已按用户决定不纳入首版。
