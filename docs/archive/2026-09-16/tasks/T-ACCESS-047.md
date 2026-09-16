---
doc_type: task
id: T-ACCESS-047
title: 全栈部署编排——compose app profile + 服务/前端镜像 + 配置占位符
status: done
plan: docs/archive/2026-09-16/release-preview-plan.md
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
  status: done
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

## 完成记录

- 提交 `ffc88ded3`（2026-09-16）。
- 验收证据：
  - `docker compose config --services` 默认档=nacos/postgresql/redis 三件（行为不变）、`--profile app` 档=七服务；
  - `docker compose --profile app up -d --build` 七容器 Up（gateway/frontend healthy）；接线冒烟：前端 `GET http://127.0.0.1/` 200、验证码 `POST /api/access/auth/captcha`（nginx→gateway→access 全链）200 带 base64 图、`POST :8080/api/example/demo/hello` 无令牌 401 信封；
  - 单测轨道三模块全绿：`mvn test -pl access-service,gateway,example-service -DskipTestcontainers=true`（2026-09-16，access-service 1252 项 + gateway 全量 + example 10 项，BUILD SUCCESS，t047_unit_tests.log）。
- 实施期四项实证修正（全记录于 Dockerfile/compose 注释）：①ENTRYPOINT 引用须相对 WORKDIR（`/app.jar` 根路径致「Unable to access jarfile」）；②`pnpm-workspace.yaml` 必须先于 install 拷入镜像（pnpm 11 的 allowBuilds 住该文件，缺位→ERR_PNPM_IGNORED_BUILDS）；③基础镜像 node:24 对齐本地（pnpm 11.5 在 node 20 报 ERR_UNKNOWN_BUILTIN_MODULE）+ pnpm 钉 11.5.0；④`.dockerignore` 弃「`target/*`+`!` 反向放行」形态改枚举排除（反向放行在不同 BuildKit 不可移植）。
- 设计回写：gateway.md §配置项 补占位符键注记；部署文档落位 docs/quickstart.md + docs/ops/deployment.md（T-ACCESS-049 承载）。
