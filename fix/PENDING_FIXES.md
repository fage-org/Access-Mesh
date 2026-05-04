# 待修复问题清单

> 本文档记录需要后续统一修复的问题，按严重程度排序。

---

## 已修复问题（2026-05-04 续）

### S6: 验证码占位符暴露验证码明文（中等）→ 已修复

**位置**: `AuthServiceImpl.java:69`

**修复方案**: 实现真正的验证码图片生成（使用 Java AWT/Graphics），包含干扰线、噪点和随机旋转，验证码明文不再暴露在返回数据中。

### S7: DTO验证缺失（中等）→ 已修复

**已修复文件**:
- `IdsReq.java` - 添加 @NotEmpty
- `ResetPasswordReq.java` - 添加 @NotNull + @NotBlank + @Size(min=8)
- `UserOrgRemoveReq.java` - 添加 @NotNull
- `UserOrgSetPrimaryReq.java` - 添加 @NotNull
- `ConfigUpdateReq.java` - 添加 @NotNull
- `SyncRetryMarkFailedReq.java` - 添加 @NotNull + @NotBlank

### S8: 使用Hutool违反规范（中等）→ 已修复

**位置**: `OAuth2ServiceImpl.java`

**修复方案**: 将 Hutool JWT 替换为 Sa-Token JWT (`SaJwtUtil.createToken`) + Base64 + Jackson 解析 JWT payload。

### 其他遗留编译问题修复

- `FileController.java` / `RoleController.java` - 移除 Lombok @Slf4j，使用标准 SLF4J Logger
- `UserServiceImpl.java` - 修复 pageReq → req 变量名
- `UserOrgServiceImpl.java` - 修复 QueryWrapper.set() 类型问题，改用 Entity 更新
- `RoleProxyServiceImpl.java` - 修复 RoleGrantReq/UserPermissionViewReq 构造器，使用正确的 Feign 方法名 `batchGrant`
- `Oauth2ClientController.java` - 添加缺失的 Oauth2ClientPageReq import

---

## 严重级别问题

### S1: 缺少API权限检查（严重）

**状态**: 待修复
**记录时间**: 2026-05-04

**问题描述**:
所有 Controller 的管理API缺少权限检查注解。任何已登录用户可访问所有管理API，包括用户管理、组织管理、菜单管理、OAuth2客户端管理等敏感操作。

**受影响文件**:
- `UserController.java` - 用户管理API
- `OrgController.java` - 组织管理API
- `MenuController.java` - 菜单管理API
- `Oauth2ClientController.java` - OAuth2客户端管理API
- `RoleController.java` - 角色管理API
- `ConfigController.java` - 系统配置API
- `DictController.java` - 字典管理API
- 其他所有 Controller

**修复方案**:
API权限验证应接入 permission-center 查询资源ID级权限：

1. 每个API对应一个资源（resourceId）和操作码（opCode）
2. 调用 permission-center 的权限检查接口验证用户是否有对应权限
3. 可通过自定义注解 `@PermCheck(resourceType, opCode)` 实现
4. 或使用 AOP 拦截器统一处理

**实现思路**:
```java
// 示例：自定义权限注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermCheck {
    String resourceType();  // 如 "USER", "ORG", "MENU"
    String opCode();        // 如 "CREATE", "UPDATE", "DELETE", "VIEW"
}

// Controller 使用示例
@PostMapping("/create")
@PermCheck(resourceType = "USER", opCode = "CREATE")
@AuditLog(module = "用户管理", action = "创建", targetType = "USER")
public PermResult<Long> createUser(@Valid @RequestBody UserCreateReq req) {
    return PermResult.success(userService.createUser(req));
}

// AOP 拦截器实现
@Aspect
@Component
public class PermCheckAspect {
    @Around("@annotation(permCheck)")
    public Object checkPermission(ProceedingJoinPoint pjp, PermCheck permCheck) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long tenantId = TenantContextHolder.getTenantId();
        // 调用 permission-center 检查权限
        boolean hasPerm = permissionFeignClient.checkPermission(
            tenantId, userId, permCheck.resourceType(), permCheck.opCode()
        );
        if (!hasPerm) {
            throw new BizException(AdminErrorCode.PERMISSION_DENIED);
        }
        return pjp.proceed();
    }
}
```

**依赖条件**:
- permission-center 需提供资源级权限检查接口
- 需建立资源类型与操作码的映射体系
- 需在权限中心预先配置各API的权限资源

**优先级**: 最高（系统安全基础）
**预计工作量**: 中等（需设计权限体系 + AOP实现 + 各Controller适配）

---

## 其他待记录问题

### J1: 事务内调用Feign（严重）- 剩余方法待修复

**状态**: 部分修复（UserServiceImpl.createUser 已修复）
**记录时间**: 2026-05-04

**问题描述**:
Service层的写操作方法在 `@Transactional` 事务内调用了 SyncHandler，而 SyncHandler 内部通过 Feign 调用 permission-center。这违反了规范 §9.3"禁止在事务内发起 Feign HTTP 调用"，远程调用超时会导致本地事务长时间持有数据库锁。

**已修复方法**:
- ✅ `UserServiceImpl.createUser()` - 已使用 TransactionTemplate + Feign移到事务外

**待修复方法列表**:

| 文件 | 方法 | 行号 | 状态 |
|------|------|------|------|
| UserServiceImpl.java | updateUser() | 102-130 | 待修复 |
| UserServiceImpl.java | deleteUser() | 133-156 | 待修复 |
| MenuServiceImpl.java | createMenu() | 52-97 | 待修复 |
| MenuServiceImpl.java | updateMenu() | 101-146 | 待修复 |
| MenuServiceImpl.java | deleteMenu() | 149-171 | 待修复 |
| OrgServiceImpl.java | createOrg() | 51-95 | 待修复 |
| OrgServiceImpl.java | deleteOrg() | 135-157 | 待修复 |

**修复模式**（参考 createUser 示例）:

```java
// 修复前（错误）
@Override
@Transactional
public Long createUser(UserCreateReq req) {
    // ... 数据库操作 ...
    userMapper.insert(user);
    
    // 🔴 Feign调用在事务内！
    Long permUserId = userSyncHandler.syncUserToPermissionCenter(tenantId, user);
    if (permUserId != null) {
        userMapper.update(user);
    }
    return user.getId();
}

// 修复后（正确）
@Override
public Long createUser(UserCreateReq req) {
    // ... 校验逻辑 ...
    
    SysUser user = new SysUser();
    // ... 设置字段 ...
    
    // ✅ DB操作在事务内
    transactionTemplate.executeWithoutResult(status -> {
        userMapper.insert(user);
    });
    
    // ✅ Feign调用在事务外
    try {
        Long permUserId = userSyncHandler.syncUserToPermissionCenter(tenantId, user);
        if (permUserId != null) {
            user.setPermUserId(permUserId);
            userMapper.update(user);
        }
    } catch (Exception e) {
        log.warn("Failed to sync: userId={}, error={}", user.getId(), e.getMessage());
        // 记录到 SysSyncRetry 表待重试
        syncRetryService.recordSyncFailure(...);
    }
    
    return user.getId();
}
```

**修复步骤**:
1. 移除方法上的 `@Transactional` 注解
2. 注入 `TransactionTemplate`、`SyncRetryService`、`ObjectMapper`
3. 使用 `transactionTemplate.executeWithoutResult()` 包裹 DB 操作
4. Feign 调用移到事务外，失败时记录到 SysSyncRetry

**优先级**: 高
**预计工作量**: 中等（7个方法需重构）

---

## 高优先级性能问题

### P5: OrgServiceImpl batchDeleteOrgs N+1查询+循环内Feign调用（高）

**状态**: 待修复
**记录时间**: 2026-05-04

**问题位置**: `OrgServiceImpl.java:269-282`

**问题代码**:
```java
for (Long orgId : req.ids()) {
    SysOrg org = orgDomainService.selectValidById(tenantId, orgId);  // 🔴 N+1查询
    List<Long> descendantIds = orgDomainService.getDescendantIdsIncludingSelf(...);  // 🔴 N+1查询
    if (org.getPermRoleId() != null) {
        orgSyncHandler.deleteOrgFromPermissionCenter(tenantId, org.getPermRoleId());  // 🔴 循环内Feign
    }
}
```

**修复方案**:
1. 批量查询所有要删除的组织及其子孙节点
2. 先完成本地DB操作（事务内）
3. 收集所有需要同步删除的 permRoleId
4. Feign调用改为批量删除或移到事务外

**优先级**: 高

---

### P6: MenuServiceImpl batchDeleteMenus N+1查询+循环内Feign调用（高）

**状态**: 待修复
**记录时间**: 2026-05-04

**问题位置**: `MenuServiceImpl.java:262-275`

**问题描述**: 与 P5 相同模式，循环内逐个查询验证和 Feign 调用。

**修复方案**: 参考 P5 修复方案。

**优先级**: 高

---

### P8: DictServiceImpl deleteDictType N+1查询+更新（高）

**状态**: 待修复
**记录时间**: 2026-05-04

**问题位置**: `DictServiceImpl.java:66-83`

**问题代码**:
```java
for (Long id : req.ids()) {
    SysDictType type = dictTypeMapper.selectOneById(id);  // 🔴 N+1查询
    SysDictType existing = dictTypeMapper.selectOneById(id);  // 🔴 重复查询！
    existing.setDeleteFlag(1L);
    dictTypeMapper.update(existing);  // 🔴 N+1更新
}
```

**修复方案**:
1. 批量查询所有字典类型
2. 使用批量更新 `updateByQuery` 设置 `deleteFlag=1`

**优先级**: 高

---

## 中等优先级性能问题

### P7: DictServiceImpl listDictTypes N+1查询（中等）

**问题位置**: `DictServiceImpl.java:94-97`

**说明**: 方法有 `@Cacheable` 缓存，首次调用存在 N+1，后续调用缓存命中。

**修复建议**: 首次调用时批量查询所有字典数据。

---

### P9: UserDomainServiceImpl existsByUsername/existsByPhone COUNT优化（中等）

**问题位置**: `UserDomainServiceImpl.java:96-102`

**问题**: 使用 `findByUsername(...) != null` 判断存在性，返回整个实体。

**修复方案**: 使用 COUNT 查询或 EXISTS 查询。

---

### P10: RoleProxyServiceImpl 本地内存缓存问题（中等）

**问题位置**: `RoleProxyServiceImpl.java:47`

**问题**: 使用本地 `ConcurrentHashMap` 作为缓存，多实例部署时数据不一致。

**修复方案**: 使用 Caffeine + Redis 二级缓存（项目规范要求）。