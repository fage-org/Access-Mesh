---
doc_type: design
title: 权限中心 v3.5 端到端设计（简化版）
status: adopted
domain: permission-center
last_reviewed: 2026-06-27
---

# AccessMesh 权限中心 v3.5 端到端设计（简化版）

> 状态：v3.5 简化版（2026-06-18 PM 决策回退完成）
> 范围：菜单零权限化 + 单 RPC 原子契约 + tenant 强制 + L1 操作 + L2 数据权限
> 历史版本：[../archive/2026-06/](../archive/2026-06/) — v3.0~v3.3 OBSOLETED；v3.4 / v3.5 初版（含 L3 维度过度设计）通过 git history 追溯
> 关联评审：[../archive/2026-06-17/design-review.md](../archive/2026-06-17/design-review.md)（原评审记录，已归档）
> 演进方向：[permission-center-v3.5.1-evolution.md](permission-center-v3.5.1-evolution.md)（v3.5.1+ 增量，非约束）
> 文档定位：**设计契约**（schema + 原则 + 公式 + 接口签名）。实施细节（审计 / 运营调控 / 迁移 / 兼容性 / 验收 / 监控 / 合规）作为 v3.5.1+ 增量章节。

---

## §0. 版本说明

### 0.1 决策路径回顾

| 阶段 | 日期 | 状态 |
|---|---|---|
| v3.0~v3.3 | 2026-06-17 | OBSOLETED — 双轨 AND + ADMIN_MENU 等历史范式 |
| v3.4 | 2026-06-18 | 提出菜单零权限化方向；引入 `is_entry` + `sensitivity_level` + DB CHECK，PM 判定为过度设计而废弃 |
| v3.5 初版 | 2026-06-18 | 提出三层权限模型（L1/L2/L3）+ `field_descriptor` 表 + 跨业务域告警 + 多个实施章节；R4 评审反馈 5 P0 + 7 P1（agent 推荐，未与 PM 讨论） |
| R5 SIGN_OFF | 2026-06-18 | 三角色一致 SIGN_OFF — 但基于含未讨论项的 v3.5 修订版，**因后续 PM 决策回退而失效** |
| **PM 决策回退** | **2026-06-18** | 本版本：删除 L3 维度 + `entry_eligible` + 跨业务域告警 + `sys_menu_ref` + §6/8/10/11/13/14/15 整章 + §4.2/§5.2 伪代码 |

### 0.2 v3.5 范围

- 菜单零权限化（删除 ADMIN_MENU 资源类型 + sys_menu.perm_code/operations 历史字段）
- 单 RPC 原子契约（`/auth/user-menu`）
- 多租户硬隔离（IR-1.4 沿用）
- **二层权限模型**：L1 操作权限 + L2 数据权限（调用方自决如何应用）

### 0.3 v3.5.1+ 增量

v3.5 范围外的后续增量（L3 字段维度、`sys_menu_ref` 跨业务线复用、审计/运营调控/迁移/兼容性/验收/监控/合规章节）已抽离为独立演进方向文档：[permission-center-v3.5.1-evolution.md](permission-center-v3.5.1-evolution.md)，`status: evolution`，非约束。

### 0.4 v3.0~v3.3 OBSOLETED 关系图

详见 [../archive/2026-06/README.md](../archive/2026-06/README.md) §"P1~P14 范式 OBSOLETED 关系图"。

---

## §1. 设计原则与单源权威声明

### 1.1 IR-1.1 PermQueryEngine 唯一鉴权入口（铁律）

> 所有授权决策必须经过 `PermQueryEngine.hasPermission()` / `query()`。admin / 业务服务 / 前端不得旁路或封装权限语义。仅管理查询（list resources / list logs）可直查 Mapper。

### 1.2 IR-1.2 菜单 = UI 元素，不是权限对象

> sys_menu 表仅承载 UI 路由元数据（display_name / path / icon / parent_id / sort_order / status）+ 关联资源 link（resource_type / resource_code）。任何"菜单专属权限"概念在 v3.5 下不存在。

### 1.3 IR-1.3 业务服务零启动耦合

> 业务服务（如 example-service）启动时**不向 perm-center 推送任何配置**。type_definition / OperationPermission 字典完全由 admin 后台 UI 维护。业务服务运行期通过 `PermQueryEngine.hasPermission()` 调用即可。

### 1.4 IR-1.4 多租户硬隔离

所有跨服务事件 / Redis pub/sub channel / 缓存 key / 数据库索引必须以 `tenant_id` 作为第一前缀或必带参数。perm-sdk HTTP 调用强制 `X-Tenant-Id` 与 body.tenantId 一致校验。

---

## §2. Schema 模型

### 2.1 sys_menu（极简化）

sys_menu 表的权威 DDL 见 [`schema/admin-service.sql`](schema/admin-service.sql) §8（PostgreSQL，v3.5 菜单零权限化最终态）。本节仅描述 v3.5 关心的语义要点，不复制 DDL（避免与权威 schema 双源漂移）。

**v3.5 语义要点**：
- `menu_type`：5 值枚举 `DIR/MENU/EXTERNAL/IFRAME/HIDDEN`（schema 中以 VARCHAR(16) + 注释表达，PostgreSQL 无内联 ENUM）
- `status`：`ENABLED/DISABLED`（schema 中以 SMALLINT 0/1 表达）
- 关联资源 link：`resource_type` / `resource_code` / `source_service`（不参与鉴权决策，仅 §4.1 派生公式用）
- 唯一索引：`uk_sys_menu_tenant_resource (tenant_id, resource_type, resource_code)`、`uk_sys_menu_tenant_path (tenant_id, path)`
- **已废弃字段**（迁移期物理删除）：`perm_code` / `operations` / `primary_operation` / `default_preset` / `visible` / `is_external` / `is_frame` / `is_cache` / `component` / `extra` / `service_code`

> v3.5 不引入 `sys_menu_ref` 表（跨业务线菜单复用作为 v3.5.1+ 增量，见 [演进方向](permission-center-v3.5.1-evolution.md)）。同一资源仅挂一个部门菜单树。

### 2.2 OperationPermission（保持 v3.3 现状）

```sql
-- v3.5 不引入任何扩展字段
-- OperationPermission 表保持 v3.3 schema：
--   code / display_name / effective_bits / resource_type 等
-- v3.4 提案的 is_entry / sensitivity_level 字段不实施
-- v3.5 初版提案的 entry_eligible 字段不引入
```

### 2.3 RoleResourcePermission（维持现状）

当前实体 schema 不变，不引入 JSON `depend_on` 结构：

```java
// RoleResourcePermission 现状（v3.5 维持）
private Long dependOn;          // 父权限引用（单一外键 ID）
private Boolean scopeAll;       // L2 数据权限：全量范围标记
private Boolean canGrant;
private Long conditionId;       // L2 数据权限：自定义条件引用
private String grantSource;
private Long grantDepId;
```

> 注：本字段清单仅列 v3.5 关心的语义字段（数据权限相关），**非完整结构**。完整 DDL 见 `schema/permission-center.sql` `role_resource_permission` 表（含 `abstract_role_id` / `resource_entity_id` / `resource_type` / `granted_bits` 等核心定位字段）。

### 2.4 删除/废弃清单

| 项 | 处理 |
|---|---|
| ADMIN_MENU 资源类型 | type_definition 表行 status=DISABLED |
| sys_menu.perm_code / operations / primary_operation / default_preset | 历史 v3.0~v3.3 字段，物理删除 |
| OperationPermission.is_entry / sensitivity_level | v3.4 提案，未上线 → 不实施 |
| field_descriptor 表 | v3.5 初版提案，未上线 → 不引入 |
| OperationPermission.entry_eligible | v3.5 初版提案 → 不引入 |
| sys_menu_ref 表 | v3.1 引入但 v3.5 不实施 → 留待 v3.5.1+ |

---

## §3. 数据权限契约

### 3.1 二层权限模型概览

```
┌─────────────────────────────────────────────────────────────┐
│ L1 操作权限（OperationPermission）                          │
│   决定：能否对资源类型 R 调用操作 OP？                      │
│   表达：role_resource_permission(role_id, resource_type,    │
│         resource_code, op_id)                                │
│   粒度：资源类型 × 操作                                    │
├─────────────────────────────────────────────────────────────┤
│ L2 数据权限                                                  │
│   决定：调 OP 时能看到哪些数据？                            │
│   表达：内部 role_resource_permission.scopeAll +             │
│         scopes（资源 ID 集合）+ conditionId                  │
│   粒度：实例集合 / 范围 / 自定义条件                        │
│                                                              │
│   ★ 关键铁律：L2 数据权限是统一抽象。perm-center 提供       │
│     数据范围"事实"，不感知调用方使用方式（行裁切 / 字段     │
│     裁切 / 其他维度均由调用方自决）。                       │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 IR-3.1 L1 操作权限完整性

> 菜单可见性派生**只**看 L1（资源类型 × 资源 code 上是否存在 ≥1 个 op）。L2 不参与菜单可见性。

### 3.3 IR-3.2 L2 数据权限契约（调用方自决）

> perm-center 提供数据范围**事实**（对外 `scopeMode` / `items` / `condition`，内部存储对应 `scopeAll` / 资源 ID 集合 / `conditionId`），**不感知调用方使用方式**：
> - 调用方可用于行级 SQL WHERE 注入
> - 调用方可用于字段级 DTO 裁切
> - 调用方可用于其他维度（如导出脱敏 / 报表聚合等）
>
> 与 [permission-center/core-flows.md](permission-center/core-flows.md) line 178 立场一致：**"权限中心只返回数据范围事实，不生成业务 SQL，不解释业务字段"**。

---

## §4. 菜单可见性派生公式

### 4.1 主公式

```
visible(menu, user) :=
    menu.status = ENABLED
  ∧ menu.delete_flag = 0
  ∧ 按 menu_type 分支：
    ┌── DIR              ⇒ ∃ 子节点最终可见 (派生剪枝)
    ├── MENU(业务)       ⇒ ∃ op ∈ user.permissions WHERE
    │                       op.tenant_id    = menu.tenant_id
    │                     ∧ op.resource_type = menu.resource_type
    │                     ∧ op.resource_code = menu.resource_code
    │                       (任何 op 即可)
    ├── MENU(纯展示)     ⇒ resource_type IS NULL → 全员可见
    ├── HIDDEN          ⇒ 派生同 MENU(业务)，不进 menus[]，下发 hiddenRoutes[]
    └── EXTERNAL/IFRAME ⇒ 派生同 MENU(业务)
```

### 4.2 语义说明

- 不引入 `is_entry` / `sensitivity_level` / `entry_eligible` 任何守门概念
- HR 误进风险纯靠"标准授权面板的人工 review"流程承担，不在模型层加技术兜底
- 菜单不在跨业务线复用（v3.5.1+ 增量）— 同一资源仅挂一个部门菜单树

---

## §5. /auth/user-menu 单 RPC 原子契约

> **服务归属**：本接口归 **admin-service**（前端唯一后端聚合入口，见 [architecture.md §1.5](architecture.md) + [services/admin-service.md](services/admin-service.md) §管理端前端聚合约束）。permission-center api-contract.md 不承载此端点（已移除）。admin-service 聚合时调用 permission-center 的 `/api/perm/auth/*` 运行时鉴权接口获取权限事实，组装为 `menus + permissions` 返回前端。
>
> **落地状态**：本接口为 v3.5 规划契约，尚未在 admin-service-api-contract.md 与代码中落地。实施时需同步 admin-service-api-contract.md。

### 5.1 接口签名

```
POST /auth/user-menu
Headers: X-Tenant-Id, X-User-Id, If-None-Match: <ETag>

Response 200:
  ETag: <opaque-hash>          ← 标准 HTTP header，由服务端实现自决（如 sha256(permissions)）
  Body:
  {
    "menus":        [...MenuRouteItem 树],
    "hiddenRoutes": [...扁平],
    "permissions":  [{type, code, op, scopes?, expiresAt?}]
  }

Response 304: 如 If-None-Match 与当前 ETag 匹配
```

> **缓存协商采用标准 HTTP ETag**，不在 body 中定义独立 `snapshotVersion` / `permFingerprint` 字段。失效广播由 Redis pub/sub 主动推送（与 ETag 协商解耦）。
>
> **令牌移除（T-PERM-018，2026-06-20）**：`user-menu` / `interface-snapshot` 等查询接口不再返回 `permissionVersion` / `notModified`，取消 HTTP ETag 协商路径。正确性改由 **缓存下沉** 保证——permission-center 侧移除 `INTERFACE_SNAPSHOT`（L2）与 `permissionVersion`，engine `forUserView` 读路径激活 `ROLE_PERM_SNAPSHOT`（per-role 精确失效，roleId 级 evictBatch）；Gateway 本地 Caffeine 仍存在，靠 Redis 广播（`PermInvalidateEvent`，含 `serviceCodes` 载荷，T-PERM-006 订阅侧）+ TTL 兜底。令牌「唯一真正作用是 INTERFACE_SNAPSHOT 缓存 key」已核实，连带 304/notModified 死代码一并清除。


### 5.2 性能基线

| 指标 | v3.3 | v3.5 |
|---|---|---|
| RPC 数 | 2（batch-check + effective-codes）| **1**（effective-codes only）|
| P99 延迟 | 基线 | **-30~50%** |
| 版本错位窗口 | 双 RPC 间隙存在 | **0**（单事务原子）|

### 5.3 大角色 fallback 边界声明

超大租户（N>2000 或 P>50000）测压验证 admin 侧 hash join 内存占用。必要时引入 perm-center 的 server-side join 端点（不阻塞主流程，作为降级路径）。

> **ETag 计算成本提示**：ETag（如 sha256(permissions)）计算成本随用户 permissions 数量线性增长。即使 304 协商也需服务端先算出当前 ETag 才能比较。建议服务端将 ETag 与 permissions 缓存到同一 Redis key（同失效），304 路径只需 Redis GET，避免每次重算。

---

## §6. 授权 UX

### 6.1 标准 perm-center 授权面板

```
角色管理 → 选"销售经理" → 添加权限
   ↓
标准面板：
   ① 选 ResourceType (EXAMPLE_REPORT)
   ② 选 ResourceCode (sales-001 或 *)
   ③ 选 Operations (VIEW / EXPORT / EDIT)              ← L1
   ④ 选 Scope（全量范围 / 资源 ID 集合 / 自定义条件；对外协议为 scopeMode）  ← L2
   ⑤ 限时（可选）
   ⑥ dry-run 影响预览
   ⑦ 保存 → POST /api/perm/role/grant（roleId 放 JSON Body，禁止路径参数，见 project-rules.md §API 规范）
```

### 6.2 IR-7.2 按菜单视图便捷入口糖

> **操作步数 ≤3 步**（选菜单 → 勾选 op → 保存）。
>
> - **自动定位**：选菜单后自动 lookup `sys_menu.resource_type/resource_code` 预填到标准面板
> - **默认勾选**：业务核心 op（如 VIEW），其他 op 灰显但可选
> - **写入路径**：与标准面板**完全同源**（同写 `role_resource_permission` 表，不绕路）

---

## §7. 性能与一致性

### 7.1 RPC 数量与延迟

沿用 §5.2 基线。

### 7.2 缓存一致性总线

权限缓存失效采用 **Redis pub/sub 主动广播 + TTL 兜底**，无持久化重投：

- permission-center 写操作 afterCommit 阶段 `StringRedisTemplate.convertAndSend("perm:invalidate", <PermInvalidateEvent JSON>)`
- Gateway 订阅该 topic，收到事件后 evict 本地 `interfaceSnapshotCache`；前端缓存仍按前端实施约定走 TTL / polling / BroadcastChannel / SSE 兜底
- 失败兜底：广播丢失不影响事务；TTL（30-60s）自然过期最终一致

> **实现进度（T-PERM-001，2026-06-20）**：Gateway 已落地快照模式——缓存 key 从 `(user,service,method,path)→Boolean` 改为 `(tenantId,subjectTypeCode,userId,serviceCode)→InterfaceSnapshotResp`，鉴权降为本地内存匹配（`InterfaceSnapshotMatcher`，支持 Ant 通配 + `scopeMode=ALL` 覆盖全服务 API），未命中回源拉取 `interface-snapshot`。`InterfaceSnapshotResp`/`InterfaceSnapshotReq` 已迁入 perm-common 供 Gateway 共享。fail-close 过渡期保留（stale-allow 见 T-GW-003）。
>
> **实现进度（T-PERM-002，2026-06-20）**：写路径缓存失效与广播已统一到 AOP 框架——`PermissionChangeContext`（ThreadLocal 累积器）+ `@PermissionChange` 注解 + `PermissionChangeAspect`（@Around，proceed 后注册单一 afterCommit sync 统一 flush：`invalidateRoleCacheByRole/Batch` + `evictBatch(CONDITION_RULES/ROLE_PERM_SNAPSHOT)` + 发布 `PermInvalidateEvent`）。业务方法体内通过 `markRoles/markUsers/markConditions/markRoleSnapshots` 登记影响范围，afterCommit 注册由框架侧统一完成（铁律 P1-B 达标，业务侧 15 处手写 `TransactionSynchronizationManager` 全部消除）。`PermInvalidationPublisher` 通过 Redis topic `perm:invalidate` 发布，失败仅 warn 靠 TTL 兜底。Gateway 订阅器为 T-PERM-006 范围（发布端已就位）。
>
> **实现进度（T-PERM-018，2026-06-20）：缓存下沉**。在 T-PERM-002 的 AOP flush 框架上落地：
> - **事件载荷扩展 serviceCodes**：`PermInvalidateEvent(tenantId, roleIds, userIds, serviceCodes)`；`PermissionChangeContext` 加 `markServiceCodes(tenantId, Set<String>|String)` 重载，`Accumulator.isEmpty()` 纳入 serviceCodes。flush 对 `serviceCodes` 不清 permission-center 缓存，仅广播（Gateway 侧清本地快照，T-PERM-006）。
> - **engine 读路径激活 ROLE_PERM_SNAPSHOT**：`queryForUserView` 走 `getBatch(ROLE_PERM_SNAPSHOT, roleIds)` → miss 集合 1 SQL（`selectValidByRoleIds`）→ `putBatch` 回填；空权限角色缓存 `List.of()`（非 null）防穿透。缓存值为条件评估前、互斥过滤前的原始权限记录（`List<RolePermEntry>`），条件实时评估 → 条件变更洞消失。flush 对 `roleIds` 追加 `evictBatch(ROLE_PERM_SNAPSHOT)`（roleId 级精确，非 evictAll）。`scopeAll`/`instance` 位掩码查询路径第一阶段不缓存（调用方多带过滤参数，缓存 key 复杂，留后续）。
> - **移除 INTERFACE_SNAPSHOT(L2) + permissionVersion/notModified**：permission-center 侧删 `PermCacheCatalog.INTERFACE_SNAPSHOT`、`permission.vo.InterfaceSnapshot`、`PermissionVersionDomainService(Impl)` 及令牌构造/304 死代码；`InterfaceSnapshotResp`/`Req` 去 `permissionVersion`/`notModified`，`QueryResourcesResp`/`QueryScopesResp`/`PermissionTreeResp` 去 `permissionVersion`；Gateway `PermissionClient.interfaceSnapshot` 去令牌参数、`PermissionFilter` 去 notModified 分支。Gateway 本地 Caffeine `interfaceSnapshotCache` 保留。
> - **写路径全路径登记**：资源软删（`deleteResources`）加 `@PermissionChange`，软删 perm 前双重登记 `markRoles`（查受影响 roleIds，新增 `selectRoleIdsByResourceIds`）+ `markServiceCodes`（资源→API mapping→serviceCode）；API mapping 增删改（`addApiMapping`/`updateApiMapping`/`removeApiMappingsByIds`）+ `syncInterfaces` 加 `@PermissionChange` 仅 `markServiceCodes`（perm 未变不 markRoles）。
> **实现进度（T-PERM-006，2026-06-27）：Gateway Redis 广播订阅器已落地**。`PermInvalidateEvent` 契约迁入 `perm-common`，permission-center 发布端与 Gateway 订阅端共享同一事件结构；permission-center 通过 `StringRedisTemplate.convertAndSend("perm:invalidate", json)` 发布 JSON，避免 Redisson 对象 pub/sub 与 Gateway reactive Redis 订阅的编码不一致。Gateway `PermInvalidationSubscriber` 订阅 topic 后调用 `InterfaceSnapshotCacheInvalidator` 清本地 Caffeine：`serviceCodes` 非空按租户+服务清，`userIds` 非空按租户+用户清，仅 `roleIds` 非空时因 Gateway 无本地角色→用户反查能力，按租户级安全清理；广播丢失继续靠 TTL 兜底。
>

> **实现进度（T-PERM-017，2026-06-24）：条件权限 Gateway 侧重评（混合方案）**。修复 T-PERM-001 评审 P1 — "快照模式下条件权限可能误放行"：
> - **`permission_condition` 新增 `gateway_evaluable` 字段**（BOOLEAN，默认 false）：标记规则可下发 Gateway 评估。创建/更新写入门禁仅允许 `IP_WHITELIST` / `IP_BLACKLIST` / `DATE_RANGE` / `TIME_RANGE` 四类置 true（`ConditionEvalUtils.isGatewayPushable` 共用白名单），未来扩展类型（如 `ORG_SCOPE` / `DATA_OWNER`）默认 fail-close 不下发。
> - **`ConditionEvalUtils` 迁入 `perm-common`**（硬切，无 DB 依赖，纯静态函数）：Gateway 与 permission-center 共享同一份评估逻辑。跨进程时钟一致性由 **NTP 同步保证**（中小企业 Gateway 与 permission-center 通常同机房，亚秒漂移 << 业务粒度小时级），不通过 context 传递 `timestamp`。
> - **`ApiPermissionEntry` 内联 `conditionRules` JSON**：`SnapshotAssembler` 仅对 `gateway_evaluable=true` 条目内联（防御性二次过滤），引擎层 `PermQueryEngine.evaluateIfNeeded` 新增"只标记不过滤"模式（`PermQuery.markConditionsOnly`）让条件条目保留进快照。实例级条目按 `(resourceEntityId, conditionId)` 组合展开，同一资源含条件+无条件多条授权各产出独立 entry（修 P1-② 折叠误拒绝）。
> - **`InterfaceSnapshotMatcher` 改三态语义（ALLOW / FALLBACK / DENY） + OR 合并**：含条件 entry 不再直接放行——`conditionRules` 内联则本地用请求 `clientIp` + 本进程时钟重评通过即 ALLOW；缺失（`gateway_evaluable=false`）则标记 FALLBACK 由 `PermissionFilter` 同步调 `/api/perm/auth/check-interface` 实时鉴权（context 仅承载 `clientIp`）。`PermissionFilter` `clientIp` 提取顺序：`X-Forwarded-For` 首段 → `X-Real-IP` → 远端地址。Fallback 失败 fail-close 503（与快照拉取一致）。
> - **DTO 暴露面控制**：内联 `conditionRules` JSON 扩大敏感配置（IP CIDR 白名单）下发面，靠 `gateway_evaluable` 标志最小化下发；未标记的规则永不离开 permission-center。

> **风险声明**：Redis 重启 / 网络分区 / 订阅断线时，权限主动撤销（HR 禁用员工 / 越权 token 紧急回收）退化为纯 TTL 失效，最长 stale 窗口 = `stale-grace-seconds` + TTL。与 PM「分钟级延迟可接受」决策一致。持久化 outbox 重投作为 v3.5.1+ 增量评估项。

> 前端缓存兜底（TTL ≤120s + 60s 心跳 polling + BroadcastChannel 多 tab 同步 + SSE 断连切 polling）属前端实施约定，详细规范见 v3.5.1+。

### 7.3 错误响应协议

错误响应（reasonCode / HTTP 状态码 / 业务状态码）**不在本文档定义**，统一归 [permission-center/api-contract.md](permission-center/api-contract.md) §7 单源。

> v3.5 立场：HTTP 响应码不作为业务状态码。reasonCode 分层、HTTP 与业务状态码分离、业务键解析失败语义等，纳入「项目响应码规范」统一设计（待设计项，见 §9.4）。

---

## §8. SDK 接入指南

### 8.1 OperationPermission 注册

业务团队上线流程：

1. 业务团队 → 治理组（工单 + schema 设计文档）
2. 治理组在 admin 后台"操作位字典"添加 op：
   - 添加 op (`code` / `display_name` / `effective_bits`)
   - **不引入 `is_entry` / `sensitivity_level` / `entry_eligible` 字段**

### 8.2 业务侧零启动耦合（沿用 IR-1.3）

业务服务**不向 perm-center 推送任何配置**。运行期通过 `PermQueryEngine.hasPermission()` 调用即可。

> v3.5 不约束业务侧实施方式 — 调用方拿到 perm-center 的数据权限**事实**（对外 `scopeMode` / `items` / `condition`）后，如何应用（行裁切 / 字段裁切 / 其他维度）由调用方自决。

---

## §9. 评审与决议

### 9.1 R5 SIGN_OFF 状态：已失效

R5 SIGN_OFF（2026-06-18）是基于含 L3 字段级维度 + 6 个实施章节 + 多项未与 PM 讨论项的 v3.5 修订版评审产出。本简化版因 PM 决策大幅回退，R5 SIGN_OFF **不再适用**。

### 9.2 PM 直接决策记录（无 R6 评审）

按 PM 决策（Q6=F3），本版本不进入 R6 评审，由 PM 直接拍板。关键决策点：

| 决策项 | 选择 | 影响章节 |
|---|---|---|
| L3 字段级权限维度 | **删除**（D 决策）| 删 §2.5 / §2.6 / §3.3-3.6 / §6.1 IR-6.1-A / §6.2 L3 channel / §13 L3 用例 / §15 GDPR/PIA/DPIA（全部失效）|
| 权限模型术语 | **二层 L1+L2**（Q1=A1）| §3 数据权限契约简化 |
| RoleResourcePermission schema | **维持现状**（Q2）| 不引入 JSON `depend_on` |
| v3.5 范围 | 菜单零权限化 + 单 RPC 原子 + tenant 强制（Q3=C1）| §0.2 范围声明 |
| 业务侧实施方式 | **不约束**（Q4=D3）| §8 SDK 接入简化 |
| 合规话术 | **stub，重新调研**（Q5=E3）| 原 §15 整章删除（v3.5.1+ 增量）|
| 评审流程 | **PM 直接决策**（Q6=F3）| 本章 |
| `OperationPermission.entry_eligible` | **删除**(项 1=B)| §2.2 / §4.1 / §8.1 |
| 跨业务域授权告警机制 | **删除**(项 2=B)| §6 授权 UX 简化(无⑤步 Field Allow / 无业务域护栏)|
| `sys_menu_ref` 跨业务线复用 | **删除**(本轮=B)| §2.1 / §4 |
| §4.2 / §5.2 算法伪代码 | **删除**(本轮=D1)| 实施细节剥离 |
| §6 审计 / §8 运营调控 / §10 迁移 / §11 兼容 / §13 验收 / §14 监控 / §15 合规 | **整章删除** | 实施细节，与设计相关性不高；留待 v3.5.1+ |
| `snapshotVersion` + `permFingerprint` body 字段 | **删除**(本轮 Q1=A)| §5.1 改用标准 HTTP ETag header；缓存协商与权限领域语义解耦 |
| `CACHE_STALE` reasonCode | **删除**(本轮 Q2=是)| §7.3 删除该行；前端失效由 Redis pub/sub 主动广播触发，不依赖 reasonCode |
| `permission_version` 表与机制 | **删除**(审计 S-001=B，落实 design-review §A'-3)| 删 schema 表 + Entity/Service/Mapper/Controller/DTO + 4 处 increment + `PermCacheCatalog.PERMISSION_VERSION` + `:{permissionVersion}` key 后缀；T-PERM-018 后不再保留 permission-derived ETag/令牌，interface-snapshot 每次实时构建，正确性由 engine `ROLE_PERM_SNAPSHOT` 读缓存 + Redis pub/sub 广播 + TTL 兜底保证；同步 overview/core-flows/implementation/api-contract/coding-standards §5 |
| `perm_outbox` 表表述 | **删除**(审计 S-003=A)| §7.2 改为 Redis pub/sub + TTL 兜底现状声明 + 风险声明 |
| v3.5 §7.3 reasonCode 表 | **删除**(审计 S-013)| 改为指针归 api-contract.md §7 单源；HTTP 码不作业务状态码 |
| 业务键解析失败语义 | **待设计**(审计 S-016)| 纳入「项目响应码规范」统一设计，见 §9.4 |
| Gateway 失效标记与订阅恢复策略 | **待设计**(审计 S-006=D)| design-review §C 加待设计注记 |
| sync_task / sync_metadata 清理策略 | **拒绝**(审计 S-010=D)| 后续改进，目前不重要 |
| 平台超管跨租户操作 | **不支持**(审计 S-017=A)| 超管分别登录每个租户实例；gateway.md / api-contract §3.1 声明 |

### 9.3 v3.5 简化版生效条件

- [x] PM 决策回退完成（2026-06-18）
- [x] v3.5 简化版文档落盘（2026-06-18，本文档）
- [ ] v3.5.1+ 章节启动顺序与依赖由 PM 后续决定（不在本版本范围）

### 9.4 待设计登记（设计文档全面审计 2026-06-20 产出）

以下项经设计文档全面审计后登记为「待设计」，不阻塞 v3.5 简化版生效，需后续专项设计：

| 待设计项 | 来源 | 范围 |
|---|---|---|
| **项目响应码规范** | S-013 + S-016 | reasonCode 分层（HTTP 响应头 vs body.reason）/ HTTP 状态码与业务状态码分离 / 业务键解析失败语义（类型未注册 / 格式错误 / 实例不存在）/ api-contract.md §7 单源化 |
| **Gateway 失效标记与订阅恢复策略** | S-006 | 失效标记数据结构（userId vs tenant+roleId 维度）/ 标记生命周期 / 订阅恢复期事件补偿（last-known-eventId 或强制清空）|
