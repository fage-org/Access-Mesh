---
doc_type: task
id: T-PERM-049
title: 全局操作概念整体退役——操作位空间按类型完全隔离（DDL CHECK 焊死；外部复审三轮 P1 越权根治）
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.3
  - docs/design/permission-center/implementation.md#§4
  - docs/design/schema/access-service.sql
  - docs/design/frontend/permission-grant.md
depends_on: []
blocks: []
acceptance:
  - "外部复审 P1（2026-08-30）：全局操作与类型专属操作同位异码时互相授权——授权行只存 resource_type + granted_bits（不存操作 ID），全局位与专属位同值时授权身份不可区分（如 USER:MANAGE=16 + 全局 EXPORT=16，查 EXPORT 会把 MANAGE 授权计入，反向同样无法区分）；DDL 仅约束类型内 (tenant, resource_type, binary_bit) 唯一，全局间位与全局 vs 专属跨作用域均无约束"
  - "设计定案（用户决策，三问均指向同一方案）：全局操作概念整体退役，每个资源类型的操作完全独立——结构上消灭该问题类（同类型同位不异码由既有 uk_typed_bit 保证，跨类型同位本就互不影响），不需要新错误码/写入口校验/类型预置检查；不采纳「授权记录改存操作 ID」模型改造（引擎位运算 SQL/缓存快照/SDK 全链路面，过度设计）"
  - "后端退役面：PermQueryEngine.resolveBitMasks 回归纯类型专属位（批量冷缓存口径保留）、resolveOperationId/batchResolveOperationIds 删全局回退、PermissionGrantDomainServiceImpl checkCanGrant/buildOperationIndex 去全局合并、PermissionGrantPlanDomainServiceImpl resolveOperation/resolveOperationByBit 收窄类型专属、OperationResolutionDomainService+Impl（mergeGlobalFallback）与 selectGlobal/selectGlobalByCode/selectGlobalByCodes mapper 删除、operation API 全局轨退役（OperationKeyReq/OperationUpdateReq resourceTypeCode @NotBlank、OperationListReq 删 includeGlobalFallback）"
  - "DDL：ck_operation_permission_resource_type_required CHECK (resource_type IS NOT NULL) 在数据层焊死全局行（连手写 SQL 也插不进）；uk_operation_permission_global 随之删除；种子本就无全局行，无迁移成本"
  - "前端退役面：resource-operation.ts 参数/键类型收窄、source-chain.ts 合并逻辑回归按类型隔离（MergedOperation/CellSource 去 globalFallback/globalOperation 字段）、GrantDialog 全局操作组与 allScopeType 紧凑类型选择器/专属 watch 整套拆除、MatrixCell/GrantMatrixPanel 标注下线、hook.ts 调用去参、fixtures 权威用例集 6→5（global-fallback 用例移除）"
  - "文档：api-contract §5.3 合并语义/键轨退役 + §6.5.1 适用性校验收窄 + §6.6 投影轨登记失效、core-flows/implementation/architecture/runbook/permission-grant.md/resource-operation.md/resource-dependency.md 同步、T-PERM-040 includeGlobalFallback 设计定案加退役注记、T-PERM-037 投影轨全局位条目标记失效"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-30
---

# T-PERM-049 全局操作概念整体退役

> 状态：done（2026-08-30 单日收口，外部复审三轮触发的设计定案）
> 依赖：无

## 背景

外部复审（T-PERM-034 第三轮）发现 P1：全局操作与类型专属操作同位异码时互相授权。核实链条：schema `uk_operation_permission_typed_bit` 仅按 (tenant, resource_type, binary_bit) 隔离位值；全局行无位约束（连全局间都无）；操作写入口位值零校验。缓和因素：种子无全局行、create 接口 resourceTypeCode 必填导致 **API 根本创建不了全局操作**——整套「专属优先、全局回退」机制（引擎合并、契约语义、前端标注、Golden 用例）服务的是一个不可达的功能。

用户定案：不要全局操作，每个资源的操作完全独立。该方案结构上消灭问题类（无需写入口不变量三件套、无需新错误码），并顺带删除大量无生产者的机制复杂度。

## 实施记录（2026-08-30）

- 后端主代码 + DDL CHECK（acceptance 3/4 条目全清单）；`OperationResolutionDomainService` 接口与实现整体删除。
- 测试：PermQueryEngineTest 二轮全局修复用例随场景删除（16→15，批量冷缓存用例保留并改纯类型轨）、OperationAppServiceImplTest 合并/全局轨用例删除（11→8）、PermissionGrantDomainServiceImplTest 全局回退用例删除、PermissionGrantPlanDomainServiceImplTest globalView 助手改类型轨 dataView、GoldenFixturePgIT 断言 6→5 用例 + 种子类型必填化、ResourceOperationKeyPgIT 全局轨用例改双类型轨并**新增真库 CHECK 拒绝断言**（插入 resource_type=NULL 行 → PSQLException 含约束名——评审要的「冲突型 PgIT」最终形态）。
- 前端：vue-tsc 0 错 / vitest 204 全过 / eslint 0 问题（授权页 utils 六文件 + 组件两件 + api + fixtures + 三 spec）。
- 回归：全仓 mvn test BUILD SUCCESS（计数见提交信息），GoldenFixturePgIT/ResourceOperationKeyPgIT/AuthorizationChangeInvalidationPgIT 定向全绿。

## 关联修订

- T-PERM-040 的 includeGlobalFallback 交付物被本任务推翻（卡片加退役注记，其余交付物不受影响）。
- T-PERM-037 的投影轨全局位合并条目随概念失效（判定/投影两侧均回归类型专属，无缺口）。
- runbook OPERATION_PERMISSIONS_BY_TYPE 部署窗口行更新（当前口径 = 类型专属集）。
