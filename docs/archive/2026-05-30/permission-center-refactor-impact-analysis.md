# Permission-Center DDD 重构：跨模块影响分析

## 1. 执行摘要

permission-center DDD 重构（统一引擎、拆分 God Class、领域逻辑下沉）在 **API 路径层面零影响**——所有 `@RequestMapping` 路径保持不变，Feign 客户端和 Gateway 的 HTTP 调用路径无需修改。

然而，重构暴露了四类跨模块问题：

1. **Gateway DTO 契约不对齐**（预存缺陷，与重构无关但需解决）
2. **Feign 客户端返回值无类型化**（8 个方法使用 `Map<String, Object>`，丢失类型安全）
3. **perm-common 缺少关键 DTO 且部分 DTO 结构未对齐**（结构化类型仍停留在 permission-center 内部）
4. **`operationCode`/`operationCodes` 字段名不一致**（admin-service 潜在缺陷，`AuthServiceImpl.getUserPermissions()` 读取 `item.get("operationCode")` 单数字段，但 permission-center 的 `EffectivePermissionItem` 序列化为 `operationCodes` 复数 `List<String>`，导致按钮级权限提取始终返回空列表）

## 2. API 路径稳定性分析

### 2.1 重构不改变的路径

以下路径由 `@RequestMapping` 注解定义，重构仅变更 Controller 类名和内部 Service 调用链，**不修改注解路径**：

| 消费者                           | 使用的路径                                             | 重构后状态                  |
| -------------------------------- | ------------------------------------------------------ | --------------------------- |
| Feign: `checkAuth`               | `POST /api/perm/auth/check`                            | 不变（AuthController 保留） |
| Feign: `batchCheckAuth`          | `POST /api/perm/auth/batch-check`                      | 不变                        |
| Gateway: `checkInterface`        | `POST /api/perm/auth/check-interface`                  | 不变                        |
| Feign: `createRole`              | `POST /api/perm/abstract-role/create`                  | 不变                        |
| Feign: `getUserRoles`            | `POST /api/perm/user-role/list`                        | 不变                        |
| Feign: `createResource`          | `POST /api/perm/resource-entity/create`                | 不变                        |
| Feign: `batchGrant`              | `POST /api/perm/role-resource-permission/save`         | 不变                        |
| Feign: `batchRevoke`             | `POST /api/perm/role-resource-permission/revoke`       | 不变                        |
| Feign: `getEffectivePermissions` | `POST /api/perm/permission-view/effective-permissions` | 不变                        |
| Feign: 其余 5 个路径             | 见上表                                                 | 不变                        |

**结论：14 个 Feign 路径 + 1 个 Gateway WebClient 路径全部保持不变。**

### 2.2 Controller 类名变更（不影响路径）

| 旧类名                    | 新类名                   | 路径                             |
| ------------------------- | ------------------------ | -------------------------------- |
| ConfigManageController    | TypeDefinitionController | `/api/perm/type-definition`      |
| ConditionManageController | ConditionController      | `/api/perm/permission-condition` |
| OperationManageController | OperationController      | `/api/perm/operation-permission` |
| ResourceManageController  | ResourceController       | `/api/perm/resource-entity`      |
| RoleManageController      | RoleController           | `/api/perm/abstract-role`        |
| UserManageController      | UserController           | `/api/perm/abstract-user`        |

> 注：上述部分重命名已在当前代码库中完成，路径均由注解独立定义。

## 3. 跨模块影响详情

### 3.1 Gateway（高优先级）

**问题：DTO 契约不对齐**

Gateway 的 `PermissionClient` 调用 `POST /api/perm/auth/check-interface`，但使用 gateway 本地定义的 DTO，与 permission-center 的 `CheckInterfaceReq`/`CheckInterfaceResp` 结构不匹配：

| 字段     | Gateway 发送                | permission-center 期望                                    |
| -------- | --------------------------- | --------------------------------------------------------- |
| 用户标识 | `userId` (Long)             | `subjectTypeCode` (String) + `subjectExternalId` (String) |
| 拒绝原因 | 读取 `denyReason`           | 返回 `reason`                                             |
| 匹配资源 | 读取 `matchedRoleId` (Long) | 返回 `matchedResources` (List)                            |
| 缓存 TTL | 不处理                      | 返回 `cacheTtlSeconds`                                    |

**影响评估**：这不是“Header 隐性兜底”的潜在问题，而是当前请求体与服务端 DTO 契约的现存不兼容。`check-interface` 入口会对 `subjectTypeCode` / `subjectExternalId` 做 `@NotBlank` 校验，服务实现也直接使用这两个 body 字段解析主体；`X-User-Id` 相关逻辑仅用于操作者上下文，不参与 `check-interface` 的主体解析。继续沿用当前 Gateway 请求体时，服务端 4xx 或反序列化异常最终会在 Gateway 侧被包装为 503“鉴权服务暂时不可用”，从而误导排查方向。

**影响范围**：

- `gateway/src/.../service/PermissionClient.java`
- `gateway/src/.../filter/PermissionFilter.java`
- `gateway/src/.../config/CacheConfig.java`（确认本轮保持固定 TTL，不引入动态 TTL）
- `gateway/src/.../model/AuthCheckRequest.java`（需替换为 perm-common 的 `CheckInterfaceReq`）
- `gateway/src/.../model/AuthCheckResponse.java`（需替换为 perm-common 的 `CheckInterfaceResp`）
- `gateway/src/.../model/GatewayResponse.java`（不受影响，仅内部使用）
- `perm-sdk/perm-common/`（需添加 `CheckInterfaceReq`/`CheckInterfaceResp`）

> 注：Gateway 的 DTO 文件位于 `model/` 包而非 `dto/` 包（`gateway/src/.../model/AuthCheckRequest.java`）。`AuthCheckResponse` 是本地扁平化模型——包含 `code`/`message`/`data` 顶层字段，`data` 内含 `allowed`/`matchedRoleId`/`matchedOperationCode`/`denyReason`。迁移时需改为标准 `PermResult<CheckInterfaceResp>` 结构，并在本轮保持固定 TTL；`cacheTtlSeconds` 留作二阶段优化。

### 3.2 perm-common + Feign Client（中优先级）

**问题 1：返回值无类型化**

Feign 客户端 14 个方法中，8 个使用 `Map<String, Object>` 作为返回类型的泛型参数：

| Feign 方法                | 当前返回类型                                                          | 应有类型                                         |
| ------------------------- | --------------------------------------------------------------------- | ------------------------------------------------ |
| `syncUser`                | `PermResult<Map<String, Object>>`                                     | `PermResult<UserResp>`                           |
| `createRole`              | `PermResult<Map<String, Object>>`                                     | `PermResult<RoleResp>`                           |
| `createResource`          | `PermResult<Map<String, Object>>`                                     | `PermResult<ResourceResp>`                       |
| `batchCreateResources`    | `PermResult<Map<String, Object>>`                                     | `PermResult<ItemsResp<ResourceResp>>`            |
| `updateResource`          | `PermResult<Map<String, Object>>`                                     | `PermResult<ResourceResp>`                       |
| `listOperations`          | `PermResult<Map<String, Object>>`                                     | `PermResult<ItemsResp<OperationPermissionResp>>` |
| `batchGrant`              | `PermResult<Map<String, Object>>`                                     | `PermResult<RolePermissionItemsResp>`            |
| `getEffectivePermissions` | `PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>>` | `PermResult<PermissionEffectivePermissionsResp>` |

**问题 2：关键 DTO 缺失或未对齐**

以下关键 DTO 仅定义在 permission-center 内部，或 perm-common 现有结构与服务端不一致：

- `CheckInterfaceReq` / `CheckInterfaceResp`（Gateway 需要）
- `UserResp`（admin-service 用户同步响应）
- `RoleResp`（admin-service 角色创建响应）
- `ResourceResp`（admin-service 资源创建/更新响应）
- `RolePermissionItemResp` / `RolePermissionItemsResp`（admin-service 授权响应）
- `ItemsResp<T>` / `PaginatedResp<T>`（通用包装器）
- `PermissionEffectivePermissionsResp`（需改为与 permission-center 一致的非泛型嵌套结构）
- `UserPermissionViewResp`（需与 permission-center 当前结构对齐）
- 其他管理类响应（admin-service 当前不使用，暂不需要）

**问题 3：UserPermissionViewResp 结构差异**

| 字段        | perm-common 版本   | permission-center 版本                                                        |
| ----------- | ------------------ | ----------------------------------------------------------------------------- |
| 顶层        | 扁平 record        | 包含 `subjectTypeCode`, `subjectExternalId`, `subjectName` + `resources` 列表 |
| sourceRoles | `List<SourceRole>` | `List<SourceRoleView>`（结构不同）                                            |

**影响范围**：

- `perm-sdk/perm-common/src/.../dto/resp/`（添加缺失的响应 DTO）
- `perm-sdk/perm-client-spring-boot-starter/src/.../feign/PermissionFeignClient.java`（类型化返回值）
- `admin-service` 中消费 `Map<String, Object>` 的代码需改为使用类型化 DTO

### 3.3 admin-service（中优先级）

**问题：消费无类型化 Feign 返回值**

admin-service 中 6 个类直接使用 `PermissionFeignClient`：

| 类                             | 调用的 Feign 方法                                                                      | 受影响程度                                                                                                           |
| ------------------------------ | -------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `AdminPermissionValidatorImpl` | `checkAuth`, `batchCheckAuth`                                                          | 低（已有类型化 `AuthCheckResp`/`BatchAuthCheckResp`）                                                                |
| `RoleProxyServiceImpl`         | `createRole`, `batchGrant`, `getEffectivePermissions`, `batchRevoke`, `listOperations` | 高（5 个方法中 3 个返回 `Map<String, Object>`）                                                                      |
| `AuthServiceImpl`              | `getUserRoles`, `getEffectivePermissions`, `batchCheckAuth`                            | **高**（`getEffectivePermissions` 返回泛型 + `operationCode`/`operationCodes` 字段名不一致导致按钮权限提取始终为空） |
| `UserSyncHandlerImpl`          | `syncUser`, `deleteUsers`                                                              | 中（`syncUser` 返回 `Map<String, Object>` 且提取 `id` 字段：`result.getData().get("id")`）                           |
| `OrgSyncHandlerImpl`           | `createResource`                                                                       | 中（返回 `Map<String, Object>` 且提取 `id` 字段：`result.getData().get("id")`）                                      |
| `MenuSyncHandlerImpl`          | `createResource`, `updateResource`                                                     | 中（`createResource` 返回 `Map<String, Object>` 且提取 `id` 字段：`result.getData().get("id")`）                     |

**主要影响**：`RoleProxyServiceImpl` 和 `AuthServiceImpl` 需要从 `Map<String, Object>` 提取数据的代码改为使用类型化 DTO。

### 3.3a `operationCode`/`operationCodes` 潜在缺陷（高优先级）

**问题**：admin-service 读取 `operationCode`（单数），但 permission-center 返回 `operationCodes`（复数，`List<String>`）

| 位置                                        | 读取方式                           | 实际 JSON 字段                     |
| ------------------------------------------- | ---------------------------------- | ---------------------------------- |
| `AuthServiceImpl.getUserPermissions()` L684 | `item.get("operationCode")`        | `operationCodes`（`List<String>`） |
| `RoleProxyServiceImpl.revokeMenuFromRole()` | `item.get("matchedPermissionIds")` | 正确（字段名一致）                 |

**影响**：`AuthServiceImpl.getUserPermissions()` 中按钮级权限码提取**始终返回空列表**——`item.get("operationCode")` 返回 `null`（因为 JSON 中字段名是 `operationCodes`），导致前端拿不到按钮权限，所有按钮级操作码丢失。这是一个**运行时静默失败**，不会抛异常，仅表现为"用户看不到按钮"。

**修复方式**：类型化后改为 `item.operationCodes()` 即可自然修复。

**权限中心侧定义**（`PermissionEffectivePermissionsResp.EffectivePermissionItem`）：

```java
public record EffectivePermissionItem(
    String resourceTypeCode,
    String resourceCode,
    String resourceName,
    String codeType,
    List<String> operationCodes,   // 复数，List<String>
    boolean scopeAll,
    List<SourceRole> sourceRoles,
    int sourceRoleCount,
    boolean sourceRolesTruncated,
    List<Long> matchedPermissionIds
) {}
```

### 3.4 perm-gateway-spring-boot-starter（低优先级）

当前为骨架模块，仅有 `PermGatewayAutoConfiguration` 和 `PermGatewayProperties`，无运行时逻辑。Gateway 实际权限逻辑在 `gateway` 模块自身。

**建议**：当前阶段无需在 starter 中新增 DTO 镜像。`gateway` 已通过 `perm-gateway-spring-boot-starter` 间接依赖 `perm-common`，可直接复用 `perm-common` 中的 `CheckInterfaceReq`/`CheckInterfaceResp`。除非后续要把 Gateway 权限调用整体封装进 starter，否则不建议在 starter 再复制一套契约对象。

### 3.5 perm-data-spring-boot-starter（无优先级）

骨架模块，无业务代码。不涉及改动。

### 3.6 example-service（无优先级）

骨架模块，仅有 `@EnableFeignClients` 注解。不涉及改动。

## 4. 优先级排序与实施顺序

| 优先级 | 模块                            | 工作量 | 理由                                                            |
| ------ | ------------------------------- | ------ | --------------------------------------------------------------- |
| P0     | perm-common                     | 2-3 天 | 关键 DTO 补齐与结构对齐是其他模块的前置条件                     |
| P1     | perm-client-spring-boot-starter | 1-2 天 | Feign 客户端类型化依赖 perm-common DTO                          |
| P1     | gateway                         | 1-2 天 | `check-interface` 当前是现存契约缺陷，依赖 perm-common DTO 对齐 |
| P2     | admin-service                   | 2-3 天 | 依赖 Feign 客户端类型化完成                                     |

**推荐实施顺序**：

```
perm-common DTO 补齐与结构对齐 → Feign Client 类型化 → Gateway 模块按 CheckInterfaceReq/CheckInterfaceResp 对齐（保持固定 TTL） → admin-service 适配
```

## 5. 重构未影响的区域

以下重构变更完全内部化，不影响其他模块：

- PermQueryEngine 统一查询引擎
- ConfigManageServiceImpl 拆分为 5 个 AppService
- PermissionGrantServiceImpl 拆分为 AppService + DomainService
- SubjectDomainService 合并 3 个旧 DomainService
- AuditDomainService 合并 2 个旧 DomainService
- @OperationLog AOP 切面
- Service 层重命名（*ManageService → *AppService）
- Controller 类名变更（路径不变）
- RolePermEntryMapper 移到 util 包
- 删除已废弃的类和字段
