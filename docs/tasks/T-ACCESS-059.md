---
doc_type: task
id: T-ACCESS-059
title: （ADM-T04）新端点、快照与 SDK/网关链路
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §8.2/§8.4
depends_on:
  - T-ACCESS-057
  - T-ACCESS-058
blocks: []
acceptance:
  - "interface-admission / interface-admission-snapshot 版本化端点+DTO（InterfaceAdmissionSnapshot：schemaVersion/tenantId/subject/serviceCode/generatedAt/expiresAt/configGeneration/routes[]/operationCandidates[]/authorizationStage/finalCheckRequired）；PermissionClient 显式可信服务模式调用；新模式失败不回落旧 API:ACCESS"
  - "网关本地判定序：校验模式/版本/时效→完整路由匹配与歧义检测（N14/N15：多匹配异要求 AMBIGUOUS 阻断、无注册拒绝且 ALL 不放行未注册）→唯一要求→评该要求条件分支（无条件或通过即 MAY_ENTER；需远端求值回源；其余拒绝）；坏条件显式不可用不变无条件；fail-closed 沿现行（缺快照/未知 schema/远端不可用=技术错误，不 stale-allow）"
  - "N12：同事实/定义/时刻/IP 下本地投影与在线准入一致；N21：快照构建中配置代次改变废弃重建（configGeneration 拍板限定语义）、旧在途不覆盖新代次；N22：新旧 schema/命名空间隔离；N28：权限服务自身认证边界独立、无准入递归、缺映射不自动公共"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-ACCESS-059 （ADM-T04）新端点、快照与 SDK/网关链路

## 背景

设计 §8.2/§8.4（报告临时编号 ADM-T04）。路由匹配从该服务完整已启用路由集取全部命中，不能按用户权限挑较弱规则；已确定公共/认证白名单维持独立、无 requiredPermission 不自动等于公共路由。

## 范围

- access-service 端点与快照构建（候选从新 OPERATION_ADMISSION+FACTS 构建，绝不经旧 GRANT_LIST 全集合 PERM_MUTEX；原始 GrantFact 不去重合并、最终投影按 type-operation+条件身份+候选类别归并）；SDK PermissionClient/Filter/Matcher；网关本地缓存与回源。

## 非目标 / 遗留

- **Q-040 对齐注记（2026-09-25 立项核对）**：新端点为 M2M 调用，其身份形态（per-service 凭证 vs 旧全局密钥）须与 Q-040（两套服务身份统一）收敛方向对齐，避免扩大旧密钥面；本卡不统一存量 auth 查询族身份。
- 失效/TTL 边界在 T-ACCESS-060；业务侧最终检查与切换在 T-ACCESS-061。
