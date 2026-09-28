-- T-ACCESS-059：无迁移期统一 OPERATION_ADMISSION 上线（2026-09-28 用户拍板：全部服务一次切换、
-- 网关单链无模式发现；回退=回滚网关版本，服务端旧端点保留至 T-ACCESS-062 退役）。执行前备份。
-- 前置：已执行 operation-admission-migrate-058.sql（required_operation_id/maintain_source/api_auth_mode 列已存在）。
BEGIN;

-- ① service_config 新增配置代次列（准入快照构建期自一致校验载体；计数列拍板 2026-09-28：
--    映射写路径/模式切换同事务 +1，构建前后代次比对、变更即废弃重建）
ALTER TABLE service_config ADD COLUMN config_generation BIGINT NOT NULL DEFAULT 0;

-- ② 无迁移期：全部有效服务一次切 OPERATION_ADMISSION（LEGACY_API 值仅作版本回退部署形态）
UPDATE service_config SET api_auth_mode = 'OPERATION_ADMISSION', updated_at = now() WHERE delete_flag = 0;

-- ③ auth/query-scopes 映射停用：M2M SDK 直连端点（不经网关路由链），已移出固定图
--    （排查页随 T-PERM-059 删除、真实消费者零）；OPERATION_ADMISSION 下启用且无操作引用的
--    映射会使 access-service 准入快照整体 20071，故必须停用。行保留作回滚面。
UPDATE resource_api_mapping SET enabled = false, updated_at = now()
WHERE tenant_id = 1 AND service_code = 'access-service' AND delete_flag = 0
  AND http_method = 'POST' AND path_pattern = '/api/access/auth/query-scopes';

-- ④ access-service 固定图映射补业务操作引用（按 BootstrapGraphDefinition.apiRoutes() 程序化生成，
--    共 105 条路由 53 组；要求=各端点服务层 QueryGate 门禁同码——契约 §25.1「接口→业务资源类型与操作」，
--    网关准入候选来自 bootstrap 既有类型级业务授权，双层同码。job/create|update|delete 绑定操作
--    但固定图不授，网关 403 与现行服务层 403 终端一致）

-- ROLE:VIEW（3 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ROLE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/abstract-role/tree', '/api/access/abstract-role/detail', '/api/access/abstract-role/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- TYPE_DEFINITION:VIEW（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'TYPE_DEFINITION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/type-definition/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- RESOURCE:VIEW（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'RESOURCE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/resource-entity/tree', '/api/access/resource-entity/detail'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- OPERATION:VIEW（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'OPERATION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/operation-permission/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- CONDITION:VIEW（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONDITION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/permission-condition/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- ROLE:MANAGE（9 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ROLE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/role-resource-permission/list', '/api/access/role-resource-permission/sub-perm-allowed-types', '/api/access/role-resource-permission/preview-grant-plan', '/api/access/role-resource-permission/apply-grant-plan', '/api/access/user-role/assign', '/api/access/user-role/revoke', '/api/access/abstract-role/update', '/api/access/abstract-role/remove', '/api/access/abstract-role/move'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE' AND o.delete_flag = 0;

-- USER:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'USER' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/user/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- ROLE:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ROLE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/abstract-role/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- SERVICE:MANAGE_API_MAPPING（3 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'SERVICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/resource-api-mapping/create', '/api/access/resource-api-mapping/update', '/api/access/resource-api-mapping/remove'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE_API_MAPPING' AND o.delete_flag = 0;

-- ADMIN_ORG_TREE_CONFIG:VIEW（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_ORG_TREE_CONFIG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/org-tree-config/page'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- ORG:VIEW（5 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ORG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/org/tree', '/api/access/org/page', '/api/access/org/users', '/api/access/user/member-candidates', '/api/access/user-org/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- ORG:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ORG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/org/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- ORG:UPDATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ORG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/org/update'
  )
  AND o.tenant_id = 1 AND o.code = 'UPDATE' AND o.delete_flag = 0;

-- ORG:DELETE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ORG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/org/delete'
  )
  AND o.tenant_id = 1 AND o.code = 'DELETE' AND o.delete_flag = 0;

-- USER:VIEW（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'USER' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/user/page', '/api/access/user-role/view'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- USER:UPDATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'USER' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/user/update'
  )
  AND o.tenant_id = 1 AND o.code = 'UPDATE' AND o.delete_flag = 0;

-- USER:DELETE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'USER' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/user/delete'
  )
  AND o.tenant_id = 1 AND o.code = 'DELETE' AND o.delete_flag = 0;

-- USER:ENABLE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'USER' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/user/enable'
  )
  AND o.tenant_id = 1 AND o.code = 'ENABLE' AND o.delete_flag = 0;

-- USER:RESET_PASSWORD（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'USER' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/user/reset-password'
  )
  AND o.tenant_id = 1 AND o.code = 'RESET_PASSWORD' AND o.delete_flag = 0;

-- ORG:MANAGE_MEMBER（3 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ORG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/user-org/assign', '/api/access/user-org/remove', '/api/access/user-org/set-primary'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE_MEMBER' AND o.delete_flag = 0;

-- RESOURCE:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'RESOURCE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/resource-entity/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- RESOURCE:MANAGE（3 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'RESOURCE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/resource-entity/update', '/api/access/resource-entity/move', '/api/access/resource-entity/remove'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE' AND o.delete_flag = 0;

-- OPERATION:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'OPERATION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/operation-permission/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- OPERATION:MANAGE（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'OPERATION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/operation-permission/update', '/api/access/operation-permission/remove'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE' AND o.delete_flag = 0;

-- CONDITION:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONDITION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/permission-condition/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- CONDITION:UPDATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONDITION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/permission-condition/update'
  )
  AND o.tenant_id = 1 AND o.code = 'UPDATE' AND o.delete_flag = 0;

-- CONDITION:DELETE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONDITION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/permission-condition/remove'
  )
  AND o.tenant_id = 1 AND o.code = 'DELETE' AND o.delete_flag = 0;

-- CONFLICT_RULE:VIEW（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONFLICT_RULE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/conflict-rule/list', '/api/access/conflict-rule/detect'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- CONFLICT_RULE:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONFLICT_RULE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/conflict-rule/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- CONFLICT_RULE:UPDATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONFLICT_RULE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/conflict-rule/update'
  )
  AND o.tenant_id = 1 AND o.code = 'UPDATE' AND o.delete_flag = 0;

-- CONFLICT_RULE:DELETE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'CONFLICT_RULE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/conflict-rule/remove'
  )
  AND o.tenant_id = 1 AND o.code = 'DELETE' AND o.delete_flag = 0;

-- DOMAIN:VIEW（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'DOMAIN' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/biz-domain/list', '/api/access/biz-domain/detail'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- SYSTEM_CONFIG:MANAGE（6 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'SYSTEM_CONFIG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/biz-domain/create', '/api/access/biz-domain/update', '/api/access/biz-domain/remove', '/api/access/domain-config/save', '/api/access/domain-config/remove', '/api/access/system-config/save'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE' AND o.delete_flag = 0;

-- SYSTEM_CONFIG:VIEW（3 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'SYSTEM_CONFIG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/domain-config/list', '/api/access/domain-config/detail', '/api/access/system-config/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- TYPE_DEFINITION:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'TYPE_DEFINITION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/type-definition/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- TYPE_DEFINITION:MANAGE（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'TYPE_DEFINITION' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/type-definition/update', '/api/access/type-definition/remove'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE' AND o.delete_flag = 0;

-- SERVICE:VIEW（4 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'SERVICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/service-config/list', '/api/access/service-config/apis', '/api/access/resource-api-mapping/list', '/api/access/service-credential/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- SERVICE:MANAGE（5 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'SERVICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/service-config/save', '/api/access/service-config/remove', '/api/access/service-credential/create', '/api/access/service-credential/update', '/api/access/service-credential/remove'
  )
  AND o.tenant_id = 1 AND o.code = 'MANAGE' AND o.delete_flag = 0;

-- SERVICE:SYNC_INTERFACE（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'SERVICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/service-config/sync', '/api/access/service-config/sync-v2'
  )
  AND o.tenant_id = 1 AND o.code = 'SYNC_INTERFACE' AND o.delete_flag = 0;

-- OPERATION_LOG:VIEW（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'OPERATION_LOG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/log/operation/list', '/api/access/log/operation/action-options'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- PERMISSION_CHANGE_LOG:VIEW（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'PERMISSION_CHANGE_LOG' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/log/change/list'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- DEPENDENCY:VIEW（5 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'DEPENDENCY' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/resource-dependency/list', '/api/access/resource-dependency/graph', '/api/access/resource-dependency/check', '/api/access/resource-dependency/explain', '/api/access/resource-dependency/declaration-status'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- ADMIN_NOTICE:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_NOTICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/notice/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- ADMIN_NOTICE:UPDATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_NOTICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/notice/update'
  )
  AND o.tenant_id = 1 AND o.code = 'UPDATE' AND o.delete_flag = 0;

-- ADMIN_NOTICE:DELETE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_NOTICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/notice/delete'
  )
  AND o.tenant_id = 1 AND o.code = 'DELETE' AND o.delete_flag = 0;

-- ADMIN_NOTICE:VIEW（4 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_NOTICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/notice/detail', '/api/access/notice/page', '/api/access/notice/my-notices', '/api/access/notice/read'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- ADMIN_NOTICE:PUBLISH（2 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_NOTICE' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/notice/publish', '/api/access/notice/revoke'
  )
  AND o.tenant_id = 1 AND o.code = 'PUBLISH' AND o.delete_flag = 0;

-- ADMIN_JOB:CREATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_JOB' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/job/create'
  )
  AND o.tenant_id = 1 AND o.code = 'CREATE' AND o.delete_flag = 0;

-- ADMIN_JOB:UPDATE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_JOB' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/job/update'
  )
  AND o.tenant_id = 1 AND o.code = 'UPDATE' AND o.delete_flag = 0;

-- ADMIN_JOB:DELETE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_JOB' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/job/delete'
  )
  AND o.tenant_id = 1 AND o.code = 'DELETE' AND o.delete_flag = 0;

-- ADMIN_JOB:ENABLE（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_JOB' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/job/toggle'
  )
  AND o.tenant_id = 1 AND o.code = 'ENABLE' AND o.delete_flag = 0;

-- ADMIN_JOB:TRIGGER（1 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_JOB' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/job/trigger'
  )
  AND o.tenant_id = 1 AND o.code = 'TRIGGER' AND o.delete_flag = 0;

-- ADMIN_JOB:VIEW（3 条）
UPDATE resource_api_mapping m
SET required_operation_id = o.id, updated_at = now()
FROM operation_permission o
JOIN type_definition td ON td.tenant_id = o.tenant_id AND td.type_key = 'resource_type'
    AND td.type_code = 'ADMIN_JOB' AND td.type_value = o.resource_type AND td.delete_flag = 0
WHERE m.tenant_id = 1 AND m.service_code = 'access-service' AND m.delete_flag = 0
  AND m.enabled = true AND m.http_method = 'POST'
  AND m.path_pattern IN (
            '/api/access/job/detail', '/api/access/job/page', '/api/access/job/log/page'
  )
  AND o.tenant_id = 1 AND o.code = 'VIEW' AND o.delete_flag = 0;

-- ⑤ bootstrap 管理角色补两笔类型级授权（开放读端点的准入要求绑定，维持管理员现行可过行为）：
--    CONDITION:VIEW（condition/list）与 ADMIN_ORG_TREE_CONFIG:VIEW（org-tree-config/page）；
--    granted_bits 取 operation_permission.binary_bit（幂等：同身份键已有有效行则跳过）
INSERT INTO role_resource_permission
    (tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type,
     scope_all, can_grant, grant_source, created_by, updated_by, delete_flag)
SELECT 1, r.id, NULL, o.binary_bit, o.resource_type, true, false, 'MANUAL', 0, 0, 0
FROM abstract_role r
JOIN operation_permission o ON o.tenant_id = 1 AND o.delete_flag = 0
JOIN type_definition td ON td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.delete_flag = 0
    AND td.type_value = o.resource_type
WHERE r.tenant_id = 1 AND r.role_type = 6 AND r.external_id = 'bootstrap-admin' AND r.delete_flag = 0
  AND ((td.type_code = 'CONDITION' AND o.code = 'VIEW')
    OR (td.type_code = 'ADMIN_ORG_TREE_CONFIG' AND o.code = 'VIEW'))
  AND NOT EXISTS (
    SELECT 1 FROM role_resource_permission p
    WHERE p.tenant_id = 1 AND p.abstract_role_id = r.id AND p.resource_type = o.resource_type
      AND p.scope_all = true AND p.resource_entity_id IS NULL AND p.delete_flag = 0);

-- ⑥ 路由集已变：递增 access-service 配置代次（其余服务为新建服务由缺省 0 起）
UPDATE service_config SET config_generation = config_generation + 1
WHERE tenant_id = 1 AND service_code = 'access-service' AND delete_flag = 0;

COMMENT ON COLUMN service_config.api_auth_mode IS '可信服务配置控制的鉴权模式 LEGACY_API/OPERATION_ADMISSION；不接受客户端模式头。T-ACCESS-059 拍板无迁移期统一上线：全部服务（含 access-service 自身）默认 OPERATION_ADMISSION，LEGACY_API 值仅作 062 退役前的版本回退部署形态（服务端准入端点对 LEGACY_API 服务按配置故障拒绝）';
COMMENT ON COLUMN service_config.config_generation IS '准入快照配置代次（T-ACCESS-059 计数列载体，2026-09-28 拍板）：该服务映射写路径（共用保存入口/删除/FULL 清理）与模式切换同事务 +1；准入快照构建事务内先读代次→构建→复读比对，变更即废弃重建（2026-09-25 拍板限定语义：构建期自一致校验+接收侧匹配检查，不承诺跨节点强一致）';

-- 验证：access-service 启用映射应全部具备操作引用（期望 0 行）
-- SELECT http_method, path_pattern FROM resource_api_mapping
-- WHERE tenant_id = 1 AND service_code = 'access-service' AND delete_flag = 0 AND enabled = true
--   AND required_operation_id IS NULL;

COMMIT;
