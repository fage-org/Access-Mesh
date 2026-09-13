---
doc_type: task
id: T-ACCESS-039
title: 缓存目录合一
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§3
  - .claude/skills/dual-layer-cache-framework/SKILL.md（双副本）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-034
blocks: []
acceptance:
  - "AdminCacheCatalog 并入统一缓存目录：合并时目录册不含 OPERATION_CODE 条目（该死条目由 T-ACCESS-034 唯一归属删除，本任务依赖其为前置）；admin:dict-types 条目迁移；全仓单一缓存目录册"
  - "admin:org-visibility 越域 key 归位重命名：新 key 字符串实施时按能力包口径定稿（候选 access:org-visibility），不做兼容双读，全消费点同步；空库无存量键迁移负担（L2 键随 TTL 自然消亡）；Nacos accessmesh.cache.catalogs.* 按旧 code 配置的运维覆盖键随改名同批核对迁移"
  - "PermCacheBoundaryValidator.SNAPSHOT_CATALOGS 六条引用重绑到统一目录册：校验面维持快照链路 6 条、isValid 判据（L2_ONLY 且有效 L2 TTL≤10s）不变；DICT_TYPES / OPERATION_PERMISSIONS_BY_TYPE / ORG_VISIBILITY 等非快照条目不纳入校验（T-ACCESS-008 定案形态维持）；断言六条仍在册且各自 mode/TTL 未变"
  - "dual-layer-cache-framework skill 双副本同步；全量回归绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

两套缓存目录（AdminCacheCatalog / PermCacheCatalog）是平行设施；ORG_VISIBILITY 条目物理在 PermCacheCatalog 而 code 叫 admin:org-visibility——命名越域实证。验收第 3 条要求缓存目录一册。注意：PermCacheBoundaryValidator 的语义是 T-ACCESS-008 授权快照安全边界（仅快照链路 6 条强制 L2_ONLY≤10s），不是「目录合法性」校验——合并后全目录 10 条中 3 条（DICT_TYPES L1_L2、OPERATION_PERMISSIONS_BY_TYPE L1_L2、ORG_VISIBILITY L2_ONLY 60s）按定案必然不满足该判据，纳入校验会导致启动失败。

## 范围

目录合一 + 越域 key 归位 + 边界校验器引用重绑（判据不变）+ skill 双副本同步。

## 当前口径

- 缓存模式（L1/L2 配置、TTL、30 秒授权陈旧预算）零改动——T-ACCESS-008 定案形态维持。
- 滚动发布登记：改名后新旧实例 evictAll 各自命名空间互不可删（RedissonBucketStore 按 catalog code 精确扫描实证）——本项目未正式部署、无新旧实例并存场景，不采双命名空间失效；若未来出现滚动发布需求，届时补双命名空间失效机制（本条为登记性已知边界，docs/pending-problems.md Q-006）。
- depends_on 含 034：OPERATION_CODE 死条目删除唯一归属 034，目录合并在其后进行。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不改任何缓存条目的 mode/TTL。
