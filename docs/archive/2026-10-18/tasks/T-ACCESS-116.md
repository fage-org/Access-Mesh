---
doc_type: task
id: T-ACCESS-116
title: 标准文件接口与默认授权接通
status: done
plan: docs/archive/2026-10-18/standard-capability-completion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md#file-capability-delivery-status
  - docs/design/access-service-architecture.md#file-bootstrap-target
  - docs/design/tenant-lifecycle.md
  - docs/design/access-service-rebuild-runbook.md
  - docs/ops/deployment.md
depends_on: []
acceptance:
  - 标准初始化登记文件接口及菜单，租户管理员取得已确认的文件授权，无须测试夹具补权
  - 管理员经现有授权入口向普通角色分配分类权限成功，原文件夹门禁与跨租户隔离保持
  - FileResp 真实返回 bizType，字段契约与 DTO 对账同步
  - 新租户开通、固定图检查、真实 Gateway 文件闭环、定向验证及计划完整回归通过
  - 部署与恢复说明区分新库和已初始化库，不自动删除现有数据库或文件卷
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

承接 [Q-078](../../../pending-problems.md#q-078)。既有 FileController/FileAppService 已实现文件能力，但默认准入与管理员授权缺失，直连服务的夹具测试不能证明标准部署可达。

## 范围

在 `BootstrapGraphDefinition` 及其正常种子链补齐文件 API、管理菜单、业务授权；检查新租户模板共用链和固定图预期。通过既有 mapper/entity 映射补充文件分类字段，不引入新分类目录服务。同步契约、初始化转授例外、SDK/前端字段镜像（如有）及部署说明。

## 当前口径

按[固定图文件能力目标](../../../design/access-service-architecture.md#file-bootstrap-target)与[文件交付状态](../../../design/access-service-api-contract.md#file-capability-delivery-status)实施。全部文件分类与可转授范围已由用户确认，不能推广成所有内置权限默认可转授。初始化、字段与真实 Gateway 链路已实现并定向验证，计划统一验收已完成。

## 验收对照

- [x] 标准初始化路由、菜单和默认授权。
- [x] bizType 字段及授权转授闭环。
- [x] 真实 Gateway 正向、越权与跨租户反向验证；固定图幂等/缺行行为。
- [x] 本地双轨核对、计划完整回归及设计/部署回写。

## 非目标 / 遗留

页面由 T-FE-078 承接，两卡共同验收后才收敛 Q-078。沿用单实例磁盘及现有文件校验；不增加公开文件路径、对象存储、自动补权或在线迁移工具。已初始化库处置按既有备份重建规程，实际破坏性环境操作须另行明确授权。

## 部署回归补齐

本任务真实 JAR 验收暴露 `sa-token-redis-jackson` 缺少 optional Java Time 模块，两个服务启动失败。2026-10-18 用户确认本次补齐既有组件的 runtime 依赖，保持会话序列化器和 Redis 数据格式；现已验证正式 JAR 启动、标准租户开通与文件闭环。长期要求回写[部署基线](../../../ops/deployment.md)。

## 验收结果

已完成实现、真实页面/接口验收、计划统一回归及本地双轨核对，详见[统一验收](evidence/standard-capability/verification.md)。
