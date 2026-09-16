---
doc_type: task
id: T-ACCESS-048
title: Q-006 双命名空间失效修复——ORG_VISIBILITY legacy 别名同批 evict + 回归锁
status: in-progress
plan: docs/plans/release-preview-plan.md
domain: access-service
design_refs:
  - .claude/skills/dual-layer-cache-framework/SKILL.md
  - .agents/skills/dual-layer-cache-framework/SKILL.md
depends_on: []
blocks: [T-ACCESS-050]
acceptance:
  - "AccessCacheCatalog 新增 ORG_VISIBILITY_LEGACY 别名条目（code=admin:org-visibility、L2_ONLY、evict-only javadoc 禁 get/put）"
  - "PermissionChangeAspect.flush 同 try 块追加 legacy evictAll（两命名空间同批失效）"
  - "PermCacheBoundaryValidator 启动期对覆盖键含未知 catalog code（至少旧 code）打 WARN"
  - "回归锁三处且旧实现下失败：PermissionChangeAspectTest 两条 flush 路径 verify legacy evict；AccessCacheCatalogBoundaryTest 别名形态锁；DualInstanceCacheInvalidationTest 双前缀键共存新 code evict 后双清"
  - "dual-layer-cache-framework skill 双副本同步别名机制说明"
  - "docs/pending-problems.md Q-006 converted→收敛（关联本卡）"
  - "模块单测轨道全绿；common 模块零改动"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-16
---

## 背景

T-ACCESS-039 将 catalog code `admin:org-visibility` 改名 `access:org-visibility`（不做兼容双读）。滚动发布时新旧实例并存，双方 evictAll 各扫自身 code 命名空间（RedissonBucketStore 按 `{tenantId}:{catalogCode}:*` 前缀），对方命名空间键不失效——org-visibility 缓存最长旧 TTL 60s 越界可见/错误拒绝。登记 Q-006「登记不实施」，发布预览把首次滚动发布拉进视野，转为实施。

## 范围

- 修复三件：legacy 别名条目、写路径同批双 evict、启动期未知覆盖键 WARN。
- 回归锁三处（见 acceptance）。
- skill 双副本说明 + Q-006 收敛登记。

## 当前口径

- 别名条目是 evict-only 兼容面：L2_ONLY（与现条目一致→`DefaultCacheService.broadcastEvictAll` 的 L1_L2 门控使其自动免广播）、TTL 60s 对齐、禁止用于 get/put（javadoc 钉死，AccessCacheCatalogBoundaryTest 锁形态）。
- 旧命名空间键随 TTL 自然消亡；别名在滚动发布窗口结束后可删（届时删除即回归本卡验收）。
- WARN 不 fail-fast：覆盖键丢失非安全事件，静默走默认值才是要暴露的问题。
- 修复完全收敛在 access-service 模块（CacheCatalogEntry 纯值对象可低成本构造别名，common 零改动）。

## 验收对照

见 acceptance 七条。

## 非目标 / 遗留

- 不做旧 Nacos 配置键自动迁移（只告警）；不改 RedissonBucketStore/CacheKeyUtil/广播三件套；不引入通用「别名注册机制」（单条目够用，避免过度设计）。
