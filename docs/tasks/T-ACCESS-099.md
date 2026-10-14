---
doc_type: task
id: T-ACCESS-099
title: 预览 Compose 文件持久化卷
status: done
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
  status: done
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

多实例共享文件存储、自动孤儿清理均维持既有非目标口径。验证过程中发现文件能力三层不可达（网关准入路由未登记恒 403、ADMIN_FILE 授权无种子行、前端无消费面）——与卷持久化正交，登记 Q-078 待产品拍板（补种子/声明休眠/裁剪三选一）。

## 完成记录

- 实现（2026-10-14）：`docker-compose.yml` access-service 块固定 `FILE_STORAGE_PATH=/var/lib/accessmesh/files` 并挂载命名卷 `accessmesh-files`（顶层 volumes 登记）；`docker compose config` 校验通过。
- 真实容器重建验证（2026-10-14，全栈预览档，`.env` 验证值）：空库按重建手册灌权威 DDL（`DROP SCHEMA public CASCADE` + 单事务 40 表）→ 平台登录→开通租户 `taccess099b-214451`→首登改密→服务层直连（SignatureVerifier 四头 + X-Internal-Secret，`FileServiceSecurityPgIT` 同款形态；ADMIN_FILE 授权按同款夹具直插四条单操作位行）上传 `bizType=verify` 得 fileId=1（卷内落盘 `/var/lib/accessmesh/files/verify/2026/10/14/e5b5623f….txt`）→ `docker compose --profile app up -d --build --force-recreate access-service` 重建（容器 ID 更换、可写层全新、20s 恢复 healthy）→ `POST /api/access/file/download {"id":1}` 返回 200、85 字节、与原文件 `diff` 逐字节一致。
- 验证路径说明：上传/下载走服务层直连而非网关——文件路由未入准入投影（Q-078 ①层），经网关恒 403；卷持久性属容器/卷层属性，与入口正交。
- 设计回写（last_reviewed 已 bump）：deployment.md §部署前置（FILE_STORAGE_PATH/命名卷/重建语义/`down -v` 连带删除警示）与 §8 备份指引（卷对应关系与 tar 备份形态示例）；access-service-architecture.md §15 新增「容器化持久化」条（单实例容器重建不再丢文件；单实例本地盘边界与多实例共享非目标不变）。
- 双轨评审：代码轨=compose 单文件 diff 复核（挂载路径与 env 一致、卷名与顶层登记一致、健康检查/端口无回归）；文档轨=deployment/architecture/契约总册对文件存储描述交叉核对无冲突；新发现 Q-078 已按登记规则落 Q-078 条目。
