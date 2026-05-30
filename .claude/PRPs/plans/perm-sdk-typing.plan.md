# Plan: perm-sdk DTO 类型化与 Feign 客户端强化

## Summary

补齐 perm-common 中缺失的关键 DTO，将 Feign 客户端返回类型从 `Map<String, Object>` 升级为强类型，并把 `UserPermissionViewResp`、`PermissionEffectivePermissionsResp` 与 permission-center 当前结构一起对齐，使 SDK 层与服务端契约完全一致。

## User Story

As a 业务服务开发者, I want Feign 客户端返回强类型 DTO 而非 `Map<String, Object>`, So that 我无需手动解析 Map、获得编译期类型安全和 IDE 自动补全。

## Problem → Solution

Feign 客户端 8/14 方法返回 `Map<String, Object>`，部分关键 DTO 仅定义在 permission-center 内部，且 perm-common 现有 `UserPermissionViewResp` / `PermissionEffectivePermissionsResp` 结构落后于服务端 → 补齐 perm-common DTO + 类型化 Feign 返回值 + 同步对齐现有 DTO 结构

## Metadata

- **Complexity**: Medium
- **Source PRD**: `docs/design/permission-center-refactor-impact-analysis.md`
- **Estimated Files**: 15-20
- **前置条件**: permission-center DDD 重构已完成（API 路径和响应结构稳定）

---

## Mandatory Reading

| Priority | File                                                                                | Why                                 |
| -------- | ----------------------------------------------------------------------------------- | ----------------------------------- |
| P0       | `perm-sdk/perm-common/src/.../dto/resp/`                                            | 当前响应 DTO 目录                   |
| P0       | `perm-sdk/perm-common/src/.../dto/req/`                                             | 当前请求 DTO 目录                   |
| P0       | `perm-sdk/perm-client-spring-boot-starter/src/.../feign/PermissionFeignClient.java` | 需类型化的 Feign 接口               |
| P1       | `permission-center/src/.../controller/AuthController.java`                          | check-interface 端点定义            |
| P1       | `permission-center/src/.../controller/RoleController.java`                          | create 返回 RoleResp                |
| P1       | `permission-center/src/.../controller/ResourceController.java`                      | create/batch 返回 ResourceResp      |
| P1       | `permission-center/src/.../controller/PermissionViewController.java`                | effective-permissions 返回结构      |
| P1       | `permission-center/src/.../controller/PermissionGrantController.java`               | save 返回结构                       |
| P1       | `permission-center/src/.../controller/OperationController.java`                     | list 返回结构                       |
| P2       | `gateway/src/.../service/PermissionClient.java`                                     | Gateway 调用 check-interface 的 DTO |
| P2       | `gateway/src/.../model/AuthCheckRequest.java`                                       | Gateway 本地请求 DTO                |
| P2       | `gateway/src/.../model/AuthCheckResponse.java`                                      | Gateway 本地响应 DTO                |

---

## Patterns to Mirror

### NAMING_CONVENTION

// SOURCE: perm-sdk/perm-common/src/.../dto/resp/AuthCheckResp.java

```java
// 响应 DTO 使用 record，字段名与 permission-center 的 JSON 序列化名一致
public record AuthCheckResp(
    boolean allowed,
    String reason,
    List<Long> matchedRoleIds,
    List<Long> matchedPermissionIds,
    boolean conditionEvaluated
) {}
```

### REQUEST_DTO_PATTERN

// SOURCE: perm-sdk/perm-common/src/.../dto/req/AuthCheckReq.java

```java
// 请求 DTO 使用 record，使用业务键（subjectTypeCode/subjectExternalId）而非内部 ID
public record AuthCheckReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String resourceTypeCode,
    @NotBlank String operationCode,
    String resourceCode,
    String domainCode,
    String codeType,
    String inheritMode,
    Map<String, Object> context
) {}
```

### FEIGN_CLIENT_PATTERN

// SOURCE: perm-sdk/perm-client-spring-boot-starter/src/.../feign/PermissionFeignClient.java

```java
// Feign 方法使用 @PostMapping，返回 PermResult<T>，T 应为 perm-common 中的强类型
@FeignClient(name = "permission-center", contextId = "permissionFeignClient")
public interface PermissionFeignClient {
    @PostMapping("/api/perm/auth/check")
    PermResult<AuthCheckResp> checkAuth(@RequestBody AuthCheckReq req);
}
```

### ENUM_PATTERN

// SOURCE: perm-sdk/perm-common/src/.../enums/DefaultOpCode.java

```java
// 枚举类放在 perm-common 的 enums 包下
public enum DefaultOpCode {
    VIEW, EDIT, DELETE
}
```

---

## Files to Change

| File                                                                                | Action | Justification                                             |
| ----------------------------------------------------------------------------------- | ------ | --------------------------------------------------------- |
| `perm-sdk/perm-common/src/.../dto/resp/CheckInterfaceResp.java`                     | CREATE | Gateway check-interface 响应                              |
| `perm-sdk/perm-common/src/.../dto/req/CheckInterfaceReq.java`                       | CREATE | Gateway check-interface 请求                              |
| `perm-sdk/perm-common/src/.../dto/resp/UserResp.java`                               | CREATE | syncUser 返回类型                                         |
| `perm-sdk/perm-common/src/.../dto/resp/RoleResp.java`                               | CREATE | createRole 返回类型                                       |
| `perm-sdk/perm-common/src/.../dto/resp/ResourceResp.java`                           | CREATE | createResource/batchCreate/update 返回类型                |
| `perm-sdk/perm-common/src/.../dto/resp/ItemsResp.java`                              | CREATE | 通用列表包装器                                            |
| `perm-sdk/perm-common/src/.../dto/resp/PaginatedResp.java`                          | CREATE | 通用分页包装器                                            |
| `perm-sdk/perm-common/src/.../dto/resp/RolePermissionItemResp.java`                 | CREATE | 授权条目类型                                              |
| `perm-sdk/perm-common/src/.../dto/resp/RolePermissionItemsResp.java`                | CREATE | batchGrant 返回类型                                       |
| `perm-sdk/perm-common/src/.../dto/resp/UserPermissionViewResp.java`                 | UPDATE | 与 permission-center 对齐                                 |
| `perm-sdk/perm-common/src/.../dto/resp/PermissionEffectivePermissionsResp.java`     | UPDATE | 去泛型化，改为与 permission-center 一致的嵌套 record 结构 |
| `perm-sdk/perm-client-spring-boot-starter/src/.../feign/PermissionFeignClient.java` | UPDATE | 类型化 8 个返回值                                         |
| `perm-sdk/perm-gateway-spring-boot-starter/`                                        | CHECK  | 确认继续复用 perm-common DTO，无需新增镜像 DTO            |

## NOT Building

- 不修改 permission-center 内部代码
- 不修改 admin-service 业务代码（由 admin-service-alignment 计划处理）
- 不修改 gateway 模块代码（由 gateway-alignment 计划处理）
- 不添加新的 Feign 端点（仅类型化现有端点）
- 不实现 perm-data-spring-boot-starter 的业务逻辑
- 不在 starter 中再镜像一套 Gateway DTO

---

## Step-by-Step Tasks

### Task 1: 读取 permission-center 响应 DTO 定义

- **ACTION**: 读取 permission-center 中所有响应 DTO 的完整定义
- **IMPLEMENT**: 读取以下文件，记录每个 record 的字段名、类型和注解
  - `CheckInterfaceResp`, `RoleResp`, `ResourceResp`, `RolePermissionItemResp`, `RolePermissionItemsResp`
  - `ItemsResp<T>`, `PaginatedResp<T>`
  - `UserPermissionViewResp`, `PermissionEffectivePermissionsResp`（含嵌套 `EffectivePermissionItem` / `SourceRole`）
  - `CheckInterfaceReq`
- **VALIDATE**: 所有字段名和类型已记录，与 permission-center 的 JSON 序列化输出一致

### Task 2: 创建通用包装器 DTO

- **ACTION**: 在 perm-common 中创建 `ItemsResp<T>` 和 `PaginatedResp<T>`
- **IMPLEMENT**:

  ```java
  // ItemsResp — 无分页的列表包装
  public record ItemsResp<T>(List<T> items) {}

  // PaginatedResp — 分页列表包装
  public record PaginatedResp<T>(List<T> items, long total, int pageNum, int pageSize, boolean hasNext) {}
  ```

- **MIRROR**: 参考 permission-center 中的 `ItemsResp` 和 `PaginatedResp` 定义
- **GOTCHA**: 泛型参数必须与 permission-center 版本兼容，字段名一致
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-common` 通过

### Task 3: 创建 CheckInterfaceReq/Resp

- **ACTION**: 在 perm-common 中创建 Gateway check-interface 的请求和响应 DTO
- **IMPLEMENT**:

  ```java
  // CheckInterfaceReq — 对齐 permission-center 的同名 record
  public record CheckInterfaceReq(
      @NotBlank String subjectTypeCode,
      @NotBlank String subjectExternalId,
      @NotBlank String serviceCode,
      @NotBlank String httpMethod,
      @NotBlank String path,
      Map<String, Object> context
  ) {}

  // CheckInterfaceResp — 对齐 permission-center 的同名 record
  public record CheckInterfaceResp(
      boolean allowed,
      String reason,
      List<MatchedResource> matchedResources,
      int cacheTtlSeconds
  ) {
      public record MatchedResource(
          Long resourceId,
          String resourceTypeCode,
          String resourceCode,
          String operationCode,
          boolean allowed,
          List<Long> matchedRoleIds,
          List<Long> matchedPermissionIds
      ) {}
  }
  ```

- **GOTCHA**: `CheckInterfaceReq` 使用 `subjectTypeCode` + `subjectExternalId`，不是 Gateway 当前的 `userId`
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-common` 通过

### Task 4: 创建 RoleResp

- **ACTION**: 在 perm-common 中创建角色响应 DTO
- **IMPLEMENT**: 对齐 permission-center 的 `RoleResp` 字段
- **GOTCHA**: 字段名必须与 JSON 序列化名一致（camelCase）
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-common` 通过

### Task 5: 创建 ResourceResp

- **ACTION**: 在 perm-common 中创建资源响应 DTO
- **IMPLEMENT**: 对齐 permission-center 的 `ResourceResp` 字段
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-common` 通过

### Task 6: 创建 RolePermissionItemsResp

- **ACTION**: 在 perm-common 中创建授权响应 DTO
- **IMPLEMENT**: 包含 `List<RolePermissionItemResp>` 和相关字段
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-common` 通过

### Task 7: 更新 UserPermissionViewResp

- **ACTION**: 将 perm-common 的 `UserPermissionViewResp` 与 permission-center 对齐
- **IMPLEMENT**: 更新为 permission-center 的结构（含 subject 信息 + resources 列表）
- **GOTCHA**: 这会破坏 admin-service 中的现有消费代码，需同步更新。考虑添加兼容层或在 admin-service 计划中处理
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-common` 通过

### Task 8: 更新 PermissionEffectivePermissionsResp

- **ACTION**: 将泛型 `PermissionEffectivePermissionsResp<T>` 改为非泛型，内嵌 `EffectivePermissionItem`
- **IMPLEMENT**:

  ```java
  // 旧 — 泛型版本
  public record PermissionEffectivePermissionsResp<T>(
      String targetType, List<T> items, int total, int pageNum, int pageSize, boolean hasNext
  ) {}

  // 新 — 与 permission-center 对齐，直接内嵌 EffectivePermissionItem
  public record PermissionEffectivePermissionsResp(
      String targetType,
      List<EffectivePermissionItem> items,
      int total,
      int pageNum,
      int pageSize,
      boolean hasNext
  ) {
      public record EffectivePermissionItem(
          String resourceTypeCode,
          String resourceCode,
          String resourceName,
          String codeType,
          List<String> operationCodes,   // 注意：复数，List<String>
          boolean scopeAll,
          List<SourceRole> sourceRoles,
          int sourceRoleCount,
          boolean sourceRolesTruncated,
          List<Long> matchedPermissionIds
      ) {}

      public record SourceRole(
          String roleTypeCode,
          String roleExternalId,
          String roleName,
          List<String> via
      ) {}
  }
  ```

- **GOTCHA**:
  - `operationCodes` 是复数 `List<String>`，不是单数 `String`。admin-service 当前 `item.get("operationCode")` 读取单数，导致按钮权限提取始终为空——类型化后自然修复
  - 此变更会破坏 admin-service 中 `PermissionEffectivePermissionsResp<Map<String, Object>>` 的所有引用，需同步更新
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-common` 通过

### Task 9: 类型化 Feign 客户端返回值

- **ACTION**: 将 PermissionFeignClient 中 8 个 `Map<String, Object>` 返回类型替换为强类型
- **IMPLEMENT**:
  | 方法 | 旧返回类型 | 新返回类型 |
  |------|-----------|-----------|
  | `syncUser` | `PermResult<Map<String, Object>>` | `PermResult<UserResp>` |
  | `createRole` | `PermResult<Map<String, Object>>` | `PermResult<RoleResp>` |
  | `createResource` | `PermResult<Map<String, Object>>` | `PermResult<ResourceResp>` |
  | `batchCreateResources` | `PermResult<Map<String, Object>>` | `PermResult<ItemsResp<ResourceResp>>` |
  | `updateResource` | `PermResult<Map<String, Object>>` | `PermResult<ResourceResp>` |
  | `listOperations` | `PermResult<Map<String, Object>>` | `PermResult<ItemsResp<OperationPermissionResp>>` |
  | `batchGrant` | `PermResult<Map<String, Object>>` | `PermResult<RolePermissionItemsResp>` |
  | `getEffectivePermissions` | `PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>>` | `PermResult<PermissionEffectivePermissionsResp>` |
- **MIRROR**: 参考 `checkAuth`/`batchCheckAuth`/`getUserRoles` 已有的强类型模式
- **GOTCHA**:
  - admin-service 中消费这些方法的代码需要同步更新（从 Map 提取改为 DTO 访问）
  - `PermissionEffectivePermissionsResp` 将从泛型 `PermissionEffectivePermissionsResp<T>` 改为非泛型（直接内嵌 `EffectivePermissionItem`），与 permission-center 保持一致
  - **`operationCode`/`operationCodes` 兼容性**：admin-service 当前读取 `item.get("operationCode")`（单数），但 `EffectivePermissionItem.operationCodes` 是复数 `List<String>`。类型化后 admin-service 代码改为 `item.operationCodes()` 即可自然修复此潜在缺陷
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-client-spring-boot-starter` 通过

### Task 10: 确认 starter 继续复用 perm-common DTO

- **ACTION**: 检查 `perm-gateway-spring-boot-starter` 对 `perm-common` 的依赖关系，确认无需新增 DTO 镜像
- **IMPLEMENT**:
  - 保持 starter 依赖 `perm-common`
  - Gateway 代码直接使用 `perm-common` 中的 `CheckInterfaceReq`/`CheckInterfaceResp`
  - 本轮不在 starter 中再复制一套同名 DTO
- **GOTCHA**: 在 starter 中再建镜像 DTO 只会增加重复契约和后续维护成本
- **VALIDATE**: `mvn compile -pl perm-sdk/perm-gateway-spring-boot-starter` 通过（无需新增代码）

### Task 11: 全量编译验证

- **ACTION**: 编译所有受影响的模块
- **IMPLEMENT**: `mvn compile -pl perm-sdk/perm-common,perm-sdk/perm-client-spring-boot-starter,perm-sdk/perm-gateway-spring-boot-starter`
- **VALIDATE**: 零编译错误

### Task 12: admin-service 编译验证（可能需要适配代码）

- **ACTION**: 验证 admin-service 编译通过
- **IMPLEMENT**: 如果 Feign 返回类型变更导致 admin-service 编译失败，需要先修复编译错误（将 Map 访问改为 DTO 访问）
- **GOTCHA**: 这是临时修复，详细的 admin-service 适配在 admin-service-alignment 计划中
- **VALIDATE**: `mvn compile -pl admin-service` 通过

---

## Testing Strategy

### Unit Tests

| Test                  | Input                    | Expected Output | Edge Case? |
| --------------------- | ------------------------ | --------------- | ---------- |
| DTO 序列化/反序列化   | JSON 字符串 → DTO record | 字段正确映射    | 无         |
| Feign Client 方法签名 | 编译期检查               | 类型安全        | 否         |

### 集成验证

- `mvn compile` 全量通过
- admin-service 所有 Feign 调用返回类型正确

### Edge Cases Checklist

- [x] JSON 字段名与 permission-center 一致（camelCase）
- [x] record 字段顺序不影响 JSON 序列化
- [x] nullable 字段使用包装类型（Long 而非 long）
- [x] 列表字段使用 List 而非数组
- [ ] admin-service 中 Map 访问代码已全部改为 DTO 访问

---

## Validation Commands

### Static Analysis

```bash
mvn compile -pl perm-sdk/perm-common,perm-sdk/perm-client-spring-boot-starter,perm-sdk/perm-gateway-spring-boot-starter
```

EXPECT: 零编译错误

### Full Compile

```bash
mvn compile
```

EXPECT: 全量编译通过（包括 admin-service 适配后的编译）

---

## Acceptance Criteria

- [ ] perm-common 包含所有 Feign 客户端需要的响应 DTO
- [ ] CheckInterfaceReq/CheckInterfaceResp 在 perm-common 中可用
- [ ] PermissionFeignClient 所有方法返回强类型
- [ ] ItemsResp/PaginatedResp 通用包装器可用
- [ ] UserPermissionViewResp 与 permission-center 结构对齐
- [ ] 全量编译通过
- [ ] admin-service 适配后编译通过

## Risks

| Risk                                          | Likelihood | Impact | Mitigation                                |
| --------------------------------------------- | ---------- | ------ | ----------------------------------------- |
| DTO 字段名与 JSON 不匹配                      | Low        | High   | 严格对照 permission-center 的 record 定义 |
| UserPermissionViewResp 变更破坏 admin-service | High       | Medium | 同步修改 admin-service 消费代码           |
| Gateway 本地 DTO 与 perm-common DTO 冲突      | Medium     | Low    | Gateway 计划中统一迁移到 perm-common DTO  |
| 新增 DTO 遗漏字段                             | Low        | Medium | 逐一对照 permission-center DTO 定义       |
