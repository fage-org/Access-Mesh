# T-ACCESS-138 CSV 与持久化导入验证

日期：2026-10-19；基线 `b4021522d`。任务已完成，定向与完整回归分别记录。

## 实现与证据

- CSV 严格 UTF-8/BOM、引号/逗号/换行、字段类型、必填列和容量校验；不保存原文件，不回显原始值。省略可空 remark 时 INSERT 不写该列。
- 导入/撤销复用 DATA_EDIT 行列执行器并额外要求行 ALL、数据维护权和全部实际字段编辑权；不按角色名称放行。状态及撤销固定原操作者/租户，撤销绑定原报表和不可复用字段 ID。
- 同租户数据集锁、请求号及有效记录唯一约束，批次与分块记录同事务；原号重放保持 IMPORTED/REVOKED，主动重导须新号。
- UUID Mapper 接线使用明确 TypeHandler。multipart 采用 file + application/json request，10 MiB 文件与 11 MiB 请求/有界吞咽上限，边界超限返回 30106。

2026-10-19 定向命令与结果：

| 范围 | 结果 |
|---|---|
| `DemoCsvReaderTest` | 初始行为缺失 7 fail；实现后 7 pass，后补 UTF-8/文件/行数上限。非法内容错误不回显原值 |
| `mvn test -pl example-service -am -Dtest=DemoDataPgIT -Dsurefire.failIfNoSpecifiedTests=false` | 20 pass / 0 fail / 0 skip；原行列行为与导入重放、撤销重导、INSTANCE 拒绝、缺列编辑权、跨块回滚、并发同号及原字段/报表身份共同通过，日志 `import-pg-green.log` |
| 上传接线与上限 | 首次超限请求被容器提前断开，明确有界吞咽后同一 HTTP 用例通过；`upload-fixed.log` 为 2 pass / 0 fail / 0 skip，包含接口声明对账 |
| `mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false` | 14 pass / 0 fail / 0 skip，2:54 min；真实 multipart 导入/同号重放、重启 example 子进程后状态和记录保留、撤销旧号不复活及新号重导，`import-e2e.log`。核心场景不依赖 PowerShell 可选验证参数 |

日志位于 `.tmp/T-ACCESS-138/`。最初 UUID 缺 TypeHandler 是装配失败，不算行为 RED；已补齐后使用真实 PG 证明读写与幂等。

## 本地双轨核对

代码轨核对权限顺序、输入限额、实际写列、UUID/摘要绑定、原身份校验、同号并发、批次与各块共享 Flex 事务、撤销有效性及控制错误，不读取 IAM 内部表、不写影子用户、不引入异步导入/通用幂等平台。原始 actor 从签名上下文取得，CSV ownerUserId 只写记录归属。

文档轨核对 multipart 例外、请求号与重导、原字段删除行为、源报表固定、原文件不持久化、脚本重试参数及状态入口；配置样例由脚本和 E2E 共用 JSON，避免两套策略形状。没有未决设计取舍；未使用子代理或外部评审。

## 完整回归

2026-10-19 22:33:36，根 reactor `mvn test -T 1C -Dexample.demo.scriptHost=<本机 pwsh>`：**3,113 项，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS，24:35 min**，包含 E2E/heavy。完整日志 `.tmp/T-ACCESS-138/full-regression.log`。期间未改源码；未执行新的全量依赖漏洞扫描。
