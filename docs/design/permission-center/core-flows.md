---
doc_type: design
title: Permission Center 核心流程链路
status: adopted
domain: permission-center
last_reviewed: 2026-08-22   # 2026-08-22 归并收口回写；此前：2026-08-08 复审（20041/20042/20043 主/子分类、旧端点终态、SubPermissionPolicy）
---

# Permission Center 核心流程链路

> 本文档把权限管理的核心场景串成接口调用链路，用于确认 API 契约、产品目标和实现方向是否一致。接口契约以 `api-contract.md` 为准。文中「permission-center / 权限中心」指 access-service 的 permission 域、「admin-service / admin」指同服务管理域（术语注记见 `overview.md`，T-ACCESS-012）。

## 1. 全局约定

所有场景都遵守以下约定：

| 约定        | 说明                                                                                                                     |
| ----------- | ------------------------------------------------------------------------------------------------------------------------ |
| 租户来源    | 只从可信 `X-Tenant-Id` 读取，请求体不包含 `tenantId`；外部伪造 Header 必须由 Gateway 清洗                                |
| 操作者来源  | 管理类写操作从 Token/SecurityContext 读取操作者                                                                          |
| 接口方式    | 全部 `POST + application/json`                                                                                           |
| 对外标识    | 运行时接口使用 `subjectTypeCode + subjectExternalId`、`resourceTypeCode + resourceCode + codeType`、`operationCode`      |
| 内部明细 ID | 仅用于已返回记录的更新、删除、子权限挂载，如 `permissionId`、`ids`                                                       |
| 响应结构    | `data` 必须是对象，列表使用 `data.items`                                                                                 |
| 事实来源    | `abstract_user`、`abstract_role`、`resource_entity`、`operation_permission`、`role_resource_permission` 等原表是事实来源 |
| 缓存失效    | 权限关系、角色、资源、条件、依赖变更后通过 Redis pub/sub 主动广播失效事件（`PermInvalidateEvent`）+ TTL 兜底；**已删除 `permission_version` 机制**（2026-06-20 审计 S-001/S-018）|

## 2. 总体主链路

```mermaid
flowchart LR
    A["初始化类型和业务域"] --> B["同步用户/服务/资源"]
    B --> C["创建角色与用户-角色关系"]
    C --> D["配置角色-资源-操作权限"]
    D --> E["配置子权限/条件/依赖/冲突规则"]
    E --> F["Gateway 或 SDK 发起鉴权"]
    F --> G["权限中心解析主体、角色、资源、操作和条件"]
    G --> H["返回允许/拒绝、数据范围和命中信息"]
    H --> I["权限视图和审计日志用于排查"]
```

## 3. 场景一：租户初始化与权限模型准备

目标：为一个租户建立基本类型、业务域、操作权限和域规则。

| 步骤 | 接口                                         | 关键入参                                     | 结果                                   |
| ---- | -------------------------------------------- | -------------------------------------------- | -------------------------------------- |
| 1    | `POST /api/perm/type-definition/create`      | `typeKey + typeCode + typeValue`             | 建立用户、角色、资源类型               |
| 2    | `POST /api/perm/biz-domain/create`           | `code=admin`                                 | 建立业务域                             |
| 3    | `POST /api/perm/operation-permission/create` | `resourceTypeCode + operationCode`           | 建立 VIEW/EDIT/ACCESS/DATA_READ 等操作 |
| 4    | `POST /api/perm/domain-config/save`          | `configType=SCOPE/RELATION/BINDING/SUB_PERM` | 约束域内允许的角色、资源、操作和子权限 |
| 5    | `POST /api/perm/system-config/save`          | 租户级配置                                   | 保存角色唯一性、默认策略等配置         |

关键逻辑：

- 创建 `resource_type` 时可以自动预置 CRUD 或 ACCESS 操作，具体以实现配置为准。
- 对外使用 `typeCode`，内部存储和计算使用 `typeValue`；服务端通过 `type_definition` 缓存完成解析。
- `domainCode` 是管理分区的命名空间标识：管理查询经 `DomainClassifyService` 按 ALL / GLOBAL_PLUS / DOMAIN_ONLY 三种模式分类过滤；查询管线不做按域的对象过滤（仅分类过滤资源类型）；角色/资源实体不内嵌域列，`domainCode` 不参与对象定位。（ P2-2 同步，旧"传域查域+全局"语义废弃）
- `SUB_PERM` 决定哪些资源类型可以作为某类父资源的子权限。
- 这些配置是后续授权校验的基础，不直接给用户产生权限。

## 4. 场景二：接入服务并同步接口资源

目标：让业务服务把自己的接口注册到权限中心，供 Gateway 鉴权使用。

| 步骤 | 接口                                       | 关键入参                          | 结果               |
| ---- | ------------------------------------------ | --------------------------------- | ------------------ |
| 1    | `POST /api/perm/service-config/save`       | `serviceCode + basePath + name`   | 注册或更新服务     |
| 2    | `POST /api/perm/service-config/sync`       | `syncMode=FULL + groups[].apis[]` | 全量同步服务接口   |
| 3    | `POST /api/perm/service-config/apis`       | `serviceCode`                     | 查看服务接口资源树 |
| 4    | `POST /api/perm/resource-api-mapping/list` | `serviceCode` 或 `resourceId`     | 查看接口映射       |

关键逻辑：

- 首期只支持 FULL 全量同步，本次上报内容就是该 `serviceCode` 的完整事实。
- 权限中心用 `basePath + api.path` 生成 Gateway 原始路径并写入 `resource_api_mapping.path_pattern`。
- 新接口自动创建 API 类型 `resource_entity` 和 `resource_api_mapping`。
- 自动创建的 API 资源必须标记 `ownerServiceCode=serviceCode`、`maintainSource=SERVICE_SYNC` 和稳定 `syncKey`。
- 上报中缺失的旧接口会被软删除映射；若资源是同一 `ownerServiceCode + maintainSource=SERVICE_SYNC` 下自动创建的 API 资源，也可同步软删除。
- FULL diff 不得删除人工维护或其他维护来源的资源。
- Gateway 鉴权使用客户端原始请求路径匹配，不使用后端 StripPrefix 后路径。

## 5. 场景三：同步主体、创建角色、分配角色

目标：把外部用户同步为权限主体，并为其分配组织、职位、基础角色或分组角色。

| 步骤 | 接口                                             | 关键入参                                              | 结果             |
| ---- | ------------------------------------------------ | ----------------------------------------------------- | ---------------- |
| 1    | `POST /api/perm/abstract-user/sync`              | `subjectTypeCode + subjectExternalId + name + syncVersion` | 幂等同步用户 |
| 2    | `POST /api/perm/abstract-role/create`            | `roleTypeCode + roleExternalId + name + domainCode`   | 创建可授权角色   |
| 3    | `POST /api/perm/abstract-role/tree`              | `domainCode/roleTypeCode`                             | 查看角色层级     |
| 4    | `POST /api/perm/user-role/assign`                | `subjectTypeCode + subjectExternalId + assignments[]` | 给用户分配角色   |
| 5    | `POST /api/perm/permission-view/effective-roles` | `subjectTypeCode + subjectExternalId`                 | 验证用户有效角色 |

关键逻辑：

- 用户同步以 `subjectTypeCode + subjectExternalId` 幂等定位。调用方全程使用业务键引用主体和资源，permission-center 内部解析为内部 ID，调用方无需回填或存储内部 ID。
- 在 AccessMesh 管理端场景中，`sys_user` 的本地投影由 `access.application` 同一事务维护：`abstract_user(subjectTypeCode=LOCAL_USER, subjectExternalId=sys_user.id)` + `resource_entity(resourceTypeCode=USER, resourceCode=sys_user.id)`，两类事实均使用业务键定位，不再走 sync API。
- 对外可调用的角色建议必须有 `roleExternalId`，后续授权和分配可以不用内部角色 ID。
- 在 AccessMesh 管理端场景中，组织既是业务树也是角色容器。`access.application` 同一事务维护本地投影：`sys_org` → `resource_entity(ORG)` + `abstract_role(ORG/POSITION)`（父角色按父节点实际 orgType 解析，业务键定位，不回填内部 ID）；`sys_user_org` 成员关系同事务写入 `user_role`（POSITION 成员 `relation_id` 指向所属组织角色）。`user-role/assign` 仅用于功能角色等正式用户角色管理操作。
- 管理端（`access.application`）不再有同步任务：投影、`permission_change_log` 与缓存失效登记在同一事务内完成，删除用户即级联软删其全部 `user_role`（含功能角色），投影依赖缺失整体回滚（fail-closed）。外部业务服务仍通过 `/api/perm/**/sync|full-sync` 维护自有类型投影（保留业务键：subject `LOCAL_USER`（原 ADMIN_USER 更名）/role `ORG|POSITION`/`SYS_USER_ORG` 20045 拒绝；resource 侧取消类型级保留，本地投影行按 `owner=access-service` 所有权保护，T-ACCESS-018）。`role_resource_permission` 属于 permission-center 授权管理域，不进入管理端写入。
- `GROUP_ROLE` 本身不直接配置权限，通过子角色或额外基本角色产生有效权限。首期用 `extra.basicRoleIds` 简化表达，缓存构建阶段展开，运行时不频繁解析 JSON。
- `POSITION` 类型分配时可带组织关系字段，用于表达职位在某组织下的上下文。
- 分配或回收用户角色后，失效该用户有效角色缓存。

## 6. 场景四：配置基础角色权限

目标：为角色配置资源和操作权限，这是最核心的权限事实写入链路。**授权页面写链路收敛为唯一写入口 `apply-grant-plan`（2026-08-02 单入口收敛）**——所有页面写操作（新增/编辑/删除主权限与子权限、跨键替换）统一在一次 plan 中表达；`save/revoke/children/add-child/remove-child` **仅迁移期保留并标记弃用**（**终态=随 T-PERM-034 删除，2026-08-08 复审确认**），`update-child/children-save/rebuild` 不实现。

| 步骤 | 接口                                           | 关键入参                                                         | 结果                             |
| ---- | ---------------------------------------------- | ---------------------------------------------------------------- | -------------------------------- |
| 1    | `POST /api/perm/resource-entity/create`        | `resourceTypeCode + resourceCode + codeType + name`              | 创建菜单、按钮、API、DATA 等资源 |
| 2    | `POST /api/perm/operation-permission/list`     | `resourceTypeCode + includeGlobalFallback`                      | 选择当前类型适用操作（专属优先、全局回退合并） |
| 3    | `POST /api/perm/permission-condition/create`   | `conditionCode + conditionRules`                                 | 可选，创建复用条件               |
| 4    | `POST /api/perm/role-resource-permission/list` | `domainCode + roleTypeCode + roleExternalId + resourceTypeCode + includeChildren` | 读取当前类型权限（含跨类型子权限按 depend_on 挂父） |
| 5    | `POST /api/perm/role-resource-permission/apply-grant-plan` | `roleTypeCode + roleExternalId + plan{creates/updates/removes}` | 单事务提交全部写意图             |
| 6    | `POST /api/perm/role-resource-permission/list` | `domainCode + roleTypeCode + roleExternalId + resourceTypeCode`  | 验证角色权限（提交后刷新验证**继续沿用当前 MatrixContext 的单个 `resourceTypeCode`**，与第 4 步同口径） |

关键逻辑（`apply-grant-plan`，详见 api-contract §6.5/§6.5.1）：

- **单事务**：`creates`（新建记录：主权限可带 children 一次性建树；子权限用 `parentPermissionId` 挂父）+ `updates`（现有**主权限** canGrant/conditionCode 微变更；**目标为子权限的 update 一律拒绝 -> 20043**，复审）+ `removes`（删除记录：主权限级联删子、子权限单条删）在**同一事务**内执行，任一失败整体回滚，无部分成功。
- **跨键替换**（范围/资源/操作变化）= removes 旧 + creates 新（同事务原子）；**子权限不迁移**，随旧主权限级联删除（预期行为），新主权限子权限在 creates 中显式配置。
- **无 CAS/无乐观锁**（收窄）：砍 expectedRevision/grant_revision/20037；后端靠单事务原子 + uk 约束 + 受影响行数断言保证一致性。
- **统一预检**：所有规则（记录存在及角色/父归属、段间互斥、AUTO_DEP 只读 20034、canGrant 授权传递、SUB_PERM fail-closed 20011（父域解析走记录自身 resource_type）、MANUAL 单直接授权唯一性 20033、scopeMode/资源兼容、**主权限条件不变量 20041/20042（复审：仅主权限）**、**子权限属性系统不变量 20043（两种 create 形态非 null/false 拒绝、update 目标为子权限拒绝；优先级先于 20041/20042，见 api-contract §6.5.1）**）经 `prevalidateGrantPlan` 唯一预检入口执行。
- **受影响行数断言**：updates/removes 实际影响行数 ≠ 预期（并发删除/修改）-> 20036 整体回滚；plan 至少含一项变更，update 至少改 canGrant/conditionCode，拒绝重复 ID 与 update/remove 交叉 ID。
- **无 CAS/无幂等表/无 clientRequestId**（收窄）：前端 saving 期间按钮 disabled 防重复点击；超时提示刷新确认；后端靠单事务原子 + uk 约束 + 受影响行数断言。
- 授权项用 `domainCode + resourceTypeCode + resourceCode + codeType + operationCode + scopeMode` 定位资源和操作；`operationCode` 必填并按"专属优先、全局回退"规则校验适用性，不匹配 -> **20008**；MANUAL 新授权一行只写一个操作位，不接受组合位。同一角色 + 资源/范围 + 操作 + 父权限最多一条 MANUAL 直接授权，`conditionCode/canGrant` 作为该记录的可变属性直接更新，重复 create -> **20033**（conditionCode/canGrant 不参与身份）。**属性不变量按主/子记录分类（复审）**：**主权限**条件不可转授（`conditionCode != null` -> `canGrant=false`，creates 主权限 + updates 结果态，违反 -> **20041**）且条件必须启用（**20042**）；**子权限**不承载条件/再授予（create 的 `conditionCode` 必须 null、`canGrant` 必须 false，违反 -> **20043**；update 目标为子权限一律 **20043**）。条件可选，填写 `conditionCode` 时必须存在且启用。
- 写入后记录 `operation_log` 和 `permission_change_log`，并通过 Redis pub/sub 广播 `PermInvalidateEvent` 失效相关缓存（afterCommit）。（**已删除 `permission_version` 递增**，2026-06-20 审计 S-001/S-018；收窄：apply-grant-plan 单事务原子 + 受影响行数断言，无 CAS/幂等表）

## 7. 权限查询引擎（PermQueryEngine）

所有权限查询和校验统一通过 `PermQueryEngine.query(PermQuery)` 执行，引擎根据查询模式走不同路径：

```
PermQueryEngine.query(PermQuery)
    │
    ├─ forUserView ──► 全量角色权限记录（不按位过滤）+ effective 操作投影
    │
    ├─ forAuthCheck / forValidate / forInterfaceCheck
    │      ├─ resolveRoleIds ──► SubjectDomainService
    │      ├─ resolveResourceTypes / resolveOperationIds ──► TypeResolutionService (ResolveContext)
    │      ├─ queryScopeAll ──► selectScopeAllPermsByBitsBatch (1 SQL)
    │      │     └─ scopeAll 匹配且 earlyReturnOnScopeAll → 提前返回
    │      ├─ resolveEntityIds ──► TypeResolutionService.batchResolveResourceIds
    │      ├─ queryInstance ──► selectInstancePermsByBitsBatch (1 SQL)
    │      ├─ expandByInheritMode ──► 按 inheritParents/inheritChildren 展开
    │      ├─ evaluateConditions ──► PermissionConditionDomainService
    │      ├─ evaluateConflicts ──► PermissionConflictDomainService
    │      └─ loadAncillary ──► 批量加载 Resource/Operation/Role
    │
    ├─ forResourceQuery ──► 仅实例查询，不评估条件/冲突
    ├─ forResourceCheck ──► 全范围+实例，完整评估
    └─ forScopeQuery ──► 不提前返回，不评估，返回全部辅助信息
```

**内部 `scopeAll` 作为一等权限维度，对外统一映射为 `scopeMode`**：

- `SnapshotAssembler`：内部 `scopeAll` 条目不展开为 N 个 API 资源，对外快照项返回 `scopeMode=ALL` 且 `httpMethod=null, pathPattern=null`；实例级条目返回 `scopeMode=INSTANCE`。
- `PermViewAssembler`：按 `resourceType` 分组输出全量范围视图项，对外使用 `scopeMode=ALL`，例如 `DATA_EDIT + DEPT + scopeMode=ALL` 表示可编辑全部部门范围。

**资源继承展开**（引擎层实现）：

- `setInheritMode("PARENT"/"CHILD"/"BOTH")` 设置后，引擎在 `expandByInheritMode()` 中加载全部有效资源构建父子图，向上遍历父链或向下递归收集子孙，克隆权限条目（`grantSource="INHERITED"`、`resourceEntityId=目标资源ID`）。
- scopeAll 条目（`resourceEntityId=null`）不参与继承展开。

**内部 AppService 使用 `PermResultUtils`** 将 `PermResult` 转为对外响应：

- `PermResultUtils.toAuthCheckResp(result)` — `check/batch-check` 响应
- `PermResultUtils.validateOrThrow(result)` — `validate` 模式（拒绝时抛异常）

## 8. 场景五：配置子权限和数据范围

目标：在主权限下挂数据范围或其他附属权限，用 `depend_on` 表达主从关系。

| 步骤 | 接口                                                | 关键入参                                     | 结果                                       |
| ---- | --------------------------------------------------- | -------------------------------------------- | ------------------------------------------ |
| 1    | `POST /api/perm/domain-config/save`                 | `configType=SUB_PERM`                        | 定义父资源类型允许挂载的子资源类型         |
| 2    | `POST /api/perm/resource-entity/create`             | `resourceTypeCode=DATA + resourceCode`       | 创建数据范围资源                           |
| 3    | `POST /api/perm/role-resource-permission/apply-grant-plan` | `plan.creates` 主权限带 `children` 嵌套（子权限写在父权限 children 内，一次性建树） | 写入主权限 + 子权限，`depend_on=父权限 id` |
| 4    | `POST /api/perm/role-resource-permission/list`      | `includeChildren=true`                       | 查询主权限下子权限（同一 list 接口）       |
| 5    | `POST /api/perm/auth/query-scopes`                  | 主资源业务键、主操作、范围资源类型和范围操作 | 运行时查询范围权限                         |

关键逻辑：

- **新父子树唯一通道 = `creates` 主权限带 `children` 嵌套**；`parentPermissionId` 仅引用提交前已存在的父记录（协议无临时关联键， P1-7）。
- 子权限继承父权限的角色，不需要再次传 `roleTypeCode + roleExternalId`。
- 删除主权限时级联软删子权限。
- 权限中心只返回数据范围事实，不生成业务 SQL，不解释业务字段。
- `auth/check` 适合只判断主权限是否允许；业务需要拿范围权限集合时，使用 `auth/query-scopes`。
- 内部 `scope_all=true` / 对外 `scopeMode=ALL` 表示该授权覆盖某个资源类型下全部范围资源，不需要创建 `data:all` 这类特殊资源。

## 9. 场景六：Gateway 接口级鉴权

目标：Gateway 在请求进入业务服务前完成接口级鉴权。**快照模式（T-PERM-001）为主链路**：Gateway 按 `(tenantId, subjectTypeCode, userId, serviceCode)` 拉取 `interface-snapshot` 全量接口权限快照后本地内存匹配（≤15s L1，Redis pub/sub 主动失效），条件不可本地评估或快照未覆盖时回退下表实时鉴权链路（check-interface，保留端点）。

| 步骤 | 执行方            | 动作                                                                                           |
| ---- | ----------------- | ---------------------------------------------------------------------------------------------- |
| 1    | Gateway           | 解析 Token，得到 `subjectTypeCode + subjectExternalId` 和 `X-Tenant-Id`，并清洗外部伪造 Header |
| 2    | Gateway           | 提取 `serviceCode + httpMethod + 原始 path`                                                    |
| 3    | Gateway           | 查询本地 L1 缓存                                                                               |
| 4    | Gateway           | 缓存未命中时调用 `POST /api/perm/auth/check-interface`                                         |
| 5    | permission 域（access-service） | 按租户、服务、方法、路径匹配 `resource_api_mapping`                                            |
| 6    | permission 域（access-service） | 解析资源、操作、用户有效角色、条件和冲突规则                                                   |
| 7    | permission 域（access-service） | 返回 `allowed/reason/matchedResources[]/cacheTtlSeconds`                                       |
| 8    | Gateway           | 允许则转发业务服务，拒绝则返回 403                                                             |

拒绝原因示例：

| reason               | 典型含义                     |
| -------------------- | ---------------------------- |
| `API_NOT_REGISTERED` | 接口没有映射，白名单模式拒绝 |
| `USER_DISABLED`      | 主体被停用                   |
| `NO_ROLE`            | 用户无有效角色               |
| `NO_PERMISSION`      | 有角色但无接口资源权限       |
| `CONDITION_NOT_MET`  | 条件不满足                   |
| `CONFLICT_DETECTED`  | 权限互斥导致失效             |

**Gateway 接口权限检查**：Gateway 调用 `POST /api/perm/auth/check-interface`，服务端匹配 API 映射后直接走 `PermQueryEngine.query(PermQuery.forInterfaceCheck())` 做 `API.ACCESS` 判定，不额外叠加其他资源类型 VIEW 门禁。

> **注意**：`getEffectivePermissions` 的权限门禁从 `SYSTEM_CONFIG.VIEW` 改为按 targetType 对应的资源类型 VIEW 权限判定。

## 10. 场景七：业务服务 SDK 鉴权与权限查询

目标：业务服务既能判断单个动作是否允许，也能查询用户可操作资源集合和数据范围。SDK 运行时接口不依赖权限中心内部数据库 ID，也不复用管理端解释用的 `permission-view/*`。

| 能力           | 接口                                  | 典型场景                                                     | 结果                          |
| -------------- | ------------------------------------- | ------------------------------------------------------------ | ----------------------------- |
| 布尔鉴权       | `POST /api/perm/auth/check`           | 打开报表前判断是否有 `VIEW` 权限                             | `allowed/reason`              |
| 批量鉴权       | `POST /api/perm/auth/batch-check`     | 列表页按钮批量置灰                                           | 每个检查项的 `allowed/reason` |
| 可操作资源查询 | `POST /api/perm/auth/query-resources` | 管理域查询可管理组织、角色、菜单                             | 资源业务键集合和命中操作      |
| 范围权限查询   | `POST /api/perm/auth/query-scopes`    | example-service 查询报表可读、可编辑的城市、部门、门店等范围 | 范围权限集合                  |

### 10.1 管理域查询可管理对象

管理域可被权限控制的组织、角色、菜单资源由 `access.application` 在管理事实写入的同一事务内维护为本地权限投影（ORG/USER/MENU 资源与 ORG/POSITION 角色，T-ACCESS-018 收敛后类型码），运行时查询直接命中本地引擎。

| 查询目标           | 资源建模                                                      | 运行时查询                                                                                     |
| ------------------ | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 用户能管理哪些组织 | `resourceTypeCode=ORG`、`resourceCode={sys_org.id}`         | `query-resources` 传 `resourceTypeCodes=["ORG"]`、`operationCodes=["UPDATE"]` 或其他管理操作 |
| 用户能管理哪些用户 | `resourceTypeCode=USER`、`resourceCode={sys_user.id}`       | `query-resources` 传 `resourceTypeCodes=["USER"]`、`operationCodes=["UPDATE","DELETE","ENABLE","RESET_PASSWORD"]` |
| 用户能管理哪些角色 | `resourceTypeCode=ROLE`、`resourceCode=role:{roleExternalId}` | `query-resources` 传 `resourceTypeCodes=["ROLE"]`、`operationCodes=["MANAGE"]` 或 `["ASSIGN"]` |
| 用户能看到哪些菜单 | `resourceTypeCode=MENU`、`resourceCode=menu:{menuCode}`       | `query-resources` 传 `resourceTypeCodes=["MENU"]`、`operationCodes=["VIEW"]`（树由调用方自建） |

调用链路：

1. 管理域入口从登录态取 `subjectTypeCode + subjectExternalId` 和 `X-Tenant-Id`。
2. 同进程调用 permission 域 `POST /api/perm/auth/query-resources` 语义（本地 AppService/engine 直调，非跨服务 HTTP），传资源类型、操作码、业务域和上下文。
3. permission 域解析用户有效角色、角色继承、资源继承、条件、冲突规则。
4. permission 域返回命中的 `resourceCode`、`operations`、`matchedRoleIds`、`matchedPermissionIds`。
5. 管理域用 `resourceCode` 回查本服务组织、角色、菜单表，过滤列表或组装树。

关键逻辑：

- permission 域不直接查询 admin 域业务表，只返回权限事实。
- 如果角色本身也是被管理对象，就必须把角色建模成 `resource_entity`；`abstract_role` 只表示授权主体，不等同于“可被管理的角色资源”。
- 菜单树展示基于 `query-resources` 平面列表由调用方自建（`treeMode` 树模式响应已于 2026-08-27 从契约移除，无真实消费方；最终排序、隐藏字段、路由元信息仍由管理域控制）。

### 10.2 example-service 查询报表范围权限

example-service 需要把报表建模为主资源，把城市、部门、门店、数据集等建模为范围资源。直接范围权限和依赖当前报表主权限的子权限会在运行时取并集。

| 步骤 | 动作             | 说明                                                                                |
| ---- | ---------------- | ----------------------------------------------------------------------------------- |
| 1    | 同步报表资源     | 例如 `resourceTypeCode=REPORT`、`resourceCode=report:sales`                         |
| 2    | 同步范围资源     | 例如 `resourceTypeCode=DATA`、`resourceCode=data:dept:A`                            |
| 3    | 配置直接范围权限 | 例如 A 部门主管角色拥有 `data:dept:A + DATA_READ`                                   |
| 4    | 配置报表主权限   | 推荐示例为 `report:sales + DATA_READ`、`report:sales + DATA_EDIT`                   |
| 5    | 配置子权限       | 在销售报表主权限下额外挂 `data:dept:B + DATA_READ`                                  |
| 6    | 运行时查询       | 调用 `POST /api/perm/auth/query-scopes`，可同时传 `DATA_READ` 和 `DATA_EDIT`        |
| 7    | 业务过滤         | example-service 按 `scopeGroups[]` 分支处理：`scopeMode=INSTANCE` 时把返回的 `items[].resourceCode` 转换为查询条件，`scopeMode=ALL` 时不加范围过滤 |

运行时规则：

- permission-center 先判断用户是否拥有主资源操作，例如 `report:sales + DATA_READ` 或 `report:sales + DATA_EDIT`。
- 主权限全部不通过时，顶层 `reason="NO_PERMISSION"`，请求笛卡尔积对应的 `scopeGroups[]` 均返回 `scopeMode=DENIED`；响应不再包含旧 `allowed` 字段。
- 直接范围权限 `DIRECT` 与当前主权限下的子权限 `DEPENDENT` 按并集返回。
- 多个主操作和多个范围操作可以一次查询；范围操作必须被一个已通过的主操作激活。
- 主资源上的 `DATA_READ/DATA_EDIT` 是推荐范例，用于 example-service 表达报表承载数据的读写；其他业务系统可以定义自己的主操作和范围操作映射。
- 子权限也要参与条件计算和冲突处理，未满足条件的子权限不进入结果。
- 调用方必须按 `scopeGroups[].scopeMode` 分支处理，不得用 `items=[]` 推断语义：`ALL` / `DENIED` / `EMPTY` 都可能为空，只有 `INSTANCE` 使用 `items[]` 承载具体范围实例。
- 全量范围必须通过对外 `scopeMode=ALL` 显式表达，例如 `DATA_EDIT + DEPT + scopeMode=ALL` 表示可编辑全部部门范围；内部存储仍对应 `role_resource_permission.scope_all=true`。
- 权限中心不生成 SQL；example-service 自行把 `data:city:shanghai`、`data:dept:finance` 等资源键映射为查询条件。

### 10.3 运行时查询边界

- `auth/check` 和 `auth/batch-check` 解决“能不能做”。
- `auth/query-resources` 解决“能操作哪些资源对象”。
- `auth/query-scopes` 解决“允许访问主资源后，能操作哪些范围资源”。
- `permission-view/*` 解决“为什么有/没有权限”，用于管理端解释、排查和审计，不作为业务服务高频运行时依赖。

## 11. 场景八：接口变更后的全量同步和权限影响

目标：服务发布后接口新增、删除或路径变化，权限中心跟随更新。

| 步骤 | 接口                                 | 关键入参                       | 结果                   |
| ---- | ------------------------------------ | ------------------------------ | ---------------------- |
| 1    | `POST /api/perm/service-config/sync` | 新的 FULL 接口列表             | 权限中心计算 diff      |
| 2    | 自动处理                             | 新接口创建 API 资源和映射      | 可被授权               |
| 3    | 自动处理                             | 删除接口软删映射和自动创建资源 | Gateway 不再匹配旧接口 |
| 4    | 自动处理                             | 影响 API mapping / API 资源的 serviceCode | 广播 `PermInvalidateEvent.serviceCodes`，Gateway 清本地快照 |
| 5    | `POST /api/perm/service-config/apis` | `serviceCode`                  | 验证最新接口资源树     |

关键逻辑：

- 同步入口不做增量模式，减少接入方心智负担。
- 自动删除只应处理同一 `ownerServiceCode + maintainSource=SERVICE_SYNC` 范围内由接口同步创建的 API 资源，避免误删人工维护资源。
- 路径变更应视为旧接口删除和新接口新增。

## 12. 场景九：资源依赖自动补全

目标：授权某个资源时，自动补齐它依赖的接口或数据资源权限。

| 步骤 | 接口                                            | 关键入参                                                        | 结果                 |
| ---- | ----------------------------------------------- | --------------------------------------------------------------- | -------------------- |
| 1    | `POST /api/perm/resource-dependency/create`     | 源资源、依赖资源、触发操作位、required 操作位、`autoGrant=true` | 建立依赖规则         |
| 2    | `POST /api/perm/role-resource-permission/apply-grant-plan` | 给角色授权源资源（plan.creates）              | 自动补齐依赖资源权限（grantSource=AUTO_DEP） |
| 3    | 自动处理                                        | 写入 `grantSource=AUTO_DEP + grantDepId`                        | 标记补全来源         |
| 4    | `POST /api/perm/role-resource-permission/list`  | 查询角色权限                                                    | 能看到自动补齐结果   |
| 5    | `POST /api/perm/resource-dependency/batch-sync` | 修改依赖规则                                                    | 清理旧补全并重新评估 |

关键逻辑：

- 自动补全不应产生重复授权。
- `resource_dependency.resource_entity_id` 是源资源/被授权资源；`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
- 授权源资源时，自动补全查询条件必须是 `resource_dependency.resource_entity_id = 授权资源ID`。
- 同一源资源和依赖资源可以按不同 `source_operation_bits` 配置多条依赖规则。
- `resource-dependency/batch-sync` 的 FULL diff 只能清理同一 `ownerServiceCode + maintainSource` 范围内缺失的规则，不能清理其他服务或其他维护来源的规则。
- 依赖规则变更时按 `grantDepId` 精准清理自动补全记录。
- 自动补全同样需要记录变更日志，并通过 `PermissionChangeContext.markRoles/markServiceCodes` 在 afterCommit 阶段失效 `ROLE_PERM_SNAPSHOT`、用户有效角色缓存并广播。
- **注意**：`auto-grant` 自动补全功能标记为 TODO，Phase 1-5 未完整实现。当前 `PermissionGrantDomainService.revokePermissions` 只做权限事实软删；缓存失效与广播由调用方通过 `PermissionChangeContext` + `@PermissionChange` afterCommit 统一处理。

## 13. 场景十：权限视图和审计排查

目标：让管理端、测试和运维能解释“用户为什么有/没有某权限”。

| 查询目标     | 接口                                                   | 说明                                             |
| ------------ | ------------------------------------------------------ | ------------------------------------------------ |
| 用户有效角色 | `POST /api/perm/permission-view/effective-roles`       | 展示直接、分组、组织、职位等来源                 |
| 用户有效权限 | `POST /api/perm/permission-view/effective-permissions` | 分页筛选展示当前有效权限                         |
| 用户资源树   | `POST /api/perm/permission-view/resource-tree`         | 菜单/按钮展示常用                                |
| 资源授权用户 | `POST /api/perm/permission-view/resource-users`        | 反查谁拥有某资源权限                             |
| 角色权限     | `POST /api/perm/permission-view/role-permissions`      | 角色维度排查                                     |
| 单权限解释   | `POST /api/perm/permission-view/explain`               | 解释某个具体权限当前是否拥有、来源和近期影响事件 |
| 近期变更     | `POST /api/perm/permission-view/recent-changes`        | 查询近期可能影响用户或角色权限的变更事件         |
| 操作日志     | `POST /api/perm/operation-log/list`                    | 所有写操作轻量审计                               |
| 权限变更日志 | `POST /api/perm/permission-change-log/list`            | 权限 diff 审计                                   |

### 13.1 操作日志记录机制

写操作日志采用 `@OperationLog` AOP + `OperationLogRuntimeContext` 双层机制：

- **`@OperationLog` 注解**：标注在 AppService 写方法上，声明 `module`、`action`、`targetType`（SpEL）、`targetId`（SpEL）、`summary`（SpEL）。由 `OperationLogAspect` 环绕拦截，方法执行成功后才记录日志。
- **`OperationLogRuntimeContext`**（ThreadLocal）：方法体内可通过静态方法覆盖注解值：
  - `markSkip()` — 跳过日志记录（如幂等操作无实际变更时）
  - `setSummary(String)` — 覆盖摘要（void 方法无法通过 `#result` 获取返回值）
  - `setTargetType(String)` / `setTargetId(Long/String)` — 覆盖目标类型和 ID
- 内部动态日志（diff 快照、冲突通知）仍由 `AuditDomainService.asyncRecordLog()` 显式调用，不走 AOP。

### 13.2 用户排查链路

典型问题：用户反馈“我突然没有某个报表权限”或“我突然多了某个权限”。

| 步骤 | 接口                                                   | 作用                                                                 |
| ---- | ------------------------------------------------------ | -------------------------------------------------------------------- |
| 1    | `POST /api/perm/permission-view/effective-roles`       | 查询用户当前有效角色，确认是否被移除角色或命中停用角色               |
| 2    | `POST /api/perm/permission-view/explain`               | 针对用户反馈的具体资源和操作，解释当前是否拥有、来源角色和未命中原因 |
| 3    | `POST /api/perm/permission-view/recent-changes`        | 查询最近 30 天可能影响该用户权限的变更事件                           |
| 4    | `POST /api/perm/permission-view/effective-permissions` | 需要浏览当前权限清单时，按资源类型、操作、关键词分页筛选             |
| 5    | `POST /api/perm/permission-change-log/list`            | 必要时查看原始 before/after/diff 审计详情                            |

展示建议：

```text
当前权限：
- report:sales DATA_READ，来源：报表查看员、部门主管

最近变更：
- 2026-04-20 管理员从“报表编辑员”角色删除了 report:sales DATA_EDIT
- 2026-04-18 用户被移出“财务主管”角色
- 2026-04-15 “销售报表”资源被停用后恢复
```

关键边界：

- `explain` 回答“某个具体权限当前是否拥有、来源角色是谁、为什么拒绝、最近有什么相关变更”。
- `effective-permissions` 回答“当前拥有什么权限”，但必须分页和筛选，不应用于一次性拉取全量权限。
- `recent-changes` 回答“最近有哪些事件可能影响该用户权限”。
- `recent-changes` 不承诺返回用户有效权限的精确历史 diff，因为同一权限可能同时来自多个角色。
- 如果角色 A 删除了某权限，但角色 B 仍授予同一权限，用户当前仍拥有该权限；页面文案应表达为“可能影响”，不能写成“用户已失去权限”。
- `effective-permissions` 默认不展开数据范围、子权限、API 资源和完整来源角色；调用方必须显式传过滤条件和展开选项。

### 13.3 角色排查链路

典型问题：管理员查看某个角色为什么当前权限发生变化。

| 步骤 | 接口                                              | 作用                                         |
| ---- | ------------------------------------------------- | -------------------------------------------- |
| 1    | `POST /api/perm/permission-view/role-permissions` | 查询角色当前权限配置                         |
| 2    | `POST /api/perm/permission-view/explain`          | 针对某个资源和操作解释该角色当前是否拥有权限 |
| 3    | `POST /api/perm/permission-view/recent-changes`   | 查询该角色最近权限增删改、状态变更、依赖变更 |
| 4    | `POST /api/perm/permission-change-log/list`       | 查看原始审计记录                             |

关键逻辑：

- 权限视图应使用实时事实数据或最新缓存，不应返回已软删除记录。
- 视图接口用于解释和展示，不应承担写入职责。
- 审计日志需要包含 `requestId`、操作者、目标对象、前后差异或摘要。
- `permission_change_log.diff_snapshot` 保存结构化变更摘要，用于 `recent-changes` 展示和筛选；`old_snapshot/new_snapshot` 保存原始审计快照。
- `diff_snapshot` 只描述本次写操作直接改变了什么，不负责计算用户最终有效权限是否发生变化。
- 查询用户最近变更时，服务端可通过 `affected_abstract_user_ids` 直接匹配用户，也可通过用户当前/历史关联角色匹配 `affected_abstract_role_ids`；首期以“排查线索完整”为目标，不做历史快照回放。

## 14. 场景十一：回收、删除和级联

目标：确保删除和回收不会留下可生效的悬挂权限。

| 场景                    | 接口                                                   | 级联或失效                              |
| ----------------------- | ------------------------------------------------------ | --------------------------------------- |
| 回收用户角色            | `POST /api/perm/user-role/revoke`                      | 失效用户有效角色缓存                    |
| 回收角色权限            | `POST /api/perm/role-resource-permission/apply-grant-plan`（plan.removes） | 级联软删子权限，失效角色权限快照与相关用户缓存 |
| 删除子权限              | `POST /api/perm/role-resource-permission/apply-grant-plan`（plan.removes 填子权限 id） | 子权限单条删（removes 不区分主/子意图） |
| 删除资源                | `POST /api/perm/resource-entity/remove`                | 软删资源、接口映射、角色权限、依赖关系；登记受影响角色和服务编码 |
| 删除角色                | `POST /api/perm/abstract-role/remove`                  | 软删用户角色关系和角色权限，直清角色权限快照并失效用户缓存 |
| 删除用户                | `POST /api/perm/abstract-user/remove`                  | 软删用户角色关系和个人角色权限          |
| 停用用户/角色/资源/服务 | 对应 update 接口                                       | 运行时鉴权直接拒绝或不参与计算          |

关键逻辑：

- 删除统一是软删除。
- 删除父权限必须级联删除 `depend_on` 指向它的子权限。
- 回收和删除都必须写审计日志。
- 任何影响鉴权结果的变更都必须触发缓存失效。

## 15. 是否满足目标的检查点

| 目标           | 检查方式                                                                                              |
| -------------- | ----------------------------------------------------------------------------------------------------- |
| 通用权限服务   | 外部系统只使用稳定业务键即可完成主体同步、授权和鉴权                                                  |
| 接口规范统一   | 全部接口走 `/api/perm/*`，无 RESTful Path 参数，无 body `tenantId`                                    |
| SaaS 多租户    | 所有查询和写入都强制带 `X-Tenant-Id`，接口映射也按租户过滤                                            |
| Gateway 可接入 | `check-interface` 使用 serviceCode、method、原始 path 判定                                            |
| SDK 可接入     | `auth/check`、`auth/batch-check`、`auth/query-resources`、`auth/query-scopes` 都不要求内部数据库 ID   |
| 管理端可解释   | 权限视图、操作日志、变更日志能解释授权来源和变更历史                                                  |
| 数据权限可表达 | `depend_on` 子权限和直接范围权限共同表达主资源上下文内的有效范围，运行时通过 `auth/query-scopes` 查询 |
| 接口同步简单   | 首期只有 FULL 同步，接入服务不需要维护增量事件                                                        |
| 缓存一致性     | 权限变更、依赖变更、角色关系变更都能通过 `PermissionChangeContext` 登记影响范围，并在事务提交后触发精确缓存失效和 Redis 广播 |

## 16. 仍需实现时重点校验

- `abstract_role.external_id` 唯一约束为 `uk_abstract_role_external (tenant_id, role_type, external_id)`（**无业务域列**，schema L147）；解析 `roleTypeCode + roleExternalId` 定位角色，**不携带 domainCode 过滤**（domainCode 仅域存在性校验， P2-2 同步修正）。
- `type_definition.type_value` 必须在同一 `tenant_id + type_key` 内全局唯一，不能按业务域重复分配相同值。
- `operationCode` 在解析时必须结合 `resourceTypeCode`，避免不同资源类型下同名操作产生歧义。
- `resourceCode` 必须结合 `resourceTypeCode + codeType` 解析，避免多编码歧义（`domainCode` 不参与资源解析， P2-2 同步）。
- `check-interface` 查询 `resource_api_mapping` 必须带 `tenant_id`。
- `check-interface` 命中同一路径的多个资源映射时采用 OR 语义，任一映射资源权限通过即允许；响应必须使用 `matchedResources[]` 表达所有命中映射资源。
- `auth/query-resources` 和 `auth/query-scopes` 必须复用 `auth/check` 的鉴权计算链路，避免查询结果和布尔鉴权结果不一致。
- `role_resource_permission.scope_all` 必须显式参与查询结果，不能用空结果或特殊资源编码隐式表示全量范围。
- `canManage=true` 只允许授权同一条权限，不能扩大资源、操作或范围；可授权对象候选范围由业务服务控制。
- 所有 Request DTO 必须移除 `tenantId`。
- 所有列表响应必须包装成 `data.items`，不能直接返回数组。
