---
doc_type: task
id: T-PERM-046
title: 业务域后端三项加固——全局域创建入口设计 + domain_config 唯一键兜底 + 删除保护并发窗口
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/permission-center/api-contract.md#§5.6
  - docs/design/frontend/biz-domain.md#§9
depends_on: []
blocks: []
acceptance:
  - "全局域创建入口（2026-09-05 定案）：biz-domain/create 增加可选 global 字段默认 false；门禁维持 SYSTEM_CONFIG:MANAGE；并发由 uk_biz_domain_global 兜底，违例映射新错误码「全局域已存在」（perm 段顺延，执行时与 T-PERM-052 新码统一排号；注意 isUniqueViolationOn 约束名带引号精确匹配——uk_biz_domain 是 uk_biz_domain_global 前缀）；global 创建后不可变（update 不改，换轨=新建域）；全局域不可删保护既有；全局范围维持 CLASSIFY 补集动态计算（语义定案：全局域=未被其他域认领类型的桶，「查全部」由 DomainQueryMode.ALL 独立承担，两概念各司其职）；前端 create 表单加开关（已存在全局域时禁用+提示）；契约 §5.1「API 创建固定普通域」条目改写"
  - "domain_config upsert 并发兜底：唯一无唯一键的 upsert 表（仅普通索引 idx_domain_config_domain），save check-then-insert 并发窗口可双插同键两行；对齐 biz_domain（uk+DIVE）/system_config（uk_system_config）先例补部分唯一索引 (tenant_id,biz_domain_id,config_type) WHERE delete_flag=0 + save DIVE 映射（并发双插→重试提示）+ PgIT 验证"
  - "删除保护与 save 的并发窗口：biz-domain remove 的引用检查与软删是两条无锁 READ COMMITTED 语句、domain-config save 的域解析也无锁——并发 save 可在检查后插入配置，留下指向已软删域的有效配置行（孤儿死数据：resolveDomainId 对软删域返 null，配置不可达，非越权非损坏）；需 remove 引用查询与 save 域解析共用域行锁（SELECT FOR UPDATE）或等价串行化机制 + PG 并发回归（外部复评 P2 登记，2026-08-29 设计定案：并入本任务统一处置，与 T-PERM-044 四棵树 check-then-act 窗口同类）"
  - "三项均低风险（DomainClassifyService 对全局域缺失容忍；每域至多 2 条配置低并发；管理页低并发窗口极窄），T-PERM-026 评审登记（2026-08-29 设计定案：登记后续任务，不在 T-PERM-026 轮内处理）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-05
---

# T-PERM-046 业务域后端三项加固——全局域创建入口设计 + domain_config 唯一键兜底 + 删除保护并发窗口

> 状态：proposed（T-PERM-026 双轨评审登记，2026-08-29；设计项「全局域创建入口」2026-09-05 设计体检定案，见 acceptance 第 1 条）
> 依赖：无硬依赖

## 背景

T-PERM-026 业务域后端收口的双轨评审发现两项模型层缺口，经定案登记后续任务处置：

1. **全局域创建入口缺失**：`biz_domain` DDL 表注释称「全局域(global=true)……由管理 API 创建，不在本文件预置」，但 `BizDomainCreateReq` 无 global 入参、`createBizDomain` 固定 `global=false`——真库无任何入口能创建全局域。`DomainClassifyService` 的全局域语义（GLOBAL_PLUS 模式、全局域隐式包含未认领资源类型）依赖 global 域存在，现状对缺失容忍（`selectGlobalByTenant` 返回 null 时语义退化）。
2. **domain_config upsert 并发双插**：契约宣称 save upsert 幂等，但存储层无唯一键保证——并发同键保存可落两行，后续 selectValidByTypeString 只命中其一，形成脏数据。对比同期收口的 biz_domain（uk_biz_domain + DIVE 兜底 20052）与 system_config（uk_system_config）先例。
3. **删除保护并发窗口孤儿配置**：remove 引用检查（selectValidByDomainIds）与 softDeleteBatch 两条语句间、save 的 resolveDomainId 无锁域解析后 insert——并发交错可留下指向已软删域的有效配置行。影响为不可达孤儿死数据（非越权、非数据损坏），管理页低并发窗口极窄；修法为域行锁或等价串行化，属锁策略设计，与 T-PERM-044 同类 check-then-act 窗口。
