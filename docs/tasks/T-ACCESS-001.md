---
doc_type: task
id: T-ACCESS-001
title: 建立 access-service 工程骨架并物理归并源码
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#2-目标工程与部署单元
  - docs/design/access-service-architecture.md#3-模块边界
  - docs/design/access-service-architecture.md#9-api-sdk-与生态切换
  - docs/design/project-rules.md
depends_on: []
blocks: []
acceptance:
  - "新增单一 Maven 模块 access-service 与 AccessServiceApplication，编译基线统一为 Java 21"
  - "admin 与 permission 源码迁入 cn.ac.fage.accessmesh.access.admin / permission，跨域入口位于 access.application"
  - "AdminErrorCode 与 PermissionErrorCode 随领域迁入后继续分别维护原 1xxxx/2xxxx 码值，不合并、重编号或创建 4xxxx 错误码枚举"
  - "重名 Controller、Service、配置类和 Spring Bean 使用明确域前缀消除冲突，不启用 Bean 覆盖"
  - "access-service 模块可完成 Spring Context 启动和模块级测试，迁移过程不改变 HTTP 契约"
  - "补充架构边界测试骨架，能够在后续任务中扩展依赖白名单"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-12
---

# T-ACCESS-001 建立 access-service 工程骨架并物理归并源码

## 背景

两个服务必须先进入同一构建与运行容器，后续单库事务、安全上下文和缓存收敛才有稳定落点。

## 范围

- 建立 Maven、启动类、包结构和测试目录。
- 机械迁移两侧源码与资源，修复包名、扫描范围和简单 Bean 冲突。
- 建立 Java 21 编译基线和最小 Context 验证。

## 非目标

- 本任务不改变领域行为，不删除内部同步链路，不完成最终部署切换。

## 完成记录

（待实施后填写。）
