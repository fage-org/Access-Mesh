---
doc_type: task
id: T-ACCESS-006
title: 建立跨域只读查询模型
status: done
plan: docs/archive/2026-08-22/access-service-merge-plan.md
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
last_updated: 2026-08-16
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
5. 权威 schema 的 `sys_menu`（display_name/DIR-MENU 枚举，无 component/visible/perm_code 列）与存量 SysMenu 实体存在 DDL-实体漂移：query 包按权威 schema 显式列读取，菜单树构建对缺失字段取默认值（component=null、showLink=true、keepAlive=false、auths=null；EXTERNAL/IFRAME→frameSrc=path；HIDDEN 不进 menus[]）；存量漂移（菜单 CRUD 写路径仍用旧实体字段，真实库写入会失败）登记交由 T-ACCESS-015 收口（原登记 T-ACCESS-012，2026-08-22 改挂）。
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

**前端影响登记**：`frontend/src/api/user-manage.ts` 的 `assignRole`/`revokeRole` 调 `/user-role/assign|revoke`（mock 阶段），Phase 3 联调（T-FE-015~022）时改调 `/api/perm/user-role/assign|revoke`。`sys_menu` DDL-实体漂移（菜单 CRUD 写路径）交由 T-ACCESS-015 收口（原登记 T-ACCESS-012，2026-08-22 改挂）。

---

## 评审修复记录（补充，2026-08-15，外部 AI 评审复评 1×P1 + 2×P2 + 2×P3 + 测试质量）

复评结论「仍不建议通过」：评审认可上轮修复方向，但指出自查菜单仍会被 permission 域 `USER:VIEW` 门禁拦截（P1）、半缺失资源链接 fail-open（P2-1）、分层规则矛盾（P2-2）、菜单树 O(n²)（P3-1）、`/role/list` 上限未声明（P3-2）、测试缺口。逐项核实修复如下。

**用户决策 1 项（2026-08-15）**：**USER:VIEW 门禁下放入口（P1）**——`PermissionViewAppService.buildEffectiveView` 公共 pipeline 移除 `USER:VIEW` 门禁（v1.4 时代即有，自查必抛被吞 → 空菜单，前端 mock 阶段未暴露）；permission 域独立 HTTP 入口 `/effective-permission-codes` 走新增 `getEffectivePermissionCodesForManage`（自查豁免 + 查他人需 `USER:VIEW`）；query 包内部调用继续用 `getEffectivePermissionCodes`，入口由各自 Controller 门禁兜底（自查豁免 + `ADMIN_USER:VIEW`，P1-2）；`getEffectivePermissions` 管理员视图保留原 `USER:VIEW`/`ROLE:VIEW` 门禁不动。query 包 → `PermissionViewAppService` 依赖经用户确认登记为横向调用例外（P2-2）。

**代码修复**：

- `PermissionViewAppServiceImpl.buildEffectiveView` 移除门禁块；新增 `getEffectivePermissionCodesForManage`（自查豁免 + 查他人需 `USER:VIEW`）；`PermissionViewController` 的 `/effective-permission-codes` 改调该方法（P1）。
- `UserMenuQueryServiceImpl.deriveVisibleMenuIds` 业务菜单判定收紧为 `resource_type != null`（P2-1：v3.5 §4.1 只认 `resource_type IS NULL` 为纯展示，DDL 无成对约束；`resource_code` 为空按不可解析资源 fail-closed，无 scopeAll 时不可见）。
- `UserMenuQueryServiceImpl.buildMenuTree` 线性化（P3-1：按 parentId 预建 children 映射再递归，每节点只访问一次；visited 环保护保留）。
- `UserRoleQueryService.listRoles` Javadoc 补「结果固定 `LIMIT 0,200`」（P3-2，用户决策「保持 + 文档声明上限」）。
- `docs/design/project-rules.md` 横向调用例外登记 query 包只读查询（P2-2，用户确认）；`access-service-architecture.md` §3 同步补白名单说明与业务菜单定义。

**测试补充**（502 tests 0 失败 27 跳过，较上轮 486 +16）：

- `AdminUserControllerTest`（3，新建）：user-menus 自查豁免、查他人需 `ADMIN_USER:VIEW`、无权限传播 SecurityException。
- `PermissionViewControllerTest`（新建）：`/effective-permission-codes` 转发 `getEffectivePermissionCodesForManage`。
- `PermissionViewAppServiceImplTest`（+4）：`getEffectiveResourceAccess` 收集 scopeAll + 实例 ID；`getEffectivePermissionCodesForManage` 自查豁免 / 查他人拒绝 / 查他人有权限放行。
- `UserMenuQueryServiceImplTest`（+2）：半缺失资源链接无 scopeAll fail-closed / 有 scopeAll 可见。
- `OrgVisibilityQueryServiceImplTest`（+2）：缓存 get 异常旁路 DB / 缓存 put 异常不阻断结果（评审要求覆盖）。
- `OrgTreeConfigServiceImplTest`（4，新建）：4 个写方法 `markVisibility` 登记 ORG_VISIBILITY 租户级失效（框架 `evictAll(ORG_VISIBILITY)` 由 `PermissionChangeAspectTest` 覆盖，本测试断言业务侧登记发生）。

---

## 评审修复记录（第三轮，2026-08-15，外部 AI 评审复评 1×P1 + 1×P2 + 2×P3 + 4 条 Javadoc 注释）

第三轮结论「仍不建议通过」（本轮评审未修改代码）。核实后：1 个 P1（两套 ID 空间混用）、1 个 P2（菜单未按 menu_type 分支）、2 个 P3（递归栈溢出、生产 Javadoc 混评审轮次信息）。逐项修复如下。

**用户决策 2 项（2026-08-15）**：

1. **P1 门禁主体改造范围**：一并修存量门禁——全部 85 处存量 permission 域 gate（`hasPermission` / `validateBatch` / `getDeniedIds`）统一改用投影主体 `abstract_user.id`，不留隔离带。
2. **菜单树递归栈溢出（P3-1）**：不修，接受风险。`sys_menu` 权威 schema 未设层级上限，极端深度存在 `StackOverflowError` 风险；菜单为受控管理数据，限制登记于 `UserMenuQueryServiceImpl.buildMenuChildren` Javadoc（`visited` 仅防护脏数据环，不改变深度）。

**P1 修复（两套 ID 空间）**：

- 新增 `OperatorSubjectResolver.requireSubjectId(tenantId, operatorId, engine)`：登录会话 / 签名代理主体持有的操作者 ID 是 admin 域 `sys_user.id`，权限引擎按 `abstract_user.id` 匹配 `user_role.abstract_user_id`。所有 engine 门禁与投影空间自查逻辑先经此转换；转换失败（投影不存在）fail-closed 抛 `SecurityException`。
- `PermQueryEngine.resolveOperatorSubjectId` 提供转换（`external_id = sys_user.id` → `abstract_user.id`）；全部 AppServiceImpl 均已注入 engine，无需为存量文件新增依赖。
- 16 个 permission AppService 存量 gate 主体改用 `operatorSubjectId`；`UserManageAppServiceImpl` 改己豁免 / 批量自删自查比较同用投影主体。
- `PermissionGrantAppServiceImpl` 委托链（`checkCanGrant` / `canGrantPermission` / `prevalidate` / `verifyChildDelegation`）改传投影主体——内部命中 `subjectDomainService.resolveEffectiveRoles`（投影空间查找）。
- 非 gate 用途保留 sys 空间：createdBy 戳记、SyncContext 上下文、audit `ChangeLogContext`、日志消息。

**P2 修复（菜单 menu_type 分支）**：`UserMenuQueryServiceImpl.deriveVisibleMenuIds` 按 v3.5 §4.1 收口——EXTERNAL/IFRAME 派生同业务 MENU（`resource_type` 非空 → 资源实例 / scopeAll 匹配；`resource_type` 为空 → fail-closed 不可见）；DIR 恒候选可见（自身携带 `resource_type` 也不参与资源判定，避免误按业务菜单丢整棵子树），是否渲染由树构建剪枝决定。

**P3-2 清理**：生产 Javadoc 移除评审轮次 / 修复标记，保留当前结论（`OperatorSubjectResolver`、`PermissionViewController`、`PermissionViewAppService`、`UserRoleQueryService`、`AdminUserController`）。

**测试**（507 tests 0 失败 27 跳过，较上轮 502 +5）：

- `PermissionViewAppServiceImplTest` 覆盖两套 ID 空间真实差异（OperatorContext sys=1 → 投影主体 1001 / 1002）：自查豁免、查他人拒绝 / 放行、操作者投影缺失 fail-closed。
- 11 个存量 AppService 测试类 + `OperationLogRuntimeContextAppServiceTest` 统一 lenient stub（`resolveOperatorSubjectId → 传入 operatorId` 的测试简化；两套 ID 差异由上述专项覆盖）。
- `UserMenuQueryServiceImplTest` +4：EXTERNAL/IFRAME 无资源 fail-closed、EXTERNAL/IFRAME 资源匹配可见（frameSrc=path）、DIR 带资源由子节点决定、DIR 带资源无子剪枝。

---

## 评审修复记录（第四轮，2026-08-16，外部 AI 评审复评 1×P2 + 3×P3）

第四轮结论「仍不建议最终通过」：认可上轮核心问题已修复（无 P0/P1），剩余异常值 fail-closed 与代码契约质量问题。核实后 4 个问题全部属实，修复如下。

**用户决策 1 项（2026-08-16）**：

1. **P3-2 门禁 API 两套 ID 混淆修复范围**：方案 1「契约闭环版」——仅做改名 + 文档契约，不引入集中门禁入口 / 静态规则。理由（用户采纳）：85 处存量调用已统一经 `OperatorSubjectResolver.requireSubjectId` 转换，投影主体还被自查比较 / 授权委托链复用；集中入口会让转换路径分裂（`ForSysOperator` 包装方法无法覆盖非门禁用途，还会形成重复转换），ArchUnit 无法在字节码层区分 `Long` 来源（形参同为 Long），只能产生虚假安全感。未来若再犯，升级方向是类型隔离（如 `record PermissionSubjectId(Long value)`），不在本次 P3 范围。

**P2 修复（未知 menu_type fail-closed）**：`UserMenuQueryServiceImpl.deriveVisibleMenuIds` 业务分支显式限定 MENU/HIDDEN/EXTERNAL/IFRAME（新增 `isBusinessMenuType` 枚举），未知 `menu_type` 默认 fail-closed（不入 visible、不参与业务匹配）。DB 中 `menu_type` 为 VARCHAR 无 CHECK 约束，显式枚举防脏数据被误放行。

**P3-2 修复（门禁 API 契约闭环）**：

- `PermQueryEngine.hasPermission / validateBatch / getDeniedIds` 参数 `operatorId → subjectId`，Javadoc 明确「权限域投影主体 `abstract_user.id`，禁止直接传 admin 域 `sys_user.id`（先经 `OperatorSubjectResolver.requireSubjectId`）」。
- `PermQuery.forValidate` 参数同步改 `subjectId`。
- `PermissionGrantDomainService.canGrantPermission / checkCanGrant`（接口 + 实现类 + Javadoc）参数改 `subjectId`——内部命中 `subjectDomainService.resolveEffectiveRoles`（投影空间查找）。
- `OperationCodeConstants` Javadoc 示例改为「先 `requireSubjectId` 转换、再传投影主体」。
- `permission-query-pipeline` 技能（`.claude` + `.agents` 两份 SKILL.md）门禁示例统一为 `subjectId` + 转换契约，并移除过时的 `engine.validate`（不存在的方法）示例。
- 非门禁用途（createdBy 戳记、审计 `ChangeLogContext`、日志消息）与 `OperatorSubjectResolver.requireSubjectId` 输入参数保留 `operatorId`（`sys_user.id` 语义）。

**P3-3 修复**：`UserRoleQueryService.listRoles` Javadoc 移除「用户决策」过程信息，只保留固定 `LIMIT 0,200` 上限与「调用方不得假定全量返回」约束。

**P3-4 修复**：`UserMenuQueryServiceImpl` 类 Javadoc + `deriveVisibleMenuIds` Javadoc / 内联注释统一为「无 scopeAll 且资源实例无法解析时 fail-closed」（与实现先判断 scopeAll 再解析实例一致）。

**测试**（509 tests 0 失败 27 跳过，较上轮 507 +2）：

- `UserMenuQueryServiceImplTest` +2：未知 menu_type 带可访问资源仍不可见（fail-closed）、未知 menu_type resource_type 为空不可见（不按纯展示放行）。
- 完整 `mvn -pl access-service clean test`：509 tests 0 failures 0 errors 27 skipped（27 = Testcontainers/Docker）。

## 评审修复记录（第五轮，2026-08-16，T-ACCESS-007 外部评审复核 T-ACCESS-006 交付物）

复核核实 3 个问题全部属实并修复：

- **菜单降级范围补全（P2，仅捕获有效权限查询异常）**：`UserMenuQueryServiceImpl.deriveVisibleMenuIds` 在 `getEffectiveResourceAccess` 降级后，类型值/资源 ID 解析（`batchResolveTypeValues`/`batchResolveResourceIds`）异常仍会中断登录——现一并 try-catch 降级为业务菜单不可见（fail-closed），仅保留 DIR/纯展示菜单，不中断登录。
- **组织递归环保护**：`OrgVisibilityQueryMapper.selectDescendantOrgIds` 递归 CTE 显式 depth 上限（100），先于 MySQL 默认 1000 次迭代上限稳定终止，防脏数据成环时查询报错/长跑。
- **菜单排序稳定**：`UserMenuQueryMapper.selectMenus` 排序由 `sort_order ASC` 补 `id ASC` 次键，同排序值顺序确定。

测试：上述修复由 T-ACCESS-007 全量回归覆盖（详见 T-ACCESS-007 完成记录）。
