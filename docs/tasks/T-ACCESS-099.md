---
doc_type: task
id: T-ACCESS-099
title: 预览 Compose 文件持久化卷
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/ops/deployment.md
  - docs/design/access-service-architecture.md
depends_on: []
blocks: []
acceptance:
  - "docker-compose.yml 全栈预览档的 access-service 配置固定文件存储路径并挂载专用命名卷；常规 `--profile app up -d --build` 重建容器后既有文件仍可下载"
  - "deployment.md 备份指引标明对应卷；与 access-service-architecture.md 的单实例本地盘边界描述对齐（多实例共享存储仍为非目标）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P1（外部持续审查 2026-10-12 初判 P2，核实后 2026-10-13 升级 P1）。外部持续审查发现：`access-service/src/main/resources/application.yml` 将文件内容写 `${user.home}/accessmesh-files`，`docker-compose.yml` 的 access-service 块（:79-118）无任何 `volumes:`（PostgreSQL 有持久卷）；`FileAppServiceImpl` 写文件与 PG 元数据分离，下载时文件缺失报「物理文件不存在」。单实例上传文件后按常规方式更新 jar 重建容器，可写层内容随旧容器消失——有效文件记录与文件夹授权仍在但内容不可下载。`docs/ops/deployment.md` 已要求容器化挂持久卷，仓库预览编排未落实；已接受边界是多实例本地盘不可共享与人工清理孤儿，不含「单实例常规容器更新丢失有效文件」。

## 范围

docker-compose.yml 预览档；deployment.md 对应说明。

## 当前口径

给 access-service 设固定绝对 `FILE_STORAGE_PATH` 并挂载专用命名卷（或直接挂默认目录）。不引入对象存储、分布式文件锁或独立文件服务。

## 验收对照

见 frontmatter `acceptance`；验收含一次真实容器重建后下载成功的记录。

## 非目标 / 遗留

多实例共享文件存储、自动孤儿清理均维持既有非目标口径。
