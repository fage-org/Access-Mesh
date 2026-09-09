-- =============================================================================
-- access-service 最终 DDL（权威文件，target: access_db.public）
-- =============================================================================
-- 本文件是 access-service 数据库结构的唯一权威定义（access-service-architecture §5.1）。
-- 取代：admin-service.sql、permission-center.sql、seed-admin-operations.sql、seed-perm-operations.sql
--       （四份旧文件已随 T-ACCESS-012 归档至 docs/archive/2026-08-22/schema/，不再作为实现依据）
--
-- 范围：33 张表
--   admin 域 14 张（sys_*，原 17 张：sys_config/sys_audit_log 并入合并表；sys_sync_task 已随 T-ACCESS-005 退役）
--   permission 域 16 张（原 18 张：system_config/operation_log 由合并表承接）
--   合并表 2 张（system_config、operation_log）
--   基础设施 1 张（sys_task_execution，T-ACCESS-009 任务租约预建）
--
-- 合并说明（access-service-architecture §5.2）：
--   system_config = sys_config + 原 system_config（字段取超集；tenant_id+config_key 唯一；
--                   键使用 admin.*/permission.*/access.* 命名空间；存量种子键已迁移至 admin.* 前缀
--                   （T-ACCESS-007），见下方 INSERT 种子，新增键经 upsert 入口 fail-closed 校验前缀）
--   operation_log  = sys_audit_log + 原 operation_log（字段取超集；target_id 使用字符串；
--                   模块标识 ADMIN/PERMISSION/ACCESS；request_body 停用恒 NULL，T-ACCESS-025 参数序列化收敛）
--
-- 软删约定（与旧库一致）：
--   delete_flag BIGINT：0 = 未删除，删除时填本行 id（确保唯一约束不冲突）
--   deleted_at TIMESTAMPTZ：纯审计字段，记录删除时间，不参与索引条件
--   所有唯一索引和业务查询统一使用 WHERE delete_flag = 0
--   例外：sys_login_log、operation_log、sys_job_log、permission_change_log 不做软删除
--
-- 本地投影所有权（access-service-architecture §4.2）：
--   abstract_user / abstract_role / user_role 增加可空列 owner_service_code：
--     'access-service' → 管理事实派生的本地投影，禁止权限管理 API 直接修改
--     NULL            → 非本地管理投影（人工维护 / 外部同步，外部同步所有权以 sync_metadata 为准）
--   resource_entity 复用既有 owner_service_code/maintain_source 字段，不复制到其他表
--
-- 种子数据（各组计数以 AccessServiceSchemaH2Test 断言为准，注释不复制数字——project-rules §文档治理去计数化）：
--   sys_oauth2_client / system_config：原样保留（键名保持现状）
--   type_definition：user_type/role_type/resource_type 三组；type_value 为权威数值，按
--     T-ACCESS-016 §13 定稿重编（T-ACCESS-018 落地）；代码不硬编码数值，运行时经
--     TypeResolutionService 动态解析；退役值 16/17/18/19/22/28 不复用
--   operation_permission：三组——①全部静态 resource_type 各预置 CRUD 四操作（CREATE bit=1/
--     VIEW bit=2/UPDATE bit=4 继承2/DELETE bit=8 继承2，CROSS JOIN 派生，DDL 直接种入的类型
--     不会触发运行时生成必须在初始化阶段种入）；②非预置扩展操作（ORG 六码同名同 bit 迁移自
--     ADMIN_ORG、USER:ENABLE bit 重分配 32、USER:RESET_PASSWORD bit 64 不变、
--     ADMIN_ROLE:GRANT/REVOKE 零消费者删除不迁移；bit 与 CRUD 不冲突）；③权限中心运行时必需
--     操作（代码实际校验的 MANAGE/ASSIGN/REVOKE/SYNC/MANAGE_API_MAPPING/SYNC_INTERFACE/ACCESS，
--     缺失时权限引擎 fail-closed 全量拒绝）
--
-- 执行：从空 PostgreSQL 一次性执行本文件即可获得完整结构；本阶段不引入 migration 框架。
--
-- type_value 终值分配表（T-ACCESS-016 定稿 2026-08-23，T-ACCESS-018 已落地重编；
-- 本注释是终态权威，INSERT 与本表一致，退役值不复用、后续新类型取现用最大值 +1 顺延）：
--   user_type：USER=1 / SERVICE=2 / LOCAL_USER=3（原 ADMIN_USER 更名，值不变；
--     主体来源语义，不再兼任资源类型；subjectTypeCode 与保留业务键 subject 侧同步更名，无兼容别名。
--     保留业务键终态（T-ACCESS-016）：subject 侧 ADMIN_USER→LOCAL_USER；role 侧 ORG|POSITION 与
--     SYS_USER_ORG 不变；resource 侧取消类型级保留——USER/MENU 为公共基础类型，本地投影行改按
--     所有权保护（外部 sync UPSERT/DISABLE/DELETE 任一 mutation 分支前置 owner=access-service 即 20045，
--     新建撞 code 由 uk 兜底），
--     管理入口类型保留清单换值 {USER, ORG, MENU}）
--   role_type（不变）：ORG=1 / POSITION=2 / PERSONAL=3 / GROUP_ROLE=5 / BASIC_ROLE=6
--     （role_type.ORG=1 与 resource_type.ORG=29 属不同 type_key，不冲突）
--   resource_type（终态）：MENU=1 / BUTTON=2 / API=3 / DATA=4 / ROLE=5 / USER=6 /
--     RESOURCE=7 / SERVICE=8 / DOMAIN=9 / TYPE_DEFINITION=10 / SYSTEM_CONFIG=11 /
--     OPERATION=12 / CONDITION=13 / CONFLICT_RULE=14 / DEPENDENCY=15 / ADMIN_DICT=20 /
--     ADMIN_DICT_DATA=21 / ADMIN_OAUTH2_CLIENT=23 / ADMIN_NOTICE=24 / ADMIN_FILE=25 /
--     ADMIN_JOB=26 / ADMIN_ORG_TREE_CONFIG=27 / ORG=29（新值，退役值 17 不复用）/
--     OPERATION_LOG=30（操作日志查询门禁，T-PERM-025 审计分离）、
--     PERMISSION_CHANGE_LOG=31（权限变更日志查询门禁，T-PERM-032 审计分离）
--   收敛映射：ADMIN_USER(16)→USER(6)、ADMIN_ROLE(18)→ROLE(5)、ADMIN_MENU(19)→MENU(1)、
--     ADMIN_CONFIG(22)→SYSTEM_CONFIG(11)、ADMIN_ORG(17)→ORG(29)；ADMIN_SYNC_TASK(28) 删除；
--     退役段 16/17/18/19/22/28 不复用；ADMIN_DICT/ADMIN_DICT_DATA/ADMIN_OAUTH2_CLIENT/
--     ADMIN_NOTICE/ADMIN_FILE/ADMIN_JOB/ADMIN_ORG_TREE_CONFIG 无重复对象不改名
--   扩展操作归属与 bit 终值（uk_operation_permission_typed_bit 要求同类型 code/bit 均唯一）：
--     ADMIN_USER:ENABLE bit16→USER:ENABLE bit32（USER 下 16 被 MANAGE 占用）；
--     ADMIN_USER:RESET_PASSWORD bit64→USER:RESET_PASSWORD bit64（不变，USER 下空闲）；
--     ADMIN_ROLE:GRANT/REVOKE 删除不迁移（零生产消费者，职责由 ROLE:MANAGE 承担；bit 与
--     ROLE:MANAGE@16/ASSIGN@32 冲突、REVOKE 与既有 ROLE:REVOKE@64 code 冲突）；
--     ADMIN_ORG:CREATE_POSITION 等 6 码同名同 bit 迁移 ORG（16-512 空闲无冲突）；
--     ADMIN_ORG:VIEW 与 ADMIN_USER:VIEW 由 CRUD 预置 VIEW 覆盖（去重语义保持）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. sys_oauth2_client - OAuth2 客户端配置
-- -----------------------------------------------------------------------------
CREATE TABLE sys_oauth2_client (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    client_id         VARCHAR(128) NOT NULL,
    client_secret     VARCHAR(256) NOT NULL,
    client_name       VARCHAR(128) NOT NULL,
    grant_types       VARCHAR(256) NOT NULL,
    redirect_uris     VARCHAR(1024),
    scopes            VARCHAR(512),
    audiences         VARCHAR(512),
    access_token_ttl  INT NOT NULL DEFAULT 7200,
    refresh_token_ttl INT NOT NULL DEFAULT 2592000,
    status            SMALLINT NOT NULL DEFAULT 1,
    created_by        BIGINT,
    updated_by        BIGINT,
    deleted_by        BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    delete_flag       BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_oauth2_client_id ON sys_oauth2_client (client_id) WHERE delete_flag = 0;

COMMENT ON TABLE sys_oauth2_client IS 'OAuth2 客户端配置';
COMMENT ON COLUMN sys_oauth2_client.client_id IS '客户端标识，全局唯一';
COMMENT ON COLUMN sys_oauth2_client.client_secret IS '客户端密钥（BCrypt 加密存储）';
COMMENT ON COLUMN sys_oauth2_client.grant_types IS '允许的授权模式（逗号分隔）：authorization_code,password,client_credentials,refresh_token';
COMMENT ON COLUMN sys_oauth2_client.redirect_uris IS '允许的回调地址（逗号分隔）';
COMMENT ON COLUMN sys_oauth2_client.scopes IS '允许的权限范围（逗号分隔）';
COMMENT ON COLUMN sys_oauth2_client.audiences IS '允许的令牌受众/目标资源服务器标识（逗号分隔，T-ACCESS-013）；配置后签发的访问令牌写入 aud claim，业务开放路径按 audience 强制校验';
COMMENT ON COLUMN sys_oauth2_client.access_token_ttl IS 'Access Token 有效期（秒），默认 7200（2小时）';
COMMENT ON COLUMN sys_oauth2_client.refresh_token_ttl IS 'Refresh Token 有效期（秒），默认 2592000（30天）';
COMMENT ON COLUMN sys_oauth2_client.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN sys_oauth2_client.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- 预置客户端数据
INSERT INTO sys_oauth2_client (tenant_id, client_id, client_secret, client_name, grant_types, redirect_uris, scopes, audiences, access_token_ttl, refresh_token_ttl, status, created_by, created_at, updated_at)
VALUES
    (1, 'admin-web',        '$2a$10$PLACEHOLDER_HASH_1', '管理端前端',  'authorization_code,password,refresh_token', 'http://localhost:3000/callback', 'all', 'access-service', 7200, 2592000, 1, 0, now(), now()),
    (1, 'example-web',      '$2a$10$PLACEHOLDER_HASH_2', '演示端前端',  'authorization_code,password,refresh_token', 'http://localhost:3001/callback', 'all', 'access-service', 7200, 2592000, 1, 0, now(), now()),
    (1, 'internal-service', '$2a$10$PLACEHOLDER_HASH_3', '服务间调用',  'client_credentials',                        NULL,                            'all', 'access-service', 7200, 0,       1, 0, now(), now());

-- -----------------------------------------------------------------------------
-- 2. sys_login_log - 登录日志（不做软删除，永久保留）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_login_log (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    user_id     BIGINT,
    username    VARCHAR(64),
    login_type  VARCHAR(32) NOT NULL,
    client_id   VARCHAR(128),
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(512),
    location    VARCHAR(256),
    status      SMALLINT NOT NULL DEFAULT 0,
    fail_reason VARCHAR(256),
    login_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_log_tenant_time ON sys_login_log (tenant_id, login_at DESC);
CREATE INDEX idx_login_log_username ON sys_login_log (tenant_id, username);

COMMENT ON TABLE sys_login_log IS '登录日志，不做软删除，永久保留';
COMMENT ON COLUMN sys_login_log.login_type IS '登录方式：PASSWORD/SMS/OAUTH2';
COMMENT ON COLUMN sys_login_log.status IS '结果：0=失败，1=成功';

-- -----------------------------------------------------------------------------
-- 3. sys_user - 用户表（事实源，投影到权限域 abstract_user）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    username        VARCHAR(64) NOT NULL,
    password        VARCHAR(256) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    phone           VARCHAR(32),
    email           VARCHAR(128),
    avatar          VARCHAR(512),
    gender          SMALLINT NOT NULL DEFAULT 0,
    status          SMALLINT NOT NULL DEFAULT 1,
    user_type       INT NOT NULL DEFAULT 1,
    force_reset_pwd BOOLEAN NOT NULL DEFAULT true,
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    delete_flag     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_user_username ON sys_user (tenant_id, username) WHERE delete_flag = 0;
CREATE UNIQUE INDEX uk_user_phone ON sys_user (tenant_id, phone) WHERE delete_flag = 0 AND phone IS NOT NULL;

COMMENT ON TABLE sys_user IS '用户表，access-service admin 域事实源；默认组织树是用户目录/身份池，负责用户生命周期。id = abstract_user.id 同值（主体 ID，T-ORG-001 已落地）：列 BIGINT 显式赋值（无自增），本地用户创建先 nextval(pg_get_serial_sequence(abstract_user.id)) 预取主体 ID N，再显式插 abstract_user(id=N, external_id=N) 与本表(id=N)；外部主体仅插 abstract_user（自增取号），两向创建顺序均不碰撞（architecture §12.2）';
COMMENT ON COLUMN sys_user.username IS '登录账号，租户内唯一';
COMMENT ON COLUMN sys_user.password IS '密码（BCrypt 加密，前端 SHA256 摘要传输）';
COMMENT ON COLUMN sys_user.gender IS '性别：0=未知，1=男，2=女';
COMMENT ON COLUMN sys_user.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN sys_user.user_type IS '用户类型（对应 type_definition user_type 的 type_value），默认 1=人员（USER）；本地登录用户为 3=LOCAL_USER（主体 ID 与 abstract_user 同源，T-ORG-001）';
COMMENT ON COLUMN sys_user.force_reset_pwd IS '是否需要强制修改密码（首次登录/管理员重置后）';
COMMENT ON COLUMN sys_user.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 4. sys_org - 统一组织表（部门/岗位/团队同表，org_type 仅标签）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_org (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    parent_id    BIGINT,
    org_type     VARCHAR(32),
    code         VARCHAR(64) NOT NULL,
    name         VARCHAR(128) NOT NULL,
    path         VARCHAR(512),
    level        INT NOT NULL DEFAULT 1,
    sort_order   INT NOT NULL DEFAULT 0,
    leader_id    BIGINT,
    status       SMALLINT NOT NULL DEFAULT 1,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    delete_flag  BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_org_code ON sys_org (tenant_id, code) WHERE delete_flag = 0;
CREATE INDEX idx_org_parent ON sys_org (tenant_id, parent_id) WHERE delete_flag = 0;
CREATE INDEX idx_org_path ON sys_org (tenant_id, path) WHERE delete_flag = 0;

COMMENT ON TABLE sys_org IS '统一组织表：部门/岗位/团队同表；默认组织树承担用户目录语义，非默认树只管理成员关系；组织/岗位同步为 ORG resource_entity（管理权限，T-ACCESS-018 收敛，原 ADMIN_ORG 并入）和 ORG/POSITION abstract_role（角色容器），均使用业务键定位，不存 permission 域内部 ID';
COMMENT ON COLUMN sys_org.parent_id IS '父节点ID，NULL=根节点';
COMMENT ON COLUMN sys_org.org_type IS '组织类型标签（字典管理），仅分类用';
COMMENT ON COLUMN sys_org.code IS '组织编码，租户内唯一';
COMMENT ON COLUMN sys_org.path IS '物化路径（如 /1/3/7/），加速树查询';
COMMENT ON COLUMN sys_org.level IS '层级深度（根节点=1），最大 10 层';
COMMENT ON COLUMN sys_org.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 5. sys_org_tree_config - 组织树配置
-- -----------------------------------------------------------------------------
CREATE TABLE sys_org_tree_config (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    root_org_id  BIGINT NOT NULL,
    tree_name    VARCHAR(128) NOT NULL,
    tree_type    VARCHAR(32) NOT NULL DEFAULT 'ORG',
    is_default   BOOLEAN NOT NULL DEFAULT false,
    single_assoc BOOLEAN NOT NULL DEFAULT true,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    delete_flag  BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_tree_config_root ON sys_org_tree_config (tenant_id, root_org_id) WHERE delete_flag = 0;
CREATE UNIQUE INDEX uk_tree_config_default ON sys_org_tree_config (tenant_id) WHERE is_default = true AND delete_flag = 0;

COMMENT ON TABLE sys_org_tree_config IS '组织树配置：每棵树一条记录，绑定根节点';
COMMENT ON COLUMN sys_org_tree_config.root_org_id IS '根组织节点ID（sys_org.id，parent_id=NULL 的节点）';
COMMENT ON COLUMN sys_org_tree_config.tree_type IS '树类型：ORG=组织树，POSITION=职位树';
COMMENT ON COLUMN sys_org_tree_config.is_default IS '是否默认组织树（每租户最多一棵）；默认树即用户目录/身份池，不只是展示默认值';
COMMENT ON COLUMN sys_org_tree_config.single_assoc IS '是否单关联（用户在该树下只能属于一个节点）。POSITION 树始终 false';
COMMENT ON COLUMN sys_org_tree_config.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 6. sys_user_org - 用户-组织关联（多对多）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_user_org (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    org_id     BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_user_org ON sys_user_org (tenant_id, user_id, org_id) WHERE delete_flag = 0;
CREATE INDEX idx_user_org_user ON sys_user_org (tenant_id, user_id) WHERE delete_flag = 0;
CREATE INDEX idx_user_org_org ON sys_user_org (tenant_id, org_id) WHERE delete_flag = 0;

COMMENT ON TABLE sys_user_org IS '用户-组织关联：多对多；默认树关系表示用户目录归属，非默认树关系表示业务组织成员关系；当前未持久化 tree_config_id，树归属由 org_id 落在哪棵 sys_org_tree_config.root_org_id 子树下推导（组织树根不得重叠，否则树归属歧义）';
COMMENT ON COLUMN sys_user_org.is_primary IS '是否主组织；首期仅表示用户在默认组织树下的主归属';
COMMENT ON COLUMN sys_user_org.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 7. sys_menu - 菜单表（UI 路由元数据 + 关联资源 link，v3.5 菜单零权限化）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_menu (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    parent_id        BIGINT,
    display_name     VARCHAR(128) NOT NULL,
    path             VARCHAR(256),
    icon             VARCHAR(64),
    sort_order       INT NOT NULL DEFAULT 0,
    menu_type        VARCHAR(16) NOT NULL,   -- DIR/MENU/EXTERNAL/IFRAME/HIDDEN
    status           SMALLINT NOT NULL DEFAULT 1,  -- 0=DISABLED, 1=ENABLED
    resource_type    VARCHAR(64),            -- 关联业务资源类型（不参与鉴权决策）
    resource_code    VARCHAR(64),            -- 关联业务资源实例（不参与鉴权决策）
    source_service   VARCHAR(64),            -- 业务服务标识（链路追溯）
    created_by       BIGINT,
    updated_by       BIGINT,
    deleted_by       BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    delete_flag      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_menu_tenant_resource ON sys_menu (tenant_id, resource_type, resource_code) WHERE delete_flag = 0 AND resource_type IS NOT NULL;
CREATE UNIQUE INDEX uk_sys_menu_tenant_path ON sys_menu (tenant_id, path) WHERE delete_flag = 0 AND path IS NOT NULL;
CREATE INDEX idx_menu_parent ON sys_menu (tenant_id, parent_id) WHERE delete_flag = 0;

COMMENT ON TABLE sys_menu IS '菜单表：access-service admin 域事实源，仅承载 UI 路由元数据 + 关联资源 link（v3.5 菜单零权限化，不承载权限语义）';
COMMENT ON COLUMN sys_menu.menu_type IS '类型：DIR=目录，MENU=菜单，EXTERNAL=外链，IFRAME=嵌入，HIDDEN=隐藏路由（派生同 MENU，不进 menus[] 下发 hiddenRoutes[]）';
COMMENT ON COLUMN sys_menu.status IS '状态：0=DISABLED，1=ENABLED';
COMMENT ON COLUMN sys_menu.resource_type IS '关联业务资源类型（不参与鉴权决策，仅 link；v3.5 §4.1 派生公式用）';
COMMENT ON COLUMN sys_menu.resource_code IS '关联业务资源实例（不参与鉴权决策，仅 link）';
COMMENT ON COLUMN sys_menu.source_service IS '业务服务标识（链路追溯，替代原 service_code）';
COMMENT ON COLUMN sys_menu.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 8. sys_dict_type - 字典类型
-- -----------------------------------------------------------------------------
CREATE TABLE sys_dict_type (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    dict_type   VARCHAR(64) NOT NULL,
    dict_name   VARCHAR(128) NOT NULL,
    status      SMALLINT NOT NULL DEFAULT 1,
    remark      VARCHAR(512),
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_dict_type ON sys_dict_type (tenant_id, dict_type) WHERE delete_flag = 0;

COMMENT ON TABLE sys_dict_type IS '字典类型';
COMMENT ON COLUMN sys_dict_type.dict_type IS '字典类型编码，租户内唯一';
COMMENT ON COLUMN sys_dict_type.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN sys_dict_type.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 9. sys_dict_data - 字典数据
-- -----------------------------------------------------------------------------
CREATE TABLE sys_dict_data (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    dict_type   VARCHAR(64) NOT NULL,
    dict_label  VARCHAR(128) NOT NULL,
    dict_value  VARCHAR(128) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    css_class   VARCHAR(128),
    list_class  VARCHAR(128),
    is_default  BOOLEAN NOT NULL DEFAULT false,
    status      SMALLINT NOT NULL DEFAULT 1,
    remark      VARCHAR(512),
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dict_data_type ON sys_dict_data (tenant_id, dict_type) WHERE delete_flag = 0;

COMMENT ON TABLE sys_dict_data IS '字典数据';
COMMENT ON COLUMN sys_dict_data.dict_type IS '关联的字典类型编码';
COMMENT ON COLUMN sys_dict_data.dict_label IS '字典标签（显示值）';
COMMENT ON COLUMN sys_dict_data.dict_value IS '字典值（存储值）';
COMMENT ON COLUMN sys_dict_data.css_class IS '样式类名';
COMMENT ON COLUMN sys_dict_data.list_class IS '表格回显样式';
COMMENT ON COLUMN sys_dict_data.is_default IS '是否默认值';
COMMENT ON COLUMN sys_dict_data.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 10. sys_notice - 通知/公告
-- -----------------------------------------------------------------------------
CREATE TABLE sys_notice (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    notice_type  VARCHAR(32) NOT NULL,
    title        VARCHAR(256) NOT NULL,
    content      TEXT,
    target_type  VARCHAR(32) NOT NULL DEFAULT 'ALL',
    target_ids   JSONB,
    status       SMALLINT NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    delete_flag  BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notice_tenant_status ON sys_notice (tenant_id, status) WHERE delete_flag = 0;

COMMENT ON TABLE sys_notice IS '通知/公告';
COMMENT ON COLUMN sys_notice.notice_type IS '类型：ANNOUNCEMENT=公告，NOTIFICATION=通知';
COMMENT ON COLUMN sys_notice.target_type IS '目标类型：ALL=全员，ORG=指定组织，USER=指定用户';
COMMENT ON COLUMN sys_notice.target_ids IS '目标ID列表（ORG/USER 时有值），JSON 数组';
COMMENT ON COLUMN sys_notice.status IS '状态：0=草稿，1=已发布，2=已撤回';
COMMENT ON COLUMN sys_notice.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 11. sys_user_notice - 用户通知状态
-- -----------------------------------------------------------------------------
CREATE TABLE sys_user_notice (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    notice_id BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    is_read   BOOLEAN NOT NULL DEFAULT false,
    read_at   TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_user_notice ON sys_user_notice (tenant_id, notice_id, user_id);
CREATE INDEX idx_user_notice_user ON sys_user_notice (tenant_id, user_id, is_read);

COMMENT ON TABLE sys_user_notice IS '用户通知状态（已读/未读）';

-- -----------------------------------------------------------------------------
-- 12. sys_file - 文件元信息
-- -----------------------------------------------------------------------------
CREATE TABLE sys_file (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    original_name VARCHAR(256) NOT NULL,
    file_name     VARCHAR(256) NOT NULL,
    file_path     VARCHAR(512) NOT NULL,
    file_url      VARCHAR(1024),
    file_size     BIGINT NOT NULL DEFAULT 0,
    file_type     VARCHAR(128),
    bucket_name   VARCHAR(128),
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_file_tenant ON sys_file (tenant_id) WHERE delete_flag = 0;

COMMENT ON TABLE sys_file IS '文件元信息，实际文件存储在本地磁盘（file.storage.path，单实例约束：多实例部署下本地盘不可共享，T-ADMIN-023 登记）';
COMMENT ON COLUMN sys_file.original_name IS '原始文件名';
COMMENT ON COLUMN sys_file.file_name IS '存储文件名（UUID）';
COMMENT ON COLUMN sys_file.file_path IS '存储相对路径（{bizType}/yyyy/MM/dd/{uuid}{ext}，读取时经路径安全校验必须位于存储根内）';
COMMENT ON COLUMN sys_file.file_url IS '访问URL';
COMMENT ON COLUMN sys_file.file_size IS '文件大小（字节）';
COMMENT ON COLUMN sys_file.file_type IS 'MIME 类型';
COMMENT ON COLUMN sys_file.bucket_name IS '业务类型（文件夹）= resource_entity(ADMIN_FILE).code，文件夹实例级授权判定键（T-ADMIN-025）：投影由 bootstrap 预置 + 上传惰性登记产出，无投影的历史脏桶实例级校验 fail-closed 拒绝';
COMMENT ON COLUMN sys_file.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 13. sys_job - 定时任务配置
-- -----------------------------------------------------------------------------
CREATE TABLE sys_job (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    job_name        VARCHAR(128) NOT NULL,
    job_group       VARCHAR(64),
    invoke_target   VARCHAR(256) NOT NULL,
    cron_expression VARCHAR(128) NOT NULL,
    misfire_policy  SMALLINT NOT NULL DEFAULT 0,
    run_as_user_id  BIGINT,
    status          SMALLINT NOT NULL DEFAULT 1,
    remark          VARCHAR(512),
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    delete_flag     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_job_tenant_status ON sys_job (tenant_id, status) WHERE delete_flag = 0;

COMMENT ON TABLE sys_job IS '定时任务配置';
COMMENT ON COLUMN sys_job.invoke_target IS '调用目标（Bean 名称 + 方法，或 HTTP 回调 URL）';
COMMENT ON COLUMN sys_job.cron_expression IS 'Cron 表达式';
COMMENT ON COLUMN sys_job.misfire_policy IS '错过策略：0=忽略，1=立即执行一次';
COMMENT ON COLUMN sys_job.run_as_user_id IS '执行身份用户ID，NULL 时以任务创建者身份执行';
COMMENT ON COLUMN sys_job.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN sys_job.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 14. sys_job_log - 任务执行日志（不做软删除）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_job_log (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    job_id        BIGINT NOT NULL,
    job_name      VARCHAR(128),
    invoke_target VARCHAR(256),
    status        SMALLINT NOT NULL DEFAULT 0,
    message       TEXT,
    cost_time     INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_log_job ON sys_job_log (tenant_id, job_id, created_at DESC);

COMMENT ON TABLE sys_job_log IS '任务执行日志，不做软删除';
COMMENT ON COLUMN sys_job_log.status IS '执行结果：0=失败，1=成功';
COMMENT ON COLUMN sys_job_log.cost_time IS '耗时（毫秒）';

-- -----------------------------------------------------------------------------
-- 15. system_config - 系统配置（合并 sys_config + 原 system_config，字段取超集）
--     键使用 admin.* / permission.* / access.* 命名空间；存量种子键迁移至
--     admin.* 前缀（T-ACCESS-007），新增键经 upsert 入口校验前缀
--     （access-service-architecture §5.2）
-- -----------------------------------------------------------------------------
CREATE TABLE system_config (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    config_key   VARCHAR(128) NOT NULL,
    config_value JSONB NOT NULL DEFAULT '{}',
    description  VARCHAR(512),          -- 原 permission system_config
    config_name  VARCHAR(256),          -- 原 admin sys_config
    remark       VARCHAR(512),          -- 原 admin sys_config
    is_system    BOOLEAN NOT NULL DEFAULT false,  -- 原 admin sys_config
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    delete_flag  BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_system_config ON system_config (tenant_id, config_key) WHERE delete_flag = 0;

COMMENT ON TABLE system_config IS '系统配置，支持租户级覆盖；合并自 sys_config（admin）与 system_config（permission）：config_key 租户内唯一，config_value 为 JSONB；is_system=true 表示系统内置不可删除';
COMMENT ON COLUMN system_config.config_key IS '配置键；按 admin.*/permission.*/access.* 命名空间组织；存量种子键已迁移至 admin.* 前缀（T-ACCESS-007），新增键经 upsert 入口校验前缀';
COMMENT ON COLUMN system_config.config_value IS '配置值（JSON）';
COMMENT ON COLUMN system_config.description IS '配置描述（permission 侧语义）';
COMMENT ON COLUMN system_config.config_name IS '配置名称（admin 侧语义）';
COMMENT ON COLUMN system_config.is_system IS '是否系统内置（不可删除）';
COMMENT ON COLUMN system_config.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- 预置配置项（原 sys_config 种子，T-ACCESS-007 迁移至 admin.* 命名空间）
INSERT INTO system_config (tenant_id, config_key, config_value, config_name, is_system, created_by, created_at, updated_at)
VALUES
    (1, 'admin.LOGIN_CAPTCHA_ENABLED',   'true',      '是否开启图形验证码',       true, 0, now(), now()),
    (1, 'admin.LOGIN_SMS_ENABLED',       'false',     '是否开启短信验证码',       true, 0, now(), now()),
    (1, 'admin.LOGIN_FAIL_LOCK_COUNT',   '5',         '密码错误锁定次数',         true, 0, now(), now()),
    (1, 'admin.LOGIN_FAIL_LOCK_MINUTES', '30',        '锁定时长（分钟）',         true, 0, now(), now()),
    (1, 'admin.LOGIN_SINGLE_DEVICE',     'false',     '单设备登录',               true, 0, now(), now()),
    (1, 'admin.LOGIN_REMOTE_ALERT',      'false',     '异地登录提醒',             true, 0, now(), now()),
    (1, 'admin.MENU_MAX_DEPTH',          '7',         '菜单树最大深度',           true, 0, now(), now()),
    (1, 'admin.FILE_UPLOAD_MAX_SIZE',    '10485760',  '文件上传大小限制（字节）', true, 0, now(), now()),
    (1, 'admin.FILE_ALLOWED_TYPES',      '["image/jpeg","image/png","image/gif","application/pdf","application/zip","text/plain"]', '允许的文件类型列表', true, 0, now(), now());

-- -----------------------------------------------------------------------------
-- 16. operation_log - 操作日志（合并 sys_audit_log + 原 operation_log，字段取超集；
--     不做软删除；target_id 使用字符串；request_body 停用恒 NULL（T-ACCESS-025 参数序列化收敛）；
--     module 三值枚举 ADMIN/PERMISSION/ACCESS，T-ACCESS-007 收敛）
-- -----------------------------------------------------------------------------
CREATE TABLE operation_log (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    module         VARCHAR(64) NOT NULL,   -- 模块标识三值枚举：ADMIN/PERMISSION/ACCESS（按事务边界判定）
    action         VARCHAR(64) NOT NULL,
    target_type    VARCHAR(64),
    target_id      VARCHAR(256),           -- 字符串（合并口径，原 permission 为 BIGINT；256 覆盖 configKey 128 / roleExternalId 256 等业务键上限）
    summary        VARCHAR(512),
    operator_id    BIGINT,                 -- 原 permission operation_log
    operator_name  VARCHAR(256),           -- 原 permission operation_log（user_id/username 旧 admin 双轨列已删，T-ACCESS-007 切面只写 operator 字段）
    ip_address     VARCHAR(64),
    request_id     VARCHAR(64),
    request_url    VARCHAR(256),           -- 原 admin sys_audit_log
    request_body   VARCHAR(4000),          -- 停用恒 NULL（T-ACCESS-025：方法参数不再序列化入库）
    response_code  INT,                    -- 原 admin sys_audit_log
    cost_time      INT,                    -- 原 admin sys_audit_log
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_operation_log_tenant_time ON operation_log (tenant_id, created_at DESC);    -- 原 permission idx_operation_log_tenant_time = 原 admin idx_audit_log_tenant_time，合并
CREATE INDEX idx_operation_log_operator ON operation_log (tenant_id, operator_id, created_at DESC);  -- 原 permission
CREATE INDEX idx_operation_log_target ON operation_log (tenant_id, target_type, target_id);   -- 原 permission
CREATE INDEX idx_operation_log_tenant_module_time ON operation_log (tenant_id, module, created_at DESC);  -- T-ACCESS-007：module 三值化后按模块边界分页过滤
CREATE INDEX idx_operation_log_request ON operation_log (request_id) WHERE request_id IS NOT NULL;  -- 原 permission

COMMENT ON TABLE operation_log IS '操作日志：轻量全量记录所有写操作，不做软删除，永久保留；与 permission_change_log 区分：本表记所有操作，permission_change_log 只记权限变更详情';
COMMENT ON COLUMN operation_log.module IS '所属模块，模块标识约定：ADMIN=管理域 / PERMISSION=权限域 / ACCESS=跨域编排';
COMMENT ON COLUMN operation_log.action IS '操作类型，`{业务对象}_{动作}` 大写事件码，如 USER_CREATE / CONFIG_UPDATE / ROLE_RESOURCE_PERMISSION_GRANT 等（各业务 @OperationLog 维护）';
COMMENT ON COLUMN operation_log.target_type IS '操作目标类型';
COMMENT ON COLUMN operation_log.target_id IS '操作目标ID（字符串，兼容业务键与数值 ID）';
COMMENT ON COLUMN operation_log.summary IS '操作摘要';
COMMENT ON COLUMN operation_log.operator_id IS '操作人ID（permission 侧语义）';
COMMENT ON COLUMN operation_log.operator_name IS '操作人名称（permission 侧语义）';
COMMENT ON COLUMN operation_log.request_url IS '请求URL（admin 侧语义）';
COMMENT ON COLUMN operation_log.request_body IS '停用恒 NULL（T-ACCESS-025 操作日志参数序列化收敛；列保留兼容既有数据）';
COMMENT ON COLUMN operation_log.response_code IS '响应状态码（admin 侧语义）';
COMMENT ON COLUMN operation_log.cost_time IS '耗时（毫秒）';
COMMENT ON COLUMN operation_log.request_id IS '请求/追踪ID';

-- -----------------------------------------------------------------------------
-- 17. type_definition - 类型定义表（预置 + 租户可扩展，is_system 区分）
--     本表种子为权威数值：user_type USER=1/SERVICE=2；role_type ORG=1/POSITION=2/
--     PERSONAL=3/GROUP_ROLE=5/BASIC_ROLE=6；resource_type MENU=1/BUTTON=2/API=3/DATA=4
--     （均与 RoleType/ResourceType 枚举一致），其余按声明顺序从 5 起分配。
-- -----------------------------------------------------------------------------
CREATE TABLE type_definition (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    type_key      VARCHAR(64) NOT NULL,
    type_code     VARCHAR(64) NOT NULL,
    type_value    INT NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    is_system     BOOLEAN NOT NULL DEFAULT false,
    sort_order    INT DEFAULT 0,
    extra         JSONB DEFAULT '{}',
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_type_definition_value ON type_definition (tenant_id, type_key, type_value) WHERE delete_flag = 0;
CREATE UNIQUE INDEX uk_type_definition_code ON type_definition (tenant_id, type_key, type_code) WHERE delete_flag = 0;

COMMENT ON TABLE type_definition IS '类型定义：type_code 是对外稳定编码，type_value 是内部存储和计算值。type_key 如 user_type/role_type/resource_type，is_system=true 为系统预置不可删改。创建 resource_type 时自动预置 CRUD 四个 operation_permission；删除 resource_type（非系统、类型下无有效资源行）时同事务级联软删该类型全部有效 operation_permission 与该类型下有效授权行（正常流仅剩 scope_all 类型级行，T-PERM-050）；删除 user_type/role_type（非系统、类型下存在有效 abstract_user/abstract_role 行）时整批拒绝（20056 删除保护——主体数据不级联，T-PERM-056）';
COMMENT ON COLUMN type_definition.type_key IS '类型键，如 user_type、role_type、resource_type';
COMMENT ON COLUMN type_definition.type_code IS '对外稳定编码，如 USER、SERVICE、BASIC_ROLE、MENU、DATA';
COMMENT ON COLUMN type_definition.type_value IS '内部枚举值；同一 tenant_id + type_key 内全局唯一，只用于存储、索引和计算，不作为外部 API 契约';
COMMENT ON COLUMN type_definition.name IS '显示名称';
COMMENT ON COLUMN type_definition.description IS '描述';
COMMENT ON COLUMN type_definition.is_system IS '是否系统预置：true=预置不可删改，false=租户自定义可扩展';
COMMENT ON COLUMN type_definition.sort_order IS '排序';
COMMENT ON COLUMN type_definition.extra IS '扩展配置(JSON)，如 {"max_depth": 5} 控制资源树深度。resource_type 类型承载类型级所有权声明（T-PERM-052，2026-09-05 定案）：managedMode=MANAGED(缺省,管理面维护)/SYNC(外部同步维护)，SYNC 时必填 syncSourceService（须为已注册有效服务，type_key 非 resource_type 携带此二键保存拒绝）；声明有效值变更（含删键隐式切回 MANAGED）——系统预置类型钉死不可变更、自定义类型在类型下存在有效资源行时拒绝（20056），保存边界校验已知键结构（显式 null 拒绝），未知键开放不视为声明（拼错键=无声明按缺省 MANAGED）；读取侧 extra 损坏按 MANAGED 处理（对外部同步 fail-closed、对管理面可写=可恢复方向）。内部来源 syncSourceService=access-service 仅 is_system 预置类型可声明（USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION 六类事实链路类型，种子声明 SYNC+access-service；ADMIN_FILE 文件夹实例由 bootstrap 预置+上传惰性登记产出，T-ADMIN-025；TYPE_DEFINITION 类型定义实例投影由写路径同事务维护+bootstrap 自愈补种产出，T-PERM-051）';
COMMENT ON COLUMN type_definition.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- 预置类型种子（tenant 1；type_value 为权威数值，与文件头 type_value 终值分配表一致——
-- T-ACCESS-016 定稿收敛映射，T-ACCESS-018 重编：五组 ADMIN_* 管理类型并入既有公共类型、
-- ADMIN_SYNC_TASK 删除、user_type ADMIN_USER 更名 LOCAL_USER、ORG 取新值 29）
INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system, sort_order, created_by, created_at, updated_at) VALUES
    -- user_type（USER=外部人员 / SERVICE=外部服务 / LOCAL_USER=本地访问主体，
    --   access.application 本地投影以 LOCAL_USER 作为 subjectTypeCode 维护 abstract_user，必须可解析）
    (1, 'user_type', 'USER',        1, '人员', true, 1, 0, now(), now()),
    (1, 'user_type', 'SERVICE',     2, '服务', true, 2, 0, now(), now()),
    (1, 'user_type', 'LOCAL_USER',  3, '本地用户', true, 3, 0, now(), now()),
    -- role_type（RoleType 枚举权威值：4 留空不可用）
    (1, 'role_type', 'ORG',        1, '组织',     true, 1, 0, now(), now()),
    (1, 'role_type', 'POSITION',   2, '职位',     true, 2, 0, now(), now()),
    (1, 'role_type', 'PERSONAL',   3, '个人',     true, 3, 0, now(), now()),
    (1, 'role_type', 'GROUP_ROLE', 5, '分组角色', true, 5, 0, now(), now()),
    (1, 'role_type', 'BASIC_ROLE', 6, '基本角色', true, 6, 0, now(), now()),
    -- resource_type（T-ACCESS-016 §13.1 终态：五组管理类型已并入 USER/ROLE/MENU/
    --   SYSTEM_CONFIG/ORG，ADMIN_SYNC_TASK 删除；T-PERM-025 增 OPERATION_LOG=30、
--   T-PERM-032 增 PERMISSION_CHANGE_LOG=31；
    --   退役值 16/17/18/19/22/28 不复用；全表见文件头部终值分配表）
    (1, 'resource_type', 'MENU',                1,  '菜单',         true,  1, 0, now(), now()),
    (1, 'resource_type', 'BUTTON',              2,  '按钮',         true,  2, 0, now(), now()),
    (1, 'resource_type', 'API',                 3,  'API接口',      true,  3, 0, now(), now()),
    (1, 'resource_type', 'DATA',                4,  '数据',         true,  4, 0, now(), now()),
    (1, 'resource_type', 'ROLE',                5,  '角色',         true,  5, 0, now(), now()),
    (1, 'resource_type', 'USER',                6,  '用户',         true,  6, 0, now(), now()),
    (1, 'resource_type', 'RESOURCE',            7,  '资源实体',     true,  7, 0, now(), now()),
    (1, 'resource_type', 'SERVICE',             8,  '服务配置',     true,  8, 0, now(), now()),
    (1, 'resource_type', 'DOMAIN',              9,  '业务域',       true,  9, 0, now(), now()),
    (1, 'resource_type', 'TYPE_DEFINITION',    10,  '类型定义',     true, 10, 0, now(), now()),
    (1, 'resource_type', 'SYSTEM_CONFIG',      11,  '系统配置',     true, 11, 0, now(), now()),
    (1, 'resource_type', 'OPERATION',          12,  '操作权限',     true, 12, 0, now(), now()),
    (1, 'resource_type', 'CONDITION',          13,  '权限条件',     true, 13, 0, now(), now()),
    (1, 'resource_type', 'CONFLICT_RULE',      14,  '冲突规则',     true, 14, 0, now(), now()),
    (1, 'resource_type', 'DEPENDENCY',         15,  '资源依赖',     true, 15, 0, now(), now()),
    (1, 'resource_type', 'ADMIN_DICT',         20,  '字典类型',     true, 20, 0, now(), now()),
    (1, 'resource_type', 'ADMIN_DICT_DATA',    21,  '字典数据',     true, 21, 0, now(), now()),
    (1, 'resource_type', 'ADMIN_OAUTH2_CLIENT', 23, 'OAuth2客户端', true, 23, 0, now(), now()),
    (1, 'resource_type', 'ADMIN_NOTICE',       24,  '通知公告',     true, 24, 0, now(), now()),
    (1, 'resource_type', 'ADMIN_FILE',         25,  '文件管理',     true, 25, 0, now(), now()),
    (1, 'resource_type', 'ADMIN_JOB',          26,  '定时任务',     true, 26, 0, now(), now()),
    (1, 'resource_type', 'ADMIN_ORG_TREE_CONFIG', 27, '组织树配置', true, 27, 0, now(), now()),
    (1, 'resource_type', 'ORG',                29, '组织管理',     true, 29, 0, now(), now()),
    (1, 'resource_type', 'OPERATION_LOG',      30, '操作日志',     true, 30, 0, now(), now()),
    (1, 'resource_type', 'PERMISSION_CHANGE_LOG', 31, '权限变更日志', true, 31, 0, now(), now());

-- T-PERM-052 内部来源声明（2026-09-05 定案）：事实链路类型——资源行由用户/组织/菜单/角色管理
-- 经 LocalProjectionDomainService 同事务自动维护（SYNC + 来源=access-service），外部同步一律拒绝
-- （来源不匹配）、管理面资源 CRUD 一律 20055；收编原类型保留清单与行级 owner=access-service 防线。
-- T-ADMIN-025（2026-09-06）增 ADMIN_FILE：文件夹实例（code=bucket_name）由 bootstrap 预置
-- default/avatar/document/image 四文件夹 + 上传新 bizType 惰性登记两条事实链产出，人工不得
-- 经 resource-entity 管理入口构造（管理面 CRUD 20055，与惰性登记 upsert 双 writer 冲突同向）。
-- T-PERM-051（2026-09-07）增 TYPE_DEFINITION：类型定义自身实例（code={typeKey}:{typeCode} 复合
-- 业务键）由 type-definition 写路径同事务投影维护 + bootstrap 自愈补种，人工不得构造（同款 20055）。
UPDATE type_definition
SET extra = '{"managedMode":"SYNC","syncSourceService":"access-service"}'
WHERE tenant_id = 1 AND type_key = 'resource_type'
  AND type_code IN ('USER', 'ORG', 'MENU', 'ROLE', 'ADMIN_FILE', 'TYPE_DEFINITION');

-- -----------------------------------------------------------------------------
-- 18. biz_domain - 业务域表（扁平列表，无启停，引用检查拒删）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_domain (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    global      BOOLEAN NOT NULL DEFAULT false,
    description VARCHAR(512),
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_biz_domain ON biz_domain (tenant_id, code) WHERE delete_flag = 0;
CREATE UNIQUE INDEX uk_biz_domain_global ON biz_domain (tenant_id) WHERE global = TRUE AND delete_flag = 0;

COMMENT ON TABLE biz_domain IS '业务域，扁平列表，对权限对象分类，无启停，删除前检查引用。全局域(global=true)每个租户仅一个，由管理 API 创建（create 可选 global 字段，T-PERM-046）；其范围：有 CLASSIFY 声明时按声明（2026-09-09 定案），否则=未被其他域认领的资源类型（动态补集）';
COMMENT ON COLUMN biz_domain.code IS '域编码';
COMMENT ON COLUMN biz_domain.name IS '域名称';
COMMENT ON COLUMN biz_domain.global IS '是否全局域：true=全局域（每租户仅一个），其范围=有 CLASSIFY 声明按声明（T-PERM-046 定案），否则为未被其他域认领的资源类型动态补集';

-- -----------------------------------------------------------------------------
-- 19. abstract_user - 抽象用户表
--     owner_service_code：'access-service'=本地投影（管理事实派生，禁止权限管理 API 直接修改），
--     NULL=人工维护/外部同步（外部同步所有权以 sync_metadata 为准）
-- -----------------------------------------------------------------------------
CREATE TABLE abstract_user (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    user_type         INT NOT NULL,
    external_id       VARCHAR(256) NOT NULL,
    name              VARCHAR(256),
    enabled           BOOLEAN NOT NULL DEFAULT true,
    extra             JSONB DEFAULT '{}',
    owner_service_code VARCHAR(128),
    created_by        BIGINT,
    updated_by        BIGINT,
    deleted_by        BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    delete_flag       BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_abstract_user ON abstract_user (tenant_id, user_type, external_id) WHERE delete_flag = 0;
CREATE INDEX idx_abstract_user_tenant ON abstract_user (tenant_id) WHERE delete_flag = 0;

COMMENT ON TABLE abstract_user IS '抽象用户，user_type 来自 type_definition。支持外部系统 API 同步（幂等）。LOCAL_USER（原 ADMIN_USER，T-ACCESS-016 更名）类型表示访问主体，不等同于被管理用户资源；具体同步方由调用方 serviceCode 标识；id 为唯一主体 ID 生成源（sys_user.id 同值，§12.2）。PERSONAL 个人角色类型在 type_definition 保留，但生命周期机制未实现，创建 abstract_user 时不自动创建（T-ACCESS-016 §14.1：bootstrap 固定图亦不含个人角色）';
COMMENT ON COLUMN abstract_user.user_type IS '用户类型枚举值：USER(1)外部人员/SERVICE(2)外部服务/LOCAL_USER(3，原 ADMIN_USER)本地访问主体，来自 type_definition';
COMMENT ON COLUMN abstract_user.external_id IS '外部业务系统唯一标识；本地投影使用 external_id = sys_user.id.toString()';
COMMENT ON COLUMN abstract_user.name IS '显示名';
COMMENT ON COLUMN abstract_user.enabled IS '是否启用：false 时鉴权不通过';
COMMENT ON COLUMN abstract_user.extra IS '扩展属性(JSON)';
COMMENT ON COLUMN abstract_user.owner_service_code IS '所有权标识：access-service=管理事实派生的本地投影（禁止权限管理 API 直接修改）；NULL=人工维护或外部同步（外部同步所有权以 sync_metadata 为准）';
COMMENT ON COLUMN abstract_user.delete_flag IS '逻辑删除：0=未删除，删除时填本行id。删除级联：user_role + 个人角色的 role_resource_permission + 失效缓存';

-- -----------------------------------------------------------------------------
-- 20. abstract_role - 抽象角色表（树形结构，通过 parent_id 支持层级）
--     角色类型说明：
--       ORG(1) 组织：树形，同步自 sys_org
--       POSITION(2) 职位：平铺（不可有子级），分配给用户时 user_role.relation_id 记录所属组织 abstract_role.id
--       PERSONAL(3) 个人：平铺，每用户1个，独立
--       GROUP_ROLE(5) 分组角色：树形，不直接配置权限，通过 extra.basicRoleIds 额外关联基本角色
--       BASIC_ROLE(6) 基本角色：平铺（不可有子级），承载实际权限配置
--     owner_service_code 语义同 abstract_user
-- -----------------------------------------------------------------------------
CREATE TABLE abstract_role (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    parent_id         BIGINT,
    role_type         INT NOT NULL,
    external_id       VARCHAR(256),
    name              VARCHAR(256) NOT NULL,
    status            INT NOT NULL DEFAULT 1,
    sort_order        INT DEFAULT 0,
    extra             JSONB DEFAULT '{}',
    owner_service_code VARCHAR(128),
    created_by        BIGINT,
    updated_by        BIGINT,
    deleted_by        BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    delete_flag       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_abstract_role_tenant_type ON abstract_role (tenant_id, role_type) WHERE delete_flag = 0;

CREATE INDEX idx_abstract_role_parent ON abstract_role (parent_id) WHERE delete_flag = 0;
CREATE UNIQUE INDEX uk_abstract_role_external ON abstract_role (tenant_id, role_type, external_id)
    WHERE external_id IS NOT NULL AND delete_flag = 0;

COMMENT ON TABLE abstract_role IS '抽象角色，树形结构（parent_id）；GROUP_ROLE 和 BASIC_ROLE 通过 type_definition 区分。ORG/POSITION 可由外部系统同步为角色容器。删除级联：user_role + role_resource_permission';
COMMENT ON COLUMN abstract_role.parent_id IS '父角色ID，用于树形层级；父子角色类型一致（create/move 应用层校验，同类型内嵌套合法）';
COMMENT ON COLUMN abstract_role.role_type IS '角色类型枚举：ORG(1)组织/POSITION(2)职位/PERSONAL(3)个人/GROUP_ROLE(5)分组角色/BASIC_ROLE(6)基本角色，来自 type_definition';
COMMENT ON COLUMN abstract_role.external_id IS '外部业务标识；对外接口按 tenant_id + role_type + external_id 定位角色；本地投影使用 external_id = sys_org.id 等管理事实 ID';
COMMENT ON COLUMN abstract_role.name IS '名称';
COMMENT ON COLUMN abstract_role.status IS '状态：0=停用 1=启用，预留扩展空间';
COMMENT ON COLUMN abstract_role.owner_service_code IS '所有权标识：access-service=管理事实派生的本地投影（禁止权限管理 API 直接修改）；NULL=人工维护或外部同步';
COMMENT ON COLUMN abstract_role.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 21. operation_permission - 操作权限表（绑定资源类型，binary_bit + inherit_mask 用 BIGINT）
--     创建 resource_type 时自动预置 CRUD 四个操作（v1.4 起词法统一为 VIEW）：
--     CREATE(bit=1,mask=0) VIEW(bit=2,mask=0) UPDATE(bit=4,mask=2继承VIEW) DELETE(bit=8,mask=2继承VIEW)
--     每个 resource_type 最多 63 个操作（BIGINT 63 位）
--     操作无启停状态，用删除代替
-- -----------------------------------------------------------------------------
CREATE TABLE operation_permission (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    resource_type INT,
    code          VARCHAR(64) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    binary_bit    BIGINT NOT NULL,
    inherit_mask  BIGINT NOT NULL DEFAULT 0,
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_operation_permission_typed ON operation_permission (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0;
-- 同 (tenant, resource_type, binary_bit) 下只能有一个有效 code，阻止「同 bit 多 code」反查歧义
CREATE UNIQUE INDEX uk_operation_permission_typed_bit ON operation_permission (tenant_id, resource_type, binary_bit) WHERE resource_type IS NOT NULL AND delete_flag = 0;
-- 全局操作概念退役（2026-08-30 设计定案）：操作位空间按类型完全隔离，resource_type 强制非空。
-- 原 uk_operation_permission_global / 全局回退合并语义（T-PERM-040 includeGlobalFallback）随概念一并退役；
-- 退役动机：授权行只存 resource_type + granted_bits（不存操作 ID），全局位与专属位同值时
-- 授权身份不可区分（同位异码互相越权），CHECK 在数据层焊死该形态。种子数据本就无全局行，无迁移成本。
ALTER TABLE operation_permission ADD CONSTRAINT ck_operation_permission_resource_type_required CHECK (resource_type IS NOT NULL);

COMMENT ON TABLE operation_permission IS '操作权限；effective = binary_bit | inherit_mask；预置CRUD：CREATE(1,0) VIEW(2,0) UPDATE(4,2) DELETE(8,2)；所属 resource_type 删除时该类型全部有效操作行同事务级联软删（对称于创建联动预置，T-PERM-050）';
COMMENT ON COLUMN operation_permission.resource_type IS '适用的资源类型枚举值（type_definition type_value）；全局操作（NULL=适用所有）概念已退役（2026-08-30 设计定案），CHECK 强制非空';
COMMENT ON COLUMN operation_permission.code IS '操作编码，如 CREATE、VIEW、UPDATE、DELETE（v1.4 起统一用 VIEW，原 READ 为历史命名）';
COMMENT ON COLUMN operation_permission.binary_bit IS '本操作独占位（BIGINT 63 个独立操作）';
COMMENT ON COLUMN operation_permission.inherit_mask IS '继承的位掩码，实际权限=binary_bit|inherit_mask';

-- 静态资源类型 CRUD 预置种子（全部 resource_type × CREATE/VIEW/UPDATE/DELETE，CROSS JOIN 派生）：
-- DDL 直接种入的 resource_type 不会触发运行时自动生成（运行时联动预置仅覆盖经
-- type-definition/create 新建的类型，见 TypeDefinitionAppServiceImpl——T-PERM-028），
-- 初始化阶段种入的类型必须在此预置；binary_bit 1/2/4/8 与下方扩展码（16 起）不冲突。
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag)
SELECT 1, td.type_value, ops.code, ops.name, ops.binary_bit, ops.inherit_mask, 0, 0, 0
FROM type_definition td
CROSS JOIN (VALUES
    ('CREATE', '创建', 1, 0),
    ('VIEW',   '查看', 2, 0),
    ('UPDATE', '更新', 4, 2),
    ('DELETE', '删除', 8, 2)
) AS ops(code, name, binary_bit, inherit_mask)
WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.delete_flag = 0
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- 非预置操作码种子（按 T-ACCESS-016 §13.3 bit 终值表随类型收敛重新归属；
-- ADMIN_ORG 六码同名同 bit 迁移 ORG(29)、ADMIN_USER 两码迁 USER(6)（ENABLE bit 16→32 重分配，
-- USER 下 16 已被 MANAGE 占用）、ADMIN_ROLE:GRANT/REVOKE 零消费者删除不迁移；
-- binary_bit 从 16 起分配，inherit_mask 读类=0、写类继承 VIEW=2）
-- ORG(29)：岗位 CRUD 精化 + 成员关系（普通组织 VIEW 由 CRUD 预置覆盖）
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag) VALUES
    (1, 29, 'CREATE_POSITION',     '创建岗位',       16,  2, 0, 0, 0),
    (1, 29, 'UPDATE_POSITION',     '编辑/移动/启停岗位', 32, 2, 0, 0, 0),
    (1, 29, 'DELETE_POSITION',     '删除岗位',       64,  2, 0, 0, 0),
    (1, 29, 'ASSIGN_POSITION_USER','岗位用户挂载/卸载/设主', 128, 2, 0, 0, 0),
    (1, 29, 'MANAGE_MEMBER',       '管理组织成员',   256, 2, 0, 0, 0),
    (1, 29, 'VIEW_POSITION',       '查看岗位',       512, 0, 0, 0, 0),
    -- USER(6)：启停 + 重置密码（bit 重分配：USER 下 16 被 MANAGE 占用，ENABLE 取 32；
    -- 用户列表查看由 CRUD 预置 VIEW 覆盖）
    (1, 6,  'ENABLE',              '启用/禁用用户',  32,  2, 0, 0, 0),
    (1, 6,  'RESET_PASSWORD',      '重置密码',       64,  2, 0, 0, 0),
    -- ADMIN_NOTICE(24)：发布公告
    (1, 24, 'PUBLISH',             '发布公告',       16,  2, 0, 0, 0),
    -- ADMIN_JOB(26)：启停任务 + 触发执行
    (1, 26, 'ENABLE',              '启用/禁用任务',  16,  2, 0, 0, 0),
    (1, 26, 'TRIGGER',             '触发执行',       64,  2, 0, 0, 0),
    -- ADMIN_ORG_TREE_CONFIG(27)：切换默认树/单关联
    (1, 27, 'TOGGLE',              '切换默认树/单关联', 16, 2, 0, 0, 0),
    -- ROLE(5)：双层门禁关键操作码（权限中心内部角色管理）
    (1, 5,  'MANAGE',              '管理',           16,  2, 0, 0, 0)
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- 权限中心运行时必需操作码：代码实际校验但 CRUD/Admin 扩展码未覆盖；
-- 缺失时 TypeResolutionServiceImpl 解析返回 null → PermQueryEngine fail-closed 全量拒绝。
-- bit 16 起按类型避让，写类 mask=2（继承 VIEW），API:ACCESS 为接口鉴权专用（mask=0）。
INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, created_by, updated_by, delete_flag) VALUES
    -- USER(6)：更新/删除用户门禁
    (1, 6,  'MANAGE',             '管理用户',         16, 2, 0, 0, 0),
    -- ROLE(5)：分组角色分配/撤销（MANAGE 已占 16）
    (1, 5,  'ASSIGN',             '分配角色',         32, 2, 0, 0, 0),
    (1, 5,  'REVOKE',             '撤销角色',         64, 2, 0, 0, 0),
    -- RESOURCE(7)：更新/删除资源实体门禁
    (1, 7,  'MANAGE',             '管理资源实体',     16, 2, 0, 0, 0),
    -- SERVICE(8)：服务配置/API 映射/接口同步
    (1, 8,  'MANAGE',             '管理服务配置',     16, 2, 0, 0, 0),
    (1, 8,  'MANAGE_API_MAPPING', '管理API映射',      32, 2, 0, 0, 0),
    (1, 8,  'SYNC_INTERFACE',     '同步服务接口',     64, 2, 0, 0, 0),
    -- TYPE_DEFINITION(10)：更新/删除类型定义门禁
    (1, 10, 'MANAGE',             '管理类型定义',     16, 2, 0, 0, 0),
    -- SYSTEM_CONFIG(11)：系统配置/业务域/域配置管理门禁
    (1, 11, 'MANAGE',             '管理系统配置',     16, 2, 0, 0, 0),
    -- OPERATION(12)：更新/删除操作权限门禁
    (1, 12, 'MANAGE',             '管理操作权限',     16, 2, 0, 0, 0),
    -- DEPENDENCY(15)：批量同步依赖门禁
    (1, 15, 'SYNC',               '批量同步依赖',     16, 2, 0, 0, 0),
    -- API(3)：网关接口鉴权专用（PermissionCheckAppServiceImpl forInterfaceCheck）
    (1, 3,  'ACCESS',             '访问接口',         16, 0, 0, 0, 0)
ON CONFLICT (tenant_id, resource_type, code) WHERE resource_type IS NOT NULL AND delete_flag = 0 DO NOTHING;

-- -----------------------------------------------------------------------------
-- 22. resource_entity - 权限资源实体表（树形，支持多编码类型 code_type）
--     数据权限也是一种资源实体（resource_type=DATA）
--     同一资源可有多行不同 code_type，默认 "default"
-- -----------------------------------------------------------------------------
CREATE TABLE resource_entity (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    parent_id     BIGINT,
    resource_type INT NOT NULL,
    code          VARCHAR(256) NOT NULL,
    code_type     VARCHAR(64) NOT NULL DEFAULT 'default',
    name          VARCHAR(256) NOT NULL,
    path          VARCHAR(1024),
    status        INT NOT NULL DEFAULT 1,
    sort_order    INT DEFAULT 0,
    extra         JSONB DEFAULT '{}',
    owner_service_code VARCHAR(128),
    maintain_source    VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_resource_entity ON resource_entity (tenant_id, resource_type, code, code_type) WHERE delete_flag = 0;
CREATE INDEX idx_resource_entity_parent ON resource_entity (parent_id) WHERE delete_flag = 0;
CREATE INDEX idx_resource_entity_type ON resource_entity (tenant_id, resource_type) WHERE delete_flag = 0;
CREATE INDEX idx_resource_entity_sync_owner ON resource_entity (tenant_id, owner_service_code, maintain_source) WHERE delete_flag = 0 AND owner_service_code IS NOT NULL;

COMMENT ON TABLE resource_entity IS '权限资源实体，树形；同一资源可有多行不同 code_type 用于编码转换（如 "default"="100", "en"="Britain", "cn"="英国"）。用户/组织等管理对象通过既有资源类型（如 USER/ORG）建模实例级权限（T-ACCESS-018 收敛，原 ADMIN_* 管理类型已并入）；owner_service_code/maintain_source 仅记录行归属（service-config 声明通道的撞码归属消歧与前端展示），写入门禁不依赖该列——资源边界以 type_definition.extra 类型级所有权声明为准（T-PERM-052，architecture §4.3）';
COMMENT ON COLUMN resource_entity.code IS '资源业务编码。长度上限 256（T-PERM-051：TYPE_DEFINITION 实例投影为复合键 {typeKey}:{typeCode}，type_key/type_code 各 ≤64，最坏 129 字符超出原 128 列宽而加宽；手工/同步入口的编码长度语义不变，仅列宽放行）';
COMMENT ON COLUMN resource_entity.parent_id IS '父节点ID';
COMMENT ON COLUMN resource_entity.resource_type IS '资源类型枚举（type_definition type_value），来自 type_definition；除 MENU/BUTTON/API/DATA 等公共基础类型外，租户可通过 type_definition 扩展管理资源类型（终态注册表见 type_definition 种子与头部终值分配表）';
COMMENT ON COLUMN resource_entity.code_type IS '编码类型，默认 "default"；同一资源不同编码体系用不同 code_type 区分';
COMMENT ON COLUMN resource_entity.name IS '名称';
COMMENT ON COLUMN resource_entity.path IS '树路径（物化路径）';
COMMENT ON COLUMN resource_entity.status IS '状态：0=停用 1=启用';
COMMENT ON COLUMN resource_entity.extra IS '扩展属性(JSON)，如菜单图标/路由等';
COMMENT ON COLUMN resource_entity.owner_service_code IS '资源维护方服务编码；仅用于 service-config/sync、资源依赖等既有维护来源标记。新 resource-entity/sync/full-sync：本地管理投影由 access.application 同一事务写入 access-service（sync 入口已拒绝旧内部来源 admin-service，20045）；外部业务服务同步保持 NULL，其 ownership 以 sync_metadata 为准';
COMMENT ON COLUMN resource_entity.maintain_source IS '维护来源（记录值，判定不依赖本列——资源所有权由类型声明 type_definition.extra.managedMode 承载，T-PERM-052）：MANUAL=人工维护，SERVICE_SYNC=service-config/sync 自动维护，SYNC=resource-entity 外部同步通道写入值（owner_service_code=NULL，所有权由类型声明+sync_metadata 承载），SDK_SCAN/MANIFEST/ADMIN_UI 可用于后续扩展；列无 CHECK 约束，新值须同步登记本注释';

-- -----------------------------------------------------------------------------
-- 23. resource_api_mapping - 接口资源映射表
-- -----------------------------------------------------------------------------
CREATE TABLE resource_api_mapping (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL,
    resource_entity_id BIGINT NOT NULL,
    service_code       VARCHAR(128) NOT NULL,
    http_method        VARCHAR(16) NOT NULL,
    path_pattern       VARCHAR(512) NOT NULL,
    match_order        INT NOT NULL DEFAULT 0,
    enabled            BOOLEAN NOT NULL DEFAULT true,
    extra              JSONB DEFAULT '{}',
    created_by         BIGINT,
    updated_by         BIGINT,
    deleted_by         BIGINT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,
    delete_flag        BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_resource_api_mapping_route ON resource_api_mapping (tenant_id, resource_entity_id, service_code, http_method, path_pattern) WHERE delete_flag = 0;
CREATE INDEX idx_resource_api_mapping_lookup ON resource_api_mapping (tenant_id, service_code, http_method, match_order) WHERE delete_flag = 0 AND enabled = true;
CREATE INDEX idx_resource_api_mapping_resource ON resource_api_mapping (resource_entity_id) WHERE delete_flag = 0;

COMMENT ON TABLE resource_api_mapping IS '接口资源映射：API 类资源到 service_code + http_method + path_pattern 的显式映射；同一路径允许映射多个资源，接口级鉴权采用 OR 语义';
COMMENT ON COLUMN resource_api_mapping.service_code IS '所属服务编码';
COMMENT ON COLUMN resource_api_mapping.http_method IS 'HTTP 方法，如 GET/POST/PUT/DELETE';
COMMENT ON COLUMN resource_api_mapping.path_pattern IS '接口路径模式（完整路径含前缀）';

-- -----------------------------------------------------------------------------
-- 24. service_config - 接入服务配置表（全量同步，支持手动增删改接口映射）
-- -----------------------------------------------------------------------------
CREATE TABLE service_config (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    service_code VARCHAR(128) NOT NULL,
    name         VARCHAR(256) NOT NULL,
    base_path    VARCHAR(512),
    description  VARCHAR(512),
    status       INT NOT NULL DEFAULT 1,
    extra        JSONB DEFAULT '{}',
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    delete_flag  BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_service_config ON service_config (tenant_id, service_code) WHERE delete_flag = 0;

COMMENT ON TABLE service_config IS '接入服务配置：全量同步策略，支持手动增删改接口映射。extra.syncTypes 声明服务可同步的类型白名单（见 api-contract §6.3.1）；停用(status=0)后其接口不参与授权且 sync/full-sync 全部拒绝';
COMMENT ON COLUMN service_config.service_code IS '服务编码，租户内唯一';
COMMENT ON COLUMN service_config.base_path IS '基础路径前缀';
COMMENT ON COLUMN service_config.extra IS '扩展属性(JSON)：syncTypes 声明同步类型白名单（subjectTypeCodes/roleTypeCodes/sourceTypes 字符串数组，缺失分类=无权限；资源维度已随 T-PERM-052 类型级所有权退役，保存含 resourceTypeCodes 拒绝）；保存时校验结构，运行时 fail-closed';
COMMENT ON COLUMN service_config.status IS '状态：0=停用 1=启用。停用后该服务的接口不参与授权，且 sync/full-sync 全部拒绝（SECURITY_DENIED）';

-- -----------------------------------------------------------------------------
-- 25. permission_condition - 权限条件表（JSONB 规则字段，一行=一个完整条件定义）
-- -----------------------------------------------------------------------------
CREATE TABLE permission_condition (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    condition_rules JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    gateway_evaluable BOOLEAN NOT NULL DEFAULT false,
    description     VARCHAR(512),
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    delete_flag     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_permission_condition ON permission_condition (tenant_id, code) WHERE delete_flag = 0;

COMMENT ON TABLE permission_condition IS '权限生效条件；condition_rules 存完整条件定义，一行=一个条件组。多个 role_resource_permission 可引用同一 condition_id 复用计算结果';
COMMENT ON COLUMN permission_condition.code IS '条件编码';
COMMENT ON COLUMN permission_condition.name IS '名称';
COMMENT ON COLUMN permission_condition.condition_rules IS '条件规则(JSON)，如 {"logic":"AND","items":[{"type":"DATE_RANGE","params":{"start":"2025-01-01","end":"2025-12-31"}},{"type":"TIME_RANGE","params":{"start":"09:00","end":"18:00"}},{"type":"IP_WHITELIST","params":{"cidrs":["192.168.1.0/24"]}}]}。预置类型：DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST';
COMMENT ON COLUMN permission_condition.enabled IS '是否启用';
COMMENT ON COLUMN permission_condition.gateway_evaluable IS '是否可下发 Gateway 评估（T-PERM-017）。true 时条件规则随接口快照内联到 Gateway，由 Gateway 用请求上下文（clientIp）本地重评。可下发类型：IP_WHITELIST/IP_BLACKLIST/DATE_RANGE/TIME_RANGE（4 类全部）。未来扩展类型（如 ORG_SCOPE）默认不下发，需显式审批加入白名单';

-- -----------------------------------------------------------------------------
-- 26. user_role - 用户关联表（统一关联角色，target_type 标记角色类型）
--     owner_service_code 语义同 abstract_user（组织成员关系与人工功能角色分配共用本表）
-- -----------------------------------------------------------------------------
CREATE TABLE user_role (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    abstract_user_id BIGINT NOT NULL,
    target_type      VARCHAR(32) NOT NULL,
    target_id        BIGINT NOT NULL,
    relation_id      BIGINT,
    valid_from       TIMESTAMPTZ,
    valid_to         TIMESTAMPTZ,
    owner_service_code VARCHAR(128),
    created_by       BIGINT,
    updated_by       BIGINT,
    deleted_by       BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    delete_flag      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_user_role ON user_role (tenant_id, abstract_user_id, target_type, target_id, COALESCE(relation_id, 0)) WHERE delete_flag = 0;
CREATE INDEX idx_user_role_user ON user_role (tenant_id, abstract_user_id) WHERE delete_flag = 0;
CREATE INDEX idx_user_role_target ON user_role (tenant_id, target_type, target_id) WHERE delete_flag = 0;

COMMENT ON TABLE user_role IS '用户关联表：target=abstract_role.id；POSITION 类型时 relation_id 记录所属组织 abstract_role.id，决定数据权限范围。外部系统的组织-用户关系变更须稳定映射为本表事实';
COMMENT ON COLUMN user_role.target_type IS '关联角色类型：ROLE=常规角色/ORG=组织/POSITION=职位/PERSONAL=个人/GROUP_ROLE=分组角色，与 abstract_role.role_type 对应';
COMMENT ON COLUMN user_role.target_id IS '关联角色ID（abstract_role.id）';
COMMENT ON COLUMN user_role.relation_id IS '关联ID，POSITION 类型时记录所属组织 abstract_role.id（由 relationKey=ORG:{orgExternalId} 解析，决定数据权限范围），其他类型时为 NULL';
COMMENT ON COLUMN user_role.valid_from IS '生效开始时间，NULL 不限制';
COMMENT ON COLUMN user_role.valid_to IS '生效结束时间，NULL 不限制';
COMMENT ON COLUMN user_role.owner_service_code IS '所有权标识：access-service=管理事实派生的本地投影；NULL=人工维护或外部同步（外部同步所有权以 sync_metadata 为准）';

-- -----------------------------------------------------------------------------
-- 27. sync_metadata - 同步元数据表（统一记录外部同步所有权、syncKey 与最后版本）
-- -----------------------------------------------------------------------------
CREATE TABLE sync_metadata (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT NOT NULL,
    entity_kind           VARCHAR(64) NOT NULL,
    source_service        VARCHAR(128) NOT NULL,
    scope_key             TEXT NOT NULL,
    scope_key_hash        CHAR(64) NOT NULL,
    business_key          TEXT NOT NULL,
    business_key_hash     CHAR(64) NOT NULL,
    sync_key              TEXT NOT NULL,
    sync_key_hash         CHAR(64) NOT NULL,
    target_id             BIGINT,
    target_status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_sync_occurred_at TIMESTAMPTZ NOT NULL,
    last_sync_sequence_no BIGINT NOT NULL,
    extra                 JSONB DEFAULT '{}',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    delete_flag           BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sync_metadata_key ON sync_metadata (tenant_id, entity_kind, source_service, scope_key_hash, business_key_hash) WHERE delete_flag = 0;
CREATE INDEX idx_sync_metadata_scope ON sync_metadata (tenant_id, entity_kind, source_service, scope_key_hash) WHERE delete_flag = 0;
CREATE INDEX idx_sync_metadata_sync_key ON sync_metadata (tenant_id, source_service, sync_key_hash) WHERE delete_flag = 0;

COMMENT ON TABLE sync_metadata IS '外部同步元数据表，统一记录 abstract_user/abstract_role/user_role/resource_entity 的同步来源、scope、业务键、目标内部ID和最后 syncVersion；用于旧版本 no-op 和 full-sync 差异校准';
COMMENT ON COLUMN sync_metadata.entity_kind IS '同步实体类型：ABSTRACT_USER/ABSTRACT_ROLE/USER_ROLE/RESOURCE_ENTITY';
COMMENT ON COLUMN sync_metadata.source_service IS '同步来源服务（外部业务服务编码，如 example-service；access-service/admin-service 等内部来源被拒绝）；必须与服务间认证主体一致';
COMMENT ON COLUMN sync_metadata.scope_key IS 'full-sync 清理范围键原文，采用 api-contract §6.2.2.4 的规范化 scopeKey，不包含 tenantId/sourceService/entityKind';
COMMENT ON COLUMN sync_metadata.scope_key_hash IS 'scope_key 的 SHA-256 lowercase hex，用于唯一约束和索引';
COMMENT ON COLUMN sync_metadata.business_key IS '同步对象业务键原文，采用 api-contract §6.2.2.4 的规范化 businessKey，不包含 tenantId/sourceService/entityKind';
COMMENT ON COLUMN sync_metadata.business_key_hash IS 'business_key 的 SHA-256 lowercase hex，用于唯一约束和索引';
COMMENT ON COLUMN sync_metadata.sync_key IS '来源内稳定同步键原文，用于定位同一外部事实，格式为 sourceService|entityKind|businessKey';
COMMENT ON COLUMN sync_metadata.sync_key_hash IS 'sync_key 的 SHA-256 lowercase hex，用于查询索引';
COMMENT ON COLUMN sync_metadata.target_id IS '目标表内部ID，仅 access-service 内部使用，不作为对外契约';
COMMENT ON COLUMN sync_metadata.target_status IS '目标同步状态：ABSTRACT_USER/ABSTRACT_ROLE/RESOURCE_ENTITY 仅允许 ACTIVE/DISABLED/DELETED；USER_ROLE 仅允许 ACTIVE/UNBOUND';
COMMENT ON COLUMN sync_metadata.last_sync_occurred_at IS '最后一次已应用同步事件发生时间';
COMMENT ON COLUMN sync_metadata.last_sync_sequence_no IS '最后一次已应用同步事件序号，和 occurred_at 共同判断新旧版本';

-- -----------------------------------------------------------------------------
-- 28. role_resource_permission - 角色-资源-操作中间表（支持子权限 depend_on，冗余 resource_type）
--     写链路：apply-grant-plan 唯一入口（记录级 plan{creates/updates/removes}）
--     只存勾选节点，查询接口支持展开父级/展开子级
--     scope_all=true 表示该操作覆盖 resource_type 下全部范围资源，此时 resource_entity_id 为空
-- -----------------------------------------------------------------------------
CREATE TABLE role_resource_permission (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               BIGINT NOT NULL,
    abstract_role_id        BIGINT NOT NULL,
    resource_entity_id      BIGINT,
    granted_bits            BIGINT NOT NULL,
    resource_type           INT NOT NULL,
    depend_on               BIGINT,
    scope_all               BOOLEAN NOT NULL DEFAULT false,
    can_grant              BOOLEAN NOT NULL DEFAULT false,
    condition_id            BIGINT,
    grant_source            VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    grant_dep_id            BIGINT,
    created_by              BIGINT,
    updated_by              BIGINT,
    deleted_by              BIGINT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    delete_flag             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_role_resource_permission_scope_all CHECK (
        (scope_all = false AND resource_entity_id IS NOT NULL)
        OR
        (scope_all = true AND resource_entity_id IS NULL)
    ),
    -- 主权限条件不可转授（T-PERM-041 评审确认）
    CONSTRAINT ck_role_resource_permission_condition_can_grant CHECK (
        condition_id IS NULL OR can_grant = false
    ),
    -- 子权限属性系统不变量：子权限不承载条件/再授予（20043 同口径，T-PERM-034 产品确认）
    CONSTRAINT ck_role_resource_permission_child_attributes CHECK (
        depend_on IS NULL OR (condition_id IS NULL AND can_grant = false)
    ),
    -- MANUAL 授权一行只对应一个操作定义；不再写入多操作组合位记录
    CONSTRAINT ck_role_resource_permission_manual_single_operation CHECK (
        COALESCE(grant_source, 'MANUAL') <> 'MANUAL'
        OR (granted_bits > 0 AND (granted_bits & (granted_bits - 1)) = 0)
    )
);

-- 通用来源记录仍按来源+条件防止完全重复；不改变 AUTO_DEP 的既有存储语义。
CREATE UNIQUE INDEX uk_role_resource_permission ON role_resource_permission (tenant_id, abstract_role_id, COALESCE(resource_entity_id, 0), resource_type, granted_bits, COALESCE(depend_on, 0), scope_all, COALESCE(grant_source, 'MANUAL'), COALESCE(condition_id, 0)) WHERE delete_flag = 0;
-- MANUAL 直接授权额外忽略 condition_id/can_grant 做唯一约束：二者是可变属性，
-- 同一角色 + 资源/范围 + 操作 + 父权限最多一条有效 MANUAL 记录；AUTO_DEP 不受此索引限制。
CREATE UNIQUE INDEX uk_role_resource_permission_manual_direct ON role_resource_permission (tenant_id, abstract_role_id, COALESCE(resource_entity_id, 0), resource_type, granted_bits, COALESCE(depend_on, 0), scope_all) WHERE delete_flag = 0 AND COALESCE(grant_source, 'MANUAL') = 'MANUAL';
CREATE INDEX idx_role_resource_permission_role ON role_resource_permission (abstract_role_id) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_resource ON role_resource_permission (resource_entity_id) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_depend ON role_resource_permission (depend_on) WHERE delete_flag = 0 AND depend_on IS NOT NULL;
CREATE INDEX idx_role_resource_permission_type ON role_resource_permission (tenant_id, resource_type) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_scope_all ON role_resource_permission (tenant_id, resource_type, granted_bits) WHERE delete_flag = 0 AND scope_all = true;

COMMENT ON TABLE role_resource_permission IS '角色对某资源某操作位的授权；同一角色+资源/范围+操作+父权限仅允许一条 MANUAL 直接授权，condition_id/can_grant 为可变属性；depend_on 实现子权限（单层）；scope_all=true 表示某资源类型全量范围授权；所属 resource_type 删除时该类型下有效授权行（scope_all 为主）同事务级联软删（T-PERM-050）';
COMMENT ON COLUMN role_resource_permission.resource_entity_id IS '资源实体ID；scope_all=false 时必填，scope_all=true 时为空';
COMMENT ON COLUMN role_resource_permission.granted_bits IS '授予的操作位；MANUAL 记录只存单个 operation_permission.binary_bit（2 的幂），配合 effective_bits / inherit_mask 实现覆盖判定';
COMMENT ON COLUMN role_resource_permission.resource_type IS '资源类型；普通授权时从 resource_entity 自动填充，scope_all=true 时用于标识全量范围资源类型';
COMMENT ON COLUMN role_resource_permission.depend_on IS '父权限ID（本表自引用），NULL=主权限，非NULL=子权限。单层依赖。删除父权限时级联软删子权限';
COMMENT ON COLUMN role_resource_permission.scope_all IS '是否覆盖该 resource_type 下全部范围资源；true 时 resource_entity_id 必须为空';
COMMENT ON COLUMN role_resource_permission.can_grant IS '是否可授权(该权限可被当前角色关联的用户授予他人)';
COMMENT ON COLUMN role_resource_permission.condition_id IS '生效条件ID（引用 permission_condition），NULL 表示始终生效';
COMMENT ON COLUMN role_resource_permission.grant_source IS '授权来源：MANUAL=手动授权，AUTO_DEP=resource_dependency 自动补全';
COMMENT ON COLUMN role_resource_permission.grant_dep_id IS '依赖规则ID（grant_source=AUTO_DEP 时记录触发的 resource_dependency.id）';

-- -----------------------------------------------------------------------------
-- 29. domain_config - 域配置表（SUB_PERM 子权限 / CLASSIFY 域分类；SCOPE/RELATION/BINDING 为历史设想类型，未实现）
-- -----------------------------------------------------------------------------
CREATE TABLE domain_config (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT NOT NULL,
    config_type   VARCHAR(32) NOT NULL,
    extra         JSONB NOT NULL DEFAULT '{}',
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

-- 唯一索引（T-PERM-046）：save 的 check-then-insert 并发双插窗口由 uk 兜底
-- （违例映射 20058 提示重试）；覆盖域配置查询全部用途（原普通索引已由本索引取代）
CREATE UNIQUE INDEX uk_domain_config ON domain_config (tenant_id, biz_domain_id, config_type) WHERE delete_flag = 0;

COMMENT ON TABLE domain_config IS '域配置：SUB_PERM=子权限配置 / CLASSIFY=域分类配置（仅此两类已实现并接受写入；SCOPE/RELATION/BINDING 为历史设想类型，未实现，请求校验拒绝）。每个域独立，无继承。CLASSIFY 挂全局域时生效（T-PERM-046，2026-09-09 定案：有声明按声明，无声明退动态补集）';
COMMENT ON COLUMN domain_config.config_type IS 'SUB_PERM / CLASSIFY（实现范围与 ConfigType 枚举一致；其余历史类型不分配）';
COMMENT ON COLUMN domain_config.extra IS 'SUB_PERM示例: {"allowed":[{"parent_type":"MENU","child_types":["BUTTON","DATA"]}]}, CLASSIFY示例: {"resourceTypeCodes":["ORG","USER"]}';

-- -----------------------------------------------------------------------------
-- 30. resource_dependency - 资源依赖表（操作位级别触发，支持自动补全）
-- -----------------------------------------------------------------------------
CREATE TABLE resource_dependency (
    id                            BIGSERIAL PRIMARY KEY,
    tenant_id                     BIGINT NOT NULL,
    resource_entity_id            BIGINT NOT NULL,
    depends_on_resource_entity_id BIGINT NOT NULL,
    source_operation_bits         BIGINT,
    required_operation_bits       BIGINT NOT NULL,
    auto_grant                    BOOLEAN NOT NULL DEFAULT false,
    owner_service_code            VARCHAR(128),
    maintain_source               VARCHAR(32) NOT NULL DEFAULT 'ADMIN_UI',
    sync_key                      VARCHAR(256),
    description                   VARCHAR(512),
    created_by                    BIGINT,
    updated_by                    BIGINT,
    deleted_by                    BIGINT,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                    TIMESTAMPTZ,
    delete_flag                   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_resource_dependency ON resource_dependency (tenant_id, resource_entity_id, depends_on_resource_entity_id, COALESCE(source_operation_bits, 0)) WHERE delete_flag = 0;
CREATE INDEX idx_resource_dependency_resource ON resource_dependency (resource_entity_id) WHERE delete_flag = 0;
CREATE INDEX idx_resource_dependency_sync_owner ON resource_dependency (tenant_id, owner_service_code, maintain_source) WHERE delete_flag = 0 AND owner_service_code IS NOT NULL;

COMMENT ON TABLE resource_dependency IS '资源依赖：resource_entity_id 是源资源/被授权资源；depends_on_resource_entity_id 是被源资源依赖、需要自动补全的目标资源。source_operation_bits 为触发条件，required_operation_bits 为目标资源需要的操作位。auto_grant 为预留字段（自动授权未实现，T-PERM-035 暂缓）：实现前所有写入口拒绝 true（错误码 20048），依赖补全不生效';
COMMENT ON COLUMN resource_dependency.resource_entity_id IS '源资源ID（被授权资源）。授权该资源且满足 source_operation_bits 时触发依赖补全';
COMMENT ON COLUMN resource_dependency.depends_on_resource_entity_id IS '被依赖资源ID（自动补全目标资源），即被 resource_entity_id 依赖的资源';
COMMENT ON COLUMN resource_dependency.source_operation_bits IS '触发条件：源资源授权含这些bit时才触发依赖，NULL=任意操作都触发；唯一约束中按 COALESCE(source_operation_bits,0) 区分同一资源对下不同触发操作';
COMMENT ON COLUMN resource_dependency.required_operation_bits IS '被依赖目标资源需要自动补全的操作位';
COMMENT ON COLUMN resource_dependency.auto_grant IS '预留未实现：自动授权暂缓（T-PERM-035），写入口仅接受 false（true 返回 20048），默认 false';
COMMENT ON COLUMN resource_dependency.owner_service_code IS '依赖规则维护方服务编码；批量同步时用于限定 FULL diff 删除范围';
COMMENT ON COLUMN resource_dependency.maintain_source IS '维护来源：ADMIN_UI=管理端维护，SDK_SCAN=SDK扫描，MANIFEST=声明式清单，SERVICE_SYNC=服务同步';
COMMENT ON COLUMN resource_dependency.sync_key IS '同步源内稳定键，用于 FULL diff 判断。不同维护来源只清理同 owner_service_code + maintain_source 范围内缺失的规则';

-- -----------------------------------------------------------------------------
-- 31. permission_conflict_rule - 权限冲突规则表（角色互斥 + 权限互斥）
-- -----------------------------------------------------------------------------
CREATE TABLE permission_conflict_rule (
    id                             BIGSERIAL PRIMARY KEY,
    tenant_id                      BIGINT NOT NULL,
    conflict_type                  VARCHAR(16) NOT NULL DEFAULT 'PERM_MUTEX',
    -- PERM_MUTEX 字段
    first_operation_permission_id  BIGINT,
    second_operation_permission_id BIGINT,
    resource_type_value            INT,
    -- ROLE_MUTEX 字段
    first_abstract_role_id         BIGINT,
    second_abstract_role_id        BIGINT,
    description                    VARCHAR(512),
    created_by                     BIGINT,
    updated_by                     BIGINT,
    deleted_by                     BIGINT,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                     TIMESTAMPTZ,
    delete_flag                    BIGINT NOT NULL DEFAULT 0
);

-- 权限互斥唯一约束（含 resource_type_value，允许同操作对不同资源类型；NULLS NOT DISTINCT 使 NULL 全局规则也受唯一约束，防并发重复插入）
CREATE UNIQUE INDEX uk_conflict_rule_perm ON permission_conflict_rule (tenant_id, first_operation_permission_id, second_operation_permission_id, resource_type_value) NULLS NOT DISTINCT WHERE conflict_type = 'PERM_MUTEX' AND delete_flag = 0;
-- 角色互斥唯一约束
CREATE UNIQUE INDEX uk_conflict_rule_role ON permission_conflict_rule (tenant_id, first_abstract_role_id, second_abstract_role_id) WHERE conflict_type = 'ROLE_MUTEX' AND delete_flag = 0;

COMMENT ON TABLE permission_conflict_rule IS '冲突规则：ROLE_MUTEX=角色互斥(写入检查拒绝) / PERM_MUTEX=权限互斥(查询时失效+异步通知)。存库时 first_id < second_id';
COMMENT ON COLUMN permission_conflict_rule.conflict_type IS 'ROLE_MUTEX=角色互斥 / PERM_MUTEX=权限互斥';
COMMENT ON COLUMN permission_conflict_rule.first_operation_permission_id IS '互斥操作一（PERM_MUTEX 时使用）';
COMMENT ON COLUMN permission_conflict_rule.second_operation_permission_id IS '互斥操作二（PERM_MUTEX 时使用）';
COMMENT ON COLUMN permission_conflict_rule.resource_type_value IS '资源类型枚举值（PERM_MUTEX 时使用），NULL=所有';
COMMENT ON COLUMN permission_conflict_rule.first_abstract_role_id IS '互斥角色一（ROLE_MUTEX 时使用）';
COMMENT ON COLUMN permission_conflict_rule.second_abstract_role_id IS '互斥角色二（ROLE_MUTEX 时使用）';

-- -----------------------------------------------------------------------------
-- 32. permission_change_log - 权限变更记录表（详细权限变更 diff）
-- -----------------------------------------------------------------------------
CREATE TABLE permission_change_log (
    id                         BIGSERIAL PRIMARY KEY,
    tenant_id                  BIGINT NOT NULL,
    entity_type                VARCHAR(64) NOT NULL,
    entity_id                  BIGINT,
    operation                  VARCHAR(16) NOT NULL,
    old_snapshot               JSONB,
    new_snapshot               JSONB,
    diff_snapshot              JSONB,
    affected_abstract_user_ids BIGINT[] DEFAULT '{}',
    affected_abstract_role_ids BIGINT[] DEFAULT '{}',
    change_reason              VARCHAR(512),
    change_source              VARCHAR(32) NOT NULL,
    request_id                 VARCHAR(64),
    created_by                 BIGINT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_change_log_tenant_users ON permission_change_log USING GIN (affected_abstract_user_ids);
CREATE INDEX idx_change_log_tenant_roles ON permission_change_log USING GIN (affected_abstract_role_ids);
CREATE INDEX idx_change_log_tenant_time ON permission_change_log (tenant_id, created_at DESC);
CREATE INDEX idx_change_log_entity ON permission_change_log (tenant_id, entity_type, entity_id);
CREATE INDEX idx_change_log_request_id ON permission_change_log (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX idx_change_log_event_time ON permission_change_log (tenant_id, (diff_snapshot->>'eventType'), created_at DESC) WHERE diff_snapshot IS NOT NULL;

COMMENT ON TABLE permission_change_log IS '权限变更记录：详细记录权限相关变更的 before/after/diff，方便排查用户因配置问题导致权限失效';
COMMENT ON COLUMN permission_change_log.entity_type IS '变更实体类型：user_role/role_resource_permission/abstract_user/abstract_role 等';
COMMENT ON COLUMN permission_change_log.operation IS '操作：INSERT/UPDATE/DELETE/BATCH_DELETE/BATCH_REMOVE（后两者为 entityId=0 批量聚合行专用）';
COMMENT ON COLUMN permission_change_log.old_snapshot IS '变更前快照(JSON)';
COMMENT ON COLUMN permission_change_log.new_snapshot IS '变更后快照(JSON)';
COMMENT ON COLUMN permission_change_log.diff_snapshot IS '结构化变更摘要(JSON)，用于权限排查展示和筛选。顶层包含 eventType + items[]，eventType/changeType 使用契约固定枚举；只描述本次写操作直接改变了什么，不计算用户最终有效权限 diff';
COMMENT ON COLUMN permission_change_log.change_source IS '变更来源：MANUAL/SERVICE_SYNC（复用 PermConstants.MaintainSource）';
COMMENT ON COLUMN permission_change_log.request_id IS '请求/追踪ID(trace_id)，同一次操作的多条记录通过此关联';

-- -----------------------------------------------------------------------------
-- 33. sys_task_execution - 任务执行表（T-ACCESS-009 任务租约预建）
--     多实例任务协调（access-service-architecture §8.1）：
--       同一计划触发使用稳定 execution_key + 唯一约束，数据库原子抢占保证同一时刻
--       最多一个活动执行者；lease_owner/lease_until 承载租约与故障接管；
--       execution_key 即幂等标识，外部副作用携带执行键。
--     本任务只建表与基础字段映射；原子抢占/续租/接管/条件完成 SQL 由 T-ACCESS-009 扩展。
-- -----------------------------------------------------------------------------
CREATE TABLE sys_task_execution (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    execution_key VARCHAR(192) NOT NULL,   -- 计划实例稳定键（如 jobId_scheduledTime）
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',  -- PENDING/RUNNING/SUCCESS/FAILED
    lease_owner   VARCHAR(128),            -- 租约持有实例标识
    lease_until   TIMESTAMPTZ,             -- 租约截止（数据库时间原子更新）
    attempt_count INT NOT NULL DEFAULT 0,  -- 幂等重试次数
    last_error    VARCHAR(1024),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_task_execution ON sys_task_execution (tenant_id, execution_key) WHERE delete_flag = 0;

COMMENT ON TABLE sys_task_execution IS '任务执行记录：多实例下同一执行键最多一个活动执行者（唯一约束保证），租约由 lease_owner/lease_until 承载，execution_key 即幂等标识；本表由 T-ACCESS-009 使用，原子并发 SQL 在 T-ACCESS-009 扩展';
COMMENT ON COLUMN sys_task_execution.execution_key IS '计划实例稳定执行键（如 jobId + scheduledTime），租户内唯一；外部副作用携带该键实现幂等';
COMMENT ON COLUMN sys_task_execution.status IS '执行状态：PENDING/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN sys_task_execution.lease_owner IS '租约持有实例标识（如 host:pid），故障后其他实例可接管';
COMMENT ON COLUMN sys_task_execution.lease_until IS '租约截止时间；过期后其他实例可原子抢占（T-ACCESS-009 实现）';
COMMENT ON COLUMN sys_task_execution.attempt_count IS '已尝试执行次数（幂等重试计数）';
COMMENT ON COLUMN sys_task_execution.last_error IS '最近一次失败原因';
COMMENT ON COLUMN sys_task_execution.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';
