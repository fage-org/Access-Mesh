---
doc_type: task
id: T-PERM-018
title: 缓存下沉——移除 INTERFACE_SNAPSHOT(L2)/permissionVersion，激活 ROLE_PERM_SNAPSHOT engine 读缓存，扩展失效事件 serviceCodes
status: done
plan: docs/plans/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§5.1-ETag
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
  - docs/design/permission-center/api-contract.md#§6.x-interface-snapshot
depends_on: [T-PERM-002]
blocks: [T-PERM-017]
acceptance:
  - "engine forUserView 读路径激活 ROLE_PERM_SNAPSHOT：getBatch 批量查 roleIds，miss 集合 1 SQL（selectValidByRoleIds），putBatch 回填；空权限角色缓存空列表（List.of() 非 null）防穿透"
  - "ROLE_PERM_SNAPSHOT 缓存值为 List<RolePermEntry>（条件评估前、互斥过滤前的原始权限记录）；RolePermEntry 提升为顶层 record permission.vo.RolePermEntry，删除 RolePermSnapshot 外壳与 version 字段"
  - "scopeAll/instance 位掩码查询路径（hasPermission/validate/checkInterface 等）第一阶段不缓存——方案明确标注为第一阶段边界"
  - "PermissionChangeAspect.flush 对 acc.roleIds 追加 evictBatch(ROLE_PERM_SNAPSHOT, roleIds)；roleSnapshotIds 保留现有 evictBatch"
  - "资源软删路径登记：deleteResources 加 @PermissionChange，软删 perm 前查受影响 roleIds（新增 mapper selectRoleIdsByResourceIds）并 markRoles；同时查受影响 serviceCodes 并 markServiceCodes（双重登记，软删前查出）"
  - "API mapping 变更全路径登记：addApiMapping/updateApiMapping/removeApiMappingsByIds + syncInterfaces 加 @PermissionChange，仅 markServiceCodes（perm 未变，不 markRoles）"
  - "失效事件扩展 serviceCodes：PermInvalidateEvent(tenantId, roleIds, userIds, serviceCodes)；PermInvalidationPublisher.publish 增 serviceCodes；PermissionChangeContext.Accumulator 加 serviceCodes 字段 + markServiceCodes 重载"
  - "移除 permission-center 侧 INTERFACE_SNAPSHOT：删 PermCacheCatalog.INTERFACE_SNAPSHOT 条目、permission.vo.InterfaceSnapshot record、interfaceSnapshot 的 cacheIdentifier/get/put/notModified 分支及相关私有方法；Gateway 本地 Caffeine interfaceSnapshotCache 保留不动"
  - "移除 permissionVersion + notModified 连同死代码：删 PermissionVersionDomainService(Impl)、buildInterfacePermissionVersion、所有 buildPermissionVersionKey 调用；DTO 字段移除（InterfaceSnapshotResp.permissionVersion/notModified、InterfaceSnapshotReq.permissionVersion、QueryResourcesResp/QueryScopesResp/PermissionTreeResp.permissionVersion）；Gateway PermissionClient/PermissionFilter 去 permissionVersion 参数与 notModified 分支"
  - "【C10 验收边界】T-PERM-018 仅就位 serviceCodes 事件载荷并发布；Gateway 订阅侧按 tenant+serviceCodes 主动清本地 interfaceSnapshotCache 属 T-PERM-006 范围。T-PERM-018 单独完成后，API mapping/resource/sync 变更的 Gateway 本地陈旧仍靠 Gateway TTL 兜底，直至 T-PERM-006 落地"
  - "mvn test 通过（含全 hit/全 miss/混合 hit-miss/空权限角色缓存/grant-revoke 失效/资源删除失效/条件变更只清 CONDITION_RULES/API mapping 变更发布含 serviceCodes/syncInterfaces 发布/移除令牌后 Gateway 拉取+本地匹配仍工作）"
  - "v3.5 §5.1/§7.2 + api-contract.md §6.x + coding-standards §5 回写"
design_writeback:
  required: true
  status: done
last_updated: 2026-06-20
---

> ✅ 完成于 2026-06-20（3 commit：commit1 事件扩展+engine 激活+资源软删登记+测试；commit2 API mapping/sync 全路径登记+移除 INTERFACE_SNAPSHOT(L2)/令牌/notModified+RolePermEntry 提升；commit3 设计回写+任务验收修订）。
> 验证：permission-center `mvn test` 143 passed / 7 skipped（集成 testcontainers 环境缺 Docker）；gateway 编译通过（无单测源）。memory `perm-token-unification-todo` 待办消除。


# T-PERM-018 缓存下沉——移除 INTERFACE_SNAPSHOT(L2)/permissionVersion，激活 ROLE_PERM_SNAPSHOT engine 读缓存

> 来源：T-PERM-001 评审 P1（接口快照陈旧）+ memory `perm-token-unification-todo`
> 依赖：T-PERM-002（PermissionChangeContext + @PermissionChange AOP afterCommit 框架，已完成）
> 阻塞：T-PERM-017（条件权限 Gateway 侧重评——快照若陈旧则重评过期规则仍错）

## 背景（评审 P1 核实结论）

T-PERM-001 快照模式下，`buildInterfacePermissionVersion` 令牌只含 roleIds 指纹，角色内权限变更（授予/撤销）不改变令牌。INTERFACE_SNAPSHOT 缓存 key = `serviceCode|permissionVersion`，permissionVersion 不变 → key 不变 → permission-center 继续返回旧快照（L1/L2 TTL 10/60min）。写路径（T-PERM-002 前）afterCommit 只清角色权限快照不清 INTERFACE_SNAPSHOT，撤权后最长 60min 仍放行。

经多轮架构讨论（见下方决策历程），方案从"令牌统一为 sha256(permissions)"演化为"缓存下沉"——比维护 permissionVersion/派生快照更干净。

## 决策历程（2026-06-20，多轮 AI 评审）

### 关键核实事实

1. **令牌（permissionVersion）唯一真正作用**是 INTERFACE_SNAPSHOT 缓存 key（`serviceCode|permissionVersion`）。其余用途均为死负载/死分支：
   - 304/notModified 路径：Gateway `PermissionFilter` 恒传 null，永不触发
   - queryResources/queryScopes/queryPermissionTree：令牌仅塞响应体，无缓存用途；前端不读、Gateway 不调
   - Gateway `PermissionClient` 接收后既不存储也不回传
   - frontend 完全不引用 permissionVersion
2. **ROLE_PERM_SNAPSHOT 缓存当前是死的**——全代码库无 get 读、无 put 写，仅角色删除时 evict
3. **engine 已有缓存但不缓存权限记录**：仅缓存 EFFECTIVE_ROLES（userId→角色集）+ OPERATION_PERMISSIONS_BY_TYPE（操作定义）；`role_resource_permission` 权限记录每次查 DB
4. **interfaceSnapshot 唯一消费者是 Gateway**；业务侧鉴权（checkInterface/engine.hasPermission/validate）走 engine 的 scopeAll/instance 位掩码路径，不碰 INTERFACE_SNAPSHOT
5. **forUserView 调用方不止 interfaceSnapshot**——还有 queryResources/queryPermissionTree/PermissionView（排障视图），激活 ROLE_PERM_SNAPSHOT 收益面更大
6. **条件变更洞在派生缓存下存在**：RolePermEntry 只存 conditionId 不存条件内容；缓存已评估的派生结果时条件变更不失效。**缓存 raw perm entries + 每次实时评估条件 → 洞消失**
7. **资源软删致陈旧（评审发现）**：deleteResources 同时软删 role_resource_permission，但未标 @PermissionChange/未 markRoles → ROLE_PERM_SNAPSHOT 含已软删 perm
8. **API mapping 变更影响 Gateway 本地快照**：add/update/removeApiMapping + syncInterfaces 改变 SnapshotAssembler 结果，但无广播 → Gateway 本地陈旧
9. **失效事件模型表达不了 serviceCode 维度**：PermInvalidateEvent 仅 (tenantId, roleIds, userIds)

### 方案演进

- **v1 候选**：令牌统一 sha256(permissions)（任务原验收）→ 三条实现路径，要么令牌无法前置（需先跑 engine）、要么范围过大（激活 ROLE_PERM_SNAPSHOT 读缓存+填充+令牌）、要么不满足字面验收
- **v2**：evictAll 兜底 + 廉价令牌 / 去掉 INTERFACE_SNAPSHOT → 用户倾向"走 engine 缓存"
- **v3 定稿**：**缓存下沉**——移除 INTERFACE_SNAPSHOT(L2) + 激活 ROLE_PERM_SNAPSHOT engine 读缓存 + 移除令牌。融合精确粒度、移除令牌、正确性完整

## 核心顾虑与回应（C1–C12，供评审）

| # | 顾虑 | 回应 |
|---|---|---|
| C1 | evictAll 粒度太粗 | 不用 evictAll。失效用 evictBatch(ROLE_PERM_SNAPSHOT, roleIds)，roleId 级精确 |
| C2 | 令牌=sha256(roleIds) 不反映权限内容 → 陈旧 | 移除令牌 + 移除 INTERFACE_SNAPSHOT(L2)。permission-center L2 无陈旧；Gateway 本地 Caffeine 仍存在，靠其 TTL + 广播（C10 边界） |
| C3 | 方案1b 令牌=sha256(permissions) 有条件变更洞 | 本方案不缓存派生结果；缓存 raw perm entries（条件评估前），条件实时评估，洞消失 |
| C4 | 去掉 INTERFACE_SNAPSHOT 多一次 DB 查询 | ROLE_PERM_SNAPSHOT 兜住；getBatch 批量查，miss 集合 1 SQL，空列表也缓存防穿透 |
| C5 | ROLE_PERM_SNAPSHOT 当前是死的 | 激活：engine forUserView 读路径 getBatch/putBatch |
| C6 | permissionVersion 字段移除涉及 Gateway+前端 | 已核实：前端不引用、Gateway 恒传 null（304 死代码）。连 notModified 一起删，不留死代码 |
| C7 | engine 已有缓存能否替代 | engine 只缓存角色集+操作定义，不缓存权限记录。需激活 ROLE_PERM_SNAPSHOT 补上 |
| C8 | 缓存层级与业务鉴权关系 | interfaceSnapshot 仅 Gateway 消费；业务侧走 engine scopeAll/instance 路径（第一阶段不缓存）。表述修正：仅 forUserView/快照类查询受益 |
| C9 | 资源软删致 ROLE_PERM_SNAPSHOT 陈旧 | deleteResources（软删 perm）加 @PermissionChange + 软删前查受影响 roleIds + markRoles → AOP evictBatch。cleanupOrphanedResources（仅软删 resource 不软删 perm）perm 未变不致陈旧，syncInterfaces 已 markServiceCodes 覆盖 |
| C10 | API mapping 变更无广播 | 扩展 PermInvalidateEvent 加 serviceCodes + markServiceCodes；API mapping/sync 全路径登记。**【边界】T-PERM-018 仅发布 serviceCodes 载荷，Gateway 订阅侧主动清依赖 T-PERM-006** |
| C11 | 事件模型扩展 | PermInvalidateEvent(tenantId, roleIds, userIds, serviceCodes)；Publisher.publish 增 serviceCodes；Accumulator 加 serviceCodes 字段 + markServiceCodes |
| C12 | sync 全路径覆盖 | syncInterfaces + 手工 add/update/removeApiMapping 全部加 @PermissionChange + markServiceCodes(tenantId, serviceCode) |

## 方案

### 1. engine forUserView 激活 ROLE_PERM_SNAPSHOT（批量 API + 空列表缓存）

`PermQueryEngine.queryForUserView`（L873）改造：

```
Map<Long, List<RolePermEntry>> cached = cacheService.getBatch(ROLE_PERM_SNAPSHOT, tenantId, roleIds);
Set<Long> miss = roleIds 中未命中 cached 的;
List<RolePermEntry> allEntries = new ArrayList<>(cached.values 合并);
if (!miss.isEmpty()) {
  List<RoleResourcePermission> dbRows = rolePermMapper.selectValidByRoleIds(tenantId, miss);  // 1 SQL
  Map<Long, List<RolePermEntry>> perRole = dbRows 按 roleId 分组 → entryMapper.toEntry;
  Map<Long, List<RolePermEntry>> toPut = new HashMap<>();
  for (roleId : miss) toPut.put(roleId, perRole.getOrDefault(roleId, List.of()));  // 空列表缓存防穿透
  cacheService.putBatch(ROLE_PERM_SNAPSHOT, tenantId, toPut);
  allEntries.addAll(perRole.values 合并);
}
```

- 缓存值类型：`List<RolePermEntry>`（**条件评估前、互斥过滤前的原始权限记录**）
- `RolePermEntry` 提升为顶层 record `permission.vo.RolePermEntry`，删除 `RolePermSnapshot` 外壳与 version 字段
- 空列表缓存：`put(null)` 会被 CacheService 忽略，故存 `List.of()`（空但非 null）
- 仅 forUserView 走此缓存；scopeAll/instance 位掩码查询路径（queryScopeAll/queryInstance）第一阶段不缓存（调用方多带过滤参数，缓存 key 复杂，留后续）

### 2. AOP flush 修订（PermissionChangeAspect.flush）

```
flush(acc):
  roleIds 非空 → invalidateRoleCacheByRole/Batch（清 EFFECTIVE_ROLES）+ evictBatch(ROLE_PERM_SNAPSHOT, roleIds)
  userIds 非空 → invalidateRoleCacheBatch
  conditionIds 非空 → evictBatch(CONDITION_RULES)
  roleSnapshotIds 非空 → evictBatch(ROLE_PERM_SNAPSHOT)
  publisher.publish(tenantId, roleIds, userIds, acc.serviceCodes())  // 含 serviceCodes
```

- markRoles（grant/revoke/addChild/removeChild/资源删除）→ roleIds → evictBatch ROLE_PERM_SNAPSHOT ✓
- markRoleSnapshots（角色删除）→ roleSnapshotIds → 保留现有 evictBatch ✓
- markUsers → 不涉及 ROLE_PERM_SNAPSHOT ✓
- markConditions → 不涉及（条件实时评估）✓
- markServiceCodes → 不清 permission-center 缓存，仅广播（Gateway 侧清）✓

### 3. 资源软删路径登记（C9）

`ResourceManageAppServiceImpl.deleteResources`（L359）：
- 加 `@PermissionChange`
- 软删 perm 前，新增 mapper `selectRoleIdsByResourceIds(tenantId, allIdsToDelete)` 查受影响 roleIds → `markRoles`
- 同时查受影响 serviceCodes（资源→API mapping→serviceCode）→ `markServiceCodes`（双重登记，软删前查出）
- softDeleteBatch 后 AOP flush 自动 evictBatch ROLE_PERM_SNAPSHOT + 广播 serviceCodes

`cleanupOrphanedResources`（sync 内部）：仅软删 resource 不软删 perm，perm 未变 → 不致 ROLE_PERM_SNAPSHOT 陈旧；resource 删除影响 interfaceSnapshot 构建 → syncInterfaces 已 markServiceCodes 覆盖，不需单独改。

### 4. API mapping 变更全路径登记（C10/C12）

| 方法 | @PermissionChange | mark |
|---|---|---|
| `ResourceManageAppServiceImpl.addApiMapping` | ✓ | markServiceCodes(tenantId, req.serviceCode()) |
| `ResourceManageAppServiceImpl.updateApiMapping` | ✓ | markServiceCodes（查 mapping 的 serviceCode） |
| `ResourceManageAppServiceImpl.removeApiMappingsByIds` | ✓ | markServiceCodes（查受影响 mappings 的 serviceCode 集合） |
| `ServiceSyncAppServiceImpl.syncInterfaces` | ✓ | markServiceCodes(tenantId, req.serviceCode()) |

API mapping 变更**不影响 ROLE_PERM_SNAPSHOT**（perm 记录未变），只影响 Gateway 本地快照（含旧 mapping）。故只 markServiceCodes（触发广播清 Gateway），不 markRoles。AOP flush 对 serviceCodes 不清 permission-center 缓存，仅广播。

### 5. 事件模型扩展（C11）

- `PermInvalidateEvent(Long tenantId, Set<Long> roleIds, Set<Long> userIds, Set<String> serviceCodes)` implements Serializable
- `PermInvalidationPublisher.publish(Long tenantId, Set<Long> roleIds, Set<Long> userIds, Set<String> serviceCodes)`
- `PermissionChangeContext.Accumulator` 加 `Set<String> serviceCodes` 字段 + `markServiceCodes(tenantId, Set<String>)` / `markServiceCodes(tenantId, String)` 重载；`isEmpty()` 纳入 serviceCodes
- **Gateway 订阅器按 tenant+serviceCodes 清本地 interfaceSnapshotCache——属 T-PERM-006 范围（C10 边界）**

### 6. 移除 INTERFACE_SNAPSHOT（permission-center L2 + vo）

- `PermCacheCatalog.INTERFACE_SNAPSHOT` 条目删除
- `PermissionQueryAppServiceImpl.interfaceSnapshot`：去 cacheIdentifier/get/put/notModified，直接 engine 构建 + SnapshotAssembler
- `permission.vo.InterfaceSnapshot` record 删除（仅 L2 缓存用）
- 私有方法 `toApiPermissionEntries`/`cacheInterfaceSnapshot`/`buildInterfaceSnapshotCacheIdentifier`/`buildInterfacePermissionVersion` 删
- **Gateway `interfaceSnapshotCache`（Caffeine）保留不动**

### 7. 移除 permissionVersion + notModified（连死代码一起清）

- `PermissionVersionDomainService`/`Impl` 删
- `PermissionQueryAppServiceImpl` 去依赖与所有 buildPermissionVersionKey 调用
- DTO 字段删：`InterfaceSnapshotResp.permissionVersion`、`InterfaceSnapshotResp.notModified`、`InterfaceSnapshotReq.permissionVersion`、`QueryResourcesResp.permissionVersion`、`QueryScopesResp.permissionVersion`、`PermissionTreeResp.permissionVersion`
- `InterfaceSnapshotResp` 去 notModified 后，interfaceSnapshot 恒返回全量 entries
- Gateway：`PermissionClient.interfaceSnapshot` 去 permissionVersion 参数；`PermissionFilter` 去 null 实参；删 notModified 处理分支
- `RolePermSnapshot` record：外层删，`RolePermEntry` 提升为 `permission.vo.RolePermEntry`

## 测试重点

### commit1（核心，自包含）
- 全 hit / 全 miss / 混合 hit-miss
- 空权限角色缓存（防穿透）
- grant/revoke 后 ROLE_PERM_SNAPSHOT 失效
- 资源删除后 ROLE_PERM_SNAPSHOT 失效（C9 回归）
- 条件变更只影响 CONDITION_RULES，不误清 ROLE_PERM_SNAPSHOT

### commit2
- API mapping 变更触发含 serviceCodes 广播
- syncInterfaces 触发广播
- 移除 permissionVersion 后 Gateway 拉取 + 本地匹配仍工作

## 提交策略

3 commit，**commit1 必须自包含**（批量 API + 空列表缓存 + 资源删除失效 + 完整测试）：
1. 事件扩展 serviceCodes(C11) + engine 激活 ROLE_PERM_SNAPSHOT（getBatch/putBatch/空列表）+ flush evictBatch + deleteResources 登记 + 完整测试
2. API mapping/sync 全路径 @PermissionChange + markServiceCodes(C10/C12) + 移除 INTERFACE_SNAPSHOT(L2)/令牌/notModified + RolePermEntry 提升（permission-center + perm-sdk + gateway）
3. 设计回写 + 任务验收修订

## C10 验收边界（终审定稿）

T-PERM-018 仅就位 serviceCodes 事件载荷并发布；Gateway 订阅侧按 tenant+serviceCodes 主动清本地 interfaceSnapshotCache 属 T-PERM-006 范围。T-PERM-018 单独完成后，API mapping/resource/sync 变更的 Gateway 本地陈旧仍靠 Gateway TTL 兜底，直至 T-PERM-006 落地。

> 决策依据（2026-06-20）：T-PERM-018 已跨 permission-center + perm-sdk + gateway DTO 契约，再并入 Gateway 订阅器会让 T-PERM-006 失去独立价值；事件载荷就位后，T-PERM-006 纯做订阅器很小，可紧随闭环。采纳用户首选建议（写入验收边界，订阅侧留 T-PERM-006）。

## 确认项（终审）

- RolePermEntry 提升顶层 record：✓
- 新增 selectRoleIdsByResourceIds（覆盖资源删除路径）：✓
- scopeAll/queryInstance 第一阶段不缓存：✓
- API mapping 只 markServiceCodes 不 markRoles：✓
- deleteResources 双重登记（markRoles + markServiceCodes，serviceCodes 软删前查出）：✓
- sync 路径只 markServiceCodes：✓
- 命名统一 serviceCodes（修正 v3 笔误 serviceIds）：✓

## 前置依赖

- T-PERM-002（PermissionChangeContext + @PermissionChange AOP afterCommit 框架，已完成）：本任务写路径失效建立在 AOP flush 框架上，不再手写 TransactionSynchronizationManager

## 阻塞下游

- T-PERM-017（条件权限 Gateway 侧重评）：Gateway 重评条件用的快照若陈旧，重评过期规则仍错。本任务消除 permission-center L2 陈旧；Gateway 本地陈旧由 T-PERM-006 闭环

## 设计回写

- `v3.5 §5.1`：令牌移除说明（无 ETag，靠 engine 实时构建 + per-role 缓存精确失效）
- `v3.5 §7.2`：缓存下沉进度 + 边界声明（仅 forUserView 受益；事件载荷扩展 serviceCodes；API mapping/sync 全路径登记；Gateway 本地仍靠 TTL+广播，订阅侧 T-PERM-006）
- `api-contract.md §6.x`：permissionVersion/notModified 字段移除、interface-snapshot 响应简化
- `coding-standards §5`：flush 失效项 + markServiceCodes API + 边界

## 关联

- memory: `perm-token-unification-todo.md`（本任务承载，完成后该待办消除）
- T-PERM-001 任务文件已标注本待办
- T-PERM-006：消费 serviceCodes 载荷的 Gateway 订阅器
