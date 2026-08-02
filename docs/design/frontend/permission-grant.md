---
doc_type: design
title: 4.1 权限授予 前端设计（v3）
status: draft   # draft → adopted（T-FE-036 done 时）
domain: frontend
last_reviewed: 2026-08-01
---

# 4.1 权限授予 前端设计（v3）

> 任务：T-FE-036（重建。v1/v2 于 2026-07-26 因交互不满意整体删除，归档于 `docs/archive/2026-07-26/`）
> 后端契约：`api-contract.md §5.5`（L258，`role-resource-permission/*`）/ `§6.4`（list 接口）/ `§6.5`（L999，子权限语义）/ `§6.5.1`（L1047，**唯一写入口 apply-grant-plan**，第十二轮单入口收敛）
> 后端任务：T-PERM-034（范围更新见 §12）
> 本页设计为**领域模型授权**（主体 ↔ 资源操作），不承载运行时权限查询（4.2 权限查询页）。

## 0. 设计目标（2026-08-01 评审收敛，15 项决策）

1. **根因**：查看与授予是两种心智任务，v1/v2 混在同一交互里互相干扰；继承关系（资源父子 + 操作继承）未体现，查看杂乱。
2. **继承模型**：①资源树父子继承（父节点授权作用于全部子孙）②操作权限继承（`inheritMask`，高级操作隐式覆盖低级操作）——两者都必须可展示来源。
3. **页面结构**：同页**查看为主**；授予 = 弹窗；弹窗确定后矩阵**实时模拟变更**（diff）；**统一提交**到后端。
4. **查看形态**：**矩阵**（资源行 × 操作列）；来源用**不同图标**；**操作列可配置**（用户自选显示哪些操作列）；树级继承 / 操作继承**两个开关可独立关闭**。
5. **资源行**：树形行（层级缩进）+ 展开/折叠 + 搜索定位。
6. **单元格**：简洁（有/无 + 来源图标 + 小角标），细节悬浮/点击详情。
7. **授权弹窗**：**以操作权限为维度**：①选操作权限 → ②展示该操作当前的资源授予情况（只读现状）→ ③选范围 + 资源 → ④条件 + canGrant。
8. **子权限**（`depend_on`）：不进弹窗，在**查看详情层**管理（统一走 `apply-grant-plan` 记录级 creates/updates/removes，第十二轮收敛）。
9. **变更预览**：矩阵就地标记（新增绿 / 删除线 / 修改黄）+ **右栏变更清单**（定位 / 逐条撤销）+ 底部"保存全部 / 放弃全部"。
10. **继承默认**：默认**开**（显示含继承，页头标注"模拟 CHILD 展开视图，非运行时默认"）；**直接授权与继承用颜色区分**（直接=实色/深色，继承=淡色，来源类型用图标区分）；开关用于"只看直接授权"的干净视图（与运行时默认一致）。
11. **条件模型**：弹窗内**单条件**（无条件或一个条件）；**详情层可添加/删除多分支**（同键多条件并存，后端已支持：uk 含 `condition_id` + 运行时 OR 评估）。
12. **主体入口**：**两入口共用一套组件**（路由/参数区分主体类型）：角色（BASIC_ROLE + GROUP_ROLE）/ 组织（ORG + POSITION）；**PERSONAL 预留**（首期移除：个人 `abstract_role` 生命周期待后端同步链路落地后恢复，见 §1.1/§12 注）。原因：有角色权限的主体不一定有组织/用户权限，两类授权是独立领域能力。
13. **分组角色**：GROUP_ROLE **只读**——左栏展开为其关联的基础角色（`extra.basicRoleIds`，`abstract-role/extra-roles/list`，已联调 ✅），选中基础角色后按普通基础角色查看/授权（授权目标 = 基础角色本身，与运行时展开语义一致，无需聚合视图）。
14. **来源链计算**：**前端自算**（资源树 + `inheritMask` + list 主权限，纯函数对齐引擎语义）；list 经 `includeChildren=false` 只取主权限（T-PERM-034 补）；T-PERM-034 另补小字段（§12）。
15. **验收**：场景清单驱动（§11）。

## 1. 定位与入口

### 1.1 两入口 + PERSONAL 预留（同一视图组件，参数区分主体类型）

| 入口 | 挂载位置 | 主体类型 | 左栏主体数据源 | 编辑能力 |
|---|---|---|---|---|
| 角色 | 角色管理页（2.2）"权限授予"入口 | `ROLE`（BASIC_ROLE + GROUP_ROLE） | `abstract-role/tree`（权限中心） | GROUP_ROLE 只读（展开为基础角色后按基础角色编辑），BASIC_ROLE 可编辑 |
| 组织 | 组织管理页（2.1）入口 | `ORG`（ORG + POSITION） | 组织树（admin-service，见 `org-user-permission-contract.md`） | 可编辑（**二期，首期不做**，第十四轮收窄） |
| 个人 | 用户管理/详情页（2.1）入口 | `PERSONAL` | 用户列表（admin-service） | **首期移除**（见下注） |

- 路由约定：`/perm/grant?subjectType=ROLE|ORG`（前端同一页面组件，`subjectType` 驱动主体数据源与标题；`PERSONAL` 预留，首期不挂路由）。
- 主体切换：左栏树选中即切换查看目标；**未保存变更在切换主体/离开时拦截**（§6.4）。
- **GROUP_ROLE 展开**（P1-2）：树中 GROUP_ROLE 节点展开为虚拟子节点（`extra-roles/list` 返回的基础角色），选中子节点后主体 = 该基础角色（`BASIC_ROLE`），查看/授权/保存全部按基础角色走；GROUP_ROLE 节点本身无权限矩阵。
- 权限接线：各入口按 `subjectType` 映射能力门控（§10）。

### 1.2 与相邻页面分工

- 4.2 权限查询页：运行时**有效权限**查询（面向使用者）。本页：**配置视图**（面向管理员，含来源与草稿）。
- 3.1 资源+操作页：资源树/操作权限定义 CRUD。本页只读消费（资源树、inheritMask）。
- 3.2 权限条件页：条件 CRUD。本页弹窗只**选择已有条件**（内联新建条件引导到 3.2，保持弹窗轻量）。
- 2.1 用户详情页：个人主体入口（PERSONAL）。**首期移除**（P1-4）：后端用户同步只建 `abstract_user`、无个人 `abstract_role`（`AbstractUserSyncAppServiceImpl` L277），`PERSONAL_{external_id}` 角色无生命周期；待个人角色同步链路另立后端任务后再恢复本入口（§12 注）。

## 2. 布局结构

```
┌─ permission-grant-page ─────────────────────────────────────────────────┐
│ 头部：标题（角色/组织 权限授予，个人预留）+ 主体切换提示 + 操作列配置 + 继承开关×2 │
│ ┌─ 左栏主体树 ─┐ ┌─ 中栏矩阵（flex:1）────────────────────┐ ┌─ 右栏变更清单 ─┐ │
│ │ 树/列表      │ │ 工具栏：搜索资源 + 操作列配置 + 开关   │ │ （空态：暂无   │ │
│ │ （可切换）   │ │ 矩阵：资源行(树形) × 操作列(可配置)    │ │  变更）        │ │
│ │              │ │ 单元格：图标+角标+悬浮详情             │ │ 分组清单       │ │
│ │              │ └────────────────────────────────────────┘ │ 定位/撤销     │ │
│ └──────────────┘                                             └────────────────┘ │
│ 底部固定条：〔放弃全部〕〔保存全部 (N)〕                                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

- 中栏矩阵为页面主体；右栏变更清单仅在有未保存变更时展示（空态可折叠）。
- 矩阵滚动：资源行固定操作列区域滚动，`:deep(.el-table__body-wrapper) { max-height: calc(100vh - var(--table-offset)); }`。

### 2.1 主体上下文（GrantContext）

页面所有 list / apply-grant-plan 调用统一携带 **GrantContext**，禁止散落拼装：

```text
GrantContext = { domainCode, roleTypeCode, roleExternalId }
```

由左栏选中节点派生（P1-1，对齐 api-contract §5.5 角色定位三元组）：

| 入口 | 选中节点 | roleTypeCode | roleExternalId | domainCode |
|---|---|---|---|---|
| ROLE | BASIC_ROLE 角色 | `BASIC_ROLE` | 角色 `externalId`（角色树接口返回） | **恒 null**（P1-1） |
| ROLE | GROUP_ROLE 展开子节点 | `BASIC_ROLE` | 子节点（基础角色）`externalId` | 同上 |
| ORG | 组织节点（orgType=ORG） | `ORG` | `String(节点 id)`（即 `sys_org.id`，对齐 api-contract L656 示例 `roleExternalId:"2001"`） | **恒 null**（P1-1） |
| ORG | 岗位节点（orgType=POSITION） | `POSITION` | `String(节点 id)` | 同上 |

> **domainCode 统一 null（P1-1，2026-08-01 第五轮 review 修正）**：`abstract_role` 无域列（`biz_domain_id` 已移除），`resolveRoleId`/`resolveResourceId` 对 domainCode 仅做域存在性校验、**不按域过滤**（`TypeResolutionServiceImpl` L192-198/244-250，注释明言不再按 BIZ_DOMAIN_ID 过滤）；角色按 `roleTypeCode + roleExternalId` 唯一解析（`uk_abstract_role_external`）。授权页全部请求恒传 null 即可获得全部能力（角色树/资源树 domainCode 为空=返回全部）。原"ORG/POSITION 必填，对齐 api-contract L106"为误用——L106 仅约束 assign/revoke 的用户-角色分配链路，与授权页无关，已撤销。

- 资源键同步升级（P1-1）：分组键/请求中的资源维度统一为 `resourceTypeCode + resourceCode + codeType`（`codeType` 取资源树节点数据，默认 `default`；`scopeMode=ALL` 时 `resourceCode/codeType` 为 null，`scopeMode` 显式入键）。
- 切换主体即切换 GrantContext；未保存变更拦截（§6.4）。

## 3. 查看模式（矩阵）

### 3.1 行：资源树形行

- 行 = 当前主体有权限（或已授权过）的资源类型下的资源树节点；层级缩进 + 展开/折叠（默认收起至第一层）。
- **ALL 虚拟行（P1-2）**：每种资源类型的分组顶部固定一行「全部资源（ALL）」，承载该类型的 `scopeMode=ALL` 记录（`resourceCode/codeType` 为空，api-contract §6.3）；与实例行互斥展示（同单元格不叠加）；行内单元格角标 A。
- 搜索：keyword 过滤资源（命中节点及其祖先链展开显示）。
- 空态：主体无任何权限时 `el-empty`「该主体暂无权限配置」。

### 3.2 列：操作列可配置

- 操作列 = 各资源类型下的操作权限（`operation-permission/list`），**含全局操作合并（P1-4 第九轮）**：`resourceTypeCode=null` 的全局操作按"**专属优先、全局回退**"合并——同 `operationCode` 存在专属定义（resourceTypeCode 匹配）时用专属，否则用全局；操作列按合并后定义去重展示；单元格覆盖判定（§3.5 coveredSet）用合并后的 `binaryBit/inheritMask`，来源链对全局操作标注"全局操作"。
- 默认展示：常用操作列（前端按 `inheritMask=0` 且使用频次排序，或全部显示，实现时定）；用户可**增删列**（配置面板勾选操作权限），配置保存在前端本地（localStorage，按 subjectType 隔离）。
- 操作继承开启时，被继承覆盖的操作列仍可显示（列上角标提示"通常由高级操作继承，可隐藏"）。

### 3.3 单元格（查看态）

| 状态 | 表现 |
|---|---|
| 无权限 | 空白 |
| 有权限（直接） | **实色/深色**标记 + 来源图标（直接=实心圆点/无角标） |
| 有权限（资源继承） | **淡色**标记 + 来源图标 = 继承自父资源（如 ⤴ 或 树形角标，hover 显示"继承自 <父资源名>"） |
| 有权限（操作继承） | **淡色**标记 + 来源图标 = 继承自操作（如 ⇢ 或 层级角标，hover 显示"继承自 <操作名>"） |
| 带条件 | 角标 C（hover 显示条件名/规则摘要） |
| 可授予 canGrant | 角标 G（hover 显示"可再授予"） |
| 范围 ALL | 角标 A（全量），INSTANCE 无角标 |
| 多分支并存 | 角标 ⧉ + 分支数（hover/点击进详情层看各分支） |
| AUTO_DEP（依赖自动补全） | 来源图标 = 自动补全（虚线标记，hover 显示"由资源依赖自动补全"）；**只读**（P1-5），不可编辑/删除 |

> 颜色规则（P1-6）：**直接授权 = 实色/深色，继承（资源/操作）= 淡色**，一眼区分；来源类型（直接/资源继承/操作继承/自动补全）用图标区分，悬浮详情展示完整来源链。
>
> **AUTO_DEP 只读（P1-5）**：AUTO_DEP 与 MANUAL **可并存**，引擎 OR 语义下各自真实生效（同一单元格可能同时存在两条记录）；UI **并列展示**（AUTO_DEP 标注自动补全来源、只读不可编辑删除，悬浮详情同时列出两条及各自条件），**不设来源覆盖/压制语义**。自动授权链路（T-PERM-035）未来若需压制语义，另行立项引擎改造。

- 悬浮详情（el-popover）：来源链 + 条件列表（多分支）+ 范围 + canGrant + dependOn 子权限数量 + 创建时间。
- 点击单元格 → 进入详情层（§5）或授权弹窗（有权限=详情，无权限=弹窗，实现时定；推荐：无权限点击=授权弹窗，有权限点击=详情层）。

### 3.4 继承开关

- 两个独立开关：**树级继承** / **操作继承**，默认均开。
- 关闭树级继承：矩阵只显示各资源节点上的直接授权（子孙行不再显示来自祖先的权限）。
- 关闭操作继承：只显示显式授予的操作列（被 `inheritMask` 覆盖的列不显示）。
- 开关为**查看态过滤**，不影响草稿与实际授权数据。
- 页头标注：**"含继承视图（模拟 CHILD 展开，非运行时默认）"**——引擎运行时默认不继承（`PermQuery.inheritParents/inheritChildren=false`，仅显式设置才展开），"只看直接授权"开关可一键切到与运行时一致的视图（P1-6）。

### 3.5 来源链计算（前端纯函数，对齐引擎语义）

输入：

- `list` 直接授权记录（`includeChildren=false`，仅主权限，T-PERM-034 补，§12 缺口 3）
- 资源树（`resource-entity/tree`，含 parentId 结构）
- 操作定义全量（`operation-permission/list`，含 binaryBit + inheritMask + resourceTypeCode；**binaryBit/inheritMask 为十进制字符串线格式**，T-PERM-028 修订，前端 BigInt 解析，P1-3）

算法（对齐 `PermQueryEngine` / `OperationPermissionUtils`）：

```
1. 直接：list 记录本身（grantSource=MANUAL/AUTO_DEP，T-PERM-034 暴露后区分）
2. **组合闭包（一次完成）**：对每条直接记录（资源 r, 操作 A），展示集合 =
   节点集 descendants(r) ∪ {r} × 操作集 coveredOperations(A)
   —— 资源继承（对齐 PermQueryEngine.java:765-850 collectDescendants）与操作继承
   （对齐 OperationPermissionUtils.covers: (effectiveBits(A) & B.binaryBit) != 0）
   以笛卡尔积一次展开，等价于"先资源展开、再对每个展开结果做操作覆盖"的迭代闭包。
   例：父资源 P 上 MANAGE → P 及其子孙节点 × {MANAGE, VIEW, ...}（被 MANAGE 的 inheritMask
   覆盖的操作），子资源上同时出现 MANAGE（资源继承）与 VIEW（资源继承 + 操作继承）
3. 来源链**两段式组合标注**：节点段（r'≠r 时"资源继承自 <r>"）+ 操作段（B≠A 时"操作继承自 <A>"）；
   两段叠加显示"继承自父资源 <r> 的 <A>（操作覆盖）"；直接记录两段均为空
4. 合并：单元格有效状态 = 全部直接记录闭包结果的并集；AUTO_DEP 与 MANUAL **并列展示**（各自真实生效，§3.3）
5. **ALL 虚拟行**：`scopeMode=ALL` 记录（含 AUTO_DEP 只读）聚合到对应资源类型的「全部资源（ALL）」虚拟行；**同样执行操作继承展开**（节点集 = 该类型的单元素虚拟节点，操作集 = `coveredOperations(A)`，来源标注操作段照常、节点段为空），展开结果仍聚合到 ALL 虚拟行（P1-2）——ALL/MANAGE 记录同时命中 ALL/VIEW 列并标注操作继承
```

- 本计算为**展示口径**，不代表运行时判定（运行时以 PermQueryEngine 为准）。
- **位运算全部走 BigInt（P1-3）**：`binaryBit`/`inheritMask`/`grantedBits` 均为十进制字符串（T-PERM-028 线格式修订），覆盖判定 `(BigInt(effectiveBits) & BigInt(target.binaryBit)) !== 0n`；禁止 number 运算（63 位 bigint 超 2^53 丢精度，事后转换无法恢复）。
- **组合位按位拆解（P1-4）**：`operationCode=null` 的记录按 `grantedBits` 与各操作列 `binaryBit` 逐位比对，命中多列则多列同时点亮（来源标注"组合位"）；无法匹配任何定义位的余位归入详情层"未定义位"展示。

## 4. 授权弹窗（以操作权限为维度）

触发：查看矩阵中无权限单元格点击"授权"，或工具栏"授权"按钮（先选操作）。

```
┌─ 授权弹窗（GrantDialog，宽 ~720px）─────────────────────────────────────┐
│ Step 1 选操作权限：操作下拉/选择器（按资源类型分组）                       │
│ Step 2 现状：该操作在当前主体下的资源授予情况（只读列表：资源 | 范围 | 条件│
│         | canGrant | 来源），辅助决策                                      │
│ Step 3 范围 + 资源：范围模式（INSTANCE 多选树选资源实例，el-tree show-checkbox，一次选多资源配同一操作 / ALL 全量）      │
│ Step 4 条件 + canGrant：条件单选（已有条件，CONDITION:VIEW）/ 无；        │
│         canGrant 开关（可选）                                              │
│ ──────────────────────────────────────────────────────────────────────── │
│ 底部：〔取消〕〔确定〕                                                      │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Step 1 选操作后** Step 2 立即加载该操作在此主体下的全部授权（**覆盖位集展开（P1-2，第七轮修正）**：对每条记录先求覆盖位集 `coveredSet = ⋃_{bit ∈ 定义位(grantedBits)} (bit.binaryBit | bit.inheritMask)`（§3.5 组合闭包操作集的落地实现，含 `operationCode=null` 组合位同一规则求并集），判定 `(BigInt(coveredSet) & BigInt(当前操作 binaryBit)) !== 0n`——**不是裸 `grantedBits & binaryBit`**（grantedBits 是直接授予位，MANAGE 覆盖 VIEW 来自 inheritMask，裸比较会误报未授权）；组合位记录标注"组合位（含当前操作）"；**不做 operationCode 等值比较**——等值会漏掉组合位记录，误报未授权导致冗余分支）。
- Step 3 默认值：无权限时 INSTANCE + 空选择；有权限时预填当前记录的范围。
- **确定语义**（三键模型，P1-5）：
  - **分组键** = (resourceTypeCode, resourceCode, codeType, **operationKey**, scopeMode)（资源维度来自 GrantContext 资源键 §2.1，ALL 时 resourceCode/codeType=null）；**operationKey = operationCode ?? "bits:"+grantedBits**（P1-4：组合位记录以位串为键，同资源同条件下多条不同 granted_bits 记录不再折叠）；**分支键** = 分组键 + 条件；**持久化 id** = 后端记录 id。
  - **多选确定（第十四轮，2026-08-02）**：Step3 INSTANCE 多选 N 个资源 -> 确定后产生 N 条 `creates`（同一操作 + 同一条件 + 同一 canGrant + 同一 scopeMode，N 个资源键）；矩阵 N 个单元格变绿；变更清单按"操作+条件+canGrant+scopeMode"分组聚合展示（如"VIEW · 无条件 · 3 个资源"），点开看明细，避免 N 行刷屏。ALL 范围下不选资源（全量），多选仅对 INSTANCE 有意义。
  - 弹窗结果 (操作, 资源集合, 范围, 条件) 与现有记录比对：
    - 分组键不存在 → 草稿 **add**（新分组）
    - 分组键存在且条件相同 → 草稿 **update**（改 canGrant 等，按 id）
    - 分组键存在但条件不同 → 草稿 **add** 新分支（同键多条件并存，uk 含 `condition_id`）
  - **范围/资源/操作变化 = 跨键替换（第十二轮收敛）**：替换 = **removes 旧 + creates 新**（同一 `apply-grant-plan` 请求内原子执行）；**子权限不迁移**——随旧主权限级联删除（预期行为，产品语义：A 部门与 B 部门不相关），新主权限的子权限在 creates 中显式配置（`children` 一次性建树或后续挂载）；变更清单提示"子权限随主权限一并移除"；仅 canGrant/conditionCode 变更走 `updates`（不重建）
  - **匹配范围仅限 MANUAL（P1-5）**：分支键比对/草稿 diff 只匹配 `grantSource=MANUAL` 记录；AUTO_DEP 记录不进比对（只读，见 §3.3），同键并存不冲突
- 确定后弹窗关闭，矩阵单元格立即显示变更态（§6.1），进入右栏清单。

## 5. 详情层（多分支 / 子权限 / 删除）

触发：点击有权限的单元格 → 详情面板（右侧抽屉或弹窗）。

```
┌─ 权限详情（PermissionDetail）───────────────────────────────────────────┐
│ 主权限：资源名 · 操作 · 范围 · canGrant（只读摘要）                      │
│ ┌─ Tab1 条件分支 ─────────────────────────────────────────────────────┐ │
│ │  分支列表：无条件分支 / 条件A分支 / ...（每行：条件名 + 规则摘要）    │ │
│ │  [添加分支]（CONDITION:VIEW，选已有条件；= 同键 add 新记录）          │ │
│ │  每行：编辑（改条件）/ 删除（= remove，至少保留一条时提示）           │ │
│ │  （改条件 = 重新绑定 conditionCode：选已有条件覆盖，不进入规则编辑）  │ │
│ ├─ Tab2 子权限（depend_on）───────────────────────────────────────────┤ │
│ │  子权限列表：资源 · 操作 · 范围 · 条件（add-child 产生的记录）        │ │
│ │  [添加子权限]：选资源类型（SUB_PERM 允许集）→ 资源树选实例/ALL →     │ │
│ │   操作（子权限操作集）→ 条件 → 确定（add-child）                      │ │
│ │  每行：编辑（updates 改条件/canGrant；契约见 api-contract §6.5.1）/ 删除（removes 子权限 id）；跨键变更 = 移除+新建（removes+creates） │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

- **草稿虚拟挂载**（P1-4a，第十三轮 P1-7 修正）：详情层允许对草稿中的新增主权限挂子权限（虚拟父 id 占位）；统一提交时一次 `apply-grant-plan` 表达全部变更——**新父子树唯一通道 = `creates` 主权限带 `children` 嵌套一次性建树**（子权限写在父权限的 children 数组内，服务端生成父 id 后回填 depend_on，无需客户端预知 id）；`parentPermissionId` **仅引用提交前已存在的父记录**（协议无 clientTempId/parentTempId，不存在"同请求内后端生成"的引用方式）；不再需要"先保存主权限再逐个挂子权限"的两步流程。
- 多分支/子权限的变更同样进入草稿（右栏清单），统一提交。

## 6. 变更管理（草稿 → 统一提交）

### 6.1 草稿模型

- `baseline`（进入时的 list 数据，`includeChildren=false`） + `draft`（变更后集合），三键 diff（分组键/分支键/持久化 id，§4 确定语义；对齐 v1 §16 草稿模型）。
- 变更类型：`add` / `update` / `remove`（对齐 apply-grant-plan 记录级 creates/updates/removes）。

### 6.2 矩阵 diff 标记

| 变更 | 表现 |
|---|---|
| add | 单元格绿色高亮 + 「＋」 |
| update | 单元格黄色角标（内容变化如条件/范围/canGrant） |
| remove | **仅当移除的是该格最后一条有效来源** → 整格删除线 + 淡出（「−」）；非最后一条（仍有其他 MANUAL 分支或 AUTO_DEP 生效）→ 保留有效底色 + **部分移除角标**（⧄，悬浮"移除 1/N 分支"） |

### 6.3 右栏变更清单

- 分组：按变更类型（新增 N / 修改 M / 删除 K）。
- 每行：资源 · 操作 · 变更摘要（如"条件：无 → 工作日"）。
- 操作：**定位**（点击滚动矩阵到对应单元格并闪烁）/ **撤销**（单条回滚为 baseline）。
- 底部：〔放弃全部〕〔保存全部 (N)〕。

### 6.4 提交（apply-grant-plan 单入口，第十二轮收敛）

- 保存全部 → **单请求 `role-resource-permission/apply-grant-plan`**（§6.5.1：记录级 plan = creates（主权限可带 children 一次性建树/子权限 parentPermissionId 挂父）+ updates（canGrant/conditionCode 微变更）+ removes（主权限级联删子/子权限单条删），单事务原子，任一失败整体回滚后重试；前端 saving 期间按钮 disabled 防重复点击，超时提示刷新确认）。
- **删除主权限** = plan.removes 一条主记录 id（后端级联删子权限），其下子权限草稿静默丢弃（不发子权限 remove）。
- **跨键替换（范围/资源/操作变化，第十二轮收敛，替代旧 rebuild）**：替换 = **removes 旧 + creates 新**（同一请求内原子执行）——单事务内级联删旧主权限（含全部子权限）+ 创建新主权限 + 创建全部子权限终态（事务内生成新父 id），整体成功或整体回滚，无权限并集窗口、无两阶段窗口；**子权限不迁移**（随旧主权限级联删除，产品语义：A 部门与 B 部门不相关），前端在替换前检查 `childCount>0` 提示"该权限下 N 条子权限将随主权限一并移除"；新主权限的子权限在 creates 中显式配置；canGrant/conditionCode 变更（不涉及资源/操作/范围）走 `updates`。
- **子权限变更（第十二轮收敛）**：新增 = creates（parentPermissionId 挂父，可随主权限 children 一次性建树）；编辑 = updates（改 canGrant/conditionCode）；删除 = removes（子权限 id）；跨键变更 = 移除+新建（removes+creates）——全部在同一 apply-grant-plan 请求内原子执行。
- **失败处理（请求粒度，第十四轮收窄）**：单次 `apply-grant-plan` 请求 = 一个**不可分割的项**--请求成功 -> **全部条目**移出清单并入 baseline；请求失败 -> **全部条目**保留标红、整体重试（后端单事务原子无部分成功；前端 saving 期间按钮 disabled 防重复提交，超时提示刷新确认）。

### 6.5 提交状态机（工程加固简化，2026-08-02 第十四轮收窄）

- **Pinia store `grant-store.ts`**（T-FE-036 内新建，不复用现有全局 store）：提交状态用 **discriminated union** 表达，**四态**（第十四轮收窄）：`idle` / `dirty` / `saving` / `saveFailed`——砍八态的 loading/ready/outcomeUnknown/stale + 代际号 + list 对比恢复 + 重放；内部管理页低频，超时由“提示刷新确认”覆盖，不做自动恢复 machinery。
- **页面 capability 与状态正交（第十一轮 P2-7 修正）**：顶层 `capability: 'edit' | 'view'` **仅由门禁派生**（ROLE:VIEW -> view；ROLE:MANAGE -> edit）+ GROUP_ROLE 主体 -> view；**不再由 AUTO_DEP 派生**。AUTO_DEP 降为**记录级** `readonlyReason: 'AUTO_DEP' | null`：单元格禁用 + 角标 + 悬浮提示，与页面状态机互不干扰（同一页 MANUAL 可编辑 + AUTO_DEP 单元格禁用共存）。
- **baseline 迁移规则**：`baseline` 只在 `apply-grant-plan` **明确成功**后切换（响应返回新权限结果）；失败 -> 条目保留标红，提示“保存失败，请重试”（整体重试，前端 saving 期间按钮 disabled 防重复提交）；**超时/网络未知** -> 提示“网络异常，请刷新页面确认当前状态”，管理员刷新 list 自行判断（不做自动 list 对比 + 重放）。
- **readonly 派生**：页面 capability 由门禁（ROLE:VIEW/MANAGE）与 GROUP_ROLE 主体派生；记录级 readonlyReason 由 AUTO_DEP 派生，两者正交。
- 状态机为 T-FE-036 实现要点（DoD：S5 相关场景必须走状态机路径验证）。
- **baseline 迁移（请求粒度）**：请求成功后才整体移入新 baseline（草稿只保留未成功请求的条目）；再次进入页面以最新 baseline 为准。
- 离开保护：存在未保存变更时，路由切换/刷新/切换主体 -> 确认提示（ElMessageBox）。
- 保存成功 -> 清空草稿，`baseline = draft`，右栏收起。

## 7. 字段定义

### 7.1 对齐后端 `RolePermissionItemResp`（`PermissionGrantAppServiceImpl.toItemRespList` L782）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键（update/remove 按 id） |
| resourceTypeCode | string | 资源类型编码 |
| resourceCode | string\|null | 资源业务编码（ALL 范围时为 null） |
| codeType | string\|null | 资源编码类型 |
| resourceName | string\|null | 资源名称 |
| operationCode | string\|null | 操作权限编码（组合位无对应定义时为 null 🔧） |
| canGrant | boolean | 是否可再授予 |
| conditionCode | string\|null | 条件编码 |
| scopeMode | string | INSTANCE \| ALL（`ScopeModeSupport.fromScopeAll`） |
| dependOn | number\|null | 父主权限 id（子权限记录非 null） |

### 7.2 前端扩展（T-PERM-034 缺口，见 §12）

| 字段 | 类型 | 说明 | 状态 |
|---|---|---|---|
| grantSource | string | MANUAL \| AUTO_DEP（来源标注：手动/依赖自动补全） | 🔧 后端补 |
| grantedBits | string | 授予位掩码，**十进制字符串**（如 `"9223372036854775807"`，前端 BigInt 解析；对齐 api-contract §6.4 线格式；记录必有值，operationCode=null 兜底与操作继承展开） | 🔧 后端补 |
| createdAt | string | 创建时间（悬浮详情展示） | 🔧 后端补 |
| childCount | number | 子权限数量（list 时按 depend_on 分组 COUNT；悬浮详情展示） | 🔧 后端补 |
| list `includeChildren` 参数 | boolean | list 仅返回主权限（dependOn==null），子权限不进来源链（现 `selectValidByRoleId` 未过滤 depend_on） | 🔧 后端补 |
| 子权限编辑/删除 | — | 无独立接口（第十二轮收敛）：updates 改 conditionCode/canGrant / removes 删子权限 / creates 挂父，全部并入 apply-grant-plan | 🔧 后端补 |

### 7.3 前端派生字段（自算，§3.5）

- `effectiveSources[]`：单元格有效状态来源链（DIRECT / RESOURCE_INHERIT / OPERATION_INHERIT + 来源节点/操作名）
- `branchCount`：多分支数（同键不同 conditionCode 的记录数）
- `dependOnCount`：子权限数

## 8. API 依赖

| 用途 | 接口 | 契约 |
|---|---|---|
| 主体树（角色入口） | `abstract-role/tree` | api-contract.md（§6.10.3） |
| 主体树（组织入口） | admin-service `org-tree`（`includePositions=true`，岗位为组织子节点，T-ADMIN-021） | admin-service，`org-user-permission-contract.md` |
| 统一提交（全部写操作） | `role-resource-permission/apply-grant-plan`（**唯一写入口**，§6.5.1；creates/updates/removes 记录级） | api-contract.md §6.5.1 |
| 直接授权列表 | `role-resource-permission/list`（`includeChildren=false`） | api-contract.md §6.4（Resp 见 §7） |
| 子权限（并入统一提交） | 无独立接口（第十二轮收敛：creates parentPermissionId / updates / removes） | api-contract.md §6.5/§6.5.1 |
| 资源树 | `resource-entity/tree` | api-contract.md §5.x（3.1 页契约） |
| 操作权限（含 inheritMask） | `operation-permission/list` | api-contract.md §5.x（3.1 页契约） |
| GROUP_ROLE 展开 | `abstract-role/extra-roles/list` | api-contract.md §6.10.3（已联调 ✅） |
| 条件列表 | `permission-condition/list` | api-contract.md §5.6（T-PERM-029） |

## 9. 组件结构（含复用）

| 组件 | 来源 | 说明 |
|---|---|---|
| 资源树选择器 | T-FE-001 组件池（3.1/4.2 复用） | 弹窗 Step 3 范围选择 |
| 条件选择器 | 本页私有（T-FE-036 内自建） | 条件单选（弹窗 Step 4 / 详情层改条件）：搜索 + 摘要 + 启用状态过滤，数据源 `permission-condition/list`；不复用 ReConditionEditor（本页只选已有条件），ReConditionPicker 已随 v1/v2 删除 |
| 角色选择器/树 | T-FE-001 池（2.2 复用） | 左栏主体树（角色入口） |
| 组织主体适配器 | 本页自建（P1-2，T-FE-036 内）+ 依赖 **T-ADMIN-021** | 左栏主体树（组织入口）：**调用 admin-service 扩展 org-tree 接口（includePositions=true，岗位作为所属组织子节点返回，不分页；T-ADMIN-021 实现；请求/响应/兼容行为/裁剪见 admin-service-api-contract.md §4.2.1，P1-6）**；**前端 OrgQuery 类型 mock 期自建、联调期对齐契约**（T-FE-036 mock 先行）；不复用 ReOrgTreePanel（其 loadTree 固定 orgType=1 且岗位需分页拉取，混合需扩展共享组件影响 user 页） |
| 来源图标集 | 本页新增 | 直接 / 资源继承 / 操作继承 / 自动补全 |
| 矩阵（资源行×操作列可配置） | 本页新增 | 查看 + diff 叠加 |
| 右栏变更清单 | 本页新增 | 定位/撤销/保存（对齐 v1 §16 交互但作为独立组件） |
| 授权弹窗（GrantDialog） | 本页新增 | §4 |

## 10. 权限接线

| 能力 | 门控 | 降级 |
|---|---|---|
| 左栏数据源（组织树/用户列表） | ADMIN_ORG:VIEW / ADMIN_USER:VIEW（沿用 2.1 页数据源门禁，perms.ts 现有权限码） | 左栏不可见/占位 |
| 矩阵查看（两入口） | ROLE:VIEW（目标抽象角色） | 无权占位 |
| 授权/删除/详情层（两入口） | ROLE:MANAGE（目标抽象角色） | 按钮禁用 + tooltip |
| 条件选择 | CONDITION:VIEW | 弹窗 Step 4 条件置灰；引导至 3.2 |
| 资源树 | RESOURCE:VIEW | 矩阵资源行不可见 |
| 操作列 | OPERATION:VIEW | 操作列不可见 |
| 详情层子权限 | ROLE:MANAGE（+SUB_PERM 配置允许集由后端校验） | 按钮禁用 |

> 双层门禁说明（P1-3）：左栏**数据源**可见性沿用入口页既有门禁（`ADMIN_ORG:VIEW` / `ADMIN_USER:VIEW` / 岗位 `ADMIN_ORG:VIEW_POSITION`，admin-service 数据，对齐 frontend `user/utils/perms.ts`）；**矩阵查看/授权动作**统一用对目标抽象角色的 ROLE:VIEW / ROLE:MANAGE（后端 `role-resource-permission/*` 均校验目标抽象角色，`PermissionGrantAppServiceImpl` L152/482/520/587/748）——组织/个人被抽象成角色正是为了"像角色一样被配权"（role-manage.md §1 依据）。
> 个人入口业务键（P1-3，**首期移除**）：左栏用户列表（admin-service）→ 选中用户 → 业务键 `PERSONAL_{external_id}`（`external_id` = 用户同步到 permission-center 时的 `sys_user.id`，即用户列表返回的 id；对齐 role-manage.md:24）——待个人 `abstract_role` 同步链路建成后恢复（§12 注）。

## 11. 验收场景清单（T-FE-036 拆分子任务依据）

- **S1 两入口**：角色/组织入口分别进入，左栏主体树正确；GROUP_ROLE 只读（无授权按钮，来源标注"来自基础角色"）。（个人入口首期移除，P1-4）
- **S2 查看矩阵**：继承默认开；来源图标正确区分 直接/资源继承/操作继承/自动补全；两开关可独立关闭；操作列可配置（增删列，刷新后保留）；树形行展开/折叠/搜索；**ALL 虚拟行**（含角标 A、与实例行互斥）；单元格角标（C/G/A/⧉）与悬浮详情正确（含 createdAt/childCount）；多分支并存正确展示；**AUTO_DEP 只读**（不可编辑/删除）。
- **S3 授权弹窗**：选操作 → Step 2 现状列表正确（含继承标注）→ 范围（树选实例/**ALL**）+ 条件 + canGrant → 确定后矩阵实时 diff（含 ALL 虚拟行 diff）；同键不同条件产生新分支；**确定语义只匹配 MANUAL**（AUTO_DEP 不进比对）。
- **S4 详情层**：多分支添加/删除；子权限添加/删除/编辑（creates parentPermissionId / updates（改 canGrant / 改条件 / 清除条件传空串）/ removes）；**跨键变更 = 移除+新建（removes+creates，同事务原子）**；**改条件撞已占用条件（完整键同键同 conditionCode 已有 MANUAL 分支）→ 前端禁用 + 后端 20033 提示不崩溃**；草稿中的新增主权限挂子权限 = creates 主权限带 children 一次性建树。
- **S5 变更提交**：矩阵标记 + 右栏清单（定位/逐条撤销）正确；**部分移除与整格移除标记区分（非最后一条有效来源 → ⧄ 角标，最后一条 → 整格删除线）**；保存全部 = **单请求 apply-grant-plan**（记录级 creates/updates/removes，单事务原子 + 受影响行数断言；跨键替换 = removes+creates 原子且变更清单提示子权限随主权限移除）；失败整批回滚后重试（前端 saving 期间按钮 disabled 防重复提交）；放弃全部回滚；部分失败保留标红可重试；未保存离开拦截。
- **S6 数据正确性**：来源链前端计算与引擎语义抽查一致（父资源授权 → 子孙行 INHERITED；MANAGE 授权 → VIEW 列 INHERITED；**父资源 MANAGE → 子资源 VIEW 单元格出现且来源链含两段（资源继承 + 操作继承）**；**ALL/MANAGE → ALL/VIEW 列出现且标注操作继承（节点段为空）**；AUTO_DEP 标注且只读；ALL 记录落虚拟行）。
- **S7 边界**：无权限主体空态；operationCode=null 记录不崩溃（展示兜底）；list 数据量大时矩阵渲染不卡顿（虚拟滚动，如资源节点 > 500）。

## 12. 与后端 T-PERM-034 的关系（范围更新）

前端自算来源链（§3.5），T-PERM-034 补七项（2026-08-02 第十二轮单入口收敛：授权写链路收敛为 list + apply-grant-plan，旧 save/revoke/children/add-child/update-child/remove-child/children-save/rebuild 全部移除/不实现；**依赖解耦**：T-FE-036 以 mock 数据驱动开发，接口形状按本设计文档，不阻塞 T-PERM-034；联调任务 T-FE-018 同时依赖二者）：

1. **`RolePermissionItemResp` 暴露 `grantSource`**（MANUAL / AUTO_DEP）：来源标注与 AUTO_DEP 只读需要（当前实体有、Resp 未暴露，`PermissionGrantAppServiceImpl.toItemRespList` L782）。
2. **`RolePermissionItemResp` 暴露 `grantedBits`**：`operationCode=null`（组合位无对应操作定义）时前端按位拆解展示与操作继承展开（当前 Resp 无此字段）。
3. **`role-resource-permission/list` 增加 `includeChildren` 参数**（默认 true 兼容）：主权限视图只取 dependOn==null 记录，子权限不进来源链（当前 `selectValidByRoleId` 未过滤 depend_on）。
4. ~~新增 update-child~~（第十二轮移除）：子权限编辑并入 apply-grant-plan.updates（改 canGrant/conditionCode，三态协议见 api-contract §6.5.1）。
5. **`RolePermissionItemResp` 增补 `createdAt` / `childCount`**：悬浮详情展示创建时间与子权限数量（list 时按 depend_on 分组 COUNT 一次返回，免逐项懒加载）。
6. ~~新增 children-save~~（第十二轮移除）：子权限增删改并入 apply-grant-plan（creates parentPermissionId / updates / removes，同一事务原子）。
7. **条件冲突校验完整键 + 新错误码 20033**：`CONDITION_BRANCH_CONFLICT`（20015=USER_NOT_FOUND 已占用，枚举最大 20032）；前置校验按完整持久化键 `(resource_entity_id, resource_type, granted_bits, depend_on, scope_all)` + 目标 `condition_id`，grant_source 不参与；creates/updates 共用同一套规则 + uk 异常转换（`PermissionErrorCode.java` 同步新增枚举）。
8. **plan 入口接入授权传递校验**（P1-1，第六轮新增）：creates 逐项走 `checkCanGrant`（匹配键含 condition 维度）、updates 设 canGrant=true 或 conditionCode 有变更走 `canGrantPermission`（对齐原 save.add L176-200 / save.update L247-279），不满足 → **20040** `GRANT_CANNOT_DELEGATE`；杜绝凭 MANAGE 绕过委托边界挂子权限。
9. **AUTO_DEP 服务端只读门禁 + 新错误码 20034**（P1-2，第六轮新增）：plan 的 creates（父权限引用）/updates/removes 全部拒绝 `grantSource=AUTO_DEP` 记录 → **20034** `AUTO_DEP_READONLY`；自动补全记录只读由服务端强制，前端只读仅为体验层。
10. **plan 全集预校验 + 新错误码 20036**（P1-3，第九轮新增）：updates/removes 的 id 必须存在且属于目标角色（否则 **20036** `PERMISSION_NOT_FOUND`——现状实现静默跳过不存在 id）；AUTO_DEP 门禁前置化（20034）；updates/removes 集合互斥（参数校验）；预校验与条件冲突查重合并为同一预检阶段（`prevalidateGrantPlan`），任一不满足整批失败。
11. ~~新增 rebuild~~（第十二轮移除）：跨键替换 = apply-grant-plan 内 removes 旧 + creates 新（同事务原子，子权限不迁移）；原子性由单事务保证（无 clientRequestId/幂等表，第十四轮砍）。
12. **checkCanGrant 匹配键扩展 condition 维度**（security review MEDIUM，第九轮新增）：`GrantCheckKey` 现不含 conditionCode——受限条件+canGrant 的操作者可经 add 路径写入无条件/更宽条件记录，等效绕过 update 段封堵；目标条件必须被操作者自身拥有的条件覆盖（契约见 api-contract §6.5 授权传递校验 L1300，含首期判定规则）。
13. **唯一写入口 `apply-grant-plan`**（第十二轮收敛定稿，第十四轮收窄）：记录级 plan = creates（主权限可带 children 一次性建树/子权限 parentPermissionId 挂父）+ updates（现有记录微变更）+ removes（主权限级联删子/子权限单条删），单事务原子执行（任一失败整体回滚）；**砍** clientRequestId/@Idempotent（T-PERM-040 删除）+ `expectedRevision` CAS + `grant_revision` 列 + 幂等表 `grant_plan_idempotency` + 20037/20039（内部管理页低频，并发/超时罕见，刷新确认即可）；返回完整持久化结果（契约见 api-contract §6.5.1）；**T-FE-036 全部写操作唯一入口**；八个旧写入口全部移除。

### 工程加固（2026-08-01 分析评审后，随 T-PERM-034/T-FE-036/T-FE-018 落地）

- **Mutation Policy（方案二）**：`PermissionGrantDomainService` 新增唯一预检入口 `prevalidateGrantPlan(plan)`——八项不变量（记录存在及角色/父归属、update/remove 互斥、AUTO_DEP 只读、canGrant 授权传递含 condition 维度、conditionCode 清空/替换扩大、SUB_PERM 约束、完整持久化键冲突、scopeMode/资源/操作兼容性）一次校验；**apply-grant-plan 唯一写入口强制调用**，AppService 禁止自行拼门禁；“命令类型（create/update/remove × 主/子权限）× 不变量”测试矩阵入 T-PERM-034 acceptance。
- **引擎双写消除（方案三）**：后端 `GoldenFixtureTest`（**6 用例精简**（第十四轮）：全局回退/组合位/ALL/资源继承/操作继承/两段组合来源）输出权威结果；前端读同一 fixtures 逐例比对（CI 失败）；配置读模型（后端视图聚合接口）记**演进方向**，本轮不实现。
- **端点契约（方案四，第十四轮定案）**：**删除** `docs/contracts/perm-grant.schema.json`（第十三轮已降级为说明性、不机器校验，维护冗余）；报文契约回归 `api-contract.md §6.4/§6.5/§6.5.1` 单一来源，补结构约束（统一响应壳/跨字段 INSTANCE-ALL 约束/local-date-time/grantedBits 十进制字符串/错误码枚举/plan 结构/无 clientRequestId）；结构校验由后端 `prevalidateGrantPlan` 运行时执行；Java DTO 手工对齐 api-contract。

> 注（P1-4）：个人入口（PERSONAL）首期移除；个人 `abstract_role` 生命周期（用户同步 upsert/删除 `PERSONAL_{external_id}`）另立后端任务，落地后恢复个人入口与 S1 个人分支验收。

旧写入口（save / add-child / children / remove-child / update-child / children-save / rebuild）已全部移除/不实现（第十二轮单入口收敛，api-contract §6.5.1）；本页只依赖 list + apply-grant-plan + extra-roles/list + 资源树/操作/条件等只读接口。

> 注：本页默认不支持"多操作位组合一次授权"的新增 UI（弹窗为单操作），`grantedBits` 仅为兼容展示；多操作位组合授权创建能力如未来需要，另行评估（对齐 T-PERM-034 范围外）。
