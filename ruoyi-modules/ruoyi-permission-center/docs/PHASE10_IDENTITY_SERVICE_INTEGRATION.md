# Phase 10: identity-service 对接

## 验收状态：已完成

---

## 1. 输入依赖

- Phase 1 的 `abstract_user` 表结构与 `type_definition` 用户类型定义
- Phase 9 的 `/api/perm/version/query` 版本查询接口
- Phase 9 的 `/api/perm/subject/mapping` 主体映射接口

---

## 2. 输出接口

### 2.1 permission-center 接口

| 接口路径 | 方法 | 说明 |
|----------|------|------|
| `/api/perm/subject/mapping` | POST | 查找或创建主体映射 |
| `/api/perm/subject/version` | POST | 查询权限版本 |

### 2.2 identity-service 接口

| 接口路径 | 方法 | 说明 |
|----------|------|------|
| `/api/identity/delegation/issue` | POST | 签发委托上下文 |
| `/api/identity/delegation/revoke` | POST | 撤销委托上下文 |

---

## 3. 实施内容

### 3.1 主体映射服务

- **服务接口**：`SubjectMappingService`
- **实现类**：`SubjectMappingServiceImpl`
- **功能**：
  - 根据用户类型和外部ID查找或创建抽象用户
  - 用户类型缓存（避免重复查询 `type_definition`）
  - 支持按租户隔离的主体查询

### 3.2 登录增强服务

- **服务接口**：`LoginEnhancementService`
- **实现类**：`LoginEnhancementServiceImpl`
- **功能**：
  - 登录时查询主体映射，获取 `abstractUserId`
  - 登录时查询权限版本，获取 `permissionVersion`
  - 将版本写入 `LoginUser`，通过 `LoginHelper.login()` 写入令牌

### 3.3 令牌字段扩展

在 `LoginUser` 中新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `permissionVersion` | String | 权限版本号，用于 gateway 刷新本地快照缓存 |
| `abstractUserId` | Long | 抽象用户ID，与 permission-center 的 abstract_user 表对应 |

在 `LoginHelper` 中新增常量和方法：

| 常量/方法 | 说明 |
|-----------|------|
| `PERMISSION_VERSION_KEY` | 令牌扩展信息键：权限版本 |
| `ABSTRACT_USER_ID_KEY` | 令牌扩展信息键：抽象用户ID |
| `getPermissionVersion()` | 获取当前令牌的权限版本 |
| `getAbstractUserId()` | 获取当前令牌的抽象用户ID |

### 3.4 委托授权流程

- **签发委托**：`/api/identity/delegation/issue`
  - 服务主体申请委托
  - 返回 `delegationId`
  - 设置过期时间
- **使用委托**：在 `issueContext` 时传入 `delegationId`
  - 主体类型变为 `DELEGATED`
  - `PrincipalContext` 携带 `DelegationContext`
- **撤销委托**：`/api/identity/delegation/revoke`

---

## 4. 失败模式

| 场景 | 处理方式 |
|------|----------|
| permission-center 不可用 | 回退到默认版本 `{tenantId}-v0` |
| 主体映射失败 | 仅查询版本，不设置 `abstractUserId` |
| 用户类型不存在 | 使用默认值 `1` |
| 委托ID无效 | 忽略委托上下文，使用原始主体类型 |

---

## 5. 验收记录

### 5.1 定向回归

```bash
mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev \
  "-Dsurefire.failIfNoSpecifiedTests=false" \
  "-Dtest=SubjectMappingServiceImplTest,HybridPermissionKernelServiceTest"
```

**结果**：Tests run: 13, Failures: 0, Errors: 0, Skipped: 0

### 5.2 关键验证点

- [x] 登录时自动查询权限版本
- [x] 登录时自动创建或查找主体映射
- [x] 权限版本写入令牌扩展信息
- [x] 抽象用户ID写入令牌扩展信息
- [x] 委托授权流程可用
- [x] 委托上下文可撤销

---

## 6. 风险与未决问题

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| HTTP 调用 permission-center | 登录延迟增加 | 可考虑使用 Dubbo 替代 RestTemplate |
| 主体映射无缓存 | 高频登录时重复查询 | 后续可添加本地缓存 |
| 委托存储在内存 | 重启后丢失 | 后续可持久化到 Redis |

---

## 7. 后续优化方向

1. **Dubbo 集成**：使用 Dubbo 替代 RestTemplate 调用 permission-center
2. **委托持久化**：将委托上下文存储到 Redis
3. **版本预加载**：在用户信息查询时预加载版本，减少登录时查询

---

## 8. 相关文件

| 文件 | 说明 |
|------|------|
| `SubjectMappingService.java` | 主体映射服务接口 |
| `SubjectMappingServiceImpl.java` | 主体映射服务实现 |
| `SubjectMappingController.java` | 主体映射控制器 |
| `LoginEnhancementService.java` | 登录增强服务接口 |
| `LoginEnhancementServiceImpl.java` | 登录增强服务实现 |
| `HybridIdentityKernelService.java` | 混合身份内核服务 |
| `PermissionVersionClient.java` | 权限版本客户端 |
| `SubjectMappingClient.java` | 主体映射客户端 |
| `LoginUser.java` | 登录用户模型（新增字段） |
| `LoginHelper.java` | 登录助手（新增方法） |
