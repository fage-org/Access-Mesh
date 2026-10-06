---
doc_type: task
id: T-ACCESS-086
title: 备份恢复与运维基线
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/ops/deployment.md（部署基线/备份恢复章节）
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "deployment.md 补 pg_dump 定时备份+恢复章节、忘 bootstrap 密码离线重置规程、JWT/签名密钥轮换 runbook（含 X-Credential-Secret 可重放后果修正）"
  - "凭据姿态统一：PostgreSQL trust→必填密码、Redis 弱默认→必填校验"
  - "compose 修整：restart: unless-stopped 与 nacos 持久化卷；删 log4j2 死 file include 或接通；example 补 actuator+healthcheck 且 gateway/example 的 depends_on 按既有 healthcheck 升级为 service_healthy（docker-compose.yml:142 现为 service_started）；NACOS_SERVER_ADDR 进 .env.example 并接线 compose 三处硬编码 nacos:8848（:81/:117/:150）实际消费该变量"
  - "deployment.md 明示凭证明文经环境变量注入（docker inspect 可见）并给出 secrets 文件替代指引"
  - "runbook 演练：备份→恢复→忘密重置→授权墓碑复活（rebuild-runbook.md:104 规程）各走一遍"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-ACCESS-086 备份恢复与运维基线

## 背景

生产部署基线文档没有任何备份/恢复规程，而文档体系的主要数据操作是销毁性的（升级=重建库）；忘 bootstrap 密码无恢复路径。同族问题：四凭据三种姿态（PG trust 无密码/Redis 弱默认/密钥 fail-fast 不一致）、compose 无 restart 策略、Nacos 无持久化卷（内嵌 derby 容器重建即清空）、log4j2 文件日志死配置、example 无 healthcheck、NACOS_SERVER_ADDR 硬编码、JWT/签名密钥无轮换 runbook。

## 范围

deployment.md/rebuild-runbook 与 compose/.env.example 的运维基线补齐与凭据姿态统一；不改造凭证注入通道（文档明示即可）。

## 当前口径

本任务是固定图加行类任务（T-ACCESS-085/T-FE-065 等）的前置：存量库升级 fail-fast 的处置规程（备份→重建）先于一切固定图变更交付。演练含授权墓碑复活路径（直改库复活软删授权行的规程核验，见 `docs/design/access-service-rebuild-runbook.md:104`，配合 T-PERM-106 种子行锁死后的恢复面）。

## 验收对照

- [x] 备份/恢复/忘密重置/密钥轮换 runbook 四章节在册且演练通过
- [x] 凭据姿态统一（trust/弱默认消除）
- [x] compose 修整落地（含 gateway/example 依赖升 service_healthy 与 NACOS 变量接线消费）
- [x] 凭证明文暴露面文档明示+替代指引
- [x] 演练四路径走通（备份→恢复→忘密重置→墓碑复活）

## 非目标 / 遗留

- 不存在的 MVC 路径被兜底映射为 HTTP 500：[Q-064](../../../pending-problems.md#q-064)，公共错误契约另核对。
- Docker secrets 注入通道改造（文档指引即可）。
- 容量参数基线表（归 T-ACCESS-090）。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
