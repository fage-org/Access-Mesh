---
doc_type: design
title: access-service 能力包结构与两域融合
status: draft
domain: cross-service
last_reviewed: 2026-09-13
---

# access-service 能力包结构与两域融合

> 定位：本文件承载 2026-09-13 用户确认的两域融合定案（决策登记见 [decision-registry.md](decision-registry.md) 2026-09-13 行），是能力包终态结构、概念收敛项与文档重组的**目标设计**。
>
> `status: draft`——[T-ACCESS-032](../tasks/T-ACCESS-032.md) 完成 §8 归属清单与边界断言细化后转 `adopted`；在此之前现行权威结构仍以 [access-service-architecture.md](access-service-architecture.md) 为准，本文件不约束实现。
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

权限引擎（PermQueryEngine + 快照/装配管线 + 引擎专属缓存）为独立子系统包，不塞入任何能力包；infrastructure 底座（请求上下文、拦截器、TypeHandler、跨能力通用实体等）照旧。

### 2.4 application 包解散

`access.application` 跨域写编排与 `application.query` 只读查询按归属并入能力包：写编排随主实体（UserWriteAppService→user、OrgWrite→org、MenuWrite→menu、UserOrgWrite→org）；查询按主实体（UserMenuQuery→menu、UserRoleQuery→role、OrgVisibilityQuery→org）。能力包合一后原「跨域」语义消失，跨能力写事务由主实体能力的 AppService 编排。

### 2.5 命名收敛

全仓统一 `XxxAppService/XxxAppServiceImpl` + `XxxDomainService/XxxDomainServiceImpl`（project-rules §8 既有规范）；admin 域 14 对 `XxxService/Impl` 改名为事实性修正，非新决策。application.query 三对 QueryService 的改名与否随 032 裁决、033 落地，全仓不留第二套调度层命名。

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
- **USER 轨细粒度化**：UserManageAppServiceImpl 换绑——updateUser 按字段分档（name/extra 变更查 `USER:UPDATE`；`enabled != null` 查 `USER:ENABLE`，防 UPDATE 绕过启停分权）、deleteUsers 查 `USER:DELETE`；`USER:MANAGE` 退役（无消费即删，仓库既有政策）。bootstrap 固定图 USER 段本无 MANAGE 位（空库上 perm 轨 update/remove 恒拒不可用）——换绑后由拒转放行是**预期的行为变化**（修复该死锁），非等价改写。自身豁免（两轨同款 operatorId==targetId 跳过门禁）保留；豁免**范围限定**（改自己的可变字段/动作边界）登记为已知遗留、不在本计划解决（2026-09-13 定案）。
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

- **URL**：维持两风格（admin 裸路径 + perm `/api/perm/**`），登记为已知问题（architecture 演进方向区），后续单独改。
- **错误码编号段**：维持 `1xxxx`/`2xxxx` 不重排。
- **前端信息架构**：维持一棵「系统管理」树 + 隐藏授权入口，不纳入融合；菜单重组如做另独立立项（产品决策）。

## 7. 文档与叙事

- **API 契约深合一**：两份契约并为一份 access-service 契约总册（按能力分章、两个 URL 家族同册分列）；`docs/design/permission-center/` 目录解散重组、`admin-service-api-contract.md` 退役并入；overview / core-flows / implementation 等设计内档归引擎子系统文档位；全仓引用一次更新。
- **任务 ID 前缀**：T-PERM / T-ADMIN 等不动（历史档案索引）。
- **规则与技能文件**：按能力 + 引擎口径重写（.claude/rules 单副本、skills 双副本同步纪律不变、AGENTS.md 指针表随改）。

## 8. 归属清单与边界断言（待细化）

> 本章由 [T-ACCESS-032](../tasks/T-ACCESS-032.md) 细化后本文件转 `adopted`：

- 逐类归属清单：现有全部类 / 表 → 目标包的映射表，含争议项裁决（sys_task_execution 等基础设施类任务设施的归属、query mapper 的归属与豁免面、service_config/operation_permission 全链归属）。
- 跨域同名类清单与逐组裁决（至少 UserCreateReq/UserUpdateReq/UserResp 三组）。
- 全部按包名扫描断言的重建设计：QueryBoundaryArchitectureTest、AccessServiceArchitectureTest、AppServiceOperationLogCoverageTest、HttpApiPathSnapshotTest 快照口径（每条规则负向样例自证）。
- application.query 三对 QueryService 的归属与命名裁决。
- 实施顺序与回归口径。

## 9. 定案修订对照

| 既有定案 | 处置 |
|---|---|
| access-service-architecture §1.2「本阶段不把两个领域立即完全扁平化」/ §11 演进方向 | 本文件承接启用（演进方向立项） |
| access-service-architecture §3 模块边界（admin/permission 包、application 唯一跨域写编排层） | 重写为能力包口径（T-ACCESS-032） |
| access-service-architecture §9 错误码两域分立、不得重编号 | 字面保持（分段 + 不重编号），枚举面合一表述更新（T-ACCESS-038） |
| project-rules §8.2 跨域 Mapper 边界 + `QueryBoundaryArchitectureTest` | 以能力为对象重建（T-ACCESS-032 设计、T-ACCESS-033 落地） |
| access-service-architecture §5.2 管理事实表与权限计算表不强行合并 | **维持，无修订** |
| 2026-09-12 T-PERM-065 admin Req DTO 不换绑 | **维持，无修订** |
