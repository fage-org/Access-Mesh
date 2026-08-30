---
doc_type: task
id: T-PERM-029
title: 3.2 权限条件后端——业务键切换/detail 20006 收紧/updatedAt 补齐/list 全量定案（permission-condition）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.6
  - docs/design/permission-center/implementation.md#§2.5
  - docs/design/frontend/permission-condition.md#§8
depends_on:
  - T-FE-009
blocks: []
acceptance:
  - "业务键切换：detail 切 ConditionDetailReq{conditionCode}（DTO 早已定义未接线）；update 切 {code, name?, conditionRules?, enabled?, gatewayEvaluable?, description?}（code 为定位键不可改，null 字段不更新）；remove 切 ConditionRemoveReq{codes:[...]} 批量软删。请求体不保留内部 id 兼容（未上线先例 T-PERM-034/028）；ConditionResp 保留 id（授权链路 role_resource_permission.condition_id 引用与诊断用途）"
  - "detail 查不到语义收紧：data:null 宽松形态删除，统一抛 20006 CONDITION_NOT_FOUND（对齐 T-PERM-028 resource-entity/detail 收紧定案与授权链路 apply-grant-plan 未知 conditionCode 同码）"
  - "list 全量不分页（设计定案，🔧3 登记反转）：条件模板数量有界（租户内几十个量级，非流水表），与 T-PERM-026 domain-config / T-PERM-027 service-config「量小不分页」双先例一致；EmptyReq 全量 ItemsResp，keyword/enabled 过滤由前端本地完成（本页已实现，后端零改动）"
  - "ConditionResp 补 updatedAt（entity 列本就存在，此前 Resp 不返回）"
  - "孤儿方法删除：ConditionAppService.deleteCondition（单删）接口+Impl 无任何生产调用方（Controller 只调批量版），随本批删除，测试覆盖迁移批量版（T-PERM-034 revokePermissions 先例）"
  - "门禁语义（2026-08-30 口径收窄）：读取（list/detail）无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）；写 create/update/remove = CONDITION:CREATE/UPDATE/DELETE 类型级（scope_all）——CONDITION 为权限定义元数据（同 OPERATION/SYSTEM_CONFIG），无 resource_entity 实例投影、实例级授权无从配置，原「实例级门禁」声称系 ID 空间错位（permission_condition.id 传入 resource_entity.id 语义的引擎轨）已废弃；实例投影与条件双轨制登记 T-PERM-048。remove 类型级全有或全无；幽灵 code 静默跳过不进入门禁"
  - "gatewayEvaluable 联合校验语义不变（T-PERM-017 C2.5 最终状态校验：只切 flag 用老 rules / 同改用新 rules / 已 true 改 rules 用新 rules），既有 13 项校验用例全数适配保持绿"
  - "前端与 mock 对齐：api 类型与函数签名切业务键 + updatedAt；hook edit/remove 传 code；mock detail/update/remove 按 code、404→20006、remove 返 data=null、种子与 create/update 维护 updatedAt；授权页契约 spec（permission-grant.spec）两用例 conditionId→code 订正"
  - "design_writeback：api-contract §5.6 permission-condition 契约要点块（业务键/20006/门禁/联合校验/幂等语义）+ 端点表行更新 + last_reviewed、permission-condition.md §8 六项全收口 + 字段/API 表对齐、看板行 ✅"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-30
---

# T-PERM-029 3.2 权限条件后端——业务键切换/detail 20006 收紧/updatedAt 补齐/list 全量定案

> 状态：done（2026-08-30 收口）
> 依赖：T-FE-009（前端权限条件页 + §8 六项 🔧 登记）
> 归属：frontend-phase2（3.2 权限条件后端）

## 背景

T-FE-009 前端权限条件页在 API 核对中登记 6 项 🔧（permission-condition.md §8）：detail/update/remove 用内部主键（应切业务键 code，schema uk tenant+code 已保证唯一）、list 无分页无筛选（登记期望补 ConditionListReq）、list/detail 无 VIEW 校验（后经 2026-08-08 产品确认取消——读取全租户开放）、ConditionResp 缺 updatedAt、api-contract §5.6 缺字段契约。授权链路 apply-grant-plan 侧已全按 conditionCode 业务键工作（selectValidByCodes + 未知码 20006 fail-closed），管理端点切业务键后两路口径统一。

## 设计定案（2026-08-30，三项均经决策）

1. **list 全量不分页**（🔧3 登记反转）：条件模板数量有界，与 domain-config/service-config「量小不分页」双先例一致；前端已实现本地过滤 keyword+enabled，后端零改动。
2. **detail 查不到抛 20006**：对齐 T-PERM-028 resource-entity/detail 收紧定案（data:null 删除）与授权链路同码。
3. **单删孤儿方法删除**：deleteCondition 无任何生产调用方（Controller 只调批量版），T-PERM-034 revokePermissions 同款先例，测试覆盖迁移批量版。

## 范围与实现

- DTO：ConditionUpdateReq 重写（conditionId→code，@NotBlank @Size(64)）；新增 ConditionRemoveReq（@NotEmpty codes 列表）；ConditionCreateReq.code 补 @Size(64)；ConditionResp 补 updatedAt；ConditionDetailReq（既有，conditionCode）接线。
- 服务与实现：getCondition(tenantId, conditionCode) 走 selectValidByCode、null 抛 20006；updateCondition 先按 code 解析（selectValidByCode 含租户+delete_flag=0 过滤，替代原 selectOneById+手工校验）再类型级 UPDATE 门禁（OperationLog SpEL 切 #req.code()）；deleteConditionsByCodes：codes 清洗去空白去重 → selectValidByCodes 单查 → 类型级 DELETE 门禁 → softDeleteBatch → markConditions/markServiceCodes（@PermissionChange 广播链路语义不变）；deleteCondition（单删）删除。
- Mapper：零新增（selectValidByCode/selectValidByCodes 既有——后者为授权链路活跃设施）；selectValidById/selectValidByIds 成为无管理端调用方（保留：BaseMapper 泛型方法及未来按 id 批量加载场景，selectValidByIdsNoTenant 同为既有缓存链路设施）。
- Controller：detail 切 ConditionDetailReq、remove 切 ConditionRemoveReq；create/list/update 签名不变。
- 测试：ConditionAppServiceImplTest 16→23——update 系列 6 用例 stub 切 selectValidByCode/构造切 code；单删覆盖迁移 shouldMarkServiceCodes_whenDeleteConditionsByCodes；BusinessKeyEndpoints 业务键回归锁（detail 命中含 updatedAt 透出 / detail 未知码 20006 / update 未知码 20006 且零副作用 verify never / remove 幽灵码静默跳过仅删解析实体 / 无类型级 DELETE 权限整批拒绝零删除 / 全空白码不触达查询）+ DtoBeanValidation（四请求 DTO 列宽与元素校验 Bean Validation 层锁定）。HttpApiPathSnapshotTest 两行签名订正（detail|ConditionDetailReq、remove|ConditionRemoveReq）。
- 前端：api/permission-condition.ts 切业务键 + updatedAt + 🔧 注释收口；hook submitCondition editingCode / removeConditions([row.code])；index.vue openEdit 传 row.code；mock/_shared/permission-condition-store.ts 类型+守卫+种子补 updatedAt（旧 localStorage 数据守卫判废自动重种子）；mock 路由 detail/update/remove 按 code、20006、remove 返 ok(null)；permission-grant.spec 两用例 {conditionId:601}→{code:"office-hours"}。

## 已知限制

- **create 重复 code 无预查友好码**：靠 uk_permission_condition 兜底拒绝（系统错误通道），与 resource/type-def create 同款；mock 层返回 409 为开发态友好提示（028 先例同款约定）。
- **remove 幂等静默跳过且响应无行数**（Void）：不存在的 code 跳过不报错，批量调用方以事后查询核对（对齐 resource-entity/remove）。
- **description 无「显式清空」通道**：update 传 null=不更新（与 028 extraClear 不同形态）——条件描述为可选项，清空场景改传空串（DB 可空列语义差异不影响功能，未发现真实诉求，不做 extraClear 同款机制）。

## 验收对照

- design_refs：api-contract §5.6 permission-condition 契约要点块 + 端点表行更新；permission-condition.md 字段表/API 表/交互流程/§8 六项全收口；implementation §2.5（PermissionConditionDomainService 条件校验，本批未触及，引用关系保持）。
- 测试：后端 ConditionAppServiceImplTest 23 项全绿（16 适配 + 6 业务键/门禁回归锁 + 1 DTO 校验）+ HttpApiPathSnapshotTest 7 项全绿（含订正 2 行）；前端 vue-tsc 干净 + vitest 216 项全绿 + 变更文件 eslint 干净。
- 回归：access-service mvn test 全量绿；git diff --check 干净。

## 完成记录

- 2026-08-30 收口：三项设计定案（list 全量不分页 / detail 20006 / 单删孤儿删除）落地；实现中对齐 028 模式（先解析后定位、请求体不留 id 兼容、Resp 保留 id）；切键同时消除一处门禁语义毛刺（原批量删按原始输入 ids 门禁，幽灵 id 触发拒绝；新实现幽灵码静默跳过）；授权页契约 spec 直调条件 update 的两用例随契约同步订正。
- 同日修正批（执行与核验发现，均最小改动）：updateCondition 补 setUpdatedBy 审计；name/description 补 @Size(128/512) 列宽（T-PERM-023 先例）并补 DtoBeanValidation 用例在 Bean Validation 层锁定；javadoc 与前端注释订正；api-contract conditionRules 来源差异收窄（list/detail 为 JSONB 回读文本，create/update 返回最终接受的规则文本）；mock 对齐（update 联合校验触发=最终态复验、remove/create/update/detail 空白与超长 400、非字符串元素防御、isDuplicateCode 死参删除）；permission-condition.md 字段表补 tenantId；implementation §2.5 补 evaluateDetailed 签名（T-PERM-033 文档滞后）；T-FE-036 任务卡 CONDITION:VIEW 旧口径加取代注记；selectValidById 零调用知情保留（范围节登记理由）。
- **门禁口径收窄（原「实例级」声称废弃，2026-08-30 设计定案）**：CONDITION 无 resource_entity 实例投影、role_resource_permission.resource_entity_id 引用 resource_entity.id 空间，实例级授权无从配置（授权页选不到、DB 配不进）——此前实现把 permission_condition.id 传入引擎（曾先后以编码轨传 id 字符串、实体轨传 id 两种形态存在）均属 ID 空间错位的虚假声称。现口径：写操作全类型级（CONDITION:CREATE/UPDATE/DELETE scope_all，与 OPERATION/SYSTEM_CONFIG 同款）；remove 类型级全有或全无（无类型级 DELETE 授权整批拒绝，用例锁定）。条件实例投影与条件双轨制登记 T-PERM-048（定案：投影为目标态、本任务不实现；构想=条件分两类——权限条件页面管理的条件（授权页仅引用）与授权页面配置的条件（权限条件页不可见不可管、仅授权页更改），数据表新增来源字段、UI 交互同步更新）。
