---
doc_type: design
title: 微服务架构设计
status: adopted
domain: common
last_reviewed: 2026-08-22
---

# 微服务架构设计

本文档定义项目整体微服务架构、各服务职责、模块划分及服务间交互方式。权限中心概念模型见 `permission-center/overview.md`。

> **归并提示（T-ACCESS-012 全量回写，2026-08-22）**：`admin-service` 与 `permission-center` 已物理归并为模块化单体 `access-service`（唯一部署单元，T-ACCESS-001~012）。本文已按归并后实现回写；access-service 内部模块边界、事务、缓存与安全细节以 [`access-service-architecture.md`](access-service-architecture.md) 为准，对外接口契约以 [`services/admin-service-api-contract.md`](services/admin-service-api-contract.md) 与 [`permission-center/api-contract.md`](permission-center/api-contract.md) 为准。

---

## 1. 整体架构

### 1.1 服务清单

> T-ACCESS-010（2026-08-22）起归并后拓扑为**当前实施基线**：`admin-service` 与 `permission-center` 已物理归并为 `access-service`（唯一部署单元），详见 `access-service-architecture.md` §2。

| 服务                          | 技术栈                         | 数据库                 | 端口(建议) | 说明                                                                   |
| ----------------------------- | ------------------------------ | ---------------------- | ---------- | ---------------------------------------------------------------------- |
| gateway                       | Spring Cloud Gateway (WebFlux) | 无（纯网关）           | 8080       | 流量入口：路由转发、Token 校验、接口鉴权                               |
| access-service（访问控制服务）| Spring Boot 3 (WebMVC)         | PostgreSQL（access_db，public schema） | 9100 | 用户、组织、菜单、认证、字典/通知/文件/审计/调度（admin 域）+ 通用权限管理与鉴权引擎（permission 域）；模块化单体，默认组织树是用户目录；组织既是业务树也是角色容器 |
| example-service（演示服务）   | Spring Boot 3 (WebMVC)         | 无（瘦身后无数据源，T-API-001） | 9300       | 权限中心接入示例：单受保护接口 `POST /api/example/demo/hello`（身份回显，经 Gateway `/example/**` 路由鉴权，3xxxx 错误码段）；接口级鉴权完全由 Gateway 承担（规范 §2.4），业务服务不引入权限 SDK |

### 1.2 基础设施

| 组件     | 选型                               | 说明                                 |
| -------- | ---------------------------------- | ------------------------------------ |
| 注册中心 | Nacos                              | 服务发现 + 配置管理一体              |
| 配置中心 | Nacos                              | 与注册中心复用                       |
| 消息队列 | RocketMQ                           | 预留给未来异步事件；当前无内部同步链路（归并后同库同事务），缓存失效经 Redis pub/sub 广播，不经过 RocketMQ |
| 缓存     | Redis                              | L2 缓存、Sa-Token 会话存储、分布式锁 |
| 文件存储 | 本地磁盘（file.storage.path）       | 文件上传下载；单实例约束（多实例本地盘不可共享，access-service-architecture §15，T-ADMIN-023 登记） |
| 任务调度 | Spring Scheduler                   | 轻量定时任务（兼演示权限控制）       |
| 认证框架 | Sa-Token + OAuth2                  | 多种授权模式并存                     |
| 链路追踪 | Micrometer Tracing + OpenTelemetry | 分布式 traceId 生成与传递            |
| 前端框架 | Vue 3 + Element Plus               | 管理端 + example 演示端              |

### 1.3 架构拓扑图

> T-ACCESS-010（2026-08-22）起为归并后拓扑：Gateway 是用户流量唯一入口，`/admin/**`、`/perm/**`、`/auth/**` 统一路由到 `lb://access-service`。

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
                          └──┬────────────┬──┘
                             │            │
                ┌────────────┘            └────────────┐
                │                                      │
       ┌────────▼─────────────────┐          ┌────────▼────────┐
       │      access-service      │          │  example-service│
       │  （模块化单体，9100）    │          │    （9300）     │
       │ admin 域：               │          │                 │
       │ · 用户/组织/菜单         │          │ · 对接演示      │
       │ · 认证/OAuth2            │          │ · 权限展示      │
       │ · 字典/通知/文件         │          │                 │
       │ · 审计/任务调度          │          │                 │
       │ permission 域：          │          │                 │
       │ · 权限管理/鉴权引擎      │          │                 │
       └───────────┬──────────────┘          └────────┬────────┘
                   │                                  │
                   ▼                                  ▼
              PostgreSQL                         （无）
              (access_db)                  example-service 无数据源（T-API-001 瘦身）
```

### 1.4 服务间交互矩阵

> T-ACCESS-010（2026-08-22）起为归并后交互；admin 与 permission 之间的内部同步/通知调用已随 T-ACCESS-005 删除（同进程 `access.application` 同事务本地投影替代）。

| 调用方          | 被调方        | 协议           | 场景                                                                                         |
| --------------- | ------------- | -------------- | -------------------------------------------------------------------------------------------- |
| gateway         | access-service | HTTP (转发)    | `/admin/**`、`/perm/**`（合并路由，StripPrefix=1）、`/auth/**`（StripPrefix=0）登录与管理接口转发 |
| gateway         | access-service | HTTP (负载均衡 WebClient) | 快照鉴权：`POST /api/perm/auth/interface-snapshot` 拉取全量接口权限快照；未覆盖场景回退 `check-interface` 实时鉴权 |
| gateway         | example-service | HTTP (转发)   | 演示服务接口转发                                                                             |
| example-service | access-service | HTTP（内部同步通道，运维期） | 接口资源注册：`POST /api/perm/resource-entity/sync`（X-Internal-Secret + X-Service-Code 身份）。运行期业务调用为零——接口级鉴权由 Gateway 承担（T-API-001：example 已删 perm-client/openfeign，无 Feign 鉴权查询） |

### 1.5 管理端前后端交互原则

- 管理端前端统一通过 Gateway 访问 access-service（`/admin/**`、`/perm/**`、`/auth/**` 路由目标统一），由 access-service 作为前端唯一后端聚合入口；前端不直接调用后端服务。
- 认证链采用“最小登录返回 + 后续聚合拉取”模型：前端调用 `/auth/login` 获取 token 与最小身份信息后，再调用 `/auth/userinfo` 与 `/auth/user-menu` 获取用户上下文、菜单、角色和权限结果。
- 业务路由、菜单和按钮权限的真实来源是后端聚合结果。其中菜单和路由由 access-service admin 域聚合下发，按钮权限由稳定 `permissions` 权限码表达。
- 前端本地 mock 可以保留并改造，用于基础前端验证、联调兜底和组件级演示，但不作为长期生产契约或路由权限事实源。
- 管理端前端最终只保留一套权限呈现模型；模板式 `auths`、`meta.roles` 等逻辑仅允许作为过渡兼容，不再作为新增设计的基准。

### 1.6 主体、业务域与接入层原则

- access-service admin 域中的默认组织树是租户内用户目录/身份池，负责用户生命周期；非默认组织树只维护“已有用户与组织节点的关系”。完整规则见 `default-org-tree-user-lifecycle.md`。
- 组织既是业务树，也是角色容器。组织结构由 admin 域主维护；与组织相关的角色、用户角色事实最终落在 permission 域（同进程同库，`access.application` 同事务写入）。
- `user-org` 变更需要稳定映射到 `user-role`。组织默认角色、岗位映射角色等规则由 `access.application` 编排，permission 域保存最终权限事实。
- 权限模型内必须区分四类事实：`abstract_user` 表示访问主体，`resource_entity(USER)` 表示被管理用户资源，`resource_entity(ORG)` 表示被管理组织资源（T-ACCESS-018 类型收敛后），`abstract_role(ORG/POSITION)` 表示组织/岗位角色容器。admin 域不存储这些事实的内部 ID，所有跨域操作使用业务键。
- 业务域只承担角色、权限分类和后台管理视角隔离职责，不承担数据权限载体、运行时鉴权主链或资源归属重构职责。
- 对外交付分层建设：核心主线稳定后，example-service 作为真实接入示例补齐；SDK 交付目标分为 Spring Boot starter、普通 Java client SDK 和其他语言对接文档三层。

---

## 2. Gateway 网关服务

### 2.1 职责边界

- **路由转发**：按配置规则将请求转发到后端服务
- **Token 校验**：解析 Sa-Token 令牌，提取租户和主体信息，清洗外部伪造 Header 后注入标准请求头
- **接口鉴权**：快照模式对接 access-service（T-PERM-001），本地内存匹配判定接口权限，未覆盖场景回退实时鉴权
- **白名单管理**：公开接口（登录、注册、公开资源等）免鉴权；健康检查不在主端口白名单——actuator 经独立管理端口提供（T-GW-007）
- **请求头增强**：向下游注入 X-Tenant-Id、X-User-Id、X-Request-Id 等标准头

### 2.2 模块划分

| #   | 模块             | 说明                                                                                           |
| --- | ---------------- | ---------------------------------------------------------------------------------------------- |
| 1   | 路由配置         | 基于 Nacos 动态路由配置，支持按服务名/路径匹配转发                                             |
| 2   | Token 校验过滤器 | 全局 GatewayFilter，Sa-Token 解析令牌，校验有效性和登录状态                                    |
| 3   | 接口鉴权过滤器   | 快照模式本地匹配（OR 合并 + 三态判定），条件不可本地评估时回退 access-service 实时鉴权；快照 L1 ≤15s，回源失败固定 fail-closed |
| 4   | 白名单管理       | 可配置的公开接口列表（Nacos 配置动态刷新），匹配的请求跳过鉴权                                 |
| 5   | 请求头增强       | 注入标准请求头（X-Tenant-Id、X-User-Id、X-Request-Id），清洗外部伪造头                         |
| 6   | 异常处理         | 统一 JSON 错误响应格式，鉴权失败/服务不可用等不同错误码                                        |

### 2.3 鉴权流程（快照模式，与权限域 core-flows 场景六对齐）

```
请求到达 Gateway
    │
    ├─ 匹配白名单？ → 是 → 跳过鉴权，直接转发
    │
    ├─ 解析 Token → 失败 → 返回 401
    │
    ├─ 提取 X-Tenant-Id、主体标识、serviceCode
    │
    ├─ 查本地快照缓存（tenantId + subjectTypeCode + userId + serviceCode）
    │   ├─ 命中 → 本地内存匹配 allowedApis（OR 合并 + 三态判定）
    │   │        ├─ 任一无条件授权 → 放行
    │   │        ├─ 条件授权且条件规则已内联 → 本地评估，通过则放行
    │   │        └─ 条件规则未下发 → 标记 FALLBACK，回退实时鉴权
    │   └─ 未命中 → 在 5 秒全链路硬截止内回源拉取快照
    │              POST /api/perm/auth/interface-snapshot（access-service）
    │
    ├─ FALLBACK：POST /api/perm/auth/check-interface（access-service 实时鉴权）
    │
    └─ 回源失败/超截止 → 固定 fail-closed 503；匹配拒绝 → 403
```

> 快照缓存 TTL ≤15s，access-service 写路径在事务提交后经 Redis pub/sub（`perm:invalidate`）主动失效；权限主动撤销优先于不可达兜底，任何不确定性一律拒绝（fail-closed，不可配置）。完整规则见 [`services/gateway.md`](services/gateway.md)。

### 2.4 缓存策略

| 层级 | 存储 | Key 模式 | TTL | 失效方式 |
| ---- | -------- | -------------------------------------- | --- | -------------------- |
| 快照 L1 | Caffeine（统一 CacheService，L1_ONLY，catalog `gw:interface-snapshot`） | `{tenantId}:gw:interface-snapshot:{subjectTypeCode,userId,serviceCode}` | ≤15s | Redis pub/sub `perm:invalidate` 主动失效 + TTL 兜底 |
| L2 | 无 | — | — | 快照缓存不落 L2；Sa-Token 会话与失效订阅仍使用 Redis |

> 一次授权请求触发的整个快照加载流程共享不超过 5 秒的墙钟硬截止，超时不写缓存并固定 fail-closed。串行授权安全预算 ≤30s = access-service 授权 L2（≤10s）+ 回源全链路截止（≤5s）+ Gateway 快照 L1（≤15s），详见 [`access-service-architecture.md`](access-service-architecture.md) §7.2。

### 2.5 Sa-Token 集成要点

- 使用 `sa-token-reactor-spring-boot3-starter`（WebFlux 版本）
- Token 存储对接 Redis（`sa-token-redis-jackson`）
- Gateway 只做 Token 解析和校验，**不做登录签发**
- 登录接口 `/auth/**` 在白名单中，请求透传到 access-service

---

## 3. access-service 管理域（admin 域）

> access-service 是模块化单体（详见 [`access-service-architecture.md`](access-service-architecture.md)），本节概述其 admin 域职责；对外接口契约见 [`services/admin-service-api-contract.md`](services/admin-service-api-contract.md)。表结构权威 DDL 为 [`schema/access-service.sql`](schema/access-service.sql)。

### 3.1 职责边界

- **认证中心**：Sa-Token OAuth2 多模式签发（授权码+PKCE、密码、客户端凭证）
- **用户管理**：完整用户生命周期（CRUD、密码、头像、启停），是用户数据的事实源
- **组织管理**：统一组织模型（部门/岗位/团队同表，按组织类型区分），支持多棵组织树和一人多岗
- **菜单管理**：菜单树维护，前端路由配置
- **角色管理**：角色与授权由 permission 域直接提供（`/api/perm/abstract-role` 等），管理端不重复建设
- **字典管理**：系统字典/枚举值维护
- **通知/消息**：系统公告 + 站内信
- **文件管理**：本地磁盘文件上传下载（单实例约束，无对象存储）
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
| 5   | 角色管理    | ~4         | -（permission 域表）         | 功能角色列表经 `application.query` 跨域只读查询；角色/授权管理直接使用 permission 域接口，旧 admin 侧代理端点已删除（T-ADMIN-024，无映射 404） |
| 6   | 字典管理    | ~6         | sys_dict_type, sys_dict_data | 字典类型 + 字典数据CRUD，支持缓存                              |
| 7   | 通知管理    | ~6         | sys_notice, sys_user_notice  | 系统公告 + 站内信，含已读/未读状态                              |
| 8   | 文件管理    | ~4         | sys_file                     | 本地磁盘上传/下载/删除（单实例约束），文件元信息持久化                          |
| 9   | 审计日志    | ~3         | operation_log（合并表）      | 操作日志记录 + 查询（`@OperationLog` AOP 自动采集；module=ADMIN/PERMISSION/ACCESS） |
| 10   | 任务调度    | ~5         | sys_job, sys_job_log, sys_task_execution | Spring Scheduler + 数据库租约（多实例抢占/续租/接管），兼演示定时任务中的权限控制 |
| 11   | 系统设置    | ~3         | system_config（合并表）      | 系统级参数配置 CRUD（`admin.*`/`permission.*`/`access.*` 命名空间） |

**预估总接口数：75 个（admin 域 sys_* 14 张表；sys_config/sys_audit_log 已并入合并表 system_config/operation_log，sys_sync_task 已随 T-ACCESS-005 退役）**

### 3.3 核心模型设计概要

#### 3.3.1 用户模型 (sys_user)

与 permission 域 `abstract_user` 的关系：

- `sys_user` 是 **admin 域事实源**，存完整业务信息（账号、密码哈希、姓名、手机、邮箱、头像等）。
- 用户生命周期由默认组织树承载。创建用户时必须绑定默认组织树中的组织节点；禁用、删除、重置密码等高危账号操作不属于非默认组织树成员管理。
- 用户创建/更新/删除时，由 `access.application` 在**同一 PostgreSQL 事务**内写入/更新权限域本地投影（`abstract_user` + `resource_entity(USER)`），无跨服务同步链路（T-ACCESS-005）。
- 若需要 `USER:{userId}` 实例级管理权限，用户投影同时维护 `resource_entity(resourceTypeCode=USER, resourceCode=sys_user.id)`，使用业务键 `resourceTypeCode=USER + resourceCode=sys_user.id` 定位。
- 投影字段映射：`sys_user.id → external_id`，`sys_user.username → name`，`sys_user.status → enabled`

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

`sys_org_tree_config.is_default=true` 的组织树是租户内身份目录树，不只是 UI 默认展示树。非默认组织树用于业务维度成员关系管理，不能创建、禁用或删除真实用户。

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

**用户-组织关联 (sys_user_org)**：多对多，一人可在多个部门/岗位。默认组织树下的关系表达用户身份目录归属；非默认组织树下的关系表达业务组织成员关系。

| 字段       | 类型      | 说明                                 |
| ---------- | --------- | ------------------------------------ |
| id         | BIGSERIAL | 主键                                 |
| tenant_id  | BIGINT    | 租户ID                               |
| user_id    | BIGINT    | 用户ID                               |
| org_id     | BIGINT    | 组织ID                               |
| is_primary | BOOLEAN   | 是否主组织；首期仅表示默认组织树下的主归属 |

#### 3.3.3 菜单模型 (sys_menu)

> **schema 迁移（2026-06-20 审计 S-002=B）**：sys_menu 表结构按 v3.5 §2.1 最终态迁移 — `menu_type` 改 5 值枚举(DIR/MENU/EXTERNAL/IFRAME/HIDDEN)，删除 `BUTTON` 类型与 `visible`/`is_external`/`is_frame`/`is_cache`/`perm_code`/`primary_operation`/`operations`/`default_preset` 字段，新增 `source_service`/`resource_type`/`resource_code` 关联业务资源 link。详见 v3.5 §2.1 + §4.1 菜单可见性派生公式。原 BUTTON 行（按钮权限）不再由 sys_menu 承载，归 v3.5.1+ 评估。

sys_menu 的权威 DDL 见 [`schema/access-service.sql`](schema/access-service.sql)（sys_menu 节）。本节仅列语义要点，不复制 DDL（避免与权威 schema 双源漂移）：

| 字段            | 语义                                    |
| --------------- | --------------------------------------- |
| id              | 主键                                    |
| tenant_id       | 租户ID                                  |
| parent_id       | 父菜单ID，NULL=根                       |
| display_name    | 菜单名称                                |
| path            | 路由路径                                |
| icon            | 图标                                    |
| sort_order      | 排序                                    |
| menu_type       | 类型：DIR/MENU/EXTERNAL/IFRAME/HIDDEN   |
| status          | 状态：ENABLED/DISABLED                  |
| resource_type   | 关联业务资源类型（不参与鉴权决策）      |
| resource_code   | 关联业务资源实例（不参与鉴权决策）      |
| source_service  | 业务服务标识（链路追溯）                |
| delete_flag     | 软删标记                                |
| created_at      | 创建时间                                |
| updated_at      | 更新时间                                |

唯一索引：`uk_sys_menu_tenant_resource (tenant_id, resource_type, resource_code)`、`uk_sys_menu_tenant_path (tenant_id, path)`。

> **已废弃字段**（迁移期物理删除）：`perm_code` / `operations` / `primary_operation` / `default_preset` / `visible` / `is_external` / `is_frame` / `is_cache` / `component`（前端组件路径归前端路由配置，不在 sys_menu） / `extra`（JSONB 扩展，按需迁移） / `service_code`（被 `source_service` 替代）。

### 3.4 与 permission 域的关系（同事务本地投影）

admin 域管理事实（`sys_user`/`sys_org`/`sys_menu`）与 permission 域权限事实（`abstract_user`/`abstract_role`/`resource_entity`/`user_role`）位于同一进程、同一数据库（`access_db.public`）。跨域写操作由 `access.application` 在同一 PostgreSQL 事务内编排：更新管理事实的同事务写入对应权限投影，任一步失败整体回滚。原跨服务 API 同步、消息通知与补偿链路（内部同步子系统）已随 T-ACCESS-005 退役。

- **用户投影（双事实，不可混淆）**：
  - `abstract_user` 用于主体解析，业务键为 `subjectTypeCode=LOCAL_USER + externalId=sys_user.id`（原 ADMIN_USER 更名，T-ACCESS-016）；
  - `resource_entity(USER)` 用于实例级用户管理权限，业务键为 `resourceTypeCode=USER + resourceCode=sys_user.id`。只维护 `abstract_user` 时用户可参与鉴权，但 `USER:{userId}` 的更新/删除/启停等实例级权限无法稳定解析。
- **组织投影（双事实）**：`resource_entity(ORG)`（组织作为可管理资源，支撑 `ORG:{orgId}` 实例级校验与 `auth/query-resources`）+ `abstract_role(ORG/POSITION)`（普通组织→`role_type=ORG`，岗位→`role_type=POSITION`，组织/岗位作为角色容器）。用户关联组织时，`access.application` 同事务写入对应 `user_role`；组织树层级由 permission 域自动维护，编排层只传当前节点和父节点业务键。
- **菜单**：sys_menu 仅承载 UI 路由元数据 + 关联资源 link（`resource_type`/`resource_code`），不承载权限语义；菜单可见性由 v3.5 §4.1 派生公式（`∃ op`）计算，前端经 `/auth/user-menu` 单 RPC 获取 `menus[] + permissions[]`，动态注册 Vue Router 路由。
- **投影所有权**：本地投影统一标记 `owner_service_code='access-service'`，只能经 `LocalProjectionDomainService` 写入；权限管理 API 不得直接修改本地投影，外部 sync 不得冒充本地来源（`sourceService=access-service/admin-service` 被拒绝）。

权威细节见 [`access-service-architecture.md`](access-service-architecture.md) §3/§4、[`services/admin-service-api-contract.md`](services/admin-service-api-contract.md) §3、[`default-org-tree-user-lifecycle.md`](default-org-tree-user-lifecycle.md)。

---

## 4. Example 演示服务

### 4.1 职责

作为独立微服务，演示第三方业务系统如何对接 access-service 权限能力实现权限管控。包含前后端，是开发者的**接入参考实现**。

### 4.2 演示模块

| #   | 模块         | 说明                                                                       |
| --- | ------------ | -------------------------------------------------------------------------- |
| 1   | 服务注册演示 | 启动时自动向 access-service 注册 service_config + 接口信息（resource_api_mapping） |
| 2   | 接口权限演示 | 注解标记接口权限要求，展示网关鉴权拦截效果                                 |
| 3   | 菜单权限演示 | 前端动态菜单渲染，基于 access-service 返回的菜单与权限结果                  |
| 4   | 按钮权限演示 | 前端按钮级别权限控制（v-permission 指令等）                                |
| 5   | 数据权限演示 | 查询数据时附加数据权限过滤条件（项目组 project_id 维度）                   |
| 6   | 权限条件演示 | 展示时间范围/IP白名单条件权限的实际效果                                    |
| 7   | 权限查询演示 | 调用 access-service 查询用户权限视图、来源追溯                             |
| 8   | SDK 集成指南 | 提供可复用的 Starter 封装（接口注册、鉴权注解、数据权限拦截器）            |

### 4.3 数据库

独立 PostgreSQL 实例，存放企业 BI 平台演示业务数据（数据源、报表、数据任务），不涉及权限数据。

### 4.4 SDK / Starter 规划

封装为独立 Maven 模块组（perm-sdk），3 个 Starter + 1 个公共模块：

```
perm-sdk/
├── perm-common/                          # 公共模型、异常、工具（PermResult, PermissionContext, ConditionRule 等）
├── perm-client-spring-boot-starter/      # 业务服务引用
├── perm-data-spring-boot-starter/        # 数据权限参考实现（⚠️ 未实现/规划中，无使用方；example 已随 T-API-001 移除该依赖）
└── perm-gateway-spring-boot-starter/     # 网关引用
```

| Starter                          | 功能                                                                                                                       |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| perm-common                      | 公共模型（PermResult/PermissionContext/ConditionRule 等）、统一异常                                                        |
| perm-client-spring-boot-starter  | access-service 权限客户端（已实现部分）：`PermissionFeignClient` 远程查询/写方法（checkAuth、batchCheckAuth、角色/资源/授权维护等）、`@EnableFeignClients` 自动装配与 `X-Internal-Secret`/`X-Service-Code` 身份透传拦截器（`perm.client.enabled` 开关）。接口扫描/@PermResource/自动注册未实现（T-API-001 名实对齐；有真实消费者后另行评估） |
| perm-gateway-spring-boot-starter | 网关插件：快照模式本地匹配鉴权（T-PERM-001）、条件本地评估、未覆盖场景回退 access-service 实时鉴权                        |
| perm-data-spring-boot-starter    | 数据权限参考实现（非官方 SDK）：@DataPermission/@DataPermissions 注解、JSqlParser SQL 改写、请求级数据范围缓存 **（⚠️ 规划中，未实现 — 2026-06-20 审计 S-011：当前模块仅含空 `PermDataAutoConfiguration`，注解/拦截器/SQL 改写均未落地，待核心主线稳定后补齐）**             |

### 4.5 核心 API 清单

#### 4.5.1 perm-client-spring-boot-starter

| 组件                           | 说明                                                                                                  |
| ------------------------------ | ----------------------------------------------------------------------------------------------------- |
| `PermissionFeignClient`        | `@FeignClient(name="access-service")`：checkAuth/batchCheckAuth 等远程查询与角色/资源/授权维护方法（api-contract 契约） |
| `FeignInternalSyncInterceptor` | 同步/写路径身份透传：注入 `X-Internal-Secret`（`${perm.internal-secret}`）与 `X-Service-Code`（`${perm.service-code}`） |

#### 4.5.2 perm-gateway-spring-boot-starter

| 组件                 | 说明                                                      |
| -------------------- | --------------------------------------------------------- |
| `PermissionFilter`   | Gateway GlobalFilter，Order=-60，快照模式本地匹配鉴权，条件不可本地评估时回退 access-service 实时鉴权 |
| `ConditionEvaluator` | 评估条件规则（时间范围、IP白名单等），返回匹配结果        |

---

~~## 5. 服务间事件（RocketMQ Topic 规划）~~

> 已移除。服务间同步改为仅 API 调用，不再使用 RocketMQ 传递用户同步和权限变更通知。

~~| Topic | 生产者 | 消费者 | 消息内容 |~~
~~| ------------------------ | ----------------- | ------------------------------- | ---------------------------- |~~
~~| USER_SYNC | admin-service | permission-center | 用户创建/更新/删除事件 |~~
~~| PERMISSION_CHANGE_NOTIFY | permission-center | admin-service / example-service | 权限变更通知（角色、资源等） |~~

---

## 6. 已确认决策汇总

| #   | 事项              | 决策                                                                 |
| --- | ----------------- | -------------------------------------------------------------------- |
| Q1  | 菜单数据归属      | sys_menu 由 access-service admin 域维护（UI 路由元数据 + 关联资源 link）；关联资源经同事务本地投影落 permission 域 resource_entity |
| Q2  | 组织-权限域映射   | 组织以 `resource_entity(ORG)` + `abstract_role(ORG/POSITION)` 双事实投影，由 access.application 同事务维护，均使用业务键定位 |
| Q3  | 限流方案          | 首期不做，后续按需集成                                               |
| Q4  | 任务调度          | Spring Scheduler（轻量），兼演示定时任务的权限控制                   |
| Q5  | 前端技术栈        | Vue 3 + Element Plus                                                 |
| Q6  | 前端菜单路由      | 动态路由：登录后拉取用户有权菜单，动态注册 Vue Router 路由           |

---

## 7. 设计约定（与项目规范一致）

- **无数据库外键**：所有关联为逻辑 ID
- **多租户**：所有表带 `tenant_id`
- **软删除**：`delete_flag`（0=未删除，删除时填本行id）
- **审计字段**：`created_by`、`updated_by`、`deleted_by`、`created_at`、`updated_at`、`deleted_at`
- **所有接口 POST + JSON Body**
- **通用响应结构**：`{ "code": 200, "message": "success", "data": {} }`
- **分页规范**：与项目规范一致，分页入参使用 `pageNum/pageSize/sort`，分页响应使用 `items/total/pageNum/pageSize/hasNext`
- **限制使用 Lombok**：仅允许精确导入 `@Getter` / `@Setter`，`@Builder` 可用于复杂构造或测试数据装配；禁止 `@Data`、`@Value`、`@EqualsAndHashCode` 等隐式生成过多逻辑的注解；不可变 DTO 优先使用 Java 21 Record
