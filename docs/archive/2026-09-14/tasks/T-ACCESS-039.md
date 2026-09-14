---
doc_type: task
id: T-ACCESS-039
title: 缓存目录合一
status: done
plan: docs/archive/2026-09-14/access-capability-fusion-plan.md
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
  status: done
last_updated: 2026-09-13
---

## 背景

两套缓存目录（AdminCacheCatalog / PermCacheCatalog）是平行设施；ORG_VISIBILITY 条目物理在 PermCacheCatalog 而 code 叫 admin:org-visibility——命名越域实证。验收第 3 条要求缓存目录一册。注意：PermCacheBoundaryValidator 的语义是 T-ACCESS-008 授权快照安全边界（仅快照链路 6 条强制 L2_ONLY≤10s），不是「目录合法性」校验——合并后全目录 9 条中 3 条（DICT_TYPES L1_L2、OPERATION_PERMISSIONS_BY_TYPE L1_L2、ORG_VISIBILITY L2_ONLY 60s）按定案必然不满足该判据，纳入校验会导致启动失败。

## 范围

目录合一 + 越域 key 归位 + 边界校验器引用重绑（判据不变）+ skill 双副本同步。

## 当前口径

- 缓存模式（L1/L2 配置、TTL、30 秒授权陈旧预算）零改动——T-ACCESS-008 定案形态维持。
- 2026-09-13 实施拍板（AskUserQuestion 两项，registry 同日 T-ACCESS-039 行）：统一目录册类名=`AccessCacheCatalog`（落 infrastructure.cache，AdminCacheCatalog/PermCacheCatalog 两旧册消亡，弃保持 PermCacheCatalog 类名/AccessServiceCacheCatalog）；ORG_VISIBILITY code 归位=`access:org-visibility`（跨能力条目取服务级前缀，弃 perm:/org: 前缀）。PermCacheBoundaryValidator 名称与判据维持（语义=授权快照安全边界，非目录合法性）。
- 滚动发布登记：改名后新旧实例 evictAll 各自命名空间互不可删（RedissonBucketStore 按 catalog code 精确扫描实证）——本项目未正式部署、无新旧实例并存场景，不采双命名空间失效；若未来出现滚动发布需求，届时补双命名空间失效机制（本条为登记性已知边界，docs/pending-problems.md Q-006）。
- depends_on 含 034：OPERATION_CODE 死条目删除唯一归属 034，目录合并在其后进行。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不改任何缓存条目的 mode/TTL。
- 规则文件旧类名残留归 041 承接（032 定案「残留面收窄——规则文档归 040/041」同款处置，2026-09-13 本地双轨代码轨实证）：`.claude/rules/permission-center-coding-standards.md` §5 反例示例仍写 `PermCacheCatalog.ROLE_PERM_SNAPSHOT`——041 重写该文件时清扫面须含旧类名（`PermCacheCatalog`/`AdminCacheCatalog`）而不仅域叙事关键词（041 验收「AdminCacheCatalog 等双轨设施已消亡」前置核对句已含此意）。

## 完成记录（2026-09-13）

- **目录合一**：`infrastructure.cache.AccessCacheCatalog` 单册（git 识别为 PermCacheCatalog 更名扩展），9 条目=原 PermCacheCatalog 8 条 mode/TTL 原样 + DICT_TYPES 自 AdminCacheCatalog 迁入（code `admin:dict-types` 不变）；`operationPermissionsByTypeKey` 键构造随册迁移；AdminCacheCatalog/PermCacheCatalog 两旧册删除。
- **越域键归位**：ORG_VISIBILITY code `admin:org-visibility` → `access:org-visibility`，全消费点走常量引用（仅目录册内 code 字符串一字改）；仓内已核无 `accessmesh.cache.catalogs.*` 覆盖键（application.yml 仅机制注释），Nacos 仓外运维面见 Q-006。
- **消费面重绑**：access-service 27 文件（main 11 + test 16）脚本机械改名（护栏断言 + 逐文件后置校验，旧名零残留）；common 框架 javadoc 示例 4 处（CacheService 3 + CacheCatalogEntry 1）；perm-common `BusinessKeys` javadoc 历史注记改中性措辞（「原靠缓存目录册注释口头约定」）；`DualInstanceContainerTest` 的 T-ACCESS-034 历史注记行保留旧类名原文（dated 出处不改写）。
- **边界校验器重绑**：`PermCacheBoundaryValidator.SNAPSHOT_CATALOGS` 六条改挂 AccessCacheCatalog，isValid 判据（L2_ONLY 且有效 L2 TTL≤10s）与类名零改动；`PermCacheCatalogBoundaryTest` 更名 `AccessCacheCatalogBoundaryTest`（9 用例=原 6 + 新增三条合一回归锁：ORG_VISIBILITY 新 code、DICT_TYPES 迁入形态（code/L1 容量）、全册条目 code 两两不同）。
- **验证链**：`mvn compile test-compile` 零告警；定向重绑单测组 13 类 171/171；单轨 `-DskipTestcontainers=true` 1232/1232（038 基线 1229 + 3 新锁）；全量收口 `mvn test -T 1C`（含 E2E，2026-09-13）BUILD SUCCESS——模块计数与 038 基线一致（perm-common 30 / common 65 / starter 15 / example 10 / gateway 106 / access-service 单测 1232 + 容器 210 / e2e 14，日志 /c/Users/li/AppData/Local/Temp/full_039.log）。
- **文档回写**：capability-structure §3/§8.2/§9 + frontmatter；engine/overview（缓存目录册引用）+ engine/implementation §5.1 表与 §8.3 措辞（last_reviewed 历史链内 2026-09-11 时点注记保留旧册名原文）；pending-problems Q-006 时态（「将改名」→ 已落地）；decision-registry 2026-09-13 拍板行（两项命名 + 弃案）；dual-layer-cache-framework skill 双副本 v3.1.0（示例改挂 AccessCacheCatalog、键格式示例补 access: 前缀条目，cp+diff 验证同步）。
- **背景勘误**：卡内「合并后全目录 10 条」为计数笔误，实际 9 条（原 perm 8 + dict 1），完成记录随批订正。

## 外部评审处置（claude+grok，2026-09-13）

共用提示词（三槽位：评审对象=36b3fecd1 首轮 / 任务相关定案与边界 / 九项专项清单含复核本地双轨处置），双通道并行只读，输出落盘 ext_review_039/。九项专项清单双通道全过（合并零漂移、消费面闭合、机械替换无副作用、判据零改动、三锁旧实现必红、残留全合法、双轨 P3 处置复核、skill 双副本 SHA256 一致、文档回写自洽）。

- **claude P3×1（运维可见面未登记）**：catalog code 同时是 Micrometer `catalog` 标签 series 与日志取值键，ORG_VISIBILITY 改名后这些 series 随之切换，Q-006 原登记只覆盖 Nacos 覆盖键与滚动发布失效两类面。亲核属实（RedissonBucketStore `.tag("catalog", k)` 等实证）——已按建议在 Q-006 现象与证据补登指标/日志面一句（纯文档，不改代码）。
- **grok 存量观察×2**：① engine/implementation.md §5.1 Gateway 快照行 L1 TTL 写「默认 30 秒」，实际 `GatewayCacheCatalog.INTERFACE_SNAPSHOT` l1Ttl=15s（T-ACCESS-008 边界 ≤15s；30s 是 10+5+15 总预算）——存量过时非本批引入，亲核属实，已改 15 秒；② common CacheCatalogEntry javadoc 前缀格式示例未列 `access:`——已补示例值。
- **结论**：claude P0-P2=0、P3=1，grok P0-P3 全零；双通道均为「可定稿、无必改项」。claude 附注（非缺陷）：`mergedCatalog_codesShouldBeDistinct` 为字面 9 项列表，未来新增条目需同步该列表（如需强锁可改反射枚举，未采纳——字面列表即本卡合一形态锁，新增条目时更新列表是预期维护动作）。
