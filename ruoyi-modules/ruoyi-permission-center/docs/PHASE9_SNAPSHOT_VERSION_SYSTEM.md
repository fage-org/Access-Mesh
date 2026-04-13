# Phase 9: 接口快照与版本系统

## 验收状态：已完成

---

## 1. 输入依赖

- Phase 1 的 `permission_version` 表结构与初始化脚本
- Phase 2 的 `resource_api_mapping` / `role_resource_permission` / `user_role` 仓储能力
- Phase 3 的 `PermissionService`、`PermissionVersionService`
- Phase 4 的 `ResourceTypeHandler` 与 `SnapshotAssembler`
- Phase 8 的冲突规则表 `permission_conflict_rule` 与冲突检测逻辑

---

## 2. 输出接口

| 接口路径 | 方法 | 说明 |
|----------|------|------|
| `/api/perm/policy/interface-snapshot` | POST | 查询用户接口权限快照 |
| `/api/perm/decision/interface` | POST | 单次接口权限判定 |
| `/api/perm/version/query` | POST | 查询当前运行时权限版本 |

---

## 3. 实施内容

### 3.1 接口快照组装链

- **查询链路**：`user_role -> abstract_role -> role_resource_permission -> resource_entity -> operation_permission -> resource_api_mapping`
- **去重**：通过 `LinkedHashMap` 按 `capabilityCode|serviceCode|httpMethod|pathPattern` 去重
- **排序**：按 `match_order ASC, id ASC, resource_code ASC, operation_code ASC` 排序
- **冲突过滤**：SQL 层 NOT EXISTS 子查询排除命中冲突规则的授权项
- **条件过滤**：`condition_id IS NULL` 确保仅无条件授权进入快照

### 3.2 快照输出字段

| 字段 | 说明 |
|------|------|
| `tenantId` | 租户ID |
| `subjectKey` | 主体标识 |
| `permissionVersion` | 权限版本号（格式：`{tenantId}-v{versionNo}`） |
| `generatedAtEpochMilli` | 快照生成时间戳 |
| `rules` | 接口权限规则列表 |

### 3.3 接口权限规则字段

| 字段 | 说明 |
|------|------|
| `capabilityCode` | 能力编码（`resourceCode:operationCode`） |
| `serviceCode` | 服务编码 |
| `httpMethod` | HTTP 方法 |
| `pathPattern` | 路径模式 |

### 3.4 版本查询

- 按租户查询当前权限版本号
- 输出 `permissionVersion` 与 `updatedAtEpochMilli`
- 版本号格式：`{tenantId}-v{versionNo}`

### 3.5 快照缓存机制

- **缓存实现**：Caffeine 本地缓存
- **缓存 Key**：`(tenantId, abstractUserId, permissionVersion)`
- **失效策略**：
  - 版本变化后自动失效（新版本创建新缓存条目）
  - 写入后 5 分钟过期
  - 最大容量 10000 条
- **效果**：防止同一用户重复高成本组装

---

## 4. 失败模式

| 场景 | 处理方式 |
|------|----------|
| `tenantId` 或 `abstractUserId` 为空 | 回退到内存权限骨架 |
| 版本查询失败 | 返回默认版本信息 |
| 快照组装失败 | 返回空快照，记录错误日志 |

---

## 5. 冲突过滤逻辑

### 5.1 SQL 层实现

```sql
AND NOT EXISTS (
    SELECT 1
    FROM permission_conflict_rule cr
    INNER JOIN role_resource_permission rrp_other
        ON rrp_other.tenant_id = ur.tenant_id
       AND rrp_other.abstract_role_id = ur.abstract_role_id
       AND rrp_other.resource_entity_id = rrp.resource_entity_id
       AND rrp_other.delete_flag = 0
       AND rrp_other.condition_id IS NULL
       AND (rrp_other.operation_permission_id = cr.first_operation_permission_id
            OR rrp_other.operation_permission_id = cr.second_operation_permission_id)
    WHERE cr.tenant_id = ur.tenant_id
      AND cr.delete_flag = 0
      AND (cr.resource_type_value IS NULL OR cr.resource_type_value = re.resource_type)
      AND (cr.biz_domain_id IS NULL OR cr.biz_domain_id = re.biz_domain_id)
      AND (rrp.operation_permission_id = cr.first_operation_permission_id
           OR rrp.operation_permission_id = cr.second_operation_permission_id)
      AND rrp_other.operation_permission_id <> rrp.operation_permission_id
)
```

### 5.2 冲突规则匹配条件

- 同一租户
- 同一角色
- 同一资源
- 两个互斥操作同时存在
- 冲突规则的资源类型和业务域匹配

---

## 6. 验收记录

### 6.1 定向回归

```bash
mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev \
  "-Dsurefire.failIfNoSpecifiedTests=false" \
  "-Dtest=HybridPermissionKernelServiceTest,DatabaseInterfacePermissionRuleQueryServiceTest,PermissionKernelSnapshotMapperSqlTest"
```

**结果**：Tests run: 14, Failures: 0, Errors: 0, Skipped: 0

### 6.2 关键验证点

- [x] 接口快照仅包含 API 资源（通过 `resource_api_mapping` JOIN 实现）
- [x] `condition_id != null` 的授权不进入快照（SQL 条件 `condition_id IS NULL`）
- [x] 冲突授权不进入快照（NOT EXISTS 子查询）
- [x] 相同版本下快照结果一致（缓存机制）
- [x] 版本变化后缓存失效（新版本创建新缓存条目）
- [x] 快照缓存命中时不重复查询规则

---

## 7. 风险与未决问题

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 快照 SQL 复杂度高 | 高并发下性能压力 | 通过缓存减少查询频率；后续可考虑异步预加载 |
| 冲突规则过多 | NOT EXISTS 子查询性能下降 | 建议控制冲突规则数量；按资源类型/业务域精确匹配 |
| 缓存容量限制 | 热点用户可能被驱逐 | 当前设置 10000 条，可根据实际调整 |

---

## 8. 后续优化方向

1. **异步预加载**：在版本变化时异步预加载热点用户快照
2. **分布式缓存**：将快照缓存迁移到 Redis，支持多实例共享
3. **增量快照**：仅返回变化部分，减少传输量
4. **监控指标**：添加快照命中率、组装耗时等监控

---

## 9. 相关文件

| 文件 | 说明 |
|------|------|
| `PermissionKernelSnapshotMapper.xml` | 快照 SQL 映射 |
| `HybridPermissionKernelService.java` | 混合内核服务实现 |
| `InterfaceSnapshotCache.java` | 快照缓存组件 |
| `DatabaseInterfacePermissionRuleQueryService.java` | 数据库规则查询服务 |
| `PolicyKernelController.java` | 快照策略接口 |
| `VersionKernelController.java` | 版本查询接口 |
| `DecisionKernelController.java` | 接口判定接口 |
