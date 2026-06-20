# AccessMesh 权限中心 v3 端到端设计（合并版）

> ⚠️ **OBSOLETED by v3.4**（2026-06-18，v3.4 已被 v3.5 取代）
> 本文档为 v3.0~v3.3 历史快照，已被 [v3.5 简化版](../../design/permission-center-v3.5-design.md) 取代（v3.4 仅通过 git history 追溯）。
> - **本文范式**：双轨 AND（菜单 ADMIN_MENU:VIEW + 业务码）+ sys_menu.operations/primary_operation 元数据化
> - **v3.4 范式**：单源派生（用户对业务资源有任何 is_entry 操作位 → 菜单可见）
> - **OBSOLETED 原因**：双系统反模式（admin 模块 sys_menu.operations 等字段实际承担权限语义，与 perm-center 形成影子双系统）
> - **R3 评估时间**：2026-06-18，3 角色（架构师/安全/PM）SIGN_OFF v3.4 方向 + NEEDS_REVISION 11 项 preconditions
> 本文仅作历史追溯参考，不再作为开发依据。当前权威文档见上方 v3.5 链接（v3.4 已被 v3.5 取代，仅通过 git history 追溯）。

---

> 评审日期：2026-06-17
> 产出方式：3 调研 + 3 派别设计 + 5 角色提案 + 3 对抗验证 + 9 角色对抗评审 + 综合（多智能体工作流，22 agents / 2.27M tokens）
> 关联评审：[design-review-2026-06-17.md](./design-review-2026-06-17.md)
> 状态：v3 合并版（2026-06-17）— 整合原 example-service-integration-design + menu-business-perm-alignment 两份文档；9 角色对抗 R1→R2 落盘；§10 多租户硬隔离补章

**合并说明**：本文件合并自 2026-06-17 同日产出的两份姊妹文档：
- `example-service-integration-design-2026-06-17.md`（v1，410 行，5 角色提案 + 3 对抗）→ 业务概念、信息流、gap 清单、推进路线已并入本文 §0.5 / §4.8 / §14 / §15
- `menu-business-perm-alignment-2026-06-17.md`（v2，945 行，3 派别设计 + 综合）→ 不可变规则、生命周期、一致性总线为本文主体（§1–§13）

合并后单文件作为权限中心 v3 GA 准入唯一设计基准。

---

## 🔄 v3 修订记录（2026-06-17）

> 9 角色独立对抗评审揭示 14 项 critical_issues（C1-C14）。本次修订落盘 6 大范式切换 + 9 处章节改动：
>
> 1. **C1 / 范式 1**：§1.2 `perm_code` 唯一索引去除，`entryPermCodes` JSONB 升为权威多值通道；菜单唯一性收敛到 `(tenant_id, path)`；`resource_entity(ADMIN_MENU).code = sys_menu.id`。
> 2. **C2 / 范式 2**：§6.3 字段级权限纳入 v2 GA 最小子集（多操作位 + DTO 白名单 + menuId 上下文复算 + 禁止 query string 列开关），停止推迟到 v3。
> 3. **C3 / 范式 3**：§3.1 依赖规则按 SSOT 分两类来源（`MANIFEST` 业务→业务、`MENU_PUBLISH_DERIVED` 业务→菜单），运行时菜单 AUTO_DEP 链路修复。
> 4. **C4 / C8**：§11 新增"资源/菜单/权限码全生命周期协议"章节（perm_code 三态状态机、菜单 CRUD/RENAME、resource_entity 同步、僵尸菜单巡检）。
> 5. **C5 / C7 / C11 / 范式 5**：§12 新增"跨服务一致性总线 + 缓存降级矩阵"章节（outbox + Kafka/Redis Stream、reasonCode 协议、HTTP 错误码映射、前端 TTL 上限 + 心跳兜底）。
> 6. **C6 / 范式 4**：§12.2 限时权限到期联动；前端 `permissions[]` 升级为对象数组（保持向后兼容）；流式接口中途切断协议。
> 7. **C12**：所有协议、索引、广播粒度强制带 `tenant_id`；resource-sync HTTP 强制 `X-Tenant-Id` 校验。
> 8. **C14**：§3.3 告警分级（HIGH/MEDIUM/INFO）+ 发布前 dry-run + 反向"谁能看此菜单"查询 API。
> 9. **§5 验收用例**：从 8 条扩展到 28 条，覆盖字段级 IDOR、限时到期、缓存一致性、码生命周期、跨租户隔离、auto-dep 巡检。
>
> 详见末尾 §13 paradigm_shifts 引用。

---

## 🔄 v3.1 修订记录（2026-06-18 — 1:1 报表-菜单约束）

> 用户拍板：一个业务报表（reportId）有且只能有一个对应菜单（sys_menu）。9 角色 R1 评审 9/9 一致投票。
>
> **范式回退/简化（5 处）**：
> 1. **P1 SIMPLIFY**：§1.2 恢复 (tenant_id, perm_code) 唯一索引；entryPermCodes 退化为强制单元素；新增反查 API。
> 2. **P2 SIMPLIFY**：§6.3 删除 IR-6.3-A/B/E（与 1:1 直接冲突或 IDOR 主路径已消失），保留并升级 IR-6.3-C/D 为 perm-sdk CI 契约硬约束，新增 IR-6.3-F 字段+行裁切铁律，重写 MenuVisibilityResolver 公式避免 HR 空白页。
> 3. **字段级权限 MOVE_TO_V2_1**：9/9 全票推迟到 v2.1；v2 GA 仅保留 IR-6.3-C/D 硬契约。
> 4. **§5 验收用例**：移除 3 条（AC-1.2-1 反转、Case 10、AC-6.3-3）；修改 4 条；新增 10 条（NEW-1:1-1~3 + NEW-S1-1~3 + NEW-S3-1~4）。
>
> **配套新增（v2 GA 必含）**：
> 1. **§1.3.5 sys_menu_ref 引用机制**：解决 S3 跨业务线复用风险（9 角色一致 HIGH/CRITICAL）。
> 2. **report_metadata 拆分元数据 + binlog 审计 + 月度 SQL 签名 hash 扫描**：防 S2 拆 reportId 反模式。
> 3. **IR-6.3-D 升级为 _suppressedFields 强响应契约**：跨接入方表现一致。
> 4. **IR-6.3-F 字段+行裁切由 OperationPermission 位驱动铁律**：禁止业务侧自推导 SCOPE_*。
> 5. **MenuVisibilityResolver 公式重写**：MENU(业务) := ADMIN_MENU:VIEW AND ∃ op∈{VIEW_*} 用户拥有 op。
> 6. 新增 reasonCode：DENIED_BY_DOMAIN / DEPRECATED_HEADER。
>
> **决策点重新拍板**：D6（字段级权限：v2 GA → MOVE_TO_V2_1）、D8（perm_code 多值通道：B → A，回退到单值 + 唯一索引）、D9-2（后端 path 反查 menuId：P0 → P1 但保留实现）、D9-4（P0-B 字段级权限项移出给 v2.1）、D9-5（field_descriptor 在 v2 GA 不强制，v2.1 落地 schema 校验）。

---

## 🔄 v3.2 修订记录（2026-06-18 — 菜单元数据化「业务能力清单」）

> 用户提出新方案：把"该报表/菜单暴露哪些操作位"从分散在前端代码的 `<Perms>` 标签集中回到菜单元数据。R1 评估方向锁定，4/5 决策按推荐采纳，D-3（manifest 新增能力是否自动加入菜单）留挂等下一轮 manifest 讨论。
>
> **范式升级（4 处）**：
> 1. **§1.2 perm_code 二段式**：从 v3.1 三段式（`{type}:{code}:{op}`，仅文档示例隐含）升级为显式二段式 `{resource_type}:{resource_code}`；新增 `resource_type` / `resource_code` 冗余列便于 query 与跨服务事件 payload 自包含。
> 2. **§1.2 operations + primary_operation 字段化**：sys_menu 新增 `operations` JSONB 数组 + `primary_operation` VARCHAR(64)；该菜单暴露的"业务能力清单"成为菜单元数据，不再仅靠前端代码 grep。
> 3. **§1.1 菜单可见性公式重写**：MENU(业务) := `ADMIN_MENU:{menuId}:VIEW ∧ hasPerm({type}:{code}:{primary_op})`；primary_op 必须 `sensitivity_level=PUBLIC`（保存校验，关闭 R2 P0-2 安全洞）。
> 4. **§3.1.2 saveMenu 派生 N 条 dependency**：每个 operations 元素生成一条 `MENU_PUBLISH_DERIVED` 行，授业务码自动 auto-grant 菜单可见。
>
> **配套新增**：
> 1. **§6.1 发布菜单 URL 协议升级**：参数从 `?perm_code=...` 改为 `?resource_type=&resource_code=&primary_operation=&operations=...`；admin 端 perm_code 自动拼接，业务方不能改。
> 2. **§3.1.5 授权面板 UX 重做**：选菜单后弹"操作位 + 套餐 + 数据范围 + dry-run"一站式面板，解决"授菜单同时授业务权"核心痛点。
> 3. **§11.2.4 operations 漂移检测**：每日 cron 扫 sys_menu.operations vs OperationPermission 表 diff，差异 MEDIUM 告警。
>
> **决策记录**：D-1 采纳 v3.2 / D-2 冗余存 resource_type+resource_code / D-4 primary_op 限 sensitivity=PUBLIC + 默认 VIEW / D-5 落盘。**D-3 留挂**（manifest 新增能力时是否自动加入已发菜单 operations，下一轮 manifest 讨论决策）。

---

## 🔄 v3.3 修订记录（2026-06-18 — 去 manifest 化 + 操作位字典中心化）

> 用户两个关键质疑触发范式重构：(1) 依赖关系不应放 manifest，**effective_bits 位运算已覆盖业务→业务依赖**；(2) 操作位即操作权限，没有"全局/租户级"分层。结果：**整个 manifest 概念物理消失**。
>
> **范式回退/重构（4 处）**：
> 1. **删除 manifest 文件 + 启动期推送机制**：业务服务对权限中心是**零启动耦合 + 仅运行时查询**。perm-manifest.yaml 不再存在。
> 2. **OperationPermission 表升级为 admin 后台维护字典**：type_definition / operation_permission 由 admin 中央治理组通过 UI 维护（持有 `OPERATION_DICT:MANAGE` 权限），业务服务**不参与**字典维护。
> 3. **依赖关系靠 effective_bits 位运算**：操作位之间的"包含/继承"关系由 `OperationPermissionUtils.covers(granted_bits, target_bits)` 位运算覆盖，**不再有 requires 字段、不再有 dependencies 配置块、不再有 MANIFEST 来源的 resource_dependency 行**。
> 4. **菜单更新发布无审核**（D-E）：Alice 自助保存 sys_menu.operations 变更，仅写 audit log；首次发布仍走 admin 二次审核流程。
>
> **配套调整**：
> 1. **§3.1.1 依赖规则来源**：删除 `MANIFEST` 来源，仅保留 `MENU_PUBLISH_DERIVED`（admin 在 saveMenu 事务内派生）+ `ADMIN_UI`（人工补登）。auto-grant 唯一职责：跨资源类型的菜单联动（业务码 → ADMIN_MENU:VIEW）。
> 2. **§3.1.2 saveMenu 协议**：保留派生 N 条 MENU_PUBLISH_DERIVED 行；增加 `?action=update` 更新发布分支（D-G 复用首次发布 URL 协议）。
> 3. **§17 操作位字典治理**（新增）：admin 后台 UI、命名规范、effective_bits 配置、敏感等级、MEDIUM 通知（敏感位变更时治理组可见，不阻断保存）。
> 4. **§18 资源类型骨架治理**（新增）：admin 后台 UI 维护 type_definition 表（替代原业务服务启动期 push）。
> 5. **gap 清单**：manifest 相关 gap 全部 [DROPPED-v3.3]；新增 admin 后台字典管理 UI 工作量。
>
> **决策记录**：D-A=a / D-B=按推荐 / **D-C 关闭**（位运算已覆盖，不需新增 requires 字段）/ D-D=不推任何东西 / D-E=不审核（含敏感位 trade-off，G1-G3 护栏可选） / **D-F 关闭**（操作位即操作权限，按 IR-10 租户隔离） / D-G=复用 URL 协议。

---

## 0. 与上一版（v2 合并前）的差异

| # | 关键决策 | 上一版（v1） | 本版（v2/v3 合并） |
|---|---|---|---|
| 1 | 菜单可见性公式 | 双轨并行（独立判定，未规定 AND/OR） | **节点类型分治 + 显式 AND**：DIR 派生、MENU 双轨 AND、纯展示页显式声明 |
| 2 | `perm_code` 物理格式 | 单值 VARCHAR(128) + 前端 meta 多值（无协议） | **单值字段 + `extra.entryPermCodes` JSONB 多值通道**，多值语义固定 ALL（无唯一索引）|
| 3 | auto-grant 默认行为 | 未明确 | **业务→菜单自动补 = 默认开启（有 manifest 声明时）**，撤业务权限级联清 AUTO_DEP |
| 4 | 路由守卫 | 仅 `meta.roles`（与契约不符） | **守卫读 `meta.permCodes` 并调 `hasPerms`**；`meta.auths/roles` 退场 |
| 5 | example-service 菜单注册 | 双向 RPC 同步 | **前端"发布菜单"半自动 UX**（采纳用户想法 1，调整版） |
| 6 | 字段级权限 | 业务 DTO 序列化层兜底，移出权限模型 | **MVP 即纳入最小子集**（多操作位 + entryPermCodes 多值 + DTO 白名单 + menuId 后端复算） |
| 7 | E-4b 反向清理工具 | Phase X 整体推迟 | **拆分**：E-4b-1（只读巡检 + dry-run + 角色归档/重置）提前 v2.1，E-4b-2 留 Phase X |
| 8 | 跨服务一致性 | 同进程 evictAfterCommit + Redis pub/sub | Outbox + Kafka/Redis Stream + 前端 TTL ≤120s + 心跳兜底 + reasonCode 协议 |
| 9 | 多租户隔离 | 隐式约束（schema NOT NULL）| **§10 显式硬隔离规范**（HMAC 跨租户写入拒绝、广播粒度 tenant_id 前缀、运行时审计）|
| 10 | **(v3.1) reportId↔menu 关系** | v2/v3 多对多（同 perm_code 多菜单）| **1:1 颠覆性约束**（reportId↔canonical menu 1:1；恢复 (tenant_id, perm_code) 唯一索引；entryPermCodes 退化单值；S3 跨业务线复用通过 §1.3.5 sys_menu_ref 引用机制解；字段级权限 MOVE_TO_V2_1）|

---

## 0.5. example-service 业务概念清单与资源分层模型

### 0.5.1 业务概念清单（演示载体）

example-service 当前文档定位是"BI 平台演示"（datasource/report/task）。多视角综合考虑，**保留 BI 主线 + 仅以最小子集证明双轨架构**：

| 业务概念 | perm 形态 | ResourceType / 操作码 | 备注 |
|---|---|---|---|
| Report 报表 | 类型化 ResourceType | `EXAMPLE_REPORT` + `VIEW/EDIT/APPROVE/EXPORT/VIEW_FINANCE_COLUMN/DATA_READ/DATA_EDIT` | 主资源，演示页面权 + 字段级 + 数据范围权 |
| Project 项目 | 范围 ResourceType | `EXAMPLE_PROJECT` + `DATA_READ/DATA_EDIT` | 被 REPORT 通过 `depend_on` 引用，演示数据范围 |
| Datasource 数据源 | 类型化 ResourceType | `EXAMPLE_DATASOURCE` + `VIEW/UPDATE/TEST_CONNECTION` | 演示 auto-grant 依赖（授 EXPORT 自动补 DATASOURCE:VIEW）|
| Task 数据任务 | 类型化 ResourceType | `EXAMPLE_TASK` + `VIEW/TRIGGER/ENABLE` | 演示 admin 任务调度中心反向调用 |
| TaskLog | **不**独立建模 | 复用 `EXAMPLE_TASK:VIEW_LOG` 操作码 | 避免对象爆炸 |
| Approval/Export | **不**独立建模 | 作为 Report/Task 上的操作码 | 反模式禁令：不要把动作造成 ResourceType |
| 部门数据范围 | 复用 admin | `ADMIN_ORG` + `DATA_READ` | 不在 example 重建，直接 depend_on |

**说明**：业务实体 Order/Customer/Quote 等在权限建模上与 Report 同构，文档一句话说明"接入方按此模式扩展"，无需在 example 中全部实现。

### 0.5.2 资源分层模型（核心，两层不是三层）

| 层 | ResourceType | 操作码 | 控制对象 |
|---|---|---|---|
| **L1 菜单可见性轨道** | `ADMIN_MENU` | `VIEW` | 路由能否进入 |
| **L2 业务操作 + 范围轨道** | 业务真实类型（`EXAMPLE_REPORT` 等） | `VIEW/CREATE/UPDATE/APPROVE/EXPORT/VIEW_FINANCE_COLUMN/DATA_READ/DATA_EDIT` | 按钮亮灭、API 闸门、行级数据、字段级 |

**已确认禁止的反模式**：
- **不**新建 `PAGE` ResourceType（v1.4 已废弃）
- **不**新建 `BUTTON` ResourceType（v1.4 已废弃，sys_menu 不再产生 BUTTON 行）
- 数据范围**不**作为独立第三层——它是 L2 中携带 `scope_all` / `depend_on` 的子权限，与"操作权"共用同一表
- 字段级**不**作为独立 ResourceType——通过 L2 多操作位 + DTO 白名单 + entryPermCodes ALL 实现（详见 §6.3）

### 0.5.3 从属/依赖关系

```
admin.sys_menu (DIR/MENU 行)
    └─ 同步 → resource_entity(type=ADMIN_MENU, code=sys_menu.id)
                      ↑
                软引用：sys_menu.perm_code='EXAMPLE_REPORT:VIEW'（字符串，非 FK）
                      + extra.entryPermCodes=[...] JSONB（权威多值）
                      ↓
example 注册的 type_definition(EXAMPLE_REPORT)
    ├─ operation_permission(VIEW/EDIT/APPROVE/VIEW_FINANCE_COLUMN/...)
    └─ resource_dependency
         ├─ MANIFEST 来源（业务→业务，启动期）
         └─ MENU_PUBLISH_DERIVED 来源（业务→菜单，admin 保存事务内派生）

授权落地：role_resource_permission
    ├─ grant_source = MANUAL / AUTO_DEP / ADMIN_UI
    └─ depend_on 子权限：scope_all=false → 关联 EXAMPLE_PROJECT 实例
```

**关键设计原则**：
- 菜单树与业务资源**不建物理外键关联表**。`menu_resource_binding` 表会引入双写、连锁失效。
- 关联点 = `sys_menu.perm_code`/`entryPermCodes` 字符串约定 + 真实鉴权由前端 `hasPerms("EXAMPLE_REPORT:APPROVE")` 调用。
- "菜单形状变化"不会污染权限模型——这是 v1.4 双轨设计的核心收益。

---

## 1. 不可变规则（核心）

### 1.1 菜单可见性主公式（节点类型分治 — v3.2 重写）

> ⚠️ **v3.2 修订（2026-06-18）**：菜单元数据从 `entryPermCodes` 多值通道改为 `operations` 集合 + `primary_operation` 显式声明。仅 `primary_operation` 参与可见性判断，其他操作位为按钮/字段级权限。

```
菜单可见(menu, user) :=
    menu.status = ENABLED
  ∧ menu.delete_flag = 0
  ∧ 按 menu_type 分支：
    ┌── DIR    ⇒  ∃ 子节点最终可见   (派生，自身不参与鉴权)
    ├── MENU(业务)  ⇒  ADMIN_MENU:{menuId}:VIEW = true
    │                 ∧ hasPerm("{resource_type}:{resource_code}:{primary_operation}", user) = true
    │                 [双轨 AND；primary_operation 必为 sensitivity=PUBLIC]
    ├── MENU(纯展示) ⇒  ADMIN_MENU:{menuId}:VIEW = true   [显式 entryPolicy='ADMIN_MENU_ONLY']
    ├── HIDDEN(visible=false)  ⇒  按 MENU(业务) 规则；不进 menus 树，下发到独立 hiddenRoutes 通道
    └── EXTERNAL/IFRAME       ⇒  按 MENU(业务) 规则；is_external 与 is_frame 二选一
```

**配套硬约束（v3.2 修订）**：

- DIR：`perm_code` / `resource_type` / `resource_code` / `primary_operation` / `operations` 必须**全部为空**，不向 permission-center 注册 `resource_entity(ADMIN_MENU)`。空目录递归剪枝。
- MENU(业务)：`perm_code` `resource_type` `resource_code` `primary_operation` `operations` **五字段全必填**；`ADMIN_MENU:VIEW` 与 `{type}:{code}:{primary_op}` **双轨 AND，缺一不可见**。
- MENU(纯展示)：`perm_code IS NULL` **必须**配 `extra.entryPolicy='ADMIN_MENU_ONLY'`，否则 admin 端保存校验失败。
- HIDDEN：`visible=false` 不进入 `menus[]`，由独立的 `hiddenRoutes[]` 数组下发，前端注册路由但不渲染侧栏；隐藏 ≠ 免鉴权。
- BUTTON：v1.5 起 `sys_menu` 不再有 BUTTON 行，迁移脚本清空残留。
- **v3.2 新增 IR-1.1-A**：`primary_operation` 必须在 `operations` 集合内（保存校验），且必须 `sensitivity_level=PUBLIC`（关闭 VIEW_COMMISSION 类敏感位被设为入口的安全洞）。
- **v3.2 新增 IR-1.1-B**：`operations` 中每个操作位都派生一条 `MENU_PUBLISH_DERIVED` 依赖（详见 §3.1.2），但仅 `primary_operation` 决定可见性。其他操作位通过前端 `<Perms value="{type}:{code}:{op}">` 控制按钮显示。

### 1.2 perm_code 数据格式（v3.2 修订 — 二段式 + operations 集合）

> ⚠️ **v3.2 修订（2026-06-18）**：v3.1 隐含的"三段式 perm_code"被显式重构为**二段式 perm_code + operations 集合 + primary_operation** 三件套，把"该菜单暴露的业务能力清单"集中到菜单元数据，解决"管理员授菜单时如何同时授业务权"的核心痛点。

**核心约束**：一个业务报表/资源（reportId）有且只能有一个对应菜单（sys_menu）。

#### 1.2.1 sys_menu schema（v3.2 字段定义）

| 字段 | 类型 | 语义 | 约束 |
|---|---|---|---|
| `perm_code` | VARCHAR(128) | **二段式 `{resource_type}:{resource_code}`**（如 `EXAMPLE_REPORT:sales-001`）；菜单全局唯一业务标识 | NOT NULL；唯一索引 `(tenant_id, perm_code)`；admin 端按 resource_type+resource_code 自动拼接，业务方不能改 |
| `resource_type` | VARCHAR(64) | 资源类型码（冗余存储，便于 query 与跨服务事件 payload 自包含） | NOT NULL（业务菜单）；必在 perm-center `type_definition.code` 中注册 |
| `resource_code` | VARCHAR(64) | 资源实例码 / 业务键（如 reportId） | NOT NULL；必在 perm-center `resource_entity` 表存在（同 tenant_id） |
| `primary_operation` | VARCHAR(64) | **入口操作位**：决定菜单是否可见；99% 场景为 `VIEW` | NOT NULL；必在 `operations` 集合内；必 `sensitivity_level=PUBLIC`（保存校验） |
| `operations` | JSONB（数组） | 该菜单暴露的全部业务操作位集合，如 `["VIEW","EXPORT","APPROVE","VIEW_COMMISSION"]` | 非空；必含 `primary_operation`；每元素必在 `operation_permission` 表 `status IN (ACTIVE, DEPRECATED)` |
| `default_preset` | VARCHAR(64) | 默认套餐名（引用 manifest 声明的 preset），授权页默认勾选 | 可空 |
| `source_service` | VARCHAR(64) | 业务服务标识（链路追溯，如 `example-service`） | NOT NULL（业务菜单） |
| `extra.entryPermCodes` | JSONB | **[DEPRECATED-v3.2]** v3 多值通道，保留兼容字段，新代码不消费 | 可空；新菜单不写此字段 |
| `extra.entryPolicy` | VARCHAR(32) | 保留字段，仅 MENU(纯展示)使用 `'ADMIN_MENU_ONLY'` | 可空 |

#### 1.2.2 不可变规则（IR-1.2.x）

- **IR-1.2-A**（沿用 v3.1）：`(tenant_id, perm_code)` 唯一索引。同租户内同 perm_code 不允许绑定到多个菜单。
- **IR-1.2-B**：`(tenant_id, path)` 唯一索引。URL→menuId 是函数。
- **IR-1.2-C**：所有跨服务事件以 tenant_id 为前缀（与 §10 多租户硬隔离同源）。
- **IR-1.2-D**（v3.1）：1:1 精确表述为 `reportId↔canonical menu 1:1`，菜单引用关系（sys_menu_ref，详见 §1.3.5）不计入唯一索引。
- **IR-1.2-E**（v3.2 替代旧 v3.1 IR-1.2-E）：`extra.entryPermCodes` 字段 DEPRECATED，新代码不消费；运行时遇旧数据自动降级为 `[primary_op]`。
- **IR-1.2-F**（v3.2 新增）：`perm_code` 必须严格二段式 `{type}:{code}`，禁止三段式与任意拼写；`primary_operation` 与其它操作位独立成列/集合，不允许塞回 perm_code 串。CI 启动期校验 `perm_code matches /^[A-Z][A-Z0-9_]*:[A-Za-z0-9_-]+$/`。
- **IR-1.2-G**（v3.2 新增）：`operations` 集合是该菜单"业务能力声明"的 SSOT 之一（与 manifest 全集互补）；前端 `useUserStore.menus[X].operations` 是按钮渲染的数据来源，不再由前端代码硬编码。

#### 1.2.3 反查 API（沿用 v3.1 NEW-1:1-3，扩展返回字段）

```
GET /admin/menu/by-perm-code/{perm_code}
Response: {
  current: {
    menuId, path,
    operations: ["VIEW", "EXPORT", ...],
    primaryOperation: "VIEW",
    resourceType: "EXAMPLE_REPORT",
    resourceCode: "sales-001"
  },
  deprecated?: { menuId, path }   // path_aliases 过渡期边角场景
}
或 404 PERM_CODE_NOT_BOUND
```

#### 1.2.4 错误码

| 错误码 | 触发场景 |
|---|---|
| `PERM_CODE_ALREADY_BOUND_TO_MENU=X` | 已被 menuId X 占用（响应体含占用 menuId+path） |
| `PERM_CODE_DUPLICATED` | 纯重复（多与并发竞态相关） |
| `PERM_CODE_FORMAT_INVALID` | 不符合二段式正则 |
| `RESOURCE_NOT_REGISTERED` | resource_type / resource_code 未在 perm-center 注册 |
| `OP_NOT_REGISTERED` | operations 元素未在 OperationPermission 表 |
| `PRIMARY_OP_NOT_IN_OPERATIONS` | primary_operation 不在 operations 集合内 |
| `PRIMARY_OP_TOO_SENSITIVE` | primary_operation 的 sensitivity_level ≠ PUBLIC |
| `ENTRY_PERM_CODES_MULTI_VALUE_NOT_SUPPORTED` | 尝试写入 entryPermCodes（已 DEPRECATED） |

#### 1.2.5 TOCTOU 防护（沿用 v3.1）

saveMenu AppService 必须使用 ON DUPLICATE KEY 语义（MyBatis-Flex 配套），禁止先 SELECT 再 INSERT；预校验 API 仅作 UX 提示，唯一索引是强约束。

#### 1.2.6 迁移脚本（附录 M-001 v3.2 版）

```sql
-- 1. 新增 v3.2 字段
ALTER TABLE sys_menu
  ADD COLUMN resource_type VARCHAR(64) NULL,
  ADD COLUMN resource_code VARCHAR(64) NULL,
  ADD COLUMN primary_operation VARCHAR(64) NULL,
  ADD COLUMN operations JSONB NULL,
  ADD COLUMN default_preset VARCHAR(64) NULL,
  ADD COLUMN source_service VARCHAR(64) NULL;

-- 2. 存量数据回填占位（治理评审拆分到独立 reportId 后人工迁移）
UPDATE sys_menu SET
  resource_type    = SPLIT_PART(perm_code, ':', 1),
  resource_code    = '__migrated_global__',          -- 标记待治理
  primary_operation = SPLIT_PART(perm_code, ':', 2),
  operations       = JSONB_BUILD_ARRAY(SPLIT_PART(perm_code, ':', 2))
WHERE menu_type = 'MENU' AND perm_code IS NOT NULL;

-- 3. 启动期 CI 校验 perm_code 必须匹配新二段式正则；不匹配 fail-fast
-- 4. 字段级 NOT NULL 约束在数据回填+治理完成后再加（避免迁移期 break）
-- 5. (tenant_id, perm_code) 唯一索引沿用 v3.1（已 ADD 过）
```

#### 1.2.7 验收点（追加到 §5）

- **NEW-1:1-1**：同租户 Alice 已有菜单 X 使用 `perm_code=EXAMPLE_REPORT:sales-001`，再发布菜单 Y 使用同一 perm_code → 触发 `BizException(PERM_CODE_ALREADY_BOUND_TO_MENU=X)`。
- **NEW-1:1-2**：业务侧拆 reportId 反模式检测 → MEDIUM 巡检告警。
- **NEW-1:1-3**：`GET /admin/menu/by-perm-code/{perm_code}` 反查 API 返回 MenuRef（含 operations + primaryOperation）或 404。
- **NEW-3.2-1**（新）：发布菜单 `primary_operation = VIEW_COMMISSION`（sensitivity=SENSITIVE）→ `BizException(PRIMARY_OP_TOO_SENSITIVE)`。
- **NEW-3.2-2**（新）：`operations = ["VIEW","EXPORT"]` 但 `primary_operation = "APPROVE"` → `BizException(PRIMARY_OP_NOT_IN_OPERATIONS)`。
- **NEW-3.2-3**（新）：`perm_code = "EXAMPLE_REPORT:sales-001:VIEW"`（三段式） → `BizException(PERM_CODE_FORMAT_INVALID)`。
- **NEW-3.2-4**（新）：菜单已发布后 manifest 新增 EXPORT_PDF 操作位 → admin 后台 MEDIUM 告警 "sales-001 菜单 operations 未声明 EXPORT_PDF"，**默认不自动加入**（D-3 留挂等 manifest 讨论决定最终行为）。

### 1.3 路由可访问性公式（与菜单可见性同源）

```
路由可访问(routePath, user) := 路由对应 menu 可见(menu, user)
```

**统一为同一公式**——避免"侧栏看到但点进去 403"或"侧栏隐藏但 URL 直跳进去"两类对账失败。

实现：前端路由 `meta.permCodes = entryPermCodes`，`meta.menuId = menuId`；`router.beforeEach` 守卫调 `hasPerms(meta.permCodes)`；后端不再下发隐藏菜单，前端绕不过。

### 1.3.5 sys_menu_ref 菜单引用机制（v3.1 新增 — S3 跨业务线复用配套）

> ⚠️ **v3.1 新增（2026-06-18）**：S3 跨业务线复用是 9 角色（架构师 HIGH、BI HIGH、PM HIGH、租户管理员 HIGH、开发 CRITICAL、Manager HIGH、安全审计员 HIGH、运维 HIGH、终端员工 LOW）联合提出的最大新风险，必须在 v2 GA 同期落地。

**问题背景**：1:1 约束（reportId↔canonical menu）下，跨业务线复用同一报表（销售部和财务部都要在自己菜单树挂'销售业绩报表'）会被 sys_menu 唯一索引直接拒绝。引入 sys_menu_ref 解决组织诉求，同时不破坏 1:1 约束。

**表设计**：
```sql
CREATE TABLE sys_menu_ref (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  parent_menu_id BIGINT NOT NULL,         -- 引用挂载的父菜单（部门菜单树节点）
  ref_menu_id BIGINT NOT NULL,            -- 指向 canonical sys_menu.id
  display_name_override VARCHAR(128),     -- 部门内自定义显示名（如'销售业绩-利润视角'）
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME, created_by BIGINT,
  UNIQUE KEY uk_parent_ref (tenant_id, parent_menu_id, ref_menu_id),
  INDEX idx_canonical (tenant_id, ref_menu_id),
  CONSTRAINT fk_ref_canonical FOREIGN KEY (tenant_id, ref_menu_id) REFERENCES sys_menu(tenant_id, id) ON DELETE CASCADE
);
```

**核心规则（IR-1.3.5-A ~ E）**：
- IR-1.3.5-A：menu_ref 不是 sys_menu，不参与 (tenant_id, perm_code) 唯一索引；canonical 菜单仍唯一。
- IR-1.3.5-B：权限校验穿透到 canonical menu_id；授权 API 入参强制校验 target 必须是 canonical menu_id，传 ref_menu_id 直接 `BizException(REF_MENU_NOT_AUTHORIZABLE)`。
- IR-1.3.5-C：menu_ref 必须同租户（衍生自 IR-10.1）；ref_menu_id 必须存在且 enabled。
- IR-1.3.5-D：删除 canonical sys_menu 级联删除所有 ref（FK ON DELETE CASCADE）；删除事务必须发出 menu-ref-changed 事件（参见 §10.3 channel）。
- IR-1.3.5-E：canonical 菜单挂在中立 `/catalog/` 命名空间下（如 `/catalog/reports/sales-perf`），不挂在任何业务部菜单树下；`/catalog/` 下注册需要 `ADMIN_MENU_CATALOG:CREATE` 操作位（仅授给中央治理组，非业务部 admin），DomainClassifyService 配置中 `/catalog/` 不归任何业务域所有，仅 GLOBAL 域可见可管。

**SDK 接口**：
```java
// 发布 canonical 菜单（受 ADMIN_MENU_CATALOG:CREATE 保护）
MenuRef publishCanonicalMenu(PublishRequest req);  // 1:1 校验在此触发
// 在部门菜单树挂引用
MenuRef attachRef(Long parentMenuId, Long canonicalMenuId, String displayNameOverride, int sortOrder);
// 反查 perm_code 占用
Optional<MenuRef> findMenuByPermCode(String permCode);  // 配合 NEW-1:1-3
```

**MenuVisibilityResolver 渲染规则**：
- 同一终端用户的菜单树聚合时，多个 menu_ref 同源（指向同一 canonical）只触发一次 PermQueryEngine 查询；
- 渲染显示名优先 display_name_override，次取 canonical 菜单 displayName；
- 前端在通过 menu_ref 进入页面时，标题栏小字 tooltip 附加 canonical 菜单原始名称（终端员工 MEDIUM 提出，避免用户对账困惑）。

**配额（关联 §10.4）**：max_menus_per_service 仅统计 canonical 菜单（sys_menu）；max_menu_refs 单独配额（默认 5x max_menus）。

**事件总线（关联 §10.3）**：新增 channel: `perm:tenant:{tenantId}:menu-ref-changed:{canonicalMenuId}`；订阅端按 ref 表反查所有受影响菜单树并失效缓存；MenuVisibilityResolver 缓存 key 加入 canonical_menu_id 维度。

**故障注入测试**：canonical 菜单被删但 ref 残留场景（理论被 FK CASCADE 阻断），前端必须降级显示而非 500。

**新增验收用例**（追加到 §5）：
- NEW-S3-1：T1 已注册 canonical menu#X (perm_code=EXAMPLE_REPORT:VIEW)，销售部和财务部各自 attachRef 到 menu#X，授权 EXAMPLE_REPORT:VIEW 给 Sales 角色，Sales 在两棵菜单树都看到入口，点击均进同一 URL，仅触发一次 PermQueryEngine.hasPermission；
- NEW-S3-2：删除 canonical menu#X 触发 sys_menu_ref CASCADE 清理 + menu-ref-changed 事件广播；
- NEW-S3-3：尝试给 ref_menu_id 单独授权被拒（REF_MENU_NOT_AUTHORIZABLE）；
- NEW-S3-4：跨租户 ref（T1 ref 指向 T2 的 canonical）注册被拒（IR-1.3.5-C）。

### 1.4 按钮/操作 hasPerms 公式

```
按钮可点(btn, user) := hasPerms(btn.permCode)   //  permCode 形如 "EXAMPLE_REPORT:APPROVE"
```

数据源：`/auth/user-menu.permissions[]`（动态白名单），与菜单层完全解耦。按钮**不**进 `sys_menu`。

---

## 2. /auth/user-menu 改造方案

### 2.1 接口契约

```json
{
  "menus": [ /* MenuRouteItem 树，含 meta.permCodes / meta.menuId / meta.frameSrc / meta.entryPolicy */ ],
  "hiddenRoutes": [ /* 隐藏路由扁平列表，前端 addRoute 但不渲染 */ ],
  "roles": ["admin", "report_viewer"],
  "permissions": ["EXAMPLE_REPORT:VIEW", "EXAMPLE_REPORT:APPROVE", "ADMIN_USER:VIEW", ...],
  "snapshotVersion": "2026061712345",
  "permFingerprint": "sha256:abcd1234..."
}
```

### 2.2 后端聚合算法

```java
public UserMenuResp getUserMenu(Long tenantId, Long userId) {
    // ① 全量菜单（admin 本地单 SQL）
    List<SysMenu> all = menuDomainService.selectAllValid(tenantId);

    // ② 解析每个 MENU 的 entryPermCodes
    Map<Long, EntrySpec> entries = parseEntrySpecs(all);

    // ③ 单次 batch-check：合并"菜单 VIEW" + "全部业务入口码"
    List<AuthCheckItem> items = new ArrayList<>();
    for (SysMenu m : all) {
        if (m.menuType == DIR) continue;
        items.add(item(ADMIN_MENU, m.id, VIEW));                    // 轨道 1
        if (!"ADMIN_MENU_ONLY".equals(entries.get(m.id).policy)) {
            for (String pc : entries.get(m.id).permCodes) {
                items.add(parseToItem(pc));                          // 轨道 2
            }
        }
    }
    Map<AuthKey, Boolean> r = permissionFeignClient.batchCheckAuth(dedup(items));

    // ④ 双轨 AND 判定（叶子节点）
    Set<Long> visibleLeafIds = new HashSet<>();
    for (SysMenu m : all) {
        if (m.menuType == DIR) continue;
        if (m.status != ENABLED) continue;
        if (!r.get(menuKey(m.id))) continue;
        EntrySpec spec = entries.get(m.id);
        if ("ADMIN_MENU_ONLY".equals(spec.policy)) {
            visibleLeafIds.add(m.id); continue;
        }
        if (spec.permCodes.isEmpty()) continue;
        if (spec.permCodes.stream().allMatch(pc -> r.get(permKey(pc)))) {
            visibleLeafIds.add(m.id);
        }
    }

    // ⑤ 父目录补齐（仅当有可见子节点；父 ADMIN_MENU:VIEW 仍要满足）
    Set<Long> finalSet = bubbleUpDirsRequiringAdminMenuView(all, visibleLeafIds, r);

    // ⑥ 拆分 visible 与 hidden
    List<MenuRouteItem> menus = buildTree(all, finalSet, /*onlyVisible*/ true);
    List<MenuRouteItem> hiddenRoutes = buildFlat(all, finalSet, /*onlyHidden*/ true);

    // ⑦ permissions 动态白名单
    Set<String> typeWhitelist = deriveTypeWhitelist(all, finalSet);
    typeWhitelist.addAll(MGMT_FALLBACK_TYPES);
    List<String> permissions = permissionFeignClient
        .getEffectivePermissionCodes(SUBJECT_TYPE_ADMIN_USER, userId, typeWhitelist)
        .getData().permissions();

    return new UserMenuResp(menus, hiddenRoutes, roles, permissions,
        snapshotVersion, fingerprint(permissions));
}
```

**性能**：单次 `batch-check` + 单次 `effective-permission-codes` + 1 次本地 SQL，RPC 数从 N+2 降为 2。

### 2.3 父菜单补齐策略

1. 父 DIR 自身**不参与** entryPermCodes 校验。
2. 父 DIR 必须满足 `ADMIN_MENU:{parentId}:VIEW` 才能保留——管理员有意隐藏整个目录是合法手段。
3. **必须**至少有一个最终可见的子节点（递归向下），否则剪掉整个 DIR。
4. 子全部被剪 → 父 DIR 整体剔除，不渲染空目录。

### 2.4 与工作单 A 快照模式的协同

- 前端 store 缓存 `snapshotVersion`；后续每次 `refreshUserMenu()` 携带 `If-None-Match`。
- 后端在 batch-check 前先查租户 + 用户的当前 snapshotVersion；命中即返回 304，无需重算。
- WebSocket / SSE 推送 `EVT_PERM_VERSION_CHANGED` 时，前端立即重拉。

### 2.5 与 Gateway 鉴权的角色分工

| 平面 | 责任 | 数据源 |
|---|---|---|
| 前端菜单/按钮（UI 隐藏） | 体验层防呆，不是安全边界 | `/auth/user-menu.menus + permissions` |
| 前端路由守卫（路由可达性） | 防 URL 直跳，体验层 | `meta.permCodes` + `hasPerms` |
| **Gateway / Controller `@PreAuthorize`** | **唯一安全边界** | `engine.hasPermission()` + ResourceApiMapping |

**铁律**：前端隐藏不等于后端拒绝；后端拒绝才是真拒绝。

---

## 3. auto-grant 边界明确

### 3.1 授业务权限时是否补菜单权限？（修订版）

> ⚠️ **v3 修订（C3）**：原 manifest 启动期声明 `EXAMPLE_REPORT:VIEW → ADMIN_MENU:{reportMenuId}:VIEW` 范式因 menuId 启动期未知而断裂；改为依赖规则按 SSOT 分两类来源。

**默认行为**：开启 auto-grant；但依赖规则的写入分两类来源，避免启动期 menuId 未知导致链路断裂。

#### 3.1.1 依赖规则来源分类（强制）

| 来源 `maintain_source` | 写入时机 | 拥有者 | 适用场景 |
|---|---|---|---|
| `MANIFEST` | ~~example-service 启动期 SDK 推送~~ | ~~example-service~~ | **[DROPPED-v3.3]** 操作位间依赖由 effective_bits 位运算覆盖 |
| `MENU_PUBLISH_DERIVED` | admin 保存 sys_menu 事务内自动派生 | admin（菜单 SSOT）| 业务→菜单依赖（`{entryPermCode} → ADMIN_MENU:{menuId}:VIEW`）|
| `ADMIN_UI` | 管理员显式在 admin UI 配置 | admin | 兜底人工补登 |

#### 3.1.2 admin 保存菜单时的派生协议（v3.2 重写 — 按 operations 数组循环）

```java
@Transactional(rollbackFor = Exception.class)
public Long saveMenu(Long tenantId, MenuPublishReq req, Long operatorId) {
  // 0. 校验六条（v3.2 新增）
  validate(req);  // PERM_CODE_FORMAT_INVALID / RESOURCE_NOT_REGISTERED / OP_NOT_REGISTERED
                  // / PRIMARY_OP_NOT_IN_OPERATIONS / PRIMARY_OP_TOO_SENSITIVE / PERM_CODE_ALREADY_BOUND_TO_MENU

  // 1. 写入 sys_menu（含 v3.2 新字段）
  String permCode = req.getResourceType() + ":" + req.getResourceCode();  // 二段式
  Long menuId = sysMenuMapper.insert(SysMenu.builder()
    .tenantId(tenantId)
    .permCode(permCode)
    .resourceType(req.getResourceType())
    .resourceCode(req.getResourceCode())
    .primaryOperation(req.getPrimaryOperation())
    .operations(req.getOperations())              // JSONB 数组
    .defaultPreset(req.getDefaultPreset())
    .sourceService(req.getSourceService())
    .path(req.getPath())
    .menuType(MenuType.MENU)
    .build());

  // 2. 注册菜单作为权限对象
  resourceEntityMapper.upsert(tenantId, ResourceTypeCode.ADMIN_MENU,
    String.valueOf(menuId), req.getDisplayName());

  // 3. v3.2 关键：按 operations 数组循环派生 N 条 MENU_PUBLISH_DERIVED 依赖
  //    每条规则形如：{type}:{code}:{op} → ADMIN_MENU:{menuId}:VIEW
  //    auto-grant 触发时，授任一 op 即可补菜单可见权（撤完所有 op 才反向清菜单可见）
  for (String op : req.getOperations()) {
    String fullPermCode = req.getResourceType() + ":" + req.getResourceCode() + ":" + op;
    resourceDependencyMapper.upsert(
      tenantId, fullPermCode,
      "ADMIN_MENU", String.valueOf(menuId), "VIEW",
      MaintainSource.MENU_PUBLISH_DERIVED);
  }

  // 4. Outbox 写事件（payload 自包含 operations + primaryOperation，跨服务订阅消费）
  outboxMapper.insert(EVT_MENU_CREATED, Map.of(
    "tenantId", tenantId, "menuId", menuId, "permCode", permCode,
    "operations", req.getOperations(), "primaryOperation", req.getPrimaryOperation()));

  // 5. evictAfterCommit + 版本广播
  permissionVersionDomainService.incrementAndBroadcast(tenantId);
  return menuId;
}
```

**核心变化**：
- **派生条数从 1 → N**（每个 operations 元素一条），授任一业务码触发 auto-grant
- **撤业务码时反向清理**遵循 §3.2 条件级联：菜单 VIEW 仅在所有依赖位都被撤后清理（共享场景受保护）
- **跨服务事件 payload 自包含**operations + primaryOperation，perm-center 订阅方无需额外查询

#### 3.1.3 PermissionGrantAppService.grant 反查（合并两类来源）

```java
// 给角色授业务码时，反查所有该业务码的依赖（含 MANIFEST 与 MENU_PUBLISH_DERIVED）
List<ResourceDependency> deps = resourceDependencyMapper.findBySourceCode(tenantId, businessPermCode);
for (ResourceDependency dep : deps) {
  if (dep.getMaintainSource() == MaintainSource.MENU_PUBLISH_DERIVED || dep.isAutoGrantEnabled()) {
    rolePermMapper.upsertAutoGrant(roleId, dep.getTargetResourceCode(), dep.getTargetOperationCode(),
      grantSource = AUTO_DEP, grantDepId = dep.getId());
  }
}
```

#### 3.1.4 撤业务权限时的反向清理

沿用原 §3.2，但 grant_dep_id 可能指向 MANIFEST 或 MENU_PUBLISH_DERIVED 任一来源——清理逻辑不区分来源，统一按 grant_dep_id 反查。

**菜单删除/重命名时**：admin 在事务内删除/标记 sys_menu 行，同时级联删除 maintain_source=MENU_PUBLISH_DERIVED 的 resource_dependency 行 + 触发反向清理 AUTO_DEP（具体见 §11.2 菜单生命周期）。

**验收点（追加到 §5）**：
- AC-3.1-1：Alice 发布 A2 菜单后，resource_dependency 表存在 (DATA_READ_FINANCE → ADMIN_MENU:{A2.id}:VIEW, source=MENU_PUBLISH_DERIVED) 行。
- AC-3.1-2：Bob 给角色授 DATA_READ_FINANCE，role_resource_permission 中自动追加 AUTO_DEP 行指向 A2 菜单 VIEW。
- AC-3.1-3：Alice 删除 A2 菜单后，对应 MENU_PUBLISH_DERIVED 依赖与所有依赖此规则的 AUTO_DEP 授权一并清理。

### 3.2 撤业务权限时 AUTO_DEP 菜单权限处理？

**条件级联清理**：

- 反查所有 `grant_source=AUTO_DEP AND grant_dep_id=被撤规则` 的菜单授权行。
- **当且仅当**该菜单不再被同角色的其他业务授权支撑（无其他 AUTO_DEP 行指向同一菜单）→ 删除。
- 共享菜单场景（菜单同时挂 A、B 两业务资源）：撤 A 不清菜单，仅 A、B 全撤完才清。
- 手工授（`grant_source=MANUAL`）的同名菜单授权**绝不动**。

### 3.3 手工授菜单权但无业务权限的菜单是否隐藏？（修订版）

> ⚠️ **v3 修订（C14）**：原'部分命中即红'告警会让 Finn 类合法配置（仅财务列）噪音化淹没真实问题。改为告警分级 + 静音白名单 + 发布前 dry-run。

**默认行为**：菜单不可见（双轨 AND 兜底）；后台告警分级避免噪音。

#### 3.3.1 告警分级

| 级别 | 触发条件 | 监控通道 |
|---|---|---|
| HIGH（实时告警）| entryPermCodes 中存在 perm_code 不在 perm-center（status≠ACTIVE/DEPRECATED），即结构性错配 | P1 监控告警 + admin 红色徽章 |
| MEDIUM（看板）| 角色授了 ADMIN_MENU:VIEW 但 entryPermCodes 与该角色已授业务码**完全无交集**（典型误配）| admin 授权页橙色高亮 + 日报汇总 |
| INFO（静默）| 角色已授 entryPermCodes 中**部分**业务码（合理的细粒度配置，如 Finn 仅财务列）| 不告警；管理员可在角色授权页查看明细 |

#### 3.3.2 '已确认无需告警'白名单

```sql
CREATE TABLE perm_alert_silence (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  confirmed_by BIGINT NOT NULL,
  confirmed_at DATETIME NOT NULL,
  reason VARCHAR(256)
);
```

admin 授权页对 MEDIUM 告警提供'确认有意为之'按钮 → 写入 perm_alert_silence → 后续不再告警。

#### 3.3.3 发布前 dry-run（解决 C14 配套）

```
POST /admin/menu/preview-visibility
Body: { entryPermCodes: [...], roleIds?: [...] }
Resp: {
  visibleByRole: { roleId: { hit: [...], missing: [...], visible: true/false } },
  estimatedUserCount: 1234,
  warnings: [...]
}
```

菜单保存表单旁挂'预览：哪些角色会看到此菜单'，作为 §3.3 告警的真值来源。

#### 3.3.4 反向'谁能看此菜单/资源'查询 API

```
GET /perm/resource/{type}/{code}/effective-subjects?op=VIEW
Resp: {
  roles: [{ roleId, roleName, conditionExpiry?, scopeHint? }],
  estimatedUserCount: 234,
  asOf: '2026-06-17T...'
}
```

**例外**：MENU(纯展示)（`entryPolicy='ADMIN_MENU_ONLY'`）只需 `ADMIN_MENU:VIEW`，单授即可见。

**验收点（追加到 §5）**：
- AC-3.3-1：Finn 角色仅授 DATA_READ_FINANCE，admin 授权页不弹告警（INFO 级静默）。
- AC-3.3-2：某角色授了 ADMIN_MENU:VIEW 但 entryPermCodes 与该角色已授业务码完全无交集 → MEDIUM 告警橙色高亮。
- AC-3.3-3：管理员保存菜单前调 dry-run，看到'团队管理者会看到、报表查看者会看到、财务报表查看者不会看到（缺 EXAMPLE_REPORT:VIEW）'明细。

### 3.4 MVP（auto-grant 未上线前）的过渡方案

1. **配对 seed 模板**：`docs/design/schema/seed-{service}-menus.sql` 强制每个 MENU 业务页面同时插入 `ADMIN_MENU:VIEW` 与 `entryPermCodes` 两侧的 `operation_permission` + `resource_entity`。
2. **授权 UI 的引导器**：admin 角色授权页选中业务资源时，旁侧渲染"该资源涉及的菜单可见权 + 依赖资源"清单（读 `resource_dependency`），引导一次性勾全。
3. **软提醒检查（不阻断）**：`PermissionGrantAppService.grant()` 检测"业务码已授但菜单 VIEW 未授"，返回 200 + warning 字段，UI 提示而非报错。
4. **CI 契约测试**：扫描所有 `sys_menu.perm_code` 非空行，断言 `ADMIN_MENU` 与每个 entryPermCode 对应资源类型同时在 permission-center 注册；断链 → CI 红。
5. **`PermissionGrantAppServiceImpl` auto-grant 实现完成后**，软提醒自动消失，warning 字段不再下发；既有数据无需迁移（AUTO_DEP 仅对新授权生效，存量 MANUAL 行保留）。

---

## 4. 全周期场景（7 阶段）

### 4.1 租户启用 example-service
example-service 启动时通过 perm-sdk 推 manifest：注册 `EXAMPLE_REPORT` 资源类型 + 操作位（VIEW/CREATE/APPROVE/EXPORT）+ `EXAMPLE_REPORT:VIEW → ADMIN_MENU:{reportMenuId}:VIEW` 等 auto-grant 依赖。permission-center 落库 `type_definition / operation_permission / resource_dependency`。**菜单不在 manifest 中**——菜单由前端"发布菜单"流程（§6.1）创建。

### 4.2 导入/维护菜单
管理员在 example-service 前端点"发布菜单"按钮 → 携 `?perm_code=EXAMPLE_REPORT:VIEW&path=/example/report&name=报表中心&menu_type=MENU` 跳转到 admin 的菜单创建页 → 用户补全图标/排序/父目录后保存。

保存校验：① menu_type=MENU 时 perm_code 或 entryPolicy 二选一；② DIR 时 perm_code 必须为空；③ 所有 entryPermCodes 在 permission-center 真实存在；④ admin 同步注册 `resource_entity(ADMIN_MENU, code=menuId)`。

### 4.3 同步业务资源 / 接口资源 / 依赖规则
example-service SDK 扫描 `@RequestMapping` + `@PreAuthorize`，上报 `resource_api_mapping`（`POST /api/example/report/approve → EXAMPLE_REPORT:APPROVE`）；依赖规则也通过 manifest 同步（业务→业务、业务→菜单两类）。admin 仅持有菜单，业务资源完全自治。

### 4.4 给角色授权
管理员在 admin 角色授权页选 `EXAMPLE_REPORT:VIEW`：
- AppService 读 `resource_dependency`，事务内写入 `role_resource_permission`：业务行 `grant_source=MANUAL`，菜单行 `grant_source=AUTO_DEP, grant_dep_id=...`。
- `permission_version` 自增；缓存 `evictAfterCommit`；广播 `EVT_PERM_VERSION_CHANGED`。
- 授权页旁侧渲染"涉及菜单：报表中心 ✓ 自动补"提示透明化。

### 4.5 用户登录获取菜单和权限
前端拉 `/auth/user-menu` → 单次 batch-check + 单次 effective-permission-codes → 返回 `{menus, hiddenRoutes, roles, permissions, snapshotVersion, permFingerprint}`。`useUserStore` 缓存全部字段；路由表按 menus 注册主路由、按 hiddenRoutes 注册隐藏路由。

### 4.6 用户进入页面 / 查列表 / 点按钮 / 调接口
- 路由守卫：`hasPerms(to.meta.permCodes)` 兜底（`meta.roles` 检查移除）。
- 页面渲染：列表组件入口 `hasPerms("EXAMPLE_REPORT:VIEW")` 二次兜底。
- 按钮：`<Perms value="EXAMPLE_REPORT:APPROVE">` 控制显示。
- API：Gateway 通过 ResourceApiMapping 转 `EXAMPLE_REPORT:APPROVE` → `engine.hasPermission()`，前端绕不过。

### 4.7 权限撤销 / 菜单删除 / 业务资源删除 / 接口变更
- 撤业务：按 §3.2 条件级联清 AUTO_DEP；版本 ++；下次 `/auth/user-menu` 自动剪。
- 删菜单：admin 软删 `sys_menu` + 同步软删 `resource_entity(ADMIN_MENU)` + 软删该菜单全部授权行；广播版本失效。
- 删业务资源类型：permission-center 删 `type_definition` + `operation_permission` + 依赖规则；菜单 entryPermCodes 失配 → `/auth/user-menu` 该菜单永远不可见，admin 后台告警"菜单 X 引用了不存在的 perm_code Y，请清理"。
- 接口变更：仅改 `resource_api_mapping`，菜单可见性不受影响。

### 4.8 端到端线性信息流（销售员小李审批订单）

```
[T0] 小李登录 → Gateway 校验 JWT → 注入 X-User-Id / X-Tenant-Id
   ↓
[T1] 浏览器 → admin-service GET /auth/user-menu
   ├─ admin 调 perm.batch-check-auth(menus × ADMIN_MENU:VIEW + 全部 entryPermCodes)
   ├─ admin 调 perm.effective-permission-codes(typeWhitelist)
   └─ 返回 { menus, hiddenRoutes, roles, permissions: [{code, expiresAt?, scopes?}],
            snapshotVersion, permFingerprint }   ← 配合工作单 A 快照模式
   ↓
[T2] 前端渲染左侧菜单（已过滤）+ 缓存 permissions 供 hasPerms 使用 + 注册 hiddenRoutes
   ↓
[T3] 小李点"订单管理"→ 路由守卫验 meta.permCodes ALL（前端缓存命中）→ 进入页面
   ↓
[T4] 页面调 example-service GET /api/biz/reports?page=1
   └─ example 入口 engine.hasPermission(EXAMPLE_REPORT, *, VIEW)
      + scopes = engine.queryScopes(EXAMPLE_PROJECT, DATA_READ) → [P-001, P-002]
   ↓
[T5] example 按 scopes 过滤 demo_report.project_id IN (P-001, P-002) → 返回列表
   ↓
[T6] 小李点"审批"按钮（前端 hasPerms("EXAMPLE_REPORT:APPROVE") 已显示）
   → POST /api/biz/reports/123/approve
   ↓
[T7] example 入口 engine.hasPermission(EXAMPLE_REPORT, 123, APPROVE)
   + 行级范围二次校验 report.project_id ∈ scopes
   + （字段级场景）DTO 白名单按 VIEW_FINANCE_COLUMN 复算
   ↓
[T8] 通过 → 写 example_db.demo_report.status='APPROVED'
   ↓
[T9] @OperationLog AOP 异步发审计 → admin.sys_audit_log
   ↓
[T10] 返回 200；perm-center / admin 不感知本次业务调用细节
```

**关键点**：
- T1 一次性拉取，T3-T6 走前端缓存（TTL ≤ 120s + SSE 推送 + 心跳兜底，详见 §12.3）
- T4/T7 是真实鉴权点，走 PermQueryEngine（fail-closed，详见 §12.5）
- T9 审计走 example 自有通道，不进 perm 同步管线
- 全链路用 X-Perm-Trace-Id 串联（详见 §12.4 reasonCode 协议）

---

## 5. 验收用例矩阵（v3.1 修订 — 30 条）

> ⚠️ **v3.1 修订（2026-06-18）**：1:1 约束下移除 3 条、修改 4 条、新增 10 条。原 28 条版本见 git history。

### 5.1 双轨 AND 与可见性（沿用原 1-8 条 KEEP）

| # | 用例 | 期望 |
|---|---|---|
| 1 | 角色 R1 授 ADMIN_MENU:M1:VIEW 但未授 entryPermCodes | menu M1 不可见 + admin 看板 MEDIUM 告警（未确认时）|
| 2 | 角色 R1 仅授 entryPermCodes 全部 | M1 不可见（双轨 AND 兜住）|
| 3 | 同时授齐 | M1 可见 |
| 4 | URL 直跳 | 路由守卫 + Controller @PreAuthorize 双重拦 |
| 5 | hiddenRoutes 显式禁用 | 路由表不注册 + 直跳 403 |
| 6 | 父菜单纯目录 | 子菜单全 invisible 时父菜单空目录隐藏 |
| 7 | batch-check RPC 1 次 | /auth/user-menu 总 RPC 数 = 2 |
| 8 | 撤业务码 | AUTO_DEP 行级联撤；MANUAL 行保留 |

### 5.2 字段级权限 + URL 注入防御（v3.1 修订）

> ⚠️ **v3.1 修订**：移除 Case 10（X-Menu-Id IDOR 主路径物理消失）；MODIFY Case 9（保留 DTO 白名单核心断言，删除 menuId 复算限定）；移除 AC-6.3-3。

| # | 用例 | 期望 |
|---|---|---|
| 9 (MODIFIED) | 小李仅授 EXAMPLE_REPORT:VIEW，URL 拼接 ?columns=finance | DTO 白名单生效，commission/finance 字段为 null + `_suppressedFields=["commission"]` |
| ~~10~~ | ~~小李前端伪造 X-Menu-Id={A2.id}~~ | **REMOVED v3.1**（X-Menu-Id IDOR 主路径物理消失） |
| 11 | Mia 授 VIEW + VIEW_FINANCE_COLUMN | finance 字段返回 |

### 5.3 perm_code 生命周期（KEEP）

| # | 用例 | 期望 |
|---|---|---|
| 12 | manifest 重命名 DATA_READ_FINANCE → VIEW_FINANCE_COLUMN | 旧码 DEPRECATED + 30 天双发期 + admin 看板可见 deprecated 用量 |
| 13 | retire_after 到期 | 引用旧码请求 deny + reason=PERM_CODE_RETIRED |
| 14 | manifest 尝试 DELETE 已用 perm_code | 拒绝 + BizException(PERM_CODE_IN_USE) |

### 5.4 菜单生命周期（KEEP）

| # | 用例 | 期望 |
|---|---|---|
| 15 | Alice 调 ?action=update&menu_id=...&new_path=... | admin 显示 path diff，确认后 sys_menu 更新 + path_aliases 保留 60 天 |
| 16 | example-service 软删 reportId=B | admin 订阅 EVT_RESOURCE_DELETED 自动给 B 菜单 warning_flag=RESOURCE_DELETED |
| 17 | 用户点 B 菜单 | 后端返回 HTTP 410 + reasonCode=RESOURCE_DELETED |

### 5.5 缓存一致性 + 限时到期（KEEP）

| # | 用例 | 期望 |
|---|---|---|
| 18 | admin 改 user_role 后 5 秒内 | perm-center USER_ROLE 缓存失效 + Mia 调 EXPORT 直接 deny + reason=USER_ROLE_REVOKED |
| 19 | Aria dateRange 在 23:00 到期 | 60 秒内 permission_version 自增 + 前端导出按钮消失 |
| 20 | Aria 22:59:30 启动流式 EXPORT | 23:00 整 SDK 下发 PERM_REVOKED trailer 优雅关闭 + 审计 mid-stream-revoked |
| 21 | Redis pub/sub 失败 | outbox worker 60 秒内补偿广播 + 前端 polling 兜底 |
| 22 | SSE 断连 30s | 前端切 polling + 横幅'权限同步中' |

### 5.6 多租户隔离（v3.1 修订）

> ⚠️ **v3.1 修订**：原 AC-1.2-1 反转为 NEW-1:1-1（同租户同 perm_code 拒绝）。

| # | 用例 | 期望 |
|---|---|---|
| 23 | T1 已用 EXAMPLE_REPORT:VIEW，T2 同样发布 | 不冲突（跨租户独立） |
| 24 | T1 与 T2 都有项目编码 P-001，Mia（T1）授 scope=[P-001] | 仅返回 T1.demo_report 行（tenant_id 强制过滤）|
| 25 | example-service 跨租户写 resource_entity（X-Tenant-Id 与 body 不符）| 拒绝 + 审计 |

### 5.7 AUTO_DEP 巡检（v3.1 修订）

> ⚠️ **v3.1 修订 AC-3.3-1**：Finn '仅财务列'场景在 1:1 下不存在，改写为'Finn 仅授 VIEW_FINANCE_COLUMN 不授 EXAMPLE_REPORT:VIEW，admin 授权页弹 MEDIUM 告警（业务码组合不完整）'。

| # | 用例 | 期望 |
|---|---|---|
| 26 | Mia 离岗后调 GET /perm/admin/auto-dep-orphans | 返回 Mia 的孤儿 AUTO_DEP 清单 |
| 27 | 撤 Manager-Mia 的 EXAMPLE_REPORT:VIEW dry-run | 返回 'A1/A2/B 三菜单将不可见 + 影响 N 用户' |
| 28 | Aria 角色 7 天到期 | 自动转 ARCHIVED + Aria-2 接任前必须 reuse-reset |

### 5.8 v3.1 新增用例（NEW-1:1 / NEW-S1 / NEW-S3 共 10 条）

#### 5.8.1 1:1 约束验收（NEW-1:1-*）
| # | 用例 | 期望 |
|---|---|---|
| NEW-1:1-1 | 同租户 Alice 已有菜单 X 使用 perm_code=EXAMPLE_REPORT:VIEW，再发布菜单 Y 使用同一 perm_code | 触发唯一索引拒绝并返回 `BizException(PERM_CODE_ALREADY_BOUND_TO_MENU=X)`，响应体含占用 menuId+path |
| NEW-1:1-2 | 业务侧拆 reportId 反模式检测 | admin 端 displayName 前缀相似度+SQL 重叠命中触发 MEDIUM 巡检告警（升级自原 INFO） |
| NEW-1:1-3 | `GET /admin/menu/by-perm-code/{perm_code}` 反查 API | 返回唯一 MenuRef 或 404 PERM_CODE_NOT_BOUND；前端发布菜单 onBlur 预校验 |

#### 5.8.2 字段级权限简化版验收（NEW-S1-*）
| # | 用例 | 期望 |
|---|---|---|
| NEW-S1-1 | Sales (VIEW_BASE+SCOPE_SELF) 访问 /api/biz/reports | 返回 base 字段+本人订单，commission=null + `_suppressedFields=["commission"]` |
| NEW-S1-2 | Manager (VIEW_BASE+SCOPE_TEAM) 访问同一接口 | 返回 base 字段+本团队订单，无 commission |
| NEW-S1-3 | HR (无任何 VIEW_*) | 菜单不渲染（MenuVisibilityResolver 第一闸）+ URL 直跳 → 403 reasonCode=DENIED_BY_DOMAIN，CTA='此功能不在你的工作范围内'（双保险）|

#### 5.8.3 跨业务线复用验收（NEW-S3-*）
| # | 用例 | 期望 |
|---|---|---|
| NEW-S3-1 | T1 已注册 canonical menu#X，销售部和财务部各自 attachRef 到 menu#X，授权 EXAMPLE_REPORT:VIEW 给 Sales 角色 | Sales 在两棵菜单树都看到入口，点击均进同一 URL，仅触发一次 PermQueryEngine.hasPermission |
| NEW-S3-2 | 删除 canonical menu#X | 触发 sys_menu_ref CASCADE 清理 + menu-ref-changed 事件广播 |
| NEW-S3-3 | 尝试给 ref_menu_id 单独授权 | 拒绝 + `BizException(REF_MENU_NOT_AUTHORIZABLE)` |
| NEW-S3-4 | 跨租户 ref（T1 ref 指向 T2 的 canonical）注册 | 拒绝（IR-1.3.5-C）|

### 5.9 v3.1 已废弃用例

| # | 原用例 | 废弃原因 |
|---|---|---|
| AC-1.2-1 | '同租户 3 菜单共享 perm_code 全部成功' | 与 1:1 冲突，反转为 NEW-1:1-1 |
| Case 10 | X-Menu-Id IDOR 主路径 | 物理消失（X-Menu-Id Header 退场） |
| AC-6.3-3 | X-Menu-Id 透传场景 | 物理消失 |

---

## 6. 用户 3 个原创想法的评估

### 6.1 想法 1：前端"发布菜单"半自动 UX

**结论：调整后采纳**（核心机制纳入 v2 主线）。

**采纳理由**：
- 解决了"菜单与业务资源所有权割裂"的根本问题——业务侧最清楚自己有哪些 perm_code，应由业务侧前端发起菜单创建意图。
- 比"manifest 自动同步菜单"更轻：admin 仍是菜单 SSOT，避免业务服务越权写 admin 库。
- 比"管理员手工填一切"更安全：perm_code、path、name 由业务前端预填，杜绝拼错。

**调整点**：
- URL 参数协议化：约定 `?source={service_code}&perm_code=...&path=...&name=...&menu_type=MENU&parent_hint=...` 标准参数，admin 端解析后预填表单，管理员仅需补图标/排序/最终父目录。
- 安全：admin 端必须二次校验 `perm_code` 在 permission-center 真实存在 + 当前用户有 `ADMIN_MENU:CREATE` 权限。
- 不做"一键发布直接落库"：必须经管理员审核保存，避免业务前端任何人都能创菜单的安全洞。

**工期**：1 周（admin 前端解析路由参数 + 表单预填 0.5 周；example-service 前端按钮 + URL 拼接 0.5 周）。

### 6.2 想法 2：业务能力注册为菜单子权限

**结论：不采纳**。

**反例分析**：
- 若 `EXAMPLE_REPORT:APPROVE` 注册为菜单子权限，则**业务能力的生命周期被绑死在菜单上**：菜单删除会牵连业务能力，菜单移动父级会改变权限路径。
- 一个业务能力可能被多个菜单复用（如"导出"按钮在报表列表页和详情页都有），挂在哪个菜单下都不合适。
- 业务能力在没有菜单的场景（如批处理、API 直调、定时任务）会变成"孤儿权限"无法注册。
- 与"业务资源类型是 SSOT"的核心原则冲突——把 UI 层级（菜单）当成权限层级，本质是 v1.4 已抛弃的"页面/按钮形状的资源类型"反模式。

**替代方案（已在 v2 中）**：业务能力注册为业务资源类型的操作位（`EXAMPLE_REPORT:APPROVE`），菜单仅通过 `entryPermCodes` 引用入口业务码，按钮通过 `hasPerms` 直接读 permissions 数组。新增 `EXPORT` 操作只需业务侧 manifest 加一行，菜单完全无感。

### 6.3 想法 3：字段级权限（v3.1 修订 — v2 GA 简化版）

> ⚠️ **v3.1 修订（2026-06-18）**：9/9 全票 MOVE_TO_V2_1，本节整章重写。删除 IR-6.3-A/B/E（与 1:1 直接冲突或 IDOR 主路径已消失），保留并升级 IR-6.3-C/D 为 perm-sdk CI 契约硬约束，新增 IR-6.3-F 字段+行裁切铁律，重写 MenuVisibilityResolver 公式避免空白页。

#### 6.3.0 决策背景
用户拍板 1:1 约束（reportId↔menu）后，纳入 v2 GA 的两条核心论据（A1/A2 双菜单子集 + X-Menu-Id IDOR 主路径）被直接物理推翻。9 角色 R1 评审 9/9 全票 MOVE_TO_V2_1。本节为 v2 GA 阶段保留的最小字段级权限契约。

#### 6.3.1 v2 GA 保留的硬契约（不可变规则）

**IR-6.3-C（升级为 perm-sdk 启动期 CI 契约）**：禁止 query string 列开关（如 `?columns=finance`、`?fields=salary`）。业务侧 Controller 不得读取 query string 列开关参数；perm-sdk CI 静态扫描发现违反则拒合并。

**IR-6.3-D（升级为 v2 GA 强响应契约）**：DTO 序列化层缺权限字段必须返回 null + 响应顶层 `_suppressedFields` 数组枚举字段名。
```json
{
  "data": { "orderId": 1001, "customer": "X", "commission": null },
  "_suppressedFields": ["commission"],
  "_traceId": "..."
}
```
前端组件库统一渲染'此列因权限隐藏'灰条+tooltip；不同接入方表现一致（终端员工 HIGH 提出）。

**IR-6.3-F（v3.1 新增 — §2 权限查询铁律延伸）**：字段裁切+行范围裁切均必须由 OperationPermission 位驱动；业务侧在 SQL 拼装阶段读取 `PermQueryEngine.query()` 返回的 PermResult，禁止业务侧自推导（如禁止 `WHERE owner_id=currentUser` 土法 SCOPE_SELF）。违反 §2 权限查询铁律的 PR 模板要求 reviewer 拒绝。

**已删除规则**（与 1:1 约束冲突或 IDOR 主路径消失）：
- ❌ IR-6.3-A（同 path 不同列子集 = 不同 menu_id）
- ❌ IR-6.3-B（多业务码组合 entryPolicy=ALL）
- ❌ IR-6.3-E（menuId 后端复算 ALL）

#### 6.3.2 OperationPermission 字段级模型（v2 GA 落地）
example-service 在 OperationPermission 表为 reportId 注册标准化字段位作为参考实现样板：
```
VIEW_BASE              -- 查看基础列（订单号、客户、数量）
VIEW_COMMISSION        -- 查看佣金列（佣金率、佣金额）
VIEW_FINANCE_COLUMN    -- 查看财务列（成本、利润）
SCOPE_SELF             -- 仅查看本人数据
SCOPE_TEAM             -- 查看本团队数据
SCOPE_ALL              -- 查看全部数据
```

**MenuVisibilityResolver 公式重写**（BI HIGH 提出，避免 HR 进入空白页）：
```
MENU(业务) 节点可见性 := ADMIN_MENU:{menuId}:VIEW AND ∃ op∈{VIEW_*前缀操作位} 使 user 拥有 op
```
HR（无任何 VIEW_* 操作位）→ 菜单不渲染 + 后端 hasPermission 第一闸 403，避免'菜单存在但所有列被裁空'的空白页。

#### 6.3.3 perm-sdk helper（v2 GA 提供）
```java
// 业务侧 DTO 序列化层调用
boolean canView = permQueryEngine.canViewField(
    tenantId, userId, ResourceTypeCode.REPORT, reportId, OperationCode.VIEW_COMMISSION
);
if (!canView) {
    dto.setCommission(null);
    suppressedFields.add("commission");
}
```

#### 6.3.4 接入方落地示例（example-service）
参见 §0.5 example-service 销售业绩报表端到端样例，含单测覆盖 NEW-S1-1~3：
- NEW-S1-1：Sales (VIEW_BASE+SCOPE_SELF) 访问 → 返回 base 字段+本人订单，commission=null + `_suppressedFields=["commission"]`
- NEW-S1-2：Manager (VIEW_BASE+SCOPE_TEAM) 访问 → 返回 base 字段+本团队订单，无 commission
- NEW-S1-3：HR (无任何 VIEW_*) 菜单不渲染；URL 直跳 → 403 reasonCode=DENIED_BY_DOMAIN，CTA='此功能不在你的工作范围内'

#### 6.3.5 1:1 约束下字段级需求落地推荐路径（防 S2 反模式）
BI 报表声明 manifest 时必须列出字段集，并为敏感列（finance/commission/salary）单独声明操作码；DTO 序列化层默认拒绝、按操作位白名单放行；ScopeCode 走 `PermQueryEngine.queryScopes`，不走业务侧 if-else。

**禁止反模式**：业务方不得通过拆 reportId（如 REP_ORDER_BASE + REP_ORDER_FIN）实现字段差异；admin 端发布表单检测 displayName 前缀相似度+SQL 重叠 → MEDIUM 提示走 OperationCode 路径（详见 §15 new_safeguards 中 S2 防御）。

#### 6.3.6 v2.1 升级路径
- 落地 manifest field_descriptor schema（DRAFT 状态，可声明，运行时不强制消费）；
- CI 校验 DRAFT 状态超过 90 天自动升级提醒；
- 操作位套餐 UI（防爆炸，Manager MEDIUM 提出）：把 VIEW_BASE+SCOPE_SELF 打包为'查看本人数据'套餐，admin 授权页默认展示套餐，'高级'抽屉展示原子位；
- example-service 集成示例必须演示 S1 字段级裁剪路径，作为 v2.1 GA 验收门禁。

#### 6.3.7 v3 升级路径
`@PermFieldGuard` 注解 + manifest field_descriptor 自动化；v2.1 接入方零迁移成本（操作位语义保持一致），由权限中台保证向前兼容。

---

## 7. 修订决策记录（追加到 design-review §11）

| 日期 | 决策点 | 决策 | 备注 |
|---|---|---|---|
| 2026-06-17 | 菜单可见性公式 | 节点类型分治 + 双轨 AND | DIR 派生、MENU(业务)双轨、纯展示页显式 entryPolicy |
| 2026-06-17 | perm_code 单值/多值 | 物理单值 + extra.entryPermCodes 多值通道，固定 ALL | 不破坏 schema，与 hasPerms 数组语义对齐 |
| 2026-06-17 | auto-grant 默认行为 | 业务→菜单：manifest 显式声明则自动补 | 撤业务时条件级联清 AUTO_DEP，共享菜单受保护 |
| 2026-06-17 | 路由可访问性 | 等价于菜单可见性（同源） | 守卫读 meta.permCodes，meta.roles/auths 退场 |
| 2026-06-17 | 隐藏路由通道 | `/auth/user-menu` 新增 hiddenRoutes 字段 | 修复现状把 visible=false 直接剪掉的缺陷 |
| 2026-06-17 | BUTTON 类型 | v1.5 起 sys_menu 不再有 BUTTON 行 | 物理迁移脚本清空残留 |
| 2026-06-17 | 菜单注册流程 | 前端"发布菜单"半自动 UX（采纳想法 1 调整版） | URL 参数协议化，admin 二次校验 |
| 2026-06-17 | 字段级权限 | 移出 v2，v3 单独立项 | 复用 condition + manifest field_descriptor |
| 2026-06-17 | 业务能力注册形态 | 仍为业务资源类型操作位（不采纳想法 2） | 不与菜单耦合，复用性优先 |

## 7.x v3.1 修订（2026-06-18 — 1:1 约束拍板后）

### 用户决策
一个业务报表（reportId）有且只能有一个对应菜单（sys_menu）。

### 自动衍生约束
1. URL→菜单 是函数（同 path 不可能映射到多个 menuId）
2. 业务码→菜单 不再是多对多
3. 'A2 = 普通+财务列'原始用户场景不再成立

### 范式决议（9/9 R1 评审）
- P1 SIMPLIFY 9/9：恢复 (tenant_id, perm_code) 唯一索引，entryPermCodes 退化单值
- P2 SIMPLIFY 9/9：删除 IR-6.3-A/B/E；保留并升级 IR-6.3-C/D；新增 IR-6.3-F
- P3/P4/P5/P6 KEEP 9/9：与 1:1 完全正交
- 字段级权限 MOVE_TO_V2_1 9/9（全票一致）

### 配套新增（v2 GA 必含）
- §1.3.5 sys_menu_ref 引用机制（解 S3 跨业务线复用）
- report_metadata.source_report_id + split_reason（防 S2 拆 reportId 反模式）
- 月度 SQL 签名 hash 扫描脚本（运行时检测 S2 反模式）
- binlog 审计 + admin 端 displayName 前缀+SQL 重叠预警
- IR-6.3-D 升级为 _suppressedFields 强响应契约
- IR-6.3-F 字段+行裁切由 OperationPermission 位驱动铁律
- MenuVisibilityResolver 公式重写为 OR(VIEW_*前缀)

### 决策点重新拍板
- D6 字段级权限：从 v3 推荐 C（v2 GA 即纳入）→ MOVE_TO_V2_1（v2 GA 仅保留 IR-6.3-C/D 硬契约）
- D8 perm_code 多值通道：从 v3 推荐 B（JSONB 多值）→ A（单值 + 唯一索引），实质等价于回退到 v1 设计
- D9-2 后端 path 反查 menuId：从 P0 → P1 但保留实现（审计 trace 价值 + 前端透传攻击面收口）
- D9-4 P0-B 字段级权限项：移出 P0-B 给 v2.1
- D9-5 字段级 manifest field_descriptor DRAFT：v2 GA 不强制，v2.1 落地 schema 校验

---

## 8. 与已决策工作单的协同/冲突修订

**工作单 A（permission-center 快照模式）**：协同。`/auth/user-menu` 响应新增 `snapshotVersion` 字段，前端缓存比对；后端命中即 304；权限版本变化通过既有 `EVT_PERM_VERSION_CHANGED` 推送。

**工作单 D（resource_dependency 自动补全实现）**：本版强依赖。D 必须在 v2 GA 前实现 `PermissionGrantAppServiceImpl.grant()` 中的 AUTO_DEP 写入 + `revoke()` 中的条件级联清理。MVP 过渡期通过 §3.4 的软提醒 + seed 模板兜底。

**工作单 E-4（前端权限指令 v-perms / hasPerms 收敛）**：协同 + 加速。本版废弃 `meta.roles`/`meta.auths` 路径，路由守卫统一改为 `hasPerms(meta.permCodes)`，可与 E-4 合并为同一 PR。

**工作单 G（ADMIN_MENU 命名归一 / business_key 一致）**：本版作为 G 的实施抓手。前端"发布菜单"流程统一生成 `business_key=menu:{tenantId}:{path-hash}`，admin 端校验唯一性；存量数据由 G 的迁移脚本清洗，本版不重复实现。

**冲突修订**：
- 工作单 E-4 原计划保留 `meta.auths` 兼容期，本版**提前**至 v2 GA 即移除（改为 `meta.permCodes`），需要前端一次性扫所有路由配置；工期从 0.5 周追加到 1 周。
- 工作单 D 的 `auto_grant` 规则原计划 `maintain_source` 仅 `ADMIN_UI`，本版要求加入 `MANIFEST` 与 `MENU_PUBLISH_DERIVED` 两类来源，需扩展 `MaintainSource` 枚举与权限校验（仅可信服务可推 MANIFEST；admin 在 saveMenu 事务内派生 MENU_PUBLISH_DERIVED）。

### 8.1 工作单协同矩阵（v1 §6 详细版合并）

| 工作单 | 状态 | 说明 |
|---|---|---|
| **A**（Gateway 快照模式 / 删 permission_version） | ✅ 协同 + 增补 | 5 视角的"登录一次性预取 + 运行时不回查"完全验证 A 决策；增补点：`/auth/user-menu` 响应携带 `snapshotVersion` + `permFingerprint`（替代 permission_version 用途） |
| **B**（scopeMode 枚举 INSTANCE/ALL/NONE） | ✅ 协同 | 与 §0.5 数据范围模型完全兼容 |
| **D-2**（BusinessKeys 工具类） | ⚠️ 调整 | 增补"按 ResourceType 选业务键策略"契约：ADMIN_MENU=`sys_menu.id` 数字字符串；范围资源=`{type}:{code}` 字符串。工具类需暴露策略入口 |
| **D-3**（@AppliesTo 启动校验） | ✅ 协同 | 无冲突 |
| **D-4**（SyncHandler payload 版本声明） | ⚠️ 调整 | 边界澄清：明确 `service-config/sync` 是否纳入 `type_definition` + `operation_permission` 种子注册。如不纳入，单独立工作单 H |
| **E-4**（auto-grant 保留 TODO） | ❌ 冲突 → 调整 | 拆分为 E-4a（MVP 主线）+ E-4b-1（v2.1）+ E-4b-2（Phase X），见 §3 + §13 范式 6 |
| **F**（DTO 单源化 / ownership 单源 sync_metadata） | ✅ 协同 | 无冲突；提示 sync_metadata 未来可承载业务接入方元数据 |

**新增工作单**：
- **G（命名归一）**：MENU → ADMIN_MENU 跨 api-contract.md / core-flows.md / schema 三处刷新；business_key 从 `menu:{code}` 统一到 `sys_menu.id`
- **H（资源类型声明式注册，可选）**：取决于 D-4 边界澄清结果
- **I（多租户硬隔离落地）**：见 §10，v3 新增 P0 工作单

---

## 9. 给产品经理拍板的最终决策点（合并版 — 共 10 项）

> 包含 v1 原始 6 项决策（D1-D6）+ v3 新增 4 项决策（D7-D10）。已勾选项为最近一轮拍板结果，待勾选项为本轮 R2 后新增。

### D1. auto-grant 是否提前进主线？

| 候选 | 说明 |
|---|---|
| A. 维持 E-4 现状（保留 TODO，Phase X）| 主线进度可控，但接入方上线即面临"漏配工单风暴" |
| **B. 拆分 E-4a / E-4b，MVP 进主线** ⭐推荐 ✅已采纳 | E-4a 工期 1.5 周，复用现有表，风险低；E-4b-1 提前 v2.1（健康度只读巡检 + dry-run + 角色归档/重置）；E-4b-2 维持 Phase X |
| C. 全量 E-4 进主线 | 4-6 周，影响其他工作单排期 |

**推荐 B（已采纳）**。理由：5 视角一致 P0 + 现有空挂代码已铺路 + R1 9 角色独立验证。

### D2. example-service 业务定位是 BI 还是销售？

| 候选 | 说明 |
|---|---|
| A. 维持 BI（datasource/report/task），与现有 schema 一致 | 改动小，但与 5 视角中销售场景的提案脱节 |
| **B. 维持 BI，但用 1 业务类型 + 1 范围类型最小子集证明双轨** ⭐推荐 ✅已采纳 | 演示项目不需要"完整业务模板"，文档提示销售/订单同构 |
| C. 改为销售业务（Order/Customer）| 改 schema、改文档，工期翻倍，收益不大 |

**推荐 B（已采纳）**。

### D3. example-service 前端接入形态？

| 候选 | 说明 |
|---|---|
| **A. 同 SPA 子路由（admin 前端工程内 example/ 目录）** ⭐推荐 ✅已采纳 | 工期最短、登录态/路由/样式天然一致、双轨架构验证完整 |
| B. iframe 嵌入 | 演示"门户归集外部系统"场景，但登录态传递、跨域、调试痛点多 |
| C. 微前端 qiankun | 工期最长，对演示项目过度复杂 |

**推荐 A（已采纳）**。

### D4. 菜单注册协议：手工 vs manifest？

| 候选 | 说明 |
|---|---|
| A. 完全手工（admin 管理面 UI 录入）| 治理边界清晰，但接入方协调成本高 |
| **B. 手工兜底 + manifest 辅助导入工具 + 前端"发布菜单"半自动 UX** ⭐推荐 ✅已采纳 | example-service 仓库内放 menu-manifest.yaml；admin 提供 import-manifest 工具；前端"发布菜单"按钮预填 URL 参数（采纳用户想法 1）|
| C. 运行时 RPC 注册（example 启动反向 push）| 破坏 admin 治理边界，KISS 验证已否决 |

**推荐 B（已采纳）**。

### D5. perm-center 不可达时 SDK 行为？

| 候选 | 说明 |
|---|---|
| **A. fail-closed（拒绝所有请求）** ⭐推荐 ✅已采纳 | 安全优先，符合权限系统第一性原则 |
| B. fail-open（缓存兜底 + TTL 内放行）| 可用性优先，但越权风险高 |
| C. 配置开关（默认 closed，特定接口可声明 open） | 灵活但复杂，初期不必 |

**推荐 A（已采纳）**。

### D6. 字段级权限是否纳入路线图？（修订版 — v3 颠覆原决策）

| 候选 | 说明 |
|---|---|
| A. 不纳入，文档明确归业务 DTO 层 | v1 原推荐；R1 9 角色否决 |
| B. 纳入 Phase X 占位 | 给未来留空间，但易被误读为承诺 |
| **C. v2 GA 即纳入最小子集（多操作位 + entryPermCodes 多值 + DTO 白名单 + menuId 后端复算）** ⭐推荐（v3 修订） | R1 BI/产品/安全/架构/开发/租户 6 角色独立指出 S1 即需要；推迟产生 IDOR + 业务侧硬编码债 |

**推荐 C**（v3 颠覆 v1 推荐 A）。详见 §6.3 IR-6.3-A~E 五条铁律。

> ⚠️ **v3.1 重新拍板（2026-06-18）**：1:1 约束下 9/9 全票 **MOVE_TO_V2_1**。v2 GA 仅保留 IR-6.3-C（禁止 query string 列开关）+ IR-6.3-D（DTO 白名单 + `_suppressedFields` 强响应契约）作为 perm-sdk CI 契约硬约束。删除 IR-6.3-A/B/E，新增 IR-6.3-F。详见 §6.3 v3.1 简化版。

### D7. 菜单可见性公式选派（v3 新增）

- 候选 A：严格双满足派（节点类型分治 + AND）
- 候选 B：单源派生派（删 ADMIN_MENU）
- **候选 C：节点类型分治派（DIR 派生 + MENU 双轨）** ⭐推荐
- **理由**：A 太严格（DIR 也参与双轨过重），B 太激进（删 ADMIN_MENU 失去运营层菜单调控能力）；C 平衡安全与灵活，DIR 派生消除"空目录"问题，MENU 双轨满足审计合规，纯展示页显式声明杜绝隐式豁免。

### D8. perm_code 多值通道（v3 新增）

- 候选 A：保持单值，多值需求改为聚合权限码（业务侧承担成本）
- **候选 B：`extra.entryPermCodes` JSONB 多值，固定 ALL** ⭐推荐
- 候选 C：物理改 schema 为数组列
- **理由**：A 让业务侧每出现"两资源联动入口"就得新建一个聚合码长期形成"派生码"垃圾；C 破坏现有唯一索引与 1:1 映射协议；B 用 JSONB 扩展不破坏 schema，多值语义固定 ALL 与 `hasPerms` 数组语义对齐。

> ⚠️ **v3.1 重新拍板（2026-06-18）**：1:1 约束下 9/9 全票回退到候选 **A 单值 + 恢复 (tenant_id, perm_code) 唯一索引**。entryPermCodes JSONB 字段保留以避免破坏性 migration（向前兼容 v3+ 边角场景），但 SDK Builder 仅暴露 `.permCode(String)` 单值方法，运行时强制 `length<=1`，length>1 直接拒绝。实质等价于回退到 v1 设计。详见 §1.2 v3.1 修订版。

### D9. R2 评估后新增决策（待拍板）

| # | 决策 | 推荐 | 工期 |
|---|---|---|---|
| D9-1 | E-4b-1 **只读巡检 API**（GET /auto-dep-orphans）从 v2.1 提前到 v2 GA？ | **是**（合规答辩刚需） | +0.5 周 |
| D9-2 | menuId 来源：客户端 X-Menu-Id Header vs **后端 path 反查**？ | **后端 path 反查**（消除 IDOR 主路径，关联 IR-6.3-E） | +0.5 周 SDK |
| D9-3 | 敏感操作（EXPORT/DELETE）`@PermSensitive` 强制绕缓存直查 DB？ | **是**（HR 离岗合规红线，SLA ≤ 5 秒） | +0.5 周 |
| D9-4 | v2 GA 拆 **P0-A 安全/一致性底盘** + P0-B 接入体验 + feature flag 分租户灰度？ | **是**（17 个 P0 等权重无法 GA） | 工期重做 |
| D9-5 | 字段级"字段→操作位"映射 SSOT：业务硬编码 vs **manifest 声明 field_descriptor (DRAFT)**？ | **manifest DRAFT**（v3 启用 `@PermFieldGuard` 时业务零迁移） | 0（仅文档） |

> ⚠️ **v3.1 重新拍板（2026-06-18）**：
> - **D9-2 后端 path 反查 menuId**：从 P0 → P1，但**保留实现，不删除**。理由：(1) 仍是 audit_log 中 X-Perm-Trace-Id 唯一可信的 menuId 来源（前端传的不可信）；(2) 防御 1:1 约束被业务侧通过 path_alias 软绕过的回归路径；(3) 工程红线'禁止前端透传 menuId'必须有后端反查兜底。同时 Gateway / SDK 层显式拒绝 X-Menu-Id Header（HTTP 400 + reasonCode=DEPRECATED_HEADER）。
> - **D9-4 P0-B 字段级权限项**：移出 P0-B 给 v2.1（与 D6 重新拍板一致）。
> - **D9-5 manifest field_descriptor**：v2 GA 不强制运行时消费；v2.1 落地 schema 校验（DRAFT 状态超 90 天 CI 提醒）。

### D10. 用户想法 1（前端"发布菜单"）是否纳入 v2 GA

- 候选 A：纳入 v2 GA（1 周工期）⭐推荐
- 候选 B：v2 仅做后端，前端发布菜单延后
- **理由**：v2 GA 即将上线 example-service 集成，正是建立"业务前端发布菜单"协议的最佳时机；推迟会让首批集成的业务服务走"管理员手工配菜单"老路，沉淀错误习惯后再纠正成本更高。

---

## 10. 多租户硬隔离规范（v3 新增 — 解决 R2 P0-3）

> ⚠️ **v3 新增（C12）**：v1 §3.4 仅 4 行提及多租户隔离，v2 文档目录从 §9 跳到 §11 章节真空，IR-1.2-C 多次引用无锚点。本章补齐多租户硬隔离规范，v2 GA 不可推迟。

### 10.1 不可变规则（IR-10.x）

- **IR-10.1-A**：所有跨服务事件 / Redis pub/sub channel / 缓存 key / 数据库索引必须以 `tenant_id` 作为**第一前缀**或**必带参数**。无 tenant_id 维度的索引/广播视为 schema 缺陷，CI 拒合并。
- **IR-10.1-B**：所有 perm-sdk HTTP 调用（resource-sync、manifest 上传、权限查询、菜单发布）必须携带 `X-Tenant-Id` Header；服务侧严格校验 `X-Tenant-Id` = `body.tenantId`，不一致直接 400 + 审计。
- **IR-10.1-C**：跨租户写入（如 example-service 用 T1 凭证写 T2 资源）一律 403 + reason=TENANT_MISMATCH（关联 §12.4 reasonCode）+ 写入 audit_log + 触发 HIGH 告警。
- **IR-10.1-D**：JWT/Session 中 `tenant_id` 与 X-Tenant-Id 不一致时拒绝；用户切换租户必须重新登录，不接受 Session 内动态切租户。
- **IR-10.1-E**：HMAC 签名（X-Service-Sign）的密钥按租户隔离；T1 的服务密钥不能签 T2 的请求。
- **IR-10.1-F**：所有 PermQueryEngine 查询入口强制 tenant_id 参数，缺省即 NPE（fail-fast，禁止沉默回填默认租户）。

### 10.2 schema 维度强制约束

```sql
-- 所有权限相关表必须 tenant_id NOT NULL
ALTER TABLE sys_menu             ADD CONSTRAINT chk_tenant CHECK (tenant_id IS NOT NULL);
ALTER TABLE resource_entity      ADD CONSTRAINT chk_tenant CHECK (tenant_id IS NOT NULL);
ALTER TABLE operation_permission ADD CONSTRAINT chk_tenant CHECK (tenant_id IS NOT NULL);
ALTER TABLE role_resource_permission ADD CONSTRAINT chk_tenant CHECK (tenant_id IS NOT NULL);
ALTER TABLE resource_dependency  ADD CONSTRAINT chk_tenant CHECK (tenant_id IS NOT NULL);
ALTER TABLE perm_outbox          ADD CONSTRAINT chk_tenant CHECK (tenant_id IS NOT NULL);

-- 唯一索引必须以 tenant_id 起始
CREATE UNIQUE INDEX uk_sys_menu_tenant_path     ON sys_menu(tenant_id, path) WHERE path IS NOT NULL AND path <> '';
CREATE UNIQUE INDEX uk_resource_entity_tenant   ON resource_entity(tenant_id, resource_type_value, code);
CREATE UNIQUE INDEX uk_op_perm_tenant_type_code ON operation_permission(tenant_id, resource_type_value, code);
```

### 10.3 跨租户事件总线粒度

| Redis pub/sub channel | 命名格式 |
|---|---|
| 权限版本变更 | `perm:tenant:{tenantId}:version-changed` |
| 用户角色变更 | `perm:tenant:{tenantId}:user-role-changed:{userId}` |
| 资源软删 | `perm:tenant:{tenantId}:resource-deleted:{resourceTypeCode}` |
| Outbox 主 topic | `perm.events.tenant.{tenantId}` |

订阅端 ACK 时强制校验消息 payload 的 tenantId 与 channel 命名一致；不一致拒绝消费 + 告警。

### 10.4 租户级配额（关联 R2 P1）

```sql
CREATE TABLE perm_tenant_quota (
  tenant_id BIGINT PRIMARY KEY,
  max_roles INT NOT NULL DEFAULT 1000,
  max_menus_per_service INT NOT NULL DEFAULT 200,
  max_resource_entities BIGINT NOT NULL DEFAULT 1000000,
  max_role_resource_permissions BIGINT NOT NULL DEFAULT 5000000,
  outbox_lag_threshold_seconds INT NOT NULL DEFAULT 60,
  query_qps_per_user INT NOT NULL DEFAULT 100,
  updated_at DATETIME NOT NULL
);
```

PermissionGrantAppService、ResourceEntityWriteService 在写入前强制查 quota，超额 BizException(QUOTA_EXCEEDED)。

### 10.5 X-Tenant-Id 校验中间件（perm-sdk + Gateway 双重）

```java
// perm-sdk HTTP 客户端注入
@Component
public class TenantConsistencyInterceptor implements ClientHttpRequestInterceptor {
  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ...) {
    String headerTid = request.getHeaders().getFirst("X-Tenant-Id");
    Long bodyTid = JsonUtils.extractTenantId(body);
    if (!Objects.equals(Long.parseLong(headerTid), bodyTid)) {
      throw new SecurityException("TENANT_MISMATCH header=" + headerTid + " body=" + bodyTid);
    }
    return execution.execute(request, body);
  }
}

// 服务侧 @TenantGuard 切面
@Aspect
public class TenantGuardAspect {
  @Before("@annotation(TenantGuard)")
  public void check(JoinPoint jp) {
    Long jwtTid = SecurityContextHolder.getCurrentTenantId();
    Long headerTid = RequestContextHolder.getHeaderLong("X-Tenant-Id");
    Long bodyTid = ((TenantAware) jp.getArgs()[0]).getTenantId();
    if (!Objects.equals(jwtTid, headerTid) || !Objects.equals(headerTid, bodyTid)) {
      auditDomainService.recordSecurityEvent("TENANT_MISMATCH", jwtTid, headerTid, bodyTid);
      throw new SecurityException("TENANT_MISMATCH");
    }
  }
}
```

### 10.6 验收点（追加到 §5）

- AC-10.1-1：T1 用户的 JWT 调用 admin 修改 T2 的 sys_menu，返回 403 + reason=TENANT_MISMATCH，audit_log 落 SECURITY_EVENT。
- AC-10.1-2：example-service（T1）通过 perm-sdk 调 resource-sync 时 body.tenantId=T1 但 X-Tenant-Id=T2，拒绝 + HIGH 告警。
- AC-10.1-3：T1 与 T2 都有 path=/r/A1 + perm_code=EXAMPLE_REPORT:VIEW 的菜单，互不干扰；Redis 广播 perm:tenant:T1:version-changed 不会失效 T2 缓存。
- AC-10.1-4：T1 角色数量达到配额 1000，admin 创建第 1001 个角色返回 BizException(QUOTA_EXCEEDED)。
- AC-10.1-5：HMAC 签名服务密钥 T1.secret 签名后请求 T2 资源，返回 401 + reason=TENANT_SIGNATURE_MISMATCH。

---

## 11. 资源 / 菜单 / 权限码生命周期协议（v3 新增）

> ⚠️ **v3 新增（C4 + C8）**：v1/v2 仅设计 create 与最终 delete，中间所有迭代场景无协议。本章是 v2 GA 不可推迟的元协议，覆盖 perm_code、sys_menu、resource_entity（业务实例）三类对象的 create / update / rename / deprecate / delete 五态全生命周期。

### 11.1 perm_code 生命周期（operation_permission lifecycle）

#### 11.1.1 状态机

```
  +----------+   manifest 推送       +-----------+   deprecated_at 到期   +--------+
  | (未存在) | -------------------> |  ACTIVE   | --------------------> | RETIRED |
  +----------+                       +-----------+                       +--------+
                                          |
                                          | manifest 标记 deprecated
                                          v
                                    +-------------+
                                    | DEPRECATED  |  ←-- 双发期默认 30 天，可由租户管理员延长至 90 天
                                    +-------------+
```

#### 11.1.2 schema 扩展

```sql
ALTER TABLE operation_permission ADD COLUMN status TINYINT NOT NULL DEFAULT 1;
-- 1=ACTIVE, 2=DEPRECATED, 3=RETIRED
ALTER TABLE operation_permission ADD COLUMN deprecated_at DATETIME NULL;
ALTER TABLE operation_permission ADD COLUMN replaced_by_code VARCHAR(64) NULL;
ALTER TABLE operation_permission ADD COLUMN retire_after DATETIME NULL;
```

#### 11.1.3 重命名（rename）协议

1. example-service 在 manifest 中声明：`operation_permission(code=VIEW_FINANCE_COLUMN, aliases=[DATA_READ_FINANCE], deprecated_after=null)`。
2. SDK 推送时 permission-center 执行：
   - INSERT 新码 VIEW_FINANCE_COLUMN（status=ACTIVE）。
   - UPDATE 旧码 DATA_READ_FINANCE.status=DEPRECATED + replaced_by_code=VIEW_FINANCE_COLUMN + deprecated_at=NOW + retire_after=NOW+30d。
3. 双发期内 PermQueryEngine.hasPermission 对 DEPRECATED 码透明转发到 replaced_by_code 评估并写入 deprecated_usage_log。
4. admin 后台暴露 'deprecated 码使用看板'：每条 DEPRECATED 码列出剩余时长、当前持有此码的角色数、近 7 天调用量。
5. 提供 `perm-migrate-tool` CLI/UI（dry-run + 批量执行）：扫描 role_resource_permission + entryPermCodes，把所有引用旧码的行复制一份指向新码，由管理员人工确认后执行。
6. retire_after 到期后 status→RETIRED，PermQueryEngine 直接 deny 引用 RETIRED 码的请求。

#### 11.1.4 不可变规则

- IR-11.1-A：manifest 不允许直接 DELETE 已用过的 operation_permission，只能标记 DEPRECATED。
- IR-11.1-B：双发期最小 30 天、最大 90 天；提前 RETIRE 必须管理员显式确认。
- IR-11.1-C：CI 契约测试启动期扫描所有 entryPermCodes 引用的 perm_code 必须 status IN (ACTIVE, DEPRECATED)；引用 RETIRED 码即红。

### 11.2 sys_menu 生命周期

#### 11.2.1 操作矩阵（扩展 §6.1 前端发布菜单协议）

| 动作 | URL 协议 | admin 行为 | 触发事件 |
|---|---|---|---|
| CREATE | `?action=create&perm_code=&path=&name=&entry_perm_codes=...` | 插入 sys_menu + resource_entity + MENU_PUBLISH_DERIVED 依赖 | EVT_PERM_VERSION_CHANGED |
| UPDATE | `?action=update&menu_id=&new_path=&new_name=` | 二次审核 + diff 视图 + 写 path_alias 老路径 | EVT_PERM_VERSION_CHANGED |
| RENAME (perm_code) | `?action=rename&menu_id=&new_perm_code=` | 触发 perm-migrate-tool dry-run + 管理员确认 | EVT_PERM_VERSION_CHANGED |
| DEPRECATE | `?action=deprecate&menu_id=` | sys_menu.status=DEPRECATED（保留可见 false）+ MENU_PUBLISH_DERIVED 依赖删除 | EVT_PERM_VERSION_CHANGED |
| DELETE（硬删）| 仅 admin 后台手工执行 | 软删 sys_menu + 反向清理 AUTO_DEP | EVT_PERM_VERSION_CHANGED |

#### 11.2.2 path 兼容期

```sql
ALTER TABLE sys_menu ADD COLUMN path_aliases JSONB NOT NULL DEFAULT '[]';
-- UPDATE 时 admin 把旧 path push 进 path_aliases
-- 前端守卫识别 path_aliases 自动 302 到新 path（兼容期 60 天）
```

#### 11.2.3 僵尸菜单巡检（admin 反向订阅）

- admin 订阅 perm-center 的 `EVT_RESOURCE_DELETED` 事件，识别 sys_menu.entryPermCodes 中包含已被软删 resource_entity.code 的菜单行 → sys_menu.warning_flag='RESOURCE_DELETED' + 运维通知。
- 每日 cron：扫描 sys_menu.entryPermCodes 中码状态非 ACTIVE/DEPRECATED 的行 → warning_flag='STALE_PERM_CODE'。
- admin 后台菜单管理页对 warning_flag 不为空的菜单显示橙色徽章 + 一键 DEPRECATE。

### 11.3 resource_entity（业务实例）同步协议

#### 11.3.1 写入路径（解决 C8）

```java
// example-service 业务侧（同事务）
@Transactional
public Long createReport(Long tenantId, ReportCreateReq req, Long operatorId) {
  Long reportId = demoReportMapper.insert(...);
  // 同事务通过 perm-sdk 推送 resource_entity
  permClient.upsertResourceEntity(
    ResourceSyncReq.builder()
      .tenantId(tenantId)            // 强制 tenant_id
      .resourceTypeCode("EXAMPLE_REPORT")
      .code(req.getReportKey())      // 业务键
      .displayName(req.getName())
      .deletedFlag(0)
      .build());
  return reportId;
}

@Transactional
public void deleteReport(Long tenantId, Long reportId, Long operatorId) {
  demoReportMapper.softDelete(reportId);
  permClient.softDeleteResourceEntity(tenantId, "EXAMPLE_REPORT", req.getReportKey());
}
```

#### 11.3.2 SDK HTTP 协议

```
POST /api/perm/resource-entity/upsert
Headers: X-Tenant-Id: {tid}, X-Service-Sign: HMAC(...)
Body: { tenantId, resourceTypeCode, code, displayName, deletedFlag, businessKey }
```

服务侧强制校验：(1) X-Tenant-Id 与 body.tenantId 一致；(2) X-Service-Sign HMAC；(3) 拒绝跨租户写入；(4) 失败重试 + outbox 兜底。

#### 11.3.3 软删语义统一（解决 C5 配套）

- IR-11.3-A：`resource_entity.deleted_flag=1` 时 `PermQueryEngine.hasPermission` 返回 `PermResult{allowed=false, reason=RESOURCE_DELETED}`，**不返回 not-found**。
- IR-11.3-B：HTTP 错误码映射（关联 §12 reasonCode 协议）：DENIED→403, RESOURCE_DELETED→410 Gone, RESOURCE_NOT_REGISTERED→404, CONDITION_EXPIRED→403 with reasonCode, USER_ROLE_REVOKED→403, CACHE_STALE→409 Conflict（强制重拉）。
- IR-11.3-C：把 v1 gap #9 优先级从 P2 升到 P0，纳入 v2 GA。

**验收点（追加到 §5）**：
- AC-11.1-1：Alice 推送 manifest 重命名 perm_code，permission-center 自动落入 DEPRECATED 状态，30 天内 PermQueryEngine 透明转发，admin 看板可见 deprecated 用量。
- AC-11.1-2：retire_after 到期后引用旧码的请求返回 deny + reason=PERM_CODE_RETIRED。
- AC-11.2-1：Alice URL 协议升级（reportId→reportKey）调用 `?action=update`，admin 显示 path diff，确认后 sys_menu.path 更新且 path_aliases 保留旧 path 60 天。
- AC-11.2-2：example-service 软删 reportId=B，admin 通过订阅 EVT_RESOURCE_DELETED 自动给 B 菜单打 warning_flag=RESOURCE_DELETED，运维看板可见。
- AC-11.3-1：example-service 跨租户尝试写 resource_entity（X-Tenant-Id 与 body 不符），permission-center 拒绝并审计。

---

## 12. 跨服务一致性总线 + 缓存降级矩阵（v3 新增）

> ⚠️ **v3 新增（C5 + C7 + C11）**：v2 §2.4 仅一句 'WebSocket/SSE 推送即重拉' 不足以覆盖 Redis 故障 / SSE 断连 / 跨进程缓存场景。本章定义事件总线、限时到期联动、前端一致性、reasonCode、降级矩阵、SLA。

### 12.1 事件总线架构

#### 12.1.1 Outbox 模式（强一致）

所有跨服务可见状态变更（user_role、role_resource_permission、resource_entity、sys_menu、operation_permission）必须在写入业务表的**同一事务**内 INSERT 一条 outbox 记录：

```sql
CREATE TABLE perm_outbox (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,         -- EVT_PERM_VERSION_CHANGED / EVT_USER_ROLE_CHANGED / EVT_RESOURCE_DELETED / EVT_PERM_CODE_DEPRECATED
  aggregate_id BIGINT NOT NULL,
  payload JSONB NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,        -- 0=PENDING, 1=PUBLISHED, 2=FAILED
  created_at DATETIME NOT NULL,
  published_at DATETIME NULL,
  retry_count INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_pending ON perm_outbox(status, created_at) WHERE status=0;
```

后台 worker 每秒扫描 status=PENDING 行 → 发布到 Kafka/Redis Stream → 标记 PUBLISHED。订阅端幂等消费 + 失败 fail-closed。

#### 12.1.2 标准事件 schema

| 事件名 | 触发源 | 订阅方 | 必填字段 |
|---|---|---|---|
| `EVT_PERM_VERSION_CHANGED` | role_resource_permission / condition / sys_menu 写入 | perm-center 缓存订阅 + 前端 SSE | tenantId, version, affectedRoleIds[], affectedUserIds[] |
| `EVT_USER_ROLE_CHANGED` | admin 写 user_role | perm-center 缓存订阅 + 前端 | tenantId, userId, addedRoleIds[], removedRoleIds[] |
| `EVT_RESOURCE_DELETED` | example-service 软删 resource_entity | admin（菜单僵尸标记）| tenantId, resourceTypeCode, code |
| `EVT_PERM_CODE_DEPRECATED` | manifest 推送码状态变更 | admin（migration 提示）+ 前端 deprecation 横幅 | tenantId, oldCode, newCode, retireAfter |

#### 12.1.3 跨进程 evictAfterCommit 契约

```java
// admin 写 user_role 后
@Transactional
public void grantUserRole(Long tenantId, Long userId, Long roleId) {
  userRoleMapper.insert(...);
  outboxMapper.insert(EVT_USER_ROLE_CHANGED, payload);
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
      // 1. 同进程缓存失效
      cacheService.evict(PermCacheCatalog.USER_ROLE, tenantId, userId);
      // 2. outbox worker 异步发布事件，perm-center 订阅后失效自身 L1+L2
    }
  });
}
```

**IR-12.1-A**：所有 user_role / role_resource_permission / condition_definition 写入必须配对 outbox 记录，否则跨服务一致性破坏。

### 12.2 限时权限到期联动（解决 C6）

#### 12.2.1 condition_expiry_scheduler

```java
// permission-center 后台任务，每分钟运行
@Scheduled(cron = "0 * * * * *")
public void expireConditions() {
  List<ConditionDefinition> expiring = conditionMapper.findExpiringWithin(60); // 60 秒内到期
  for (ConditionDefinition c : expiring) {
    permissionVersionDomainService.incrementAndBroadcast(c.getTenantId(), c.getAffectedRoleIds());
    outboxMapper.insert(EVT_PERM_VERSION_CHANGED, ...);
  }
}
```

#### 12.2.2 /auth/user-menu 响应附 condition 元数据

```json
{
  "snapshotVersion": "v20260617-007",
  "effectiveUntil": "2026-10-07T23:00:00Z",
  "menus": [...],
  "permissions": [
    { "code": "EXAMPLE_REPORT:VIEW" },
    { "code": "EXAMPLE_REPORT:EXPORT", "expiresAt": "2026-10-07T23:00:00Z", "scopes": ["P-001","P-002"] }
  ]
}
```

前端 store：(1) 接到 effectiveUntil 后定时器在到期前 60 秒主动重拉；(2) hasPerms 对带 expiresAt 的码做客户端时间检测，过期视为未授；(3) 兼容老调用：permissions 元素若无 expiresAt 字段按字符串处理。

#### 12.2.3 流式接口中途切断

```java
// 长连接（导出/SSE）每 30 秒回查
PermResult check = permQueryEngine.hasPermission(tenantId, userId, type, code, op);
if (!check.isAllowed() && check.getReason() == CONDITION_EXPIRED) {
  outputStream.write(buildTrailerFrame("PERM_REVOKED", "权限已到期"));
  outputStream.close();
  auditDomainService.recordChangeLog("mid-stream-revoked", ...);
}
```

SDK 提供 `@PermStreamCheck(intervalSec = 30)` 注解封装。

### 12.3 前端 store 一致性保证

| 项目 | 规则 |
|---|---|
| 强制 TTL 上限 | menus / permissions 缓存 max-age 必须 ≤ 120 秒，超时强制重拉，**不依赖** SSE 推送 |
| 心跳兜底 | 每 60 秒 HEAD `/auth/user-menu/version` 比对 snapshotVersion，差异即重拉 |
| SSE 断连阈值 | 连续断连 ≥ 30 秒切换到 polling 模式 + UI 横幅 '权限同步中' |
| 多 tab 同步 | BroadcastChannel 同步 snapshotVersion；任一 tab 收到 EVT_PERM_VERSION_CHANGED 触发全局软刷（仅替换 store，不卸载路由）|
| Token refresh | refresh 后比对 permFingerprint，不一致才提示，不整页跳登录 |
| 上下文保留 | 软刷不卸载组件，使用 keep-alive 路由 + 本地草稿 store；表单已填数据保留 |

### 12.4 标准 reasonCode 协议（解决 C11）

#### 12.4.1 PermResult.reason 枚举

```java
public enum PermDenyReason {
  DENIED,                  // 普通无权
  RESOURCE_DELETED,        // 资源已软删（HTTP 410）
  RESOURCE_NOT_REGISTERED, // 资源未注册（HTTP 404）
  CONDITION_EXPIRED,       // condition.dateRange 到期
  CONDITION_OUT_OF_SCOPE,  // scope 不命中
  USER_ROLE_REVOKED,       // user_role 已撤销（缓存窗口期）
  CACHE_STALE,             // snapshotVersion 不一致（HTTP 409，前端强制重拉）
  PERM_CODE_RETIRED,       // 引用 RETIRED 码
  TENANT_MISMATCH,         // 跨租户访问
  MENU_ENTRY_DENIED,       // entryPermCodes ALL 校验失败
  FAIL_CLOSED              // perm-center 不可达
}
```

#### 12.4.2 HTTP 响应头

```
X-Perm-Deny-Reason: CONDITION_EXPIRED
X-Perm-Snapshot-Version: v20260617-007
X-Perm-Trace-Id: <traceId>
X-Perm-Expired-At: 2026-10-07T23:00:00Z
```

#### 12.4.3 前端 /error/403 文案模板

```typescript
const REASON_TEMPLATES = {
  DENIED: { title: '无权访问', cta: '申请权限' },
  RESOURCE_DELETED: { title: '该资源已下线', cta: '联系管理员' },
  CONDITION_EXPIRED: { title: '您的临时权限已到期', cta: '联系审批人续期', extra: 'X-Perm-Expired-At' },
  USER_ROLE_REVOKED: { title: '您的角色已变更', cta: '刷新页面' },
  CACHE_STALE: { title: '权限已更新', cta: '正在重新加载...', autoRetry: true },
  PERM_CODE_RETIRED: { title: '此功能已下线', cta: '联系管理员' },
  MENU_ENTRY_DENIED: { title: '无法进入此页面', cta: '返回首页' }
};
```

### 12.5 降级矩阵

| 故障源 | 检测方式 | SDK / 组件行为 | 越权窗口 |
|---|---|---|---|
| perm-center 不可达 | RPC timeout > 3s | fail-closed：拒绝所有鉴权请求；admin 后台进入只读模式 | 0（彻底拒绝）|
| Redis pub/sub 失败 | publish 失败 / 副本异常 | 切换到 outbox + polling；前端心跳兜底 | ≤ 60 秒（兜底间隔）|
| SSE 断连 ≥ 30s | 心跳超时 | 前端切换 polling + 横幅提示 | ≤ 60 秒 |
| admin 不可达 | RPC timeout | example-service 走快照 + 拒绝授权变更类操作 | 0（短期可用）|
| HR 同步队列堆积 | lag > 阈值 | 监控告警 + 关键操作（EXPORT/DELETE）入口禁用快照、强制实时校验 | 见关键操作策略 |

### 12.6 SLA 指标（写入文档作为强制 SLO）

| 指标 | 阈值 |
|---|---|
| 离职/换岗生效到全链路 fail-closed | ≤ 5 秒（关键操作入口）/ ≤ 60 秒（普通菜单展示）|
| condition.dateRange 到期联动 | ≤ 60 秒 |
| 跨服务事件 lag p99 | ≤ 30 秒 |
| 前端 snapshotVersion stale age p99 | ≤ 120 秒 |
| Redis 广播失败补偿 RTO | ≤ 60 秒（outbox worker）|

**验收点（追加到 §5）**：
- AC-12.1-1：admin 改 user_role 后 perm-center USER_ROLE 缓存在 5 秒内失效，Mia 在 00:00:30 调 EXPORT 直接 deny + reason=USER_ROLE_REVOKED。
- AC-12.2-1：Aria 的 dateRange 在 23:00 到期，permission-center 在 60 秒内自增 permission_version 并广播，前端 menus/permissions 收缩，导出按钮消失。
- AC-12.2-2：Aria 在 22:59:30 启动流式 EXPORT，23:00 整 SDK 中途下发 PERM_REVOKED trailer 优雅关闭流，审计日志含 mid-stream-revoked。
- AC-12.3-1：前端 store 在 SSE 断连 30 秒后切换 polling，UI 横幅显示'权限同步中'。
- AC-12.4-1：Mia 点 B 菜单（已软删），后端返回 HTTP 410 + X-Perm-Deny-Reason=RESOURCE_DELETED，前端展示'该资源已下线'。

---

## 13. v3 paradigm_shifts 引用（2026-06-17）

> 6 大范式切换的 from→to 全文，便于落地代码评审时追溯：

1. **唯一索引范式**：`sys_menu.perm_code` 物理唯一索引 + 与 resource_entity.code 1:1 映射 → perm_code 仅作 entryPermCodes[0] 冗余视图（无唯一约束）；entryPermCodes JSONB 为权威多值通道；resource_entity(ADMIN_MENU).code = sys_menu.id；菜单唯一性靠 (tenant_id, path) 复合索引。
2. **字段级权限范式**：完全推迟到 v3 → MVP 即纳入最小子集（多操作位 + entryPermCodes 多值 + DTO 白名单 + menuId 上下文复算 + 禁止 query string 列开关）；schema 预留 field_descriptor。
3. **依赖规则 SSOT 范式**：manifest 启动期一次性声明所有依赖（含菜单依赖）→ 业务→业务依赖 SSOT 在 manifest（启动期）；业务→菜单依赖 SSOT 在 admin（保存菜单事务内派生 MENU_PUBLISH_DERIVED 行）。
4. **前端 permissions 范式**：字符串数组 string[] → 对象数组 [{code, expiresAt?, scopes?}]；hasPerms 函数支持过期时间客户端检测；保持向后兼容（旧字符串元素继续按 string 处理）。
5. **缓存一致性范式**：'WebSocket/SSE 推送即重拉' + evictAfterCommit（同进程内）→ Outbox + Kafka/Redis Stream 持久化事件总线 + 前端 TTL 上限（≤120s）+ 心跳 polling 兜底（60s）+ BroadcastChannel 多 tab 同步 + 标准化 reasonCode 协议（10 类枚举 + HTTP 错误码映射 + X-Perm-* 响应头）。
6. **AUTO_DEP 工具范式**：E-4b 整体推迟到 Phase X → E-4b-1（健康度只读巡检 + 手动撤销 dry-run + 角色归档/重置）提前到 v2.1；E-4b-2（依赖规则变更级联清理 + 批量回填）保留 Phase X。

### 13.1 v3.2 范式增量（2026-06-18 — 菜单元数据化业务能力清单）

7. **perm_code 二段式范式**：v3.1 隐含三段式 `{type}:{code}:{op}` → 显式二段式 `{type}:{code}`；`primary_operation` 与 `operations` 集合升级为独立 schema 字段；`extra.entryPermCodes` DEPRECATED；菜单可见性公式从"双轨 AND 多业务码"简化为"双轨 AND 单 primary_op"。
8. **业务能力清单元数据化**：操作位声明从分散在前端代码的 `<Perms>` 标签集中到 `sys_menu.operations` JSONB；admin 授权面板由"先选菜单再切换到角色管理勾业务码"改为"选菜单一站式授权（含套餐/dry-run/数据范围）"；前端按钮渲染由"开发者硬编码 perm code"改为"遍历 useUserStore.menus[X].operations"。
9. **MENU_PUBLISH_DERIVED 派生 N 条**：saveMenu 事务从派生 1 条依赖变为派生 N 条（每个 operations 元素一条），授任一业务码即触发 auto-grant 菜单可见；撤完所有 op 才反向清理（共享场景受保护）。
10. **入口操作位安全分桶**：`primary_operation` 必须 `sensitivity_level=PUBLIC`（保存校验），关闭 R2 P0-2 安全洞（VIEW_COMMISSION 类敏感位被设为入口导致"看不到佣金权也能进菜单"）。

> **未决悬挂**：D-3（manifest 新增能力时是否自动加入已发菜单 operations）默认"不自动 + admin MEDIUM 告警"，最终行为待下一轮 manifest 讨论拍板。

### 13.2 v3.3 范式增量（2026-06-18 — 去 manifest 化 + 字典中心化）

11. **manifest 物理消失范式**：业务服务对权限中心从"启动期 push manifest + 运行时调用"双通道 → **零启动耦合 + 仅运行时查询**。perm-manifest.yaml 不再存在，业务服务启动时不推任何东西，type_definition / OperationPermission 字典完全由 admin 后台 UI 维护。
12. **依赖关系位运算化范式**：v3.2 隐含的 OperationPermission `requires` 字段 / manifest dependencies block / `MANIFEST` 来源的 resource_dependency 行 → 全部废除。**业务→业务依赖完全靠 effective_bits 位运算 + `OperationPermissionUtils.covers()`**；auto-grant 唯一职责退化为"跨资源类型的菜单联动"（业务码 → ADMIN_MENU:VIEW）。
13. **更新发布无审核范式**（D-E）：菜单首次发布需 admin 二次审核（补图标/父目录/排序）；之后 Alice 自助保存 sys_menu.operations 变更，仅写 audit log，**不阻断业务节奏**。代价是敏感位（sensitivity=SENSITIVE）变更存在合规风险，需要 G1-G3 护栏（治理组通知 + binlog 审计 + 权责分离）作为可选事后兜底。
14. **字典 admin 中心化范式**：操作位字典维护权 = 中央治理组（持有 `OPERATION_DICT:MANAGE`）；业务方上线新资源类型/操作位前必须先与治理组协作在 admin 后台创建。换取的收益：跨业务统一字典 + 命名规范可控 + 敏感等级权威 + 操作位复用最大化。

> **D-3 关闭**：随 manifest 概念消失，"manifest 新增能力是否自动同步菜单"问题不复存在。新能力上字典后由 Alice 主动走"更新发布"流程加入相关菜单。

### 13.3 v3.1 范式回退记录（2026-06-18 — 9 角色 R1 评审 9/9 一致投票）

| 范式 ID | 名称 | v3.1 最终 verdict | 9 角色投票汇总 |
|---|---|---|---|
| P1 | 唯一索引 / perm_code 多值通道 | **SIMPLIFY** 9/9 | BI/Manager/终端员工/PM/架构师/开发/安全/运维/租户管理员均明确支持恢复 (tenant_id, perm_code) 唯一索引、entryPermCodes 退化为强制单元素。安全审计员特别强调：物理唯一索引 > 应用层校验 > 文档约束，是 1:1 在 schema 层的硬性兜底。 |
| P2 | 字段级权限 MVP 子集 | **SIMPLIFY** 9/9 | 全部 9 角色明确反对完全降级为'业务侧最佳实践'。安全审计员/PM/租户管理员/BI/终端员工特别强调：必须保留 IR-6.3-C（禁止 query string 列开关）+ IR-6.3-D（DTO 白名单按操作位）作为 v2 GA 的权限中心契约硬约束（违反 CI 拒合并），不能下放。删除 IR-6.3-A/B/E（与 1:1 直接冲突或 IDOR 主路径已消失），新增 IR-6.3-F（字段+行裁切由 OperationPermission 位驱动）。 |
| P3 | 依赖规则 SSOT 分两类（MANIFEST / MENU_PUBLISH_DERIVED）| **KEEP** 9/9（v3.3 后实质退化为单类 MENU_PUBLISH_DERIVED） | 与 1:1 完全正交，分类是 SSOT 解耦关键。开发特别强调：合并两类会让 manifest 被迫声明 ADMIN_MENU:{menuId}:VIEW 内部派生码，违反封装。1:1 反而让 MENU_PUBLISH_DERIVED 派生更干净（一对一，无歧义）。**v3.3 后**：MANIFEST 来源因位运算化被 [DROPPED]，仅保留 MENU_PUBLISH_DERIVED + ADMIN_UI。 |
| P4 | 前端 permissions 对象数组（{code, expiresAt?, scopes?}）| **KEEP** 9/9 | 与 1:1 完全正交。终端员工/开发/Manager 特别强调：limit 时到期检测、scope 资源 ID 列表是真实业务诉求，简化会让客户端 30 分钟到期提示横幅消失，触发突然 403 的糟糕体验。 |
| P5 | 缓存一致性总线（Outbox + Kafka/Redis Stream + reasonCode + 降级矩阵）| **KEEP** 9/9（架构师 STRONG_KEEP）| 与 1:1 完全正交，是跨服务一致性最后防线。安全审计员/运维 SRE 强调：reasonCode 协议是排障主要 trace 字段。MENU_ENTRY_DENIED 标记为 deprecated 倾向；新增 DENIED_BY_DOMAIN + DEPRECATED_HEADER；新增 channel `menu-ref-changed`。事务边界 evictAfterCommit 模式不可取消（数据正确性核心）。 |
| P6 | AUTO_DEP 巡检 E-4b-1 提前到 v2 GA | **KEEP** 9/9 | HR 离岗合规、孤儿 AUTO_DEP 清理、dry-run 是独立 P0 安全合规议题，与 1:1 完全无关。1:1 反而让 AUTO_DEP 链路更清晰。Manager/租户管理员/安全审计员把这条作为合规红线。 |

**字段级权限最终决议**：**MOVE_TO_V2_1**（9/9 全票一致，全员 stance.recommendation=MOVE_TO_V2_1）。v2 GA 期业务侧过渡协议详见 §6.3 v3.1 简化版。

---

## 17. 操作位字典治理（v3.3 新增）

> ⚠️ **v3.3 核心新增**：取代原 manifest dependencies + manifest operations 声明。OperationPermission 表升级为 admin 后台维护的"全局字典"，所有业务服务的操作位定义集中于此。

### 17.1 字典 schema

```sql
operation_permission 表（关键字段）：
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,                       -- IR-10 租户隔离
  resource_type_value INT NOT NULL,                -- 关联 type_definition
  code VARCHAR(64) NOT NULL,                       -- 如 VIEW / EXPORT / VIEW_COMMISSION
  display_name VARCHAR(128) NOT NULL,              -- 如"导出 Excel"
  description TEXT,                                -- 长描述（治理审计用）
  bit_value BIGINT NOT NULL,                       -- 位掩码（单一 bit）
  effective_bits BIGINT NOT NULL,                  -- 有效位（含包含关系，effectiveBits |= bit_value | parent.effective_bits）
  sensitivity_level VARCHAR(16) NOT NULL,          -- PUBLIC / RESTRICTED / SENSITIVE
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE / DEPRECATED / RETIRED
  deprecated_at DATETIME NULL,
  retire_after DATETIME NULL,
  replaced_by_code VARCHAR(64) NULL,
  created_by BIGINT NOT NULL,                      -- 治理组操作员
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_op_tenant_type_code (tenant_id, resource_type_value, code)
```

### 17.2 维护权限

- `OPERATION_DICT:MANAGE`：增删改字典行（仅授给中央治理组）
- `OPERATION_DICT:VIEW`：仅查看字典（业务方读，发菜单时选用）
- 业务方**没有**直接写权限；新增操作位走治理工单流程

### 17.3 admin 后台 UI 关键功能

| 功能 | 说明 |
|---|---|
| 字典浏览 | 按 resource_type 分组展示，支持模糊搜索（防止重复创建） |
| 新增操作位 | 表单：code（正则校验 `[A-Z][A-Z0-9_]*`）+ display_name + sensitivity + 父操作位（自动算 effective_bits） |
| 复用提示 | 创建时按 code 模糊匹配已有项，提示"是否复用？" |
| effective_bits 可视化 | 展示位继承关系树（VIEW → EXPORT 包含 VIEW → ADMIN 包含全部） |
| sensitivity 升级流程 | 把 PUBLIC 升级为 SENSITIVE 是危险变更，需二人审核 |
| 弃用流程 | 标记 DEPRECATED + 设置 retire_after（30-90 天双发期） |
| 跨菜单影响视图 | 选中某操作位 → 显示哪些 sys_menu.operations 引用它 |
| 跨角色影响视图 | 选中某操作位 → 显示持有此码的角色清单（弃用前必看） |

### 17.4 命名规范

- **通用操作位**：`VIEW` / `EDIT` / `EXPORT` / `APPROVE` / `DELETE`（无前缀，全资源类型可用）
- **字段级敏感位**：`VIEW_<COLUMN_NAME>_COLUMN`（如 `VIEW_COMMISSION_COLUMN` / `VIEW_SALARY_COLUMN`），sensitivity=SENSITIVE
- **格式变体位**：`EXPORT_<FORMAT>`（如 `EXPORT_PDF` / `EXPORT_CSV`），sensitivity 同基础 EXPORT
- **管理位**：`<RESOURCE>_MANAGE` / `<RESOURCE>_AUDIT`（如 `OPERATION_DICT_MANAGE`），sensitivity=RESTRICTED
- **禁止**：业务方任意命名（如 `MY_CUSTOM_OP_001`）→ admin 后台正则拒绝

### 17.5 与位运算的协同

```java
// 字典定义阶段：admin 配置 effective_bits
// VIEW.effective_bits           = 0b00001 (bit 0)
// EXPORT.effective_bits         = 0b00011 (bit 1 OR VIEW.effective_bits)
// EXPORT_PDF.effective_bits     = 0b10011 (bit 4 OR EXPORT.effective_bits)

// 鉴权阶段：PermQueryEngine 直接用位运算判断
long granted = OperationPermissionUtils.effectiveBits(role授权的操作位);
long target  = OperationPermissionUtils.effectiveBits(此次请求需要的操作位);
boolean ok   = OperationPermissionUtils.covers(granted, target);
// 不需要查 resource_dependency 表，不需要 auto-grant 补依赖位
```

### 17.6 与"更新发布"的协同（D-E 不审核 + G1 通知）

Alice 在更新发布表单加 sensitivity=SENSITIVE 操作位时：
- **保存仍然成功**（D-E 决策：不阻断）
- **同步触发 INFO 通知**到治理组消息队列：`menu={menu_id} added SENSITIVE op {op_code} by {alice}`
- 治理组事后看到通知能介入（撤回、复审、找业务方约谈）
- 此机制不改变 D-E 自治原则，仅提供事后可见性

---

## 18. 资源类型骨架治理（v3.3 新增）

> ⚠️ **v3.3 核心新增**：取代原业务服务启动期 push 的 manifest service registration。type_definition 表完全由 admin 后台维护。

### 18.1 schema

```sql
type_definition 表（关键字段）：
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,                       -- 如 EXAMPLE_REPORT
  display_name VARCHAR(128) NOT NULL,
  kind VARCHAR(16) NOT NULL,                       -- TYPED / SCOPE
  business_key_format VARCHAR(128),                -- 如 "<reportId>"
  service_code VARCHAR(64) NOT NULL,               -- 业务服务标识（仅展示用，无强校验）
  type_value INT NOT NULL,                         -- 内部数字编号（位运算/索引用）
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_td_tenant_code (tenant_id, code)
```

### 18.2 业务上线协作流程

```
业务团队规划新资源类型 EXAMPLE_REPORT
   ↓
业务团队 → 治理组 (工单/邮件，附 schema 设计文档)
   ↓
治理组 admin 后台:
  ① "资源类型管理" → 添加 EXAMPLE_REPORT (kind=TYPED)
  ② "操作位字典" → 添加 VIEW / EXPORT / VIEW_COMMISSION_COLUMN
  ③ 配置 effective_bits 位继承
  ④ 设置 sensitivity_level
   ↓
业务团队上线 example-service（不推任何配置，运行期通过 PermQueryEngine 调用即可）
```

### 18.3 与 sys_menu / ResourceEntity 的关系

| 概念 | 维护人 | 时机 | 例子 |
|---|---|---|---|
| **type_definition**（资源类型骨架） | admin 治理组 | 业务上线前一次性配置 | "EXAMPLE_REPORT 类型存在，TYPED" |
| **OperationPermission**（操作位字典） | admin 治理组 | 业务上线前 + 后续按需扩充 | "EXAMPLE_REPORT 下有 VIEW/EXPORT/..." |
| **ResourceEntity**（具体实例） | 业务服务运行时推送 | 报表创建/删除时 | "今天新增了 reportId=sales-001" |
| **sys_menu.operations**（菜单能力声明） | Alice 发菜单时填 | 每菜单独立 | "销售业绩菜单选 VIEW + EXPORT + VIEW_COMMISSION_COLUMN" |

四张表的责任分明：admin 管类型与字典，业务管实例，Alice 管菜单能力组合。

### 18.4 验收点

- **AC-17.1**：业务服务（example-service）启动时不向 perm-center 推送任何配置，仅通过 PermQueryEngine 查询接口调用。
- **AC-17.2**：admin 后台无 OperationPermission 数据时业务服务调用 hasPermission 全部 fail-closed（保留 v3.1 默认行为）。
- **AC-17.3**：Alice 在 admin 后台菜单发布表单中无法选择 status=DEPRECATED 的操作位（前端置灰 + 后端校验）。
- **AC-17.4**：admin 治理组添加 sensitivity=SENSITIVE 操作位需二人审核（如 G3 配套护栏启用）。

---

## 14. gap 清单（v1 §7 合并 + v3 修订）

> ⚠️ **v3 修订（C5/C8/C12 衍生）**：原 gap #5/#9 优先级被低估；新增 gap #10–#17 覆盖多租户硬隔离、manifest schema、perm code lifecycle、字段级 MVP、限时到期联动、AUTO_DEP 巡检、告警分级、reasonCode 协议——全为 v2 GA 不可推迟项。R2 评估又新增 gap #18–#21。

| # | gap 名称 | 原优先级 | v3 修订 | R2 后修订 | 工期 | 关联 critical_issues |
|---|---|---|---|---|---|---|
| 1 | perm-sdk Java 实现 | P0 | P0 | P0 | 2 周 | — |
| 2 | example-service 实例资源同步 SDK | — | **P0** | P0 | 1 周 | C8 |
| 3 | menu-manifest（含 §11.2 完整 CRUD 协议）| P1 | **P0** | P0 | 1.5 周 | C4 |
| 4 | resource-sync HTTP 协议（含 tenant 校验、HMAC、outbox）| P1 | **P0** | P0 | 1 周 | C8、C12 |
| 5 | **跨服务一致性总线 + 降级矩阵**（§12 落地）| P1 | **P0** | P0 | 2 周 | C5、C7 |
| 6 | example-service 业务样例 + 单测 fixture（perm-sdk-test 模块）| P2 | **P1** | **P0**（R2 升级）| 1 周 | 开发人员痛点 |
| 7 | 全链路集成测试 | P1 | P1 | P1 | 1 周 | — |
| 8 | perm-center HTTP 协议规范文档 | P1 | P1 | **P0**（R2 升级）| 0.5 周 | — |
| 9 | **资源/菜单软删除语义统一**（§11 落地）| P2 | **P0** | P0 | 1.5 周 | C4、C5 |
| 10 | **多租户硬隔离规范**（§10 新增）| — | **P0** | P0 | 1 周 | C12 |
| 11 | **manifest JSON Schema + 上传协议规范文档** | — | **P0** | P0 | 0.5 周 | C13 |
| 12 | **permission code lifecycle**（§11.1 落地）| — | **P0** | P0 | 1 周 | C4 |
| 13 | **字段级权限 MVP 子集**（§6.3 修订落地）| — | **P0** | P0 | 1 周 | C2 | <br>⚠️ **v3.1 修订**：降级为 v2.1（**[DROPPED-v3.1 from P0]**），v2 GA 仅保留 IR-6.3-C/D 硬契约 + perm-sdk CI 静态扫描 + 端到端样例 |
| 14 | **限时权限到期联动 + 流式接口中途切断**（§12.2 落地）| — | **P0** | P0 | 1 周 | C6 |
| 15 | **E-4b-1 AUTO_DEP 健康度 + dry-run + 角色归档/重置**（§3 修订）| Phase X | **v2.1（P0）** | **v2 GA（P0）**（R2 D9-1 提前）| 1.5 周 | C9 |
| 16 | **告警分级 + 发布前 dry-run + 反向查询 API**（§3.3 修订）| — | **P1** | P1 | 0.5 周 | C14 |
| 17 | **/error/403 reasonCode 协议 + 前端会话上下文保留**（§12.4/12.3）| — | **P0** | P0 | 1 周 | C11 |
| 18 | example-service MVP 业务实现 | P0 | P0 | P0 | 2 周 | — |
| 19 | **menuId 来源后端 path 反查 + IDOR 防护**（§10 + IR-6.3-E）| — | — | **P0**（R2 D9-2 新增）| 0.5 周 | R2 安全 CRITICAL | <br>⚠️ **v3.1 修订**：降级为 P1（X-Menu-Id IDOR 主路径物理消失），但**保留实现，不删除**——审计 trace 唯一 menuId 来源 + path_alias 软绕过防御 |
| 20 | **`@PermSensitive` 强制绕缓存 + 离岗黑名单**（§12.6）| — | — | **P0**（R2 D9-3 新增）| 0.5 周 | R2 安全 CRITICAL |
| 21 | **outbox worker 多副本 + ShedLock + 全局 cache-bypass 应急开关**（§12.1）| — | — | **P0**（R2 SRE 新增）| 1 周 | R2 SRE CRITICAL |
| 22 | **manifest alias 跨服务/跨权限级转发限制**（IR-11.1-D/E）| — | — | **P0**（R2 安全 HIGH 新增）| 0.5 周 | R2 安全 |
| 23 | **租户级配额 perm_tenant_quota**（§10.4）| — | — | **P1**（R2 租户管理员 HIGH）| 0.5 周 | R2 租户 |
| 24 | **admin saveMenu 跨库事务边界声明**（§3.1.2 配套）| — | — | **P0**（R2 架构 HIGH）| 0.5 周 | R2 架构 |
| 25 | **批量发布菜单 UX + wireframe + 操作步数 KPI**（§6.1 配套）| — | — | **P0**（R2 产品 HIGH）| 1 周 | R2 产品 |
| 26 | **(v3.1) sys_menu_ref 跨业务线复用机制**（§1.3.5）| — | — | — | **P0**（v3.1 新增）| 1.5 周 | S3 风险（9 角色 HIGH/CRITICAL）|
| 27 | **(v3.1) S2 拆 reportId 反模式三层兜底**（report_metadata.source_report_id + binlog 审计 + 月度 SQL hash 扫描 + admin 端 displayName 相似度预警）| — | — | — | **P0**（v3.1 新增）| 1.5 周 | S2 风险（9 角色 HIGH/CRITICAL）|
| 28 | **(v3.1) IR-6.3-C/D/F 硬契约 + perm-sdk CI 静态扫描 + canViewField helper**（§6.3 v3.1 简化版）| — | — | — | **P0**（v3.1 新增）| 1 周 | 字段级权限 v2 GA 兜底 |
| 29 | **(v3.1) MenuVisibilityResolver 公式重写为 OR(VIEW_*前缀)** + reasonCode DENIED_BY_DOMAIN/DEPRECATED_HEADER（§6.3 / §12.4）| — | — | — | **P0**（v3.1 新增）| 0.5 周 | 避免 HR 空白页 + X-Menu-Id 攻击面收口 |
| 30 | **(v3.1) 操作位套餐 UI（防爆炸）+ field_descriptor DRAFT schema 校验**（§6.3.6）| — | — | — | **P1（v2.1）**（v3.1 新增）| 1 周 | OperationCode 爆炸 + 字段级 v2.1 升级路径 |

**总工期增量**：v1 原约 8.5 周 → v3 修订约 19 周 → R2 后约 24 周（含 example-service 业务）。建议**拆 P0-A（安全/一致性底盘，必须）+ P0-B（接入体验，强烈推荐）+ feature flag 分租户灰度**（D9-4 推荐 A），分批交付。

---

## 15. 推进路线（v1 §9 合并 + R2 重排）

### 第 1 批 — P0-A 安全/一致性底盘（必须，0-6 周，v2 GA 阻塞）

- **gap #1, #4, #11**：perm-sdk Java + resource-sync HTTP + manifest JSON Schema —— 3 周
- **gap #5, #21**：跨服务一致性总线 + outbox 多副本 + cache-bypass 应急开关 —— 2.5 周
- **gap #10**：多租户硬隔离规范（§10）—— 1 周
- **gap #19, #20, #22**：menuId 后端反查 + @PermSensitive + manifest alias 限制 —— 1.5 周
- **gap #14, #17**：限时到期联动 + reasonCode 协议 —— 2 周
- **gap #24**：admin saveMenu 跨库事务边界 —— 0.5 周

### 第 2 批 — P0-B 接入体验 + auto-grant（强烈推荐，3-9 周，可与 P0-A 并行）

- **G**：ADMIN_MENU 命名归一 —— 0.5 周
- **D-2 增补**：BusinessKeys 策略入口 —— +0.5 周（合入既有工作单）
- **E-4a**：auto-grant MVP（grant 时正向补全 + MANIFEST + MENU_PUBLISH_DERIVED）—— 1.5 周
- **E-4b-1（D9-1 提前）**：AUTO_DEP 只读巡检 + dry-run + 角色归档/重置 —— 1.5 周
- **gap #2, #3, #9, #12**：实例资源同步 SDK + menu-manifest CRUD + 软删语义 + perm code lifecycle —— 4 周
- **gap #13**：字段级权限 MVP（IR-6.3-A~E）—— 1 周
- **gap #25**：批量发布菜单 UX + wireframe —— 1 周
- **gap #6, #8, #16**：fixture 套件 + 协议文档 + 告警分级 —— 2 周

### 第 3 批 — example-service MVP + 全链路验证（6-12 周）

- **gap #18**：example-service MVP（1 业务类型 + 1 范围类型 + auto-grant 演示 + 字段级演示 A1/A2）—— 2 周
- **gap #7**：前端接入形态实现（同 SPA 子路由）—— 1 周
- **H（条件性新增）**：取决于 D-4 边界结论 —— 1 周
- **gap #23**：租户级配额 —— 0.5 周
- **端到端黄金路径测试**：含 R2 三场景（S1 字段级 / S2 软删生命周期 / S3 HR 换岗）—— 1 周

### 维持 Phase X

- **E-4b-2**：auto-grant 复杂场景（依赖规则级联清理、批量回填、multi-hop 路径分析）
- **共享菜单多挂载点**（gap #5 边界场景，引入 menu_placement 表）
- **field_descriptor 全自动化**（v3 启用 `@PermFieldGuard`，业务零迁移）
- **非 Java SDK**（Go/Python/Node，按接入方实际需求驱动）

### 工期合计与灰度计划（D9-4 推荐拆分）

- **P0-A（安全/一致性底盘）**：6 周关键路径，feature flag `perm.tenant-isolation.v3-strict` 默认 false → 灰度租户名单 → 全量
- **P0-B（接入体验）**：6 周与 P0-A 并行，feature flag `perm.auto-grant.menu-publish-derived` 默认 false → 灰度
- **example-service MVP**：6 周第三批，依赖 P0-A/P0-B 完成
- **总关键路径**：约 12 周交付 v2 GA 可演示版本（含 R2 P0 闭环）；example-service 完整接入 18 周

### 关键结论一句话

**当前 AccessMesh 主线设计在权限模型层面（v1.4 双轨 + v3 修订）经得起 9 角色对抗评审；真正的缺口在"接入面 + 安全细节 + 文档自洽"——已通过 R1→R2 两轮迭代落盘 6 大范式切换，v3 GA 准入还需关闭 R2 暴露的 6 项 P0 闭环（约 1.5-2 周）。建议按 P0-A/P0-B 双轨拆分 + feature flag 分租户灰度推进。**
