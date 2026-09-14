---
doc_type: problems
title: 待解决问题清单
counter: Q-010           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-09-14
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：定案结论（含「不解决」拍板）唯一载体是 `docs/design/decision-registry.md`，本文件不复制定案正文；问题转出后方案细节唯一详细来源是任务卡，本文件只保留索引行。

## 未收敛问题

## Q-001 URL 路径风格统一（admin 裸路径 vs perm 前缀路径）

- **状态**：open
- **登记**：2026-09-13
- **来源**：两域融合定案——表面项拍板「URL 两风格维持，仅登记，后续单独改」（decision-registry 2026-09-13 融合行③；capability-structure §6）
- **关联**：—

**现象与证据**：access-service 同一服务两套 URL 风格并存——管理面家族裸路径（控制器 `@RequestMapping("/org")`、`"/user-org"`、`"/oauth2/client"` 等，原 admin 域入口，契约见 `docs/design/access-service-api-contract.md`（契约总册））；perm 家族前缀路径（`/api/perm/abstract-role`、`/api/perm/auth/**` 等，原 permission 域入口）。Gateway 快照鉴权按路径匹配接口资源（注册面：access-service 自有路由由 bootstrap 固定图写入、外部接入服务经 service-config sync FULL 声明，见 access-service-architecture §14.2/§14.3），前端 API 层按两风格分别拼路径。

**影响**：调用方与文档心智分裂（同一平台两套入口风格）；统一属对外契约破坏性变更——牵动前端全部调用点、Gateway 路由、已注册接口资源与 SDK 端点，需一次性迁移窗口与兼容策略，未定案前不动。

**设想方向（未定案）**：统一为一套前缀风格（如 `/api/<域>/**`）+ 前端/Gateway/接口资源注册同批迁移；或维持现状仅在文档明确两风格边界。

## Q-002 USER 写入口自身豁免的范围限定

- **状态**：open
- **登记**：2026-09-13
- **来源**：融合计划外部评审发现的门禁缺口，用户定案「豁免范围应限定、登记遗留，不在本计划解决」（decision-registry 2026-09-13 融合行处置；T-ACCESS-034 非目标/遗留）
- **关联**：—

**现象与证据**：两轨用户写入口对「操作者 == 目标用户」整段跳过门禁——perm 轨 `UserManageAppServiceImpl.updateUser` 以 `if (!operatorId.equals(req.userId()))` 包裹门禁（`:276-281`）；`deleteUsers` 的 `nonSelfUserIds` 只决定门禁集合、`softDeleteBatch` 用**含自身**的 `existingUserIds`（`:345-362`——原校 `USER:MANAGE`，已随 T-ACCESS-034 换绑为 `USER:DELETE`）；admin 轨 `UserServiceImpl` 同款豁免。T-ACCESS-034 换绑后，自身路径仍零 USER 操作位即可改自己 name/extra/enabled 或删自己（LOCAL_USER 被 `localProjectionGuard` 拒；`abstract-user/update` 端点契约已发布但仓内零调用方——前端零消费、SDK 无该方法；`abstract-user/remove` 经 SDK `deleteUsers` 可达）。

**影响**：操作者对自身操作时零权限通过——自身 `enabled` 变更绕过启停分权（USER:ENABLE）、自删绕过删除门禁，「改自己允许哪些字段/动作」的权限边界缺失。示例：已登录主体对自身 id 调 `/api/perm/abstract-user/remove`（SDK `deleteUsers` 可达）——自身被 `nonSelfUserIds` 剔除后门禁集合为空，软删直接生效。

**设想方向（未定案）**：豁免收窄为档案字段（name/extra），enabled 变更与删除不豁免（须 ENABLE/DELETE 操作位）；或取消豁免统一按字段分档查码；两轨须同步定案（两轨一致是既有豁免的保留依据）。

## Q-003 operationCodeKey 族大小写口径不一致（授权域归一 vs 查询域裸拼）

- **状态**：open
- **登记**：2026-09-13（历史登记收编——原 2026-09-07 定案「保持现状待统一」）
- **来源**：`docs/design/engine/implementation.md` §8.2（登记待统一）；decision-registry 2026-09-07 行
- **关联**：—

**现象与证据**：授权域（PermissionGrantDomainServiceImpl 及 Plan 域）先 `toUpperCase()` 再拼键——`applyGrantPlan` 传小写 `view` 可匹配 DB `VIEW` 授权成功；查询/解析域（TypeResolutionService / ResolveContext / PermissionQuery）裸拼——同一份小写 `view` 走 check/dependency 链路解析不到、按 20005 fail-closed 拒绝。`operationCode` 入参仅 `@NotBlank`、无大写 `@Pattern` 锁。

**影响**：现在为什么没出事——唯一活跃调用方为管理前端（全发大写常量）；一旦出现小写调用方（SDK 消费者/脚本/新前端页），同一操作码双语义：授权面成功、查询面拒绝。数据示例：`apply-grant-plan` 提交 `code="view"` 落库成功，运行时 `check` 提交 `code="view"` 却 20005 拒绝。

**设想方向（未定案）**：raw 严格化 / 归一宽松化 + DTO `@Pattern` 大写前置拒绝——均属行为变更，需单独立项（2026-09-07 定案原文）。

## Q-004 BusinessKeys / SyncKeyCodec 命名偏离 XxxUtil 规范

- **状态**：open
- **登记**：2026-09-13（历史登记收编——原 2026-09-07 定案「用户已知、后续 IDE 统一改名」）
- **来源**：decision-registry 2026-09-07 行；project-rules §6.2（规范原文）
- **关联**：—

**现象与证据**：project-rules §6.2 要求工具类命名 `XxxUtil`；`perm-common util/BusinessKeys.java`（业务键唯一入口）与 `access-service permission/util/SyncKeyCodec.java`（sync 契约键）为名词命名。

**影响**：纯规范层面（命名不一致），无行为影响；评审已按 registry 豁免不再报，改名落地前规则例外句不写。

**设想方向（未定案）**：IDE 统一改名（机械替换 + import 面）；时机未定。

## Q-006 ORG_VISIBILITY 缓存 key 改名后的滚动发布双命名空间失效（登记不实施）

- **状态**：open
- **登记**：2026-09-13（历史登记收编——2026-09-13 外评裁决「登记不实施」）
- **来源**：T-ACCESS-039 当前口径（登记性已知边界）；decision-registry 2026-09-13 融合行处置④
- **关联**：—

**现象与证据**：T-ACCESS-039 已将 catalog code `admin:org-visibility` 改名为 `access:org-visibility`（2026-09-13 落地，不做兼容双读）——此后若发生新旧实例并存的滚动发布，双方 `evictAll` 各扫自身 catalog code 命名空间互不可删（RedissonBucketStore 按 `{tenantId}:{catalogCode}:*` 精确扫描实证）；Nacos 按旧 code 配置的 TTL 覆盖同批被静默忽略。按 code 索引的运维可见面同批切换：Micrometer `catalog` 标签 series（RedissonBucketStore/CombinedL1L2Store 等以 `catalog.getCode()` 建 series）与日志中的 catalog 值随 code 改名，首次部署的看板/告警须按新 code 取数（2026-09-13 外评 claude 补登，当前仓内零监控规则消费、未部署无实际影响）。

**影响**：现在为什么没出事——项目未正式部署、无新旧实例并存场景；首次滚动发布时若不补，权限/组织关系变更后另一版本命名空间的 org-visibility 缓存不失效，最长旧 TTL 60s 的越界可见/错误拒绝（该条目非安全快照链路，影响有界）。

**设想方向（未定案）**：届时补双命名空间失效（写路径同批 evict 新旧 code）+ 旧 Nacos 键迁移告警 + 新旧共存失效回归锁（外评建议原样在案）。

## Q-007 sync 通道跨类型父子边是否收紧为同类型父边

- **状态**：open
- **登记**：2026-09-13（历史登记收编——原 2026-09-05 T-PERM-052 已知边界「另行评估」）
- **来源**：T-PERM-052 已知边界（登记，另行评估）
- **关联**：—

**现象与证据**：resource sync/full-sync 的 `parentResourceTypeCode` 可与 item 自身类型不同（跨类型父子边合法）；remove 级联删除全集守卫（batchGetDescendantIds 展开、含跨类型后代，按类型值一次批量判定）已防御连带误删破坏面。

**影响**：跨类型父子边可构造跨类型资源树依赖（数据示例：SYNC 类型 A 的资源挂 MANAGED 类型 B 的父节点），该结构是否应为合法终态未定案；现状有守卫兜底、无已知破坏。

**设想方向（未定案）**：入口收紧为同类型父边（校验拒绝）或维持合法并文档化——另行评估。

## Q-008 SERVICE/API 固定图种子行维持 MANAGED，是否声明内部来源收紧

- **状态**：open
- **登记**：2026-09-13（历史登记收编——原 2026-09-05 T-PERM-052 已知边界「另行评估」）
- **来源**：T-PERM-052 已知边界（登记，另行评估）
- **关联**：—

**现象与证据**：USER/ORG/MENU/ROLE 四类型种子已声明 SYNC+access-service（T-PERM-052 内部来源统一），SERVICE/API 固定图种子行维持 MANAGED——管理面手工 CRUD 现状允许，靠启动固定图校验保护。

**影响**：SERVICE/API 类型行可经管理面手工增删改，与内部事实链路类型的收紧口径不一致；现状靠 bootstrap 固定图校验兜底、无已知破坏。

**设想方向（未定案）**：声明内部来源收紧（对齐四类型）或维持 MANAGED 依赖固定图校验——另行评估。

## Q-009 存量跨能力 mapper 直读收敛（19 类 30 边冻结白名单的后续消化）

- **状态**：open
- **登记**：2026-09-13
- **来源**：T-ACCESS-032 双轨评审发现 + 用户拍板「冻结白名单」（decision-registry 2026-09-13 T-ACCESS-032 行裁决⑨；capability-structure §8.4 豁免 6；白名单实体由 T-ACCESS-033 落地冻结、不承接本问题的收敛）
- **关联**：—

**现象与证据**：存量代码既有 DomainService/AppService 直读他实体 mapper 共 **19 类 30 边**（闭合清单见 capability-structure §8.4 豁免 6 表；如 `PermissionGrantAppServiceImpl` 直读 role/type/rule/resource 四 mapper、`UserMenuQueryServiceImpl` 直读 `UserRoleQueryMapper`、`UserServiceImpl` 直读 `SysUserOrgMapper`、`PermissionGrantPlanDomainServiceImpl` 直读 domain/type/rule/resource 四 mapper）——同域内今日全部合法；T-ACCESS-033 拆包后成为「能力包 A import 能力包 B 的 mapper」，按裁决⑨进 `QueryBoundaryArchitectureTest` 冻结白名单（断言锁「不得新增」，033 落地时在测试内按 §8.4 表逐行落 ArchUnit ignoreDependency）。采集口径：字节码级全形态（import 行 + 内联 FQCN），禁单 import 扫描（内联 FQCN 形态实证存在于 ConflictRuleAppServiceImpl 等）。

**影响**：现在为什么没出事——白名单锁死增量，存量不扩大；但这 30 边仍是能力包间数据边界豁免点，边界语义靠白名单维持而非结构收敛。数据示例：grant 包的授权编排直读 role/type/rule/resource 四包 mapper，任何一处表结构变更的耦合面横跨五个能力包。

**设想方向（未定案）**：逐边改走对方 DomainService 封装（30 边改写涉及事务语义，须逐处评估）；或部分收敛（高频变更面优先）；不在融合计划（T-ACCESS-032~041）内，收敛任务启动时从看板计数器取号。

## 已收敛（终态索引，一行一条；详情在关联任务卡/decision-registry）

| Q-ID | 标题 | 收敛形态 | 关联 | 收敛日期 |
|---|---|---|---|---|
| Q-010 | SystemConfigMapper.selectByTenantId 零消费死方法 | closed（随 2026-09-14 轻量清扫批次顺带删除：接口方法 + XML 语句；全仓零调用 T-ACCESS-037 已双轨核实，删除后 SystemConfigAppServiceImplTest 10/10 绿；无任务卡载体，登记口径即顺带删） | —（2026-09-14 归档清扫批次，见 archive/2026-09-14/README.md 批次二） | 2026-09-14 |
| Q-005 | 权限视图/排查页删除后新形态重做 | closed（重复——任务层已有安排载体：T-FE-043 随卡归档「新形态另立任务」+ T-PERM-059 定案③「另立任务」；2026-09-13 用户裁定：已有任务载体的事项不登记问题清单，重做启动时从看板计数器取号） | T-PERM-059（done）、T-FE-043（cancelled，已归档） | 2026-09-13 |
