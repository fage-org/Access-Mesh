---
doc_type: task
id: T-ADMIN-021
title: org-tree 扩展 includePositions（组织+岗位一体树，授权页主体树数据源）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: admin-service
design_refs:
  - docs/design/frontend/permission-grant.md#§9
  - docs/design/services/admin-service-api-contract.md#§4.2.1
depends_on: []
acceptance:
  - "org-tree 接口新增 includePositions 参数（默认 false 兼容现有调用）：true 时返回组织树 + 岗位节点（岗位作为所属组织的子节点，同一树结构，不分页）；响应 `PermResult<ItemsResp<OrgResp>>{data:{items:[...]}}`（P1-3；复用 perm-common 泛型替代原定稿命名的 OrgItemsResp——用户决策，线格式不变）"
  - "岗位节点复用现有 `orgType=2` 字段区分（组织 orgType=1，与 PositionTab 一致；不引入新字段）"
  - "数据源门禁对齐现有 org-tree（ORG:VIEW / ORG:VIEW_POSITION，T-ACCESS-018 收敛后类型码）；否定性验收：仅 ORG:VIEW（无 VIEW_POSITION）的调用者响应中不包含任何岗位节点（orgType=2）；故障验收：本地权限引擎技术故障时 org-tree 返回 SystemException 错误响应（统一响应业务码 99999 标识故障），不得返回裁剪后的树（岗位裁剪不得静默降级，P2-1；归并后无远程调用，原 Feign 不可达语义已失效）"
  - "design_writeback：docs/design/frontend/permission-grant.md §9 组织主体适配器描述确认；docs/design/services/admin-service-api-contract.md §4.2.1 includePositions 契约（P1-6）+ 债务①②销账"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-03
---

# T-ADMIN-021 org-tree 扩展 includePositions

> 状态：done（2026-09-03 收口；首期（T-FE-018 角色联调）不依赖本任务，本任务随 T-FE-037 组织入口解锁需求而实施）
> 复杂度：🟡 中
> 关联前端：T-FE-037（组织入口左栏主体树消费本接口）

## 背景

权限授予页（T-FE-036）组织入口需要"组织 + 岗位"一体左栏主体树。现状：

- `org-tree`（orgType=1）只返回普通组织树（`ReOrgTreePanel` 固定 orgType=1）；
- 岗位由 `getOrgPage({orgType:2})` 分页加载（`PositionTab` 模式），非树结构且分页上限 100。

2026-08-01 评审 P2-3 定案：**新增后端一体树接口（不分页）**，前端一次拉取，避免岗位分页遍历与静默漏节点。

> **重基线（T-ACCESS-012，2026-08-22）**：落点为 access-service admin 域单模块；门禁经本地 `PermQueryEngine`（`AdminPermissionValidator`，无 Feign 自调用）。

## 设计口径（实施前用户决策，2026-09-03）

1. **契约债务①全顺带**（用户决策）：`OrgQuery` 一并补齐 `operationCode` 与 `treeConfigId` 字段，"CREATE+混合树拒绝"规则不再空转。
2. **treeConfigId 缺省 = 默认树子树**（用户决策，契约字面）：不传 → 只返回默认树（is_default）子树，多树租户下其他树须显式传 id；无默认配置 → 11001 fail-closed（对齐 resolver「禁止静默 fallback」既有口径）。
3. **CREATE × treeConfigId 同传 → 拒绝 10008**（用户决策）：与 P2-1（CREATE+includePositions 拒绝）同一严格度。
4. **响应包装复用 `ItemsResp<OrgResp>`**（用户决策）：perm-common 泛型与 /role/list、/user-role/list 同款，线格式与 P1-3 定稿完全相同，不新建 OrgItemsResp 类。
5. 实施按先例直接定的三点：门禁组合分发走 `OrgOperationCodeMapper.resolve(orgType, operationCode)`（单一事实源）；故障码 `GlobalErrorCode.SYSTEM_ERROR(99999)`（任务卡授权"按 ErrorCode 现行定义选取"，EXTERNAL_SERVICE_ERROR 远程语义已失效不复用）；操作者主体缺失归技术故障抛 SystemException（hasTypeLevel 的 false 仅限引擎成功响应且明确拒绝）。
6. **顺带契约失实修正**（发现即修，属「以契约为准」同族）：`orgName`/`parentOrgId` 原为死参数（实现完全忽略，契约声称可用）→ 修正为真实过滤（orgName=树剪枝保留祖先链；parentOrgId=配置子树内取该节点子树）；契约 OrgResp 表格 phone/email 陈旧行删除（字段早已移除）。
7. **过滤语义定案（用户决策）**：根存在性守卫（11002）基于未过滤全量——仅根真缺失/软删时触发；orgType/status 为**节点级内存过滤**（根不豁免，不匹配即从结果集剔除：查停用树 status=0 时启用根被滤即空树、orgType=2 无 parentOrgId 恒空树）；orgType 值域白名单 {1,2}（非法值 10008，防经 normalize 回退后空集绕过）。

## 实现要点（终态）

- `OrgQuery` record：+`operationCode`（VIEW/CREATE，缺省 VIEW，非法值 10008）/+`treeConfigId`/+`includePositions`。
- `AdminPermissionValidator.hasTypeLevel(String, String)`（接口新增，`AdminPermissionValidatorImpl` 实现）：仅引擎成功响应且 allowed=false 返回 false；引擎 RuntimeException 与主体缺失包装 `SystemException(99999)`；`checkAndThrow` 不动。
- `OrgServiceImpl.treeOrgs` 重写：
  - 参数校验（非法 operationCode / CREATE+includePositions / CREATE+treeConfigId → 10008；includePositions!=true 缺 orgType → 10107 保留）；
  - 门禁：混合树组织轨 `ORG:VIEW` + 岗位轨 `hasTypeLevel(ORG, VIEW_POSITION)`；单类型 `resolve(orgType, operationCode)`；
  - 树范围：`resolveTreeScope`（treeConfigId 查 id / 缺省默认树，缺失 11001；根组织已删 11002）+ 内存祖先链子树裁剪（步数上限防异常父环，根治归 T-PERM-044）；
  - 岗位裁剪先于名称过滤（仅 ORG:VIEW 调用者无任何 orgType=2 节点）；orgName 树剪枝（自身或后代命中保留）；parentOrgId 子树透视（不在范围内=空结果）；顶层=配置根单根。
- `OrgController.treeOrgs`：响应 `PermResult<ItemsResp<OrgResp>>`。
- 前端：`getOrgTree` 解包 `data.items`（调用方数组契约不变，OrgForm/UserDetailPanel 零改动）；`OrgQuery` 类型 +3 字段；`ReOrgTreePanel.loadTree` 补传 `treeConfigId`（配置切换真实生效，原「待后端补齐后此处补参」TODO 闭环）。
- Bootstrap 固定图零变更（/admin/org/tree 在册；ORG 全档 scopeAll 含 VIEW_POSITION/CREATE 已在图）。

## 验收标准

见 acceptance（全部达成，回归锁定见完成记录）。

## 已知边界（登记不修）

- orgType=2 单类型树查询无前端消费者且结构退化（岗位父节点均为组织，子树裁剪后仅直属根/父节点的岗位可见）——岗位树形态统一走 includePositions 混合树；契约 §4.2.1 已注记。
- 多树租户下不传 treeConfigId 只见默认树（用户决策语义）——存量 OrgForm/UserDetailPanel 不传参，行为从「全量森林」收窄为「默认树子树」；单默认树租户（当前全部环境）无差异。T-FE-037 组织入口显式传配置 id。

## 完成记录（2026-09-03）

1. **后端**：`OrgQuery` +3 字段；`AdminPermissionValidator`/`Impl` 新增 `hasTypeLevel`（fail-closed 故障语义，主体解析一并纳入统一包装）；`OrgServiceImpl.treeOrgs` 重写（参数校验含 orgType 白名单/组合门禁/配置子树裁剪/岗位裁剪/orgType+status 节点级内存过滤/orgName 剪枝/parentOrgId 透视；根存在性守卫基于未过滤全量）；`OrgController` 响应切 `PermResult<ItemsResp<OrgResp>>` 并清理未用导入；注入 `SysOrgTreeConfigMapper`。
2. **前端**：`api/user-manage.ts` getOrgTree 解包 items + `OrgQuery` 类型对齐（operationCode/treeConfigId/includePositions，orgType 条件必填可选化）；`ReOrgTreePanel` loadTree 补 treeConfigId（树配置切换真实生效）+ loadConfigs/loadTree 失败 catch 空态降级（无默认配置租户不空白面板）。
3. **回归锁定**：
   - `OrgTreeIncludePositionsPgIT`（新，18 用例，真实 PG+Redis+引擎+MockMvc 全链路）：兼容行为（orgType=1 单类型/items 包装/默认树单根/无岗位）、缺省 orgType 10107、混合树全权（岗位挂所属组织、无下级）、**否定性裁剪**（仅 ORG:VIEW → 零 orgType=2、组织轨完好）、无授权 403、**故障验收**（@SpyBean 引擎 VIEW_POSITION 判定注入 RuntimeException → 业务码 99999、data null、不返回裁剪树）、CREATE 语义（门禁真实生效/限默认树/×treeConfigId 10008/×includePositions 10008/非法值 10008）、treeConfigId（显式非默认子树/缺省默认树字面/不存在 11001）、无默认配置 11001、orgName 剪枝（祖先链保留/无命中分支剪除）、parentOrgId 透视（子树内保留/范围外空结果）、过滤语义守卫（status=0 根不匹配→空树且不误报 11002、orgType=2 无透视→空树——两用例经 stash 旧实现红证）、orgType=3/小写 operationCode 10008、根真软删 11002、岗位裁剪旁路锁（orgName 搜岗位名/parentOrgId=岗位 id → 仍零 orgType=2）。
   - `AdminPermissionValidatorImplHasTypeLevelTest`（新，3 用例）：返回值透传/引擎故障包装 99999/主体缺失 99999。
   - `HttpApiPathSnapshotTest` golden 行更新为 ItemsResp 包装。
4. **文档回写**：契约 §4.2.1 全字段落地+债务①②销账+orgName/parentOrgId 死参数修正注记+phone/email 陈旧行删除+orgType=2 退化注记；permission-grant.md §9 组织主体适配器标注已实现（组织入口联调归 T-FE-037）；两文档 last_reviewed 更新。
5. **回归**：后端全量 mvn test 绿（DualInstance 停 dev 后跑）；前端 vue-tsc 0 错 / vitest 202/202（基线不变）；重建库后浏览器冒烟（组织与用户页树正常渲染 + 混合树 curl 断言）。
6. **解锁**：T-FE-037（Phase 3 最后一项联调）后端依赖全部就绪。
