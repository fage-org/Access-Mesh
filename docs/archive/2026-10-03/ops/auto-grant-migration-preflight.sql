-- 自动授权迁移前只读盘点（T-PERM-078 / T-PERM-071）。
-- 在目标部署库执行；测试库结果不能证明目标部署零存量。
-- 本脚本不迁移、不删除、不将旧依赖转为自动生效；详情限量，汇总不截断。
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

-- 旧依赖来源与启用标记。任何有效行均须有明确保留/转换处置，不能直接删列后启用。
SELECT tenant_id, maintain_source, owner_service_code, auto_grant, count(*) AS active_count
FROM resource_dependency
WHERE delete_flag = 0
GROUP BY tenant_id, maintain_source, owner_service_code, auto_grant
ORDER BY tenant_id, maintain_source, owner_service_code, auto_grant;

-- 历史自动授权：即使现役写入口拒绝 autoGrant=true，也不能假定从未导入/手写过授权行。
SELECT tenant_id, grant_source, count(*) AS active_count,
       count(DISTINCT abstract_role_id) AS affected_role_count,
       count(*) FILTER (WHERE grant_dep_id IS NOT NULL) AS with_legacy_dependency_id
FROM role_resource_permission
WHERE delete_flag = 0 AND grant_source = 'AUTO_DEP'
GROUP BY tenant_id, grant_source
ORDER BY tenant_id;

-- 源/目标资源缺失、跨租户或已删除的旧边汇总；软删除资源不作为有效迁移目标。
SELECT d.tenant_id,
       count(*) FILTER (WHERE source.id IS NULL) AS missing_source_count,
       count(*) FILTER (WHERE target.id IS NULL) AS missing_target_count,
       count(*) FILTER (WHERE d.resource_entity_id = d.depends_on_resource_entity_id) AS self_edge_count
FROM resource_dependency d
LEFT JOIN resource_entity source
  ON source.tenant_id = d.tenant_id AND source.id = d.resource_entity_id AND source.delete_flag = 0
LEFT JOIN resource_entity target
  ON target.tenant_id = d.tenant_id AND target.id = d.depends_on_resource_entity_id AND target.delete_flag = 0
WHERE d.delete_flag = 0
GROUP BY d.tenant_id
ORDER BY d.tenant_id;

-- 有效旧边明细（最多 500 条）：业务键和类型所有权供所属服务核对，不能直接作为 manifest 发布。
SELECT d.tenant_id, d.id AS legacy_dependency_id, d.maintain_source, d.owner_service_code,
       d.auto_grant, d.source_operation_bits, d.required_operation_bits,
       source_type.type_code AS source_type_code, source.code AS source_code, source.code_type AS source_code_type,
       source_type.extra ->> 'syncSourceService' AS source_type_owner,
       target_type.type_code AS target_type_code, target.code AS target_code, target.code_type AS target_code_type,
       target_type.extra ->> 'syncSourceService' AS target_type_owner
FROM resource_dependency d
LEFT JOIN resource_entity source
  ON source.tenant_id = d.tenant_id AND source.id = d.resource_entity_id AND source.delete_flag = 0
LEFT JOIN resource_entity target
  ON target.tenant_id = d.tenant_id AND target.id = d.depends_on_resource_entity_id AND target.delete_flag = 0
LEFT JOIN type_definition source_type
  ON source_type.tenant_id = d.tenant_id AND source_type.type_key = 'resource_type'
 AND source_type.type_value = source.resource_type AND source_type.delete_flag = 0
LEFT JOIN type_definition target_type
  ON target_type.tenant_id = d.tenant_id AND target_type.type_key = 'resource_type'
 AND target_type.type_value = target.resource_type AND target_type.delete_flag = 0
WHERE d.delete_flag = 0
ORDER BY d.tenant_id, d.id
LIMIT 500;

COMMIT;
