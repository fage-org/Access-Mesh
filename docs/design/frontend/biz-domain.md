---
doc_type: design
title: 5.1 业务域页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-07-01
---

# 5.1 业务域页 前端设计

> 任务：T-FE-006（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.1（类型与域）/ §5.6（高级能力）
> 参照范式：6.2 系统配置页（`system-config.md`，PureTableBar 表格列表范式 + SSOT/降级/核对清单结构）

## 1. 页面定位

业务域页管理两张表、两套接口，**主从同页**：

- **主区 BizDomain**（`biz_domain` 表，`/api/perm/biz-domain/*`）：业务域本身 CRUD（create / detail / list / update / remove 批量软删）。业务域是权限系统的顶层分区概念，用于隔离不同业务场景的权限配置（如 HR 域、订单域、全局域）。
- **从区 DomainConfig**（`domain_config` 表，`/api/perm/domain-config/*`）：域级配置，`CLASSIFY` 是其一个 `configType`（非 biz-domain 独立字段）。任务标题「域 CRUD + CLASSIFY 类型归属」把两者合并描述——经核实 CLASSIFY 是 `domain_config.config_type` 的一个取值，故「类型归属」实为「域配置的 CLASSIFY 配置项」。

关键特性：

- **主从布局**：上区 BizDomain 表格（CRUD），选中域后下区展示该域的 DomainConfig 列表。
- **BizDomain 有独立 create/update/remove**（非 system-config 的纯 save upsert）：主表 hook 区分 create/edit + handleDelete。
- **DomainConfig save upsert 幂等**（同 system-config 范式）：按 domainCode+configType upsert（存在则 update extra，不存在则 insert），新建/编辑统一走 save。
- **全局域不可删**：`biz_domain.global` 字段（每租户仅一个全局域，隐式包含未认领类型），但 `BizDomainResp` **不返回 global**（🔧 T-PERM-026）——前端无法区分全局域、无法预判删除。mock 内部保留 global 做「全局域不可删」校验，后端需有此校验。
- **biz-domain list 无分页**：返回全量 `ItemsResp`（同 system-config），前端本地过滤+分页。
- **extra 是 JSON**：`domain_config.extra` schema 是 JSONB，前端按 JSON 字符串编辑 + 提交前 `JSON.parse` 校验（同 system-config configValue 范式）。

## 2. 布局结构

主从布局（上下两区），上区用 PureTableBar 表格范式（遵循 `frontend-layout-patterns`，同 system-config），下区用 pure-table 子表：

```
┌─ biz-domain-page（main-content）──────────────────────────────┐
│ 上区：BizDomain 表格（PureTableBar）                            │
│  ├─ #title：keyword 输入 + 搜索/重置                              │
│  ├─ #buttons：新增业务域（v-if=canManage）                       │
│  pure-table（adaptive 分页表格）                                 │
│   列：code(等宽) / name / description / createdAt               │
│       / 操作(编辑/删除/配置)                                     │
│  分页                                                           │
├─────────────────────────────────────────────────────────────────┤
│ 下区：DomainConfig 子表（选中域后展示）                          │
│  标题：「{name}（{code}）的域配置」+ 新增配置（v-if=canManage）   │
│  pure-table（量小，无分页）                                      │
│   列：configType(el-tag+中文label) / extra(JSON,tooltip截断)    │
│       / updatedAt / 操作(编辑/删除)                              │
│  未选中域时：el-empty 引导「点击配置按钮」                        │
└─────────────────────────────────────────────────────────────────┘
```

- 上区表格滚动交给 `pure-table` 的 `adaptive` prop；下区子表量小不滚动。
- 覆写 layout `.main-content` margin：`div.biz-domain-page.main-content { margin: var(--space-3) }`（特异性 0,2,1 > layout scoped 0,2,0，无需 `!important`）。
- `.biz-domain-page` flex column：上区 `.main-table-wrap { flex: 1 }` 占满剩余高度，下区 `.config-section { flex-shrink: 0 }` 固定高度。
- extra 列：长 JSON 用 `el-tooltip` 完整查看 + 单元格内 `text-overflow: ellipsis` 截断。

## 3. 字段定义

### 3.1 业务域响应（BizDomainResp，对齐后端 BizDomainResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键 |
| tenantId | number? | 租户 ID（从 X-Tenant-Id Header 取，不在请求体） |
| code | string | 业务域编码（租户内唯一，`uk_biz_domain`，如 HR / ORDER / GLOBAL） |
| name | string | 业务域名称 |
| description | string \| null | 描述（可空） |
| createdAt | string? | 创建时间 |

> 🔧 **缺 global 字段**：entity/schema 有 `global`（全局域语义关键），Resp 不返回。前端无法区分全局域与普通域、无法阻止删除全局域（登记 T-PERM-026）。

### 3.2 域配置响应（DomainConfigResp，对齐后端 DomainConfigResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键 |
| tenantId | number? | 租户 ID |
| bizDomainId | number | 所属业务域 ID（save 时由 domainCode 解析） |
| configType | string | 配置类型（SCOPE / RELATION / BINDING / SUB_PERM / CLASSIFY） |
| extra | string | 配置值（JSON 字符串，schema 是 JSONB） |
| updatedAt | string? | 更新时间 |

### 3.3 业务域表单（BizDomainFormData）

| 字段 | 校验 | 说明 |
|---|---|---|
| code | 必填，大写字母开头+大写字母/数字/下划线，最长 64 | 新建可填；编辑只读（唯一键，改它等于新建新域） |
| name | 必填，最长 128 | 域名称 |
| description | 可空 | 描述 |

### 3.4 域配置表单（DomainConfigFormData）

| 字段 | 校验 | 说明 |
|---|---|---|
| configType | 必填，下拉 5 选 1 | 新建可选；编辑只读（upsert 键 domainCode+configType 的一部分） |
| extra | 必填，合法 JSON | textarea 编辑；提交前 `JSON.parse` 校验；默认 `{}` |

### 3.5 配置类型选项（CONFIG_TYPE_OPTIONS，对齐 schema 注释）

| value | label |
|---|---|
| CLASSIFY | CLASSIFY（域分类配置） |
| SCOPE | SCOPE（域范围） |
| RELATION | RELATION（域关系） |
| BINDING | BINDING（域绑定） |
| SUB_PERM | SUB_PERM（子权限配置） |

> 🔧 后端 `AppServiceImpl.upsertDomainConfig` 注释只提 CLASSIFY/SUB_PERM，schema 注释列 5 种（登记 T-PERM-026）。前端下拉列全 5 种。

## 4. 交互流程

### 4.1 主表：列表加载与过滤

- **加载**：进入页面 `getBizDomainList()` → 后端返回 `ItemsResp`（全量，无分页/无过滤，见 §8 🔧 第 4 条）→ hook `loadTable` 本地做 keyword 过滤 + code 排序 + 切片分页。
- **keyword 搜索**：hook 本地按 code / name / description 模糊匹配。
- **分页**：`onPageChange` / `onPageSizeChange`，`pagination.total` = 本地过滤后长度，`tableData` = 切片后的当前页。
- **业务域量小**：每次翻页重拉全量可接受；Phase 2 后端补 keyword/pageNum/pageSize 参数 + 返回 PaginatedResp 后（T-PERM-026）可切回服务端分页。

### 4.2 主表：新增业务域

- 顶部「新增业务域」按钮（门禁 `SYSTEM_CONFIG:MANAGE`，即 CONFIG_SAVE）→ BizDomainForm 弹窗。
- 表单：code 可填、name、description。提交 → `createBizDomain`（code+name+description）→ mock 校验 code 唯一后 insert（自动分配 id+时间戳）→ 成功 `loadTable`。

### 4.3 主表：编辑业务域

- 操作列「编辑」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ BizDomainForm 弹窗。
- 编辑态：code 只读（唯一键稳定），name/description 可改。
- 提交 → `updateBizDomain`（domainId + name + description）→ mock 按 domainId 更新 → 成功 `loadTable`。若编辑的是当前选中域，同步刷新子表标题。

### 4.4 主表：删除业务域

- 操作列「删除」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ `handleDeleteBizDomain([id])` 内前置 `ElMessageBox.confirm` 二次确认（对齐 role/type-def 范式，业务域为持久配置类资源）→ 确认后 `removeBizDomain([id])` 批量软删。
- **全局域不可删**：mock 校验 `global=true` 拒绝返回 400（🔧 Resp 不返回 global，前端无法预判，后端需有此校验，登记 T-PERM-026）。
- 若删除了当前选中域，清空子表。

### 4.5 主从联动：选中域 → 加载子表

- 操作列「配置」按钮（`v-if="canViewConfig"`，门禁 `SYSTEM_CONFIG:VIEW`）→ 点击 `onConfigLink(row)` → `selectDomain(row, canViewConfig.value)` + 滚动到子表区 → `currentDomain` 设为该域 → `loadConfigs()` 调 `getDomainConfigList(currentDomain.code)` → 子表展示该域 DomainConfig 列表。
- **权限守卫（避免可避免的 403）**：`selectDomain` 接 `canViewConfig` 形参，无 `SYSTEM_CONFIG:VIEW` 时只选中域、**不发 /list 请求**，子表由 `el-empty` 提示无权。按钮 `v-if="canViewConfig"` 隐藏入口 + selectDomain 守卫为双保险。
- 未选中域时：下区 `el-empty` 引导「点击配置按钮」。

### 4.6 子表：新增域配置

- 下区「新增配置」按钮（`v-if="canManage && currentDomain"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ DomainConfigForm 弹窗。
- 表单：domainCode（只读展示当前域）、configType 下拉 5 选 1、extra textarea（默认 `{}`）。提交前 `JSON.parse` 校验 extra。
- 提交 → `saveDomainConfig`（domainCode + configType + extra）→ mock upsert（不存在则 insert，自动分配 id+时间戳）→ 成功 `loadConfigs`。

### 4.7 子表：编辑域配置

- 子表操作列「编辑」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ DomainConfigForm 弹窗。
- 编辑态：configType 只读（upsert 键稳定），extra 可改。提交前 `JSON.parse` 校验。
- 提交 → `saveDomainConfig`（同 domainCode+configType 覆盖，upsert update 分支）→ mock 更新 extra/updatedAt → 成功 `loadConfigs`。

### 4.8 子表：删除域配置

- 子表操作列「删除」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ `handleDeleteConfig([id])` 内前置 `ElMessageBox.confirm` 二次确认（对齐 role/type-def 范式，域配置为持久配置类资源）→ 确认后 `removeDomainConfig([id])` 批量软删 → 成功 `loadConfigs`。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 域列表 | `POST /api/perm/biz-domain/list` | `{}` (EmptyReq) | `ItemsResp<BizDomainResp>`（全量，无分页） | 🔧 见 §8 第 4 条 |
| 域详情 | `POST /api/perm/biz-domain/detail` | `{id}` (IdReq) | `BizDomainResp` | 🔧 见 §8 第 3 条（切业务键 code） |
| 域创建 | `POST /api/perm/biz-domain/create` | `{code,name,description?}` | `BizDomainResp` | ✅ |
| 域更新 | `POST /api/perm/biz-domain/update` | `{domainId,name?,description?}` | `BizDomainResp` | 🔧 见 §8 第 3 条（切业务键 code） |
| 域删除 | `POST /api/perm/biz-domain/remove` | `{ids}` (IdsReq) | `void`（批量软删） | ✅（全局域不可删待后端校验，见 §8 第 2 条） |
| 配置列表 | `POST /api/perm/domain-config/list` | `{domainCode?}` | `ItemsResp<DomainConfigResp>` | ✅ |
| 配置详情 | `POST /api/perm/domain-config/detail` | `{domainCode,configType}` | `DomainConfigResp` | ✅（业务键二元组） |
| 配置保存 | `POST /api/perm/domain-config/save` | `{domainCode,configType,extra}` | `DomainConfigResp`（upsert） | ✅ |
| 配置删除 | `POST /api/perm/domain-config/remove` | `{ids}` (IdsReq) | `void`（批量软删） | ✅ |

> 契约 §5.1/§5.6 路径与后端实现**一致**（无路径错误，与 operation-log 不同）。

## 6. 组件结构

```
views/system/biz-domain/
├── index.vue                  # 主页面（主从布局 + 权限门控）
├── components/
│   ├── BizDomainForm.vue      # 业务域表单弹窗（create/edit，独立 create/update 接口）
│   └── DomainConfigForm.vue   # 域配置表单弹窗（save upsert，extra JSON 校验）
└── utils/
    ├── hook.ts                # useBizDomain（主表 CRUD + 选中域 + 子表加载 + 删除确认）
    ├── perms.ts               # BIZ_DOMAIN_PERMS（SSOT，DOMAIN:VIEW + SYSTEM_CONFIG:VIEW/MANAGE）
    └── types.ts               # 表单类型 + 工厂 + CONFIG_TYPE_OPTIONS 5 项 + parseExtra
```

> **mock 共享注册表**：`mock/_bizDomainRegistry.ts` 持有业务域内存数据（唯一权威源），`mock/biz-domain.ts`（CRUD）与 `mock/domain-config.ts`（save 解析 domainCode→bizDomainId）共用同一份。使运行时新建/删除的业务域能被 domain-config save 实时感知（后端等价 `typeResolutionService.resolveDomainId`）。零 src 依赖（仅 mock 间共享，不 import @/api/*）。

### Step 1.5 组件识别（登记 T-FE-001 组件池）

| 候选 | 本页使用场景 | 跨页复用 | 确认状态 |
|---|---|---|---|
| JSON 编辑校验（textarea + JSON.parse） | 本页 extra | 6.2 系统配置 configValue / 3.2 权限条件 extra（JSON 字段编辑） | ⏳ 待确认（T-FE-009 推进时，与 system-config 共同确认抽取 ReJsonEditor） |
| 主从布局 hook（主表 CRUD + 选中行 + 子表加载） | 本页主从 | 暂无直接同范式页（其他页多为单表） | ⏳ 待确认（暂不抽取，模式较独特） |

> 当前不提前抽取，待 2+ 页确认模式一致后由 T-FE-001 派生子任务。

## 7. 权限接线（hasPerms → 按钮 → 降级）

`BIZ_DOMAIN_PERMS`（`views/system/biz-domain/utils/perms.ts`，SSOT）涉及**两个资源类型**：

| perm 串 | 操作码 | 资源类型 | 控制按钮 |
|---|---|---|---|
| `DOMAIN:VIEW` | VIEW | DOMAIN（独立） | biz-domain 列表/详情可见（后端 list/detail 校验；本页 canView） |
| `SYSTEM_CONFIG:VIEW` | VIEW | SYSTEM_CONFIG（复用） | domain-config 子表可见（后端 list/detail 校验；本页 canViewConfig） |
| `SYSTEM_CONFIG:MANAGE` | MANAGE | SYSTEM_CONFIG（复用） | biz-domain 新增/编辑/删除 + domain-config 新增/编辑/删除（本页 canManage） |

> **门禁映射**（对齐后端）：
> - biz-domain create/update/remove → `SYSTEM_CONFIG:MANAGE`（后端 createBizDomain/updateBizDomain/deleteBizDomainsByIds）
> - biz-domain list/detail → `DOMAIN:VIEW`（后端 listBizDomains/getBizDomain，ResourceTypeCode.DOMAIN）
> - domain-config save/remove → `SYSTEM_CONFIG:MANAGE`（后端 upsertDomainConfig/deleteDomainConfigsByIds）
> - domain-config list/detail → `SYSTEM_CONFIG:VIEW`（后端 listDomainConfigs/getDomainConfig）
>
> **DOMAIN:VIEW 是新增权限串**：后端用 DOMAIN:VIEW，但 login 矩阵此前无 DOMAIN 串。本页新增 DOMAIN_VIEW，login 矩阵为所有账号预置 DOMAIN:VIEW（业务域基础设施各角色均可见）。🔧 DOMAIN 权限种子缺失登记 T-PERM-026。

### 降级策略

> **路由可达性与按钮门禁现状（项目共性，非本页独有）**：
> - **路由可达性**：pure-admin-thin 的 `filterNoPermissionTree`（`router/utils.ts:85`）路由过滤**只基于 `meta.roles`，不使用 `meta.auths`**。本页及 user/role/type-def 等所有页 meta 均只有 `auths` 无 `roles`，故菜单对所有登录用户可见，**页面级拦截靠后端 403 兜底**。
> - **按钮门禁**：本页按钮 `canManage = hasPerms(BIZ_DOMAIN_PERMS.CONFIG_SAVE)` / `canView = hasPerms(BIZ_DOMAIN_PERMS.DOMAIN_VIEW)`，`hasPerms`（`utils/auth.ts:131`）读取**登录态 `permissions`**（`/auth/user-menu` 下发的 perm 串数组），**不是 `meta.auths`**。
> - **`meta.auths` 的真实用途**：仅作为路由元信息清单（派生自 `BIZ_DOMAIN_PERM_LIST`），供 `hasAuth` 使用；本页按钮未用 `hasAuth`。
> 这与 system-config.md / role-manage.md / type-definition.md 同口径（既有文档同样把按钮门禁写成 auths 控制，属共性表述偏差）。

- 无 `DOMAIN:VIEW` → 菜单仍可见、路由可达；进入页面后 `loadTable` 调 `/list` 由后端 DOMAIN:VIEW 校验拒绝（403），前端 `message` 报错。
- 无 `SYSTEM_CONFIG:MANAGE` → 隐藏「新增业务域」/「编辑」/「删除」及子表「新增配置」/「编辑」/「删除」按钮（`v-if="canManage"`），操作列显示「—」。
- 无 `SYSTEM_CONFIG:VIEW` → 子表区显示 `el-empty`「无权查看域配置」（`v-else-if="!canViewConfig"`）。

> 🔧 路由级 auths 拦截缺失属项目共性问题（type-def/role/user/config/operation-log 同），登记待统一立项处理。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 业务域权限 |
|---|---|
| admin | 全权（DOMAIN:VIEW + SYSTEM_CONFIG:VIEW/MANAGE） |
| sec（安全管理员） | 全权（DOMAIN:VIEW + CONFIG_SAVE=MANAGE）——配置管理与 sec 职责同源 |
| hr（组织人事管理员） | 只读（DOMAIN:VIEW + SYSTEM_CONFIG:VIEW） |
| auditor（审计员） | 只读（DOMAIN:VIEW + SYSTEM_CONFIG:VIEW） |

## 8. API 核对清单（登记 T-PERM-026）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-026。

### 🔧 需改造

1. **DOMAIN 权限种子缺失**
   - 现状：schema 无 `INSERT` 为 `DOMAIN` 资源类型预置 VIEW 操作位；login 矩阵此前也无 DOMAIN 串。联调真后端时 biz-domain list/detail 可能全账号无权（403）。
   - 期望：租户初始化种子为 `DOMAIN` 资源类型预置 VIEW 操作位（与其他资源类型同口径）。
   - 前端可行性：✅ Phase 1 mock 自配角色矩阵（admin/sec/hr/auditor 均预置 DOMAIN:VIEW）规避。后端补种子后联调验证。
   - 归属：T-PERM-026 🔧。

2. **BizDomainResp 缺 global 字段**
   - 现状：entity/schema `biz_domain.global`（每租户仅一个全局域，隐式包含未认领类型），但 `BizDomainResp` 不返回 global。前端无法区分全局域与普通域、无法阻止删除全局域。
   - 期望：Resp 补 global 字段，前端可标识全局域并禁用其删除按钮。
   - 前端可行性：✅ mock 内部保留 global 做「全局域不可删」校验（响应 clone 时剔除对齐后端现状）；后端补字段后前端可显式禁用。
   - 归属：T-PERM-026 🔧。

3. **biz-domain detail/update 用内部主键**
   - 现状：detail 接 `IdReq{id}`、update 接 `BizDomainUpdateReq{domainId}`，均用内部主键。与 T-FE-002 角色 detail 同类 🔧。
   - 期望：切业务键 code（schema `uk_biz_domain(tenant_id,code)` 已保证租户内唯一）。
   - 前端可行性：✅ 前端 list 已返回 code+id，切业务键后前端 detail/update 改传 code 即可。
   - 归属：T-PERM-026 🔧。

4. **biz-domain list 无分页**
   - 现状：list 接 `EmptyReq` 无参，返回全量 `ItemsResp`（无 keyword/pageNum/pageSize）。
   - 期望：补 keyword/pageNum/pageSize 参数 + 返回 PaginatedResp。
   - 前端可行性：✅ 本页本地过滤+分页（业务域量小可接受）；后端补参数后切回服务端分页。
   - 归属：T-PERM-026 🔧。

5. **AppServiceImpl configType 注释不全**
   - 现状：`upsertDomainConfig` 注释只提 CLASSIFY/SUB_PERM，schema 注释列 5 种（SCOPE/RELATION/BINDING/SUB_PERM/CLASSIFY）。
   - 期望：注释补全 5 种 configType 语义。
   - 前端可行性：✅ 本页 configType 下拉已列全 5 种。
   - 归属：T-PERM-026 🔧（确认型，非必改）。

6. **domain-config save 的 extra JSONB↔String 映射确认**
   - 现状：schema `extra JSONB NOT NULL DEFAULT '{}'`，entity `DomainConfig.extra` 声明为 `String`，AppServiceImpl 直接 `setExtra(req.extra())`。MyBatis-Flex + 驱动处理 JSONB↔String 序列化。
   - 期望：确认 JSONB↔String 映射行为正确（同 system-config configValue，登记 T-PERM-024 同类）。
   - 前端可行性：✅ 本页 extra 按 JSON 字符串提交/展示 + 前端 `JSON.parse` 预校验。
   - 归属：T-PERM-026 🔧（确认型，非必改）。

### ✅ 满足

- 9 接口满足前端需求，请求/响应结构与 mock 对齐。契约 §5.1/§5.6 路径与后端实现一致（无路径错误）。
- domain-config save upsert 幂等覆盖新建/编辑，detail 用业务键二元组（domainCode+configType），无需独立 create/update。

### 备注

- **主从布局非单表**：本页是主从两表联动（BizDomain 主 + DomainConfig 从），与其他单表页（system-config/type-def）不同。主表有独立 create/update/remove（非 upsert），子表 save upsert。
- **全局域语义**：每租户仅一个全局域（`uk_biz_domain_global`），其范围隐式包含未被其他域认领的资源类型（DomainClassifyService 全局域语义）。前端不直接管理全局域创建（由系统初始化），仅展示 + 禁删。

## 9. 已知限制（Phase 1）

- biz-domain list 为前端本地过滤+分页（后端返回全量 ItemsResp）；Phase 2 后端补 keyword/pageNum/pageSize + 返回 PaginatedResp 后切换服务端分页。
- extra JSON 校验为前端 `JSON.parse` 预拦截（对齐后端 `JsonValidationUtils`）；联调时后端二次校验。
- mock 保留 `global`/`deleted` 内部字段（对齐 schema delete_flag 范式 + 全局域不可删校验），但响应 clone 时剔除以对齐后端 Resp 现状。`domainCode` 为 domain-config mock 内部冗余字段（便于按域过滤），响应 clone 时剔除。
- **mock 共享注册表**：`mock/_bizDomainRegistry.ts` 持有业务域内存数据，biz-domain 与 domain-config mock 共用同一份——运行时新建/删除的域对 domain-config save 的 `resolveDomainId(code)` 实时可见（修复「新建业务域后无法新增域配置」）。
- 全局域不可删靠 mock 校验（后端 Resp 不返回 global，前端无法预判）；联调（T-FE-021）需后端先补 §8 第 1/2 项（DOMAIN 权限种子 + Resp global 字段），否则联调无权访问或误删全局域。
- **删除二次确认**：业务域/域配置删除均在 hook `handleDelete*` 内前置 `ElMessageBox.confirm`（对齐 role/type-def 范式，持久配置类资源防误删）。
- **子表权限守卫**：「配置」按钮 `v-if="canViewConfig"` 隐藏无权入口；`selectDomain(row, canViewConfig)` 双保险守卫，无 `SYSTEM_CONFIG:VIEW` 时只选中域不发 /list 请求，避免可避免的 403。
