---
doc_type: task
id: T-ACCESS-066
title: 设计/契约/注释漂移八主题清扫（doc-only）
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/architecture.md（Gateway 白名单叙述）
  - docs/design/access-service-architecture.md（白名单/SDK 身份总览）
  - docs/design/services/gateway.md（现行白名单权威，核对基准）
  - docs/design/schema/access-service.sql（sys_org.parent_id 注释）
  - docs/design/access-service-api-contract.md（旧准入协议标注、用户角色端点指代）
  - docs/design/permission-center-v3.5-design.md §7（旧准入设计标替代）
  - docs/design/org-user-permission-contract.md（/user-role/view 指代）
  - docs/design/frontend/system-config.md（分页/mock 陈旧对比句）
  - access-service/src/main/java（SignatureEnrichFilter 排序注释、resolveAutoGrants TODO——代码注释面，非设计文档）
depends_on: []
blocks: []
acceptance:
  - "八主题逐一订正（清单见 Q-015 表）：Gateway 白名单精确入口范围、过滤器排序注释（-40 先于 -35）、sys_org.parent_id 注释 NULL=0 口径（COMMENT 改动同步迁移脚本，AutoGrantMigrationPgIT 快照比对含注释）、SDK 身份总览补 FeignCredentialInterceptor、用户角色端点指 /user-role/view、v3.5 §7 旧准入协议标 superseded/替代指向契约 §25、resolveAutoGrants TODO 改现实现指认、system-config 页面对比句改现行 usePagedList/真实 API 口径"
  - "纯文档/注释改动零运行时行为变更（diff 核对无代码语义改动）；白名单等安全语义以现行权威与实现核对落笔，不从陈旧文字反推"
  - "需要新契约语义的主题保持关联问题（运行时身份边界 Q-040、清空语义 Q-043），不在文档清扫中定案"
  - "Q-015 全部主题处置完毕（订正或登记维持+依据）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-ACCESS-066 设计/契约/注释漂移八主题清扫（doc-only）

## 背景

承接 [Q-015](../pending-problems.md#q-015)（合并 Q-026/041/042/051/053/054/055）：八个主题的设计、契约与代码注释未同步现行实现——误导入口、身份、状态与执行序理解。本组仅为文档/注释漂移，未把相应运行时缺陷标为已修。

## 范围

八主题集中校正文档/注释及连带引用；schema COMMENT 改动须同步迁移脚本（T-PERM-072 实证：AutoGrantMigrationPgIT 快照比对含注释）。

## 当前口径

doc-only：不动运行时行为；每主题以现行权威（gateway.md、契约 §25、实装代码）为核对基准。

## 非目标 / 遗留

- 运行时身份边界（Q-040）与清空语义（Q-043）的契约定案。
