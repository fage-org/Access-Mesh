# 3.4 资源依赖页前端设计

> status: adopted
> 任务：T-FE-011（mock 驱动）
> 后端契约：api-contract.md §5.6（`resource-dependency/*`）/ §6.9（batch-sync）
> 后端任务：T-PERM-031（depends_on 本任务；已收口 2026-08-30，终态见 api-contract §5.6 resource-dependency 契约要点）
> last_reviewed: 2026-08-30（T-PERM-031 收口回写：🔧 清单 8 项处置、门禁五档类型级、Resp 补静态字段+bits 字符串线格式、PUT 全量替换落地）

## 1. 背景

资源依赖页：资源依赖关系 CRUD、依赖图可视化、循环依赖检测、批量同步。

- 依赖关系定义「授权源资源时自动补全目标资源权限」的级联规则
- `source*` = 源资源（被授权资源，对应 `resource_dependency.resource_entity_id`）
- `target*` = 目标资源（被依赖、需自动补全，对应 `resource_dependency.depends_on_resource_entity_id`）
- `sourceOperationBits` = 触发条件（源资源授权含这些操作位时触发，NULL=任意操作）
- `requiredOperationBits` = 目标资源需补全的操作位
- `autoGrant` = 是否自动授予目标资源权限

布局：单表格 CRUD（对齐 conflict-rule 范式）+ 表单弹窗 + 环检测对话框 + 依赖图抽屉。

## 2. 布局

```
┌─ PureTableBar ──────────────────────────────────────────────┐
│ [关键词] [重置]                    [环检测] [依赖图] [新增依赖] │
├─────────────────────────────────────────────────────────────┤
│ 依赖关系              │触发操作│要求操作│自动│描述│创建时间│操作│
│ 角色管理(MENU)->鉴权校验(API)│ VIEW   │ VIEW   │自动│ ... │...│编辑删除│
└─────────────────────────────────────────────────────────────┘

依赖图抽屉（el-drawer rtl 60%）：
┌──────────────────────────────────┐
│   ◯ 角色管理 ──> ◯ 鉴权校验       │
│       ↓                          │
│   ◯ 资源与操作                    │
│  (echarts graph 力导向布局)       │
└──────────────────────────────────┘
```

- 页面层：`display: flex; flex-direction: column; height: 100%`（对齐 conflict-rule）
- 表格滚动：`:deep(.el-table__body-wrapper) { max-height: calc(100vh - var(--table-offset)) }`
- 覆写 layout margin：`div.resource-dependency-page.main-content { margin: var(--space-3) }`（特异性 0,2,1）
- 依赖图：`el-drawer` direction="rtl" size="60%"，echarts graph 力导向布局，支持拖拽/缩放

## 3. 字段与交互

### 表格列

| 列 | prop | 渲染 |
|----|------|------|
| 依赖关系 | relation | `源资源名(类型) -> 目标资源名(类型)`，类型 el-tag |
| 触发操作 | sourceOps | bitsToOpNames（位运算拆解，null=任意） |
| 要求操作 | requiredOps | bitsToOpNames |
| 自动 | autoGrant | el-tag（true=success"自动"/false=info"手动"） |
| 描述 | description | 文本，空显 `-` |
| 创建时间 | createdAt | 等宽字体 |
| 操作 | operation | 编辑 / 删除（权限门控） |

**bits->操作码反向映射**（T-PERM-031 设计定案维持：后端不做 operationCodes 反解，前端建映射拆解）：
- `getOperationList` 返回每个操作的 `binaryBit`（2 的幂次，字符串线格式）
- hook 建 `bitToOp: Map<binaryBit, {code, name}>`，对 `sourceOperationBits`/`requiredOperationBits`（字符串线格式）做 BigInt 位运算拆解（hasBit，63 位安全）

**资源名称/类型映射**（应对后端 Resp 缺 name/typeCode）：
- `getResourceTree` 扁平化建 `resourceMap: Map<id, {name, resourceTypeCode, code, codeType}>`
- Resp 返回 `resourceEntityId`/`dependsOnResourceEntityId`，反查映射得名称和类型

### 表单（DependencyForm.vue）

- 源资源类型下拉 + 源资源下拉（联动，filterable，显示 `名称（code）`）
- 目标资源类型下拉 + 目标资源下拉（联动）
- 触发操作多选（按源资源类型过滤，含全局操作；空=任意操作触发）
- 要求操作多选（按目标资源类型过滤，必填非空）
- autoGrant 开关（**默认 false 且禁用**——预留字段，自动授权未实现（T-PERM-035 暂缓），后端拒绝保存 true 返回 20048；T-PERM-035 实现后放开）
- 描述文本域（可空，maxlength 512）
- 校验：源/目标资源必选 + 不能相同 + 要求操作必填非空
- 编辑模式资源对可改（全量替换契约，Q3=B）：mock 支持完整字段覆盖；真后端 🔧 补全 update DTO

**资源选择器交互**（Q4=A）：资源类型下拉 + 资源下拉联动，与 conflict-rule 操作权限选择器范式一致。类型切换时清空资源 ID 和操作码。

### 环检测对话框（CycleCheckDialog.vue）

- el-alert 标注"检测添加依赖是否会形成循环引用"
- 源资源类型 + 源资源下拉 / 目标资源类型 + 目标资源下拉（联动）
- 检测按钮 -> 调用 `checkDependencyCycle` API（业务键）
- 结果：`hasCycle`（success/error alert）+ 回显源/目标资源
- addDialog `hideFooter: true`，用户用右上角 X 关闭

### 依赖图抽屉（DependencyGraph.vue）

- echarts graph 力导向布局（`plugins/echarts.ts` 注册 `GraphChart`）
- nodes：资源节点（id/name/category=resourceTypeCode），symbolSize 按连接数加权
- edges：源资源 -> 目标资源（箭头方向）
- 交互：拖拽节点、滚轮缩放、roam 平移、tooltip 显示资源详情
- 颜色：从 CSS 变量取色（`getComputedStyle`），深色模式自适应
- 空数据显示 el-empty

## 4. API 核对（基线：mock 请求/响应；T-PERM-031 已收口 2026-08-30）

| 端点 | 请求 | 响应 | 状态 |
|------|------|------|------|
| `POST /resource-dependency/list` | `DependencyListReq{resourceEntityId?}` | `ItemsResp<ResourceDependencyResp>` | ✅（VIEW 门禁补齐；全量不分页定案维持） |
| `POST /resource-dependency/create` | `ResourceDependencyCreateReq`（业务键） | `ResourceDependencyResp` | ✅（+20054/20005/20044 预查） |
| `POST /resource-dependency/update` | `ResourceDependencyUpdateReq{id,...}` | `ResourceDependencyResp` | ✅（资源对业务键补全，PUT 全量覆盖） |
| `POST /resource-dependency/remove` | `IdsReq{ids}` | `Void` | ✅（类型级全有或全无） |
| `POST /resource-dependency/graph` | `DependencyListReq{resourceEntityId?}` | `ItemsResp<ResourceDependencyResp>` | ✅（VIEW 门禁补齐；扁平结构维持定案） |
| `POST /resource-dependency/check` | `ResourceDependencyCheckReq`（业务键） | `DependencyCycleCheckResp` | ✅（VIEW 门禁补齐） |
| `POST /resource-dependency/batch-sync` | `DependencyBatchSyncReq` | `Void` | 后端已收口（FULL diff 三元组匹配等）；前端 P0 维持 TODO（Q5=B） |

### 🔧 登记T-PERM-031（已随 T-PERM-031 全收口 2026-08-30）

- **ResourceDependencyResp 字段不全**：~~缺 `sourceResourceTypeCode`/`targetResourceTypeCode`（资源类型）、资源 `name`（只有 code）、`sourceOperationCodes`/`requiredOperationCodes`（只有 bits）、`ownerServiceCode`/`maintainSource`/`updatedAt`~~ **已补静态字段**（类型/名称/updatedAt/来源归属）；操作位改**字符串线格式**（63 位 bigint 位值列，T-PERM-028 同款）。operationCodes 数组经设计决策**不反解**（前端 bitToOp 映射维持——操作软删后残留位反解有歧义）。前端 `getResourceTree`+`getOperationList` 映射作为冗余快路径保留。
- **ResourceDependencyUpdateReq 字段不全**：~~缺 `sourceResourceCode`/`targetResourceCode`/`sourceCodeType`/`targetCodeType`，无法切换资源对~~ **已补全**（PUT 全量覆盖契约落地，前端全量提交不再被静默丢弃）。
- **list/graph/check 三端点无权限校验**：**已补类型级 DEPENDENCY:VIEW**。
- **list 无分页、仅按内部主键 resourceEntityId 过滤**：**维持定案**（量小非流水表，029/030 同款；关键词过滤前端本地完成）。
- **graph 返回扁平列表非图结构**：**维持定案**（前端建 nodes/edges）。
- **DEPENDENCY 权限种子缺失**：**bootstrap 固定图已补五条**（VIEW/CREATE/UPDATE/DELETE/SYNC 类型级不可转授，空库死锁防护）。
- **maintainSource 枚举不一致**：**已收口** schema 四值白名单（ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC，DTO @Pattern 校验；UI 创建行落 ADMIN_UI）。
- **batch-sync FULL diff 匹配只比 sourceCode+targetCode**：**已修**（三元组匹配：源+目标+COALESCE(source_bits,0)，对齐 uk 语义）。
- **batch-sync P0 标 TODO**（Q5=B）：前端不实现 UI 与 mock（后端端点已收口），维持后续阶段。

## 5. 权限接线

资源类型 `DEPENDENCY`，写权限 CREATE/UPDATE/DELETE **三档独立**（非 MANAGE，对齐后端 DependencyAppServiceImpl），另含 SYNC（batch-sync 专用）：

| perm 串 | 门控 | 后端校验 |
|---------|------|----------|
| `DEPENDENCY:VIEW` | 路由可达性 + 列表加载 + 环检测/依赖图按钮 | ✅ 类型级（list/graph/check 已补齐，T-PERM-031） |
| `DEPENDENCY:CREATE` | 新增按钮 | createDependency 类型级校验 |
| `DEPENDENCY:UPDATE` | 编辑按钮 | updateDependency 类型级校验（T-PERM-031 收窄，原实例级系 ID 空间错位） |
| `DEPENDENCY:DELETE` | 删除按钮 | deleteDependencies 类型级全有或全无（T-PERM-031 收窄） |
| `DEPENDENCY:SYNC` | （P0 不暴露按钮，batch-sync 标 TODO） | batchSyncDependencies 类型级校验 |

- SSOT：`views/system/resource-dependency/utils/perms.ts`（RESOURCE_DEPENDENCY_PERMS / PERM_LIST / VIEW_PERMS）
- 路由 `meta.auths`：`[...RESOURCE_DEPENDENCY_PERM_LIST]`
- mock 角色矩阵：admin 全权；sec（安全管理员）VIEW+CREATE+UPDATE+DELETE+SYNC；hr/auditor 只读 VIEW
- 环检测/依赖图按钮复用 VIEW 门控（后端 check/graph 无独立权限校验）

## 6. 组件

| 组件 | 路径 | 职责 |
|------|------|------|
| DependencyForm | components/DependencyForm.vue | 新增/编辑表单（资源对+操作码+autoGrant） |
| CycleCheckDialog | components/CycleCheckDialog.vue | 循环依赖检测对话框 |
| DependencyGraph | components/DependencyGraph.vue | 依赖图抽屉（echarts graph） |
| useResourceDependency | utils/hook.ts | 列表/CRUD/检测/引用数据映射 |
| types | utils/types.ts | DependencyFormData / 工厂 / maintainSource 枚举 |
| perms | utils/perms.ts | 权限码 SSOT |

### Step 1.5 组件识别

- 资源选择器（类型下拉+资源下拉联动）：DependencyForm 与 CycleCheckDialog 各自实现（结构简单，不抽取共享，对齐 conflict-rule DetectDialog 范式）
- bits->操作码映射：hook 内聚（DependencyForm 编辑初始化需 bitsToOpCodes，组件内建 bitToOp 自包含）
- 依赖图：echarts graph，本页独有，不抽取
- 不抽取共享组件，不登记 T-FE-001 组件池

## 7. mock 种子

对齐 resource-operation mock 资源 ID（201-232）和操作 binaryBit（CREATE=1/VIEW=2/UPDATE=4/DELETE=8）：

| id | 源资源 | 目标资源 | 触发操作 | 要求操作 | autoGrant | 描述 |
|----|--------|----------|----------|----------|-----------|------|
| 801 | 203 角色管理(MENU) | 221 鉴权校验(API) | VIEW | VIEW | true | 访问角色管理需先通过鉴权校验 |
| 802 | 204 资源与操作(MENU) | 222 资源树查询(API) | VIEW | VIEW | true | 资源与操作页需资源树查询接口 |
| 803 | 202 组织与用户(MENU) | 231 部门数据(DATA) | VIEW | VIEW | true | 组织与用户页依赖部门数据 |
| 804 | 212 编辑按钮(BUTTON) | 232 角色数据(DATA) | UPDATE | VIEW | false | 编辑按钮依赖角色数据查看（手动补全） |

- create/update 用业务键（sourceResourceTypeCode+sourceResourceCode+...），mock 内部解析为资源 ID
- update 全量替换（Q3=B）：资源对可改，mock 支持完整字段覆盖
- isDuplicate：同源/目标资源对 + 同 sourceOperationBits（对齐 schema uk_resource_dependency 唯一约束）
- check 循环检测：DFS 从 target 反查是否能到达 source（对齐后端 hasDependencyCycle.canReach）
- graph 返回扁平依赖列表（对齐后端 graph，前端建 nodes/edges）

## 8. 核对清单

| # | 检查项 | 状态 |
|---|--------|------|
| 1 | P0 骨架（路由/标题/表格+表单弹窗+环检测对话框+依赖图抽屉） | ✅ |
| 2 | Step 1.5 组件识别（资源选择器内聚，不抽取共享） | ✅ |
| 3 | API 核对（7 端点，🔧/❌ 登记T-PERM-031，batch-sync P0 标 TODO） | ✅ |
| 4 | P2 权限接线（hasPerms + 无权降级 el-empty） | ✅ |
| 5 | design_writeback（本文件 status: adopted） | ✅ |
| 6 | bits->操作码映射（bitToOp 位运算拆解，应对 Resp 缺操作码） | ✅ |
| 7 | 资源名称/类型映射（resourceMap 反查，应对 Resp 缺 name/typeCode） | ✅ |
| 8 | 依赖图可视化（echarts graph 力导向布局，注册 GraphChart） | ✅ |
| 9 | 编辑表单资源对可改（全量替换契约 Q3=B，mock 支持，真后端 🔧） | ✅ |
| 10 | batch-sync 标 TODO（Q5=B，不实现 UI 与 mock） | ✅ |
