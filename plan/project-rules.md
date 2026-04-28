# 项目开发规范（PROJECT RULES）

> 本文档是项目全体开发成员必须遵守的工程规范，覆盖报文、接口、异常、日志、依赖、工具类、对象设计、事务、安全、缓存、数据库、MQ、服务调用、Git 等方面。
>
> **技术栈基线：** Spring Boot 3 + Java 21 + MyBatis-Flex + Spring Cloud + RocketMQ + PostgreSQL + Redis

---

## 目录

1. [通用报文规范](#1-通用报文规范)
2. [接口规范](#2-接口规范)
3. [异常处理规范](#3-异常处理规范)
4. [日志规范](#4-日志规范)
5. [依赖规范](#5-依赖规范)
6. [工具类规范](#6-工具类规范)
7. [对象设计规范](#7-对象设计规范)
8. [分层架构规范](#8-分层架构规范)
9. [事务规范](#9-事务规范)
10. [数据校验规范](#10-数据校验规范)
11. [安全规范](#11-安全规范)
12. [缓存规范](#12-缓存规范)
13. [数据库设计规范](#13-数据库设计规范)
14. [OpenFeign 服务调用规范](#14-openfeign-服务调用规范)
15. [MQ 消息规范](#15-mq-消息规范)
16. [Git 提交规范](#16-git-提交规范)

---

## 1. 通用报文规范

### 1.1 统一响应体结构

所有接口（含网关透传、服务内部 Feign 接口）必须使用以下统一响应体：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "requestId": "uuid-xxx",
  "traceId": "a3f2b1c0d4e5..."
}
```

| 字段        | 类型      | 说明                                                          |
| ----------- | --------- | ------------------------------------------------------------- |
| `code`      | `int`     | 200 = 成功；非零为错误码，见 §1.2                              |
| `message`   | `String`  | 面向前端展示的提示文本，不得包含堆栈信息                      |
| `data`      | `Object`  | 业务数据；失败时为 `null`                                     |
| `requestId` | `String`  | 请求追踪 ID，由 Gateway 生成                                   |
| `traceId`   | `String`  | 链路追踪 ID，由 Micrometer Tracing 生成，网关注入并全链路透传 |

> **禁止**直接将 `data` 设计为 `List`，必须包装为对象（如分页结构），保留扩展空间。

### 1.2 业务错误码规范

| 范围          | 归属模块          | 说明                             |
| ------------- | ----------------- | -------------------------------- |
| `200`         | 全局              | 成功                             |
| `10001–19999` | admin-service     | 管理服务业务错误                 |
| `20001–29999` | permission-center | 权限中心业务错误                 |
| `30001–39999` | example-service   | 演示服务业务错误                 |
| `90001–99999` | 全局系统错误      | 参数校验失败、系统异常等公共错误 |

- `9xxxx` 段系统公共错误由 `common` 模块统一定义枚举，各业务模块**不得重复定义**。
- 每个模块维护一个 `XxxErrorCode` 枚举类，字段格式：`CODE(int code, String msg)`。

### 1.3 分页入参与响应规范

所有分页查询接口的分页参数统一命名：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "sort": "createdAt,desc"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `pageNum` | `int` | 当前页码，从 1 开始 |
| `pageSize` | `int` | 每页条数，默认 20，最大不超过 100 |
| `sort` | `String` | 排序字段和方向，格式 `field,asc` 或 `field,desc`，可空 |

分页响应结构统一放在 `data` 对象内：

```json
{
  "code": 200,
  "message": "success",
  "requestId": "...",
  "traceId": "...",
  "data": {
    "items": [],
    "total": 100,
    "pageNum": 1,
    "pageSize": 20,
    "hasNext": true
  }
}
```

`data.items` 为数据列表，`total/pageNum/pageSize/hasNext` 为分页元数据。非分页列表也必须使用 `{ "items": [...] }` 包装，不直接返回数组。

---

## 2. 接口规范

### 2.1 HTTP 方法

**所有对外接口统一使用 `POST` 方法 + JSON 请求体**，不使用 GET/PUT/DELETE/PATCH。原因：

- 与 permission-center 内部规范保持一致。
- 规避 URL 长度限制（复杂查询条件无需放 QueryString）。
- 统一鉴权拦截逻辑，减少网关路由配置复杂度。

**强制要求：**

- Controller 层**禁止**使用 `@RequestParam` 接收请求参数（文件上传 `MultipartFile` 场景除外）。
- 所有请求参数必须通过 `@RequestBody` + Request DTO（Java Record）接收，包括单个 ID、查询条件、分页参数等。
- **禁止**使用 `@GetMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`，统一使用 `@PostMapping`。
- 业务 ID、查询条件、分页参数等**禁止**放在路径中（如 `/user/{id}`），必须放在 JSON Body 内。
- `tenantId` 不作为普通业务入参放在 URL 或 Body 中；服务端统一从 `X-Tenant-Id`、Token 或 SecurityContext 读取。

**例外场景（允许 `@RequestParam`）：**

| 场景 | 原因 |
|------|------|
| 文件上传（`MultipartFile`） | 必须使用 `multipart/form-data` |
| 文件下载（二进制流响应） | 响应不是 JSON |

### 2.2 路径命名规范

```
/v{version}/{resource}/{action}
```

示例：

| 路径              | 说明       |
| ----------------- | ---------- |
| `/api/user/create` | 创建用户   |
| `/api/user/update` | 更新用户   |
| `/api/user/delete` | 删除用户   |
| `/api/user/get`    | 查询单条   |
| `/api/user/page`   | 分页查询   |
| `/api/user/list`   | 不分页列表 |

规则：

- 路径全部**小写 + 短横线**分隔多词（`/role-group/`）。
- 路径格式为 `/api/{module}/{resource}/{action}`，第一段为模块标识，不使用版本号。
- `action` 语义化动词：`create / update / delete / get / page / list / enable / disable / batch-delete`。
- **禁止** RESTful 风格路径参数（如 `/user/{id}`），ID 统一放 JSON Body。

### 2.3 Content-Type

- 请求：`Content-Type: application/json`
- 文件上传：`Content-Type: multipart/form-data`（例外，允许）
- 响应：`Content-Type: application/json; charset=UTF-8`

### 2.4 认证与鉴权

- 所有接口（除登录/健康检查白名单）均需携带 `Authorization: Bearer <token>`。
- 网关负责 Token 解析和接口级鉴权；服务内不重复鉴权，但**不得绕过**安全上下文传递。
- 内部 Feign 调用需携带服务标识 Header（`X-Service-Code`），网关区分内外流量。

---

## 3. 异常处理规范

### 3.1 全局拦截机制

**必须**在每个 Spring Boot 服务中使用 `@RestControllerAdvice` + `@ExceptionHandler` 集中处理所有异常，将其转换为统一响应体（见 §1.1）。

**禁止**在 Controller 层使用 try-catch 吞掉异常后返回自定义结构。

### 3.2 异常类层级

```
RuntimeException
├── BizException          # 业务异常 —— 预期内的业务规则拒绝
│     └── 携带 errorCode (int) + message (String)
└── SystemException       # 系统异常 —— 非预期的技术层错误
      └── 携带 errorCode (int) + message (String) + cause (Throwable)
```

- `BizException`：主动抛出，表示**预期内**的业务规则拒绝，如"用户不存在"、"余额不足"。
- `SystemException`：包装底层技术异常（IO、RPC 失败等），表示**非预期**的系统故障。
- 两者均继承自 `RuntimeException`，携带模块错误码（见 §1.2）。

### 3.3 全局 ExceptionHandler 处理顺序

| 异常类型                          | HTTP 状态码 | code       | 日志级别 | 堆栈 |
| --------------------------------- | ----------- | ---------- | -------- | ---- |
| `BizException`                    | 200         | 业务错误码 | WARN     | 否   |
| `MethodArgumentNotValidException` | 200         | `90001`    | WARN     | 否   |
| `ConstraintViolationException`    | 200         | `90001`    | WARN     | 否   |
| `SystemException`                 | 200         | 系统错误码 | ERROR    | 是   |
| `Exception`（兜底）               | 200         | `99999`    | ERROR    | 是   |

> HTTP 状态码统一返回 200，由 `success` + `code` 区分业务成功与失败，降低前端复杂度。
>
> **例外**：Gateway 对外响应使用真实 HTTP 状态码（401 未认证、403 鉴权拒绝、502 上游异常、503 服务不可用等），
> 前端需根据 HTTP 状态码做差异化处理。内部服务间调用（OpenFeign）仍遵循统一 200 约定。

### 3.4 注意事项

- **禁止**在业务代码中 `catch (Exception e) { }` 静默吞掉异常。
- **禁止**将 `SystemException` 的 cause 堆栈输出到 `msg` 字段（防止信息泄露）。
- 日志中记录 `traceId`、`userId`、`tenantId` 上下文，便于排查。

---

## 4. 日志规范

### 4.1 日志框架

- 使用 **SLF4J + Log4j2**。
- **禁止**直接使用 `System.out.println`。
- 所有服务均排除 Spring Boot 默认 `spring-boot-starter-logging`（Logback）并显式引入 `log4j2`。

### 4.2 日志格式

**所有环境统一输出 JSON 格式**，便于 ELK/Loki 等日志系统采集：

```json
{
  "timestamp": "2026-04-21T10:00:00.123Z",
  "level": "INFO",
  "logger": "cn.ac.fage.accessmesh.admin.UserService",
  "thread": "http-nio-9100-exec-1",
  "traceId": "a3f2b1c0d4e5...",
  "userId": "10086",
  "tenantId": "tenant-001",
  "serviceCode": "admin-service",
  "message": "用户登录成功",
  "exception": null
}
```

### 4.3 MDC 上下文字段

以下字段必须通过 MDC 注入，在整个请求生命周期内可用：

| MDC Key       | 来源                                                            |
| ------------- | --------------------------------------------------------------- |
| `traceId`     | Micrometer Tracing 自动注入                                     |
| `userId`      | 网关解析 Token 后写入请求 Header，服务层从 SecurityContext 读取 |
| `tenantId`    | 同上                                                            |
| `serviceCode` | 服务启动时从配置文件读取                                        |

- 使用 Filter（WebMVC）在请求入口设置 MDC，请求结束后**必须 clear**（防 ThreadPool 污染）。
- Feign 调用时需通过 `RequestInterceptor` 将 MDC 字段透传到下游 Header。

### 4.4 日志级别规范

| 场景                         | 级别    |
| ---------------------------- | ------- |
| 正常业务流程关键节点         | `INFO`  |
| 业务异常（BizException）     | `WARN`  |
| 系统异常 / 第三方调用失败    | `ERROR` |
| 调试信息（开发阶段临时使用） | `DEBUG` |
| 性能探针 / 方法入参出参      | `TRACE` |

- `ERROR` 级别**必须**打印完整堆栈（`log.error("msg", e)`）。
- `WARN` 级别**不打印**堆栈，只记录上下文关键字段。
- 生产环境日志级别设置为 `INFO`，开发环境可设 `DEBUG`。

### 4.5 敏感信息脱敏

以下信息**禁止**出现在日志中：

- 密码、密钥、Token、证书
- 手机号、身份证（如必须记录，脱敏后输出：`138****8888`）
- 完整 SQL（MyBatis-Flex 慢 SQL 日志脱敏处理参数值）

### 4.6 日志文件分割与保留

```
# 开发环境
按天滚动，保留 7 天

# 生产环境
按天滚动，保留 90 天
ERROR 级别单独写入 error.log，保留 180 天
```

两套配置分别放 `log4j2-dev.xml` / `log4j2-prod.xml`，通过 Spring Profile 激活。

---

## 5. 依赖规范

### 5.1 版本管理原则

- 所有第三方依赖版本**统一在根 `pom.xml` 的 `<dependencyManagement>` 或独立 BOM 中定义**。
- 子模块引入依赖**不得**自行声明 `<version>`，版本号由父级统一管控。
- **例外**：子模块因兼容性问题确需覆盖版本时，必须在 `pom.xml` 中添加注释说明原因及影响范围。

### 5.2 禁止引入的依赖

| 依赖        | 禁止原因                                                                 | 替代方案                                                   |
| ----------- | ------------------------------------------------------------------------ | ---------------------------------------------------------- |
| `Lombok`    | Java 21 Record + `@Getter`/`@Builder` 可覆盖，避免 APT 编译问题          | Java 21 Record（不可变 DTO）；手写 getter/setter（实体类） |
| `Hutool`    | 依赖庞大（全量引入）、部分工具类实现有安全问题，与项目其他工具库功能重复 | Apache Commons / Guava / Jackson                           |
| `FastJSON`  | 历史上存在多次高危 RCE 漏洞                                              | Jackson（全局统一）                                        |
| `fastjson2` | 同上，尽管已重写，但团队统一用 Jackson，避免多库并存混乱                 | Jackson                                                    |

### 5.3 推荐工具库

| 用途        | 推荐库                                          |
| ----------- | ----------------------------------------------- |
| JSON 序列化 | `jackson-databind`（Spring Boot 内置）          |
| 字符串处理  | `commons-lang3`                                 |
| 集合处理    | `guava`                                         |
| HTTP 客户端 | `OpenFeign`（内部）/ `Spring WebClient`（外部） |
| Bean 映射   | `MapStruct`                                     |
| ORM         | `MyBatis-Flex`                                  |

### 5.4 依赖作用域规范

- 测试专用依赖须标注 `<scope>test</scope>`。
- 仅在编译期需要的注解处理器须标注 `<scope>provided</scope>`（如 `MapStruct` 的 `processor`）。
- **禁止**将测试依赖泄漏到主代码编译路径。

---

## 6. 工具类规范

### 6.1 创建原则

- **公共工具类**（多个模块复用）：统一放在 `common` 模块的 `util` 包下，禁止在业务模块中重复创建。
- **模块专用工具类**：允许在本模块内创建，但功能不得与 `common` 模块已有工具类重复。
- 优先使用 Apache Commons / Guava 已有方法，确认无满足方案再自定义。

### 6.2 命名规范

- 类名格式：`XxxUtil`（首字母大写，去掉末尾 `s`）。
  - ✅ `DateUtil`, `StringUtil`, `JsonUtil`
  - ❌ `DateUtils`, `StringUtils`（保留给第三方库，避免混淆）
- 工具类方法必须是**静态方法**。
- 工具类必须是**无状态**的（禁止持有成员变量）。
- 工具类构造器声明为 `private`，防止实例化。

### 6.3 禁止行为

- 禁止在工具类中注入 Spring Bean（使用 `ApplicationContext` 的工具类视为 Helper，单独命名为 `XxxHelper`）。
- 禁止在工具类中直接操作数据库或发起 HTTP 请求。

---

## 7. 对象设计规范

### 7.1 分层对象模型

```
Controller 层          ← 入参：XxxReq    出参：XxxResp（通过统一响应体包装）
Service 层            ← 内部流转可直接使用 Entity 或专用 DTO
Mapper 层             ← Entity（与数据库表一一对应）

按需扩展：
XxxVO                 ← 特殊场景的视图对象（如聚合多表的展示对象）
```

规则：

- **Entity 禁止出现在 Controller 入参/出参中**（防止字段过度暴露）。
- Service 层向 Controller 层返回 `XxxResp` 对象，不直接返回 Entity。
- Service 内部调用可传递 Entity，但跨服务 Feign 接口必须使用 DTO。

### 7.2 命名规范

| 类型                 | 命名规则        | 示例                                 |
| -------------------- | --------------- | ------------------------------------ |
| 请求 DTO（入参）     | `XxxReq`        | `CreateUserReq`, `UserPageReq`       |
| 响应 DTO（出参）     | `XxxResp`       | `UserDetailResp`, `UserPageItemResp` |
| 视图对象（聚合展示） | `XxxVO`         | `UserRoleVO`                         |
| 数据库实体           | `Xxx`（无后缀） | `User`, `AbstractRole`               |
| Feign 接口响应       | `XxxDTO`        | `PermissionCheckDTO`                 |

### 7.3 实体类规范

- 实体类字段与数据库列**严格一一对应**，不添加业务逻辑方法。
- 不使用 `@Transient` 承载统计/聚合字段（此类字段放 VO/Resp）。
- 每个实体类必须包含**审计字段**（通过 MyBatis-Flex 公共字段填充实现）：

```java
private Long createdBy;
private Long updatedBy;
private Long deletedBy;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private LocalDateTime deletedAt;
```

### 7.4 日期时间类型

- **全面使用 Java 8+ 时间 API**：`LocalDateTime`、`LocalDate`、`LocalTime`。
- **禁止**使用 `java.util.Date`、`java.sql.Timestamp`、`java.sql.Date`。
- 数据库时区统一为 UTC，应用层转换为本地时区后展示。
- Jackson 序列化配置：`LocalDateTime` 序列化为 ISO-8601 字符串（`"2026-04-21T10:00:00"`）。

### 7.5 Record 使用规范

- **不可变 DTO**（如请求体、响应体、Feign DTO）**推荐**使用 Java 21 `record`。
- 实体类（Entity）**不使用** `record`（MyBatis-Flex 需要无参构造）。
- `record` 类禁止添加 setter 方法，保持不可变性。

---

## 8. 分层架构规范

### 8.1 分层结构

```
Controller（接入层）
    │  负责：入参校验（@Validated）、调用调度层 Service、响应体包装
    ▼
调度层 Service（Orchestration Service）
    │  负责：将多个逻辑级 Service 的调用组合成一个完整的业务功能
    │  命名：XxxService（接口）/ XxxServiceImpl（实现）
    ▼
逻辑级 Service（Domain Service）
    │  负责：单一最小业务逻辑单元，如"查询单个用户"、"校验用户是否存在"
    │  命名：XxxDomainService（接口）/ XxxDomainServiceImpl（实现）
    ▼
Mapper（数据访问层）
    │  负责：SQL 的封装，只做数据读写，禁止包含业务判断
```

### 8.2 调用方向规范

- **禁止跳层调用**：Controller 不得直接调用 Mapper；逻辑级 Service 不得调用调度层 Service。
- **禁止横向调用**：同层级之间禁止互相调用（如 Service A 调用 Service B 同层方法，应抽取到更低层）。
- Mapper 层只做数据访问，禁止包含分支业务逻辑（`if`/`switch` 等）。

### 8.3 包结构规范

```
cn.ac.fage.accessmesh.{service}
├── controller          # 接入层
├── service
│   ├── impl            # 调度层 Service 实现
│   └── domain          # 逻辑级 Domain Service
├── mapper              # MyBatis-Flex Mapper
├── entity              # 数据库实体
├── dto
│   ├── req             # 入参 XxxReq
│   └── resp            # 出参 XxxResp
├── vo                  # 视图对象（按需）
├── enums               # 枚举
├── config              # 配置类
└── util                # 本模块专用工具类（禁止与 common 重复）
```

---

## 9. 事务规范

### 9.1 事务使用原则

- `@Transactional` 注解**只允许在 Service 层**（调度层或逻辑级 Service）使用。
- **禁止**在 Controller 层、Mapper 层、工具类中使用事务注解。

### 9.2 事务配置规范

```java
// 写操作 —— 默认加事务（不必显式声明，但推荐显式声明以提高可读性）
@Transactional(rollbackFor = Exception.class)
public void createUser(CreateUserReq req) { ... }

// 查询操作 —— 必须显式声明 readOnly=true
@Transactional(readOnly = true)
public UserDetailResp getUserDetail(Long userId) { ... }
```

### 9.3 禁止在事务内的操作

| 操作                           | 原因                                   |
| ------------------------------ | -------------------------------------- |
| 发起 Feign HTTP 调用           | 远程调用超时会导致本地事务长时间持有锁 |
| 发送 MQ 消息                   | 消息发送成功但事务回滚会导致数据不一致 |
| 调用外部第三方 API             | 同上                                   |
| 大批量数据查询（超过 1000 条） | 长事务占用连接池                       |

> **跨服务数据操作**禁止使用本地事务，必须通过 TCC / Saga / 消息最终一致性实现分布式事务。

---

## 10. 数据校验规范

### 10.1 入参校验

- Controller 层使用 Jakarta Bean Validation 注解（`@NotNull`、`@NotBlank`、`@Size`、`@Pattern` 等）+ `@Validated` 开启校验。
- 校验失败由全局 `@RestControllerAdvice` 捕获 `MethodArgumentNotValidException` 统一处理，返回 `code=90001`。

### 10.2 复杂业务校验

- 注解无法表达的复杂业务规则（如"用户名唯一性"、"状态流转合法性"）在**逻辑级 Service** 层手动校验，抛出 `BizException`。
- **禁止**在 Mapper 层做业务合法性校验。

### 10.3 校验分组

- 创建和更新接口共用 DTO 时，使用 Validation Groups 区分：
  - `Create.class`：创建时校验规则
  - `Update.class`：更新时校验规则

---

## 11. 安全规范

### 11.1 SQL 注入防范

- 使用 MyBatis-Flex 参数绑定（`#{}` 占位符），**禁止**字符串拼接 SQL。
- 动态排序字段必须通过**白名单枚举**校验，禁止直接将前端传入的字段名拼入 ORDER BY。

### 11.2 权限检查规范

- **不得绕过权限上下文**：服务内部所有写操作必须携带有效的 `userId` + `tenantId`，通过 SecurityContext 获取，禁止硬编码或从请求体信任字段获取。
- 网关层鉴权拒绝后**不得**在服务层二次放行。
- 内部 Feign 调用（service 间）通过 `X-Service-Code` Header 标识，网关不鉴权内部流量，但服务端需校验来源合法性。

### 11.3 敏感信息规范

- 密码字段：数据库存储 bcrypt 哈希值，接口响应**不返回**任何密码相关字段。
- Token / 密钥：**禁止**写入日志（见 §4.5）。
- 用户隐私字段（手机号、邮箱等）在列表接口中默认脱敏，详情接口按权限决定是否脱敏。

### 11.4 输入过滤

- 接收富文本内容时必须进行 XSS 过滤（使用 OWASP Java HTML Sanitizer）。
- 文件上传必须校验文件类型（MIME 类型白名单）和文件大小上限。

---

## 12. 缓存规范

### 12.1 缓存策略

- 采用 **L1（本地缓存 Caffeine）+ L2（Redis）两级缓存**策略。
- 缓存一致性模式：统一使用 **Cache Aside（旁路缓存）**模式。
  - 读：先读 L1 → 读 L2 → 读 DB，逐级回填。
  - 写：先写 DB → 删除 L2 缓存 → 删除 L1 缓存。

### 12.2 缓存 Key 命名规范

```
{serviceCode}:{bizModule}:{dataType}:{identifier}
```

示例：

| Key                                   | 说明                 |
| ------------------------------------- | -------------------- |
| `perm:role:detail:1234`               | 权限中心角色详情     |
| `admin:user:detail:10086`             | 管理服务用户详情     |
| `perm:permission_version:roleId:5678` | 权限版本（角色维度） |

规则：

- 全部小写，段之间用 `:` 分隔。
- Key 必须包含**租户维度**时，加在最前：`{tenantId}:{serviceCode}:...`。
- 禁止在 Key 中拼接用户输入的原始字符串（防止 Key 冲突/注入）。

### 12.3 缓存 TTL 规范

| 数据类型      | L1 TTL        | L2 TTL           |
| ------------- | ------------- | ---------------- |
| 权限快照      | 60 秒         | 5 分钟           |
| 用户信息      | 60 秒         | 5 分钟           |
| 字典/枚举配置 | 10 分钟       | 1 小时           |
| Token 会话    | 无（不走 L1） | 由 Sa-Token 管理 |

### 12.4 缓存使用禁止项

- **禁止缓存超大对象**（单个 Key value > 1MB，需拆分或分页）。
- **禁止在事务内操作缓存**（参见 §9.3）。
- **禁止缓存 null 值超过 30 秒**（防止缓存穿透，可用短 TTL 空值兜底）。

---

## 13. 数据库设计规范

### 13.1 命名规范

| 对象         | 规范                          | 示例                          |
| ------------ | ----------------------------- | ----------------------------- |
| 表名         | 小写 + 下划线，名词单数       | `abstract_user`、`biz_domain` |
| 字段名       | 小写 + 下划线                 | `created_at`、`delete_flag`   |
| 索引名       | `idx_{table}_{col1}_{col2}`   | `idx_user_tenant_id`          |
| 唯一索引     | `uniq_{table}_{col1}_{col2}`  | `uniq_user_name_tenant`       |
| 外键（逻辑） | 使用 `_id` 后缀，不建物理外键 | `role_id`、`tenant_id`        |

### 13.2 必须字段

每张业务表必须包含以下字段：

```sql
id          BIGINT PRIMARY KEY,              -- 主键（建议 Snowflake ID）
tenant_id   BIGINT NOT NULL,                 -- 租户 ID（多租户隔离）
delete_flag BIGINT NOT NULL DEFAULT 0,       -- 软删除（0=未删除，删除时填本行 id）
created_by  BIGINT,
updated_by  BIGINT,
deleted_by  BIGINT,
created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
deleted_at  TIMESTAMPTZ
```

### 13.3 设计原则

- **无物理外键**：所有表关联为逻辑 ID，由应用层保证数据一致性。
- **软删除**：统一使用 `delete_flag`（删除时设为本行 id 值），所有唯一约束必须附加 `WHERE delete_flag = 0`。
- **无 ENUM 类型**：枚举值使用 `INT` 或 `VARCHAR`，枚举含义在代码枚举类中维护。
- **租户隔离**：所有多租户数据表必须包含 `tenant_id`，查询时必须带租户条件。
- **禁止存储明文密码**。
- 大字段（JSON 配置等）使用 PostgreSQL `JSONB` 类型。
- 时间字段统一使用 `TIMESTAMPTZ`（带时区）。

### 13.4 索引规范

- 主键外所有高频查询条件字段必须建索引。
- 多条件查询建**复合索引**，遵循最左前缀原则。
- 单表索引数量不超过 5 个（避免写放大）。
- 禁止在低基数字段（如 `delete_flag`）上单独建索引。

---

## 14. OpenFeign 服务调用规范

### 14.1 超时配置

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 3000 # 连接超时 3s
        readTimeout: 5000 # 读取超时 5s
      permission-center:
        readTimeout: 10000 # 鉴权接口可适当放宽
```

### 14.2 Header 透传

所有 Feign 调用必须通过 `RequestInterceptor` 透传以下 Header：

| Header           | 说明                         |
| ---------------- | ---------------------------- |
| `Authorization`  | Bearer Token（用户身份透传） |
| `X-Trace-Id`     | 链路追踪 ID                  |
| `X-Tenant-Id`    | 租户 ID                      |
| `X-Service-Code` | 调用方服务标识               |

### 14.3 降级与重试

- **禁止**无限重试，最多重试 1 次（幂等接口）。
- **非幂等接口**（创建、删除）**禁止重试**。
- 使用 Resilience4j 配置熔断降级，降级方法返回 `SystemException` 或空响应，**禁止**返回 null。

### 14.4 Feign 接口规范

- Feign 接口定义在被调方 `api` 子模块中，调用方通过 Maven 依赖引入。
- 接口方法命名与服务端 Controller 保持一致。
- Feign 接口的入参/出参使用专用 DTO（`XxxDTO`），不复用业务模块内部的 `XxxReq/XxxResp`。

---

## 15. MQ 消息规范

> **注意**：当前服务间数据同步（用户同步、菜单同步、权限变更通知等）均采用 **API 调用**方式，不使用 RocketMQ。
> 本章 MQ 规范作为基础设施标准保留，供将来异步事件（如审计日志收集、跨系统通知等）场景参考。
> 历史讨论记录已归档；当前结论以本节说明为准。

### 15.1 消息格式

所有 RocketMQ 消息体统一为 JSON 格式，包含以下字段：

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

| 字段            | 说明                                     |
| --------------- | ---------------------------------------- |
| `eventId`       | 全局唯一 UUID，用于消费幂等去重          |
| `eventType`     | 事件类型，全大写下划线（`USER_CREATED`） |
| `version`       | 消息格式版本，向前兼容时递增 minor       |
| `timestamp`     | 事件发生时间，ISO-8601 UTC               |
| `tenantId`      | 租户 ID                                  |
| `sourceService` | 来源服务标识                             |
| `payload`       | 业务数据，根据 `eventType` 不同而不同    |

### 15.2 幂等设计

- 消费者必须实现幂等消费：以 `eventId` 为幂等键，消费前查询 Redis/DB 是否已处理。
- 幂等记录 TTL 建议为消息最大重试周期的 2 倍（RocketMQ 默认 16 次重试约 4.5 小时，TTL 建议 10 小时）。

### 15.3 发送规范

- **禁止**在事务内发送 MQ 消息（参见 §9.3）；写 DB 成功后发送，或使用事务消息。
- 使用事务消息时，Half Message 与本地事务必须在同一方法内完成。
- 消息发送失败时记录 `ERROR` 日志，包含完整 payload，由人工或定时任务补偿重发。

### 15.4 消费重试

- 消费失败时**必须抛出异常**（而非返回 `RECONSUME_LATER`），触发 RocketMQ 自动重试。
- 重试耗尽（16 次）后消息进入死信队列，必须配置死信队列告警和人工处理流程。
- 消费者代码禁止 `catch (Exception e) {}` 静默失败。

---

## 16. Git 提交规范

### 16.1 分支命名策略

| 分支类型   | 命名格式                    | 示例                           |
| ---------- | --------------------------- | ------------------------------ |
| 主干分支   | `main`                      | `main`                         |
| 开发分支   | `develop`                   | `develop`                      |
| 功能分支   | `feature/{模块}-{功能描述}` | `feature/perm-role-group`      |
| 修复分支   | `fix/{模块}-{问题描述}`     | `fix/admin-login-token-expire` |
| 发布分支   | `release/{版本号}`          | `release/1.2.0`                |
| 热修复分支 | `hotfix/{版本号}-{描述}`    | `hotfix/1.1.1-sql-injection`   |

### 16.2 Commit Message 格式

遵循 **Conventional Commits** 规范：

```
<type>(<scope>): <subject>

[可选 body]

[可选 footer]
```

**type 枚举：**

| type       | 说明                     |
| ---------- | ------------------------ |
| `feat`     | 新功能                   |
| `fix`      | Bug 修复                 |
| `refactor` | 重构（不影响功能）       |
| `perf`     | 性能优化                 |
| `test`     | 添加或修改测试           |
| `docs`     | 文档变更                 |
| `chore`    | 构建工具、依赖升级等杂项 |
| `ci`       | CI/CD 配置变更           |
| `revert`   | 回滚提交                 |

**示例：**

```
feat(permission-center): 新增分组角色批量删除接口

- 支持批量删除，子角色递归清理
- 增加引用检查，有角色关联时拒绝删除

Closes #123
```

### 16.3 提交规则

- 每次提交**聚焦一件事**，禁止将多个无关改动混入一次提交。
- 禁止提交包含以下内容的代码：
  - 调试日志（`System.out.println`、临时 `log.debug`）
  - 注释掉的废弃代码块
  - 硬编码的密码/密钥/IP 地址
- PR/MR 合并前必须通过 CI 全量测试，`main`/`develop` 分支**禁止**直接 push。
