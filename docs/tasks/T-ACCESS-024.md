---
doc_type: task
id: T-ACCESS-024
title: 时间语义 UTC 统一（TypeHandler/JDBC/JVM）
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/project-rules.md
  - docs/design/access-service-architecture.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "TimestamptzLocalDateTimeTypeHandler 显式按 UTC 转换，不再经 java.sql.Timestamp + JVM 默认时区漂移（TIMESTAMPTZ 读写与 JVM 时区解耦）"
  - "JDBC URL 删除 PostgreSQL 无意义的 serverTimezone 残留（MySQL 语义）；JVM 时区经 common UtcTimezoneEnvironmentPostProcessor 代码级强制 UTC（部署零配置）、Jackson 序列化时区统一 UTC 落地并写入部署说明"
  - "双时区验证：同一测试 JVM 在 UTC+8 与 UTC 默认时区两轮（测试内切换默认时区，等效覆盖 handler 与 JVM 时区解耦）对同一数据库写入/读取相同 LocalDateTime 结果一致的测试用例（容器轨道）"
  - "止血档明确：不改 97 个 TIMESTAMPTZ 列为 OffsetDateTime、不全面换 Instant（改动量与收益不匹配，登记为未来可选演进）"
  - "全部适用容器门控与单测全绿（时间断言无时区偶发）"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-25
---

# T-ACCESS-024 时间语义 UTC 统一

## 背景

TIMESTAMPTZ/JSONB TypeHandler（提交 b9f48bb39 引入）仍经 Timestamp.valueOf()/getTimestamp() 与 JVM 默认时区换算（handler 注释自认）；JDBC URL 残留 MySQL 语义 serverTimezone=Asia/Shanghai（pgjdbc 忽略但误导）；项目规范要求数据库 UTC 但无 user.timezone/Jackson/会话级 UTC 约束闭合，无跨时区测试。TIMESTAMPTZ 语义随部署环境漂移。

## 范围

- TypeHandler 显式 UTC 转换；连接/会话时区固定；JVM 与序列化 UTC 统一；双时区测试。

## 当前口径

- 止血档（UTC 约定闭合）而非全面改型（OffsetDateTime 97 列），与"新增工程化功能必须回答是否阻断 E2E/修复数据损坏风险"约束一致：本任务修复正确性风险，不扩面。
- 语义定约：**LocalDateTime 全链路 UTC 墙钟**——读写（TypeHandler 显式换算）、生产（`LocalDateTime.now()` 实测 163 处、51 文件，依赖 JVM 强制 UTC）、序列化（ISO 无偏移=UTC 墙钟）、调度（cron 按 JVM 时区）四点同源；服务器/会话时区不参与语义（显式换算后无消费方，不额外固定会话时区——加了即死配置，与清理 serverTimezone 初衷相悖）。

## 非目标 / 遗留

- 不改列类型、不迁移实体字段类型（OffsetDateTime/Instant 全面改型登记为演进方向，access-service-architecture §11）。
- 不做全球化多时区展示（前端展示时区转换另行按需）。

## 执行记录（2026-08-25）

**机制终态**：

- **TypeHandler**：`TimestamptzLocalDateTimeTypeHandler` 重写为经 pgjdbc 原生 `OffsetDateTime` 双向映射——写入 `parameter.atOffset(UTC)`、读取 `atZoneSameInstant(UTC).toLocalDateTime()`，不再经 `java.sql.Timestamp`（规范 §7.4 禁用，且其按 JVM 默认时区换算是旧漂移源）。全局注册不变（`MybatisFlexTypeHandlerConfig`）。
- **JVM 默认时区强制 UTC（代码级）**：common 新增 `UtcTimezoneEnvironmentPostProcessor`，经 `META-INF/spring.factories` 注册，环境准备阶段 `TimeZone.setDefault(UTC)`、幂等。应用启动、`@SpringBootTest` 容器轨、E2E 子进程同源生效——测试 JVM 不经 main()，部署级 `-Duser.timezone`/`TZ` 方案罩不住该场景，是选代码级的决定性原因；部署侧零配置防配错；无退出开关（JVM 强制 UTC 是全链路语义前提，多时区需求出现时另立任务）。行为面随之统一：日志时间戳、cron 调度时区（§8.1 跨实例同时区约定自动闭合）。
- **JDBC URL**：`serverTimezone=Asia/Shanghai` 残留删除（access `application.yml` + example `bootstrap.yml` 两处）。Nacos 远端若持有旧 URL 覆盖值需同步清理（仅误导不改行为）。
- **Jackson**：全局 ObjectMapper（common `cacheObjectMapper`，经 `@ConditionalOnMissingBean` 兼任 HTTP 序列化）补 `setTimeZone(UTC)` 防御性兜底（现状全仓无 `Date`/`Instant` 字段，no-op）；契约钉死：`LocalDateTime` 序列化 ISO-8601 无偏移字符串、语义=UTC 墙钟，前端展示转换按需另行处理。
- **既有开发数据卷**：切换前由非 UTC JVM（+8 开发机）写入的行读取墙钟整体偏 -8h；开发期标准处置 `docker compose down -v` 重建（与 T-ACCESS-021 runbook 同款），无生产数据不做迁移（README 部署说明已注明）。
- **缺陷同型登记**：双时区 IT 首跑暴露 MyBatis-Flex `insert()` 显式写全列、null 绕过列默认值触发 NOT NULL 违例（system_config 的 config_value/is_system/delete_flag）——与 T-ACCESS-021 揪出的 matchOrder 缺省 500 同型，测试侧显式赋值规避，生产写路径不受影响（均经 DomainService 显式赋值）。

**验证证据（Windows 11 + WSL2 docker-desktop，2026-08-25）**：

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 双时区容器 IT（Asia/Shanghai 与 UTC 轮次对同一 PG 写读） | `mvn -pl access-service test -Dtest=TimestamptzDualTimezonePgIT` | Tests run: 1, Failures: 0（轮内往返一致、跨轮读一致、两轮库内瞬时直读相同且等于墙钟按 UTC 解释） |
| access-service 单测轨 | `mvn -pl access-service test -DskipTestcontainers=true` | Tests run: 736, Failures: 0（基线 729 + handler 换算 5 + EPP 注册/ObjectMapper TZ 断言 2；EPP 注册断言经 SpringFactoriesLoader 直接验证、与宿主时区无关，时区断言在 UTC+8 本机证明实际执行） |
| access-service 容器轨 | `mvn -pl access-service test` | Tests run: 98, Failures: 0（基线 97 + 双时区 IT 1） |
| common 单测 | `mvn -pl common test` | Tests run: 63, Failures: 0（含 EPP 强制/幂等 2 + mapper TZ 1） |
| gateway 单测轨 + E2E | `mvn -pl gateway test` | 93 + 8 全绿（E2E 103.1s 双服务子进程真实启动，EPP 在子进程链路下工作正常） |
| example-service 编译 | `mvn -pl example-service compile` | BUILD SUCCESS（yml 清理无代码影响） |

**design 回写**：project-rules §7.4（时间语义全链路 UTC 四点同源 + 禁 serverTimezone + Jackson 口径 + TypeHandler 落位说明）；access-service-architecture 新增 §16（时间语义 UTC 统一）+ §8.1 cron 时区表述 + §11 演进方向补 OffsetDateTime 改型条目；README 快速开始补部署说明 + 未交付清单剔除本任务；AGENTS.md 核心编码规范补 UTC 语义条目（指向 §7.4）。
