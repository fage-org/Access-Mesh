-- =============================================================================
-- *** SUPERSEDED（2026-08-12，T-ACCESS-002）***
-- 本文件已被 docs/design/schema/access-service.sql 取代（access-service-architecture §5.1），
-- 不再作为实现依据。保留仅供历史参考，禁止继续引用。
-- =============================================================================
-- =============================================================================
-- 种子数据：permission-center 内部资源类型的非预置操作码
-- =============================================================================
--
-- 背景说明：
--   permission-center 创建 resource_type 时自动预置 CRUD 四操作
--   （CREATE/VIEW/UPDATE/DELETE），但 MANAGE 等扩展操作码需手工注册。
--
--   ROLE:MANAGE 是双层门禁的关键操作码：
--     admin 层：ADMIN_ROLE:GRANT / ADMIN_ROLE:REVOKE（admin-service 入口门禁）
--     perm-center 内部：ROLE:MANAGE（PermissionGrantAppServiceImpl / UserManageAppServiceImpl
--                       / RoleManageAppServiceImpl 共 10+ 处引用）
--
--   若不注册，PermQueryEngine.resolveOperationId 返回 null → 全量 denied，
--   导致所有角色授权/回收操作被 permission-center 内部拒绝。
--
-- 关联：user-role-proxy-fix-plan.md EXT-6
-- =============================================================================

-- ---------------------------------------------------------------------------
-- ROLE 资源类型的 MANAGE 操作码
-- 调用方：RoleManageAppServiceImpl / PermissionGrantAppServiceImpl / UserManageAppServiceImpl
-- ---------------------------------------------------------------------------

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'MANAGE', '管理', 16, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ROLE' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;
