---
doc_type: design
title: 6.2 系统配置页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-28   # 2026-08-28 T-PERM-024 收口：§5/§8/§9 终态化（契约要点补全、种子误报澄清、JSONB 实证+isSystem 修复、list 服务端分页）
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
| 列表 | `POST /api/perm/system-config/list` | `{keyword?,pageNum?,pageSize?}` | `PaginatedResp<SystemConfigResp>`（服务端过滤+分页，ORDER BY configKey,id） | ✅（T-PERM-024 收口） |
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
| `SYSTEM_CONFIG:VIEW` | VIEW | SYSTEM_CONFIG | 列表数据可见（后端 list/detail 校验 VIEW；本页不涉及 VIEW 按钮门禁，见降级策略） |
| `SYSTEM_CONFIG:MANAGE` | MANAGE | SYSTEM_CONFIG | 新增 / 编辑（save 统一口径） |

> **B1 口径**：后端 `SystemConfigAppServiceImpl` 的 `upsertSystemConfig` 以 `SYSTEM_CONFIG:MANAGE` 做门禁，`listSystemConfigs`/`getSystemConfig` 以 `VIEW`，无独立 CREATE/UPDATE/DELETE 操作码。前端新增/编辑统一映射到 `SYSTEM_CONFIG:MANAGE`（与类型定义页 EDIT/DELETE→MANAGE 同口径，但更简——系统配置只有一个写操作 save）。`SYSTEM_CONFIG_PERM_LIST` 用 `Set` 去重确保路由 `meta.auths` 无冗余。

### 降级策略

> **路由可达性与按钮门禁现状（项目共性，非本页独有）**：
> - **路由可达性**：pure-admin-thin 的 `filterNoPermissionTree`（`router/utils.ts:85`）路由过滤**只基于 `meta.roles`，不使用 `meta.auths`**。本页及 user/role/type-def 等所有页 meta 均只有 `auths` 无 `roles`，故菜单对所有登录用户可见，**页面级拦截靠后端 403 兜底**。
> - **按钮门禁**：本页按钮 `canSave = hasPerms(SYSTEM_CONFIG_PERMS.CONFIG_SAVE)`（`index.vue:39`），`hasPerms`（`utils/auth.ts:131`）读取**登录态 `permissions`**（`/auth/user-menu` 下发的 perm 串数组），**不是 `meta.auths`**。
> - **`meta.auths` 的真实用途**：仅作为路由元信息清单（派生自 `SYSTEM_CONFIG_PERM_LIST`），供 `hasAuth`（`router/utils.ts:366`，从当前路由 meta.auths 读）使用；本页按钮未用 `hasAuth`。即 `meta.auths` 是路由级元信息/`hasAuth` 清单，不参与本页按钮显隐。
> 这与 role-manage.md / type-definition.md 同口径（既有文档同样把按钮门禁写成 auths 控制，属共性表述偏差）。

- 无 `SYSTEM_CONFIG:VIEW` → **菜单仍可见、路由可达**（路由过滤基于 roles，本项目未设 roles）；进入页面后 `loadTable` 调 `/list` 由后端 VIEW 校验拒绝（403），前端 `message` 报错。本页无 VIEW 级按钮（VIEW 只决定列表数据可见性，由后端兜底）。
- 无 `SYSTEM_CONFIG:MANAGE` → 隐藏「新增配置」和「编辑」按钮（`v-if="canSave"`，`hasPerms` 读登录态 permissions 判定），操作列显示「—」。

> 🔧 路由级 auths 拦截缺失属项目共性问题（type-def/role/user 同），若需「无 VIEW 真正路由不可达」需改 `filterNoPermissionTree` 按 `meta.auths` 过滤——影响所有页，超出 T-FE-004 范围，登记待统一立项处理。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 系统配置权限 |
|---|---|
| admin | 全权（VIEW/MANAGE） |
| sec（安全管理员） | 全权（CONFIG_SAVE=MANAGE）——配置管理与 sec 职责同源 |
| hr（组织人事管理员） | 只读（VIEW） |
| auditor（审计员） | 只读（VIEW） |

## 8. API 核对清单（T-PERM-024，2026-08-28 收口）

Phase 1 登记的 🔧 项处置终态：

1. ✅ **api-contract §5.8 补 system-config 契约要点**：字段契约、upsert 语义、20047 命名空间校验、JSONB 规范化语义、权限门禁已写入 §5.8（前端无需改动）。
2. ❌ **SYSTEM_CONFIG 权限种子缺失——核实不成立**：权威 DDL 的 CRUD 预置种子组（CROSS JOIN 全部 resource_type × CREATE/VIEW/UPDATE/DELETE——核实时 23 类/92 条，T-PERM-025 增 OPERATION_LOG 后 24 类/96 条）已覆盖 SYSTEM_CONFIG(11) 的 VIEW，扩展码组另有 MANAGE(16)——Phase 1 清单登记时未对照权威 schema，无需改动。
3. ✅ **config_value JSONB ↔ String 映射确认**：新增 `SystemConfigJsonbPgIT`（真实 PostgreSQL 容器轨）实证——语义等价（中文/嵌套/数组/空格变体解析树相等）、读出为 DB 规范化 JSON 文本（非字节回显）、规范化幂等（展示值可直接再提交）、无截断/转义问题。**该 PgIT 同时发现并修复归并遗留生产缺陷**：upsert 新建分支未设 `is_system`（NOT NULL 列）→ API 新建配置项必然 DataIntegrityViolation 裸 99999；修复为固定 `isSystem=false`（系统内置仅走种子）+ 单测回归锁。
4. ✅ **list 服务端过滤+分页**（§9 预期、§8 原漏登，收口补登）：`SystemConfigListReq` = `{keyword?, pageNum?, pageSize?}`（替换 EmptyReq），返回 `PaginatedResp`（keyword LIKE configKey/description、ORDER BY config_key,id）；分页参数均不传 = 字典全量（上限 200，先例 `/role/list`）；本页 hook 已切服务端分页。

### ✅ 满足

- detail/save 满足前端需求；save upsert 幂等覆盖新建/编辑（新建路径 isSystem 缺陷已随第 3 项修复）。
- list 已随第 4 项收口。

### 备注

- **扁平 key-value 非分组表单**：任务原标题「租户级配置分组表单」与实际后端契约（无 config_group 字段）不符，本页按扁平 key-value 字典实现。若后续需分组展示，可前端按 configKey 命名前缀分组（不改后端），当前不实现。
- **无删除接口**：后端 system-config 无 remove，配置项不可删除（仅可 upsert 覆盖）。这是设计约束——配置键稳定，避免误删导致系统行为回退默认。
- **save 幂等语义**：与 §4 动词规范 `save=幂等创建或更新` 一致，前端不区分新建/编辑调用。

## 9. 已知限制

- ~~list 为前端本地过滤+分页~~ 已随 T-PERM-024 切服务端过滤+分页（2026-08-28）。
- configValue JSON 校验为前端 `JSON.parse` 预拦截（对齐后端 `JsonValidationUtils`）；后端二次校验不变。
- ~~mock 保留 deleted 内部标记~~ mock 随真实链路（T-FE-041）移除；后端无删除接口的设计约束不变。
- 联调（T-FE-022）：§8 四项已收口，无阻塞项。
