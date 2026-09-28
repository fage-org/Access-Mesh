---
doc_type: task
id: T-ACCESS-059
title: （ADM-T04）新端点、快照与 SDK/网关链路
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §8.2/§8.4
  - docs/design/access-service-api-contract.md §25.1/§25.2/§25.5/§25.6/§25.8
  - docs/design/schema/access-service.sql
  - docs/design/services/gateway.md
  - docs/design/engine/implementation.md §3.8
  - docs/design/engine/core-flows.md
  - docs/design/service-authentication.md §3.5
  - docs/design/access-service-architecture.md §7
  - docs/design/architecture.md
  - docs/design/extension-guide.md
depends_on:
  - T-ACCESS-057
  - T-ACCESS-058
blocks:
  - T-ACCESS-060
acceptance:
  - "interface-admission / interface-admission-snapshot 版本化端点+DTO（InterfaceAdmissionSnapshot：schemaVersion/tenantId/subject/serviceCode/generatedAt/expiresAt/configGeneration/routes[]/operationCandidates[]/authorizationStage/finalCheckRequired）；PermissionClient 显式可信服务模式调用；新模式失败不回落旧 API:ACCESS"
  - "网关本地判定序：校验模式/版本/时效→完整路由匹配与歧义检测（N14/N15：多匹配异要求 AMBIGUOUS 阻断、无注册拒绝且 ALL 不放行未注册）→唯一要求→评该要求条件分支（无条件或通过即 MAY_ENTER；N11：条件不可下发且无其他通过分支时本地回源、在线按相同规则求值；其余拒绝）；坏条件显式不可用不变无条件；fail-closed 沿现行"
  - "N12：同事实/定义/时刻/IP 下本地投影与在线准入一致；N21：快照构建中配置代次改变废弃重建（configGeneration 拍板限定语义）、旧在途不覆盖新代次；N22：新旧 schema/命名空间隔离；N28：权限服务自身认证边界独立、无准入递归、缺映射不自动公共"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-28
---

# T-ACCESS-059 （ADM-T04）新端点、快照与 SDK/网关链路

## 背景

设计 §8.2/§8.4（报告临时编号 ADM-T04）。路由匹配从该服务完整已启用路由集取全部命中，不能按用户权限挑较弱规则；已确定公共/认证白名单维持独立、无 requiredPermission 不自动等于公共路由。

## 范围

- access-service 端点与快照构建（候选从新 OPERATION_ADMISSION+FACTS 构建，绝不经旧 GRANT_LIST 全集合 PERM_MUTEX；原始 GrantFact 不去重合并、最终投影按 type-operation+条件身份+候选类别归并）；SDK PermissionClient 调用方法；网关本地缓存与回源。

## 非目标 / 遗留

- ~~Q-040 对齐注记~~ 已落地（凭证+网关内部密钥并存，见当前口径④）。
- 失效/TTL 边界在 T-ACCESS-060；业务侧最终检查与切换在 T-ACCESS-061；旧协议退役在 T-ACCESS-062。

## 当前口径（2026-09-28 六项用户拍板与实现形态）

1. **无迁移期统一上线**：全部服务（含 access-service 自身）一次切 OPERATION_ADMISSION，网关单链无模式发现；DDL 与 service-config/save 创建缺省改 OPERATION_ADMISSION；存量库经迁移脚本 `docs/ops/operation-admission-migrate-059.sql`（config_generation 列、全量模式切换、固定图映射补操作引用、query-scopes 种子映射停用、bootstrap 角色补 CONDITION:VIEW/ADMIN_ORG_TREE_CONFIG:VIEW）。迁移期「按服务选择」口径废止（契约 §25.1 同批修订）。
2. **网关直接切新链删旧链**：PermissionFilter 整体改写（准入快照+`InterfaceAdmissionMatcher` 四态+`interface-admission` 回源），网关侧旧链消费（interface-snapshot 拉取/旧 matcher/check-interface 调用/旧缓存目录）删除；服务端旧端点保留至 T-ACCESS-062；回退=回滚网关版本。缓存目录 `gw:interface-admission-snapshot`（L1_ONLY 15s，与旧目录 schema 命名空间隔离 N22）；TTL 沿 15s/5s 既有预算形态，完整边界推导与启动校验归 T-ACCESS-060。
3. **access-service 自身端点也接准入**（选项 B）：固定图 105 条映射按「各端点服务层 QueryGate 门禁同码」补业务操作引用（开放读端点 condition/list、org-tree-config/page 按所属类型 VIEW 绑定并同批补授；job 写三档绑码不授——网关 403 与现行服务层 403 终端一致；`auth/query-scopes` M2M 种子映射移出固定图停用）；bootstrap 完整性检查新增模式漂移与映射缺操作引用两条 fail-fast。N28「认证边界独立」以服务端 M2M 身份链+服务层门禁独立承载（网关不设跳过分支）。
4. **端点身份=凭证+网关内部密钥并存**：两端点入 `M2mCredentialEndpoints` 白名单（Q-040 收敛方向：运行时查询族首批凭证化端点）；凭证调用按 sync-v2 先例约束 serviceCode=凭证所属服务；网关沿用 X-Internal-Secret 平台信任域形态；旧密钥+自报 X-Service-Code 的纯服务调用按 URI 拒绝 403（RequestContextInterceptor，sync-v2 先例）。
5. **configGeneration=service_config.config_generation 计数列**：映射写路径（ApiMappingWriteDomainService.saveAll 单点覆盖手工/同步/bootstrap、FULL 清理删除、removeApiMappingsByIds）与模式/启停切换同事务 +1；快照构建以独立语句 `selectConfigGeneration` 复读（绕开 MyBatis 会话缓存同语句假读），有限重试（3 次）后按技术故障失败关闭。
6. **SDK 只加 PermissionClient 两方法**（Filter/Matcher 落网关侧；SDK 决策拍板——业务服务本该做最终检查 auth/check，准入本地判定进 SDK 易诱导误用）。

实现落点：`PermissionAdmissionAppService(Impl)`（在线判定：完整路由集匹配→同要求去重/异要求 20070/悬空 20071→QueryItem.admission；快照：全部去重要求一次 execute 多 admissionFacts item，N13 共享读算）、`InterfaceAdmissionSnapshotAssembler`（批量操作/类型反查、候选按 type-operation+条件身份+候选类别归并、条件内联共享抽取的 `GatewayPushableRules`）、网关 `PermissionFilter`/`InterfaceAdmissionMatcher`/`PermissionClient` 重写、SDK `PermissionFeignClient` 两方法（契约冻结清单 17→19）。

## 验收对照

- **N11**：`InterfaceAdmissionPgIT.onlineAdmissionShouldReturnNoRoleAndNoCandidateAndConditionNotMet`（条件 IP 白名单不通过→CONDITION_NOT_MET、通过→MAY_ENTER，在线按相同规则求值）＋网关 `InterfaceAdmissionMatcherTest`（notLocallyEvaluable/inlinedRulesMissing→FALLBACK；解析失败=不可用分支回源）＋ E2E ⑥/⑧（条件不在场，链路窗口语义同验）。
- **N12**：matcher 与服务端共用 `ConditionEvalUtils` 同规则算法；`InterfaceAdmissionPgIT` 在线判定与 `InterfaceAdmissionMatcherTest` 本地判定对同事实（IP 白名单 10/8）同结果；E2E 双链真实服务对拍（网关本地放行/拒绝与授权变更一致收敛）。
- **N14**：PgIT `onlineAdmissionShouldRejectAmbiguousRequirementsAsConfigFault`（20070）＋ matcher `ambiguousRequirement_configFault_n14`（网关本地 503）。
- **N15**：PgIT `onlineAdmissionShouldDenyUnregisteredPathEvenForAllHolder`（ALL 不放行未注册）＋ matcher `unregisteredPath_denied`/`sameRequirementDeduped_multiMatch`。
- **N21**：PgIT `snapshotBuildShouldDiscardAndRebuildWhenGenerationChangesMidBuild`（SpyBean 代次序列 5→6→6，废弃重建以新代次返回）＋ `configGenerationShouldIncrementOnEveryMappingWritePath`（save/update/remove 逐写入口 +1 回归锁；saveAll/FULL 由 058 既有 AdmissionMappingPgIT 路径覆盖 bump 调用点）。
- **N22**：新 DTO `InterfaceAdmissionSnapshotResp`（schemaVersion=1）与旧 `InterfaceSnapshotResp` 结构/目录命名空间互不复用；matcher 未知 schemaVersion→CONFIG_FAULT；网关旧链消费删除。
- **N28**：两端点为 M2M 身份链（凭证/内部密钥），权限服务自身端点经网关时走统一准入（自身映射已补操作引用，无递归——准入端点无映射不经网关路由链）；缺映射→API_NOT_REGISTERED 拒绝（PgIT 断言），不自动公共。
- 收口全量 `mvn test -T 1C` BUILD SUCCESS 零失败（网关 121 含新增过期缓存回归锁、access 单测轨 1598、容器组 425 零跳过、E2E 16——BasicRoleGrantVerticalSliceE2EIT 8/8 + ExampleProtectedApiE2EIT 8/8 真实三服务双垂直切片；日志 `.tmp-t-access-059-full2.log`）。首轮全量曾因并行负载下 Docker 环境瞬时不可用跳过容器 fork（418 skipped），Docker 恢复后 access-service 模块整轨重跑 425 项零失败定性，最终全量复跑全绿。

## 完成记录

- 2026-09-28：六项拍板（无迁移期/切链删旧链/自身端点接准入/凭证+旧密钥并存/计数列/SDK 仅客户端方法）落地；DDL+迁移脚本 059、固定图 105 路由操作引用与两笔补授、代次 bump 四挂点、两端点+快照装配、网关新链四态+SDK 两方法、E2E 双切片同批改造（sync-v2+业务类型+实例授权/USER:VIEW 实例授权）。
- 双轨评审处置：代码轨 P1×1——网关缓存 TTL 与服务端 expiresAt 起点差（=回源延迟）使缓存尾部「缓存仍在、快照已过期」曾被判 CONFIG_FAULT 硬 503；修复为缓存命中路径过期预检（`isFresh`，过期按 miss 驱逐重载），新增回归锁 `shouldReloadInsteadOfConfigFault_whenCachedSnapshotExpired`（红跑实证：注释预检后旧实现下失败）。P3 若干——SnapshotAssembler 冗余 import、yml/catalog/metrics/skill 六处旧目录代码与 check_interface 指标名残留同批清理；装配器 factsByRequirement 键错位（item key vs 要求键）实现期自查修复。存疑登记：`gateway.permission.unregisteredPolicy` 为本卡前既有零消费配置（非本批引入，未动——建议随 062 清理批处置）。
- 文档轨：契约 §25.1/§25.2/§25.5（无迁移期口径、身份形态、代次载体、网关消费形态、解析失败=不可用分支）＋设计 §8.4 落地注＋gateway.md 快照章整节重写＋engine/implementation §3.8 两行＋core-flows/architecture/extension-guide/access-service-architecture/overview 链路口径＋service-authentication §3.5 白名单扩展＋schema 注释与迁移脚本同步＋permission-query-pipeline skill 双副本（端点落地口径）。
