# 3.3 冲突规则页前端设计

> status: adopted
> 任务：T-FE-010（mock 驱动）
> 后端契约：api-contract.md §5.6（`conflict-rule/*` 含 detect；T-PERM-030 收口契约要点）
> 后端任务：T-PERM-030（depends_on 本任务，已收口 2026-08-30）
> last_reviewed: 2026-09-02（T-FE-020 联调收口：前端 API 路径修正 + Gateway +5 端点注册 + mock 退役；§4 🔧 六项全收口 + §5 权限接线四档类型级口径——2026-08-30）

> **联调注记（T-FE-020，2026-09-02）**：本页 6 端点全部经 Gateway 真实链路收口。联调发现前端 `api/conflict-rule.ts` 仍用裸 `/api/perm/conflict-rule/*`（T-FE-041 全局切 Gateway 外部路径时漏改本页；Gateway 仅路由 /admin/**、/perm/**，裸路径必 404）——已统一修正为 `/perm/api/perm/conflict-rule/*`。list/create/update/remove/detect +5 端点补注册 bootstrap 清单（detail 本页不消费未注册）；引用数据 abstract-role/list +1 注册（T-FE-016 登记的届时事项），type-definition/list 与 operation-permission/list 先在册。逐 DTO 比对零漂移；CONFLICT_RULE 四档固定图原持（T-PERM-030 预置，零新增）。

## 1. 背景

冲突规则页：角色互斥 / 权限互斥规则 CRUD + 冲突检测。

- `ROLE_MUTEX`（角色互斥）：两个角色不能同时授予同一用户，字段 `firstAbstractRoleId` + `secondAbstractRoleId`
- `PERM_MUTEX`（权限互斥）：两个操作权限不能同时授予，字段 `firstOperationPermissionId` + `secondOperationPermissionId` + `resourceTypeValue`

布局：单表格 CRUD（对齐 permission-condition 范式）+ 表单弹窗（冲突类型切换动态字段）+ 检测对话框。

## 2. 布局

```
┌─ PureTableBar ──────────────────────────────────────────────┐
│ [类型筛选] [关键词] [重置]          [冲突检测] [新增规则]    │
├─────────────────────────────────────────────────────────────┤
│ 冲突类型 │ 规则内容         │ 描述 │ 创建时间 │ 操作        │
│ 角色互斥 │ 基础用户↔高级用户 │ ...  │ ...      │ 编辑 删除   │
│ 权限互斥 │ 创建↔删除 [MENU]  │ ...  │ ...      │ 编辑 删除   │
└─────────────────────────────────────────────────────────────┘
```

- 页面层：`display: flex; flex-direction: column; height: 100%`（对齐 permission-condition）
- 表格滚动：`:deep(.el-table__body-wrapper) { max-height: calc(100vh - var(--table-offset)) }`
- 覆写 layout margin：`div.conflict-rule-page.main-content { margin: var(--space-3) }`（特异性 0,2,1）

## 3. 字段与交互

### 表格列

| 列 | prop | 渲染 |
|----|------|------|
| 冲突类型 | conflictType | el-tag（ROLE_MUTEX=warning / PERM_MUTEX=danger） |
| 规则内容 | ruleContent | `对象A ↔ 对象B` + PERM_MUTEX 追加资源类型 tag |
| 描述 | description | 文本，空显示 `-` |
| 创建时间 | createdAt | 等宽字体 |
| 操作 | operation | 编辑 / 删除（权限门控） |

**规则内容名称解析**（设计 §Q3）：Resp 只返回 ID，前端加载引用数据建立映射：
- `roleMap`：getRoleList（BASIC_ROLE + GROUP_ROLE，按 hasNext 循环拉全分页——后端单页上限 200，只取首页会截断超页角色）→ id→name
- `operationMap`：getOperationList → id→name
- `resourceTypeMap`：getTypeDefList 筛 `resource_type` → typeValue→name
- 三路请求独立成败（allSettled），单路失败不清空其余成功路；映射缺失回退 `#ID`

### 表单（ConflictForm.vue）

- 冲突类型单选（角色互斥 / 权限互斥），切换时清空对侧字段
- ROLE_MUTEX：两个角色选择器（filterable，显示 `名称（roleTypeCode）`）
- PERM_MUTEX：两个操作权限选择器（按 resourceTypeCode 分组 el-option-group）+ 资源类型选择器（可空=全部）
- 描述文本域（可空，maxlength 512）
- 校验：对应类型字段必选 + 两个对象不能相同

### 检测对话框（DetectDialog.vue）

- el-alert 标注"仅检测权限互斥（PERM_MUTEX）"--后端 detect 仅支持操作权限对
- 两个操作权限选择器 + 资源类型选择器（可空）
- 检测按钮 → 调用 `detectConflictRule` API
- 结果：`conflictDetected`（success/error alert）+ `matchedRules[]`（对象对 + 资源类型 + 描述）
- 双向匹配：A-B 与 B-A 视为同一冲突（后端语义）
- addDialog `hideFooter: true`，用户用右上角 X 关闭

## 4. API 核对（基线：mock 请求/响应）

| 端点 | 请求 | 响应 | 状态 |
|------|------|------|------|
| `POST /conflict-rule/list` | EmptyReq | ItemsResp<ConflictRuleResp> | ✅（后端补类型级 VIEW 门禁 + Resp 补 updatedAt，T-PERM-030） |
| `POST /conflict-rule/detail` | IdReq{id} | ConflictRuleResp | ✅（后端补 VIEW 门禁；查不到 20020，T-PERM-030） |
| `POST /conflict-rule/create` | ConflictRuleReq | ConflictRuleResp | ✅（description ≤512 校验补齐，T-PERM-030） |
| `POST /conflict-rule/update` | ConflictRuleUpdateReq{id,...} | ConflictRuleResp | ✅（updatedBy 审计补齐，T-PERM-030） |
| `POST /conflict-rule/remove` | IdsReq{ids} | Void | ✅（响应 data=null；类型级 DELETE 全有或全无 + 幽灵 id 幂等跳过，T-PERM-030） |
| `POST /conflict-rule/detect` | ConflictRuleDetectReq | ConflictDetectResp | ✅（后端补类型级 VIEW 门禁，T-PERM-030） |

### 🔧 登记T-PERM-030（六项全收口，2026-08-30）

- detail/update/remove 用内部主键 id —— **评估定案维持 id**：冲突规则无业务键（type+对象对+rtv 为复合语义身份，无单列 code 可切，与 resource/operation/condition 不同；造 code 列属过度设计）
- list 无分页无筛选（全量），后端 listConflictRules 无 VIEW 校验 —— **已补**：读三端点（list/detail/detect）类型级 CONFLICT_RULE:VIEW 门禁（经决策；CONDITION「读不设门禁」定案依据是授权页依赖条件列表，冲突规则无此跨页依赖）；list 维持全量不分页（量小定案）；bootstrap 固定图补 CONFLICT_RULE 四档 + CONDITION 写三档（空库死锁防护，CONDITION 为 T-PERM-029 遗漏同款缺口顺带补）
- Resp 缺 updatedAt —— **已补**（create=createdAt、update 刷新；update 同步补 updatedBy 审计）
- `ConflictRuleDetailReq`（conflictRuleId）死代码 —— **已删除**（Controller 用 IdReq{id}）
- conflictType 实体注释（MUTEX_OP/MUTEX_ROLE）与 enum 不一致 —— **已修正**（对齐 ROLE_MUTEX/PERM_MUTEX）
- detect 无权限校验 —— **已补类型级 VIEW**（matchedRules 透出完整规则数据，与 list 同级敏感；经决策）

另随 T-PERM-030：写门禁从「编码轨传内部 id 的实例级」收窄为类型级（CONFLICT_RULE 无实例投影，ID 空间错位废弃，同 T-PERM-029 CONDITION 口径，CONDITION 侧投影已随 T-PERM-048 落地（仅 MANAGED），CONFLICT_RULE 维持类型级——投影如需另立任务）；detail 查不到从 data:null 收紧为 20020；update 从 UpdateChain 改 UpdateEntity 强制写列（T-PERM-028 extraClear 同款标准方式，语义不变）；mock 对齐后端错误码（404→20020、409→20032、remove 返 data=null）。

### ✅ 本次修复（T-PERM-030 P1，2026-07-11）

后端 `ConflictRuleAppServiceImpl` + `PermissionConflictRuleMapper.xml` + schema 联动修复 4 项 P1：

- **update 清空字段失效**：`updateConflictRule` 改用 `UpdateChain` 全量覆盖（按 conflictType 写入对应字段集，对侧强制 null）；PERM_MUTEX 下 `resourceTypeValue` 直接用 req 值（null=清空"全部"）。解决原 `if(field!=null)` 语义无法清空字段的问题（类型切换脏数据 / 资源类型清空无效）。
- **update 全量替换语义**：`ConflictRuleUpdateReq.conflictType` 加 `@NotBlank`，javadoc 明确 PUT 语义（须传完整字段集），rtv 显式传（null=清空）。解决"仅更新描述致 rtv 意外清空"的契约风险。
- **detect 漏报 NULL 全局规则**：Mapper.xml `selectByTenantAndResourceType` SQL 改为 `AND (resource_type_value = X OR resource_type_value IS NULL)`，对齐 schema「NULL=所有」语义。
- **去重不一致**：schema `uk_conflict_rule_perm` 加 `resource_type_value` 列（允许同操作对不同资源类型）；后端新增 `isDuplicate` 业务去重（create/update 调用，双向匹配 + rtv 区分，`Objects.equals(null,null)` 弥补 PG 唯一索引 `NULL!=NULL` 缺口）；create/update 规范化 `first<second` 顺序（对齐 schema 注释）。
- 新增错误码 `CONFLICT_RULE_DUPLICATE(20032)`。

## 5. 权限接线

资源类型 `CONFLICT_RULE`，读 VIEW + 写 CREATE/UPDATE/DELETE **四档独立**（非 CREATE+MANAGE，对齐后端；T-PERM-030 收口口径——读三端点 list/detail/detect 与写三档均为类型级）：

| perm 串 | 门控 | 后端校验 |
|---------|------|----------|
| `CONFLICT_RULE:VIEW` | 路由可达性 + 列表加载 | ✅ 类型级（list/detail/detect，T-PERM-030 补齐） |
| `CONFLICT_RULE:CREATE` | 新增按钮 | createConflictRule 校验（类型级） |
| `CONFLICT_RULE:UPDATE` | 编辑按钮 | updateConflictRule 校验（类型级，T-PERM-030 收窄） |
| `CONFLICT_RULE:DELETE` | 删除按钮 | deleteConflictRulesByIds 校验（类型级全有或全无，T-PERM-030 收窄） |

- SSOT：`views/system/conflict-rule/utils/perms.ts`（CONFLICT_RULE_PERMS / CONFLICT_RULE_PERM_LIST / CONFLICT_RULE_VIEW_PERMS）
- 路由 `meta.auths`：`[...CONFLICT_RULE_PERM_LIST]`
- mock 角色矩阵：admin 全权；sec（安全管理员）VIEW+CREATE+UPDATE+DELETE；hr/auditor 只读 VIEW
- detect 按钮复用 VIEW 门控（后端 detect 已补同款类型级 VIEW 校验，T-PERM-030）
- bootstrap 固定图持 CONFLICT_RULE 四档类型级不可转授（空库授予起点，T-PERM-030 补入）

## 6. 组件

| 组件 | 路径 | 职责 |
|------|------|------|
| ConflictForm | components/ConflictForm.vue | 新增/编辑表单（冲突类型切换动态字段） |
| DetectDialog | components/DetectDialog.vue | 冲突检测对话框（操作权限对 + 结果展示） |
| useConflictRule | utils/hook.ts | 列表/CRUD/detect/引用数据映射 |
| types | utils/types.ts | CONFLICT_TYPE_OPTIONS / ConflictFormData / 工厂 |
| perms | utils/perms.ts | 权限码 SSOT |

### Step 1.5 组件识别

- Diff 对比面板候选：本页无 diff 场景，不抽取
- 操作权限分组选择器（el-option-group by resourceTypeCode）：ConflictForm 与 DetectDialog 各自实现（结构简单，不抽取共享）
- 名称解析映射（roleMap/operationMap/resourceTypeMap）：hook 内聚，不抽取

## 7. mock 种子（已随 T-FE-020 退役删除；下表仅历史对照）

对齐 resource-operation / type-def mock 种子 ID（名称映射通过 API 加载建立；role-manage mock 已随 T-FE-016 退役，角色种子 ID 101/102/201/202 仅作历史对照）：

| id | 类型 | 对象对 | 资源类型 |
|----|------|--------|----------|
| 701 | ROLE_MUTEX | 101 基础用户 ↔ 102 高级用户 | - |
| 702 | ROLE_MUTEX | 201 核心开发组 ↔ 202 运维保障组 | - |
| 703 | PERM_MUTEX | 501 MENU:CREATE ↔ 504 MENU:DELETE | 1 MENU |
| 704 | PERM_MUTEX | 509 API:CREATE ↔ 512 API:DELETE | 3 API |

- create/update 去重：双向匹配（A-B 与 B-A 视为等价）
- detect：双向匹配 + resourceTypeValue 过滤（null=全部）

## 8. 核对清单

| # | 检查项 | 状态 |
|---|--------|------|
| 1 | P0 骨架（路由/标题/表格+表单弹窗+检测对话框） | ✅ |
| 2 | Step 1.5 组件识别（Diff 面板不适用，分组选择器内聚） | ✅ |
| 3 | API 核对（6 端点 ✅，🔧 六项已随 T-PERM-030 全收口，见 §4） | ✅ |
| 4 | P2 权限接线（hasPerms + 无权降级 el-empty） | ✅ |
| 5 | design_writeback（本文件 status: adopted） | ✅ |
| 6 | resourceTypeValue 映射（getTypeDefList 筛 resource_type，设计 §Q1） | ✅ |
| 7 | 表格名称映射（role/operation map，设计 §Q3） | ✅ |
| 8 | detect 仅操作权限对（设计 §Q2，UI 标注） | ✅ |
