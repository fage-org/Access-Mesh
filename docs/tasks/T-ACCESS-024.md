---
doc_type: task
id: T-ACCESS-024
title: 时间语义 UTC 统一（TypeHandler/JDBC/JVM）
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/project-rules.md
  - docs/design/access-service-architecture.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "TimestamptzLocalDateTimeTypeHandler 显式按 UTC 转换，不再经 java.sql.Timestamp + JVM 默认时区漂移（TIMESTAMPTZ 读写与 JVM 时区解耦）"
  - "JDBC URL 删除 PostgreSQL 无意义的 serverTimezone 残留（MySQL 语义）；JVM 时区（启动参数/Dockerfile/ compose 环境）、Jackson 序列化时区统一 UTC 决策落地并写入部署说明"
  - "双时区验证：两个不同时区 JVM（如 UTC+8 与 UTC）对同一数据库写入/读取相同 LocalDateTime 结果一致的测试用例（容器轨道）"
  - "止血档明确：不改 97 个 TIMESTAMPTZ 列为 OffsetDateTime、不全面换 Instant（改动量与收益不匹配，登记为未来可选演进）"
  - "全部适用容器门控与单测全绿（时间断言无时区偶发）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-22
---

# T-ACCESS-024 时间语义 UTC 统一

## 背景

TIMESTAMPTZ/JSONB TypeHandler（提交 b9f48bb39 引入）仍经 Timestamp.valueOf()/getTimestamp() 与 JVM 默认时区换算（handler 注释自认）；JDBC URL 残留 MySQL 语义 serverTimezone=Asia/Shanghai（pgjdbc 忽略但误导）；项目规范要求数据库 UTC 但无 user.timezone/Jackson/会话级 UTC 约束闭合，无跨时区测试。TIMESTAMPTZ 语义随部署环境漂移。

## 范围

- TypeHandler 显式 UTC 转换；连接/会话时区固定；JVM 与序列化 UTC 统一；双时区测试。

## 当前口径

- 止血档（UTC 约定闭合）而非全面改型（OffsetDateTime 97 列），与"新增工程化功能必须回答是否阻断 E2E/修复数据损坏风险"约束一致：本任务修复正确性风险，不扩面。

## 非目标 / 遗留

- 不改列类型、不迁移实体字段类型。
- 不做全球化多时区展示（前端展示时区转换另行按需）。
