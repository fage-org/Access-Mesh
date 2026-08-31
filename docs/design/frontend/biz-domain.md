---
doc_type: design
title: 5.1 业务域页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-31   # 2026-08-31 T-PERM-037 收口：路由级 auths 登记收口（menus 接线归 Phase 3 T-FE-015）；2026-08-29 T-PERM-026 后端收口终态化（业务键/分页/global/删除保护/JSON 校验/JSONB 确认）；原文 2026-07-01 Phase 1 前端设计定稿
---

# 5.1 业务域页 前端设计

> 任务：T-FE-006（Phase 1，mock 驱动）；后端收口：T-PERM-026（2026-08-29，契约要点见 api-contract §5.1/§5.6）
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
- **全局域不可删**：`biz_domain.global` 字段（每租户仅一个全局域，隐式包含未认领类型）。T-PERM-026 起 `BizDomainResp` 返回 global——前端以「全局」tag 标识并禁用删除按钮预判，后端 remove 删除保护兜底（全局域或域下存在配置拒绝 20051）。
- **biz-domain list 服务端过滤+分页**（T-PERM-026 收口）：`{keyword, pageNum, pageSize}` → `PaginatedResp`（keyword LIKE code/name/description、ORDER BY code,id、均不传=字典全量上限 200），同 system-config 范式。
- **extra 是 JSON**：`domain_config.extra` schema 是 JSONB，前端按 JSON 字符串编辑 + 提交前 `JSON.parse` 校验；后端 save 亦经 `JsonValidationUtils` 校验（T-PERM-026 补齐），JSONB↔String 映射已真库确认（语义等价、可直接再提交）。

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
| global | boolean? | 是否全局域（每租户仅一个；T-PERM-026 起 Resp 返回，前端标识+禁删预判） |
| createdAt | string? | 创建时间 |

### 3.2 域配置响应（DomainConfigResp，对齐后端 DomainConfigResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键 |
| tenantId | number? | 租户 ID |
| bizDomainId | number | 所属业务域 ID（save 时由 domainCode 解析） |
| configType | string | 配置类型（仅 SUB_PERM / CLASSIFY 已实现，2026-08-27 起写入白名单校验拒绝其余历史类型） |
| extra | string | 配置值（JSON 字符串，schema 是 JSONB；读出为 DB 规范化文本，可直接再提交） |
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
| configType | 必填，下拉 2 选 1（SUB_PERM/CLASSIFY） | 新建可选；编辑只读（upsert 键 domainCode+configType 的一部分）；写入白名单校验拒绝其余历史类型 |
| extra | 必填，合法 JSON | textarea 编辑；提交前 `JSON.parse` 校验；默认 `{}` |

### 3.5 配置类型选项（CONFIG_TYPE_OPTIONS，对齐 schema 注释与后端白名单）

| value | label |
|---|---|
| CLASSIFY | CLASSIFY（域分类配置） |
| SUB_PERM | SUB_PERM（子权限配置） |

> 2026-08-27 收窄：仅 SUB_PERM/CLASSIFY 两类已实现，后端 `DomainConfigReq` 白名单校验拒绝 `SCOPE/RELATION/BINDING`（历史设想类型，未实现）；前端下拉与 mock 已同步收窄。

## 4. 交互流程

### 4.1 主表：列表加载与过滤

- **加载**（T-PERM-026 收口：服务端过滤+分页）：`getBizDomainList({keyword, pageNum, pageSize})` → 后端返回 `PaginatedResp`（keyword LIKE code/name/description、ORDER BY code,id），前端只消费。
- **keyword 搜索**：`onSearch` 重置页码后重拉；`onReset` 清空重拉。
- **分页**：`onPageChange` / `onPageSizeChange` 透传服务端，`pagination.total` = 响应 total。

### 4.2 主表：新增业务域

- 顶部「新增业务域」按钮（门禁 `SYSTEM_CONFIG:MANAGE`，即 CONFIG_SAVE）→ BizDomainForm 弹窗。
- 表单：code 可填、name、description。提交 → `createBizDomain`（code+name+description）→ 后端查重（重复 20052，uk_biz_domain 兜底）后落库 → 成功 `loadTable`。

### 4.3 主表：编辑业务域

- 操作列「编辑」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ BizDomainForm 弹窗。
- 编辑态：code 只读（唯一键稳定），name/description 可改。
- 提交 → `updateBizDomain`（domainCode + name + description，T-PERM-026 切业务键 code）→ 成功 `loadTable`。若编辑的是当前选中域，同步刷新子表标题。name/description 总是携带表单当前值（description 空串=显式清空，后端仅 null 表示不更新）；未知编码后端拒绝 20017。

### 4.4 主表：删除业务域

- 操作列「删除」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`；`row.global=true` 的行禁用按钮预判）→ `handleDeleteBizDomain([id])` 内前置 `ElMessageBox.confirm` 二次确认（对齐 role/type-def 范式，业务域为持久配置类资源）→ 确认后 `removeBizDomain([id])` 批量软删。
- **删除保护（T-PERM-026，后端 20051 兜底）**：全局域不可删（Resp.global 前端预判禁用按钮）；域下仍存在有效域配置时整批拒绝（需先删除该域下配置），确认文案已注明。
- 若删除了当前选中域，清空子表。

### 4.5 主从联动：选中域 → 加载子表

- 操作列「配置」按钮（`v-if="canViewConfig"`，门禁 `SYSTEM_CONFIG:VIEW`）→ 点击 `onConfigLink(row)` → `selectDomain(row, canViewConfig.value)` + 滚动到子表区 → `currentDomain` 设为该域 → `loadConfigs()` 调 `getDomainConfigList(currentDomain.code)` → 子表展示该域 DomainConfig 列表。
- **权限守卫（避免可避免的 403）**：`selectDomain` 接 `canViewConfig` 形参，无 `SYSTEM_CONFIG:VIEW` 时只选中域、**不发 /list 请求**，子表由 `el-empty` 提示无权。按钮 `v-if="canViewConfig"` 隐藏入口 + selectDomain 守卫为双保险。
- 未选中域时：下区 `el-empty` 引导「点击配置按钮」。

### 4.6 子表：新增域配置

- 下区「新增配置」按钮（`v-if="canManage && currentDomain"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ DomainConfigForm 弹窗。
- 表单：domainCode（只读展示当前域）、configType 下拉 2 选 1（SUB_PERM/CLASSIFY）、extra textarea（默认 `{}`）。提交前 `JSON.parse` 校验 extra。
- 提交 → `saveDomainConfig`（domainCode + configType + extra）→ 后端 upsert（JSON 校验 + 白名单 + 按 domainCode+configType 幂等）→ 成功 `loadConfigs`。

### 4.7 子表：编辑域配置

- 子表操作列「编辑」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ DomainConfigForm 弹窗。
- 编辑态：configType 只读（upsert 键稳定），extra 可改。提交前 `JSON.parse` 校验。
- 提交 → `saveDomainConfig`（同 domainCode+configType 覆盖，upsert update 分支）→ 后端更新 extra/updatedAt → 成功 `loadConfigs`。

### 4.8 子表：删除域配置

- 子表操作列「删除」按钮（`v-if="canManage"`，门禁 `SYSTEM_CONFIG:MANAGE`）→ `handleDeleteConfig([id])` 内前置 `ElMessageBox.confirm` 二次确认（对齐 role/type-def 范式，域配置为持久配置类资源）→ 确认后 `removeDomainConfig([id])` 批量软删 → 成功 `loadConfigs`。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 域列表 | `POST /api/perm/biz-domain/list` | `{keyword?,pageNum?,pageSize?}` | `PaginatedResp<BizDomainResp>` | ✅（T-PERM-026 服务端过滤+分页） |
| 域详情 | `POST /api/perm/biz-domain/detail` | `{domainCode}` | `BizDomainResp`（未命中 data=null） | ✅（T-PERM-026 切业务键） |
| 域创建 | `POST /api/perm/biz-domain/create` | `{code,name,description?}` | `BizDomainResp` | ✅（重复 20052） |
| 域更新 | `POST /api/perm/biz-domain/update` | `{domainCode,name?,description?}` | `BizDomainResp` | ✅（T-PERM-026 切业务键；空串清空 description） |
| 域删除 | `POST /api/perm/biz-domain/remove` | `{ids}` (IdsReq) | `void`（批量软删） | ✅（删除保护 20051：全局域/域下有配置） |
| 配置列表 | `POST /api/perm/domain-config/list` | `{domainCode?}` | `ItemsResp<DomainConfigResp>` | ✅ |
| 配置详情 | `POST /api/perm/domain-config/detail` | `{domainCode,configType}` | `DomainConfigResp` | ✅（业务键二元组） |
| 配置保存 | `POST /api/perm/domain-config/save` | `{domainCode,configType,extra}` | `DomainConfigResp`（upsert） | ✅ |
| 配置删除 | `POST /api/perm/domain-config/remove` | `{ids}` (IdsReq) | `void`（批量软删） | ✅ |

> 契约 §5.1/§5.6 路径与后端实现**一致**（无路径错误，与 operation-log 不同）。T-PERM-026 契约要点已回写 api-contract §5.1（biz-domain）/§5.6（domain-config）。

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
    └── types.ts               # 表单类型 + 工厂 + CONFIG_TYPE_OPTIONS 2 项（SUB_PERM/CLASSIFY） + parseExtra
```

> **mock 共享注册表**：`mock/_bizDomainRegistry.ts` 持有业务域内存数据（唯一权威源），`mock/biz-domain.ts`（CRUD）与 `mock/domain-config.ts`（save 解析 domainCode→bizDomainId）共用同一份。使运行时新建/删除的业务域能被 domain-config save 实时感知（后端等价 `typeResolutionService.resolveDomainId`）。域配置数据同范式下沉 `mock/_domainConfigRegistry.ts`（T-PERM-026 起）——biz-domain mock remove 的「域下存在配置拒删」引用检查需要两份运行时数据一致。零 src 依赖（仅 mock 间共享，不 import @/api/*）。

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
> **DOMAIN:VIEW 接入收口（T-PERM-026）**：原 🔧「DOMAIN 权限种子缺失」经核实**不成立**——权威 DDL CRUD 预置组（全部 resource_type × CREATE/VIEW/UPDATE/DELETE CROSS JOIN 派生）已覆盖 DOMAIN(9) VIEW 操作位（同 T-PERM-024 SYSTEM_CONFIG 误报先例）。真正缺口是空库 bootstrap 固定图未持 DOMAIN:VIEW（checkCanGrant 要求操作者先持有才能转授，无授予起点死锁）——已补入 businessGrants + GRANT_RESOURCE_TYPES（OPERATION_LOG:VIEW 先例，AccessBootstrapPgIT 计数锁定）。前端 DOMAIN_VIEW 常量与 login 矩阵预置保持既有实现。

### 降级策略

> **路由可达性与按钮门禁现状（项目共性，非本页独有）**：
> - **路由可达性**：pure-admin-thin 的 `filterNoPermissionTree`（`router/utils.ts:85`）路由过滤**只基于 `meta.roles`，不使用 `meta.auths`**。本页及 user/role/type-def 等所有页 meta 均只有 `auths` 无 `roles`，故菜单对所有登录用户可见，**页面级拦截靠后端 403 兜底**。
> - **按钮门禁**：本页按钮 `canManage = hasPerms(BIZ_DOMAIN_PERMS.CONFIG_SAVE)` / `canView = hasPerms(BIZ_DOMAIN_PERMS.DOMAIN_VIEW)`，`hasPerms`（`utils/auth.ts:131`）读取**登录态 `permissions`**（`/auth/user-menu` 下发的 perm 串数组），**不是 `meta.auths`**。
> - **`meta.auths` 的真实用途**：仅作为路由元信息清单（派生自 `BIZ_DOMAIN_PERM_LIST`），供 `hasAuth` 使用；本页按钮未用 `hasAuth`。
> 这与 system-config.md / role-manage.md / type-definition.md 同口径（既有文档同样把按钮门禁写成 auths 控制，属共性表述偏差）。

- 无 `DOMAIN:VIEW` → 菜单仍可见、路由可达；进入页面后 `loadTable` 调 `/list` 由后端 DOMAIN:VIEW 校验拒绝（403），前端 `message` 报错。
- 无 `SYSTEM_CONFIG:MANAGE` → 隐藏「新增业务域」/「编辑」/「删除」及子表「新增配置」/「编辑」/「删除」按钮（`v-if="canManage"`），操作列显示「—」。
- 无 `SYSTEM_CONFIG:VIEW` → 子表区显示 `el-empty`「无权查看域配置」（`v-else-if="!canViewConfig"`）。

> ~~🔧 路由级 auths 拦截缺失属项目共性问题（type-def/role/user/config/operation-log 同）~~ **已收口（2026-08-31 设计定案，T-PERM-037）**：菜单可见性 v3.5 §4.1 ∃op 派生方案后端已实现（`/auth/user-menu` 双轨下发按权限过滤后的 menus 树），前端接线归入 Phase 3 联调 T-FE-015（登录链路切真实接口时菜单栏从本地静态路由切后端派生 menus 树）；不改 `filterNoPermissionTree` 按 `meta.auths` 过滤（与后端派生方案重复，且 auths 为前端静态声明可绕过）。联调前维持「菜单可见、路由可达、后端 VIEW 403 兜底」。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 业务域权限 |
|---|---|
| admin | 全权（DOMAIN:VIEW + SYSTEM_CONFIG:VIEW/MANAGE） |
| sec（安全管理员） | 全权（DOMAIN:VIEW + CONFIG_SAVE=MANAGE）——配置管理与 sec 职责同源 |
| hr（组织人事管理员） | 只读（DOMAIN:VIEW + SYSTEM_CONFIG:VIEW） |
| auditor（审计员） | 只读（DOMAIN:VIEW + SYSTEM_CONFIG:VIEW） |

## 8. API 核对清单（T-PERM-026 已收口，2026-08-29）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-026；以下为收口终态（原文划线保留核对轨迹）。

### 🔧 需改造

1. ~~**DOMAIN 权限种子缺失**~~（已收口反转，2026-08-29）：DDL CRUD 预置组已覆盖 DOMAIN VIEW 操作位（误报，同 T-PERM-024 SYSTEM_CONFIG 先例）；真实缺口为 bootstrap 固定图未持 DOMAIN:VIEW（无授予起点死锁），已补入 businessGrants + GRANT_RESOURCE_TYPES（见 §7）。

2. ~~**BizDomainResp 缺 global 字段**~~（已收口，2026-08-29）：Resp 补 global 字段；前端「全局」tag 标识 + 禁用删除按钮预判；后端 remove 删除保护兜底（全局域或域下存在配置拒绝 20051，schema「引用检查拒删」落地——原前端确认文案「域配置将一并处理」的歧义随之收口为「先删配置再删域」）。

3. ~~**biz-domain detail/update 用内部主键**~~（已收口，2026-08-29）：detail 接 `BizDomainDetailReq{domainCode}`（未命中 data=null，role detail 先例）、update 接 `{domainCode, name?, description?}`（description 空串=显式清空）；前端与 mock 同步切业务键。

4. ~~**biz-domain list 无分页**~~（已收口，2026-08-29）：list 接 `BizDomainListReq{keyword?, pageNum?, pageSize?}` 返回 `PaginatedResp`（keyword LIKE code/name/description、ORDER BY code,id、均不传=字典全量上限 200，system-config 范式）；前端 hook 切服务端过滤分页。

5. ~~**AppServiceImpl configType 注释不全**~~（已收口反转，2026-08-27）
   - 原 🔧 登记的"schema 注释列 5 种 vs 后端只提两类"漂移已按两类收口定案：SUB_PERM/CLASSIFY 为唯一实现范围（DDL 注释、后端注释、`DomainConfigReq` 白名单、前端下拉四处同源），SCOPE/RELATION/BINDING 为历史设想类型不再提供；原"前端列全 5 种"已被收窄取代。

6. ~~**domain-config save 的 extra JSONB↔String 映射确认**~~（已收口确认，2026-08-29）：`BizDomainConfigPgIT` 真库锁定——`JsonbStringTypeHandler` 映射语义等价（读出为 DB 规范化 JSON 文本、可直接再提交，SystemConfigJsonbPgIT 同范式同结论）；随任务新发现并补齐：save 原缺 `JsonValidationUtils` 前置校验（非法 JSON 会打到 PG 解析错误裸 99999），已补（system-config/condition 同范式）。附带修复 **P0 隐患**：`BizDomainMapper.selectByCode/selectByCodes` XML 列名误写 `domain_code`（DDL 列为 `code`），真库必报 42703——该查询是 `resolveDomainId` 底层（domain-config save/list/detail、DomainClassifyService、checkCanGrant 批量解析共同消费），单测 mock mapper 掩盖，PgIT 回归锁锁定。

### ✅ 满足

- 9 接口满足前端需求，请求/响应结构与 mock 对齐。契约 §5.1/§5.6 路径与后端实现一致（无路径错误）。
- domain-config save upsert 幂等覆盖新建/编辑，detail 用业务键二元组（domainCode+configType），无需独立 create/update。

### 备注

- **主从布局非单表**：本页是主从两表联动（BizDomain 主 + DomainConfig 从），与其他单表页（system-config/type-def）不同。主表有独立 create/update/remove（非 upsert），子表 save upsert。
- **全局域语义**：每租户仅一个全局域（`uk_biz_domain_global`），其范围隐式包含未被其他域认领的资源类型（DomainClassifyService 全局域语义）。前端不直接管理全局域创建（由系统初始化），仅展示 + 禁删。

## 9. 已知限制与收口状态（T-PERM-026 后）

- ~~biz-domain list 前端本地过滤+分页~~ → 已切服务端过滤+分页（2026-08-29）。
- extra JSON 校验：前端 `JSON.parse` 预拦截 + 后端 `JsonValidationUtils` 二次校验（T-PERM-026 补齐双层）。
- mock 保留 `deleted` 内部字段（对齐 schema delete_flag 范式），响应 clone 时剔除；`global` 随 Resp 收口改为下发（对齐后端终态）。`domainCode` 为 domain-config mock 内部冗余字段（便于按域过滤），响应 clone 时剔除。
- **mock 共享注册表**：`mock/_bizDomainRegistry.ts` 持有业务域内存数据，biz-domain 与 domain-config mock 共用同一份——运行时新建/删除的域对 domain-config save 的 `resolveDomainId(code)` 实时可见；域配置数据同范式下沉 `mock/_domainConfigRegistry.ts`（biz-domain remove 引用检查需要）。
- ~~全局域不可删靠 mock 校验~~ → Resp 返回 global 后前端预判禁用删除按钮 + 后端 remove 删除保护（20051）双层兜底；bootstrap 固定图已补 DOMAIN:VIEW（空库可访问，§7）。
- **删除二次确认**：业务域/域配置删除均在 hook `handleDelete*` 内前置 `ElMessageBox.confirm`（对齐 role/type-def 范式，持久配置类资源防误删）。
- **子表权限守卫**：「配置」按钮 `v-if="canViewConfig"` 隐藏无权入口；`selectDomain(row, canViewConfig)` 双保险守卫，无 `SYSTEM_CONFIG:VIEW` 时只选中域不发 /list 请求，避免可避免的 403。
- **全局域创建入口缺失（登记 T-PERM-046）**：DDL 注释称「全局域由管理 API 创建」，但 create 固定 global=false，真库无任何入口能创建 global=true 域（mock 有 GLOBAL 种子、真库无）——uk_biz_domain_global 与「全局域不可删」保护在真库形同虚设；DomainClassifyService 对全局域缺失容忍（语义退化但不报错）。
- **domain_config 并发双插已知限制（登记 T-PERM-046）**：save 的 check-then-insert 在并发窗口可双插同键两行（表无唯一键，仅普通索引）；每域至多 2 条配置、管理页低并发，实际风险极低，契约 §5.6 已标注。
- **删除保护并发窗口（登记 T-PERM-046）**：remove 引用检查与软删两条无锁语句间、save 域解析后 insert——并发交错可留指向已软删域的孤儿配置（不可达死数据，非越权）；管理页低并发窗口极窄，锁策略设计并入 T-PERM-046 统一处置。
