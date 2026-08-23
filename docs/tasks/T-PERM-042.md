---
doc_type: task
id: T-PERM-042
title: 权限引擎显式资源 API 与实例门禁修复
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
  - docs/design/access-service-architecture.md
depends_on: [T-ACCESS-016, T-ACCESS-017]
blocks: [T-ORG-001]
acceptance:
  - "引擎落地 T-ACCESS-016 定稿 API：hasPermissionByCode / getDeniedResourceCodes 对外；getDeniedEntityIds / hasPermissionByEntityId 仅引擎内部或已完成解析的调用方；泛型 <ID>、Object resourceId、toLongId() 运行时猜测全部删除；validateBatch 等抛异常便捷方法从引擎删除（引擎纯查询，异常由调用方显式抛出：admin 域经 AdminPermissionValidator 门面、permission 域 AppService if-throw）"
  - "全部 USER/ROLE 实例门禁调用点改为业务编码语义：生产代码 9 处 getDeniedIds 外部调用逐处改造（含 UserManageAppServiceImpl、RoleManageAppServiceImpl 已核实的 abstract id 错传点），改造清单在任务卡登记并以全量 grep 复核清零"
  - "USER/ROLE 等业务对象门禁调用点与跨服务 SDK 不使用 resource_entity.id，统一业务编码；资源实体管理类接口（资源树、API 映射、资源依赖、权限树——现有 ApiMappingResp/ResourceDependencyResp/ResourcePermissionTreeResp 等）保留 resource_entity.id 现状，不在本任务重构"
  - "code → entity 解析统一下沉 TypeResolutionService 批量方法（无 N+1）"
  - "授权页 3 个读接口补齐类型级 VIEW 门禁（T-ACCESS-016 §14.5 终态，随本任务落地）：abstract-role/tree→ROLE:VIEW、resource-entity/tree→RESOURCE:VIEW、operation-permission/list→OPERATION:VIEW（现状无业务门禁，bootstrap 最小种子依赖该终态；permission-condition/list 维持无门禁——api-contract §5.6 产品确认条件列表全租户开放，不在补齐范围）"
  - "USER/ROLE 实例门禁错参的正确预期测试在本任务内新增（测试自装配 resource_entity 投影 fixtures，不依赖生产写路径投影）并同提交转绿；T-ACCESS-017 特征测试保持全绿。真实 USER/ROLE 写路径产生投影后的实例授权端到端转绿归 T-ACCESS-019"
  - "单测 + PostgreSQL Testcontainers 双层验证"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-23
---

# T-PERM-042 权限引擎显式资源 API 与实例门禁修复

## 背景

引擎当前以 `<ID> Set<ID> getDeniedIds(...)` 泛型形态接受资源 ID 并直接按 `resource_entity.id` 匹配实例授权（`PermQueryEngine` 实例路径）；生产代码 9 处外部调用中至少 6 处 USER/ROLE 门禁把 `abstract_user.id`/`abstract_role.id` 直接传入，ID 空间错位导致类型级 scopeAll 正常而实例级授权恒拒绝/误判，且三个空间同为 Long，误传可能命中另一空间合法记录。已确认方案：不做双 ID 断言与 Resolver 加固，直接按 T-ACCESS-016 定稿的显式 API 重塑整个实例权限契约。

## 范围

- `PermQueryEngine` 及门禁入口（`AdminPermissionValidatorImpl` 等）API 拆分与实现。
- 全部 USER/ROLE 实例门禁调用点改造（以全量 grep getDeniedIds 为准，含 6 处已知错传点）。
- code → entity 批量解析下沉 `TypeResolutionService`（复用规范 §8.4，禁 N+1）。

## 当前口径

- 对外语义统一业务编码（String code）；entityId 只在引擎内部流转。
- 引擎纯查询：`hasPermissionByCode`/`getDeniedResourceCodes` 对外、`getDeniedEntityIds`/`hasPermissionByEntityId` 内部；抛异常便捷方法从引擎删除，异常由调用方显式抛出（admin 域经 `AdminPermissionValidator` 门面、permission 域 AppService if-throw，见 implementation §3.1）。
- 实施顺序上先于资源类型收敛（T-ACCESS-018），本任务仍使用现行类型码，类型切换由 T-ACCESS-018 承担，两者独立提交。

## 非目标 / 遗留

- 不动 hasPermission 现有 code 解析正确的调用方语义（仅统一入口形态）。
- 不改缓存目录与 TTL；不动 scope_all/scopeMode 边界映射。
- 主体 ID 数值统一（会话/审计/user_role）不在本任务（归 T-ORG-001）。

## 实施记录（2026-08-23）

### 改造清单（全量 grep 复核清零：`getDeniedIds` / `validateBatch` / `toLongId` / `.hasPermission(` 生产与测试代码零残留）

**引擎（`PermQueryEngine` + `PermQuery`）**

- 删除 `hasPermission(Object)` / `<ID> validateBatch` / `<ID> getDeniedIds` / `toLongId`（引擎纯查询，不抛 `SecurityException`）。
- 新增对外业务编码轨：`hasPermissionByCode(tenantId, subjectId, type, String code, op)`（null code=类型级）、`getDeniedResourceCodes(...)`（一次 `TypeResolutionService.batchResolveResourceIds` 批量解析，无 N+1；未解析 code fail-closed 进入拒绝集）。
- 新增 entityId 轨（仅引擎内部或已完成解析的调用方）：`hasPermissionByEntityId(...)`、`getDeniedEntityIds(...)`（直查 `resource_entity.id`，零 code 解析）。
- `PermQuery` 新增 `forValidateByEntityId` 工厂（与 `forValidate` 同形态，实例目标直接给 entity id）。

**9 处 `getDeniedIds` 外部调用**

| 调用点 | 改造 |
|---|---|
| `UserManageAppServiceImpl#deleteUsers`（USER 错传点） | → `getDeniedResourceCodes`，code = `abstract_user.id.toString()`（§12.3） |
| `UserManageAppServiceImpl#assignRole`（ROLE 错传点） | → `getDeniedResourceCodes`，code = roleId 字符串化 |
| `UserManageAppServiceImpl#assignRolesBatch`（ROLE 错传点） | 同上（单目标） |
| `UserManageAppServiceImpl#revokeRolesBatch`（ROLE 错传点） | 同上 |
| `RoleManageAppServiceImpl#deleteRoles` 根角色（ROLE 错传点） | → `getDeniedResourceCodes` |
| `RoleManageAppServiceImpl#deleteRoles` 子孙角色（ROLE 错传点） | 同上 |
| `ResourceManageAppServiceImpl#deleteResources`（RESOURCE，entity id 语义正确） | → `getDeniedEntityIds`（语义不变） |
| `AdminPermissionValidatorImpl#checkBatchInstanceLevel` | 手工 code→entity 解析删除，下沉引擎 → `getDeniedResourceCodes`（门面三方法形态不变，admin contract §2 终态） |
| `OrgVisibilityQueryServiceImpl#filterVisibleOrgIds` | 同上（未解析组织经引擎归入拒绝集 → 不可见，fail-closed 等价） |

**5 处 `validateBatch` → 调用方显式 if-throw**

- `ConflictRule` / `Condition` / `Dependency` / `TypeDefinition` 4 处（Long PK）→ `getDeniedEntityIds` + if-throw（严格保持今日直查语义；用户决策 2026-08-23）。
- `ResourceManageAppServiceImpl#removeApiMappingsByIds`（SERVICE 业务码）→ `getDeniedResourceCodes` + if-throw（修复原 `toLongId` 对非数字 serviceCode 恒拒缺陷，bootstrap §14.4 实例级 `SERVICE:MANAGE_API_MAPPING` 依赖该终态）。

**`hasPermission` 全量迁移（基线 73 处 = 70 处 code 轨 + 3 处 entityId 轨；评审 P2 计数校正）**

- 70 处 → `hasPermissionByCode`（null 类型级原样；Long PK 调用方 `String.valueOf` 保语义；SERVICE String 业务码直传）。
- 3 处 RESOURCE 实例门禁 → `hasPermissionByEntityId`（用户决策 2026-08-23，implementation §3.1 定稿示例意图）：`ResourceManageAppServiceImpl#updateResource` / `#moveResource`、`PermissionViewAppServiceImpl#getResourcePermissions`——资源实体管理链路按 §12.3 边界保留 entity id，且修复原「entity id 被 String.valueOf 后当 code 解析、实例级恒不命中」缺陷（实例级授权自此真正生效）。

**授权页 3 读接口门禁补齐（architecture §14.5 终态）**

- `RoleManageAppServiceImpl#getRoleTree` → 类型级 `ROLE:VIEW`；`ResourceManageAppServiceImpl#getResourceTree` → `RESOURCE:VIEW`；`OperationAppServiceImpl#listOperations` → `OPERATION:VIEW`（照 `listTypes` 模式：`requireSubjectId` + `hasPermissionByCode(null)` + `SecurityException`）。`permission-condition/list` 维持无业务门禁。

**测试**

- 单测层新增：`PermQueryEngineTest` 3 用例（业务编码轨解析/无角色 fail-closed/entityId 轨零解析）；`RoleManageAppServiceImplTest` / `ResourceManageAppServiceImplTest` / `OperationAppServiceImplTest` 3 读接口门禁正反用例。
- PG（Testcontainers）层新增 `InstanceGateBusinessCodePgIT` 3 用例：USER/ROLE 实例门禁业务编码命中、无投影/未授权/无角色 fail-closed、错参防线（显式高位投影 id 装配下 abstract id 落 entity 空间全量拒绝）——fixtures 自装配 `resource_entity` 投影（显式 900xxx id 段，杜绝跨空间撞值干扰断言），不依赖生产写路径投影（端到端归 T-ACCESS-019）。
- 既有测试迁移：`AdminPermissionValidatorImplTest`（按新语义重写 5 用例）、`OrgVisibilityQueryServiceImplTest`、`OperationLogRuntimeContextAppServiceTest`、`ResourceDeletePermChangeRegistrationTest`、`UserManageAppServiceImplTest`、`PermissionCharacterizationPgIT`、`PermQueryEngineTest` 及 8 个文件 mock 形态机械迁移。

### 验证证据（2026-08-23，本地 Docker Desktop 4.87 用户级安装）

- 全量 `mvn test -pl access-service`（surefire 双 execution）**BUILD SUCCESS**：单测 execution 675 项（2 既有 skip）+ 容器 execution 64 项全绿（含 T-ACCESS-017 特征测试 4/4、`InstanceGateBusinessCodePgIT` 4/4）。评审修复后复验同命令全绿（评审新增：单测「scopeAll+无投影」+ 门面单点 4 例、PG「scopeAll 放行无投影编码」1 例）。
- 定向 `-Dtest=PermissionCharacterizationPgIT,InstanceGateBusinessCodePgIT`：8/8 全绿（评审修复轮新增 scopeAll 用例后为 4+4；复评 P3 校正，原记录 7/7 为新增前数字未同步）。
- grep 复核：`access-service` main/test 对 `getDeniedIds`/`validateBatch`/`toLongId`/`.hasPermission(` 零残留（引擎删除说明的设计文档与 T-ACCESS-017 卡内历史记录除外）。

### 文档回写

- 任务卡本节；`docs/tasks/README.md` 与 `docs/plans/product-vertical-slice-plan.md` 状态行 → done。
- `implementation.md` §3.1「与现 `getDeniedIds` 相同管线」措辞现实化（`getDeniedEntityIds`）；§7.2/§7.6 已是 T-ACCESS-016 终态无改动。
- `.agents/skills/permission-query-pipeline/SKILL.md` 与 `.claude/skills/` 镜像、`accessmesh-patterns`（两份）、`AGENTS.md`、`overview.md`、`project-rules.md`、`implementation.md` §4.2/§7.6 的旧 API 引用统一清扫为显式四方法（评审 P2：design_writeback 声明与活动文档现状不符）。

## 评审修复记录（2026-08-23，AI 评审三项全部核实属实）

- **P1 `getDeniedResourceCodes` 先解析 code 后查 scopeAll，无投影编码被误拒**：重构为 implementation §3.1 规定管线——先角色/类型/操作解析与类型级 scopeAll 检查（命中含条件/冲突评估通过则全部允许，**包括尚无投影实体的编码**，与 `hasPermissionByCode` 的 scopeAll 提前返回语义一致），未命中才批量 code→entity 解析与实例级查询；抽出共享私有步骤 `passesScopeAll` / `computeInstanceDenied`（`getDeniedEntityIds` 行为不变）。原实现的两个缺陷：scopeAll 命中时未解析 code 仍被拒；全部 code 未解析时 `getDeniedEntityIds` 空集合短路导致完全不检查 scopeAll。补测试：单测「scopeAll + 全无投影 → 空拒绝集 + 零 code 解析」、PG IT「scopeAll + 有投影/无投影混合编码全部放行」；无角色用例随管线顺序更新为「不解析 code 直接全拒」。
- **P2 `AdminPermissionValidatorImpl#checkAndThrow` 仍走 `forAuthCheck + query`**：按 admin contract §2 终态收敛到 `engine.hasPermissionByCode`（forValidate 语义），消除与显式 API 并存的两套单点门禁语义；补单点门禁测试 4 例（checkInstanceLevel 通过/拒绝、checkTypeLevel null code、操作者主体缺失 fail-closed）。
- **P2 计数校正**：`hasPermission` 迁移基线为 73 处（70 code 轨 + 3 entityId 轨），原任务卡误记为 70 处（67+3）。

## 二轮复评修复记录（2026-08-23）

- **P2 `.claude/rules/` 编码规则残留旧 API**：`permission-center-coding-standards.md`（§1/§2/异常示例/§16/检查表共 6 处）与 `testing-standards.md`（2 处 mock 示例）统一改为四显式入口；§2 权限查询铁律重写为业务编码轨 / 实体 ID 轨双轨表述并注明旧 API 已删除。首轮清扫遗漏了 `.claude/rules/` 目录。
- **P3 定向测试计数**：7/7 → 8/8（首轮记录为评审修复新增 scopeAll 用例前的数字，未同步）。