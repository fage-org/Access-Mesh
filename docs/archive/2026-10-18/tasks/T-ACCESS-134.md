---
doc_type: task
id: T-ACCESS-134
title: 重启持久化与备份恢复验收
status: done
plan: docs/archive/2026-10-18/candidate-deployment-acceptance-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-rebuild-runbook.md
  - docs/design/architecture.md
  - docs/design/frontend/file-management.md
depends_on:
  - T-ACCESS-133
acceptance:
  - 同一候选产物重启及重建应用容器后租户数据和文件保持
  - 数据库与文件成套备份并在另一套隔离资源实际恢复
  - 恢复应用确实连接恢复库和独立 Redis 且允许与拒绝行为正确
  - 记录恢复耗时与数据核验结果并回写支持边界
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

已有[部署基线](../../../ops/deployment.md)定义数据库和文件卷备份、恢复库验证及不可丢数据边界；本卡以当前候选实际执行，证明已有数据能够在支持形态下恢复。

## 范围

记录 T-ACCESS-132/133 留下的租户、用户、角色授权、服务登记及文件摘要。先以相同产物重启应用，再在保留专用数据卷的前提下重建应用容器，核对登录、业务允许/拒绝、文件下载内容与幂等初始化。通过正常登录取得新会话验证，不把旧会话必须存活加入契约。

按部署手册停止验收环境写入，在一致时点成套备份数据库与文件；明确恢复所需配置、版本与密钥材料的保管位置，证据不得泄露内容。生成备份摘要及样本核验清单，再恢复到另一套专用数据库、文件卷、Redis 和服务发现隔离环境。不得把备份覆盖到原验收库或任何既有开发库。

恢复实例显式覆盖 SPRING_DATASOURCE_URL，核对实际 JDBC 目标及服务发现地址，避免 application.yml 默认 access_db 导致误连源库。恢复使用同一候选应用与匹配数据，不向不同 DDL 灌旧全库 dump 冒充升级；不复制旧会话来绕过登录。恢复端经 Gateway 重验登录、业务允许与拒绝、跨租户拒绝及文件内容摘要。

## 当前口径

恢复目标沿用 T-ACCESS-131 的资源清单隔离原则；同名库/卷存在即调查，不自动 DROP 或 down -v。必要停止与重建仅面向本计划创建、归属已核实的资源。保留原验收环境与备份，收口记录保留位置和后续清理责任，不自动删除运行数据。

记录备份时间点、停写窗口、备份/恢复命令、耗时、数据核验和文件摘要，不将一次测量包装成 RPO/RTO 承诺。现行重建手册、架构及文件设计只回写实际演练支持的恢复范围，部署手册补可复现操作和证据链接。

## 验收对照

- [x] 应用重启与重建后业务数据和文件完整，初始化未覆盖既有身份或授权。
- [x] 一致时点数据库与文件成套备份完成，材料可定位且敏感信息受保护。
- [x] 恢复栈确实使用独立资源，数据与文件核验和 Gateway 行为验证通过。
- [x] 实测耗时、边界与材料保留位置登记，设计和手册回写完成。

## 非目标 / 遗留

不验证跨版本保数据升级，不引入 migration 框架、共享文件存储、灾备自动切换或生产恢复承诺。不得把同版本恢复通过写为旧版本升级通过。

## 完成记录

2026-10-18 使用 T-ACCESS-131 固定镜像完成应用重启、保留数据卷的容器重建，以及停写后的 PG/文件成套备份。恢复到 `am-restore-20261018-44e178a` 独立栈，显式连接 `access_restore`；启动前全部表数据指纹和序列状态匹配，启动后实际 JDBC 会话、独立服务发现、空 Redis 与源旧会话拒绝均有证据。两租户管理员及普通用户重新正常登录后，授权允许/拒绝、跨租户文件拒绝、下载哈希和已删除样本保持通过。

发现同时重启应用可令 nginx 访问 Gateway 旧 IP，容器 healthy 仍返回 502；已按后端就绪后刷新前端的顺序恢复，并在重建及恢复启动中重验，部署手册补入操作顺序与真实 API 入口探测。无产品源码或镜像变更。数据库恢复 2.313 秒，恢复执行至 API 入口可用 68.913 秒（基础设施预先就绪）；仅为本次样本实测，不是生产 RTO。

[验收记录](evidence/candidate-deployment/T-ACCESS-134/verification.md)及[脱敏命令](evidence/candidate-deployment/T-ACCESS-134/commands.md)保存版本、原始响应、文件/数据库摘要、耗时和本地双轨结论。执行 run.py、verify.py、backup.py、restore.py、final-collect.py 形成真实环境验证记录；完整回归仍由 T-ACCESS-135 承载。本地代码轨与文档轨通过，design_refs 与部署手册已回写。

源入口 18080、恢复入口 18090 及两套专用资源保留；受限备份在 `.tmp/candidate-deployment-acceptance/T-ACCESS-134/backup-20261018T123757Z/`。后续清理须明确对象，不自动删除。example 运行期内存报表不持久化，非空 Nacos 配置恢复、跨版本升级和多实例文件存储不属于本次结论。
