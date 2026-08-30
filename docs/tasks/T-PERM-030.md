---
doc_type: task
id: T-PERM-030
title: 3.3 冲突规则后端——读三端点 VIEW 门禁/detail 20020 收紧/类型级写门禁收窄/updatedAt 补齐/bootstrap 死锁补授（conflict-rule）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.6
  - docs/design/permission-center/implementation.md#§2.4
  - docs/design/frontend/conflict-rule.md#§4
depends_on:
  - T-FE-010
blocks: []
acceptance:
  - "读门禁补齐（2026-08-30 经决策）：list/detail/detect 三端点补类型级 CONFLICT_RULE:VIEW 门禁——matchedRules 同样透出规则数据，与 list 同级敏感；CONDITION「读不设门禁」定案依据（授权页依赖条件列表，bootstrap apiRoutes 含 permission-condition/list）不适用于冲突规则，无跨页依赖"
  - "detail 收紧：查不到抛 20020 CONFLICT_RULE_NOT_FOUND（原 data:null 宽松语义删除，对齐 028 resource-entity/029 condition 两先例）；update 先解析后门禁（未知 id 优先 20020 且零副作用）"
  - "定位键评估定案：维持内部主键 id——冲突规则无业务键（type+对象对+rtv 为复合语义身份，无单列 code 可切，与 resource/operation/condition 不同）；造 code 列属过度设计不做"
  - "写门禁类型级收窄（与 T-PERM-029 CONDITION 同口径）：CONFLICT_RULE 无 resource_entity 实例投影，原 update/delete 编码轨传内部 id 的「实例级」声称系 ID 空间错位废弃（实例投影登记 T-PERM-048）；remove 类型级全有或全无、幽灵 id 解析阶段静默跳过（幂等，对齐 resource-entity/condition remove）"
  - "Resp 补 updatedAt（entity 列本就存在）；update 补 updatedBy 审计（029 同款预存在缺口）；description 补 @Size(512) 列宽校验（create/update）"
  - "死代码清理：ConflictRuleDetailReq（Controller 实际用 IdReq）、deleteConflictRule 单删孤儿方法（零生产调用方，Controller 仅调批量版，029 deleteCondition 先例）删除；conflictType 实体注释对齐枚举（ROLE_MUTEX/PERM_MUTEX）；ConflictRuleReq 死 import NotNull 清除"
  - "update 实现 UpdateChain→UpdateEntity 强制写列（对齐 028 extraClear 全仓标准方式；UpdateChain 为全仓唯一使用且零测试，语义不变）；全量覆盖语义/双向去重/first<second 规范化/唯一约束兜底 20032 均保持"
  - "bootstrap 固定图补授（经决策，空库死锁防护——checkCanGrant 要求操作者先持有，025/026/027/032 逐任务补齐模式）：CONFLICT_RULE:VIEW/CREATE/UPDATE/DELETE 四条 + 顺带补 CONDITION:CREATE/UPDATE/DELETE 三条（T-PERM-029 遗漏同款缺口，读取无门禁故无 VIEW 条目）；GRANT_RESOURCE_TYPES 同步补两类型（操作位查询范围）"
  - "测试：新建 ConflictRuleAppServiceImplTest 33 项（首个用例集——此前 CRUD 服务零单测覆盖：门禁四档+20020+去重/规范化/全量覆盖+幂等/全有或全无+detect 双向+DTO Bean Validation）；AccessBootstrapPgIT 授权计数 27→34/scopeAll 14→21"
  - "前端与 mock 对齐：api Resp +updatedAt + 🔧 注释收口；mock detail/update 404→20020、create/update 重复 409→20032（与后端业务去重同码）、remove 返 data=null（后端 Void）、updatedAt 维护；perms.ts/hook.ts 门禁注释四档口径"
  - "design_writeback：api-contract §5.6 conflict-rule 契约要点块 + last_reviewed、conflict-rule.md §4 六项收口 + §5 权限接线四档口径 + last_reviewed、看板行 ✅ + T-FE-010 行收口标注、plan 进度行"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-30
---

# T-PERM-030 3.3 冲突规则后端——读三端点 VIEW 门禁/detail 20020 收紧/类型级写门禁收窄/updatedAt 补齐/bootstrap 死锁补授

> 状态：done（2026-08-30 收口）
> 依赖：T-FE-010（前端冲突规则页 + §4 六项 🔧 登记）
> 归属：frontend-phase2（3.3 冲突规则后端）

## 背景

T-FE-010 前端冲突规则页在 API 核对中登记 6 项 🔧/❌（conflict-rule.md §4）。另有两项跨切：①写门禁与 CONDITION 同款 ID 空间错位（T-PERM-048 登记项，随本任务同口径收口）；②bootstrap 固定图不含 CONFLICT_RULE/CONDITION 任何条目——空库上 checkCanGrant 20040 导致两页写权限永远授不出去（死锁，025/026/027/032 逐任务补齐模式的既知机制，029 收口时未发现 CONDITION 缺口）。2026-07-11 曾有一批 P1 修复（update 全量覆盖/detect NULL 全局规则/去重规范化/20032），本批在其上收口。

## 设计定案（2026-08-30，四项均经决策）

1. **读三端点全补类型级 VIEW**：detect 的 matchedRules 同样透出规则数据，与 list 同级敏感；CONDITION 读不设门禁的定案依据（授权页依赖）不适用于冲突规则。
2. **detail 查不到抛 20020**：对齐 028/029 两连先例；mock 同步对齐。
3. **bootstrap 补 CONFLICT_RULE 四条**：空库死锁防护，同 DOMAIN/SERVICE 先例。
4. **CONDITION 域同款缺口顺带补三条**：T-PERM-029 遗漏的最小事实性修正（同一文件三行 + 计数断言，不留已知死锁）。

## 范围与实现

- DTO：ConflictRuleResp +updatedAt；ConflictRuleReq/UpdateReq description 补 @Size(512)；ConflictRuleReq 死 import 清除；ConflictRuleDetailReq 删除。
- 服务与实现：list/detect 补类型级 VIEW（OperatorContext 取操作者，OperationAppServiceImpl 读门禁同款）；detail VIEW + null→20020；update 先解析（selectValidById 含租户+delete_flag=0）后类型级 UPDATE 门禁，UpdateChain→UpdateEntity（028 标准），UPDATED_BY 补写；deleteConflictRule 单删删除；deleteConflictRulesByIds 解析→幽灵跳过→类型级 DELETE（全有或全无）→softDeleteBatch。
- 实体注释：conflictType 对齐 ROLE_MUTEX/PERM_MUTEX。
- bootstrap：BootstrapGraphDefinition.businessGrants +7 条（CONFLICT_RULE 四档 + CONDITION 写三档）；AccessBootstrapInitializer.GRANT_RESOURCE_TYPES +2 类型（回归首跑抓出：缺项致操作位 fail-fast，全部 13 个 IT 失败同根因）；AccessBootstrapPgIT 计数 27→34/14→21。
- 测试：ConflictRuleAppServiceImplTest 新建 33 项（Create 9 / Read 5 / Update 6 / Remove 5 / Detect 3 / DtoBeanValidation 5——update 全量覆盖用 UpdateEntity patch 参数捕获断言对侧强制 null 与 updatedBy，并以 UpdateWrapper.getUpdates() 锁定显式 null 列（getter 无法区分显式 set(null) 与未 set，删掉 set(null) 行断言才失败）；读门禁 mockStatic OperatorContext）。
- 前端：api/conflict-rule.ts 类型 +updatedAt、🔧 注释收口；mock 错误码对齐（20020/20032/ok(null)）+ updatedAt 维护；perms.ts 四档口径注释；hook.ts 读门禁注释。

## 已知限制

- **ROLE_MUTEX_RULE 缓存不随 CRUD 主动失效**：PermissionConflictDomainService 的角色互斥缓存为快照链路目录（L2_ONLY TTL 10s，「10+5+15≤30s」安全边界设计），冲突规则增删改后最多约 30s 内生效；与快照链路目录统一设计一致，不加写路径失效（过度设计）。
- **create/update 重复预查为业务层全量加载比对**（isDuplicate selectByTenantId + 内存双向匹配）：租户内规则量小可接受；并发窗口由 uk_conflict_rule_perm/role（NULLS NOT DISTINCT）DB 兜底转 20032。
- **remove 幂等静默跳过且响应无行数**（Void data=null）：调用方以事后查询核对（对齐 resource-entity/condition remove）。

## 完成记录

- 2026-08-30 收口后双轨内部评审修正（代码轨 0P1+1P2+5P3、文档轨 0P1+1P2+7P3，逐条核实后处置，修项全落地）：P2 ①测试锁定强化——UpdateEntity「强制写列」语义此前用 getter 断言无法区分显式 set(null) 与未 set（删掉 set(null) 行测试仍绿），补 UpdateWrapper.getUpdates() 显式 null 列断言（类型切换/rtv 清空两用例）；②架构文档同步——access-service-architecture §14.4 业务门禁最小集表补 CONFLICT_RULE 四档与 CONDITION 写三档两行、§14.7 规模枚举句同步（该表自带同步义务）。P3 批：update 末尾 re-select 空窗口（并发软删）null→20020 收口防 NPE 500（补用例，33 项构成之一）；isDuplicate 传参 rtv 在 ROLE_MUTEX 分支随写库规范化为 null（畸形请求下预查全 miss 仅绕 DB 兜底一道，行为无差异）；「先解析后门禁」注释援引订正为 T-PERM-029 模式（028 的 update 实为先门禁后解析）；shouldMarkSkip 两用例补 OperationLogRuntimeContext.snapshot().skip() 断言；AccessBootstrapPgIT 状态① DisplayName 计数 23→34 失真订正；router system.ts「三档独立」残留改四档口径、perms.ts sec 矩阵句补 VIEW（与 mock 实态一致）、mock 内联注释 UpdateChain 残留改 UpdateEntity、T-FE-010 卡 last_updated 随头部订正 bump、plan 030 行 detail 20020 移出六项括号。评审存疑三项均按既定事实/先例豁免：bootstrap 补授对已部署旧规模环境的升级 fail-fast（项目未上线无存量部署）；update 先解析后门禁的存在性泄露（与 029 定案形态同款，id 为内部自增价值低）；下一批预告（SYSTEM_CONFIG 无固定图条目死锁、TYPE_DEFINITION/DEPENDENCY 两处编码轨实例级同病）归各自后续任务。

## 验收对照

- design_refs：api-contract §5.6 conflict-rule 契约要点块（定位键/20020/四档类型级门禁/全量覆盖语义/detect/updatedAt/bootstrap 补授）；conflict-rule.md §4 六项全收口 + §5 四档口径 + §8 清单行；implementation §2.4（PermissionConflictDomainService 运行时冲突过滤，本批未触及，引用关系保持）。
- 测试：后端 ConflictRuleAppServiceImplTest 33 项全绿 + AccessBootstrapPgIT 14 项全绿（计数断言 34/21 锁定补授）；前端 vue-tsc 干净 + vitest 216 项全绿 + 变更文件 eslint 干净。
- 回归：access-service mvn test 单元轨全绿（构建到达容器轨执行即证）；容器轨 112 项仅 TaskExecutionLeaseConcurrencyTest 1 项失败——T-PERM-029 回归期间已确认的 admin 域时序抖动遗留（隔离重跑 10/10 绿，与本批 conflict 域/bootstrap 变更零关联）；surefire 聚合 876 项 0 失败 0 错误。首轮全量曾 13 失败，根因为 GRANT_RESOURCE_TYPES 未同步新类型（操作位 fail-fast 级联全部 AccessBootstrapPgIT 用例），补两类型后全绿。git diff --check 干净。
