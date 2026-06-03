# AccessMesh 模块与接口逐轮核对（Round 1 - Round 7）及问题诊断表

> 本文档用于沉淀当前阶段对 AccessMesh 模块边界、接口组织方式、前后端映射关系、权限主链以及补充能力层的核对结果。
>
> 本文档是“现状分析 + 问题诊断 + 收口方向”文档，不替代 `docs/design/` 下的正式设计文档，也不直接作为接口实现规范使用。

## 1. 文档目的

本轮核对的出发点不是简单罗列接口，而是先回答三个更基础的问题：

1. 项目当前到底由哪些模块构成，每个模块的职责边界是什么。
2. 现有设计与实现之间，最关键的断层出现在什么位置。
3. 如果要把系统拉回到一条可落地的主线，先收哪几条链，哪些能力后置建设。

因此，本文档同时保留四类信息：

- 文档层事实：设计文档声称系统应如何工作。
- 实现层事实：当前代码里已经实现了什么。
- 前端消费层事实：前端当前实际依赖了什么。
- 阶段性判断：哪些问题已经明确不合理，哪些能力已达成共识，哪些能力应延后。

## 2. 适用范围与阅读方式

### 2.1 适用范围

本文档覆盖以下范围：

- `gateway`
- `admin-service`
- `permission-center`
- `frontend`
- `perm-common`
- `perm-client-spring-boot-starter`
- `perm-entity`
- `perm-data-spring-boot-starter`
- `perm-gateway-spring-boot-starter`
- `example-service`

本文档不替代以下正式文档：

- 微服务整体架构以 [docs/design/architecture.md](../../design/architecture.md) 为准。
- 项目工程规范以 [docs/design/project-rules.md](../../design/project-rules.md) 为准。
- 权限中心概念、API、核心调用链路与实现设计分别以 [docs/design/permission-center/overview.md](../../design/permission-center/overview.md)、[docs/design/permission-center/api-contract.md](../../design/permission-center/api-contract.md)、[docs/design/permission-center/core-flows.md](../../design/permission-center/core-flows.md)、[docs/design/permission-center/implementation.md](../../design/permission-center/implementation.md) 为准。

### 2.2 阅读方式

推荐按以下顺序阅读：

1. 先看“全局结论”，理解当前最核心的问题并不是某个单接口，而是三条主线没有完全收口。
2. 再看 Round 1 到 Round 7，理解各模块和功能域为什么会出现断层。
3. 最后看“问题诊断表”和“建议实施顺序”，把分析转成后续实现优先级。

## 3. 全局结论

当前最核心的问题，不是模块数量多，也不是接口数量多，而是三条主线没有完全收口：

1. 身份主线没有收口。
   - 登录成功之后，身份如何稳定穿透到 Gateway 与后端服务的运行时鉴权链，当前存在高风险断层。
   - 前端当前的登录、刷新、动态路由加载仍保留模板契约，而后端真实链路已经演进为另一套模型。

2. 授权主线没有收口。
   - `permission-center` 已经具备相对完整的权限事实模型，但用户组织关系如何映射为真实角色关系、资源依赖如何自动补权，仍存在关键缺口。
   - 这意味着“权限模型看上去完整”并不等于“主业务链已经能跑通”。

3. 前端消费主线没有收口。
   - 前端仍带有模板时代的 `auths`、`meta.roles`、mock async-routes 和本地 permissions 混合逻辑。
   - 后端则已经朝“admin-service 聚合返回 menus + roles + permissions，permission-center 提供统一权限事实”方向演进。

如果这三条主线不先收口，后续即使继续补页面、补接口、补 SDK，也会建立在不稳定的基础之上。

## 4. Round 1：认证与会话

### 4.1 核对目标

这一轮要确认的不是“登录接口能不能调通”，而是整个认证与会话链路是否形成闭环：

- 前端到底调用谁登录。
- 登录返回什么最小信息。
- 登录后用户信息和菜单由谁提供。
- Gateway 如何识别身份并继续完成接口鉴权。

### 4.2 涉及模块

- `frontend`
- `admin-service`
- `gateway`
- `permission-center`

### 4.3 涉及前端入口

- [frontend/src/api/user.ts](../../frontend/src/api/user.ts)
- [frontend/src/api/routes.ts](../../frontend/src/api/routes.ts)
- [frontend/src/store/modules/user.ts](../../frontend/src/store/modules/user.ts)
- [frontend/src/utils/http/index.ts](../../frontend/src/utils/http/index.ts)
- [frontend/src/router/index.ts](../../frontend/src/router/index.ts)

### 4.4 涉及后端入口

- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/AuthController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/AuthController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OAuth2Controller.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OAuth2Controller.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/impl/AuthServiceImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/impl/AuthServiceImpl.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/dto/auth/LoginResp.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/dto/auth/LoginResp.java)
- [gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/AuthTokenFilter.java](../../gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/AuthTokenFilter.java)

### 4.5 已确认事实

1. 后端认证入口已经明确在 `admin-service`，而不是 `permission-center`。
2. 后端真实登录响应模型是 `accessToken`、`refreshToken`、`expiresIn`、`tokenType`、`userId`、`username`、`tenantId`、`forceResetPwd` 等最小身份与认证结果，并不直接返回前端模板期望的 `roles`、`permissions`、完整路由信息。
3. 前端当前仍存在模板时代的接口假设：
   - 登录接口按 `/login` 使用。
   - 刷新接口按 `/refresh-token` 使用。
   - 动态路由按 `/get-async-routes` 获取。
4. 后端真实菜单聚合入口已经存在于 `/auth/user-menu`，说明后端已经具备向前端聚合返回菜单、角色、权限的方向。
5. `AuthServiceImpl` 已经通过调用 permission-center 的能力获取 `roles` 和 `permissions`，这说明角色与权限事实并不是计划中才有，而是已经在服务端聚合逻辑中开始使用。

### 4.6 结构性问题

这一轮暴露出三个关键断层：

1. 前后端认证契约断层。
   - 前端还依赖模板接口和模板返回结构。
   - 后端真实契约已经演进为“最小登录返回 + 登录后拉聚合信息”。

2. 菜单加载入口断层。
   - 前端当前还将动态路由视为独立 mock 能力。
   - 后端已经开始提供 `user-menu` 聚合返回。

3. 登录态穿透链断层。
   - `admin-service` 当前登录过程对身份信息的写入方式，与 Gateway 在运行时读取身份信息的方式存在高风险不一致。
   - 这是当前最危险的问题之一，因为它会造成“前端登录成功，但 Gateway 无法稳定识别租户、主体类型、用户名”等运行时现象。

### 4.7 目标模型

Round 1 的目标模型已经在讨论中明确：

1. 前端统一调 `admin-service` 登录入口。
2. 登录只返回 token 与最小身份信息。
3. 登录成功后，前端立刻再调 `userinfo` 和 `user-menu`。
4. 前端不直连 `permission-center`。
5. Gateway 统一完成身份头注入与接口鉴权。
6. `permission-center` 保持权限事实源定位，不直接承担前端聚合入口。

### 4.8 待后续落地项

- 统一登录态写入与 Gateway 读取模型。
- 把前端模板接口彻底替换为真实认证契约。
- 明确刷新 token 的真实后端入口和前端接入方式。
- 明确 `user-menu` 的最终响应模型，作为前端菜单和权限消费的基础契约。

## 5. Round 2：主体与组织

### 5.1 核对目标

这一轮要回答的是：用户、组织、角色、用户角色关系，到底由谁主维护；组织树与角色容器之间，最终采用什么关系模型；admin-service 与 permission-center 如何分工。

### 5.2 涉及模块

- `admin-service`
- `permission-center`
- `frontend`

### 5.3 涉及前端入口

这一轮前端并没有完全成型的真实对接入口，但后续会直接影响：

- 用户管理页面
- 组织管理页面
- 角色管理页面
- 用户与组织关系配置页面

### 5.4 涉及后端入口

- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OrgController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OrgController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/RoleController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/RoleController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserOrgController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserOrgController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/RoleProxyService.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/RoleProxyService.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/impl/RoleProxyServiceImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/impl/RoleProxyServiceImpl.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/UserSyncHandlerImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/UserSyncHandlerImpl.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/OrgSyncHandlerImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/OrgSyncHandlerImpl.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/RoleController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/RoleController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserRoleController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserRoleController.java)

### 5.5 已确认事实

1. 用户和组织的主事实源当前在 `admin-service`。
2. `permission-center` 中的抽象用户更像权限镜像与查询对象，而不是业务主事实源。
3. 用户同步链已经存在，而且方向明确为 `admin-service -> permission-center`。
4. 组织当前只同步为 `permission-center` 的 ORG 资源，而不是自动创建角色。
5. 角色与用户角色关系的事实源在 `permission-center`。
6. `admin-service` 当前的角色能力只是较薄的一层代理，尚不足以承接未来前端所有角色管理诉求。
7. `UserOrgController` 已经管理用户与组织关系，但“用户加入组织后怎样自然形成权限事实”还没有真正闭环到 user-role。

### 5.6 结构性问题

1. 用户组织关系与权限角色关系尚未闭环。
   - 如果用户属于组织，但没有自动稳定映射到 permission-center 的 user-role，那么组织树、岗位、默认角色这些设计就无法形成真实授权结果。

2. 组织目前只有资源语义，没有完整角色容器语义。
   - 当前同步实现说明组织可以作为 ORG 资源存在。
   - 但若目标是“组织既是业务树，也是角色容器”，则仅同步资源还不够。

3. admin-service 代理面太薄。
   - 既然前端已确定不直连 permission-center，那么 admin-service 迟早要承担完整角色管理聚合层职责。
   - 当前能力面明显不够，后续会成为前端真实对接时的能力缺口。

### 5.7 目标模型

Round 2 已经明确的目标模型包括：

1. admin-service 继续作为用户和组织的主事实源。
2. permission-center 继续作为角色和用户角色关系的权限事实源。
3. 组织在权限模型中既是业务树节点，也是角色容器。
4. 用户加入组织后应自动获得组织默认角色或岗位映射角色。
5. 前端侧统一通过 admin-service 管理角色能力，而不直接管理 permission-center 的底层细节。

### 5.8 待后续落地项

- 明确 user-org 到 user-role 的映射规则与事务边界。
- 为组织角色、岗位角色、通用角色设计统一代理能力。
- 补齐 admin-service 对角色树、角色授权、角色查询、角色分配的完整聚合代理面。

## 6. Round 3：资源与菜单

### 6.1 核对目标

这一轮的核心问题是：菜单、按钮、接口、业务路由，到底是几套模型，还是一套统一 Resource 模型；前端动态路由是由模板 mock 驱动，还是由后端真实下发。

### 6.2 涉及模块

- `frontend`
- `admin-service`
- `permission-center`

### 6.3 涉及前端入口

- [frontend/src/api/routes.ts](../../frontend/src/api/routes.ts)
- [frontend/src/router/utils.ts](../../frontend/src/router/utils.ts)
- [frontend/src/router/index.ts](../../frontend/src/router/index.ts)
- [frontend/mock/asyncRoutes.ts](../../frontend/mock/asyncRoutes.ts)

### 6.4 涉及后端入口

- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/MenuSyncHandlerImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/MenuSyncHandlerImpl.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/AuthController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/AuthController.java)
- [docs/design/permission-center/overview.md](../../design/permission-center/overview.md)

### 6.5 已确认事实

1. 前端当前动态路由仍依赖模板式 `/get-async-routes` 和 mock 数据。
2. 后端真实菜单聚合入口已经存在于 `/auth/user-menu`。
3. admin-service 中已经存在菜单同步到 permission-center 资源的实现。
4. 当前同步过程使用 `SysMenu.permCode` 作为资源编码，说明菜单和资源之间已经存在现实映射关系。
5. permission-center 的概念模型本身就希望把菜单、按钮、接口等统一放入 Resource 抽象内管理。

### 6.6 结构性问题

1. 前端路由来源仍停留在模板时代。
   - 这会导致前端长期维护一套脱离后端权限事实的路由系统。

2. 菜单、按钮、接口权限仍有继续分裂配置的风险。
   - 如果 Resource 模型没有真正统一收口，后续很容易形成“菜单一套、按钮一套、接口一套”的配置碎片。

3. admin-service 与 permission-center 的分工需要进一步收清。
   - admin-service 负责聚合返回给前端。
   - permission-center 负责维护统一资源与依赖事实。
   - 这两者不能再次重叠。

### 6.7 目标模型

Round 3 已明确的目标模型是：

1. 业务路由全部由后端下发。
2. admin-service 统一向前端返回完整业务菜单与权限结果。
3. permission-center 统一维护菜单、按钮、接口等资源及其依赖关系。
4. 菜单与接口权限通过 `resource_dependency` 自动补齐，而不是继续分裂配置。
5. 前端只消费 `menus + permission codes`，不再长期维护 template mock 路由体系。

### 6.8 待后续落地项

- 明确 `user-menu` 的最终树结构与前端路由转换规则。
- 明确菜单资源、按钮资源、接口资源的编码规范。
- 补齐菜单、按钮、接口之间的依赖配置与自动补权链路。

## 7. Round 4：授权与权限事实

### 7.1 核对目标

这一轮关注的是权限事实层本身是否完整：角色授权、条件权限、范围权限、资源依赖、冲突规则、权限版本这些能力现在处在什么状态，真正首期阻塞项又是什么。

### 7.2 涉及模块

- `permission-center`
- `admin-service`

### 7.3 涉及后端入口

- [docs/design/permission-center/overview.md](../../design/permission-center/overview.md)
- [docs/design/permission-center/core-flows.md](../../design/permission-center/core-flows.md)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/impl/PermissionGrantAppServiceImpl.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/impl/PermissionGrantAppServiceImpl.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/domain/impl/PermissionGrantDomainServiceImpl.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/domain/impl/PermissionGrantDomainServiceImpl.java)

### 7.4 已确认事实

1. 条件权限、范围权限、权限版本、资源依赖、冲突规则等概念模型已经在文档和代码中具备明确位置。
2. 角色授权写入链已经存在，版本递增和缓存失效机制也有雏形。
3. `resource_dependency` 已有表、配置模型和 `autoGrant` 语义。
4. 授权链中关于自动补齐依赖资源权限的逻辑仍未完整落地。
5. 角色互斥与资源加操作互斥已经讨论成双轨模型：前者写时处理，后者查询时处理。
6. 条件权限首期只需支持简单平铺条件，不应过早扩展复杂嵌套逻辑。

### 7.5 结构性问题

1. `resource_dependency` 自动补权是最关键的未闭环能力。
   - 这项能力如果不完成，统一资源模型将退化为人工分裂授权模型。

2. 写链与读链虽然都有基础，但还没有完全围绕同一个主线收口。
   - 这意味着看似拥有很多能力对象，实际落到业务链上仍可能出现配置与结果脱节。

3. 条件权限与范围权限如果边界不收敛，会挤占核心主线预算。
   - 当前已经明确首期应克制复杂度，这是正确方向。

### 7.6 目标模型

Round 4 的目标模型已经明确为：

1. 角色授权保存后即可通过 `resource_dependency` 自动补齐依赖资源权限。
2. 角色级互斥在写入时阻断。
3. 资源 + 操作级互斥在查询时判断与过滤。
4. 条件权限只承担轻量过滤职责，不抢占主权限模型复杂度预算。

### 7.7 待后续落地项

- 完成自动补权链路。
- 明确依赖补权与撤权的版本更新策略。
- 为冲突处理、依赖补权、条件权限增加更明确的外部行为验证。

## 8. Round 5：运行时鉴权

### 8.1 核对目标

这一轮关注的是运行时链条是否真的能承接前面几轮的设计结果：Gateway 是否能识别身份、透传上下文、做缓存、调 permission-center 做接口鉴权，permission-center 是否能稳定返回结果。

### 8.2 涉及模块

- `gateway`
- `permission-center`
- `admin-service`

### 8.3 涉及后端入口

- [gateway/src/main/resources/bootstrap.yml](../../gateway/src/main/resources/bootstrap.yml)
- [gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/AuthTokenFilter.java](../../gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/AuthTokenFilter.java)
- [gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/PermissionFilter.java](../../gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/PermissionFilter.java)
- [gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/HeaderEnrichFilter.java](../../gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/HeaderEnrichFilter.java)

### 8.4 已确认事实

1. Gateway 的主链已经存在：白名单、头清洗、Token 解析、L1 缓存、Header 注入、签名增强、调用 `check-interface`。
2. permission-center 侧也已有运行时权限查询与校验基础，包括 permissionVersion、query-resources、query-scopes、InterfaceSnapshot 等模型。
3. 当前 Gateway 的缓存更接近“短 TTL 容忍窗口”模式，而非强一致主动失效模式。
4. Gateway 已确认存在签名生成动作，但下游统一签名校验的落点尚未完全确认。

### 8.5 结构性问题

1. 登录态穿透链是当前最危险断层。
   - 它不只是登录接口的一个小问题，而是整个运行时鉴权链是否可靠的根基。

2. 缓存一致性策略目前仍停留在“方向明确、边界待收口”的状态。
   - 短 TTL 可以作为现实妥协，但需要清楚它与 permissionVersion 的关系。

3. 签名闭环尚未完全确认。
   - 如果 Gateway 只负责签名生成，而下游没有统一验签点，那么这一层设计就是不完整的。

### 8.6 目标模型

Round 5 的目标模型应当是：

1. 登录态信息从认证端到 Gateway 到服务端读取完全一致。
2. Gateway 作为统一运行时门禁，负责身份识别、头注入、调用 permission-center 做接口级鉴权。
3. permission-center 基于统一权限事实与版本模型提供稳定鉴权结果。
4. 缓存一致性策略有明确解释，不再处于“实现上有，但语义上说不清”的状态。

### 8.7 待后续落地项

- 统一身份载体与读取方式。
- 明确签名校验落点。
- 给缓存策略写出正式运行时语义说明。

## 9. Round 6：前端权限呈现

### 9.1 核对目标

这一轮要确认的是：前端到底保留几套权限呈现模型，页面、菜单、按钮权限分别从哪里来，当前模板遗留和目标架构之间的差距有多大。

### 9.2 涉及模块

- `frontend`
- `admin-service`
- `permission-center`

### 9.3 涉及前端入口

- [frontend/src/components/ReAuth/src/auth.tsx](../../frontend/src/components/ReAuth/src/auth.tsx)
- [frontend/src/components/RePerms/src/perms.tsx](../../frontend/src/components/RePerms/src/perms.tsx)
- [frontend/src/directives/auth/index.ts](../../frontend/src/directives/auth/index.ts)
- [frontend/src/directives/perms/index.ts](../../frontend/src/directives/perms/index.ts)
- [frontend/src/router/index.ts](../../frontend/src/router/index.ts)
- [frontend/src/router/utils.ts](../../frontend/src/router/utils.ts)
- [frontend/src/store/modules/user.ts](../../frontend/src/store/modules/user.ts)
- [frontend/src/store/modules/permission.ts](../../frontend/src/store/modules/permission.ts)
- [frontend/src/views/permission/button/index.vue](../../frontend/src/views/permission/button/index.vue)
- [frontend/src/views/permission/button/perms.vue](../../frontend/src/views/permission/button/perms.vue)

### 9.4 已确认事实

1. 前端当前存在两套按钮权限体系并行：
   - `Auth / v-auth / hasAuth` 走路由侧 `auths` 逻辑。
   - `Perms / v-perms / hasPerms` 走用户 `permissions` 逻辑。
2. 页面和菜单权限当前大量依赖 `meta.roles` 加前端过滤。
3. `usePermissionStore` 实际上更像菜单和路由 store，而不是权限事实 store。
4. 权限事实散落在 user store、本地存储、Cookie 等多个位置。
5. 当前权限演示页面本质上是在同时展示两套模板时代模型。

### 9.5 结构性问题

1. 双轨并行使前端权限语义无法收口。
2. store 命名与职责不一致，容易误导后续开发。
3. 只要前端还长期依赖 `meta.roles` 和 `auths` 模型，就无法真正转向统一后端下发权限结果。

### 9.6 目标模型

Round 6 的目标模型已经明确：

1. admin-service 通过统一聚合接口返回 `menus + roles + permissions`。
2. 前端只保留一套本地权限呈现模型。
3. 页面和菜单主要依赖后端下发路由与最小本地校验。
4. 按钮统一依赖稳定的 `permissions` 权限码。
5. 现有基于 `auths` 的模板体系逐步退场。

### 9.7 待后续落地项

- 重构前端权限 store 边界与命名。
- 清理模板 mock 契约和第二套权限逻辑。
- 让前端权限模型真正收敛到后端聚合返回结果。

## 10. Round 7：补充域与演示层

### 10.1 核对目标

这一轮不是为了继续展开主链，而是判断哪些模块属于“核心主线必须保留”，哪些属于“后台支撑但首期收敛”，哪些属于“后置建设的对外交付层”。

### 10.2 涉及模块

- `permission-center` 的日志审计、类型定义、业务域、系统配置
- `example-service`
- `perm-common`
- `perm-client-spring-boot-starter`
- `perm-entity`
- `perm-data-spring-boot-starter`
- `perm-gateway-spring-boot-starter`

### 10.3 已确认事实

1. 日志审计与类型定义都属于应长期保留的后台支撑能力，成熟度也相对较高。
2. 业务域和系统配置都应保留，但不应在首期无限扩张。
3. 业务域的职责边界已明确：只做角色和权限分类，让不同业务管理员聚焦各自负责的管理视角，降低管理复杂度。
4. 业务域不承担数据权限、运行时鉴权主链、资源树归属重构等超出“管理分类”的职责。
5. example-service 的目标定位已被重新明确：它不是长期 skeleton，而是核心主线完成后的真实接入示例。
6. SDK 的最终交付目标也已明确：
   - Spring Boot 项目提供 starter / 自动配置式 SDK。
   - 普通 Java 项目提供轻量 client SDK。
   - 其他语言通过稳定接口契约和接入文档对接。
7. `perm-common`、`perm-client-spring-boot-starter`、`perm-entity` 属于生产主线基础模块。
8. 部分 starter 当前实现度偏低，需要在未来结合最终 SDK 方案重新评估保留、合并还是重写。

### 10.4 结构性问题

1. 辅助能力存在扩张风险。
   - 如果业务域、系统配置、演示层、starter 在首期过度展开，会直接侵蚀核心主线预算。

2. example-service 与 SDK 层的定位此前不够清楚。
   - 它们很重要，但不应抢在主线之前建设。

3. starter 模块存在“名字已经很完整，能力却未完全落地”的误导风险。

### 10.5 目标模型

Round 7 已收敛出的整体分层为：

1. 核心主线优先完成：`gateway`、`admin-service`、`permission-center`、`perm-common`、`perm-client-spring-boot-starter`、`perm-entity`。
2. 后台管理支撑首期收敛：日志审计、类型定义、业务域分类、必要系统配置。
3. 核心完成后补齐的真实接入层：`example-service`、Spring Boot SDK、Java SDK、对外接口文档。
4. 待评估重构层：当前实现度不足的 starter，根据最终 SDK 方案决定去留。

### 10.6 待后续落地项

- 定义 example-service 的真实接入示例范围。
- 在核心契约稳定后设计三层 SDK 交付方案。
- 对现有 starter 做去留评估。

## 11. 问题诊断表

### 11.1 总表

| 档位            | 主题                             | 核心诊断                                                                                                                  | 不处理的直接后果                                                         | 建议处理                                                         |
| --------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------- |
| P0 必须先改     | 登录态穿透链路                   | admin-service 当前登录态写入方式与 Gateway 读取身份方式存在高概率不一致，认证成功后未必能稳定穿透到运行时鉴权链。         | 出现“能登录但接口鉴权异常”或租户、主体类型丢失，整条权限链不可稳定验证。 | 先统一登录态载体与 Gateway 身份读取约定。                        |
| P0 必须先改     | 前后端认证契约断层               | 前端仍基于模板接口与 mock 契约组织登录、刷新、动态路由；后端真实入口已经是 login + userinfo + user-menu 聚合链。          | 后续菜单、角色、按钮权限都会继续建立在错误契约上。                       | 冻结目标契约：最小登录返回，随后拉 userinfo 和 user-menu。       |
| P0 必须先改     | 前端权限模型双轨并行             | 当前同时存在 auths 路由权限体系与 permissions 权限码体系，且权限事实散落在多个 store 和本地缓存位置。                     | 页面、菜单、按钮的权限语义持续打架，前后端映射无法收口。                 | 保留 permissions 单轨，逐步淘汰 auths 模板链。                   |
| P0 必须先改     | 用户-组织-角色闭环未落地         | 用户、组织由 admin-service 主维护已基本明确，但 user-org 如何稳定映射为 permission-center 的 user-role 事实仍未真正闭环。 | 组织树、岗位、角色容器这些设计即使定了，也无法形成可运行的真实授权关系。 | 把“用户加入组织自动获得组织角色或岗位角色”落成主链能力。         |
| P0 必须先改     | resource_dependency 自动补权缺口 | 资源依赖模型已经设计且被确认为首期必需，但授权保存链仍存在未完成点。                                                      | 菜单、按钮、接口之间继续靠人工分裂配置，权限模型重新碎裂。               | 作为授权主链首期必做项，与角色授权保存链一起收口。               |
| P1 第二阶段补齐 | admin-service 聚合代理能力偏薄   | 已确定前端不直连 permission-center，但 admin-service 当前对角色、授权、查询的代理面仍偏薄，尚不足以成为唯一前端后端入口。 | 前端一旦开始真实对接，会发现聚合层能力不完整。                           | 在主链打通后，补齐角色管理、授权管理、用户角色分配等统一代理面。 |
| P1 第二阶段补齐 | 运行时一致性细节未完全闭合       | Gateway L1 缓存、permissionVersion、签名生成已有方向，但签名校验落点和更强一致性策略尚未完全确认。                        | 运行时链路可用但边界不够稳，后续容易在缓存窗口和跨服务验签处出现灰区。   | 先保证主链可用，再补齐签名验证闭环和缓存一致性策略说明。         |
| P1 第二阶段补齐 | 业务域与系统配置存在扩张风险     | 业务域已明确只是管理分类能力，系统配置也应仅保留必要范围；两者若不控边界，都会侵蚀主线预算。                              | 容易把“降低管理复杂度”的辅助能力做成新的复杂平台。                       | 业务域只做角色和权限分类视图；系统配置只保留首期必需键集合。     |
| P2 后期建设     | 真实接入示例尚未进入建设期       | example-service 应做成真实接入示例，但当前阶段不应抢主线资源。                                                            | 过早做会分散核心链路收口；长期不做则外部接入说服力不足。                 | 待核心主线稳定后，再把 example-service 补成真实接入样板。        |
| P2 后期建设     | SDK 交付层尚未展开               | 外部系统接入最终需要 Spring Boot starter、普通 Java SDK、其他语言接口文档三层交付，但这不是当前主线阻塞项。               | 现在展开会倒逼主线过早固化；长期不做则外部接入成本高。                   | 作为核心完成后的正式交付层建设，基于稳定契约再设计。             |
| P2 后期建设     | starter 模块定位待重构           | 当前部分 starter 实现度偏低，是否保留、合并还是按最终 SDK 形态重写，取决于主线稳定后的接入策略。                          | 继续维持半成品状态会误导后续开发者，以为已有可复用接入能力。             | 暂时标记为待评估层，等 SDK 方案确定后统一裁决。                  |

### 11.2 P0 详细说明

#### 11.2.1 登录态穿透链路

这是当前最危险的问题，因为它不是单纯的接口命名不一致，而是整个认证结果能否进入运行时鉴权链的问题。

如果登录态信息的写入方式与 Gateway 的读取方式不一致，系统就会出现一种很难排查的假象：

- 前端看到登录成功。
- token 也已经拿到了。
- 但进入 Gateway 后，租户、主体类型、用户名等上下文拿不全，导致接口鉴权表现异常。

这项问题直接影响“身份主线”，因此必须优先处理。

#### 11.2.2 前后端认证契约断层

当前前端仍携带模板时代的登录、刷新、动态路由加载模式，而后端已经朝新的聚合链路演进。如果不先统一契约，后续所有前端改造都会持续建立在旧模型上。

这项问题同样直接影响“身份主线”和“前端消费主线”。

#### 11.2.3 前端权限模型双轨并行

现在前端同时保留 `auths` 和 `permissions` 两套逻辑，本质上相当于维护两套权限世界观。这会使页面、菜单、按钮三个层面的权限语义长期不一致。

这项问题直接影响“前端消费主线”。

#### 11.2.4 用户-组织-角色闭环未落地

主体模型如果不能把用户组织关系稳定映射成角色关系，那么组织作为角色容器、岗位映射角色、组织默认角色等设计全部无法真正落地。

这项问题直接影响“授权主线”。

#### 11.2.5 resource_dependency 自动补权缺口

统一资源模型的真正价值，不在于概念上把菜单、按钮、接口都叫 Resource，而在于授权时能自动补齐必要依赖。只要这项能力不落地，系统最终还是会退化成人工拆分授权。

这项问题同样直接影响“授权主线”。

### 11.3 P1 详细说明

P1 代表“第二阶段必须补齐，但不是当前最先阻塞主链的项”。

1. admin-service 聚合代理能力偏薄。
   - 这是目标架构落地过程中的必补层。
   - 但它依赖 P0 先收主线，否则补出来的代理层也会继续建立在不稳定契约上。

2. 运行时一致性细节未完全闭合。
   - 当前主链方向是对的，但缓存、签名、强一致性边界还不够完整。
   - 这些应在主链打通后继续收口。

3. 业务域与系统配置存在扩张风险。
   - 它们是重要能力，但不该成为首期主线的复杂度黑洞。

### 11.4 P2 详细说明

P2 不是“不重要”，而是“必须等核心契约稳定后再建设”的内容。

1. example-service 作为真实接入示例很重要，但它是主线稳定后的对外交付样板。
2. SDK 三层交付同样很重要，但应建立在稳定接口契约之上，而不是反过来牵引主线频繁变动。
3. starter 模块的去留和重构策略，需要等 SDK 总体形态稳定后再判断。

## 12. 建议实施顺序

结合 Round 1 到 Round 7 的核对结果，推荐按以下顺序推进：

1. 先修身份主线。
   - 统一登录态载体。
   - 固化 `login -> userinfo -> user-menu` 契约。
   - 让前端正式切到真实认证链。

2. 再修授权主线。
   - 完成 user-org 到 user-role 映射。
   - 完成 `resource_dependency` 自动补权。
   - 扩展 admin-service 的聚合代理能力。

3. 然后收前端消费主线。
   - 让前端只保留 `menus + roles + permissions` 一套模型。
   - 清理模板式 `auths`、`meta.roles`、mock async-routes 依赖。

4. 最后再做后台支撑收敛与对外交付层。
   - 收敛业务域和系统配置边界。
   - 建设真实接入示例。
   - 设计并交付三层 SDK。

## 13. 已确认决策与边界

以下结论已经在当前阶段讨论中明确，不应在没有新证据的情况下反复回滚：

1. 前端不直连 `permission-center`，由 `admin-service` 作为前端唯一后端聚合入口。
2. 登录采用最小返回，登录后再获取 `userinfo` 与 `user-menu`。
3. 业务路由统一由后端下发。
4. 按钮权限最终以稳定 `permissions` 权限码呈现。
5. `resource_dependency` 自动补权属于首期必须落地能力。
6. 组织在权限模型中既是业务树，也是角色容器。
7. 业务域只做角色和权限分类管理视角隔离，不承担数据权限与运行时鉴权主链职责。
8. example-service 需要在核心完成后做成真实接入示例，而不是长期停留在演示骨架定位。
9. 外部系统接入最终需要三层交付：Spring Boot starter、普通 Java SDK、其他语言接口文档。

## 14. 结语

这份文档的核心价值，不在于把所有模块都评价一遍，而在于把当前阶段最关键的结构性判断固定下来：

- AccessMesh 不是缺少功能点，而是需要先把三条主线收口。
- 当前最危险的问题不是某个页面没做完，而是身份主线和授权主线还存在关键断层。
- 一旦先把 P0 主线问题收口，后续前端改造、后台支撑补齐、真实接入示例与 SDK 交付都会变得清晰得多。

因此，这份文档既是当前阶段的总结，也是后续继续推进接口核对、模块重整和目标蓝图设计的基础输入。# AccessMesh 模块与接口逐轮核对（Round 1 - Round 7）及问题诊断表

> 本文档用于记录当前阶段对 AccessMesh 各模块、接口主链和前后端映射的逐轮核对结果，以及基于这些核对形成的问题诊断表。
>
> 本文档是“现状分析与阶段性收口”文档，不替代 `docs/design/` 下的正式设计文档。实现时若与设计文档冲突，以 `docs/design/` 下的权威文档为准。

## 1. 文档目的

本轮梳理的直接背景是：项目后端已经实现了较多内容，但整体上仍存在三个明显的不满意点。

1. 模块划分不清。
2. 主业务链路不清。
3. 前后端映射混乱。

因此，这份文档不以“把所有接口罗列一遍”为目标，而是先回答三个更基础的问题：

1. 当前各核心对象到底由谁主维护。
2. 当前身份、授权、前端消费三条主线是否已经闭环。
3. 哪些问题必须先处理，哪些问题应该后置处理。

## 2. 适用范围

本文档覆盖以下范围：

- `gateway`
- `admin-service`
- `permission-center`
- `perm-common`
- `perm-client-spring-boot-starter`
- `perm-entity`
- `frontend`
- `example-service`
- 与上述模块直接相关的 `docs/design/` 权威设计文档和部分归档文档

本文档不替代单接口级核对，也不替代数据库表结构或正式 API 契约。它的作用是把“当前现状是什么、问题在哪里、目标模型是什么、下一步怎么排优先级”系统化记录下来。

## 3. 阅读方式

建议按以下顺序阅读：

1. 先看“全局结论”，理解当前最核心的问题不是单点 bug，而是三条主线没有完全收口。
2. 再看“模块边界总览”，理解用户、组织、角色、资源、权限关系分别由谁主维护。
3. 然后按 Round 1 到 Round 7 阅读各功能域的详细核对结果。
4. 最后查看“问题诊断表”和“建议实施顺序”，把现状问题转成实际执行优先级。

## 4. 全局结论

经过 Round 1 到 Round 7 的逐轮核对，可以把当前结构性问题压缩成三条主线：

### 4.1 身份主线没有完全收口

目标模型应该是：

`frontend -> admin-service /auth/login -> /auth/userinfo -> /auth/user-menu -> Gateway 注入身份头 -> permission-center 参与运行时鉴权`

但当前实现里，前端仍保留模板式登录、刷新和动态路由契约，admin-service 的真实认证接口和 Gateway 的身份读取约定也存在潜在断层。结果是“登录能不能稳定穿透到运行时鉴权链”仍然是当前最危险的基础风险。

### 4.2 授权主线没有完全收口

目标模型应该是：

`admin-service 维护用户/组织/菜单事实 -> permission-center 维护角色、资源、授权和运行时判定事实 -> user-org 能稳定映射到 user-role -> role grant 能通过 resource_dependency 自动补齐依赖资源权限`

但当前 user-org 到 user-role 的闭环仍未明确落地，`resource_dependency` 虽然已设计且被确认是首期必需，但授权保存链仍存在未完成点。这意味着角色、菜单、按钮、接口权限之间还不能形成稳定的一条主线。

### 4.3 前端消费主线没有完全收口

目标模型应该是：

`admin-service 统一向前端下发 menus + roles + permissions -> 前端只保留一套权限呈现逻辑 -> 页面/菜单依赖后端路由结果，按钮依赖稳定权限码`

但当前前端仍存在两套权限体系并行：

- 一套是 `auths` / `hasAuth` / `v-auth` 的模板逻辑。
- 一套是 `permissions` / `hasPerms` / `v-perms` 的权限码逻辑。

同时，前端仍有大量逻辑建立在 mock 路由和本地缓存权限事实上，导致前后端映射长期无法收口。

## 5. 模块边界总览

### 5.1 核心对象事实源总览

| 对象               | 当前主事实源             | 当前镜像或辅助方                 | 说明                                                                                          |
| ------------------ | ------------------------ | -------------------------------- | --------------------------------------------------------------------------------------------- |
| 用户               | `admin-service`          | `permission-center` 抽象用户     | 用户 CRUD 明确由 admin-service 主维护，permission-center 更多承担权限查询和镜像角色           |
| 组织               | `admin-service`          | `permission-center` ORG 资源镜像 | 当前组织同步为 ORG 资源，但尚未天然闭合为角色容器                                             |
| 角色               | `permission-center`      | `admin-service` 代理层           | admin-service 当前角色接口偏薄，更像代理入口；真正的角色和 user-role 事实在 permission-center |
| 菜单               | `admin-service`          | `permission-center` 资源镜像     | 菜单由 admin-service 主维护，并同步为 permission-center 资源                                  |
| 按钮/接口资源      | `permission-center`      | admin-service 菜单与业务模型映射 | 目标应统一进入 Resource 模型，由 permission-center 管理授权关系                               |
| 权限关系           | `permission-center`      | admin-service 聚合查询           | 角色授权、权限查询、运行时接口鉴权都应以 permission-center 为事实源                           |
| 前端路由与菜单呈现 | `admin-service` 聚合输出 | frontend 本地转换                | 目标是由 admin-service 统一聚合，不应再依赖 mock 路由契约                                     |

### 5.2 目标模块分层

当前阶段建议把模块分成四层：

1. 核心主线层：`gateway`、`admin-service`、`permission-center`、`perm-common`、`perm-client-spring-boot-starter`、`perm-entity`
2. 后台管理支撑层：日志审计、类型定义、业务域分类、必要系统配置
3. 核心完成后补齐的接入层：`example-service`、Spring Boot SDK、普通 Java SDK、其他语言接口文档
4. 待评估重构层：当前实现度不足的 starter 或误导性较强的半成品接入层

这个分层的核心目的不是给模块贴标签，而是避免“主线还没收口，就先去扩 SDK、演示层和外围平台能力”。

## 6. Round 1｜认证与会话

### 6.1 核对目标

Round 1 的目标是回答四个问题：

1. 前端现在到底调谁登录。
2. 登录成功后，前端应该再拉哪些信息。
3. Gateway 运行时鉴权依赖的身份信息来自哪里。
4. 前端是否应该直接调用 permission-center。

### 6.2 涉及模块与入口

前端入口：

- [frontend/src/api/user.ts](../../frontend/src/api/user.ts)
- [frontend/src/api/routes.ts](../../frontend/src/api/routes.ts)
- [frontend/src/store/modules/user.ts](../../frontend/src/store/modules/user.ts)
- [frontend/src/utils/http/index.ts](../../frontend/src/utils/http/index.ts)

后端入口：

- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/AuthController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/AuthController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OAuth2Controller.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OAuth2Controller.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/impl/AuthServiceImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/impl/AuthServiceImpl.java)
- [gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/AuthTokenFilter.java](../../gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/AuthTokenFilter.java)
- [gateway/src/main/resources/bootstrap.yml](../../gateway/src/main/resources/bootstrap.yml)

### 6.3 已确认事实

1. 前端当前登录与动态路由仍明显带有模板契约痕迹，入口分别是 [frontend/src/api/user.ts](../../frontend/src/api/user.ts) 和 [frontend/src/api/routes.ts](../../frontend/src/api/routes.ts)。
2. 后端真实认证主入口已经是 [AuthController](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/AuthController.java) 与 [OAuth2Controller](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OAuth2Controller.java)。
3. 前端当前预期登录响应中含有 `roles`、`permissions`、`expires` 等模板字段，但后端 [LoginResp](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/dto/auth/LoginResp.java) 实际返回的是 `accessToken`、`refreshToken`、`expiresIn`、`userId`、`tenantId`、`username`、`tokenType`、`forceResetPwd`。
4. 前端当前动态路由接口是模板式的 `/get-async-routes`，但后端真实菜单聚合入口已经是 `/auth/user-menu`。
5. `AuthServiceImpl` 已经通过权限客户端聚合角色和权限信息，说明后端方向本身更接近“登录后再拉用户信息和菜单”的模型。

### 6.4 结构性问题

#### 问题一：前后端认证契约断层

前端现在仍按模板式协议组织登录与刷新，而后端实际认证模型已经走向“最小登录返回 + 后续拉用户信息和菜单”的结构。只要这一层不断开，后续所有前端页面、菜单、按钮权限都会继续建立在错误前提上。

#### 问题二：动态路由入口仍停留在模板逻辑

前端当前依赖 `/get-async-routes` 和 mock 数据，但项目目标已经明确为后端统一下发业务路由。这意味着前端在菜单、页面权限和按钮权限三者的关系上，仍未切到真实后端契约。

#### 问题三：登录态穿透链存在高风险断层

这是 Round 1 最危险的问题。当前代码显示：admin-service 登录逻辑把 `tenantId`、`subjectTypeCode` 等信息写入 SaSession，而 Gateway 的 [AuthTokenFilter](../../gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/AuthTokenFilter.java) 读取这些身份信息时依赖 token extra。两者如果没有统一约定，就会出现“登录表面成功，但进入 Gateway 后身份上下文不完整”的高风险。

### 6.5 目标模型

Round 1 已经收敛出的目标模型如下：

1. 前端只调 admin-service，不直连 permission-center。
2. `/auth/login` 只返回 token 和最小身份信息，不直接返回完整角色和权限集合。
3. 登录完成后，前端立即调用 `/auth/userinfo` 与 `/auth/user-menu`。
4. Gateway 统一负责身份头注入和运行时接口鉴权。
5. permission-center 作为权限事实源参与角色、权限和接口鉴权结果计算。

### 6.6 待后续落地项

1. 统一 Sa-Token 登录态写入与 Gateway 身份读取约定。
2. 前端改造为 `login -> userinfo -> user-menu` 的真实链路。
3. 退场模板式 `/get-async-routes`、`/login`、`/refresh-token` 预设逻辑。

## 7. Round 2｜主体与组织

### 7.1 核对目标

Round 2 关注的是主体事实源和组织语义：

1. 用户由谁主维护。
2. 组织由谁主维护。
3. 角色与 user-role 到底由谁主维护。
4. 用户加入组织后，权限事实如何闭环。

### 7.2 涉及模块与入口

前端相关入口：

- 当前主要仍处于后端核对阶段，前端尚未形成稳定的真实组织角色管理入口

后端入口：

- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OrgController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/OrgController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/RoleController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/RoleController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserOrgController.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/controller/UserOrgController.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/UserSyncHandlerImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/UserSyncHandlerImpl.java)
- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/OrgSyncHandlerImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/OrgSyncHandlerImpl.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/RoleController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/RoleController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserRoleController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/UserRoleController.java)

### 7.3 已确认事实

1. 用户事实源在 admin-service，permission-center 的抽象用户更像权限镜像与查询对象。
2. 用户同步链已经存在，方向是 admin-service 单向同步到 permission-center。
3. 组织当前只同步为 permission-center 的 ORG 资源，不自动创建角色。
4. admin-service 当前已有角色入口，但能力范围明显偏薄，更像部分代理，而不是完整的角色管理聚合层。
5. permission-center 才是角色与 user-role 关系的真实事实源。
6. `UserOrgController` 已经管理 user-org 关系，但尚未看到 user-org 自动落成 permission-center user-role 的完整闭环证据。
7. 同步失败后已有定时重试机制，而不是完全依赖人工修复。

### 7.4 结构性问题

#### 问题一：组织语义只有资源镜像，没有真正闭合为角色容器

你已经明确：组织不仅是业务树，也是角色容器。当前实现只完成了“组织同步成 ORG 资源”，却没有稳定落地“组织承载默认角色、组织角色集合、岗位角色映射”的语义，因此结构仍是不完整的。

#### 问题二：user-org 到 user-role 关系没有自然闭环

这是 Round 2 的核心结构缺口。因为组织和角色语义没有彻底打通，所以“用户加入组织就自动获得组织角色或岗位角色”这条主线现在还没有真正落地。

#### 问题三：admin-service 作为前端唯一入口的代理能力还不够完整

如果后续前端统一只走 admin-service，那 admin-service 需要代理或编排更多角色能力，而不是只保留几个薄接口。

### 7.5 目标模型

Round 2 已确认的目标模型如下：

1. admin-service 继续作为用户与组织的主事实源。
2. permission-center 继续作为角色、授权、user-role 的事实源。
3. 组织既是业务树，也是角色容器。
4. 用户加入组织时，系统自动把组织默认角色或岗位映射角色落到 user-role 事实中。
5. 前端所有角色管理能力最终都经由 admin-service 统一代理，不直接打到 permission-center。

### 7.6 待后续落地项

1. 定义组织默认角色和岗位角色的映射模型。
2. 在 user-org 变更时稳定落地到 permission-center user-role。
3. 扩展 admin-service 的角色代理面，使其足以承接真实前端管理入口。

## 8. Round 3｜资源与菜单

### 8.1 核对目标

Round 3 关注资源、菜单和业务路由：

1. 菜单由谁主维护。
2. 业务路由由谁下发。
3. 菜单、按钮、接口是否应该进入统一资源模型。
4. 菜单权限和接口权限如何避免分裂配置。

### 8.2 涉及模块与入口

前端入口：

- [frontend/src/api/routes.ts](../../frontend/src/api/routes.ts)
- [frontend/src/router/index.ts](../../frontend/src/router/index.ts)
- [frontend/src/router/utils.ts](../../frontend/src/router/utils.ts)

后端入口：

- [admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/MenuSyncHandlerImpl.java](../../admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/domain/impl/MenuSyncHandlerImpl.java)
- [docs/design/permission-center/overview.md](../../design/permission-center/overview.md)

### 8.3 已确认事实

1. 前端当前动态路由仍依赖模板式 `/get-async-routes` 和 mock 数据。
2. admin-service 已有菜单同步到 permission-center 资源的实现，且当前用 `SysMenu.permCode` 作为资源编码的重要映射点。
3. permission-center 概念模型中，菜单、按钮、接口、报表、数据范围等都被统一建模为 Resource。

### 8.4 结构性问题

#### 问题一：业务路由来源尚未真正切到后端

目标已经明确为“业务路由全部后端下发”，但现状仍是前端模板式动态路由。只要这里不断开，前端权限模型就会继续混杂“页面路由事实”和“本地模板规则”。

#### 问题二：菜单、按钮、接口尚未形成统一授权主线

如果菜单、按钮、接口仍分散在不同来源管理，后续授权一定会回到“页面配一套、按钮配一套、接口配一套”的碎裂状态。

#### 问题三：资源依赖还没有真正承担统一收口职责

Round 3 和 Round 4 之间最重要的连接点就是 `resource_dependency`。如果它不落地，业务路由和接口权限就很难建立稳定映射关系。

### 8.5 目标模型

1. admin-service 统一向前端下发业务路由、菜单和最终权限结果。
2. permission-center 统一维护菜单、按钮、接口等资源对象及其依赖关系。
3. 前端只消费 `menus + permission codes`，不再维护独立 mock 路由权限体系。
4. 菜单与接口权限通过资源依赖自动补齐，而不是继续分裂配置。

### 8.6 待后续落地项

1. 用 `/auth/user-menu` 取代前端模板式 async-routes 入口。
2. 明确菜单资源、按钮资源、接口资源的编码映射规则。
3. 将 `resource_dependency` 真正接入授权保存链，形成统一主线。

## 9. Round 4｜授权与权限事实

### 9.1 核对目标

Round 4 的目标是确认授权模型本身是否足够闭环：

1. 条件权限和范围权限是否已有对象模型。
2. 资源依赖和冲突规则是否已有基础设计。
3. 权限版本和缓存失效是否已有机制。
4. 哪些能力是首期必须落地，哪些应收敛范围。

### 9.2 涉及模块与入口

主要依据：

- [docs/design/permission-center/core-flows.md](../../design/permission-center/core-flows.md)
- [docs/design/permission-center/overview.md](../../design/permission-center/overview.md)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/impl/PermissionGrantAppServiceImpl.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/impl/PermissionGrantAppServiceImpl.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/domain/impl/PermissionGrantDomainServiceImpl.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/domain/impl/PermissionGrantDomainServiceImpl.java)

### 9.3 已确认事实

1. 条件权限、范围权限、权限版本、资源依赖、冲突规则在模型层都已有明确来源。
2. `resource_dependency` 已有表结构和 `autoGrant` 设计。
3. 授权保存链和领域逻辑中仍存在与自动补权相关的 TODO。
4. 角色互斥与资源加操作互斥，已经收敛为双轨策略。
5. 条件权限第一阶段只需要支持简单平铺条件，不需要复杂嵌套表达式。

### 9.4 结构性问题

#### 问题一：资源依赖自动补权缺口仍是首期核心缺口

这不是可选增强，而是你已经明确要求首期必须完成的骨干能力。因为目标模型里，菜单、按钮、接口权限的关系不应该再靠人工重复维护，必须通过 `resource_dependency` 自动完成最小可用闭环。

#### 问题二：授权模型容易因为“高级能力”而失控

条件权限、范围权限、冲突规则都属于易膨胀能力。当前已经明确的正确边界是：

- 角色级互斥在写入时处理。
- 资源加操作级互斥在查询时处理。
- 条件权限首期只承担轻量过滤职责。

只有这样，首期才能把预算集中在真正的授权主线上。

### 9.5 目标模型

1. 角色授权保存后，即可通过 `resource_dependency` 自动补齐依赖资源权限。
2. 角色级互斥在写入时报错，防止非法授权进入事实层。
3. 资源加操作级互斥在查询时判定并过滤，避免查询层逻辑失真。
4. 条件权限只支持首期必要的简单平铺条件。

### 9.6 待后续落地项

1. 完成授权保存链中的自动补权逻辑。
2. 把自动补权与权限版本更新、缓存失效联动起来。
3. 为条件权限、冲突规则写清首期与后续阶段边界。

## 10. Round 5｜运行时鉴权

### 10.1 核对目标

Round 5 关注的是系统运行时是否真能稳定完成鉴权：

1. Gateway 主链是否完整。
2. permission-center 是否具备运行时判定所需模型。
3. 缓存和版本机制是否能支撑主链。
4. 是否存在阻断运行时落地的高风险断层。

### 10.2 涉及模块与入口

- [gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter](../../gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter)
- [gateway/src/main/resources/bootstrap.yml](../../gateway/src/main/resources/bootstrap.yml)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/impl/PermissionQueryAppServiceImpl.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/service/impl/PermissionQueryAppServiceImpl.java)

### 10.3 已确认事实

1. Gateway 侧白名单、头清洗、Token 解析、L1 缓存、Header 注入、签名增强、调用 permission-center `check-interface` 的主链已经存在。
2. permission-center 侧的运行时鉴权、`permissionVersion`、`query-resources`、`query-scopes`、`InterfaceSnapshot` 等模型基本齐备。
3. Gateway 当前 L1 缓存更像 TTL 容忍窗口，而不是强一致主动失效模式。
4. 当前已确认 Gateway 会生成签名，但下游统一验签落点仍待进一步确认。

### 10.4 结构性问题

#### 问题一：登录态穿透链不一致是运行时最大风险

这是全项目当前最危险的技术断层之一。如果 admin-service 登录成功后没有把 Gateway 真正需要的身份信息以统一方式放进 token 或读取上下文，那么运行时接口鉴权链就会建立在不完整主体信息之上。

#### 问题二：缓存一致性当前更偏“最终一致”而非强一致

当前组合更像是 permission-center 通过 `permissionVersion` 管理内部权限版本，Gateway 通过短 TTL 容忍一定窗口。这种方式不是错误，但需要明确说明边界，避免后续误以为已经具备主动强一致失效能力。

#### 问题三：签名闭环仍需确认

如果只有 Gateway 生成签名，下游缺少统一校验落点，那么服务间调用安全闭环仍然是不完整的。

### 10.5 目标模型

1. Gateway 成为统一运行时门禁入口。
2. permission-center 负责接口权限判定与相关查询模型。
3. 身份载体、缓存策略、验签策略三者都要形成明确一致的约定。

### 10.6 待后续落地项

1. 统一登录态载体，确保 Gateway 能完整读取租户、主体类型、用户名等身份信息。
2. 补齐签名验证链路的落点确认与文档说明。
3. 明确当前缓存一致性模型是“短 TTL + 权限版本驱动”的边界，而不是强一致推送失效。

## 11. Round 6｜前端权限呈现

### 11.1 核对目标

Round 6 关注的是前端最终如何消费权限结果：

1. 页面权限、菜单权限、按钮权限当前分别怎么判断。
2. 前端本地存了哪些权限事实。
3. 前端是否存在两套权限系统并行。
4. 前端最终应该保留哪一套模型。

### 11.2 涉及模块与入口

- [frontend/src/router/index.ts](../../frontend/src/router/index.ts)
- [frontend/src/router/utils.ts](../../frontend/src/router/utils.ts)
- [frontend/src/store/modules/user.ts](../../frontend/src/store/modules/user.ts)
- [frontend/src/store/modules/permission.ts](../../frontend/src/store/modules/permission.ts)
- [frontend/src/utils/auth.ts](../../frontend/src/utils/auth.ts)
- [frontend/src/components/ReAuth/src/auth.tsx](../../frontend/src/components/ReAuth/src/auth.tsx)
- [frontend/src/components/RePerms/src/perms.tsx](../../frontend/src/components/RePerms/src/perms.tsx)
- [frontend/src/directives/auth/index.ts](../../frontend/src/directives/auth/index.ts)
- [frontend/src/directives/perms/index.ts](../../frontend/src/directives/perms/index.ts)
- [frontend/src/views/permission/button/index.vue](../../frontend/src/views/permission/button/index.vue)
- [frontend/src/views/permission/button/perms.vue](../../frontend/src/views/permission/button/perms.vue)

### 11.3 已确认事实

1. 前端当前同时存在两套按钮权限模型：`Auth` / `v-auth` / `hasAuth` 与 `Perms` / `v-perms` / `hasPerms`。
2. 页面和菜单权限当前仍主要依赖 `meta.roles` 与前端本地过滤。
3. `usePermissionStore` 名称上像“权限事实 store”，但实际上更偏菜单和路由状态。
4. 真正的角色、权限事实又散落在 user store、本地缓存和 token 相关逻辑中。
5. 现有权限演示页本质上展示的是模板时代留下的两套并行模型。

### 11.4 结构性问题

#### 问题一：前端权限语义分裂

当页面、菜单、按钮分别由不同来源判断时，前端会出现“菜单能看见但按钮不一致”或“页面能进入但接口不通过”的长期混乱。当前正处于这种分裂状态。

#### 问题二：前端本地缓存了过多权限事实

目标模型里，前端应该只消费后端聚合好的结果，而不应自己承担权限事实源角色。现在权限事实散落在多个位置，本质上说明前端还在承担一部分不该承担的权限主逻辑。

#### 问题三：命名和职责已经开始误导

例如 `usePermissionStore` 实际存的是菜单与路由，不是权限事实。这种命名问题看起来是小事，但一旦继续扩展，会不断强化错误抽象。

### 11.5 目标模型

1. admin-service 统一返回 `menus + roles + permissions`。
2. 前端页面和菜单主要依赖后端下发路由与最小本地校验。
3. 按钮统一依赖稳定 `permissions` 权限码。
4. 现有 `auths` 模板链逐步退场，前端只保留一套权限呈现逻辑。

### 11.6 待后续落地项

1. 退场 `auths` / `hasAuth` / `v-auth` 体系。
2. 收敛 user store、permission store、本地缓存中的权限事实职责。
3. 建立前端只消费后端聚合结果的最终模型。

## 12. Round 7｜补充域与演示层

### 12.1 核对目标

Round 7 关注的是：

1. 哪些支撑能力应该保留。
2. 哪些能力必须首期收敛范围。
3. 哪些模块属于后置建设，不应抢主线资源。

### 12.2 涉及模块与入口

- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/LogQueryController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/LogQueryController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/TypeDefinitionController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/TypeDefinitionController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/BizDomainController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/BizDomainController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/DomainConfigController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/DomainConfigController.java)
- [permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/SystemConfigController.java](../../permission-center/src/main/java/cn/ac/fage/accessmesh/permission/controller/SystemConfigController.java)
- [docs/design/services/example-service.md](../../design/services/example-service.md)

### 12.3 已确认事实

1. 日志审计与类型定义都属于核心或后台管理必需能力，且当前成熟度较高。
2. 业务域与系统配置都应该保留，但首期不能无限扩张。
3. 业务域的职责已经明确：它只用于角色、权限的分类与后台管理视角隔离，不承担数据权限、运行时鉴权主链或资源树归属重构职责。
4. `example-service` 的目标定位已经明确为“真实接入示例”，但建设时点应后置到项目核心主线稳定之后。
5. 外部系统接入最终需要三层交付：Spring Boot starter、普通 Java SDK、其他语言接口文档。
6. `perm-common`、`perm-client-spring-boot-starter`、`perm-entity` 当前属于生产主线必须保留的基础模块。
7. 部分 starter 当前实现度偏低，后续是否保留、合并或重写，应放到 SDK 方案成型后再裁决。

### 12.4 结构性问题

#### 问题一：支撑能力容易无限扩张

业务域、系统配置、日志、接入层这类能力很容易在主线未收口时不断扩张，最终抢走核心问题的实现预算。当前已经明确的正确方式是：保留这些模块，但严格收敛首期边界。

#### 问题二：演示层与 SDK 层如果过早展开，会反向绑架主线

如果核心契约还没稳定，就提前做真实接入示例和多形态 SDK，后面极可能因为主线调整而大面积返工。

#### 问题三：业务域必须保持“管理分类”定位

这一点非常关键。业务域不是新的业务对象主线，也不是数据权限平台，它只是为了降低后台管理复杂度，让不同业务管理员只聚焦自己那部分角色与权限集合。

### 12.5 目标模型

1. 日志审计、类型定义持续保留，作为后台支撑能力。
2. 业务域只承担角色和权限分类、管理视角隔离职责。
3. 系统配置只保留首期必需配置键，不扩成泛平台配置中心。
4. 核心主线稳定后，再建设真实接入示例与三层 SDK 交付体系。

### 12.6 待后续落地项

1. 明确业务域最小管理视图和操作边界。
2. 明确系统配置首期必须支持的配置键集合。
3. 在核心主线收口后，再设计 example-service 与 SDK 交付方案。

## 13. 问题诊断表

### 13.1 汇总表

| 档位            | 主题                               | 核心诊断                                                                                                                      | 不处理的直接后果                                                         | 建议处理                                                         |
| --------------- | ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------- |
| P0 必须先改     | 登录态穿透链路                     | admin-service 当前登录态写入方式与 Gateway 读取身份方式存在高概率不一致，认证成功后未必能稳定穿透到运行时鉴权链。             | 出现“能登录但接口鉴权异常”或租户、主体类型丢失，整条权限链不可稳定验证。 | 先统一登录态载体与 Gateway 身份读取约定。                        |
| P0 必须先改     | 前后端认证契约断层                 | 前端仍基于模板接口与 mock 契约组织登录、刷新、动态路由；后端真实入口已经是 `login + userinfo + user-menu` 聚合链。            | 后续菜单、角色、按钮权限都会继续建立在错误契约上。                       | 冻结目标契约：最小登录返回，随后拉 userinfo 和 user-menu。       |
| P0 必须先改     | 前端权限模型双轨并行               | 当前同时存在 `auths` 路由权限体系与 `permissions` 权限码体系，且权限事实散落在多个 store 和本地缓存位置。                     | 页面、菜单、按钮的权限语义持续打架，前后端映射无法收口。                 | 保留 `permissions` 单轨，逐步淘汰 `auths` 模板链。               |
| P0 必须先改     | 用户-组织-角色闭环未落地           | 用户、组织由 admin-service 主维护已基本明确，但 `user-org` 如何稳定映射为 permission-center 的 `user-role` 事实仍未真正闭环。 | 组织树、岗位、角色容器这些设计即使定了，也无法形成可运行的真实授权关系。 | 把“用户加入组织自动获得组织角色或岗位角色”落成主链能力。         |
| P0 必须先改     | `resource_dependency` 自动补权缺口 | 资源依赖模型已经设计且被确认为首期必需，但授权保存链仍存在未完成点。                                                          | 菜单、按钮、接口之间继续靠人工分裂配置，权限模型重新碎裂。               | 作为授权主链首期必做项，与角色授权保存链一起收口。               |
| P1 第二阶段补齐 | admin-service 聚合代理能力偏薄     | 已确定前端不直连 permission-center，但 admin-service 当前对角色、授权、查询的代理面仍偏薄，尚不足以成为唯一前端后端入口。     | 前端一旦开始真实对接，会发现聚合层能力不完整。                           | 在主链打通后，补齐角色管理、授权管理、用户角色分配等统一代理面。 |
| P1 第二阶段补齐 | 运行时一致性细节未完全闭合         | Gateway L1 缓存、`permissionVersion`、签名生成已有方向，但签名校验落点和更强一致性策略尚未完全确认。                          | 运行时链路可用但边界不够稳，后续容易在缓存窗口和跨服务验签处出现灰区。   | 先保证主链可用，再补齐签名验证闭环和缓存一致性策略说明。         |
| P1 第二阶段补齐 | 业务域与系统配置存在扩张风险       | 业务域已明确只是管理分类能力，系统配置也应仅保留必要范围；两者若不控边界，都会侵蚀主线预算。                                  | 容易把“降低管理复杂度”的辅助能力做成新的复杂平台。                       | 业务域只做角色和权限分类视图；系统配置只保留首期必需键集合。     |
| P2 后期建设     | 真实接入示例尚未进入建设期         | `example-service` 应做成真实接入示例，但当前阶段不应抢主线资源。                                                              | 过早做会分散核心链路收口；长期不做则外部接入说服力不足。                 | 待核心主线稳定后，再把 `example-service` 补成真实接入样板。      |
| P2 后期建设     | SDK 交付层尚未展开                 | 外部系统接入最终需要 Spring Boot starter、普通 Java SDK、其他语言接口文档三层交付，但这不是当前主线阻塞项。                   | 现在展开会倒逼主线过早固化；长期不做则外部接入成本高。                   | 作为核心完成后的正式交付层建设，基于稳定契约再设计。             |
| P2 后期建设     | starter 模块定位待重构             | 当前部分 starter 实现度偏低，是否保留、合并还是按最终 SDK 形态重写，取决于主线稳定后的接入策略。                              | 继续维持半成品状态会误导后续开发者，以为已有可复用接入能力。             | 暂时标记为待评估层，等 SDK 方案确定后统一裁决。                  |

### 13.2 P0 项详细说明

#### P0-1 登录态穿透链路

之所以把这项列为 P0，是因为它直接决定整条身份主线是否真实存在。如果登录态写入与 Gateway 读取约定不一致，那么后续所有接口鉴权都可能建立在不完整主体信息上。这个问题如果不先解决，其他再漂亮的权限模型也只能停留在“静态设计可讲通，运行时不一定成立”的状态。

#### P0-2 前后端认证契约断层

这项之所以是 P0，是因为前端仍在消费错误的后端想象。只要前端继续以模板式响应结构和 mock 路由契约为基础，后续你让前端接真实接口时，就一定会遇到整片逻辑要一起重写，而不是局部替换。

#### P0-3 前端权限模型双轨并行

这项之所以是 P0，是因为它会持续制造“页面、菜单、按钮三套不同语义”。如果不先裁掉一套，后续无论你怎么补后端，前端都会继续把结果解释成两套含义不同的权限系统。

#### P0-4 用户-组织-角色闭环未落地

这项之所以是 P0，是因为它打断了主体到授权的主桥梁。你已经明确组织既是业务树，也是角色容器。如果 user-org 变更无法稳定落到 user-role 上，那么组织、岗位、角色这一整套设计都还没有进入“可运行状态”。

#### P0-5 `resource_dependency` 自动补权缺口

这项之所以是 P0，是因为它打断了资源主线。你已经明确菜单、按钮、接口权限不应该继续分裂配置，而应该由依赖关系自动收口。如果这里不先补，权限主线就会在落地时重新碎裂。

### 13.3 P1 项说明

P1 项不是不重要，而是它们依赖 P0 主线先收口：

1. admin-service 聚合代理面偏薄，是架构从“方向正确”走向“真正可落地”的补完工作。
2. 运行时一致性细节未完全闭合，是对主链可靠性的增强，而不是主线是否存在的前提。
3. 业务域与系统配置边界控制，是防止支撑能力侵蚀主线预算的治理问题。

### 13.4 P2 项说明

P2 项不是可以忽略，而是它们必须建立在稳定契约之上：

1. 真实接入示例必须做，但要在核心主线稳定后做，才能成为真正可信的样板。
2. SDK 三层交付必须做，但必须等核心接口契约相对稳定，否则会不断推翻。
3. starter 模块的最终命运，也应该由最终接入策略决定，而不是在当前阶段凭直觉清理。

## 14. 建议实施顺序

基于上述诊断，建议按以下顺序推进：

### 第一阶段：先修身份主线

1. 统一 admin-service 登录态写入与 Gateway 身份读取约定。
2. 冻结前端真实认证链：`login -> userinfo -> user-menu`。
3. 退场模板式登录、刷新、动态路由接口假设。

### 第二阶段：再修授权主线

1. 把 `user-org` 到 `user-role` 的闭环真正落地。
2. 完成 `resource_dependency` 自动补权。
3. 扩展 admin-service 的角色与授权聚合代理能力。

### 第三阶段：收前端消费主线

1. admin-service 统一返回 `menus + roles + permissions`。
2. 前端只保留一套权限体系。
3. 页面、菜单、按钮权限语义全部对齐后端聚合结果。

### 第四阶段：补支撑域与接入层

1. 收敛业务域分类视图与系统配置边界。
2. 在核心主线稳定后建设真实接入示例。
3. 再设计并交付 Spring Boot starter、普通 Java SDK 和其他语言接口文档。

## 15. 已确认决策与边界

以下内容已经在本轮逐步核对中完成拍板，后续设计和实现应以此为边界：

1. 前端不直接调用 permission-center，统一经 admin-service 或 Gateway 中转。
2. 登录返回采用“最小返回”模型，登录后再拉 userinfo 和 user-menu。
3. 业务路由统一由后端下发，前端不再维持模板式 mock 动态路由体系。
4. 菜单、按钮、接口权限通过统一资源模型管理，并通过 `resource_dependency` 形成依赖收口。
5. 组织既是业务树，也是角色容器。
6. 用户加入组织时，自动获得组织角色或岗位映射角色。
7. 前端最终只保留一套权限呈现逻辑，按钮统一靠稳定权限码。
8. 业务域只做角色和权限分类、后台管理视角隔离，不扩成数据权限或运行时鉴权主线。
9. `example-service` 要做成真实接入示例，但放到核心主线完成后再建设。
10. 外部接入最终按三层交付：Spring Boot starter、普通 Java SDK、其他语言接口文档。

## 16. 结语

Round 1 到 Round 7 的核对结果表明，当前项目真正的问题不是“已经写了很多后端代码但还缺几个接口”，而是身份主线、授权主线、前端消费主线三条核心路径还没有彻底收口。

一旦把这三条主线依次打通，后续无论是前端接入、管理界面收敛、业务域隔离、还是对外 SDK 与真实接入示例建设，都会从“反复返工”变成“顺着稳定主线向外扩展”。

因此，这份文档的价值不在于把所有现状都记录下来，而在于把“下一步先做什么”说清楚：先修身份主线，再修授权主线，再收前端消费主线，最后补支撑域与接入层。
