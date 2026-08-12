---
doc_type: task
id: T-ACCESS-001
title: 建立 access-service 工程骨架并物理归并源码
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#2-目标工程与部署单元
  - docs/design/access-service-architecture.md#3-模块边界
  - docs/design/access-service-architecture.md#9-api-sdk-与生态切换
  - docs/design/project-rules.md
depends_on: []
blocks: []
acceptance:
  - "新增单一 Maven 模块 access-service 与 AccessServiceApplication，编译基线统一为 Java 21"
  - "admin 与 permission 源码迁入 cn.ac.fage.accessmesh.access.admin / permission，跨域入口位于 access.application"
  - "AdminErrorCode 与 PermissionErrorCode 随领域迁入后继续分别维护原 1xxxx/2xxxx 码值，不合并、重编号或创建 4xxxx 错误码枚举"
  - "重名 Controller、Service、配置类和 Spring Bean 使用明确域前缀消除冲突，不启用 Bean 覆盖"
  - "access-service 模块可完成 Spring Context 启动和模块级测试，迁移过程不改变 HTTP 契约"
  - "补充架构边界测试骨架，能够在后续任务中扩展依赖白名单"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-12
---

# T-ACCESS-001 建立 access-service 工程骨架并物理归并源码

## 背景

两个服务必须先进入同一构建与运行容器，后续单库事务、安全上下文和缓存收敛才有稳定落点。

## 范围

- 建立 Maven、启动类、包结构和测试目录。
- 机械迁移两侧源码与资源，修复包名、扫描范围和简单 Bean 冲突。
- 建立 Java 21 编译基线和最小 Context 验证。

## 非目标

- 本任务不改变领域行为，不删除内部同步链路，不完成最终部署切换。

## 完成记录

### 实施摘要（2026-08-12）

**决策确认**（5 项，全部选 A）：
1. Java 21 全局升级（根 pom `java.version` 17→21）
2. `perm-entity` 模块合并进 access-service，删除独立模块
3. 旧模块（admin-service、permission-center）从根 pom 移除并删除目录
4. `TenantContextHolder` 和 `MybatisFlexTenantConfig` 统一到 `access.infrastructure`
5. 4 对重名 Controller 使用域前缀重命名（AdminXxxController / PermXxxController）

**工程变更**：
- 新增 `access-service` Maven 模块，`AccessServiceApplication` 位于 `cn.ac.fage.accessmesh.access`
- admin 源码迁入 `cn.ac.fage.accessmesh.access.admin`（218 main + 23 test = 241 文件；迁入 221 后统一删除旧启动类与 2 个冲突配置类）
- permission 源码迁入 `cn.ac.fage.accessmesh.access.permission`（334 main + 47 test = 381 文件；迁入 319 + perm-entity 18 后统一删除旧启动类与 2 个冲突配置类）
- perm-entity 实体迁入 `cn.ac.fage.accessmesh.access.permission.entity`（18 文件）
- 35 个 mapper XML 迁入并更新 namespace
- 合并配置（`application.yml`，原 bootstrap.yml 迁入）（端口 9100、access_db、Redis DB 0、双域 type-aliases-package）
- 删除 `AdminServiceApplication` 和 `PermissionCenterApplication`，仅保留 `AccessServiceApplication`
- 旧模块 admin-service、permission-center、perm-entity 从根 pom 移除并删除目录

**冲突消除**：
- `TenantContextHolder` → 统一到 `access.infrastructure.TenantContextHolder`
- `MybatisFlexTenantConfig` → 统一到 `access.infrastructure.MybatisFlexTenantConfig`
- `AuthController` → `AdminAuthController` / `PermAuthController`
- `RoleController` → `AdminRoleController` / `PermRoleController`
- `UserController` → `AdminUserController` / `PermUserController`
- `UserRoleController` → `AdminUserRoleController` / `PermUserRoleController`
- 修复 `RoleResourcePermissionMapper.xml` 中 `&` 未转义为 `&amp;` 的 XML 语法问题

**验证结果**：
- `mvn compile`（全 reactor）BUILD SUCCESS — Java 21，555 源文件（218 admin + 334 permission + 2 infrastructure/application + 1 启动类）
- `mvn test-compile` BUILD SUCCESS — 72 测试文件
- 架构边界测试 3/3 通过（admin↔permission 无横向依赖、切片无循环）
- 启动类验证测试 2/2 通过（Context 启动 + 注解配置正确）
- `AdminErrorCode`（1xxxx）与 `PermissionErrorCode`（2xxxx）分别保留原包路径和码值
- HTTP 路由未改变（admin: /auth、/role、/user 等；permission: /api/perm/*）

**设计回写**：
- `access-service-architecture.md` §2/§3/§9 无需修改（实现与设计一致）
- `project-rules.md` §1.2 错误码分段无需修改（AdminErrorCode/PermissionErrorCode 未合并）
- `architecture.md` 旧双服务拓扑标注已存在（待 T-ACCESS-012 全量回写）

### 评审修复（2026-08-12，Request Changes 复审通过）

AI 评审 6 个问题处理结果：

| # | 评审问题 | 结论 | 处理 |
|---|---|---|---|
| 1 | bootstrap.yml 未被加载 | 成立 | 改为 `application.yml` + `spring.config.import=optional:nacos:access-service.yml`（用户决策选 application.yml 方案） |
| 2 | 缺少连接池 | 成立 | 添加 `spring-boot-starter-jdbc`（HikariCP） |
| 3 | Feign 无 LoadBalancer | 成立 | 添加 `spring-cloud-starter-loadbalancer` |
| 4 | 同步服务身份不一致 | **部分成立（结论更正）** | SDK `FeignInternalSyncInterceptor` 通过 `perm.service-code`（默认 `admin-service`）设置 `X-Service-Code`。首轮核实遗漏了该设置方，结论错误。删除合并时自造的 `perm.service-code=access-service` 配置后，拦截器回退默认 `admin-service`，与 `SyncTaskBuilder` Payload 对齐，同步链路保持原行为（正确做法） |
| 5 | 无 Context 启动测试 | 成立 | 重写 `AccessServiceApplicationTest` 为真正的最小 Context 测试（H2 + mock Redis/Redisson/TenantIdProvider），2/2 通过 |
| 6 | Log4j2/Logback 冲突 | 成立 | 排除 `perm-client-spring-boot-starter` 传递的 `spring-boot-starter-logging` |

**额外发现**：修复 `FileServiceImpl` 存量 bug——`@Value("${file.max-size:${DEFAULT_MAX_FILE_SIZE}}")` 的默认值引用 Java 常量不生效，改为编译期常量拼接（默认值 10MB 不变，行为保持）。该 bug 及缺连接池/bootstrap starter 等问题说明**原 admin-service/permission-center 从未能通过本地配置完成 Spring Context 启动**；access-service 是第一个可完整启动的模块。

**复审验证**：`AccessServiceApplicationTest` 2/2 通过（Context 真实启动、配置加载、Bean 无冲突）；access-service 全量 347 测试 0 失败 7 跳过（5 个 Testcontainers Docker 不可用 + 2 个 `@Disabled("Requires OperatorContext mock setup")`）；全 reactor 编译通过。

### 二轮评审修复（2026-08-12）

| # | 评审问题 | 结论 | 处理 |
|---|---|---|---|
| 1 | log4j2 `<Property>` 结构无效 | 成立 | `<Property>` 移入 `<Properties>` 容器，`${LOG_PATTERN}` 正常解析 |
| 2 | Context 测试未隔离 Nacos | 成立 | 测试属性覆盖 `spring.config.import=optional:classpath:/test-nacos-dummy.yml` + `nacos.config.import-check.enabled=false` + discovery/config enabled=false，Config Data 阶段不再连接 Nacos |
| 3 | 定时任务污染后续测试 | 成立 | `accessmesh.sync.scheduler.enabled=false` 关闭 SyncTaskScheduler（5s 查库）；`@MockBean UserRoleOrphanCleanupTask`（无开关）；SyncFullSyncTrigger 默认不启用 |
| 4 | 任务卡结论与代码矛盾 | 成立 | 更正：SDK `FeignInternalSyncInterceptor` 是 `X-Service-Code` 设置方（默认 `admin-service`），删除 `perm.service-code=access-service` 后回退默认值与 Payload 对齐；7 跳过测试 = 5 Docker + 2 OperatorContext mock |

**二轮复审验证**：Context 测试 2/2 通过；全量 347 测试 0 失败 7 跳过（构成不变）；无 log4j2 配置警告、无 Nacos 连接痕迹。

### 三轮评审修复（2026-08-12，无 P0/P1，清理后收口）

| # | 评审问题 | 结论 | 处理 |
|---|---|---|---|
| 1 | Nacos 日志监听器写外部文件（`~/logs/nacos/config.log`） | 成立 | surefire 配置 `nacos.logging.default.config.enabled=false`，测试不再写构建目录外文件 |
| 2 | 完成记录过期数据（bootstrap.yml / 557 / 无警告结论） | 成立 | 更正：配置说明改为 application.yml；源文件计数 555（218+334+2+1）；验证结论补充 Nacos 日志副作用已消除 |

**三轮复审验证**：Context 测试 2/2 通过；删除外部日志文件后重跑，不再产生 `~/logs/nacos/config.log`；全量 347 测试 0 失败 7 跳过（5 Docker + 2 OperatorContext）；全 reactor 编译通过。

### 四轮评审修复（2026-08-12，收口）

| # | 评审问题 | 结论 | 处理 |
|---|---|---|---|
| 1 | Surefire 插件缺少版本（Maven 模型稳定性警告） | 成立 | 根 pom `pluginManagement` 统一管理 `maven-surefire-plugin` 3.5.4（access-service 插件继承），警告消除 |
| 2 | 域级统计仍是旧值 | 成立 | 更正：admin 218（迁入 221 − 旧启动类 1 − 冲突配置类 2）、permission 334（迁入 319 + perm-entity 18 − 旧启动类 1 − 冲突配置类 2）；启动类验证测试 3/3 → 2/2 |

**四轮复审验证**：`mvn help:effective-pom` 无 surefire 版本缺失警告；全量 347 测试 0 失败 7 跳过；全 reactor 编译通过。
