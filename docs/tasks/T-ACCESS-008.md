---
doc_type: task
id: T-ACCESS-008
title: 统一缓存并实现多实例失效及30秒安全边界
status: proposed
plan: docs/plans/access-service-merge-plan.md
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
  status: pending
last_updated: 2026-08-12
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

（待实施后填写。）
