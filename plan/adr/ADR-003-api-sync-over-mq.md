# ADR-003：服务间同步采用 API 调用替代 MQ

**状态**：✅ 已采纳

**日期**：2026-04-24

**上下文**

原始设计采用 RocketMQ 传递用户同步和权限变更通知事件（USER_SYNC、PERMISSION_CHANGE_NOTIFY Topic）。
问题：MQ 引入分布式事务复杂度，需要处理消息丢失、乱序、幂等等问题。

**决策**

采用仅 API 同步方式，去除 MQ 双通道。

**同步架构**：

- 外部系统（admin-service、example-service）调用权限中心 `POST /api/perm/{type}/sync` 接口同步数据
- 幂等保证：按 `(source_system, entity_type, external_id, version)` 去重
- 乱序处理：每条记录带 `version`，version <= 已处理最大 version 时忽略
- 失败处理：admin-service 通过 `sys_sync_retry` 本地消息表异步重试（最大 3 次）

**后果**

- MQ 仍保留在基础设施中（用于未来异步事件如审计日志收集）
- ARCHITECTURE_DESIGN.md 中 RocketMQ Topic 规划已删除
- 外部服务需实现本地消息表保障一致性
