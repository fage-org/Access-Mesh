---
doc_type: design
title: 权限条件 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-07-11
---

# 3.2 权限条件 前端设计

> 任务：T-FE-009（第 2 批末项，mock 驱动）
> 后端契约：api-contract.md §5.6（端点总览，字段契约缺失 🔧 登记T-PERM-029）
> 后端任务：T-PERM-029（permission-condition/*，Phase 2）

## 布局结构

单表格扁平 CRUD（非树），范式对齐 type-def / system-config：

```
┌─ permission-condition-page（height:100%，layout 加 .main-content margin）─┐
│ ┌─ table-wrap（flex:1）──────────────────────────────────────────────┐ │
│ │ PureTableBar                                                       │ │
│ │   #title: 搜索栏（keyword + enabled 筛选 + 重置，本地过滤）        │ │
│ │   #buttons: 新增条件（CONDITION:CREATE）                           │ │
│ │   pure-table:                                                      │ │
│ │     编码 | 名称 | 启用 | Gateway评估 | 规则 | 描述 | 创建时间 | 操作│ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

- 无分页：后端 list 返回全量 ItemsResp，前端本地过滤（keyword + enabled）。
- 表格滚动：`:deep(.el-table__body-wrapper) { max-height: calc(100vh - var(--table-offset)); }`。
- 无权占位：`canView=false` 时 `el-empty`「你没有查看权限条件的权限」。

## 字段定义

### ConditionResp（对齐后端 ConditionResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键（🔧 Phase 2 切业务键 code） |
| code | string | 条件编码（业务键，uk tenant+code，创建后不可改） |
| name | string | 条件名称 |
| conditionRules | string | 条件规则 JSON 字符串，结构 {logic, items[]} |
| enabled | boolean | 是否启用 |
| gatewayEvaluable | boolean | 是否可下发 Gateway 评估（T-PERM-017） |
| description | string\|null | 条件描述 |
| createdAt | string | 创建时间（后端 Resp 无 updatedAt 🔧） |

### conditionRules JSON 结构（对齐 ConditionEvalUtils）

```json
{
  "logic": "AND",
  "items": [
    { "type": "DATE_RANGE",   "params": { "start": "2025-01-01", "end": "2025-12-31" } },
    { "type": "TIME_RANGE",   "params": { "start": "09:00:00",   "end": "18:00:00" } },
    { "type": "IP_WHITELIST", "params": { "cidrs": ["192.168.1.0/24"] } },
    { "type": "IP_BLACKLIST", "params": { "cidrs": ["10.0.0.0/8"] } }
  ]
}
```

- `logic`：AND | OR，缺省 AND（VALID_LOGIC = {AND, OR}）
- 4 种预置类型（GATEWAY_PUSHABLE_TYPES 全覆盖）：
  - DATE_RANGE：params.start/end，`yyyy-MM-dd`
  - TIME_RANGE：params.start/end，`HH:mm:ss`（支持跨午夜）
  - IP_WHITELIST/IP_BLACKLIST：params.cidrs[]，CIDR 列表

### 表单字段（ConditionFormData）

code / name / enabled（开关）/ gatewayEvaluable（开关）/ description（textarea）/ rules（结构化，提交时 `serializeRules` 序列化为 JSON 字符串）。

## 交互流程

### 列表

- onMounted -> loadList（CONDITION:VIEW 短路）-> 本地过滤展示。
- 搜索：keyword（code/name 模糊）+ enabled（启用/停用）实时过滤，重置清空。

### 新增/编辑（弹窗 ConditionForm）

- code：create 可编辑 / edit 只读（业务键）。
- 条件规则可视化编辑器（Q1=A）：
  - logic 单选（AND/OR）。
  - items 行：[type 下拉] [params 动态表单] [删除]，`v-for :key="item._id"`（前端运行时 id，不进 JSON）。
  - params 动态：DATE_RANGE/TIME_RANGE -> start+end（date-picker/time-picker）；IP_* -> cidrs[]（tag 输入，回车/逗号/空格批量添加）。
  - + 添加条件项。
- gatewayEvaluable 开关暴露（Q2=暴露），开启时前端预校验 items type 全在白名单（对齐后端 `validateGatewayPushable`），4 类都在实际总能通过；保留以防未来扩展类型。
- 提交前校验：表单 rules + itemsValid（至少 1 项且 params 完整）+ gatewayPushableViolation。
- 提交：`serializeRules(rules)` -> createCondition/updateCondition。

### 删除

- ElMessageBox 确认 -> removeConditions([id]) -> loadList。

### 规则摘要列

- `summarizeRules(conditionRules)` -> `AND · 2 项（日期范围、IP 白名单）`。
- el-popover hover 展开完整 JSON（pre 格式）。

## API 依赖

| 接口 | 方法 | 请求 | 响应 | 权限 |
|---|---|---|---|---|
| /api/perm/permission-condition/list | POST | EmptyReq | ItemsResp<ConditionResp> | 🔧 无 VIEW 校验 |
| /api/perm/permission-condition/detail | POST | IdReq{id} | ConditionResp | 🔧 无校验 |
| /api/perm/permission-condition/create | POST | ConditionCreateReq | ConditionResp | CONDITION:CREATE |
| /api/perm/permission-condition/update | POST | ConditionUpdateReq | ConditionResp | CONDITION:UPDATE |
| /api/perm/permission-condition/remove | POST | IdsReq{ids} | Void | CONDITION:DELETE |

后端实现：ConditionController + ConditionAppServiceImpl。
conditionRules 评估：ConditionEvalUtils（perm-common，Gateway 与 permission-center 共享）。

## 组件结构

- `index.vue` - 页面壳 + PureTableBar + 弹窗调度。
- `components/ConditionForm.vue` - 表单 + 条件规则可视化编辑器（内联）。
- `utils/hook.ts` - 列表加载 + CRUD + 本地过滤。
- `utils/perms.ts` - 权限码 SSOT。
- `utils/types.ts` - 表单类型 + 常量 + serializeRules/parseRules/summarizeRules。
- `api/permission-condition.ts` - 类型 + API 函数。
- `mock/permission-condition.ts` - fake-server 路由（4 条种子：office-hours/corp-ip-only/temp-access/blacklist-vpn）。

### 可复用组件识别（T-FE-001 池）

- **权限条件选择器**：本页独立 CRUD + T-FE-014 权限授予页内联选择。本页内联实现条件规则编辑器，待 T-FE-014 推进时确认抽取为 `ReConditionPicker`。

## 权限接线

### perms.ts SSOT（CONDITION 资源类型，三档独立非 MANAGE）

| 串 | 门控 | 按钮 |
|---|---|---|
| CONDITION:VIEW | 路由可达 + loadList | 列表展示 |
| CONDITION:CREATE | openCreate | 新增条件 |
| CONDITION:UPDATE | openEdit | 编辑 |
| CONDITION:DELETE | onDelete | 删除 |

> 与 RESOURCE/OPERATION 的 CREATE+MANAGE 两档不同，CONDITION 后端用独立的 CREATE/UPDATE/DELETE 三档（ConditionAppServiceImpl 对齐 `OperationCodeConstants.UPDATE`/`DELETE`，非 MANAGE）。

### 角色矩阵（mock/login.ts）

| 角色 | VIEW | CREATE | UPDATE | DELETE |
|---|---|---|---|---|
| admin | ✓ | ✓ | ✓ | ✓ |
| sec | ✓ | ✓ | ✓ | ✓ |
| hr | ✓ | - | - | - |
| auditor | ✓ | - | - | - |

sec 负责条件定义（与 RESOURCE/OPERATION 同源），拥有 CREATE+UPDATE+DELETE；admin 全权；hr/auditor 只读 VIEW。

## §8 核对清单（🔧 登记T-PERM-029）

| # | 项 | 现状 | 改造 |
|---|---|---|---|
| 1 | detail 用内部主键 id | IdReq{id} | 切业务键 code（ConditionDetailReq 已定义 conditionCode 但 Controller 没用，schema uk 保证唯一） |
| 2 | update/remove 用内部主键 | conditionId/ids | 切业务键 code（批量按 code 列表） |
| 3 | list 无分页无筛选 | EmptyReq 全量 | 补 ConditionListReq（keyword/enabled/pageNum/pageSize） |
| 4 | list/detail 无 VIEW 校验 | 无 hasPermission | 补 CONDITION:VIEW 校验 + schema 种子预置 VIEW 操作位 |
| 5 | ConditionResp 缺 updatedAt | entity 有但 Resp 不返回 | 补 updatedAt 字段 |
| 6 | api-contract §5.6 缺字段契约 | 仅端点总览 | 补 ConditionResp/CreateReq/UpdateReq 字段表与请求示例 |

前端按现状（id 主键 + 本地过滤）实现，🔧 项归 T-PERM-029 Phase 2 后端收敛。
