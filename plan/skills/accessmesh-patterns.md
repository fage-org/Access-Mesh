---
name: accessmesh-patterns
description: Coding patterns extracted from AccessMesh (RuoYi-Cloud-Plus) repository
version: 1.0.0
source: local-git-analysis
analyzed_commits: 100
---

# AccessMesh Patterns

基于 Spring Cloud 微服务架构的通用访问控制平台编码规范与模式。

## Commit Conventions

项目使用 **Conventional Commits** 规范，支持中文描述：

| type       | 说明     | 示例                                                                              |
| ---------- | -------- | --------------------------------------------------------------------------------- |
| `feat`     | 新功能   | `feat(permission-center): 新增分组角色批量删除接口`                               |
| `fix`      | Bug 修复 | `fix(gateway): align PermissionClient with permission-center response model`      |
| `refactor` | 重构     | `refactor: decouple admin-service from permission-center via generic Feign proxy` |
| `docs`     | 文档     | `docs(plan): freeze phase0 baseline docs`                                         |
| `chore`    | 杄项     | `chore(ci): 简化构建流程并优化依赖安装`                                           |
| `perf`     | 性能优化 | `perf(cache): optimize L1 cache hit rate`                                         |

**格式**: `<type>(<scope>): <subject>`

---

## Code Architecture

### 服务架构

```text
Gateway (8080)
    ├── admin-service (9100)      用户/组织/菜单/认证
    ├── permission-center (9200)  核心权限引擎
    └── example-service (9300)    对接演示
```

### 分层架构

```text
Controller（接入层）
    │  入参校验(@Validated)、调用调度层、响应包装
    ▼
调度层 Service（XxxService / XxxServiceImpl）
    │  业务流程编排、跨领域协调、事务边界
    ▼
逻辑级 DomainService（XxxDomainService / XxxDomainServiceImpl）
    │  单一领域逻辑、可复用原子操作、缓存管理
    ▼
Mapper（数据访问层）
    │  SQL 封装，禁止包含业务判断
```

### 包结构

```
cn.ac.fage.accessmesh.{service}
├── controller          # 接入层
├── service
│   ├── impl            # 调度层 Service 实现
│   └── domain          # 逻辑级 Domain Service
│       └── impl        # Domain Service 实现
├── mapper              # MyBatis-Flex Mapper
├── entity              # 数据库实体（无后缀）
├── dto
│   ├── req             # 入参 XxxReq
│   └── resp            # 出参 XxxResp
├── enums               # 枚举
├── config              # 配置类
└── util                # 本模块专用工具类
```

---

## API Design Patterns

### HTTP 方法规范

**强制规则**：所有对外接口使用 `POST` + JSON Body

| 规则                 | 说明                                          |
| -------------------- | --------------------------------------------- |
| 禁止 `@RequestParam` | 所有参数通过 `@RequestBody` + Request DTO     |
| 禁止 GET/PUT/DELETE  | 统一使用 `@PostMapping`                       |
| 禁止路径参数         | ID 放 JSON Body，禁止 `/user/{id}`            |
| 例外                 | 文件上传 `MultipartFile` 可用 `@RequestParam` |

### 路径命名

```
/api/{module}/{resource}/{action}
```

| 路径               | 说明       |
| ------------------ | ---------- |
| `/api/user/create` | 创建       |
| `/api/user/update` | 更新       |
| `/api/user/delete` | 删除       |
| `/api/user/get`    | 单条查询   |
| `/api/user/page`   | 分页查询   |
| `/api/user/list`   | 不分页列表 |

### 统一响应体

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "requestId": "uuid-xxx",
  "traceId": "a3f2b1c0d4e5..."
}
```

**错误码分段**：

- `200` = 成功
- `10001-19999` = admin-service
- `20001-29999` = permission-center
- `30001-39999` = example-service
- `90001-99999` = 全局系统错误

### 分页规范

**请求**：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "sort": "createdAt,desc"
}
```

**响应**：

```json
{
  "data": {
    "items": [],
    "total": 100,
    "pageNum": 1,
    "pageSize": 20,
    "hasNext": true
  }
}
```

---

## DTO & Entity Patterns

### 命名规范

| 类型       | 命名            | 示例                                 |
| ---------- | --------------- | ------------------------------------ |
| 请求 DTO   | `XxxReq`        | `CreateUserReq`, `UserPageReq`       |
| 响应 DTO   | `XxxResp`       | `UserDetailResp`, `UserPageItemResp` |
| 视图对象   | `XxxVO`         | `UserRoleVO`                         |
| 数据库实体 | `Xxx`（无后缀） | `User`, `AbstractRole`               |
| Feign DTO  | `XxxDTO`        | `PermissionCheckDTO`                 |

### Record 使用

- **不可变 DTO**：使用 Java 21 `record`
- **实体类**：不使用 `record`（MyBatis-Flex 需无参构造）

### 实体审计字段

```java
private Long createdBy;
private Long updatedBy;
private Long deletedBy;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private LocalDateTime deletedAt;
```

---

## Service Layer Patterns

### 调用方向规范

| 规则               | 说明                                      |
| ------------------ | ----------------------------------------- |
| 禁止跳层调用       | Controller 不得直接调用 Mapper            |
| 禁止横向调用       | 同层 Service 禁止互相调用                 |
| DomainService 复用 | 新增功能必须检查 DomainService 可复用方法 |

### DomainService 复用清单

| 场景         | 已有方法                                                    | 禁止行为                 |
| ------------ | ----------------------------------------------------------- | ------------------------ |
| 用户角色解析 | `UserRoleDomainService.resolveEffectiveRoles()`             | 调度层直接查 `user_role` |
| 资源层级遍历 | `ResourceEntityDomainService.getAncestorIds()`              | 调度层写递归             |
| 权限级联删除 | `RolePermissionDomainService.revokePermissionWithCascade()` | 循环删除子权限           |
| 类型解析     | `TypeResolutionService.resolveTypeValue()`                  | 直接查 `type_definition` |

### N+1 查询禁止

**禁止模式**：

```java
// ❌ 错误
for (Long id : ids) {
    mapper.selectOneById(id);
}
```

**正确模式**：

```java
// ✅ 批量查询
List<Xxx> list = mapper.selectListByQuery(
    QueryWrapper.create().where(XXX.ID.in(ids))
);
Map<Long, Xxx> map = list.stream()
    .collect(Collectors.toMap(Xxx::getId, x -> x));
```

---

## Database Patterns

### 表设计规范

```sql
id          BIGINT PRIMARY KEY,              -- Snowflake ID
tenant_id   BIGINT NOT NULL,                 -- 租户隔离
delete_flag BIGINT NOT NULL DEFAULT 0,       -- 软删除
created_by  BIGINT,
updated_by  BIGINT,
deleted_by  BIGINT,
created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
deleted_at  TIMESTAMPTZ
```

### SQL 书写规范

- **所有自定义 SQL 必须写在 XML 文件**
- 禁止 `@Select`/`@Update`/`@Insert`/`@Delete` 注解
- XML 位置：`src/main/resources/mapper/XxxMapper.xml`

### 命名规范

| 对象     | 规范                 | 示例                          |
| -------- | -------------------- | ----------------------------- |
| 表名     | 小写+下划线，单数    | `abstract_user`, `biz_domain` |
| 字段名   | 小写+下划线          | `created_at`, `delete_flag`   |
| 索引     | `idx_{table}_{col}`  | `idx_user_tenant_id`          |
| 唯一索引 | `uniq_{table}_{col}` | `uniq_user_name_tenant`       |

---

## Caching Patterns

### 两级缓存

```text
L1: Caffeine（本地缓存，60秒 TTL）
L2: Redis（分布式缓存，5分钟 TTL）
```

### Cache Aside 模式

```text
读：L1 → L2 → DB，逐级回填
写：DB → 删除 L2 → 删除 L1
```

### Key 命名

```
{serviceCode}:{bizModule}:{dataType}:{identifier}
```

示例：

- `perm:role:detail:1234`
- `admin:user:detail:10086`

---

## Feign Patterns

### Header 透传

| Header           | 说明           |
| ---------------- | -------------- |
| `Authorization`  | Bearer Token   |
| `X-Trace-Id`     | 链路追踪       |
| `X-Tenant-Id`    | 租户 ID        |
| `X-Service-Code` | 调用方服务标识 |

### 超时配置

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 3000
        readTimeout: 5000
      permission-center:
        readTimeout: 10000
```

---

## Forbidden Patterns

### 禁止引入的依赖

| 依赖       | 替代方案                                                   |
| ---------- | ---------------------------------------------------------- |
| `Lombok`   | 仅允许 `@Getter` / `@Setter`；不可变 DTO 用 Java 21 Record |
| `Hutool`   | Apache Commons / Guava / Jackson                           |
| `FastJSON` | Jackson                                                    |

### 禁止的日期类型

| 禁止                 | 使用            |
| -------------------- | --------------- |
| `java.util.Date`     | `LocalDateTime` |
| `java.sql.Timestamp` | `LocalDateTime` |
| `java.sql.Date`      | `LocalDate`     |

### 其他禁止项

- 禁止 `System.out.println`
- 禁止 `catch (Exception e) {}` 静默吞异常
- 禁止缓存 null 值超过 30 秒
- 禁止事务内发起 Feign/MQ 调用
- 禁止物理外键

---

## Testing Patterns

- 测试文件位置：`src/test/java/...`
- 命名：`XxxTest.java` 或 `XxxServiceImplTest.java`
- 集成测试：`XxxIntegrationTest.java`

---

## Workflows

### 新增功能流程

1. 确认 API 契约（路径、请求体、响应体）
2. 创建 DTO（`XxxReq` / `XxxResp`）
3. 检查 DomainService 是否有可复用方法
4. 实现 Service 层（调度层编排 + DomainService 复用）
5. 实现 Controller（入参校验 + 响应包装）
6. 编写单元测试

### 添加新实体流程

1. 创建 Entity（含审计字段）
2. 创建 Mapper 接口
3. 创建 Mapper XML（复杂 SQL）
4. 创建 DomainService（原子操作 + 缓存）
5. 创建调度层 Service（编排）

---

## Reference Documents

| 主题     | 文档                                     |
| -------- | ---------------------------------------- |
| 工程规范 | `plan/project-rules.md`                  |
| 整体架构 | `plan/architecture.md`                   |
| API 契约 | `plan/permission-center/api-contract.md` |
| 核心流程 | `plan/permission-center/core-flows.md`   |
| 表结构   | `plan/schema/*.sql`                      |

---

_Generated by skill-create from git history analysis_
