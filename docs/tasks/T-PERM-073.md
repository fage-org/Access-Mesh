---
doc_type: task
id: T-PERM-073
title: 观测与对账（explain + Admin UI 改版 + Reconciler）
status: proposed
plan: —（无所属计划；T-PERM-035 实现序列 035C）
domain: permission-center
design_refs:
  - docs/design/dependency-auto-grant.md#§11（explain 契约与门禁）
  - docs/design/dependency-auto-grant.md#§12（Admin UI 定位）
  - docs/design/dependency-auto-grant.md#§13（Reconciliation）
  - docs/design/access-service-api-contract.md（explain 章登记）
  - docs/design/extension-guide.md（接入章节）
  - docs/design/frontend/resource-dependency.md（依赖页改版回写）
  - docs/design/frontend/permission-grant.md（授予页分组展示回写）
depends_on:
  - T-PERM-072
blocks: []
acceptance:
  - "explain 端点（POST）：DEPENDENCY:VIEW 类型级服务端门禁、角色业务键入参禁裸 roleId、凭证端点集不开放；输出 support 链（含多级 parent 链）+ 条件放宽来源标注——035B 二遍追踪已保证 support 完整，无回填负担；explain 新端点入 bootstrap 固定图 API 行（授权行按 DEPENDENCY:VIEW 档位）"
  - "Admin UI 依赖页改版：观察+排障+override 定位（展示列 Source/Target/Owner/MaintainSource/Revision/CompileStatus+Reason/**Cross-eligible（同 owner ✓/override）**/Affected Roles/AUTO_DEP Count + 服务级 manifest 同步状态展示〔ownerServiceCode/revision/lastSyncAt/status〕）；override 表单=ADMIN_UI declaration（豁免同 owner，红字标注）"
  - "授予页/角色权限视图：显式权限与自动权限分组（自动权限只读+来源链入口）"
  - "Reconciler 后台对账：declaration/compiled/materialized/support 四层一致性检查（含抽样 desired vs actual 比对）"
  - "extension-guide 接入章节（starter 使用指南）与契约总册 explain 章登记"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-19
---

# T-PERM-073 观测与对账（035C）

## 背景

T-PERM-035 v1 设计定稿（2026-09-19，用户确认）。管理员从依赖编辑者转为观察+排障+override；物化层的可解释性与对账兜底收口。

## 范围

设计稿 §11–§13：explain、Admin UI 改版、Reconciler、extension-guide/契约登记。

## 非目标 / 遗留

- support_format_version/全量回填/explain 启用门禁：已随过度设计裁剪取消（registry 2026-09-19 过度设计重评③）。

## 验收对照

见 acceptance。
