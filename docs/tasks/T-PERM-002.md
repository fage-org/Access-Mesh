---
doc_type: task
id: T-PERM-002
title: PermissionChangeContext ThreadLocal + AppService AOP afterCommit（统一缓存失效与广播发布）
status: in-progress
plan: docs/plans/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
  - docs/design/permission-center-v3.5-design.md#§5.1-ETag
  - .claude/rules/permission-center-coding-standards.md#§5-事务边界
depends_on: [T-PERM-001]
blocks: [T-PERM-004, T-PERM-005, T-PERM-018, T-PERM-006]
acceptance:
  - "新增 PermissionChangeContext（ThreadLocal 累积器）+ @PermissionChange 注解 + PermissionChangeAspect（@Around，proceed 后注册单一 afterCommit sync 统一 flush）"
  - "新增 PermInvalidateEvent record + 发布端（redisson topic `perm:invalidate`）；T-PERM-006 范围收敛为仅 Gateway 订阅器"
  - "消除全部 15 处业务侧手写 TransactionSynchronizationManager（P1-B 铁律达标）"
  - "框架侧（Aspect 内）统一注册 afterCommit 不违反 P1-B（业务方法体内不再手写同步）"
  - "mvn compile + mvn test 通过（无回归）"
  - "v3.5 §7.2 + coding-standards §5 回写"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-20
---

# T-PERM-002 PermissionChangeContext + AppService AOP afterCommit

> 来源：工作单 A（perm-cache-invalidation）A'-2 决策；评审 P1（接口快照陈旧）的前置依赖
> 关联铁律：permission-center-coding-standards §5 P1-B（禁止业务侧手写 TransactionSynchronizationManager）

## 背景

当前写路径（授权/撤销/用户角色变更/条件变更/角色删除/组角色）在 15 处业务方法体内手写 `TransactionSynchronizationManager.registerSynchronization(afterCommit → evict)`，违反 P1-B 铁律。且：

- 各处 afterCommit 逻辑重复、分散，难统一追加 Redis 广播
- `ConditionAppServiceImpl` 外层手写 afterCommit 包裹 `conditionDomainService.evictConditionCache`，而后者内部又调 `cacheService.evictAfterCommit`（再注册一次 sync）——双重注册潜在 bug
- 写路径不清 `INTERFACE_SNAPSHOT`（T-PERM-018 待修），但本任务先建立统一 AOP 框架，为 T-PERM-018 精确失效铺路

## 核实结论（2026-06-20）

### P1-B 违规存量（15 处）

| 文件 | 处数 | afterCommit 调用 | 迁移后 mark |
|---|---|---|---|
| `PermissionGrantAppServiceImpl` | 4（batchGrant L444 / batchRevoke L497 / addChildren L728 / removeChild L779） | `subjectDomainService.invalidateRoleCacheByRole(tenantId, roleId)` | `markRoles` |
| `UserManageAppServiceImpl` | 4（L251 / L400 / L510 / L668） | `subjectDomainService.invalidateRoleCacheBatch(tenantId, userIds)` | `markUsers` |
| `ConditionAppServiceImpl` | 3（L161 / L221 / L277） | `conditionDomainService.evictConditionCache[Batch]` | `markConditions` |
| `GroupRoleAppServiceImpl` | 2（L137 / L193） | `subjectDomainService.invalidateRoleCacheByRole` | `markRoles` |
| `RoleManageAppServiceImpl` | 1（L288 delete） | `cacheService.evictBatch(ROLE_PERM_SNAPSHOT, tenantId, roleIds)` | `markRoleSnapshots` |
| `PermissionGrantDomainServiceImpl` | 死导入 | 仅 import 无调用 | 清理 import |

### 基础设施现状

- `CacheService` 已有 `evictAfterCommit` / `evictBatchAfterCommit` / `evict` / `evictBatch` / `evictAll`
- `subjectDomainService.invalidateRoleCacheByRole/Batch` 内部即 `cacheService.evictBatch(EFFECTIVE_ROLES)`（即时失效，非再注册）
- `conditionDomainService.evictConditionCache` 内部用 `evictAfterCommit`（再注册 sync）→ AOP 改调 `cacheService.evictBatch(CONDITION_RULES)` 即时失效，消除双重注册
- `OperationLogAspect`（@Around + SpEL）作 AOP 风格参考
- 无 `PermissionChangeContext` / `PermInvalidateEvent` / 订阅器
- redisson 3.37.0 已是 permission-center 依赖

## 决策（2026-06-20 用户确认）

1. **AOP 切点**：注解驱动 `@PermissionChange`，标在 AppService 写方法上（与 `@OperationLog` 风格一致，显式可控，不误切只读）
2. **Redis publish 归属**：T-PERM-002 同时做 evict + publish（建 `PermInvalidateEvent` + 发布端）；T-PERM-006 收敛为仅 Gateway 订阅器
3. **迁移节奏**：全量收敛 15 处，分两步提交（commit1 框架+PermissionGrant，commit2 其余 5 文件）

### 实现层选择（已在任务文件登记，非用户决策项）

`PermissionChangeContext.mark*` 由 **AppService 方法体调用**（影响范围 roleId/userIds 在 AppService 编排层解析得出），而非 coding-standard §5 示例字面的 `permissionGrantDomainService.markAffected(...)`。理由：影响范围 id 在 AppService 层已解析，DomainService 签名无需改动，迁移最小化；`PermissionChangeContext` 为静态 ThreadLocal（同 `TenantContextHolder`），DomainService 未来也可直接 mark。P1-B 铁律（业务侧不手写 `TransactionSynchronizationManager`）达标——afterCommit 注册由 Aspect 框架侧统一完成。

## 设计

### PermissionChangeContext（静态 ThreadLocal 累积器）

```
package cn.ac.fage.accessmesh.permission.cache

字段：tenantId / Set<Long> roleIds / userIds / conditionIds / roleSnapshotIds
API：
  bindIfAbsent(): boolean       // AOP 入口调用，返回是否为本调用 owner
  markRoles(tenantId, ids)      // DomainService/AppService 登记受影响角色
  markUsers(tenantId, ids)
  markConditions(tenantId, ids)
  markRoleSnapshots(tenantId, ids)  // ROLE_PERM_SNAPSHOT 直清（角色删除）
  snapshot(): Accumulator       // AOP 读取（可能 null）
  clear()                       // AOP afterCompletion / catch 调用
```

mark 方法在 context 未绑定时 no-op（debug 日志），防止 DomainService 越界 mark 导致 ThreadLocal 泄漏。

### @PermissionChange 注解

标在 AppService 写方法上（与 @OperationLog 并存）。无属性（纯标记），或可选 `publish`（默认 true）。

### PermissionChangeAspect（@Around("@annotation(pc)")）

```
around(pjp, pc):
  owner = PermissionChangeContext.bindIfAbsent()
  try:
    result = pjp.proceed()              // 业务方法体内 markAffected*
    if owner:
      acc = PermissionChangeContext.snapshot()
      if tx active:
        registerSynchronization:
          afterCommit:    flush(acc)    // evict + publish
          afterCompletion: PermissionChangeContext.clear()
      else:
        flush(acc); clear()
    return result
  catch t:
    if owner: PermissionChangeContext.clear()   // 回滚/非事务异常清场
    throw
```

`flush(acc)` 逻辑（全部即时失效，afterCommit 已是提交后，读已提交数据）：
- `acc.roleIds` 非空 → 逐 roleId `subjectDomainService.invalidateRoleCacheByRole(tenantId, roleId)`
- `acc.userIds` 非空 → `subjectDomainService.invalidateRoleCacheBatch(tenantId, userIds)`
- `acc.conditionIds` 非空 → `cacheService.evictBatch(CONDITION_RULES, tenantId, conditionIds)`
- `acc.roleSnapshotIds` 非空 → `cacheService.evictBatch(ROLE_PERM_SNAPSHOT, tenantId, roleSnapshotIds)`
- `redissonClient.getTopic("perm:invalidate").publish(new PermInvalidateEvent(tenantId, roleIds, userIds))`（失败仅 warn，不影响事务）

### PermInvalidateEvent

`record PermInvalidateEvent(Long tenantId, Set<Long> roleIds, Set<Long> userIds)` —— 可序列化，Gateway 订阅后据此 evict 本地 INTERFACE_SNAPSHOT（T-PERM-006 实现）。

## 前置依赖

- T-PERM-001（快照模式已落地，Gateway 缓存模型已就位）

## 阻塞下游

- T-PERM-004 / T-PERM-005（删 increment / 删缓存目录）：本任务清理写路径 afterCommit 后，下游删除更安全
- T-PERM-018（令牌统一 + 写路径精确失效 INTERFACE_SNAPSHOT）：建立在 AOP 框架上，flush 中追加 INTERFACE_SNAPSHOT 失效
- T-PERM-006：范围收敛为仅 Gateway 订阅器（发布端由本任务完成）

## 验收

- 见 frontmatter `acceptance`
- 测试：现有 PermissionGrantAppServiceImplTest 等 mock 测试不应回归（AOP 在纯单测无 Spring 容器时不触发，业务方法直接调 markAffected 为 no-op，afterCommit 由测试不涉及）

## 设计回写

- `v3.5 §7.2`：AOP afterCommit + publish 发布端落地进度
- `coding-standards §5`：标注 T-PERM-002 落地，框架侧统一 afterCommit 注册属允许，业务侧 markAffected 替代手写同步
- `perm-cache-invalidation-plan`：A'-2 进度
- 看板：T-PERM-002 ✅ + 回写 ✓；T-PERM-006 标题/范围更新
