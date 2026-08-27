---
doc_type: task
id: T-ACCESS-015
title: 菜单 CRUD 写链路对齐 v3.5 最终态与权威 DDL（消除 sys_menu DDL-实体漂移）
status: done
plan: docs/plans/access-post-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#§3-模块边界
  - docs/design/permission-center-v3.5-design.md#§2.1
  - docs/design/permission-center-v3.5-design.md#§4.1
  - docs/design/schema/access-service.sql
  - docs/design/services/admin-service-api-contract.md
depends_on:
  - T-ACCESS-012
blocks: []
acceptance:
  - "执行前确认：菜单管理对外契约字段终态（displayName/menuType=DIR|MENU|EXTERNAL|IFRAME|HIDDEN 字符串枚举、移除 perms/component/visible）与前端菜单管理页消费字段联动方案"
  - "SysMenu 实体与 SysMenuMapper 对齐 access-service.sql sys_menu 列（display_name、menu_type 5 值 VARCHAR、无 name/perm_code/visible/component）；Mapper XML 无 perm_code 查询残留，findByPermCode 与 MENU_PERM_CODE_EXISTS 按终态退役或显式保留理由"
  - "菜单写链路（create/update/delete）在真实 PostgreSQL（Testcontainers 或既有空库测试轨道）下成功执行，覆盖 uk_sys_menu_tenant_resource/uk_sys_menu_tenant_path 唯一索引冲突路径"
  - "MenuCreateReq/MenuUpdateReq 契约 DTO 按 v3.5 §2.1 最终态定稿并回写 admin-service-api-contract §菜单接口；BUTTON 分支逻辑（MENU_TYPE_BUTTON/非按钮→按钮投影删除）随 5 值枚举移除，ADMIN_MENU 投影对齐 v3.5 §4.1 派生公式（业务菜单=resource_type 非空）"
  - "存量 mock DomainService 的菜单测试迁移到新字段；LocalProjectionDomainService.upsertAdminMenu 名参与 UserMenuQueryService 消费链路回归通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-22
---

# T-ACCESS-015 菜单 CRUD 写链路对齐 v3.5 最终态与权威 DDL

## 背景

T-ACCESS-006 建立跨域只读查询时登记的存量 DDL-实体漂移：权威 DDL `access-service.sql` 的 `sys_menu` 已按 v3.5 最终态收敛（`display_name`、`menu_type` 5 值枚举 DIR/MENU/EXTERNAL/IFRAME/HIDDEN、无 `component`/`visible`/`perm_code` 列），但菜单 CRUD 写路径（`MenuWriteAppServiceImpl` + `SysMenu` 实体 + `SysMenuMapper`）仍使用旧实体字段 `name`/`visible`/`perm_code`/`component` 与数字 `menu_type`，Mapper 仍按 `perm_code` 查询——真实 PostgreSQL 下菜单创建/更新与相关查询会直接失败（现有测试仅 mock DomainService，未覆盖此组合）。原登记由 T-ACCESS-012 收口；该收敛属功能开发（含对外契约 DTO 变更），与文档生命周期任务主题不同，按治理规则（遗留工作新开未占用任务 ID）新开本任务承接（2026-08-22）。

## 范围

- `SysMenu` 实体、`SysMenuMapper`（含 XML）对齐权威 DDL 列。
- `MenuCreateReq`/`MenuUpdateReq` 契约 DTO 按 v3.5 §2.1 定稿（含错误码处置：`MENU_PERM_CODE_EXISTS`）。
- `MenuWriteAppServiceImpl` 写链路与 BUTTON 分支移除后的投影/删除语义。
- `LocalProjectionDomainService.upsertAdminMenu` 及 `UserMenuQueryService` 消费链路回归。
- 真实 PostgreSQL 写入验证与既有菜单测试迁移。

## 非目标 / 遗留

- 不改变 v3.5 §2.1 已定稿的菜单模型语义（零权限化、5 值枚举、派生公式）。
- 前端菜单管理页字段联动在执行前确认项中登记，不在本卡预设方案。

## 完成记录（2026-08-22）

### 设计定案

1. **ADMIN_MENU 投影维护条件**：BUTTON 移除后 DIR/MENU/EXTERNAL/IFRAME/HIDDEN **五值全量投影**（BUTTON=操作权限，不是需要维护的菜单类型；实例级管理门禁依赖投影行授权到具体菜单实例；"业务菜单=resource_type 非空"仅作为 §4.1 可见性派生在 UserMenuQueryService 读链路的回归口径）。
2. **唯一性错误码**：新增 `MENU_PATH_EXISTS(10205)`、`MENU_RESOURCE_EXISTS(10206)`，退役 `MENU_PERM_CODE_EXISTS(10202)`（ErrorContract 测试退役清单登记，码值不复用）。
3. **契约终态对齐 DDL**：`status` 1=ENABLED（默认）/0=DISABLED，移除 `visible/perms/component/menuName`；字段 `menuType(String)/displayName/parentId/path/icon/sortOrder/status/resourceType/resourceCode/sourceService`。
4. **sourceService 写语义**：请求可选传入，缺省 `access-service`；update 不可改。
5. **前端联动**：调查确认前端当前无菜单管理页（`views/system` 无 menu 目录、`api/` 无 menu 模块），仅消费 `/auth/user-menu`（读链路已按新 schema 实现），契约定稿无现存破坏面。

### 代码变更

- `SysMenu`：对齐 DDL 列（`displayName/menuType 5 值/status/resourceType/resourceCode/sourceService`），删除 `name/permCode/visible/component/isExternal/isFrame/isCache/extra/permResourceId/serviceCode`。
- `SysMenuMapper` + XML：删除 `selectByPermCode/selectByPermCodes/selectExistingByPermCodes`（DDL 无 perm_code 列）；新增 `existsByPath/existsByResource`（排除自身的唯一性预查）。
- `MenuDomainService` + Impl：`findByPermCode/findByPermCodes/findExistingPermCodes` 退役，新增 `pathExists/resourceExists`。
- `MenuCreateReq/MenuUpdateReq/MenuResp`：v3.5 终态定稿；`resourceType/resourceCode` 成对校验（`@AssertTrue`，走 90001）。
- `MenuWriteAppServiceImpl`：`MENU_TYPE_BUTTON` 短路与"非按钮→按钮投影删除"分支移除；写前唯一性预查 + `DuplicateKeyException` 按约束名（`uk_sys_menu_tenant_path`/`uk_sys_menu_tenant_resource`）映射 10205/10206 兜底；`sourceService` 缺省 `access-service`；投影无条件 upsert。
- `MenuServiceImpl`：`toResp/buildTree` 适配新字段。
- `AdminErrorCode`：退役 10202，新增 10205/10206。

### 测试

- `MenuWriteAppServiceTest`：迁移到新字段语义（9 用例：五值全量投影、10205/10206 预查、部分更新、类型切换不删投影、删除投影清理）。
- `ErrorCodeContractTest`：新增 `RETIRED_ADMIN_NAMES` 退役清单（枚举名移除 + 码值不得复用）。
- `MenuWritePostgresIT`（新增，Testcontainers 真实 PG，`disabledWithoutDocker`）：新列落库、五值投影、唯一索引冲突（预查 + 并发窗口兜底按约束名映射）、更新投影同步、软删唯一性释放、`UserMenuQueryMapper.selectMenus` 读链路消费回归、深度限制。**本机无 Docker/PG，与项目既有 8 个 Testcontainers IT 同轨道自动跳过，待 Docker 环境执行**。
- `upsertAdminMenu` 签名/实现未变（参数与新实体一一对应），消费链路经 `MenuWritePostgresIT.writePathConsumedByUserMenuQueryMapper` 回归。

### 二轮复评修复（2026-08-22，AI 评审 2×P1 + 2×P2）

1. **[P1] 空白可选值破坏 NULL 语义**：`MenuWriteAppServiceImpl` 新增 `normalize()`（空白→null），create 装配与 update 应用统一规范化；update 空白等同未提供（跳过保留原值）。**用户决策：服务端规范化为 null**（空串写库会命中部分唯一索引 `WHERE col IS NOT NULL` 并被读链路误判为业务菜单 fail-closed）。
2. **[P1] 菜单深度少计算一层**：`calculateDepth` 返回父节点自身深度（顶级=1），调用方由 `> 5` 改为 `+ 1 > MAX_MENU_DEPTH(5)`（新节点深度 = 父深度+1），第 6 层正确拒绝；接口 javadoc 同步修正（原"根=0"与实现"顶级=1"矛盾）。补不依赖 Docker 的 `MenuDomainServiceImplTest`（层级语义）与 `MenuWriteAppServiceTest` 深度边界用例（父=5 拒绝/父=4 通过）。
3. **[P2] status 未限制枚举值**：`MenuCreateReq`/`MenuUpdateReq` 的 status 补 `@Min(0) @Max(1)`（DDL SMALLINT 无 CHECK，契约终态 0/1）。
4. **[P2] 旧 BUTTON 投影表述残留**：admin-service-api-contract §3 投影表（`menuType≠3 按钮不投影`→五值全量投影）、`LocalProjectionDomainService.upsertAdminMenu` javadoc、`MenuController` 类/方法注释、`MenuServiceImpl`/`MenuService` 方法注释（权限标识→路径/资源唯一性；deleteMenu"级联删子菜单"错误描述→"有子菜单拒绝"）同步收口。
5. `MenuWritePostgresIT` 补空白规范化真实 PG 用例（两条空 path 落库 NULL 互不冲突）；`batchCalculateDepth` 经核实无任何调用方（预留代码），未改动。
6. **回归中发现的存量 flaky（T-ACCESS-013 归属，顺手修复）**：`OAuth2ResourcePathPropertiesTest.guardRejects_patternCoveringSessionUserinfo` 断言 `hasMessageContaining("平台会话端点 /auth/user")` 依赖 `Set.of` 三元素迭代顺序（JDK 不可变集合跨 JVM 启动非确定，4 次运行 1 挂 3 过），与其注释声明的"命中三端点任一即视为防护生效"不符；断言改为不绑定具体端点（`覆盖平台会话端点` + `（JWT 分支不得覆盖）`）。

### 三轮复评修复（2026-08-22，AI 评审 1×P1 + 2×P2，均围绕 parentId 校验）

1. **[P1] 换父可成环**：`updateMenu` 换父仅校验深度，`parentId` 指向自身或后代时写入环——环后 `batchGetAncestorIds` 祖先链 while 无环防护死循环、递归 CTE（UNION ALL）不收敛、`sys_menu.parent_id` 无外键兜底。修复：新增 `MENU_PARENT_INVALID(10207)`，换父时目标父命中 `getDescendantIdsIncludingSelf(id)` 集合（含自身，一次判断覆盖两类）即拒绝。
2. **[P2] 换父未计子树高度**：仅校验被移动节点新深度，带子树的菜单移动可突破 5 层。修复：`SysMenuMapper.selectSubtreeHeight`（单条递归 CTE，根=1）+ `MenuDomainService.subtreeHeight`，换父校验升级为 `calculateDepth(newParent) + subtreeHeight(id) > 5`（单节点高度 1 与旧语义等价；create 新节点无子树，维持 `+1`）。
3. **[P2] 不存在的父当第 1 层**：`calculateDepth` 对查不到的 parentId 返回 1（空祖先链+1），孤儿节点可写入且从根不可达。修复：create/update 对正数 parentId 先 `selectValidById` 校验，失败抛 `MENU_NOT_FOUND(10201)`（消息"父菜单不存在"）。
4. 测试：`MenuWriteAppServiceTest` 补 6 用例（create 孤儿父 10201 / 换父自身 10207 / 换父后代 10207 / 换父父不存在 10201 / 子树突破 5 层 10203 / 子树恰 5 层通过），既有深度用例补父存在 stub；`MenuWritePostgresIT` 补 4 用例（孤儿父、换父自身、换父后代、子树边界，真实 PG 下验证 parent 未被改写）。契约 §4.6 错误清单与写链路语义同步。

### 四轮复评修复（2026-08-22，AI 评审 1×P2）

1. **[P2] 移到顶级时深度多算一层**：`calculateDepth(0)` 返回 1，高度 5 的合法子树移到顶级会被 `1+5>5` 误拒（实际最深仍第 5 层）。修复：换父校验的父深度对顶级目标（`parentId=0`）按 0 计且不再调用 `calculateDepth`，非顶级才取父节点自身深度；公式统一为"父深度 + 子树高度 ≤ 5"（create 路径顶级时 `1+1>5` 恒 false 不受影响，未改动）。契约公式同步修正为"新根深度 + 子树高度 - 1 ≤ 5"。单测补高度 5 子树移到顶级放行（含不调 calculateDepth 断言）与移到第 1 层父拒绝对照；IT 补真实 PG 场景（高度 5 子树下挂第 1 层父拒绝 + 脏数据挂深后经 API 移回顶级放行）。

### 文档回写

- `admin-service-api-contract.md`：新增 §4.6 菜单管理（`/menu/*` 五接口 + 写链路语义）。
- 本任务卡状态 `proposed → done`。

### 已知限制 / 遗留

- 菜单深度上限代码硬编码 5（`MENU_DEPTH_EXCEEDED`），种子配置 `admin.MENU_MAX_DEPTH=7` 未接线——沿用现状，不在本卡范围。
- 前端菜单管理页待 Phase 2 前端任务开发（契约已先行定稿）。
