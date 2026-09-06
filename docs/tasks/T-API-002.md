---
doc_type: task
id: T-API-002
title: perm-sdk 补齐 auth/query-resources 与 auth/query-scopes 调用入口
status: done
plan: docs/plans/design-audit-followup-plan.md
domain: cross-service
design_refs:
  - docs/design/permission-center/core-flows.md#§15
  - docs/design/permission-center/api-contract.md
depends_on: []
blocks: []
acceptance:
  - "DTO 公共化与内部 id 全族裁剪（2026-09-05 定案全裁；**2026-09-06 用户决策扩大到 check 族三接口**）：QueryResourcesReq/Resp、QueryScopesReq/Resp 的稳定部分迁入 perm-common（access-service 改 import，HTTP JSON 面同步收窄）；内部数据库 id 字段族全数从对外响应裁剪——QueryScopesResp.parentPermissionIds、ScopeGroup.matchedRoleIds/matchedPermissionIds/dependOnPermissionIds、ResourceEntry.matchedRoleIds/matchedPermissionIds（均为 role/role_resource_permission 内部行 id，与 core-flows §15「不要求/不泄漏内部数据库 ID」口径对齐）+ AuthCheckResp/BatchAuthCheckResp.AuthCheckItemResult 的 matchedRoleIds/matchedPermissionIds + CheckInterfaceResp.MatchedResource 的 resourceId/matchedRoleIds/matchedPermissionIds（resourceId=resource_entity 行 id）；前端排查页同端点复用（api-contract §6.7），frontend perm-scope.ts 类型与排查页展示消费面**同批改造**（排查页 2026-09-06 用户定案暂停待重做 T-FE-043，仅编译一致最小改动：类型字段清除 + 「主权限ID」栏删除）；api-contract 契约同步回写"
  - "PermissionFeignClient 增加两个 POST 方法（照 check/batch-check 现成样式）：/api/perm/auth/query-resources、/api/perm/auth/query-scopes，沿用既有服务身份拦截器，不新增 SDK 抽象层"
  - "PermissionFeignClientContractTest 端点清单同步：contractPathsAreFrozen 方法计数 16→18 一并更新 + 两个新端点回归锁（路径存在性 + DTO 字段快照防漂移）；access-service 侧补线格式字段快照与 check 族双副本同形回归锁（CheckFamilyWireShapeTest）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-06
---

# T-API-002 perm-sdk 补齐 auth/query-resources 与 auth/query-scopes 调用入口

> 状态：in-progress（2026-09-06 进入执行；前端排查页面暂停分支见下方执行期决策）
> 依赖：无；SNAPSHOT 链注意 perm-common 改动须先 install 再编译下游（AGENTS.md 构建陷阱）

## 背景

core-flows §15「SDK 可接入」检查点承诺四件套（check/batch-check/query-resources/query-scopes），服务端端点齐全（PermAuthController），但 SDK `PermissionFeignClient` 只有前两个，且四个 Query* DTO 锁在 access-service 内部包。接入方拉取「用户可见资源集合」（query-resources）与「数据范围四态」（query-scopes，DENIED/INSTANCE/ALL/EMPTY）是数据权限的运行时消费端——SDK 缺口等于业务服务接不上数据权限，只能手写 HTTP + 自造 DTO 副本（制造漂移面）。

## 设计口径（2026-09-05 定案；2026-09-06 执行期两次用户决策）

- 最小补齐：DTO 稳定部分公共化 + 两个 Feign 方法 + 契约测试，无新抽象层。
- **内部 id 字段族全裁**：同端点被管理排查页复用（§6.7），裁剪前后端同批——排查页展示形态不新增后端字段，按「去掉内部 id 展示 + 类型面清除」落地（页面已暂停，见下）。
- `effective-permissions` 是管理端排查视图（分页、给人看），不替代上述运行时能力。
- 不含 example-service 演示改造（如需另开小项）。

### 执行期用户决策（2026-09-06，已登记 decision-registry）

1. **裁剪面扩大到 check 族三接口**：check/batch-check/check-interface 响应的 matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId 一并裁剪；explain 内部消费改走引擎 PermResult 内部载体（`ExplainCheckOutcome`），§6.8 explain 响应字段不动（管理端排查端点保留）。
2. **权限排查页暂停待重做**（T-FE-043 登记重做事项）：前端仅做编译一致最小改动——permission-query.ts/perm-scope.ts 类型字段清除 + ScopeMatrixPanel「主权限ID」栏删除，不投入页面改造。
3. 零调用转换器 `PermResultUtils.toQueryResourcesResp` 按零调用删除先例处置（方法+单测删除，收口汇报）。

## 范围

- perm-common DTO 迁移（Query* 四件）+ 内部 id 字段族裁剪（Query* 六字段 + check 族扩大面）；
- PermissionFeignClient 两方法 + 契约测试（计数 16→18）+ 回归锁（SDK 侧 + access-service 侧 CheckFamilyWireShapeTest 双副本同形锁）；
- 前端排查页编译一致最小改造（页面暂停，重做登记 T-FE-043）；
- api-contract 回写（§5.7 SDK 入口清单注记、§6.1/§6.2/§6.6/§6.7 裁字段口径与前端暂停注记）。

## 完成记录（2026-09-06 收口）

- **DTO 公共化**：QueryResourcesReq/Resp、QueryScopesReq/Resp 迁入 perm-common（access-service 本地四件删除、改 import；HttpApiPathSnapshotTest 签名快照 FQN 同步）。
- **内部 id 字段族全线裁剪**：原定案六字段 + 执行期用户决策扩大面（AuthCheckResp / BatchAuthCheckResp.AuthCheckItemResult 的 matchedRoleIds/matchedPermissionIds；CheckInterfaceResp.MatchedResource 的 resourceId/matchedRoleIds/matchedPermissionIds）；check 族 access-service/perm-common 双副本同批裁剪。explain 内部改用 ExplainCheckOutcome 私有 record 直接消费 PermResult（reason fallback 与 matched 集合语义与旧载体逐一等价），§6.8 explain 响应字段不动。
- **SDK 补齐**：PermissionFeignClient +queryResources/queryScopes（照 check/batch-check 样式）；契约测试 16→18 + query/check 两族 DTO 字段快照锁。
- **回归锁**：access-service 新增 CheckFamilyWireShapeTest（query/check 族线格式字段快照 + check 族双副本同形 + 五字段防回潮负向锁，旧实现下必红）。
- **前端**：排查页暂停（T-FE-043 登记），仅编译一致最小改动——permission-query.ts / perm-scope.ts 类型清除 + ScopeMatrixPanel「主权限ID」栏删除（三栏改两栏）。
- **顺带清扫**：零调用转换器 PermResultUtils.toQueryResourcesResp + 其单测删除（零调用删除先例）；PermissionViewAppServiceImpl 死 import AuthCheckReq 删除。
- **设计回写**：api-contract §5.7/§6.1/§6.2/§6.6/§6.7 + frontmatter；core-flows §10.1 步骤 4 + §15「不泄漏」口径 + frontmatter；design/frontend/permission-query.md Tab2 / 核对表第 6 行 + frontmatter；decision-registry 当轮两定案；T-FE-043 立卡 + 看板 / 计划同步。
- **验证**：perm-common 先 install 后全模块 mvn test 零失败（access-service / starter / gateway / example-service，以各模块 surefire 报告为准）；前端 typecheck + vitest 零失败。双轨评审（代码轨 / 文档轨）零 P0/P1；P2×1（core-flows §10.1 残留）+ P3×7 全部核实属实并当场修复，修复后回归锁复跑通过。
- **已知观察（范围外，未处置）**：interface-snapshot 的 ApiPermissionEntry.conditionId 与 query-permission-tree 的 TreeNode.resourceId 仍暴露内部行 id（前者 Gateway 快照匹配消费、后者管理端树通道，均非 SDK 四件套）——如需口径收严另行登记。

