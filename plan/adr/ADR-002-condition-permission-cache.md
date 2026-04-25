# ADR-002：条件权限缓存策略

**状态**：✅ 已采纳

**日期**：2026-04-24

**上下文**

条件权限（DATE_RANGE、TIME_RANGE、IP_WHITELIST 等）需要在鉴权时评估运行时参数。
问题：缓存权限快照时条件权限如何处理？两种候选方案：
- 方案 A：缓存不包含条件权限，鉴权时额外 DB 查询
- 方案 B：条件权限存入缓存，标记 `hasCondition=true`，鉴权时评估

**决策**

采用方案 B：条件权限存入 Redis 第二份缓存 `perm:role:perms`，标记 `hasCondition=true`。

**评估流程**：

1. 权限中心鉴权时读 Redis 两份数据（用户角色 + 角色权限）
2. 识别到 `hasCondition=true` 的条目 → 查 `permission_condition` 表
3. 使用请求 context（clientIp、timestamp）评估条件规则
4. 相同 `condition_id` 在同一次请求内复用计算结果

**后果**

- 条件权限参与 Gateway 接口级鉴权
- 条件评估在权限中心内部完成，Gateway 只接收 `allowed/denied` 结果
- 缓存 TTL 为 5 分钟，变更即时失效
