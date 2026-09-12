---
doc_type: task
id: T-PERM-062
title: 新类型首笔授权生命周期种子——createType/createOperation 同事务种 AUTHORITY_ROOT 首授行（三路方案咨询收敛后用户定案）
status: done
plan: —
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/permission-center/api-contract.md#§6.5.1
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md#§14.2
  - docs/design/extension-guide.md#§3.5
depends_on: []
blocks: []
acceptance:
  - "createType（typeKey=resource_type）在 insertPresetOperations 之后同事务向「类型所有者角色」写入 4 条首授行：CRUD 位 1/2/4/8 各一条（单 bit）、scope_all=true、can_grant=true、condition_id=null、resource_entity_id=null、depend_on=null、grant_source=AUTHORITY_ROOT；任一失败整单回滚，不存在「已建类型但无所有者」中间态"
  - "createOperation（目标为自定义 resource_type，is_system=false）同事务向同一所有者角色追加该操作位一条首授行（不钩则追加操作死锁——EXPORT 位 16 先例）；与类型生命周期写路径共持 RESOURCE_ENTITY 树写锁并锁内重读类型行（评审批次：无锁时与所有者变更/类型删除交错产生不可回收种子行/漏级联）；is_system 类型不钩（内置类型转授链收窄维持）"
  - "所有者角色：TypeCreateReq 增可选 ownerRoleTypeCode/ownerRoleExternalId（与授权页同套角色业务键，成对提供），持久化于 type_definition.extra.grantOriginRole（服务端管理键——任意 typeKey 的 create 请求 extra 自带该键拒绝 20044）；缺省 BASIC_ROLE/bootstrap-admin；角色不存在（20001）/停用（20003）整单回滚"
  - "GrantSource 新增 AUTHORITY_ROOT：DDL CHECK 焊死形状（scope_all=true + can_grant=true + condition_id IS NULL + resource_entity_id IS NULL + depend_on IS NULL + 单操作位）；apply-grant-plan 的 updates/removes/向其挂子权限对 AUTHORITY_ROOT 行拒绝 20061（对齐 AUTO_DEP 只读 20034 先例）；类型删除级联仍清理（T-PERM-050 先例，级联覆盖全部 grant_source）"
  - "写入通道：复用 bootstrap 直写通道（跳过 prevalidate/verifyDelegation，保留 validateSingleManualGrants + validateGrantAttributes 两条领域校验），下沉为 PermissionGrantPlanDomainService.seedGrants（实施期定案：plan→grant 域既有单向依赖，反向下沉会构造循环；语义契约不变；幂等 insert-if-absent）供 bootstrap 与类型生命周期写路径共用；checkCanGrant 与通用授权链零改动（§14.1 红线维持：不开旁路、无超管豁免、不加 bootstrapBypass）"
  - "20040 reason 细分（实施期定案：仅自定义类型 is_system=false）：委托失败（NO_PERMISSION/NO_GRANT_RIGHT）且目标自定义 resource_type 在租户内零条可转授覆盖行时 message reason 改判 TYPE_GRANT_ORIGIN_MISSING；内置类型 reason 维持原值（转授链收窄是设计状态）；前端文案映射并引导到类型定义页"
  - "所有者变更迁移（实施期定案）：update 携带不同 grantOriginRole → 新所有者先行解析（失败整单回滚），类型行落库后同事务「先清后种」重整化（软删该类型全部 AUTHORITY_ROOT 行——含已删角色/误配旧 owner 残留——再向新所有者补齐全部有效操作位）；未携带键=保留现值（指针无清除语义）；同值幂等；指针仅自定义 resource_type 可携带（20044）；旧 extra 坏 JSON 降级为无指针（管理面可修复方向，T-PERM-052 口径）"
  - "markRoles：createType/createOperation/updateType 迁移后 @PermissionChange + PermissionChangeContext.markRoles（授权页展示面缓存卫生；委托路径 bypassPermSnapshot 已新鲜）"
  - "实施首步「授权根活性报告」：固定图 canGrant 分布核对（内置类型 canGrant=false 维持不动）；存量回填范围=空（无正式部署，用户口径——runbook 不登记回填条目）"
  - "回归锁：CustomResourceTypeSlicePgIT 重写锁组（创建即 4 条种子/追加操作补种/非所有者角色成员仍 20040【必要性锁换位——checkCanGrant 无管理员豁免】/种子行改删 20061/所有者迁移清理+补齐+旧 owner 失去转授/零授权根 reason=TYPE_GRANT_ORIGIN_MISSING/所有者解析失败整单回滚/非 resource_type 不触发/DDL CHECK 负向）；bootstrap 固定图计数锁不受影响（AccessBootstrapPgIT 维持全绿）"
  - "文档回写：extension-guide §3.5（部署方责任→创建即建授权根）；api-contract §5.1（ownerRole 字段 + 授权根生命周期）/§6.5.1（20061 + reason 注记）；schema（GrantSource CHECK + extra.grantOriginRole 注释）；architecture §14.2（种子类写入措辞扩为 bootstrap/类型首授）；前端类型定义页所有者角色选择器（缺省引导角色）+ 授权页授权根标注与只读提示"
design_writeback:
  required: true
  status: done
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

**实施期补充定案**（2026-09-12 活性报告后用户逐项拍板，registry 同日行）：

1. **reason 细分仅自定义类型**（is_system=false）：内置类型零可转授行是 T-PERM-027 转授链收窄的设计状态，reason 维持 NO_PERMISSION/NO_GRANT_RIGHT——「去类型定义页」引导对内置类型失真，且既有 20040 message 口径零扰动。
2. **无历史数据负担**：项目未正式部署，存量自定义类型回填面不存在——runbook 不登记 grantOriginRole 回填条目（DDL CHECK 仅存于权威 schema，容器测试库随建随有）。
3. **所有者指针允许变更并补齐种子**（取代立项时「AUTHORITY_ROOT 结构防护替代 reseed」中指针不可变的推论）：update 携带不同 grantOriginRole → 新所有者先行解析（失败整单回滚），类型行落库后同事务「先清后种」迁移——软删该类型全部 AUTHORITY_ROOT 行（含已删角色/误配旧 owner 残留）再向新所有者补齐全部有效操作位。只补不迁的叠加形态被否决（旧 owner 永久持有不可移除的类型级可转授权，误配 owner 一次即永久扩权）。
4. **owner 角色被删除不加删除守卫**：恢复路径 = type-definition/update 重指所有者（迁移语义自动清理已删角色残留行并补齐新 owner）——恢复面收敛进 update 单入口，无独立 reseed 端点。
5. 附带实现口径：种子直写通道下沉落 `PermissionGrantPlanDomainService.seedGrants`（任务卡原写 PermissionGrantDomainService——plan→grant 域既有单向依赖，反向下沉会构造 Spring 循环依赖；语义契约不变）；指针为服务端管理键（create 请求 extra 自带拒绝 20044）、无清除语义（update 未携带键=保留现值）；指针仅自定义 resource_type 可携带（is_system/其他 typeKey 20044）；种子通道幂等 insert-if-absent（operation 软删后同位重建时旧种子行仍有效，直插会撞 uk 误报 20033）。

**否决项登记**（三路一致否决，勿再提议）：checkCanGrant 运行时豁免/超管旁路（撞 §14.1 红线 + 打穿内置类型 canGrant=false 转授收窄）；GRANT_ORIGIN 独立能力位/赋权权拆分（改委托核心语义，位覆盖/条件/子权限边界全重吵，误接类型级 TYPE_DEFINITION:MANAGE 即全类型扩散）；纯文档方案（把设计缺陷转嫁给文档）。

**已知边界**：种子行被直改库损毁（如直接清行/翻 can_grant）后产品内无重种入口——但 20040 reason 细分（TYPE_GRANT_ORIGIN_MISSING）使其可发现，修复走运维 SQL（对齐 T-PERM-052「直改库损毁不设计恢复」先例；owner 角色被删除这类**产品内**失能经 update 重指所有者恢复，不属本边界）；内置类型业务门禁 canGrant=false 维持不动（转授链收窄的既有产品选择，与本缺口正交——活性报告已核对：固定图 129 行中 48 类型级门禁 canGrant=false 刻意维持，唯二 canGrant=true 为 API:ACCESS 类型级与 my-info 目标接口；bootstrap 授权检测为子集匹配，角色上多余授权行不冲突——AUTHORITY_ROOT 行不触发重启 fail-fast）。

## 范围

- TypeDefinitionAppServiceImpl.createType / OperationAppServiceImpl.createOperation 双钩子；
- TypeCreateReq + ownerRole 字段（双副本 DTO 同步）+ 前端类型定义页所有者选择器；
- GrantSource.AUTHORITY_ROOT + DDL CHECK + apply-grant-plan 改删拒绝（存量回填面=空，无 runbook 订正条目——实施期定案②）；
- PermissionGrantPlanDomainService.seedGrants 种子写入共用方法（bootstrap 通道下沉；落位偏差见实施期定案⑤）；
- checkCanGrant reason 细分 + 前端文案；@PermissionChange/markRoles 接线；
- 活性报告（实施首步）；文档五处回写；回归锁组。

## 非目标

- 不动 checkCanGrant 委托语义与通用授权链（§14.1）；
- 不做 reseed-grant-origin API（AUTHORITY_ROOT 结构防护下冗余）；
- 不动内置类型固定图 canGrant=false 与转授链收窄（T-PERM-027 口径）；
- 不做 GRANT_ORIGIN 能力位/赋权权拆分；
- 不做租户开通/tenant_authority_root 正式表（codex 咨询稿方案，当前固定租户 1 下 extra 持久化已足；多租户开通时随该任务重估）。

## 完成记录

- 2026-09-12 实施收口：
  - **活性报告**（实施首步）：固定图 canGrant 分布=129 行（48 类型级门禁 canGrant=false + API:ACCESS 类型级 1 条 canGrant=true + 80 实例级 API:ACCESS 中 my-info 1 条 canGrant=true）；内置类型转授链收窄维持不动；bootstrap 授权检测子集匹配核实（多余授权行不冲突，AUTHORITY_ROOT 行不触发重启 fail-fast）；存量自定义类型回填范围=空（无正式部署，用户口径）。
  - **后端**：GrantSource.AUTHORITY_ROOT + 错误码 20061；种子直写通道下沉 `PermissionGrantPlanDomainService.seedGrants`（幂等 insert-if-absent，bootstrap insertGrants 改委托）；新增 GrantOriginDomainService（指针解析 fail-closed/角色解析 20001/20003/指针注入拒绝客户端键/种子行构造/先清后种迁移）；createType 双钩子（ownerRole 字段 + extra.grantOriginRole 注入 + CRUD 四条种子）；createOperation 钩子（仅自定义类型，追加操作位补种）；updateType 所有者变更同事务迁移（未携带键=保留现值）；assertMutable 扩 AUTHORITY_ROOT（updates/removes/挂子三面 20061）；checkCanGrant reason 细分（失败路径 + 仅自定义类型 + 范围/位覆盖镜像判定）；markRoles/@PermissionChange 接线（createType/updateType/createOperation）。
  - **DDL**：`ck_role_resource_permission_authority_root` CHECK（scopeAll+canGrant+无条件+无实例+depend_on NULL+单操作位）+ grant_source 列注释 + type_definition.extra 注释补 grantOriginRole 语义。
  - **前端**：类型定义页「所有者角色」选择器（BASIC_ROLE 启用角色、hasNext 循环拉全、编辑态指针同步进 extra JSON）；授权页 MatrixCell 授权根标注与只读提示；20061 错误码文案；20040 reason=TYPE_GRANT_ORIGIN_MISSING 引导文案（grant-store classifySaveError 分流）。
  - **回归锁组**：CustomResourceTypeSlicePgIT 重写（锁①创建即 4 条种子/②追加操作补种/③非所有者 20040【必要性锁换位】/④种子行改删 20061/⑤所有者迁移清理+补齐+旧 owner 失去转授/⑥零授权根 reason=TYPE_GRANT_ORIGIN_MISSING/⑦所有者解析失败整单回滚/⑧非 resource_type 不触发/⑨DDL CHECK 负向）；bootstrap 计数锁 129/49/2 不受影响（AccessBootstrapPgIT 全绿）；单测新增 GrantOriginDomainServiceImplTest（指针解析/角色解析/注入拒绝/形状/迁移）+ TypeDefinition/Operation/Plan 域用例组扩展；既有测试适配（构造函数/DTO 补参/投影 IT 补引导角色行）。
  - **文档**：api-contract §5.1（授权根生命周期契约）+ §6.5.1（20061 + reason 注记）；extension-guide §3.5 重写（部署方责任→创建即建授权根）+ §8 资产行；architecture §14.2（种子类写入措辞扩展）；schema 注释；registry 实施期定案行。
  - 验证：单测轨道全模块全绿；容器组（切片/投影/资源操作键/bootstrap）全绿；前端 vitest 触达面全绿 + typecheck 通过。
  - 双轨评审处置（同日）：代码轨 P1-1（前端编辑态把非 BASIC_ROLE 所有者指针静默改写为 BASIC_ROLE——externalId 未变时回写原指针整体）已修；P2-1（createOperation 补种与所有者迁移/类型删除并发交错无锁）已修（共持 RESOURCE_ENTITY 树写锁+锁内重读+锁回归锁）；P3-1（updateType 旧 extra 坏 JSON fail-closed 堵死管理面修复通道）已修（旧侧降级 null，新侧维持 fail-closed）；P3-2（非 resource_type create 携带指针键静默落库）已修（任意 typeKey 统一 20044 + 用例）；P3-3（选择器 externalId 缺失 id 兜底会提交不可解析值）已修（无 externalId 不入选项）。文档轨 P1-1/P2-1/P2-2/P3-1（验收合同终态化/范围措辞/去计数/锚点精确化）已修；P2-3（看板行/回写状态）随 done 收口同步。存疑维持现状两项（20040 早退路径不细分、候选行不筛角色停用——口径简单性与 evaluate 同构）；AGENTS.md 登记与 T-FE-023 回指注记两项经用户拍板采纳并同批落地（AGENTS.md 权限中心实现提醒节增「类型授权根生命周期」bullet；T-FE-023 遗留节补 T-PERM-062 回指）。
  - 收口验证：全量回归 `mvn test -T 1C`（含 E2E 轨）reactor 全绿。
  - claude 外评处置（同日，用户显式触发；范围 025936e97..579ee12b0）：P2×1 核实后撤回（锁经 afterCompletion 释放覆盖全事务 + typeDef 锁内无缓存直查，两事务严格串行，陈旧读窗口不可达）；附带缺陷成立已修（createOperation 锁内 typeDef==null → fail-closed 20021，不再静默跳过种子并插孤儿操作行）；P3×2 已修（详情抽屉 AUTHORITY_ROOT 三态呈现；ownerRoleTypeCode 值域用户拍板收紧仅 BASIC_ROLE，容器角色 20044 + 回归锁 + 契约/schema 同步）；存量 RolePermissionItemResp Javadoc 扩列。
