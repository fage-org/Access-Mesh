---
doc_type: task
id: T-PERM-059
title: 权限视图/排查删除重设计——删除收口（全删 8 端点，新设计方向另立任务）
status: done
plan: docs/archive/2026-09-12/permission-query-unification-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.8
  - docs/design/access-service-architecture.md#§14
  - docs/design/permission-center/implementation.md#§3
depends_on: []
blocks: []
acceptance:
  - "范围圈定定案（2026-09-10 三项拍板）：全删 8 端点——permission-view 七端点（effective-permissions/resource-users/role-permissions/effective-roles/resource-tree/explain/recent-changes）+ /auth/query-permission-tree；explain 不保留（codex 四轮外评登记的 USER INSTANCE 候选明细完整性遗留随之消亡不修）；effective-permission-codes（登录串端点）与 query-scopes（运行时 SDK 端点）确认排除"
  - "连动面处置定案：①Gateway bootstrap 固定图——effective-permissions/explain 两行与「权限排查」菜单种子删除（query-scopes 路由行保留维持空库 API 映射种子）+ architecture §14.4 注记回写 + runbook 固定图升级 FAQ 行；②T-FE-043 cancel（页面随删除收口关闭，重做考虑事项随卡归档，新形态另立任务）"
  - "分期定案：本卡只做删除收口；「新设计方向产出（基于统一引擎结果模型的视图/排查新形态设计草案）」由后续任务承接（grill 级产品讨论，需用户深度参与）"
  - "e2e 核对：两垂直切片不触删除面端点（代码级核实零引用），全量回归确认零影响"
  - "遗留处置：explain USER 目标 scopeMode=INSTANCE 且 scopeAll 授权被条件/互斥评估清空时候选明细缺实例授权行——explain 已删，遗留随之消亡（登记于 T-PERM-057 codex 四轮外评，本卡闭环）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-10
---

# T-PERM-059 权限视图/排查删除重设计

> 状态：done（2026-09-10 删除收口；新设计方向另立任务）
> 依赖：无（独立范围决策任务；删除面核实于 T-PERM-057 落地后的统一引擎基线）

## 背景

权限视图/排查系（permission-view 七端点 + 前端排查页）整体删除重新设计（2026-09-09 Q14 定案）。前端排查页本已暂停待重做（T-FE-043）；/query-permission-tree 经核实为零外部消费端点，一并并入删除。

## 范围定案（2026-09-10 用户拍板，3+2 项）

1. **删除边界=全删 8 端点**：七端点 + query-permission-tree 全部物理删除，explain 不保留——「需要人类可读解释时可另用 explain」的契约指引同步移除（T-API-003 恢复 check 族结果记录全量回传后，内部 id 可定位行；人类可读通道待重做）。删除安全性核实：纯读侧管理端点，SDK/Gateway/e2e/example-service 零依赖，前端仅已暂停的排查页消费其中 2 个。
2. **前端面=一并删除**：`views/system/permission-query/` 整目录 + `api/permission-query.ts` + `api/perm-scope.ts`（零导入方孤儿）+ 路由注册；T-FE-043 cancel。
3. **分期收口**：本卡只做删除；新设计方向（基于 PermResult 全量结果记录的视图/排查新形态）另立任务，需 grill 级产品讨论。
4. **收尾两项**（评审存疑上报后拍板）：①check-interface `matchedResources[].resourceTypeCode` 契约示例改 null（实现恒 null——registry T-API-003 行挂靠本卡的示例漂移收口）；②permission-query-unification 计划随本卡归档 + query-engine-unification.md 转 superseded（四任务全 done）。

## 实施记录（2026-09-10）

### 后端删除

- **Controller**：`PermissionViewController` 仅保留 `effective-permission-codes`（登录串）；`PermAuthController` 删 `queryPermissionTree`。
- **Service**：`PermissionViewAppService`(+Impl) 删六方法与全部排查面私有 helper，保留登录串三方法 + `buildEffectiveView` 公共管线（构造依赖 12→5）；`PermissionQueryAppService`(+Impl) 删 `queryPermissionTree` 段（约 265 行）；`LogQueryAppService`(+Impl) 删 `getRecentChanges` + 专用 helper（`listChangeLogsFiltered`/`countChangeLogsFiltered`/`toRecentChange`）。
- **领域服务**：`AuditDomainService`(+Impl) 删 `queryRecentChanges`/`countRecentChanges`；`PermissionConditionDomainService`(+Impl) 删 explain 专属 `evaluateDetailed`（STATUS 常量与 `ItemDetail` 迁入实现类私有载体 `LoadedRules`/`ItemDetail`，保留面 `evaluate` 运行时路径不受影响）；`PermissionConflictDomainService`(+Impl) 删 explain 专属 `filterPermMutexWithDrops`（`computeMutexContext` 为 `filterPermMutex` 共享保留）。
- **DTO/vo/util**：access-service 本地 18 个 DTO（8 req + 10 resp）、vo 2 个（ConditionEvaluationDetail/MutexFilterResult）、util `PermTreeAssembler`（零消费方）删除；**perm-common SDK 副本**3 个（UserPermissionViewReq/UserPermissionViewResp/PermissionEffectivePermissionsResp）与 `PermissionFeignClient.getEffectivePermissions` Feign 方法同批删除（「概念退役清扫面含副本」纪律）。
- **bootstrap 固定图**：删 effective-permissions/explain 两行路由 + 「权限排查」MenuSeed（菜单 13 页→12 系统子页 + welcome）；query-scopes 路由行保留（SDK 运行时契约端点，固定图注册维持空库 API 映射种子）。

### 测试

- 先整删后按保留面最小重写：`PermissionConditionDomainServiceImplTest`（重写 4 例，evaluate 运行时路径驱动）与 `PermissionConflictDomainServiceImplTest`（重写 3 例，filterPermMutex 驱动）——初判「全部用例锚定删除面」有误，双轨评审 P2 指出用例断言的是保留面共享语义（引擎测试为直通桩，此两文件是唯一行为锁），详见下方评审处置节。
- 清理：`PermissionViewAppServiceImplTest`（12 用例删、登录串 5 用例保留）、`LogQueryAppServiceImplChangeLogTest`（recent-changes 4 用例删）、`AuditDomainServiceImplTest`（2 用例删）、`HttpApiPathSnapshotTest`（8 路径行 + 8 契约行删）、`PermissionViewControllerTest`（构造签名跟随，登录串门禁转发锁保留）。

### 前端删除

- `views/system/permission-query/`（index + components×3 + utils×3）、`api/permission-query.ts`、`api/perm-scope.ts`、`router/modules/system.ts` 路由块与 import；`permission-change-log/utils/perms.ts` 边界注释改删除口径。typecheck 通过。

### 文档回写

- api-contract：§5.8 表 7 行删 + 标题改「审计与系统配置」+ 删除说明注记；§6.8 整节删（**diff_snapshot 轻量规范迁入 §5.8 change-log 段**——变更日志写路径保留面）；§6.10.5 删；§6.1/§6.4/§6.7/§6.10.6 交叉引用同步；frontmatter。
- implementation：§3.8 对外接口表两行删、§2 组件树/骨架残留清理、§3.1 T-PERM-058 注记口径更新（登录权限串为 forUserView+PermViewAssembler 管线唯一存续消费面）、§7.5 整节删、§7.6 条款 6 删。
- architecture §14.4 门禁表注记 + last_reviewed；runbook 固定图升级 FAQ 行（含旧库菜单残留处置口径）；decision-registry 定案行；本卡 + T-FE-043 + README ×2 + plan 进度。
- `design/frontend/permission-query.md` 归档至 `docs/archive/2026-09-10/`。
- core-flows.md（§13 场景十收口为审计双日志面 + 表格行删）、overview.md（运行时接口列表与审计节改写）、project-rules.md（分层举例换存活例子）、permission-change-log.md（分工叙述与规范引用）、access-service-architecture.md（§12 门禁注记、§4 业务编码契约注记）。

## claude 外评处置（2026-09-10，提交 6e665334b 后）

claude CLI 独立评审（默认模型，只读）：P0=0/P1=0，P2×4 + P3×7 组——全部为文档/注释删除口径收口不彻底与两处死代码，无运行时影响（评审同时实证：8 端点路径全仓零命中、排除面 100% 存活且登录串管线逐字节等价、固定图计数静态独立推算一致、重写测试判别力足够且覆盖度较改前提升）。逐条核实属实后全处置：

- P2：§6.8 活代码引用 23 处（permission-change-log.ts 12/types.ts 2/DiffSnapshotPanel.vue 1/授权链 Java 3/测试 5）改指「§5.8 diff_snapshot 规范（原 §6.8）」+ 契约原 §6.8 位置留编号退役锚点；契约 §5.8 表格空行截断；impactLevel 枚举句与排查页暂停注记两处自相矛盾改历史口径；admin-service-api-contract 菜单种子行 15→14 随固定图回写。
- P3：PermMutexContext 的 firedRule/isFired/codeOf+opById（explain 明细归因链残留）与 ScopeModeSupport.fromSnapshot（含测试 3 行，生产零调用——快照读侧解析随 explain/recent-changes 消亡）删除；保留面注释现在时 6 处（LogQuery Javadoc/Mapper.java/.xml/PermResultUtils/契约 L15/L397）加删除注记；core-flows mermaid 节点、implementation §3.5 explain 句与「遗留」清单两项 done 收口、change-log 排查步骤引用、frontend README 归档标注、mock/login.ts 注释；归档索引四处（plan 状态行/plans README 划线约定/T-FE-043 design_refs 归档路径/registry 2026-09-06 旧行加取代注记）；BootstrapGraphDefinition 菜单 Javadoc 首句加「原」字。
- 登记不处置：PermissionViewController 类名与职责不再完全匹配（观察项，重做排查面时一并裁并）。

## codex luna max 复评处置（2026-09-11，提交 93daea457 后）

codex luna max（read-only，813k tokens）：P0=0/P1=0，P2×2 + P3×4——删除面本体零缺陷（专项清单九项逐一执行通过），全部问题为归档索引同步与 claude 处置轮引入的引用瑕疵。逐条核实属实后全处置：

- P2：①归档状态未同步权威入口——design/README 索引行仍写「evolution 待实施」改 superseded、query-engine-unification.md 补 `superseded_by: implementation.md#§3`、补 `docs/archive/2026-09-10/README.md` 归档批次说明（先例 2026-09-07）；②runbook 固定图历史增长叙述（菜单 15 行/82 端点）补「T-PERM-059 后现值 80 路由/79 映射/菜单 14 行——以 BootstrapGraphDefinition 与 AccessBootstrapPgIT 为准」注记 + AccessBootstrapInitializer 菜单注释 15→14。
- P3：①claude 处置轮兜底替换把「（原 §6.8）」二次替换成「（原 §5.8 diff_snapshot 规范）」的自我引入错引（8 文件嵌套形态）修正 + 旧 §6.8 行号引用清理；②任务卡「整删两 DomainService 测试」表述与重写事实矛盾修正 + ScopeModeSupportTest 残留空测试方法删；③AbstractRoleMapper.selectFilteredByIds（唯一消费方 filterRoleIds 已删，claude 轮漏清）连 Java 方法与 XML 块删除；④operation-log.md/change-log.md 两处前端设计现在时句加删除注记。

## 非目标 / 后续

- 登录权限串链路（effective-permission-codes + UserMenuQueryService + getEffectiveResourceAccess）不动——buildEffectiveView 管线为唯一存续消费路径。
- 管理面写门禁与授权页消费的端点（role-resource-permission/list、resource-entity/tree 等）不动。
- **新设计方向另立任务**：基于统一引擎结果模型（matchedRoleIds/matchedPermissionIds 全量回传）的视图/排查新形态设计草案——grill 级产品讨论（排查页长什么样、结果记录可解释到什么粒度、要不要复用 query-scopes 等）。
