---
doc_type: task
id: T-PERM-046
title: 业务域后端三项加固——全局域创建入口设计 + domain_config 唯一键兜底 + 删除保护并发窗口
status: done
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
  - "全局域创建入口（2026-09-05 定案）：biz-domain/create 增加可选 global 字段默认 false；门禁维持 SYSTEM_CONFIG:MANAGE；并发由 uk_biz_domain_global 兜底，违例映射新错误码「全局域已存在」（perm 段顺延，执行时与 T-PERM-052 新码统一排号；注意 isUniqueViolationOn 约束名带引号精确匹配——uk_biz_domain 是 uk_biz_domain_global 前缀）；global 创建后不可变（update 不改，换轨=新建域）；全局域不可删保护既有；~~全局范围维持 CLASSIFY 补集动态计算~~（执行期被 2026-09-09 用户定案推翻：CLASSIFY 声明生效——有声明按声明、无声明退补集，见完成记录）；前端 create 表单加开关（已存在全局域时禁用+提示）；契约 §5.1「API 创建固定普通域」条目改写"
  - "domain_config upsert 并发兜底：唯一无唯一键的 upsert 表（仅普通索引 idx_domain_config_domain），save check-then-insert 并发窗口可双插同键两行；对齐 biz_domain（uk+DIVE）/system_config（uk_system_config）先例补部分唯一索引 (tenant_id,biz_domain_id,config_type) WHERE delete_flag=0 + save DIVE 映射（并发双插→重试提示）+ PgIT 验证"
  - "删除保护与 save 的并发窗口：biz-domain remove 的引用检查与软删是两条无锁 READ COMMITTED 语句、domain-config save 的域解析也无锁——并发 save 可在检查后插入配置，留下指向已软删域的有效配置行（孤儿死数据：resolveDomainId 对软删域返 null，配置不可达，非越权非损坏）；需 remove 引用查询与 save 域解析共用域行锁（SELECT FOR UPDATE）或等价串行化机制 + PG 并发回归（外部复评 P2 登记，2026-08-29 设计定案：并入本任务统一处置，与 T-PERM-044 四棵树 check-then-act 窗口同类）"
  - "三项均低风险（DomainClassifyService 对全局域缺失容忍；每域至多 2 条配置低并发；管理页低并发窗口极窄），T-PERM-026 评审登记（2026-08-29 设计定案：登记后续任务，不在 T-PERM-026 轮内处理）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-09
---

# T-PERM-046 业务域后端三项加固——全局域创建入口设计 + domain_config 唯一键兜底 + 删除保护并发窗口

> 状态：done（2026-09-09 收口；执行期一项设计定案变更见下「执行定案」）
> 依赖：无硬依赖

## 背景

T-PERM-026 业务域后端收口的双轨评审发现两项模型层缺口，经定案登记后续任务处置：

1. **全局域创建入口缺失**：`biz_domain` DDL 表注释称「全局域(global=true)……由管理 API 创建，不在本文件预置」，但 `BizDomainCreateReq` 无 global 入参、`createBizDomain` 固定 `global=false`——真库无任何入口能创建全局域。`DomainClassifyService` 的全局域语义（GLOBAL_PLUS 模式、全局域隐式包含未认领资源类型）依赖 global 域存在，现状对缺失容忍（`selectGlobalByTenant` 返回 null 时语义退化）。
2. **domain_config upsert 并发双插**：契约宣称 save upsert 幂等，但存储层无唯一键保证——并发同键保存可落两行，后续 selectValidByTypeString 只命中其一，形成脏数据。对比同期收口的 biz_domain（uk_biz_domain + DIVE 兜底 20052）与 system_config（uk_system_config）先例。
3. **删除保护并发窗口孤儿配置**：remove 引用检查（selectValidByDomainIds）与 softDeleteBatch 两条语句间、save 的 resolveDomainId 无锁域解析后 insert——并发交错可留下指向已软删域的有效配置行。影响为不可达孤儿死数据（非越权、非数据损坏），管理页低并发窗口极窄；修法为域行锁或等价串行化，属锁策略设计，与 T-PERM-044 同类 check-then-act 窗口。

## 执行定案（2026-09-09，均已登记 decision-registry）

1. **全局域 CLASSIFY 声明生效**（用户三选一拍板「让 CLASSIFY 生效」，推翻建卡时「维持补集动态计算」口径）：全局域实际范围=有 CLASSIFY 声明按声明（声明即收窄，可为空集）、无声明退动态补集；GLOBAL_PLUS 隐式段同源对齐（有声明=声明集，无声明/无全局域=未被非全局域认领补集，既有语义不变）；反查 findDomainIdsByTypeCodes 次序不变（非全局域认领优先、全局域兜底）。SUB_PERM 挂全局域本有真实语义（未认领类型兜底读全局域策略），不受影响。对存量部署零行为差异（全局域此前物理不可创建）。
2. **错误码排号**：20057 DOMAIN_GLOBAL_EXISTS / 20058 DOMAIN_CONFIG_CONCURRENT_CONFLICT（perm 段顺延 20056 之后）。
3. **锁机制按任务卡首选落地=FOR UPDATE 域行锁**（未采用 T-PERM-044 的 Redisson——树场景递归 CTE 校验锁不住行集合，本场景精确域行集合 FOR UPDATE 双向闭合且无 Redis fail-closed 依赖）。

## 完成记录（2026-09-09）

**①全局域创建入口**：
- `BizDomainCreateReq` 加 `global`（Boolean 可选默认 false/null 同 false）；`createBizDomain` global=true 时预查 `selectGlobalByTenant` 已存在拒绝 20057；DIVE 兜底扩双约束（`"uk_biz_domain"`→20052 / `"uk_biz_domain_global"`→20057，带引号精确匹配防前缀误吞）；update 请求体不含 global（创建后不可变）。
- `DomainClassifyServiceImpl`：新增 `effectiveTypeCodes`（全局域有 CLASSIFY 行按声明、无行退补集）；`matchesTypeCode` GLOBAL_PLUS 隐式段对齐全局域实际范围；`findDomainIdsByTypeCodes` 次序不变无需改。
- 前端：`BizDomainCreateReq`（TS）加 `global?`；`BizDomainFormData` 加 `global`；`BizDomainForm.vue` create 态「全局域」开关（`globalExists` prop 已存在时禁用+提示）；`hook.ts` 新增 `checkGlobalDomainExists`（弹窗打开时拉字典全量判断，失败按不存在、后端 20057 兜底）；`index.vue` 传参接线。
- 契约 §5.1 create 条目改写、biz-domain.md §4.2/§8/§9、AGENTS.md 业务域分类模型段、architecture §13 措辞同步。

**②domain_config 唯一键兜底**：
- schema：`idx_domain_config_domain` 普通索引替换为 `uk_domain_config (tenant_id, biz_domain_id, config_type) WHERE delete_flag=0` 部分唯一索引（覆盖原查询用途）；H2 适配层自动处理（delete_flag 谓词去 WHERE 保留唯一）。
- `upsertDomainConfig` insert 包 DIVE：`"uk_domain_config"` 违例映射 20058（提示重试，重试时另一事务已提交转 update 分支）。

**③remove/save 域行锁**：
- `BizDomainMapper` 新增 `selectByCodeForUpdate` / `selectValidByIdsForUpdate`（XML FOR UPDATE，mapper 同事务连接，锁至提交）；`deleteBizDomainsByIds` 校验读改走 ForUpdate 版本；`upsertDomainConfig` 域解析从无锁 `resolveDomainId` 改为 `selectByCodeForUpdate`（DomainConfigAppServiceImpl 直接注入 BizDomainMapper，BizDomainAppService 引用 DomainConfigMapper 同款同域惯例）。
- 双向闭合：remove 提交后 save 解析不到软删域（20017）；save 持锁插入的配置被 remove 引用检查看到（20051 拒删）。

**测试**（本批全绿，总量以当轮 surefire 报告为准）：
- 单测：BizDomainAppServiceImplTest（+3：global 预查拒绝/DIVE 20057/global=true 落库；原「全局违例重抛」用例随行为反转改写为映射 20057 断言）；DomainConfigAppServiceImplTest（+2：DIVE 20058/非唯一 DIVE 重抛；+锁版本 verify 回归锁——save 域解析必须走 ForUpdate、never resolveDomainId）；DomainClassifyServiceImplTest（+2：声明生效/GLOBAL_PLUS 收窄——旧实现下失败）。
- 真库（容器轨）：AccessServiceSchemaPostgresTest 原样 DDL 通过（含新唯一索引）+ BizDomainConfigPgIT（+3：uk_domain_config 双插拒/uk_biz_domain_global 全域双插拒/FOR UPDATE 串行化——remove 持锁软删期间 save 解析有界等待证明阻塞+提交后读 null；分页用例 PGITPAGEAGLOBAL 改普通域避免与全局唯一用例共享库互斥）。
- H2：AccessServiceSchemaH2Test（+1 domain_config 唯一约束）。
- 回归面：access-service 单测轨道全量（-DskipTestcontainers=true）0 失败；前端 tsc+vue-tsc 通过。

**残留清扫**：「隐式包含/无需配置 CLASSIFY/由系统初始化」旧口径全仓清扫（schema 列注释、architecture、biz-domain.md、AGENTS.md、前端 api 注释、5 处 Java Javadoc：BizDomain/BizDomainResp/ConfigType/PermissionErrorCode/DomainClassifyService）。
