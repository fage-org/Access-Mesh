---
doc_type: task
id: T-ORG-001
title: 统一本地主体 ID（B-lite：共享主体 ID，删除 OperatorSubjectResolver）
status: done
plan: docs/archive/2026-08-27/product-vertical-slice-plan.md
domain: org-user
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/implementation.md
  - docs/design/default-org-tree-user-lifecycle.md
depends_on: [T-PERM-042]
blocks: [T-ACCESS-018, T-ADMIN-022]
acceptance:
  - "新建本地用户后 sys_user.id == abstract_user.id；先创建外部主体、再创建本地用户不会主键碰撞（主体专用序列或等价机制生效）"
  - "生产代码中 OperatorSubjectResolver 与 resolveOperatorSubjectId 引用为零（全量 grep 清零，含 javadoc）"
  - "登录 ID、审计操作者 ID（createdBy/updatedBy/deletedBy）、user_role.abstract_user_id、Sa-Token 会话、perm:effective-roles 缓存键数值一致且均为主体 ID"
  - "登录/审计/缓存对外接口形态不变：Sa-Token 仍登录同一 Long、审计仍记录该 Long、user_role 与角色展开缓存仍以主体 ID 为键——变化集中在 ID 分配、投影写入调用方与 Resolver 删除"
  - "缓存框架与 catalog 零修改：主体键缓存（effective-roles 等）本以 abstract_user.id 为标识符，统一后数值与键格式均不变；不新增迁移专用 evictAll、审计日志或兼容键；空库重建 runbook 含清理开发/验收 Redis 实例"
  - "既有用户投影链路调用方随统一主体 ID 切换（数值统一后 sys_user.id = abstract_user.id，投影 code 语义自然一致，无需投影切换代码）；USER/ROLE 投影全写路径补齐归 T-ACCESS-019"
  - "空库重建 runbook：按权威 DDL 重建后走全量种子 + 用户/角色/组织/菜单样例验证主体链；不做在线双读迁移"
  - "全量单测 + CI 门控全绿（CI 已由 T-ACCESS-017 落地，无需外部主机重复执行）；T-ACCESS-017 特征测试全绿（Resolver 映射特征测试随 Resolver 删除同步移除）"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-23
---

# T-ORG-001 统一本地主体 ID（B-lite）

## 背景

本地用户现有三个数值独立 ID（sys_user.id 独立自增用于登录/审计；abstract_user.id 独立自增用于权限主体，创建顺序为先插 sys_user 再以其 id 为 external_id 插 abstract_user；resource_entity(ADMIN_USER).id 为实例授权代理键）。OperatorSubjectResolver 约 80 次调用分布于 18 个生产文件，已是系统性心智税；三空间同为 Long 导致实例门禁错传难以被断言识别（该缺陷已由 T-PERM-042 在 API 层面消除）。已确认采纳 B-lite：abstract_user.id 为唯一主体 ID，本地用户 sys_user.id = abstract_user.id，外部主体仅有 abstract_user 行；不引入全局对象 ID 中心，最多使用只服务于用户主体的数据库序列防碰撞。

本任务在 T-PERM-042（引擎显式 API）之后、类型收敛（T-ACCESS-018）与投影补齐（T-ACCESS-019）之前实施：投影在 T-ACCESS-019 直接以最终主体 ID + 最终类型码一次写成，本任务不需要动投影语义。

## 范围

- ID 分配机制：用户主体专用序列（或等价方案，按 T-ACCESS-016 定稿），本地用户创建写链路调整（abstract_user 与 sys_user 同 ID 落库，写入顺序按实现确定）。
- 删除 OperatorSubjectResolver 与全部调用点；会话、OperatorContext、审计填充、user_role、既有投影链路调用方统一主体 ID。
- 空库重建 runbook（含清理开发/验收 Redis）与全量回归。

## 当前口径

- 统一后保留的两个 ID 属不同概念（主体"谁" vs resource_entity"哪条授权记录"），不视为重复设计。
- 缓存零修改：主体键缓存标识符本就是 abstract_user.id，统一不改键；不做 evictAll 迁移（空库重建模式下开发 Redis 随 runbook 清理）。
- 外部同步主体业务键链路不变（abstract_user.external_id 语义保留）。
- 未上线、库可重建：不做在线迁移、不建双读/兼容层。
- 与 T-PERM-042 / T-ACCESS-018 分批独立提交，禁止一次提交同时修改身份、资源类型、引擎与缓存。

## 非目标 / 遗留

- 不删除 abstract_user / resource_entity 表，不做全平台统一 ID。
- 不重写缓存框架（保留冻结）。
- USER/ROLE 投影全写路径补齐归 T-ACCESS-019；bootstrap 种子归 T-ACCESS-020。

## 实施记录（2026-08-23）

### ID 分配机制与创建写链路（architecture §12.2）

- 权威 DDL：`sys_user.id` `BIGSERIAL` → `BIGINT PRIMARY KEY`（去自增），表注释更新为已落地终态。
- `AbstractUserMapper.nextSubjectId()`：`SELECT nextval(pg_get_serial_sequence('abstract_user','id'))` 预取主体 ID。
- `LocalProjectionDomainService.createLocalUserSubject(tenantId, name, enabled, extraJson)`（新增）：预取 N → 显式插 `abstract_user(id=N, external_id=N, ADMIN_USER)` + `resource_entity(ADMIN_USER, code=N)`，返回 N。
- `UserWriteAppServiceImpl.createUser`：改为「主体链先行」——先 `createLocalUserSubject` 取 N，再以 `sys_user(id=N)` 显式插入；审计/markUsers 均用同一 N。updateUser/updateStatus 等投影 upsert 调用方不动（external_id=N 语义自然一致）。
- **Flex 主键策略适配（实施中发现）**：`@Id(keyType=KeyType.Auto)` 会把已设置的 id 排除在生成的 INSERT 外（`abstract_user` 首轮静默拿到自增 id ≠ 预取值、`sys_user` 直接 NULL 主键违约）。修复：`SysUser` 改 `KeyType.None`（唯一插入路径 createUser 恒显式赋 id；`insertBatch` 无调用点）；`AbstractUser` 保留 Auto（外部主体依赖自增）并新增 `insertWithExplicitId` 显式 SQL（extra 走 JsonbStringTypeHandler）。
- 投影缺失补建分支（单条/批量 upsert 的 insert 路径）维持自增取号不动（用户决策 2026-08-23：统一后正常链路不可达，非数字场景 fail-closed）。

### Resolver 删除（生产代码 grep 含 Javadoc 完全清零）

- 删除 `OperatorSubjectResolver.java` 与 `PermQueryEngine.resolveOperatorSubjectId`。
- 82 处 `requireSubjectId` 调用语句全量转换：门禁直接使用 `operatorId`（统一后即主体 ID）；Javadoc 主体契约表述同步（引擎/PermQuery/OperationCodeConstants/PermissionGrantDomainService(+Impl)/PermissionGrantPlanDomainService）。
- 原 fail-closed 语义等价保留：操作者主体不存在 = 无角色 = 引擎全量拒绝。

### 会话/审计/缓存（形态零变化核对）

- Sa-Token `StpUtil.login(user.getId())`、审计戳记、`user_role.abstract_user_id`、`perm:effective-roles` 主体键：对外仍是同一 Long，数值统一后即主体 ID；缓存框架与 catalog 零修改。

### 空库重建 runbook

- 新增 `docs/design/access-service-rebuild-runbook.md`（清库 → 权威 DDL+种子 → **Redis 清理（必做，防旧主体键命中重建后重叠 id）** → 主体链六步验证 + 常见问题表），`docs/README.md` 权威来源与目录树登记。

### 测试

- 新增 `LocalSubjectIdUnificationPgIT`（真实 PG+Redis，2 用例）：① createUser 后 `sys_user.id == abstract_user.id`、`external_id`/资源 code = 主体 ID 字符串化；② 外部主体先建/后建两种顺序与本地用户共用序列均不碰撞。
- 适配：`UserWriteAppServiceFaultInjectionTest/IT`（故障注入点切 `createLocalUserSubject`，事实/投影顺序语义随新链路更新）；`AuthorizationChangeInvalidationPgIT` 操作者 fixture 改显式同 ID 主体；`PermissionCharacterizationPgIT` 移除 Resolver 映射特征用例（验收条款）；`LoginSessionPgIT` fixture 显式 id；`PermissionViewAppServiceImplTest` 双 ID 桩改统一 ID、移除主体缺失失效前提用例；16 处测试身份桩清理。
- 验证：全量 `mvn test -pl access-service` 双 execution **BUILD SUCCESS**——单测 674（2 既有 skip）+ 容器 65 全绿。

## 评审修复记录（2026-08-23，AI 评审五项全部核实属实）

- **P1 空库 runbook 无法执行主体链验收**：如实声明前置——仓库无 `docker-compose.yml`（README/AGENTS 的 compose 引用为 T-ACCESS-020 已登记断链）、DDL 无管理员种子；新增「临时验证 fixture」节（SQL 直插同 ID 主体链 + BASIC_ROLE + `ROLE:MANAGE` scopeAll + 验证码登录步骤）使 HTTP 主体链在 T-ACCESS-020 前可执行，并标注 bootstrap 落地后整节删除；主体链验证补自动化 IT 指引（Unification/Invalidation/LoginSession 三 IT 覆盖服务级链路）。
- **P2 Redis 选择性清理模式**：键为租户优先 `{tenantId}:{catalogCode}:{identifier}`（如 `1:perm:effective-roles:456`），原 `perm:*`/`op_perm:*` 模式不会命中；改为 `1:*` 按租户整段清理（`op_perm:` 实为 `perm:operation-permissions-by-type` 目录内的 identifier 前缀，不存在该目录段）。
- **P2 外部主体创建路径**：`/api/perm/user/create` 不存在，改为直连形态 `/api/perm/abstract-user/create` 并注明 Gateway 前缀差异。
- **P3 DDL 幂等表述**：普通 `CREATE TABLE` 非幂等，改为「在空 schema 中一次性执行」并入常见问题表。
- **P3 Mapper 注解风格**：`AbstractUserMapper` 两处全限定名注解改为 import `Insert`/`Select`（消除生产 Mapper 孤例）。

## 二轮复评修复记录（2026-08-23，五项全部核实属实，均为 runbook 修正）

- **P1 fixture 权限不足**：原仅授 `ROLE:MANAGE`（掩码只含 VIEW），第一个写接口即 403。按第 3 节六步逐门禁补齐最小类型级 scopeAll 授权（对照控制器实测）：`ADMIN_USER:CREATE`、`USER:CREATE`、`ROLE:CREATE`、`ROLE:MANAGE`、`ADMIN_ORG:CREATE`、`ADMIN_ORG:UPDATE`（子组织父级校验与用户挂载实例级由 scopeAll 覆盖）、`ADMIN_MENU:CREATE`。
- **P1 新用户断言复用管理员 ID**：步骤 1 改用独立变量 M（接口响应 id，必然大于 fixture 管理员 N）按 M 回查三表，杜绝命中 fixture 数据的误判。
- **P2 直连路径混入 Gateway 前缀**：admin 域控制器真实映射无 `/admin` 前缀（实测 `AdminUserController=/user`、`AdminRoleController=/role`、`/org`、`/user-org`、`/menu`），直连路径全部修正并在节首声明 Gateway 前缀差异。
- **P2 登录缺 tenantId**：`LoginReq.tenantId` 为 `@NotBlank`（实现内 `Long.parseLong`），登录示例补 `tenantId:"1"`，404/400 场景入常见问题表。
- **P2 Redis 扫描无认证**：`--scan` 一侧补 `-a <密码>`（原命令 NOAUTH 零键枚举、静默不删），并加常见问题条目。

## 三轮复评修复记录（2026-08-23，1 项属实）

- **P1 用户挂载权限码不正确**：`/user-org/assign` 经 `OrgOperationCodeMapper.resolveForUserOrg` 把普通组织 UPDATE 映射为 `MANAGE_MEMBER`(bit 256)、岗位映射为 `ASSIGN_POSITION_USER`(bit 128)，与 UPDATE(bit 4) 是不同操作码，scopeAll 不跨码覆盖——fixture 补 `(17,256)` 与 `(17,128)` 两行，`(17,4)` 注释收窄为「子组织父级实例校验」。
