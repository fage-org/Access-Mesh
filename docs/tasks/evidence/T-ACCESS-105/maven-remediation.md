# Maven 依赖扫描与修复证据

## 扫描数据与门禁

2026-10-17，Dependency Check 12.1.9 导入 NVD 时对 CVE-2026-6785 / CVE-2026-6786 报 URL 超出 1000 字符字段长度。该工具会记录逐条错误后继续运行，不能以其继续执行推导为完整导入。[官方修复](https://github.com/dependency-check/DependencyCheck/issues/8466) 已在同一大版本的 12.2.2 发布，CI 与本地均固定到 12.2.2。

本地首次初始化使用 NVD 官方年度压缩文件及 modified 文件；全部按官方 metadata 的 gzip 长度与解压后 SHA-256 核验，清单见 [官方文件元数据](nvd-feed-manifest-2026-10-17.json)。本地缓存只复用校验通过的原始文件，不修改 CVE 内容。12.2.2 的 `update-only` 完整导入通过，再执行 `update-only -DnvdValidForHours=0` 强制 API 增量更新，补齐 229 条更新，退出码 0、零逐条导入错误。CI 保持 NVD API 更新方式，不依赖本地缓存。

`mvn -B org.owasp:dependency-check-maven:12.2.2:aggregate -DfailBuildOnCVSS=7.0 -DfailOnError=true -DskipTestScope=false -DskipProvidedScope=false -Dformats=HTML,JSON -DossIndexAnalyzerEnabled=false -DnodeAuditAnalyzerEnabled=false`（本地另指定独立 dataDirectory 和报告目录）完成分析并因漏洞阈值退出 1。原始 [JSON 报告（gzip）](maven-audit-before-2026-10-17.json.gz) 的数据时间：NVD API Last Checked=`2026-10-17T11:39:37+08`，Last Modified=`2026-10-17T03:16:28Z`。

基线报告：16 个报告条目存在 CVSS ≥ 7 匹配，共 131 个匹配项、127 个不同 CVE。计数不是已证实可利用的业务问题数；存在跨产品/语言的 CPE 错配，核实后按下述批准范围精确处置。

CI 将完整扫描日志保存为 `reports/maven/scan.log`，保留 Maven 退出码，并额外拒绝任何 `[ERROR]` 日志。按工作流原样提取 Bash 片段，用命令替身验证：正常退出 0 → 0；逐条导入错误但命令退出 0 → 1；漏洞退出 1 → 1；工具失败退出 2 → 2。该验证是门禁接线行为证据，不等于 GitHub Actions 真实运行。

## 当前核实

- PostgreSQL：42.6.2（测试路径）和 42.7.3（运行路径）命中 CVE-2026-42198，恶意数据库服务器可通过过大的 SCRAM 迭代次数消耗连接线程 CPU。统一管理升级到 42.7.14，尚待后端回归验证。
- MyBatis-Flex：扫描器将 `mybatis-flex-core@1.11.7` 错配为 `mybatis:mybatis:1.11.7`，升到 1.11.8 后仍有同类错配。CVE-2023-25330 针对 MyBatis-Plus；CVE-2020-26945 针对 MyBatis <3.5.6，而实际 MyBatis 依赖为 3.5.19。
- OpenTelemetry Java：报告中的 CVE-2026-24051 / CVE-2026-39883 属于 Go SDK 的操作系统命令执行路径；其他命中也包含 Go/JavaScript 产品。当前 Java artifact 不能仅因相似 CPE 名称而按这些版本判断。升级后核对剩余匹配，不自行忽略。
- Spring：按任务当前口径迁移到已批准的 Boot 4 正式组合；不使用旧分支商业修复版或未经批准的风险例外冒充公开升级完成。

## 精确误报与限定风险

2026-10-17 用户批准的误报按包/版本及单个 CVE 排除：

| 对象 | 匹配 | 实证 |
|---|---|---|
| MyBatis-Flex core、spring、codegen、annotation、processor、spring-boot-starter、spring-boot4-starter 七个包，均为 1.11.8 | CVE-2023-25330 / CVE-2020-26945 | Maven 实际 MyBatis 为 3.5.19，无 MyBatis-Plus；Flex 的 1.11.8 不是 MyBatis 版本。[MyBatis 修复](https://github.com/mybatis/mybatis-3/pull/2079)、[MyBatis-Plus 说明](https://baomidou.com/reference/about-cve/) |
| Nacos logback-adapter 1.1.4 | CVE-2021-29441 / CVE-2021-29442 / CVE-2021-43116 | `jar tf` 显示日志适配类，不包含 AuthFilter、ConfigOpsController 或控制台。其版本不是 Nacos 服务器版本 |
| docker-java-transport-zerodep 3.7.1 内嵌 HttpClient 5.5.1 | CVE-2026-71290 | `javap -p -c`：ApacheDockerHttpClientImpl 字段类型为 shaded `impl.classic.CloseableHttpClient`；[Apache 公告](https://lists.apache.org/thread/bhf7g2zwpom2ohvwjjjlonc93br2s8vq) 明确 Classic 不受影响 |
| 同一 Docker JAR 内嵌 httpcore5 / httpcore5-h2 5.3.6 | CVE-2026-54428 | Docker 使用 Classic HTTP/1.1，无 HTTP/2 HPACK 调用路径；[Apache 公告](https://lists.apache.org/thread/5zjp8vczvxq19pw2rvhs21q446bhl0sd) 针对 HTTP/2 解码 |

上述不适用匹配涉及 7 个不同 CVE，规则限定当前 artifact 或 Docker 内嵌路径；升级或调用方式改变须重新核实，不泛化为产品级忽略。扫描器会合并同类依赖；普通 HttpCore 升级后 Docker 内嵌旧副本单独出现，Flex core 排除后 spring 包单独出现，因此不能只对比报告条目数量。

另有 **1 项真实风险临时接受**：Docker JAR 内嵌 HttpCore 5.3.6 的 CVE-2026-54399。字节码显示 ManagedHttpClientConnectionFactory 的 Http1Config 入参为 null，默认 maxLineLength/maxHeaderCount 均为 -1；[Apache 公告](https://lists.apache.org/thread/zmxh1pl2zohov5ntdh4lt85gfrlchgpy) 所述 HTTP/1.1 头资源耗尽风险确实存在。用户仅批准当前 Testcontainers 测试依赖、可信 Docker 环境；不属于生产请求链。普通 Maven HttpCore 已升级至 5.4.4，不能覆盖 shaded 副本。上游新修复版本、生产依赖路径或不可信 Docker/代理接入触发撤回和复核；不得称此漏洞已修复。

## 迁移行为验证

最终解析版本：Framework 7.0.9、Gateway 5.0.3、Nacos client 3.1.1、Jackson 3.1.5（第三方内部另有 Jackson 2.21.5）、Redisson 4.8.0 / spring-data-40、Nimbus 10.10、PG 42.7.14、普通 HttpClient 5.6.5 / HttpCore 5.4.4、Tomcat 11.0.27、JUnit 6.0.3、Testcontainers 2.0.5。解析闭包中没有 Hutool 或 sa-token-jwt；Docker 内嵌旧 HttpClient/HttpCore 单独按上文处置，不与普通依赖混淆。

- 全 reactor `mvn -B install -DskipTests -Dspring-boot.repackage.skip=true`：12 模块编译及 install 成功；这是编译证据，不代替测试。
- `ExampleServiceApplicationTest`：补齐 Boot 4 拆分后的测试模块，原有 6 项 HTTP 用例全部通过。
- SDK `PermissionRegistrationPublisher.read` 保留 IOException 契约：Jackson 3 解析异常显式包装，原有严格 JSON 用例保留；网关内部密钥测试检查实际传给下游的 exchange，避免依赖框架可变头共享行为。定向回归 15 项通过。
- Nacos 客户端 3.1.1 对仓库现有服务器 2.3.2 独立冒烟通过：临时无数据卷容器，端口仅绑定 127.0.0.1:18848/19848；注册 `t105-compatibility-probe` 的 127.0.0.1:19300 实例并发现，发布/轮询读取 `probe.value=verified` 后删除配置并注销。首次立即读取遇到配置发布可见性延迟；改用有截止时间的条件轮询后通过。未改变服务端版本或现有数据卷，验证结束删除临时容器。

- 完整回归发现测试装配问题后停止该次运行，隔离 AccessBootstrapPgIT / AdminXmlPaginationPgIT 复现 28 项中的 9 个错误；修复直接服务调用的 Sa-Token 请求上下文、切换 MyBatis-Flex v4 显式自动配置引用后，28 项全部通过。E2E 共享类路径中补齐 Tomcat/Netty 管理端口隔离，ExampleProtectedApiE2EIT 的 10 项完整主线通过。
- Gateway 5 的 RemoveXForwardedHeadersFilter 会删除入口已重建的 XFF。新增实际装配头过滤链用例先得到 XFF=null 的真实失败；ClientIpHeadersFilter 在链末位只按 socket 对端重建后，配置与清洗 16 项通过，伪造多值和无 remoteAddr 分支均验证。不打开 trusted-proxies，保持既定 IP 信任契约。

## 升级后扫描

2026-10-17 12:50:59，本地同一完整 Maven aggregate 命令，附加 `-DsuppressionFiles=tools/security/dependency-check-suppressions.xml`：退出 0，无 `[ERROR]`，未排除 CVSS ≥ 7 匹配为 0。[原始 JSON（gzip）](maven-audit-after-2026-10-17.json.gz) 保留 suppressedVulnerabilities 及合并关联依赖；当前显示 8 个排除记录、8 个不同 CVE，其中 7 个为不适用匹配、1 个为已批准测试风险。包清单比主条目数多，原因是扫描器合并展示，不应把数字当作独立可利用问题数。

`node tools/security/check-maven-audit.mjs <原始报告>` 退出 0，[判定报告](maven-risk-decision-2026-10-17.json) 记录当次 Maven Central 复核时间和 test 引用。5 项 Node 门禁测试通过，涵盖 Windows/Unix 路径、httpcore5/h2、生产/新模块/缺失范围、不同包版本、新正式版、预发布及元数据缺失；把测试范围检查故意改成恒真后，原测试失败，证明生产路径断言有效。按当前工作流提取的 Bash 片段验证 5 种退出组合：正常、逐条导入错误、漏洞失败、扫描器失败、风险门禁失败，均符合阻断预期。

这只证明本地扫描和接线行为；GitHub Actions 的真实执行和工件仍是独立验收项。前端最新复验仍仅接受 braces 开发路径风险，不能把任一端的通过表述为漏洞清零。

## 本地双轨评审

代码轨先独立核对迁移工作树：HS256 限定/错误密钥/到期及 loginType 拒绝、独立 HMAC 和原签发器夹具、撤销/租户/密码代际调用、缓存键/TTL/提交后失效、JSON 大整数与 ISO 日期、SDK 子上下文及宿主隔离、Maven/Pnpm 阈值及例外范围；当前无未处理的静态评审 P0–P2 缺陷，行为由下述完整回归独立验证。实际测试已暴露并修正 mapper 条件装配、SDK IOException 契约及网关断言观察对象，未删测试或放宽授权断言。无新增待决策项或无真实调用方的抽象。

文档轨随后独立核对工程规范、架构、API、Gateway/接入指南、安全规则、AGENTS/README 与任务卡：发现旧 Gateway 配置前缀和 SDK 编解码注释残留（P3），已改为当前装配方式；Unreleased 补充迁移及风险接受说明。历史 superseded 契约和旧版本 CHANGELOG 保留历史事实。任务/计划未提前标终态，完整收口回归已通过，GitHub Actions 仍待真实执行。

## 本地验收与 CI 待办

2026-10-17 13:24:25，`mvn -B test -T 1C` 全 reactor 成功，**2870 项、0 失败、0 错误、0 跳过，17:30 min**；容器 execution 523 项、E2E 模块 33 项均执行，包含两种 heavy 验证，65540 行资源清理用例耗时 393.8 秒。整份日志落盘后解析 Maven execution 汇总，并与本次新生成 XML 中的 testcase 元素交叉核对一致；旧报告不计入。[分模块及测试类汇总](backend-verification-2026-10-17.json) 保留命令、结束时间和完整日志 SHA-256。

后端单测、定向 PG、跨服务主线及失败隔离证据如上；前端 562 项、类型检查/lint/构建和 44 组 DTO 对账通过。Maven 与 pnpm 本地安全门禁均通过，保留批准的 braces 开发依赖风险和 Docker 测试依赖风险，不声称漏洞清零。

GitHub Actions 真实执行和报告工件仍待完成。T-ACCESS-105 / T-ACCESS-103 保持 review，计划保持 active；本地验证不能替代该项 CI 验收。
