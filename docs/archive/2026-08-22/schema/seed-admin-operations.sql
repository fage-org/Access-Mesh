-- =============================================================================
-- *** SUPERSEDED（2026-08-12，T-ACCESS-002）***
-- 本文件已被 docs/design/schema/access-service.sql 取代（access-service-architecture §5.1），
-- 不再作为实现依据。保留仅供历史参考，禁止继续引用。
-- =============================================================================
-- =============================================================================
-- 种子数据：admin-service 资源类型的非预置操作码
-- =============================================================================
--
-- 背景说明：
--   permission-center 创建 resource_type 时自动预置 CRUD 四操作
--   （CREATE/VIEW/UPDATE/DELETE，bit=1/2/4/8，见 permission-center.sql §5 注释）。
--   v1.4 起词法统一为 VIEW（原 READ 为历史命名，已弃用）。
--   AdminOperationCode.java 定义了更多操作码（ENABLE、RESET_PASSWORD、
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
--   inherit_mask：读类操作=0，写类操作继承 VIEW（mask=2，与预置 UPDATE 一致）。
--   v1.4 起 (tenant_id, resource_type, binary_bit) 唯一索引强制同位单 code，
--   避免「同 bit 多 code」反查歧义（见 OperationPermissionUtils.findByResourceTypeAndBinaryBit）。
--
-- 租户：
--   默认 tenant_id=1，多租户部署时按需复制。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- ADMIN_ORG（组织/岗位管理）—— 扩展操作码
-- 调用方：OrgServiceImpl (CREATE_POSITION/UPDATE_POSITION/DELETE_POSITION)
--         UserOrgServiceImpl (MANAGE_MEMBER/ASSIGN_POSITION_USER)
--
-- orgType → 操作码映射（声明式，见 OrgOperationCodeMapper）：
--   orgType         含义      CREATE              UPDATE              DELETE              成员关系                查看
--   ──────────      ────      ──────              ──────              ──────              ────────────────       ──────
--   null / "1"      普通组织  CREATE              UPDATE              DELETE              MANAGE_MEMBER          VIEW
--   "2" / "POSITION" 岗位     CREATE_POSITION     UPDATE_POSITION     DELETE_POSITION     ASSIGN_POSITION_USER   VIEW_POSITION
--
-- v1.4 变更：
--   - 普通组织成员关系从 UPDATE 拆出 MANAGE_MEMBER（与 ASSIGN_POSITION_USER 同构）
--   - 岗位 Tab 查看从 ADMIN_MENU:VIEW 拆出 ADMIN_ORG:VIEW_POSITION（VIEW 类细化到资源类型）
--   - 普通组织查看注册为 ADMIN_ORG:VIEW
-- ---------------------------------------------------------------------------

-- 普通组织 VIEW（v1.4 VIEW 类细化到资源类型）
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'VIEW', '查看组织', 2, 0, 0, 0, 0  -- VIEW 占用 bit=2（v1.4 词法统一，原 READ 为历史命名；inherit_mask=0 因 VIEW 自身就是读类）
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

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

-- 普通组织成员关系（v1.4 从 UPDATE 拆出，与 ASSIGN_POSITION_USER 同构）
-- 适用 orgType: null / "1" / "ORG"（普通组织），见 OrgOperationCodeMapper.resolveForUserOrg
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'MANAGE_MEMBER', '管理组织成员', 256, 2, 0, 0, 0
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- 岗位 Tab 查看（v1.4 VIEW 类细化到资源类型，与 ADMIN_ORG:VIEW 解耦）
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'VIEW_POSITION', '查看岗位', 512, 0, 0, 0, 0  -- orgType="2"/"POSITION"，inherit_mask=0 读类无需继承
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_ORG' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- ---------------------------------------------------------------------------
-- ADMIN_USER（用户管理）—— 扩展操作码
-- 调用方：UserServiceImpl (ENABLE/RESET_PASSWORD)
--
-- v1.4 变更：
--   - DISABLE 已合并入 ENABLE（toggle 语义，UI 同一控件无独立配权必要）
--   - 用户列表查看注册为 ADMIN_USER:VIEW（v1.4 VIEW 类细化到资源类型）
-- ---------------------------------------------------------------------------

-- 用户列表查看（v1.4 VIEW 类细化到资源类型）
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'VIEW', '查看用户', 2, 0, 0, 0, 0  -- VIEW 占用 bit=2（v1.4 词法统一，原 READ 为历史命名）
FROM type_definition td
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = 'ADMIN_USER' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'ENABLE', '启用/禁用用户', 16, 2, 0, 0, 0  -- v1.4 toggle 语义，DISABLE 已合并
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
-- 调用方：JobServiceImpl (ENABLE/TRIGGER)
--
-- v1.4 变更：DISABLE 已合并入 ENABLE（toggle 语义）
-- ---------------------------------------------------------------------------

INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, 'ENABLE', '启用/禁用任务', 16, 2, 0, 0, 0  -- v1.4 toggle 语义
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
