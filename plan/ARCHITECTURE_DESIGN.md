# 微服务架构设计

本文档定义项目整体微服务架构、各服务职责、模块划分及服务间交互方式。与 `DESIGN.md`（权限中心）配套使用。

---

## 1. 整体架构

### 1.1 服务清单

| 服务                          | 技术栈                         | 数据库                 | 端口(建议) | 说明                                                         |
| ----------------------------- | ------------------------------ | ---------------------- | ---------- | ------------------------------------------------------------ |
| gateway                       | Spring Cloud Gateway (WebFlux) | 无（纯网关）           | 8080       | 流量入口：路由转发、Token 校验、接口鉴权                     |
| admin-service（管理服务）     | Spring Boot 3 (WebMVC)         | PostgreSQL（独立实例） | 9100       | 用户、组织、菜单、字典、通知、文件、审计、任务调度、系统设置 |
| permission-center（权限中心） | Spring Boot 3 (WebMVC)         | PostgreSQL（独立实例） | 9200       | 通用权限管理与鉴权引擎（已完成设计）                         |
| example-service（演示服务）   | Spring Boot 3 (WebMVC)         | PostgreSQL（独立实例） | 9300       | 权限中心对接演示 + 权限管控功能展示                          |

### 1.2 基础设施

| 组件     | 选型                               | 说明                                 |
| -------- | ---------------------------------- | ------------------------------------ |
| 注册中心 | Nacos                              | 服务发现 + 配置管理一体              |
| 配置中心 | Nacos                              | 与注册中心复用                       |
| 消息队列 | RocketMQ                           | 用户同步、权限变更通知等异步事件     |
| 缓存     | Redis                              | L2 缓存、Sa-Token 会话存储、分布式锁 |
| 对象存储 | S3 兼容（MinIO / 阿里云 OSS）      | 文件上传下载                         |
| 任务调度 | Spring Scheduler                   | 轻量定时任务（兼演示权限控制）       |
| 认证框架 | Sa-Token + OAuth2                  | 多种授权模式并存                     |
| 链路追踪 | Micrometer Tracing + OpenTelemetry | 分布式 traceId 生成与传递            |
| 前端框架 | Vue 3 + Element Plus               | 管理端 + example 演示端              |

### 1.3 架构拓扑图

```
                          ┌──────────────────┐
                          │   前端应用 (SPA)  │
                          └────────┬─────────┘
                                   │ HTTP
                          ┌────────▼─────────┐
                          │     Gateway      │
                          │  (Spring Cloud)  │
                          │                  │
                          │ · Token 校验     │
                          │ · 接口鉴权       │
                          │ · 路由转发       │
                          └──┬─────┬─────┬───┘
                             │     │     │
                ┌────────────┘     │     └────────────┐
                │                  │                   │
       ┌────────▼──────┐  ┌───────▼───────┐  ┌───────▼────────┐
       │ admin-service  │  │  permission   │  │   example      │
       │                │  │   -center     │  │   -service     │
       │ · 用户管理     │  │               │  │                │
       │ · 组织管理     │  │ · 权限管理    │  │ · 对接演示     │
       │ · 菜单管理     │  │ · 鉴权引擎    │  │ · 权限展示     │
       │ · 认证/OAuth2  │  │ · 版本管理    │  │                │
       │ · 字典/通知    │  │               │  │                │
       │ · 文件/OSS     │  │               │  │                │
       │ · 审计日志     │  │               │  │                │
       │ · 任务调度     │  │               │  │                │
       └───────┬────────┘  └───────┬───────┘  └───────┬────────┘
               │                   │                   │
               ▼                   ▼                   ▼
          PostgreSQL          PostgreSQL          PostgreSQL
          (admin_db)         (perm_db)          (example_db)
```

### 1.4 服务间交互矩阵

| 调用方            | 被调方            | 协议        | 场景                                                                     |
| ----------------- | ----------------- | ----------- | ------------------------------------------------------------------------ |
| gateway           | admin-service     | HTTP (转发) | 登录请求透传、管理接口转发                                               |
| gateway           | permission-center | OpenFeign   | 拉取服务接口权限快照（按 service_code 维度）、权限版本轮询、条件鉴权回调 |
| gateway           | example-service   | HTTP (转发) | 演示服务接口转发                                                         |
| admin-service     | permission-center | OpenFeign   | 用户同步、角色查询/复用、菜单资源同步、鉴权查询                          |
| admin-service     | permission-center | RocketMQ    | 用户创建/更新/删除事件异步同步                                           |
| example-service   | permission-center | OpenFeign   | 鉴权查询、权限数据查询                                                   |
| permission-center | admin-service     | RocketMQ    | 权限变更通知（可选，如角色变更通知管理端刷新缓存）                       |

---

## 2. Gateway 网关服务

### 2.1 职责边界

- **路由转发**：按配置规则将请求转发到后端服务
- **Token 校验**：解析 Sa-Token 令牌，提取 tenant_id、user_id 等信息注入请求头
- **接口鉴权**：对接权限中心，判断用户是否有权访问当前接口
- **白名单管理**：公开接口（登录、注册、健康检查等）免鉴权
- **请求头增强**：向下游注入 X-Tenant-Id、X-User-Id、X-Request-Id 等标准头

### 2.2 模块划分

| #   | 模块             | 说明                                                                         |
| --- | ---------------- | ---------------------------------------------------------------------------- |
| 1   | 路由配置         | 基于 Nacos 动态路由配置，支持按服务名/路径匹配转发                           |
| 2   | Token 校验过滤器 | 全局 GatewayFilter，Sa-Token 解析令牌，校验有效性和登录状态                  |
| 3   | 接口鉴权过滤器   | 全局 GatewayFilter，对接权限中心判断接口权限，L1+L2 缓存，未注册接口默认拒绝 |
| 4   | 白名单管理       | 可配置的公开接口列表（Nacos 配置动态刷新），匹配的请求跳过鉴权               |
| 5   | 请求头增强       | 注入标准请求头（tenant_id, user_id, request_id），清洗外部伪造头             |
| 6   | 异常处理         | 统一 JSON 错误响应格式，鉴权失败/服务不可用等不同错误码                      |

### 2.3 鉴权流程（与权限中心 §6.5 和 perm-gateway-starter 对齐）

```
请求到达 Gateway
    │
    ├─ 匹配白名单？ → 是 → 跳过鉴权，直接转发
    │
    ├─ 解析 Token → 失败 → 返回 401
    │
    ├─ 提取 tenant_id, user_id
    │
    ├─ 从 Redis 实时查询用户角色列表 (Key: perm:user:roles:{tenantId}:{userId})
    │
    ├─ 从路由元数据提取 serviceCode
    │
    ├─ 查 L1 Caffeine 快照（按 serviceCode）
    │   ├─ 命中 → 匹配接口 + 角色规则
    │   └─ 未命中 → 查 L2 Redis → 命中则写入 L1 并匹配
    │       └─ L2 未命中 → 返回 503（快照由 SnapshotRefreshScheduler 30s 定时维护）
    │
    └─ 匹配当前请求
        ├─ 按 httpMethod + path 在快照 rules 中查找 InterfaceRule
        │   └─ 未命中 → 返回 403（白名单模式：未注册接口默认拒绝）
        ├─ 遍历用户角色列表，在 InterfaceRule.roleRules 中查找匹配的角色
        │   └─ 无匹配角色 → 返回 403
        └─ 检查条件：无条件→放行；简单条件→本地评估；复杂条件→回调权限中心
```

### 2.4 缓存策略

| 层级 | 存储     | Key                                   | TTL  | 失效方式                    |
| ---- | -------- | ------------------------------------- | ---- | --------------------------- |
| L1   | Caffeine | `perm:snapshot:{serviceCode}`         | 30s  | TTL 过期 + 版本轮询强制刷新 |
| L2   | Redis    | `gateway:perm:snapshot:{serviceCode}` | 5min | TTL 过期 + 版本轮询强制刷新 |

> 快照按服务维度缓存，所有用户共享。SnapshotRefreshScheduler 每 30s 轮询权限中心版本号，版本变更时拉取全量快照并更新 L1+L2。

### 2.5 Sa-Token 集成要点

- 使用 `sa-token-reactor-spring-boot3-starter`（WebFlux 版本）
- Token 存储对接 Redis（`sa-token-redis-jackson`）
- Gateway 只做 Token 解析和校验，**不做登录签发**
- 登录接口 `/auth/**` 在白名单中，请求透传到 admin-service

---

## 3. 管理服务 (admin-service)

### 3.1 职责边界

- **认证中心**：Sa-Token OAuth2 多模式签发（授权码+PKCE、密码、客户端凭证）
- **用户管理**：完整用户生命周期（CRUD、密码、头像、启停），是用户数据的事实源
- **组织管理**：统一组织模型（部门/岗位/团队同表，按组织类型区分），支持多棵组织树和一人多岗
- **菜单管理**：菜单树维护，前端路由配置
- **角色管理**：复用权限中心角色功能，按需补充管理侧逻辑
- **字典管理**：系统字典/枚举值维护
- **通知/消息**：系统公告 + 站内信
- **文件/OSS**：S3 标准文件上传下载
- **审计日志**：用户操作行为记录
- **任务调度**：定时任务管理
- **系统设置**：系统级配置参数管理

### 3.2 模块划分

| #   | 模块        | 预估接口数 | 数据库表                     | 说明                                                            |
| --- | ----------- | ---------- | ---------------------------- | --------------------------------------------------------------- |
| 1   | 认证 (auth) | ~6         | -（Sa-Token）                | 登录/登出/刷新/OAuth2授权端点；Token 存 Redis                   |
| 2   | 用户管理    | ~8         | sys_user                     | 含密码、手机、邮箱等业务字段；CRUD + 启停 + 重置密码 + 个人中心 |
| 3   | 组织管理    | ~8         | sys_org, sys_user_org        | 统一组织表(type区分)，树形结构；用户-组织多对多关联             |
| 4   | 菜单管理    | ~6         | sys_menu                     | 菜单树CRUD + 权限标识配置                                       |
| 5   | 角色管理    | ~4         | -（复用权限中心）            | 代理/封装权限中心的角色相关接口，补充管理端特有逻辑             |
| 6   | 字典管理    | ~6         | sys_dict_type, sys_dict_data | 字典类型 + 字典数据CRUD，支持缓存                               |
| 7   | 通知管理    | ~6         | sys_notice, sys_user_notice  | 系统公告 + 站内信，含已读/未读状态                              |
| 8   | 文件管理    | ~4         | sys_file                     | S3兼容上传/下载/删除，文件元信息持久化                          |
| 9   | 审计日志    | ~3         | sys_audit_log                | 操作日志记录 + 查询（AOP 自动采集）                             |
| 10  | 任务调度    | ~5         | sys_job, sys_job_log         | Spring Scheduler，兼演示定时任务中的权限控制                    |
| 11  | 系统设置    | ~3         | sys_config                   | 系统级参数配置 CRUD                                             |

**预估总接口数：75 个（17 张表）**

### 3.3 核心模型设计概要

#### 3.3.1 用户模型 (sys_user)

与权限中心的 `abstract_user` 关系：

- `admin-service.sys_user` 是**事实源**，存完整业务信息（账号、密码哈希、姓名、手机、邮箱、头像等）
- 用户创建/更新/删除时，通过 **API + RocketMQ 双通道**同步到权限中心的 `abstract_user`
- `sys_user.id` 对应权限中心的 `abstract_user.external_id`
- 同步字段映射：`sys_user.id → external_id`，`sys_user.username → name`，`sys_user.status → enabled`

关键字段（概要）：

| 字段      | 类型         | 说明                                     |
| --------- | ------------ | ---------------------------------------- |
| id        | BIGSERIAL    | 主键                                     |
| tenant_id | BIGINT       | 租户ID                                   |
| username  | VARCHAR(64)  | 登录账号，租户内唯一                     |
| password  | VARCHAR(256) | 密码哈希（BCrypt）                       |
| name      | VARCHAR(128) | 用户姓名                                 |
| phone     | VARCHAR(32)  | 手机号                                   |
| email     | VARCHAR(128) | 邮箱                                     |
| avatar    | VARCHAR(512) | 头像URL                                  |
| gender    | SMALLINT     | 性别                                     |
| status    | SMALLINT     | 状态（0=停用/1=启用）                    |
| 审计字段  | -            | created_by, updated_by, deleted_by, etc. |

#### 3.3.2 组织模型 (sys_org)

核心设计：**部门/岗位/团队同表，org_type 区分，支持多棵独立组织树**

| 字段       | 类型         | 说明                                        |
| ---------- | ------------ | ------------------------------------------- |
| id         | BIGSERIAL    | 主键                                        |
| tenant_id  | BIGINT       | 租户ID                                      |
| parent_id  | BIGINT       | 父节点ID，NULL=根节点                       |
| org_type   | VARCHAR(32)  | 组织类型：DEPT/POSITION/TEAM 等（字典管理） |
| code       | VARCHAR(64)  | 组织编码                                    |
| name       | VARCHAR(128) | 组织名称                                    |
| path       | VARCHAR(512) | 物化路径（如 /1/3/7/），加速树查询          |
| level      | INT          | 层级深度                                    |
| sort_order | INT          | 排序                                        |
| leader_id  | BIGINT       | 负责人（关联 sys_user.id）                  |
| status     | SMALLINT     | 状态（0=停用/1=启用）                       |

**用户-组织关联 (sys_user_org)**：多对多，一人可在多个部门/岗位

| 字段       | 类型      | 说明                                 |
| ---------- | --------- | ------------------------------------ |
| id         | BIGSERIAL | 主键                                 |
| tenant_id  | BIGINT    | 租户ID                               |
| user_id    | BIGINT    | 用户ID                               |
| org_id     | BIGINT    | 组织ID                               |
| is_primary | BOOLEAN   | 是否主组织（每种 org_type 最多一个） |

#### 3.3.3 菜单模型 (sys_menu)

| 字段         | 类型         | 说明                                    |
| ------------ | ------------ | --------------------------------------- |
| id           | BIGSERIAL    | 主键                                    |
| tenant_id    | BIGINT       | 租户ID                                  |
| parent_id    | BIGINT       | 父菜单ID，NULL=根                       |
| menu_type    | VARCHAR(16)  | 类型：DIR(目录)/MENU(菜单)/BUTTON(按钮) |
| service_code | VARCHAR(64)  | 所属服务标识（admin-service 等）        |
| name         | VARCHAR(64)  | 菜单名称                                |
| path         | VARCHAR(256) | 路由路径                                |
| component    | VARCHAR(256) | 前端组件路径                            |
| icon         | VARCHAR(64)  | 图标                                    |
| perm_code    | VARCHAR(128) | 权限标识（与权限中心资源编码关联）      |
| sort_order   | INT          | 排序                                    |
| visible      | BOOLEAN      | 是否可见                                |
| is_frame     | BOOLEAN      | 是否 iframe 嵌入（门户归集外部页面）    |
| status       | SMALLINT     | 状态（0=停用/1=启用）                   |
| extra        | JSONB        | 扩展配置（路由元信息 query 参数等）     |

### 3.4 与权限中心的交互

#### 3.4.1 用户同步

```
admin-service                        permission-center
    │                                       │
    ├─ 创建用户 sys_user ──API/MQ──────────▶ 创建 abstract_user
    │                                       │ + 自动创建个人角色
    │                                       │ + 加入默认分组
    ├─ 更新用户状态 ───────API/MQ──────────▶ 更新 abstract_user.enabled
    │                                       │
    └─ 删除用户 ───────────API/MQ──────────▶ 软删 abstract_user
                                            │ + 级联清理关联
```

#### 3.4.2 角色管理复用

- 管理服务**不自建角色表**，直接调用权限中心的角色相关接口
- 管理端的「角色管理」页面本质上是权限中心接口的 UI 包装
- 管理服务可做额外封装：如把组织-角色的关联逻辑聚合在管理服务侧

#### 3.4.3 菜单与资源同步

- **admin-service 是菜单数据的事实源**（sys_menu 表存完整菜单信息：路由、组件、图标等）
- 菜单创建/更新/删除时，同步到权限中心作为 `resource_entity`（MENU/BUTTON 类型）
- 同步字段映射：`sys_menu.perm_code → resource_entity.code`，`sys_menu.name → resource_entity.name`
- 前端渲染菜单时：从 admin-service 拉取完整菜单树（含路由信息）+ 从权限中心获取用户有权的菜单列表 → 取交集
- **动态路由**：前端登录后拉取用户有权菜单，动态注册 Vue Router 路由

#### 3.4.4 组织与权限中心同步

- 管理服务的组织节点(sys_org) **同步为权限中心的 `abstract_role`（role_type=ORG）**
- 每个组织节点对应一个 ORG 类型角色，用户关联到组织时 → 在权限中心写入 user_role(ROLE)
- 组织树的层级关系通过 role_group 分组来体现（可选）
- 这样用户通过所在组织自动获得该组织角色上配置的权限

---

## 4. Example 演示服务

### 4.1 职责

作为独立微服务，演示第三方业务系统如何对接权限中心实现权限管控。包含前后端，是开发者的**接入参考实现**。

### 4.2 演示模块

| #   | 模块         | 说明                                                                       |
| --- | ------------ | -------------------------------------------------------------------------- |
| 1   | 服务注册演示 | 启动时自动向权限中心注册 service_config + 接口信息（resource_api_mapping） |
| 2   | 接口权限演示 | 注解标记接口权限要求，展示网关鉴权拦截效果                                 |
| 3   | 菜单权限演示 | 前端动态菜单渲染，基于权限中心返回的菜单资源                               |
| 4   | 按钮权限演示 | 前端按钮级别权限控制（v-permission 指令等）                                |
| 5   | 数据权限演示 | 查询数据时附加数据权限过滤条件（项目组 project_id 维度）                   |
| 6   | 权限条件演示 | 展示时间范围/IP白名单条件权限的实际效果                                    |
| 7   | 权限查询演示 | 调用权限中心查询用户权限视图、来源追溯                                     |
| 8   | SDK 集成指南 | 提供可复用的 Starter 封装（接口注册、鉴权注解、数据权限拦截器）            |

### 4.3 数据库

独立 PostgreSQL 实例，存放企业 BI 平台演示业务数据（数据源、报表、数据任务），不涉及权限数据。

### 4.4 SDK / Starter 规划

封装为独立 Maven 模块组（perm-sdk），3 个 Starter + 1 个公共模块：

```
perm-sdk/
├── perm-common/                          # 公共模型、异常、工具（PermResult, PermissionContext, ConditionRule 等）
├── perm-client-spring-boot-starter/      # 业务服务引用
├── perm-data-spring-boot-starter/        # 数据权限参考实现（仅 example 使用）
└── perm-gateway-spring-boot-starter/     # 网关引用
```

| Starter                          | 功能                                                                                                                       |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| perm-common                      | 公共模型（PermResult/PermissionContext/ConditionRule 等）、统一异常                                                        |
| perm-client-spring-boot-starter  | 权限中心客户端：反射扫描接口+@PermResource 增强、全量幂等注册、PermissionClient 鉴权查询、Feign 容错与身份透传（混合模式） |
| perm-gateway-spring-boot-starter | 网关插件：服务维度权限快照管理（30s 版本轮询）、L1+L2 缓存、ConditionEvaluator 条件评估（简单本地/复杂回调）               |
| perm-data-spring-boot-starter    | 数据权限参考实现（非官方 SDK）：@DataPermission/@DataPermissions 注解、JSqlParser SQL 改写、请求级数据范围缓存             |

---

## 5. 服务间事件（RocketMQ Topic 规划）

| Topic                    | 生产者            | 消费者                          | 消息内容                     |
| ------------------------ | ----------------- | ------------------------------- | ---------------------------- |
| USER_SYNC                | admin-service     | permission-center               | 用户创建/更新/删除事件       |
| PERMISSION_CHANGE_NOTIFY | permission-center | admin-service / example-service | 权限变更通知（角色、资源等） |

---

## 6. 已确认决策汇总

| #   | 事项              | 决策                                                                 |
| --- | ----------------- | -------------------------------------------------------------------- |
| Q1  | 菜单数据归属      | admin-service 存完整菜单表(sys_menu)，同步到权限中心 resource_entity |
| Q2  | 组织-权限中心映射 | 组织同步为权限中心的 abstract_role(ORG 类型)                         |
| Q3  | 限流方案          | 首期不做，后续按需集成                                               |
| Q4  | 任务调度          | Spring Scheduler（轻量），兼演示定时任务的权限控制                   |
| Q5  | 前端技术栈        | Vue 3 + Element Plus                                                 |
| Q6  | 前端菜单路由      | 动态路由：登录后拉取用户有权菜单，动态注册 Vue Router 路由           |

---

## 7. 设计约定（与权限中心一致）

- **无数据库外键**：所有关联为逻辑 ID
- **多租户**：所有表带 `tenant_id`
- **软删除**：`delete_flag`（0=未删除，删除时填本行id）
- **审计字段**：`created_by`、`updated_by`、`deleted_by`、`created_at`、`updated_at`、`deleted_at`
- **所有接口 POST + JSON Body**
- **通用响应结构**：`{ "code": 200, "message": "success", "data": {} }`
- **分页规范**：与权限中心一致的 `pageNum/pageSize/rows/total`
