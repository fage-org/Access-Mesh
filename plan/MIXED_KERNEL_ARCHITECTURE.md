# 混合内核权限平台架构

## 1. 目标

本方案面向中小型企业，最终采用 `gateway + identity-service + permission-center` 三个核心启动项目的混合内核架构。

- `gateway` 负责统一接入、令牌校验、接口级权限拦截。
- `identity-service` 负责认证、用户组织岗位主数据、服务账号、委托授权证明。
- `permission-center` 负责权限事实模型、用户与角色授权、接口权限快照、权限查询、变更审计。

设计原则：

- 80% 标准能力内建，20% 业务个性能力可扩展。
- 核心启动项目保留 `gateway`、`identity-service`、`permission-center`，其余业务启动模块均视为待移除对象。
- 接口统一采用 `POST + JSON`。
- **原表为事实层，kernel 为查询/消费层**。
- `permission-center` 以 `abstract_user`、`abstract_role`、`user_role`、`resource_entity`、`operation_permission`、`role_resource_permission` 等原表作为权限事实来源。
- `operation_permission` 通过 `resource_type` 直接绑定适用的资源类型，`binary_bit + inherit_mask`（BIGINT）表达操作继承。
- `resource_api_mapping` 显式表达接口资源与 `service_code + http_method + path_pattern` 的映射。
- `permission_version` 作为运行时版本游标，供 `identity-service` 写入令牌、供 `gateway` 判断是否刷新本地快照。
- `gateway` 不直接查库，只消费 `permission-center` 暴露的快照、判定与版本接口。
- `permission-center` 不承载具体业务系统的数据权限执行，只输出标准权限描述。
- 复杂权限逻辑默认在业务服务侧落地，中心只做标准化管理与查询。
- 所有能力按原生多租户设计。

## 2. 运行时边界

### 2.1 gateway

- 校验用户令牌、服务令牌、委托令牌。
- 做接口级权限拦截，不做复杂数据权限判定。
- 维护本地接口权限快照缓存。
- 根据 `permissionVersion` 按需向 `permission-center` 拉取快照。

### 2.2 identity-service

- 维护用户、组织、岗位、成员关系、服务账号。
- 负责登录、令牌签发、令牌解析、令牌续期。
- 负责签发和撤销委托授权证明。
- 登录主体需映射到 `permission-center.abstract_user`。
- 签发令牌前查询当前 `permissionVersion`。
- 令牌只携带身份上下文与版本号，不携带完整权限快照。

### 2.3 permission-center

- 维护 17 张权限事实表，不再引入第二套主存储模型。
- 对外提供 kernel 风格的查询/消费接口，供 `gateway` 和其他运行时组件消费。
- 重点交付：接口权限快照查询、接口判定、版本查询、用户与角色授权管理、条件审核、冲突检测、依赖查询。

## 3. 核心模型

### 3.1 主体模型

- `USER`：人类用户主体。
- `SERVICE`：服务主体，例如定时任务服务、集成服务。
- `DELEGATED`：服务主体携带用户委托证明后的复合上下文。

### 3.2 事实模型

- 主表：`abstract_user`、`abstract_role`、`user_role`、`resource_entity`、`operation_permission`、`role_resource_permission`。
- 配套保留域配置（`domain_scope_config`、`domain_relation_config`、`domain_scope_binding`）、权限条件（`permission_condition`）、资源依赖（`resource_dependency`）、冲突规则（`permission_conflict_rule`）、变更日志等表。
- `operation_permission` 通过 `resource_type` 绑定适用的资源类型，`binary_bit + inherit_mask`（BIGINT）表达操作继承关系。
- `resource_entity` 承载菜单、按钮、接口、数据对象等资源定义。资源树继承由查询接口参数控制。
- `permission_condition` 支持预设（handler 编码）和自定义（需审核），通过 `condition_source`、审核状态 `status` 与独立启停开关 `enabled` 管理。
- `permission_conflict_rule` 在查询时检测冲突，冲突权限失效并异步通知管理员修正。
- `resource_dependency` 由资源注册方自动维护，权限中台只负责存储与查询。
- 类型定义使用专用 `type_definition` 表（原 system_config）。

### 3.3 运行时补充模型

- `resource_api_mapping`：把接口类型资源映射到具体服务编码、HTTP 方法、路径模式，供 `gateway` 做精确匹配。
- `permission_version`：记录租户当前权限版本，权限发生变更后递增，供令牌与网关缓存协同刷新。
- kernel DTO 只作为对外查询包装，不再作为新的存储事实层。

### 3.4 扩展方向

- 17 张表覆盖标准 RBAC + 域隔离 + 条件 + 冲突 + 依赖场景。
- 当标准模型无法覆盖剩余场景时，允许业务侧基于事实层结果做二次判定。
- 数据权限执行由业务服务侧落地，权限中台只输出标准描述。

## 4. 关键流程

### 4.1 登录

1. 用户或服务在 `identity-service` 完成认证。
2. `identity-service` 将登录主体映射到 `permission-center` 中的 `abstract_user`。
3. `identity-service` 在签发令牌前查询对应租户当前的 `permissionVersion`。
4. 签发只包含身份上下文、租户上下文、版本号的令牌。

### 4.2 gateway 接口鉴权

1. `gateway` 校验令牌。
2. 读取令牌中的 `permissionVersion`。
3. 本地无快照或版本变更时，从 `permission-center` 拉取接口权限快照。
4. `permission-center` 基于原始授权表 + `resource_api_mapping` 组装快照，组装时执行冲突检测排除冲突权限。
5. `gateway` 根据 `service_code + http_method + path_pattern` 匹配接口资源并执行拦截。

### 4.3 服务代表用户调用

1. 用户为服务申请委托授权证明。
2. `identity-service` 签发带委托信息的服务令牌。
3. `gateway` 和业务服务同时基于服务主体与委托上下文做权限控制。

## 5. 对外接口分组

### 5.1 identity-service

- `/api/identity/auth/*`
- `/api/identity/delegation/*`
- `/api/identity/subjects/*`

### 5.2 permission-center

- `/api/perm/policy/*`
- `/api/perm/decision/*`
- `/api/perm/version/*`
- `/api/perm/conditions/*`
- `/api/perm/conflict-rules/*`
- `/api/perm/resource-dependencies/*`
- `/api/perm/audit/*`

## 6. 扩展约束

- 扩展逻辑尽量保持单层接口，不引入深层回调链。
- 对外 DTO 必须从事实层稳定映射出来，不能反向变成新的主存储模型。
- `permission-center` 只关心"授权配置是否成立"和"描述是否可查询"，不关心业务 SQL 或领域对象装配。
- `gateway` 的接口鉴权必须可关闭、可渐进接入，避免一次性替换现有链路。

## 7. 落地策略

- 短期先完成架构骨架、共享契约、关键入口与占位实现。
- 第一优先级先做模块收敛，逐步去除示例模块、可视化附属模块与非核心业务模块的默认构建和启动职责。
- 中期逐步补齐 `identity-service`、`permission-center` 与 `gateway` 的真实业务能力。
- 长期再做 MQ 失效通知、快照优化、审计治理、扩展生态。
