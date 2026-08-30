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
  - "门禁语义保持：读取（list/detail）无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）；create=CONDITION:CREATE 类型级、update/remove=CONDITION:UPDATE/DELETE 实例级（先按 code 解析实体再按 entityId 门禁）；remove 对解析出的实体集合批量校验，任一拒绝整批 fail-closed——幽灵 code 静默跳过不进入门禁（原实现按原始输入 ids 门禁，幽灵 id 会触发 fail-closed，此为切键后的行为修正）"
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
- 服务与实现：getCondition(tenantId, conditionCode) 走 selectValidByCode、null 抛 20006；updateCondition 先按 code 解析（selectValidByCode 含租户+delete_flag=0 过滤，替代原 selectOneById+手工校验）再实例级 UPDATE 门禁（OperationLog SpEL 切 #req.code()）；deleteConditionsByCodes：codes 清洗去空白去重 → selectValidByCodes 单查 → 解析实体 id 集合批量 DELETE 门禁 → softDeleteBatch → markConditions/markServiceCodes（@PermissionChange 广播链路语义不变）；deleteCondition（单删）删除。
- Mapper：零新增（selectValidByCode/selectValidByCodes 既有——后者为授权链路活跃设施）；selectValidById/selectValidByIds 成为无管理端调用方（保留：BaseMapper 泛型方法及未来按 id 批量加载场景，selectValidByIdsNoTenant 同为既有缓存链路设施）。
- Controller：detail 切 ConditionDetailReq、remove 切 ConditionRemoveReq；create/list/update 签名不变。
- 测试：ConditionAppServiceImplTest 16→24——update 系列 6 用例 stub 切 selectValidByCode/构造切 code；单删覆盖迁移 shouldMarkServiceCodes_whenDeleteConditionsByCodes；新增 BusinessKeyEndpoints 7 用例（detail 命中含 updatedAt 透出 / detail 未知码 20006 / update 未知码 20006 且零副作用 verify never / remove 幽灵码静默跳过仅删解析实体 / 门禁拒绝整批 fail-closed 零删除 / 全空白码不触达查询 / getDeniedEntityIds 收解析实体键非原始码数）。HttpApiPathSnapshotTest 两行签名订正（detail|ConditionDetailReq、remove|ConditionRemoveReq）。
- 前端：api/permission-condition.ts 切业务键 + updatedAt + 🔧 注释收口；hook submitCondition editingCode / removeConditions([row.code])；index.vue openEdit 传 row.code；mock/_shared/permission-condition-store.ts 类型+守卫+种子补 updatedAt（旧 localStorage 数据守卫判废自动重种子）；mock 路由 detail/update/remove 按 code、20006、remove 返 ok(null)；permission-grant.spec 两用例 {conditionId:601}→{code:"office-hours"}。

## 已知限制

- **create 重复 code 无预查友好码**：靠 uk_permission_condition 兜底拒绝（系统错误通道），与 resource/type-def create 同款；mock 层返回 409 为开发态友好提示（028 先例同款约定）。
- **remove 幂等静默跳过且响应无行数**（Void）：不存在的 code 跳过不报错，批量调用方以事后查询核对（对齐 resource-entity/remove）。
- **description 无「显式清空」通道**：update 传 null=不更新（与 028 extraClear 不同形态）——条件描述为可选项，清空场景改传空串（DB 可空列语义差异不影响功能，未发现真实诉求，不做 extraClear 同款机制）。

## 验收对照

- design_refs：api-contract §5.6 permission-condition 契约要点块 + 端点表行更新；permission-condition.md 字段表/API 表/交互流程/§8 六项全收口；implementation §2.5（PermissionConditionDomainService 条件校验，本批未触及，引用关系保持）。
- 测试：后端 ConditionAppServiceImplTest 24 项全绿（16 适配 + 7 业务键新增 + 1 DTO 校验[codex 评审补]）+ HttpApiPathSnapshotTest 7 项全绿（含订正 2 行）；前端 vue-tsc 干净 + vitest 216 项全绿 + 变更文件 eslint 干净。
- 回归：access-service mvn test 全量绿；git diff --check 干净。

## 完成记录

- 2026-08-30 收口：三项设计定案全落地；实现中对齐 028 模式（先解析后门禁、请求体不留 id 兼容、Resp 保留 id）；切键同时发现并消除一处门禁语义毛刺（原批量删按原始输入 ids 门禁，幽灵 id 触发 fail-closed；新实现只对解析实体门禁，幽灵码静默跳过——用例锁定）；授权页契约 spec 直调条件 update 的两用例随契约同步订正。
- 2026-08-30 双轨内部评审收口（代码轨 0P1+0P2+8P3、文档轨 0P1+0P2+6P3，逐条核实后处置，12 项 P3 全部最小修正）：updateCondition 补 setUpdatedBy 审计（预存在缺口，028 同款模式）；name/description 补 @Size(128/512) 列宽（T-PERM-023 先例，create/update 两 DTO）；javadoc 三处与前端 perms.ts 注释订正（条件ID→业务键 code、@throws 类型）；api-contract remove 拒绝语义措辞收窄（幽灵键幂等一致但本域 fail-closed 整批 ≠ resource-entity 部分成功，分叉成文）与内部 id 出现处收窄（补 explain 排查明细）；mock 三项对齐（update 联合校验触发=最终态复验对齐后端、remove 空白/超长元素 400 对齐元素级校验、isDuplicateCode 死参删除）；permission-condition.md 字段表补 tenantId；看板 T-FE-009 行 🔧 尾注收口标注；implementation §2.5 补 evaluateDetailed 签名（T-PERM-033 文档滞后顺访）。维持现状：selectValidById 零调用知情保留（上文范围节已登记）；CONDITION 实例门禁经引擎 scopeAll 优先逻辑核实实际等价类型级（CONDITION 无 resource_entity 实例投影，无安全回退，设计现状非缺陷，不另立任务）。
- 2026-08-30 codex 外部评审收口（gpt-5.6-sol xhigh 只读，1P2+4P3 全属实全处置）：P2 updateCondition 门禁轨道误用——按 code 解析实体后仍调 hasPermissionByCode 传 String.valueOf(id)（编码轨传内部 id，违背引擎「两轨不得混用」边界与任务卡/契约「按 entityId 门禁」自述，父提交既有误用、本批换 id 来源未换轨道，内部双轨评审漏网；经引擎源码核实 scopeAll 提前返回在 code 解析之前，现状下两轨行为等价无越权无功能损坏，但轨道必须修正）——改 hasPermissionByEntityId(condition.getId())，测试 5 处 stub + 1 处 verify 同步切轨；P3 ①DTO 校验零测试覆盖——补 DtoBeanValidation 用例（Jakarta Validator 直验四个请求 DTO 的 code/name/description 列宽与 remove 元素级校验，先例 DomainConfigAppServiceImplTest）；②mock create/update/detail 未对齐 Bean Validation——补 code 空白/超长与 name/description 超长 400（detail 空白 code 先 400 再 20006）；③api-contract「JSONB 读出的规范化文本」覆盖过宽——收窄为 list/detail 是 DB 回读、create/update 返回最终接受的规则文本（不做写后反查）；④T-FE-036 任务卡两处 CONDITION:VIEW 旧口径残留——加删除线与取代注记（2026-08-08 产品确认移除，见 T-FE-040/T-PERM-029）。codex 其余核验结论（SQL 过滤正确、@PermissionChange 回滚不 flush、幂等/fail-closed 路径正确、快照订正正确、7 新用例真锁语义）与内部评审一致。
