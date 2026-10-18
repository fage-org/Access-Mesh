---
doc_type: task
id: T-ACCESS-131
title: 候选版本冻结与隔离部署
status: done
plan: docs/plans/candidate-deployment-acceptance-plan.md
domain: cross-service
design_refs:
  - docs/design/architecture.md
  - docs/design/browser-session-authentication.md
depends_on: []
acceptance:
  - 候选源码、构建工具、正式产物和配置可追溯且不含明文密钥
  - 启动前证明容器、端口、卷、网络、数据库、Redis 与 Nacos 均不复用现有环境
  - 从空数据资源完成平台正式产物部署、健康检查及网页入口访问
  - 记录真实拓扑与未验证边界并回写设计及部署说明
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

当前仓库已有全栈预览编排、[快速开始](../quickstart.md)及[部署基线](../ops/deployment.md)，需要将已验证源码转化为可复现的候选部署。计划制定时 HEAD 为 5a9af9cc5c1e0a48015b26ae3b601b76a0261166；此值仅是起始参考，实际候选必须在实施时冻结，不能预填为验收通过版本。

## 范围

先盘点本机运行资源与可用工具，再从固定提交构建前端与正式服务 JAR/镜像。记录源码提交、未提交差异、JDK/Maven/Node/pnpm/Compose 版本、构建命令、DDL 摘要、产物 SHA-256、镜像 ID/digest、配置模板及脱敏差异。复用现有构建和 Compose 资产，不另建发布平台。

默认使用本机回环访问的独立容器栈。现有 [Compose](../../docker-compose.yml) 含固定 container_name、宿主端口及显式文件卷名 accessmesh-files；仅改 Compose project name 不满足隔离。准备可复现的专用编排或覆盖配置，核对渲染后容器名、端口、全部卷和挂载源、网络、镜像标签、数据库目标、Redis 与 Nacos 实例及服务发现地址。覆盖文件须实际替换原值，不能因列表合并保留现有端口。专用镜像标签不得覆盖现有 dev 标签。

密钥独立生成并存于忽略的运行目录或环境变量；公开证据只保留键名、必要非敏感值及校验结论，原始渲染配置可能含密钥，不原样提交。启动前记录资源归属清单，已有同名资源先调查；不停止、接管或清空既有环境。

## 当前口径

按现有空库链路启动 PG/Redis/Nacos、access-service、Gateway 和前端；此阶段仅初始化独立平台账号，租户在 T-ACCESS-132 开通，example-service 在 T-ACCESS-133 配置凭证后启动。不得直写业务表补租户或绕过 bootstrap。验收正式 fat JAR/镜像的真实启动，特别核对登录会话序列化运行时依赖；开发启动与测试类路径不替代正式包证据。

健康检查、入口路由、公开 Origin 与回环 Cookie 行为按[浏览器认证设计](../design/browser-session-authentication.md)验证。若选择非默认宿主端口，显式检查 Origin/CORS 配置。仅证明实际回环预览拓扑；对外 HTTPS、真实域名或多代理链未经执行不得标通过。

执行日志写入忽略目录 .tmp/candidate-deployment-acceptance/；可提交的摘要、脱敏命令及配置模板在实施时写入 docs/tasks/evidence/candidate-deployment/。本卡完成后把实际验证拓扑与证据入口回写架构的部署说明、浏览器认证的环境边界及部署手册，保留未验证项。

## 验收对照

- [x] 冻结候选来源与产物清单，确认没有不可追溯构建输入。
- [x] 渲染后资源隔离核对通过，启动前后既有环境未被改动。
- [x] 新数据资源完成初始化，正式包健康、网页可访问，平台登录链路可用。
- [x] 配置与证据脱敏，部署命令可复现，实际边界回写完成。

## 非目标 / 遗留

不采购或申请远端环境，不操作现有开发库，不发布版本，不建立新 CI/CD 流水线。恢复环境另由 T-ACCESS-134 创建，不能把本任务的空库启动算作备份恢复通过。

## 完成记录

2026-10-18：候选基线 44e178a1b 加公开前端默认值补丁，正式产物、隔离部署、页面渲染及平台认证 HTTP 链路已实测通过，见[验收记录](evidence/candidate-deployment/verification.md)。干净检出构建暴露的默认环境文件缺失已修复，保留失败与通过证据。后端 mvn -B clean install -DskipTests 成功，前端 Docker 内 pnpm build 成功；这些是构建结果，不计作测试通过。完整回归按计划留在 T-ACCESS-135。原有环境未改动，专用栈保留供后续任务；当前尚未开通租户。
