-- =============================================================================
-- 通用权限中心 - PostgreSQL 表结构（17 张表）
-- 无外键，逻辑关联由应用保证
-- 执行顺序按依赖关系，建议按序号依次执行
-- =============================================================================
-- 软删约定：
--   delete_flag BIGINT：0 = 未删除，删除时填本行 id（确保唯一约束不冲突）
--   deleted_at TIMESTAMPTZ：纯审计字段，记录删除时间，不参与索引条件
--   所有唯一索引和业务查询统一使用 WHERE delete_flag = 0
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 类型定义表（原 system_config，专用于类型枚举定义）
-- -----------------------------------------------------------------------------
CREATE TABLE type_definition (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT,
    type_key      VARCHAR(64) NOT NULL,
    type_value    INT NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    sort_order    INT DEFAULT 0,
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_type_definition_domain ON type_definition (tenant_id, biz_domain_id, type_key, type_value) WHERE biz_domain_id IS NOT NULL AND delete_flag = 0;
CREATE UNIQUE INDEX uk_type_definition_global ON type_definition (tenant_id, type_key, type_value) WHERE biz_domain_id IS NULL AND delete_flag = 0;

COMMENT ON TABLE type_definition IS '类型定义：type_key 如 user_type/role_type/resource_type，type_value 为枚举整型';
COMMENT ON COLUMN type_definition.id IS '主键';
COMMENT ON COLUMN type_definition.tenant_id IS '租户ID';
COMMENT ON COLUMN type_definition.biz_domain_id IS '业务域ID，NULL 表示全局类型';
COMMENT ON COLUMN type_definition.type_key IS '类型键，如 user_type、role_type、resource_type';
COMMENT ON COLUMN type_definition.type_value IS '枚举值，如 1=人员 2=服务 3=第三方';
COMMENT ON COLUMN type_definition.name IS '显示名称';
COMMENT ON COLUMN type_definition.description IS '描述';
COMMENT ON COLUMN type_definition.sort_order IS '排序';
COMMENT ON COLUMN type_definition.created_by IS '创建人ID';
COMMENT ON COLUMN type_definition.updated_by IS '更新人ID';
COMMENT ON COLUMN type_definition.deleted_by IS '删除人ID';
COMMENT ON COLUMN type_definition.created_at IS '创建时间';
COMMENT ON COLUMN type_definition.updated_at IS '更新时间';
COMMENT ON COLUMN type_definition.deleted_at IS '软删时间（审计用，不参与索引条件）';
COMMENT ON COLUMN type_definition.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 1. 业务域表
-- -----------------------------------------------------------------------------
CREATE TABLE biz_domain (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(128) NOT NULL,
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

COMMENT ON TABLE biz_domain IS '业务域，对权限对象分类';
COMMENT ON COLUMN biz_domain.id IS '主键';
COMMENT ON COLUMN biz_domain.tenant_id IS '租户ID';
COMMENT ON COLUMN biz_domain.code IS '域编码';
COMMENT ON COLUMN biz_domain.name IS '域名称';
COMMENT ON COLUMN biz_domain.description IS '描述';
COMMENT ON COLUMN biz_domain.created_by IS '创建人ID';
COMMENT ON COLUMN biz_domain.updated_by IS '更新人ID';
COMMENT ON COLUMN biz_domain.deleted_by IS '删除人ID';
COMMENT ON COLUMN biz_domain.created_at IS '创建时间';
COMMENT ON COLUMN biz_domain.updated_at IS '更新时间';
COMMENT ON COLUMN biz_domain.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN biz_domain.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 2. 抽象用户表（不含 biz_domain_id，通过角色关联域）
-- -----------------------------------------------------------------------------
CREATE TABLE abstract_user (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    user_type   INT NOT NULL,
    external_id VARCHAR(256) NOT NULL,
    name        VARCHAR(256),
    extra       JSONB DEFAULT '{}',
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_abstract_user ON abstract_user (tenant_id, user_type, external_id) WHERE delete_flag = 0;
CREATE INDEX idx_abstract_user_tenant ON abstract_user (tenant_id) WHERE delete_flag = 0;

COMMENT ON TABLE abstract_user IS '抽象用户，user_type 来自 type_definition';
COMMENT ON COLUMN abstract_user.id IS '主键';
COMMENT ON COLUMN abstract_user.tenant_id IS '租户ID';
COMMENT ON COLUMN abstract_user.user_type IS '用户类型枚举值，来自 type_definition.type_key=user_type';
COMMENT ON COLUMN abstract_user.external_id IS '外部业务系统唯一标识';
COMMENT ON COLUMN abstract_user.name IS '显示名';
COMMENT ON COLUMN abstract_user.extra IS '扩展属性(JSON)';
COMMENT ON COLUMN abstract_user.created_by IS '创建人ID';
COMMENT ON COLUMN abstract_user.updated_by IS '更新人ID';
COMMENT ON COLUMN abstract_user.deleted_by IS '删除人ID';
COMMENT ON COLUMN abstract_user.created_at IS '创建时间';
COMMENT ON COLUMN abstract_user.updated_at IS '更新时间';
COMMENT ON COLUMN abstract_user.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN abstract_user.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 3. 抽象角色表（支持树形，biz_domain_id 可空表示全局角色）
-- -----------------------------------------------------------------------------
CREATE TABLE abstract_role (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT,
    role_type     INT NOT NULL,
    parent_id     BIGINT,
    external_id   VARCHAR(256),
    name          VARCHAR(256) NOT NULL,
    path          VARCHAR(1024),
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

CREATE INDEX idx_abstract_role_tenant_domain ON abstract_role (tenant_id, biz_domain_id) WHERE delete_flag = 0;
CREATE INDEX idx_abstract_role_parent ON abstract_role (parent_id) WHERE delete_flag = 0;
CREATE INDEX idx_abstract_role_path ON abstract_role (path) WHERE delete_flag = 0 AND path IS NOT NULL;

COMMENT ON TABLE abstract_role IS '抽象角色，树形；biz_domain_id 为 NULL 表示全局角色';
COMMENT ON COLUMN abstract_role.id IS '主键';
COMMENT ON COLUMN abstract_role.tenant_id IS '租户ID';
COMMENT ON COLUMN abstract_role.biz_domain_id IS '所属业务域ID，NULL 表示全局角色';
COMMENT ON COLUMN abstract_role.role_type IS '角色类型枚举，来自 type_definition';
COMMENT ON COLUMN abstract_role.parent_id IS '父节点ID，NULL 为根';
COMMENT ON COLUMN abstract_role.external_id IS '外部业务标识';
COMMENT ON COLUMN abstract_role.name IS '名称';
COMMENT ON COLUMN abstract_role.path IS '树路径，如 /1/2/3';
COMMENT ON COLUMN abstract_role.sort_order IS '同层排序';
COMMENT ON COLUMN abstract_role.extra IS '扩展属性(JSON)';
COMMENT ON COLUMN abstract_role.created_by IS '创建人ID';
COMMENT ON COLUMN abstract_role.updated_by IS '更新人ID';
COMMENT ON COLUMN abstract_role.deleted_by IS '删除人ID';
COMMENT ON COLUMN abstract_role.created_at IS '创建时间';
COMMENT ON COLUMN abstract_role.updated_at IS '更新时间';
COMMENT ON COLUMN abstract_role.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN abstract_role.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 4. 操作权限表（绑定资源类型，binary_bit + inherit_mask 用 BIGINT）
--    resource_type 替代原 biz_domain_id，直接表达操作适用的资源类型
--    effective = binary_bit | inherit_mask
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
CREATE UNIQUE INDEX uk_operation_permission_global ON operation_permission (tenant_id, code) WHERE resource_type IS NULL AND delete_flag = 0;

COMMENT ON TABLE operation_permission IS '操作权限，绑定资源类型；effective = binary_bit | inherit_mask';
COMMENT ON COLUMN operation_permission.id IS '主键';
COMMENT ON COLUMN operation_permission.tenant_id IS '租户ID';
COMMENT ON COLUMN operation_permission.resource_type IS '适用的资源类型枚举值（来自 type_definition.type_key=resource_type），NULL 表示适用所有资源类型';
COMMENT ON COLUMN operation_permission.code IS '操作编码，如 VIEW、EDIT';
COMMENT ON COLUMN operation_permission.name IS '显示名';
COMMENT ON COLUMN operation_permission.binary_bit IS '本操作独占位，如 1、2、4、8（BIGINT 支持 63 个独立操作）';
COMMENT ON COLUMN operation_permission.inherit_mask IS '继承的位掩码，实际权限=binary_bit|inherit_mask';
COMMENT ON COLUMN operation_permission.created_by IS '创建人ID';
COMMENT ON COLUMN operation_permission.updated_by IS '更新人ID';
COMMENT ON COLUMN operation_permission.deleted_by IS '删除人ID';
COMMENT ON COLUMN operation_permission.created_at IS '创建时间';
COMMENT ON COLUMN operation_permission.updated_at IS '更新时间';
COMMENT ON COLUMN operation_permission.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN operation_permission.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 5. 权限资源实体表（树形，biz_domain_id 可空表示全局资源）
--    资源树继承由查询接口参数控制，不在表结构中定义
-- -----------------------------------------------------------------------------
CREATE TABLE resource_entity (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT,
    parent_id     BIGINT,
    code          VARCHAR(128) NOT NULL,
    name          VARCHAR(256) NOT NULL,
    resource_type INT,
    path          VARCHAR(1024),
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

CREATE UNIQUE INDEX uk_resource_entity_domain ON resource_entity (tenant_id, biz_domain_id, code) WHERE biz_domain_id IS NOT NULL AND delete_flag = 0;
CREATE UNIQUE INDEX uk_resource_entity_global ON resource_entity (tenant_id, code) WHERE biz_domain_id IS NULL AND delete_flag = 0;
CREATE INDEX idx_resource_entity_tenant_domain ON resource_entity (tenant_id, biz_domain_id) WHERE delete_flag = 0;
CREATE INDEX idx_resource_entity_parent ON resource_entity (parent_id) WHERE delete_flag = 0;

COMMENT ON TABLE resource_entity IS '权限资源实体，树形；resource_type 来自 type_definition';
COMMENT ON COLUMN resource_entity.id IS '主键';
COMMENT ON COLUMN resource_entity.tenant_id IS '租户ID';
COMMENT ON COLUMN resource_entity.biz_domain_id IS '所属业务域ID，NULL 表示全局资源';
COMMENT ON COLUMN resource_entity.parent_id IS '父节点ID';
COMMENT ON COLUMN resource_entity.code IS '资源编码';
COMMENT ON COLUMN resource_entity.name IS '名称';
COMMENT ON COLUMN resource_entity.resource_type IS '资源类型枚举，来自 type_definition';
COMMENT ON COLUMN resource_entity.path IS '树路径';
COMMENT ON COLUMN resource_entity.sort_order IS '同层排序';
COMMENT ON COLUMN resource_entity.extra IS '扩展属性(JSON)';
COMMENT ON COLUMN resource_entity.created_by IS '创建人ID';
COMMENT ON COLUMN resource_entity.updated_by IS '更新人ID';
COMMENT ON COLUMN resource_entity.deleted_by IS '删除人ID';
COMMENT ON COLUMN resource_entity.created_at IS '创建时间';
COMMENT ON COLUMN resource_entity.updated_at IS '更新时间';
COMMENT ON COLUMN resource_entity.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN resource_entity.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 6. 接口资源映射表
-- -----------------------------------------------------------------------------
CREATE TABLE resource_api_mapping (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL,
    biz_domain_id      BIGINT,
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

COMMENT ON TABLE resource_api_mapping IS '接口资源映射：把 API 类资源显式映射到 service_code + http_method + path_pattern';
COMMENT ON COLUMN resource_api_mapping.id IS '主键';
COMMENT ON COLUMN resource_api_mapping.tenant_id IS '租户ID';
COMMENT ON COLUMN resource_api_mapping.biz_domain_id IS '所属业务域ID，NULL 表示全局映射';
COMMENT ON COLUMN resource_api_mapping.resource_entity_id IS '关联的资源实体ID，通常为 API 类型资源';
COMMENT ON COLUMN resource_api_mapping.service_code IS '所属服务编码，如 gateway routeId 或业务服务标识';
COMMENT ON COLUMN resource_api_mapping.http_method IS 'HTTP 方法，如 GET/POST/PUT/DELETE';
COMMENT ON COLUMN resource_api_mapping.path_pattern IS '接口路径模式，如 /api/users/**';
COMMENT ON COLUMN resource_api_mapping.match_order IS '匹配优先级，数值越小越优先';
COMMENT ON COLUMN resource_api_mapping.enabled IS '是否启用';
COMMENT ON COLUMN resource_api_mapping.extra IS '扩展属性(JSON)';
COMMENT ON COLUMN resource_api_mapping.created_by IS '创建人ID';
COMMENT ON COLUMN resource_api_mapping.updated_by IS '更新人ID';
COMMENT ON COLUMN resource_api_mapping.deleted_by IS '删除人ID';
COMMENT ON COLUMN resource_api_mapping.created_at IS '创建时间';
COMMENT ON COLUMN resource_api_mapping.updated_at IS '更新时间';
COMMENT ON COLUMN resource_api_mapping.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN resource_api_mapping.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 7. 权限生效条件表（预设 + 自定义审核模式）
-- -----------------------------------------------------------------------------
CREATE TABLE permission_condition (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    code             VARCHAR(64) NOT NULL,
    name             VARCHAR(128) NOT NULL,
    condition_source VARCHAR(16) NOT NULL DEFAULT 'PRESET',
    expression       TEXT NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    enabled          BOOLEAN NOT NULL DEFAULT true,
    description      VARCHAR(512),
    reviewed_by      BIGINT,
    reviewed_at      TIMESTAMPTZ,
    created_by       BIGINT,
    updated_by       BIGINT,
    deleted_by       BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    delete_flag      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_permission_condition ON permission_condition (tenant_id, code) WHERE delete_flag = 0;

COMMENT ON TABLE permission_condition IS '权限生效条件；condition_source=PRESET 为系统预设（始终 APPROVED），CUSTOM 为自定义（需审核），enabled 为独立启停开关';
COMMENT ON COLUMN permission_condition.id IS '主键';
COMMENT ON COLUMN permission_condition.tenant_id IS '租户ID';
COMMENT ON COLUMN permission_condition.code IS '条件编码';
COMMENT ON COLUMN permission_condition.name IS '名称';
COMMENT ON COLUMN permission_condition.condition_source IS '条件来源：PRESET=系统预设 / CUSTOM=自定义';
COMMENT ON COLUMN permission_condition.expression IS '条件表达式（PRESET 为 handler 编码，CUSTOM 为表达式文本）';
COMMENT ON COLUMN permission_condition.status IS '审核状态：APPROVED=已通过 / PENDING=待审核 / REJECTED=已拒绝';
COMMENT ON COLUMN permission_condition.enabled IS '启停开关：true=启用 / false=停用';
COMMENT ON COLUMN permission_condition.description IS '说明/变量约定';
COMMENT ON COLUMN permission_condition.reviewed_by IS '审核人ID';
COMMENT ON COLUMN permission_condition.reviewed_at IS '审核时间';
COMMENT ON COLUMN permission_condition.created_by IS '创建人ID';
COMMENT ON COLUMN permission_condition.updated_by IS '更新人ID';
COMMENT ON COLUMN permission_condition.deleted_by IS '删除人ID';
COMMENT ON COLUMN permission_condition.created_at IS '创建时间';
COMMENT ON COLUMN permission_condition.updated_at IS '更新时间';
COMMENT ON COLUMN permission_condition.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN permission_condition.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 8. 用户-角色关联表
-- -----------------------------------------------------------------------------
CREATE TABLE user_role (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    abstract_user_id BIGINT NOT NULL,
    abstract_role_id BIGINT NOT NULL,
    valid_from       TIMESTAMPTZ,
    valid_to         TIMESTAMPTZ,
    created_by       BIGINT,
    updated_by       BIGINT,
    deleted_by       BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    delete_flag      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_user_role ON user_role (tenant_id, abstract_user_id, abstract_role_id) WHERE delete_flag = 0;
CREATE INDEX idx_user_role_user ON user_role (abstract_user_id) WHERE delete_flag = 0;
CREATE INDEX idx_user_role_role ON user_role (abstract_role_id) WHERE delete_flag = 0;

COMMENT ON TABLE user_role IS '用户-角色多对多，valid_from/valid_to 为生效时间范围';
COMMENT ON COLUMN user_role.id IS '主键';
COMMENT ON COLUMN user_role.tenant_id IS '租户ID';
COMMENT ON COLUMN user_role.abstract_user_id IS '抽象用户ID';
COMMENT ON COLUMN user_role.abstract_role_id IS '抽象角色ID';
COMMENT ON COLUMN user_role.valid_from IS '生效开始时间，NULL 不限制';
COMMENT ON COLUMN user_role.valid_to IS '生效结束时间，NULL 不限制';
COMMENT ON COLUMN user_role.created_by IS '创建人ID';
COMMENT ON COLUMN user_role.updated_by IS '更新人ID';
COMMENT ON COLUMN user_role.deleted_by IS '删除人ID';
COMMENT ON COLUMN user_role.created_at IS '创建时间';
COMMENT ON COLUMN user_role.updated_at IS '更新时间';
COMMENT ON COLUMN user_role.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN user_role.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 9. 角色-资源-操作中间表
-- -----------------------------------------------------------------------------
CREATE TABLE role_resource_permission (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               BIGINT NOT NULL,
    abstract_role_id        BIGINT NOT NULL,
    resource_entity_id      BIGINT NOT NULL,
    operation_permission_id BIGINT NOT NULL,
    can_manage              BOOLEAN NOT NULL DEFAULT false,
    condition_id            BIGINT,
    created_by              BIGINT,
    updated_by              BIGINT,
    deleted_by              BIGINT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    delete_flag             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_role_resource_permission ON role_resource_permission (tenant_id, abstract_role_id, resource_entity_id, operation_permission_id) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_role ON role_resource_permission (abstract_role_id) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_resource ON role_resource_permission (resource_entity_id) WHERE delete_flag = 0;

COMMENT ON TABLE role_resource_permission IS '角色对某资源某操作的授权；condition_id 为 NULL 表示始终生效';
COMMENT ON COLUMN role_resource_permission.id IS '主键';
COMMENT ON COLUMN role_resource_permission.tenant_id IS '租户ID';
COMMENT ON COLUMN role_resource_permission.abstract_role_id IS '抽象角色ID';
COMMENT ON COLUMN role_resource_permission.resource_entity_id IS '资源实体ID';
COMMENT ON COLUMN role_resource_permission.operation_permission_id IS '操作权限ID';
COMMENT ON COLUMN role_resource_permission.can_manage IS '是否可管理(给他人授权)';
COMMENT ON COLUMN role_resource_permission.condition_id IS '生效条件ID（引用 permission_condition），NULL 表示始终生效';
COMMENT ON COLUMN role_resource_permission.created_by IS '创建人ID';
COMMENT ON COLUMN role_resource_permission.updated_by IS '更新人ID';
COMMENT ON COLUMN role_resource_permission.deleted_by IS '删除人ID';
COMMENT ON COLUMN role_resource_permission.created_at IS '创建时间';
COMMENT ON COLUMN role_resource_permission.updated_at IS '更新时间';
COMMENT ON COLUMN role_resource_permission.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN role_resource_permission.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 10. 域范围配置
-- -----------------------------------------------------------------------------
CREATE TABLE domain_scope_config (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT NOT NULL,
    scope_type    VARCHAR(32) NOT NULL,
    scope_ref_id  BIGINT NOT NULL,
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_domain_scope_config ON domain_scope_config (tenant_id, biz_domain_id, scope_type, scope_ref_id) WHERE delete_flag = 0;

COMMENT ON TABLE domain_scope_config IS '域下允许的角色类型/资源类型/操作：scope_type=ROLE_TYPE|RESOURCE_TYPE|OPERATION';
COMMENT ON COLUMN domain_scope_config.id IS '主键';
COMMENT ON COLUMN domain_scope_config.tenant_id IS '租户ID';
COMMENT ON COLUMN domain_scope_config.biz_domain_id IS '业务域ID';
COMMENT ON COLUMN domain_scope_config.scope_type IS '范围类型：ROLE_TYPE/RESOURCE_TYPE/OPERATION';
COMMENT ON COLUMN domain_scope_config.scope_ref_id IS '引用值：类型时为 type_value，操作时为 operation_permission.id';
COMMENT ON COLUMN domain_scope_config.created_by IS '创建人ID';
COMMENT ON COLUMN domain_scope_config.updated_by IS '更新人ID';
COMMENT ON COLUMN domain_scope_config.deleted_by IS '删除人ID';
COMMENT ON COLUMN domain_scope_config.created_at IS '创建时间';
COMMENT ON COLUMN domain_scope_config.updated_at IS '更新时间';
COMMENT ON COLUMN domain_scope_config.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN domain_scope_config.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 11. 域关系配置（仅 ROLE_RESOURCE：哪种角色类型可关联哪种资源类型）
--     资源类型与操作的绑定已移至 operation_permission.resource_type
-- -----------------------------------------------------------------------------
CREATE TABLE domain_relation_config (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    left_ref_id   BIGINT NOT NULL,
    right_ref_id  BIGINT NOT NULL,
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_domain_relation_config ON domain_relation_config (tenant_id, biz_domain_id, relation_type, left_ref_id, right_ref_id) WHERE delete_flag = 0;

COMMENT ON TABLE domain_relation_config IS '域内可关联关系：ROLE_RESOURCE（角色类型-资源类型）';
COMMENT ON COLUMN domain_relation_config.id IS '主键';
COMMENT ON COLUMN domain_relation_config.tenant_id IS '租户ID';
COMMENT ON COLUMN domain_relation_config.biz_domain_id IS '业务域ID';
COMMENT ON COLUMN domain_relation_config.relation_type IS '关系类型：当前仅 ROLE_RESOURCE';
COMMENT ON COLUMN domain_relation_config.left_ref_id IS '左侧引用：ROLE_RESOURCE 时为 role_type 枚举值';
COMMENT ON COLUMN domain_relation_config.right_ref_id IS '右侧引用：ROLE_RESOURCE 时为 resource_type 枚举值';
COMMENT ON COLUMN domain_relation_config.created_by IS '创建人ID';
COMMENT ON COLUMN domain_relation_config.updated_by IS '更新人ID';
COMMENT ON COLUMN domain_relation_config.deleted_by IS '删除人ID';
COMMENT ON COLUMN domain_relation_config.created_at IS '创建时间';
COMMENT ON COLUMN domain_relation_config.updated_at IS '更新时间';
COMMENT ON COLUMN domain_relation_config.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN domain_relation_config.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 12. 域引用绑定表（全局角色/资源/操作绑定到域）
-- -----------------------------------------------------------------------------
CREATE TABLE domain_scope_binding (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    biz_domain_id   BIGINT NOT NULL,
    bound_type      VARCHAR(32) NOT NULL,
    bound_entity_id BIGINT NOT NULL,
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    delete_flag     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_domain_scope_binding ON domain_scope_binding (tenant_id, biz_domain_id, bound_type, bound_entity_id) WHERE delete_flag = 0;
CREATE INDEX idx_domain_scope_binding_domain ON domain_scope_binding (tenant_id, biz_domain_id) WHERE delete_flag = 0;

COMMENT ON TABLE domain_scope_binding IS '将全局角色/资源/操作绑定到业务域，实现一份配置多域生效';
COMMENT ON COLUMN domain_scope_binding.id IS '主键';
COMMENT ON COLUMN domain_scope_binding.tenant_id IS '租户ID';
COMMENT ON COLUMN domain_scope_binding.biz_domain_id IS '被绑定的业务域ID';
COMMENT ON COLUMN domain_scope_binding.bound_type IS '绑定类型：ROLE/RESOURCE/OPERATION';
COMMENT ON COLUMN domain_scope_binding.bound_entity_id IS '被绑定实体ID（对应表主键，须为全局即 biz_domain_id 为 NULL 的实体）';
COMMENT ON COLUMN domain_scope_binding.created_by IS '创建人ID';
COMMENT ON COLUMN domain_scope_binding.updated_by IS '更新人ID';
COMMENT ON COLUMN domain_scope_binding.deleted_by IS '删除人ID';
COMMENT ON COLUMN domain_scope_binding.created_at IS '创建时间';
COMMENT ON COLUMN domain_scope_binding.updated_at IS '更新时间';
COMMENT ON COLUMN domain_scope_binding.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN domain_scope_binding.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 13. 资源依赖表（声明式元数据，由资源注册方自动维护，写入时校验防环）
-- -----------------------------------------------------------------------------
CREATE TABLE resource_dependency (
    id                               BIGSERIAL PRIMARY KEY,
    tenant_id                        BIGINT NOT NULL,
    resource_entity_id               BIGINT NOT NULL,
    depends_on_resource_entity_id    BIGINT NOT NULL,
    source_operation_permission_id   BIGINT,
    required_operation_permission_id BIGINT NOT NULL,
    created_by                       BIGINT,
    updated_by                       BIGINT,
    deleted_by                       BIGINT,
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                       TIMESTAMPTZ,
    delete_flag                      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_resource_dependency ON resource_dependency (tenant_id, resource_entity_id, depends_on_resource_entity_id, COALESCE(source_operation_permission_id, 0)) WHERE delete_flag = 0;
CREATE INDEX idx_resource_dependency_resource ON resource_dependency (resource_entity_id) WHERE delete_flag = 0;

COMMENT ON TABLE resource_dependency IS '资源依赖：声明式元数据，由资源注册方（业务系统）自动维护；写入时校验防环';
COMMENT ON COLUMN resource_dependency.id IS '主键';
COMMENT ON COLUMN resource_dependency.tenant_id IS '租户ID';
COMMENT ON COLUMN resource_dependency.resource_entity_id IS '主体资源ID（被授权方，如报表/菜单）';
COMMENT ON COLUMN resource_dependency.depends_on_resource_entity_id IS '依赖资源ID（需同时具备权限，如数据源/数据集）';
COMMENT ON COLUMN resource_dependency.source_operation_permission_id IS '仅当对主体资源做该操作时应用本依赖，NULL 表示任意操作都需满足';
COMMENT ON COLUMN resource_dependency.required_operation_permission_id IS '对依赖资源所需的操作ID';
COMMENT ON COLUMN resource_dependency.created_by IS '创建人ID';
COMMENT ON COLUMN resource_dependency.updated_by IS '更新人ID';
COMMENT ON COLUMN resource_dependency.deleted_by IS '删除人ID';
COMMENT ON COLUMN resource_dependency.created_at IS '创建时间';
COMMENT ON COLUMN resource_dependency.updated_at IS '更新时间';
COMMENT ON COLUMN resource_dependency.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN resource_dependency.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 14. 权限冲突规则表（查询时检测：检测到冲突则相关权限失效，异步通知管理员修正）
-- -----------------------------------------------------------------------------
CREATE TABLE permission_conflict_rule (
    id                             BIGSERIAL PRIMARY KEY,
    tenant_id                      BIGINT NOT NULL,
    biz_domain_id                  BIGINT,
    first_operation_permission_id  BIGINT NOT NULL,
    second_operation_permission_id BIGINT NOT NULL,
    resource_type_value            INT,
    created_by                     BIGINT,
    updated_by                     BIGINT,
    deleted_by                     BIGINT,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                     TIMESTAMPTZ,
    delete_flag                    BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_permission_conflict_rule_domain ON permission_conflict_rule (tenant_id, biz_domain_id, first_operation_permission_id, second_operation_permission_id) WHERE biz_domain_id IS NOT NULL AND delete_flag = 0;
CREATE UNIQUE INDEX uk_permission_conflict_rule_global ON permission_conflict_rule (tenant_id, first_operation_permission_id, second_operation_permission_id) WHERE biz_domain_id IS NULL AND delete_flag = 0;

COMMENT ON TABLE permission_conflict_rule IS '同一用户对同一资源不能同时拥有 first 与 second 操作；查询时检测失效 + 异步通知修正；存库时 first_id < second_id';
COMMENT ON COLUMN permission_conflict_rule.id IS '主键';
COMMENT ON COLUMN permission_conflict_rule.tenant_id IS '租户ID';
COMMENT ON COLUMN permission_conflict_rule.biz_domain_id IS '业务域ID，NULL 表示全局规则';
COMMENT ON COLUMN permission_conflict_rule.first_operation_permission_id IS '互斥操作一';
COMMENT ON COLUMN permission_conflict_rule.second_operation_permission_id IS '互斥操作二（存库时 first_id < second_id）';
COMMENT ON COLUMN permission_conflict_rule.resource_type_value IS '仅当资源类型为该枚举值时生效，NULL 表示所有资源类型';
COMMENT ON COLUMN permission_conflict_rule.created_by IS '创建人ID';
COMMENT ON COLUMN permission_conflict_rule.updated_by IS '更新人ID';
COMMENT ON COLUMN permission_conflict_rule.deleted_by IS '删除人ID';
COMMENT ON COLUMN permission_conflict_rule.created_at IS '创建时间';
COMMENT ON COLUMN permission_conflict_rule.updated_at IS '更新时间';
COMMENT ON COLUMN permission_conflict_rule.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN permission_conflict_rule.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 15. 权限版本表
-- -----------------------------------------------------------------------------
CREATE TABLE permission_version (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    version_no          BIGINT NOT NULL,
    trigger_entity_type VARCHAR(64),
    trigger_entity_id   BIGINT,
    remark              VARCHAR(512),
    created_by          BIGINT,
    updated_by          BIGINT,
    deleted_by          BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    delete_flag         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_permission_version_tenant_version ON permission_version (tenant_id, version_no) WHERE delete_flag = 0;
CREATE INDEX idx_permission_version_tenant_latest ON permission_version (tenant_id, created_at DESC) WHERE delete_flag = 0;

COMMENT ON TABLE permission_version IS '权限版本游标：权限发生变化时递增，供 identity-service 与 gateway 刷新运行时快照';
COMMENT ON COLUMN permission_version.id IS '主键';
COMMENT ON COLUMN permission_version.tenant_id IS '租户ID';
COMMENT ON COLUMN permission_version.version_no IS '权限版本号，按租户递增';
COMMENT ON COLUMN permission_version.trigger_entity_type IS '触发版本变更的实体类型，如 user_role/role_resource_permission';
COMMENT ON COLUMN permission_version.trigger_entity_id IS '触发版本变更的实体ID';
COMMENT ON COLUMN permission_version.remark IS '版本变更说明';
COMMENT ON COLUMN permission_version.created_by IS '创建人ID';
COMMENT ON COLUMN permission_version.updated_by IS '更新人ID';
COMMENT ON COLUMN permission_version.deleted_by IS '删除人ID';
COMMENT ON COLUMN permission_version.created_at IS '创建时间';
COMMENT ON COLUMN permission_version.updated_at IS '更新时间';
COMMENT ON COLUMN permission_version.deleted_at IS '软删时间（审计用）';
COMMENT ON COLUMN permission_version.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 16. 变更记录表
-- -----------------------------------------------------------------------------
CREATE TABLE permission_change_log (
    id                         BIGSERIAL PRIMARY KEY,
    tenant_id                  BIGINT NOT NULL,
    biz_domain_id              BIGINT,
    entity_type                VARCHAR(64) NOT NULL,
    entity_id                  BIGINT,
    operation                  VARCHAR(16) NOT NULL,
    old_snapshot               JSONB,
    new_snapshot               JSONB,
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
CREATE INDEX idx_change_log_tenant_domain_time ON permission_change_log (tenant_id, biz_domain_id, created_at DESC);
CREATE INDEX idx_change_log_entity ON permission_change_log (tenant_id, entity_type, entity_id);

COMMENT ON TABLE permission_change_log IS '权限变更记录';
COMMENT ON COLUMN permission_change_log.id IS '主键';
COMMENT ON COLUMN permission_change_log.tenant_id IS '租户ID';
COMMENT ON COLUMN permission_change_log.biz_domain_id IS '业务域ID，NULL 表示与域无关';
COMMENT ON COLUMN permission_change_log.entity_type IS '变更实体类型：user_role/batch_user_role/role_resource_permission 等';
COMMENT ON COLUMN permission_change_log.entity_id IS '被变更记录的主键ID，批量时可0或批次ID';
COMMENT ON COLUMN permission_change_log.operation IS '操作：INSERT/UPDATE/DELETE';
COMMENT ON COLUMN permission_change_log.old_snapshot IS '变更前快照(JSON)';
COMMENT ON COLUMN permission_change_log.new_snapshot IS '变更后快照(JSON)';
COMMENT ON COLUMN permission_change_log.affected_abstract_user_ids IS '本条变更影响的用户ID数组';
COMMENT ON COLUMN permission_change_log.affected_abstract_role_ids IS '本条变更影响的角色ID数组';
COMMENT ON COLUMN permission_change_log.change_reason IS '变更原因说明';
COMMENT ON COLUMN permission_change_log.change_source IS '变更来源：ADMIN/MQ_SYNC/API/SYSTEM';
COMMENT ON COLUMN permission_change_log.request_id IS '请求/追踪ID';
COMMENT ON COLUMN permission_change_log.created_by IS '执行变更的操作人ID';
COMMENT ON COLUMN permission_change_log.created_at IS '变更时间';
