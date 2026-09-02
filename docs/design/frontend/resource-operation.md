---
doc_type: design
title: 资源与操作定义 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-09-02   # 2026-09-02 T-FE-017 联调收口（§5 补 Gateway 注册登记与联调注记、§7 权限接线补固定图口径、§8 mock 校验句终态化、联调发现 maintain_source 落库缺陷已修）；2026-08-29 §5/§8 全量收口（T-PERM-028 落地：业务键切换/bigint 字符串线格式/extraClear/VIEW 门禁补齐/resource_type 联动预置）；2026-08-28 §8 增补第 6 项：resource_type 联动预置操作位自 T-PERM-023 改归属登记
---

# 资源与操作定义 前端设计

> 对应任务 T-FE-008（3.1 资源+操作定义页）。后端契约见 `docs/design/permission-center/api-contract.md` §5.3。

## 1. 定位

管理两类权限基础元数据：

- **资源实体**（`resource_entity`，树形）：菜单/按钮/接口/数据等受保护对象，支持树形 CRUD + 移动。
- **操作权限**（`operation_permission`，按资源类型维度）：CREATE/VIEW/UPDATE/DELETE 等动作，位运算（`binaryBit` 独占位 + `inheritMask` 继承掩码，实际权限 = 两者按位或）。

## 2. 布局结构

左右两栏 Grid（对齐用户管理/角色管理范式）：

```
┌─ 左侧资源面板 (minmax(240px,300px)) ─┐  ┌─ 右侧详情区 (1fr) ──────────────┐
│ [资源类型下拉 ▼]                     │  │ 资源信息条                        │
│ [搜索]                               │  │ typeCode/name/status/code/path    │
│ [刷新][新增]                         │  │ [新增子资源][编辑][移动][删除]    │
│ ┌─ el-tree ────────────────────────┐ │  ├──────────────────────────────────┤
│ │ ● 系统管理        sys-mgmt       │ │  │ 操作权限（该 resourceType）        │
│ │   ● 组织与用户    user           │ │  │ PureTableBar + pure-table          │
│ │   ● 角色管理      role           │ │  │ [新增操作]  编码/名称/位/掩码/有效位 │
│ │   ● 资源与操作    res-op         │ │  │ CREATE  创建  bit=1 mask=0 eff=1    │
│ └──────────────────────────────────┘ │  │ VIEW    查看  bit=2 mask=0 eff=2    │
└──────────────────────────────────────┘  └──────────────────────────────────┘
```

- **左侧**：资源类型下拉（数据源 `getTypeDefList` 过滤 `typeKey=resource_type`）+ 搜索（名称/编码）+ el-tree（节点：状态点 + 名称 + 编码）+ 刷新/新增按钮。
- **右侧**：选中节点 -> 资源信息条（字段 + 操作按钮）+ 操作权限表（跟 `resourceType` 联动，不跟单实例联动）。
- 未选中节点 -> `el-empty` 提示。

### 联动语义

- `selectedResourceTypeCode` 是全局筛选维度，切换时同时刷新资源树与操作权限表，并清空选中节点。
- 选中树节点只更新右侧资源信息条，不重载操作权限表（操作权限按 `resourceType` 维度，与单个资源实例无关）。

## 3. 字段定义

### 资源实体（ResourceResp）

| 字段 | 说明 | 编辑态 |
|---|---|---|
| resourceTypeCode | 资源类型编码（来自 type_definition） | 只读 |
| code | 资源编码（业务键） | 只读 |
| codeType | 编码类型（默认 default，业务键一部分） | 只读 |
| name | 资源名称 | 可编辑 |
| parentId | 父资源 id | 只读（走移动弹窗） |
| status | 状态（1=启用/0=停用） | 可编辑 |
| sortOrder | 排序号 | 可编辑 |
| extra | 扩展属性 JSON | 可编辑 |

### 操作权限（OperationPermissionResp）

| 字段 | 说明 | 编辑态 |
|---|---|---|
| resourceTypeCode | 资源类型编码 | 只读 |
| code | 操作编码（业务键，大写） | 只读 |
| name | 操作名称 | 可编辑 |
| binaryBit | 独占位（2 的幂次） | 可编辑 |
| inheritMask | 继承掩码（所继承操作 binaryBit 之和） | 可编辑 |
| effective | 有效位 = binaryBit \| inheritMask | 计算列 |

## 4. 交互流程

### 资源 CRUD

- **新增根资源**：左侧「新增」按钮 -> ResourceForm（mode=create, parentId=null）。
- **新增子资源**：右侧「新增子资源」-> ResourceForm（mode=create, parentNode=selectedNode）。
- **编辑**：右侧「编辑」-> 调 `getResourceDetail` 拉完整数据（树节点不含 extra）-> ResourceForm（mode=edit）。
- **移动**：右侧「移动」-> ResourceMoveForm（popover 树选目标父节点，可设为顶层）。
- **删除**：右侧「删除」-> 确认框 -> 级联删除子孙。
- **父选择器**：popover + el-tree，过滤同类型，编辑态排除自身及子孙（防环）。

### 操作权限 CRUD

- **新增操作**：操作权限表「新增操作」-> OperationForm（mode=create）。
- **编辑/删除**：行内按钮。
- **binaryBit 校验**：必须为 2 的幂次（前端校验 + mock 唯一性校验 `uk_operation_permission_typed_bit`）。

## 5. API 依赖

对齐 `api-contract.md` §5.3。后端实现：`ResourceController` + `OperationController`。

> **联调注记（T-FE-017，2026-09-02）**：本页消费 9 端点全部经 Gateway 真实链路收口（tree/operation list 先在册，create/update/move/remove/detail 与 operation create/update/remove 共 +8 端点补注册 bootstrap 清单）；operation detail 与 resource list 本页不消费未注册（后续消费页按页注册）。联调逐 DTO 比对零漂移；发现的 maintain_source 落库缺陷（create 未设值致 NOT NULL 500）已在后端修复并加 PgIT 回归锁。

| 接口 | 用途 | 核对 |
|---|---|---|
| `POST /api/perm/resource-entity/tree` | 资源树（按 resourceTypeCode 过滤） | ✅ |
| `POST /api/perm/resource-entity/detail` | 资源详情（编辑态拉 extra） | ✅ 业务键（T-PERM-028） |
| `POST /api/perm/resource-entity/create` | 创建资源 | ✅ |
| `POST /api/perm/resource-entity/update` | 更新资源 | ✅ 业务键 + extraClear（T-PERM-028） |
| `POST /api/perm/resource-entity/move` | 移动资源 | ✅ 业务键对 {resource, parent\|null}（T-PERM-028） |
| `POST /api/perm/resource-entity/remove` | 删除资源（批量） | ✅ {items:[业务键]}（T-PERM-028） |
| `POST /api/perm/resource-entity/list` | 资源列表（分页） | ✅（本页不消费；已补 RESOURCE:VIEW 门禁） |
| `POST /api/perm/resource-entity/batch-create` | 批量创建 | ✅（本页不消费） |
| `POST /api/perm/operation-permission/list` | 操作权限列表 | ✅（无分页，前端本地处理） |
| `POST /api/perm/operation-permission/detail` | 操作详情 | ✅ 业务键（resourceTypeCode 必填；全局操作概念已退役，T-PERM-028） |
| `POST /api/perm/operation-permission/create` | 创建操作 | ✅ 位字段十进制字符串（T-PERM-028） |
| `POST /api/perm/operation-permission/update` | 更新操作 | ✅ 业务键；code/type 不可改（T-PERM-028） |
| `POST /api/perm/operation-permission/remove` | 删除操作（批量） | ✅ {items:[业务键]}（T-PERM-028） |

## 6. 组件结构

```
views/system/resource-operation/
├── index.vue                     # 左右布局
├── utils/
│   ├── hook.ts                   # 状态 + CRUD（useResourceOperation）
│   ├── perms.ts                  # 权限 SSOT
│   └── types.ts                  # 表单类型/常量
└── components/
    ├── ResourceForm.vue          # 资源新增/编辑（含父选择器 popover）
    ├── OperationForm.vue         # 操作新增/编辑（binaryBit 2的幂次校验）
    └── ResourceMoveForm.vue      # 移动资源（目标父选择器）
```

### 可复用组件识别（T-FE-001 组件池）

- **资源树选择器**：本页 ResourceForm/ResourceMoveForm 的父选择器（popover + el-tree）与角色管理父角色选择器范式一致。T-FE-013（权限查询）/ T-FE-014（权限授予）推进时确认是否抽取为 `ReResourceTreeSelect`。

## 7. 权限接线

对齐后端 `ResourceManageAppService` / `OperationAppService` 的 `hasPermission` 校验。

| 权限码 | 资源类型 | 门控范围 |
|---|---|---|
| `RESOURCE:VIEW` | RESOURCE | 路由可达 + 资源树可见 |
| `RESOURCE:CREATE` | RESOURCE | 新增根资源 / 新增子资源 |
| `RESOURCE:MANAGE` | RESOURCE | 编辑 / 移动 / 删除（后端统一 MANAGE） |
| `OPERATION:VIEW` | OPERATION | 路由可达 + 操作权限表可见 |
| `OPERATION:CREATE` | OPERATION | 新增操作 |
| `OPERATION:MANAGE` | OPERATION | 编辑 / 删除操作（后端统一 MANAGE） |

### mock 角色矩阵

- **admin**：全清单（RESOURCE + OPERATION 所有）。

> 固定图口径（T-FE-017）：bootstrap 固定图持 RESOURCE/OPERATION 各 VIEW+CREATE+MANAGE 六条 scopeAll（VIEW 原有，CREATE/MANAGE 四条为 T-FE-017 补齐死锁防护）。
- **sec**（安全管理员）：RESOURCE/OPERATION 的 CREATE + MANAGE + VIEW（权限定义职责）。
- **hr/auditor**：VIEW_PERMS（RESOURCE:VIEW + OPERATION:VIEW，只读）。

## 8. API 核对清单（登记 T-PERM-028）

### 🔧 需改造（Phase 2 后端）——六项已全数收口（T-PERM-028，2026-08-29）

1. ~~**资源实体业务键切换**~~ **已收口（T-PERM-028）**：`detail/update/move/remove` 已切业务键 `(resourceTypeCode, code, codeType)`（codeType 缺省归一 default）；update 的 code 可更新字段已删（业务键不可变），extraClear 显式清空 extra，move 补跨类型/防环 20053（原内部 id 实现缺两项校验）。
2. ~~**操作权限业务键切换**~~ **已收口（T-PERM-028）**：`detail/update/remove` 已切业务键 `(resourceTypeCode, code)`（原 resourceTypeCode 可空=全局操作轨已随全局操作概念退役删除，2026-08-30，resourceTypeCode 必填）。
3. ~~**VIEW 门禁种子缺失**~~ **已收口（T-PERM-028）**：核实结论——tree 与 operation list 门禁 T-PERM-042 已补；真正缺的 `resource-entity/list`、`resource-entity/detail`、`operation-permission/detail` 三处已补类型级 VIEW（2026-08-29 用户决策全补）；种子由 DDL CRUD 预置组覆盖（2026-08-28 核实，半句不成立）。
4. **resource-entity list 分页**：后端 `ResourceListReq` 有分页参数，本页以树为主不消费，保留契约对齐。
5. ~~**bigint 字段 63 位精度**~~ **已收口（T-PERM-028）**：响应 DTO 加 `@JsonSerialize(ToStringSerializer.class)`（全项目 bigint 序列化策略首例），请求侧 Long 组件由 Jackson 宽容接受十进制字符串；前端线格式全切 string，显示/运算/排序全 BigInt 无损。表单内部保留 el-input-number 数值控件、提交转字符串（2026-08-29 用户决策：2⁵³ 内输入精确，保留增减按钮体验）；复评 P1 收口（2026-08-30）：编辑态位字段脏检查（未变更不重提交）+ 超精度高位值（>2⁵³）只读字符串精确展示，杜绝 Number 往返静默改写存储位值。
6. ~~**resource_type 创建联动预置 operation_permission**~~ **已收口（T-PERM-028，2026-08-29 用户决策实现）**：`type-definition/create` 在 typeKey=resource_type 时同事务预置 CRUD 四操作位 CREATE(1,0)/VIEW(2,0)/UPDATE(4,2)/DELETE(8,2)（DDL 预置组模板同款，新类型位段空闲无 uk 冲突；跨域写入先例 ServiceConfig 级联）。

### ✅ 满足

- `tree` 返回 `ItemsResp<ResourceTreeResp>`，每个 `item.root` 为一棵树根，森林语义。
- `create`（resource/operation）字段对齐 DTO，业务键唯一性校验（真实 uk 约束；mock 校验随路由段退役）。
- `move` 防环（自身/子孙）+ 跨类型拦截（真实 20053）。
- `remove` 级联软删子孙（resource，真实递归 CTE）。

> mock 终态（T-FE-017，2026-09-02）：`mock/resource-operation.ts` 的 11 个 fake-server 路由（旧 `/api/perm/**`，自 T-FE-041 失配）已删除；仅保留数据导出（resources/operations/presetOperationsForType），待 resource-dependency/type-def/permission-grant 三页联调时随各自 mock 退役（设计定案）。
- `operation-permission list` 无分页，前端本地过滤，量小可接受。

## 9. 验收记录

- [x] 左右网格布局（左资源树 + 右资源信息条 + 操作权限表），对齐用户管理页范式与 CSS 设计系统。
- [x] 资源类型下拉数据源复用 `getTypeDefList` 过滤 `resource_type`。
- [x] 资源树 CRUD + 移动（弹窗选目标父节点，防环 + 跨类型拦截）。
- [x] 操作权限 CRUD，binaryBit 2 的幂次校验 + 同类型内唯一性。
- [x] hasPerms 门控 `RESOURCE:VIEW/CREATE/MANAGE` + `OPERATION:VIEW/CREATE/MANAGE`，admin/sec 全权、hr/auditor 只读。
- [x] API 核对清单产出，🔧 项登记 T-PERM-028。
