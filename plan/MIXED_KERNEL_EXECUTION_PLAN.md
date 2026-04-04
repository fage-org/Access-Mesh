# 混合内核权限平台执行计划

## 当前状态

- 更新时间：2026-04-04
- Phase 0：已完成
- Phase 1：已完成
- Phase 2：已完成
- Phase 3：已完成
- Phase 4：已完成
- Phase 5：已完成
- Phase 6：进行中，且为当前最高优先级
- Phase 7：未开始
- Phase 8：未开始
- Phase 1-4 骨架已完成本地编译与定向测试验证
- 默认 Maven 聚合、默认部署文档、默认 CI、默认 Nacos 初始化脚本、默认 Docker/K8s 交付面已收敛为 `gateway + identity-service + permission-center` 三个核心启动项目
- 表结构经评审后定为 17 张表：还原 permission_condition / resource_dependency / permission_conflict_rule / domain_relation_config / domain_scope_binding；system_config 改名 type_definition；operation_permission 改为 resource_type 绑定 + BIGINT 位运算；permission_condition 增加审核状态；domain_relation_config 简化为仅 ROLE_RESOURCE；资源树继承改为接口参数控制
- 下一阶段优先执行：Phase 6.1 落实完整 17 张表 DDL 与实体对齐，Phase 6.2 持久化替换

当前阻塞：

- 当前默认拓扑已完成收敛，历史模块目录仍保留在仓库中，但已降级为非默认资产，不再阻塞后续主线改造。
- 当前 Phase 1-4 仅交付混合内核骨架与占位实现，真实授权模型、接口路由映射、权限版本、缓存刷新、持久化与审计仍待在后续 Phase 落地。

最近验证：

- 2026-03-23：执行 `mvn -pl ruoyi-auth,ruoyi-gateway,ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InMemoryIdentityKernelServiceTest,InMemoryPermissionKernelServiceTest,PermissionRuleMatcherTest -Pdev`
- 结果：`BUILD SUCCESS`
- 明细：`InMemoryIdentityKernelServiceTest` 3/3 通过，`PermissionRuleMatcherTest` 2/2 通过，`InMemoryPermissionKernelServiceTest` 3/3 通过
- 2026-03-23：模块裁剪后再次执行同一命令，结果仍为 `BUILD SUCCESS`
- 2026-03-25：执行 `java -version`，结果为 `OpenJDK 21.0.10`
- 2026-03-25：执行 `mvn -version`，结果为 `Apache Maven 3.9.9`
- 2026-03-25：再次执行 `mvn -pl ruoyi-auth,ruoyi-gateway,ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InMemoryIdentityKernelServiceTest,InMemoryPermissionKernelServiceTest,PermissionRuleMatcherTest -Pdev`
- 结果：`BUILD SUCCESS`
- 明细：`InMemoryIdentityKernelServiceTest` 3/3 通过，`PermissionRuleMatcherTest` 2/2 通过，`InMemoryPermissionKernelServiceTest` 3/3 通过
- 2026-03-25：执行 `mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PermissionVersionServiceImplTest,HybridPermissionKernelServiceTest -Pdev`
- 结果：`BUILD SUCCESS`
- 明细：`HybridPermissionKernelServiceTest` 2/2 通过，`PermissionVersionServiceImplTest` 3/3 通过
- 2026-03-25：执行 `mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ResourceApiMappingServiceImplTest -Pdev`
- 结果：`BUILD SUCCESS`
- 明细：`ResourceApiMappingServiceImplTest` 2/2 通过
- 2026-03-25：执行 `mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=HybridPermissionKernelServiceTest,DatabaseInterfacePermissionRuleQueryServiceTest -Pdev`
- 结果：`BUILD SUCCESS`
- 明细：`DatabaseInterfacePermissionRuleQueryServiceTest` 2/2 通过，`HybridPermissionKernelServiceTest` 5/5 通过

## Phase 0: 文档与契约冻结

- 固化混合内核架构文档。
- 定义共享 DTO 与接口分组。
- 明确 `identity-service` / `permission-center` / `gateway` 的边界，不再混用。

交付物：

- 架构文档
- 执行计划文档
- 共享 API 契约模块

## Phase 1: 共享契约层

状态：已完成

- 新增共享授权契约模块。
- 定义主体上下文、委托上下文、能力定义、快照、数据范围描述、版本信息。
- 定义 `gateway`、`identity-service`、`permission-center` 间使用的基础请求与响应对象。

交付物：

- `ruoyi-api-*` 新模块
- 基础 DTO
- 已落地：`ruoyi-api-auth`

## Phase 2: identity-service 骨架

状态：已完成

- 将现有认证启动模块逐步重塑为 `identity-service`。
- 保留原有认证链路可兼容运行。
- 新增面向未来架构的认证、委托、主体查询接口骨架。

交付物：

- identity-service 入口控制器
- 基础服务接口与占位实现
- 已落地：`/api/identity/auth/*`、`/api/identity/delegation/*`、`/api/identity/subjects/*`

## Phase 3: permission-center 骨架

状态：已完成

- 新增目录注册、扩展能力清单注册、接口判定、快照查询、数据范围描述查询、版本查询接口骨架。
- 使用内存级占位实现承接第一批契约，后续再替换为持久化实现。

交付物：

- catalog / decision / version 接口骨架
- 基础注册与查询服务
- 已落地：`/api/perm/catalog/*`、`/api/perm/policy/*`、`/api/perm/decision/*`、`/api/perm/version/*`

## Phase 4: gateway 接口鉴权入口

状态：已完成

- 新增可开关的接口权限鉴权入口。
- 定义权限快照拉取服务、请求上下文解析器、接口匹配器。
- 默认关闭，避免影响现有网关行为。

交付物：

- 鉴权配置项
- 网关鉴权扩展点
- 已落地：`gateway.authz.*` 配置、主体解析器、快照客户端占位、规则匹配器、过滤器接入点

## Phase 5: 模块收敛与冗余移除

状态：已完成

目标：

- 将运行时拓扑收敛为 `ruoyi-gateway`、`ruoyi-auth`、`ruoyi-modules/ruoyi-permission-center` 三个核心启动项目。
- 保留 `ruoyi-auth` 作为长期存在的 `identity-service`，只裁剪非核心业务模块与附属模块。

完成标准：

- 根工程默认只围绕 `gateway`、`identity-service`、`permission-center` 构建和运行。
- `ruoyi-auth` 明确保留为长期存在的 `identity-service`。
- 文档、配置、脚本与聚合关系全部反映三核心启动项目拓扑。

## Phase 6: 真正业务能力

状态：进行中

原则：

- 原权限表结构仍是主事实层，不再新建一套 kernel 主存储模型。
- kernel 相关 DTO 与接口只作为运行时查询/消费包装层。
- 完整 17 张表：含 type_definition、operation_permission（resource_type + BIGINT 位运算）、permission_condition（预设+自定义审核）、resource_dependency（声明式）、permission_conflict_rule（查询时检测失效）、domain_relation_config（仅 ROLE_RESOURCE）、domain_scope_binding。
- `gateway` 不直接查库，只消费 `permission-center` 组装后的快照/判定/版本接口。

### Phase 6.1: 权限事实层落库

状态：部分完成

- 以 `plan/permission_center_schema.sql` 为准，固化完整 17 张表 DDL。
- 已完成 `permission_version` 和 `resource_api_mapping` 的完整链路。
- 输出迁移脚本、实体、Mapper、基础仓储。

当前进展：

- 已完成 `permission_version`：DDL、实体、Mapper、Mapper XML、服务、kernel 版本查询适配层、定向测试。
- 已完成 `resource_api_mapping`：DDL、实体、Mapper、Mapper XML、基础查询服务、定向测试。
- 已完成：将 `resource_api_mapping` 正式接入接口权限快照组装链。

待完成（须与新 DDL 对齐）：

- `type_definition` 实体与 Mapper（原 system_config 改名，config_key → type_key）。
- `operation_permission` 实体更新（biz_domain_id → resource_type，binary_bit/inherit_mask 改 BIGINT）。
- `permission_condition` 实体与 Mapper（新增 condition_source、status、reviewed_by、reviewed_at）。
- `role_resource_permission` 实体还原 condition_id 字段。
- `domain_relation_config` 实体与 Mapper（仅 ROLE_RESOURCE，移除 default_condition_id）。
- `domain_scope_binding` 实体与 Mapper。
- `resource_dependency` 实体与 Mapper。
- `permission_conflict_rule` 实体与 Mapper。

### Phase 6.2: permission-center 持久化替换

状态：部分完成

- 用数据库实现替换当前内存级 `policy` / `decision` / `version` 占位服务。
- 从 `abstract_user`、`user_role`、`abstract_role`、`role_resource_permission`、`resource_entity`、`operation_permission` 组装接口权限快照。
- 基于 `resource_api_mapping` 输出 `gateway` 可直接消费的接口快照。
- 快照组装时执行 `permission_conflict_rule` 冲突检测，冲突权限排除并异步通知。
- 资源树继承由查询接口 `inherit_mode` 参数控制。
- 操作继承通过 `binary_bit | inherit_mask`（BIGINT）位运算表达。

当前进展：

- 已完成：`PermissionKernelSnapshotMapper` 以原表 Join 方式查询。
- 已完成：`DatabaseInterfacePermissionRuleQueryService` 组装 `InterfacePermissionRule`。
- 已完成：`HybridPermissionKernelService` 的接口快照与判定已走持久化查询。
- 当前边界：`principalContext.subjectId` 按 `abstract_user_id` 解释；`condition_id != null` 的授权项暂不下发到接口快照。

尚未完成：

- `queryDataScopes` / `queryCustomScopes` 持久化替换。
- 冲突检测集成到快照组装链。
- 条件校验集成（PRESET handler + CUSTOM 表达式引擎）。
- 资源依赖查询接口。
- `inherit_mode` 参数支持。

### Phase 6.3: identity-service 接入抽象用户与版本

状态：未开始

- 将登录主体映射到 `permission-center.abstract_user`。
- 令牌签发前查询 `permissionVersion` 并写入主体上下文。
- 保留原认证主线，逐步替换当前内存身份/权限占位依赖。

### Phase 6.4: gateway 接口权限真实接入

状态：未开始

- 对接 `permission-center` 的真实快照接口与版本查询接口。
- 用 `service_code + http_method + path_pattern` 做接口匹配。
- 完成本地快照缓存、版本刷新、接口拦截闭环。

## Phase 7: 标准数据范围与扩展机制

状态：未开始

- 实现标准数据范围描述。
- 发布业务侧 SDK。
- 实现自定义能力声明注册与本地 `CustomPermissionHandler` 模式。

## Phase 8: 治理与优化

状态：未开始

- 版本拉取优化。
- MQ 失效通知预留。
- 审计与告警。
- 接入规范、示例项目与管理端配套。

## 每轮实施约定

- 每次优先完成一个 Phase 或一个可闭环子任务。
- 运行时目标固定为三个核心启动项目：`gateway + identity-service + permission-center`。
- 优先删除或下线冗余模块，再补充新能力，避免在待废弃模块上继续投入。
- 新增接口统一使用 `POST + JSON`。
- 复杂权限逻辑默认落在业务服务侧，不向中心反向侵入业务领域。
