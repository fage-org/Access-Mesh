-- Historical fixture from be96a293f (pre T-PERM-071): exact affected tables, indexes and constraints.

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
COMMENT ON COLUMN sync_metadata.scope_key IS 'full-sync 清理范围键原文，采用 api-contract §19.7 的规范化 scopeKey，不包含 tenantId/sourceService/entityKind';
COMMENT ON COLUMN sync_metadata.scope_key_hash IS 'scope_key 的 SHA-256 lowercase hex，用于唯一约束和索引';
COMMENT ON COLUMN sync_metadata.business_key IS '同步对象业务键原文，采用 api-contract §19.7 的规范化 businessKey，不包含 tenantId/sourceService/entityKind';
COMMENT ON COLUMN sync_metadata.business_key_hash IS 'business_key 的 SHA-256 lowercase hex，用于唯一约束和索引';
COMMENT ON COLUMN sync_metadata.sync_key IS '来源内稳定同步键原文，用于定位同一外部事实，格式为 sourceService|entityKind|businessKey';
COMMENT ON COLUMN sync_metadata.sync_key_hash IS 'sync_key 的 SHA-256 lowercase hex，用于查询索引';
COMMENT ON COLUMN sync_metadata.target_id IS '目标表内部ID，仅 access-service 内部使用，不作为对外契约';
COMMENT ON COLUMN sync_metadata.target_status IS '目标同步状态：ABSTRACT_USER/ABSTRACT_ROLE/RESOURCE_ENTITY 仅允许 ACTIVE/DISABLED/DELETED；USER_ROLE 仅允许 ACTIVE/UNBOUND';
COMMENT ON COLUMN sync_metadata.last_sync_occurred_at IS '最后一次已应用同步事件发生时间';
COMMENT ON COLUMN sync_metadata.last_sync_sequence_no IS '最后一次已应用同步事件序号，和 occurred_at 共同判断新旧版本';

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
    ),
    -- 授权根种子形状焊死（T-PERM-062）：类型生命周期写路径（createType/createOperation/所有者
    -- 变更迁移）落库的 AUTHORITY_ROOT 行恒为「类型级 scopeAll + 可转授 + 无条件 + 无实例 + 单操作位」
    CONSTRAINT ck_role_resource_permission_authority_root CHECK (
        COALESCE(grant_source, 'MANUAL') <> 'AUTHORITY_ROOT'
        OR (scope_all = true AND can_grant = true AND condition_id IS NULL
            AND resource_entity_id IS NULL AND depend_on IS NULL
            AND granted_bits > 0 AND (granted_bits & (granted_bits - 1)) = 0)
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
COMMENT ON COLUMN role_resource_permission.grant_source IS '授权来源：MANUAL=手动授权，AUTO_DEP=resource_dependency 自动补全，AUTHORITY_ROOT=类型授权根种子（T-PERM-062：自定义 resource_type 的首授基座，类型创建/追加操作自动补种、所有者变更同事务迁移（先清后种）、类型删除级联清理（T-PERM-050）；apply-grant-plan 不可改删 20061，形状由 ck_role_resource_permission_authority_root 焊死）';
COMMENT ON COLUMN role_resource_permission.grant_dep_id IS '依赖规则ID（grant_source=AUTO_DEP 时记录触发的 resource_dependency.id）';

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

COMMENT ON TABLE resource_dependency IS '资源依赖：resource_entity_id 是源资源/被授权资源；depends_on_resource_entity_id 是被源资源依赖、需要自动补全的目标资源。source_operation_bits 为触发条件，required_operation_bits 为目标资源需要的操作位。auto_grant 为预留字段（自动授权简化方案已采纳，T-PERM-078 细化、T-PERM-071～073 实施）：字段随声明通道落地退役，退役前所有写入口拒绝 true（错误码 20048），依赖补全当前不生效';
COMMENT ON COLUMN resource_dependency.resource_entity_id IS '源资源ID（被授权资源）。授权该资源且满足 source_operation_bits 时触发依赖补全';
COMMENT ON COLUMN resource_dependency.depends_on_resource_entity_id IS '被依赖资源ID（自动补全目标资源），即被 resource_entity_id 依赖的资源';
COMMENT ON COLUMN resource_dependency.source_operation_bits IS '触发条件：源资源授权含这些bit时才触发依赖，NULL=任意操作都触发；唯一约束中按 COALESCE(source_operation_bits,0) 区分同一资源对下不同触发操作';
COMMENT ON COLUMN resource_dependency.required_operation_bits IS '被依赖目标资源需要自动补全的操作位';
COMMENT ON COLUMN resource_dependency.auto_grant IS '预留未实现：自动授权按简化方案由 T-PERM-078 细化、T-PERM-071～073 实施；字段随声明通道退役，退役前仅接受 false（true 返回 20048），默认 false';
COMMENT ON COLUMN resource_dependency.owner_service_code IS '依赖规则维护方服务编码；批量同步时用于限定 FULL diff 删除范围';
COMMENT ON COLUMN resource_dependency.maintain_source IS '维护来源：ADMIN_UI=管理端维护，SDK_SCAN=SDK扫描，MANIFEST=声明式清单，SERVICE_SYNC=服务同步';
COMMENT ON COLUMN resource_dependency.sync_key IS '同步源内稳定键，用于 FULL diff 判断。不同维护来源只清理同 owner_service_code + maintain_source 范围内缺失的规则';
