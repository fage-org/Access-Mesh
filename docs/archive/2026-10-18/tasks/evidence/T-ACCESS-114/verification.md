# 服务凭证 SHA-256 验收

任务：T-ACCESS-114。实施基线：`fd18eaf693ca4d48101346ceca2e35b43cbbafbb`。

## 范围与依据

最终源码基线见 [SHA-256 清单](source-sha256.json)，包含生产代码、单测、PG 与 E2E 夹具；全量回归前后核对本清单，运行期间不改源码。

服务凭证随机 secret 的存储与验证一次切换 SHA-256，旧 BCrypt 直接拒绝并重新签发，不保留兼容、缓存或自动迁移。依据为服务认证 §3.2、契约总册 §24、权威 schema、部署换发说明及本任务已确认范围。用户密码与 OAuth2 密钥保持原策略。

## 测试证据

2026-10-17～18，本机 Java 21 / Docker Desktop；定向验证于 17 日完成，全量于 18 日 00:09（Asia/Shanghai）完成：

| 验证 | 命令 | 结果 |
|---|---|---|
| 旧实现反例 | `mvn test -pl access-service -DskipTestcontainers=true '-Dtest=ServiceCredentialDomainServiceImplTest#shouldIssueWithCanonicalFormat+shouldReturnPrincipal_whenValidCredential+shouldRejectLegacyBcryptHash'` | 3 项均按行为断言失败，0 错误/跳过：旧签发不是 SHA-256；新摘要不能验密；旧 BCrypt 未按无效凭证拒绝 |
| 定向单测 | `mvn test -pl access-service -DskipTestcontainers=true '-Dtest=ServiceCredentialDomainServiceImplTest,ServiceAuthArbiterTest,ServiceCredentialAppServiceImplTest'` | 47 项通过，0 失败/错误/跳过；容器 execution 按命令跳过，不计入此数 |
| 定向 PG | `mvn test -pl access-service '-Dtest=ServiceCredentialPgIT,ServiceConfigCascadePgIT,AccessServiceSchemaPostgresTest'` | 49 项通过，0 失败/错误/跳过；凭证 16、服务删除并发/事务 8、原样 schema 25 |
| 业务接入 E2E | `mvn test -pl e2e -am '-Dtest=ExampleBusinessFinalCheckE2EIT' '-Dsurefire.failIfNoSpecifiedTests=false'` | 12 项通过，0 失败/错误/跳过；reactor 构建成功，1 分 42 秒 |
| 收口全量 | `mvn test -T 1C` | 2963 项通过，0 失败/错误/跳过；全部 reactor 模块 SUCCESS，18 分 18 秒；E2E 模块 34 项、heavy 通过（431.4 秒），未带跳过开关 |

全量计数取完整日志中各 Surefire execution 的最终汇总行相加，不累加逐类明细。开始前 9100 无监听，回归前后本次源码 SHA-256 清单一致；全量完成后仅整理文档与归档，不修改源码。

完整日志位于 `.tmp/T-ACCESS-114/`。一次全量启动在发现 E2E 预置凭证仍为旧格式后主动中止，确认 Maven 与其测试进程退出后才修改夹具；中止运行不计作通过。一次 E2E 启动因 PowerShell 拆分未加引号的 Maven 属性失败，未执行测试；修正命令见上表。

既有生命周期 PG 用例在成功认证后再停用、过期、服务停用/恢复及删除，验证没有沿用旧成功结果。新增 PG 用例验证旧 BCrypt 经仲裁器返回 403/20065、不产生 principal、不改写旧行，重新签发后同协议可用。单测覆盖损坏/未知摘要、大小写与附加字节、完整 UTF-8 输入、摘要不能替代 secret、不同凭证不能混用。

## 代码轨评审

主代理只读核对；范围包含领域服务、AppService 事务与审计、仲裁器、Mapper、响应 DTO，以及单测/PG/E2E 夹具。依据为当前工程规范、服务认证、测试与安全规范。

**已处置发现**：

- P2：`e2e/src/test/java/cn/ac/fage/accessmesh/e2e/ExampleBusinessFinalCheckE2EIT.java:141` 的数据库夹具仍用 BCrypt，导致新服务端拒绝业务 SDK；已改为独立计算的规范 SHA-256 摘要，调用方明文与原业务断言保持。全仓扫描服务凭证存储写入点，无其他旧活跃夹具。

**实证通过项**：

- `ServiceCredentialDomainServiceImpl.java:55` 保持 32 字节 SecureRandom，管理 DTO 无自定义 secret；主源码唯一 `secret_hash` 写入在签发入口。
- `ServiceCredentialDomainServiceImpl.java:95` 严格版本与固定摘要长度，`MessageDigest.isEqual` 比较；摘要实例局部创建、完整 UTF-8，无共享可变摘要器。
- 验密先于状态细分，状态仍通过原 Mapper/DomainService 逐请求读取；无查询内数据库写入、无缓存、无新增跨能力 Mapper 调用。
- AppService 事务、签发/删除服务行锁、操作日志、身份派生与端点白名单保持。普通响应 DTO 不暴露哈希。
- 旧行为反例已实证失败；真实 PG 锁定持久化、旧行拒绝及重新签发，生命周期用例覆盖先成功后拒绝。

**存疑待用户决策项**：无。**过度设计可裁剪项**：无；仅增加领域服务私有摘要方法与固定格式常量，没有通用算法注册器、策略类、新配置或兼容通道。

最终结论：已处置夹具遗漏后，定向 E2E 与最终全量均通过，无未处置 P0–P3 问题。

## 文档轨评审

代码轨结论形成后，主代理独立只读核对任务范围、定案入口、工程治理、认证设计、契约总册、schema、部署说明和 Changelog，没有因代码轨发现缩小文档排查面。

**已处置发现**：

- P3：Changelog 在 Unreleased 下重复新增 Changed 节，已并入原节。
- P3：任务背景将旧 BCrypt 行为写成当前状态，已改为“改造前”。

**实证通过项**：摘要格式、完整字节语义、错误码顺序、仅适用高熵随机服务 secret、无兼容换发及协调升级均一致。schema 只改注释，宽度不变；部署步骤无需销毁库或清理用户会话。凭证协议中的重放说明已与既有服务认证/部署边界一致，不承诺 secret 泄露无害。没有把算法探针当作接口时延或部署容量保证。

**存疑待用户决策项**：无。**过度设计可裁剪项**：无。归档时继续核对任务状态、问题索引与相对链接。

最终结论：文档发现已修正；任务 done、Q-076 已收敛，归档文件及权威入口链接已核对，无未处置 P0–P3 问题。

## 验证边界

未执行外部 AI 评审、真实部署或真实凭证换发。未修改前端、SDK 请求协议或权限引擎；不为此增加前端测试。算法评估中的约 64ms BCrypt 与约 0.3μs SHA-256 来自合成凭证微型探针，不代表接口吞吐承诺；数据库与网络成本仍在。
