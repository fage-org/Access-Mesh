# 3.3 冲突规则页前端设计

> status: adopted
> 任务：T-FE-010（mock 驱动）
> 后端契约：api-contract.md §5.6（`conflict-rule/*` 含 detect）
> 后端任务：T-PERM-030（depends_on 本任务）

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
- `roleMap`：getRoleList（BASIC_ROLE + GROUP_ROLE）→ id→name
- `operationMap`：getOperationList → id→name
- `resourceTypeMap`：getTypeDefList 筛 `resource_type` → typeValue→name
- 映射缺失回退 `#ID`

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
| `POST /conflict-rule/list` | EmptyReq | ItemsResp<ConflictRuleResp> | ✅ |
| `POST /conflict-rule/detail` | IdReq{id} | ConflictRuleResp | ✅ |
| `POST /conflict-rule/create` | ConflictRuleReq | ConflictRuleResp | ✅ |
| `POST /conflict-rule/update` | ConflictRuleUpdateReq{id,...} | ConflictRuleResp | ✅ |
| `POST /conflict-rule/remove` | IdsReq{ids} | Void | ✅ |
| `POST /conflict-rule/detect` | ConflictRuleDetectReq | ConflictDetectResp | ✅ |

### 🔧 登记T-PERM-030

- detail/update/remove 用内部主键 id（冲突规则无业务键如 code，id 即唯一标识；与 resource/operation/condition 不同，切业务键诉求弱，登记后端评估）
- list 无分页无筛选（全量），后端 listConflictRules 无 VIEW 校验，种子可能缺失
- Resp 缺 updatedAt（只有 createdAt），🔧 后端补齐
- `ConflictRuleDetailReq`（conflictRuleId）为死代码，Controller 实际用 IdReq{id}，❌ 后端清理
- conflictType 实体注释（MUTEX_OP/MUTEX_ROLE）与 enum（ROLE_MUTEX/PERM_MUTEX）不一致，🔧 后端修正注释
- detect 无权限校验（public 方法），🔧 后端评估是否补 VIEW 校验

## 5. 权限接线

资源类型 `CONFLICT_RULE`，写权限 CREATE/UPDATE/DELETE **三档独立**（非 CREATE+MANAGE，对齐后端 ConflictRuleAppServiceImpl）：

| perm 串 | 门控 | 后端校验 |
|---------|------|----------|
| `CONFLICT_RULE:VIEW` | 路由可达性 + 列表加载 | 🔧 无（list/detail 未校验，登记 T-PERM-030） |
| `CONFLICT_RULE:CREATE` | 新增按钮 | createConflictRule 校验 |
| `CONFLICT_RULE:UPDATE` | 编辑按钮 | updateConflictRule 校验 |
| `CONFLICT_RULE:DELETE` | 删除按钮 | deleteConflictRule/deleteConflictRulesByIds 校验 |

- SSOT：`views/system/conflict-rule/utils/perms.ts`（CONFLICT_RULE_PERMS / CONFLICT_RULE_PERM_LIST / CONFLICT_RULE_VIEW_PERMS）
- 路由 `meta.auths`：`[...CONFLICT_RULE_PERM_LIST]`
- mock 角色矩阵：admin 全权；sec（安全管理员）CREATE+UPDATE+DELETE；hr/auditor 只读 VIEW
- detect 按钮复用 VIEW 门控（后端 detect 无独立权限校验）

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

## 7. mock 种子

对齐 role-manage / resource-operation / type-def mock 种子 ID（名称映射通过 API 加载建立）：

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
| 3 | API 核对（6 端点 ✅，🔧/❌ 登记T-PERM-030） | ✅ |
| 4 | P2 权限接线（hasPerms + 无权降级 el-empty） | ✅ |
| 5 | design_writeback（本文件 status: adopted） | ✅ |
| 6 | resourceTypeValue 映射（getTypeDefList 筛 resource_type，设计 §Q1） | ✅ |
| 7 | 表格名称映射（role/operation map，设计 §Q3） | ✅ |
| 8 | detect 仅操作权限对（设计 §Q2，UI 标注） | ✅ |
