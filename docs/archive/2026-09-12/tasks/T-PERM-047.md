---
doc_type: task
id: T-PERM-047
title: 操作定义缓存失效接线——OPERATION_PERMISSIONS_BY_TYPE 写路径 evict（create/update/deleteOperation）
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§5
  - .claude/skills/dual-layer-cache-framework/SKILL.md
depends_on: []
blocks: []
acceptance:
  - "OPERATION_PERMISSIONS_BY_TYPE（L1 60m/L2 120m，普通缓存+跨实例 L1 失效广播）在 createOperation/updateOperation/deleteOperations 写路径后无任何 evict——引擎（PermQueryEngine 位掩码判定）最长 1-2 小时按旧位值/已删操作判定，位值变更后已授权角色语义静默翻转（T-PERM-028 双轨评审 P2 登记，2026-08-29 用户决策立独立任务；旧 id 版同样缺失，非 T-PERM-028 回归）"
  - "按 dual-layer-cache 框架 evictAfterCommit 模式接线：三处写路径事务提交后失效；键粒度需定夺（引擎按 type 维度缓存 identifier，写操作影响该租户该类型集合——逐 type 失效 vs 整租户失效），含 TypeDefinition 联动预置路径的失效"
  - "注意失效广播目录语义：L1_L2 目录失效经 RTopic 广播各实例清本地 L1（框架已内置，写路径只需 evictAfterCommit）；补缓存失效触发表到 implementation.md §5.2"
  - "回归：位值更新后引擎判定即时生效的单测/PgIT（旧实现下失败——TTL 兜底窗口内旧值判定）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-07
---

# T-PERM-047 操作定义缓存失效接线——OPERATION_PERMISSIONS_BY_TYPE 写路径 evict

> 状态：done（2026-09-07 实现收口；2026-08-29 T-PERM-028 双轨评审登记、用户决策立独立任务）
> 依赖：无硬依赖（缓存框架 evictAfterCommit 机制现成，键粒度设计是主要工作）

## 背景

PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE 目录注释承诺「普通缓存 + 跨实例 L1 失效广播」，但 operation-permission 的三个写路径（create/update/delete）从未接线任何 evict——承诺的失效机制只对快照链路目录实现过。T-PERM-028 把位值更新做成管理页主流程后暴露面变大（此前该页无真实后端）。

## 风险定性

位值变更是极低频管理操作，TTL（L1 60m/L2 120m）兜底；窗口内语义翻转属惰性不一致，非越权非损坏。因此登记独立任务而非热修。

## 实现记录（2026-09-07）

**键粒度定夺：逐 type 失效**。缓存 identifier 本身就是 per-type（`op_perm:{resourceType}`，值为该类型全量有效操作 Map），写入只影响单一类型（create/update）或请求命中的类型集合（delete 批量）——逐 type `evictAfterCommit`/`evictBatchAfterCommit` 与键粒度严格对齐；整租户失效需 catalog 级 `evictAll` 走 SCAN（框架语义：恢复场景专用、禁止高频调用），无收益不采用。键构造统一收敛到 `PermCacheCatalog.operationPermissionsByTypeKey(Integer)`（读路径 `PermQueryEngine.resolveBitMasks` 全部四处内联拼接同步替换，写路径禁手拼前缀）。

**接线点（4 处，全部 evictAfterCommit 形态、AppService 事务方法内）**：

| 写路径 | 失效调用 |
| --- | --- |
| `OperationAppServiceImpl.createOperation` | 单键 `op_perm:{resourceType}`（新增操作改变该类型操作集合） |
| `OperationAppServiceImpl.updateOperation` | 单键（位值/继承掩码变更改变覆盖判定输入 effectiveBits） |
| `OperationAppServiceImpl.deleteOperations` | 受影响类型去重后一次 `evictBatchAfterCommit`（已删操作行不得继续参与覆盖判定）；空键/零命中早退不失效 |
| `TypeDefinitionAppServiceImpl.createType`（resource_type 联动预置 CRUD 四操作位） | 单键 `op_perm:{typeValue}`。当前 typeValue 为全量行（含软删）max+1、软删不复用，新值键必为冷键——语义完备性接线（写路径变更集合即失效），不依赖分配策略不变 |

**机制语义**：L1_L2 目录 evict 由框架同步清共享 L2 + 经 RTopic 广播各实例清本地 L1（广播失败 WARN+指标、TTL 兜底）——写路径只调 evictAfterCommit 即可，无手写 TransactionSynchronization（§5 铁律 P1-B）。构造器新增 `CacheService` 依赖（Spring 装配由 `CacheAutoConfiguration` 唯一 bean 提供）。

**回归锁（旧实现下失败已实证）**：

- 行为级 `OperationPermissionCacheEvictionPgIT`（真实 PG+Redis+真实 bean+真实引擎，三场景各用独立资源类型/位段/主体互不串键）：update 撤销继承掩码后引擎判定翻转 allow→deny；delete 覆盖操作后 allow→deny；create 新增覆盖操作后 deny→allow。**证伪运行**：临时摘除 update 路径 evict 后对应场景失败（`Expecting value to be false but was true`——陈旧 Map 按旧掩码放行），恢复后全绿。
- 接线级：`OperationAppServiceImplTest` create/update 失效 verify + delete 批量失效 verify + 空输入不失效 never 锁；`ResourceOperationKeyPgIT` 预置路径失效 verify（键格式 `op_perm:{typeValue}` 与引擎读取面同源锁定）。

**文档回写**：`implementation.md` §5.2 缓存失效触发点补「操作权限定义变更」条目（触发面/失效粒度/兜底窗口）。

**范围外发现（登记不处理）**：implementation.md §5.1 缓存 Key 与 TTL 汇总表仍为 T-ACCESS-008 边界改造前的旧值（EFFECTIVE_ROLES/ROLE_PERM_SNAPSHOT 标 `L1_L2` 5m/30m、CONDITION_RULES/ROLE_MUTEX_RULE 标 `L1_L2` 10m/30m，实际四者均为 `L2_ONLY` ≤10s；TYPE_VALUE/TYPE_CODE 两目录整行缺失；OPERATION_PERMISSIONS_BY_TYPE 行本身正确）——独立文档漂移，建议后续任务统一校表。
