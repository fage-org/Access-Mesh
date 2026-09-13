---
doc_type: design
title: access-service 能力包结构与两域融合
status: adopted
domain: cross-service
last_reviewed: 2026-09-13
---

# access-service 能力包结构与两域融合

> 定位：本文件承载 2026-09-13 用户确认的两域融合定案（决策登记见 [decision-registry.md](decision-registry.md) 2026-09-13 行），是能力包终态结构、概念收敛项与文档重组的**目标设计**。
>
> `status: adopted`（T-ACCESS-032 于 2026-09-13 完成 §8 归属清单与边界断言细化，十项裁决用户拍板后转正）；结构迁移由 T-ACCESS-033 执行，在其完成前现行代码包结构仍以 [access-service-architecture.md](access-service-architecture.md) §3（已重写为能力包口径）为准绳、本文件为终态契约。
>
> 与 access-service-architecture.md 的关系：该文档 §1.2「本阶段不把两个领域立即完全扁平化」与 §11「重复领域模型扁平化」演进方向由本文件承接启用；§3 模块边界、§9 错误码表述由本文件修订（T-ACCESS-032/038 执行回写，修订对照见本文 §9）。

## 1. 融合目标与验收

一句话：把 access-service 从「归并了但内部平行的两个域」变成「一套能力包 + 独立权限引擎子系统」——结构上消灭 admin/permission 边界与全部平行设施，概念上保持「管理事实/权限计算」双轨表语义、只做字段级消减与单点收口，对外表面基本维持现状。

验收五条（融合完成判定）：

1. 新增一个纯管理对象（无权限语义，如公告分类）：只碰 platform 一个包，零跨包协调。（可判定形态：ArchUnit 规则——platform 包类零 import 其他能力包，032 设计、033 落地）
2. 新增一个权限资源对象：type 登记 + 资源维护 + 门禁调用三步，全程不出现「属于哪个域」的问题。（可判定形态：032 归属清单含 type 登记/资源维护/门禁调用三步的落地类映射）
3. 平行设施归零：错误码一册、缓存目录一册、操作码一册（§5.1 结果）；审计写入入口维持 `@OperationLog` 单入口（permission_change_log 为权限事实变更语义并存，见 §4.2）。
4. `QueryBoundaryArchitectureTest` 以能力为对象重建（能力间不互读 Mapper）且通过。
5. 项目规则文件不再按 admin/permission 域描述规范（按能力 + 引擎口径重写）。

## 2. 包结构终态

### 2.1 能力包（12 个）

每个能力包内含 Controller → AppService → DomainService → Mapper 全链。下表为指示性归属，终态以 §8 归属清单为准：

| 包 | 领域范围 |
|---|---|
| auth | 登录会话（SaToken）、OAuth2 授权与客户端管理 |
| user | sys_user 管理、abstract_user 主体管理、USER 投影与资源投影、user 同步执行层 |
| org | sys_org / org-tree-config / user-org 管理、ORG/POSITION 容器投影、组织可见性查询 |
| menu | sys_menu 管理、MENU 资源投影、用户菜单派生查询 |
| role | abstract_role 管理、user-role 持有管理、角色同步执行层、互斥守卫 |
| grant | role_resource_permission 授权与授权视图/计划、AUTHORITY_ROOT 种子 |
| resource | resource_entity、resource_api_mapping、resource_dependency、资源同步执行层 |
| type | type_definition、类型投影与所有权声明门禁 |
| domain | biz_domain、domain_config、业务域分类查询 |
| rule | permission_condition、permission_conflict_rule |
| audit | operation_log、permission_change_log、sys_login_log 三日志与查询面 |
| platform | 字典、公告、文件（sys_file 与 ADMIN_FILE 登记）、任务（job）、system_config 等无权限语义的纯管理杂项 |

### 2.2 sync 通道包

- 归属 sync 包：4 组 sync/full-sync Controller 及抽象基类（AbstractUserSyncController、AbstractRoleSyncController、UserRoleSyncController、ResourceEntitySyncController 及 full-sync 变体）、通道级守卫（来源/所有权/白名单的通道面）、`sync_metadata` 记账设施（SyncMetadataDomainService + mapper + 实体 + 契约测试锚点——全仓只有同步通道消费它）。
- 执行层随实体：AbstractUserSyncAppServiceImpl 等执行 AppService 留在对应能力包；内部投影（LocalProjectionDomainService）不属于 sync 包，随实体同事务写路径。
- 依赖形态：sync→实体包（Controller 编排）与实体包→sync（执行层调记账设施）构成包级双向边，接受；类级无环即可，「能力间不互读 Mapper」断言不受影响（记账 mapper 经 DomainService 封装）。

### 2.3 引擎子系统与 infrastructure

权限引擎（PermQueryEngine + 快照/装配管线 + 引擎专属缓存）为独立子系统包（`engine`），不塞入任何能力包；infrastructure 底座（请求上下文、拦截器、TypeHandler、跨能力通用实体等）照旧。

**032 裁决扩充（终态顶层包 +2，见 §8.0 裁决 6/8 与 §8.2）**：另设 `bootstrap`（空库自举编排 + BootstrapSeedWriter）与 `projection`（内部事实投影统一门面 LocalProjectionDomainService + PermConstants）两个顶层包——终态顶层包合计 17 个（12 能力包 + sync + engine + projection + bootstrap + infrastructure）。

### 2.4 application 包解散

`access.application` 跨域写编排与 `application.query` 只读查询按归属并入能力包：写编排随主实体（UserWriteAppService→user、OrgWrite→org、MenuWrite→menu、UserOrgWrite→org）；查询按主实体（UserMenuQuery→menu、UserRoleQuery→role、OrgVisibilityQuery→org）。能力包合一后原「跨域」语义消失，跨能力写事务由主实体能力的 AppService 编排；`application.bootstrap` 独立为顶层 `bootstrap` 包（§8.0 裁决 6）。

### 2.5 命名收敛

全仓统一 `XxxAppService/XxxAppServiceImpl` + `XxxDomainService/XxxDomainServiceImpl`（project-rules §8 既有规范）；admin 域 14 对 `XxxService/Impl` 改名为事实性修正，非新决策。application.query 三对 QueryService 随 033 改名 `XxxQueryAppService` 口径（§8.0 裁决 5，2026-09-13 拍板），全仓不留第二套调度层命名；perm 轨用户 DTO 三组改名消歧见 §8.3(a)。

## 3. 设施收敛

- **错误码**：合类不合号——AdminErrorCode / PermissionErrorCode 合一为单一错误码面，`1xxxx`、`2xxxx` 编号段原值保留、不重排（architecture §9 分段与不重编号的字面保持）；新增码归属段规则随 T-ACCESS-038 文档面明确。
- **缓存目录**：AdminCacheCatalog 与 PermCacheCatalog 合一为一册（OPERATION_CODE 死条目先随 T-ACCESS-034 删除）；`admin:org-visibility` 越域命名随迁移归位。
- **操作码**：见 §5.1。

## 4. 概念边界维持项（不合并清单）

### 4.1 表级维持

六对双轨表全部维持（用户 sys_user↔abstract_user、组织 sys_org↔abstract_role 容器、菜单 sys_menu↔resource_entity(MENU)、字典 sys_dict_*↔type_definition、文件 sys_file↔ADMIN_FILE、日志三表）；**引擎零直读 sys_\* 管理事实表**的不变量保持（引擎输入面 = 投影表）。

### 4.2 必须留的字段与双写（评估结论）

- `sys_user.status` ↔ `abstract_user.enabled` 双写：登录读前者、引擎读后者，同事务同源正是防「认证放行 + 主体停用分裂」的契约；当前无绕过投影的写入口。
- name 类三写（sys_user→abstract_user→resource_entity 等）：主体管理页、授权页资源树、角色树有真实展示读取方。
- 日志三表：operation_log 与 permission_change_log 双写是有意设计（request_id 同值 JOIN 复原审计链），两者是「操作审计」与「权限事实变更」两个语义，并存登记不合并；sys_login_log 为登录事件专表。
- `sys_user_org` ↔ `user_role` 关系级双写：投影设计。

## 5. 概念收敛项

### 5.1 操作码（唯一权限语义变更）

- **常量面合一**：AdminOperationCode 与 OperationCodeConstants 并为单一操作码常量面（按资源类型分节）；补录 `API:ACCESS`（消除 bootstrap/快照装配/接口判定四处裸字符串）与 `ROLE:ASSIGN`/`ROLE:REVOKE`（DDL 种子在册、后端零代码引用——按「统一常量面=注册表镜像」收录，与既有「常量类只镜像代码引用面」口径的关系随 T-ACCESS-034 登记）。
- **USER 轨细粒度化**：UserManageAppServiceImpl 换绑——updateUser 按字段分档（name/extra 变更查 `USER:UPDATE`；`enabled != null` 查 `USER:ENABLE`，防 UPDATE 绕过启停分权）、deleteUsers 查 `USER:DELETE`；`USER:MANAGE` 退役（无消费即删，仓库既有政策）。bootstrap 固定图 USER 段本无 MANAGE 位（空库上 perm 轨 update/remove 恒拒不可用）——换绑后由拒转放行是**预期的行为变化**（修复该死锁），非等价改写。自身豁免（两轨同款 operatorId==targetId 跳过门禁）保留；豁免**范围限定**（改自己的可变字段/动作边界）登记为已知遗留（docs/pending-problems.md Q-002）、不在本计划解决（2026-09-13 定案）。
- 粗细粒度双轨仅统一 USER 一处；ROLE / RESOURCE 等的 `MANAGE` 惯例不动。
- SDK `DefaultOpCode`（VIEW/EDIT/DELETE）维持（SDK 自身契约）；EDIT 在服务端操作码注册表无预置，登记为已知差异。
- 影响面：DDL 种子与 schema/表征测试（InstanceGate/UserRoleWriteProjectionPgIT 的 MANAGE 行迁细码）、注释残留清扫、契约文档；bootstrap 固定图零变更（仅加无 MANAGE 断言）；前端 user 页已用细粒度且零 abstract-user 消费、e2e 无 MANAGE 断言（均已核实，无迁移项）。

### 5.2 字段消减（六项，零读取方实证）

1. `abstract_user.extra` 的 username 投影（删投影写入；extra 列本体留）
2. `abstract_role` 容器行 `sort_order` 停投影（列本体由功能角色使用，保留）
3. `abstract_role` 容器行 `extra.orgType` 停写（orgType 语义已由 role_type 承载，extra.orgType 键整体消亡）
4. `resource_entity.sort_order` 全字段面退役（列 + SDK 双册 Create/Update Req 字段 + ResourceResp/ResourceTreeResp + 全部写入点 + 前端提交载荷停发——独立任务 T-ACCESS-036，涉对外契约与前端提交面）
5. `AdminCacheCatalog.OPERATION_CODE` 死缓存条目删除（DualInstanceContainerTest 换验通样例）
6. MenuServiceImpl / AuthServiceImpl 的 validator 死注入、死 import 清扫

### 5.3 system_config 单入口化

退役 admin `/config` 入口（ConfigController + ConfigService + 测试 + 契约段；前端与 e2e 零消费已核实）；保留 `/api/perm/system-config`（前端唯一消费方，T-PERM-024 收口）。

## 6. 对外表面

- **URL**：维持两风格（admin 裸路径 + perm `/api/perm/**`），登记为已知问题（docs/pending-problems.md Q-001），后续单独改。
- **错误码编号段**：维持 `1xxxx`/`2xxxx` 不重排。
- **前端信息架构**：维持一棵「系统管理」树 + 隐藏授权入口，不纳入融合；菜单重组如做另独立立项（产品决策）。

## 7. 文档与叙事

- **API 契约深合一**：两份契约并为一份 access-service 契约总册（按能力分章、两个 URL 家族同册分列）；`docs/design/permission-center/` 目录解散重组、`admin-service-api-contract.md` 退役并入；overview / core-flows / implementation 等设计内档归引擎子系统文档位；全仓引用一次更新。
- **任务 ID 前缀**：T-PERM / T-ADMIN 等不动（历史档案索引）。
- **规则与技能文件**：按能力 + 引擎口径重写（.claude/rules 单副本、skills 双副本同步纪律不变、AGENTS.md 指针表随改）。

## 8. 归属清单与边界断言（T-ACCESS-032 定稿，2026-09-13）

> 本章为 T-ACCESS-033 机械迁移的**唯一搬迁依据**；十项裁决用户已拍板（decision-registry 2026-09-13 融合定稿行与 T-ACCESS-032 行）。源类基线 = 归并后 live 树 `cn.ac.fage.accessmesh.access` 主源码全部类（完整性由 033 全量核对兜底；测试树随主类同规则迁移，不在表中重复）。

### 8.0 裁决汇总（十项拍板）

| # | 裁决项 | 结论 |
|---|---|---|
| 1 | 跨域同名 DTO 三组（UserCreateReq / UserUpdateReq / UserResp） | perm 轨改名 `AbstractUserCreateReq` / `AbstractUserUpdateReq` / `AbstractUserResp`（与端点 `/api/perm/abstract-user/*` 语义对齐）；admin 轨保名；perm-common SDK 副本不动（T-PERM-065 单源纪律）。仅 Java 类名变，JSON 契约零变化 |
| 2 | operation_permission 全链 | **type 包**（操作位空间按类型隔离的核心语义；类型删除级联同包闭环） |
| 3 | service_config 全链 | **resource 包**（MappingSyncHandler 直读 ResourceEntity/ApiMapping mapper，同步产物即资源行；resource 行既有「资源同步执行层」） |
| 4 | 任务治理设施（sys_task_execution 链 11 类） | **infrastructure.task**（job 业务链归 platform；租约/执行/调度设施归底座，对齐 architecture §8.1 多实例协调定位） |
| 5 | application.query 三对 QueryService | 随 033 改名 `UserMenuQueryAppService` / `UserRoleQueryAppService` / `OrgVisibilityQueryAppService`（对齐 §2.5 唯一调度层命名；只读语义由方法注解表达） |
| 6 | bootstrap 设施 | **独立顶层 bootstrap 包**（BootstrapSeedWriter 接口与实现随包迁入；断言排除集收敛为单包） |
| 7 | 门禁设施与操作码常量面 | **都归 engine**：AdminPermissionValidator（含 Impl）为引擎门面；AdminOperationCode + OperationCodeConstants 033 先两册随迁 engine、034 合一为单册 |
| 8 | LocalProjectionDomainService + PermConstants | **独立顶层 projection 包**（内部事实投影统一门面，横跨五能力不可拆；两个 ProjectionWriter 各随实体） |
| 9 | 存量跨能力 mapper 直读（双轨评审发现） | **冻结白名单**（2026-09-13 拍板）：§8.4 第六条豁免——存量引用机械原样保留（闭合清单实测 19 类 30 边，见 §8.4 表），断言锁「不得新增」；存量收敛（改走对方 DomainService）另立后续任务（docs/pending-problems.md Q-009 登记） |
| 10 | 僵尸 DTO 扩面（双轨评审发现） | `BatchResultResp`、`OperationDetailReq` 与 `PermissionCheckReq` 同批随 033 删除（全仓零消费实测，「无消费即删」政策） |

### 8.1 终态顶层包总览

17 个顶层包 = 12 能力包（§2.1）+ sync 通道包（§2.2）+ engine 引擎子系统 + infrastructure 底座 + **032 裁决新增 bootstrap / projection 两顶层包**（裁决 6/8，对 §2.3「引擎子系统与 infrastructure」结构的扩充）。

能力包统一子结构（sync / engine / projection / bootstrap / infrastructure 形态见 8.2 各节）：

```text
<capability>/
├── controller/            # Controller 类名维持现状（AdminXxx/PermXxx 前缀不消解——命名收敛仅 Service 层，§2.5）
├── service/               # XxxAppService 接口 + impl/ 实现（033 改名面仅此层）
│   └── domain/ + impl/    # XxxDomainService
├── mapper/
├── entity/
├── dto/req/ dto/resp/     # 投影 record 另立 dto/projection/（按需）
└── enums/ util/           # 按需
```

Mapper XML 随包迁移：`resources/mapper/query/*.xml` → `resources/mapper/{org,menu,role}/`；其余 mapper XML（`resources/mapper/` 根下存量）随所属能力包同规则迁移，namespace/resultType FQCN 同批更新（033 任务卡断言面覆盖）。**Mapper 接口一律落 `*.mapper` 子包**（能力包 `{cap}.mapper`、`sync.mapper`、`infrastructure.mapper`——@MapperScan 按包清单扫描不漏注册；记账/租约的 DomainService 与实体可留在语义子包，mapper 接口不随行）。表基线：权威 DDL 全部 33 张表在 §8.2 各节「表：」行登记。

### 8.2 逐包归属清单（源 → 目标）

除标注「改名」「删除」外一律保名搬迁；`admin.*` / `permission.*` / `application.*` 均省略前缀 `cn.ac.fage.accessmesh.access.`。

#### user

| 源 | 目标 | 说明 |
|---|---|---|
| admin.controller.AdminUserController | user.controller | |
| admin.dto.req：UserBatchCreateReq、UserCreateReq、UserPageReq、UserQuery、UserUpdateReq、UserUpdateStatusReq、ResetPasswordReq、MemberCandidatesReq | user.dto.req | admin 轨保名 |
| admin.dto.resp：UserCreateResp、UserPageItemResp、UserResp、MemberCandidateItemResp（含 UserPageItemResp$OrgBrief 嵌套）、ResetPasswordResp | user.dto.resp | |
| admin.dto.resp.**BatchResultResp** | —（**删除**） | 零消费方（裁决 10） |
| admin.service.UserService/Impl | user.service.UserAppService/Impl | 改名（§2.5） |
| admin.service.domain.UserDomainService/Impl | user.service.domain | |
| admin.entity.SysUser + admin.mapper.SysUserMapper | user | |
| application.UserWriteAppService/Impl | user.service | §2.4 写编排归位 |
| permission.controller.PermUserController | user.controller | |
| permission.dto.req：UserCreateReq→**AbstractUserCreateReq**、UserUpdateReq→**AbstractUserUpdateReq**、UserDetailReq、UserListReq | user.dto.req | 裁决 1 改名 |
| permission.dto.resp：UserResp→**AbstractUserResp** | user.dto.resp | 裁决 1 改名 |
| permission.service.UserManageAppService/Impl、AbstractUserSyncAppService + permission.service.impl.AbstractUserSyncAppServiceImpl | user.service | 末者为 sync 执行层，随实体（§2.2） |
| permission.service.domain.impl.BatchAdminUserProjectionWriter | user.service.domain | |
| permission.entity.AbstractUser + permission.mapper.AbstractUserMapper | user | |

表：`sys_user`、`abstract_user`。

#### org

| 源 | 目标 | 说明 |
|---|---|---|
| admin.controller：OrgController、OrgTreeConfigController、UserOrgController | org.controller | |
| admin.dto.req：OrgBatchCreateReq、OrgCreateReq、OrgUpdateReq、OrgPageReq、OrgQuery、OrgTreeConfigCreateReq、OrgTreeConfigUpdateReq、UserOrgAssignReq、UserOrgRemoveReq、UserOrgSetPrimaryReq | org.dto.req | |
| admin.dto.resp：OrgResp、OrgTreeConfigResp、OrgUserItemResp | org.dto.resp | |
| admin.service：OrgService→OrgAppService、OrgTreeConfigService→OrgTreeConfigAppService、UserOrgService→UserOrgAppService（各含 Impl） | org.service | 改名（§2.5） |
| admin.service.domain：OrgDomainService、OrgTreeConfigDomainService、UserOrgDomainService（各含 Impl） | org.service.domain | |
| admin.entity：SysOrg、SysOrgTreeConfig、SysUserOrg + 对应 3 Mapper | org | |
| application.OrgWriteAppService/Impl、application.UserOrgWriteAppService/Impl | org.service | §2.4 |
| application.query.OrgVisibilityQueryService/Impl | org.service.**OrgVisibilityQueryAppService**/Impl | 裁决 5 改名 |
| application.query.mapper.OrgVisibilityQueryMapper + `mapper/query/OrgVisibilityQueryMapper.xml` | org.mapper + `resources/mapper/org/` | XML 直读 sys_org/sys_org_tree_config |
| admin.security.OrgOperationCodeMapper | **engine**（见 engine 节） | 门禁映射随操作码面（裁决 7） |

表：`sys_org`、`sys_org_tree_config`、`sys_user_org`。OrgTreeConfig 验收句落位：§2.1 org 行已含 org-tree-config，管理链归 org；其可见性查询链（OrgVisibilityQuery*）随裁决 5 同归 org。

#### menu

| 源 | 目标 | 说明 |
|---|---|---|
| admin.controller.MenuController | menu.controller | |
| admin.dto.req：MenuBatchCreateReq、MenuCreateReq、MenuUpdateReq；admin.dto.resp.MenuResp | menu.dto | |
| admin.service.MenuService/Impl | menu.service.**MenuAppService**/Impl | 改名（§2.5） |
| admin.service.domain.MenuDomainService/Impl | menu.service.domain | |
| admin.entity.SysMenu + SysMenuMapper | menu | |
| application.MenuWriteAppService/Impl | menu.service | §2.4 |
| application.query.UserMenuQueryService/Impl | menu.service.**UserMenuQueryAppService**/Impl | 裁决 5 改名 |
| application.query.mapper.UserMenuQueryMapper + XML | menu.mapper + `resources/mapper/menu/` | XML 直读 sys_menu + sys_user_org（跨能力表 XML 直读，§8.4 豁免口径） |
| application.query.projection：MenuProjection、UserOrgProjection | menu.dto.projection | 唯一消费方 UserMenuQueryMapper 链 |
| admin.dto.auth.UserMenuResp | menu.dto.resp | `/auth/user-menu` 响应随查询族（auth 跨包 import） |

表：`sys_menu`。

#### role

| 源 | 目标 | 说明 |
|---|---|---|
| admin.controller.AdminRoleController（/role/list、/role/my-info）+ **AdminUserRoleController**（/user-role/list） | role.controller | 后者双轨/外评补登（漏网修正） |
| admin.dto.req.UserRoleListReq、admin.dto.resp：UserRoleItemResp、RoleListItemResp | role.dto | user-role 持有视角查询族（§2.1 role 行） |
| permission.controller：PermRoleController、PermUserRoleController | role.controller | |
| permission.dto.req：RoleMoveReq、RoleUpdateReq、RoleTreeReq、UserRoleBatchAssignReq | role.dto.req | |
| permission.dto.resp：RoleResp、RoleTreeResp、UserRolesResp | role.dto.resp | access↔perm-common 同名跨模块不冲突，保名 |
| permission.service：RoleManageAppService/Impl、UserRoleSyncAppService/Impl、AbstractRoleSyncAppService + permission.service.impl.AbstractRoleSyncAppServiceImpl | role.service | 末两行为 sync 执行层，随实体 |
| permission.service.domain.impl.UserRoleProjectionWriter | role.service.domain | |
| application.query.UserRoleQueryService/Impl | role.service.**UserRoleQueryAppService**/Impl | 裁决 5 改名 |
| application.query.mapper.UserRoleQueryMapper + XML | role.mapper + `resources/mapper/role/` | XML 直读 user_role/abstract_role/sys_org |
| application.query.projection：UserRoleProjection、FunctionalRoleProjection、OrgBriefProjection | role.dto.projection | 唯一消费方 UserRoleQueryMapper 链 |
| permission.entity：AbstractRole、UserRole + 2 Mapper | role | |

表：`abstract_role`、`user_role`。互斥守卫（assign/batch-assign 的 ROLE_MUTEX 拦截、存量立规守卫）随 user-role 写入口在 role；互斥规则本体（PermissionConflictDomainService）在 rule。

#### grant

| 源 | 目标 | 说明 |
|---|---|---|
| permission.controller.PermissionGrantController | grant.controller | |
| permission.dto.req：ApplyGrantPlanReq、RolePermissionListReq、SubPermAllowedTypesReq；resp：RolePermissionItemResp、RolePermissionItemsResp、SubPermAllowedTypesResp | grant.dto | |
| permission.service.PermissionGrantAppService/Impl | grant.service | |
| permission.service.domain：PermissionGrantDomainService、PermissionGrantPlanDomainService、GrantOriginDomainService（各含 Impl） | grant.service.domain | AUTHORITY_ROOT 种子（seedGrants）同链 |
| permission.entity.RoleResourcePermission + Mapper | grant | |
| permission.enums：GrantSource、TargetMode | grant.enums | |
| permission.util.DatabaseExceptionSupport | grant.util | 唯一消费方 GrantPlanDomainServiceImpl |

表：`role_resource_permission`。

#### resource

| 源 | 目标 | 说明 |
|---|---|---|
| permission.controller：ResourceController、ResourceApiMappingController、ResourceDependencyController、**ServiceConfigController** | resource.controller | 裁决 3 |
| permission.dto.req：ResourceListReq、ResourceMoveReq、ResourceDetailReq、ResourceResolveKey、ResourceResolveRequest、ResourceTreeReq、DependencyBatchSyncReq、DependencyListReq、ResourceDependencyCheckReq、ResourceDependencyCreateReq、ResourceDependencyUpdateReq、ApiMappingAddReq、ApiMappingListReq、ApiMappingUpdateReq、ServiceConfigReq、ServiceConfigGetReq、ServiceConfigApisReq、ServiceConfigSyncReq | resource.dto.req | |
| permission.dto.resp：ResourceResp、ResourceTreeResp、ResourceDependencyResp、DependencyCycleCheckResp、ApiMappingResp、ServiceConfigResp、ServiceConfigSyncResp | resource.dto.resp | |
| permission.service：ResourceManageAppService/Impl、ResourceEntitySyncAppService/Impl（sync 执行层）、ServiceConfigAppService/Impl、ServiceSyncAppService/Impl、DependencyAppService/Impl | resource.service | |
| permission.service.domain：ResourceEntityDomainService/Impl、MappingSyncHandler/Impl、ResourceSyncHandler/Impl | resource.service.domain | |
| permission.entity：ResourceEntity、ResourceApiMapping、ResourceDependency、**ServiceConfig** + 4 Mapper | resource | 裁决 3 |

表：`resource_entity`、`resource_api_mapping`、`resource_dependency`、`service_config`。

#### type

| 源 | 目标 | 说明 |
|---|---|---|
| permission.controller：TypeDefinitionController、**OperationController** | type.controller | 裁决 2 |
| permission.dto.req：TypeCreateReq、TypeUpdateReq、TypeDetailReq、TypeListReq、OperationCreateReq、OperationUpdateReq、OperationKeyReq、OperationKeysReq；resp：TypeDefinitionResp、OperationPermissionResp | type.dto | |
| permission.dto.req.**OperationDetailReq** | —（**删除**） | 零消费方（裁决 10） |
| permission.service：TypeDefinitionAppService/Impl、OperationAppService/Impl | type.service | |
| permission.service.domain.ResourceTypeOwnershipGuard | type.service.domain | §2.1 type 行「所有权声明门禁」 |
| permission.entity：TypeDefinition、**OperationPermission** + 2 Mapper | type | 裁决 2；OPERATION_PERMISSIONS_BY_TYPE 缓存条目语义同归（039 合一目录时落位） |
| permission.enums：ResourceType、ResourceTypeCode、RoleType | type.enums | 类型注册表镜像；engine/各能力跨包 import 允许 |

表：`type_definition`、`operation_permission`。

#### domain

| 源 | 目标 |
|---|---|
| permission.controller：BizDomainController、DomainConfigController | domain.controller |
| permission.dto.req：BizDomainCreateReq、BizDomainDetailReq、BizDomainListReq、BizDomainUpdateReq、DomainConfigReq、DomainConfigGetReq、DomainConfigListReq；resp：BizDomainResp、DomainConfigResp | domain.dto |
| permission.service：BizDomainAppService/Impl、DomainConfigAppService/Impl | domain.service |
| permission.service.domain.DomainClassifyService/Impl | domain.service.domain |
| permission.entity：BizDomain、DomainConfig + 2 Mapper | domain |
| permission.enums：ConfigType、DomainQueryMode | domain.enums |

表：`biz_domain`、`domain_config`。

#### rule

| 源 | 目标 |
|---|---|
| permission.controller：ConditionController、ConflictRuleController | rule.controller |
| permission.dto.req：ConditionCreateReq、ConditionDetailReq、ConditionListReq、ConditionRemoveReq、ConditionUpdateReq、ConflictRuleReq、ConflictRuleUpdateReq、ConflictRuleDetectReq；resp：ConditionResp、ConflictRuleResp、ConflictDetectResp | rule.dto |
| permission.service：ConditionAppService/Impl、ConflictRuleAppService/Impl | rule.service |
| permission.service.domain：PermissionConditionDomainService/Impl、PermissionConflictDomainService/Impl | rule.service.domain |
| permission.entity：PermissionCondition、PermissionConflictRule + 2 Mapper | rule |
| permission.enums：ConditionSource、ConflictType | rule.enums |

表：`permission_condition`、`permission_conflict_rule`。CONDITION 实例投影写路径经 projection 门面（跨包 DomainService 调用）。

#### audit

| 源 | 目标 | 说明 |
|---|---|---|
| permission.controller.LogQueryController | audit.controller | |
| permission.dto.req：ChangeLogListReq、LogActionOptionsReq、OperationLogListReq；resp：ChangeLogResp、OperationLogResp | audit.dto | |
| permission.service.LogQueryAppService/Impl | audit.service | |
| permission.service.domain.AuditDomainService/Impl | audit.service.domain | 双日志（operation_log + permission_change_log）写入 |
| permission.aop.OperationLogAspect + infrastructure.aop.OperationLog（注解）+ infrastructure.aop.OperationLogRuntimeContext | audit.aop | 注解、切面与运行时上下文（ThreadLocal 伴侣）同包内聚；全能力包 import audit 注解（横切，允许） |
| infrastructure.entity.OperationLog + infrastructure.mapper.OperationLogMapper | audit | |
| permission.entity.PermissionChangeLog + Mapper | audit | |
| admin.controller.LoginLogController；admin.service.LoginLogService/Impl→**LoginLogAppService**/Impl；admin.service.domain.LoginLogDomainService/Impl；admin.entity.SysLoginLog + Mapper；admin.dto.resp.LoginLogResp | audit | 改名（§2.5） |
| permission.util.JsonValidationUtils | audit.util | 消费面=审计/条件/域配置/系统配置 JSON 校验，随主消费方 audit，跨包 import |

表：`operation_log`、`permission_change_log`、`sys_login_log`。

#### platform

| 源 | 目标 | 说明 |
|---|---|---|
| admin.controller：DictController、NoticeController、FileController、JobController、ConfigController | platform.controller | ConfigController（/config）037 退役，033 先随迁 |
| permission.controller.SystemConfigController（/api/perm/system-config） | platform.controller | 保留入口（§5.3） |
| admin.dto req/resp：Dict 族（DictDataCreateReq/UpdateReq、DictTypeCreateReq、DictDataResp、DictTypeResp）、Notice 族（NoticeCreateReq/UpdateReq、NoticeResp）、File 族（FilePageReq、FileResp）、Job 族（JobCreateReq/UpdateReq、JobLogPageReq、JobResp、JobLogResp）、ConfigUpdateReq、ConfigResp | platform.dto | |
| permission.dto req/resp：SystemConfigGetReq、SystemConfigListReq、SystemConfigReq、SystemConfigResp | platform.dto | |
| admin.service：DictService→**DictAppService**、NoticeService→**NoticeAppService**、FileService→**FileAppService**、JobService→**JobAppService**、ConfigService→**ConfigAppService**（各含 Impl） | platform.service | 改名（§2.5） |
| permission.service.SystemConfigAppService/Impl | platform.service | 保名 |
| admin.service.domain：JobLogDomainService/Impl | platform.service.domain | 业务日志；**JobInvokeDomainService/Impl 归 infrastructure.task（裁决 4）** |
| admin.entity：SysDictType、SysDictData、SysNotice、SysUserNotice、SysFile、SysJob、SysJobLog + 7 Mapper | platform | |
| infrastructure.entity.SystemConfig + infrastructure.mapper.SystemConfigMapper | platform | |
| admin.security.AdminFileFolderRegistrar + application.security.AdminFileFolderRegistrarImpl | platform.service | ADMIN_FILE 文件夹实例惰性登记，file 链 |
| admin.dto.req.IdsReq → **infrastructure.dto**（见 infrastructure 节） | | 跨能力共享 DTO |

表：`sys_dict_type`、`sys_dict_data`、`sys_notice`、`sys_user_notice`、`sys_file`、`sys_job`、`sys_job_log`、`system_config`。

#### auth

| 源 | 目标 | 说明 |
|---|---|---|
| admin.controller：AdminAuthController（/auth/**）、OAuth2Controller、Oauth2ClientController | auth.controller | |
| admin.dto.auth：CaptchaResp、LoginReq、LoginResp、SmsLoginReq、UserInfoResp、UserMenuResp→menu；admin.dto.oauth2：AuthorizeReq、AuthorizeResp、TokenReq、TokenResp、OAuth2UserInfoResp | auth.dto | UserInfoResp 主入口在 auth（/auth/userinfo），menu/role 查询跨包复用 |
| admin.dto.req：Oauth2ClientCreateReq、Oauth2ClientPageReq、Oauth2ClientUpdateReq；resp：Oauth2ClientResp | auth.dto | |
| admin.service：AuthService→**AuthAppService**、OAuth2Service→**OAuth2AppService**、Oauth2ClientService→**Oauth2ClientAppService**（各含 Impl） | auth.service | 改名（§2.5） |
| admin.service.domain.OAuth2ClientDomainService/Impl | auth.service.domain | |
| admin.entity.SysOauth2Client + Mapper | auth | |
| infrastructure.OAuth2JwtSupport、OAuth2ResourcePathProperties | **infrastructure（留置）** | 被 RequestContextInterceptor 横切消费，非 auth 专属 |

表：`sys_oauth2_client`。

#### sync（§2.2）

| 源 | 目标 | 说明 |
|---|---|---|
| permission.controller：AbstractUserSyncController、AbstractRoleSyncController、UserRoleSyncController、ResourceEntitySyncController | sync.controller | 4 组 sync/full-sync 入口（full-sync 为同 Controller 方法） |
| permission.service.domain：SyncTypeGuard、**LocalProjectionGuard**；permission.constant.**LocalProjectionOwner** | sync.guard | 通道级守卫（来源/所有权/白名单通道面）；LocalProjectionGuard 消费面=sync 执行层+管理面，随通道守卫归 sync |
| permission.service.domain.SyncMetadataDomainService/Impl + permission.entity.SyncMetadata + permission.dto.common.SyncVersionRef | sync.metadata（**SyncMetadataMapper → sync.mapper**，§8.1 mapper 子包约定） | 记账设施，全仓仅同步通道消费（实测 4 个执行层 AppServiceImpl） |
| permission.service.sync：SyncAuthVerifier、SyncResultBuilder | sync | |
| permission.service.domain.sync：FullSyncStrategy、SyncContext、SyncMappingsResult、SyncModeStrategy、SyncModeStrategyFactory、SyncResourcesResult、SyncResult | sync.strategy | 同步策略族；执行层（user/role/resource 包内 4 个 XxxSyncAppServiceImpl）跨包调用 |
| permission.util.SyncKeyCodec | sync | sync API 契约键 |
| permission.dto.req：AbstractUserFullSyncReq、AbstractUserSyncItem、AbstractUserSyncReq、AbstractUserSyncScope、AbstractRoleFullSyncReq、AbstractRoleSyncItem、AbstractRoleSyncReq、AbstractRoleSyncScope、ResourceEntityFullSyncReq、ResourceEntitySyncItem、ResourceEntitySyncReq、ResourceEntitySyncScope、UserRoleFullSyncReq、UserRoleSyncItem、UserRoleSyncReq、UserRoleSyncScope | sync.dto | 通道契约 DTO 随 Controller；执行层跨包 import（能力→sync 边，§2.2 双向边接受） |

表：`sync_metadata`。

#### engine（§2.3 + 裁决 7）

| 源 | 目标 | 说明 |
|---|---|---|
| permission.service.domain.impl.PermQueryEngine | engine.core | |
| permission.service.domain：TypeResolutionService/Impl、ResolveContext、SubjectDomainService/Impl、BatchConditionEvaluator、BatchPermMutexEvaluator | engine.core | 类型解析/主体装载/批量评估器=引擎管线输入面与评估器 |
| permission.service：PermissionCheckAppService/Impl、PermissionQueryAppService/Impl、PermissionViewAppService/Impl | engine.service | 引擎对外查询编排 |
| permission.controller：PermAuthController、PermissionViewController | engine.controller | check/batch-check/check-interface/interface-snapshot/query-resources/query-scopes/effective-permission-codes |
| admin.security.**AdminPermissionValidator** + application.security.**AdminPermissionValidatorImpl** | engine | 门禁门面（消费面横跨全部 admin 能力 Service、写编排与查询；Impl 委托 PermQueryEngine），裁决 7 |
| admin.security.**AdminOperationCode** + permission.constant.**OperationCodeConstants** + admin.security.**OrgOperationCodeMapper** | engine.constant | 033 两册先随迁，034 合一为单册按资源类型分节（裁决 7） |
| permission.util：PermResultUtils、SnapshotAssembler、PermViewAssembler、ScopeModeSupport、OperationPermissionUtils、RolePermEntryMapper；permission.vo.RolePermEntry | engine.util / engine.vo | |
| permission.dto.query：PermBatchQuery、PermBatchResult、PermEvalContext、PermQuery、PermResult、PermViewFilter、PermViewResult；permission.dto.resp：AuthCheckResp、BatchAuthCheckResp、CheckInterfaceResp | engine.dto | 查询模型 + check 族响应（消费面全在 engine：PermAuthController/PermissionCheckAppService/PermResultUtils） |
| permission.dto.req.**PermissionCheckReq** | —（**删除**） | 实测零消费方（僵尸 DTO，按「无消费即删」政策随 033 清除） |

引擎无自有 mapper；其直读投影/映射表 mapper（PermissionView/QueryAppServiceImpl→ResourceEntityMapper、PermissionCheckAppServiceImpl→ResourceApiMappingMapper 等既有输入面装载形态）属 §8.4 断言显式豁免。`engine.service` 对外查询编排与能力包 DomainService 同层互调为既有形态（`PermissionQueryAppServiceImpl`→`DomainClassifyService`/`PermissionConflictDomainService`）；`engine.core` 不依赖能力包的 Service/DomainService（对实体/DTO/枚举的跨包 import 与 mapper 直读为既有输入面形态，见 §8.4 豁免 1——非断言对象）。

#### projection（裁决 8）

| 源 | 目标 | 说明 |
|---|---|---|
| permission.service.domain.LocalProjectionDomainService/Impl | projection | 内部事实投影统一门面（user/org/menu/type/condition 五能力投影写路径；消费方横跨写编排、管理面与 bootstrap，跨包调用） |
| permission.constant.PermConstants | projection | 跨能力共享投影常量（消费面实测含 engine.core、grant/resource/role/user 诸能力与 bootstrap，非仅写编排） |

#### bootstrap（裁决 6）

| 源 | 目标 | 说明 |
|---|---|---|
| application.bootstrap：AccessBootstrapInitializer、AccessBootstrapProperties、AccessBootstrapRunner、BootstrapGraphDefinition | bootstrap | |
| permission.service.domain.BootstrapSeedWriter + impl.BootstrapSeedWriterImpl | bootstrap | 直读 6 mapper 的固定图种子写入器随包迁入；`bootstrapSeedWriterIsBootstrapOnly` 断言排除集收敛为本包（§8.4） |

#### infrastructure（§2.3 + 裁决 4）

| 源 | 目标 | 说明 |
|---|---|---|
| infrastructure 现有底座：AccessRequestContext、CallerType、JsonbStringTypeHandler、MybatisFlexTenantConfig、MybatisFlexTypeHandlerConfig、OAuth2JwtSupport、OAuth2ResourcePathProperties、RequestContext、RequestContextInterceptor、SecurityAttributes、SignatureVerifier、TenantContextHolder、TimestamptzLocalDateTimeTypeHandler、TreeWriteLockSupport、infrastructure.util.HttpRequestUtils、SensitiveDataUtils | 原位 | 照旧 |
| permission.config：HeaderSignatureInterceptor、InternalApiSecretInterceptor、SecurityWebMvcConfig、AsyncConfig | infrastructure.config | 安全拦截器/异步配置 |
| permission.aop.PermissionChangeAspect + infrastructure.PermissionChange、PermissionChangeContext | infrastructure | 缓存失效横切（事务后广播） |
| permission.cache：PermInvalidationPublisher、PermCacheBoundaryValidator；admin.cache.**AdminCacheCatalog** + permission.cache.**PermCacheCatalog** | infrastructure.cache | 两册 033 先随迁，039 合一为单册（`admin:org-visibility` 越域命名同任务归位，Q-006 滚动发布边界登记不实施） |
| permission.util：OperatorContext、OperatorUtil、SecurityUtils、StringUtils、PageUtil、PermissionConstants、TreeBuilder；permission.util.SecurityEventType、SecurityLogUtil | infrastructure.util | 跨能力通用（Operator 族消费面横跨全部能力与写编排；PermissionConstants 消费面 grant/resource/role/user；TreeBuilder 消费面 role/resource；SecurityLog/EventType 随拦截器） |
| **任务治理设施（裁决 4）**：admin.service.domain.TaskExecutionDomainService/Impl、JobInvokeDomainService/Impl；admin.config.TaskExecutorConfig；admin.schedule：JobScheduleReconciler、TaskLeaseTakeoverScheduler；infrastructure.entity.SysTaskExecution；infrastructure.task：JobInvocable、TaskExecutionContext；**SysTaskExecutionMapper → infrastructure.mapper**（§8.1 mapper 子包约定） | infrastructure.task（mapper 接口除外） | 唯一消费方=platform job 链（JobAppServiceImpl 跨包调用） |
| admin.dto.req.**IdsReq** + permission.dto.req.**EmptyReq** | infrastructure.dto | 跨能力共享 DTO（IdsReq 消费面横跨 platform/user/org 诸能力 Controller 与 Service；EmptyReq 消费面 rule+resource） |
| admin.enums.**AdminErrorCode** + permission.enums.**PermissionErrorCode** | infrastructure.enums | 033 两册随迁，038 合一为单册（合类不合号） |

表：`sys_task_execution`（裁决 4，租约表随任务治理设施归底座）。

### 8.3 跨域同名类清单与逐组裁决

**（a）access-service 内部同名（搬包后同包冲突——裁决 1 改名消歧）**：

| 简单名 | 副本 | 裁决 |
|---|---|---|
| UserCreateReq | admin.dto.req（/user/create 管理页建档）vs permission.dto.req（abstract-user 同步通道 item） | admin 保名 → user.dto.req；perm 改名 **AbstractUserCreateReq** |
| UserUpdateReq | 同上两副本 | admin 保名；perm 改名 **AbstractUserUpdateReq** |
| UserResp | admin.dto.resp（/user/detail 档案）vs permission.dto.resp（abstract-user API）vs perm-common SDK（第三份，跨模块不冲突） | admin 保名；perm 改名 **AbstractUserResp**；perm-common 副本不动 |

**（b）infrastructure 内部同名（子包隔离即可，无需改名）**：`OperationLog` 注解（infrastructure.aop）与实体（infrastructure.entity）→ 均迁 audit 包后以 `audit.aop` / `audit.entity` 子包分置，Java 全限定名不冲突。

**（c）access-service ↔ perm-common 跨模块同名（不冲突，全保名——T-PERM-065 单源纪律维持）**：AuthCheckResp、BatchAuthCheckResp、CheckInterfaceResp、OperationPermissionResp、ResourceResp、RolePermissionItemResp、RolePermissionItemsResp、RoleResp、UserResp（SDK 份）、UserRolesResp、IdsReq、UserRoleListReq；另 common.model.IdReq ↔ perm.common.dto.req.IdReq。搬包后 access 侧进各能力包或 engine（check 族三件见 §8.2 engine 节），跨模块同名互不可见。

**（d）无关项**：common 与 gateway 各一份 GlobalExceptionHandler（跨模块、跨部署单元）。

### 8.4 架构断言重建设计（五测试，能力口径）

**QueryBoundaryArchitectureTest（验收 4 载体）**：断言对象从「admin/permission 两域」改为 12 能力包枚举集合。

1. 能力包类不依赖**其他能力包**的 mapper 包（`..{capA}..` → `..{capB}.mapper..` 全组合禁断，唯白名单除外，见豁免 6）。**断言面=mapper 包**：跨能力实体 import（如 grant 侧 import AbstractRole）为既有普遍形态、不禁止——「互不直读 Mapper/实体」的「实体」半句不落断言（architecture §3 / project-rules §8.2 同口径）。负向样例：在 menu 包新增类 import `role.mapper.UserRoleMapper`（不在冻结白名单）→ 拒绝。
2. 豁免声明（显式白名单，逐条注释依据）：
   - **engine** 直读投影/映射表 mapper（PermissionView/QueryAppServiceImpl→ResourceEntityMapper、PermissionCheckAppServiceImpl→ResourceApiMappingMapper 等既有输入面装载形态）与 `engine.service`→能力包 DomainService 同层调用（DomainClassifyService/PermissionConflictDomainService）——engine 非能力包，mapper 豁免面之外的类依赖走同层通用约束；
   - **projection**（LocalProjectionDomainServiceImpl）直读投影目标表 mapper——投影写路径职责（裁决 8）；
   - **sync.metadata** 记账数据访问以 SyncMetadataDomainService 封装为主；ResourceEntitySyncAppServiceImpl 存量同时直读 SyncMetadataMapper 一处（033 机械保留，白名单登记）；
   - **bootstrap**（BootstrapSeedWriterImpl）直读 6 mapper——既有豁免，由 bootstrapSeedWriterIsBootstrapOnly 断言单独锁定；
   - **QueryMapper XML 直读跨能力表**（UserMenuQueryMapper.xml 读 sys_user_org 等）是 XML 面非 Java 类依赖，不属本断言；XML 语义由 QueryMapperXmlContractTest 继续 lock。
   - **存量跨能力 mapper 引用冻结集合（裁决 9，2026-09-13 拍板）**：存量代码既有 DomainService/AppService 直读他实体 mapper 共 **19 类 30 边**，闭合清单如下（033 落地时在测试内按目标包类名逐行落 ArchUnit `ignoreDependency` 白名单，断言锁「**不得新增**」；存量收敛改走对方 DomainService 封装不在融合计划内，docs/pending-problems.md Q-009 登记）：

     | 消费方（目标包.类） | 直读的其他能力包 mapper |
     |---|---|
     | grant.PermissionGrantAppServiceImpl | role.AbstractRoleMapper、rule.PermissionConditionMapper、type.OperationPermissionMapper、resource.ResourceEntityMapper |
     | grant.PermissionGrantPlanDomainServiceImpl | domain.DomainConfigMapper、rule.PermissionConditionMapper、type.OperationPermissionMapper、resource.ResourceEntityMapper |
     | grant.PermissionGrantDomainServiceImpl | type.OperationPermissionMapper、type.TypeDefinitionMapper |
     | grant.GrantOriginDomainServiceImpl | role.AbstractRoleMapper、type.OperationPermissionMapper |
     | user.UserManageAppServiceImpl | role.AbstractRoleMapper、role.UserRoleMapper |
     | user.UserServiceImpl（迁移后 UserAppServiceImpl） | org.SysUserOrgMapper |
     | user.BatchAdminUserProjectionWriter | role.UserRoleMapper、resource.ResourceEntityMapper |
     | role.UserRoleProjectionWriter | user.AbstractUserMapper |
     | menu.UserMenuQueryServiceImpl（迁移后 UserMenuQueryAppServiceImpl） | role.UserRoleQueryMapper |
     | domain.DomainClassifyServiceImpl | type.TypeDefinitionMapper |
     | type.TypeDefinitionAppServiceImpl | grant.RoleResourcePermissionMapper、resource.ResourceApiMappingMapper |
     | type.ResourceTypeOwnershipGuard | resource.ServiceConfigMapper |
     | resource.ResourceManageAppServiceImpl | grant.RoleResourcePermissionMapper |
     | resource.ResourceEntityDomainServiceImpl | grant.RoleResourcePermissionMapper |
     | resource.DependencyAppServiceImpl | type.OperationPermissionMapper |
     | rule.ConditionAppServiceImpl | grant.RoleResourcePermissionMapper |
     | rule.PermissionConditionDomainServiceImpl | grant.RoleResourcePermissionMapper |
     | rule.ConflictRuleAppServiceImpl | role.AbstractRoleMapper（内联 FQCN） |
     | rule.PermissionConflictDomainServiceImpl | type.OperationPermissionMapper |

     **采集口径（写死）**：字节码级依赖形态全采集（import 行 + 内联 FQCN 字段声明，同 ArchUnit 字节码分析口径）——禁用单 import 行扫描（内联 FQCN 形态实证存在于 ConflictRuleAppServiceImpl 等）；引擎（engine）/projection/bootstrap/sync 记账（含 ResourceEntitySyncAppServiceImpl→sync.mapper 直读一处，豁免 3）不在此表（非能力包源集或另有豁免）。
3. 原「query 包」五条规则（admin/permission mapper 互禁、application 非 query 禁 mapper、query mapper 只读前缀、query 包不依赖域实体/Mapper）中：前三条随包结构消失（application 解散），只读前缀规则改为「QueryMapper（**按类名 `*QueryMapper` 匹配**，非整包——避免误杀同包写 Mapper）接口方法 select/count/list 前缀」继续生效。
4. **落位兜底断言（外评补充，033 落地）**：全部主源码类必须落在 17 顶层包（`..access..` 下类 `resideInAnyPackage(..access.auth.., ..access.user.., …, ..access.infrastructure..)` 全枚举）——防漏行类静默残留旧包；负向样例：类残留 `..access.admin..` → 拒绝。

**AccessServiceArchitectureTest**：现行 admin↔permission 互不依赖族（adminShouldNotDependOnPermission / adminServiceImplShouldNotDependOnPermission / permissionShouldNotDependOnAdmin / applicationMayDependOnBothDomains 四条）**删除**——能力口径允许 AppService/DomainService 同层跨能力依赖（project-rules §8.2、设计 §2.4）。保留并重判：

1. Mapper 数据边界与 QueryBoundary 同源（同断言两处引用同一规则文本；实体面不禁，§8.4 规则 1 口径）；
2. `bootstrapSeedWriterIsBootstrapOnly`：排除集由「application.bootstrap + permission.service.domain.impl + architecture」重判为「**bootstrap 包** + architecture 测试包」（裁决 6 使排除集收敛单包）；
3. 现行规则对非实现类的排除细节（`..admin.controller..` 排除、enums 排除等）在能力口径下逐条核对后随包集合重写——能力包间实现类依赖已放开，排除细节仅保留在 Mapper 边界与 bootstrap 断言内。

每条重建规则附负向样例自证仍能拒绝违规（033 落地时以 ArchUnit 违规注入验证）。

**AppServiceOperationLogCoverageTest**：扫描包常量由三域 4 包改为 12 能力包 `service.impl` 包集合（能力包统一子结构后可合并模式匹配）；覆盖下限自证由 `>= 40` 改为逐能力包断言（每能力包至少扫描到 1 个 ServiceImpl，或显式登记该能力无 ServiceImpl）；module 三值化、KNOWN_TABLE_NAMES 33 表、targetType 例外清单不变。

**HttpApiPathSnapshotTest**：路径快照（EXPECTED_PATHS）**零变化**——URL 两风格维持（§6、Q-001）；签名快照（EXPECTED_SIGNATURES）因包名与裁决 1 改名**全量机械重生成**（`access.permission.dto.req.UserCreateReq` → `access.user.dto.req.AbstractUserCreateReq` 等），`normalize()` 剥离规则不变。迁移验收纪律：路径集合 diff 必须为空，任何路径增删即迁移引入契约漂移、禁止。RETIRED_PATHS 清单不变；037 执行时增补 `/config/*` 四条。

**QueryMapperXmlContractTest**：query XML 目录由 `resources/mapper/query/` 改为 `resources/mapper/{org,menu,role}/`（随 QueryMapper 归位）；六组断言（只 select、显式 tenant_id、分页 ORDER BY+LIMIT、显式列、UserRoleProjection 有效期窗口与 LEFT JOIN、IN 空集合守卫）语义全部保留，仅文件路径断言更新。

### 8.5 实施顺序与回归口径

1. **T-ACCESS-033（单任务，纯机械）**：按 §8.2 映射表搬包 + import 更新 + 改名面（admin 14 对 Service→AppService、QueryService 三对、perm 用户 DTO 三组）+ 僵尸 DTO 三件删除（PermissionCheckReq、BatchResultResp、OperationDetailReq）+ 五测试重建（含冻结白名单落地）+ XML 迁移。禁止任何业务逻辑改动；契约测试与双实例验收纪律沿用 T-ACCESS-001~012 剧本。
2. 每步验证序：全量编译 → 单测轨道（`-DskipTestcontainers=true`）→ 全量回归（`-T 1C` 含 E2E，收口形态）。
3. 033 完成后按计划「建议执行顺序」推进 040 契约深合一 → 034/035/036/037/038 并行 → 039（034 后）→ 041 收尾；各任务回归口径以各自任务卡为准。

## 9. 定案修订对照

| 既有定案 | 处置 |
|---|---|
| access-service-architecture §1.2「本阶段不把两个领域立即完全扁平化」/ §11 演进方向 | 本文件承接启用（演进方向立项） |
| access-service-architecture §3 模块边界（admin/permission 包、application 唯一跨域写编排层） | 重写为能力包口径（T-ACCESS-032） |
| access-service-architecture §9 错误码两域分立、不得重编号 | 字面保持（分段 + 不重编号），枚举面合一表述更新（T-ACCESS-038） |
| project-rules §8.2 跨域 Mapper 边界 + `QueryBoundaryArchitectureTest` | 以能力为对象重建（T-ACCESS-032 设计、T-ACCESS-033 落地） |
| access-service-architecture §5.2 管理事实表与权限计算表不强行合并 | **维持，无修订** |
| 2026-09-12 T-PERM-065 admin Req DTO 不换绑 | **维持，无修订** |
