# GitHub Copilot 项目开发规范指令

> 本文件作为 GitHub Copilot 的上下文指令。在生成任何代码时，必须严格遵守以下所有规范。
> 完整规范文档见 `plan/PROJECT_RULES.md`。

---

## 技术栈基线

- **Java 21 + Spring Boot 3 + Spring Cloud**
- **ORM**: MyBatis-Flex
- **数据库**: PostgreSQL（多租户，软删除）
- **缓存**: Caffeine（L1）+ Redis（L2）
- **MQ**: RocketMQ
- **认证**: Sa-Token + OAuth2
- **JSON**: Jackson（禁止 FastJSON / fastjson2 / Hutool）
- **日志**: SLF4J + Log4j2（禁止 Logback）

---

## 接口规范

- 所有接口统一使用 **`POST` + JSON Body**，禁止 GET/PUT/DELETE/PATCH。
- 路径格式：`/v1/{resource}/{action}`，全小写，多词用短横线。
- 禁止 RESTful 路径参数（`/user/{id}`），ID 统一放 JSON Body。

---

## 统一响应体

所有接口必须返回以下结构：

```java
// 成功
R.ok(data)
// 失败
R.fail(errorCode, msg)
```

```json
{
  "success": true,
  "code": 0,
  "msg": "操作成功",
  "data": {},
  "traceId": "..."
}
```

- `code=0` 表示成功，非零为错误码。
- HTTP 状态码统一返回 `200`。
- 分页响应：`data = { items: [], pagination: { total, page, size, totalPages } }`。

---

## 错误码分段

| 范围          | 模块               |
| ------------- | ------------------ |
| `0`           | 成功               |
| `10001–19999` | admin-service      |
| `20001–29999` | permission-center  |
| `30001–39999` | example-service    |
| `90001–99999` | 全局系统错误       |

---

## 异常处理

- 全局使用 `@RestControllerAdvice` + `@ExceptionHandler`，禁止 Controller 中 try-catch。
- 业务异常：抛 `BizException(errorCode, msg)`，日志级别 `WARN`，不打印堆栈。
- 系统异常：抛 `SystemException(errorCode, msg, cause)`，日志级别 `ERROR`，打印堆栈。
- 禁止在任何地方 `catch (Exception e) {}` 静默吞掉异常。

---

## 分层结构

```
Controller
    ↓ 调用
调度层 Service（XxxService / XxxServiceImpl）     ← 组合多个业务逻辑
    ↓ 调用
逻辑级 Service（XxxDomainService）               ← 单一最小业务逻辑
    ↓ 调用
Mapper                                           ← 只做数据读写，无业务逻辑
```

- **禁止跳层调用**（Controller 禁止直接调 Mapper）。
- **禁止同层横向调用**。
- Mapper 层禁止包含 if/switch 业务判断。

---

## 对象命名规范

| 类型         | 命名规则      | 示例                    |
| ------------ | ------------- | ----------------------- |
| 请求入参     | `XxxReq`      | `CreateUserReq`         |
| 响应出参     | `XxxResp`     | `UserDetailResp`        |
| 视图对象     | `XxxVO`       | `UserRoleVO`            |
| 数据库实体   | `Xxx`（无后缀）| `User`, `AbstractRole` |
| Feign DTO    | `XxxDTO`      | `PermissionCheckDTO`    |

- **Entity 禁止出现在 Controller 入参/出参**。
- 工具类命名：`XxxUtil`（去掉末尾 s），工具类必须为静态无状态。
- 禁止在业务模块创建与 `common` 重复的 Util 类。

---

## 禁止使用的类型与依赖

```java
// 禁止
import java.util.Date;
import java.sql.Timestamp;
// 使用
import java.time.LocalDateTime;
import java.time.LocalDate;

// 禁止
import com.alibaba.fastjson.*;
import com.alibaba.fastjson2.*;
import cn.hutool.*;
import lombok.*;  // 禁止 Lombok，用 Java 21 Record 或手写
// 使用
import com.fasterxml.jackson.*;
import org.apache.commons.lang3.*;
import com.google.common.*;
```

---

## 实体类规范

每个实体类必须包含审计字段：

```java
private Long createdBy;
private Long updatedBy;
private Long deletedBy;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private LocalDateTime deletedAt;
```

- 软删除字段：`deleteFlag`（`0`=未删除，删除时设为本行 id）。
- 多租户字段：`tenantId`，所有查询必须带租户条件。
- 实体类禁止内置业务逻辑方法。

---

## 事务规范

```java
// 写操作
@Transactional(rollbackFor = Exception.class)
public void createUser(CreateUserReq req) { ... }

// 查询操作
@Transactional(readOnly = true)
public UserDetailResp getUser(Long id) { ... }
```

- `@Transactional` 只允许在 Service 层。
- **禁止在事务内**：发起 Feign 调用、发送 MQ 消息、调用外部 API。

---

## 日志规范

```java
// 必须通过 SLF4J 接口记录
private static final Logger log = LoggerFactory.getLogger(XxxService.class);

// 业务异常 → WARN，不打堆栈
log.warn("用户不存在: userId={}, tenantId={}", userId, tenantId);

// 系统异常 → ERROR，打堆栈
log.error("查询权限失败: userId={}", userId, e);

// 禁止
System.out.println("...");
e.printStackTrace();
```

- 日志中禁止出现：密码、Token、完整手机号/身份证。
- MDC 字段（traceId / userId / tenantId / serviceCode）由框架统一注入，不需要手动打印。

---

## 数据校验规范

```java
// Controller 层注解校验
public R<Void> createUser(@RequestBody @Validated CreateUserReq req) { ... }

// DTO 上使用 Jakarta Validation
public record CreateUserReq(
    @NotBlank String username,
    @Size(min = 6, max = 32) String password
) {}

// 复杂业务校验在逻辑级 Service 层手动检查
if (userDomainService.existsByUsername(req.username(), tenantId)) {
    throw new BizException(UserErrorCode.USERNAME_DUPLICATE);
}
```

---

## 缓存 Key 命名

```
{serviceCode}:{bizModule}:{dataType}:{identifier}
```

示例：`perm:role:detail:1234`、`admin:user:detail:10086`
- 全小写，`:` 分隔。
- 禁止直接将用户输入拼入 Key。

---

## MQ 消息格式

```json
{
  "eventId": "uuid-v4",
  "eventType": "USER_CREATED",
  "version": "1.0",
  "timestamp": "2026-04-21T10:00:00Z",
  "tenantId": "tenant-001",
  "sourceService": "admin-service",
  "payload": {}
}
```

- 消费者必须以 `eventId` 实现幂等。
- 消费失败必须抛出异常（禁止静默消费失败）。

---

## SQL 安全

- 禁止字符串拼接 SQL，必须使用 MyBatis-Flex 参数绑定（`#{}` 占位符）。
- 动态排序字段必须通过白名单枚举校验，禁止直接将前端传入字段名拼入 ORDER BY。

---

## Git Commit 格式

```
feat(permission-center): 新增角色分组批量删除接口
fix(admin): 修复登录 Token 过期未刷新问题
refactor(common): 优化分页响应结构
```

格式：`<type>(<scope>): <subject>`，type 枚举：`feat / fix / refactor / perf / test / docs / chore / ci / revert`。
