---
doc_type: task
id: T-PERM-034
title: 4.1 权限授予后端改造（七项 + 工程加固收窄 + 全链路迁移简化）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.5
  - docs/design/permission-center/api-contract.md#§6.4
  - docs/design/permission-center/api-contract.md#§6.5
  - docs/design/permission-center/api-contract.md#§6.5.1
  - docs/design/permission-center/api-contract.md#§6.5.2
  - docs/design/permission-center/core-flows.md#§6
  - docs/design/permission-center/implementation.md
  - docs/design/frontend/permission-grant.md#§12
  - docs/design/schema/access-service.sql
depends_on:
  - T-PERM-031
blocks:
  - T-FE-018
acceptance:
  - "七项改造完成（2026-08-08 v3.1 扩展范围）：①RolePermissionItemResp 暴露 grantSource + grantedBits（十进制字符串）；②list 加 includeChildren 参数（默认 true 兼容；**v3.1 baseline=true 一次取全量，来源链过滤 dependOn==null**）；③增补 createdAt/childCount（list 按 depend_on 分组 COUNT 一次返回）；④新增 apply-grant-plan（记录级 creates/updates/removes，单事务原子执行任一失败整体回滚；单事务原子 + 受影响行数断言，无 CAS/无幂等表；唯一写入口）；⑤移除旧写入口（save/revoke/children/add-child/remove-child 端点删除；update-child/children-save/rebuild 不实现）+ 全链路迁移简化（perm-common RoleGrantReq/BatchRevokeReq 与 perm-client PermissionFeignClient.batchGrant/batchRevoke 移除——属对外 SDK/HTTP 契约面，执行时确认仓库外无外部消费者后删除；原 admin-service RoleProxyServiceImpl 改造子项已失效：类已随 T-ACCESS-006 删除，菜单授权由本地投影/permission 域直接承载）；⑥统一预检 prevalidateGrantPlan（八项不变量，去幂等键格式）+ 错误码（20033 完整键冲突/20034 AUTO_DEP 只读/20036 全集预校验/20011 SUB_PERM fail-closed + 父域 resource_type 直查/checkCanGrant 批量收口/20040 委托失败/**20043 子权限属性系统不变量（create 非 null/false 或 update 目标为子权限一律拒绝）**；**20041/20042 枚举与 PermissionErrorCode 建立、校验实现与测试归 T-PERM-041**；砍 20037/20039；**移除废弃的 checkCanGrant 匹配键扩展 condition 维度**（条件不参与身份））；⑦无幂等中间件（已定案：幂等中间件实现取消（未登记看板），clientRequestId/@Idempotent 从契约移除；前端按钮 disabled 防重复点击，后端单事务+uk+行数断言）"
  - "Mutation Policy：**`PermissionGrantPlanDomainService`（实际代码入口，；Java 方法名 = `prevalidate(plan)`，`prevalidateGrantPlan` 仅作流程名/章节名使用）** 新增唯一预检入口 prevalidate(plan)（不变量：记录存在及角色/父归属、update/remove 互斥、AUTO_DEP 只读、canGrant 授权传递、**子权限属性系统不变量 20043（先于主权限不变量判定，错误优先级：先按主/子分类，子权限 create 非 null/false -> 20043、子权限 update -> 20043）**、SUB_PERM 约束（父域 resource_type 直查）、完整持久化键冲突、scopeMode/资源/操作兼容性；**主权限条件不变量 20041/20042 由 T-PERM-041 在本预检内增量实现**）；**授权域内同层调用例外**：`PermissionGrantPlanDomainServiceImpl` 组合注入 `PermissionGrantDomainService`（checkCanGrant/validateSingleManualGrants/validateGrantAttributes）为**明确允许的例外**——写链路单事务聚合服务组合领域校验能力，不推广到其他域；apply-grant-plan 唯一写入口强制调用，AppService 禁止自行拼门禁"
  - "测试矩阵适用性覆盖（非笛卡尔积）：成功路径 6 条（create 主权限 / create 主权限嵌套 children（子权限属性固定 null/false）/ create 子权限引用已有父 / update 主权限（canGrant 或 conditionCode 微变更）/ remove 子权限 / remove 主权限验证级联删子）；**子权限属性反例**：children 嵌套 create 带 conditionCode -> 20043、children 嵌套 create 带 canGrant=true -> 20043、parentPermissionId 挂父 create 带 conditionCode -> 20043、update 目标为子权限 -> 20043（含 update 只改 canGrant 与只改 conditionCode 两形态）；**主权限条件不变量反例（20041/20042）由 T-PERM-041 验收，本任务不重复**；不变量反例按适用命令--记录存在与角色归属（update/remove）、父记录归属与层级（create 子权限）、AUTO_DEP 只读（update/remove + 向 AUTO_DEP 父 create 子权限）、checkCanGrant（所有 create + update canGrant=true 或 conditionCode 变更）、完整持久化键冲突（create + update，condition 不参与身份）、SUB_PERM（create 子权限）、scope/resource/operation 兼容（create）、段间互斥（update/remove 同 id + 各段内部重复 id）；SUB_PERM 四格 INSTANCE/ALL × 具体域/全局域；**SUB_PERM 全量结构校验：allowed 任一项缺 parent_type/child_types 非数组 -> CONFIG_INVALID（读接口 reason），写链路同步拒绝**；事务故障注入 1 条（已完成至少一次写入后抛异常，验证事实表+审计表均无部分状态）"
  - "**schema 文件删除（已定案）**：perm-grant.schema.json 删除；api-contract §6.4/§6.5/§6.5.1/**§6.5.2** 为唯一权威契约（错误码枚举 20001-20012/20033/20034/20036/20011/20040、plan 结构、统一响应壳、跨字段 INSTANCE-ALL 约束、local-date-time、grantedBits 十进制字符串、无 clientRequestId）；结构校验由 prevalidate 运行时执行（不做前端机器编译门禁）"
  - "**授权传递查询固定批次数**：prevalidateGrantPlan 内 checkCanGrant 全链路查询次数与资源类型数量无关--一次 selectByTenantAndResourceTypes(tenantId, resourceTypeValues) 加载后内存分组构建 grantedOpIndex；查询次数断言测试防回归"
  - "**错误码精简**：20033/20034/20036/20011/20040 纳入 PermissionErrorCode 枚举与 api-contract 错误码表；**砍 20037 VERSION_CONFLICT / 20039 IDEMPOTENCY_OPERATOR_MISMATCH**（随 CAS/幂等表移除）；errorResponse.data 必填（失败恒 null）"
  - "**无幂等中间件（已定案）**：幂等中间件实现取消（未登记看板）；clientRequestId/@Idempotent 从契约移除；前端 saving 期间按钮 disabled 防重复点击，超时提示刷新确认；后端靠单事务原子 + uk 约束 + 受影响行数断言保证不重复/不部分成功"
  - "**SUB_PERM fail-closed + 父域 resource_type 直查**：配置缺失/extra 空/格式错误 -> 20011 拒绝；extra=* 显式通配；父域解析直接读 role_resource_permission.resource_type（废弃 resource_entity_id 反查）；INSTANCE/ALL × 具体域/全局域四格测试；上线前核对配置种子覆盖率"
  - "**写入口五项清单（已定案）**：applyGrantPlan 必须齐全--① @Transactional(rollbackFor=Exception.class) ② @OperationLog ③ @PermissionChange ④ auditDomainService.recordChangeLog（同事务一条聚合 permission_change_log）⑤ PermissionChangeContext.markRoles；缺任一项导致审计缺失或缓存失效遗漏"
  - "**diff_snapshot 契约形状（T-PERM-033 依赖登记）**：apply-grant-plan 变更日志写 §6.8 items[].permission 聚合形状（eventType=ROLE_PERMISSION_CHANGE、6 字段业务键、role 摘要），permission-view/explain 的 ROLE 目标 recentChanges 端到端可用并有测试锁定"
  - "**端到端场景 1（成功提交）**：权限事实落库 + 恰好一条聚合 permission_change_log + 提交后缓存失效及 PermInvalidateEvent 广播发生"
  - "**端到端场景 2（执行失败）**：权限事实与变更日志全部回滚（无部分状态）+ 不触发缓存失效及广播"
  - "wire DTO 通过 api-contract 字段对齐（mock 与真实响应均按 api-contract，nullable/统一响应壳/BigInt 字符串格式由契约固化）"
  - "数据库异常统一转换为业务错误码（uk 冲突->20033 等，不暴露数据库异常）"
  - "**超时/重复提交处理**：前端 saving 期间按钮 disabled 防重复提交；超时 -> 提示刷新确认（不做重放/不做幂等表）；执行顺序：① 认证 + 目标角色 ROLE:MANAGE 门禁（hasPermission 显式判断 false 抛 SecurityException）-> ② prevalidateGrantPlan -> ③ 单事务执行（creates/updates/removes）+ 受影响行数断言"
  - "GoldenFixtureTest 通过：**6 用例精简**（全局回退/组合位/ALL/资源继承/操作继承/两段组合来源）输出权威结果；全局操作解析链路已落地（TypeResolutionService 专属优先+全局 fallback、T-PERM-028 全局轨、T-PERM-040 list 类型合并），所需 fixture 随本任务测试自建（原「T-PERM-031 阶段 2-4 生产」划分已随该范围被吸收而废止，2026-08-30 清扫）"
  - "**grant_revision DDL 砍**：abstract_role 不新增 grant_revision 列（CAS 移除）；list 响应不含 revision；apply-grant-plan 请求不含 expectedRevision/clientRequestId、响应不含 revision/currentRevision/replayed；**受影响行数断言**：updates/removes 实际影响行数 ≠ 预期 -> 20036 整体回滚"
  - "**schema DDL：子权限属性系统不变量 CHECK**：access-service.sql 补 `CHECK (depend_on IS NULL OR (condition_id IS NULL AND can_grant = false))`（子权限不变量 DDL 兜底，2026-08-08 产品确认，与 20043 同口径；仅最终态建表 DDL，项目未上线不考虑历史数据）"
  - "**SUB_PERM 共享策略对象 + 只读接口（v3.1）**：从 `assertSubPermissionAllowed` 抽取不可变策略对象 `SubPermissionPolicy { mode, reason, allowedTypeCodes, allows(childTypeCode) }`，**唯一公开解析入口 `PermissionGrantPlanDomainService.resolveSubPermissionPolicy(tenantId, parentResourceTypeCode)`**（顶层通配、**全量结构校验（任一 allowed 项缺 parent_type/child_types 非数组 -> CONFIG_INVALID）**、并集去重、大小写不敏感、错误原因统一组装）；新增 `POST role-resource-permission/sub-perm-allowed-types`（api-contract §6.5.2：ROLE:VIEW 门禁但失败响应不同——角色定位失败 20001、无 VIEW 抛 SecurityException、parentResourceTypeCode 无效 20007；reason 枚举 CONFIG_MISSING/EMPTY/INVALID/PARENT_NOT_CONFIGURED/**CHILD_TYPES_EMPTY**）；读接口由 AppService 映射 `resolveSubPermissionPolicy` 结果直接序列化，写链路 prevalidate 复用同一解析器调 `policy.allows(childTypeCode)`，**禁止在 AppService/Controller 另行编写 SUB_PERM 判断（读写同源）**；前端不硬编码允许集"
  - "design_writeback（权威文档全量同步门禁，人工清单核对）：api-contract §6.4/§6.5/§6.5.1/§6.5.2 契约实现状态标注（砍 CAS/幂等/revision 字段、补结构约束、20041/20042 仅主权限、20043 子权限不变量、错误优先级、sub-perm-allowed-types）、access-service.sql（role_resource_permission 节：**删 grant_revision 列 + 删 grant_plan_idempotency 表** + 表注释同步）、core-flows.md §6 + §8/§12/§14 场景（apply-grant-plan 主通道，无 CAS/幂等）、implementation.md §4 全章（接口/DTO/时序图/伪代码，收窄重写 + SubPermissionPolicy）、permission-grant.md status draft -> adopted、任务验收描述--逐项人工核对，任一不同步不通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-30   # 收口：四缺口推进（经决策）+ 已完成项核对登记；两项引擎级发现随 Golden 比对修复
---

# T-PERM-034 4.1 权限授予后端改造

> 状态：done（2026-08-30 收口，终态见 api-contract §6.5.1/§6.5.2 落地注记与完成记录；2026-08-02 收窄：砍 CAS + grant_revision 列 + 幂等表 + 20037/20039 + 重试 machinery + clientRequestId/@Idempotent（幂等中间件实现取消（未登记看板））；全链路迁移简化为 Feign+DTO+测试替换；schema 删除，契约回归 api-contract）
> 依赖：T-PERM-031（已 done：本任务前置的资源依赖后端已收口；原「全局操作 fixture 生产」括注已废止——范围被 T-PERM-028/040 覆盖）
> 前置验收：见 acceptance（DoD 门禁）

## 背景

> **重基线（T-ACCESS-012，2026-08-22）**：落点为 access-service permission 域单模块；schema 权威为 `access-service.sql`；「迁移 admin-service」语义失效——角色/授权管理本就在同服务 permission 域，本任务即在该域内完成端点收口。

v3 权限授予页（T-FE-036）需要后端补齐授权写链路能力。2026-08-02 单入口收敛（list + apply-grant-plan），随后全链路迁移与幂等加固。**收窄（2026-08-02）**：经设计复盘，认定 CAS + 持久化幂等表 + 八态状态机是 Stripe 式重幂等，对低频内部管理页错配--并发编辑/网络超时罕见，管理员刷新确认即可。砍 CAS + grant_revision 列 + grant_plan_idempotency 表 + 20037/20039 + hash canonical + replayed/currentRevision + 20037 重试 machinery；幂等中间件实现取消（未登记看板），前端 saving 期间按钮 disabled 防重复点击 + 后端单事务+uk+行数断言；全链路迁移简化（CAS 砍后无 revision/无重试，admin-service 菜单授权直接构造 creates、撤销 list 拿 id 不变）；schema 删除（此前已降级为说明性、不机器校验，维护冗余），契约回归 api-contract 单一来源。核心目标：唯一写入口、单事务原子、受影响行数断言、不变量自动化测试。

## 范围（七项）

1. `RolePermissionItemResp` 暴露 `grantSource`（MANUAL/AUTO_DEP）+ `grantedBits`（63 位位图，十进制字符串，前端 BigInt）
2. `list` 加 `includeChildren` 参数（默认 true 兼容；**v3.1：baseline=true 一次取全量主+子，来源链过滤 dependOn==null；false 仅辅助查询**）；**响应不含 revision**（CAS 砍，无乐观锁基线）
3. `RolePermissionItemResp` 增补 `createdAt`/`childCount`（list 时按 depend_on 分组 COUNT 一次返回）
4. 新增 `apply-grant-plan` 聚合接口（api-contract §6.5.1）：记录级 `plan{creates/updates/removes}`（子权限用 parentPermissionId 挂父；替换 = removes+creates 原子、子权限不迁移）+ **单事务原子执行**（任一失败整体回滚）+ 受影响行数断言；**唯一写入口**；**diff_snapshot 对齐契约 §6.8 轻量规范（T-PERM-033 依赖登记，2026-08-29）**：变更日志从现行 `{creates,updates,removes}` ID 列表旧形状改为 `{eventType:ROLE_PERMISSION_CHANGE, items:[{changeType, permission:{6字段业务键}, role:{...}}]}` 聚合一条（写路径补权限行+资源实体+类型解析的业务键装配），解锁 permission-view/explain 的 ROLE 目标 recentChanges；**砍 expectedRevision CAS + grant_revision 列 + 幂等表 + 20037/20039 + clientRequestId/@Idempotent（幂等中间件实现取消（未登记看板））**
5. 移除旧写入口（**随本任务统一删除**）：已实现端点（save/revoke/children/add-child/remove-child）从 PermissionGrantController 删除；计划中接口（update-child/children-save/rebuild）不实现；**兼容期（迁移完成前）add-child 强制复用子权限属性不变量 20043 校验，不得成为绕过路径**；**全链路迁移简化**（CAS 砍后无 revision/无 20037 重试）：perm-common `RoleGrantReq`/`BatchRevokeReq` 移除、perm-client `PermissionFeignClient.batchGrant/batchRevoke` 移除——两者属对外 SDK/HTTP 契约面，执行时确认仓库外无外部消费者后删除（T-ACCESS-012 重基线：保留该删除断任务）；原 admin-service `RoleProxyServiceImpl` 改造与测试重写子项已失效——类已随 T-ACCESS-006 删除，admin 侧菜单授权端点已删除（T-ADMIN-024，无映射 404），授权统一走 permission 域 apply-grant-plan
6. 统一预检与错误码：prevalidateGrantPlan 唯一预检（不变量集，去幂等键格式）；完整持久化键冲突 + 20033（**conditionCode/canGrant 不参与身份，移除条件维度**）；AUTO_DEP 只读 + 20034；全集预校验 + 20036；SUB_PERM fail-closed + 20011（父域解析改 resource_type 直查）；checkCanGrant 批量收口（一次 selectByTenantAndResourceTypes 内存分组）；委托失败 20040；**子权限属性系统不变量 20043（create 非 null/false 或 update 目标为子权限一律拒绝；错误优先级先于主权限不变量）**；**20041/20042 校验实现与测试归 T-PERM-041**；**砍 20037 VERSION_CONFLICT / 20039 IDEMPOTENCY_OPERATOR_MISMATCH**
7. **无幂等中间件**（已定案：幂等中间件实现取消（未登记看板），clientRequestId/@Idempotent 从契约移除；前端按钮 disabled 防重复点击，后端单事务+uk+行数断言）

## 工程加固

- **Mutation Policy**：`PermissionGrantPlanDomainService.prevalidate(plan)`（**Java 方法名；`prevalidateGrantPlan` 仅作流程名**）唯一预检（不变量集，去幂等键格式；**组合 PermissionGrantDomainService 校验能力——授权域内同层调用为明确允许的例外，产品确认**；主权限 20041/20042 由 T-PERM-041 增量实现）；apply-grant-plan 唯一写入口强制调用；测试矩阵入 acceptance
- **Golden fixtures**：`GoldenFixtureTest` 权威用例集（**6 用例精简**：全局回退/组合位/ALL/资源继承/操作继承/两段组合来源），前端比对基准
- **端点契约**：**删除** `docs/contracts/perm-grant.schema.json`；报文契约回归 `api-contract.md §6.4/§6.5/§6.5.1/§6.5.2` 单一来源，补结构约束（统一响应壳/跨字段 INSTANCE-ALL 约束/local-date-time/grantedBits 十进制字符串/错误码枚举（含 20041/20042/20043）/plan 结构/无 clientRequestId）；结构校验由 prevalidateGrantPlan 运行时执行

## 完成记录

- **2026-08-30 主体收口（四缺口推进，经决策——已完成项不重做，仅登记核对结论）**。执行前逐项核对代码现状，确认下列验收项**已存在**（多为 T-PERM-040/041/028 与端点退役批次先行落地，经多轮回归）：Resp 四字段（grantSource/grantedBits/createdAt/childCount）+ list 参数 includeChildren、错误码族（20011/20033/20034/20036/20040/20041）、schema CHECK 约束、prevalidate 主体不变量（互斥/重复/存在性/父归属/AUTO_DEP 只读/SUB_PERM fail-closed/checkCanGrant 批量固定查询/父域 resource_type 直查）、写入口五项清单（事务/操作日志/@PermissionChange/recordChangeLog/markRoles）、apply-grant-plan 唯一写入口与行数断言。本批实施四个真缺口：
  1. **20043 子权限属性系统不变量**：枚举建立（SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED）+ 预检（children 嵌套与 parentPermissionId 挂父两形态 create 的 conditionCode/canGrant 检查、update 目标为子权限一律拒绝，错误优先级先于 20041）；顺带补齐验收不变量族遗漏的「向 AUTO_DEP 父挂子权限 → 20034」。
  2. **SubPermissionPolicy + sub-perm-allowed-types（§6.5.2 全套）**：从 assertSubPermissionAllowed 抽取不可变策略对象（mode/reason/allowedTypeCodes/allows），唯一公开入口 resolveSubPermissionPolicy（20007 类型校验+域定位+优先级 0-6 解析）；新端点（ROLE:VIEW 实例门禁、resolveRoleId 失败 20001、无 VIEW 抛 SecurityException、策略直接序列化）；快照 +1 端点；写链路复用同一解析器（读写同源）。
  3. **diff_snapshot §6.8 聚合形状**（T-PERM-033 登记的读侧依赖解锁）：PreparedGrantPlan 增 AuditPermissionKey 快照（creates 取请求键、updates/removes 预检期反查业务键——removes 行软删后不可回查；悬挂引用降级 null 键字段，删除不被死引用阻塞），AppService 装配 eventType/items[]{changeType,permission 6 字段业务键,role 摘要} 一条聚合日志（ObjectNode 序列化）。
  4. **GoldenFixturePgIT（经决策选型：PgIT 引擎级比对）**：读前端权威 fixtures（6 用例），真库种类型/操作/资源/主体/权限行，运行时引擎逐（资源×操作）评估与 expected.cells 等价比对（nodeClosure 语义映射为 {资源}∪祖先链逐点判定取或）；组合位记录以 AUTO_DEP 落库（MANUAL 单位 CHECK 约束，组合位属防御性展示语义）；预种全部操作后再评估（OPERATION_PERMISSIONS_BY_TYPE 缓存首载合并快照——T-PERM-047 写路径失效未接线的中途补种会读到陈旧缓存）。
- **两项引擎级发现（Golden 比对首跑即抓出，均已修复）**：①`PermQueryEngine.resolveBitMasks` 此前不计全局操作位——授权侧经 mergeGlobalFallback 允许授全局操作（如 EXPORT），运行时引擎却忽略该位（前端自算==引擎语义的实质分歧）；修复为按类型合并「专属优先、全局回退」（新增 selectGlobal mapper，OPERATION_PERMISSIONS_BY_TYPE 缓存内容改为合并后最终可用集，单生产者无一致性风险）。②`expandByInheritMode` 的继承展开语义为对已加载条目的展示性克隆（core-flows §资源继承展开如实描述），实例判定按查询实体精确加载——非缺陷，测试按此映射（祖先链 OR）。
- **测试**：PermissionGrantPlanDomainServiceImplTest 4→35（成功路径 6/20043 反例 5/不变量反例 10/SUB_PERM 四格 3/Policy 判定表 11）；新建 PermissionGrantAppServiceImplTest 5（门禁/映射 4 + diff_snapshot 聚合形状捕获 1）；GoldenFixturePgIT 1（6 用例）；AuthorizationChangeInvalidationPgIT 1→3（+端到端场景 1：成功提交恰好一条聚合日志+缓存失效；+端到端场景 2：DB 触发器注入第二笔 create 失败→事实/日志全回滚+不触发失效，断言根因为注入异常防委托失败假阳性）；HttpApiPathSnapshotTest +sub-perm-allowed-types。过程修复测试基建：insertRolePerm 未写 can_grant 列（操作者「可转授」实际落库 false——batchRevoke 因 removes 无委托键侥幸通过）。
- **文档**：api-contract（§5.5 行收口/§6.5.2 落地注记/§6.8 写侧落地增补/last_reviewed）、implementation §4（落地注记+伪代码 diff_snapshot 形状订正）、permission-grant.md（last_reviewed+决策 16/17 收口+Golden 落地注记）、看板行 ✅、plan 进度行、AGENTS 阶段句。
- **回归（commit 011faa1a2，2026-08-30）**：access-service mvn test 单元轨 961 项 0 失败 0 错误 + 容器轨 115 项仅 TaskExecutionLeaseConcurrencyTest 已知抖动（029 遗留，隔离重跑 10/10 绿零关联）；GoldenFixturePgIT 6 用例、E2E 3/3、快照测试全绿；git diff --check 干净。
- **复评收口（同日，双轨 0P1+4P2+12P3+5 存疑全处置）**：①20043 上移至预检循环（契约「子权限 update 一律」——空变更/父被删两路径原被 VALIDATION_FAILED 抢占，+2 用例锁定）；②引擎合并归一化复用 OperationResolutionDomainService.normalizeCode（原裸 toUpperCase 无 trim/Locale，与写链路口径漂移）；③parseSubPermissionPolicy 结构校验改 isTextual（数字标量原被 asText 收编容错，+2 用例）；④文档批：api-contract §6.8 过期现状登记订正+§6.5.1/§6.5.2 标题 🔧 摘除、前端 permission-grant.ts「后端未实现」注释改已落地口径+20043 常量名对齐 NOT_ALLOWED、permission-grant.md §7.1 六行 🔧 后端补→已落地+fixtures 注记历史限定+§8/头部/T-FE-038 标记清理、T-FE-018/看板 034 补已 done、任务卡补回归行+编辑残留清理+Resp 四字段订正、plan 行时序订正；⑤经决策三项：§6.6 投影轨全局位分歧登记 T-PERM-037（api-contract 现状注记）、rebuild-runbook 上线检查单加缓存 flush 提示、T-FE-041 卡遗留登记补解除注记。
- **外部复评三轮（同日）**：P1 全局操作与类型专属操作同位异码互相越权——经决策整体退役全局操作概念，独立任务 T-PERM-049 单日收口（本卡二轮修复的两处引擎全局位逻辑随概念移除，见该卡关联修订）。
- **外部复评二轮收口（同日，1P1+2P2+1P3：三修一不修）**：P1 多类型查询中全局目标操作位套用到「同码专属定义已取代全局」的类型——uk_operation_permission_typed_bit 按 tenant+resource_type 隔离位值，全局 bit 与专属 bit 属不同位空间（越权与漏授权双向出错）→ 目标位按该类型合并结果中同码实际生效定义取值（PermQueryEngineTest +1 用例锁定位掩码值）；P2 apply 级联删除子权限漏出 diff_snapshot → 预检期经 selectValidByDependOns（租户口径与 cascadeSoftDeleteChildren 一致）快照子权限 REMOVE 业务键、与显式 removes 去重（+2 用例，api-contract §6.8 同步）；P2 操作定义冷缓存回源 N+1 → getBatch 收集 miss 类型 + 1 次全局 + 1 次批量专属 IN + putBatch 分组回填（+1 用例锁定 SQL 次数与回填键集）；P3 normalizeCode 加 Locale.ROOT 经定案不修——特殊 Locale（土耳其语 dotted-i）场景不在支持范围，其余同站点失败方向均 fail-closed 拒绝无越权。引擎测试桩 6 处随 getBatch 化同步；GoldenFixturePgIT/AuthorizationChangeInvalidationPgIT 4/4 复跑全绿。
- **2026-08-27 范围第 5 项（端点退役）独立收口（设计定案）**：`save/revoke/children/add-child/remove-child` 五端点从 PermissionGrantController 删除（无存量调用方，AppService 五方法、五个请求 DTO、死私有方法一并清除）；SDK 面 perm-common `RoleGrantReq`/`BatchRevokeReq` 与 perm-client `PermissionFeignClient.batchGrant/batchRevoke` 同步移除；快照 190→185 + RETIRED_PATHS 登记；新增 RetiredGrantApiContractTest（五端点 404 + 存活端点委托）；AuthorizationChangeInvalidationPgIT 撤销场景改走 applyGrantPlan removes。任务其余六项（grantSource/grantedBits、includeChildren、createdAt/childCount、sub-perm-allowed-types、prevalidate 不变量族、测试矩阵）待 T-PERM-031 完成后推进，任务整体不置 done。
