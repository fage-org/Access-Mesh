---
name: testing-standards
description: >-
  测试标准规范。
  Rule type: ALWAYS — applies to all testing code changes.
  Covers: TDD workflow, coverage thresholds, test independence, mocking, naming,
  edge cases, behavior testing, library testing, test data factories.
origin: project
metadata:
  project: AccessMesh
  version: "1.0.0"
---

# 测试标准规范

## 1. TDD 工作流

**MUST** 遵循 RED-GREEN-REFACTOR 循环：

1. **RED**: 先写失败的测试
2. **GREEN**: 用最少的代码让测试通过
3. **REFACTOR**: 重构代码，保持测试通过

```java
// ✅ 正确 — TDD 流程
// Step 1 (RED): 先写测试
@Test
void shouldThrowExceptionWhenRoleNotFound() {
    assertThatThrownBy(() -> roleService.getRole(1L, 999L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("Role not found");
}

// Step 2 (GREEN): 实现最小代码
public RoleResp getRole(Long tenantId, Long roleId) {
    AbstractRole role = abstractRoleMapper.selectOneById(roleId);
    if (role == null) {
        throw new BizException(ErrorCode.ROLE_NOT_FOUND, "Role not found");
    }
    return toRoleResp(role);
}

// Step 3 (REFACTOR): 优化实现
public RoleResp getRole(Long tenantId, Long roleId) {
    return toRoleResp(findRoleOrThrow(roleId));
}

private AbstractRole findRoleOrThrow(Long roleId) {
    return Optional.ofNullable(abstractRoleMapper.selectOneById(roleId))
        .orElseThrow(() -> new BizException(ErrorCode.ROLE_NOT_FOUND, "Role not found"));
}

// ❌ 禁止 — 先写实现后补测试
public RoleResp getRole(Long tenantId, Long roleId) {
    // 已有实现...
}
// 之后才写测试（往往只覆盖 happy path）
@Test
void shouldReturnRole() {
    // 只测试正常情况
}
```

```typescript
// ✅ 正确 — Vue 组件 TDD
// Step 1 (RED): 先写测试
it("should show error when name is empty", async () => {
  const wrapper = mount(UserForm);
  await wrapper.find('input[name="name"]').setValue("");
  await wrapper.find("form").trigger("submit");
  expect(wrapper.find(".error-message").text()).toContain("名称不能为空");
});

// Step 2 (GREEN): 实现验证逻辑
const validateName = (name: string) => {
  if (!name.trim()) {
    errors.name = "名称不能为空";
    return false;
  }
  return true;
};
```

## 2. 测试覆盖率

**SHOULD** 保持业务逻辑代码 80%+ 测试覆盖率。

```yaml
# ✅ 正确 — 覆盖率配置示例 (JaCoCo)
coverage:
  target: 80%
  exclude:
    - "**/controller/**"      # Controller 层可选
    - "**/dto/**"             # DTO 纯数据类无需测试
    - "**/config/**"          # 配置类无需测试
    - "**/Application.java"   # 启动类无需测试

# ✅ 正确 — 覆盖率配置示例 (pytest-cov)
[tool.coverage.run]
source = ["src"]
omit = ["*/tests/*", "*/__init__.py"]

[tool.coverage.report]
fail_under = 80
```

| 代码类型               | 是否需要测试 | 说明                    |
| ---------------------- | ------------ | ----------------------- |
| AppService 业务逻辑    | ✅ 必须      | 核心业务逻辑            |
| DomainService 领域规则 | ✅ 必须      | 领域规则验证            |
| Controller 参数适配    | ⚠️ 可选      | 可通过集成测试覆盖      |
| DTO / VO / Request     | ❌ 不需要    | 纯数据类                |
| Configuration          | ❌ 不需要    | 配置类                  |
| Mapper 接口            | ⚠️ 可选      | MyBatis-Flex 生成的接口 |

## 3. 测试独立性

**MUST** 确保测试可按任意顺序独立运行，**禁止**测试间共享可变状态。

```java
// ✅ 正确 — 每个测试独立准备数据
@Test
void shouldCreateRole() {
    RoleCreateReq req = new RoleCreateReq("admin", null, "管理员角色");
    roleService.createRole(TENANT_ID, req, OPERATOR_ID);
}

@Test
void shouldDeleteRole() {
    Long roleId = createTestRole("temp");
    roleService.deleteRole(TENANT_ID, roleId);
}

// ❌ 禁止 — 共享可变状态
private static Long sharedRoleId;  // WRONG

@BeforeAll
static void setup() {
    sharedRoleId = createTestRole("shared");
}

@Test
void test1() {
    roleService.deleteRole(TENANT_ID, sharedRoleId);  // 修改共享状态
}

@Test
void test2() {
    roleService.getRole(TENANT_ID, sharedRoleId);  // 依赖 test1 执行顺序
}
```

```typescript
// ✅ 正确 — Vue 组件测试独立
beforeEach(() => {
  wrapper = mount(UserForm, { props: { user: { name: "test" } } });
});

afterEach(() => {
  wrapper.unmount();
});
```

## 4. Mock 外部依赖

**MUST** Mock 外部依赖，**禁止** Mock 被测单元本身。

```java
// ✅ 正确 — Mock 外部依赖
@ExtendWith(MockitoExtension.class)
class RoleManageAppServiceTest {
    @Mock
    private AbstractRoleMapper abstractRoleMapper;

    @Mock
    private PermQueryEngine permQueryEngine;

    @InjectMocks
    private RoleManageAppServiceImpl roleManageAppService;  // 被测单元（不 Mock）

    @Test
    void shouldCreateRole() {
        when(permQueryEngine.hasPermission(any(), any(), any(), any(), any()))
            .thenReturn(true);

        RoleResp result = roleManageAppService.createRole(TENANT_ID, req, OPERATOR_ID);

        assertThat(result.getId()).isEqualTo(1L);
    }
}

// ❌ 禁止 — Mock 被测单元
RoleManageAppService service = mock(RoleManageAppService.class);  // WRONG
when(service.createRole(any())).thenReturn(response);  // 测试的不是真实代码
```

| 类型          | 是否 Mock  | 说明                     |
| ------------- | ---------- | ------------------------ |
| 数据库        | ✅ Mock    | 使用 @Mock 或内存数据库  |
| 外部 API/服务 | ✅ Mock    | Mock WebClient, Feign 等 |
| 文件系统      | ✅ Mock    | Mock File, Path 操作     |
| 被测单元本身  | ❌ 不 Mock | 使用真实实例             |
| 值对象/DTO    | ❌ 不 Mock | 使用真实实例             |

## 5. 测试命名规范

**MUST** 使用描述性测试名称，清晰表达测试意图和预期结果。

命名模式：`should_[expected_behavior]_when_[condition]`

```java
// ✅ 正确 — 描述性命名
@Test
void shouldThrowException_whenRoleNotFound() {
    assertThatThrownBy(() -> roleService.getRole(TENANT_ID, 999L))
        .isInstanceOf(BizException.class)
        .hasMessage("Role not found");
}

@Test
void shouldReturnRole_whenUserHasViewPermission() {
    when(permQueryEngine.hasPermission(any(), any(), eq(ROLE), eq(roleId), eq(VIEW)))
        .thenReturn(true);

    RoleResp result = roleService.getRole(TENANT_ID, roleId);

    assertThat(result).isNotNull();
}

// ❌ 禁止 — 无意义命名
@Test
void testCreate() { }  // 测试什么？

@Test
void test1() { }  // 完全无意义
```

```typescript
// ✅ 正确 — Vue 组件测试命名
describe("UserForm", () => {
  it("should emit save event when form is valid", () => {});
  it("should show error when name is empty", () => {});
  it("should disable submit button while submitting", () => {});
});
```

## 6. 边界条件测试

**MUST** 测试边界条件和异常场景，**禁止**只测试 happy path。

对公开业务接口，**SHOULD** 断言公开异常语义：业务拒绝优先断言 `BizException`，安全拒绝断言 `SecurityException`，技术故障断言 `SystemException`。仅在私有 helper、enum/factory、框架适配层的编程契约校验中断言 `IllegalArgumentException` / `IllegalStateException`。

```java
// ✅ 正确 — 完整测试边界条件
@Nested
class CreateRole {

    @Test
    void shouldCreateRole_whenValidInput() {
        // Happy path
    }

    @Test
    void shouldThrowException_whenNameIsNull() {
        RoleCreateReq req = new RoleCreateReq(null, null, "desc");
        assertThatThrownBy(() -> roleService.createRole(TENANT_ID, req, OPERATOR_ID))
            .isInstanceOf(BizException.class);
    }

    @Test
    void shouldThrowException_whenNameIsEmpty() {
        RoleCreateReq req = new RoleCreateReq("", null, "desc");
        assertThatThrownBy(() -> roleService.createRole(TENANT_ID, req, OPERATOR_ID))
            .isInstanceOf(BizException.class);
    }

    @Test
    void shouldThrowException_whenNameTooLong() {
        String longName = "a".repeat(256);
        RoleCreateReq req = new RoleCreateReq(longName, null, "desc");
        assertThatThrownBy(() -> roleService.createRole(TENANT_ID, req, OPERATOR_ID))
            .isInstanceOf(BizException.class);
    }

    @Test
    void shouldThrowException_whenDuplicateName() {
        createTestRole("admin");
        RoleCreateReq req = new RoleCreateReq("admin", null, "desc");
        assertThatThrownBy(() -> roleService.createRole(TENANT_ID, req, OPERATOR_ID))
            .isInstanceOf(BizException.class);
    }
}
```

| 类型       | 示例                     |
| ---------- | ------------------------ |
| 空值       | null, Optional.empty()   |
| 空集合     | List.of(), Set.of()      |
| 边界值     | Integer.MAX_VALUE, 0, -1 |
| 字符串边界 | "", " ", 超长字符串      |
| 重复值     | 主键冲突，唯一索引冲突   |
| 状态冲突   | 已删除，已禁用           |
| 权限边界   | 无权限，部分权限         |

## 7. 测试行为而非实现

**MUST** 测试公开行为（输入/输出），**禁止**测试私有实现细节。

```java
// ✅ 正确 — 测试行为（公开 API）
@Test
void shouldReturnUserRoleCount_whenQueryPermissions() {
    Long userId = createTestUser("user1");
    Long roleId = createTestRole("admin");
    assignRole(userId, roleId);

    Map<Long, Set<Long>> roles = subjectDomainService.batchResolveEffectiveRoles(
        TENANT_ID, Set.of(userId)
    );

    assertThat(roles.get(userId)).contains(roleId);
}

// ❌ 禁止 — 测试私有实现细节
@Test
void shouldCallCacheWhenQueryRoles() {
    subjectDomainService.resolveEffectiveRoles(TENANT_ID, userId);
    verify(cacheService).get(any());  // 内部缓存实现可能改变
}

// ❌ 禁止 — 测试私有方法
@Test
void testPrivateMethod() throws Exception {
    Method method = SubjectDomainServiceImpl.class.getDeclaredMethod("buildCacheKey", Long.class);
    method.setAccessible(true);  // 反射测试私有方法是脆弱的
}
```

```typescript
// ✅ 正确 — Vue 组件测试行为
it("should emit save event when form is submitted", async () => {
  const wrapper = mount(UserForm, { props: { user: testUser } });
  await wrapper.find("form").trigger("submit");

  expect(wrapper.emitted("save")).toBeTruthy();
});

// ❌ 禁止 — 测试组件内部状态
it("should set internalLoading to true", async () => {
  expect(wrapper.vm.internalLoading).toBe(true); // 组件可能重构
});
```

## 8. 不测试第三方代码

**MUST NOT** 测试第三方库/框架的功能。**SHOULD** 测试你对它们的集成和使用方式。

```java
// ❌ 禁止 — 测试库功能
@Test
void testArrayListAdd() {
    List<String> list = new ArrayList<>();
    list.add("test");
    assertThat(list).hasSize(1);  // JDK 已测试，浪费时间
}

@Test
void testMyBatisFlexQuery() {
    QueryWrapper qw = QueryWrapper.create().where(Tables.ABSTRACT_ROLE.ID.eq(1L));
    assertThat(qw).isNotNull();  // 无意义
}

// ✅ 正确 — 测试你的集成代码
@Test
void shouldMapRoleEntityToResponse() {
    AbstractRole role = new AbstractRole();
    role.setId(1L);
    role.setName("admin");

    RoleResp response = RoleMapper.toResponse(role);  // 你的转换逻辑

    assertThat(response.getId()).isEqualTo(1L);
}
```

| 类型                             | 是否需要测试 |
| -------------------------------- | ------------ |
| JDK/标准库                       | ❌ 不需要    |
| 框架核心 (Spring, Vue)           | ❌ 不需要    |
| 工具库 (Guava, @pureadmin/utils) | ❌ 不需要    |
| 你的业务逻辑                     | ✅ 必须      |
| 你的组件                         | ✅ 必须      |
| 你的集成代码                     | ✅ 必须      |

## 9. 测试数据工厂

**SHOULD** 优先使用工厂方法或 record 直接构造创建测试数据；当 DTO 已提供 `@Builder` 且直接构造会明显降低可读性时，可以使用 Builder，**避免**在每个测试中手动构造对象。

```java
// ✅ 正确 — 使用工厂方法
@Test
void shouldAssignRoleToUser() {
    Long userId = TestUserFactory.create("user1");
    Long roleId = TestRoleFactory.create("admin", permissions);

    userRoleService.assignRole(TENANT_ID, userId, roleId);

    assertThat(userRoleService.getUserRoles(TENANT_ID, userId)).contains(roleId);
}

// ✅ 正确 — 优先直接构造 record/不可变 DTO
@Test
void shouldCreateRoleWithCustomSettings() {
    RoleCreateReq req = new RoleCreateReq("admin", null, "EXT-001");

    roleService.createRole(TENANT_ID, req, OPERATOR_ID);
}

// ✅ 允许 — DTO 已提供 @Builder 时，可用于复杂测试数据
@Test
void shouldCreateRoleWithBuilder_whenBuilderImprovesReadability() {
    RoleCreateReq req = RoleCreateReq.builder()
        .name("admin")
        .parentId(null)
        .externalId("EXT-001")
        .build();

    roleService.createRole(TENANT_ID, req, OPERATOR_ID);
}

// ❌ 禁止 — 每个测试手动构造对象
@Test
void test1() {
    AbstractRole role = new AbstractRole();
    role.setId(1L);
    role.setTenantId(TENANT_ID);
    role.setName("admin");
    // ... 很多字段重复设置
}

// ✅ 正确 — 测试数据工厂类
public class TestRoleFactory {
    public static Long create(String name) {
        return create(name, null, null);
    }

    public static Long create(String name, Long parentId, List<Long> permissionIds) {
        RoleCreateReq req = new RoleCreateReq(name, parentId, permissionIds);

        RoleResp role = roleAppService.createRole(DEFAULT_TENANT_ID, req, DEFAULT_OPERATOR_ID);
        return role.getId();
    }

    public static Long createWithDefaults() {
        return create("test-role-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
```

```typescript
// ✅ 正确 — Vue 组件测试数据工厂
export function createTestUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    name: "test-user",
    email: "test@example.com",
    roles: ["user"],
    ...overrides,
  };
}

// 使用
const user = createTestUser({ name: "admin", roles: ["admin"] });
```

---

**记住**: 测试即文档。好的测试清晰展示代码应该如何使用。保持测试简洁、可读、可维护。
