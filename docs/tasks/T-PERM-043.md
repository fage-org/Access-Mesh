---
doc_type: task
id: T-PERM-043
title: GROUP_ROLE 写入口删除与前端隐藏
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
  - docs/design/frontend/role-manage.md
  - docs/design/frontend/permission-grant.md
depends_on: [T-ACCESS-019, T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "GROUP_ROLE 专用写入口直接删除：group-role CRUD 写操作接口与 abstract-role/extra-roles/add|remove 接口移除（已核实前端/SDK 无任何调用方，不做兼容层），GroupRoleAppServiceImpl 及写 abstract_user_id=null 的死路径（违反 DDL NOT NULL，从未可用）同步删除"
  - "通用角色创建/更新入口显式拒绝 GROUP_ROLE：复用 ROLE_TYPE_MISMATCH(20022)（类型不匹配语义）；rejectReservedRoleType/LOCAL_PROJECTION_IMMUTABLE(20045) 为本地投影保护语义、GROUP_ROLE 不在其保留集，不适用本场景；不新增专属错误码"
  - "前端隐藏 GROUP_ROLE：角色管理页角色类型选项默认不出现（保留代码不删，常量/开关控制）；无导航入口现状保持"
  - "BASIC_ROLE 为首期唯一功能角色在契约与前端页面口径中明确"
  - "双事实源遗留登记：管理侧 user_role 关系与运行时 abstract_role.extra.basicRoleIds 两套读取路径的现状写入遗留清单，待未来按 role_inclusion(group_role_id, included_role_id) 单事实源设计另行立项时统一"
  - "单测 + PG 用例：已删除写入口做不存在负向验收（模式同 T-ACCESS-011 退役接口验收）；BASIC_ROLE CRUD 不受影响"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-25
---

# T-PERM-043 GROUP_ROLE 写入口删除与前端隐藏

## 背景

GROUP_ROLE 写入路径从未可用：GroupRoleAppServiceImpl:123 写 user_role.abstract_user_id=null，违反 DDL NOT NULL（access-service.sql user_role 表 `abstract_user_id BIGINT NOT NULL`），插入即抛异常；同时管理侧从 user_role 关系读组内 BASIC_ROLE、运行时从 abstract_role.extra(JSONB).basicRoleIds 展开，两套事实源互不同步。已确认处置：直接删除无调用方的专用写入口（与计划「没有存量调用方就不创建兼容层」约束一致，不为无调用方接口维护「功能未开放」语义）；前端隐藏；BASIC_ROLE 为首期唯一功能角色。本任务在 T-ACCESS-019 之后实施（同触 RoleManageAppServiceImpl，避免并发）。

> 勘误（2026-08-25）：验收条款 1 与本节「无任何调用方/无调用方」按狭义读法仅指 add|remove 写接口；读接口 `extra-roles/list` 曾有授予页 `SubjectTreePanel` 活跃调用（执行期核实），已随授予页同步隐藏处置，list 随 Impl 整删。

## 范围

- 专用写接口与 GroupRoleAppServiceImpl 死路径删除。
- 前端 GROUP_ROLE 选项隐藏（不删代码）。
- 遗留双事实源清单登记（供未来立项输入）。

## 当前口径

- 与退役 API（T-ADMIN-024）同模式：无调用方直接删，错误码不复用、不加映射层。
- 通用入口复用现有类型校验错误（ROLE_TYPE_MISMATCH 20022），不新增错误码。
- extra-roles 三接口（list/add/remove）一并退役：list 唯一数据生产者 add 从未成功写入、恒返回空，随 GroupRoleAppServiceImpl 整删；进 RETIRED_PATHS 负向防回归；契约 §6.10.3 改题保留 tree 语义、删 extra-roles 正文。
- 存量 GROUP_ROLE 行清理通道：delete/move 保持可用（update 请求体无 roleTypeCode，拒绝语义=按目标角色现行类型判定）。
- GROUP_ROLE 生命周期彻底冻结（2026-08-25 用户确认）：通用 create/update 与外部 sync/full-sync 通道均拒绝 GROUP_ROLE(20022)，白名单声明 role:GROUP_ROLE 亦不生效。
- 前端隐藏形态（2026-08-25 用户确认）：直接收窄数组 `MANAGEABLE_ROLE_TYPES = [BASIC_ROLE]`（注释保留历史与恢复条件，不引入显式开关常量）；授予页主体树同口径过滤仅 BASIC_ROLE，保留代码不删。
- 双事实源遗留登记位置（2026-08-25 用户确认）：根 README「技术债遗留登记」段 + 本任务卡遗留节（沿用现有载体，不新建 known-issues 文档）。
- 未来真实需求出现时按 role_inclusion 单事实源表设计重立项，删除 extra.basicRoleIds，禁止继续滥用 user_role 表达角色包含关系。

## 非目标 / 遗留

- GROUP_ROLE 枚举、role_type 种子与读模型保留且冻结（不扩展、不新增入口、不做能力框架）。
- 不实现 role_inclusion、不做角色包含运行时展开。
- **双事实源遗留登记（2026-08-25，登记于仓库 README「技术债遗留登记」段）**：管理侧曾以 `user_role(target_type=GROUP_ROLE, relation_id=基础角色)` 表达组-角色包含（专用写入口已删，该路径从未成功写入），运行时前向授权展开消费角色树 + `abstract_role.extra(JSONB).basicRoleIds`（现行无任何写入方——create/update/sync/full-sync 均拒绝 GROUP_ROLE(20022)；历史管理侧 extra 透传通道曾可写，存量不保证为空，展开仅对既有 GROUP_ROLE 行按类型门控解析）——两套读路径互不同步。未来出现真实组角色需求时按 `role_inclusion(group_role_id, included_role_id)` 单事实源表重新立项，删除 extra.basicRoleIds，禁止继续滥用 user_role 表达角色包含关系。
- 前端 extra-roles 面板/API 封装/ROLE:ASSIGN/ROLE:REVOKE perm 串保留为不可达代码，待 role_inclusion 立项后随 `MANAGEABLE_ROLE_TYPES` 恢复。
- **冲突规则页 GROUP_ROLE 选项（2026-08-25 评审登记，产品口径待确认）**：`conflict-rule/utils/hook.ts` ROLE_MUTEX 表单角色选择器仍请求并列出 GROUP_ROLE（`roleTypeCodes: ["BASIC_ROLE","GROUP_ROLE"]`）。保留理由：后端 /role/list 读模型冻结仍返回 GROUP_ROLE、用户对存量 GROUP_ROLE 的分配链路完整、存量规则引用解析依赖该选项；但「新建规则可选运行时空语义角色」属产品口径问题，待产品确认后收窄（表单收窄 BASIC、解析映射保留 GROUP 为折中方案）。
- **概览/流程文档注记**：overview.md §角色模型、core-flows.md 场景二仍按现行运行时语义描述「首期用 extra.basicRoleIds 简化表达，缓存构建阶段展开」——运行时读路径确实保留该展开，非失实；零写入方现状由 README 技术债段承载，不在概念文档重复。

## 执行记录（2026-08-25）

**后端（access-service）**：

- 删除文件：`GroupRoleAppService`、`GroupRoleAppServiceImpl`、`GroupRoleExtraRoleReq`、`GroupRoleExtraRolesListReq`、`RoleSummaryResp`（引用面核实仅剩互相引用与快照字符串）。
- `PermRoleController`：移除 GroupRoleAppService 注入与 `/extra-roles/list|add|remove` 三端点及相关 import；类 javadoc 补 T-PERM-043 说明。
- `UserRoleMapper`（+XML）：删除仅被 GroupRoleAppServiceImpl 调用的 `selectValidByTargetAndRelation`、`selectByTargetTypeAndTargetId`。
- `RoleManageAppServiceImpl`：`createRole` 在 rejectReservedRoleType 后拒绝 `roleTypeCode=GROUP_ROLE`（20022，resolve 后按值双保险）；`updateRole` 在 rejectIfLocalRole 后按目标现行类型拒绝 GROUP_ROLE（20022，`RoleType.GROUP_ROLE.getValue()` 比较，与 deleteRoles 既有模式一致）；delete/move 不动（清理通道）；类 javadoc 更新。
- `AbstractRoleSyncAppServiceImpl`：`sync`/`fullSync` 在 rejectReservedRoleType 后拒绝 GROUP_ROLE（20022，先于服务-类型白名单判定——白名单声明 role:GROUP_ROLE 亦不生效），外部同步通道与通用入口同口径，GROUP_ROLE 生命周期彻底冻结。
- 无新增错误码：复用 `ROLE_TYPE_MISMATCH(20022)`（ErrorCodeContractTest 既有映射无需变更）。

**测试**：

- `HttpApiPathSnapshotTest`：EXPECTED_PATHS/EXPECTED_SIGNATURES 移除 3 条 extra-roles（198→195）；RETIRED_PATHS 增 3 路径（T-ACCESS-011 同款注解扫描级退役负向防回归）。
- `RoleManageAppServiceImplTest` 新增 2 用例：createRole 拒 GROUP_ROLE（20022，不触类型解析/投影）；updateRole 拒 GROUP_ROLE 目标（20022，不触投影）。
- `UserRoleWriteProjectionPgIT`（真实 PG+Redis）：
  - 适配 2 个读模型用例：组角色事实改 JDBC 直插（`insertGroupRole` 同时补建 ROLE 投影，对齐原 createRole 生产副作用——否则 moveRole 挂子时父投影缺失 fail-closed）；status 翻转改 JDBC 直改 + 直调生产同款失效入口 `subjectDomainService.invalidateRoleCacheByRoles`（PermissionChangeAspect afterCommit 同款）；moveRole/deleteRoles 保持经应用服务（清理通道验证）。
  - 新增 `groupRoleWriteEntriesShouldBeRejectedWithTypeMismatch`：PG 语境 create/update 拒绝（20022）+ 无事实落库 + BASIC_ROLE 创建不受影响（验收「BASIC_ROLE CRUD 不受影响」）。
- 其余 GROUP_ROLE 引用核实均为读模型（特征测试 JDBC 装配、UserRoleQueryServiceImpl 白名单、SubjectDomainService 展开/反查），不动。

**前端**：

- `api/role-manage.ts`：`MANAGEABLE_ROLE_TYPES` 收窄为 `[BASIC_ROLE]`（见当前口径），头部注释与类型清单注释更新（GROUP_ROLE 隐藏、恢复条件）。
- 角色页：面板/hook/API 封装/mock 路由保留不删——树过滤 `isPageVisibleRoleType`、新建下拉（`MANAGEABLE_ROLE_TYPES.map` 派生）随常量自动收窄，GROUP_ROLE 节点不进树、额外角色面板不可达（死代码）。
- 授予页（extra-roles/list 的活跃调用方，执行期核实）：`grant/utils/subject-tree.ts` `filterVisibleTree` 过滤改仅 BASIC_ROLE（GROUP_ROLE 节点整棵裁掉，`SubjectTreePanel` 的 extra-roles 展开调用随之不可达、代码保留）；`subject-tree.spec.ts` 过滤/嵌套用例适配（GROUP_ROLE 改为断言被裁掉）。
- 注释口径更新：角色页 `hook.ts`/`types.ts`/`perms.ts`、授予页 `SubjectTreePanel.vue` 头注（去 GroupRoleAppServiceImpl 行号引用，标注 T-PERM-043 状态与恢复条件）。

**设计回写**：

- `api-contract.md`：§5.2 接口表删 3 行 + 退役说明块（含双事实源与 role_inclusion 立项指引）；§6.10.3 改题「abstract-role/tree」+ 删除 extra-roles 契约正文 + 新增 GROUP_ROLE 写入口收口语义（create/update/sync/full-sync 20022、move/remove 清理通道、assign/revoke 存量行仍可用、读模型冻结）；§6.3.1 白名单语义补 GROUP_ROLE 恒拒例外；§6.8 diff_snapshot `eventType` 枚举移除 `GROUP_ROLE_CHANGE`（全仓核实自登记起零生产方）。
- `implementation.md`：包结构 23→22 AppService（删 GroupRoleAppServiceImpl 行）；§5.2 缓存失效触发点 GROUP_ROLE 块改述（parent_id 经 moveRole 仍可变；extra.basicRoleIds 零写入方 + 遗留指引）。
- `frontend/role-manage.md`：§1 页面定位（BASIC_ROLE 唯一）、§2 布局注记、§3.2 表单字段、§4.1 树操作、§4.2 改「已退役，代码保留」、§4.3 配权、§5 API 表删 3 行、§7 权限接线（ASSIGN/REVOKE 标不可达）、§8 满足清单与范围备注同步。
- `frontend/permission-grant.md`：头部决策 12/13、§1.1 入口表与 P1-2 展开条目、§6.5 状态机 capability 派生、§11 S1 场景、§8 API 表删 GROUP_ROLE 展开行、§12 依赖接口清单去 extra-roles/list。
- 根 `README.md`：未交付清单更新（T-PERM-043 交付口径）+ 新增「技术债遗留登记——GROUP_ROLE 双事实源」段。
- `docs/design/schema/access-service.sql`：不改（user_role DDL 与 role_type 种子冻结保留）。

**验证**：

- 后端：access-service 全量 `mvn test` 0 失败（含 Testcontainers 容器轨道）——含 HttpApiPathSnapshotTest（195 路径快照 + 退役负向）、RoleManageAppServiceImplTest（+2 拒绝用例）、AbstractRoleSyncAppServiceTest（+2 sync/full-sync 拒绝用例）、RetiredSyncTaskEndpointTest（注册表 + MockMvc 运行时负向）、UserRoleWriteProjectionPgIT（9 用例真实容器全绿）。
- 前端：typecheck / vitest / eslint+prettier 全绿。
- 全仓 grep：已删符号（GroupRoleAppService/两个 Req DTO/RoleSummaryResp/两个 Mapper 方法）在 access-service/perm-sdk/gateway/example-service 零残留。

## 评审修复（2026-08-25，含外部评审核实）

内部多维评审与外部评审（codex gpt-5.6-sol xhigh，只读）结论经逐条核实后全部修复：

- **sync/full-sync 通道收口（高，外部评审发现）**：外部同步仍可创建/更新 GROUP_ROLE 并透传写 extra（含 basicRoleIds），与「冻结」目标及遗留登记「无写入方」表述冲突——经用户确认按彻底冻结处置（sync/full-sync 拒绝 20022，先于白名单判定；`AbstractRoleSyncAppServiceTest` 补 2 用例；契约 §6.10.3/§6.3.1 回写；README/本卡「无写入方」措辞修正为「现行无写入方，历史 extra 透传曾可写、存量不保证为空」）。sync 无缓存失效登记为既有遗留（access-service-architecture §12.3，非本任务引入，不在此展开）。
- **文档活引用（高）**：permission-change-log.md 仍把 `GROUP_ROLE_CHANGE` 列为现行枚举（「7 枚举」两处）——修：枚举表删行、7→6；前端 `api/permission-change-log.ts` DiffEventType 联合与 change-log 页 label 映射同步删成员；`mock/permission-change-log.ts` 删 GROUP_ROLE_CHANGE mock 条目（自登记起零生产方，无历史数据兼容问题）。收尾复核又清 3 处「7 枚举」残留（mock 头注、parseDiffSnapshot 注释、设计文档 mock 说明的「14 条/7 种」→「13 条/6 种」）并把该文档两处失效源码行号引用改为描述性文字。
- **permission-grant.md 四处漏改（中）**：GrantContext 派生表、§6.5 状态机两处 capability 派生、§11 S1 验收场景仍以现行口径描述 GROUP_ROLE 展开——修：补不可达/隐藏注记。
- **README 残留（中）**：未交付清单枚举残留「登录锁定临时化」（T-ADMIN-022 已交付，与同句括注矛盾）——修：删条目。
- **退役负向第二层（中）**：验收「模式同 T-ACCESS-011」但只有注解扫描层——修：`RetiredSyncTaskEndpointTest` 注册表 substring 增 `extra-roles` + 3 个 MockMvc 运行时负向用例。发现 `/api/perm/**` 族匿名请求被内部凭证拦截链以 403 pre-handler 拒绝（admin 族为 401），`retiredPathRejected` 匹配器据此为 401/403/404——「无路由」权威证据仍为注册表断言。
- **后端低项**：createRole 方法 javadoc 过时——修；create 判定基准统一（resolve 后按值双保险，理论别名绕过面现实被 type_definition 唯一约束封死）；保留的 extra-roles 前端封装注释标「已退役不可调用」（role-manage.ts 三函数 + 三类型）。
- **测试低项**：unit update 拒绝用例隐式依赖检查顺序——修（lenient MANAGE stub + 注释）；PG update 拒绝补事实未变断言；PG 裸字面量 20022 改枚举引用；快照类 javadoc 基线补 195 注记；PG 注释补 flush 省略腿说明。
- **前端低项**：6 处矛盾注释（perms.ts 创建按钮、grant types.ts 两处、role index.vue 配权入口、RoleForm 校验注释+placeholder、mock 头注）+ SubjectTreePanel emit 注记——修；spec 补「GROUP 父带 BASIC 子整棵裁掉」用例。
- **登记低项**：任务卡 design_refs/看板补 frontend/permission-grant.md；背景节勘误注；api-contract §6.10.3 补 assign/revoke 冻结口径；implementation/role-manage 遗留指引锚名修正（「未交付清单」→「技术债遗留登记」段）。
- **登记不改**：conflict-rule 页 GROUP_ROLE 选项（产品口径，见遗留节）；overview/core-flows 概念文档（运行时语义仍准确）；mock 不仿真 20022（mock 路径已失配）；本任务卡按文档治理口径化（决策结论并入当前口径、去过程性轮次记录）。
- **收尾复核低项（外部复审，全部属实已修）**：`createRole` 方法 javadoc「仅接受 BASIC_ROLE」过度陈述（PERSONAL 未被拒为历史行为）——改为如实列举拒绝集并注明 PERSONAL 现状；`RetiredSyncTaskEndpointTest` 类注释仍称「固定 404」与断言 401/403/404 矛盾——统一为 pre-handler fail-closed 口径；角色页缺「前端隐藏 GROUP_ROLE」直接回归测试——补 `views/system/role/utils/types.spec.ts`（钉 MANAGEABLE_ROLE_TYPES=[BASIC_ROLE] 与 isPageVisibleRoleType 过滤，mock @/utils/http 断链同 grant-store 范式）。
- **外部评审测试说明**：codex 只读沙箱无法执行构建/测试（写入被阻），全绿证据由执行侧回归提供——后端全量 801 用例 0 失败（含 Testcontainers 容器轨道）+ 前端 typecheck / vitest / lint。
