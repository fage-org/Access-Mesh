---
doc_type: task
id: T-ACCESS-066
title: 设计/契约/注释漂移九主题清扫（doc-only）
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/architecture.md（Gateway 白名单叙述）
  - docs/design/access-service-architecture.md（白名单/SDK 身份总览）
  - docs/design/services/gateway.md（现行白名单权威，核对基准）
  - docs/design/schema/access-service.sql（sys_org.parent_id 注释）
  - docs/design/access-service-api-contract.md（旧准入协议标注、用户角色端点指代）
  - docs/design/permission-center-v3.5-design.md §7（旧准入设计标替代）
  - docs/design/r2-unified-query-and-admission.md 651/726（superseded 历史稿内部「本章为实施期设计权威」残句，2026-10-04 测试精简评审发现并入）
  - docs/design/org-user-permission-contract.md（/user-role/view 指代）
  - docs/design/frontend/system-config.md（分页/mock 陈旧对比句）
  - gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/SignatureEnrichFilter.java（排序注释）
  - access-service/src/main/java/cn/ac/fage/accessmesh/access/grant/service/domain（PermissionGrantDomainService 及实现的自动授权注释）
depends_on: []
blocks: []
acceptance:
  - "九主题逐一订正（清单见 Q-015 表）：Gateway 白名单精确入口范围、过滤器排序注释（-40 先于 -35）、sys_org.parent_id 注释 NULL=0 口径（COMMENT 改动以当前权威 schema 为准，迁移脚本已由 T-ACCESS-073 退出，不再同步历史快照）、SDK 身份总览补 FeignCredentialInterceptor、用户角色端点指 /user-role/view、v3.5 §7 旧准入协议标 superseded/替代指向契约 §25、r2 历史稿 651/726「本章为实施期设计权威」残句删句或改指契约 §25、resolveAutoGrants TODO 改现实现指认、system-config 页面对比句改现行 usePagedList/真实 API 口径"
  - "纯文档/注释改动零运行时行为变更（diff 核对无代码语义改动）；白名单等安全语义以现行权威与实现核对落笔，不从陈旧文字反推"
  - "需要新契约语义的主题保持关联问题（运行时身份边界 Q-040、清空语义 Q-043），不在文档清扫中定案"
  - "Q-015 全部主题处置完毕（订正或登记维持+依据）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-066 设计/契约/注释漂移九主题清扫（doc-only）

## 背景

承接 [Q-015](../../../pending-problems.md#q-015)（合并 Q-026/041/042/051/053/054/055；2026-10-04 测试精简评审追加 R2 历史稿残句主题）：九个主题的设计、契约与代码注释未同步现行实现——误导入口、身份、状态与执行序理解。本组仅为文档/注释漂移，未把相应运行时缺陷标为已修。

## 范围

九主题集中校正文档/注释及连带引用；schema COMMENT 改动只核对当前权威 DDL 与对应文档；旧迁移资产已由 T-ACCESS-073 退出，不再要求同步归档脚本。其余主题范围保持。

## 当前口径

doc-only：不动运行时行为；每主题以现行权威（gateway.md、契约 §25、实装代码）为核对基准。

## 非目标 / 遗留

- 运行时身份边界（Q-040）与清空语义（Q-043）的契约定案。

## 验收对照

- [x] 白名单、过滤器顺序、schema 注释、SDK 身份、管理查询端点逐项核对订正。
- [x] v3.5 旧准入内容替代指向现行契约，R2 历史稿不再声明当前权威。
- [x] 自动授权注释与系统配置/日志页面文档反映当前实现。
- [x] 本卡源码仅注释，DDL 仅 COMMENT；运行时身份与清空目标分别由对应任务承载。
- [x] Q-015 主题全部覆盖，本地代码轨与文档轨无未处理项。

## 完成记录

2026-10-04：定点关键词、引用与实现核对通过；`mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,ResourceOperationKeyPgIT#resourceTreeIncludesDisabledResourcesByDefault` 22 项通过、零跳过，包含原样 DDL COMMENT。完整主题对照见[证据](evidence/T-ACCESS-066/verification.md)。
