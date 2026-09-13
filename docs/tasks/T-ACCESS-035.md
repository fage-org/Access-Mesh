---
doc_type: task
id: T-ACCESS-035
title: 双轨死字段消减（无契约联动四项）
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§5.2
  - docs/design/schema/access-service.sql
depends_on:
  - T-ACCESS-033
blocks: []
acceptance:
  - "abstract_user.extra 的 username 投影写入删除（extra 列本体保留）；全仓无 extraUsername 写入残留"
  - "abstract_role 容器行（ORG/POSITION）sort_order 停投影（列本体保留，功能角色仍用）"
  - "容器行 extra.orgType 停写且键消亡（orgType 语义由 role_type 承载）"
  - "validator 死注入、死 import 清除（现名 MenuServiceImpl / AuthServiceImpl；验收以 033 改名映射表的新类名为准——类名随 033 收敛后按旧名检索会假绿）"
  - "schema 注释同步；回归锁覆盖（投影不再写死字段）；全量回归绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

字段消减评估（2026-09-13 全仓读取面实证，出处见 decision-registry 同日行）：四项零读取方的双轨死数据/死引用。resource_entity.sort_order 因涉对外契约单列 T-ACCESS-036。

## 范围

1. 删 abstract_user.extra 的 username 投影（UserWriteAppServiceImpl.extraUsername 及调用点）。
2. 容器行 sort_order / extra.orgType 停投影（OrgWriteAppServiceImpl.projectOrg 与 LocalProjectionDomainServiceImpl.upsertAdminOrg——容器行不再 setSortOrder、不再传 extraOrgType；列本体与功能角色路径保留）。
3. MenuServiceImpl 死注入字段、AuthServiceImpl 死 import 清除。

## 当前口径

- 「停写/停投影」为最小形态；DDL 列级增删按 T-ACCESS-032 归属清单时的 schema 定稿口径执行（空库可直删，但列本体有其他消费方的必须保留——sort_order 即此类）。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不动 resource_entity.sort_order（T-ACCESS-036）。
- 不动必须留字段（status/enabled 双写、name 三写等，见设计 §4.2）。
