---
doc_type: design
title: 权限条件 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-30   # 2026-08-30 §8 全量收口 + 字段/API 表对齐（T-PERM-029 落地：detail/update/remove 切业务键 code、detail 查不到 20006、list 全量不分页设计定案、ConditionResp 补 updatedAt、remove 幂等静默跳过）；此前 2026-08-08 产品确认移除 CONDITION:VIEW 读取门禁
---

# 3.2 权限条件 前端设计

> 任务：T-FE-009（第 2 批末项，mock 驱动）
> 后端契约：api-contract.md §5.6（含 permission-condition 契约要点，T-PERM-029 收口）
> 后端任务：T-PERM-029（permission-condition/*，Phase 2，已收口 2026-08-30）

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
- ~~无权占位~~（**2026-08-08 产品确认移除：条件查看全租户开放，无读取门禁，列表始终可读**；本地过滤 keyword + enabled 保留）。

## 字段定义

### ConditionResp（对齐后端 ConditionResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键（授权链路 conditionId 引用；管理端点定位已切业务键 code，T-PERM-029） |
| code | string | 条件编码（业务键，uk tenant+code，创建后不可改） |
| name | string | 条件名称 |
| conditionRules | string | 条件规则 JSON 字符串，结构 {logic, items[]} |
| enabled | boolean | 是否启用 |
| gatewayEvaluable | boolean | 是否可下发 Gateway 评估（T-PERM-017） |
| description | string\|null | 条件描述 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间（T-PERM-029 补齐） |

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

- onMounted -> loadList（**无读取门禁，2026-08-08 产品确认：条件规则非敏感、全租户开放**）-> 本地过滤展示。
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

- ElMessageBox 确认 -> removeConditions([row.code]) -> loadList（T-PERM-029：按业务键批量，不存在的 code 静默跳过）。

### 规则摘要列

- `summarizeRules(conditionRules)` -> `AND · 2 项（日期范围、IP 白名单）`。
- el-popover hover 展开完整 JSON（pre 格式）。

## API 依赖

| 接口 | 方法 | 请求 | 响应 | 权限 |
|---|---|---|---|---|
| /api/perm/permission-condition/list | POST | EmptyReq | ItemsResp<ConditionResp> | 无读取门禁（产品确认，全租户开放） |
| /api/perm/permission-condition/detail | POST | ConditionDetailReq{conditionCode} | ConditionResp | 无读取门禁；查不到 20006（T-PERM-029） |
| /api/perm/permission-condition/create | POST | ConditionCreateReq | ConditionResp | CONDITION:CREATE |
| /api/perm/permission-condition/update | POST | ConditionUpdateReq{code,...} | ConditionResp | CONDITION:UPDATE（实例级） |
| /api/perm/permission-condition/remove | POST | ConditionRemoveReq{codes} | Void | CONDITION:DELETE（实例级，fail-closed） |

后端实现：ConditionController + ConditionAppServiceImpl。
conditionRules 评估：ConditionEvalUtils（perm-common，Gateway 与 access-service permission 域共享）。

## 组件结构

- `index.vue` - 页面壳 + PureTableBar + 弹窗调度。
- `components/ConditionForm.vue` - 条件元信息表单（code/name/enabled/gatewayEvaluable/description）+ 复用 `ReConditionEditor` 渲染条件规则（T-FE-024 抽取）。
- `utils/hook.ts` - 列表加载 + CRUD + 本地过滤。
- `utils/perms.ts` - 权限码 SSOT。
- `utils/types.ts` - 条件表单元信息类型（ConditionFormData）；规则模型（ConditionRules / 序列化 / 摘要 / 常量）已抽到 `@/utils/condition-rules`（T-FE-024）。
- `api/permission-condition.ts` - 类型 + API 函数。
- `mock/permission-condition.ts` - fake-server 路由（4 条种子：office-hours/corp-ip-only/temp-access/blacklist-vpn）。

### 可复用组件识别（T-FE-001 池）

- **权限条件选择器**：本页独立 CRUD。T-FE-024 曾抽取共享组件 `ReConditionPicker` + `ReConditionEditor`，供本页与权限授予页附加设置弹窗共用。2026-07-26 权限授予页 v1+v2 整体删除重做，`ReConditionPicker` 随之删除（仅服务权限授予页），`ReConditionEditor` 保留（本页 ConditionForm 继续使用）。两处共享同一规则模型 `@/utils/condition-rules`。

## 权限接线

### perms.ts SSOT（CONDITION 资源类型，三档独立非 MANAGE）

| 串 | 门控 | 按钮 |
|---|---|---|
| ~~CONDITION:VIEW~~（2026-08-08 产品确认移除：条件查看全租户开放，无读取门禁） | 路由可达（登录即可） | 列表展示 |
| CONDITION:CREATE | openCreate | 新增条件 |
| CONDITION:UPDATE | openEdit | 编辑 |
| CONDITION:DELETE | onDelete | 删除 |

> 与 RESOURCE/OPERATION 的 CREATE+MANAGE 两档不同，CONDITION 后端用独立的 CREATE/UPDATE/DELETE 三档（ConditionAppServiceImpl 对齐 `OperationCodeConstants.UPDATE`/`DELETE`，非 MANAGE）。

### 角色矩阵（mock/login.ts，2026-08-08 起 VIEW 列移除）

| 角色 | CREATE | UPDATE | DELETE |
|---|---|---|---|
| admin | ✓ | ✓ | ✓ |
| sec | ✓ | ✓ | ✓ |
| hr | - | - | - |
| auditor | - | - | - |

sec 负责条件定义（与 RESOURCE/OPERATION 同源），拥有 CREATE+UPDATE+DELETE；admin 全权；hr/auditor 无写权限（列表读取全租户开放，不再由 VIEW 列控制）。

## §8 核对清单（原登记 T-PERM-029）——已全数收口（T-PERM-029，2026-08-30）

| # | 项 | 原登记改造 | 收口结论 |
|---|---|---|---|
| 1 | detail 用内部主键 id | 切业务键 code | ✅ 已收口：`ConditionDetailReq{conditionCode}` 接线（DTO 早已定义未用），查不到 20006（原 data=null 宽松语义删除，对齐 resource-entity/detail 收紧定案） |
| 2 | update/remove 用内部主键 | 切业务键 code（批量按 code 列表） | ✅ 已收口：update `{code,...}`（定位键不可改）；remove `{codes:[...]}` 批量，不存在的 code 静默跳过（幂等，对齐 resource-entity/remove）；孤儿方法 deleteCondition（单删）无调用方已删除（T-PERM-034 revokePermissions 先例） |
| 3 | list 无分页无筛选 | 补 ConditionListReq（keyword/enabled/pageNum/pageSize） | **设计定案反转：全量不分页**——条件模板数量有界（租户内几十个量级，非流水表），与 T-PERM-026 domain-config / T-PERM-027 service-config「量小不分页」双先例一致；keyword/enabled 过滤由前端本地完成（本页已实现），后端零改动 |
| 4 | list/detail 无 VIEW 校验 | ~~补 CONDITION:VIEW~~ | ❌ 2026-08-08 产品确认取消（读取全租户开放），维持无门禁（见上文） |
| 5 | ConditionResp 缺 updatedAt | 补 updatedAt 字段 | ✅ 已收口：Resp/前端类型/mock 均补齐（entity 列本就存在） |
| 6 | api-contract §5.6 缺字段契约 | 补字段表与请求示例 | ✅ 已收口：§5.6 增 permission-condition 契约要点块（业务键/20006/门禁/gatewayEvaluable 联合校验/幂等语义） |

前端已随 T-PERM-029 同步切业务键（api/hook/index/mock + 授权页契约 spec 两用例订正），Phase 3 联调（T-FE-020）无需再动本页接口层。
