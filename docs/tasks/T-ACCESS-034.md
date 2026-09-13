---
doc_type: task
id: T-ACCESS-034
title: 操作码合一与 USER 轨细粒度化
status: proposed
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
  status: pending
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

## 非目标 / 遗留

- **自身豁免的范围限定**（改自己可改哪些字段/动作——自身 `enabled` 变更与自删是否应豁免门禁）登记为已知问题（docs/pending-problems.md Q-002），**不在本计划解决**（2026-09-13 用户定案：豁免范围应限定、另立任务处置；本任务维持与 admin 轨同款的现状豁免语义）。
- 不统一其他资源类型的粗细粒度选择（ROLE:MANAGE 等惯例保留）。
- 不动 SDK DefaultOpCode 枚举本身。
