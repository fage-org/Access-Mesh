---
doc_type: task
id: T-PERM-056
title: user_type/role_type 删除零检查——主体/角色类型引用面保护（T-PERM-050 盘点拆分）
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/api-contract.md#§5.1
depends_on: []
blocks: []
acceptance:
  - "背景（T-PERM-050 盘点发现，2026-09-09 用户拍板拆分登记）：deleteTypesByIds 对自定义 user_type/role_type 删除零检查零级联——abstract_user.user_type / abstract_role.role_type 以 type_value 引用类型定义，删除被引用类型后该类型用户/角色行反解缺项（管理视图不可达、无清理入口），与 T-PERM-050 同性质的孤儿面"
  - "修法方向执行时决策：删除保护（存在引用该 typeValue 的有效 abstract_user/abstract_role 行时拒绝删除，对齐 resource_type 行数守卫先例 20056）/ 级联 / 其他；role_type 侧 ORG/POSITION 等种子类型 isSystem=true 本就不可删，面仅限自定义角色类型"
  - "回归锁（旧实现下失败的用例）+ schema 注释 + api-contract §5.1 语义回写"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-09
---

# T-PERM-056 user_type/role_type 删除引用面行数守卫

> 状态：done（2026-09-09 收口）
> 依赖：无硬依赖

## 背景

T-PERM-050 盘点发现：`TypeDefinitionAppServiceImpl#deleteTypesByIds` 的引用检查仅覆盖 `typeKey=resource_type`（行数守卫 + 操作/授权级联）。自定义 `user_type`/`role_type` 删除同样零检查——例：建自定义用户类型「CONTRACTOR」并创建该类型用户后删除类型，用户的 `user_type` 指向已软删类型值，批量反解缺项、管理视图不可达。与 T-PERM-050 根治的 resource_type 孤儿面同性质，但主题不同（主体/角色类型引用面），2026-09-09 用户拍板拆分登记。

## 范围

- user_type：`abstract_user.user_type` 引用面检查。✅
- role_type：`abstract_role.role_type` 引用面检查。✅
- 存量孤儿订正语句登记。✅（rebuild-runbook §3 检测定位条目——不做自动订正，迁往哪个类型/是否软删是业务决策）

## 当前口径（2026-09-09 用户拍板）

- **修法 = 删除保护**：`deleteTypesByIds` 对 user_type/role_type 补引用面守卫——存在引用该 typeValue 的有效 `abstract_user`/`abstract_role` 行时整批拒绝（20056，message 列冲突 typeCode）；管理员须先删/迁走该类型用户/角色再删类型。**不级联**：用户/角色是业务主体数据，对标 resource_entity 面=守卫先例（T-PERM-052），而非操作位/投影=级联先例（T-PERM-050/051）——级联会连带 user_role 关系、角色授权行、USER/ROLE 投影行、缓存失效与 Gateway 广播，一次删类型静默蒸发一批登录主体。
- **role_type 并发闭合**：批删含 role_type 时与角色写入口共持 `ABSTRACT_ROLE` 树写锁并锁内重读（锁序固定 RESOURCE_ENTITY→ABSTRACT_ROLE 单向）。自定义 role_type 角色行的唯一**创建**入口是角色同步通道（`AbstractRoleSyncAppServiceImpl` sync/full-sync 均持同锁；管理面 createRole 经 `RoleType.fromValue` 枚举校验只接受种子类型进不来，updateRole/moveRole 可写已存在行但亦持同锁）——「守卫查零行→并发建该类型角色→类型删除落库」交错双向闭合。
- **user_type best-effort**：用户写入口（createUser/用户同步）无锁可复用，用户行无树结构本无锁需求，为极窄交错给高频用户创建加分布式锁不成比例——对齐 T-PERM-050 级联并发 best-effort 先例；交错残留由 typeValue 软删不复用兜底（孤儿 user_type 值永不撞新类型，`uk_abstract_user` 部分索引无冲突恶化路径），后果同存量孤儿面（列表反解缺项，无越权通道）。
- **错误码复用 20056**（不新排号）：`TYPE_OWNERSHIP_CHANGE_CONFLICT` message 措辞扩为「有效引用行——资源行/用户行/角色行」；抛出点文案分别为「类型下存在有效资源行/用户行/角色行，不可删除: {typeCode}」。
- **守卫查询**：`SubjectDomainService.findUserTypesWithValidRows / findRoleTypesWithValidRows`（对齐 `ResourceEntityDomainService.findTypesWithValidRows` 先例；mapper `selectDistinctUserTypesWithValidRows / selectDistinctRoleTypesWithValidRows` 一次 IN 批量 DISTINCT，防批删循环单查 §8.4.8）。
- **写入口事实**：role_type 种子（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE）isSystem 不可删；user_type 种子（USER/SERVICE/LOCAL_USER）同理——守卫面实际仅覆盖租户自建类型行。组织→角色投影（LocalProjection）只产 ORG/POSITION 种子类型行，不构成自定义类型写入口。

## 验收对照

| 验收条目 | 终态 |
|---|---|
| 修法方向（保护/级联/其他，执行时决策） | 用户二选一拍板=删除保护（20056 整批拒绝，对齐 resource_type 行数守卫先例），弃级联 |
| 回归锁（旧实现下失败的用例） | 单测 3 新用例：user_type 拒删（20056+零写入+守卫批量查询+不持锁）/ role_type 拒删+ABSTRACT_ROLE 锁 / resource_type 纯批不触主体守卫（分族防误伤）；旧实现（零检查）下删除照常完成不抛异常，前两条必红。1 用例演进：shouldNotTouchResourceTypeFacesWhenDeletingOtherTypeKeys 补守卫放行 stub（user_type 无引用照删，维持原意图）。PgIT 1 用例（真实库）：user_type/role_type 各自拒删 + 清走引用后放行 + role 锁真实取放 |
| schema 注释 | type_definition 表注释补 user_type/role_type 删除保护语义 |
| api-contract §5.1 | remove 段补主体类型删除保护（守卫语义 + 锁闭合/best-effort 并发口径 + 20056 措辞扩展），last_reviewed 注记 |
| 存量订正登记 | rebuild-runbook §3 检测条目（SELECT 悬空主体行供人工处置，不做自动订正） |

## 完成记录

- 实现：`TypeDefinitionAppServiceImpl#deleteTypesByIds` 主体类型守卫块（`rejectIfSubjectTypeReferenced` 参数化私有方法，user_type/role_type 各调一次）+ role_type 锁块（ABSTRACT_ROLE 共持+锁内重读，与 resource_type 锁块合流）；`SubjectDomainService`/`SubjectDomainServiceImpl` 新增 `findUserTypesWithValidRows`/`findRoleTypesWithValidRows`；`AbstractUserMapper`/`AbstractRoleMapper`（接口+XML）新增 `selectDistinctUserTypesWithValidRows`/`selectDistinctRoleTypesWithValidRows`；`PermissionErrorCode` 20056 message 扩展。
- 回归证据（2026-09-09）：`mvn test -pl access-service -DskipTestcontainers=true` 单测轨道全绿（TypeDefinitionAppServiceImplTest 47/0 含 3 新用例）；`mvn test -pl access-service -Dtest=TypeDefinitionProjectionPgIT` 容器 8/0（含 T-PERM-056 真实库用例）；收口 `mvn test -T 1C`（E2E 含）全 10 模块 SUCCESS——聚合 3012 tests / 0 failures / 0 errors / 0 skipped。
- 设计回写：api-contract §5.1 + last_reviewed、schema type_definition 表注释、rebuild-runbook §3、decision-registry 2026-09-09 定案行、本卡收口。
- 双轨评审（2026-09-09）：代码轨零 P0-P2（实证通过项 11 条：锁序无对向死锁路径全库穷举、role_type 锁闭合前提/写入口事实实证、锁内重读消费链、守卫与先例同构、Mapper 实参序/XML/索引、回归锁强度、缓存事务边界、构造适配、规范、残留、文档一致）；文档轨 P1×1（本卡收口时序——评审处置+收口全量后回填完成，已按 T-PERM-050 先例路径闭环）+ P3×3。P3 处置：PgIT 场景3 注释锁断言修正（同线程重入会掩盖泄漏，不以此证明释放）、「唯一写入口」四处精化为「唯一创建入口」（api-contract/registry/任务卡/代码注释）、任务卡节序对齐模板；登记不改：锁判定先于 isSystem 过滤（与 resource_type 面既有形态一致，改则两面分叉）、T-PERM-050 卡「拆分登记」历史语态（终态冻结记录）、AGENTS.md 不收录（粒度与 T-PERM-050 级联同粒度未收录，机制已三重钉死）。
