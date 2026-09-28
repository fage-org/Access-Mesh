# Runbook：服务级暂停切换与运行库盘点（T-ACCESS-061）

> 适用场景：业务服务从 LEGACY_API 切到 OPERATION_ADMISSION、或对已切服务做模式/启停
> 变更。设计依据：r2-unified-query-and-admission.md §8.5「首次迁移默认采用可验证的服务级
> 切换」与 §8.6；契约总册 §25.7「服务迁移门槛」。演练证据：
> `e2e/src/test/java/cn/ac/fage/accessmesh/e2e/ExampleBusinessFinalCheckE2EIT.java`
> 步骤⑪（暂停→切模式→恢复：代次单调递增、空快照 DENY、恢复以库为准）。

## 一、迁移资格（切换前置门槛）

迁移资格 = **「最终检查的代码位置 + 反向拒绝测试」**，不是 `businessChecked=true` 类配置。
没有逐路由证明（业务服务内实际目标完整鉴权代码位置明确、且存在「无权限时确实拒绝」
的反向测试）的服务不切新模式。

- 最终检查代码位置：业务服务内每个路由对**业务请求实际解析出的目标**调用
  `auth/check` 族端点做实例级判定（example-service 参考实现：
  `BusinessPermChecker` 门面 + `ReportController` 七路由）。
- 反向拒绝测试：逐路由「无权限→拒绝」用例（example 参考：
  `ReportControllerTest` 16 用例——查 A 不按 B 取数、批量逐项、TYPE_LEVEL、
  父上下文、异步执行时点重查）。
- 主体纪律：主体/租户只取自可信认证链（Gateway 注入且已验签的请求头），
  请求 DTO 不携带主体/租户字段，客户端不可自报。

## 二、切换步骤（服务级暂停切换）

按序执行，任一步失败即停止并回退到上一步形态（恢复=反向执行模式/启停保存）：

| 步 | 操作 | 验证 |
|---|---|---|
| 1. 暂停 | `POST /api/access/service-config/save` `{serviceCode, name, status: 0}` | `service_config.config_generation` 递增；网关对新请求返回 403（空路由快照 DENY，不回源风暴——T-ACCESS-060 拍板空快照可缓存） |
| 2. 确认逐路由最终检查 | 逐路由核对「最终检查代码位置 + 反向拒绝测试」（§一） | 证明材料落任务卡/评审记录 |
| 3. 切模式 | 同端点保存 `{serviceCode, name, apiAuthMode: "OPERATION_ADMISSION"}`（仍处暂停态） | `config_generation` 再递增 |
| 4. 全节点确认新版本并清旧缓存 | 模式/启停保存同事务广播 `perm:invalidate`（serviceCodes 非空→网关租户级快照清除）；确认全部网关节点收到（订阅重连节点由启动全量清空兜底） | 网关本地快照代次=新代次（快照响应 `configGeneration`） |
| 5. 恢复 | 保存 `{serviceCode, name, status: 1}` | `config_generation` 再递增；30 秒陈旧窗口内业务恢复放行（恢复以库为准，不依赖广播送达） |

回退形态（版本回退部署）：`apiAuthMode=LEGACY_API` 仅作 062 退役前的回退部署形态——
OPERATION_ADMISSION 端点不服务 LEGACY 模式（快照端点 20071 信封→网关 503），
回退须整体回滚网关版本，禁止新旧 OR。

## 三、运行库盘点（切换前必执行）

六类查询逐项执行，结果留档到任务卡；**不确认的数据不启新模式**。非 API 存量映射要
显式找到登记 API 并补准入操作（不能凭旧菜单类型猜 VIEW）。

```sql
-- ① 各服务映射分布与登记实体类型（非 API 手工映射=引用非 API 类型实体的启用映射行）
SELECT m.service_code,
       count(*) AS mappings,
       count(*) FILTER (WHERE re.resource_type = (SELECT type_value FROM type_definition
           WHERE tenant_id = m.tenant_id AND type_key = 'resource_type' AND type_code = 'API' AND delete_flag = 0)) AS api_ref,
       count(*) FILTER (WHERE re.id IS NULL) AS dangling_ref
FROM resource_api_mapping m
LEFT JOIN resource_entity re ON re.id = m.resource_entity_id AND re.tenant_id = m.tenant_id AND re.delete_flag = 0
WHERE m.delete_flag = 0
GROUP BY m.service_code ORDER BY 1;

-- ② 同路由/重叠路径多要求（启用映射按精确路由重复；重叠路径歧义由快照构建期 20070 拦截）
SELECT http_method || ' ' || path_pattern AS route, count(*), count(DISTINCT required_operation_id) AS distinct_reqs
FROM resource_api_mapping
WHERE delete_flag = 0 AND enabled
GROUP BY 1 HAVING count(*) > 1;

-- ③ 缺业务操作登记（启用映射无 required_operation_id——OPERATION_ADMISSION 下 20071 配置故障）
SELECT service_code, http_method, path_pattern
FROM resource_api_mapping
WHERE delete_flag = 0 AND enabled AND required_operation_id IS NULL;

-- ④ 各服务独立 API 授权（旧 API:ACCESS 授权行残留——OPERATION_ADMISSION 不消费，062 受控清理面）
SELECT rrp.tenant_id, rrp.abstract_role_id, count(*)
FROM role_resource_permission rrp
JOIN type_definition td ON td.type_value = rrp.resource_type AND td.type_key = 'resource_type' AND td.type_code = 'API' AND td.delete_flag = 0
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

dev 运行库执行记录（T-ACCESS-061，重建到 HEAD 后）：见任务卡完成记录。

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
