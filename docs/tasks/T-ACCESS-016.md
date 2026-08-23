---
doc_type: task
id: T-ACCESS-016
title: 身份与资源模型设计定稿（B-lite 终态 + 类型收敛映射 + 引擎显式 API 契约）
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
  - docs/design/services/admin-service-api-contract.md
depends_on: []
blocks: [T-PERM-042, T-ACCESS-018, T-ACCESS-019, T-ORG-001]
acceptance:
  - "access-service-architecture 新增「主体身份模型」章节：abstract_user.id 为唯一主体 ID；本地用户 sys_user.id = abstract_user.id；外部主体仅有 abstract_user 行；Sa-Token 会话、OperatorContext、createdBy/updatedBy、user_role.abstract_user_id、perm:effective-roles 缓存键统一使用主体 ID；OperatorSubjectResolver/resolveOperatorSubjectId 目标态为删除；本地与外部主体 ID 不碰撞的分配机制（用户主体专用数据库序列或等价方案）写明取舍"
  - "access-service-architecture 新增「资源类型注册表」章节：USER/ADMIN_USER→USER、ROLE/ADMIN_ROLE→ROLE、MENU/ADMIN_MENU→MENU、SYSTEM_CONFIG/ADMIN_CONFIG→SYSTEM_CONFIG、ADMIN_ORG→ORG 收敛映射定稿；ADMIN_SYNC_TASK 删除；ADMIN_FILE/ADMIN_NOTICE 等无重复项不改名；user_type.ADMIN_USER 更名 LOCAL_USER（主体来源语义，不再兼任资源类型）；type_value 终值分配明确（处理 USER=6 既有占用与 ADMIN_* 段退役值，同 tenant_id+type_key 内唯一）"
  - "api-contract/implementation 定稿引擎显式资源 API 契约：对外 hasPermissionByCode(...) 与 getDeniedResourceCodes(...)；getDeniedEntityIds(...) 仅引擎内部或已完成解析的调用方使用；删除泛型 <ID>、Object resourceId、toLongId() 运行时猜测；resource_entity(USER).code = subjectId、resource_entity(ROLE).code = roleId 的业务编码语义定稿"
  - "明确 resource_entity.id 边界：USER/ROLE 等业务对象门禁与跨服务 SDK 不得使用 resource_entity.id（统一业务 code/externalId）；权限域内部及直接管理资源实体的后台接口（资源树、API 映射、资源依赖、权限树等 resource_entity 自身的管理链路）允许继续使用，现有 ApiMappingResp/ResourceDependencyResp/ResourcePermissionTreeResp 等契约不因此重构"
  - "bootstrap 首管理员权限模型定稿：bootstrap 创建首管理员并绑定一个管理用功能角色（按 bootstrap 管理 API 清单双层最小授权）；禁止平台超管旁路与硬编码超级用户；首管理员对目标接口仅预授 API:ACCESS+canGrant 用于授权传递，因目标 API 无映射，首管理员自身无法经 Gateway 调用它；E2E 目标用户与普通功能角色由 E2E 场景内经管理链路创建（双角色双用户模型，配合 T-ACCESS-020/T-ACCESS-021）"
  - "bootstrap 管理 API 清单定稿（首管理员授权链闭合）：固定 E2E 实际所需的管理 API 外部路径——写动作（用户创建、角色创建、API 映射创建、授权、角色分配、回收）与授权页初始化所需读接口（角色树、类型定义、资源树、操作列表、条件列表、既有授权查询等，以授权页加载链路为准逐项列入；缺读接口则首管理员能进页面但初始化请求全 403），存在业务门禁的管理 API 授予双层最小权限——Gateway 层 API:ACCESS（实例级）+ 接口内部业务门禁（含读接口 VIEW 门禁；permission-condition/list 例外：无业务门禁、仅 Gateway 层实例授权，不得补建 CONDITION:VIEW）；目标 API 与管理 API 的 resource_entity(API) 资源均由 bootstrap 内部写入，E2E 外部管理链路只创建目标映射，不授予 RESOURCE:CREATE；禁止 API 类型级 scopeAll、Gateway 临时白名单、平台超管旁路（Gateway 对空快照默认拒绝，授权传递要求操作者持有目标权限且 canGrant=true，闭环只能靠最小种子解决，不开任何旁路）"
  - "E2E 目标接口定稿为 POST /admin/role/my-info（登录用户自查，无二层管理门禁）：bootstrap 仅预建其 resource_entity(API) 资源并给首管理员精确授予该 API 的 API:ACCESS + canGrant=true，不创建其 resource_api_mapping——映射由 E2E 真实创建，既保证「真实创建 API 映射」步骤成立，又使 canGrant 授权传递链合法、目标用户保持初始 403"
  - "bootstrap 管理角色业务门禁最小集（明确到 resourceType+operation+scopeMode+resourceCode，实施时按写入口门禁全量核对微调——缺漏补种、过授收敛）：USER:CREATE（scopeAll，创建目标用户）；ROLE:CREATE（scopeAll，创建 BASIC_ROLE）；ROLE:MANAGE（scopeAll，管理新建角色与为用户分配角色；其继承掩码已含 VIEW，不重复授 ROLE:VIEW）；SERVICE:MANAGE_API_MAPPING（access-service 实例，创建目标 API 映射）；TYPE_DEFINITION:VIEW（scopeAll，授权页无条件加载类型列表且后端 listTypes 强制门禁）；RESOURCE:VIEW（scopeAll，授权页资源树加载门控）；OPERATION:VIEW（scopeAll，授权页操作列表加载门控）；目标 API 实例的 API:ACCESS+canGrant（向 BASIC_ROLE 授权）。不授予 RESOURCE:CREATE 等 API 资源创建权限，避免无谓扩大根权限"
  - "bootstrap 固定图包含 resource_entity(SERVICE, code=access-service)：当前 DDL 无 SERVICE 类型资源种子、本地投影亦不产出（仅有 USER/ROLE/MENU 投影），实例级 SERVICE:MANAGE_API_MAPPING 需要该服务资源作为绑定对象，由 bootstrap 幂等写入"
  - "bootstrap 载体终态定稿：首期固定租户 1（类型种子即租户 1，不做租户开通）；载体为 access-service 内默认关闭（access.bootstrap.enabled）的幂等 ApplicationRunner，复用现有领域服务（用户/主体/投影/角色/授权链）与 BCrypt；不维护 SQL bootstrap 种子链路，不建 bootstrap 框架/独立模块/分布式锁；幂等按三状态口径执行（全图不存在则单事务创建 / 全图完整匹配则整体 no-op / 部分存在或不匹配则 fail-fast 报告冲突，唯一约束仅并发兜底）；compose 仅承诺一键基础设施，README 写明 access-service/Gateway/前端启动顺序"
  - "MENU 资源语义定稿：MENU:CREATE/UPDATE/DELETE/VIEW 仅保护菜单配置后台；普通用户菜单可见性仍按 sys_menu.resource_type/resource_code 关联业务权限派生（现有 UserMenuQueryService 语义），两者不混同"
  - "T-PERM-042 / T-ACCESS-018 / T-ACCESS-019 / T-ORG-001 可直接按定稿章节实施，无需二次设计决策"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-23
---

# T-ACCESS-016 身份与资源模型设计定稿

## 背景

风险基线核实确认：本地用户存在三个数值独立的 ID（`sys_user.id` / `abstract_user.id` / `resource_entity(ADMIN_USER).id`），`OperatorSubjectResolver` 约 80 次方法调用分布于 18 个生产文件；生产代码 9 处 `getDeniedIds` 外部调用中至少 6 处 USER/ROLE 实例门禁直接传入 `abstract_user.id`/`abstract_role.id`，而引擎按 `resource_entity.id` 匹配（`PermQueryEngine` 实例授权路径）；角色与部分用户创建路径（`UserManageAppServiceImpl`）缺同事务资源投影。三个 ID 空间同为 `Long`，误传可能恰好命中另一空间合法记录，简单存在性断言无法识别来源。

已确认采用 B-lite 路线：统一本地主体 ID 为确定目标，不继续投资双 ID 断言与 Resolver 加固；项目未上线、数据库可重建，是统一成本最低的窗口。本任务把已拍板的终态沉淀为权威设计，作为模型收敛 Epic 四张实施卡的唯一契约来源。

## 范围

- `access-service-architecture.md` 新增「主体身份模型」「资源类型注册表」两章，修订受影响章节（§3 模块边界、§4 数据与事务、§6 安全）。
- `schema/access-service.sql`：type_definition 种子终态注释（type_value 终值分配表，实施改动在 T-ACCESS-018）。
- `permission-center/api-contract.md` / `implementation.md`：引擎显式资源 API 签名与 code/entityId 语义。
- `admin-service-api-contract.md`：管理契约中主体 ID/资源编码对外口径核对（业务对象门禁用业务编码；资源实体管理链路保留 resource_entity.id）。

## 当前口径

- 统一后保留的两个 ID 属不同概念：主体 ID（"谁"）与 resource_entity.id（"哪条授权资源记录"），不视为重复设计。
- 不引入全局对象 ID 中心；最多使用只服务于用户主体的数据库序列，确保本地与外部主体不碰撞。
- 外部同步主体继续仅存在于 abstract_user（业务键同步链路不变，见 access-service-architecture §4）。
- 引擎 API 拆分解决的是整个实例权限契约歧义，不只是当前错参点。
- 缓存框架不动：`beginRead`/剩余 TTL/30 秒边界保留；主体键缓存本以 abstract_user.id 为标识符，统一后缓存键与值均不变，仅空库重建 runbook 清理开发/验收 Redis（实施在 T-ORG-001）。

## 非目标 / 遗留

- 不做全局统一对象 ID 中心、全平台共享序列、ID 包装类型双轨。
- 不做在线数据迁移设计（空库重建模式，runbook 归 T-ORG-001）。
- 菜单可见性派生逻辑不改（已按 v3.5 实现），仅定稿 MENU 管理资源语义。

## 设计回写（2026-08-23）

定稿内容与写回位置：

- `access-service-architecture.md`：新增 §12 主体身份模型（abstract_user 唯一 ID 源、序列预取闭环、OperatorSubjectResolver 退役、业务编码语义）、§13 资源类型注册表（收敛映射、type_value 终值、扩展操作 code/bit 终值）、§14 空库 bootstrap 首管理员权限模型（授权统一落管理用功能角色、固定图稳定业务键、管理 API 清单、业务门禁最小集、目标接口定稿）；§4.2/§4.3/§6.1 终态注记。
- 保留业务键终态（§4.3）：subject 侧 `ADMIN_USER`→`LOCAL_USER`；role 侧 `ORG|POSITION` 与 `SYS_USER_ORG` 不变；resource 侧取消类型级保留——USER/MENU 保持公共基础类型，本地投影行按所有权保护（外部 sync UPSERT/DISABLE/DELETE 任一 mutation 分支前置 `rejectIfLocalResource`、新建撞 code 由唯一索引兜底、full-sync 删除范围不触及 `owner=access-service` 行），管理入口类型保留清单换值 `{USER, ORG, MENU}`。
- 授权页读接口 VIEW 门禁补齐范围为 3 项（§14.5：角色树/资源树/操作列表，随 T-PERM-042 落地）；`permission-condition/list` 维持无业务读取门禁（api-contract §5.6 既有产品确认：条件规则全租户开放、非敏感），访问控制仅由 Gateway 层实例级 `API:ACCESS` 承担。
- `schema/access-service.sql`：文件头 type_value 终值分配表、扩展操作 bit 终值与保留业务键终态（INSERT 不动，种子重编归 T-ACCESS-018）；`sys_user`/`abstract_user` 表注释终态注记。
- `permission-center/api-contract.md`：§3.4 实例授权业务编码语义；§10 决策 18。
- `permission-center/implementation.md`：§3.1 引擎便捷 API 终态（引擎纯查询不抛异常）；§7.2/§7.3/§7.6 同步。
- `services/admin-service-api-contract.md`：§2 门禁终态口径；§3 投影终态注记。

T-PERM-042 / T-ACCESS-018 / T-ACCESS-019 / T-ORG-001 按定稿章节实施。
