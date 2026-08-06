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
  - docs/design/permission-center/core-flows.md#§6
  - docs/design/permission-center/implementation.md
  - docs/design/frontend/permission-grant.md#§12
  - docs/design/schema/permission-center.sql
depends_on:
  - T-PERM-031
blocks:
  - T-FE-018
acceptance:
  - "七项改造完成（第十四轮收窄）：①RolePermissionItemResp 暴露 grantSource + grantedBits（十进制字符串）；②list 加 includeChildren 参数（默认 true 兼容）；③增补 createdAt/childCount（list 按 depend_on 分组 COUNT 一次返回）；④新增 apply-grant-plan（记录级 creates/updates/removes，单事务原子执行任一失败整体回滚；单事务原子 + 受影响行数断言，无 CAS/无幂等表；唯一写入口）；⑤移除旧写入口（save/revoke/children/add-child/remove-child 端点删除；update-child/children-save/rebuild 不实现）+ 全链路迁移简化（perm-common RoleGrantReq/BatchRevokeReq 移除、perm-client PermissionFeignClient batchGrant/batchRevoke 移除、admin-service RoleProxyServiceImpl 菜单授权直接构造 creates/撤销 list 拿 id+removes（无 revision/无 20037 重试）+ 相关测试重写）；⑥统一预检 prevalidateGrantPlan（八项不变量，去幂等键格式）+ 错误码（20033 完整键冲突/20034 AUTO_DEP 只读/20036 全集预校验/20011 SUB_PERM fail-closed + 父域 resource_type 直查/checkCanGrant 匹配键扩展 condition 维度 + 批量收口/20040 委托失败；砍 20037/20039）；⑦无幂等中间件（第十四轮定案：幂等中间件实现取消（未登记看板），clientRequestId/@Idempotent 从契约移除；前端按钮 disabled 防重复点击，后端单事务+uk+行数断言）"
  - "Mutation Policy：PermissionGrantDomainService 新增唯一预检入口 prevalidateGrantPlan(plan)（八项不变量：记录存在及角色/父归属、update/remove 互斥、AUTO_DEP 只读、canGrant 授权传递含 condition 维度、conditionCode 清空/替换扩大、SUB_PERM 约束（父域 resource_type 直查）、完整持久化键冲突、scopeMode/资源/操作兼容性）；apply-grant-plan 唯一写入口强制调用，AppService 禁止自行拼门禁"
  - "测试矩阵适用性覆盖（非笛卡尔积）：成功路径 6 条（create 主权限 / create 主权限嵌套 children / create 子权限引用已有父 / update 主子权限 / remove 子权限 / remove 主权限验证级联删子）；不变量反例按适用命令--记录存在与角色归属（update/remove）、父记录归属与层级（create 子权限）、AUTO_DEP 只读（update/remove + 向 AUTO_DEP 父 create 子权限）、checkCanGrant（所有 create + update canGrant=true 或 conditionCode 变更）、条件完整键冲突（create + update 条件后撞已有分支）、SUB_PERM（create 子权限）、scope/resource/operation 兼容（create）、段间互斥（update/remove 同 id + 各段内部重复 id）；SUB_PERM 四格 INSTANCE/ALL × 具体域/全局域；事务故障注入 1 条（已完成至少一次写入后抛异常，验证事实表+审计表均无部分状态）"
  - "**schema 文件删除（第十四轮定案）**：perm-grant.schema.json 删除；api-contract §6.4/§6.5/§6.5.1 为唯一权威契约（错误码枚举 20001-20012/20033/20034/20036/20011/20040、plan 结构、统一响应壳、跨字段 INSTANCE-ALL 约束、local-date-time、grantedBits 十进制字符串、无 clientRequestId）；结构校验由 prevalidateGrantPlan 运行时执行（不做前端机器编译门禁）"
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
  - "design_writeback（权威文档全量同步门禁，人工清单核对）：api-contract §6.4/§6.5/§6.5.1 契约实现状态标注（砍 CAS/幂等/revision 字段、补结构约束）、permission-center.sql（**删 grant_revision 列 + 删 grant_plan_idempotency 表** + 表注释同步）、core-flows.md §6 + §8/§12/§14 场景（apply-grant-plan 主通道，无 CAS/幂等）、implementation.md §4 全章（接口/DTO/时序图/伪代码，收窄重写）、permission-grant.md status draft -> adopted、任务验收描述--逐项人工核对，任一不同步不通过"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-05
---

# T-PERM-034 4.1 权限授予后端改造

> 状态：proposed（2026-08-02 第十四轮收窄：砍 CAS + grant_revision 列 + 幂等表 + 20037/20039 + 重试 machinery + clientRequestId/@Idempotent（幂等中间件实现取消（未登记看板））；全链路迁移简化为 Feign+DTO+测试替换；schema 删除，契约回归 api-contract）
> 依赖：T-PERM-031（全局操作 fixture 生产）
> 前置验收：见 acceptance（DoD 门禁）

## 背景

v3 权限授予页（T-FE-036）需要后端补齐授权写链路能力。历经多轮评审，2026-08-02 第十二轮单入口收敛（list + apply-grant-plan），第十三轮全链路迁移 + 幂等加固。**第十四轮（2026-08-02）收窄**：经设计复盘，认定 CAS + 持久化幂等表 + 八态状态机是 Stripe 式重幂等，对低频内部管理页错配--并发编辑/网络超时罕见，管理员刷新确认即可。砍 CAS + grant_revision 列 + grant_plan_idempotency 表 + 20037/20039 + hash canonical + replayed/currentRevision + 20037 重试 machinery；幂等中间件实现取消（未登记看板），前端 saving 期间按钮 disabled 防重复点击 + 后端单事务+uk+行数断言；全链路迁移简化（CAS 砍后无 revision/无重试，admin-service 菜单授权直接构造 creates、撤销 list 拿 id 不变）；schema 删除（第十三轮已降级为说明性、不机器校验，维护冗余），契约回归 api-contract 单一来源。核心目标：唯一写入口、单事务原子、受影响行数断言、不变量自动化测试。

## 范围（七项，第十四轮收窄）

1. `RolePermissionItemResp` 暴露 `grantSource`（MANUAL/AUTO_DEP）+ `grantedBits`（63 位位图，十进制字符串，前端 BigInt）
2. `list` 加 `includeChildren` 参数（默认 true 兼容；主权限视图只取 dependOn==null）；**响应不含 revision**（CAS 砍，无乐观锁基线）
3. `RolePermissionItemResp` 增补 `createdAt`/`childCount`（list 时按 depend_on 分组 COUNT 一次返回）
4. 新增 `apply-grant-plan` 聚合接口（api-contract §6.5.1）：记录级 `plan{creates/updates/removes}`（子权限用 parentPermissionId 挂父；替换 = removes+creates 原子、子权限不迁移）+ **单事务原子执行**（任一失败整体回滚）+ 受影响行数断言；**唯一写入口**；**砍 expectedRevision CAS + grant_revision 列 + 幂等表 + 20037/20039 + clientRequestId/@Idempotent（幂等中间件实现取消（未登记看板））**
5. 移除旧写入口：已实现端点（save/revoke/children/add-child/remove-child）从 PermissionGrantController 删除；计划中接口（update-child/children-save/rebuild）不实现；**全链路迁移简化**（CAS 砍后无 revision/无 20037 重试）：perm-common `RoleGrantReq`/`BatchRevokeReq` 移除、perm-client `PermissionFeignClient.batchGrant/batchRevoke` 移除、admin-service `RoleProxyServiceImpl.grantMenuToRole` 改构造 apply-grant-plan.creates（直接传键，不 list）、`revokeMenuFromRole` 改 apply-grant-plan.removes（list 拿 id 不变，现状本就 list）+ `RoleProxyServiceImplTest` 重写
6. 统一预检与错误码：prevalidateGrantPlan 唯一预检（**八项不变量**，去幂等键格式）；条件冲突完整键查重 + 20033；AUTO_DEP 只读 + 20034；全集预校验 + 20036；SUB_PERM fail-closed + 20011（父域解析改 resource_type 直查）；checkCanGrant 匹配键扩展 condition 维度 + 批量收口（一次 selectByTenantAndResourceTypes 内存分组）；委托失败 20040；**砍 20037 VERSION_CONFLICT / 20039 IDEMPOTENCY_OPERATOR_MISMATCH**
7. **无幂等中间件**（第十四轮定案：幂等中间件实现取消（未登记看板），clientRequestId/@Idempotent 从契约移除；前端按钮 disabled 防重复点击，后端单事务+uk+行数断言）

## 工程加固（第十四轮收窄）

- **Mutation Policy**：`PermissionGrantDomainService.prevalidateGrantPlan(plan)` 唯一预检（八项不变量，去幂等键格式）；apply-grant-plan 唯一写入口强制调用；测试矩阵入 acceptance（砍幂等三格 + CAS 并发击穿）
- **Golden fixtures**：`GoldenFixtureTest` 权威用例集（**6 用例精简**：全局回退/组合位/ALL/资源继承/操作继承/两段组合来源），前端比对基准
- **端点契约**：**删除** `docs/contracts/perm-grant.schema.json`；报文契约回归 `api-contract.md §6.4/§6.5/§6.5.1` 单一来源，补结构约束（统一响应壳/跨字段 INSTANCE-ALL 约束/local-date-time/grantedBits 十进制字符串/错误码枚举/plan 结构/无 clientRequestId）；结构校验由 prevalidateGrantPlan 运行时执行

## 完成记录

（待实现后填写）
