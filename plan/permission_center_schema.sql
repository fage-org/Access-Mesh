-- =============================================================================
-- 通用权限中心 - PostgreSQL 表结构（18 张表）
-- 无外键，逻辑关联由应用保证
-- 执行顺序按依赖关系，建议按序号依次执行
-- =============================================================================
-- 软删约定：
--   delete_flag BIGINT：0 = 未删除，删除时填本行 id（确保唯一约束不冲突）
--   deleted_at TIMESTAMPTZ：纯审计字段，记录删除时间，不参与索引条件
--   所有唯一索引和业务查询统一使用 WHERE delete_flag = 0
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 类型定义表（预置 + 租户可扩展，is_system 区分）
-- -----------------------------------------------------------------------------
CREATE TABLE type_definition (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT,
    type_key      VARCHAR(64) NOT NULL,
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

CREATE UNIQUE INDEX uk_type_definition_domain ON type_definition (tenant_id, biz_domain_id, type_key, type_value) WHERE biz_domain_id IS NOT NULL AND delete_flag = 0;
CREATE UNIQUE INDEX uk_type_definition_global ON type_definition (tenant_id, type_key, type_value) WHERE biz_domain_id IS NULL AND delete_flag = 0;

COMMENT ON TABLE type_definition IS '类型定义：type_key 如 user_type/role_type/resource_type/group_type，is_system=true 为系统预置不可删改。创建 resource_type 时自动预置 CRUD 四个 operation_permission';
COMMENT ON COLUMN type_definition.id IS '主键';
COMMENT ON COLUMN type_definition.tenant_id IS '租户ID';
COMMENT ON COLUMN type_definition.biz_domain_id IS '业务域ID，NULL 表示全局类型';
COMMENT ON COLUMN type_definition.type_key IS '类型键，如 user_type、role_type、resource_type、group_type';
COMMENT ON COLUMN type_definition.type_value IS '枚举值，如 1=人员 2=服务';
COMMENT ON COLUMN type_definition.name IS '显示名称';
COMMENT ON COLUMN type_definition.description IS '描述';
COMMENT ON COLUMN type_definition.is_system IS '是否系统预置：true=预置不可删改，false=租户自定义可扩展';
COMMENT ON COLUMN type_definition.sort_order IS '排序';
COMMENT ON COLUMN type_definition.extra IS '扩展配置(JSON)，如 {"max_depth": 5} 控制资源树深度';
COMMENT ON COLUMN type_definition.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 2. 业务域表（扁平列表，无启停，引用检查拒删）
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

COMMENT ON TABLE biz_domain IS '业务域，扁平列表，对权限对象分类，无启停，删除前检查引用';
COMMENT ON COLUMN biz_domain.code IS '域编码';
COMMENT ON COLUMN biz_domain.name IS '域名称';

-- -----------------------------------------------------------------------------
-- 3. 抽象用户表（不含 biz_domain_id，通过分组/角色关联域）
--    创建用户时自动创建个人角色 PERSONAL_{external_id}
-- -----------------------------------------------------------------------------
CREATE TABLE abstract_user (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    user_type   INT NOT NULL,
    external_id VARCHAR(256) NOT NULL,
    name        VARCHAR(256),
    enabled     BOOLEAN NOT NULL DEFAULT true,
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

COMMENT ON TABLE abstract_user IS '抽象用户，user_type 来自 type_definition。创建时自动创建个人角色 PERSONAL_{external_id}。支持外部系统 API 同步（幂等）';
COMMENT ON COLUMN abstract_user.user_type IS '用户类型枚举值：USER(1)/SERVICE(2)，来自 type_definition';
COMMENT ON COLUMN abstract_user.external_id IS '外部业务系统唯一标识';
COMMENT ON COLUMN abstract_user.name IS '显示名';
COMMENT ON COLUMN abstract_user.enabled IS '是否启用：false 时鉴权不通过';
COMMENT ON COLUMN abstract_user.extra IS '扩展属性(JSON)';
COMMENT ON COLUMN abstract_user.delete_flag IS '逻辑删除：0=未删除，删除时填本行id。删除级联：user_role + 个人角色的 role_resource_permission + 失效缓存';

-- 角色分组已合并至 abstract_role 树形结构，独立 role_group / role_group_role 表已移除
-- abstract_role 新增 parent_id 字段支持树形；role_type 新增 GROUP_ROLE 和 BASIC_ROLE
-- user_role 新增 relation_id 字段用于 POSITION 绑定组织等场景

-- -----------------------------------------------------------------------------
-- 4. 抽象角色表（树形结构，通过 parent_id 支持层级）
--    角色类型说明：
--      ORG(1) 组织：树形，同步自 sys_org
--      POSITION(2) 职位：平铺（不可有子级），分配给用户时 user_role.relation_id 记录所属组织
--      PERSONAL(3) 个人：平铺，每用户1个，独立
--      GROUP_ROLE(5) 分组角色：树形，不直接配置权限，通过 extra.basicRoleIds 额外关联基本角色
--      BASIC_ROLE(6) 基本角色：平铺（不可有子级），承载实际权限配置
--    角色名唯一性可配置（租户级 system_config）
--    个人角色 PERSONAL_{external_id} 不在管理界面展示，每用户最多1个
-- -----------------------------------------------------------------------------
CREATE TABLE abstract_role (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT,
    parent_id     BIGINT,
    role_type     INT NOT NULL,
    external_id   VARCHAR(256),
    name          VARCHAR(256) NOT NULL,
    status        INT NOT NULL DEFAULT 1,
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
CREATE INDEX idx_abstract_role_tenant_type ON abstract_role (tenant_id, role_type) WHERE delete_flag = 0;
CREATE INDEX idx_abstract_role_parent ON abstract_role (parent_id) WHERE delete_flag = 0;

COMMENT ON TABLE abstract_role IS '抽象角色，树形结构（parent_id）；GROUP_ROLE 和 BASIC_ROLE 通过 type_definition 区分。删除级联：user_role + role_resource_permission';
COMMENT ON COLUMN abstract_role.biz_domain_id IS '所属业务域ID，NULL 表示全局角色';
COMMENT ON COLUMN abstract_role.parent_id IS '父角色ID，用于树形层级；BASIC_ROLE 和 PERSONAL 不允许有子级（应用层约束）';
COMMENT ON COLUMN abstract_role.role_type IS '角色类型枚举：ORG(1)组织/POSITION(2)职位/PERSONAL(3)个人/GROUP_ROLE(5)分组角色/BASIC_ROLE(6)基本角色，来自 type_definition';
COMMENT ON COLUMN abstract_role.external_id IS '外部业务标识';
COMMENT ON COLUMN abstract_role.name IS '名称';
COMMENT ON COLUMN abstract_role.status IS '状态：0=停用 1=启用，预留扩展空间';
COMMENT ON COLUMN abstract_role.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- 分组-角色关联表已移除，分组角色通过 abstract_role.parent_id 和 extra.basicRoleIds 管理

-- -----------------------------------------------------------------------------
-- 5. 操作权限表（绑定资源类型，binary_bit + inherit_mask 用 BIGINT）
--    创建 resource_type 时自动预置 CRUD 四个操作：
--    CREATE(bit=1,mask=0) READ(bit=2,mask=0) UPDATE(bit=4,mask=2继承READ) DELETE(bit=8,mask=2继承READ)
--    每个 resource_type 最多 63 个操作（BIGINT 63 位）
--    操作无启停状态，用删除代替
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

COMMENT ON TABLE operation_permission IS '操作权限；effective = binary_bit | inherit_mask；预置CRUD：CREATE(1,0) READ(2,0) UPDATE(4,2) DELETE(8,2)';
COMMENT ON COLUMN operation_permission.resource_type IS '适用的资源类型枚举值，NULL 表示适用所有';
COMMENT ON COLUMN operation_permission.code IS '操作编码，如 CREATE、READ、UPDATE、DELETE';
COMMENT ON COLUMN operation_permission.binary_bit IS '本操作独占位（BIGINT 63 个独立操作）';
COMMENT ON COLUMN operation_permission.inherit_mask IS '继承的位掩码，实际权限=binary_bit|inherit_mask';

-- -----------------------------------------------------------------------------
-- 6. 权限资源实体表（树形，支持多编码类型 code_type）
--    数据权限也是一种资源实体（resource_type=DATA）
--    同一资源可有多行不同 code_type，默认 "default"
-- -----------------------------------------------------------------------------
CREATE TABLE resource_entity (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    biz_domain_id BIGINT,
    parent_id     BIGINT,
    resource_type INT NOT NULL,
    code          VARCHAR(128) NOT NULL,
    code_type     VARCHAR(64) NOT NULL DEFAULT 'default',
    name          VARCHAR(256) NOT NULL,
    path          VARCHAR(1024),
    status        INT NOT NULL DEFAULT 1,
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

-- 唯一约束包含 resource_type + code_type，biz_domain_id 可 NULL
CREATE UNIQUE INDEX uk_resource_entity_domain ON resource_entity (tenant_id, resource_type, biz_domain_id, code, code_type) WHERE biz_domain_id IS NOT NULL AND delete_flag = 0;
CREATE UNIQUE INDEX uk_resource_entity_global ON resource_entity (tenant_id, resource_type, code, code_type) WHERE biz_domain_id IS NULL AND delete_flag = 0;
CREATE INDEX idx_resource_entity_tenant_domain ON resource_entity (tenant_id, biz_domain_id) WHERE delete_flag = 0;
CREATE INDEX idx_resource_entity_parent ON resource_entity (parent_id) WHERE delete_flag = 0;
CREATE INDEX idx_resource_entity_type ON resource_entity (tenant_id, resource_type) WHERE delete_flag = 0;

COMMENT ON TABLE resource_entity IS '权限资源实体，树形；同一资源可有多行不同 code_type 用于编码转换（如 "default"="100", "en"="Britain", "cn"="英国"）';
COMMENT ON COLUMN resource_entity.biz_domain_id IS '所属业务域ID，NULL 表示全局资源';
COMMENT ON COLUMN resource_entity.parent_id IS '父节点ID';
COMMENT ON COLUMN resource_entity.resource_type IS '资源类型枚举：MENU(1)/BUTTON(2)/API(3)/DATA(4)，来自 type_definition';
COMMENT ON COLUMN resource_entity.code IS '资源编码';
COMMENT ON COLUMN resource_entity.code_type IS '编码类型，默认 "default"；同一资源不同编码体系用不同 code_type 区分';
COMMENT ON COLUMN resource_entity.name IS '名称';
COMMENT ON COLUMN resource_entity.path IS '树路径（物化路径）';
COMMENT ON COLUMN resource_entity.status IS '状态：0=停用 1=启用';
COMMENT ON COLUMN resource_entity.extra IS '扩展属性(JSON)，如菜单图标/路由等';

-- -----------------------------------------------------------------------------
-- 7. 接口资源映射表
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

COMMENT ON TABLE resource_api_mapping IS '接口资源映射：API 类资源到 service_code + http_method + path_pattern 的显式映射';
COMMENT ON COLUMN resource_api_mapping.service_code IS '所属服务编码';
COMMENT ON COLUMN resource_api_mapping.http_method IS 'HTTP 方法，如 GET/POST/PUT/DELETE';
COMMENT ON COLUMN resource_api_mapping.path_pattern IS '接口路径模式（完整路径含前缀）';

-- -----------------------------------------------------------------------------
-- 8. 接入服务配置表（全量同步，支持手动增删改接口映射）
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

COMMENT ON TABLE service_config IS '接入服务配置：全量同步策略，支持手动增删改接口映射。停用(status=0)后其接口不参与授权';
COMMENT ON COLUMN service_config.service_code IS '服务编码，租户内唯一';
COMMENT ON COLUMN service_config.base_path IS '基础路径前缀';
COMMENT ON COLUMN service_config.status IS '状态：0=停用 1=启用。停用后该服务的接口不参与授权';

-- -----------------------------------------------------------------------------
-- 9. 权限条件表（JSONB 规则字段，一行=一个完整条件定义）
--     条件独立实体，多个 role_resource_permission 可引用同一 condition_id 复用
--     预置条件类型：DATE_RANGE, TIME_RANGE, IP_WHITELIST, IP_BLACKLIST
-- -----------------------------------------------------------------------------
CREATE TABLE permission_condition (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    condition_rules JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT true,
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

-- -----------------------------------------------------------------------------
-- 9. 用户关联表（统一关联角色，target_type 标记角色类型）
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

COMMENT ON TABLE user_role IS '用户关联表：target=abstract_role.id；POSITION 类型时 relation_id 记录所属组织，决定数据权限范围';
COMMENT ON COLUMN user_role.target_type IS '关联角色类型：ROLE=常规角色/ORG=组织/POSITION=职位/PERSONAL=个人/GROUP_ROLE=分组角色，与 abstract_role.role_type 对应';
COMMENT ON COLUMN user_role.target_id IS '关联角色ID（abstract_role.id）';
COMMENT ON COLUMN user_role.relation_id IS '关联ID，POSITION 类型时记录所属组织 ID（决定数据权限范围），其他类型时为 NULL';
COMMENT ON COLUMN user_role.valid_from IS '生效开始时间，NULL 不限制';
COMMENT ON COLUMN user_role.valid_to IS '生效结束时间，NULL 不限制';

-- -----------------------------------------------------------------------------
-- 11. 角色-资源-操作中间表（支持子权限 depend_on，冗余 resource_type）
--     批量授权接口格式 {add:[], update:[], delete:[]}
--     只存勾选节点，查询接口支持展开父级/展开子级
--     scope_all=true 表示该操作覆盖 resource_type 下全部范围资源，此时 resource_entity_id 为空
-- -----------------------------------------------------------------------------
CREATE TABLE role_resource_permission (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               BIGINT NOT NULL,
    abstract_role_id        BIGINT NOT NULL,
    resource_entity_id      BIGINT,
    operation_permission_id BIGINT NOT NULL,
    resource_type           INT NOT NULL,
    depend_on               BIGINT,
    scope_all               BOOLEAN NOT NULL DEFAULT false,
    can_manage              BOOLEAN NOT NULL DEFAULT false,
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
    )
);

CREATE UNIQUE INDEX uk_role_resource_permission ON role_resource_permission (tenant_id, abstract_role_id, COALESCE(resource_entity_id, 0), resource_type, operation_permission_id, COALESCE(depend_on, 0), scope_all, COALESCE(grant_source, 'MANUAL'), COALESCE(condition_id, 0)) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_role ON role_resource_permission (abstract_role_id) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_resource ON role_resource_permission (resource_entity_id) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_depend ON role_resource_permission (depend_on) WHERE delete_flag = 0 AND depend_on IS NOT NULL;
CREATE INDEX idx_role_resource_permission_type ON role_resource_permission (tenant_id, resource_type) WHERE delete_flag = 0;
CREATE INDEX idx_role_resource_permission_scope_all ON role_resource_permission (tenant_id, resource_type, operation_permission_id) WHERE delete_flag = 0 AND scope_all = true;

COMMENT ON TABLE role_resource_permission IS '角色对某资源某操作的授权；depend_on 实现子权限（单层）；scope_all=true 表示某资源类型全量范围授权';
COMMENT ON COLUMN role_resource_permission.resource_entity_id IS '资源实体ID；scope_all=false 时必填，scope_all=true 时为空';
COMMENT ON COLUMN role_resource_permission.resource_type IS '资源类型；普通授权时从 resource_entity 自动填充，scope_all=true 时用于标识全量范围资源类型';
COMMENT ON COLUMN role_resource_permission.depend_on IS '父权限ID（本表自引用），NULL=主权限，非NULL=子权限。单层依赖。删除父权限时级联软删子权限';
COMMENT ON COLUMN role_resource_permission.scope_all IS '是否覆盖该 resource_type 下全部范围资源；true 时 resource_entity_id 必须为空';
COMMENT ON COLUMN role_resource_permission.can_manage IS '是否可管理(给他人授权)';
COMMENT ON COLUMN role_resource_permission.condition_id IS '生效条件ID（引用 permission_condition），NULL 表示始终生效';
COMMENT ON COLUMN role_resource_permission.grant_source IS '授权来源：MANUAL=手动授权，AUTO_DEP=resource_dependency 自动补全';
COMMENT ON COLUMN role_resource_permission.grant_dep_id IS '依赖规则ID（grant_source=AUTO_DEP 时记录触发的 resource_dependency.id）';

-- -----------------------------------------------------------------------------
-- 12. 域配置表（三合一 + 子权限配置：SCOPE / RELATION / BINDING / SUB_PERM）
--     每个域独立配置，无继承，变更即时生效（缓存失效）
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

CREATE INDEX idx_domain_config_domain ON domain_config (tenant_id, biz_domain_id, config_type) WHERE delete_flag = 0;

COMMENT ON TABLE domain_config IS '域配置：SCOPE=域范围 / RELATION=域关系 / BINDING=域绑定 / SUB_PERM=子权限配置。每个域独立，无继承';
COMMENT ON COLUMN domain_config.config_type IS 'SCOPE / RELATION / BINDING / SUB_PERM';
COMMENT ON COLUMN domain_config.extra IS 'SUB_PERM示例: {"allowed":[{"parent_type":"MENU","child_types":["BUTTON","DATA"]}]}';

-- -----------------------------------------------------------------------------
-- 13. 资源依赖表（操作位级别触发，支持自动补全）
--     由外部系统通过接口维护，权限中心负责存储和查询
-- -----------------------------------------------------------------------------
CREATE TABLE resource_dependency (
    id                            BIGSERIAL PRIMARY KEY,
    tenant_id                     BIGINT NOT NULL,
    resource_entity_id            BIGINT NOT NULL,
    depends_on_resource_entity_id BIGINT NOT NULL,
    source_operation_bits         BIGINT,
    required_operation_bits       BIGINT NOT NULL,
    auto_grant                    BOOLEAN NOT NULL DEFAULT true,
    description                   VARCHAR(512),
    created_by                    BIGINT,
    updated_by                    BIGINT,
    deleted_by                    BIGINT,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                    TIMESTAMPTZ,
    delete_flag                   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_resource_dependency ON resource_dependency (tenant_id, resource_entity_id, depends_on_resource_entity_id) WHERE delete_flag = 0;
CREATE INDEX idx_resource_dependency_resource ON resource_dependency (resource_entity_id) WHERE delete_flag = 0;

COMMENT ON TABLE resource_dependency IS '资源依赖：source_operation_bits 为触发条件（源资源授权含这些bit时触发），required_operation_bits 为依赖资源需要的操作位。auto_grant=true 时授权时自动补全';
COMMENT ON COLUMN resource_dependency.resource_entity_id IS '源资源ID（被授权的）';
COMMENT ON COLUMN resource_dependency.depends_on_resource_entity_id IS '依赖资源ID（需自动补全的）';
COMMENT ON COLUMN resource_dependency.source_operation_bits IS '触发条件：源资源授权含这些bit时才触发依赖，NULL=任意操作都触发';
COMMENT ON COLUMN resource_dependency.required_operation_bits IS '依赖资源需要的操作位';
COMMENT ON COLUMN resource_dependency.auto_grant IS '授权源资源时是否自动授予依赖资源权限';

-- -----------------------------------------------------------------------------
-- 14. 权限冲突规则表（角色互斥 + 权限互斥）
--     角色互斥：写入时检查，违反直接拒绝
--     权限互斥：查询时检查，冲突权限失效 + 异步通知
--     性能方案：查询时实时计算 + TTL 缓存（版本变更失效）
-- -----------------------------------------------------------------------------
CREATE TABLE permission_conflict_rule (
    id                             BIGSERIAL PRIMARY KEY,
    tenant_id                      BIGINT NOT NULL,
    biz_domain_id                  BIGINT,
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

-- 权限互斥唯一约束
CREATE UNIQUE INDEX uk_conflict_rule_perm_domain ON permission_conflict_rule (tenant_id, biz_domain_id, first_operation_permission_id, second_operation_permission_id) WHERE biz_domain_id IS NOT NULL AND conflict_type = 'PERM_MUTEX' AND delete_flag = 0;
CREATE UNIQUE INDEX uk_conflict_rule_perm_global ON permission_conflict_rule (tenant_id, first_operation_permission_id, second_operation_permission_id) WHERE biz_domain_id IS NULL AND conflict_type = 'PERM_MUTEX' AND delete_flag = 0;
-- 角色互斥唯一约束
CREATE UNIQUE INDEX uk_conflict_rule_role_domain ON permission_conflict_rule (tenant_id, biz_domain_id, first_abstract_role_id, second_abstract_role_id) WHERE biz_domain_id IS NOT NULL AND conflict_type = 'ROLE_MUTEX' AND delete_flag = 0;
CREATE UNIQUE INDEX uk_conflict_rule_role_global ON permission_conflict_rule (tenant_id, first_abstract_role_id, second_abstract_role_id) WHERE biz_domain_id IS NULL AND conflict_type = 'ROLE_MUTEX' AND delete_flag = 0;

COMMENT ON TABLE permission_conflict_rule IS '冲突规则：ROLE_MUTEX=角色互斥(写入检查拒绝) / PERM_MUTEX=权限互斥(查询时失效+异步通知)。存库时 first_id < second_id';
COMMENT ON COLUMN permission_conflict_rule.conflict_type IS 'ROLE_MUTEX=角色互斥 / PERM_MUTEX=权限互斥';
COMMENT ON COLUMN permission_conflict_rule.first_operation_permission_id IS '互斥操作一（PERM_MUTEX 时使用）';
COMMENT ON COLUMN permission_conflict_rule.second_operation_permission_id IS '互斥操作二（PERM_MUTEX 时使用）';
COMMENT ON COLUMN permission_conflict_rule.resource_type_value IS '资源类型枚举值（PERM_MUTEX 时使用），NULL=所有';
COMMENT ON COLUMN permission_conflict_rule.first_abstract_role_id IS '互斥角色一（ROLE_MUTEX 时使用）';
COMMENT ON COLUMN permission_conflict_rule.second_abstract_role_id IS '互斥角色二（ROLE_MUTEX 时使用）';

-- -----------------------------------------------------------------------------
-- 15. 权限版本表（角色级粒度，每个角色单独版本号）
--     权限变更时自动递增，仅用于缓存失效，不存快照
-- -----------------------------------------------------------------------------
CREATE TABLE permission_version (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    abstract_role_id    BIGINT,
    version_no          BIGINT NOT NULL,
    trigger_entity_type VARCHAR(64),
    trigger_entity_id   BIGINT,
    remark              VARCHAR(512),
    created_by          BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_permission_version ON permission_version (tenant_id, COALESCE(abstract_role_id, 0), version_no);
CREATE INDEX idx_permission_version_role ON permission_version (tenant_id, abstract_role_id, created_at DESC);

COMMENT ON TABLE permission_version IS '权限版本游标：角色级粒度，权限变更时自动递增，仅用于缓存失效，不存快照';
COMMENT ON COLUMN permission_version.abstract_role_id IS '角色ID，NULL 表示租户全局版本';
COMMENT ON COLUMN permission_version.version_no IS '版本号，按角色递增';
COMMENT ON COLUMN permission_version.trigger_entity_type IS '触发变更的实体类型';
COMMENT ON COLUMN permission_version.trigger_entity_id IS '触发变更的实体ID';

-- -----------------------------------------------------------------------------
-- 16. 权限变更记录表（详细权限变更 diff，方便排查权限问题）
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
CREATE INDEX idx_change_log_tenant_domain_time ON permission_change_log (tenant_id, biz_domain_id, created_at DESC);
CREATE INDEX idx_change_log_entity ON permission_change_log (tenant_id, entity_type, entity_id);
CREATE INDEX idx_change_log_request_id ON permission_change_log (request_id) WHERE request_id IS NOT NULL;

COMMENT ON TABLE permission_change_log IS '权限变更记录：详细记录权限相关变更的 before/after/diff，方便排查用户因配置问题导致权限失效';
COMMENT ON COLUMN permission_change_log.entity_type IS '变更实体类型：user_role/role_resource_permission/abstract_user/abstract_role 等';
COMMENT ON COLUMN permission_change_log.operation IS '操作：INSERT/UPDATE/DELETE';
COMMENT ON COLUMN permission_change_log.old_snapshot IS '变更前快照(JSON)';
COMMENT ON COLUMN permission_change_log.new_snapshot IS '变更后快照(JSON)';
COMMENT ON COLUMN permission_change_log.diff_snapshot IS '变更差异(JSON)，前后快照对比';
COMMENT ON COLUMN permission_change_log.change_source IS '变更来源：ADMIN/SYNC/API/SYSTEM';
COMMENT ON COLUMN permission_change_log.request_id IS '请求/追踪ID(trace_id)，同一次操作的多条记录通过此关联';

-- -----------------------------------------------------------------------------
-- 17. 系统配置表（租户级配置，如角色名唯一性等）
-- -----------------------------------------------------------------------------
CREATE TABLE system_config (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    config_key   VARCHAR(128) NOT NULL,
    config_value JSONB NOT NULL DEFAULT '{}',
    description  VARCHAR(512),
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    delete_flag  BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_system_config ON system_config (tenant_id, config_key) WHERE delete_flag = 0;

COMMENT ON TABLE system_config IS '系统配置：租户级配置项，如角色名唯一性策略、未注册接口默认策略等';
COMMENT ON COLUMN system_config.config_key IS '配置键，如 ROLE_NAME_UNIQUE_MODE / UNREGISTERED_API_POLICY';
COMMENT ON COLUMN system_config.config_value IS '配置值(JSON)，如 {"mode":"DOMAIN_UNIQUE"} 或 {"mode":"NO_RESTRICT"}';

-- -----------------------------------------------------------------------------
-- 18. 操作日志表（轻量全量记录所有写操作）
-- -----------------------------------------------------------------------------
CREATE TABLE operation_log (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    module         VARCHAR(64) NOT NULL,
    action         VARCHAR(64) NOT NULL,
    target_type    VARCHAR(64),
    target_id      BIGINT,
    summary        VARCHAR(512),
    operator_id    BIGINT,
    operator_name  VARCHAR(256),
    ip_address     VARCHAR(64),
    request_id     VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_operation_log_tenant_time ON operation_log (tenant_id, created_at DESC);
CREATE INDEX idx_operation_log_operator ON operation_log (tenant_id, operator_id, created_at DESC);
CREATE INDEX idx_operation_log_target ON operation_log (tenant_id, target_type, target_id);
CREATE INDEX idx_operation_log_request ON operation_log (request_id) WHERE request_id IS NOT NULL;

COMMENT ON TABLE operation_log IS '操作日志：轻量全量记录所有写操作，简单记录。与 permission_change_log 区分：本表记所有操作，permission_change_log 只记权限变更详情';
COMMENT ON COLUMN operation_log.module IS '所属模块，如 type_definition/abstract_user/abstract_role 等';
COMMENT ON COLUMN operation_log.action IS '操作类型，如 CREATE/UPDATE/DELETE/SYNC/ASSIGN 等';
COMMENT ON COLUMN operation_log.target_type IS '操作目标类型';
COMMENT ON COLUMN operation_log.target_id IS '操作目标ID';
COMMENT ON COLUMN operation_log.summary IS '操作摘要';
COMMENT ON COLUMN operation_log.operator_id IS '操作人ID';
COMMENT ON COLUMN operation_log.operator_name IS '操作人名称';
COMMENT ON COLUMN operation_log.ip_address IS '操作人IP';
COMMENT ON COLUMN operation_log.request_id IS '请求/追踪ID';
