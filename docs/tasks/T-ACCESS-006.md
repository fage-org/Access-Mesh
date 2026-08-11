---
doc_type: task
id: T-ACCESS-006
title: 建立跨域只读查询模型
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#3-模块边界
  - docs/design/org-user-permission-contract.md
  - docs/design/permission-center/api-contract.md
depends_on:
  - T-ACCESS-002
  - T-ACCESS-005
blocks: []
acceptance:
  - "跨管理域与权限域的组合查询集中到 access.application.query，不散落在任一领域 Mapper"
  - "专用 QueryMapper 只包含 SELECT，返回 Projection/DTO，不暴露或修改领域实体"
  - "列表、树和详情查询显式包含 tenant_id 条件，分页总数与结果一致"
  - "组合查询采用 JOIN 或批量查询，测试或静态规则证明不存在循环单条查询"
  - "架构测试仅对白名单 query 包开放跨域表读取，并禁止其执行写 SQL"
  - "相关 HTTP 响应与原契约兼容"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-10
---

# T-ACCESS-006 建立跨域只读查询模型

## 背景

单库可以提高用户、组织和权限组合视图的分页与查询效率，但必须把直接跨域读取限制在明确的只读边界。

## 范围

- 识别并迁移现有跨服务组合查询。
- 建立专用 Projection、QueryMapper 和只读事务。
- 增加租户、分页、N+1 与架构约束测试。

## 完成记录

（待实施后填写。）
