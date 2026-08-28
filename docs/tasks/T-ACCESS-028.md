---
doc_type: task
id: T-ACCESS-028
title: perm-data 空装配模块删除（SDK 面名实对齐）
status: done
plan: docs/plans/product-positioning-landing-plan.md
domain: cross-service
design_refs:
  - docs/design/architecture.md
  - README.md
  - docs/design/services/example-service.md
depends_on: []
blocks: []
acceptance:
  - "perm-data-spring-boot-starter 模块目录删除，perm-sdk 聚合与根 dependencyManagement 移除该模块"
  - "文档口径同步：SDK 面仅 perm-common/perm-client/perm-gateway；数据权限参考实现表述为演进方向未提供模块"
  - "全仓构建通过（根 mvn clean install -DskipTests），无残留引用"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-28
---

# T-ACCESS-028 perm-data 空装配模块删除

> 状态：done（2026-08-28 收口）

## 背景

perm-data-spring-boot-starter 自 2026-06-20 审计 S-011 起登记为「仅空 `PermDataAutoConfiguration`，注解/拦截器/SQL 改写均未落地」；无任何使用方（example 已随 T-API-001 移除依赖，CI/部署无引用）。2026-08-28 定位定案后按演进方向口径收口：删除空模块，将来实现时重新立项。

## 完成记录（2026-08-28）

- 删除模块目录 `perm-sdk/perm-data-spring-boot-starter/`；`perm-sdk/pom.xml` modules 与根 `pom.xml` dependencyManagement 同步移除。
- 文档同步：根 README SDK 行、architecture §4.4 模块树与 Starter 能力表（原表行改演进方向注记）、example-service.md SDK 规划口径；example-service POM 注释与主/测试类 Javadoc 的 perm-data 提及清理。
- 核实无消费方：全仓 pom/yml/CI grep 仅剩历史记录性提及（任务卡 T-API-001、example-service.md 依赖瘦身历史段），保留。
- 回归：根 `mvn clean install -DskipTests` 通过；access-service `mvn test` 751 项 0 失败 0 错误（含容器轨 98，2026-08-28）。
