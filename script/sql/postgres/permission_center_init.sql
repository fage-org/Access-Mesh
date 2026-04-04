-- =============================================================================
-- 通用权限中心 - Phase 1 初始化数据（PostgreSQL）
-- 固定示例租户：tenant_id = 1
-- 说明：
--   1. 本脚本依赖 permission_center_schema.sql 已执行
--   2. 仅初始化类型定义、首批操作、示例业务域、默认域配置模板、初始权限版本
--   3. 首批 role_type 仅初始化：系统管理员 = 1
--   4. 示例业务域固定为：SYSTEM_MANAGEMENT（系统管理）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 类型定义：user_type
-- -----------------------------------------------------------------------------
INSERT INTO type_definition (
    tenant_id, biz_domain_id, type_key, type_value, name, description, sort_order,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    1, NULL, t.type_key, t.type_value, t.name, t.description, t.sort_order,
    1, 1, NULL, now(), now(), NULL, 0
FROM (
    VALUES
        ('user_type', 1, 'USER', '人类用户主体', 1),
        ('user_type', 2, 'SERVICE', '服务主体', 2),
        ('user_type', 3, 'DELEGATED', '委托上下文主体', 3)
) AS t(type_key, type_value, name, description, sort_order)
WHERE NOT EXISTS (
    SELECT 1
    FROM type_definition td
    WHERE td.tenant_id = 1
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
    1, NULL, t.type_key, t.type_value, t.name, t.description, t.sort_order,
    1, 1, NULL, now(), now(), NULL, 0
FROM (
    VALUES
        ('role_type', 1, '系统管理员', '系统管理员角色类型', 1)
) AS t(type_key, type_value, name, description, sort_order)
WHERE NOT EXISTS (
    SELECT 1
    FROM type_definition td
    WHERE td.tenant_id = 1
      AND td.biz_domain_id IS NULL
      AND td.type_key = t.type_key
      AND td.type_value = t.type_value
      AND td.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 2. 类型定义：resource_type
--    按当前阶段决策，按 1/2/3/4 顺序初始化
-- -----------------------------------------------------------------------------
INSERT INTO type_definition (
    tenant_id, biz_domain_id, type_key, type_value, name, description, sort_order,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    1, NULL, t.type_key, t.type_value, t.name, t.description, t.sort_order,
    1, 1, NULL, now(), now(), NULL, 0
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
    WHERE td.tenant_id = 1
      AND td.biz_domain_id IS NULL
      AND td.type_key = t.type_key
      AND td.type_value = t.type_value
      AND td.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 3. 首批操作定义
--    固定 binary_bit + inherit_mask 规则：
--      VIEW       = 1, inherit_mask = 0
--      EDIT       = 4, inherit_mask = 1   (沿用设计文档示例：EDIT 包含 VIEW)
--      ACCESS     = 8, inherit_mask = 0   (API 默认操作编码)
--      DATA_READ  = 16, inherit_mask = 0
--      DATA_EXPORT= 32, inherit_mask = 0
-- -----------------------------------------------------------------------------
INSERT INTO operation_permission (
    tenant_id, resource_type, code, name, binary_bit, inherit_mask,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    1, s.resource_type, s.code, s.name, s.binary_bit, s.inherit_mask,
    1, 1, NULL, now(), now(), NULL, 0
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
    WHERE op.tenant_id = 1
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
    1, 'SYSTEM_MANAGEMENT', '系统管理', 'Phase 1 系统管理业务域',
    1, 1, NULL, now(), now(), NULL, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM biz_domain bd
    WHERE bd.tenant_id = 1
      AND bd.code = 'SYSTEM_MANAGEMENT'
      AND bd.delete_flag = 0
);

-- -----------------------------------------------------------------------------
-- 5. 默认域配置模板
--    先按“系统管理”业务域初始化最大权限模板：
--      1. domain_scope_config：放开 ROLE_TYPE / RESOURCE_TYPE / OPERATION
--      2. domain_relation_config：系统管理员角色类型可关联全部资源类型
--      3. domain_scope_binding：默认绑定全部操作定义
-- -----------------------------------------------------------------------------
WITH system_domain AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = 1
      AND code = 'SYSTEM_MANAGEMENT'
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
    1, d.id, 'ROLE_TYPE', r.scope_ref_id,
    1, 1, NULL, now(), now(), NULL, 0
FROM system_domain d
CROSS JOIN role_scope_seed r
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_config cfg
    WHERE cfg.tenant_id = 1
      AND cfg.biz_domain_id = d.id
      AND cfg.scope_type = 'ROLE_TYPE'
      AND cfg.scope_ref_id = r.scope_ref_id
      AND cfg.delete_flag = 0
);

WITH system_domain AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = 1
      AND code = 'SYSTEM_MANAGEMENT'
      AND delete_flag = 0
),
resource_scope_seed AS (
    SELECT 1 AS scope_ref_id UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4
),
operation_scope_seed AS (
    SELECT id AS scope_ref_id
    FROM operation_permission
    WHERE tenant_id = 1
      AND delete_flag = 0
      AND code IN ('VIEW', 'EDIT', 'ACCESS', 'DATA_READ', 'DATA_EXPORT')
)
INSERT INTO domain_scope_config (
    tenant_id, biz_domain_id, scope_type, scope_ref_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    1, d.id, 'RESOURCE_TYPE', r.scope_ref_id,
    1, 1, NULL, now(), now(), NULL, 0
FROM system_domain d
CROSS JOIN resource_scope_seed r
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_config cfg
    WHERE cfg.tenant_id = 1
      AND cfg.biz_domain_id = d.id
      AND cfg.scope_type = 'RESOURCE_TYPE'
      AND cfg.scope_ref_id = r.scope_ref_id
      AND cfg.delete_flag = 0
);

WITH system_domain AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = 1
      AND code = 'SYSTEM_MANAGEMENT'
      AND delete_flag = 0
),
operation_scope_seed AS (
    SELECT id AS scope_ref_id
    FROM operation_permission
    WHERE tenant_id = 1
      AND delete_flag = 0
      AND code IN ('VIEW', 'EDIT', 'ACCESS', 'DATA_READ', 'DATA_EXPORT')
)
INSERT INTO domain_scope_config (
    tenant_id, biz_domain_id, scope_type, scope_ref_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    1, d.id, 'OPERATION', o.scope_ref_id,
    1, 1, NULL, now(), now(), NULL, 0
FROM system_domain d
CROSS JOIN operation_scope_seed o
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_config cfg
    WHERE cfg.tenant_id = 1
      AND cfg.biz_domain_id = d.id
      AND cfg.scope_type = 'OPERATION'
      AND cfg.scope_ref_id = o.scope_ref_id
      AND cfg.delete_flag = 0
);

WITH system_domain AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = 1
      AND code = 'SYSTEM_MANAGEMENT'
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
    1, d.id, 'ROLE_RESOURCE', rr.role_type_value, rr.resource_type_value,
    1, 1, NULL, now(), now(), NULL, 0
FROM system_domain d
CROSS JOIN role_resource_seed rr
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_relation_config cfg
    WHERE cfg.tenant_id = 1
      AND cfg.biz_domain_id = d.id
      AND cfg.relation_type = 'ROLE_RESOURCE'
      AND cfg.left_ref_id = rr.role_type_value
      AND cfg.right_ref_id = rr.resource_type_value
      AND cfg.delete_flag = 0
);

WITH system_domain AS (
    SELECT id
    FROM biz_domain
    WHERE tenant_id = 1
      AND code = 'SYSTEM_MANAGEMENT'
      AND delete_flag = 0
),
operation_binding_seed AS (
    SELECT id AS bound_entity_id
    FROM operation_permission
    WHERE tenant_id = 1
      AND delete_flag = 0
      AND code IN ('VIEW', 'EDIT', 'ACCESS', 'DATA_READ', 'DATA_EXPORT')
)
INSERT INTO domain_scope_binding (
    tenant_id, biz_domain_id, bound_type, bound_entity_id,
    created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
)
SELECT
    1, d.id, 'OPERATION', o.bound_entity_id,
    1, 1, NULL, now(), now(), NULL, 0
FROM system_domain d
CROSS JOIN operation_binding_seed o
WHERE NOT EXISTS (
    SELECT 1
    FROM domain_scope_binding cfg
    WHERE cfg.tenant_id = 1
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
    1, 1, 'SYSTEM_INIT', 0, 'Phase 1 初始化版本',
    1, 1, NULL, now(), now(), NULL, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM permission_version pv
    WHERE pv.tenant_id = 1
      AND pv.version_no = 1
      AND pv.delete_flag = 0
);
