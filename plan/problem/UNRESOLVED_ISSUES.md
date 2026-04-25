# 待确认的设计问题

本文档记录尚未解决的设计问题，待后续确认。

---

## ~~问题 8：组织模型映射到权限中心~~ ✅ 已解决

### 解决方案

去除 `role_group` 和 `role_group_role` 表，直接在 `abstract_role` 中通过 `parent_id` 实现树形结构。

**角色类型行为差异**：
- ORG(1)：组织树，同步自 `sys_org`，支持子级
- POSITION(2)：平铺，分配时 `user_role.relation_id` 记录所属组织，决定数据权限范围
- PERSONAL(3)：平铺，每用户1个
- GROUP_ROLE(5)：树形，不直接配置权限，通过 `extra.basicRoleIds` 额外关联基本角色
- BASIC_ROLE(6)：平铺，承载实际权限配置

**POSITION 组织绑定**：`user_role` 表新增 `relation_id BIGINT` 字段。POSITION 在 `admin-service` 记录所属组织范围（业务约束），分配给用户时 `user_role.relation_id` 存储 orgId，决定数据权限范围。

**相关变更**：
- SQL 表数从 20 张减少到 18 张
- `abstract_role` 新增 `parent_id` 字段
- `user_role` 新增 `relation_id` 字段，唯一约束调整为 `(tenant_id, abstract_user_id, target_type, target_id, COALESCE(relation_id, 0))`
- `target_type` 标记角色类型（ROLE/ORG/POSITION/PERSONAL/GROUP_ROLE），不再区分 ROLE/GROUP

---

## ~~问题 9：条件权限快照处理不一致~~ ✅ 已解决

### 解决方案

采用方案 B：条件权限存入 Redis 第二份缓存 `perm:role:perms`，标记 `hasCondition=true`。权限中心鉴权时识别到该标记后，查 `permission_condition` 表评估条件规则。

- 条件权限参与 Gateway 接口级鉴权
- 相同 `condition_id` 在同一次请求内复用计算结果
- 条件评估在权限中心内部完成，Gateway 只接收 allowed/denied 结果

---

## ~~问题 10：分布式事务/事件一致性保证~~ ✅ 已解决

### 解决方案

采用方案 A：**仅 API 同步**，去除 MQ 双通道。

**同步架构**：
- 外部系统（admin-service、example-service 等）调用权限中心 `POST /api/perm/sync/{type}` 接口同步数据
- 幂等保证：权限中心维护同步幂等机制，按 `(source_system, entity_type, external_id, action, version)` 去重
- 乱序处理：每条记录带 `version`（时间戳/版本号），新请求 version <= 已处理的最大 version 时忽略
- 失败处理：通过 `sys_sync_retry` 本地消息表异步重试（详见 ADMIN_SERVICE_DESIGN.md §2.3）
- 权限中心提供失败记录查询和手动重放接口

**外部服务对应调整**：
- **admin-service**：去除与权限中心同步的 MQ 通道，保留 API 同步调用，通过 `sys_sync_retry` 本地消息表保障一致性
- **example-service**：同上，去除 MQ 通道，改用 API 同步

---

## ~~问题 13：StripPrefix 与 resource_api_mapping 协调~~ ✅ 已解决

### 解决方案

`resource_api_mapping.path_pattern` 存储的是 **Gateway 接收到的原始请求路径**（如 `/admin/api/users/list`），不是后端服务的实际路径。Gateway 鉴权时直接用客户端原始请求路径匹配 `path_pattern`。详见 DESIGN.md §8.2。

---

*本文档由 2026-04-24 的设计评审产生，所有问题均已确认并解决。*
