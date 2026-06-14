-- =============================================================================
-- 种子数据：admin-service 资源类型的非预置操作码
-- =============================================================================
--
-- 背景说明：
--   permission-center 创建 resource_type 时自动预置 CRUD 四操作
--   （CREATE/READ/UPDATE/DELETE，bit=1/2/4/8，见 permission-center.sql §5 注释）。
--   但 AdminOperationCode.java 定义了更多操作码（ENABLE、RESET_PASSWORD、
--   CREATE_POSITION 等），这些不在自动预置范围内，必须手工注册到
--   operation_permission 表。
--
--   若不注册，PermQueryEngine.resolveOperationId 返回 null → 全量 denied
--   （见 PermQueryEngine.java:273-275），导致真实环境所有非 CRUD 写操作被拒绝。
--
-- 注册原则：
--   仅注册有实际调用方的操作码（grep AdminOperationCode / AdminResourceType
--   交叉核对），无调用方的不种，避免误导。
--
-- 执行时机：
--   在 permission-center.sql 建表后、admin-service 首次启动前执行。
--   要求 type_definition 中已有对应的 resource_type 行（type_key='resource_type'），
--   否则子查询返回空、INSERT 跳过（ON CONFLICT DO NOTHING 兜底）。
--
-- binary_bit 分配原则：
--   预置 CRUD 占 bit 1/2/4/8，非预置从 bit 16 开始（留间隔给未来扩展）。
--   inherit_mask：读类操作=0，写类操作继承 READ（mask=2，与预置 UPDATE 一致）。
--
-- 租户：
--   默认 tenant_id=1，多租户部署时按需复制。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- ADMIN_ORG（组织/岗位管理）—— 扩展操作码
-- 调用方：OrgServiceImpl (CREATE_POSITION/UPDATE_POSITION/DELETE_POSITION)
--         UserOrgServiceImpl (ASSIGN_POSITION_USER)
--
-- orgType → 操作码映射（声明式，见 OrgOperationCodeMapper）：
--   orgType         含义      CREATE              UPDATE              DELETE              成员关系(UPDATE)
--   ──────────      ────      ──────              ──────              ──────              ────────────────
--   null / "1"      普通组织  CREATE              UPDATE              DELETE              UPDATE
--   "2" / "POSITION" 岗位     CREATE_POSITION     UPDATE_POSITION     DELETE_POSITION     ASSIGN_POSITION_USER
-- ---------------------------------------------------------------------------

-- 岗位 CRUD 精化操作码（v1.3 操作码精化）
-- 适用 orgType: "2" / "POSITION"（岗位），见 OrgOperationCodeMapper
-- 详见 docs/design/org-user-permission-contract.md §4 D 区 + §5 备注 ⁴
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'CREATE_POSITION', '创建岗位', 16, 2, 0, 0, 0  -- orgType="2"/"POSITION"
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'UPDATE_POSITION', '编辑/移动/启停岗位', 32, 2, 0, 0, 0  -- orgType="2"/"POSITION"
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'DELETE_POSITION', '删除岗位', 64, 2, 0, 0, 0  -- orgType="2"/"POSITION"
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'ASSIGN_POSITION_USER', '岗位用户挂载/卸载/设主', 128, 2, 0, 0, 0  -- orgType="2"/"POSITION"
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- ---------------------------------------------------------------------------
-- ADMIN_USER（用户管理）—— 扩展操作码
-- 调用方：UserServiceImpl (ENABLE/DISABLE/RESET_PASSWORD)
-- ---------------------------------------------------------------------------

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'ENABLE', '启用用户', 16, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_USER' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'DISABLE', '禁用用户', 32, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_USER' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'RESET_PASSWORD', '重置密码', 64, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_USER' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- ---------------------------------------------------------------------------
-- ADMIN_ROLE（角色管理）—— 扩展操作码
-- 调用方：RoleProxyServiceImpl (GRANT/REVOKE)
-- ---------------------------------------------------------------------------

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'GRANT', '授权', 16, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ROLE' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'REVOKE', '撤销权限', 32, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ROLE' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- ---------------------------------------------------------------------------
-- ADMIN_NOTICE（通知公告）—— 扩展操作码
-- 调用方：NoticeServiceImpl (PUBLISH)
-- ---------------------------------------------------------------------------

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'PUBLISH', '发布公告', 16, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_NOTICE' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- ---------------------------------------------------------------------------
-- ADMIN_JOB（定时任务）—— 扩展操作码
-- 调用方：JobServiceImpl (ENABLE/DISABLE/TRIGGER)
-- ---------------------------------------------------------------------------

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'ENABLE', '启用任务', 16, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_JOB' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'DISABLE', '禁用任务', 32, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_JOB' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'TRIGGER', '触发执行', 64, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_JOB' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- ---------------------------------------------------------------------------
-- ADMIN_ORG_TREE_CONFIG（组织树配置）—— 扩展操作码
-- 调用方：OrgTreeConfigServiceImpl (TOGGLE)
-- ---------------------------------------------------------------------------

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'TOGGLE', '切换默认树/单关联', 16, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG_TREE_CONFIG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- ---------------------------------------------------------------------------
-- 未注册的操作码（AdminOperationCode 中已定义但无实际调用方，暂不注册）
-- ---------------------------------------------------------------------------
-- ADMIN_MENU:PUBLISH     — 无调用方（PUBLISH 实际在 ADMIN_NOTICE 上）
-- ADMIN_SYNC_TASK:TRIGGER — 无调用方（SYNC_TASK 只用 VIEW/UPDATE/DELETE）
-- ADMIN_SYNC_TASK:TOGGLE  — 无调用方（TOGGLE 实际在 ADMIN_ORG_TREE_CONFIG 上）
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 备注：permission-center 内部资源类型（USER/ROLE/RESOURCE/SERVICE/DOMAIN 等）
-- 使用 OperationCodeConstants（CREATE/VIEW/MANAGE/UPDATE/DELETE/ASSIGN/REVOKE/SYNC/...），
-- 其操作码注册由 permission-center 自身在创建 resource_type 时或通过管理 API 完成，
-- 不在此种子脚本范围内。
-- ---------------------------------------------------------------------------
