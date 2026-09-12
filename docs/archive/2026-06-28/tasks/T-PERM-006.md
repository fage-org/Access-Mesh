---
doc_type: task
id: T-PERM-006
title: Gateway 订阅 perm:invalidate topic 清理本地 INTERFACE_SNAPSHOT
status: done
plan: docs/archive/2026-06-28/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
  - docs/design/services/gateway.md#主动失效-T-PERM-006
depends_on: [T-PERM-018]
blocks: [T-GW-005]
acceptance:
  - "广播契约 PermInvalidateEvent 下沉到 perm-common，permission-center 发布端与 Gateway 订阅端共享同一事件结构"
  - "permission-center 发布端通过 StringRedisTemplate.convertAndSend(\"perm:invalidate\", json) 发布 JSON，发布失败仅 WARN，事务提交后 TTL 兜底"
  - "Gateway 通过 ReactiveStringRedisTemplate 订阅 perm:invalidate，收到消息后反序列化并调用本地 interfaceSnapshotCache 失效器"
  - "本地失效粒度：serviceCodes 非空按 tenant+serviceCode 清；userIds 非空按 tenant+userId 清；仅 roleIds 非空时按 tenant 级安全清理"
  - "PermissionFilter 与失效器共用 InterfaceSnapshotCacheKeys，避免缓存 key 解析/构造漂移"
  - "单元测试覆盖 serviceCodes/userIds/roleIds-only/malformed key/malformed message/发布 JSON"
  - "mvn -pl gateway,permission-center -am -Dtest=InterfaceSnapshotCacheInvalidatorTest,PermInvalidationSubscriberTest,PermInvalidationPublisherTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test 通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-06-27
---

# T-PERM-006 — Gateway Redis 广播订阅器

## 背景

T-PERM-018 已将 permission-center 侧 `INTERFACE_SNAPSHOT(L2)` / `permissionVersion` 移除，并扩展事件载荷 `serviceCodes`，但 Gateway 本地 Caffeine `interfaceSnapshotCache` 仍只能靠 30s TTL 自然过期。API mapping / 资源 / syncInterfaces / 条件规则变化会改变 `interface-snapshot` 构建结果，需由 Gateway 主动订阅 `perm:invalidate` 后清本地快照闭环。

## 决策

- 广播契约迁入 `perm-common`：`cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent`，避免 permission-center 私有包 DTO 被 Gateway 依赖。
- 发布格式改为 JSON 字符串：permission-center 使用 `StringRedisTemplate.convertAndSend`，Gateway 使用 `ReactiveStringRedisTemplate.listenTo`。这样避免 Redisson 对象 pub/sub codec 与 Spring Reactive Redis 订阅格式不一致。
- Gateway 不读取权限库，不做 roleId → userId 反查。仅 `roleIds` 非空事件按租户级安全清理，牺牲粒度换正确性；`serviceCodes` / `userIds` 存在时使用更精细维度清理。

## 实现摘要

- `perm-sdk/perm-common/.../event/PermInvalidateEvent.java`：共享事件 record。
- `permission-center/.../cache/PermInvalidationPublisher.java`：发布 JSON 到 `perm:invalidate`。
- `gateway/.../cache/PermInvalidationSubscriber.java`：Gateway reactive Redis 订阅器。
- `gateway/.../cache/InterfaceSnapshotCacheInvalidator.java`：按事件维度清本地 Caffeine。
- `gateway/.../cache/InterfaceSnapshotCacheKeys.java`：缓存 key 构造/解析单源，`PermissionFilter` 改为复用。

## 验证

```bash
mvn -pl gateway,permission-center -am \
  -Dtest=InterfaceSnapshotCacheInvalidatorTest,PermInvalidationSubscriberTest,PermInvalidationPublisherTest \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：8 tests，0 failures，构建成功。

另运行：

```bash
mvn -pl gateway,permission-center -am -DskipTests compile
```

结果：构建成功。
