# Plan: Gateway 权限校验 DTO 对齐

## Summary

将 Gateway 模块中 permission-center 集成的本地 DTO（`AuthCheckRequest`/`AuthCheckResponse`）替换为 perm-common 中的 `CheckInterfaceReq`/`CheckInterfaceResp`，修复当前请求/响应契约不对齐的现存缺陷。本轮保持现有固定 L1 TTL，不在此计划中引入 `cacheTtlSeconds` 驱动的动态过期。

## User Story

As a Gateway 开发者, I want Gateway 与 permission-center 的 DTO 契约显式对齐, So that 请求体满足服务端当前校验约束，响应字段能被正确解析，并避免把契约错误误判为“鉴权服务不可用”。

## Problem → Solution

Gateway 使用本地 `AuthCheckRequest`（含 `userId` Long）发送请求，但 permission-center 期望 `CheckInterfaceReq`（含 `subjectTypeCode` + `subjectExternalId`）。响应侧 Gateway 读取 `denyReason`/`matchedRoleId`，但 permission-center 返回 `reason`/`matchedResources`/`cacheTtlSeconds`。这不是“隐性 Header 机制未报错”，而是当前 body 契约与服务端 DTO 的现存不兼容；`X-User-Id` 相关逻辑仅用于操作者上下文，不参与 `check-interface` 主体解析。本轮替换为 perm-common DTO，并保持现有固定 TTL 缓存策略；`cacheTtlSeconds` 留作二阶段优化。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `plan/permission-center-refactor-impact-analysis.md`
- **Estimated Files**: 8-10
- **前置条件**: perm-sdk-typing 计划完成（perm-common 包含 CheckInterfaceReq/CheckInterfaceResp）

---

## Mandatory Reading

| Priority | File                                                            | Why                                           |
| -------- | --------------------------------------------------------------- | --------------------------------------------- |
| P0       | `gateway/src/.../service/PermissionClient.java`                 | 当前 WebClient 调用逻辑                       |
| P0       | `gateway/src/.../filter/PermissionFilter.java`                  | 当前权限过滤逻辑和缓存策略                    |
| P0       | `gateway/src/.../model/AuthCheckRequest.java`                   | 需要替换的请求 DTO（位于 model/ 包，非 dto/） |
| P0       | `gateway/src/.../model/AuthCheckResponse.java`                  | 需要替换的响应 DTO（位于 model/ 包，非 dto/） |
| P1       | `perm-sdk/perm-common/src/.../dto/req/CheckInterfaceReq.java`   | 替换目标请求 DTO                              |
| P1       | `perm-sdk/perm-common/src/.../dto/resp/CheckInterfaceResp.java` | 替换目标响应 DTO                              |
| P1       | `gateway/src/.../config/GatewayProperties.java`                 | checkInterfacePath 配置                       |
| P2       | `permission-center/src/.../controller/AuthController.java`      | check-interface 端点实现                      |
| P2       | `permission-center/src/.../dto/req/CheckInterfaceReq.java`      | 服务端 DTO 定义（对照用）                     |
| P2       | `gateway/src/.../model/GatewayResponse.java`                    | 内部响应 DTO，不受迁移影响（确认用）          |

---

## Patterns to Mirror

### WEBCLIENT_PATTERN

// SOURCE: gateway/src/.../service/PermissionClient.java

```java
// WebClient 使用 @LoadBalanced，发送 X-Tenant-Id 和 X-Internal-Secret Header
public Mono<AuthCheckResponse> checkInterface(AuthCheckRequest req, Long tenantId) {
    return webClient.post()
        .uri(checkInterfacePath)
        .header("X-Tenant-Id", String.valueOf(tenantId))
        .header("X-Internal-Secret", internalSecret)
        .bodyValue(req)
        .retrieve()
        .bodyToMono(AuthCheckResponse.class);
}
```

### FILTER_PATTERN

// SOURCE: gateway/src/.../filter/PermissionFilter.java

```java
// GlobalFilter 在 AuthFilter 之后执行（order=-60）
// 使用 Caffeine L1 缓存，key: tenantId:userId:serviceCode:httpMethod:path
// Fail-close 策略：permission-center 不可达时拒绝请求（503）
// deny 结果不缓存（授权生效无延迟）
```

### CACHE_KEY_PATTERN

// SOURCE: gateway/src/.../filter/PermissionFilter.java

```java
// 缓存键格式
String cacheKey = "perm:check:" + tenantId + ":" + userId + ":" + serviceCode + ":" + httpMethod + ":" + path;
```

---

## Files to Change

| File                                            | Action | Justification                                                                                            |
| ----------------------------------------------- | ------ | -------------------------------------------------------------------------------------------------------- |
| `gateway/src/.../service/PermissionClient.java` | UPDATE | 替换 DTO 类型，返回 `Mono<PermResult<CheckInterfaceResp>>`，修复当前请求/响应契约不兼容                  |
| `gateway/src/.../filter/PermissionFilter.java`  | UPDATE | 使用新 DTO，保持现有固定 TTL 缓存与 fail-close 行为                                                      |
| `gateway/src/.../config/CacheConfig.java`       | CHECK  | 确认本轮继续使用固定 `expireAfterWrite`，不引入变长 TTL                                                  |
| `gateway/src/.../model/AuthCheckRequest.java`   | DELETE | 替换为 perm-common 的 CheckInterfaceReq                                                                  |
| `gateway/src/.../model/AuthCheckResponse.java`  | DELETE | 替换为 PermResult<CheckInterfaceResp>（注意：当前是扁平化的 PermResult，迁移后使用标准 PermResult 包装） |
| `gateway/src/.../model/` (整个目录)             | CHECK  | 确认 GatewayResponse.java 不受影响（内部响应，不涉及 permission-center 契约），无其他本地 DTO 需要迁移   |

## NOT Building

- 不修改 permission-center 的 check-interface 端点逻辑
- 不修改 Gateway 的其他 Filter（Auth、Header 等）
- 不修改 Gateway 的路由配置
- 不将 Gateway 权限逻辑迁移到 perm-gateway-spring-boot-starter（仅 DTO 对齐）
- 不在本轮引入 `cacheTtlSeconds` 驱动的动态 TTL（固定 TTL 保持不变）

---

## Step-by-Step Tasks

### Task 1: 确认当前问题是 body 契约不兼容

- **ACTION**: 阅读 AuthController 和 PermissionCheckAppServiceImpl，确认 `check-interface` 的主体解析来源
- **IMPLEMENT**: 验证 `subjectTypeCode` / `subjectExternalId` 只从 body 读取，并区分 `X-User-Id` 在 OperatorContext 中的用途
- **GOTCHA**: `X-User-Id` 相关逻辑用于操作者上下文，不是 `CheckInterfaceReq` 的回填来源；当前 Gateway body 不满足服务端 DTO 约束
- **VALIDATE**: 形成明确结论：现状是现存缺陷，不是隐性 Header 依赖

### Task 2: 更新 PermissionClient 请求 DTO

- **ACTION**: 将 `AuthCheckRequest` 替换为 `CheckInterfaceReq`
- **IMPLEMENT**:

  ```java
  // 旧代码 — AuthCheckRequest 有 top-level clientIp 和嵌套 context(ip, timestamp)
  AuthCheckRequest req = new AuthCheckRequest(userId, serviceCode, httpMethod, path, clientIp, context);

  // 新代码 — CheckInterfaceReq 无 clientIp/top-level 字段，全部移入 context Map
  Map<String, Object> contextMap = new HashMap<>();
  if (clientIp != null) contextMap.put("clientIp", clientIp);
  if (timestamp != null) contextMap.put("timestamp", timestamp);

  CheckInterfaceReq req = new CheckInterfaceReq(
      "USER",                    // subjectTypeCode
      String.valueOf(userId),    // subjectExternalId
      serviceCode,
      httpMethod,
      path,
      contextMap                 // clientIp 和 timestamp 移入 context
  );
  ```

- **MIRROR**: 参考 perm-common 的 CheckInterfaceReq 定义（6 个字段，无 clientIp top-level 字段）
- **GOTCHA**:
  - `userId` 需要转为 String 类型的 `subjectExternalId`
  - `AuthCheckRequest` 的 top-level `clientIp` 和嵌套 `Context(ip, timestamp)` 在 `CheckInterfaceReq` 中均移入 `context` Map
  - `CheckInterfaceReq` 的 `subjectTypeCode` 和 `subjectExternalId` 有 `@NotBlank` 校验，不可为 null
- **VALIDATE**: 编译通过

### Task 3: 更新 PermissionClient 响应 DTO

- **ACTION**: 将 `AuthCheckResponse` 替换为 `PermResult<CheckInterfaceResp>`
- **IMPLEMENT**:

  ```java
  // 旧代码 — AuthCheckResponse 是 PermResult 的扁平化版本（code/message/data 在同一层级）
  Mono<AuthCheckResponse> responseMono = permissionClient.checkInterface(req, tenantId);
  AuthCheckResponse resp = responseMono.block();
  boolean allowed = resp.getData().getAllowed();
  String reason = resp.getData().getDenyReason();
  Long matchedRoleId = resp.getData().getMatchedRoleId();

  // 新代码 — 返回标准 PermResult<CheckInterfaceResp>，反序列化结构不同
  Mono<PermResult<CheckInterfaceResp>> responseMono = permissionClient.checkInterface(req, tenantId);
  PermResult<CheckInterfaceResp> result = responseMono.block();
  CheckInterfaceResp resp = result.getData();
  boolean allowed = resp.allowed();
  String reason = resp.reason();
  // matchedResources 是列表，取第一个的 matchedRoleIds
  Long matchedRoleId = resp.matchedResources().isEmpty() ? null
      : resp.matchedResources().get(0).matchedRoleIds().isEmpty() ? null
      : resp.matchedResources().get(0).matchedRoleIds().get(0);
  int cacheTtlSeconds = resp.cacheTtlSeconds();
  ```

- **GOTCHA**:
  - **反序列化结构差异**：当前 `AuthCheckResponse` 是 PermResult 的扁平化版本（`code`/`message`/`data` 在同一层级），WebClient 使用 `bodyToMono(AuthCheckResponse.class)` 反序列化。迁移后使用 `bodyToMono(new ParameterizedTypeReference<PermResult<CheckInterfaceResp>>() {})` 或自定义 Jackson 反序列化
  - `matchedRoleId` 从单值 Long 变为 `List<MatchedResource>` → `matchedRoleIds` (List<Long>)，层级更深
  - `denyReason` → `reason`，字段名变更
  - `cacheTtlSeconds` 在本轮仅要求可被正确解析，不据此修改缓存 TTL
- **VALIDATE**: 编译通过；WebClient 能正确反序列化 `PermResult<CheckInterfaceResp>`

### Task 4: 保持现有固定 TTL 缓存策略

- **ACTION**: 在 DTO 对齐后保持现有 `Cache<String, Boolean>` + `expireAfterWrite` 模型不变
- **IMPLEMENT**:
  ```java
  // 保持现有固定 TTL 模型
  // CacheConfig 中继续使用 gateway.cache.l1.ttlSeconds
  // PermissionFilter 仅在 allowed=true 时写入缓存
  // cacheTtlSeconds 本轮只要求可被解析，不驱动变长过期
  ```
- **GOTCHA**:
  - 当前固定 TTL 定义在 CacheConfig，而不是 PermissionFilter；本轮不应在过滤器内引入 `CacheEntry` / `Expiry` 复杂度
  - deny 结果不缓存的行为不变（仅 allowed=true 写入缓存）
- **VALIDATE**: 编译通过；缓存行为与现状一致

### Task 5: 更新 PermissionFilter reason 映射

- **ACTION**: 更新 deny reason 的映射逻辑
- **IMPLEMENT**: 对照 permission-center 返回的 reason 值更新映射

  ```java
  // 旧代码
  String denyReason = resp.getData().getDenyReason();

  // 新代码
  String reason = checkInterfaceResp.reason();
  // 映射可能需要更新（如 "USER_DISABLED" → 保持一致）
  ```

- **VALIDATE**: reason 映射覆盖所有 permission-center 可能返回的值

### Task 6: 删除旧 DTO 文件

- **ACTION**: 删除 gateway 本地的 `AuthCheckRequest.java` 和 `AuthCheckResponse.java`（位于 `model/` 包，非 `dto/` 包）
- **IMPLEMENT**: 确认所有引用已迁移到 perm-common DTO 后删除。`GatewayResponse.java` 保留（内部响应 DTO，不涉及 permission-center 契约）
- **GOTCHA**: 旧 DTO 文件路径为 `gateway/src/.../model/AuthCheckRequest.java`，不是 `dto/` 包
- **VALIDATE**: `mvn compile -pl gateway` 通过

### Task 7: 更新 Gateway 单元测试

- **ACTION**: 更新 PermissionClientTest 和 PermissionFilterTest
- **IMPLEMENT**:
  - Mock 返回 `PermResult<CheckInterfaceResp>` 而非 `AuthCheckResponse`
  - 验证请求中包含 `subjectTypeCode="USER"` 和 `subjectExternalId`
  - 验证响应中包含 `cacheTtlSeconds` 时不影响反序列化与放行逻辑
- **VALIDATE**: `mvn test -pl gateway` 通过

### Task 8: 集成验证

- **ACTION**: 启动 Gateway + permission-center，验证 check-interface 调用正常
- **IMPLEMENT**: 手动或通过 E2E 测试验证：
  1. 已授权用户 → 请求通过
  2. 未授权用户 → 请求被拒（503 或 403）
  3. permission-center 不可达 → fail-close（503）
  4. 缓存命中 → 在固定 TTL 窗口内不再调用 permission-center
- **VALIDATE**: 所有场景行为正确

---

## Testing Strategy

### Unit Tests

| Test                          | Input                                                                                     | Expected Output                                                   | Edge Case? |
| ----------------------------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------------------------- | ---------- |
| PermissionClient 构造正确请求 | userId=123, serviceCode="svc", method=GET, path="/api"                                    | CheckInterfaceReq.subjectTypeCode="USER", subjectExternalId="123" | 是         |
| PermissionClient 解析正确响应 | CheckInterfaceResp(allowed=true, reason=null, matchedResources=[...], cacheTtlSeconds=10) | 过滤器放行且解析不失败                                            | 否         |
| PermissionFilter 保持固定 TTL | allowed=true                                                                              | 继续写入现有 `Cache<String, Boolean>`                             | 是         |
| PermissionFilter deny 不缓存  | allowed=false                                                                             | 不写入缓存                                                        | 否         |
| PermissionFilter fail-close   | WebClient 超时                                                                            | 返回 503                                                          | 否         |
| PermissionFilter reason 映射  | reason="USER_DISABLED"                                                                    | 映射到正确的日志/响应                                             | 否         |

### Edge Cases Checklist

- [ ] userId 为 null 时的处理
- [ ] subjectExternalId 非数字格式的处理
- [ ] matchedResources 为空列表
- [ ] `cacheTtlSeconds` 存在但本轮固定 TTL 策略不变
- [ ] permission-center 返回非标准 reason 值
- [ ] 并发请求下的缓存一致性

---

## Validation Commands

### Compile

```bash
mvn compile -pl gateway
```

EXPECT: 零编译错误

### Unit Tests

```bash
mvn test -pl gateway
```

EXPECT: 所有测试通过

### Full Compile

```bash
mvn compile
```

EXPECT: 全量编译通过

---

## Acceptance Criteria

- [ ] Gateway 不再使用本地 `AuthCheckRequest`/`AuthCheckResponse`
- [ ] 请求使用 `CheckInterfaceReq`，包含 `subjectTypeCode` + `subjectExternalId`
- [ ] 响应使用 `CheckInterfaceResp`，正确解析 `reason`/`matchedResources`/`cacheTtlSeconds`
- [ ] 保留现有固定 TTL 缓存策略，本轮不引入动态 TTL
- [ ] fail-close 行为不变
- [ ] deny 结果不缓存行为不变
- [ ] 所有 Gateway 测试通过

## Risks

| Risk                                                            | Likelihood | Impact | Mitigation                                                                                |
| --------------------------------------------------------------- | ---------- | ------ | ----------------------------------------------------------------------------------------- |
| 当前契约错误会在 Gateway 侧表现为 503，联调时误判为服务不可用   | Medium     | High   | Task 1 先明确 body 契约问题，并按 `CheckInterfaceReq` 对齐                                |
| AuthCheckResponse 扁平化 PermResult 导致反序列化失败            | Medium     | High   | 使用 `ParameterizedTypeReference<PermResult<CheckInterfaceResp>>` 或确保 Jackson 配置兼容 |
| reason 值变更导致映射不匹配                                     | Low        | Medium | 对比新旧 reason 枚举值                                                                    |
| 二阶段引入动态 TTL 时需同时调整 CacheConfig 和 cache value 类型 | Low        | Medium | 本轮显式不做，后续单独计划                                                                |
| admin-service 也受影响                                          | Low        | Low    | admin-service 使用 Feign 而非 WebClient，路径不同                                         |

## Notes

- Gateway 当前使用 `@LoadBalanced WebClient` 调用 permission-center，不经过 Feign
- Gateway 的 `X-Internal-Secret` Header 签名机制需保持不变
- `cacheTtlSeconds` 在本轮仅要求可被正确解析，不驱动缓存过期；如需动态 TTL，二阶段需同时调整 CacheConfig 与 cache value 类型
