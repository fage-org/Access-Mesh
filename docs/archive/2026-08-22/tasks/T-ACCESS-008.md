---
doc_type: task
id: T-ACCESS-008
title: 统一缓存并实现多实例失效及30秒安全边界
status: done
plan: docs/archive/2026-08-22/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#7-缓存与多实例一致性
  - docs/design/project-rules.md#12-缓存规范
  - docs/design/permission-center-v3.5-design.md#72-缓存一致性总线
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-003
  - T-ACCESS-005
blocks: []
acceptance:
  - "access-service 内所有业务缓存使用唯一 CacheService，catalog code 按领域采用 admin:/perm:/access: 前缀；独立部署的 Gateway 使用自身唯一 CacheService 并保留 gw: 前缀"
  - "CacheCatalogEntry、CacheProperties、CombinedL1L2Store、RedissonBucketStore 与 CaffeineLocalCacheStore 的 TTL 统一为 java.time.Duration 并支持秒级精度；YAML 使用 15s/5m 等 Spring Boot Duration 文法"
  - "全仓迁移并删除 l1TtlMinutes/l2TtlMinutes/l1-expire-minutes/l2-ttl-minutes 及分钟换算，不保留兼容别名，不允许业务侧硬编码 TTL"
  - "现有 catalog、YAML 和缓存测试全部迁移且原分钟配置保持等价时长；新增 10 秒与 15 秒 Caffeine/Redisson 单条及批量过期测试"
  - "access-service 内所有可能影响接口权限快照的 catalog 使用 L2_ONLY，TTL 均不超过10秒且不创建授权 L1"
  - "CacheService 与底层 Store SPI 的单条/批量 put 支持单次有效 TTL，并强制不超过 catalog TTL；普通缓存默认仍使用 catalog TTL，业务侧不得绕过 CacheService 或直接操作 Redis/Caffeine"
  - "每次授权 L2 miss 在数据库读取事务或快照查询前记录单调时钟起点；回填只使用读取起点加 catalog TTL 所剩余的 TTL，剩余 TTL≤0 时不写入，单条、批量、并发合并和重试不得重置起点"
  - "权限缓存不可用时 access-service 绕过缓存查询数据库；无法得到可信结果时 fail-closed"
  - "Gateway 权限快照使用 L1_ONLY 且 TTL 不超过15秒；删除 gateway.permission.fail-mode 配置及 open、stale-allow 分支，权限回源失败固定 fail-closed"
  - "Gateway 增加不超过5秒的权限快照加载全链路墙钟硬截止时间，覆盖服务发现/负载均衡、连接、请求发送、access-service处理、响应读取/解码及失效竞争触发的重试；现有连接/响应分段超时不得替代总截止时间"
  - "快照加载超过5秒时不得写入 Gateway 缓存并固定返回503；同一授权请求内的所有尝试共享同一截止时间，不得因重试重新计时"
  - "分别强制校验上游授权 L2 TTL≤10秒、Gateway 快照回源全链路截止时间≤5秒、Gateway 权限 L1 TTL≤15秒，配置超限时启动失败；第一阶段固定10秒+5秒+15秒，总预算不超过30秒，且不新增 permission revision、版本号或 validUntil"
  - "回写 gateway.md 的“快照失效标记与订阅恢复”“配置项”“失联兜底模式”“监控指标”：补充5秒全链路截止时间，删除 open/stale-allow、stale store、stale-grace 及其指标和告警，仅保留失效代际、回源并发防护、订阅重连全量清空等仍有效能力"
  - "核对 T-GW-001/003/004/005 与 T-PERM-008 的历史完成语义；任务保持 done 作为历史事实，废弃的可切换 fail-mode、open 和 stale-allow 范围由本任务实现和设计回写取代"
  - "事务提交后清理共享 L2并广播 Gateway/普通 L1 失效；回滚不失效"
  - "双实例测试覆盖正常广播、广播丢失、Redis 短暂故障、权限撤销，以及上游L2接近10秒过期并注入接近5秒全链路延迟后被Gateway回填15秒的边界，证明最坏陈旧窗口不超过30秒；另覆盖回源超过5秒时不写缓存并返回503"
  - "闩锁并发测试覆盖旧授权读取开始后发生权限事务提交与失效、随后旧读取完成并尝试回填；验证只能写入剩余 TTL、预算耗尽时不写入，且批量与重试不会重新获得完整10秒"
  - "指标能够区分 L1/L2 命中、回源、失效失败和 fail-closed"
  - "按最终实现回写 project-rules 缓存规范，并同步更新 .claude 与 .agents 下 dual-layer-cache-framework 两份 skill，校验镜像内容一致"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-21
---

# T-ACCESS-008 统一缓存并实现多实例失效及30秒安全边界

## 背景

原双层缓存只清理当前 JVM 的 L1。归并后的授权读取不引入版本号，必须用共享 L2、短 TTL 和故障拒绝限制风险。

## 范围

- 收敛 catalog、缓存调用方式以及受 catalog 上限约束的单次剩余 TTL 写入能力。
- 将统一缓存框架及所有存量配置硬迁移为 Duration 秒级精度。
- 调整授权缓存模式和 Gateway 兜底策略；在包含初次回源、失效代际校验及其重试的组合响应流最外层施加一次 5 秒总截止，禁止把 `timeout` 放在单次尝试内导致重试重新计时。
- 建立双实例和故障场景验证。

## 非目标

- 不新增 `permission_revision`、快照版本号、`validUntil` 或 access-service 授权 L1。

## 完成记录

**done（2026-08-21）**。四项用户设计决策（2026-08-21）：① 仅快照链路 6 目录切 L2_ONLY≤10s（OPERATION_PERMISSIONS_BY_TYPE 保持 L1_L2，靠普通 L1 跨实例失效广播）；② 读取令牌辅助 + Duration 重载；③ Gateway 失效粒度用户级精确 + 租户级兜底；④ Gateway TTL/容量配置统一到 catalog + `accessmesh.cache` 覆盖。

**common/cache 框架**：

- `CacheCatalogEntry`/`CacheProperties` TTL 硬迁移 `Duration`（Builder `l1Ttl`/`l2Ttl`；YAML 键 `l1-ttl`/`l2-ttl`，Spring Duration 文法）；分钟字段与换算全删、无兼容别名。
- SPI 与三 store 新增 `put/putBatch(..., Duration effectiveTtl)`：L2（RBucket）精确按单次 TTL 写入；L1（Caffeine 固定过期无条目级 API）仅当 catalog L1 TTL ≤ 有效 TTL 预算时写入，否则跳过（宁可不缓存不超预算）；所有层强制 cap catalog 有效 TTL、≤0 不写。
- `CacheService.beginRead(catalog)` 产生 `CacheReadToken`（单调时钟起点）；`put(token,...)`/`putBatch(token,...)` 自动写「读取起点 + catalog 有效 TTL」剩余 TTL（含 YAML 覆盖值），预算耗尽不写。`DefaultCacheService` 新增可注入单调时钟的测试构造。
- 普通 L1 跨实例失效广播：`RedissonCacheInvalidationBroadcaster` 经 RTopic `accessmesh:cache:l1-invalidate` 广播 `{catalogCode, tenantId, keys|all}`；L1_L2 目录 evict/evictAll 时发布（事务场景即提交后，回滚不失效），各实例订阅后 `CombinedL1L2Store.invalidateLocalL1/All` 清本地 L1；广播失败不抛异常，计 `cache.invalidate.failures{type=broadcast|subscribe|listen}`，L1 TTL 兜底。
- 指标补齐：`cache.puts`（回源回填，layer tag）、`cache.invalidate.failures`；L1/L2 命中已有指标保留。

**access-service**：

- `PermCacheCatalog`：6 个快照链路目录（EFFECTIVE_ROLES/ROLE_PERM_SNAPSHOT/TYPE_VALUE/TYPE_CODE/CONDITION_RULES/ROLE_MUTEX_RULE）→ L2_ONLY + `Duration.ofSeconds(10)`，不创建授权 L1；OPERATION_PERMISSIONS_BY_TYPE 保持 L1_L2（60m/120m Duration 化）；ORG_VISIBILITY 保持 L2_ONLY 60s。AdminCacheCatalog Duration 化（等价时长）。
- 授权读路径接入 ReadToken（DB 读取前 beginRead，回填剩余 TTL，批量共享令牌）：`SubjectDomainServiceImpl.resolveEffectiveRolesBatch`、`PermQueryEngine.loadRolePermEntriesWithCache`、`TypeResolutionServiceImpl`（TYPE_VALUE/TYPE_CODE）、`PermissionConditionDomainServiceImpl`、`PermissionConflictDomainServiceImpl`。
- `PermCacheBoundaryValidator`（InitializingBean）：6 目录有效 L2 TTL（含 YAML 覆盖）>10s 或非 L2_ONLY → 启动失败。
- 删除 application.yml 无绑定死配置 `perm.cache.l1/l2`（expire-minutes/ttl-minutes）。

**Gateway**：

- 删除 `FailMode`/`StaleEntry`/`CacheConfig` 裸 Caffeine Bean/stale store/fail-mode 配置与 open、stale-allow 全部分支及指标（`PermissionFilterFailModeTest` 随之删除）；权限回源失败固定 fail-closed 503。
- 快照缓存迁统一 `CacheService`（L1_ONLY，`gw:interface-snapshot`，catalog 15s/50000；`accessmesh.cache.catalogs."[gw:interface-snapshot]".l1-ttl/l1-maximum-size` 运维覆盖）；identifier = `subjectTypeCode:userId:serviceCode`，marker/in-flight 用租户限定键防跨租户串扰。
- 失效粒度（用户决策③）：仅 userIds → 用户级精确（本地跟踪索引 tenantId:identifier→userId 枚举）；serviceCodes/仅 roleIds → 租户级 evictAll + globalEpoch 递增；订阅重连 catalog 级跨租户全清（二轮复评起，见下）。保留失效代际（LoadToken）与 per-key in-flight 去重；孤立标记 60s 清理。
- 5 秒全链路硬截止 `gateway.permission.snapshot-load-deadline`（默认/上限 5s）：`Mono.defer` 一次确定截止时刻，组合流（含失效竞争重试）整体 `.timeout`，重试原样传递 deadlineNanos 不重新计时；写入缓存前再校验截止，超时不写缓存 + `DeadlineExceededException` 503 + `reason=deadline_exceeded` 指标。WebClient 分段超时（连接3s/响应5s）保留但不替代总截止。
- `GatewayCacheBoundaryValidator`：快照有效 L1 TTL>15s 或 deadline>5s/≤0 → 启动失败。

**测试**（全量 BUILD SUCCESS；终态 common 58 / gateway 59 / access-service 579 含 27 个 Docker 门控跳过 / example 5）：

- common：`CacheEffectiveTtlTest`（cap/≤0 不写/剩余 TTL/YAML 覆盖预算/批量共享起点）；`CaffeineSecondsPrecisionTest`（10s/15s 单条+批量 policy 精确断言 + 亚秒 TTL 真实过期）；`RedissonSecondsPrecisionTest`（10s/15s 单条+批量 set/setAsync Duration 参数、cap、预算耗尽不写）；`CacheInvalidationBroadcasterTest`（消息契约/订阅清 L1/发布失败指标）。
- access-service：`PermCacheCatalogBoundaryTest`（6 目录 L2_ONLY 10s、普通目录模式、启动校验通过/超限失败、10+5+15=30 预算）；`StaleBackfillLatchTest`（闩锁：旧读取开始→提交失效→旧读取完成回填仅剩 7s、预算耗尽不写、批量共享起点）；`DualInstanceCacheInvalidationTest`（内存 Redis 双 CacheService 实例：正常广播/广播丢失 TTL 兜底+失效失败指标/Redis 故障绕过（L1 命中仍可用、L1 miss 返 null 不抛异常）/权限撤销跨实例一致/批量共享 L2/上游 L2 剩余 TTL 锚定读取起点——压缩目录演示 30s 边界上游不变式）；`AuthorizationCacheBypassToDbTest`（缓存故障绕过 DB、DB 空不放大异常）。
- gateway：`PermissionFilterTest` 重写（回源写缓存+unmark+track、token 失效 503、重试一次、二次失效 503、命中但标记→驱逐回源、截止超时 503 不写缓存、重试共享截止不重置、截止内完成正常）；`PermissionFilterMetricsTest` 重写（unreachable 双 source、closed denied、deadline_exceeded；open/stale 断言随实现删除）；`InterfaceSnapshotCacheInvalidatorTest` 重写（用户级精确只清该租户该用户、服务级/角色级租户兜底、空载荷 no-op、clearAll 全租户）；`GatewayCacheBoundaryValidatorTest`（15s/5s 超限与零值启动失败）；`GatewayApplicationConfigTest` 全上下文含新校验器与 CacheService 通过。

**历史任务核对**：T-GW-001/003/004/005 与 T-PERM-008 保持 done 作为历史事实；其废弃范围（可切换 fail-mode、open、stale-allow、stale store、stale-grace 及相关指标告警）由本任务实现与 gateway.md 回写取代，看板登记行已更新为已回写。

**设计回写**：`docs/design/services/gateway.md`（缓存模型/主动失效/失效标记与回源防护/配置项/固定 fail-closed+5s 截止/监控指标与告警）、`docs/design/project-rules.md` §12.0/12.3（Duration、单次有效 TTL、剩余 TTL 回填、30s 边界、L1 广播、新禁止项）、`.claude` 与 `.agents` 双 skill 镜像同步一致（v3.0.0）、AGENTS.md skill 摘要、access-service-architecture.md §7 增补落地实现注记。

**复评修复（2026-08-21，AI 评审 2 P1 + 4 P2 全部确认属实并修复）**：

- **P1-1 租户级失效旧回源复活窗口**：`evictTenantWide`/`clearAll` 原先 evictAll 后才递增 globalEpoch，两步之间旧 `LoadToken` 仍有效可写回。修复：先递增代际（作废全部在途 token）再清缓存。回归：`GatewayInvalidationRaceTest`（慢 evictAll 400ms 期间旧回源提交被作废、重试拉取新快照；clearAll 同场景）。
- **P1-2 用户级失效遗漏在途回源**：`evictByUsers` 原先只枚举跟踪索引（track 发生在写入后），首次回源在途 key 不被标记，撤权后旧回源正常提交。修复：候选 = 跟踪索引 ∪ 在途回源注册表（同租户同用户）。回归：首次加载期间仅 userIds 撤权事件 → 旧回源作废重试。
- **P2-3 L2 命中回填 L1 放大单次有效 TTL**：`CombinedL1L2Store` L2 命中原先无条件按完整 catalog L1 TTL 回填，短有效 TTL 条目被放大。修复：回填前校验 `remainTimeToLive` ≥ catalog L1 TTL，否则不回填（单条与批量）；查询失败跳过回填不影响返回值。
- **P2-4 跟踪索引配置漂移与兜底语义**：索引改经 `CacheProperties` 取 `gw:interface-snapshot` 有效 TTL/容量（跟随 YAML 覆盖）；兜底语义按用户决策（2026-08-21）取 **TTL 兜底**——索引缺失（毫秒级定时器偏差）与广播丢失同等语义，由快照 ≤15s TTL 兜底在 30s 预算内，不降级租户级清理；gateway.md/架构注记同步为如实口径。
- **P2-5 L2_ONLY 全量失效失败误报成功**：`RedissonBucketStore.deleteBatchKeys` 不再吞异常，`evictAll` 失败计 `cache.invalidate.failures{type=evict}` 并按失败记日志（授权 L2_ONLY 目录失效故障可经任务要求的失效失败指标识别）。
- **P2-6 组合边界验收**：新增 `SnapshotSafetyBoundaryTest` 真实时钟串联「上游 L2 10s（接近过期仍可读、按绝对时刻死亡）+ 4.5s 全链路回源延迟（距 5s 截止留 ~0.5s 余量，二轮复评放宽）+ Gateway 15s L1 回填」，断言最坏陈旧窗口 = 回填时刻 + 15s ≤ T0+30s；代际竞态由 `GatewayInvalidationRaceTest` 真实 invalidator 覆盖。
- **质量备注**：`InvalidationMarker` 类注释（stale-allow 残留口径）与 `CombinedL1L2Store` 单次有效 TTL Javadoc（"条目级 TTL 覆盖"错误描述）更正为实际行为。

**第二轮复评修复（2026-08-21，3 P2 + 2 P3 全部确认属实并修复）**：

- **P2-1 重连 clearAll 非真正全量**（用户决策：加跨租户全清 API）：`CacheService`/双 SPI/三 Store 新增 `evictAll(catalog)` catalog 级跨租户全清（L1 本地按 catalog 前缀；L2 SCAN `*:code:*`），Gateway `clearAll` 改用之——不再依赖跟踪索引推导租户（索引与主缓存独立淘汰会漏清），"订阅重连全量清空"字面成立。回归：`CatalogWideEvictAllTest`（跨租户清理/其他目录不受影响/SCAN 模式与删除断言/失败计指标）+ `InterfaceSnapshotCacheInvalidatorTest.clearAll_shouldEvictTenantsMissingFromTrackingIndex`（索引丢失租户仍被清空）。
- **P2-2 单条/批量失效未计入失效指标**：`RedissonBucketStore.evict/evictBatch` 失败除 `cache.l2.errors` 外同时计入 `cache.invalidate.failures{type=evict}`——授权 L2_ONLY 目录主用的两种失效方式现在可经失效失败指标区分。
- **P2-3 `accessmesh.cache.default.*` 无法绑定**：字段 `defaultConfig` 的规范绑定路径为 `accessmesh.cache.default-config.*`；全仓（CacheProperties Javadoc、project-rules §12.0/§12.3、双 skill、access-service yml 注释）统一更正为 `default-config.*`。新增 `CachePropertiesBindingTest` 锁定绑定契约（default-config 全局默认与 effective 回退、catalogs 带冒号键覆盖、`default.*` 不产生绑定防文档回归）。
- **P3-1 L2 命中回填门控缺直接回归**：新增 `CombinedL1L2BackfillGatingTest`——剩余寿命不足不回填（重读仍打 L2）、充足则回填（重读命中 L1）、TTL 查询异常跳过回填但正常返回值、批量按 key 门控。
- **P3-2 组合测试截止余量过小**：`SnapshotSafetyBoundaryTest` 延迟调整为 9.2s 等待 + 4.5s 回源（距 5s 截止 ~0.5s、距 30s 预算 ~1.3s 余量）；临界截止行为已由 PermissionFilterTest 截止用例覆盖。
- **Javadoc 残留**：`InvalidationMarker.retainLiveKeys` 注释 stale 措辞更正。

**第三轮复评修复（2026-08-21，1 P2 + 3 P3 文档一致性）**：

- **P2 catalog 级全清误删其他目录**：`*:code:*` 宽松 SCAN 模式无法约束 catalogCode 紧跟租户段，其他目录 identifier 内嵌本目录编码的键（如 `1:test:other:x:test:catalog-wide:y`）会被误删。修复：`CacheKeyUtil.belongsToCatalog` 改为按完整键结构精确匹配（首段数字租户ID + catalogCode 紧随其后以分隔符结束，精度与租户级 SCAN 前缀一致），两个 Redis Store 的 catalog 级全清在删除前精确过滤。回归：`CacheKeyUtilTest.belongsToCatalog_shouldMatchOnlyKeysOfThatCatalog` + `CatalogWideEvictAllTest` 碰撞键用例（L1 与 Redisson SCAN 各一，碰撞键存活/不被删除）。
- **P3 文档一致性**：`InterfaceSnapshotCacheInvalidator` 类注释失效能力描述更新（含 catalog 级全清）；任务卡完成记录的旧重连方案/测试数量/4.8s 延迟同步为终态；`SnapshotSafetyBoundaryTest` 耗时注释更正（约 13.7 秒）。
