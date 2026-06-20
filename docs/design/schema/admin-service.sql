-- =============================================================================
-- 管理服务 (admin-service) - PostgreSQL 表结构（18 张表）
-- 无外键，逻辑关联由应用保证
-- =============================================================================
-- 软删约定（与权限中心一致）：
--   delete_flag BIGINT：0 = 未删除，删除时填本行 id（确保唯一约束不冲突）
--   deleted_at TIMESTAMPTZ：纯审计字段，记录删除时间，不参与索引条件
--   所有唯一索引和业务查询统一使用 WHERE delete_flag = 0
-- 例外：sys_login_log、sys_audit_log、sys_job_log 不做软删除
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
COMMENT ON COLUMN sys_oauth2_client.access_token_ttl IS 'Access Token 有效期（秒），默认 7200（2小时）';
COMMENT ON COLUMN sys_oauth2_client.refresh_token_ttl IS 'Refresh Token 有效期（秒），默认 2592000（30天）';
COMMENT ON COLUMN sys_oauth2_client.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN sys_oauth2_client.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- 预置客户端数据
INSERT INTO sys_oauth2_client (tenant_id, client_id, client_secret, client_name, grant_types, redirect_uris, scopes, access_token_ttl, refresh_token_ttl, status, created_by, created_at, updated_at)
VALUES
    (1, 'admin-web',        '$2a$10$PLACEHOLDER_HASH_1', '管理端前端',  'authorization_code,password,refresh_token', 'http://localhost:3000/callback', 'all', 7200, 2592000, 1, 0, now(), now()),
    (1, 'example-web',      '$2a$10$PLACEHOLDER_HASH_2', '演示端前端',  'authorization_code,password,refresh_token', 'http://localhost:3001/callback', 'all', 7200, 2592000, 1, 0, now(), now()),
    (1, 'internal-service', '$2a$10$PLACEHOLDER_HASH_3', '服务间调用',  'client_credentials',                        NULL,                            'all', 7200, 0,       1, 0, now(), now());

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
-- 3. sys_user - 用户表（事实源，同步到权限中心 abstract_user）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
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

COMMENT ON TABLE sys_user IS '用户表，admin-service 事实源；默认组织树是用户目录/身份池，负责用户生命周期';
COMMENT ON COLUMN sys_user.username IS '登录账号，租户内唯一';
COMMENT ON COLUMN sys_user.password IS '密码（BCrypt 加密，前端 SHA256 摘要传输）';
COMMENT ON COLUMN sys_user.gender IS '性别：0=未知，1=男，2=女';
COMMENT ON COLUMN sys_user.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN sys_user.user_type IS '用户类型（对应权限中心 user_type），默认 1=人员';
COMMENT ON COLUMN sys_user.force_reset_pwd IS '是否需要强制修改密码（首次登录/管理员重置后）';
COMMENT ON COLUMN sys_user.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 5. sys_org - 统一组织表（部门/岗位/团队同表，org_type 仅标签）
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

COMMENT ON TABLE sys_org IS '统一组织表：部门/岗位/团队同表；默认组织树承担用户目录语义，非默认树只管理成员关系；组织/岗位同步为 ADMIN_ORG resource_entity（管理权限）和 ORG/POSITION abstract_role（角色容器），均使用业务键定位，不存 permission-center 内部 ID';
COMMENT ON COLUMN sys_org.parent_id IS '父节点ID，NULL=根节点';
COMMENT ON COLUMN sys_org.org_type IS '组织类型标签（字典管理），仅分类用';
COMMENT ON COLUMN sys_org.code IS '组织编码，租户内唯一';
COMMENT ON COLUMN sys_org.path IS '物化路径（如 /1/3/7/），加速树查询';
COMMENT ON COLUMN sys_org.level IS '层级深度（根节点=1），最大 10 层';
COMMENT ON COLUMN sys_org.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 6. sys_org_tree_config - 组织树配置
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
-- 7. sys_user_org - 用户-组织关联（多对多）
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
-- 8. sys_menu - 菜单表（UI 路由元数据 + 关联资源 link，v3.5 菜单零权限化）
--    *** schema 迁移 2026-06-20 审计 S-002=B（v3.5 §2.1 最终态）***
--    menu_type 改 5 值 ENUM(DIR/MENU/EXTERNAL/IFRAME/HIDDEN)，删除 BUTTON；
--    删除 perm_code/visible/is_external/is_frame/is_cache/component/service_code 字段
--    （权限语义不再由 sys_menu 承载，菜单可见性由 v3.5 §4.1 ∃ op 派生公式计算）；
--    新增 source_service/resource_type/resource_code 关联业务资源 link。
--    迁移期存量数据需：BUTTON 行归 v3.5.1+ 评估；perm_code 唯一索引下线。
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

COMMENT ON TABLE sys_menu IS '菜单表：admin-service 事实源，仅承载 UI 路由元数据 + 关联资源 link（v3.5 菜单零权限化，不承载权限语义）';
COMMENT ON COLUMN sys_menu.menu_type IS '类型：DIR=目录，MENU=菜单，EXTERNAL=外链，IFRAME=嵌入，HIDDEN=隐藏路由（派生同 MENU，不进 menus[] 下发 hiddenRoutes[]）';
COMMENT ON COLUMN sys_menu.status IS '状态：0=DISABLED，1=ENABLED';
COMMENT ON COLUMN sys_menu.resource_type IS '关联业务资源类型（不参与鉴权决策，仅 link；v3.5 §4.1 派生公式用）';
COMMENT ON COLUMN sys_menu.resource_code IS '关联业务资源实例（不参与鉴权决策，仅 link）';
COMMENT ON COLUMN sys_menu.source_service IS '业务服务标识（链路追溯，替代原 service_code）';
COMMENT ON COLUMN sys_menu.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- 已废弃字段（迁移期物理删除）：perm_code / operations / primary_operation / default_preset /
--   visible / is_external / is_frame / is_cache / component / extra / service_code
-- 已废弃索引：uk_menu_perm_code（perm_code 唯一索引下线）、idx_menu_service_tenant（service_code 删除）

-- -----------------------------------------------------------------------------
-- 9. sys_dict_type - 字典类型
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
-- 10. sys_dict_data - 字典数据
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
-- 11. sys_notice - 通知/公告
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
-- 12. sys_user_notice - 用户通知状态
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
-- 13. sys_file - 文件元信息
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

COMMENT ON TABLE sys_file IS '文件元信息，实际文件存储在 S3 兼容对象存储中';
COMMENT ON COLUMN sys_file.original_name IS '原始文件名';
COMMENT ON COLUMN sys_file.file_name IS '存储文件名（UUID）';
COMMENT ON COLUMN sys_file.file_path IS '存储路径（bucket/path）';
COMMENT ON COLUMN sys_file.file_url IS '访问URL';
COMMENT ON COLUMN sys_file.file_size IS '文件大小（字节）';
COMMENT ON COLUMN sys_file.file_type IS 'MIME 类型';
COMMENT ON COLUMN sys_file.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 14. sys_audit_log - 审计日志（不做软删除，永久保留）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    user_id       BIGINT,
    username      VARCHAR(64),
    module        VARCHAR(64),
    action        VARCHAR(64),
    target_type   VARCHAR(64),
    target_id     VARCHAR(64),
    summary       VARCHAR(512),
    ip_address    VARCHAR(64),
    request_id    VARCHAR(64),
    request_url   VARCHAR(256),
    request_body  TEXT,
    response_code INT,
    cost_time     INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tenant_time ON sys_audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_user ON sys_audit_log (tenant_id, user_id);

COMMENT ON TABLE sys_audit_log IS '审计日志，不做软删除，永久保留';
COMMENT ON COLUMN sys_audit_log.module IS '模块名（用户管理/组织管理等）';
COMMENT ON COLUMN sys_audit_log.action IS '操作类型：CREATE/UPDATE/DELETE 等';
COMMENT ON COLUMN sys_audit_log.target_type IS '目标类型：USER/ORG/MENU 等';
COMMENT ON COLUMN sys_audit_log.request_body IS '请求体（敏感字段已脱敏）';
COMMENT ON COLUMN sys_audit_log.cost_time IS '耗时（毫秒）';

-- -----------------------------------------------------------------------------
-- 15. sys_job - 定时任务
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
-- 16. sys_job_log - 任务执行日志（不做软删除）
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
-- 17. sys_config - 系统配置
-- -----------------------------------------------------------------------------
CREATE TABLE sys_config (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    config_key   VARCHAR(128) NOT NULL,
    config_value JSONB NOT NULL DEFAULT '{}',
    config_name  VARCHAR(256),
    remark       VARCHAR(512),
    is_system    BOOLEAN NOT NULL DEFAULT false,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    delete_flag  BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_config_key ON sys_config (tenant_id, config_key) WHERE delete_flag = 0;

COMMENT ON TABLE sys_config IS '系统配置，支持租户级覆盖';
COMMENT ON COLUMN sys_config.config_key IS '配置键';
COMMENT ON COLUMN sys_config.config_value IS '配置值（JSON）';
COMMENT ON COLUMN sys_config.is_system IS '是否系统内置（不可删除）';
COMMENT ON COLUMN sys_config.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 18. sys_sync_task - 同步任务表（本地消息表）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_sync_task (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT NOT NULL,
    message_key           VARCHAR(192) NOT NULL,
    sync_action           VARCHAR(64) NOT NULL,
    business_key          TEXT NOT NULL,
    business_key_hash     CHAR(64) NOT NULL,
    batch_key             TEXT,
    batch_key_hash        CHAR(64),
    target_service        VARCHAR(64) NOT NULL DEFAULT 'permission-center',
    payload               JSONB NOT NULL DEFAULT '{}',
    payload_version       INT NOT NULL DEFAULT 1,
    display_attrs         JSONB NOT NULL DEFAULT '{}',
    sync_occurred_at      TIMESTAMPTZ NOT NULL,
    sync_sequence_no      BIGINT NOT NULL,
    phase                 VARCHAR(64),
    retry_count           INT NOT NULL DEFAULT 0,
    max_retries           INT NOT NULL DEFAULT 5,
    next_retry_at         TIMESTAMPTZ,
    locked_at             TIMESTAMPTZ,
    locked_by             VARCHAR(128),
    last_error            VARCHAR(1024),
    status                VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_by            BIGINT,
    updated_by            BIGINT,
    deleted_by            BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    delete_flag           BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sync_task_message_key ON sys_sync_task (tenant_id, message_key) WHERE delete_flag = 0;
CREATE UNIQUE INDEX uk_sync_task_pending_business ON sys_sync_task (tenant_id, sync_action, business_key_hash) WHERE status = 'PENDING' AND delete_flag = 0;
CREATE INDEX idx_sync_task_due ON sys_sync_task (tenant_id, status, phase, next_retry_at) WHERE delete_flag = 0;
CREATE INDEX idx_sync_task_business_key ON sys_sync_task (tenant_id, sync_action, business_key_hash) WHERE delete_flag = 0;
CREATE INDEX idx_sync_task_batch_phase ON sys_sync_task (tenant_id, batch_key_hash, phase, status) WHERE delete_flag = 0 AND batch_key_hash IS NOT NULL;

COMMENT ON TABLE sys_sync_task IS '同步任务表（本地消息表），保障与权限中心数据一致性；主业务事务内写任务，事务外重放';
COMMENT ON COLUMN sys_sync_task.message_key IS '事件唯一键，每次业务变更唯一；合并 PENDING 任务时覆盖为最新事件 key，旧事件 key 不再保留';
COMMENT ON COLUMN sys_sync_task.sync_action IS '同步动作：PERM_ABSTRACT_USER_SYNC/PERM_ABSTRACT_ROLE_SYNC/PERM_USER_ROLE_SYNC/PERM_RESOURCE_ENTITY_SYNC';
COMMENT ON COLUMN sys_sync_task.business_key IS '同步业务键原文，采用 api-contract §6.2.2.4 的规范化 businessKey；用于排查，不直接参与唯一索引';
COMMENT ON COLUMN sys_sync_task.business_key_hash IS 'business_key 的 SHA-256 lowercase hex，用于唯一约束和索引';
COMMENT ON COLUMN sys_sync_task.batch_key IS '全量校准批次键原文；单次实时同步为空。全量任务同一批次共享同一 batch_key';
COMMENT ON COLUMN sys_sync_task.batch_key_hash IS 'batch_key 的 SHA-256 lowercase hex，用于同批次 phase 推进查询';
COMMENT ON COLUMN sys_sync_task.target_service IS '目标服务（如 permission-center）';
COMMENT ON COLUMN sys_sync_task.payload IS '同步请求参数快照，必须匹配 sync_action 对应的强类型 DTO';
COMMENT ON COLUMN sys_sync_task.payload_version IS 'payload 契约版本；handler 遇到高于自身支持上限的版本必须拒绝并置为不可重试失败';
COMMENT ON COLUMN sys_sync_task.display_attrs IS '仅供 UI/审计展示的冗余信息，如 entityType/externalId/operationType；严禁用于执行路由或业务判断';
COMMENT ON COLUMN sys_sync_task.sync_occurred_at IS '源事件发生时间，参与 syncVersion 乱序判断';
COMMENT ON COLUMN sys_sync_task.sync_sequence_no IS '源事件序号，和 sync_occurred_at 共同构成 syncVersion';
COMMENT ON COLUMN sys_sync_task.phase IS '执行阶段枚举：USER_SUBJECT/USER_RESOURCE/ORG_RESOURCE/ORG_ROLE/USER_ROLE/MENU_RESOURCE/OTHER_RESOURCE；阶段推进规则见 admin-service.md';
COMMENT ON COLUMN sys_sync_task.retry_count IS '已重试次数';
COMMENT ON COLUMN sys_sync_task.max_retries IS '最大自动重试次数';
COMMENT ON COLUMN sys_sync_task.next_retry_at IS '下次重试时间（退避策略计算）';
COMMENT ON COLUMN sys_sync_task.locked_at IS '任务认领时间，用于多实例调度防重复执行';
COMMENT ON COLUMN sys_sync_task.locked_by IS '任务认领节点标识';
COMMENT ON COLUMN sys_sync_task.last_error IS '最后一次失败原因；首期不建 attempt 明细表';
COMMENT ON COLUMN sys_sync_task.status IS '状态：PENDING/PROCESSING/SUCCESS/FAILED';

-- 预置配置项
INSERT INTO sys_config (tenant_id, config_key, config_value, config_name, is_system, created_by, created_at, updated_at)
VALUES
    (1, 'LOGIN_CAPTCHA_ENABLED',   'true',      '是否开启图形验证码',       true, 0, now(), now()),
    (1, 'LOGIN_SMS_ENABLED',       'false',     '是否开启短信验证码',       true, 0, now(), now()),
    (1, 'LOGIN_FAIL_LOCK_COUNT',   '5',         '密码错误锁定次数',         true, 0, now(), now()),
    (1, 'LOGIN_FAIL_LOCK_MINUTES', '30',        '锁定时长（分钟）',         true, 0, now(), now()),
    (1, 'LOGIN_SINGLE_DEVICE',     'false',     '单设备登录',               true, 0, now(), now()),
    (1, 'LOGIN_REMOTE_ALERT',      'false',     '异地登录提醒',             true, 0, now(), now()),
    (1, 'MENU_MAX_DEPTH',          '7',         '菜单树最大深度',           true, 0, now(), now()),
    (1, 'FILE_UPLOAD_MAX_SIZE',    '10485760',  '文件上传大小限制（字节）', true, 0, now(), now()),
    (1, 'FILE_ALLOWED_TYPES',      '["image/jpeg","image/png","image/gif","application/pdf","application/zip","text/plain"]', '允许的文件类型列表', true, 0, now(), now());
