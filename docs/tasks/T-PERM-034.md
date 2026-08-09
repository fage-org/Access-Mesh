---
doc_type: task
id: T-PERM-034
title: 4.1 权限授予后端改造（七项 + 工程加固收窄 + 全链路迁移简化，第十四轮收窄）
status: proposed
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
  - docs/design/schema/permission-center.sql
depends_on:
  - T-PERM-031
blocks:
  - T-FE-018
acceptance:
  - "七项改造完成（第十四轮收窄；2026-08-08 八轮复审扩展 v3.1 范围）：①RolePermissionItemResp 暴露 grantSource + grantedBits（十进制字符串）；②list 加 includeChildren 参数（默认 true 兼容；**v3.1 baseline=true 一次取全量，来源链过滤 dependOn==null**）；③增补 createdAt/childCount（list 按 depend_on 分组 COUNT 一次返回）；④新增 apply-grant-plan（记录级 creates/updates/removes，单事务原子执行任一失败整体回滚；单事务原子 + 受影响行数断言，无 CAS/无幂等表；唯一写入口）；⑤移除旧写入口（save/revoke/children/add-child/remove-child 端点删除；update-child/children-save/rebuild 不实现）+ 全链路迁移简化（perm-common RoleGrantReq/BatchRevokeReq 移除、perm-client PermissionFeignClient batchGrant/batchRevoke 移除、admin-service RoleProxyServiceImpl 菜单授权直接构造 creates/撤销 list 拿 id+removes（无 revision/无 20037 重试）+ 相关测试重写）；⑥统一预检 prevalidateGrantPlan（八项不变量，去幂等键格式）+ 错误码（20033 完整键冲突/20034 AUTO_DEP 只读/20036 全集预校验/20011 SUB_PERM fail-closed + 父域 resource_type 直查/checkCanGrant 批量收口/20040 委托失败/**20043 子权限属性系统不变量（create 非 null/false 或 update 目标为子权限一律拒绝）**；**20041/20042 枚举与 PermissionErrorCode 建立、校验实现与测试归 T-PERM-041（九轮复审职责拆分，本任务不重复验收）**；砍 20037/20039；**移除废弃的 checkCanGrant 匹配键扩展 condition 维度**（条件不参与身份，八轮复审））；⑦无幂等中间件（第十四轮定案：幂等中间件实现取消（未登记看板），clientRequestId/@Idempotent 从契约移除；前端按钮 disabled 防重复点击，后端单事务+uk+行数断言）"
  - "Mutation Policy：**`PermissionGrantPlanDomainService`（实际代码入口，十一轮复审统一命名；Java 方法名 = `prevalidate(plan)`，`prevalidateGrantPlan` 仅作流程名/章节名使用）** 新增唯一预检入口 prevalidate(plan)（不变量：记录存在及角色/父归属、update/remove 互斥、AUTO_DEP 只读、canGrant 授权传递、**子权限属性系统不变量 20043（先于主权限不变量判定，错误优先级：先按主/子分类，子权限 create 非 null/false -> 20043、子权限 update -> 20043）**、SUB_PERM 约束（父域 resource_type 直查）、完整持久化键冲突、scopeMode/资源/操作兼容性；**主权限条件不变量 20041/20042 由 T-PERM-041 在本预检内增量实现（十轮复审职责确认，本任务不验收，但预检框架为其预留校验位）**）；**授权域内同层调用例外（十轮复审产品确认）**：`PermissionGrantPlanDomainServiceImpl` 组合注入 `PermissionGrantDomainService`（checkCanGrant/validateSingleManualGrants/validateGrantAttributes）为**明确允许的例外**——写链路单事务聚合服务组合领域校验能力，不推广到其他域；apply-grant-plan 唯一写入口强制调用，AppService 禁止自行拼门禁"
  - "测试矩阵适用性覆盖（非笛卡尔积）：成功路径 6 条（create 主权限 / create 主权限嵌套 children（子权限属性固定 null/false）/ create 子权限引用已有父 / update 主权限（canGrant 或 conditionCode 微变更）/ remove 子权限 / remove 主权限验证级联删子）；**子权限属性反例（八轮复审新增）**：children 嵌套 create 带 conditionCode -> 20043、children 嵌套 create 带 canGrant=true -> 20043、parentPermissionId 挂父 create 带 conditionCode -> 20043、update 目标为子权限 -> 20043（含 update 只改 canGrant 与只改 conditionCode 两形态）；**主权限条件不变量反例（20041/20042）由 T-PERM-041 验收（九轮复审职责拆分），本任务不重复**；不变量反例按适用命令--记录存在与角色归属（update/remove）、父记录归属与层级（create 子权限）、AUTO_DEP 只读（update/remove + 向 AUTO_DEP 父 create 子权限）、checkCanGrant（所有 create + update canGrant=true 或 conditionCode 变更）、完整持久化键冲突（create + update，condition 不参与身份）、SUB_PERM（create 子权限）、scope/resource/operation 兼容（create）、段间互斥（update/remove 同 id + 各段内部重复 id）；SUB_PERM 四格 INSTANCE/ALL × 具体域/全局域；**SUB_PERM 全量结构校验（八轮复审新增）：allowed 任一项缺 parent_type/child_types 非数组 -> CONFIG_INVALID（读接口 reason），写链路同步拒绝**；事务故障注入 1 条（已完成至少一次写入后抛异常，验证事实表+审计表均无部分状态）"
  - "**schema 文件删除（第十四轮定案）**：perm-grant.schema.json 删除；api-contract §6.4/§6.5/§6.5.1/**§6.5.2** 为唯一权威契约（错误码枚举 20001-20012/20033/20034/20036/20011/20040、plan 结构、统一响应壳、跨字段 INSTANCE-ALL 约束、local-date-time、grantedBits 十进制字符串、无 clientRequestId）；结构校验由 prevalidate 运行时执行（不做前端机器编译门禁）"
  - "**授权传递查询固定批次数**：prevalidateGrantPlan 内 checkCanGrant 全链路查询次数与资源类型数量无关--一次 selectByTenantAndResourceTypes(tenantId, resourceTypeValues) 加载后内存分组构建 grantedOpIndex；查询次数断言测试防回归"
  - "**错误码精简（第十四轮）**：20033/20034/20036/20011/20040 纳入 PermissionErrorCode 枚举与 api-contract 错误码表；**砍 20037 VERSION_CONFLICT / 20039 IDEMPOTENCY_OPERATOR_MISMATCH**（随 CAS/幂等表移除）；errorResponse.data 必填（失败恒 null）"
  - "**无幂等中间件（第十四轮定案）**：幂等中间件实现取消（未登记看板）；clientRequestId/@Idempotent 从契约移除；前端 saving 期间按钮 disabled 防重复点击，超时提示刷新确认；后端靠单事务原子 + uk 约束 + 受影响行数断言保证不重复/不部分成功"
  - "**SUB_PERM fail-closed + 父域 resource_type 直查**：配置缺失/extra 空/格式错误 -> 20011 拒绝；extra=* 显式通配；父域解析直接读 role_resource_permission.resource_type（废弃 resource_entity_id 反查）；INSTANCE/ALL × 具体域/全局域四格测试；上线前核对配置种子覆盖率"
  - "**写入口五项清单（第十四轮定案）**：applyGrantPlan 必须齐全--① @Transactional(rollbackFor=Exception.class) ② @OperationLog ③ @PermissionChange ④ auditDomainService.recordChangeLog（同事务一条聚合 permission_change_log）⑤ PermissionChangeContext.markRoles；缺任一项导致审计缺失或缓存失效遗漏"
  - "**端到端场景 1（成功提交）**：权限事实落库 + 恰好一条聚合 permission_change_log + 提交后缓存失效及 PermInvalidateEvent 广播发生"
  - "**端到端场景 2（执行失败）**：权限事实与变更日志全部回滚（无部分状态）+ 不触发缓存失效及广播"
  - "wire DTO 通过 api-contract 字段对齐（mock 与真实响应均按 api-contract，nullable/统一响应壳/BigInt 字符串格式由契约固化）"
  - "数据库异常统一转换为业务错误码（uk 冲突->20033 等，不暴露数据库异常）"
  - "**超时/重复提交处理（第十四轮）**：前端 saving 期间按钮 disabled 防重复提交；超时 -> 提示刷新确认（不做重放/不做幂等表）；执行顺序：① 认证 + 目标角色 ROLE:MANAGE 门禁（hasPermission 显式判断 false 抛 SecurityException）-> ② prevalidateGrantPlan -> ③ 单事务执行（creates/updates/removes）+ 受影响行数断言"
  - "GoldenFixtureTest 通过：**6 用例精简**（全局回退/组合位/ALL/资源继承/操作继承/两段组合来源）输出权威结果；全局操作 fixture 由 T-PERM-031 阶段 2-4 完成后生产（本任务依赖 T-PERM-031）"
  - "**grant_revision DDL 砍（第十四轮）**：abstract_role 不新增 grant_revision 列（CAS 移除）；list 响应不含 revision；apply-grant-plan 请求不含 expectedRevision/clientRequestId、响应不含 revision/currentRevision/replayed；**受影响行数断言**：updates/removes 实际影响行数 ≠ 预期 -> 20036 整体回滚"
  - "**schema DDL：子权限属性系统不变量 CHECK（九轮复审职责拆分：本任务负责子权限 CHECK，主权限 CHECK 归 T-PERM-041）**：permission-center.sql 补 `CHECK (depend_on IS NULL OR (condition_id IS NULL AND can_grant = false))`（子权限不变量 DDL 兜底，2026-08-08 七轮复审产品确认，与 20043 同口径；仅最终态建表 DDL，项目未上线不考虑历史数据）"
  - "**SUB_PERM 共享策略对象 + 只读接口（v3.1，八轮复审扩展，十一轮复审补公开入口）**：从 `assertSubPermissionAllowed` 抽取不可变策略对象 `SubPermissionPolicy { mode, reason, allowedTypeCodes, allows(childTypeCode) }`，**唯一公开解析入口 `PermissionGrantPlanDomainService.resolveSubPermissionPolicy(tenantId, parentResourceTypeCode)`**（顶层通配、**全量结构校验（任一 allowed 项缺 parent_type/child_types 非数组 -> CONFIG_INVALID）**、并集去重、大小写不敏感、错误原因统一组装）；新增 `POST role-resource-permission/sub-perm-allowed-types`（api-contract §6.5.2：ROLE:VIEW 门禁但失败响应不同——角色定位失败 20001、无 VIEW 抛 SecurityException、parentResourceTypeCode 无效 20007；reason 枚举 CONFIG_MISSING/EMPTY/INVALID/PARENT_NOT_CONFIGURED/**CHILD_TYPES_EMPTY**）；读接口由 AppService 映射 `resolveSubPermissionPolicy` 结果直接序列化，写链路 prevalidate 复用同一解析器调 `policy.allows(childTypeCode)`，**禁止在 AppService/Controller 另行编写 SUB_PERM 判断（读写同源）**；前端不硬编码允许集"
  - "design_writeback（权威文档全量同步门禁，人工清单核对）：api-contract §6.4/§6.5/§6.5.1/§6.5.2 契约实现状态标注（砍 CAS/幂等/revision 字段、补结构约束、20041/20042 仅主权限、20043 子权限不变量、错误优先级、sub-perm-allowed-types）、permission-center.sql（**删 grant_revision 列 + 删 grant_plan_idempotency 表** + 表注释同步）、core-flows.md §6 + §8/§12/§14 场景（apply-grant-plan 主通道，无 CAS/幂等）、implementation.md §4 全章（接口/DTO/时序图/伪代码，收窄重写 + SubPermissionPolicy）、permission-grant.md status draft -> adopted、任务验收描述--逐项人工核对，任一不同步不通过"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-08
---

# T-PERM-034 4.1 权限授予后端改造

> 状态：proposed（2026-08-02 第十四轮收窄：砍 CAS + grant_revision 列 + 幂等表 + 20037/20039 + 重试 machinery + clientRequestId/@Idempotent（幂等中间件实现取消（未登记看板））；全链路迁移简化为 Feign+DTO+测试替换；schema 删除，契约回归 api-contract）
> 依赖：T-PERM-031（全局操作 fixture 生产）
> 前置验收：见 acceptance（DoD 门禁）

## 背景

v3 权限授予页（T-FE-036）需要后端补齐授权写链路能力。历经多轮评审，2026-08-02 第十二轮单入口收敛（list + apply-grant-plan），第十三轮全链路迁移 + 幂等加固。**第十四轮（2026-08-02）收窄**：经设计复盘，认定 CAS + 持久化幂等表 + 八态状态机是 Stripe 式重幂等，对低频内部管理页错配--并发编辑/网络超时罕见，管理员刷新确认即可。砍 CAS + grant_revision 列 + grant_plan_idempotency 表 + 20037/20039 + hash canonical + replayed/currentRevision + 20037 重试 machinery；幂等中间件实现取消（未登记看板），前端 saving 期间按钮 disabled 防重复点击 + 后端单事务+uk+行数断言；全链路迁移简化（CAS 砍后无 revision/无重试，admin-service 菜单授权直接构造 creates、撤销 list 拿 id 不变）；schema 删除（第十三轮已降级为说明性、不机器校验，维护冗余），契约回归 api-contract 单一来源。核心目标：唯一写入口、单事务原子、受影响行数断言、不变量自动化测试。

## 范围（七项，第十四轮收窄）

1. `RolePermissionItemResp` 暴露 `grantSource`（MANUAL/AUTO_DEP）+ `grantedBits`（63 位位图，十进制字符串，前端 BigInt）
2. `list` 加 `includeChildren` 参数（默认 true 兼容；**v3.1：baseline=true 一次取全量主+子，来源链过滤 dependOn==null；false 仅辅助查询**）；**响应不含 revision**（CAS 砍，无乐观锁基线）
3. `RolePermissionItemResp` 增补 `createdAt`/`childCount`（list 时按 depend_on 分组 COUNT 一次返回）
4. 新增 `apply-grant-plan` 聚合接口（api-contract §6.5.1）：记录级 `plan{creates/updates/removes}`（子权限用 parentPermissionId 挂父；替换 = removes+creates 原子、子权限不迁移）+ **单事务原子执行**（任一失败整体回滚）+ 受影响行数断言；**唯一写入口**；**砍 expectedRevision CAS + grant_revision 列 + 幂等表 + 20037/20039 + clientRequestId/@Idempotent（幂等中间件实现取消（未登记看板））**
5. 移除旧写入口（**八轮复审确认：随本任务迁移 admin-service 后统一删除**）：已实现端点（save/revoke/children/add-child/remove-child）从 PermissionGrantController 删除；计划中接口（update-child/children-save/rebuild）不实现；**兼容期（迁移完成前）add-child 强制复用子权限属性不变量 20043 校验，不得成为绕过路径**；**全链路迁移简化**（CAS 砍后无 revision/无 20037 重试）：perm-common `RoleGrantReq`/`BatchRevokeReq` 移除、perm-client `PermissionFeignClient.batchGrant/batchRevoke` 移除、admin-service `RoleProxyServiceImpl.grantMenuToRole` 改构造 apply-grant-plan.creates（直接传键，不 list）、`revokeMenuFromRole` 改 apply-grant-plan.removes（list 拿 id 不变，现状本就 list）+ `RoleProxyServiceImplTest` 重写
6. 统一预检与错误码：prevalidateGrantPlan 唯一预检（不变量集，去幂等键格式）；完整持久化键冲突 + 20033（**conditionCode/canGrant 不参与身份，八轮复审移除条件维度**）；AUTO_DEP 只读 + 20034；全集预校验 + 20036；SUB_PERM fail-closed + 20011（父域解析改 resource_type 直查）；checkCanGrant 批量收口（一次 selectByTenantAndResourceTypes 内存分组）；委托失败 20040；**子权限属性系统不变量 20043（create 非 null/false 或 update 目标为子权限一律拒绝；错误优先级先于主权限不变量，八轮复审）**；**20041/20042 校验实现与测试归 T-PERM-041（九轮复审职责拆分，本任务不重复）**；**砍 20037 VERSION_CONFLICT / 20039 IDEMPOTENCY_OPERATOR_MISMATCH**
7. **无幂等中间件**（第十四轮定案：幂等中间件实现取消（未登记看板），clientRequestId/@Idempotent 从契约移除；前端按钮 disabled 防重复点击，后端单事务+uk+行数断言）

## 工程加固（第十四轮收窄）

- **Mutation Policy**：`PermissionGrantPlanDomainService.prevalidate(plan)`（**Java 方法名；`prevalidateGrantPlan` 仅作流程名**）唯一预检（不变量集，去幂等键格式；**组合 PermissionGrantDomainService 校验能力——授权域内同层调用为明确允许的例外，十轮复审产品确认**；主权限 20041/20042 由 T-PERM-041 增量实现）；apply-grant-plan 唯一写入口强制调用；测试矩阵入 acceptance
- **Golden fixtures**：`GoldenFixtureTest` 权威用例集（**6 用例精简**：全局回退/组合位/ALL/资源继承/操作继承/两段组合来源），前端比对基准
- **端点契约**：**删除** `docs/contracts/perm-grant.schema.json`；报文契约回归 `api-contract.md §6.4/§6.5/§6.5.1/§6.5.2` 单一来源，补结构约束（统一响应壳/跨字段 INSTANCE-ALL 约束/local-date-time/grantedBits 十进制字符串/错误码枚举（含 20041/20042/20043，九轮复审）/plan 结构/无 clientRequestId）；结构校验由 prevalidateGrantPlan 运行时执行

## 完成记录

（待实现后填写）
