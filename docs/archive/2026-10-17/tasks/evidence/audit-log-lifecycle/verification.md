# 审计日志生命周期验收

日期：2026-10-17。基础提交 `bb03f780e`，实现范围 T-ACCESS-107、T-ACCESS-108、T-FE-070。执行期间未改变任何真实业务库或部署配置。

## 行为证据

- CSV 编码初始红测：`mvn test -pl access-service -DskipTestcontainers=true -Dtest=AuditCsvWriterTest`，4 项行为断言失败、0 错误/跳过；验证转义、公式前缀、容量与 location 退出。
- 定向单测：`mvn test -pl access-service -DskipTestcontainers=true -Dtest=AuditCsvWriterTest,AuditReadMaskingTest,AuditLifecycleTest,JobAppServiceImplTest,HttpApiPathSnapshotTest,AccessServiceArchitectureTest,AppServiceOperationLogCoverageTest`，46 项通过，0 失败/错误/跳过（容器 execution 按本条命令跳过，不计入本条通过范围）。
- 原样 DDL 与分页：AccessServiceSchemaPostgresTest 25 项、AdminXmlPaginationPgIT 8 项通过。
- 真实事务测试最初遗漏 MyBatis-Flex 事务自动配置，普通 JDBC 事务管理器未覆盖 Mapper 事务；已补齐与正式应用相同的 FlexTransactionAutoConfiguration，再验证独立连接 NOWAIT 不能夺取租户/任务尝试行锁，回滚恢复被删行。未以 mock 或改弱断言替代该证据。
- 最终新增 AuditLifecyclePgIT 7 项在完整回归中通过：三表截止线/租户范围、500 条分批、停用暂停/恢复、过期尝试拒绝、事务锁与回滚、登记幂等/关闭、同源过滤/脱敏/行数超限，以及跨游标批次并发清理仍返回完整语句快照。
- 前端：`pnpm typecheck`、`pnpm lint:check`、`pnpm contracts:check`（44 组）、`pnpm build` 均通过；`pnpm test:run` 52 个文件、568 项通过。新增测试涵盖 CSV 成功/JSON 错误、换会话/卸载丢弃与对象 URL 释放；原列表加载测试补齐新依赖模拟后通过。

完整后端执行：`mvn test -T 1C`，8:28 min，已运行 2876 项（含 heavy/容器），1 项失败、0 错误/跳过；失败为 AccessBootstrapPgIT 旧初始化数量断言，API 资源 112→115、POST 映射 111→114，对应新增导出路由。Maven 因此阻断 E2E，未把未执行视为通过。

运行结束后仅更新上述测试预期，业务源码不变；`mvn test -T 1C -pl e2e -am '-Dtest=AccessBootstrapPgIT,*E2EIT,E2eProcessSupportTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，BUILD SUCCESS，5:08 min：初始化组 20 项、E2E 33 项全部通过，0 失败/错误/跳过。E2E 随 reactor 编译上游，未单独从本地仓库拿 repackaged jar。全量首次结果与后续隔离复验共同覆盖最终后端范围；未宣称首次全量命令成功。

最终兼容性补验：旧登录日志请求携带 sort 的红测 1 项断言失败（反序列化不接受原有字段），保留该字段原先“接受但不参与排序”的行为后，`mvn test -pl access-service '-Dtest=AuditLifecycleTest,AuditReadMaskingTest,AuditCsvWriterTest,HttpApiPathSnapshotTest'`，21 项通过、0 失败/错误/跳过，BUILD SUCCESS（21.621 秒）。首次补验执行会话失去完成状态，未当作完成证据，已用上述成功执行确认。完整运行后业务源码唯一补充为该请求字段兼容，初始化调整仅为测试数量预期；未重复与这些收尾无关的整套测试。

按测试类以最后一次结果覆盖此前结果，最终后端覆盖 2910 项，0 未解决失败/错误/跳过；包含 E2E 33 项与 heavy。临时日志保存在 `.tmp/Q-061/`，本记录以内联结果为持久依据。

## 代码轨

实证通过项：

- 操作/变更导出沿用列表主体，登录导出保留列表既有 LOCAL_USER 解析；QueryGate VIEW 门禁先于审计数据读取。租户只取可信上下文，列集合来自现有响应转换，原始快照不额外暴露。
- Mapper 复用列表 SQL 条件；一条游标 SELECT 使用语句快照，跨批次并发清理不丢导出行。行数上限加一用于识别超限；UTF-8 实际字节（含 BOM、转义、表头）验证完成后才返回 HTTP 文件。
- 默认无期限且关闭清理；不可变启动配置不会因 Environment 后续变动热刷新。每类别独立天数，平台审计不在 SQL 删除范围内。
- 部署任务只占固定目标的部分唯一索引。任务创建/修改/批量删除/启停/手动触发均在现有权限检查后拒绝受保护目标，拒绝先于取消调度等副作用；保护与实际调用解析规则一致，包括尾部分隔符。
- 清理须 TASK 上下文及计划执行时间；批次锁序为租户→当前任务尝试，停用提交后不进入新批，旧尝试不能删除；每批独立事务，沿用既有任务租约/重试/执行日志。
- 前端共用筛选构造器，JSON 错误 Blob 不保存为 CSV；卸载取消请求，旧会话成功响应不下载；HTTP 层既有 401/403 处理保留。
- 控制器不直读 Mapper，跨能力通过 DomainService；新机制各有实际调用方，没有新增归档中心、异步导出任务或配置 UI。

未解决 P0/P1/P2/P3：无。初始化数量断言已按新增路由修正并经真实初始化复验。存疑待用户决策项：无。过度设计可裁剪项：无。

## 文档轨

实证通过项：B/B2/C1/D1/E1/F1/G1/H1/I1 均归位契约 §16.7；三个二进制下载例外、错误码、启动配置、停用恢复、不可逆在线删除、location 退出与原库备份/重建边界一致。JobResp.deploymentManaged 为部署管理归属，未增租户配置面。schema、DTO 字段对账、前端页面说明与部署指南已同步。

事实性 P3 已处理：§17 锚点曾位于新增 §16.7 正文中，已归位；错误码分段补齐审计交付及已有平台段。不存在新的产品取舍。归档与链接检查已在终态迁移后执行，任务及 Q-ID 指针可解析。

## 验证边界

本次没有运行浏览器中的人工交互验收，也没有启用实际部署清理策略；前端行为依据 Vitest、类型、构建与静态接线检查。运行中修改源码禁止，最终源文件校验清单随本记录保存。
