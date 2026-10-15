---
doc_type: task
id: T-ACCESS-100
title: SDK 公共依赖宿主侵入收口（日志实现/默认时区/全局异常处理/Web 栈传递）
status: done
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/project-rules.md
  - docs/design/service-authentication.md
depends_on: []
blocks: []
acceptance:
  - "外部普通 Spring Boot 宿主引入权限 SDK 后：默认日志后端不被替换（common 不再传递 spring-boot-starter-log4j2 实现依赖）、JVM 默认时区不被无条件改写、无关 API 的错误响应不被平台全局异常处理器改写、WebFlux 宿主不被推成 Servlet 应用"
  - "四项各有最小宿主探针/依赖解析回归锁（仓库内 Maven 解析或等价离线验证）"
  - "project-rules 依赖边界与 service-authentication SDK 边界章节回写最终依赖清单"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-14
---

## 背景

P2×4 合并族（外部持续审查 2026-10-12/14 发现，主代理核实成立）。SDK 公共模块把平台服务端装配整包传递给外部宿主：

- **日志实现**：`common/pom.xml:39-42` 将 spring-boot-starter-log4j2 作为普通依赖；client starter 消费 common 无排除，其自身排除仅影响本路径。宿主 starter-web 在前时类路径同时带入 Logback 与 log4j 桥接环（探针实证可复现 `LoggingException`），SDK 在前时宿主默认后端被替换为 Log4j2。
- **默认时区**：SDK 无条件执行平台时区改写，改变宿主进程行为（与 common UtcTimezoneEnvironmentPostProcessor 同源逻辑被传递）。
- **全局异常处理**：SDK 间接安装平台全局异常处理器，宿主无关 API 的 HTTP 错误契约被改写。
- **Web 栈**：SDK 服务端 Web 依赖把 WebFlux 宿主解析成 Servlet 应用。

停用权限客户端开关不能移除这些运行依赖。

## 范围

perm-common/client starter/registration starter 的依赖与自动装配边界；仓库模块自身行为不变（仍统一 Log4j2/UTC）。

## 当前口径

SDK 依赖边界只保留 SLF4J API 等真正公共依赖；平台选用的日志实现、时区 EPP、全局异常处理、Servlet 栈由平台应用自己声明。平台内模块按既定选择配套排除，不改运行形态。不新增日志抽象、多后端兼容框架或配置开关矩阵；四项合并一次收口，不按接口数量扩展。

## 验收对照

见 frontmatter `acceptance`；以依赖解析与最小宿主探针为锁（不要求启动完整宿主 HTTP 应用）。

## 非目标 / 遗留

平台内部模块的 Log4j2/UTC 强制口径不变；时区「不做时区处理」产品定案不变（本卡只管 SDK 不外溢）。

## 完成记录

- 实现（2026-10-14）：①common 瘦身——`spring-boot-starter-web` 改 optional（Web 栈仅编译期可见）、删除 `spring-boot-starter-log4j2` 传递（三平台应用本就各自显式声明）、删除 common 的 `META-INF/spring.factories`（EPP 注册）与 `AutoConfiguration.imports` 整文件（common 零注册外溢）；②平台侧自声明——access-service/example-service 各自注册 EPP + Common/Cache/RedissonCache 四条，gateway 注册 EPP + 缓存两条 + `CommonAutoConfiguration` 候选登记（「注册以排除」，见下）；③SDK 零参与——两 starter 摘除全部 `spring-boot-starter-logging` 排除（排除项经 Maven 就近仲裁会反向剪掉宿主自选后端，探针编译期实证），`slf4j-api` 以 provided 显式化（原经 common 日志实现件间接获得）；④测试域单 Provider——common（optional starter-web 带 logging 排除 + test log4j2）/perm-common（test log4j2）/两 starter（宿主式 Logback，无声明）。
- 探针红跑 4/4（`sdk-host-probe` 新模块=外部 WebFlux+Logback 宿主消费两 starter；旧边界下）：①log4j-core/log4j-slf4j2-impl/log4j-jul 类路径在场（FAILURE）；②③宿主上下文启动 ERROR——除 EPP 时区翻转外，暴露**同根第五面**：`CacheAutoConfiguration` 方法签名引用 optional 的 micrometer，注册外溢到缺件宿主在条件求值期 `NoClassDefFoundError` 直接炸掉宿主启动（common imports 整文件退出而非仅摘 CommonAutoConfiguration 行的硬理由）；④应用类型推断 SERVLET + DispatcherServlet/Tomcat 在场。红跑过程另实证：探针缺 `@EnableAutoConfiguration` 时 AutoConfiguration.imports 不触发（③假绿），补齐后复红。
- e2e 双向实证（全量 r1/r2）：r1（摘除 gateway exclude）gateway 子进程 3 ERROR——E2E 共享大类路径（子进程类路径=本 JVM 类路径去 test-classes，含兄弟模块 target/classes）经 access/example 注册文件带入 `CommonAutoConfiguration`，与 gateway 自有 `GlobalExceptionHandler` 同名 Bean 冲突（T-ACCESS-003 既有防线承重）；r2（恢复 exclude）gateway 模块自身 7 ERROR——`AutoConfigurationImportSelector.checkExcludedClasses` 对非候选类抛「could not be excluded」。定案「注册以排除」：gateway 自身 imports 候选登记 + 主类 exclude，任意类路径形态下 exclude 合法且净效果恒为不装配，与改造前行为完全等价。
- 回归（2026-10-14）：探针 4/4 绿；定向 common/perm-sdk 三件/gateway 145/example 53/access 单测轨全绿；全量 `mvn test -T 1C` 第三轮 BUILD SUCCESS（11 模块，2817 tests / 0 failures / 0 errors，E2E SUCCESS）——r1/r2 为修复过程轮。
- 设计回写：project-rules §4.1（日志句补 SDK 不传递）、§5.5 新增「依赖传递边界（common / 权限 SDK）」（common 传递面白名单/零注册/SDK 零参与/探针锁）、§7.4（EPP 注册位置口径）；service-authentication §3.2（common 单源理由句随传递面收敛订正）、§3.3 SDK 直连行（T-ACCESS-100 依赖传递边界与最终依赖清单）；`UtcTimezoneEnvironmentPostProcessor`/`CommonAutoConfiguration` javadoc 与 `AccessServiceApplicationTest` 注册断言 javadoc 同步。
- codex sol r2 处置（2026-10-15，P2-3/P2-4）：①SDK 闭包日志归零——client starter 以 `spring-boot-autoconfigure` 取代 `spring-boot-starter` 直依，openfeign/loadbalancer/validation 深边排除 boot 聚合器（红跑：新增 `sdk-host-probe-log4j2` 模块=自选 Log4j2 宿主，Logback 击穿宿主剪除→绿）；SDK 库模块测试域统一绑定全局 starter-test 的 Logback（test log4j2 撤除——与全局 test 依赖成桥接环，评审旁证）；②SDK Feign 编解码自洽——`PermFeignClientsConfiguration` 子上下文 `@ConditionalOnMissingBean` HttpMessageConverters 兜底（专属 ObjectMapper+jsr310 ISO 时间串），`jackson-datatype-jsr310` 随 perm-common compile 传递（红跑：无 converters 自动配置的宿主 checkAuth 编码 NoSuchBeanDefinition〔SpringEncoder.initConverters 字节码实证〕→绿）；project-rules §4.1/§5.5、service-authentication §3.3 同步。
