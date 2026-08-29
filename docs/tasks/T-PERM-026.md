---
doc_type: task
id: T-PERM-026
title: 5.1 业务域后端——业务键/分页/global/删除保护收口（biz-domain + domain-config）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/permission-center/api-contract.md#§5.6
  - docs/design/permission-center/implementation.md#§2.7
  - docs/design/frontend/biz-domain.md#§8
depends_on:
  - T-FE-006
blocks: []
acceptance:
  - "biz-domain detail/update 切业务键 code 定位（uk_biz_domain；detail 未命中 data=null、update 未知编码 20017；description 空串=显式清空、null=不更新）——T-FE-006 🔧 第 3 项"
  - "biz-domain list 补 keyword（LIKE code/name/description）+ 服务端分页（ORDER BY code,id；均不传=字典全量上限 200，system-config 范式）返回 PaginatedResp——🔧 第 4 项"
  - "BizDomainResp 补 global 字段 + remove 删除保护（20051 DOMAIN_DELETE_CONFLICT：全局域不可删、域下存在有效 domain_config 行引用检查拒删，schema「引用检查拒删」落地）——🔧 第 2 项"
  - "create 编码查重（预查 + uk_biz_domain DIVE 兜底同映射 20052 DOMAIN_CODE_DUPLICATE，T-PERM-023 先例）+ 请求体长度/格式校验对齐 schema 列宽"
  - "domain-config save 补 JsonValidationUtils 前置校验（非法 JSON fail-closed）+ extra JSONB↔String 映射真库确认（BizDomainConfigPgIT，🔧 第 6 项确认型）"
  - "DOMAIN:VIEW 补入空库 bootstrap 固定图（businessGrants + GRANT_RESOURCE_TYPES，无授予起点死锁防护；原 🔧 第 1 项「种子缺失」经核实不成立——DDL CRUD 预置组已覆盖，误报登记反转）"
  - "P0 隐患修复：BizDomainMapper.selectByCode/selectByCodes XML 列名误写 domain_code（DDL 列为 code），真库必报 42703——resolveDomainId 底层（domain-config 链路/DomainClassifyService/checkCanGrant 消费），PgIT 回归锁"
  - "前端与 mock 对齐：api/hook/index 切服务端分页+业务键+global 标识禁删；mock 双注册表（_bizDomainRegistry + 新 _domainConfigRegistry）对齐错误码 20051/20052/20017 语义"
  - "design_writeback：api-contract §5.1 biz-domain/§5.6 domain-config 契约要点、biz-domain.md §8 终态化、看板行 ✅"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-29
---

# T-PERM-026 5.1 业务域后端——业务键/分页/global/删除保护收口

> 状态：done（2026-08-29 收口）
> 依赖：T-FE-006（前端业务域页 + 🔧 清单登记）
> 归属：frontend-phase2（5.1 业务域后端）

## 背景

T-FE-006 前端业务域页（主从：BizDomain CRUD 主表 + DomainConfig 子表）在 API 核对中登记 6 项 🔧（biz-domain.md §8）：DOMAIN 权限种子、Resp 缺 global、detail/update 内部主键、list 无分页、configType 注释（已先期收口）、extra JSONB 映射确认。

## 设计定案（2026-08-29，两项用户决策）

1. **DOMAIN:VIEW 补入 bootstrap 固定图**：checkCanGrant 要求操作者先持有才能转授，固定图不持则空库上业务域页读路径无授予起点（死锁）——对齐 T-PERM-025 OPERATION_LOG:VIEW / T-PERM-032 PERMISSION_CHANGE_LOG:VIEW 先例；businessGrants + GRANT_RESOURCE_TYPES 同步，AccessBootstrapPgIT 计数 23→24 / scopeAll 10→11。
2. **错误码承载**：20051 DOMAIN_DELETE_CONFLICT（全局域不可删 + 域配置引用拒删，同一删除被拒语义、message 区分两类原因）+ 20052 DOMAIN_CODE_DUPLICATE（create 编码重复）；对齐「每类拒绝语义一码」惯例（20049/20050 先例）。

## 范围与实现

- **P0 隐患（核对新发现）**：`BizDomainMapper.xml` selectByCode/selectByCodes 列名误写 `domain_code`（DDL 列为 `code`）——`resolveDomainId` 底层查询真库必报 42703，domain-config save/list/detail、DomainClassifyService、checkCanGrant 批量解析全部受影响，单测 mock mapper 掩盖；修列名 + BizDomainConfigPgIT 真库回归锁（同时锁定 extra JSONB↔String 映射语义等价，🔧6 确认收口）。
- biz-domain：detail 接 BizDomainDetailReq{domainCode}（原死 DTO 接线；门禁改类型级 DOMAIN:VIEW 先于查询避免存在性泄露）；update 切 {domainCode,name?,description?}；list 切 BizDomainListReq{keyword,pageNum,pageSize}→PaginatedResp（Controller paged 判定同 system-config）；Resp +global；create 预查+DIVE 映射 20052 + @Size/@Pattern 对齐列宽；remove 删除保护（global 批量过滤 → selectValidByDomainIds 引用检查一次批量查询 → softDeleteBatch；死方法 selectByTenantId 随切分页删除）。
- domain-config：save 补 JsonValidationUtils.validateJson（system-config/condition 同范式，原非法 JSON 打到 PG 解析错误裸 99999）。
- 前端：api/biz-domain.ts 类型与四函数切新契约；hook 切服务端过滤分页 + edit 传 code + description 总是携带（空串=清空）；index.vue 全局 tag + global 行禁删 + 关键字占位符；mock：biz-domain/domain-config 路由对齐（分页/业务键/global 下发/20051/20052/20017 语义），域配置数据下沉新 mock/_domainConfigRegistry.ts（remove 引用检查需两份运行时数据一致）。

## 验收对照

- design_refs：api-contract §5.1/§5.6 契约要点已回写；biz-domain.md §1-§9 终态化；implementation §2.7（DomainClassifyService）本任务零改动（核对确认——resolveDomainId 列名修复属 Mapper 层，其接口签名未变）。
- 测试：BizDomainAppServiceImplTest 2→15（业务键 detail/update、空串清空、20051 两类拒绝、20052 预查+DIVE 映射+非唯一 DIVE 重抛、keyword 规整、门禁先于查询）；DomainConfigAppServiceImplTest 3→4（非法 JSON 拒绝不触库）；HttpApiPathSnapshotTest 快照 2 行同步（detail IdReq→BizDomainDetailReq、list EmptyReq→BizDomainListReq+PaginatedResp）；AccessBootstrapPgIT 计数 24/11；新增 BizDomainConfigPgIT（PG 容器轨：resolveDomainId/selectByCodes 真库走通 + extra JSONB roundtrip 语义等价 + 引用检查/软删清空 + keyword 过滤分页）。
- 回归：access-service mvn test 全绿；前端 typecheck 干净 + vitest 216 项全绿。

## 完成记录

- 2026-08-29 收口：P0 列名隐患修复 + 六项 🔧 全处置（第 1 项误报反转、第 5 项先期已收口）+ 两项设计定案（DOMAIN:VIEW 固定图、错误码 20051/20052）+ 前端与 mock 双注册表对齐；后端单测/PgIT/快照/bootstrap 计数全绿，前端 typecheck+vitest 全绿。
