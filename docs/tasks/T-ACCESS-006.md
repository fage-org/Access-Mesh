---
doc_type: task
id: T-ACCESS-006
title: 建立跨域只读查询模型
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#3-模块边界
  - docs/design/org-user-permission-contract.md
  - docs/design/permission-center/api-contract.md
depends_on:
  - T-ACCESS-002
  - T-ACCESS-005
blocks: []
acceptance:
  - "跨管理域与权限域的组合查询集中到 access.application.query，不散落在任一领域 Mapper"
  - "专用 QueryMapper 只包含 SELECT，返回 Projection/DTO，不暴露或修改领域实体"
  - "列表、树和详情查询显式包含 tenant_id 条件，分页总数与结果一致"
  - "组合查询采用 JOIN 或批量查询，测试或静态规则证明不存在循环单条查询"
  - "架构测试仅对白名单 query 包开放跨域表读取，并禁止其执行写 SQL"
  - "相关 HTTP 响应与原契约兼容"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-15
---

# T-ACCESS-006 建立跨域只读查询模型

## 背景

单库可以提高用户、组织和权限组合视图的分页与查询效率，但必须把直接跨域读取限制在明确的只读边界。

## 范围

- 识别并迁移现有跨服务组合查询。
- 建立专用 Projection、QueryMapper 和只读事务。
- 增加租户、分页、N+1 与架构约束测试。

## 完成记录

2026-08-15 实施完成。

**用户决策 6 项**：

1. 迁移范围：数据组合响应 + 可见性过滤迁入 query 包；`AdminPermissionValidator` 门禁与写编排 AppService 留在原处（engine 判定，语义属 security）。
2. QueryMapper 形态：批量查询 + 内存组装（权限判定必须经 engine，不跨域 JOIN）。
3. 合并后不再保留「代理 service 类」：`RoleProxyService`/`RoleProxyServiceImpl`、`OrgVisibilityService`/`OrgVisibilityServiceImpl`（Feign 时代遗留的 admin 接口 + application 实现形态）整体删除，admin 域直接依赖 query 包服务。
4. 角色直接由 permission 管理：admin 侧 `/role/grant-menu`、`/role/revoke-menu`、`/user-role/assign`、`/user-role/revoke` 退役（保留映射恒抛新增错误码 `ROLE_API_RETIRED`(10111)）；`/role/create` 保持恒 20045 语义不变。角色与授权管理由 `/api/perm/abstract-role`、`/api/perm/user-role`、`/api/perm/role-resource-permission` 直接提供。
5. 权威 schema 的 `sys_menu`（display_name/DIR-MENU 枚举，无 component/visible/perm_code 列）与存量 SysMenu 实体存在 DDL-实体漂移：query 包按权威 schema 显式列读取，菜单树构建对缺失字段取默认值（component=null、showLink=true、keepAlive=false、auths=null；EXTERNAL/IFRAME→frameSrc=path；HIDDEN 不进 menus[]）；存量漂移（菜单 CRUD 写路径仍用旧实体字段，真实库写入会失败）登记交由 T-ACCESS-012 收口。
6. 验证方式：静态测试（ArchUnit 包边界 + XML 只读/tenant_id/分页契约断言）+ 查询服务 `@Transactional(readOnly = true)`。

**新增 `access.application.query` 包**：

- 查询服务（接口 + `impl/` 同包实现，只读事务）：
  - `UserMenuQueryService`：`/auth/user-menu`、`/user/user-menus`、`/role/my-info` 聚合（sys_menu 树 + 角色/权限码 + 菜单可见性判定，含登录链路容错——角色/权限加载失败降级为空，菜单树仍构建）。
  - `UserRoleQueryService`：`/role/list` 功能角色（门禁 ADMIN_ROLE:VIEW、功能角色白名单、matchNone 语义、LIMIT 0,200）；`/user-role/list`（门禁 ADMIN_USER:VIEW@userId，有效期窗口 SQL 过滤，POSITION 补所属组织名——**判定以 target 角色解析类型码为准**，`user_role.target_type` 写路径恒为 ROLE/GROUP_ROLE 不承载岗位语义）。
  - `OrgVisibilityQueryService`：组织可见性过滤（批量业务键解析 + `engine.getDeniedIds` 一次判定，未解析 fail-closed；ORG_VISIBILITY 缓存保持）。
- 专用 QueryMapper（`query/mapper` + `resources/mapper/query/`）：只 SELECT、显式 `tenant_id`、返回 `query/projection` 包 Projection record；权限判定一律经 `PermQueryEngine`/`TypeResolutionService`。
- 查询服务方法标注 `@Transactional(readOnly = true)`。

**角色代理退役**：`RoleProxyService` 等 4 个代理类删除；退役写端点保留映射恒拒绝（10111/20045），移除其上的 `@AuditLog`（切面仅成功路径落库，恒抛端点上的注解是虚假审计保证）；读端点（`/role/list`、`/role/my-info`、`/user-role/list`、`/user/user-menus`、`/auth/user-menu`）全部经 query 包服务，响应结构与原契约一致（DTO record 复用，字段零改动）。

**测试**（483 tests 0 失败 27 跳过，27 = Testcontainers/Docker）：

- `UserRoleQueryServiceImplTest`（10）：listRoles 默认/拒绝/未注册/门禁调用与拒绝传播；listUserRoles POSITION 组织名/投影缺失/目标角色缺失保留行/有效期窗口透传/门禁调用。
- `UserMenuQueryServiceImplTest`（5）：user 上下文聚合/容错；菜单树构建（DIR+子菜单+祖先链+HIDDEN 排除+status/sort 过滤+默认值）；角色失败仍建树；权限过滤失败降级空树。
- `OrgVisibilityQueryServiceImplTest`（6）：可见性过滤批量/异常传播/缓存命中/无默认树/子树过滤回填缓存。
- `RetiredRoleApiContractTest`（6）：5 个退役写端点恒拒绝错误码 + 读端点委托。
- `QueryBoundaryArchitectureTest`（5）：admin/permission 互不使用对方 Mapper；application 非 query 包不使用两域 Mapper；query 包不依赖两域实体/Mapper；query Mapper 方法名 select/count/list 白名单。
- `QueryMapperXmlContractTest`（7）：query XML 只 select 标签；每个 select 显式 tenant_id；分页 LIMIT/OFFSET/ORDER BY；禁 SELECT *；有效期窗口与 LEFT JOIN 条件钉住；批量 IN 空集合守卫。

**设计回写**：`access-service-architecture.md` §3（query 包落地形态、依赖白名单、角色代理退役、sys_menu 漂移登记）；`admin-service-api-contract.md` §4.4（读保留/写退役）；`org-user-permission-contract.md`（C 区、备注 ³、§8 实现项、ORG_ROLE 清理完成态）；`default-org-tree-user-lifecycle.md`（ORG_ROLE P0 清理项完成态）。

---

## 评审修复记录（2026-08-15，外部 AI 评审 3×P1 + 4×P2 + 6 质量项）

评审结论「T-ACCESS-006 暂不建议评审通过」。逐项核实后：3 个 P1 中 2 个为真实缺陷（P1-1、P1-3），1 个为安全门禁缺口需用户决策（P1-2）；4 个 P2 中 2 个为真实缺陷（P2-1 缓存故障、P2-4 递归环），2 个需用户决策（P2-2 角色数据归属、P2-3 200 条截断）；6 个质量项中 2 个确认（`POSITION` 常量替换、`roleFailure` 断言修正），4 个不采纳（记录理由）。

**用户决策 4 项（2026-08-15）**：

1. **菜单可见性（P1-1）**：按 v3.5 §4.1 派生公式实施——业务菜单（`resource_type`/`resource_code` 非空）→ 用户对该资源有任意有效操作码（含 scopeAll 全范围）即可见；纯展示菜单全员可见；DIR 剪枝；HIDDEN/EXTERNAL/IFRAME 派生同业务菜单。不再按 ADMIN_MENU 授权模型（v3.5 已删除 ADMIN_MENU）。
2. **user-menus 门禁（P1-2）**：方案 1+2——`AdminUserController.getUserMenus` 查自己豁免，查他人（`req.id() != 当前登录用户`）需 `ADMIN_USER:VIEW@目标用户`（`AdminPermissionValidator.checkInstanceLevel`）。
3. **200 条截断（P2-3）**：保持 `LIMIT 0,200` 上限 + Javadoc/文档声明（功能角色面向前端下拉，超出 200 属配置异常）。
4. **角色数据归属（P2-2）**：角色数据走 query 服务（`UserRoleQueryMapper` 直读 `user_role ⨝ abstract_role`），权限事实保留 AppService（`PermissionViewAppService`）。

**代码修复**：

- `UserMenuQueryServiceImpl` 重写：`deriveVisibleMenuIds` 按 v3.5 §4.1 派生公式（P1-1）；`buildMenuTree` 加 `visited` 集合防脏数据环（P2-4）；依赖收敛为 `UserMenuQueryMapper`/`UserRoleQueryMapper`/`PermissionViewAppService`/`TypeResolutionService`（移除 `UserManageAppService`、`PermQueryEngine` 直依赖）。
- `PermissionViewAppService` 新增 `EffectiveResourceAccess(allScopeTypes, resourceEntityIds)` + `getEffectiveResourceAccess`（提取 `buildEffectiveView` 公共 pipeline，与 `getEffectivePermissionCodes` 复用）。
- `AdminUserController.getUserMenus` 加方案 1+2 门禁（P1-2）。
- `PermissionChangeContext.markVisibility` + `@PermissionChange` 接入 `OrgTreeConfigServiceImpl` 4 个写方法（P1-3：默认树/配置变更 → 租户级失效 ORG_VISIBILITY）。**注意**：`PermissionChange` 注解与 `PermissionChangeContext` 已从 permission 域移至 `access.infrastructure`（跨 admin/permission 框架机制，语义同 `TenantContextHolder`；切面 `PermissionChangeAspect` 仍留在 permission 域），架构测试 `adminShouldNotDependOnPermission` 据此保持通过。
- `OrgVisibilityQueryServiceImpl` 缓存读写 try-catch 旁路 DB（P2-1：Redis 故障降级 fail-open 至数据库层，权限判定仍经 engine fail-closed）。
- `UserRoleQueryServiceImpl`：`POSITION` 常量替换为 `LocalProjectionOwner.ROLE_POSITION`。
- `QueryBoundaryArchitectureTest` 新增 query 包 AppService 黑名单规则（仅 `PermissionViewAppService`；`simpleNameEndingWith("AppService")` 谓词）。

**测试**（486 tests 0 失败 27 跳过）：`UserMenuQueryServiceImplTest` 重写为 7 个（派生公式实例匹配/scopeAll 全范围/纯展示/HIDDEN/status=0/DIR 剪枝/fail-closed/容错降级）；`QueryBoundaryArchitectureTest` 6 个。

**前端影响登记**：`frontend/src/api/user-manage.ts` 的 `assignRole`/`revokeRole` 调 `/user-role/assign|revoke`（mock 阶段），Phase 3 联调（T-FE-015~022）时改调 `/api/perm/user-role/assign|revoke`。`sys_menu` DDL-实体漂移（菜单 CRUD 写路径）交由 T-ACCESS-012 收口。
