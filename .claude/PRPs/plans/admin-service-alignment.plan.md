# Plan: admin-service Feign 返回值类型化适配

## Summary

将 admin-service 中所有消费 `PermissionFeignClient` 返回 `Map<String, Object>` 的代码改为使用强类型 DTO 访问，消除手动 Map 提取带来的运行时风险和代码可读性问题。

## User Story

As a admin-service 开发者, I want Feign 客户端返回强类型 DTO, So that 我无需手动从 Map 中提取字段、获得编译期类型安全和 IDE 自动补全，且重构时 IDE 可自动追踪影响范围。

## Problem → Solution

admin-service 中 `RoleProxyServiceImpl` 和 `AuthServiceImpl` 通过 `Map<String, Object>` 访问 Feign 返回值（如 `result.getData().get("id")`），字段名拼写错误只在运行时暴露，重构时 IDE 无法追踪 → Feign 返回类型化后，改为 DTO 字段访问（如 `result.getData().id()`）

## Metadata

- **Complexity**: Medium
- **Source PRD**: `docs/design/permission-center-refactor-impact-analysis.md`
- **Estimated Files**: 6-8
- **前置条件**: perm-sdk-typing 计划完成（Feign 客户端返回值已类型化）

---

## Mandatory Reading

| Priority | File                                                                            | Why                                        |
| -------- | ------------------------------------------------------------------------------- | ------------------------------------------ |
| P0       | `admin-service/src/.../service/impl/RoleProxyServiceImpl.java`                  | 主要受影响：5 个 Feign 调用中 3 个返回 Map |
| P0       | `admin-service/src/.../service/impl/AuthServiceImpl.java`                       | 受影响：getEffectivePermissions 返回泛型   |
| P1       | `admin-service/src/.../security/AdminPermissionValidatorImpl.java`              | 低影响：已有类型化返回                     |
| P1       | `admin-service/src/.../service/domain/impl/UserSyncHandlerImpl.java`            | 低影响：仅检查成功/失败                    |
| P1       | `admin-service/src/.../service/domain/impl/OrgSyncHandlerImpl.java`             | 低影响：仅检查成功/失败                    |
| P1       | `admin-service/src/.../service/domain/impl/MenuSyncHandlerImpl.java`            | 低影响：仅检查成功/失败                    |
| P2       | `perm-sdk/perm-common/src/.../dto/resp/RoleResp.java`                           | createRole 返回类型                        |
| P2       | `perm-sdk/perm-common/src/.../dto/resp/ResourceResp.java`                       | createResource 返回类型                    |
| P2       | `perm-sdk/perm-common/src/.../dto/resp/RolePermissionItemsResp.java`            | batchGrant 返回类型                        |
| P2       | `perm-sdk/perm-common/src/.../dto/resp/PermissionEffectivePermissionsResp.java` | effective-permissions 返回类型             |
| P2       | `perm-sdk/perm-common/src/.../dto/resp/UserPermissionViewResp.java`             | 同步对齐后的 SDK 视图 DTO，确认无间接影响  |

---

## Patterns to Mirror

### CURRENT_MAP_ACCESS_PATTERN

// SOURCE: admin-service/src/.../service/impl/RoleProxyServiceImpl.java

```java
// 当前模式：从 Map<String, Object> 提取字段
PermResult<Map<String, Object>> result = permissionFeignClient.createRole(req);
Long roleId = result.getData() != null ? ((Number) result.getData().get("id")).longValue() : null;
```

### TARGET_TYPED_ACCESS_PATTERN

// SOURCE: admin-service/src/.../security/AdminPermissionValidatorImpl.java

```java
// 目标模式：使用强类型 DTO
PermResult<AuthCheckResp> result = permissionFeignClient.checkAuth(req);
boolean allowed = result.getData().allowed();
```

### FEIGN_CALL_PATTERN

// SOURCE: admin-service/src/.../service/impl/RoleProxyServiceImpl.java

```java
// Feign 调用统一模式：获取 PermResult，检查成功，使用 data
PermResult<RoleResp> result = permissionFeignClient.createRole(req);
if (result.getCode() != 200 || result.getData() == null) {
    throw new BizException("创建角色失败: " + result.getMessage());
}
Long roleId = result.getData().id();
```

---

## Files to Change

| File                                                                 | Action | Justification                                                                                                                  |
| -------------------------------------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------ |
| `admin-service/src/.../service/impl/RoleProxyServiceImpl.java`       | UPDATE | 3 个 Map 返回值改为 DTO 访问（createRole→RoleResp, getEffectivePermissions→EffectivePermissionItem, listOperations→ItemsResp） |
| `admin-service/src/.../service/impl/AuthServiceImpl.java`            | UPDATE | getEffectivePermissions 泛型参数变更 + operationCode/operationCodes 潜在缺陷修复                                               |
| `admin-service/src/.../service/domain/impl/UserSyncHandlerImpl.java` | UPDATE | syncUser 返回 Map→UserResp，提取 `id` 字段改为 DTO 访问                                                                        |
| `admin-service/src/.../service/domain/impl/OrgSyncHandlerImpl.java`  | UPDATE | createResource 返回 Map→ResourceResp，提取 `id` 字段改为 DTO 访问                                                              |
| `admin-service/src/.../service/domain/impl/MenuSyncHandlerImpl.java` | UPDATE | createResource/updateResource 返回 Map→ResourceResp，提取 `id` 字段改为 DTO 访问                                               |
| `admin-service/src/.../security/AdminPermissionValidatorImpl.java`   | CHECK  | 确认已有类型化返回无需修改                                                                                                     |

## NOT Building

- 不修改 admin-service 的业务逻辑
- 不添加新的 Feign 调用
- 不修改 admin-service 的 Controller 层
- 不修改 admin-service 的数据模型
- 不新增单元测试（仅适配类型变更，更新现有测试的断言和 Mock 返回类型）

---

## Step-by-Step Tasks

### Task 1: 分析 RoleProxyServiceImpl 中的 Map 访问

- **ACTION**: 读取 RoleProxyServiceImpl，定位所有 `Map<String, Object>` 提取代码
- **IMPLEMENT**: 逐一记录每个 Map.get() 调用对应的 Feign 方法和提取的字段
- **FINDINGS**:
  - `createRoleForOrg`：`PermResult<Map<String, Object>>` → `result.getData().get("id")` → 需改为 `RoleResp.id()`
  - `grantMenuToRole`：`PermResult<Map<String, Object>>` → 仅检查 `result.getCode() != 200`，无 Map 字段提取 → **no-op 迁移**（仅改返回类型声明）
  - `revokeMenuFromRole`：`PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>>` → 提取 `item.get("resourceCode")` 和 `item.get("matchedPermissionIds")` → 需改为 `EffectivePermissionItem.resourceCode()` 和 `EffectivePermissionItem.matchedPermissionIds()`
  - `loadOperations`：`PermResult<Map<String, Object>>` → 提取 `items[].code` 和 `items[].id` → 需改为 `ItemsResp<OperationPermissionResp>` 访问
- **VALIDATE**: 完整记录所有需要迁移的 Map 访问点

### Task 2: 迁移 RoleProxyServiceImpl.createRoleForOrg

- **ACTION**: 将 `createRole` 返回的 Map 访问改为 RoleResp 访问
- **IMPLEMENT**:

  ```java
  // 旧
  PermResult<Map<String, Object>> result = permissionFeignClient.createRole(req);
  Long roleId = ((Number) result.getData().get("id")).longValue();

  // 新
  PermResult<RoleResp> result = permissionFeignClient.createRole(req);
  Long roleId = result.getData().id();
  ```

- **GOTCHA**: 确认 RoleResp 的 `id()` 字段名与 Map 中的 `"id"` key 对应
- **VALIDATE**: 编译通过

### Task 3: 迁移 RoleProxyServiceImpl.grantMenuToRole

- **ACTION**: 将 `batchGrant` 返回类型从 `Map<String, Object>` 改为 `RolePermissionItemsResp`
- **IMPLEMENT**:

  ```java
  // 旧 — 仅检查成功/失败，不提取 Map 字段
  PermResult<Map<String, Object>> result = permissionFeignClient.batchGrant(req);
  if (result == null || result.getCode() != 200) { ... }

  // 新 — no-op 迁移，仅改返回类型声明
  PermResult<RolePermissionItemsResp> result = permissionFeignClient.batchGrant(req);
  if (result == null || result.getCode() != 200) { ... }
  ```

- **GOTCHA**: 此方法是 no-op 迁移——仅声明类型变更，业务逻辑不变。`batchGrant` 的返回值仅用于检查成功/失败
- **VALIDATE**: 编译通过

### Task 4: 迁移 RoleProxyServiceImpl.revokeMenuFromRole

- **ACTION**: 将 `getEffectivePermissions` 返回的 `PermissionEffectivePermissionsResp<Map<String, Object>>` 改为 `PermissionEffectivePermissionsResp`（非泛型，内嵌 EffectivePermissionItem）
- **IMPLEMENT**:

  ```java
  // 旧 — 从 Map<String, Object> 提取 resourceCode 和 matchedPermissionIds
  PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>> viewResult =
      permissionFeignClient.getEffectivePermissions(viewReq);
  PermissionEffectivePermissionsResp<Map<String, Object>> respData = viewResult.getData();
  for (Map<String, Object> item : respData.items()) {
      Object resourceCodeObj = item.get("resourceCode");
      if (resourceCodeObj != null && targetResourceCode.equals(resourceCodeObj.toString())) {
          Object permissionIdsObj = item.get("matchedPermissionIds");
          if (permissionIdsObj instanceof List<?> ids) {
              for (Object idObj : ids) {
                  permissionIds.add(Long.valueOf(idObj.toString()));
              }
          }
      }
  }

  // 新 — 使用 EffectivePermissionItem 强类型访问
  PermResult<PermissionEffectivePermissionsResp> viewResult =
      permissionFeignClient.getEffectivePermissions(viewReq);
  PermissionEffectivePermissionsResp respData = viewResult.getData();
  for (var item : respData.items()) {
      if (targetResourceCode.equals(item.resourceCode())) {
          permissionIds.addAll(item.matchedPermissionIds());
      }
  }
  ```

- **GOTCHA**:
  - `PermissionEffectivePermissionsResp` 从泛型 `PermissionEffectivePermissionsResp<T>` 变为非泛型（perm-sdk-typing 计划完成后的状态）
  - `EffectivePermissionItem` 是 `PermissionEffectivePermissionsResp` 的嵌套 record，无需单独 import
  - `matchedPermissionIds()` 直接返回 `List<Long>`，无需手动类型转换
- **VALIDATE**: 编译通过

### Task 5: 迁移 RoleProxyServiceImpl.loadOperations

- **ACTION**: 将 `listOperations` 返回的 `Map<String, Object>` 改为 `ItemsResp<OperationPermissionResp>` 访问
- **IMPLEMENT**:

  ```java
  // 旧 — 从 Map 提取 items[].code 和 items[].id
  PermResult<Map<String, Object>> result = permissionFeignClient.listOperations(req);
  Object itemsObj = result.getData().get("items");
  if (itemsObj instanceof List<?> items) {
      Map<String, Long> ops = new HashMap<>();
      for (Object item : items) {
          if (item instanceof Map<?, ?> map) {
              Object codeObj = map.get("code");
              Object idObj = map.get("id");
              if (codeObj != null && idObj != null) {
                  ops.put(codeObj.toString(), Long.valueOf(idObj.toString()));
              }
          }
      }
      return ops;
  }

  // 新 — 使用 ItemsResp<OperationPermissionResp> 强类型访问
  PermResult<ItemsResp<OperationPermissionResp>> result = permissionFeignClient.listOperations(req);
  if (result == null || result.getData() == null) { ... }
  Map<String, Long> ops = new HashMap<>();
  for (OperationPermissionResp op : result.getData().items()) {
      ops.put(op.code(), op.id());
  }
  return ops;
  ```

- **GOTCHA**: `OperationPermissionResp` 字段名需与 permission-center 的 `OperationPermission` 实体序列化名一致（`code` 和 `id`）
- **VALIDATE**: 编译通过

### Task 6: 迁移 AuthServiceImpl — operationCode/operationCodes 缺陷修复

- **ACTION**: 将 `getEffectivePermissions` 的 Map 访问改为 DTO 访问，**修复 operationCode/operationCodes 潜在缺陷**
- **IMPLEMENT**:

  ```java
  // 旧代码 — AuthServiceImpl.getUserPermissions() 第 684 行
  // BUG: 读取 "operationCode"（单数），但 permission-center 返回 "operationCodes"（复数 List<String>）
  // 导致按钮级权限提取始终返回空列表——运行时静默失败
  PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>> result =
      permissionFeignClient.getEffectivePermissions(req);
  for (Map<String, Object> item : result.getData().items()) {
      Object opCode = item.get("operationCode");  // 始终返回 null！
      // ...
  }

  // 新代码 — 使用 EffectivePermissionItem 强类型访问
  PermResult<PermissionEffectivePermissionsResp> result =
      permissionFeignClient.getEffectivePermissions(req);
  for (var item : result.getData().items()) {
      List<String> opCodes = item.operationCodes();  // 正确读取复数 List<String>
      // ...
  }
  ```

- **GOTCHA**:
  - **这是预存缺陷**：`item.get("operationCode")` 返回 null，因为 JSON 字段名是 `operationCodes`（复数 `List<String>`）。类型化后自然修复
  - `PermissionEffectivePermissionsResp` 已从泛型变为非泛型（perm-sdk-typing 计划完成后）
  - `EffectivePermissionItem.operationCodes()` 返回 `List<String>`，业务逻辑需适配从单数 String 到复数 List 的变化
- **VALIDATE**: 编译通过；按钮级权限提取不再返回空列表

### Task 7: 迁移 Handler 类的 Map 访问

- **ACTION**: 迁移 UserSyncHandlerImpl、OrgSyncHandlerImpl、MenuSyncHandlerImpl 中提取 Map 字段的代码
- **IMPLEMENT**:

  ```java
  // UserSyncHandlerImpl — syncUser 返回 Map → UserResp
  // 旧
  PermResult<Map<String, Object>> result = permissionFeignClient.syncUser(req);
  Long userId = ((Number) result.getData().get("id")).longValue();
  // 新
  PermResult<UserResp> result = permissionFeignClient.syncUser(req);
  Long userId = result.getData().id();

  // OrgSyncHandlerImpl — createResource 返回 Map → ResourceResp
  // 旧
  PermResult<Map<String, Object>> result = permissionFeignClient.createResource(req);
  Long resourceId = ((Number) result.getData().get("id")).longValue();
  // 新
  PermResult<ResourceResp> result = permissionFeignClient.createResource(req);
  Long resourceId = result.getData().id();

  // MenuSyncHandlerImpl — createResource/updateResource 返回 Map → ResourceResp
  // 旧
  PermResult<Map<String, Object>> result = permissionFeignClient.createResource(req);
  Long resourceId = ((Number) result.getData().get("id")).longValue();
  // 新
  PermResult<ResourceResp> result = permissionFeignClient.createResource(req);
  Long resourceId = result.getData().id();
  ```

- **GOTCHA**:
  - 三个 Handler 都提取 `id` 字段——从 `((Number) result.getData().get("id")).longValue()` 改为 `result.getData().id()`
  - `UserResp.id()` 和 `ResourceResp.id()` 返回 `Long`，无需手动类型转换
  - 如果 Handler 中 `createResource`/`updateResource` 也检查了 `result.getCode()`，保持不变
- **VALIDATE**: 编译通过

### Task 8: 更新现有单元测试

- **ACTION**: 更新所有受影响类的现有单元测试（Mock 返回类型和断言）
- **IMPLEMENT**:
  - Mock 返回类型从 `Map<String, Object>` 改为对应 DTO（`RoleResp`、`ResourceResp`、`UserResp`、`PermissionEffectivePermissionsResp`、`ItemsResp<OperationPermissionResp>`）
  - 断言从 Map 值提取改为 DTO 字段访问
  - 特别更新 `AuthServiceImpl` 测试：Mock 返回 `EffectivePermissionItem`，验证 `operationCodes()` 读取正确（修复后按钮权限不再为空）
- **GOTCHA**: 不新增测试用例，仅适配类型变更导致的 Mock/断言失败
- **VALIDATE**: `mvn test -pl admin-service` 通过

### Task 9: 全量编译验证

- **ACTION**: 编译全部项目
- **IMPLEMENT**: `mvn compile`
- **VALIDATE**: 零编译错误

---

## Testing Strategy

### Unit Tests

| Test                                    | Input              | Expected Output    | Edge Case? |
| --------------------------------------- | ------------------ | ------------------ | ---------- |
| RoleProxyServiceImpl.createRoleForOrg   | 组织 ID + 名称     | 返回 RoleResp.id() | 否         |
| RoleProxyServiceImpl.grantMenuToRole    | 角色 ID + 菜单 IDs | 授权成功           | 否         |
| RoleProxyServiceImpl.revokeMenuFromRole | 角色 ID + 菜单 IDs | 撤销成功           | 否         |
| RoleProxyServiceImpl.loadOperations     | 资源类型           | 返回操作列表       | 否         |
| AuthServiceImpl.getUserPermissions      | 用户 ID            | 返回权限视图       | 否         |
| AuthServiceImpl.filterAllowedMenus      | 菜单列表           | 过滤后菜单         | 是         |

### Edge Cases Checklist

- [ ] Feign 返回 null data 时的处理
- [ ] Feign 返回非 200 code 时的处理
- [ ] PermissionEffectivePermissionsResp.items() 为空列表
- [ ] EffectivePermissionItem.operationCodes() 为空列表（按钮权限为空）
- [ ] RoleResp.id() 为 null
- [ ] ResourceResp.id() 为 null
- [ ] UserResp.id() 为 null

---

## Validation Commands

### Compile

```bash
mvn compile -pl admin-service
```

EXPECT: 零编译错误

### Unit Tests

```bash
mvn test -pl admin-service
```

EXPECT: 所有测试通过

### Full Compile

```bash
mvn compile
```

EXPECT: 全量编译通过

---

## Acceptance Criteria

- [ ] admin-service 中不再有 `Map<String, Object>` 类型的 Feign 返回值消费
- [ ] 所有 Feign 调用使用强类型 DTO
- [ ] RoleProxyServiceImpl 4 个 Map 返回值全部类型化（createRole, grantMenuToRole, revokeMenuFromRole, loadOperations）
- [ ] AuthServiceImpl Feign 调用类型化 + operationCode/operationCodes 缺陷修复
- [ ] 3 个 Handler（UserSync, OrgSync, MenuSync）的 Map 访问迁移为 DTO 访问
- [ ] 全量编译通过
- [ ] admin-service 测试通过

## Risks

| Risk                                                           | Likelihood | Impact | Mitigation                                                    |
| -------------------------------------------------------------- | ---------- | ------ | ------------------------------------------------------------- |
| UserPermissionViewResp 结构变更导致字段提取失败                | Medium     | High   | 仔细对照新旧结构，逐字段映射                                  |
| operationCode→operationCodes 迁移后业务逻辑需适配 List<String> | Medium     | High   | AuthServiceImpl 中从单值判断改为遍历 List，确认所有消费方兼容 |
| Map.get() 隐式类型转换丢失                                     | Low        | Medium | DTO 字段类型已明确定义，编译期检查                            |
| Handler 类中遗漏 Map 访问点                                    | Low        | Low    | 全文搜索 `Map<String, Object>` 和 `.get(`                     |
| RoleResp.id() / ResourceResp.id() 返回 null                    | Low        | Medium | 保留现有 null 检查逻辑                                        |

## Notes

- 此计划必须在 perm-sdk-typing 计划完成后执行
- 此计划不引入新业务逻辑，仅将 Map 访问替换为 DTO 访问
- 如果 Handler 类仅检查 `result.getCode()` 而不提取 Map 字段，则无需修改
