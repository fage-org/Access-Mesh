---
doc_type: task
id: T-PERM-062
title: 新类型首笔授权生命周期种子——createType/createOperation 同事务种 AUTHORITY_ROOT 首授行（三路方案咨询收敛后用户定案）
status: proposed
plan: —
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/permission-center/api-contract.md#§6.5
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md#§14.2
  - docs/design/extension-guide.md#§3.5
depends_on: []
blocks: []
acceptance:
  - "createType（typeKey=resource_type）在 insertPresetOperations 之后同事务向「类型所有者角色」写入 4 条首授行：CRUD 位 1/2/4/8 各一条（单 bit）、scope_all=true、can_grant=true、condition_id=null、resource_entity_id=null、grant_source=AUTHORITY_ROOT；任一失败整单回滚，不存在「已建类型但无所有者」中间态"
  - "createOperation（目标为自定义 resource_type）同事务向同一所有者角色追加该操作位一条首授行（不钩则追加操作死锁——EXPORT 位 16 先例）；所有者以 type_definition.extra 持久化的 grantOriginRole 为准，缺失/停用/跨租户解析失败 → BizException 整单回滚"
  - "所有者角色：TypeCreateReq 增可选 ownerRoleTypeCode/ownerRoleExternalId（与授权页同套角色业务键），持久化于 type_definition.extra.grantOriginRole；缺省 BootstrapGraphDefinition.ADMIN_ROLE_TYPE_CODE/ADMIN_ROLE_EXTERNAL_ID（BASIC_ROLE/bootstrap-admin）；跨租户解析不到即整体失败（不泄漏他租户角色存在性）"
  - "GrantSource 新增 AUTHORITY_ROOT：DDL CHECK 焊死形状（scope_all=true + can_grant=true + condition_id IS NULL + resource_entity_id IS NULL + 单操作位）；apply-grant-plan 的 updates/removes 对 AUTHORITY_ROOT 行拒绝（对齐 AUTO_DEP 只读 20034 先例，错误码任务内定）；类型/操作删除级联仍清理（T-PERM-050 先例）"
  - "写入通道：复用 bootstrap 直写通道（跳过 prevalidate/verifyDelegation，保留 validateSingleManualGrants + validateGrantAttributes 两条领域校验），下沉为 PermissionGrantDomainService 命名方法供 bootstrap 与类型/操作创建共用；checkCanGrant 与通用授权链零改动（§14.1 红线维持：不开旁路、无超管豁免、不加 bootstrapBypass）"
  - "20040 reason 细分：目标类型在租户内零条可转授覆盖行时返回专用 reason TYPE_GRANT_ORIGIN_MISSING（与正常委托失败 NO_PERMISSION 区分）；前端文案映射并引导到类型定义页"
  - "markRoles：createType/createOperation 落首授行后 @PermissionChange + PermissionChangeContext.markRoles（授权页展示面缓存卫生；委托路径 bypassPermSnapshot 已新鲜——grok 咨询稿的 TTL 假失败论断经核实不适用于 20040 路径）"
  - "实施首步出「授权根活性报告」：扫描固定图业务门禁 canGrant 分布与存量自定义类型回填范围（runbook 订正 SQL）；内置类型 canGrant=false 维持不动（勿未经确认升级系统类型可转授）"
  - "回归锁：CustomResourceTypeSlicePgIT 阶段 5a 翻转（创建类型后直接可授权；必要性锁改打在「非所有者角色成员仍 20040」上，继续保证 checkCanGrant 无管理员豁免）；bootstrap 固定图计数锁不受影响（AccessBootstrapPgIT 维持全绿）；AUTHORITY_ROOT 行改删拒绝锁；追加操作自动补种锁；DDL CHECK 负向；所有者解析失败回滚锁；非 resource_type 类型键不触发"
  - "文档回写：extension-guide §3.5（部署方责任→创建即建授权基座）；api-contract §5.1（ownerRole 字段 + AUTHORITY_ROOT 语义）/§6.5（不可改删）；schema（GrantSource CHECK + extra.grantOriginRole 注释）；architecture §14.2（种子类写入措辞扩为 bootstrap/类型首授）；前端类型定义页所有者角色选择器（缺省引导角色）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-12
---

# T-PERM-062 新类型首笔授权生命周期种子

> 状态：proposed（2026-09-12 立项；设计经 claude / grok-4.6 / codex gpt-5.6-sol xhigh 三路独立方案咨询收敛后用户逐项拍板，定案登记见 decision-registry 2026-09-12 行）
> 来源：T-FE-023 扩展性验证发现的产品级缺口（全新自定义类型无人能经 apply-grant-plan 完成首笔授权，20040 死锁；CustomResourceTypeSlicePgIT 阶段 5a/5b 锁定、extension-guide §3.5 曾按部署方种子口径记录）

## 设计定案（当前口径）

**问题本质**：委托校验 `checkCanGrant` 严格无旁路（操作者必须已持有覆盖目标、canGrant=true、无条件的授权行），而 bootstrap 固定图只覆盖种子类型——全新自定义类型租户内可转授行数恒为 0，形成「要授权先得有权限」的死锁。固定图内唯一的 canGrant 行 `API:ACCESS` 正是同一死锁的既有解法（T-API-001，代码注释自证「鸡生蛋」），本任务把该模式从「建库时点」推广到「类型生命周期」。

**定案机制**（三路咨询收敛 + 用户四项拍板：方向=生命周期种子；钩子=创建+追加操作；接收方=可选指定+缺省引导角色；加固=reason 细分 + AUTHORITY_ROOT 来源 + 活性报告，不采 reseed）：

1. **种子钩子**：createType（4 条 CRUD）与 createOperation（每追加操作 1 条）双写入点，同事务落单 bit 类型级可转授行——不钩追加操作则死锁转移到第五个操作。
2. **接收方**：请求可选 ownerRoleTypeCode/ownerRoleExternalId，持久化于 type_definition.extra.grantOriginRole（extra 是通用扩展位，managedMode/syncSourceService 同先例）；缺省 BASIC_ROLE/bootstrap-admin；解析失败整单回滚。所有者显式落角色 → 创建者离场时经正常角色成员管理恢复授权能力。
3. **AUTHORITY_ROOT 来源**（结构防护替代 reseed）：DDL CHECK 焊死形状 + apply-grant-plan 不可改删 → 误删致死锁复发在产品内结构性不可能，故 reseed API 不采；类型/操作删除级联清理是唯一回收路径（T-PERM-050）。
4. **写入通道复用**：bootstrap 直写通道（跳过委托校验、保留两条领域校验）下沉为 PermissionGrantDomainService 共用命名方法——「在不变量之外建立引导」而非开洞；能走到 createType 的操作者本就持有全类型覆盖的 TYPE_DEFINITION:CREATE（类型级门禁不解析实例），种子未给出任何他得不到的东西（权力守恒）。
5. **reason 细分**：TYPE_GRANT_ORIGIN_MISSING 区分「类型未初始化/种子被直改库清除」与正常委托失败。

**否决项登记**（三路一致否决，勿再提议）：checkCanGrant 运行时豁免/超管旁路（撞 §14.1 红线 + 打穿内置类型 canGrant=false 转授收窄）；GRANT_ORIGIN 独立能力位/赋权权拆分（改委托核心语义，位覆盖/条件/子权限边界全重吵，误接类型级 TYPE_DEFINITION:MANAGE 即全类型扩散）；纯文档方案（把设计缺陷转嫁给文档）。

**已知边界**：种子行被直改库损毁后无产品内恢复（AUTHORITY_ROOT 不可改删的代价面）——runbook 登记订正 SQL 兜底，属设计接受的残留（对齐 T-PERM-052「直改库损毁不设计恢复」先例）；内置类型业务门禁 canGrant=false 维持不动（那是转授链收窄的既有产品选择，与本缺口正交——活性报告核对边界但不扩权）。

## 范围

- TypeDefinitionAppServiceImpl.createType / OperationAppServiceImpl.createOperation 双钩子；
- TypeCreateReq + ownerRole 字段（双副本 DTO 同步）+ 前端类型定义页所有者选择器；
- GrantSource.AUTHORITY_ROOT + DDL CHECK + apply-grant-plan 改删拒绝 + runbook 订正语句；
- PermissionGrantDomainService 种子写入共用方法（bootstrap 通道下沉）；
- checkCanGrant reason 细分 + 前端文案；@PermissionChange/markRoles 接线；
- 活性报告（实施首步）；文档五处回写；回归锁组。

## 非目标

- 不动 checkCanGrant 委托语义与通用授权链（§14.1）；
- 不做 reseed-grant-origin API（AUTHORITY_ROOT 结构防护下冗余）；
- 不动内置类型固定图 canGrant=false 与转授链收窄（T-PERM-027 口径）；
- 不做 GRANT_ORIGIN 能力位/赋权权拆分；
- 不做租户开通/tenant_authority_root 正式表（codex 咨询稿方案，当前固定租户 1 下 extra 持久化已足；多租户开通时随该任务重估）。

## 完成记录

（待实施）
