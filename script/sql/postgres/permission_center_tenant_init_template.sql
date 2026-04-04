-- =============================================================================
-- 通用权限中心 - 新租户初始化模板（PostgreSQL）
-- 使用方式：
--   1. 将 {{TENANT_ID}}、{{CREATED_BY}}、{{DOMAIN_CODE}}、{{DOMAIN_NAME}} 替换为实际值
--      建议系统管理域使用：
--        {{DOMAIN_CODE}} = SYSTEM_MANAGEMENT
--        {{DOMAIN_NAME}} = 系统管理
--   2. 先执行 permission_center_schema.sql，再执行本模板
-- 说明：
--   1. 首批 role_type 仅初始化：系统管理员 = 1
--   2. 默认域配置模板覆盖：domain_scope_config / domain_relation_config / domain_scope_binding
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 类型定义：user_type
-- -----------------------------------------------------------------------------
INSERT INTO type_definition (
    tenant_id, biz_domain_id, type_key, type_value, name, description, sort_order,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, NULL, t.type_key, t.type_value, t.name, t.description, t.sort_order,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM (
    VALUES
        ('user_type', 1, 'USER', '人类用户主体', 1),
        ('user_type', 2, 'SERVICE', '服务主体', 2),
        ('user_type', 3, 'DELEGATED', '委托上下文主体', 3)
) AS t(type_key, type_value, name, description, sort_order)
WHERE NOT EXISTS (
    SELECT 1
    FROM type_definition td
    WHERE td.tenant_id = {{TENANT_ID}}
      AND td.biz_domain_id IS NULL
      AND td.type_key = t.type_key
      AND td.type_value = t.type_value
      AND td.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 1. 类型定义：role_type
-- -----------------------------------------------------------------------------
INSERT INTO type_definition (
    tenant_id, biz_domain_id, type_key, type_value, name, description, sort_order,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, NULL, t.type_key, t.type_value, t.name, t.description, t.sort_order,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM (
    VALUES
        ('role_type', 1, '系统管理员', '系统管理员角色类型', 1)
) AS t(type_key, type_value, name, description, sort_order)
WHERE NOT EXISTS (
    SELECT 1
    FROM type_definition td
    WHERE td.tenant_id = {{TENANT_ID}}
      AND td.biz_domain_id IS NULL
      AND td.type_key = t.type_key
      AND td.type_value = t.type_value
      AND td.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 2. 类型定义：resource_type
-- -----------------------------------------------------------------------------
INSERT INTO type_definition (
    tenant_id, biz_domain_id, type_key, type_value, name, description, sort_order,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, NULL, t.type_key, t.type_value, t.name, t.description, t.sort_order,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM (
    VALUES
        ('resource_type', 1, 'MENU', '菜单类资源', 1),
        ('resource_type', 2, 'API', '接口类资源', 2),
        ('resource_type', 3, 'DATA', '数据类资源', 3),
        ('resource_type', 4, 'BUTTON', '按钮类资源', 4)
) AS t(type_key, type_value, name, description, sort_order)
WHERE NOT EXISTS (
    SELECT 1
    FROM type_definition td
    WHERE td.tenant_id = {{TENANT_ID}}
      AND td.biz_domain_id IS NULL
      AND td.type_key = t.type_key
      AND td.type_value = t.type_value
      AND td.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 3. 首批操作定义
-- -----------------------------------------------------------------------------
INSERT INTO operation_permission (
    tenant_id, resource_type, code, name, binary_bit, inherit_mask,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, s.resource_type, s.code, s.name, s.binary_bit, s.inherit_mask,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM (
    VALUES
        (NULL, 'VIEW', '查看', 1::BIGINT, 0::BIGINT),
        (NULL, 'EDIT', '编辑', 4::BIGINT, 1::BIGINT),
        (2, 'ACCESS', '访问', 8::BIGINT, 0::BIGINT),
        (3, 'DATA_READ', '数据读取', 16::BIGINT, 0::BIGINT),
        (3, 'DATA_EXPORT', '数据导出', 32::BIGINT, 0::BIGINT)
) AS s(resource_type, code, name, binary_bit, inherit_mask)
WHERE NOT EXISTS (
    SELECT 1
    FROM operation_permission op
    WHERE op.tenant_id = {{TENANT_ID}}
      AND (
            (s.resource_type IS NULL AND op.resource_type IS NULL AND op.code = s.code)
         OR (s.resource_type IS NOT NULL AND op.resource_type = s.resource_type AND op.code = s.code)
      )
      AND op.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 4. 示例业务域
-- -----------------------------------------------------------------------------
INSERT INTO biz_domain (
    tenant_id, code, name, description,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, '{{DOMAIN_CODE}}', '{{DOMAIN_NAME}}', '新租户默认业务域模板',
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM biz_domain bd
    WHERE bd.tenant_id = {{TENANT_ID}}
      AND bd.code = '{{DOMAIN_CODE}}'
      AND bd.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 5. 默认域配置模板
-- -----------------------------------------------------------------------------
WITH domain_seed AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = {{TENANT_ID}}
      AND code = '{{DOMAIN_CODE}}'
      AND delete_flag = 0
),
role_scope_seed AS (
    SELECT 1 AS scope_ref_id
)
INSERT INTO domain_scope_config (
    tenant_id, biz_domain_id, scope_type, scope_ref_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, d.id, 'ROLE_TYPE', r.scope_ref_id,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM domain_seed d
CROSS JOIN role_scope_seed r
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_config cfg
    WHERE cfg.tenant_id = {{TENANT_ID}}
      AND cfg.biz_domain_id = d.id
      AND cfg.scope_type = 'ROLE_TYPE'
      AND cfg.scope_ref_id = r.scope_ref_id
      AND cfg.delete_flag = 0
);

WITH domain_seed AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = {{TENANT_ID}}
      AND code = '{{DOMAIN_CODE}}'
      AND delete_flag = 0
),
resource_scope_seed AS (
    SELECT 1 AS scope_ref_id UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4
)
INSERT INTO domain_scope_config (
    tenant_id, biz_domain_id, scope_type, scope_ref_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, d.id, 'RESOURCE_TYPE', r.scope_ref_id,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM domain_seed d
CROSS JOIN resource_scope_seed r
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_config cfg
    WHERE cfg.tenant_id = {{TENANT_ID}}
      AND cfg.biz_domain_id = d.id
      AND cfg.scope_type = 'RESOURCE_TYPE'
      AND cfg.scope_ref_id = r.scope_ref_id
      AND cfg.delete_flag = 0
);

WITH domain_seed AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = {{TENANT_ID}}
      AND code = '{{DOMAIN_CODE}}'
      AND delete_flag = 0
),
operation_scope_seed AS (
    SELECT id AS scope_ref_id
    FROM operation_permission
    WHERE tenant_id = {{TENANT_ID}}
      AND delete_flag = 0
      AND code IN ('VIEW', 'EDIT', 'ACCESS', 'DATA_READ', 'DATA_EXPORT')
)
INSERT INTO domain_scope_config (
    tenant_id, biz_domain_id, scope_type, scope_ref_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, d.id, 'OPERATION', o.scope_ref_id,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM domain_seed d
CROSS JOIN operation_scope_seed o
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_config cfg
    WHERE cfg.tenant_id = {{TENANT_ID}}
      AND cfg.biz_domain_id = d.id
      AND cfg.scope_type = 'OPERATION'
      AND cfg.scope_ref_id = o.scope_ref_id
      AND cfg.delete_flag = 0
);

WITH domain_seed AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = {{TENANT_ID}}
      AND code = '{{DOMAIN_CODE}}'
      AND delete_flag = 0
),
role_resource_seed AS (
    SELECT 1 AS role_type_value, 1 AS resource_type_value UNION ALL
    SELECT 1, 2 UNION ALL
    SELECT 1, 3 UNION ALL
    SELECT 1, 4
)
INSERT INTO domain_relation_config (
    tenant_id, biz_domain_id, relation_type, left_ref_id, right_ref_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, d.id, 'ROLE_RESOURCE', rr.role_type_value, rr.resource_type_value,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM domain_seed d
CROSS JOIN role_resource_seed rr
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_relation_config cfg
    WHERE cfg.tenant_id = {{TENANT_ID}}
      AND cfg.biz_domain_id = d.id
      AND cfg.relation_type = 'ROLE_RESOURCE'
      AND cfg.left_ref_id = rr.role_type_value
      AND cfg.right_ref_id = rr.resource_type_value
      AND cfg.delete_flag = 0
);

WITH domain_seed AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = {{TENANT_ID}}
      AND code = '{{DOMAIN_CODE}}'
      AND delete_flag = 0
),
operation_binding_seed AS (
    SELECT id AS bound_entity_id
    FROM operation_permission
    WHERE tenant_id = {{TENANT_ID}}
      AND delete_flag = 0
      AND code IN ('VIEW', 'EDIT', 'ACCESS', 'DATA_READ', 'DATA_EXPORT')
)
INSERT INTO domain_scope_binding (
    tenant_id, biz_domain_id, bound_type, bound_entity_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, d.id, 'OPERATION', o.bound_entity_id,
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
FROM domain_seed d
CROSS JOIN operation_binding_seed o
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_binding cfg
    WHERE cfg.tenant_id = {{TENANT_ID}}
      AND cfg.biz_domain_id = d.id
      AND cfg.bound_type = 'OPERATION'
      AND cfg.bound_entity_id = o.bound_entity_id
      AND cfg.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 6. 初始权限版本
-- -----------------------------------------------------------------------------
INSERT INTO permission_version (
    tenant_id, version_no, trigger_entity_type, trigger_entity_id, remark,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    {{TENANT_ID}}, 1, 'SYSTEM_INIT', 0, '租户初始化版本',
    {{CREATED_BY}}, {{CREATED_BY}}, NULL, now(), now(), NULL, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM permission_version pv
    WHERE pv.tenant_id = {{TENANT_ID}}
      AND pv.version_no = 1
      AND pv.delete_flag = 0
);
