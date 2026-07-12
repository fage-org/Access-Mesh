---
doc_type: design
title: 权限授予 前端 UX 设计
status: draft
domain: frontend
last_reviewed: 2026-07-12
---

# 4.1 权限授予 前端 UX 设计

> 任务：T-FE-014（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.5 / §6.4 / §6.5
> 本文只定义信息架构、展示内容、交互状态、扩展边界与适应性，不定义颜色、字号、间距等视觉样式。

## 1. 页面定位与核心决策

页面的唯一任务是：**选择一个角色，在资源树中直接配置对应操作权限，并清晰查看当前权限与本次变更。**

采用左中右三列、各自纵向滚动的工作台：

- 左栏：角色树。
- 中栏：资源与操作融合的权限树表。
- 右栏：当前权限与本次变更。

不采用三步向导，也不设置常驻“权限检查器”。权限条件、`canGrant` 等低频属性通过权限单元的「附加设置」弹窗配置。

### 1.1 UX 数据模型

| UI 对象 | 契约含义 |
|---|---|
| 当前角色 | `domainCode + roleTypeCode + roleExternalId` |
| 权限单元 | 资源类型 × 资源实例/全量范围 × 操作 |
| 实例权限 | `scopeMode=INSTANCE`，包含 `resourceCode + codeType` |
| 全量权限 | `scopeMode=ALL`，只包含资源类型 + 操作 |
| 附加设置 | `conditionCode + canGrant` |
| 子权限/范围 | `dependOn=主权限 id` 的一层附属权限 |
| 直接权限 | 当前角色自身配置、可由本页编辑的权限事实 |
| 派生权限 | 未来由资源依赖自动补全或角色组合产生的只读结果 |

资源和操作不再拆成两个连续步骤。中栏的一行资源与一列操作相交形成一个权限单元，点击单元格就是最直接的授权交互。

## 2. 总体布局

```text
┌─ 页面上下文：业务域 / 页面标题 / 刷新 ─────────────────────────────────────┐
│                                                                          │
│ ┌─ 左栏：角色树 ─────┐ ┌─ 中栏：资源 × 操作权限树表 ─────┐ ┌─ 右栏 ─────┐ │
│ │ 角色类型            │ │ 资源类型 / 搜索 / 视图筛选       │ │ 当前权限(12)│ │
│ │ 搜索                │ │                                  │ │ 本次变更(4) │ │
│ │                     │ │ 资源树              VIEW UPDATE │ │             │ │
│ │ ▾ 基础角色          │ │ 全部 MENU 资源       [ ]   [✓]  │ │ 按资源分组   │ │
│ │   ● 系统管理员      │ │ ▾ 系统管理           [✓]   [ ]  │ │ 权限定位     │ │
│ │   ● 安全管理员      │ │   ├─ 角色管理         [✓]   [✓]  │ │ 变更撤销     │ │
│ │ ▾ 组织角色          │ │   └─ 资源管理         [✓]   [ ]  │ │             │ │
│ │   ● 研发部          │ │                                  │ │ 放弃 / 保存  │ │
│ │ ▾ 个人角色          │ │ 单元格菜单：[附加设置][配置范围] │ │             │ │
│ └─────────────────────┘ └──────────────────────────────────┘ └─────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 三栏职责

| 区域 | 回答的问题 | 不承担的职责 |
|---|---|---|
| 左栏角色树 | 正在给谁配置权限？ | 不编辑角色本身，不分配用户角色 |
| 中栏权限树表 | 该角色对哪些资源可以执行哪些操作？ | 不常驻展示条件表单，不展示长变更清单 |
| 右栏权限/变更 | 当前已经有什么？这次改了什么？ | 不成为第二套权限编辑器 |

三栏各自独立滚动。切换角色、资源类型或右栏 Tab 时，其他区域保持位置与草稿状态，避免长树反复回到顶部。

## 3. 左栏：角色树

### 3.1 基本结构

左栏固定为角色选择区，包含：

- 业务域上下文：页面级选择；切换业务域会重新加载角色与资源上下文。
- 角色类型筛选：全部 / BASIC_ROLE / GROUP_ROLE / ORG / POSITION / PERSONAL。
- 搜索：匹配角色名称与外部标识，搜索结果保留所属类型和路径。
- 角色树：单选，当前角色保持高亮。

前端可增加五个“类型虚拟根”用于组织展示，但虚拟根不是后端角色，不能被选择、授权或作为 API 入参。

### 3.2 不同角色类型的适配

- BASIC_ROLE / GROUP_ROLE：展示自身角色层级。
- ORG / POSITION：展示同步生成的组织或岗位层级。
- PERSONAL：数量通常大，类型根下采用分页搜索或虚拟滚动，不要求一次生成完整树。
- 禁用角色：可以被搜索和查看，但不能进入编辑态，并显示不可编辑原因。

角色树的数据加载采用混合策略：层级浏览可使用 `abstract-role/tree`，大规模搜索和 PERSONAL 列表使用 `abstract-role/list`，不能把一次加载全部五类角色作为长期前提。

### 3.3 选择与切换角色

选择角色后：

1. 中栏加载该角色所在上下文的资源类型、资源树与操作定义。
2. 右栏加载该角色的直接权限清单。
3. 根据目标角色实例权限切换为可编辑或只读状态。

有未保存变更时选择另一角色，弹出：

- 保存更改后切换。
- 放弃更改并切换。
- 取消切换。

切换业务域使用相同保护逻辑。

### 3.4 GROUP_ROLE 的模型冲突

`T-FE-014` 写明五种角色都可选并配权，但权限中心 `overview.md` 和 `core-flows.md` 又规定 GROUP_ROLE 只聚合 `extra.basicRoleIds`，不直接配置权限。UX 采用能力驱动：

- 五种角色都可在左栏被发现和选中。
- 角色返回 `directGrantable` 能力。
- `directGrantable=false` 时，中栏展示有效权限的只读视图；右栏展示来源角色，并提供「管理所含基础角色」入口。
- 在产品模型未明确变更前，建议 GROUP_ROLE 默认 `directGrantable=false`。

## 4. 中栏：资源与操作融合的权限树表

### 4.1 为什么采用树表

资源通常是纵向展开很长的层级结构，而操作权限与每个资源强关联。使用树表可以同时保留：

- 资源的父子层级、完整路径和上下文。
- 同类型操作的横向比较。
- 一个明确的交叉权限单元，避免“勾资源后还要去另一区域找操作”。

树节点展开/折叠只控制导航，不代表授权；授权只由操作单元格表达。

### 4.2 资源类型上下文

中栏一次只展示一个资源类型：

- 顶部通过资源类型切换器选择 MENU、BUTTON、API、DATA 或未来新增类型。
- 操作列随资源类型动态加载自 `operation-permission/list`。
- 切换资源类型只改变中栏当前视图，不清除该角色其他类型的草稿。
- 类型入口显示当前直接权限数量和本次变更数量。

不同资源类型的操作集合可能完全不同，前端不得硬编码 CREATE / VIEW / UPDATE / DELETE 四列。

### 4.3 树表结构

```text
资源                         VIEW     CREATE     UPDATE     DELETE
全部 MENU 资源               [ALL]      [ ]        [ ]        [ ]
▾ 系统管理                   [✓]        [ ]        [ ]        [ ]
  ├─ 组织与用户              [✓]        [ ]        [✓]        [ ]
  ├─ 角色管理                [✓]        [ ]        [~]        [ ]
  └─ 资源与操作              [+]        [ ]        [ ]        [ ]
```

- 第一列是可展开的资源树。
- 后续列是当前资源类型支持的操作。
- 第一行是固定的全量范围行，详见 §5。
- 每个交叉单元格是一个权限单元。
- 单元格可附带条件、允许转授权、子权限数量和来源标记，但不展开长文本。

### 4.4 单元格状态

| 状态 | 可编辑 | 点击主区域 | 更多操作 |
|---|---:|---|---|
| 未授权 | 是 | 加入待新增 | 无 |
| 已直接授权 | 是 | 加入待移除 | 附加设置 / 配置范围 |
| 待新增 | 是 | 撤销本次新增 | 附加设置 / 配置范围 |
| 待移除 | 是 | 恢复原权限 | 查看原配置 |
| 已修改属性 | 是 | 加入待移除 | 附加设置 / 配置范围 / 撤销修改 |
| 被 ALL 覆盖 | 视事实而定 | 查看覆盖关系 | 查看直接实例记录 / 清理冗余 |
| 派生或自动补全 | 否 | 查看来源 | 跳转来源规则 |
| 操作者无法授予 | 否 | 不变更 | 查看不可授予原因 |

“主区域点击切换授权”与“更多按钮打开附加配置”必须分离，避免用户为了查看条件而误撤销权限。

### 4.5 操作信息

操作列标题除名称外还需提供：

- 操作编码。
- 是否包含其他操作语义（`inheritMask` 摘要）。
- 当前列直接权限数量、ALL 状态和草稿变化数量。

若一个操作因继承关系产生有效权限，必须与直接授权状态区分，不能都显示成同一个可取消勾选态。

### 4.6 搜索和视图筛选

中栏支持：

- 按资源名称、编码和完整路径搜索。
- 仅看已授权。
- 仅看本次变更。
- 仅看含条件或子权限。
- 展开全部 / 收起全部。

搜索命中子节点时保留并展开祖先路径。筛选只改变展示，不改变授权草稿。

### 4.7 批量配置

默认交互是直接点击单元格。需要批量操作时进入明确的“批量模式”：

1. 资源列出现行选择框，选择资源节点。
2. 顶部选择目标操作。
3. 执行批量授予、批量撤销或批量附加设置。

行选择框只表示批量目标，不表示资源已经授权。批量动作不得隐式扩展到折叠子孙；需要包含子孙时使用明确动作「选择当前及子孙」。

## 5. 全量范围 `scopeMode=ALL`

### 5.1 展示方式

每个资源类型树表顶部固定一行「全部 {资源类型名称} 资源」。该行与实例资源使用相同操作列：

```text
全部 DATA 资源     DATA_READ [ALL✓]   DATA_EDIT [ ]
```

一个 ALL 单元格准确表示：

```text
resourceTypeCode + operationCode + scopeMode=ALL
```

它不包含 `resourceCode/codeType`，也不创建 `data:all` 一类虚拟资源。

### 5.2 ALL 与实例权限并存

同一资源类型 + 操作下，ALL 与实例授权在契约上可以并存，但实例授权会被全量权限覆盖。交互规则：

- 开启 ALL 时，如存在实例权限，先展示影响摘要。
- 默认建议「启用 ALL，并移除被覆盖的 n 条实例权限」。
- 允许选择「保留实例记录」，但右栏和变更预览标记为冗余。
- ALL 生效时，该操作列的实例单元格展示“被 ALL 覆盖”，但仍可查看其是否存在直接实例记录。
- 关闭 ALL 不自动生成实例权限；若实例移除尚未保存，可一并撤销替换操作。

### 5.3 ALL 的批量语义

ALL 是“某资源类型下某操作的全量范围”，不是：

- 给所有资源授予所有操作。
- 勾选资源树根节点。
- 自动勾选当前已加载的所有实例。

因此它必须始终按操作列分别配置。

## 6. 权限附加设置弹窗

### 6.1 入口

已授权、待新增或已修改的权限单元显示「附加设置」入口。入口也可从右栏权限项打开。

弹窗标题完整说明当前对象：

```text
角色：安全管理员
权限：角色管理 / UPDATE / INSTANCE
```

### 6.2 弹窗内容

弹窗只承载低频附加属性：

- 权限条件：无条件或选择一个启用的 `conditionCode`。
- 允许继续授权：对应 `canGrant`。
- 当前权限身份摘要：资源、操作、范围，只读。
- 子权限摘要与「配置子权限/范围」入口。

条件不是权限的必经步骤。用户只点击单元格即可产生无条件、不可继续授权的默认权限。

### 6.3 条件选择与内联新建

- 按名称/编码搜索条件，展示规则摘要和 Gateway 可评估状态。
- 只能新选启用条件。
- 已绑定后停用的条件可回显，但保存前必须替换或清除。
- 有 `CONDITION:CREATE` 时显示「新建条件」。创建成功后自动选中并回到当前弹窗。
- 无创建权限时不展示不可用入口。
- 条件编辑器复用权限条件页的规则模型、序列化和校验逻辑。

### 6.4 打开未授权单元

未授权单元格不直接显示附加设置。用户可通过单元格菜单选择「授予并配置」，一次完成：

1. 将权限加入待新增。
2. 打开附加设置弹窗。

取消弹窗不会撤销权限新增；若希望两者一起取消，应提供明确的「取消授权」动作，避免弹窗关闭语义不清。

## 7. 子权限 / 范围权限

### 7.1 展示入口

子权限属于某一个主权限单元：

- 中栏单元格显示子权限数量标记。
- 附加设置弹窗显示摘要和「配置子权限/范围」。
- 右栏权限项可直接打开范围配置。

范围配置内容仍是资源与操作强关联的树表，因此使用大尺寸抽屉或独立工作区，不塞入普通属性弹窗：

```text
主权限：销售报表 / DATA_READ / INSTANCE
允许子资源类型：DATA

资源                         DATA_READ     DATA_EDIT
全部 DATA 资源                 [ALL]          [ ]
▾ 华东数据                      [ ]            [ ]
  ├─ 上海数据                  [✓]            [ ]
  └─ 杭州数据                  [✓]            [ ]
```

### 7.2 交互规则

- 可选子资源类型由当前业务域 `SUB_PERM` 配置驱动。
- 子权限同样支持 `INSTANCE / ALL`、条件和 `canGrant`。
- 子权限只允许一层，不再显示范围入口。
- 删除主权限时，右栏变更详情明确展示将级联移除的子权限数量与条目。

### 7.3 新主权限的保存

`add-child` 需要真实 `parentPermissionId`，新主权限在保存前只有前端临时键。前端允许先配置完整草稿，保存时：

1. 调用 `role-resource-permission/save` 保存主权限。
2. 用稳定权限键从响应中匹配新主权限 id。
3. 调用 `add-child` 保存对应子权限。
4. 处理已有子权限的修改与移除。
5. 重新加载服务端事实作为新基线。

当前不是后端单事务。若主权限成功而子权限失败：

- 显示「主权限已保存，部分范围保存失败」。
- 重新加载服务端事实。
- 保留失败的子权限草稿并提供「重试未完成项」。

## 8. 右栏：当前权限与本次变更

右栏是持续可见的结果视图，包含两个 Tab。

### 8.1 当前权限

展示当前角色的直接权限，并为未来派生来源预留展示能力：

- 按资源类型分组。
- 组内优先展示 ALL 权限，再按资源树路径组织实例权限。
- 每项显示资源、操作、范围、条件、`canGrant`、子权限数量和来源。
- 支持只看 ALL、含条件、含子权限、可继续授权、派生权限。
- 点击权限项，中栏切换到对应资源类型、展开资源路径并聚焦权限单元。

右栏只提供定位、查看和上下文动作，不重复绘制一套可直接勾选的权限矩阵。

### 8.2 本次变更

按新增、修改、移除、子权限变更分组：

```text
新增 2
  + MENU / 角色管理 / UPDATE / INSTANCE
  + DATA / DATA_READ / ALL

修改 1
  ~ API / 鉴权校验 / VIEW：绑定 office-hours

移除 1
  - MENU / 组织用户 / DELETE
```

每个变更项支持：

- 点击定位中栏权限单元。
- 撤销该项变更。
- 展开查看变更前后属性。
- 查看主权限删除导致的子权限级联影响。

右栏底部持续展示「放弃全部更改」与「保存全部更改」。无变更时保存按钮不可用，并明确显示当前配置已同步。

### 8.3 自动授权预览

T-PERM-035 启用后，本次变更 Tab 增加只读分组「系统将自动补全」：

- 明确展示“因为源资源 A 依赖目标资源 B，将新增 B 的某操作”。
- 自动补全不与用户直接新增混在同一分组。
- Phase 1 只保留入口位置并标注后端能力未启用，不伪造结果。

## 9. 草稿与保存

### 9.1 草稿模型

选择角色后保存一份 `baseline`，所有交互只修改本地 `draft`。主权限稳定键：

```text
domainCode + resourceTypeCode + scopeMode
+ resourceCode? + codeType? + operationCode
```

- 新键进入 `add`。
- 已有键属性变化进入 `update`，使用服务端 id。
- 已有键被取消进入 `remove`，使用服务端 id。
- 子权限键额外包含父权限稳定键。
- 右栏始终由 baseline 与 draft 的差异计算，不另存一份容易失真的变更清单。

### 9.2 保存前校验

确定性错误阻断保存：

- 目标角色禁用或不可管理。
- 操作者不能授予某权限。
- 条件已停用或不存在。
- 子权限资源类型不符合 `SUB_PERM`。
- 请求没有实际变化。

冗余和影响类问题作为警告：

- ALL 覆盖实例权限。
- 删除主权限级联删除子权限。
- 自动授权将产生额外权限。

### 9.3 并发与离开保护

- 保存期间三栏只读，防止重复提交和草稿变化。
- 有草稿时切换角色、业务域、关闭或刷新页面均触发离开保护。
- 长时间编辑需有 `configVersion` 或 `updatedAt` 做乐观并发校验。
- 服务端已变化时停止覆盖，提供重新加载与差异对比。

## 10. 加载、空状态和权限降级

| 场景 | 展示与动作 |
|---|---|
| 未选择角色 | 左栏提示选择角色；中右栏显示关联空状态 |
| 角色无直接权限 | 中栏仍可配置；右栏提示“尚未配置直接权限” |
| 无 ROLE:VIEW | 整页无权状态，不发起角色权限加载 |
| 有 VIEW 无 MANAGE | 三栏可浏览，中栏只读，右栏无保存动作 |
| GROUP_ROLE 不可直接配权 | 中栏只读展示有效来源，提供管理基础角色入口 |
| 资源类型无操作 | 提示先在“资源与操作”定义操作，并提供跳转 |
| 资源树为空 | 提示先同步或创建该类型资源 |
| 条件加载失败 | 不阻断基础权限编辑；附加设置弹窗局部重试 |
| 保存部分失败 | 重载服务端事实，右栏保留失败项并支持重试 |
| 权限列表返回空 | 必须区分“没有配置”与“没有查看权限” |

页面同时存在粗粒度和实例级门控：

| 能力 | 门控 |
|---|---|
| 进入页面和查看角色权限 | `ROLE:VIEW` |
| 新增/修改/移除主权限 | 目标角色 `ROLE:MANAGE` + 操作者对待授权限拥有 `canGrant=true` |
| 查看条件候选 | `CONDITION:VIEW` |
| 内联新建条件 | `CONDITION:CREATE` |
| 配置子权限 | 目标角色 `ROLE:MANAGE` + `SUB_PERM` 约束 |

`hasPerms` 只用于路由和通用按钮的粗粒度降级，不能代替后端对具体角色和具体权限单元的校验。

## 11. 适应性与未来扩展

### 11.1 宽度适应

| 可用宽度 | 布局变化 |
|---|---|
| 宽屏 | 左中右三栏同时显示，中栏占主要宽度 |
| 中等宽度 | 左栏可折叠；右栏改为抽屉或覆盖面板；中栏保持树表主体 |
| 窄屏 | 角色、权限树表、权限清单三个视图切换；草稿与当前角色上下文保持不变 |

窄屏切换只是同一工作台的呈现方式，不创建另一套向导流程。

### 11.2 高度与大数据适应

- 三栏独立滚动并记忆滚动位置。
- 角色树支持懒加载、虚拟滚动和服务端搜索。
- 资源树支持懒加载和服务端搜索，搜索结果可按路径回填。
- 树表必要时使用行虚拟化；固定资源列与操作表头，长树滚动时仍保留上下文。
- 操作列很多时支持横向滚动和列筛选。
- 子权限按主权限延迟加载，不随当前权限一次加载全部 children。

### 11.3 能力驱动

前端按能力元数据渲染，而不是判断固定类型字符串：

```ts
interface GrantCapabilities {
  directGrantable: boolean;
  supportsInstance: boolean;
  supportsAll: boolean;
  supportsCondition: boolean;
  supportsDelegation: boolean;
  supportsChildren: boolean;
  childResourceTypeCodes: string[];
}
```

未来新增角色类型、资源类型、操作或范围模型时，三栏结构与树表交互不需要重做。

### 11.4 权限来源

中栏单元格和右栏权限项必须区分：

- MANUAL：直接配置，可编辑。
- AUTO_DEP：资源依赖自动补全，只读并显示来源。
- COMPOSED / INHERITED：角色组合或继承产生，只读并显示来源角色。

不能只用同一个勾选状态表示三类事实。

## 12. 组件识别（T-FE-001）

| 候选 | 结论 | 原因 |
|---|---|---|
| 角色选择器 | 登记角色树数据适配层，不直接复用角色管理父角色树 | 本页需五类型、类型虚拟根、搜索、懒加载与实例能力；父角色选择仅同类型单选 |
| 资源树选择器 | 登记资源树数据适配层，不抽取整块权限树表 | 权限树表把资源层级与动态操作列融合，语义明显不同于父资源单选 |
| 权限条件选择器 | 确认适合抽取 `ReConditionPicker` | 条件管理页与附加设置弹窗共享搜索、摘要、启用状态和内联新建 |
| 条件规则编辑器 | 适合从 ConditionForm 抽取结构化编辑器 | 两处必须使用同一规则序列化和校验模型 |

共享组件只共享稳定语义，不为了“都是树”而强行复用整块 UI。

## 13. API 依赖与契约缺口（登记 T-PERM-034）

### 13.1 当前接口

| 用途 | 接口 |
|---|---|
| 角色树 / 搜索 | `POST /api/perm/abstract-role/tree` / `list` |
| 资源类型 | `POST /api/perm/type-definition/list` |
| 资源树 | `POST /api/perm/resource-entity/tree` |
| 操作列 | `POST /api/perm/operation-permission/list` |
| 条件候选 | `POST /api/perm/permission-condition/list` |
| 已有权限 | `POST /api/perm/role-resource-permission/list` |
| 主权限保存 | `POST /api/perm/role-resource-permission/save` |
| 子权限查询 | `POST /api/perm/role-resource-permission/children` |
| 子权限新增/移除 | `POST /api/perm/role-resource-permission/add-child` / `remove-child` |

### 13.2 需收敛的缺口

1. GROUP_ROLE 是否允许直接持有权限存在模型冲突；建议保留可选、只读组合权限，默认 `directGrantable=false`。
2. `listPermissions` 无 VIEW 时返回空数组，与角色确实无权限无法区分；应返回明确拒绝或能力字段。
3. `RolePermissionItemResp` 缺 `grantSource/grantDepId`，中栏和右栏无法区分直接、自动补全与派生来源。
4. 缺少每个候选权限的 `grantableByOperator + reason`，无法在树表编辑前禁用不可授予单元格。
5. 角色响应缺 `canView/canManage/directGrantable`，只能做粗粒度 `hasPerms`。
6. 主权限 save 与 add-child 非原子；长期建议 `save` 支持 client key 引用父权限并单事务保存 children。
7. 页面需要根据主资源类型获得结构化 `childResourceTypeCodes`，当前只能解析 domain-config.extra。
8. `implementation.md` 仍出现 `scopeAll` 和计数型响应，实际 DTO/契约使用 `scopeMode` 且 Controller 返回 items，应统一。
9. 缺少 `configVersion/updatedAt`，无法可靠处理多人并发编辑。
10. 子权限新增可批量、移除仅单条，属性更新是否允许通过主 save 的 update 传子权限 id 需明确。
11. 大规模角色树与资源树缺乏统一的懒加载/搜索定位契约；PERSONAL 和大型资源目录不能依赖全量树。

## 14. 建议验收场景

1. 左栏切换五类角色，中右栏保持正确上下文与只读/编辑状态。
2. 中栏在资源树行与操作列交叉处直接新增、撤销权限，无额外“选资源”步骤。
3. 搜索深层资源后保留祖先路径，并能从右栏反向定位到该单元格。
4. 开启某操作的 ALL 权限时，明确处理被覆盖的实例权限。
5. 从单元格打开附加设置，绑定条件与 `canGrant`，关闭后在单元格和右栏回显摘要。
6. 使用批量模式选择当前节点及子孙，对同一操作批量授权，不影响折叠且未选择的节点。
7. 右栏按新增、修改、移除展示差异，支持逐项撤销和定位。
8. 新主权限先配置子权限，模拟主权限成功、子权限失败后保留草稿重试。
9. VIEW-only 用户可浏览三栏但不能修改；无 VIEW 用户不加载权限事实。
10. 长角色树、长资源树和多操作列下仍能通过懒加载、虚拟化和独立滚动工作。

## 15. P0 实现状态（T-FE-014，2026-07-11）

### 15.1 已实现

- 三栏工作台（角色树 / 资源×操作权限树表 / 当前权限+本次变更双 Tab）
- 左栏角色树：五类型虚拟根 + 类型筛选 + 搜索 + 单选 + directGrantable/canView/canManage
- 中栏权限树表：资源类型切换 + 资源树 + 动态操作列 + ALL 范围行 + 单元格点击切换
- 单元格 6 态：UNAUTHORIZED/GRANTED/PENDING_ADD/PENDING_REMOVE/MODIFIED/ALL_COVERED
- 附加设置弹窗：条件选择 + canGrant + 子权限入口；条件内联新建直接复用 ConditionForm（决策点 1）
- 子权限抽屉：子资源类型×操作树表 + add-child/children/remove-child
- 右栏：当前权限 Tab（按资源类型分组）+ 本次变更 Tab（add/update/remove diff + 逐项撤销）
- 草稿模型：baseline + draft + 稳定键 diff
- 两步保存：save 主权限 -> 匹配新 id -> add-child 子权限
- 部分失败处理（决策点 5）：主成功子失败 -> 重载 baseline + 保留失败草稿 + 重试；可控失败通过 `localStorage.__permGrant_simulateChildFailure=true`
- 权限门控：ROLE:VIEW 查看 / ROLE:MANAGE 编辑；CONDITION:VIEW/CREATE 条件
- 离开保护：切换角色未保存弹窗 + beforeunload
- 能力驱动（决策点 6 按归属拆分）：角色节点 directGrantable/canView/canManage；资源类型 supportsInstance/All/Condition/Delegation；业务域 supportsChildren/childResourceTypeCodes；候选单元 grantableByOperator/denyReason（operatorCapability 组合判定）

### 15.2 暂缓标 TODO

- §4.7 批量模式（行选择 + 批量授予/撤销）
- 单元格派生/自动补全态（AUTO_DEP/COMPOSED）
- GROUP_ROLE "管理基础角色"入口（中栏只读空状态已实现，决策点 3）
- 自动授权预览（T-PERM-035，§8.3）
- 懒加载/虚拟滚动/宽度适应三档（§11.1/11.2）
- configVersion 乐观并发（§9.3，缺口 9）
- 右栏定位中栏权限单元（§8.1 点击定位）

### 15.3 决策点调整结论

| 决策点 | 结论 |
|---|---|
| 1. 条件内联新建 | 直接 import ConditionForm 复用（不降级 JSON 文本域） |
| 2. 批量模式 | P0 暂缓 |
| 3. GROUP_ROLE | directGrantable=false 只读空状态；有效权限展开暂缓 |
| 4. 路由权限码 | ROLE:VIEW/ROLE:MANAGE（非临时口径）；T-PERM-034 补能力语义 |
| 5. 子权限部分失败 | hook 实现捕获/重载/保留/重试；可控 rejection 测试 |
| 6. 能力字段 | 按归属拆分（角色/资源类型/业务域/候选单元），不统一默认 true |

### 15.4 组件识别（T-FE-001 回写）

| 候选 | 结论 |
|---|---|
| 条件编辑/选择器 | 已达第二个使用场景（条件管理页 + 本页附加设置），确认后续抽取 ReConditionPicker/ReConditionEditor |
| 角色树数据适配层 | 登记，P0 不抽取整块 UI（页面语义差异大） |
| 资源树数据适配层 | 登记，P0 不抽取整块 UI（页面语义差异大） |

### 15.5 评审修复（2026-07-11，9 项 P1 + 补充）

| # | 问题 | 修复 |
|---|---|---|
| P1-1 | mock 重复注册 5 共享端点 | 删除重复端点，API adapt* 适配现有 mock 结构（角色树 items[0].root.children + 字段映射 + 能力推断；资源类型过滤 resource_type + 能力推断；资源树解包 items[].root；操作 code->operationCode；条件 summary 派生） |
| P1-2 | 空主权限 save | saveAll 主权限 add/update/remove 全空时跳过 save；mock save 端点 GRANT_REQUEST_EMPTY 兜底 |
| P1-3 | dependOnTempKey 为 null | saveAll 优先用 child.dependOn 解析父权限 id |
| P1-4 | remove-child 失败判断 | failedChildRemove 纳入失败列表 + 修正最终判断 |
| P1-5 | 子权限抽屉只取 [0] + 无 ALL + 无附加设置 | 类型切换器 + ALL 行（supportsAll）+ 附加设置入口（emit open-setting） |
| P1-6 | 撤销也检查 grantableByOperator | toggleMainCell 只在新增时检查（canGrant 不约束回收） |
| P1-7 | domainCode 强制 "example" | selectRole 保持空（契约空域=全局）；mock permissionFacts domainCode="" |
| P1-8 | 离开保护不拦截路由 + 不移除监听器 | onBeforeRouteLeave + onUnmounted cleanup |
| 补充 | supportsCondition/Delegation 未消费 | AdditionalSettingDialog 按能力禁用条件/canGrant + 子权限支持（setChildCellAttr） |

### 15.6 第三轮评审修复（2026-07-11，8 P1 + 1 P2）

| # | 问题 | 修复 |
|---|---|---|
| P1-1 | 响应类型与真实后端契约不兼容（RolePermissionItemsResp 只有 items；ItemResp 无 domainCode/grantSource，scopeAll 是 boolean） | API 层新增 RawRolePermissionItem/RawRolePermissionListResp + adaptRolePermissionItem/adaptRolePermissionList：scopeMode/scopeAll 双字段容错、domainCode 从请求补齐、grantSource 默认 MANUAL（待 T-PERM-034）、domainCapability/operatorCapability 防御 default（保守空，禁止默认全可用）；getRolePermissionList/saveRolePermission/addChildPermission 均经 adapt |
| P1-2 | 角色树与授权 mock 角色事实不一致（共享树 GROUP_402/403/PERSONAL_501/ORG_1/POSITION_30，授权 mock 不认识；role_admin/role_report_viewer 不在共享树） | roleFacts 对齐共享树 9 标识（删 role_admin/role_report_viewer，新增 GROUP_402/403，ORG_501->ORG_1，POS_601->POSITION_30，PERSONAL_u10001->PERSONAL_501）；permissionFacts 迁移 role_admin->BASIC_201、role_report_viewer->BASIC_202；不修改 role-manage.ts |
| P1-3 | 加载失败保留上一角色权限状态（selectRole 先设 currentRole 再加载，失败不清空/回滚） | 重构 loadRolePermissionSnapshot(role, domainCode) 返回完整 snapshot 不修改 refs；selectRole 先加载快照成功后一次性提交 currentRole/domain/baseline/draft/capabilities，失败旧上下文完全不变；reloadBaseline 同步重构 |
| P1-4 | 父权限 ID 无法解析时仍报告保存成功（failedChildAdd.push 后未设 childFailureOccurred） | parentId null 时追加 childFailureOccurred = true + 保留 FailedChildOp { op:"add", child, childKey } |
| P1-5 | 删除主权限后又重复删除其子权限（后端级联删除，remove-child 返回 CHILD_PERMISSION_NOT_FOUND 误判部分失败） | 子 remove diff 过滤 d.permission.dependOn && mainRemove.includes(dependOn) 的项，跳过 remove-child |
| P1-6 | 失败的子权限删除无法重试（统一 set 不产生 diff；硬编码 INSTANCE/default） | failedChildren 改为 FailedChildOp[] { op, child, childKey }，childKey 直接取 childDiff 的 d.key；retry 时 add->draft.set(childKey, child)、remove->draft.delete(childKey)；删除 findParentTypeCode/ResourceCode/OperationCode 反查函数 |
| P1-7 | 主权限附加设置未接入资源能力（API/BUTTON supportsDelegation=false 仍可开 canGrant） | PermissionMatrixPanel.onOpenSetting 从 currentResourceType 传入 supportsCondition/supportsDelegation |
| P1-8 | 子权限新增绕过 grantableByOperator（buildChildContext 只查只读，onToggle 无能力检查） | buildChildContext 调 store.isGrantableByOperator(selectedChildType, operationCode)；onToggle 新增路径（!inDraft && !inBase）检查，撤销/恢复不检查 |
| P2 | 角色搜索没有实际过滤（共享 tree 端点不消费 keyword，adaptRoleTree 不过滤） | adaptRoleTree 接收 keyword，适配后本地过滤 roleName/roleExternalId，保留命中角色所在的类型虚拟根 |
