---
doc_type: design
title: 项目开发规范（PROJECT RULES）
status: adopted
domain: common
last_reviewed: 2026-09-07   # 规范性文件审查修复（traceId 口径/log4j2 单文件/N+1 表指针化/软删例外/租户入口/openfeign 前缀/路径模板）；2026-09-06 T-ADMIN-027：§1.3 补信封承载类单源指引；2026-08-12 access-service 归并：错误码继续按管理域/权限域分段
---

# 项目开发规范（PROJECT RULES）

> 本文档是项目全体开发成员必须遵守的工程规范，覆盖报文、接口、异常、日志、依赖、工具类、对象设计、事务、安全、缓存、数据库、MQ、服务调用、Git 等方面。
>
> **技术栈基线：** Spring Boot 3 + Java 21 + MyBatis-Flex + Spring Cloud + RocketMQ（预留未启用）+ PostgreSQL + Redis

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
  "message": "success",
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
| `traceId`   | `String` | 链路追踪 ID，`RResponseAdvice` 回填：优先取上游 `X-Trace-Id` 请求头，缺省与 `requestId` 相同（`X-Request-Id` 由 Gateway `RequestIdFilter` 生成/透传） |

> 响应壳构造统一使用 `R.ok(data)` / `R.fail(code, message)`；`requestId/traceId` 由 `RResponseAdvice` 在序列化前回填，业务代码不写入。

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
| `pageSize` | `int`    | 每页条数，默认 10，最大 200（`PageUtil`）                |
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

分页信封承载类为 perm-common `PageResp<T>`，无分页列表承载类为 `ItemsResp`（`perm.common.dto.resp`，admin/permission 域与 SDK 单一来源，全仓不建同构副本）。

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
/api/{module}/{resource}/{action}
```

示例（permission 域现行 Controller 实际形态）：

| 路径                              | 说明       |
| --------------------------------- | ---------- |
| `/api/perm/abstract-role/create`  | 创建角色   |
| `/api/perm/abstract-role/update`  | 更新角色   |
| `/api/perm/abstract-role/remove`  | 删除角色   |
| `/api/perm/abstract-role/list`    | 分页列表   |
| `/api/perm/log/operation/list`    | 操作日志   |

> admin 域存量为资源根三段形态（Controller 挂 `/user`、`/org` 等，如 `/user/create`），外部经 Gateway 路由 `/admin/**`（StripPrefix=1）访问；新增接口（含 admin 域）统一按上方四段模板。

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

> 表值与 `common` 模块 `GlobalExceptionHandler` 实际实现一致（2026-08-29 双轨评审收口：原表「统一 200」与代码的 400/403/500 长期分歧，按代码对齐）。

| 异常类型                          | HTTP 状态码 | code       | 日志级别 | 堆栈 |
| --------------------------------- | ----------- | ---------- | -------- | ---- |
| `BizException`                    | 200         | 业务错误码 | WARN     | 否   |
| `SystemException`                 | 200         | 系统错误码 | ERROR    | 是   |
| `SecurityException`               | 403         | `403`      | WARN     | 否   |
| `MethodArgumentNotValidException` | 400         | `90001`    | WARN     | 否   |
| `ConstraintViolationException`    | 400         | `90001`    | WARN     | 否   |
| `IllegalArgumentException`        | 400         | `400`      | WARN     | 否   |
| `HttpMessageNotReadableException` | 400         | `90001`    | WARN     | 否   |
| `Exception`（兜底）               | 500         | `99999`    | ERROR    | 是   |

> 业务异常（`BizException`/`SystemException`）HTTP 200，由 `success` + `code` 区分业务成功与失败；
> 安全拒绝与参数/请求体类错误使用语义化真实状态码（403/400），兜底异常 500——不向客户端暴露堆栈。
>
> **Gateway 对外响应**使用真实 HTTP 状态码（401 未认证、403 鉴权拒绝、502 上游异常、503 服务不可用等），
> 前端需根据 HTTP 状态码做差异化处理。内部服务间调用（OpenFeign）业务语义仍以 body `code` 为准。

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
| `traceId`     | `RequestContextInterceptor` 写入 MDC：取 `X-Request-Id` 头，缺省生成 UUID（截断 64 字符） |
| `userId`      | 网关解析 Token 后写入请求 Header，服务层从 SecurityContext 读取 |
| `tenantId`    | 同上                                                            |
| `serviceCode` | 服务启动时从配置文件读取                                        |

- access-service 由 `RequestContextInterceptor` 在请求入口设置 MDC，请求结束后**必须 clear**（防 ThreadPool 污染）。
- Feign 跨服务透传现状：SDK `FeignInternalSyncInterceptor` 仅注入 `X-Internal-Secret` / `X-Service-Code`；统一链路追踪头透传尚未实现（目标头为 `X-Request-Id`，与 Gateway 头链同名，见 §14.2），跨服务日志关联暂依赖 Gateway 的 `X-Request-Id` 头链。

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

三服务各使用单文件 `log4j2-spring.xml`（`application.yml` 经 `logging.config: classpath:log4j2-spring.xml` 显式指定）；上述保留期为部署侧目标要求，按环境在该文件/部署配置中落实。

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

> 适用范围为可变业务实体；例外见 §13.2（四类日志表不做软删、纯关联状态表无审计字段，以权威 DDL 为准）。

### 7.4 日期时间类型

- **全面使用 Java 8+ 时间 API**：`LocalDateTime`、`LocalDate`、`LocalTime`。
- **禁止**使用 `java.util.Date`、`java.sql.Timestamp`、`java.sql.Date`。
- **时间语义全链路 UTC（T-ACCESS-024）**：JVM 默认时区由 common `UtcTimezoneEnvironmentPostProcessor` 启动即强制 UTC（代码级，应用与 `@SpringBootTest` 同源生效，部署无需 `-Duser.timezone`/`TZ`）；`LocalDateTime` ↔ TIMESTAMPTZ 由全局 `TimestamptzLocalDateTimeTypeHandler` 显式按 UTC 换算（经 `OffsetDateTime`，不经 `java.sql.Timestamp`/JVM 时区漂移；handler 落位 access-service、经 `MybatisFlexTypeHandlerConfig` 全局注册，新服务模块引入 DB 实体时须复制同等 handler——不上提 common 以免引入 ORM 依赖）；JDBC URL 禁止携带 `serverTimezone`（MySQL 语义参数，pgjdbc 忽略且误导）。未来多时区部署另立任务，本项目不做跨时区支持。
- Jackson 序列化配置：`LocalDateTime` 序列化为 ISO-8601 字符串（`"2026-04-21T10:00:00"`，无偏移，语义=UTC 墙钟）；全局 ObjectMapper（common `cacheObjectMapper`）时区固定 UTC。前端展示时区转换按需另行处理，服务端不做「应用层转本地时区」。
- **63 位 bigint 列 JSON 序列化为十进制字符串（T-PERM-028 定策略，首例 `OperationPermissionResp.binaryBit/inheritMask`）**：JSON number 在 >2^53 丢精度，暴露该类列的响应 DTO 字段加 `@JsonSerialize(using = ToStringSerializer.class)`（内部类型保持 `Long`）；请求侧 `Long` 组件由 Jackson 宽容接受十进制字符串，前端线格式统一 string、位运算用 BigInt。新增暴露 bigint 位值/大数列的 DTO 沿用此策略。

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
- **允许同层横向调用（2026-08-22 用户确认全局放开）**：同层级之间允许互相复用（调度层 Service 互调、DomainService 互调、跨域 Service/AppService 注入复用，如 infrastructure 安全拦截器注入 admin 域 `OAuth2ClientDomainService`、`PermissionGrantPlanDomainServiceImpl` 组合 `PermissionGrantDomainService` 校验能力），无需逐一登记例外。通用约束：① 仅限同层之间（调度层↔调度层、DomainService↔DomainService；跨层仍遵守跳层禁令）；② 不得形成循环依赖；③ 复用方不重复实现被复用方已有的领域逻辑（与 §8.4 复用规范一致）；④ 跨域 Mapper 直读边界不变——admin/permission 域互不直读对方 Mapper（`QueryBoundaryArchitectureTest` 数据边界断言继续生效）。历史：2026-08-08 授权域、2026-08-15 query 包（T-ACCESS-006）、2026-08-20 审计门面三个单点例外的登记随全局放开废止，其限定语义（单向、只读复用、不承载事务）收敛为上述通用约束。
- Mapper 层只做数据访问，禁止包含分支业务逻辑（`if`/`switch` 等）。

**permission-center Controller（补充）：**

- 查询编排类接口（如 `query-resources`、`operation-log/list`）的分页过滤、JSON 解析、多表组装须在**调度层 Service**（如 `PermissionQueryAppService`、`LogQueryAppService`）完成；Controller 仅做校验与 `R` 包装。（原举例 permission-view/explain 已随 T-PERM-059 删除，2026-09-10）

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
| **调度层 AppService**    | `XxxAppService` / `XxxAppServiceImpl`       | 业务流程编排、跨领域协调、外部接口契约转换、权限检查、事务边界 | `batchGrant()`、`check()`、`queryResources()`                           |
| **逻辑级 DomainService** | `XxxDomainService` / `XxxDomainServiceImpl` | 单一领域逻辑、可复用的原子操作、内部数据转换、缓存管理         | `resolveEffectiveRoles()`、`batchGetDescendantIds()`、`checkCanGrant()` |

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
| 权限级联删除             | `PermissionGrantPlanDomainService.apply()`（removes 软删+级联+行数断言 20036） | 调度层写子权限删除循环        |
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
  └→ 调用 PermissionGrantPlanDomainService.prevalidate/apply（removes 软删+级联，行数断言 fail-closed）
  └→ 登记 PermissionChangeContext.markRoles()（@PermissionChange AOP afterCommit 失效 + 广播）

调度层 PermissionViewAppServiceImpl
  └→ 调用 SubjectDomainService.resolveEffectiveRoles()（角色解析+缓存）
  └→ 调用 TypeResolutionService.resolveTypeValue() / resolveRoleId()（类型转换）

调度层 ResourceManageAppServiceImpl
  └→ 调用 ResourceEntityDomainService.batchGetDescendantIds()（层级遍历）
  └→ 权限判定统一走 PermQueryEngine（hasPermissionByCode / hasPermissionByEntityId / getDeniedEntityIds / getDeniedResourceCodes）
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
| 权限批量检查     | `PermQueryEngine.getDeniedResourceCodes()` / `PermQueryEngine.getDeniedEntityIds()`（T-PERM-042 终态，旧 validateBatch/getDeniedIds 已删除） |
| 授权批量校验     | `PermissionGrantDomainService.checkCanGrant()`                       |
| 角色批量加载     | `PermQueryEngine.batchLoadRoles(Long tenantId, Set<Long> ids)`（引擎内部私有方法；外部批量加载走 `abstractRoleMapper.selectValidByIds(tenantId, ids)`） |
| 操作权限批量加载 | `PermQueryEngine.batchLoadOperations(Long tenantId, Set<Long> ids)`（引擎内部私有方法；外部批量加载走 `operationPermissionMapper.selectValidByIds(tenantId, ids)`） |

存量 N+1 整改进度以任务板（`docs/tasks/README.md`）为准，本规范不维护逐方法状态表（避免随代码演进腐化）；规范本体只保留上表的批量约束与批量模式。

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

**新增权限码五步清单（2026-08-28 定规，漏一步即功能静默断链）**——引入新的资源类型/权限码时，以下五处必须同批完成：

1. `ResourceTypeCode` 枚举常量（漏：编译期暴露，最不易漏）；
2. 权威 DDL `type_definition` 种子行（漏：类型解析 fail-closed 全量拒绝）；
3. bootstrap 固定图 `BootstrapGraphDefinition.businessGrants` + `AccessBootstrapInitializer.GRANT_RESOURCE_TYPES`（漏：新码无人持有即无授予起点，死锁）；
4. `UserMenuQueryServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES` 下发白名单（漏：**前端 `hasPerms` 永远拿不到该串**，页面按钮/路由静默隐藏——DB 授权真实存在、后端门禁真实通过、全部测试绿，极难发现；2026-08-28 T-PERM-025 P0 教训，`UserMenuQueryServiceImplTest` 有防再犯断言）；
5. 前端对应页面 `perms.ts` SSOT 常量（漏：前后端门禁口径分叉）。

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
- **类型描述符**: `CacheCatalogEntry<V>`（TTL 为 `java.time.Duration`，秒级精度）
- **读取令牌**: `CacheReadToken<V>`（授权 L2 miss 剩余 TTL 回填辅助）
- **模式枚举**: `CacheMode`
- **L1_L2 实现**: `CombinedL1L2Store`
- **L2_ONLY 实现**: `RedissonBucketStore`
- **L1_ONLY 实现**: `CaffeineLocalCacheStore`
- **配置**: `CacheProperties`（代码默认值 + `accessmesh.cache.default-config` / `accessmesh.cache.catalogs.*` 运维覆盖，Spring Boot Duration 文法如 `15s`/`5m`）
- **跨实例 L1 失效广播**: `CacheInvalidationBroadcaster`（Redisson 可用时自动装配，L1_L2 目录失效时经 RTopic 广播，各实例订阅清理本地 L1）
- **自动配置**: `CacheAutoConfiguration` 按 `accessmesh.cache.enabled` 装配唯一 `CacheService`（默认启用，显式 `false` 关闭）；`RedissonCacheAutoConfiguration` 只在 Redisson 可用时补充 store bean 与广播器

**核心规范**：

| 项目             | 规范                                                 | 说明                                                      |
| ---------------- | ---------------------------------------------------- | --------------------------------------------------------- |
| **业务调用模式** | `get` → miss 后业务加载 → `put` → `evictAfterCommit` | 统一使用显式 Cache Aside，不提供 loader 回调 API          |
| **键格式**       | `{tenantId}:{catalogCode}:{identifier}`              | `catalogCode` 必须自带服务前缀，如 `perm:effective-roles` |
| **L1_L2**        | `CombinedL1L2Store`                                  | Caffeine L1 + Redisson `RBucket` L2，条目级 TTL           |
| **L2_ONLY**      | `RedissonBucketStore`                                | 纯 Redis 分布式缓存                                       |
| **L1_ONLY**      | `CaffeineLocalCacheStore`                            | 纯本地缓存，适用于 gateway 等无 Redisson 依赖模块         |
| **事务后失效**   | `evictAfterCommit` / `evictBatchAfterCommit`         | 由 `CacheService` 内部感知事务状态；回滚不失效            |
| **全量失效**     | `evictAll(catalog, tenantId)` / `evictAll(catalog)`  | 后者为 catalog 级跨租户全清（订阅重连等恢复场景，L2 走 SCAN，禁止高频调用） |
| **单次有效 TTL** | `put(..., Duration)` / `putBatch(..., Duration)`     | 强制不超过 catalog 有效 TTL，剩余 ≤0 不写；L2 精确生效     |
| **剩余 TTL 回填** | `beginRead` → 查 DB → `put(token,...)` / `putBatch(token,...)` | 授权 L2 miss 在 DB 读取前记录单调时钟起点；回填只写「读取起点 + catalog TTL」剩余 TTL；单条/批量/并发合并/重试不得重置起点 |

**T-ACCESS-008 授权缓存安全边界（30 秒）**：

- access-service 内可能影响接口权限快照的 6 个目录（`perm:effective-roles`、`perm:role-perm-snapshot`、`perm:type-value`、`perm:type-code`、`perm:condition-rules`、`perm:role-mutex-rule`）统一 **L2_ONLY、TTL≤10s、不创建授权 L1**；有效 L2 TTL 超限由 `PermCacheBoundaryValidator` 启动校验强制（含 YAML 覆盖值）。
- Gateway 快照缓存 L1 TTL≤15s、快照加载全链路墙钟截止≤5s（`gateway.permission.snapshot-load-deadline`），由 `GatewayCacheBoundaryValidator` 启动校验强制；超截止不写缓存并固定 fail-closed 503。
- 三段预算 10s + 5s + 15s ≤ 30s 构成最坏陈旧窗口上限；正常失效（事务提交后 evict 共享 L2 + 广播清理 Gateway/普通 L1）目标毫秒到亚秒级。
- 权限缓存不可用时 access-service 绕过缓存查数据库；无法得到可信授权结果时 fail-closed。

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
- ❌ 禁止分钟制 TTL 字段（`l1TtlMinutes`/`l2TtlMinutes`/`l1-expire-minutes`/`l2-ttl-minutes` 已删除，不留兼容别名）
- ❌ 禁止业务侧硬编码 TTL 换算（统一 `Duration` + catalog 声明 + `accessmesh.cache` 覆盖）

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

| 维度       | 规则                                                               |
| ---------- | ------------------------------------------------------------------ |
| 类型       | 统一 `java.time.Duration` 秒级精度；YAML 使用 Spring Boot Duration 文法（`15s`/`5m`） |
| 默认值来源 | 优先使用 `CacheCatalogEntry` 中声明的 TTL / size                   |
| 运维覆盖   | 使用 `accessmesh.cache.default-config.*` 与 `accessmesh.cache.catalogs.*` |
| L1_ONLY    | 只配置 L1 TTL 与 size                                              |
| L2_ONLY    | 只配置 L2 TTL                                                      |
| L1_L2      | 同时配置 L1 与 L2                                                  |
| 安全边界   | 快照链路 6 目录有效 L2 TTL≤10s、Gateway 快照 L1≤15s——超限启动失败  |

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

> 例外（以权威 DDL `docs/design/schema/access-service.sql` 为准）：`sys_login_log`、`operation_log`、`sys_job_log`、`permission_change_log` 四类日志表不做软删除；`sys_user_notice` 等纯关联状态表无审计字段、唯一索引不加 `WHERE delete_flag = 0`（行生命周期即业务状态，物理删除/唯一键即可表达）。

### 13.3 设计原则

- **无物理外键**：所有表关联为逻辑 ID，由应用层保证数据一致性。
- **软删除**：统一使用 `delete_flag`（删除时设为本行 id 值），可变业务表的唯一约束必须附加 `WHERE delete_flag = 0`；四类日志表与纯关联状态表例外见 §13.2。
- **无 ENUM 类型**：枚举值使用 `INT` 或 `VARCHAR`，枚举含义在代码枚举类中维护。
- **租户隔离（MyBatis-Flex TenantFactory 自动处理）**：
  - 所有多租户数据表必须包含 `tenant_id` 列。
  - **已全局配置 TenantFactory**：`MybatisFlexTenantConfig` 通过 `TenantManager.setTenantFactory()` 自动为所有 SQL 查询添加 `tenant_id = ?` 条件。
  - **开发者无需手动添加 tenant_id 条件**：`selectOneById(id)`、`selectListByQuery()` 等方法会自动注入租户过滤。
  - **前提条件**：请求入口必须经过 `RequestContextInterceptor`（唯一绑定 `AccessRequestContext` 的 HTTP 入口，T-ACCESS-004 起替换旧 TenantInterceptor/PermTenantInterceptor 双链）设置租户上下文，否则租户过滤不生效；`TenantContextHolder` 仅作兼容门面委托该上下文，新代码直接用 `AccessRequestContext`。
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
<mapper namespace="cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper">

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
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000 # 连接超时 3s
            read-timeout: 5000 # 读取超时 5s
          # 客户端键与 @FeignClient(name=...) 一致（本仓库为 access-service）
          access-service:
            read-timeout: 10000 # 鉴权接口可适当放宽
```

### 14.2 Header 透传

> **现状（与 §4.3 口径一致）**：当前唯一装配的 Feign `RequestInterceptor` 是 SDK 的
> `FeignInternalSyncInterceptor`，仅注入 `X-Internal-Secret` / `X-Service-Code`；
> `X-Tenant-Id` 由调用方业务侧拦截器从 ThreadLocal 注入；`Authorization` 与链路追踪头
> 尚无统一透传——跨服务日志关联暂依赖 Gateway 的 `X-Request-Id` 头链（`RequestIdFilter`
> 生成 → `HeaderEnrichFilter` 注入下游，服务侧 `RequestContextInterceptor` 写 MDC）。
> 下表为目标态契约：新增统一透传拦截器时按此实现并补契约测试，勿在现状下当作已生效规范引用。

目标态：所有 Feign 调用必须通过 `RequestInterceptor` 透传以下 Header：

| Header           | 说明                         |
| ---------------- | ---------------------------- |
| `Authorization`  | Bearer Token（用户身份透传） |
| `X-Request-Id`   | 链路追踪 ID（与 Gateway 请求关联头同名，衔接 MDC `traceId`） |
| `X-Tenant-Id`    | 租户 ID                      |
| `X-Service-Code` | 调用方服务标识               |

- Gateway 必须清洗外部请求中伪造的 `X-Tenant-Id`、`X-User-Id`、`X-Service-Code` 等安全 Header，再根据 Token 或可信服务身份重新注入。
- 业务服务只能信任 Gateway/Feign 拦截器注入的安全上下文，不得直接信任客户端原始 Header。

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
| 任务卡 `T-*.md` | 该任务详细范围/验收/背景/当前口径（唯一详细来源） | 评审轮次日记、「用户决策」过程清单 |
| `implementation.md`/`core-flows.md` | 实现设计/核心流程（引用 api-contract，不重复字段定义） | 重复字段表 |
| `phase*-plan.md`/`tasks/README.md` | 任务清单（标题/状态/直接依赖/链接）+ 计数器 + 依赖图 | 复制"七项/八项/第几轮/完整字段清单/🔧 详细范围" |
| `decision-registry.md` | 定案登记（日期+一句话口径+出处锚点+评审处置；会话内定案无仓库正文时口径写全） | 复制定案正文、讨论过程叙事 |

phase plan / README 任务行只保留：标题（简短）、状态、直接依赖、链接。详细范围写进任务卡，不复制到计划/索引。

### 禁止记录讨论轮次

- 设计、计划、任务、索引和项目规则中**禁止记录“第 N 轮讨论/评审/复审”**，不得把讨论轮次写入标题、状态、验收项、正文注释、frontmatter 注释或变更摘要。**任务卡正文与设计同等适用。**
- 文档只保留**当前有效结论**（当前口径）及必要的确认日期、责任任务、适用范围和取代关系；需要说明变化时，直接写“原口径已废弃 / 当前口径为 …”，不得用轮次定位。
- 已采纳的「用户决策」写成当前口径并回写对应 design；任务卡不保留「某日用户决定」过程句。
- **注释/文档层去计数化（2026-08-28 定规）**：统计型计数（行数/条数/项数等清单总量汇总）不写入注释、javadoc、DisplayName 与叙事文档——它们复制测试已锁的事实且必然漂移；需要总量指引时写「以 X 测试断言为准」或不依赖数字的表述。**保留**：测试内计数断言（行为锁）、结构不变量数字（每类型四操作、63 位掩码）、语义型数字（类型值/bit 位/错误码段/上限值）、完成记录与提交信息中的「从 X 改为 Y」变更叙述。存量统计型计数按触达时清理。
- 讨论过程留在会话、Git 历史或归档材料中；正式决策需要长期追溯时登记 `docs/design/decision-registry.md`（用户定案当轮登记、旧口径被推翻标 superseded 不删；写入/消费协议见该文件），按**主题/日期**组织，不按讨论先后轮次组织。
- 触达含轮次标记的存量活跃文档时，应在不改变已确认语义的前提下改写为当前结论；不得继续复制轮次标记到新的权威文档或任务卡。
- `review → done` 须跑本节轮次词扫描（步骤见 `design-plan-task-lifecycle` skill）；遗留另开任务时必须使用看板计数器新分配、主题一致的未占用 ID。

### 写入口通用清单（适用影响权限计算或缓存的写入口）

1. `@Transactional(rollbackFor=Exception.class)` 单事务原子
2. `@OperationLog` 入口级操作日志（必填 `module`/`action`/`targetType`/`targetId`/`summary`；`module` 三值 `ADMIN/PERMISSION/ACCESS`，`action` 大写事件码 `{业务对象}_{动作}`，`targetType` 用小写表名、批量操作 `targetId=""`；方法参数不入库、摘要只记对象 ID/动作/结果——判定细节见 access-service-architecture §8.2，标注不再测试强制（T-ACCESS-025））
3. `@PermissionChange` 缓存失效 AOP（afterCommit flush mark\*）--**按影响范围适用**：仅影响权限计算/缓存的写入口标注；普通配置写操作不强制
4. 契约要求的审计记录（如 `auditDomainService.recordChangeLog`，按写入口语义写合适的 change_log）
5. 按影响范围调用适当的 `PermissionChangeContext.mark*`（角色权限事实写入口用 `markRoles`；条件变更用 `markConditions`；服务/API 变更用 `markServiceCodes`）

> apply-grant-plan 的精确五项（recordChangeLog + markRoles）见 implementation.md §4.2 与 T-PERM-034 acceptance，本规则不复制任务细节。

### 定稿前关键词扫描

方案收窄后、提交前，对活跃文档（排除 `docs/archive/`）执行关键词扫描，清除正向残留（描述性引用如"已砍/移除/无"可保留）。

授权契约残留（触达授权/grant 文档时）：

```
grep -rn "expectedRevision\|grant_revision\|grant_plan_idempotency\|20037\|20039\|@Idempotent\|clientRequestId\|perm-grant.schema" docs/ --include="*.md" --include="*.sql" | grep -v "docs/archive/"
```

轮次标记（任务 `review → done` 与文档定稿均须跑；命中则先改写成当前口径。存量 005/006 等历史卡不在本次清扫范围，不作为全库变绿门槛）：

```
grep -rn "第[一二三四五六七八九十0-9]\\+轮\\|评审修复\\|复评\\|评审修复记录" docs/ --include="*.md" --include="*.sql" | grep -v "docs/archive/"
```

### 测试适用性覆盖

禁止笛卡尔积测试（命令类型 × 不变量全交叉）。采用适用性覆盖：每种命令一条成功路径 + 每项不变量在适用命令上一条反例 + 一条事务故障注入。详细适用关系由各任务 acceptance 定义，本规则不复制。

### 方案收窄流程

改 API 契约 + 任务卡（唯一详细来源）-> phase plan/README 只同步标题/状态/依赖/链接 -> 执行关键词扫描 -> 历史决策移归档/ADR。
