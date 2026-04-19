# Example 演示服务 - 详细设计文档

本文档定义 example-service 的模块、演示场景、SDK 设计及接口清单。作为第三方业务系统对接权限中心的**参考实现**。

---

## 1. 概述

### 1.1 定位

example-service 是一个**独立微服务**，模拟真实业务系统对接权限中心的完整流程。包含前后端，供开发者参考和学习。

### 1.2 核心演示目标

1. 业务服务如何**注册接口**到权限中心
2. 网关如何**拦截接口**实现接口级鉴权
3. 前端如何实现**动态菜单**和**按钮权限**
4. 后端如何实现**数据权限过滤**
5. 如何使用**权限条件**（时间/IP 限制）
6. 如何**查询用户权限**和**来源追溯**
7. SDK Starter 的集成方式

### 1.3 技术选型

| 项目     | 选型                   |
| -------- | ---------------------- |
| 后端     | Spring Boot 3 (WebMVC) |
| 前端     | Vue 3 + Element Plus   |
| 数据库   | PostgreSQL（独立实例） |
| 缓存     | Redis                  |
| RPC      | OpenFeign              |
| 注册发现 | Nacos                  |

---

## 2. 演示业务模型

### 2.1 业务场景

模拟一个**企业 BI 平台**，包含以下核心实体：

| 实体     | 说明                                                           |
| -------- | -------------------------------------------------------------- |
| 数据源   | 数据库连接/API 端点配置（名称、类型、连接信息、所属项目组）    |
| 报表     | 报表定义（名称、描述、查询 SQL/配置、所属项目组），可查看/分享 |
| 数据任务 | ETL/数据处理任务（名称、Cron、执行脚本、状态、所属项目组）     |

选择企业 BI 平台是因为它天然适合 toB 场景，且能完整演示：

- **数据权限**：按项目组/部门控制报表和数据源的可见范围
- **操作权限**：创建/编辑/删除/发布/分享报表，执行/暂停数据任务
- **条件权限**：限时报表分享、IP 限制等

### 2.2 数据库表

#### 2.2.1 demo_datasource（数据源）

| 字段       | 类型         | 说明                                  |
| ---------- | ------------ | ------------------------------------- |
| id         | BIGSERIAL    | 主键                                  |
| tenant_id  | BIGINT       | 租户ID                                |
| name       | VARCHAR(128) | 数据源名称                            |
| ds_type    | VARCHAR(32)  | 类型（MYSQL/POSTGRESQL/API/CSV）      |
| config     | JSONB        | 连接配置（加密存储，host/port/db 等） |
| project_id | BIGINT       | 所属项目组ID（用于数据权限演示）      |
| status     | SMALLINT     | 状态（0=不可用/1=可用）               |
| created_by | BIGINT       | 创建人                                |
| created_at | TIMESTAMPTZ  | 创建时间                              |

#### 2.2.2 demo_report（报表）

| 字段          | 类型         | 说明                             |
| ------------- | ------------ | -------------------------------- |
| id            | BIGSERIAL    | 主键                             |
| tenant_id     | BIGINT       | 租户ID                           |
| name          | VARCHAR(128) | 报表名称                         |
| description   | VARCHAR(512) | 报表描述                         |
| datasource_id | BIGINT       | 关联数据源                       |
| query_config  | JSONB        | 查询配置（SQL/聚合条件等）       |
| project_id    | BIGINT       | 所属项目组ID（用于数据权限演示） |
| status        | SMALLINT     | 状态（0=草稿/1=已发布/2=已归档） |
| shared        | BOOLEAN      | 是否已分享                       |
| created_by    | BIGINT       | 创建人                           |
| created_at    | TIMESTAMPTZ  | 创建时间                         |

#### 2.2.3 demo_task（数据任务）

| 字段            | 类型         | 说明                             |
| --------------- | ------------ | -------------------------------- |
| id              | BIGSERIAL    | 主键                             |
| tenant_id       | BIGINT       | 租户ID                           |
| name            | VARCHAR(128) | 任务名称                         |
| task_type       | VARCHAR(32)  | 任务类型（ETL/SYNC/AGGREGATE）   |
| datasource_id   | BIGINT       | 关联数据源                       |
| task_config     | JSONB        | 任务配置（脚本/步骤定义）        |
| cron_expression | VARCHAR(128) | Cron 表达式（定时执行）          |
| project_id      | BIGINT       | 所属项目组ID（用于数据权限演示） |
| status          | SMALLINT     | 状态（0=停用/1=启用/2=运行中）   |
| last_run_at     | TIMESTAMPTZ  | 最近执行时间                     |
| created_by      | BIGINT       | 创建人                           |
| created_at      | TIMESTAMPTZ  | 创建时间                         |

#### 2.2.4 demo_task_log（任务执行日志）

| 字段        | 类型        | 说明                      |
| ----------- | ----------- | ------------------------- |
| id          | BIGSERIAL   | 主键                      |
| tenant_id   | BIGINT      | 租户ID                    |
| task_id     | BIGINT      | 任务ID                    |
| status      | SMALLINT    | 执行结果（0=失败/1=成功） |
| message     | TEXT        | 执行信息/异常             |
| cost_time   | INT         | 耗时（毫秒）              |
| started_at  | TIMESTAMPTZ | 开始时间                  |
| finished_at | TIMESTAMPTZ | 结束时间                  |

---

## 3. 演示模块与接口

### 3.1 模块一：服务注册演示

**目标**：展示业务服务启动时如何自动注册到权限中心。

**实现方式**：

- 应用启动时，通过 `perm-client-spring-boot-starter` 自动收集所有接口信息
- 调用权限中心 `POST /api/perm/service-config/sync` 全量幂等同步（service_config + 接口列表一次推送）
- 接口信息从 `@RequestMapping` 反射扫描 + `@PermResource` 注解增强自动收集

**配置示例**：

```yaml
perm:
  client:
    enabled: true
    service-code: example-service
    service-name: 演示服务
    auto-register: true # 启动时自动注册
    scan-packages:
      - com.xxx.example.controller
```

**管理端查看**：注册成功后，可在权限中心管理端看到 example-service 的所有接口列表。

### 3.2 模块二：接口权限演示

**目标**：展示网关层接口级鉴权效果。

**接口列表**：

| #   | 接口                                 | 权限要求                  | 说明                       |
| --- | ------------------------------------ | ------------------------- | -------------------------- |
| 1   | POST /api/example/datasources/list   | example:datasource:read   | 数据源列表                 |
| 2   | POST /api/example/datasources/create | example:datasource:create | 创建数据源                 |
| 3   | POST /api/example/datasources/update | example:datasource:update | 更新数据源                 |
| 4   | POST /api/example/datasources/remove | example:datasource:delete | 删除数据源                 |
| 5   | POST /api/example/datasources/test   | example:datasource:update | 测试连接                   |
| 6   | POST /api/example/reports/list       | example:report:read       | 报表列表                   |
| 7   | POST /api/example/reports/create     | example:report:create     | 创建报表                   |
| 8   | POST /api/example/reports/update     | example:report:update     | 更新报表                   |
| 9   | POST /api/example/reports/remove     | example:report:delete     | 删除报表                   |
| 10  | POST /api/example/reports/publish    | example:report:publish    | 发布报表（自定义操作）     |
| 11  | POST /api/example/reports/share      | example:report:share      | 分享报表（自定义操作）     |
| 12  | POST /api/example/reports/export     | example:report:export     | 导出报表（自定义操作）     |
| 13  | POST /api/example/tasks/list         | example:task:read         | 任务列表                   |
| 14  | POST /api/example/tasks/create       | example:task:create       | 创建任务                   |
| 15  | POST /api/example/tasks/update       | example:task:update       | 更新任务                   |
| 16  | POST /api/example/tasks/remove       | example:task:delete       | 删除任务                   |
| 17  | POST /api/example/tasks/execute      | example:task:execute      | 手动执行任务（自定义操作） |
| 18  | POST /api/example/tasks/pause        | example:task:pause        | 暂停任务（自定义操作）     |
| 19  | POST /api/example/tasks/logs/list    | example:task:read         | 任务执行日志               |

**演示场景**：

1. 数据分析师有报表查看权限 → 可访问 /reports/list → 正常返回
2. 数据分析师无数据源管理权限 → 访问 /datasources/create → Gateway 返回 403
3. 运维人员有任务管理权限 → 可执行/暂停任务
4. 新增的未注册接口 → Gateway 返回 403（白名单模式）

**数据任务与 admin-service A-10 任务调度的集成**：

- `demo_task` 定义业务任务元数据（任务名称、类型、脚本配置等）
- 实际的定时调度由 admin-service A-10 任务调度中心统一管理
- 集成方式：创建/更新 `demo_task` 时，同步调用 admin-service 注册/更新调度任务
- admin-service 通过 HTTP 回调 example-service 的执行端点（`POST /api/example/tasks/callback`）触发执行
- example-service 提供任务执行回调接口，接收来自 admin-service 的调度触发
- 这是**外部任务归集**模式的标准演示（对应 A-10 的外部任务集成设计）

| #   | 新增接口                         | 说明                               |
| --- | -------------------------------- | ---------------------------------- |
| 20  | POST /api/example/tasks/callback | 任务执行回调（admin-service 调用） |

**注解方式**（用于代码内权限声明，供 SDK 收集）：

```java
@Tag(name = "报表管理", description = "example:report")
@RestController
@RequestMapping("/api/example/reports")
public class ReportController {

    @Operation(summary = "报表列表")
    @PermResource(code = "example:report:read", name = "报表列表", group = "报表管理", operations = {"READ"})
    @PostMapping("/list")
    public R<PageResult<Report>> list(@RequestBody ReportQuery query) { ... }

    @Operation(summary = "发布报表")
    @PermResource(code = "example:report:publish", name = "发布报表", group = "报表管理", operations = {"PUBLISH"})
    @PostMapping("/publish")
    public R<Void> publish(@RequestBody ReportPublishReq req) { ... }
}
```

### 3.3 模块三：菜单权限演示

**目标**：展示前端如何基于权限实现动态菜单和路由。

**前端实现要点**：

1. 登录后调用 `/api/admin/menus/user-menus` 获取用户有权的菜单树
2. 使用 `router.addRoute()` 动态注册路由
3. 无权限的菜单/页面不在导航栏中展示
4. 手动输入无权路由 URL → 重定向到 403 页面

**演示菜单结构**：

```
BI 平台
├── 数据源管理          (example:datasource:read)
│   ├── [新增] 按钮     (example:datasource:create)
│   ├── [编辑] 按钮     (example:datasource:update)
│   ├── [删除] 按钮     (example:datasource:delete)
│   └── [测试连接] 按钮 (example:datasource:update)
├── 报表中心            (example:report:read)
│   ├── [新增] 按钮     (example:report:create)
│   ├── [编辑] 按钮     (example:report:update)
│   ├── [删除] 按钮     (example:report:delete)
│   ├── [发布] 按钮     (example:report:publish)
│   ├── [分享] 按钮     (example:report:share)
│   └── [导出] 按钮     (example:report:export)
├── 数据任务            (example:task:read)
│   ├── [新增] 按钮     (example:task:create)
│   ├── [编辑] 按钮     (example:task:update)
│   ├── [删除] 按钮     (example:task:delete)
│   ├── [执行] 按钮     (example:task:execute)
│   └── [暂停] 按钮     (example:task:pause)
└── 权限查询            (内置页面)
```

### 3.4 模块四：按钮权限演示

**目标**：展示前端按钮级别权限控制。

**前端实现**：

```vue
<!-- 方式一：v-permission 自定义指令 -->
<el-button v-permission="'example:report:publish'">发布报表</el-button>

<!-- 方式二：v-if + 权限判断函数 -->
<el-button v-if="hasPermission('example:report:share')">分享</el-button>
```

**实现原理**：

- 登录后获取用户所有按钮级权限码列表（permCodes）
- `v-permission` 指令检查 permCodes 中是否包含目标权限码
- 无权限的按钮自动隐藏（或 disabled）

### 3.5 模块五：数据权限演示

**目标**：展示后端如何基于数据权限实现数据过滤。

**演示场景**：

- 报表、数据源、任务都有 `project_id`（项目组）维度
- 用户 A 只能查看"项目组 Alpha"的报表 → 查询时自动追加 `WHERE project_id = 1`
- 用户 B 可查看"项目组 Alpha + Beta" → 追加 `WHERE project_id IN (1, 2)`
- 管理员可查看所有报表 → 不追加过滤条件

**后端实现**：

```java
// 方式一：注解声明数据权限
@DataPermission(type = "PROJECT", column = "project_id")
@PostMapping("/list")
public R<PageResult<Report>> list(@RequestBody ReportQuery query) { ... }

// 方式二：编程式获取数据范围
@PostMapping("/list")
public R<PageResult<Report>> list(@RequestBody ReportQuery query) {
    List<Long> projectIds = permissionClient.getDataScope("PROJECT");
    // projectIds → [1, 2]
    query.setProjectIds(projectIds);
    return reportService.list(query);
}
```

**实现原理**：

1. 权限中心存储数据权限（resource_type=DATA 的 resource_entity）
2. 用户的数据权限范围 = 其所有有效角色上配置的 DATA 类型资源的并集
3. `perm-data-spring-boot-starter`（参考实现）提供 MyBatis 拦截器，自动注入 SQL 过滤条件
4. **无数据权限配置时 → 返回空数据**（安全优先，即"未授权等于无权"）

**接口**：

| #   | 接口                               | 说明                                 |
| --- | ---------------------------------- | ------------------------------------ |
| 1   | POST /api/example/reports/list     | 报表列表（自动按项目组数据权限过滤） |
| 2   | POST /api/example/reports/list-all | 报表列表（不过滤，仅管理员可访问）   |

### 3.6 模块六：权限条件演示

**目标**：展示权限条件（时间范围/IP 白名单）的实际效果。

**演示场景**：

1. **限时分享**：用户 C 的报表分享权限配置了 DATE_RANGE 条件（2026-01-01 ~ 2026-06-30）
   - 有效期内 → 可分享报表
   - 过期后 → 鉴权返回 CONDITION_NOT_MET
2. **工作时间**：用户 D 的任务执行权限配置了 TIME_RANGE 条件（09:00 ~ 18:00）
   - 工作时间内 → 可手动执行任务
   - 下班后 → 无法执行
3. **IP 限制**：用户 E 的数据源管理权限配置了 IP_WHITELIST（仅公司 IP 段）
   - 公司内网 → 正常管理
   - 外网 → 拒绝（保护敏感数据源连接信息）

**接口**：

| #   | 接口                            | 说明                           |
| --- | ------------------------------- | ------------------------------ |
| 1   | POST /api/example/reports/share | 分享报表（可能受时间条件限制） |
| 2   | POST /api/example/tasks/execute | 执行任务（可能受工时条件限制） |

### 3.7 模块七：权限查询演示

**目标**：展示如何查询用户权限视图、来源追溯。

**接口**（代理调用权限中心）：

| #   | 接口                                  | 说明                             |
| --- | ------------------------------------- | -------------------------------- |
| 1   | POST /api/example/perm/my-permissions | 查询当前用户所有有效权限         |
| 2   | POST /api/example/perm/check          | 单次权限检查                     |
| 3   | POST /api/example/perm/check-batch    | 批量权限检查                     |
| 4   | POST /api/example/perm/trace          | 权限来源追溯（展示权限继承链路） |

**前端展示页面**：

- 「我的权限」页面：以树形/表格方式展示当前用户的全部有效权限
- 每条权限标注来源（来自哪个角色/分组）
- 支持搜索和按资源类型筛选

### 3.8 模块八：定时任务权限演示

**目标**：展示定时任务中如何执行带权限上下文的操作。

**演示场景**：

- 数据任务「每日数据汇总」需要读取所有数据源并生成汇总报表
- 任务以「系统服务账号」身份执行，该账号在权限中心有全部数据权限
- 演示如何在无 HTTP 上下文时设置权限上下文

**实现方式**：

```java
@Scheduled(cron = "0 0 2 * * ?")
public void dailyDataSummary() {
    // 使用系统服务账号的权限上下文
    PermissionContext.runAs(systemUserId, tenantId, () -> {
        List<Datasource> allDs = datasourceService.listAll();
        reportService.generateDailyReport(allDs);
    });
}
```

---

## 4. SDK Starter 设计

### 4.0 模块结构

3 个独立 Starter + 1 个公共模块，按需引入：

```
perm-sdk/
├── perm-common/                          # 公共模型、异常、工具
│   ├── PermResult.java                   # 鉴权结果对象
│   ├── PermissionContext.java            # 权限上下文（ThreadLocal）
│   ├── PermException.java                # 统一异常
│   └── model/                            # DTO: UserInfo, RoleInfo, ConditionRule...
├── perm-client-spring-boot-starter/      # 业务服务引用
├── perm-data-spring-boot-starter/        # 数据权限参考实现（仅 example 使用）
└── perm-gateway-spring-boot-starter/     # 网关引用
```

**依赖关系**：perm-client / perm-data / perm-gateway 均依赖 perm-common。

---

### 4.1 perm-client-spring-boot-starter

**职责**：权限中心客户端，提供接口注册、鉴权查询、身份上下文、服务间调用等基础能力。

#### 4.1.1 自动配置

```yaml
perm:
  client:
    enabled: true
    service-code: example-service
    service-name: 演示服务
    auto-register: true # 启动时自动注册接口
    fail-on-error: false # 注册失败时是否阻断启动（默认 false=仅告警）
    scan-packages:
      - com.xxx.example.controller
    feign:
      url: lb://permission-center
      connect-timeout: 3000 # 连接超时 3s
      read-timeout: 5000 # 读超时 5s
      retry: 1 # 失败重试 1 次
```

#### 4.1.2 组件清单

| 组件                      | 说明                                                                                                     |
| ------------------------- | -------------------------------------------------------------------------------------------------------- |
| **PermissionClient**      | 鉴权查询：check / checkBatch / getDataScope。无本地缓存，实时调用权限中心                                |
| **PermissionContext**     | ThreadLocal 实现，存储当前用户/租户/Token。Filter 入口从请求头（X-User-Id 等）提取并写入                 |
| **InterfaceCollector**    | 反射扫描 `scan-packages` 下所有 `@RequestMapping`，提取 method+path；有 `@PermResource` 时提取增强元数据 |
| **AutoRegisterRunner**    | ApplicationRunner，启动时调用 `POST /api/perm/service-config/sync` 全量幂等同步                          |
| **@PermResource**         | 接口权限声明注解（见下方）                                                                               |
| **PermCenterFeignClient** | 权限中心 Feign 接口封装                                                                                  |
| **PermContextFilter**     | Servlet Filter（order=Ordered.HIGHEST_PRECEDENCE+10），从请求头提取上下文写入 ThreadLocal                |
| **FeignTokenInterceptor** | Feign RequestInterceptor，服务间调用自动注入身份（见 4.1.6）                                             |

#### 4.1.3 @PermResource 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermResource {
    /** 权限标识码（全局唯一），如 "report:list" */
    String code();
    /** 名称，如 "报表列表" */
    String name() default "";
    /** 分组，如 "报表管理"（用于注册到权限中心时的资源树层级） */
    String group() default "";
    /** 资源类型：MENU / BUTTON / API，默认 API */
    String resourceType() default "API";
    /** 操作码列表，如 {"READ","EXPORT"}，默认 {} 表示仅检查接口访问权限 */
    String[] operations() default {};
}
```

**收集策略**：反射扫描 + @PermResource 增强

- 所有 `@RequestMapping` 方法均被收集（method + path）
- 有 `@PermResource` 的方法额外提取 code/name/group/resourceType/operations
- 无 `@PermResource` 的方法仅注册 API 映射，不创建权限资源（可在管理后台补配）

#### 4.1.4 接口同步模式

**全量幂等同步**：每次启动推送全量接口列表到 `POST /api/perm/service-config/sync`，权限中心 upsert 处理。

```json
// POST /api/perm/service-config/sync 请求体
{
  "service_code": "example-service",
  "service_name": "演示服务",
  "interfaces": [
    {
      "http_method": "GET",
      "path_pattern": "/api/example/reports",
      "perm_code": "report:list",
      "perm_name": "报表列表",
      "perm_group": "报表管理",
      "resource_type": "API",
      "operations": ["READ"]
    }
  ]
}
```

**注册失败处理**：

- `fail-on-error: false`（默认）：打印 WARN 日志，服务正常启动。依赖网关已有的快照缓存仍可运行
- `fail-on-error: true`：抛出异常阻断启动，强制修复后重启

#### 4.1.5 PermissionClient API

```java
public class PermissionClient {
    /**
     * 单次鉴权 → POST /api/perm/auth/check
     * @return PermResult(allowed, denyReason, conditions)
     */
    PermResult check(String resourceCode, String operationCode);

    /**
     * 批量鉴权 → POST /api/perm/auth/batch-check
     */
    List<PermResult> checkBatch(List<PermCheckRequest> requests);

    /**
     * 获取数据权限范围 → POST /api/perm/permission-view/user-permissions
     * @param dataType 数据权限类型（如 "PROJECT"）
     * @return 允许的数据ID列表
     */
    List<Long> getDataScope(String dataType);
}
```

**PermResult 结构**：

```java
public record PermResult(
    boolean allowed,
    String denyReason,         // 拒绝原因（allowed=false 时有值）
    List<ConditionRule> conditions  // 附加条件（时间范围、IP 等，需调用方进一步评估）
) {}
```

**Feign 容错**：

- 连接超时 3s / 读超时 5s / 失败重试 1 次
- check 类接口降级返回 `PermResult(allowed=false, denyReason="权限中心不可用")`（安全优先）
- getDataScope 类接口降级返回空列表

#### 4.1.6 服务间调用身份传递

**混合模式**：根据上下文自动选择身份：

```java
public class FeignTokenInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        // 1. 优先透传当前用户 Token（HTTP 请求上下文中有值时）
        String token = PermissionContext.getCurrentToken();
        if (token != null) {
            template.header("Authorization", "Bearer " + token);
            return;
        }
        // 2. 无用户上下文时（定时任务/MQ 消费），使用 client_credentials Token
        String serviceToken = serviceTokenProvider.getToken();
        template.header("Authorization", "Bearer " + serviceToken);
    }
}
```

- **有用户上下文**（HTTP 请求）：透传用户 Token，下游服务按用户权限鉴权
- **无用户上下文**（定时任务/MQ 消费/PermissionContext.runAs）：自动使用 `internal-service` 的 client_credentials Token

---

### 4.2 perm-data-spring-boot-starter（参考实现，非官方 SDK）

**定位**：作为 example-service 内的**参考实现**，展示数据权限自动过滤的一种可行方案。权限中心不提供官方 perm-data SDK，各业务系统根据自身架构自行适配使用权限信息。

#### 4.2.1 自动配置

```yaml
perm:
  data:
    enabled: true
    default-mode: FILTER # FILTER=自动拦截 / MANUAL=手动获取
```

#### 4.2.2 组件清单

| 组件                            | 说明                                                                    |
| ------------------------------- | ----------------------------------------------------------------------- |
| **@DataPermission**             | 单维度数据权限注解，标记在 Mapper 方法 **或** Service/Controller 方法上 |
| **@DataPermissions**            | 包装注解，支持多维度（多个 @DataPermission）                            |
| **DataPermissionInterceptor**   | MyBatis Interceptor，拦截 SQL 并追加 WHERE 条件                         |
| **DataScopeProvider**           | 数据范围提供者，从权限中心获取用户数据范围并在请求级 ThreadLocal 缓存   |
| **DataPermissionContextHolder** | ThreadLocal 持有本次请求的 @DataPermission 配置，传递到 MyBatis 层      |

#### 4.2.3 @DataPermission 注解

```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DataPermissions.class)
public @interface DataPermission {
    /** 数据权限类型（对应权限中心 resource_type），如 "PROJECT" */
    String type();
    /** 数据库字段名，如 "project_id" */
    String column();
    /** 运算符，默认 IN。支持 IN / EQ / LIKE */
    String operator() default "IN";
}

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermissions {
    DataPermission[] value();
}
```

**使用示例**：

```java
// 单维度 - 标在 Service 方法上
@DataPermission(type = "PROJECT", column = "project_id")
public List<Report> listReports(ReportQuery query) {
    return reportMapper.selectList(query);
}

// 多维度 - 标在 Mapper 方法上
@DataPermission(type = "PROJECT", column = "project_id")
@DataPermission(type = "DEPT", column = "dept_id")
List<Report> selectList(@Param("query") ReportQuery query);
```

**触发层级**：

- 支持标记在 **Mapper 方法** 或 **Service/Controller 方法**（通过 AOP + ThreadLocal 传递）
- **禁止嵌套**：同一调用链中 Service 和 Mapper 不可同时标注 `@DataPermission`，启动时扫描检测到嵌套冲突则抛出异常
- Service/Controller 层的注解通过 AOP 拦截器写入 `DataPermissionContextHolder`，MyBatis Interceptor 从 ThreadLocal 读取

#### 4.2.4 拦截原理

1. **AOP 阶段**（Service/Controller 层）：拦截器捕获 `@DataPermission` 注解，写入 `DataPermissionContextHolder`（ThreadLocal）
2. **MyBatis Interceptor 阶段**：
   a. 检查当前 Mapper 方法是否有 `@DataPermission`（直接标记）或 ThreadLocal 中是否有上层传递的配置
   b. 从 `DataScopeProvider` 获取当前用户在指定 type 下的数据范围值列表
   c. 使用 **JSqlParser** 解析原始 SQL AST
   d. 追加 WHERE 条件：`AND {column} IN (v1, v2, v3)`
   e. 多维度时追加多个 AND 条件
   f. 无数据权限配置时 → 追加 `AND 1=0`（返回空结果，安全优先）
3. **请求结束**：清理 ThreadLocal

**限制说明**（参考实现定位）：

- 不支持表别名（不处理 JOIN 查询中的别名歧义），仅适用于简单 SQL 场景
- SQL 改写使用 IN 子句，大数据量场景业务系统应自行优化为 EXISTS 或其他方式
- 多维度条件之间为 AND 关系

#### 4.2.5 DataScopeProvider 缓存策略

**请求级缓存**：同一 HTTP 请求内，相同 userId + type 的数据范围仅查询权限中心一次，结果缓存在 ThreadLocal 中，请求结束时清理。

```java
public class DataScopeProvider {
    // ThreadLocal<Map<String(type), List<Long>(dataIds)>>
    private static final ThreadLocal<Map<String, List<Long>>> SCOPE_CACHE = ...;

    public List<Long> getDataScope(String type) {
        return SCOPE_CACHE.get()
            .computeIfAbsent(type, t -> permissionClient.getDataScope(t));
    }
}
```

---

### 4.3 perm-gateway-spring-boot-starter

**职责**：网关鉴权插件，封装接口权限快照管理、L1+L2 缓存、条件评估等逻辑。已在 Gateway 设计中引用。

#### 4.3.1 自动配置

```yaml
perm:
  gateway:
    enabled: true
    snapshot:
      poll-interval: 30s # 版本轮询间隔
      version-url: lb://permission-center # 权限中心地址
    cache:
      l1-max-size: 100 # Caffeine L1 最大条目（服务数量级别）
      l1-ttl: 30s # Caffeine L1 过期时间
      l2-ttl: 5m # Redis L2 过期时间
      l2-key-prefix: "gateway:perm:" # Redis key 前缀
    unregistered-policy: DENY # 未注册接口策略：DENY / ALLOW（仅开发环境）
```

#### 4.3.2 组件清单

| 组件                         | 说明                                                   |
| ---------------------------- | ------------------------------------------------------ |
| **PermissionFilter**         | GlobalFilter（order=-60），从快照判定接口权限          |
| **PermissionCacheService**   | L1 Caffeine + L2 Redis 二级缓存管理                    |
| **PermissionSnapshotClient** | 权限中心 Feign/WebClient 接口：拉取快照、查询版本      |
| **SnapshotRefreshScheduler** | 定时任务：每 30s 轮询版本号，变更时拉取全量快照        |
| **ConditionEvaluator**       | 条件权限评估器：本地评估简单条件，复杂条件回调权限中心 |
| **GatewayPermProperties**    | 配置属性类                                             |

#### 4.3.3 权限快照数据结构

**按服务拆分**：每个 service_code 一个独立快照，减小单次传输量。

```typescript
// 快照顶层结构
interface PermissionSnapshot {
  serviceCode: string; // 服务标识
  version: number; // 版本号
  generatedAt: string; // 快照生成时间
  rules: Map<string, InterfaceRule>; // key = "GET:/api/example/reports"
}

// 接口规则
interface InterfaceRule {
  httpMethod: string;
  pathPattern: string;
  // 按角色分组的权限规则
  roleRules: Map<string, RoleRuleDetail>; // key = roleId
}

// 角色维度的规则详情
interface RoleRuleDetail {
  roleId: string;
  roleCode: string;
  operations: string[]; // 允许的操作码 ["READ", "EXPORT"]
  conditions: ConditionRule[]; // 附加条件（可为空）
}

// 条件规则
interface ConditionRule {
  conditionId: string;
  logic: "AND" | "OR";
  items: ConditionItem[];
}

interface ConditionItem {
  type:
    | "DATE_RANGE"
    | "TIME_RANGE"
    | "IP_WHITELIST"
    | "IP_BLACKLIST"
    | "CUSTOM";
  params: Map<string, string>; // 如 { "start": "2025-01-01", "end": "2025-12-31" }
}
```

#### 4.3.4 快照更新机制

**定时轮询**（每 30s）：

```
SnapshotRefreshScheduler
  │
  ├─ 1. POST /api/perm/permission-version/query → 获取各服务版本号
  │
  ├─ 2. 比对本地版本号，版本不一致的服务 →
  │     POST /api/perm/auth/interface-snapshot?service_code=xxx → 拉取全量快照
  │
  ├─ 3. 更新 L1 Caffeine 缓存（整体替换）
  │
  └─ 4. 写入 L2 Redis 缓存（key: gateway:perm:snapshot:{serviceCode}）
```

**启动预热**：

1. 优先从 Redis L2 加载快照（快速恢复，毫秒级）
2. L2 有数据则立即可用，同时异步触发全量拉取刷新
3. L2 无数据则同步拉取权限中心快照，阻塞直到就绪
4. 拉取失败 + L2 无数据 = 所有非白名单请求返回 503（fail-close）

#### 4.3.5 鉴权流程

```
请求到达 PermissionFilter
  │
  ├─ 1. 从 Exchange 获取 method + path + tenantId + userId
  │
  ├─ 2. 从 Redis 实时查询用户角色列表
  │     Key: perm:user:roles:{tenantId}:{userId} (SET 类型)
  │
  ├─ 3. 在快照中查找匹配的 InterfaceRule（method:path）
  │     ├─ 未命中 → DENY（白名单模式，unregistered-policy=DENY）
  │     └─ 命中 → 继续
  │
  ├─ 4. 遍历用户角色，查找 roleRules 中是否存在匹配的角色
  │     ├─ 无匹配角色 → DENY（无权限）
  │     └─ 有匹配角色 → 获取 RoleRuleDetail
  │
  ├─ 5. 检查条件（conditions）
  │     ├─ 无条件 → ALLOW
  │     ├─ 简单条件（DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST）
  │     │   → ConditionEvaluator 本地评估
  │     └─ 复杂条件（CUSTOM 或未识别类型）
  │         → 回调权限中心 POST /api/perm/auth/interface-decision
  │
  └─ 6. 返回 ALLOW / DENY
```

#### 4.3.6 条件权限评估

**ConditionEvaluator 本地评估的条件类型**：

| 类型         | 评估逻辑                        |
| ------------ | ------------------------------- |
| DATE_RANGE   | 当前日期是否在 start~end 范围内 |
| TIME_RANGE   | 当前时间是否在 start~end 范围内 |
| IP_WHITELIST | 请求 IP 是否在白名单列表中      |
| IP_BLACKLIST | 请求 IP 是否不在黑名单列表中    |

其他条件类型（CUSTOM 或未来扩展的类型）→ 回退调用 `POST /api/perm/auth/interface-decision`，由权限中心完整评估。

#### 4.3.7 序列化

快照在 Redis L2 中以 **JSON** 格式存储（Jackson 序列化），便于调试和运维排查。

---

## 5. 前端演示项目结构

```
example-ui/
├── src/
│   ├── api/                    # 接口调用
│   │   ├── datasource.ts
│   │   ├── report.ts
│   │   ├── task.ts
│   │   └── permission.ts
│   ├── components/
│   │   └── PermButton.vue      # 权限按钮组件
│   ├── directive/
│   │   └── permission.ts       # v-permission 指令
│   ├── hooks/
│   │   └── usePermission.ts    # 权限判断 composable
│   ├── router/
│   │   ├── index.ts            # 路由入口
│   │   └── dynamicRoutes.ts    # 动态路由生成逻辑
│   ├── store/
│   │   ├── user.ts             # 用户状态（含权限码列表）
│   │   └── permission.ts       # 路由权限状态
│   ├── views/
│   │   ├── datasource/         # 数据源管理
│   │   ├── report/             # 报表中心
│   │   ├── task/               # 数据任务
│   │   ├── permission/         # 权限查询演示页
│   │   └── 403.vue             # 无权限页面
│   └── utils/
│       └── request.ts          # Axios 封装（含 Token 注入）
├── package.json
└── vite.config.ts
```

### 5.1 关键前端逻辑

**动态路由生成**：

```typescript
// dynamicRoutes.ts
export async function generateRoutes() {
  // 1. 从后端获取用户有权菜单
  const menus = await getUserMenus();
  // 2. 将菜单数据转换为 Vue Router 路由
  const routes = menus.map((menu) => ({
    path: menu.path,
    name: menu.name,
    component: loadComponent(menu.component), // 动态 import
    meta: {
      title: menu.name,
      icon: menu.icon,
      permCode: menu.permCode,
      keepAlive: menu.isCache,
    },
    children: menu.children ? generateChildren(menu.children) : [],
  }));
  // 3. 动态添加路由
  routes.forEach((route) => router.addRoute(route));
  return routes;
}
```

**权限指令**：

```typescript
// permission.ts
const vPermission: Directive = {
  mounted(el, binding) {
    const permCode = binding.value;
    const userPerms = useUserStore().permissions;
    if (!userPerms.includes(permCode)) {
      el.parentNode?.removeChild(el);
    }
  },
};
```

---

## 6. 演示数据初始化

### 6.1 权限中心初始化数据

| 数据                     | 内容                                              |
| ------------------------ | ------------------------------------------------- |
| service_config           | example-service 注册                              |
| resource_type            | 预置 MENU/BUTTON/API/DATA                         |
| resource_entity (MENU)   | 数据源管理、报表中心、数据任务                    |
| resource_entity (BUTTON) | 各页面按钮权限                                    |
| resource_entity (DATA)   | 项目组数据权限（Alpha/Beta/Gamma）                |
| operation_permission     | CRUD + PUBLISH + SHARE + EXPORT + EXECUTE + PAUSE |
| abstract_role            | 管理员/数据分析师/运维人员/报表查看者             |
| role_resource_permission | 各角色的权限配置                                  |
| permission_condition     | 时间范围/IP白名单条件示例                         |

### 6.2 管理服务初始化数据

| 数据     | 内容                                   |
| -------- | -------------------------------------- |
| sys_user | admin/analyst/operator/viewer 演示账号 |
| sys_org  | 总公司/数据部/运维部 组织结构          |
| sys_menu | BI 平台菜单树                          |

### 6.3 Example 服务初始化数据

| 数据            | 内容                                  |
| --------------- | ------------------------------------- |
| demo_datasource | 3~5 条示例数据源（不同类型/项目组）   |
| demo_report     | 10~15 条示例报表（不同项目组/状态）   |
| demo_task       | 5~8 条示例数据任务（不同类型/项目组） |
| demo_task_log   | 20~30 条任务执行日志                  |

---

## 7. 模块汇总

| #   | 模块         | 后端接口数 | 前端页面 | 说明                                    |
| --- | ------------ | ---------- | -------- | --------------------------------------- |
| 1   | 服务注册演示 | 0（自动）  | -        | 启动自动注册                            |
| 2   | 接口权限演示 | 20         | 3 页     | 数据源/报表/任务 CRUD + 操作 + 任务回调 |
| 3   | 菜单权限演示 | 0          | 路由层   | 动态路由                                |
| 4   | 按钮权限演示 | 0          | 组件层   | v-permission 指令                       |
| 5   | 数据权限演示 | 2          | 1 页     | 按项目组过滤，无权限返回空              |
| 6   | 权限条件演示 | 0          | -        | 复用接口权限演示的接口                  |
| 7   | 权限查询演示 | 4          | 1 页     | 我的权限/权限检查/追溯                  |
| 8   | 定时任务演示 | 0（定时）  | -        | 系统账号权限上下文                      |

**后端接口：26 个，前端页面：5 个，SDK Starter：3 个（含 1 个参考实现），数据库表：4 个**
