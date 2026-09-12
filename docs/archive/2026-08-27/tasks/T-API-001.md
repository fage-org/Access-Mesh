---
doc_type: task
id: T-API-001
title: example 单受保护接口接入（Gateway 主线）与 Starter 名实对齐
status: done
plan: docs/archive/2026-08-27/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/services/example-service.md
  - docs/design/services/gateway.md
  - docs/design/architecture.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "example-service 实现一个经 Gateway 路由与鉴权保护的业务 API：未授权 403 → 在 access-service 授权后 200（证明业务服务接入路径真实可用，不再是启动骨架）；错误码用 3xxxx 段"
  - "接口保护由 Gateway 承担（规范 §2.4 服务内不重复鉴权）：不调用 PermissionFeignClient，不为演示新增接口扫描器、@PermResource、自动注册或服务内二次鉴权"
  - "POM 依赖瘦身：删除单接口不消费的 perm-client、perm-data、MyBatis-Flex、PostgreSQL、Redis、MapStruct、JSqlParser 依赖（实施时逐项核对实际消费，确有消费者保留并登记理由）"
  - "architecture.md §4.4 Starter 能力表与 §4.5.1 修正：perm-client 仅描述已实现的 Feign 能力（checkAuth/batchCheckAuth 等远程查询与 X-Internal-Secret 身份透传），移除未实现的「反射扫描接口+@PermResource 增强、全量幂等注册」表述；perm-data 已有「规划中未实现」标注保持"
  - "perm-data starter 标记 experimental/unavailable 或从 README 特性表移除（当前为空配置类，名实不符）；README 特性表逐项与实际对齐：example 单接口已交付、报表数据范围/动态 SQL 权限明确标注未交付"
  - "单测 + 一次真实链路验证（依赖 T-ACCESS-021 环境，403→200 证据登记）"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-26
---

# T-API-001 example 单受保护接口接入（Gateway 主线）与 Starter 名实对齐

## 背景

example-service 仍是启动骨架（仅 Application 类，无任何 Controller）；perm-data starter 为空配置类；architecture.md §4.4 能力表对 perm-client 宣称「反射扫描接口+@PermResource 增强、全量幂等注册」、§4.5.1 列出 @PermResource/PermissionClient.hasPermission/DataPermissionInterceptor——实际 starter 仅为 Feign 自动配置（@EnableFeignClients + 身份透传拦截器，提供 checkAuth/batchCheckAuth 等远程方法），扫描/注解/自动注册能力不存在；README 特性表宣称「三个 Starter 快速集成」可用，名实不符。接口级鉴权由 Gateway 承担（规范 §2.4），业务服务无需 perm-client 即可被保护； Gateway 403→200 证明的是接入路径而非 starter 消费。

## 范围

- 一个经 Gateway 保护的 example 业务 API（3xxxx 错误码）。
- example POM 依赖瘦身（含未消费的 perm-client）。
- architecture.md Starter 能力表与 README 特性表名实对齐。

## 当前口径

- Gateway 是接口鉴权唯一执行点；example 不做服务内二次鉴权。
- 未消费的 starter 依赖直接删除，不为「证明 Starter」制造调用；starter 能力文档只描述已实现部分。

## 非目标 / 遗留

- 不实现接口扫描、@PermResource、自动注册框架（有真实消费者后另行评估）。
- 不做报表数据范围、动态 SQL 数据权限（T-PERM-036 关联，仍暂缓）。
- 不为 example 建管理 UI。

## 执行记录（2026-08-26）

**机制终态**：

- **受保护接口**：`POST /api/example/demo/hello`（`DemoController`，身份回显接口）——入参 `{name}`，返回问候语 + Gateway `HeaderEnrichFilter` 注入的 `X-User-Id`/`X-Tenant-Id` 回显；`name` 空白拒绝 30001、身份头缺失（未走 Gateway）拒绝 30002（`ExampleErrorCode`，example 业务域错误码段首次启用）。接口自身零鉴权逻辑，保护完全由 Gateway 承担（规范 §2.4）。
- **接入路径**（E2E 钉死）：业务服务经 `/api/perm/resource-entity/sync`（UPSERT、`X-Internal-Secret`+`X-Service-Code`+`X-Tenant-Id` 身份、`service_config.extra.syncTypes` 白名单声明 `resourceTypeCodes:["API"]`）注册 API 资源 → 管理员经资源树定位后创建 `resource_api_mapping`（serviceCode=example-service、pathPattern=外部路径 `/example/api/example/demo/hello`）→ 授予 BASIC_ROLE `API:ACCESS` → 快照生效后 403 变 200。
  - **接入路径更替（2026-09-05，T-PERM-052 类型级所有权，本行上方为历史口径）**：`resource-entity/sync` 直连通道 + `syncTypes.resourceTypeCodes` 白名单 + 管理员手工建映射三段已退役——API 类型恒 MANAGED，resource-entity/sync 对其一律 RESOURCE_TYPE_OWNERSHIP_DENIED；现行接入 = 管理员经 Gateway 调 `service-config/sync`（FULL 接口声明，bootstrap 固定图含该路径且 SERVICE:SYNC_INTERFACE 类型级授予）一步创建 API 资源与 Gateway 映射（owner=example-service、maintainSource=SERVICE_SYNC、pathPattern=basePath+path）。E2E 第③步已同步迁移（ExampleProtectedApiE2EIT）。
- **POM/bootstrap 瘦身**：删除 perm-client、perm-data、openfeign、MyBatis-Flex、PostgreSQL、Redis、MapStruct、JSqlParser（逐一核对无消费方；Application 类同步删 `@EnableFeignClients`/`@MapperScan`）；保留 common/web/validation/nacos/log4j2。`bootstrap.yml` 改名 `application.yml` 并补 `spring.config.import: optional:nacos:example-service.yml`——原 bootstrap.yml 为死配置（依赖树无 bootstrap starter，端口 9300/Nacos 配置从不加载，服务实际以默认 8080 裸起）；`accessmesh.cache.enabled: false`（无缓存消费，caffeine 为 common optional 依赖未传递；HTTP ObjectMapper 由 Boot 自动配置提供，Jackson 默认时区即 UTC，与全链路 UTC 语义一致）。
- **bootstrap 图两处类型级化**（消除新服务接入鸡生蛋——实例级下新服务首条映射/首次授权均无正规入口，终态口径见 access-service-architecture §14.4）：
  1. `SERVICE:MANAGE_API_MAPPING` 实例级(access-service)→类型级（scopeAll）——否则新接入服务的首条 API 映射无正规创建入口（管理员 canGrant=false 无法自授）；
  2. 新增 `API:ACCESS` 类型级 + canGrant=true——否则新接入服务接口无法被首管理员授权给角色（13 条实例级管理 API 授权保留，最小暴露面不变）。
  固定图授权 20→21 条；设计回写 access-service-architecture §14.4/§14.7/固定图对象 1。
- **E2E**：`ExampleProtectedApiE2EIT`（gateway 模块，容器轨道）——三子进程（access-service/Gateway/example-service）+ Testcontainers PG/Redis，固定 6 步：① bootstrap 首管理员真实验证码登录 → ② 目标用户+空权限 BASIC_ROLE → ③ sync 注册资源+管理员建映射 → ④ 目标用户 403 → ⑤ apply-grant-plan 授予 API:ACCESS → ⑥ 30 秒窗口内 200（信封 code=200 + greeting + 身份回显 userId=目标用户）+ 授权后参数非法仍信封 30001。gateway POM 增 example-service test 依赖（子进程同 java.class.path 要求，同 access-service 先例）。
- **附带修复**：`AccessServiceApplicationTest` EPP 注册断言改直读 `META-INF/spring.factories`——T-ACCESS-024 提交的 `SpringFactoriesLoader.loadFactoryNames` 在 Spring 6.1.5 不可访问（test-compile 即失败，属遗留编译缺陷；load() 实例化路径会触发 CloudFoundryVcapEPP 不可实例化，故直读资源文件）。
- **名实对齐**：architecture.md §4.4 perm-client 行改为仅描述已实现 Feign 能力、§4.5.1 换为 PermissionFeignClient/FeignInternalSyncInterceptor 两真实组件、§3 表 example 行更新（含瘦身后无数据源）；README 特性表补 perm-data experimental 与「接入示例已交付/报表数据范围与动态 SQL 未交付」、架构图行与未交付清单同步、example 库说明改为暂无消费方；example-service.md 新增「已交付：单受保护接口」章节（接口契约/接入路径/瘦身口径）。

- **交付后加固记录（快照过滤/签名校验/文档治理）**：① 接口快照路径补 ACCESS 操作码过滤（`SnapshotAssembler` 原只按 API 资源类型过滤，非 ACCESS 操作的 API 授权也会被 Gateway 视为接口放行，与 fallback check-interface 语义不一致——属既有缺陷，补 `OPERATION_ACCESS` 过滤与 fallback 双路径同口径，SnapshotAssemblerTest 6 用例含「非 ACCESS 排除」回归）；② example 增 `GatewaySignatureFilter`（身份信任链示例）：复算 Gateway SignatureEnrichFilter 的 HMAC-SHA256（`HMAC(secret, userId|tenantId|timestamp)`，时效窗 300s 同 Gateway valid-seconds，常量时间比较），携带身份头但签名缺失/不匹配/密钥未配置一律拒绝信封 30003（fail-closed）；信任边界的根本保障仍是网络隔离，签名为纵深防御演示，控制器 javadoc 同口径改写；③ EPP 注册断言改 `ClassLoader.getResources` 枚举全部 `META-INF/spring.factories`（消除类路径顺序脆弱性，与 SpringFactoriesLoader 合并语义一致）；④ 文档治理修复：任务卡过程句、architecture.md 基础设施图 example_db/交互矩阵 OpenFeign 行/perm-data 树注释三处旧事实、BootstrapGraphDefinition 注释计数 7→8/20→21、example-service.md SDK 参考（标注规划口径）与数据库（无数据源）章节矛盾、access-service-architecture §14.4 补首管理员「内置 API 超管」语义强度提示、§14.7 存量轮次词改当前口径。

- **交付后加固记录（快照 scopeAll 语义/运行面）**：① API 类型级 scopeAll 快照语义修正——API 类型级 scopeAll 快照装配不再输出 ALL 通配条目，改展开为该 serviceCode 全部 enabled 映射的 INSTANCE 条目（条件语义保留，`ResourceApiMappingMapper.selectEnabledByServiceCode` 一次性查询）：原实现经 ALL 条目使 Gateway 对该服务任意路由路径（含未注册接口）直接放行，违反 §14.2「禁止 API 类型级 scopeAll 大包授权」与未注册默认拒绝口径；类型级授权语义钉死为「全部已注册 API」，新增映射经快照 TTL/广播窗口生效；② 快照 ACCESS 位掩码补继承语义——`resolveAccessCoverageMask` 取「有效位（binaryBit|inheritMask）覆盖 ACCESS 的全部操作位」，与 fallback 引擎一致（自定义操作经 inheritMask 继承 ACCESS 时快照同样放行）；不采用 `PermResult.effectiveOperationEntries`（无 conditionId 投影，条件装配必需），单次 mapper 查询不变；③ example POM 排除 `spring-boot-starter-logging`（web starter 传递 Logback 与显式 log4j2 starter 双 Provider，实际选择 Logback 导致 log4j2-spring.xml 全部被忽略）+ 修复 log4j2-spring.xml `Property` 元素层级错误（Log4j2 真正生效后暴露）；④ 签名时效窗可配置 `example.signature.valid-seconds`（默认 300，与 access-service `perm.signature.valid-seconds` 运维同调）；⑤ README 启动顺序补 example-service（含 ACCESSMESH_SIGNATURE_SECRET 同源要求与 30003 行为）；⑥ AccessBootstrapInitializer 五处注释计数 20→21/门禁 7→8/实例级 SERVICE 残留表述、§14.7「（第二轮）」轮次词、未用 import 清理。SnapshotAssemblerTest 6→9（scopeAll 展开/含条件展开/inheritMask 继承），ExampleServiceApplicationTest 3→4（时间戳超窗过期+未来）。

- **交付后加固记录（日志与配置锁定）**：example log4j2-spring.xml 布局改 JsonLayout（与 access-service/Gateway 同口径，project-rules §4.2 全环境统一 JSON；原 PatternLayout 输出纯文本违规，MDC 结构化字段无法输出）；签名时效窗可配置性经 `GatewaySignatureFilterTest` 锁定——以非默认窗口 60s 验证边界（ts=now-30 放行、±120s 拒绝 30003），实现若回退硬编码 300s 该用例即失败（顺带修复：`secretConfigured` 初始化移入构造器，单元测试不经 `@PostConstruct` 也可验证）；SnapshotAssembler 类注释/GatewaySignatureFilter verify 注释与实现对齐（scopeAll 展开口径/可配置窗口口径）。

**遗留登记**：`docs/design/schema/example-service.sql` 无消费方（瘦身删数据源），保留供未来演示数据场景；Nacos 远端若持有 example 旧配置需清理（bootstrap.yml 死配置时期远端从未生效，无迁移负担）。

**验证证据（Windows 11 + WSL2 docker-desktop，2026-08-26）**：

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| example 单测 | `mvn -pl example-service test` | Tests run: 10, Failures: 0（DemoControllerTest 3 + ExampleServiceApplicationTest 4 + GatewaySignatureFilterTest 3：自定义 60s 窗内放行/过期/未来拒绝——锁定 valid-seconds 可配置性） |
| example E2E（三子进程 403→200） | `mvn -pl gateway test -Dtest=ExampleProtectedApiE2EIT` | Tests run: 6, Failures: 0（④真实 403 → ⑥ 200+身份回显+30001+直连伪造身份头 30003；加固后复跑全绿） |
| access-service 单测轨 | `mvn -pl access-service test -DskipTestcontainers=true` | 740 全绿（基线 736 + SnapshotAssemblerTest 新增 3：非 ACCESS 排除/scopeAll 展开/inheritMask 继承；AccessServiceApplicationTest 10/10 含枚举式 EPP 断言——加固后复跑） |
| access-service 容器轨（bootstrap 图变更回归） | `mvn -pl access-service test` | Tests run: 98, Failures: 0（AccessBootstrapPgIT 14/14：授权计数 20→21/scopeAll 6→8/canGrant 2 同步更新） |
| gateway 全量（BasicRoleGrant E2E 回归 + 新 E2E） | `mvn -pl gateway test` | 单测轨 93 + 容器轨 14 全绿（BasicRoleGrantVerticalSliceE2EIT 8/8 回归通过——类型级化未破坏原链路；ExampleProtectedApiE2EIT 6/6） |
