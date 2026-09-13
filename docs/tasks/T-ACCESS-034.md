---
doc_type: task
id: T-ACCESS-034
title: 操作码合一与 USER 轨细粒度化
status: done
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§5.1
  - docs/design/access-service-api-contract.md#§12.1（资源与操作：操作注册与码表；T-ACCESS-040 重挂总册）
  - docs/design/schema/access-service.sql（操作码种子）
  - docs/design/access-service-api-contract.md#§4（管理域门禁规范：操作码常量段；T-ACCESS-040 重挂总册）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-040
blocks: []
acceptance:
  - "全仓单一操作码常量面（按资源类型分节）；AdminOperationCode / OperationCodeConstants 旧类消亡"
  - "API:ACCESS 补录常量并消除四处裸字符串（BootstrapGraphDefinition×2、SnapshotAssembler、PermissionCheckAppServiceImpl）；ROLE:ASSIGN / ROLE:REVOKE 按「统一常量面=注册表镜像」收录（DDL 种子在册、后端零代码引用；与 T-PERM-019 D3「常量类只镜像代码引用面」的口径关系在本卡登记，OperationCodeConstants javadoc 与 implementation §8.3 死常量注记同步）"
  - "USER 轨换绑按字段分档落地：UserManageAppServiceImpl.updateUser 的 name/extra 变更查 USER:UPDATE、enabled != null 查 USER:ENABLE（组合字段须同时通过全部涉及的操作码）；deleteUsers 查 USER:DELETE；字段分档门禁作用于**非自身更新**路径——现有 operatorId.equals(userId) 自身豁免语义不变（自身更新不新增门禁）；空 patch（仅 userId、无任何业务字段）直接 90001 拒绝——UserUpdateReq 补「至少一个业务字段」校验（走 Bean Validation → MethodArgumentNotValidException → 90001 通道；禁服务层 IllegalArgumentException——该通道经 GlobalExceptionHandler 返回 code=400 非 90001），堵住无门禁落点的无条件写副作用（现状空 patch 仍写库/投影/审计/失效缓存）；行为锁覆盖：name-only / extra-only / enabled-only / 组合 / 全空拒绝 / 仅持 UPDATE 改 enabled 拒绝 / 自身更新且无任何 USER 操作位仍成功 / DELETE 批量；同文件三处 ROLE:MANAGE 调用点不动"
  - "bootstrap 固定图 GrantSpec 集合零变更（USER 段维持 CREATE/VIEW/UPDATE/DELETE/ENABLE/RESET_PASSWORD；断言固定图无 USER:MANAGE 行）"
  - "空库首管理员经 /api/perm/abstract-user/update|remove 由恒拒转放行（PgIT 正向锁；现状固定图不含 MANAGE 位、perm 轨两入口在空库不可用，本任务修复该死锁——由拒转放行是预期行为变化，非等价改写）"
  - "USER:MANAGE 全仓零残留：生产代码（含 ConditionAppServiceImpl 等注释残留）、DDL 种子、固定图、契约、schema/表征测试（InstanceGateBusinessCodePgIT、UserRoleWriteProjectionPgIT 的手工 MANAGE 行迁细码；**AccessServiceSchemaH2Test 的 USER:MANAGE 位值断言与种子对清迁**）；schema 注释（access-service.sql 多处「16 被 MANAGE 占用」表述）随删种同批修正；以全仓 rg 'USER.{0,4}MANAGE' 清单核对"
  - "AdminCacheCatalog.OPERATION_CODE 死缓存条目删除；DualInstanceContainerTest 换验通样例（该删除唯一归属本任务，T-ACCESS-039 以此为前置）"
  - "SDK DefaultOpCode 不动；EDIT 无服务端预置登记为已知差异（契约/扩展指南注记）"
  - "e2e 与全量回归绿；操作码契约测试更新"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

两套操作码常量（AdminOperationCode / OperationCodeConstants）是同一物理注册表 operation_permission 的两个局部视图：CRUD 四码字面相同，admin 独有 11 码是真实操作位；USER 资源存在粗细粒度双轨（admin 轨细粒度 vs perm 轨 USER:MANAGE）。定案：常量面合一 + USER 轨细粒度化（capability-structure §5.1，唯一权限语义变更）。

## 范围

1. 建单一操作码常量面，两套并一 + 补录（理由见 acceptance 第 2 条）；全部调用点改引用（以全仓 rg 清单为准，不预设计数）。
2. UserManageAppServiceImpl 换绑：updateUser 按字段分档（name/extra→USER:UPDATE；enabled≠null→USER:ENABLE）、deleteUsers→USER:DELETE；USER:MANAGE 退役（DDL 种子、固定图、契约同步清理）。
3. ROLE / RESOURCE 等类型的 MANAGE 惯例不动；粗细统一仅 USER 一处。
4. 死缓存条目删除与测试样例替换。
5. SDK 已知差异登记（EDIT 无预置）。
6. bootstrap 固定图（零变更断言）、schema/表征测试迁移、注释残留清扫、e2e、契约（新册）回写。

## 当前口径

- 空库起步无存量授权数据，语义变更无迁移负担；bootstrap 幂等重种。
- 自身豁免保留（裁决依据：admin 轨 UserServiceImpl 同款 `if (!userId.equals(currentUserId))` 豁免在册——两轨一致即「与 admin 轨对齐」）；自身更新/删除不新增门禁，行为锁含「自身更新且无任何 USER 操作位仍成功」。
- 前端 user 页已用细粒度（`views/system/user/utils/perms.ts`）且 frontend 零 abstract-user API 消费（已核实）；e2e 无 USER:MANAGE 断言（已核实，无迁移项）。
- depends_on 含 040：契约回写落新册，不写旧册。

## 验收对照

见 frontmatter acceptance。

## 完成记录（2026-09-13 收口）

- **常量面合一**：新册 `engine/constant/OperationCode.java`（22 常量 = 两旧册并集 19 + 补录 ACCESS/ASSIGN/REVOKE，按资源类型分节、值名一致——两项命名用户拍板，registry 2026-09-13 T-ACCESS-034 行）；两旧类删除，57 个 Java 文件调用点机械改指（全仓 rg 旧类名生产/测试源码零残留，唯一命中为新册 javadoc 历史注记）。四处 ACCESS 裸字符串消除（BootstrapGraphDefinition×2、SnapshotAssembler 私有常量删除、PermissionCheckAppServiceImpl）。
- **口径关系登记（acceptance 第 2 条指定）**：ROLE:ASSIGN/REVOKE 按「统一常量面=注册表镜像」收录——DDL 种子在册即收录常量，即使后端零代码引用；该口径**取代** T-PERM-019 D3「常量类只镜像代码引用面」（仅约束常量面收录范围；D3 的种子-代码一致性核对方法不受影响）。落点：新册 javadoc + engine/implementation §8.3 死常量注记改写。
- **USER 轨换绑**：updateUser 字段分档（name/extra→USER:UPDATE、enabled≠null→USER:ENABLE、组合须全过、自身豁免保留且零引擎调用有锁）；deleteUsers→USER:DELETE；同文件三处 ROLE:MANAGE 语义未动。空 patch 经 `AbstractUserUpdateReq.isAtLeastOneBusinessFieldPresent()` @AssertTrue → MethodArgumentNotValidException → 90001（PermUserController @Valid + GlobalExceptionHandler 既有通道；`AbstractUserUpdateReqValidationTest` 锁空拒绝/单字段三分支/组合放行）。
- **USER:MANAGE 退役**：DDL 种子行删除（125→124，位 16 空闲不复用，四处注释修正）；H2/Postgres schema 测试（种子集 24 对 + 位值断言删）同步；InstanceGateBusinessCodePgIT/UserRoleWriteProjectionPgIT 手工 MANAGE 行迁细码（UserRoleWriteProjectionPgIT 含分档锁：仅 UPDATE 位用户 name-only 放行/enabled-only 拒/组合拒；MANUAL 授权 DDL CHECK 限单操作位故三细码分三行授予）；ConditionAppServiceImpl 两处「USER:MANAGE 同款」注释改「业务编码轨同款」；契约 §12.1 remove 示例 USER:MANAGE→ROLE:MANAGE、§16.4 参照系措辞更新。残留面以 `rg 'USER.{0,4}MANAGE'` 清单核对：现存命中均为退役注记形态（契约/schema/runbook/architecture/代码注释的历史时态叙述与 registry/裁决文本/已终态卡叙事），现在时活引用零。
- **bootstrap 固定图零变更**：GrantSpec 值零变化（仅常量源换指）；`BootstrapGraphUserSectionTest` 锁 USER 段六细码无 MANAGE；空库首管理员由恒拒转放行正向锁 = `FirstAdminUserTrackPgIT`（真实 PG：bootstrap 后 create→name-only 改名→enabled-only 禁用→remove 全链放行 + 投影软删断言 + 固定图对 USER:MANAGE fail-closed 反证锁；旧实现下必红）。
- **死缓存条目**：AdminCacheCatalog.OPERATION_CODE 删除（全仓零消费方）；DualInstanceContainerTest 换同目录 L1_L2 活条目 DICT_TYPES 验通（L2 共享 + L1 广播失效两机制断言原样）——T-ACCESS-039 前置达成。
- **SDK 差异登记**：DefaultOpCode 不动；EDIT 无服务端预置注记落 extension-guide §2.3 + 契约 §4 历史注。
- **契约/文档回写**：总册 §4 操作码常量行重写（单一常量源+注册表镜像口径+22 码清单）、§7.8 新增管理端点字段分档门禁表；architecture §12.3、rebuild-runbook、engine 两册示例与 §8.3、org-user-permission-contract、frontend/permission-condition、pending-problems Q-002 时态与行号锚点同批；skills 双副本（permission-query-pipeline）与 rules（permission-center-coding-standards）、AGENTS.md 触发词、前端三处 perms.ts 注释机械改名（.claude/.agents diff 为空）。rules 文件存量旧包路径（access.permission.*）属 T-ACCESS-041 重写范围，未动。
- **外部评审处置（2026-09-13，claude + grok 双通道并行，均只读 + 禁子代理，共用同一提示词，对象=commit 8040cf454）**：claude P0/P1=0、P2×1+P3×1；grok 四级全零 + 存量观察六条。两通道对同一事实收敛（admin 轨 /user/update 仅凭 USER:UPDATE 可写 status、旁路本次 ENABLE 分权）、分级分歧（claude=既有问题被本次变更暴露 P2；grok=定案范围仅 perm 轨的存量观察）——主代理亲核代码裁决事实成立（UserWriteAppServiceImpl:180-183 门禁仅 UPDATE + :211-213 直写 status + 契约 §7.4 status 可写、§7.8 新立「防 UPDATE 绕过启停分权」声明使不对称显性化），**用户拍板本批补门禁**：updateUser 非自身且 status≠null 补查 USER:ENABLE（复用 /user/enable 同款 checkInstanceLevel；自身豁免保留），契约 §7.4 门禁行同步，`UserWriteAppServiceUpdateGateTest` 四用例行为锁（仅持 UPDATE 改 status 拒且零写[旧实现必红]/无 status 不查 ENABLE/双码放行/自身含 status 零门禁）。claude P3（ENABLE 分节失真：ADMIN_JOB:ENABLE 共用值面却归 USER 节 + BootstrapGraphDefinition「OperationCode.API 段」指代）与 grok 存量观察两处（ResolveContext javadoc USER+MANAGE 示例、H2Test「24 个静态类型」计数）均事实级修复：ENABLE 上移通用节 + 契约 §4 常量行归类同步；其余 grok 观察（AbstractUserUpdateReq 无 extraClear、90001 无 MockMvc 信封锁、superseded 残件表述）核为已知边界/既有形态不处置。
- **测试与评审**：单测轨道全绿；定向容器组（AccessBootstrapPgIT 17/SchemaPostgres 14/InstanceGate 4/UserRoleWriteProjection 9/DualInstance 3/FirstAdmin 1）全绿；全量回归 `-T 1C`（含 E2E 两垂直切片）BUILD SUCCESS。双轨本地评审（代码轨+文档轨并行）：代码轨 P0-P2 零 + P3×2、文档轨 P2×1（三处机械替换抹掉两旧类历史区分）+P3×6，逐条核实全处置（含 registry 表体空行修复、Q-002 行号锚点、BootstrapGraphDefinition DDL 行号引用去数字化）。

## 已知边界

- 自身豁免范围限定维持 Q-002 登记（本任务未动豁免语义，行为锁含「自身更新且无任何 USER 操作位仍成功」）。
- rules 文件 permission 旧包路径残留归 T-ACCESS-041（本任务只改类名与 import 行）。

## 非目标 / 遗留

- **自身豁免的范围限定**（改自己可改哪些字段/动作——自身 `enabled` 变更与自删是否应豁免门禁）登记为已知问题（docs/pending-problems.md Q-002），**不在本计划解决**（2026-09-13 用户定案：豁免范围应限定、另立任务处置；本任务维持与 admin 轨同款的现状豁免语义）。
- 不统一其他资源类型的粗细粒度选择（ROLE:MANAGE 等惯例保留）。
- 不动 SDK DefaultOpCode 枚举本身。
