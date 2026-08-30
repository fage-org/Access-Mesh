---
doc_type: task
id: T-PERM-040
title: 4.1 权限授予单资源类型后端支持（operation-permission/list 类型查询合并 + role-resource-permission/list 类型过滤 + 全部 create 形态 20008 校验）
status: proposed
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
  - "operation-permission/list 新增类型查询参数：resourceTypeCode（可选）+ includeGlobalFallback（可选，默认 false）；includeGlobalFallback=false/缺省时 null/缺省 = 全量原始定义（兼容现状）；includeGlobalFallback=true 时后端完成'专属优先、全局回退'合并，响应直接返回当前 resourceTypeCode 最终可用操作集合（同 operationCode 专属定义覆盖全局定义；无专属时全局操作可用），且 resourceTypeCode=null/缺省时结果 = **仅全局操作集合**（不返回其他类型专属定义）"
  - "合并结果每项 resourceTypeCode 明确（专属=当前类型 / 全局=null）；binaryBit/inheritMask 保持十进制字符串线格式（T-PERM-028 基线），前端可直接消费"
  - "role-resource-permission/list 新增 resourceTypeCode（可选，授权矩阵调用时必填）：按类型过滤主权限（depend_on IS NULL + resource_type 匹配）；includeChildren=false 只返回该类型主权限；includeChildren=true 返回该类型主权限及其全部子权限（子权限可能跨资源类型，按 depend_on 挂父返回，不能按子记录自身类型过滤）；resourceTypeCode=null 兼容既有调用方；对应 Mapper 批量查询（按类型过滤主权限，避免 N+1）"
  - "apply-grant-plan creates 逐项校验 operationCode 是否适用于记录的资源类型：与 operation-permission/list 合并**共用同一解析实现**（禁止两套逻辑），判定基于**有效（未停用）定义**；不匹配 -> 20008 RESOURCE_TYPE_OPERATION_MISMATCH（错误码已存在，复用）；operationCode=null（组合位）跳过单码校验但**仍须校验位集**：grantedBits 每个置位必须 ⊆ 该类型合并后适用操作集合的 binaryBit 并集，否则 -> 20008；**覆盖全部新记录形态：creates[].key（主权限）、creates[].children[] 嵌套子权限（按子记录自身 resourceTypeCode+operationCode 执行同一规则）、parentPermissionId 挂父的 create**"
  - "校验测试矩阵：专属操作命中 / 无专属回退全局 / 同码专属+全局并存取专属（全局定义不构成适用依据）/ 其他类型专属操作写入 -> 20008 / 组合位记录跳过单码校验（位集合法） / 组合位含非适用操作位 -> 20008 / **嵌套子权限（children[]）使用不适用操作 -> 20008 / parentPermissionId 挂父的 create 使用不适用操作 -> 20008** / resourceTypeCode=null+includeGlobalFallback=true 仅返回全局集合（不含其他类型专属定义）/ includeGlobalFallback 缺省行为回归（不破坏既有调用方）/ **role-resource-permission/list 类型过滤：resourceTypeCode 命中 / 未命中空列表 / includeChildren=true 跨类型子权限按 depend_on 挂父返回 / resourceTypeCode=null 兼容既有调用方**"
  - "design_writeback（回写门禁）：api-contract §5.3（请求参数表 + 合并语义）/ §6.4（resourceTypeCode + includeChildren 跨类型规则）/ §6.5.1（creates 校验规则含嵌套形态）、core-flows.md §6（步骤表 + 操作适用性校验）；permission-grant.md §12 第 14 条核对一致"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-05
---

> **退役注记（2026-08-30，T-PERM-049）**：本任务的 `includeGlobalFallback` 合并参数与「专属优先、全局回退」语义已随全局操作概念整体退役删除（外部复审 P1 越权结论触发的设计定案——授权行只存 resource_type+granted_bits，全局位与专属位同值时授权身份不可区分）；`resourceTypeCode` 类型过滤与 role-resource-permission/list 类型过滤等其余交付物不受影响。

# T-PERM-040 权限授予单资源类型后端支持

> 状态：proposed（2026-08-03 单类型矩阵上下文定稿立项，2026-08-03 评审扩展：+role-resource-permission/list 类型过滤 + 嵌套子权限 20008 全覆盖；ID 040 原口头引用为"幂等中间件实现任务"，随第十四轮收窄取消、从未在看板登记，本任务复用该 ID，相关历史措辞已改为"幂等中间件实现取消（未登记看板）"）
> 依赖：T-PERM-028（operation-permission bigint 十进制字符串线格式基线）、T-PERM-034（apply-grant-plan 唯一写入口 + prevalidateGrantPlan 统一预检基线）
> 前置验收：见 acceptance（DoD 门禁）

## 背景

权限授予页（v3，T-FE-036）定稿**单类型矩阵上下文**：矩阵一次只呈现一个 `resourceTypeCode`，操作列 = 当前类型专属操作 ∪ 全局回退。为让前端不再重复实现"专属优先、全局回退"领域规则，后端提供：

1. `operation-permission/list` 类型查询参数（`resourceTypeCode + includeGlobalFallback`），true 时返回合并后集合；
2. `role-resource-permission/list` 类型过滤（`resourceTypeCode`，含跨类型子权限按 depend_on 挂父规则，2026-08-05 评审扩展）；
3. `apply-grant-plan` creates 写入校验（operationCode 适用于记录的资源类型，不匹配 → 20008）。

## 范围

1. `OperationPermissionAppService`（或等价服务）list 支持 `resourceTypeCode + includeGlobalFallback` 参数：includeGlobalFallback=true 时按"专属优先、全局回退"合并（同 `operationCode` 专属定义优先，被覆盖的全局定义剔除；无专属时全局定义保留）；响应结构不变（OperationPermissionResp items）。
2. 合并规则抽为单一解析方法（如 `resolveEffectiveOperations(tenantId, resourceTypeCode)`），**list 合并与 apply-grant-plan 校验共用**。
3. `role-resource-permission/list` 支持 `resourceTypeCode` 过滤（主权限按 `depend_on IS NULL + resource_type` 过滤）；`includeChildren=true` 时子权限按 `depend_on` 挂在该类型主权限下返回（子权限跨类型不按自身类型过滤）；对应 Mapper 批量查询（按类型过滤主权限，避免 N+1）。
4. `prevalidateGrantPlan` creates 段补 operationCode 适用性校验：**覆盖主权限 key、children[] 嵌套、parentPermissionId 挂父三种形态**（不匹配 → 20008，组合位按位集校验）。
5. 测试覆盖（见 acceptance 第 5 条）。

## 完成记录

（待实现后填写）
