---
doc_type: task
id: T-PERM-018
title: 权限令牌统一 sha256(permissions) + 写路径精确失效接口快照
status: proposed
plan: docs/plans/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§5.1-ETag
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
depends_on: [T-PERM-002]
blocks: [T-PERM-017]
acceptance:
  - "令牌生成统一为单一来源：sha256(permissions) 反映权限内容变更（v3.5 §5.1 ETag）"
  - "合并 buildInterfacePermissionVersion（私有 roleIds 指纹）与 buildPermissionVersionKey（domain service 占位）"
  - "授权写路径精确失效受影响的 INTERFACE_SNAPSHOT 缓存（令牌含内容后 key 可定位，或配合 evictAll）"
  - "消除撤权后最长 60min 仍放行的漏洞"
  - "permissionVersion 字段及 buildPermissionVersionKey 占位最终移除（InterfaceSnapshotResp/QueryResourcesResp/QueryScopesResp/PermissionTreeResp）"
  - "mvn test 通过 + v3.5 §5.1/§7.2 回写"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-20
---

# T-PERM-018 权限令牌统一 sha256(permissions) + 写路径失效接口快照

> 来源：T-PERM-001 评审 P1（接口快照陈旧）+ memory 待办 perm-token-unification-todo
> 用户决策（2026-06-20）：P1 陈旧单开任务，本轮不修

## 背景（评审 P1 核实结论）

T-PERM-001 快照模式下，`buildInterfacePermissionVersion`（L640）令牌只含 roleIds 指纹，角色内权限变更（授予/撤销）不改变令牌。INTERFACE_SNAPSHOT 缓存 key = `serviceCode|permissionVersion`（L602），permissionVersion 不变 → key 不变 → permission-center 继续返回旧快照（L1/L2 TTL 10/60min）。

写路径（`PermissionGrantAppServiceImpl` L444/L497 等）afterCommit 只清 `invalidateRoleCacheByRole`（角色权限快照），**不清 INTERFACE_SNAPSHOT**。结果：撤权后最长 60min 仍放行，新授权也可能长期不生效。

## 关键约束（评审核实）

- INTERFACE_SNAPSHOT 缓存 key 不含 userId/roleId，只有 `serviceCode|permissionVersion`
- 令牌不变时**无法精确定位**要清的缓存 key（路径 C 不可行）
- 令牌升级为 sha256(permissions) 后，权限内容变即令牌变 → 新请求 key 不同自然 miss 重建，旧 key 靠 TTL 过期
- 令牌升级仍需配合 evict 消除旧 key 的 TTL 窗口（或接受 TTL 兜底）

## 方案

1. **令牌统一**：合并两套占位实现为单一 `sha256(permissions)` 生成器
   - `PermissionQueryAppServiceImpl.buildInterfacePermissionVersion`（私有）
   - `PermissionVersionDomainService.buildPermissionVersionKey`（domain service 占位）
   - 基于 interface-snapshot 返回的 allowedApis（或引擎权限结果）计算 sha256
2. **写路径失效**：授权写 afterCommit 精确失效受影响的 INTERFACE_SNAPSHOT
   - 令牌含内容后，受影响用户的旧 key 可通过 roleIds/userId 维度定位
   - 或过渡期用 `cacheService.evictAll(INTERFACE_SNAPSHOT, tenantId)` 租户级清（粗粒度但安全）
3. **字段移除**：令牌统一稳定后，移除 `permissionVersion` 字段及 `buildPermissionVersionKey` 占位

## 前置依赖

- **T-PERM-002（PermissionChangeContext ThreadLocal + AppService AOP afterCommit）必须先做**：
  - 当前写路径手写 TransactionSynchronizationManager（违反 P1-B 铁律），T-PERM-002 改为 AOP 统一处理
  - 令牌失效登记通过 PermissionChangeContext.markAffected* 收集影响范围
  - 本任务的写路径失效逻辑应建立在 T-PERM-002 的 AOP 框架上，不再手写同步代码

## 阻塞下游

- **T-PERM-017（条件权限 Gateway 侧重评）依赖本任务**：Gateway 重评条件用的快照若陈旧，重评过期规则仍错

## 设计回写

- `v3.5 §5.1`：ETag 令牌 = sha256(permissions) 落地
- `v3.5 §7.2`：令牌统一 + 写路径失效实现进度

## 关联

- memory: `perm-token-unification-todo.md`
- T-PERM-001 任务文件已标注本待办
