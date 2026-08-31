---
doc_type: task
id: T-PERM-040
title: 4.1 权限授予单资源类型后端支持（operation-permission/list 类型查询 + role-resource-permission/list 类型过滤 + 全部 create 形态 20008 校验）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.3
  - docs/design/permission-center/api-contract.md#§6.4
  - docs/design/permission-center/api-contract.md#§6.5.1
  - docs/design/permission-center/core-flows.md#§6
  - docs/design/frontend/permission-grant.md#§3.2
depends_on:
  - T-PERM-028
  - T-PERM-034
blocks:
  - T-FE-018
acceptance:
  - ~~"operation-permission/list 新增类型查询参数：resourceTypeCode（可选）+ includeGlobalFallback（可选，默认 false）；includeGlobalFallback=false/缺省时 null/缺省 = 全量原始定义（兼容现状）；includeGlobalFallback=true 时后端完成'专属优先、全局回退'合并，响应直接返回当前 resourceTypeCode 最终可用操作集合（同 operationCode 专属定义覆盖全局定义；无专属时全局操作可用），且 resourceTypeCode=null/缺省时结果 = **仅全局操作集合**（不返回其他类型专属定义）"~~ **已失效（2026-08-30 全局操作概念退役，T-PERM-049）**：合并语义整体删除，`resourceTypeCode` 可选过滤维持（归并前 permission-center 代码已带）并经本任务收口（2026-08-31）：指定但类型不存在 -> 空列表 fail-closed（原实现回退全量，与 role-resource-permission/list 口径相反）；原 `domainCode` 死参数删除（DTO 声称过滤、实现忽略、契约未登记、前端零传参——2026-08-31 设计定案）
  - ~~"合并结果每项 resourceTypeCode 明确（专属=当前类型 / 全局=null）；binaryBit/inheritMask 保持十进制字符串线格式（T-PERM-028 基线），前端可直接消费"~~ **已失效（随 includeGlobalFallback 退役）**：操作定义按类型隔离后响应即原始定义集合，binaryBit/inheritMask 十进制字符串线格式维持（T-PERM-028 基线）
  - "role-resource-permission/list 新增 resourceTypeCode（可选，授权矩阵调用时必填）：按类型过滤主权限（depend_on IS NULL + resource_type 匹配）；includeChildren=false 只返回该类型主权限；includeChildren=true 返回该类型主权限及其全部子权限（子权限可能跨资源类型，按 depend_on 挂父返回，不能按子记录自身类型过滤）；resourceTypeCode=null 兼容既有调用方；对应 Mapper 批量查询（按类型过滤主权限，避免 N+1）"——**已落地（2026-08-31 收口）**：原实现为单次全量查询 + 内存过滤（功能语义等价），经设计定案（2026-08-31）按验收字面补专用 Mapper 查询：主权限 `selectValidMainByRoleIdAndResourceType`（depend_on IS NULL + resource_type + 角色下沉 SQL）+ 子权限 `selectValidByRoleIdAndDependIds`（按 depend_on 批量挂父，双重约束——depend_on ∈ 主权限集合 且 abstract_role_id = 目标角色——SQL 层满足，复用既有查询零新增）；childCount 计数源 = 主权限 + 其全部直接子权限（includeChildren=false 时仍为真实计数）；resourceTypeCode 指定但类型不存在 -> 空列表
  - "apply-grant-plan creates 逐项校验 operationCode 是否适用于记录的资源类型：与 operation-permission/list 合并**共用同一解析实现**（禁止两套逻辑），判定基于**有效（未停用）定义**；不匹配 -> 20008 RESOURCE_TYPE_OPERATION_MISMATCH（错误码已存在，复用）；operationCode=null（组合位）跳过单码校验但**仍须校验位集**：grantedBits 每个置位必须 ⊆ 该类型合并后适用操作集合的 binaryBit 并集，否则 -> 20008；**覆盖全部新记录形态：creates[].key（主权限）、creates[].children[] 嵌套子权限（按子记录自身 resourceTypeCode+operationCode 执行同一规则）、parentPermissionId 挂父的 create**"——**已落地**：`PermissionGrantPlanDomainServiceImpl.resolveOperation` 类型隔离判定（T-PERM-049 收窄后终态），三种 create 形态全经 `toPermission` -> resolveOperation（码存在于其他类型但本类型无专属定义 -> 20008；任何类型都不存在 -> 20005 OPERATION_NOT_FOUND）；「单一解析实现」随合并语义退役收窄为同一份类型专属定义数据源（`selectByTenantAndResourceType`，delete_flag=0 即有效口径——operation_permission 无 enabled 列）；~~组合位子句已失效~~：validateKeyShapes 要求 operationCode 必填非空白（契约「MANUAL 新授权一行只写一个操作位，不接受组合位」），组合位 create 形态不存在
  - "校验测试矩阵"——**已补齐（2026-08-31）**：~~专属操作命中/无专属回退全局/同码专属+全局并存取专属~~（随全局操作退役失效；正向命中由既有 SuccessPaths create 用例覆盖）+ OperationApplicabilityValidation 四用例（主 key 20008 / children[] 嵌套 20008 / parentPermissionId 挂父 20008 / 未知码 20005 区分锁定）+ PermissionGrantAppServiceImplTest 五用例（类型命中含跨类型子权限挂父并断言专用 SQL 调用 / 未知类型空列表零查询 / includeChildren=false 仅主权限但 childCount 真实 / 类型无命中空列表不查子 / null 兼容全量走既有查询）+ OperationAppServiceImplTest 两用例（未知类型空列表 fail-closed / 已知类型解析后下发 Mapper）
  - "design_writeback（回写门禁）：api-contract §5.3（请求参数表 + 合并语义）/ §6.4（resourceTypeCode + includeChildren 跨类型规则）/ §6.5.1（creates 校验规则含嵌套形态）、core-flows.md §6（步骤表 + 操作适用性校验）；permission-grant.md §12 第 14 条核对一致"——**已完成（2026-08-31）**：api-contract §5.3 参数表补未知类型空列表 + domainCode 删除登记、§6.4 标已落地含 SQL 下沉口径、§6.5.1 标已落地含 20008/20005 区分；core-flows §6 步骤表（步骤 2/4/6）与 L127 适用性校验经核对已是终态口径无需改；permission-grant.md §12 第 14 条重写为终态（含退役与删除登记）
design_writeback:
  required: true
  status: done
last_updated: 2026-08-31
---

> **退役注记（2026-08-30，T-PERM-049）**：本任务的 `includeGlobalFallback` 合并参数与「专属优先、全局回退」语义已随全局操作概念整体退役删除（外部复审 P1 越权结论触发的设计定案——授权行只存 resource_type+granted_bits，全局位与专属位同值时授权身份不可区分）；`resourceTypeCode` 类型过滤与 role-resource-permission/list 类型过滤等其余交付物不受影响。

# T-PERM-040 权限授予单资源类型后端支持

> 状态：done（2026-08-31 收口）
> 依赖：T-PERM-028（operation-permission bigint 十进制字符串线格式基线）、T-PERM-034（apply-grant-plan 唯一写入口 + prevalidateGrantPlan 统一预检基线）
> 前置验收：见 acceptance（DoD 门禁）

## 背景

权限授予页（v3，T-FE-036）定稿**单类型矩阵上下文**：矩阵一次只呈现一个 `resourceTypeCode`，操作列 = 当前类型专属操作 ∪ 全局回退。为让前端不再重复实现"专属优先、全局回退"领域规则，后端提供：

1. `operation-permission/list` 类型查询参数（`resourceTypeCode + includeGlobalFallback`），true 时返回合并后集合；
2. `role-resource-permission/list` 类型过滤（`resourceTypeCode`，含跨类型子权限按 depend_on 挂父规则，2026-08-05 评审扩展）；
3. `apply-grant-plan` creates 写入校验（operationCode 适用于记录的资源类型，不匹配 → 20008）。

> 背景第 1 条的合并语义已随 T-PERM-049 退役（见上方退役注记）；操作列 = 当前类型专属操作（类型隔离后无回退轨）。

## 范围

1. `OperationPermissionAppService`（或等价服务）list 支持 `resourceTypeCode + includeGlobalFallback` 参数：includeGlobalFallback=true 时按"专属优先、全局回退"合并（同 `operationCode` 专属定义优先，被覆盖的全局定义剔除；无专属时全局定义保留）；响应结构不变（OperationPermissionResp items）。~~（已随 T-PERM-049 退役）~~
2. 合并规则抽为单一解析方法（如 `resolveEffectiveOperations(tenantId, resourceTypeCode)`），**list 合并与 apply-grant-plan 校验共用**。~~（已随 T-PERM-049 退役；两处共用同一份类型专属定义数据源）~~
3. `role-resource-permission/list` 支持 `resourceTypeCode` 过滤（主权限按 `depend_on IS NULL + resource_type` 过滤）；`includeChildren=true` 时子权限按 `depend_on` 挂在该类型主权限下返回（子权限跨类型不按自身类型过滤）；对应 Mapper 批量查询（按类型过滤主权限，避免 N+1）。
4. `prevalidateGrantPlan` creates 段补 operationCode 适用性校验：**覆盖主权限 key、children[] 嵌套、parentPermissionId 挂父三种形态**（不匹配 → 20008，组合位按位集校验）。
5. 测试覆盖（见 acceptance 第 5 条）。

## 完成记录（2026-08-31 收口）

**调研先行核实（不重做）**：三块交付物实现面在收口前均已先行存在——① `operation-permission/list` 的 `resourceTypeCode` 可选过滤归并前 permission-center 代码已带（`OperationAppServiceImpl.listOperations` + `selectByTenantAndResourceType`）；② `listPermissions` 的类型过滤 + includeChildren 跨类型子权限挂父语义已实现（原为单次全量查询 + 内存过滤）；③ creates 20008 校验由 `resolveOperation` 承载（T-PERM-049 收窄后即类型隔离判定终态），三种 create 形态全经 `toPermission` 覆盖。真实缺口为一处行为不一致、一个死参数、测试矩阵缺失与 🔧 标记收口。

**本任务三项设计定案（2026-08-31）**：

1. **未知 resourceTypeCode 统一空列表 fail-closed**：`listOperations` 原实现类型解析返回 null 时 SQL 过滤条件被跳过、回退返回全量操作列表，与 `listPermissions`（空列表）口径相反——统一为空列表（读接口「查不到就空」既有模式；类型下拉来自类型定义页，正常流程不会传未知类型）。
2. **类型过滤下沉专用 Mapper 查询**：验收字面要求「对应 Mapper 批量查询」，原内存过滤虽无 N+1 但经定案按字面落地——新增 `selectValidMainByRoleIdAndResourceType`（tenant + role + depend_on IS NULL + resource_type + delete_flag=0），子权限复用既有 `selectValidByRoleIdAndDependIds`（双重约束 SQL 层满足）；childCount 计数源 = 主权限 + 全部直接子权限，includeChildren=false 时计数仍真实。
3. **`OperationListReq.domainCode` 死参数删除**：DTO 注释声称「用于过滤」但实现从未使用、契约 §5.3 参数表未登记、前端全部调用点零传参——删除（DTO 字段 + Service 签名 + Controller + 前端 `OperationListQuery` 类型同步）。

**测试补齐**：`PermissionGrantPlanDomainServiceImplTest` 新增 `OperationApplicabilityValidation` 四用例（主 key / children[] 嵌套 / parentPermissionId 挂父三种形态 20008 + 未知码 20005 区分锁定——旧实现若误把异类型码当未知码或放行均失败）；`PermissionGrantAppServiceImplTest` 新增五用例（类型命中含跨类型子权限挂父 + 专用 SQL 调用断言 / 未知类型空列表零查询 / includeChildren=false 仅主权限但 childCount 真实 / 类型无命中不查子 / null 兼容全量不走专用 SQL）；`OperationAppServiceImplTest` 两用例收口（未知类型空列表——修复前该用例因回退全量必然失败、已知类型解析后下发 Mapper）+ 既有两用例签名同步。

**文档回写**：见 acceptance 第 6 条。

**回归**：access-service 双轨全绿（单元轨 990 + 容器轨 115，0 失败 0 错误；单元轨 +11 为本任务新增用例）；前端 vue-tsc 0 错 / vitest 204 全过 / eslint 0 问题（含改动文件）。

**解锁**：T-FE-018（权限授予页联调首期）全部后端依赖就绪。
