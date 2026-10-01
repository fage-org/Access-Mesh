# Runbook：服务暂停恢复与运行库盘点

T-ACCESS-062 后服务统一操作准入，没有模式切换字段。首次退役部署与成套回滚见 [API 退役手册](runbook-api-retirement-062.md)。日常发布仍使用服务级暂停，演练由 `ExampleBusinessFinalCheckE2EIT` 的暂停/恢复用例承载。

## 一、接入资格

每个业务路由必须有对实际目标的最终检查代码与无权限反向拒绝测试。主体与租户取自可信认证链，不能由业务请求 DTO 自报。Gateway 准入通过不等于对象鉴权通过。

## 二、暂停与恢复

1. `POST /api/access/service-config/save` 保存 `{serviceCode, name, status:0}`；配置代次递增，网关最终收敛到空路由快照拒绝。
2. 核对业务最终检查，完成发布或映射更新。确认全部节点运行目标版本，停止旧进程以清除在途加载、本地快照与负缓存。
3. 保存 `{serviceCode, name, status:1}`；代次再次递增，并广播服务快照失效。业务按库中启停状态恢复，广播丢失时受既有 30 秒边界约束。失败时维持暂停，不能关闭安全检查作为恢复手段。

## 三、运行库盘点（迁移或恢复服务前执行）

六类查询逐项执行，结果留档到任务卡；**不确认的数据不恢复服务**。非 API 存量映射要
显式找到登记 API 并补准入操作，或经确认清理（不能凭旧菜单类型猜 VIEW）。盘点包含停用映射，防止以后启用时重新引入死配置。重叠路径歧义 SQL 无法完备枚举（查询②只覆盖精确重复）：恢复流量前对每个服务抽验实际路径，至少覆盖被通配模式遮蔽的精确路由（如 `/reports/**` 之下的 `/reports/export`）——经网关以真实账号请求，预期为正常业务响应或 403 无权限，出现 503 即配置故障（路由歧义/引用损坏），不确认不恢复。

```sql
-- ① 各服务映射分布与登记实体类型（包含停用映射；按租户+服务分组，多租户同服务码不合并）
SELECT m.tenant_id, m.service_code,
       count(*) AS mappings,
       count(*) FILTER (WHERE re.resource_type = (SELECT type_value FROM type_definition
           WHERE tenant_id = m.tenant_id AND type_key = 'resource_type' AND type_code = 'API' AND delete_flag = 0)) AS api_ref,
       count(*) FILTER (WHERE re.id IS NULL) AS dangling_ref
FROM resource_api_mapping m
LEFT JOIN resource_entity re ON re.id = m.resource_entity_id AND re.tenant_id = m.tenant_id AND re.delete_flag = 0
WHERE m.delete_flag = 0
GROUP BY m.tenant_id, m.service_code ORDER BY 1, 2;

-- ② 同路由多要求（启用映射按精确路由重复——只覆盖 method+path 完全相同的重复行；重叠路径
--    歧义〔如 /reports/** 通配遮蔽 /reports/export 精确〕不在盘点与快照构建期检测，请求匹配时
--    才拦截：网关本地等价检测→终端 503、在线判定 20070——须按本节末段的实际路径抽验清零；
--    按租户+服务+路由分组——歧义判定域是单服务内，不同服务的合法同名路由不算重复）
SELECT tenant_id, service_code, http_method || ' ' || path_pattern AS route, count(*), count(DISTINCT required_operation_id) AS distinct_reqs
FROM resource_api_mapping
WHERE delete_flag = 0 AND enabled
GROUP BY 1, 2, 3 HAVING count(*) > 1;

-- ③ 操作引用缺失/悬空或定义损坏（包含停用映射）：操作行缺失/软删、所属类型不可反查、API 类型
--    操作（作准入要求恒无候选）在启用映射命中时快照 20071 配置故障→网关 503；binary_bit 非
--    单个正位是写侧保存入口必拒的脏数据（判定面按位语义不可信）——命中任意一项即不恢复
SELECT m.tenant_id, m.id, m.service_code, m.http_method, m.path_pattern, m.enabled, m.required_operation_id,
       op.code AS op_code, td.type_code AS op_type_code, op.binary_bit
FROM resource_api_mapping m
LEFT JOIN operation_permission op ON op.id = m.required_operation_id
    AND op.tenant_id = m.tenant_id AND op.delete_flag = 0
LEFT JOIN type_definition td ON td.tenant_id = m.tenant_id AND td.type_key = 'resource_type'
    AND td.type_value = op.resource_type AND td.delete_flag = 0
WHERE m.delete_flag = 0
  AND (op.id IS NULL OR td.id IS NULL OR td.type_code = 'API'
       OR op.binary_bit IS NULL OR op.binary_bit <= 0 OR bit_count(op.binary_bit::bit(64)) <> 1);

-- ④ 独立 API 授权残留（当前应为零；异常存量按 062 手册受控处置）
SELECT rrp.tenant_id, rrp.abstract_role_id, count(*)
FROM role_resource_permission rrp
JOIN type_definition td ON td.tenant_id = rrp.tenant_id AND td.type_value = rrp.resource_type AND td.type_key = 'resource_type' AND td.type_code = 'API' AND td.delete_flag = 0
WHERE rrp.delete_flag = 0
GROUP BY 1, 2;

-- ⑤ 同步归属（SERVICE_SYNC 映射与 owner 声明一致性；手工映射 maintain_source=MANUAL 不受 FULL 清理）
SELECT service_code, maintain_source, count(*)
FROM resource_api_mapping
WHERE delete_flag = 0
GROUP BY 1, 2;

-- ⑥ 无最终业务门禁路由（业务服务侧逐路由核对——查询面是 access 映射登记，门禁面在业务代码位置+反向拒绝测试，见 §一）
SELECT m.service_code, count(*) AS enabled_routes
FROM resource_api_mapping m
JOIN service_config sc ON sc.tenant_id = m.tenant_id AND sc.service_code = m.service_code AND sc.delete_flag = 0
WHERE m.delete_flag = 0 AND m.enabled
GROUP BY 1;
```

dev 运行库初始盘点见 [T-ACCESS-061](../archive/2026-10-01/tasks/T-ACCESS-061.md)，退役后复核见 [T-PERM-054](../archive/2026-10-01/tasks/T-PERM-054.md#完成记录)。其他部署须在自身运行库执行，不以开发库结果替代。映射注释的存量同步脚本为 [api-mapping-comments-054.sql](api-mapping-comments-054.sql)，仅更新说明，不清理数据。

## 四、example-service 参考接入形态（T-ACCESS-061 拍板）

- **客户端**：业务服务消费 `perm-client-spring-boot-starter`（Feign），服务认证走内部密钥
  通道（SDK `FeignInternalSyncInterceptor` 注入 `X-Internal-Secret`/`X-Service-Code`，
  `perm.internal-secret`+`perm.service-code` 配置）；`X-Tenant-Id` 由业务侧
  `FeignTenantHeaderInterceptor` 从调用上下文注入（SDK 约定租户头由调用方负责）。
- **最终检查调用面**：`auth/check`（单目标 DECISION，含 depend_on 父上下文字段）、
  `auth/batch-check`（独立批量逐项）、`auth/query-resources`（列表/搜索范围）。
- **失败语义**：业务最终拒绝=example 域信封 30004（HTTP 200，与网关准入 403 形成层次
  区分）；鉴权服务不可用=fail-closed 30005，不放行任何数据。
- **异步作业**：提交与执行各自鉴权——执行时点重查同一目标，不永久复用提交时点结论
  （撤权窗口内提交的作业在执行时点被拒绝）。
