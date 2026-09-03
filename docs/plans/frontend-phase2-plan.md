---
doc_type: plan
title: 前端 Phase 2 — 核心功能补齐 + 后端接口改造
status: proposed
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
tasks:
  - T-PERM-022
  - T-PERM-023
  - T-PERM-024
  - T-PERM-025
  - T-PERM-026
  - T-PERM-027
  - T-PERM-028
  - T-PERM-029
  - T-PERM-030
  - T-PERM-031
  - T-PERM-032
  - T-PERM-033
  - T-PERM-034
  - T-PERM-035
  - T-PERM-036
  - T-PERM-037
  - T-PERM-040
  - T-PERM-041
  - T-FE-036
  - T-FE-038
  - T-FE-039
  - T-FE-040
  - T-ADMIN-021
acceptance: "13 页后端接口改造完成（T-PERM-022~034 逐页实现）；跨页共性接口改造 + api-contract 回写完成（T-PERM-037）；自动授权实现 + 测试通过（T-PERM-035）；动态数据权限链路验证通过（T-PERM-036）；T-FE-036 前端权限授予页（mock 驱动）实现完成（本 plan 关联的前端部分，见正文前端重建任务节；含 DoD：api-contract 对齐/引擎 fixtures 比对/四态状态机）；单类型矩阵上下文完成（T-FE-038 前端 + T-PERM-040 后端，2026-08-03 定稿，2026-08-05 扩展 list 类型过滤 + 嵌套 20008）；条件权限不可转授完成（T-PERM-041，20041 + **20042 条件启用状态** + 主权限 DDL CHECK）；矩阵图标正交模型完成（T-FE-039）；**授权弹窗 v3.1 记录级聚焦编辑完成（T-FE-040，mock-first，包含：焦点生命周期/显式复制/停用条件/CONDITION:VIEW 移除/子权限记录级入口/节点摘要/20042-20043 提示，联调见 T-FE-018）**；T-ADMIN-021 org-tree includePositions **二期**（首期只角色入口）。注：T-PERM-035/036 受 design-review §11 暂缓门禁约束，需 PM 重申后才能进入 in-progress。"
last_updated: 2026-09-03
---

# 前端 Phase 2 — 核心功能补齐 + 后端接口改造

> 状态：proposed
> 来源：`docs/archive/2026-08-27/improvement-plan.md` §4 Phase 2 拆分（roadmap 已归档，拆分产物即本 plan）
> ⚠️ 暂缓门禁：自动授权（T-PERM-035）/ 动态数据权限（T-PERM-036）受 design-review §11 E4 / Q7-B 决策约束，近期不推进，需 PM 重申后才能重启。

## decision_refs（暂缓依据，非实现依据）

> 以下归档文档仅作决策溯源，**不作为实现依据**，不进入 design_refs 回写范围。

- `docs/archive/2026-06-17/design-review.md` §11 — E4（auto-grant 保留 TODO + Phase X 未排期）、Q7/B（动态数据权限延后到 example-service 暂不实现）

## 目标

- 实现 Phase 1 各页标记的 🔧❌ 接口改造（T-PERM-022~034 逐页实现）
- 跨页共性接口改造 + api-contract 回写收尾（T-PERM-037，不重复逐页改造）
- 实现自动授权（T-PERM-035，暂缓）
- 验证动态数据权限端到端链路（T-PERM-036，暂缓）

## 非目标

- 不在本 Phase 做前后端联调（归 Phase 3）
- 不实现 example-service（design-review §11 Q7/B 决策暂不实现）

## 准入条件

- Phase 1 收尾（13 页 API 核对清单产出 🔧❌ 项）
- **暂缓项额外门禁**：design-review §11 E4（auto-grant Phase X 未排期）/ Q7-B（动态数据权限延后到 example-service 暂不实现）需 PM 重申解除

## 任务清单

### 逐页后端接口改造（T-PERM-022~034，depends_on 对应 Phase 1 前端任务）

| ID | 页面 | depends_on | 门禁 |
|---|---|---|---|
| T-PERM-022 | 2.2 角色管理后端 | T-FE-002 | Phase 1 该页 API 核对清单产出 |
| T-PERM-023 | 6.1 类型定义后端 | T-FE-003 | 同上 |
| T-PERM-024 | 6.2 系统配置后端 | T-FE-004 | 同上 |
| T-PERM-025 | 7.1 操作日志后端 | T-FE-005 | 同上 |
| T-PERM-026 | 5.1 业务域后端 | T-FE-006 | 同上 |
| T-PERM-027 | 5.2 服务+接口映射后端 | T-FE-007 | 同上 |
| T-PERM-028 | 3.1 资源+操作后端 | T-FE-008 | 同上 |
| T-PERM-029 | 3.2 权限条件后端 | T-FE-009 | 同上 |
| T-PERM-030 | 3.3 冲突规则后端 | T-FE-010 | 同上 |
| T-PERM-031 | 3.4 资源依赖后端 | T-FE-011 | 同上 |
| T-PERM-032 | 7.2 变更日志后端 | T-FE-012 | 同上 |
| T-PERM-033 | 4.2 权限查询后端 | T-FE-013 | 同上 |
| T-PERM-034 | 4.1 权限授予后端（见任务卡） | T-PERM-031 | 同上（design_refs 含 access-service.sql） |

### 单类型矩阵后端任务（T-PERM-040/041，2026-08-03 定稿；范围见任务卡）

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-PERM-040](../tasks/T-PERM-040.md) | 4.1 权限授予单资源类型后端支持 | ✅ | T-PERM-028, T-PERM-034 |
| [T-PERM-041](../tasks/T-PERM-041.md) | 主权限条件不变量（20041 不可转授 + 20042 启用状态） | ✅ | T-PERM-034 |

### 前端重建任务（T-FE-036/T-FE-038/T-FE-039，本 plan 关联的前端部分）

> frontmatter `tasks` 同时登记 T-FE-036/T-FE-038/T-FE-039/T-FE-040；前端以 **mock 数据驱动**（接口形状按 api-contract），T-FE-038 mock 先行、T-PERM-040 非前置（2026-08-05 评审方案 B），联调任务 T-FE-018 同时依赖 T-FE-038 + T-PERM-040 汇合；T-FE-040（v3.1 记录级聚焦编辑）同样 mock-first，依赖 T-FE-039 代码基线，blocks T-FE-018。

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-FE-036](../tasks/T-FE-036.md) | 4.1 权限授予页重设计（v3） | ✅ | T-FE-001/002/008/009 |
| [T-FE-038](../tasks/T-FE-038.md) | 4.1 权限授予页单类型矩阵上下文 | ✅ | T-FE-036 |
| [T-FE-039](../tasks/T-FE-039.md) | 4.1 矩阵图标正交状态模型与图标精简 | ✅ | T-FE-038 |
| [T-FE-040](../tasks/T-FE-040.md) | 4.1 授权弹窗 v3.1 记录级聚焦编辑（决策记录已确认，mock-first） | ✅ | T-FE-039 |
| [T-ADMIN-021](../tasks/T-ADMIN-021.md) | org-tree 扩展 includePositions（组织+岗位一体树，授权页主体树数据源） | ✅ | — |


### 暂缓核心能力（T-PERM-035/036）

| ID | 内容 | status | 门禁 |
|---|---|---|---|
| T-PERM-035 | 自动授权（resolveAutoGrants + autoGrantForInsert + 循环依赖检测） | proposed | design-review §11 E4：Phase X 未排期，需 PM 重申 |
| T-PERM-036 | 动态数据权限端到端验证（scopeMode → SQL 映射链路） | proposed | design-review §11 Q7/B：延后到 example-service 暂不实现 |

### 共性收尾（T-PERM-037）

| ID | 内容 | status | 门禁 |
|---|---|---|---|
| [T-PERM-037](../tasks/T-PERM-037.md) | 跨页共性接口改造 + api-contract 回写收尾（**不重复逐页改造**，仅处理多页共用接口与契约回写） | ✅（2026-08-31 审计型零代码变更收口） | depends_on T-PERM-022~034 |

## 归档条件

- T-PERM-022~034 + 037 + 040 + 041 done（或暂缓项 035/036 cancelled，需 PM 决策）+ T-FE-036 done（前端部分，见上）+ **T-FE-038 done（单类型矩阵上下文）** + **T-FE-039 done（图标正交模型）** + **T-FE-040 done（授权弹窗 v3.1 记录级聚焦编辑）** + **T-ADMIN-021 done（2026-08-01 补充）**
- 改造接口回写 `docs/design/permission-center/api-contract.md`
- 自动授权流程回写 `core-flows.md`（若 035 推进）

## 当前进度

- 2026-06-29：建立本 plan + 拆分 16 个任务（13 逐页后端 + 035/036 暂缓 + 037 共性收尾）。全部 proposed，暂缓项带门禁，待 PM 重申。
- 2026-08-02：**T-FE-036 实现完成转 review**（mock 驱动 + 自验通过；设计 `permission-grant.md` 回写 adopted，含 §13 实现注记；S1~S7 待人工交互验收）。验收后本 plan 前端部分仅剩 T-ADMIN-021（二期）与 T-PERM-022~037 后端任务。
- 2026-08-03：**单类型矩阵上下文定稿**（需求确认：单权限类型 = 单个 `resourceTypeCode`），新增 T-FE-038（前端 MatrixContext + 类型切换加载 + 操作列配置按类型隔离）与 T-PERM-040（后端 operation-permission/list 类型查询 + apply-grant-plan 20008 校验）；设计回写 permission-grant.md §2.2/§3.2/§3.5/§3.6/§11（S8/S9）/§12、api-contract §5.3/§6.5.1、core-flows §6。
- 2026-08-05：**图标映射定稿**——条纹=有条件、粗黑边框=可转授 canGrant、红/淡红=撤销（旧映射已废弃，T-FE-039 已同步）；子权限分叉精确投影规则（仅直接主权限记录、继承格不复制、级联撤销附红图标）；条件转授前端行为（选条件清 canGrant、20041 提示）并入 T-FE-039；T-FE-018 补充 T-FE-039/T-PERM-041 依赖；T-PERM-041 范围限定为仅最终态建表 DDL（不考虑历史数据，用户确认）；任务行治理精简。
- 2026-08-09：**T-FE-040 授权弹窗 v3.1 记录级聚焦编辑完成**（mock-first：焦点生命周期/显式复制/停用条件/CONDITION:VIEW 移除/子权限记录级入口/节点摘要；S1~S8 mock 人工验收 + 纯 reducer 单测 206 tests 全过，design 回写 done）。本 plan 前端部分剩余 T-ADMIN-021（二期）；后端任务 T-PERM-022~034/037/040/041 全部 ⚙️ 待启动（T-FE-040 blocks 的 T-FE-018 联调依赖 T-PERM-034/040/041）。
- 2026-08-27：T-PERM-034 第 5 项（旧写入口端点退役）已独立收口（save/revoke/children/add-child/remove-child 五端点删除，apply-grant-plan 为唯一写入口）；其余六项待 T-PERM-031 完成后推进，见任务卡完成记录。
- 2026-08-29：**T-PERM-033 权限查询后端收口**——门禁设计定案：不引入独立排查码（原预案 PERMISSION_QUERY:VIEW 否决，权限码结构为「资源:操作」），explain/recent-changes 门禁切被查目标实例 USER:VIEW/ROLE:VIEW，query-scopes 维持运行时语义（§6.7 登记）；explain 契约扩展（评估上下文两态/条件评估明细脱敏/互斥丢弃明细）+ recentChanges 按权限键 6 字段过滤；核对完成（LOCAL_USER 语义/query-resources 字段一致/permission-view 唯一差异 resource-users 登记）；前端 perms 常量切 USER:VIEW/ROLE:VIEW。剩余逐页任务：T-PERM-026~031/034/037。
- 2026-08-29：**T-PERM-026 业务域后端收口**——P0 隐患修复（BizDomainMapper selectByCode/selectByCodes 列名误写 domain_code，resolveDomainId 底层真库必炸，PgIT 回归锁）；六项 🔧 全处置（DOMAIN 种子误报反转、Resp+global、detail/update 切业务键 code、list 服务端过滤分页、JSONB 映射确认+save 补 JSON 校验）；两项设计定案：DOMAIN:VIEW 补 bootstrap 固定图（无授予起点死锁防护）、错误码 20051 DOMAIN_DELETE_CONFLICT（全局域/引用拒删）+20052 DOMAIN_CODE_DUPLICATE；前端与 mock 双注册表对齐。剩余逐页任务：T-PERM-027~031/034/037。
- 2026-08-29：**T-PERM-027 服务+接口映射后端收口**——七项 🔧 全处置：删除级联清理（全部映射含 MANUAL + SERVICE_SYNC 孤立资源 FULL diff 同边界 + Gateway 广播，PgIT 真库锁）、ApiMappingResp 补资源业务字段四项（批量补全，apis 委托 list 同层复用）、mapping list 补 SERVICE:VIEW 门禁+服务维裁剪、syncMode FULL-only（@Pattern 校验 + 删除零调用 IncrementalSyncStrategy）、Resp+updatedAt（lastSyncedAt 登记不做）、list 维持全量收口为设计定案、资源选择器登记 T-PERM-028 联动；SERVICE:VIEW/MANAGE/SYNC_INTERFACE 补 bootstrap 固定图（AccessBootstrapPgIT 断言锁定）。剩余逐页任务：T-PERM-028~031/034/037。
- 2026-08-29：**T-PERM-028 资源+操作定义后端收口**——六项 🔧 全处置：detail/update/move/remove 切业务键（混合形态：detail/update 键平铺、move 嵌套 {resource,parent|null}、remove items；operation 侧 resourceTypeCode 可空=全局轨）、bigint 十进制字符串线格式（ToStringSerializer 全项目首例，project-rules §7.4 定策略；T-FE-018 联调门禁解除）、extraClear 显式清空（UpdateEntity 强制写列——BaseMapper.update 忽略 null 为 PgIT 抓出的既有陷阱，移顶层同修复）、VIEW 门禁三处补齐（resource list/detail + operation detail，类型级）、resource_type 创建联动预置 CRUD 四操作位（DDL 模板同款）、T-PERM-027 §7.6 资源选择器落地（MappingForm 类型下拉+树选，仍提交内部 resourceId）；五项设计定案经用户决策；两处既有缺陷修复（selectByResourceTypeAndCode XML 参数名错配——零调用方从未暴露；move 防环/跨类型校验缺失，20053 新码）；PgIT 真库锁 3 用例 + WireFormat 契约测试。剩余逐页任务：T-PERM-029~031/034/037。
- 2026-08-30：**T-PERM-029 权限条件后端收口**——六项 🔧 全处置：管理端点 detail/update/remove 切业务键 code（uk tenant+code 部分索引，软删后可复用）、detail 查不到抛 20006（data:null 宽松语义删除）、list 维持全量不分页（设计定案反转前端分页登记）、ConditionResp 补 updatedAt、ConditionDetailReq 接线；单删孤儿方法删除；写门禁收窄为类型级（CONDITION 无实例投影，原实例级声称系 ID 空间错位废弃；实例投影与条件双轨制登记 T-PERM-048）。剩余逐页任务：T-PERM-030/031/034/037。
- 2026-08-30：**T-PERM-030 冲突规则后端收口**——六项 🔧 全处置（定位键维持内部 id 评估定案/读三端点 list/detail/detect 补类型级 VIEW/Resp 补 updatedAt/ConflictRuleDetailReq 死代码删除/实体注释对齐/detect 补 VIEW），另 detail 查不到 20020 收紧；写门禁类型级收窄（CONFLICT_RULE 同 CONDITION 口径，T-PERM-048）；bootstrap 固定图补 CONFLICT_RULE 四档 + 顺带补 CONDITION 写三档（029 遗漏的空库死锁缺口，checkCanGrant 无授予起点）；update UpdateChain→UpdateEntity 对齐 028 标准；新建 ConflictRuleAppServiceImplTest 33 项（此前零单测）；四项设计定案经用户决策。剩余逐页任务：T-PERM-031/034/037。
- 2026-08-30：**T-PERM-031 资源依赖后端收口**——八项 🔧 全处置：门禁五档类型级（读 list/graph/check 补 VIEW + update/remove 从 ID 空间错位的实例级收窄）、update PUT 全量替换+资源对业务键补全（Q3=B 前端契约，原静默丢弃资源对字段）、Resp 补静态字段+操作位字符串线格式（operationCodes 反解经决策不做）、maintainSource 四值白名单（schema 口径收口）、batch-sync FULL diff 三元组匹配（源+目标+触发位，对齐 uk 语义）+ 循环外批量预解析消解 N+1；另等价重复业务预查新增 20054、操作码 fail-closed 20005（原静默丢弃落 0 写出语义错误规则）、自依赖 20044、updatedBy 审计、bootstrap 五条补授（含 SYNC）+ GRANT_RESOURCE_TYPES 同步；DependencyAppServiceImplTest 35 项（含内外部复评收口用例：空白码统一/清单级必填预检/级联校验/二次加载 fail-closed）+ AccessBootstrapPgIT 39/26；三项设计定案经用户决策。剩余逐页任务：T-PERM-034/037。
- 2026-08-30：**T-PERM-034 权限授予后端收口（四缺口推进，经决策）**——已完成项核对登记不重做（Resp 四字段/错误码族/DDL CHECK/prevalidate 主体/写入口五项清单均先行存在）；实施：①20043 子权限属性系统不变量（两 create 形态+update 子目标，先于 20041，+「向 AUTO_DEP 父挂子权限 20034」补齐）②SubPermissionPolicy 抽取+resolveSubPermissionPolicy 唯一公开入口+sub-perm-allowed-types 端点（§6.5.2 全套，读写同源）③diff_snapshot §6.8 聚合形状（AuditPermissionKey 预检期快照+一条聚合日志，T-PERM-033 读侧依赖解锁）④GoldenFixturePgIT 引擎级比对（6 用例真库种数据逐格等价）——比对首跑抓出引擎实质分歧并修复：resolveBitMasks 不计全局操作位（授权允许的全局位运行时被忽略），改按类型合并专属+全局；测试 4→35+5+1+3（含端到端成功/失败两场景）。剩余逐页任务：T-PERM-037/040/041（040/041 为授予链配套）。
- 2026-08-30：**T-PERM-041 主权限条件不变量收口**——调研核实先行交付不重做（20041 枚举+prevalidate 最终态判定、`ck_role_resource_permission_condition_can_grant` DDL CHECK、api-contract/core-flows/permission-grant.md 契约文字均已存在），实际缺口仅 20042 后端校验与计划级测试：`CONDITION_DISABLED(20042)` 枚举建立（沿用预留编号）+ prevalidate 两处校验（creates 主权限新写入条件必须启用；updates 仅变更时校验——同 id 重写=存量保留按条件 id 比对豁免，清除仅精确空串、空白串落 20006、缺省不触发；均紧随 20041 之后符合 §6.5.1 优先级）；20006 存在性批量预检先于 20041/20042 维持既有顺序（设计定案：极端组合下与 mock 错误码不同但均为拒绝，不重排）；计划级条件不变量测试矩阵（doCallRealMethod 走真实 20041）+ H2 CHECK 用例。剩余逐页任务：T-PERM-037/040（040 为授予链配套）。
- 2026-08-31：**T-PERM-040 单资源类型后端支持收口**——调研核实三块交付物实现面先行存在（operation-permission/list `resourceTypeCode` 过滤归并前已带、listPermissions 类型过滤+跨类型子权限挂父已实现、creates 20008 由 resolveOperation 承载且三形态全覆盖），includeGlobalFallback 合并语义已随 T-PERM-049 退役；实际缺口收口（三项设计定案）：①未知 resourceTypeCode 统一空列表 fail-closed（listOperations 原回退全量，与 listPermissions 口径相反）②类型过滤下沉专用 Mapper 查询（新增 `selectValidMainByRoleIdAndResourceType` 主权限 SQL + 复用 `selectValidByRoleIdAndDependIds` 子权限双重约束，childCount 计数源含全部直接子权限）③`OperationListReq.domainCode` 死参数删除（从未实现过滤、契约未登记、前端零传参，DTO+签名+前端类型同步）；操作适用性测试矩阵补齐（20008 三形态+20005 区分锁定、grant list 过滤五用例、operation list fail-closed 两用例）。剩余逐页任务：T-PERM-037。T-FE-018 后端依赖全部就绪。
- 2026-08-31：**T-PERM-037 共性收口（审计型零代码变更）——Phase 2 逐页后端任务全部完成**（T-PERM-022~034/037/040/041；暂缓项 035/036 与 T-ADMIN-021 另行定夺）。全量审计结论：各页 🔧❌ 全处置零补漏（admin 契约 13 个 🔧 为已实现历史变更标记，抽验在案）、四组跨页共用接口消费侧零不一致（type-definition/list 四处消费全传 typeKey、资源树四处消费、condition/list 两页、operation-permission/list 随 040 收口）、api-contract 零占位残留、Phase 1 核对状态已随逐页任务回写。唯一悬空项「路由级 auths 拦截缺失」经用户决策定案：菜单可见性 v3.5 ∃op 派生方案后端已实现（user-menu 双轨下发过滤后 menus 树）、前端未接线（T-FE-041 纯静态路由模式，initRouter 传空数组、menus 存 user store 备用），**user-menu menus 轨道前端接线归入 Phase 3 联调 T-FE-015**（不立独立任务、不改 filterNoPermissionTree——与既定后端派生方案重复且可绕过）；三处页设文档登记同步收口。本 plan 归档另待 T-ADMIN-021（前端部分）与暂缓项定夺；T-FE-015~022 联调解锁。
- 2026-09-03：**T-ADMIN-021 org-tree includePositions 收口——本 plan 后端任务全部完成，仅剩暂缓项 035/036 待定夺**。四项用户决策落地：债务①全顺带（OrgQuery 补 operationCode+treeConfigId，"CREATE+混合树拒绝"不再空转）、treeConfigId 缺省=默认树子树（契约字面；无默认配置 11001 fail-closed）、CREATE×treeConfigId 同传拒绝 10008、响应复用 perm-common ItemsResp<OrgResp>（线格式与 P1-3 定稿相同）。岗位节点后端裁剪（hasTypeLevel 非抛出判定，引擎技术故障/主体缺失 SystemException 99999 不静默降级）；顺带修正 orgName/parentOrgId 死参数与契约 OrgResp phone/email 陈旧行；前端 getOrgTree 解包 items（调用方零改动）+ ReOrgTreePanel 树配置切换真实生效。回归：OrgTreeIncludePositionsPgIT 21 用例（否定性裁剪/故障/兼容/多树/参数拒绝/过滤语义守卫/裁剪旁路锁/组合剪枝/门禁路径区分，红绿双证）+ hasTypeLevel 单测 3 用例 + 快照更新 + 前端 tsc/vitest 202 基线；过滤语义定案——根存在性守卫基于未过滤全量（11002 仅真缺失/软删），orgType/status 节点级内存过滤（根不豁免），orgType 值域白名单 10008。T-FE-037（Phase 3 最后一项联调）依赖全部就绪。
