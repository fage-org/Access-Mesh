---
doc_type: design
title: 6.2 系统配置页 前端设计
status: draft
domain: frontend
last_reviewed: 2026-07-01
---

# 6.2 系统配置页 前端设计

> 任务：T-FE-004（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.8（系统配置仅 3 行表格条目，无独立字段契约章节——🔧 登记 T-PERM-024）
> 参照范式：6.1 类型定义页（`type-definition.md`，PureTableBar 表格列表范式 + SSOT/降级/核对清单结构）

## 1. 页面定位

系统配置页管理 `system_config` 表的**租户级配置项**：扁平 `tenant_id + config_key + config_value(JSONB)` 键值表，每条是 `(configKey 唯一键, configValue JSON, description)`。

- **扁平 key-value 字典，非分组表单**：`system_config` 表无 `config_group` 字段（schema:635-656），设计为扁平键值存储。任务原标题「租户级配置分组表单」与实际后端契约不符，本页按实际扁平 key-value 字典实现。
- **无 is_system 字段**：与 `type_definition` 不同，`system_config` 无系统预置概念，所有配置项均为租户级可编辑。
- **无 status 字段**：无启停概念，本页无启停列/启停按钮。
- **save upsert 幂等**：后端仅 `list/detail/save` 三端点，`save` 按 `configKey` upsert（存在则 update 不存在则 insert），**无 create/update/remove 独立接口、无删除接口**。新建/编辑统一走 `save`。
- **configValue 是 JSON**：schema `config_value JSONB NOT NULL DEFAULT '{}'`，前端按 JSON 字符串编辑 + 提交前 `JSON.parse` 校验（对齐后端 `JsonValidationUtils.validateJson`）。

## 2. 布局结构

PureTableBar 表格列表范式（遵循 `frontend-layout-patterns`），与 6.1 类型定义页同源：

```
┌─ system-config-page（main-content）─────────────────────────┐
│ PureTableBar                                                 │
│  ├─ #title：keyword 输入 + 搜索/重置                          │
│  └─ #buttons：新增配置（v-if=canSave）                        │
│ pure-table（adaptive 分页表格）                               │
│  列：configKey(等宽) / configValue(JSON,tooltip 截断)         │
│      / description / updatedAt / 操作(编辑)                   │
│ 分页                                                         │
└──────────────────────────────────────────────────────────────┘
```

- 表格滚动交给 `pure-table` 的 `adaptive` prop，不手动设 overflow。
- 覆写 layout `.main-content` margin：`div.system-config-page.main-content { margin: var(--space-3) }`（特异性 0,2,1 > layout scoped 0,2,0，无需 `!important`）。
- `.table-wrap { flex: 1; min-height: 0; overflow: hidden }` + `:deep(.pure-table)/:deep(.el-table)` 高度填充（复用 type-def 范式）。
- configValue 列：长 JSON 用 `el-tooltip` 完整查看 + 单元格内 `text-overflow: ellipsis` 截断，避免撑爆表格。

## 3. 字段定义

### 3.1 系统配置响应（SystemConfigResp，对齐后端 SystemConfigResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键 |
| tenantId | number? | 租户 ID（从 X-Tenant-Id Header 取，不在请求体） |
| configKey | string | 配置键（租户内唯一，`uk_system_config`，如 ROLE_NAME_UNIQUE_MODE） |
| configValue | string | 配置值（JSON 字符串，schema 是 JSONB，前端按字符串处理） |
| description | string \| null | 描述（可空） |
| updatedAt | string? | 更新时间 |
| createdAt | string? | 创建时间 |

### 3.2 系统配置表单（SystemConfigFormData）

| 字段 | 校验 | 说明 |
|---|---|---|
| configKey | 必填，大写字母开头+大写字母/数字/下划线，最长 128 | 新建可填；编辑只读（唯一键，改它等于新建新项，与 type-def 稳定编码同口径） |
| configValue | 必填，合法 JSON | textarea 编辑；提交前 `JSON.parse` 校验（对齐后端 `JsonValidationUtils.validateJson`）；默认 `{}` |
| description | 可空 | 描述 |

> **无 id 字段在表单**：save 按 configKey upsert，不需 id。

## 4. 交互流程

### 4.1 列表加载与过滤

- **加载**：进入页面 `getSystemConfigList({})` → 后端返回 `ItemsResp`（全量，无分页/无过滤，见 §8 🔧 第 1 条）→ hook `loadTable` 本地做 keyword 过滤 + configKey 排序 + 切片分页。
- **keyword 搜索**：hook 本地按 configKey / description 模糊匹配。
- **分页**：`onPageChange` / `onPageSizeChange`，`pagination.total` = 本地过滤后长度，`tableData` = 切片后的当前页。
- **配置项量小**：每次翻页重拉全量可接受；Phase 2 后端补 keyword/pageNum/pageSize 参数 + 返回 PaginatedResp 后（T-PERM-024）可切回服务端分页。

### 4.2 新增

- 顶部「新增配置」按钮（门禁 `SYSTEM_CONFIG:MANAGE`）→ 表单弹窗。
- 表单：configKey 可填、configValue textarea（默认 `{}`）、description。提交前 `JSON.parse` 校验 configValue 合法性。
- 提交 → `saveSystemConfig`（configKey + configValue + description）→ mock 按 configKey upsert（不存在则 insert，自动分配 id + 时间戳）→ 成功 `loadTable`。

### 4.3 编辑

- 操作列「编辑」按钮（`v-if="canSave"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ 表单弹窗。
- 编辑态：configKey 只读（唯一键稳定），configValue/description 可改。提交前 `JSON.parse` 校验。
- 提交 → `saveSystemConfig`（同 configKey 覆盖，upsert update 分支）→ mock 更新 configValue/description/updatedAt → 成功 `loadTable`。

### 4.4 删除

- **无删除操作**：后端无 remove 接口（save 是唯一写操作，upsert 语义无删除）。操作列仅「编辑」按钮，无删除按钮。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 列表 | `POST /api/perm/system-config/list` | `{}` (EmptyReq) | `ItemsResp<SystemConfigResp>`（全量，无分页/无过滤） | 🔧 见 §8 |
| 详情 | `POST /api/perm/system-config/detail` | `{configKey}` (SystemConfigGetReq) | `SystemConfigResp` | ✅ |
| 保存 | `POST /api/perm/system-config/save` | `{configKey,configValue,description?}` (SystemConfigReq) | `SystemConfigResp`（upsert） | ✅ |

> **无 create/update/remove**：save 是 upsert 幂等，新建/编辑统一走 save。

## 6. 组件结构

```
views/system/config/
├── index.vue                  # 主页面（PureTableBar 表格 + 权限门控）
├── components/
│   └── ConfigForm.vue         # 配置表单弹窗（新建/编辑，提交统一 save）
└── utils/
    ├── hook.ts                # useSystemConfig（分页表格加载 + save + JSON 校验）
    ├── perms.ts               # SYSTEM_CONFIG_PERMS（SSOT）
    └── types.ts               # SystemConfigFormData + 工厂 + parseConfigValue
```

### Step 1.5 组件识别（登记 T-FE-001 组件池）

| 候选 | 本页使用场景 | 跨页复用 | 确认状态 |
|---|---|---|---|
| 分页表格 hook（tableData/pagination/loadTable/CRUD） | 本页表格 | 6.1 类型定义 / 7.1 操作日志 / 7.2 变更日志（同范式表格列表） | ⏳ 待确认（T-FE-005/012 推进时，模式一致则派生 ReTableHook） |
| JSON 编辑校验（textarea + JSON.parse） | 本页 configValue | 3.2 权限条件 extra / 6.1 类型定义 extra（JSON 字段编辑） | ⏳ 待确认（T-FE-009 推进时） |

> 当前不提前抽取，待 2+ 页确认模式一致后由 T-FE-001 派生子任务。

## 7. 权限接线（hasPerms → 按钮 → 降级）

`SYSTEM_CONFIG_PERMS`（`views/system/config/utils/perms.ts`，SSOT）：

| perm 串 | 操作码 | 锚点 | 控制按钮 |
|---|---|---|---|
| `SYSTEM_CONFIG:VIEW` | VIEW | SYSTEM_CONFIG | 列表数据可见（后端 list/detail 校验 VIEW；前端按钮级由 auths 控制，见降级策略） |
| `SYSTEM_CONFIG:MANAGE` | MANAGE | SYSTEM_CONFIG | 新增 / 编辑（save 统一口径） |

> **B1 口径**：后端 `SystemConfigAppServiceImpl` 的 `upsertSystemConfig` 以 `SYSTEM_CONFIG:MANAGE` 做门禁，`listSystemConfigs`/`getSystemConfig` 以 `VIEW`，无独立 CREATE/UPDATE/DELETE 操作码。前端新增/编辑统一映射到 `SYSTEM_CONFIG:MANAGE`（与类型定义页 EDIT/DELETE→MANAGE 同口径，但更简——系统配置只有一个写操作 save）。`SYSTEM_CONFIG_PERM_LIST` 用 `Set` 去重确保路由 `meta.auths` 无冗余。

### 降级策略

> **路由可达性现状（项目共性，非本页独有）**：pure-admin-thin 的 `filterNoPermissionTree`（`router/utils.ts:85`）路由过滤**只基于 `meta.roles`，不使用 `meta.auths`**。本页及 user/role/type-def 等所有页 meta 均只有 `auths` 无 `roles`，故菜单对所有登录用户可见，**页面级拦截靠后端 403 兜底**，`meta.auths` 仅用于按钮级 `hasAuth`/`hasPerms` 控制。这与 role-manage.md / type-definition.md 同口径。

- 无 `SYSTEM_CONFIG:VIEW` → **菜单仍可见、路由可达**（路由过滤基于 roles，本项目未设 roles）；进入页面后 `loadTable` 调 `/list` 由后端 VIEW 校验拒绝（403），前端 `message` 报错。按钮级 `meta.auths` 派生自 `SYSTEM_CONFIG_PERM_LIST`，仅用于按钮显隐，不拦截路由。
- 无 `SYSTEM_CONFIG:MANAGE` → 隐藏「新增配置」和「编辑」按钮（`v-if="canSave"`），操作列显示「—」。

> 🔧 路由级 auths 拦截缺失属项目共性问题（type-def/role/user 同），若需「无 VIEW 真正路由不可达」需改 `filterNoPermissionTree` 按 `meta.auths` 过滤——影响所有页，超出 T-FE-004 范围，登记待统一立项处理。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 系统配置权限 |
|---|---|
| admin | 全权（VIEW/MANAGE） |
| sec（安全管理员） | 全权（CONFIG_SAVE=MANAGE）——配置管理与 sec 职责同源 |
| hr（组织人事管理员） | 只读（VIEW） |
| auditor（审计员） | 只读（VIEW） |

## 8. API 核对清单（登记 T-PERM-024）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-024。

### 🔧 需改造

1. **api-contract §5.8 缺 system-config 专属字段契约**
   - 现状：§5.8 标题实为「视图与审计」，system-config 仅 3 行表格条目（list/detail/save 路径），**无独立字段契约章节**（无字段表/请求示例）。字段由后端 DTO（`SystemConfigReq`/`SystemConfigGetReq`/`SystemConfigResp`）落地。
   - 期望：§5.8 或新增 §5.x 补 system-config 请求/响应字段契约（configKey/configValue/description + 响应字段），与其他资源契约章节同口径。
   - 前端可行性：✅ 本页已按后端 DTO 字段实现，契约补全后前端无需改动（字段已对齐）。
   - 归属：T-PERM-024 🔧。

2. **`SYSTEM_CONFIG` 权限种子缺失**
   - 现状：schema 无 `INSERT` 为 `SYSTEM_CONFIG` 资源类型在 `type_definition`（resource_type）或 `operation_permission` 表预置 VIEW/MANAGE 操作位。联调真后端时权限判定可能为空，导致所有账号无权访问。
   - 期望：租户初始化种子为 `SYSTEM_CONFIG` 资源类型预置 VIEW + MANAGE 操作位（与其他资源类型同口径）。
   - 前端可行性：✅ Phase 1 mock 自配角色矩阵（admin/sec 全权、hr/auditor 只读）规避。后端补种子后联调验证。
   - 归属：T-PERM-024 🔧。

3. **`config_value` JSONB ↔ entity String 映射确认**
   - 现状：schema `config_value JSONB NOT NULL DEFAULT '{}'`，entity `SystemConfig.configValue` 声明为 `String`，`SystemConfigAppServiceImpl` 直接 `setConfigValue(req.configValue())`，`JsonValidationUtils.validateJson` 校验。MyBatis-Flex + 驱动处理 JSONB↔String 序列化。
   - 期望：确认 JSONB↔String 映射在 PostgreSQL 驱动下行为正确（写入 JSON 字符串、读出 JSON 字符串），无静默截断/转义问题。
   - 前端可行性：✅ 本页 configValue 按 JSON 字符串提交/展示 + 前端 `JSON.parse` 预校验。后端确认映射后联调。
   - 归属：T-PERM-024 🔧（确认型，非必改）。

### ✅ 满足

- list/detail/save 满足前端需求，请求/响应结构与 mock 对齐。save upsert 幂等覆盖新建/编辑，无需独立 create/update。

### 备注

- **扁平 key-value 非分组表单**：任务原标题「租户级配置分组表单」与实际后端契约（无 config_group 字段）不符，本页按扁平 key-value 字典实现。若后续需分组展示，可前端按 configKey 命名前缀分组（不改后端），当前不实现。
- **无删除接口**：后端 system-config 无 remove，配置项不可删除（仅可 upsert 覆盖）。这是设计约束——配置键稳定，避免误删导致系统行为回退默认。
- **save 幂等语义**：与 §4 动词规范 `save=幂等创建或更新` 一致，前端不区分新建/编辑调用。

## 9. 已知限制（Phase 1）

- list 为前端本地过滤+分页（后端返回全量 ItemsResp）；Phase 2 后端补 keyword/pageNum/pageSize + 返回 PaginatedResp 后切换服务端分页。
- configValue JSON 校验为前端 `JSON.parse` 预拦截（对齐后端 `JsonValidationUtils`）；联调时后端二次校验。
- mock 保留 `deleted` 内部标记（对齐 schema delete_flag 范式），但后端无删除接口，该标记仅预留。
- 联调（T-FE-022）需后端先补 §8 三项 🔧（尤其权限种子，否则联调无权访问）。
