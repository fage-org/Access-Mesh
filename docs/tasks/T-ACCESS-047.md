---
doc_type: task
id: T-ACCESS-047
title: 全栈部署编排——compose app profile + 服务/前端镜像 + 配置占位符
status: in-progress
plan: docs/plans/release-preview-plan.md
domain: access-service
design_refs:
  - docs/design/services/gateway.md#配置项
  - docs/design/access-service-architecture.md#2-服务与模块划分
  - docs/design/access-service-architecture.md#16-时间语义
depends_on: []
blocks: [T-ACCESS-049, T-ACCESS-050]
acceptance:
  - "三服务 application.yml 占位符落地且默认值=现硬编码值（NACOS_SERVER_ADDR/DB_HOST/DB_PORT/REDIS_HOST/REDIS_PORT），本地 dev 零参数行为不变"
  - "三服务瘦 Dockerfile（temurin 21-jre + 宿主机 fat jar）+ 前端 Dockerfile 改造（nginx 同源反代 /api→gateway:8080）"
  - "docker-compose.yml 新增 gateway/access-service/example-service/frontend 四服务挂 profiles:[app]，密钥经 .env 插值，access-service 挂 bootstrap 启用；默认 docker compose up 仍只起基建（头注释改写）"
  - ".env.example 落地（五密钥/密码项+注释，三服务同值约束说明）"
  - "空库全栈拉起：mvn package -DskipTests + cp .env.example .env + docker compose --profile app up -d --build → 五容器 healthy、前端登录成功、example API 经 Gateway 403（未授权态）"
  - "单测轨道回归绿（占位符默认值不改变任何既有测试行为）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-16
---

## 背景

compose 现状仅基建（T-ACCESS-020 定调「仅承诺一键基础设施」），三服务地址硬编码 127.0.0.1、靠手工 `mvn spring-boot:run` 启动；前端生产链路半成品（有 Dockerfile 无 nginx /api 反代，axios 全相对路径强依赖同源代理）。发布预览目标要求外部用户一条命令跑起全栈。

## 范围

- `access-service/gateway/example-service` 三份 application.yml 占位符化（默认值=现值）。
- 三模块根各建 Dockerfile（eclipse-temurin:21-jre + COPY target fat jar）。
- `frontend/Dockerfile` 改造 + 新增 nginx.conf（listen 80；location /api/ 反代 gateway:8080，无 rewrite——外部路径=服务路径）。
- `docker-compose.yml` 增 app profile 四服务（NACOS_SERVER_ADDR=nacos:8848、DB_HOST=postgresql、REDIS_HOST=redis、密钥 .env 插值、bootstrap 启用、depends_on 基建 healthcheck）；头注释改写但默认行为不变。
- 根 `.env.example`。

## 当前口径

- 占位符一律带默认值，本地开发与 e2e（命令行参数覆盖启动）零影响。
- 服务镜像走「宿主机 mvn package + 瘦镜像拷 jar」，不在 Docker 内跑 maven。
- 前端镜像保留两段式 node 构建（外部用户免装 Node）；同源反代后无 CORS 依赖。
- bootstrap 仅单实例启用（既有约束），app profile 单副本。

## 验收对照

见 acceptance 六条；部署验证的完整冒烟清单（含授权后 example 200）在 T-ACCESS-050 执行，本任务验收到「五容器 healthy + 登录 + 未授权 403」。

## 非目标 / 遗留

- 不做 CI 发布流水线/镜像推送；不做 DDL 迁移；多实例拓扑；生产 HTTPS（归 deployment.md 文档面，T-ACCESS-049）。
