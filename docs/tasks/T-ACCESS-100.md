---
doc_type: task
id: T-ACCESS-100
title: SDK 公共依赖宿主侵入收口（日志实现/默认时区/全局异常处理/Web 栈传递）
status: proposed
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
  status: pending
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
