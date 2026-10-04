# API 独立授权与旧协议退役

适用于已完成 T-ACCESS-059/061、全部服务具备业务最终检查的环境。新版本统一使用操作准入，服务配置没有鉴权模式字段。接口登记实体与路由映射继续保留；授权页不再提供 API 类型。

## 发布前准备

1. 核对每个业务路由的最终检查代码和反向拒绝测试。新准入通过只允许请求进入业务服务，不代表请求目标已获得访问权。
2. 暂停入口流量，停止所有旧版 Gateway、access-service 和同步写者；确认没有旧节点可能恢复。不能在旧写者仍存活时删授权，否则旧版本可能再次生成 API 授权。
3. 对目标 PostgreSQL 库执行 `pg_dump` 完整备份，记录备份路径、版本和校验和。先在备份恢复出的独立数据库演练迁移、回滚和启动，再操作运行库。
4. 盘点有效 API 授权：按租户的 `type_definition(type_key='resource_type', type_code='API').type_value` 关联 `role_resource_permission.resource_type`，不要用类型表主键关联。按租户、角色、grant_source 汇总，保存行级备份。bootstrap 同样使用 MANUAL，不能按来源字段识别全部旧授权。

## 迁移

停流期间使用 `psql -v ON_ERROR_STOP=1 -f docs/ops/api-authorization-retire-062.sql` 执行。

- 单事务锁住服务配置、类型目录和授权表，检查所有有效服务已迁新模式；若业务子授权或跨租户子行引用 API 父授权，整批失败，先报告明细再决定处置。
- `accessmesh_retirement_062` 保存执行时间、数据库执行人、授权原行/退役后行、所有服务原模式值。该命名空间是本次迁移账本，不是运行时功能；重复执行拒绝覆盖。
- 所有来源的有效 API 授权按仓库软删除形态退役，其他类型授权及 API 登记目录不变；随后删除 `service_config.api_auth_mode`。旧 `/service-config/sync` 登记若已存在可作为目录历史保留，但服务端端点已删除；接口清单以后只通过 `/sync-v2` 发布。
- 启动新版本前清权限事实缓存：Redis 使用 `SCAN` 按目录枚举后分批 `UNLINK`，清 `{tenant}:perm:role-perm-snapshot:*`、`{tenant}:perm:effective-roles:*`、`{tenant}:access:org-visibility:*`。不使用 `KEYS` 或 `FLUSHALL`，不影响其他应用数据。
- 旧 `gw:interface-snapshot` 与新 `gw:interface-admission-snapshot` 均为各 Gateway 进程内 L1。停止并替换全部节点会同时丢弃旧/新缓存、空快照负缓存和在途加载；无需创建旧目录兼容别名。确认旧进程终止后再恢复流量，不能只等待一个新节点启动。

## 验证与恢复流量

有效 API 类型授权数必须为零；API 登记实体与映射应与迁移前一致；非 API 授权逐行应保持一致；服务配置中不存在模式列。空库启动也必须零 API 授权。同时盘点准入要求不得为 API 类型（写侧已整类拒绝、读侧报 20071，存量脏数据须清理或改绑业务操作）：

```sql
SELECT m.tenant_id, m.service_code, m.http_method, m.path_pattern
FROM resource_api_mapping m
JOIN operation_permission op ON op.id = m.required_operation_id
    AND op.tenant_id = m.tenant_id AND op.delete_flag = 0
JOIN type_definition t ON t.tenant_id = m.tenant_id AND t.type_key = 'resource_type'
    AND t.type_code = 'API' AND t.delete_flag = 0 AND t.type_value = op.resource_type
WHERE m.delete_flag = 0;
```

启动新版本后验证管理员登录、业务授权页、接口登记与 sync-v2。无授权角色被网关拒绝；只有一个业务对象权限的角色可以进入路由，但访问另一对象仍被业务拒绝。旧检查/快照/sync 端点无映射。完成后恢复入口流量，保留本次账本和完整备份。

## 回滚

先重新停流并停止所有新版本进程，再执行 `api-authorization-rollback-062.sql`，并恢复匹配的旧代码版本；不能只恢复数据或只回滚 Gateway。

回滚逐行比较退役后授权快照。授权发生变化、服务集合变化或账本已回滚时拒绝自动恢复，须核对后另行处置；唯一键冲突等数据库故障会整批回滚。成功时恢复原模式列和授权原审计/软删字段，账本保留回滚时间。重新清理上述共享权限缓存，启动匹配版本并完成业务安全验证后再恢复流量。回滚不构成允许长期恢复旧授权模式的依据。

## 回滚后重新迁移

迁移脚本检测到 `accessmesh_retirement_062` 账本存在即整单拒绝（`T062_ALREADY_RECORDED`），回滚只标记 `rolled_back_at` 不删除账本——回滚后如需再次退役，**禁止直接 `DROP SCHEMA`**（会销毁唯一的行级原始快照）。处置：

1. 确认账本 `manifest.rolled_back_at` 非空（已回滚形态）且新一次迁移前已完成新的完整 `pg_dump` 备份。
2. 保留证据改名归档：`ALTER SCHEMA accessmesh_retirement_062 RENAME TO accessmesh_retirement_062_rb_<YYYYMMDD>;`（账本行与快照随 schema 整体保留）。
3. 重新执行 `api-authorization-retire-062.sql`，产生全新账本；验证与缓存清理同「迁移」段。

演练环境重复演练同样按本节改名后重跑。
