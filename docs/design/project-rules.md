---
doc_type: design
title: 项目开发规范（PROJECT RULES）
status: adopted
domain: common
last_reviewed: 2026-08-12   # access-service 归并：错误码继续按管理域/权限域分段
---

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

| 字段        | 类型     | 说明                                                          |
| ----------- | -------- | ------------------------------------------------------------- |
| `code`      | `int`    | 200 = 成功；非零为错误码，见 §1.2                             |
| `message`   | `String` | 面向前端展示的提示文本，不得包含堆栈信息                      |
| `data`      | `Object` | 业务数据；失败时为 `null`                                     |
| `requestId` | `String` | 请求追踪 ID，由 Gateway 生成                                  |
| `traceId`   | `String` | 链路追踪 ID，由 Micrometer Tracing 生成，网关注入并全链路透传 |

> **禁止**直接将 `data` 设计为 `List`，必须包装为对象（如分页结构），保留扩展空间。

### 1.2 业务错误码规范

| 范围          | 归属模块          | 说明                             |
| ------------- | ----------------- | -------------------------------- |
| `200`         | 全局              | 成功                             |
| `10001–19999` | access-service 管理域 | 兼容原 admin 管理业务错误      |
| `20001–29999` | access-service 权限域 | 兼容原 permission 权限业务错误 |
| `30001–39999` | example-service   | 演示服务业务错误                 |
| `90001–99999` | 全局系统错误      | 参数校验失败、系统异常等公共错误 |

- `9xxxx` 段系统公共错误由 `common` 模块统一定义枚举，各业务模块**不得重复定义**。
- `admin-service` 与 `permission-center` 物理归并后不改变错误码的领域归属：既有码值原样保留，管理域新增错误继续使用 `1xxxx`，权限域新增错误继续使用 `2xxxx`。
- `access.application` 不单独占用错误码段；跨域编排错误按对外入口所属领域选择 `1xxxx` 或 `2xxxx`，与具体领域无关的公共技术失败使用 `9xxxx`。
- 禁止因服务重命名而重编号，禁止为 `access-service` 新增 `4xxxx` 错误码段。
- 每个业务域维护一个 `XxxErrorCode` 枚举类，字段格式：`CODE(int code, String msg)`；管理域和权限域枚举不得合并。

### 1.3 分页入参与响应规范

所有分页查询接口的分页参数统一命名：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "sort": "createdAt,desc"
}
```

| 字段       | 类型     | 说明                                                   |
| ---------- | -------- | ------------------------------------------------------ |
| `pageNum`  | `int`    | 当前页码，从 1 开始                                    |
| `pageSize` | `int`    | 每页条数，默认 20，最大不超过 100                      |
| `sort`     | `String` | 排序字段和方向，格式 `field,asc` 或 `field,desc`，可空 |

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

**permission-center 业务键与 `remove` 主键（补充）：**

- 对外接口定位**用户/角色**时，使用 `subjectTypeCode + subjectExternalId`、`domainCode + roleTypeCode + roleExternalId` 等契约字段，**禁止**要求调用方传入 `abstract_user.id`、`abstract_role.id`（管理端二次查询用的 `list/detail` 返回 id 仅用于**同模块** update/remove 链路中已有说明的接口除外）。
- `remove` 类接口请求体中的 `{ "ids": [...] }` 表示**配置表主键**（如 `domain_config.id`、`service_config.id`、`resource_api_mapping.id`），用于删除已在 `list`/`detail` 中返回过的行；与「主体/角色业务键」分层使用，互不替代。

**例外场景（允许 `@RequestParam`）：**

| 场景                        | 原因                           |
| --------------------------- | ------------------------------ |
| 文件上传（`MultipartFile`） | 必须使用 `multipart/form-data` |
| 文件下载（二进制流响应）    | 响应不是 JSON                  |

### 2.2 路径命名规范

```
/v{version}/{resource}/{action}
```

示例：

| 路径               | 说明       |
| ------------------ | ---------- |
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

### 3.2.1 业务代码使用边界

- **这属于规则（rules），不属于 skill**：异常分类是写业务代码时始终生效的约束，应写入项目规范或模块规则，而不是按需加载的专题技能。
- `SecurityException` 仅用于**身份、签名、认证、鉴权、越权访问**等安全拒绝场景，例如 Token/Gateway 签名非法、无法确定操作者身份、权限不足。
- `BizException` 用于**预期内的业务拒绝**，包括资源不存在、业务键无效、状态不允许、数据重复、领域规则冲突等；优先使用模块错误码，而不是裸字符串异常。
- `SystemException` 用于**非预期的技术故障**或需要保留 `cause` 的系统错误，例如 IO、RPC、序列化、数据库驱动、算法初始化失败等。
- `IllegalArgumentException` 仅允许用于**私有 helper / util / factory / enum** 的编程契约校验或框架适配，不应作为 AppService/DomainService 对外公开业务分支的主要异常类型。
- `IllegalStateException` 仅允许用于**启动失败、配置缺失、内部不变量破坏、理论上不应发生的状态**；不得用来表达普通业务状态拒绝，更不能替代鉴权异常。
- **禁止**为了“统一”而把资源不存在、状态不合法、重复创建等普通业务拒绝全部改抛 `SecurityException`；这会混淆安全拒绝与业务拒绝的边界。
- 存量代码中若仍有 `IllegalArgumentException` / `IllegalStateException` / `SecurityException` 混用，后续**触达即按上述边界收敛**，不要继续复制旧写法。

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
- AppService / DomainService 新增公开方法优先使用 `BizException` / `SystemException` / `SecurityException` 三类语义化异常；`IllegalArgumentException` / `IllegalStateException` 仅保留给内部 fail-fast 场景。

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

| 依赖        | 禁止原因                                                                                           | 替代方案                                                               |
| ----------- | -------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `Lombok`    | 优先保持显式；禁止 `@Data`、`@Value` 等隐式生成过多逻辑的注解，`@Builder` 仅在复杂构造场景按需使用 | Java 21 Record（不可变 DTO 优先）；必要时手写方法或受控使用 `@Builder` |
| `Hutool`    | 依赖庞大（全量引入）、部分工具类实现有安全问题，与项目其他工具库功能重复                           | Apache Commons / Guava / Jackson                                       |
| `FastJSON`  | 历史上存在多次高危 RCE 漏洞                                                                        | Jackson（全局统一）                                                    |
| `fastjson2` | 同上，尽管已重写，但团队统一用 Jackson，避免多库并存混乱                                           | Jackson                                                                |

补充约束：DTO / Request / Response 优先使用 Java 21 Record。Lombok 可在需要可变字段的实体类、配置类、简单结果类中使用 `@Getter` / `@Setter`；确有复杂构造或测试装配可读性收益时允许使用 `@Builder`，但仍禁止 `@Data`、`@Value`、`@EqualsAndHashCode` 等其他高隐式注解。

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
- **授权域同层调用例外（2026-08-08 产品确认，唯一例外）**：权限中心的授权写链路中，`PermissionGrantPlanDomainServiceImpl` 组合注入 `PermissionGrantDomainService`（`checkCanGrant`/`validateSingleManualGrants`/`validateGrantAttributes`）为**明确允许的例外**，限定条件：① 仅限授权域 `PlanDomainService → GrantDomainService` 单向；② 禁止反向调用与循环依赖；③ 仅复用校验能力，不承载事务编排（**事务仅由 AppService/调度层声明，PlanDomainService 仅参与该事务**）；④ **不推广为一般规则**，其他域/其他服务仍禁止同层横向调用。
- Mapper 层只做数据访问，禁止包含分支业务逻辑（`if`/`switch` 等）。

**permission-center Controller（补充）：**

- 权限排查类接口（如 `permission-view/explain`、`recent-changes`）的编排、分页过滤、JSON 解析、多表组装须在**调度层 Service**（如 `PermissionViewAppService`）完成；Controller 仅做校验与 `PermResult` 包装。

### 8.3 包结构规范

```
cn.ac.fage.accessmesh.{service}
├── controller          # 接入层
├── service
│   ├── impl            # 调度层 Service 实现
│   └── domain          # 逻辑级 Domain Service
│       └── impl        # Domain Service 实现
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

### 8.4 Service 层复用与扩展规范

本节定义两层 Service（调度层 AppService / 逻辑级 DomainService）的功能复用原则与扩展性设计要求，**新增、修改功能时必须遵循**。

#### 8.4.1 两层职责划分

| 层级                     | 命名                                        | 职责                                                           | 典型方法                                                                    |
| ------------------------ | ------------------------------------------- | -------------------------------------------------------------- | --------------------------------------------------------------------------- |
| **调度层 AppService**    | `XxxAppService` / `XxxAppServiceImpl`       | 业务流程编排、跨领域协调、外部接口契约转换、权限检查、事务边界 | `batchGrant()`、`check()`、`listEffectiveRoles()`                           |
| **逻辑级 DomainService** | `XxxDomainService` / `XxxDomainServiceImpl` | 单一领域逻辑、可复用的原子操作、内部数据转换、缓存管理         | `resolveEffectiveRoles()`、`batchGetDescendantIds()`、`revokePermissions()` |

**核心原则**：

- 调度层负责**组合**，不实现单一领域逻辑
- 领域层负责**原子逻辑**，可被多个调度层服务复用
- 领域层方法应**高内聚**，一个方法只做一件事

#### 8.4.2 必须复用的场景

以下场景**必须**复用现有 DomainService 方法，禁止在调度层重新实现：

| 场景                     | 已有 DomainService 方法                                          | 禁止行为                      |
| ------------------------ | ---------------------------------------------------------------- | ----------------------------- |
| 用户角色解析（带缓存）   | `SubjectDomainService.resolveEffectiveRoles()`                   | 调度层直接查询 `user_role` 表 |
| 资源层级遍历             | `ResourceEntityDomainService.batchGetDescendantIds()`            | 调度层写递归遍历逻辑          |
| 权限级联删除             | `PermissionGrantDomainService.revokePermissions()`               | 调度层写子权限删除循环        |
| 角色关联用户缓存批量失效 | `SubjectDomainService.invalidateRoleCacheByRoles()`              | afterCommit 按角色循环逐个失效（N+1） |
| 类型解析（code ↔ value） | `TypeResolutionService.resolveTypeValue()` / `resolveTypeCode()` | 调度层查 `type_definition` 表 |

**判断标准**：如果逻辑涉及**单一领域实体**的原子操作（查、改、删、转换），应下沉到 DomainService。

#### 8.4.3 新增功能前的检查清单

新增 Service 方法前，**必须**检查：

1. **是否已有可复用的 DomainService 方法？**
   - 搜索 `service/domain/` 目录下相关领域服务
   - 检查方法签名是否满足需求（参数、返回值）

2. **是否需要新建 DomainService 方法？**
   - 如果逻辑是单一领域的原子操作，应新建 DomainService 方法
   - 新建前确认没有类似逻辑已在其他 DomainService 中实现

3. **调度层是否只做编排？**
   - 调度层方法应只包含：调用 DomainService、结果组合、异常转换
   - 如果调度层出现 `if/for/while` 处理业务数据，考虑下沉

4. **是否有缓存可利用？**
   - DomainService 中的缓存方法（如 `resolveEffectiveRoles()`）优先使用
   - 禁止在调度层绕过缓存直接查数据库

#### 8.4.4 扩展性设计要求

新增 DomainService 方法时，**必须**考虑扩展性：

| 要求           | 说明                                           | 示例                                                                                |
| -------------- | ---------------------------------------------- | ----------------------------------------------------------------------------------- |
| **参数设计**   | 预留过滤/扩展参数，使用 nullable 或默认值      | `resolveResourceId(tenantId, resourceTypeCode, resourceCode, codeType, domainCode)` |
| **返回值设计** | 返回足够信息供调用方二次处理，不丢失上下文     | 返回 `Set<Long>` 角色 ID + `Map<Long, RoleInfo>` 角色详情                           |
| **批量优化**   | 支持批量输入，避免 N+1 查询                    | `batchGetRolePermissions(roleIds)` 返回 `Map<Long, List<...>>`                      |
| **缓存友好**   | 高频查询方法应集成缓存，调用方无需关心缓存细节 | `resolveEffectiveRoles()` 内置 L1/L2 缓存                                           |

**禁止行为**：

- 禁止 DomainService 方法返回 Controller 层 DTO（如 `XxxResp`），应返回领域对象或基础类型
- 禁止 DomainService 方法依赖外部业务上下文（如 `OperatorContext`），应通过参数传入

#### 8.4.5 代码重复检测标准

以下模式表示代码重复，**必须**重构：

| 重复模式                        | 检测方法                                       | 重构方案                                |
| ------------------------------- | ---------------------------------------------- | --------------------------------------- | ----------------------------------------- |
| 相同 SQL 查询出现在多个 Service | Grep 搜索 `selectListByQuery` 或表名           | 抽取到 DomainService 或 Mapper          |
| 相同数据转换逻辑出现在多处      | 搜索 `new XxxResp(...)` 或 `stream().map(...)` | 抽取为 DomainService 转换方法或实体方法 |
| 相同计算公式出现在多处          | 搜索计算表达式（如 `binaryBit                  | inheritMask`）                          | 抽取为实体方法（如 `getEffectiveBits()`） |
| 相同业务校验逻辑出现在多处      | 搜索校验注释或异常抛出                         | 抽取为 DomainService 校验方法           |

#### 8.4.6 实体方法封装标准

以下逻辑**优先**封装到实体类（Entity）方法：

| 场景           | 实体方法示例                                                  |
| -------------- | ------------------------------------------------------------- | ------------ |
| 字段派生计算   | `OperationPermission.getEffectiveBits()` = `binaryBit         | inheritMask` |
| 状态判断       | `AbstractRole.isEnabled()` = `status == 1 && deleteFlag == 0` |
| 业务字段格式化 | `ResourceEntity.getFullCode()` = `parentCode + "/" + code`    |

**规则**：

- 实体方法**禁止**依赖外部服务（Mapper、其他 Service）
- 实体方法**禁止**修改自身状态（保持只读计算）
- 复杂逻辑（涉及多表查询）不应放实体类，放 DomainService

#### 8.4.7 已识别的复用案例

以下为当前 permission-center 已实现的复用模式，**后续开发必须沿用**：

```
调度层 PermissionGrantAppServiceImpl
  └→ 调用 SubjectDomainService.resolveEffectiveRoles()（带缓存）
  └→ 调用 PermissionGrantDomainService.revokePermissions()（级联删除）
  └→ 登记 PermissionChangeContext.markRoles()（@PermissionChange AOP afterCommit 失效 + 广播）

调度层 PermissionViewAppServiceImpl
  └→ 调用 SubjectDomainService.resolveEffectiveRoles()（角色解析+缓存）
  └→ 调用 TypeResolutionService.resolveTypeValue() / resolveRoleId()（类型转换）

调度层 ResourceManageAppServiceImpl
  └→ 调用 ResourceEntityDomainService.batchGetDescendantIds()（层级遍历）
  └→ 调用 OperationPermission.getEffectiveBits()（实体方法）
```

**违反此规范的代码评审时必须打回修改**。

#### 8.4.8 N+1 查询性能问题禁止

**定义**：N+1 问题是指在循环中逐个查询数据库，导致 1 次主查询 + N 次额外查询的性能问题。这是 Service 层最常见且最严重的性能缺陷。

**禁止场景**：

| 禁止模式       | 问题示例                                                    | 正确方案                                 |
| -------------- | ----------------------------------------------------------- | ---------------------------------------- |
| 循环内单条查询 | `for (Long id : ids) { mapper.selectOneById(id); }`         | `mapper.selectListByQuery(ids)` 批量查询 |
| 循环内关联查询 | `for (Role r : roles) { r.getPermissions(); }` 每次查权限表 | 先批量查所有权限，再按 roleId 分组       |
| 循环内类型解析 | `for (Entity e : list) { typeService.resolve(e.type); }`    | 批量解析或使用缓存 Map                   |
| 循环内外部调用 | `for (User u : users) { feign.getUserDetail(u.id); }`       | 批量 Feign 接口或本地批量查询            |

**检测方法**：

1. **代码审查**：搜索以下模式组合
   - `for` / `while` 循环 + `selectOneById` / `selectOneByQuery`
   - `stream().map()` 内部调用 Mapper 方法
   - 循环内调用 DomainService 单条查询方法

2. **日志监控**：开启 MyBatis-Flex SQL 日志，观察同一请求内相同 SQL 执行次数
   - 同一 SQL 执行次数 > 3 次，大概率是 N+1 问题
   - 同一请求 SQL 总数 > 20 条，需审查是否有 N+1

3. **性能测试**：接口响应时间随数据量线性增长，可能是 N+1

**正确批量查询模式**：

```java
// ❌ N+1 错误模式
List<Long> roleIds = ...;
Map<Long, AbstractRole> roleMap = new HashMap<>();
for (Long roleId : roleIds) {
    AbstractRole role = abstractRoleMapper.selectOneById(roleId);  // N 次查询
    roleMap.put(roleId, role);
}

// ✅ 批量查询正确模式
List<Long> roleIds = ...;
List<AbstractRole> roles = abstractRoleMapper.selectListByQuery(
    QueryWrapper.create().where(ABSTRACT_ROLE.ID.in(roleIds))
);
Map<Long, AbstractRole> roleMap = roles.stream()
    .collect(Collectors.toMap(AbstractRole::getId, r -> r));  // 1 次查询
```

**批量查询设计要求**：

| 要求                           | 说明                                                           |
| ------------------------------ | -------------------------------------------------------------- |
| **DomainService 提供批量方法** | 高频查询场景必须提供 `batchGetXxx(List<Long> ids)` 方法        |
| **批量结果用 Map 返回**        | 返回 `Map<Long, Xxx>` 便于调用方按 ID 快速获取                 |
| **批量方法内含缓存**           | 批量方法应利用缓存，避免每次批量查询都穿透到 DB                |
| **分批处理**                   | 批量 ID 数量 > 1000 时分批查询（每批 500-1000），避免 SQL 过长 |

**已识别的批量优化案例**：

| 场景             | 批量方法                                                             |
| ---------------- | -------------------------------------------------------------------- |
| 权限批量检查     | `PermQueryEngine.validateBatch()` / `PermQueryEngine.getDeniedIds()` |
| 授权批量校验     | `PermissionGrantDomainService.checkCanGrant()`                       |
| 角色批量加载     | `PermQueryEngine.batchLoadRoles(Set<Long> roleIds)`                  |
| 操作权限批量加载 | `PermQueryEngine.batchLoadOperations(Set<Long> opIds)`               |

**N+1 问题跟踪**：

| 状态      | Service                        | 方法                   | 问题描述                          |
| --------- | ------------------------------ | ---------------------- | --------------------------------- |
| ✅ 已修复 | `UserServiceImpl`              | `batchCreateUsers`     | 循环内单条查询父组织/检查编码重复 |
| ✅ 已修复 | `OrgServiceImpl`               | `batchCreateOrgs`      | 循环内单条查询父组织/检查编码重复 |
| ✅ 已修复 | `MenuServiceImpl`              | `batchCreateMenus`     | 循环内单条查询父菜单/检查路径重复 |
| ❌ 待修复 | `UserOrgServiceImpl`           | `setPrimaryOrg`        | 循环内单条查询用户组织关系        |
| ✅ 已修复 | `DictServiceImpl`              | `listDictTypes`        | 循环内单条查询字典类型详情        |
| ❌ 待修复 | `RoleManageAppServiceImpl`     | `deleteRoles`          | 循环内单条查询角色权限并删除      |
| ❌ 待修复 | `UserManageAppServiceImpl`     | `assignRole`           | 循环内单条查询用户并分配角色      |
| ❌ 待修复 | `UserManageAppServiceImpl`     | `assignRolesBatch`     | 循环内单条查询用户并批量分配角色  |
| ❌ 待修复 | `ResourceManageAppServiceImpl` | `batchCreateResources` | 循环内单条查询父资源/检查编码重复 |
| ❌ 待修复 | `ResourceManageAppServiceImpl` | `deleteResources`      | 循环内单条查询资源依赖并删除      |

**评审标准**：

- 新增 Service 方法包含循环 + 数据库查询，**必须打回**
- 已有方法发现 N+1 问题，**必须修复**（优先级：P1）
- 循环内调用外部服务（Feign/HTTP），**必须打回**（改用批量接口）

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

### 12.0 双层缓存框架（统一实现）

> **完整规范见 `.claude/skills/dual-layer-cache-framework/SKILL.md`**

项目已统一到 `common/cache/` 模块，所有业务缓存必须使用以下组件：

- **唯一入口**: `CacheService`
- **类型描述符**: `CacheCatalogEntry<V>`
- **模式枚举**: `CacheMode`
- **L1_L2 实现**: `CombinedL1L2Store`
- **L2_ONLY 实现**: `RedissonBucketStore`
- **L1_ONLY 实现**: `CaffeineLocalCacheStore`
- **配置**: `CacheProperties`（代码默认值 + `accessmesh.cache.default` / `accessmesh.cache.catalogs.*` 运维覆盖）
- **自动配置**: `CacheAutoConfiguration` 始终创建唯一 `CacheService`；`RedissonCacheAutoConfiguration` 只在 Redisson 可用时补充 store bean

**核心规范**：

| 项目             | 规范                                                 | 说明                                                      |
| ---------------- | ---------------------------------------------------- | --------------------------------------------------------- |
| **业务调用模式** | `get` → miss 后业务加载 → `put` → `evictAfterCommit` | 统一使用显式 Cache Aside，不提供 loader 回调 API          |
| **键格式**       | `{tenantId}:{catalogCode}:{identifier}`              | `catalogCode` 必须自带服务前缀，如 `perm:effective-roles` |
| **L1_L2**        | `CombinedL1L2Store`                                  | Caffeine L1 + Redisson `RBucket` L2，支持条目级 TTL       |
| **L2_ONLY**      | `RedissonBucketStore`                                | 纯 Redis 分布式缓存                                       |
| **L1_ONLY**      | `CaffeineLocalCacheStore`                            | 纯本地缓存，适用于 gateway 等无 Redisson 依赖模块         |
| **事务后失效**   | `evictAfterCommit` / `evictBatchAfterCommit`         | 由 `CacheService` 内部感知事务状态                        |

业务服务只允许注入 `CacheService`，不再为单个缓存创建 `CacheManager`、region 类或 loader 回调适配层。

**禁止事项**：

- ❌ 禁止使用 `ConcurrentHashMap` 替代 Caffeine（缺少 TTL、容量限制）
- ❌ 禁止使用 Redis KEYS 命令（用 SCAN）
- ❌ 禁止继续使用 `GenericCacheManager` / `AbstractGenericCacheManager`
- ❌ 禁止创建单缓存 `CacheManager` / region 类
- ❌ 禁止提供 loader 回调式缓存 API
- ❌ 禁止业务缓存直接操作 `RedisTemplate` / `StringRedisTemplate` / 裸 `Caffeine`
- ❌ 禁止业务缓存继续使用 `@Cacheable` / `@CacheEvict`
- ❌ 禁止在循环中调用单条查询方法（用批量方法）
- ❌ 禁止数据变更后不触发缓存失效
- ❌ 禁止业务侧手写 `TransactionSynchronizationManager.registerSynchronization` 做缓存失效

---

### 12.1 缓存策略（已由框架统一实现）

- 采用 **L1（本地 Caffeine）+ L2（Redis）** 的统一抽象，但是否启用由 `CacheMode` 决定。
- 缓存一致性模式：统一使用 **显式 Cache Aside** 模式。
  - 读：先 `cacheService.get(...)`，miss 后由业务代码查询 DB 或领域服务，再显式 `put` / `putBatch`。
  - 写：先写 DB，再调用 `cacheService.evictAfterCommit(...)` 或 `evictBatchAfterCommit(...)`。
- 业务代码不需要感知 store 内部细节，也不要自行规定 L1/L2 的内部操作顺序。

### 12.2 缓存 Key 命名规范

```
{tenantId}:{catalogCode}:{identifier}
```

示例：

| Key                              | 说明                          |
| -------------------------------- | ----------------------------- |
| `1:perm:effective-roles:1234`    | 租户 1 的用户有效角色         |
| `1:admin:dict-types:all`         | 租户 1 的字典类型列表         |
| `1:perm:role-perm-snapshot:5678` | 租户 1 的角色权限快照（roleId）|

规则：

- `catalogCode` 必须使用服务前缀，推荐形如 `perm:effective-roles`、`admin:dict-types`、`gw:perm-check`。
- 全部小写，段之间用 `:` 分隔。
- 禁止在 Key 中拼接未经规整的原始用户输入（防止 Key 冲突/注入）。

### 12.3 缓存 TTL 规范

| 维度       | 规范                                                               |
| ---------- | ------------------------------------------------------------------ |
| 默认值来源 | 优先使用 `CacheCatalogEntry` 中声明的 TTL / size                   |
| 运维覆盖   | 使用 `accessmesh.cache.default.*` 与 `accessmesh.cache.catalogs.*` |
| L1_ONLY    | 只配置 L1 TTL 与 size                                              |
| L2_ONLY    | 只配置 L2 TTL                                                      |
| L1_L2      | 同时配置 L1 与 L2                                                  |

具体 TTL 应按 catalog 粒度定义，不再使用模块私有的分散常量或 Spring Cache region 配置。

### 12.4 缓存使用禁止项

- **禁止缓存超大对象**（单个 Key value > 1MB，需拆分或分页）。
- **禁止在写事务里直接执行业务缓存失效**，必须优先使用 `evictAfterCommit` / `evictBatchAfterCommit`。
- **禁止缓存接口提供 loader 回调、匿名函数回填或 manager 继承模板**。
- **禁止把业务缓存实现散落到模块内的私有 Caffeine / RedisTemplate 封装中**。

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
- **租户隔离（MyBatis-Flex TenantFactory 自动处理）**：
  - 所有多租户数据表必须包含 `tenant_id` 列。
  - **已全局配置 TenantFactory**：`MybatisFlexTenantConfig` 通过 `TenantManager.setTenantFactory()` 自动为所有 SQL 查询添加 `tenant_id = ?` 条件。
  - **开发者无需手动添加 tenant_id 条件**：`selectOneById(id)`、`selectListByQuery()` 等方法会自动注入租户过滤。
  - **前提条件**：请求入口必须通过 `TenantInterceptor` 设置 `TenantContextHolder.setTenantId()`，否则租户过滤不生效。
  - **特殊场景**：如需跨租户查询（仅限系统管理场景），使用 `TenantManager.ignore()` 临时绕过，但必须在代码中添加注释说明原因。
- **禁止存储明文密码**。
- 大字段（JSON 配置等）使用 PostgreSQL `JSONB` 类型。
- 时间字段统一使用 `TIMESTAMPTZ`（带时区）。

### 13.4 索引规范

- 主键外所有高频查询条件字段必须建索引。
- 多条件查询建**复合索引**，遵循最左前缀原则。
- 单表索引数量不超过 5 个（避免写放大）。
- 禁止在低基数字段（如 `delete_flag`）上单独建索引。

### 13.5 SQL 书写规范

- **所有自定义 SQL 必须写在 XML 文件中**，禁止使用 `@Update`、`@Select`、`@Insert`、`@Delete` 等注解直接书写 SQL。
- XML 文件位置：`src/main/resources/mapper/XxxMapper.xml`。
- Mapper 接口只声明方法签名，不包含 SQL 内容。

**规范示例**：

```xml
<!-- src/main/resources/mapper/UserRoleMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper">

    <update id="softDeleteBatch">
        UPDATE user_role
        SET delete_flag = id, deleted_at = #{deletedAt}
        WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        AND delete_flag = 0 AND tenant_id = #{tenantId}
    </update>

</mapper>
```

```java
// src/main/java/.../mapper/UserRoleMapper.java
public interface UserRoleMapper extends BaseMapper<UserRole> {

    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
```

**禁止示例**：

```java
// ❌ 禁止使用注解书写 SQL
@Update("<script>UPDATE user_role SET delete_flag = id WHERE id IN <foreach...></script>")
int softDeleteBatch(...);
```

**原因**：

- XML 文件便于 SQL 维护和版本管理
- 复杂 SQL（多条件、动态拼接）在 XML 中更清晰
- 避免注解字符串过长影响代码可读性
- 统一风格便于团队协作和代码审查

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

- Gateway 必须清洗外部请求中伪造的 `X-Tenant-Id`、`X-User-Id`、`X-Service-Code` 等安全 Header，再根据 Token 或可信服务身份重新注入。
- 业务服务和 permission-center 只能信任 Gateway/Feign 拦截器注入的安全上下文，不得直接信任客户端原始 Header。

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

---

## 17. 重构触发条件与代码健康度

### 17.1 重构触发信号

出现以下信号时必须启动重构：

| 触发信号 | 阈值 | 重构动作 |
|----------|------|----------|
| **代码量超标** | 单类超过 1500 行 | 拆分为多个专注类 |
| **依赖过多** | 构造函数注入超过 8 个 | 职责拆分 |
| **重复代码** | 同一 SQL/逻辑出现在 3 处以上 | 抽取到 DomainService/Mapper |
| **N+1 查询** | 循环内单条 DB 查询 | 批量方法 + 缓存 |
| **多路径并存** | 同一功能存在多条独立实现路径 | 统一 Engine/入口 |

### 17.2 代码健康度检查清单

每次代码审查和重构时检查：

- [ ] 是否存在可复用的 DomainService 方法但未使用？
- [ ] 是否存在 N+1 查询风险（循环 + DB 查询）？
- [ ] 是否使用了已删除的类或字段（见 `.claude/rules/permission-center-coding-standards.md` §17-18）？
- [ ] 异常类型是否正确（BizException / SystemException / SecurityException）？
- [ ] 事务边界是否在 AppService 层声明？
- [ ] 缓存失效是否绑定事务提交后执行？

### 17.3 重构流程

大规模重构采用分 Phase 推进：

1. **Phase N**: 实现阶段目标（独立提交）
2. **fix**: 代码审查修复（独立提交）
3. **docs**: 仍在推进的计划放在 `docs/plans/`；阶段完成后把过程文档归档到 `docs/archive/YYYY-MM-DD/`，并将长期有效结论沉淀到 `docs/design/`

每个 Phase 保持可独立交付，避免一次性大改动导致难以审查。

## 文档治理（仓库级权威，参见 .claude/rules/docs-governance.md 引用）

### 分层职责

| 文档 | 职责 | 禁止 |
|---|---|---|
| `api-contract.md` | 字段/错误码/请求响应结构（唯一详细来源） | - |
| 任务卡 `T-*.md` | 该任务详细范围/验收/背景（唯一详细来源） | - |
| `implementation.md`/`core-flows.md` | 实现设计/核心流程（引用 api-contract，不重复字段定义） | 重复字段表 |
| `phase*-plan.md`/`tasks/README.md` | 任务清单（标题/状态/直接依赖/链接）+ 计数器 + 依赖图 | 复制"七项/八项/第几轮/完整字段清单/🔧 详细范围" |

phase plan / README 任务行只保留：标题（简短）、状态、直接依赖、链接。详细范围写进任务卡，不复制到计划/索引。

### 禁止记录讨论轮次

- 设计、计划、任务、索引和项目规则中**禁止记录“第 N 轮讨论/评审/复审”**，不得把讨论轮次写入标题、状态、验收项、正文注释、frontmatter 注释或变更摘要。
- 文档只保留**当前有效结论**及必要的确认日期、责任任务、适用范围和取代关系；需要说明变化时，直接写“原口径已废弃 / 当前口径为 …”，不得用轮次定位。
- 讨论过程留在会话、Git 历史或归档材料中；正式决策需要长期追溯时使用 ADR/确认记录，并按**主题/决策编号**组织，不按讨论先后轮次组织。
- 触达含轮次标记的存量活跃文档时，应在不改变已确认语义的前提下改写为当前结论；不得继续复制轮次标记到新的权威文档或任务卡。

### 写入口通用清单（适用影响权限计算或缓存的写入口）

1. `@Transactional(rollbackFor=Exception.class)` 单事务原子
2. `@OperationLog` 入口级操作日志
3. `@PermissionChange` 缓存失效 AOP（afterCommit flush mark\*）--**按影响范围适用**：仅影响权限计算/缓存的写入口标注；普通配置写操作不强制
4. 契约要求的审计记录（如 `auditDomainService.recordChangeLog`，按写入口语义写合适的 change_log）
5. 按影响范围调用适当的 `PermissionChangeContext.mark*`（角色权限事实写入口用 `markRoles`；条件变更用 `markConditions`；服务/API 变更用 `markServiceCodes`）

> apply-grant-plan 的精确五项（recordChangeLog + markRoles）见 implementation.md §4.2 与 T-PERM-034 acceptance，本规则不复制任务细节。

### 定稿前关键词扫描

方案收窄后、提交前，对活跃文档（排除 `docs/archive/`）执行关键词扫描，清除正向残留（描述性引用如"已砍/移除/无"可保留）：

```
grep -rn "expectedRevision\|grant_revision\|grant_plan_idempotency\|20037\|20039\|@Idempotent\|clientRequestId\|perm-grant.schema" docs/ --include="*.md" --include="*.sql" | grep -v "docs/archive/"
```

### 测试适用性覆盖

禁止笛卡尔积测试（命令类型 × 不变量全交叉）。采用适用性覆盖：每种命令一条成功路径 + 每项不变量在适用命令上一条反例 + 一条事务故障注入。详细适用关系由各任务 acceptance 定义，本规则不复制。

### 方案收窄流程

改 API 契约 + 任务卡（唯一详细来源）-> phase plan/README 只同步标题/状态/依赖/链接 -> 执行关键词扫描 -> 历史决策移归档/ADR。
