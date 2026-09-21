-- T-PERM-071 upgrade. Run with all old/new application writers stopped, after the read-only preflight.
-- PostgreSQL public schema only. Entire transaction rolls back on any failure; never auto-convert legacy facts.
BEGIN;
SET LOCAL search_path = public;
LOCK TABLE resource_dependency, role_resource_permission, sync_metadata IN ACCESS EXCLUSIVE MODE;

DO $migration$
DECLARE actual_shape text[];
BEGIN
    IF to_regclass('resource_dependency_legacy') IS NOT NULL
       OR to_regclass('permission_dependency_declaration') IS NOT NULL
       OR to_regclass('service_manifest_sync') IS NOT NULL
       OR to_regclass('resource_publication_state') IS NOT NULL THEN
        RAISE EXCEPTION 'AUTO_GRANT_MIGRATION_TARGET_EXISTS: inspect migration history; do not overwrite';
    END IF;
    SELECT array_agg(attname || ':' || format_type(atttypid, atttypmod) ORDER BY attname COLLATE "C")
      INTO actual_shape FROM pg_attribute
     WHERE attrelid='resource_dependency'::regclass AND attnum>0 AND NOT attisdropped;
    IF actual_shape IS DISTINCT FROM ARRAY[
        'auto_grant:boolean','created_at:timestamp with time zone','created_by:bigint',
        'delete_flag:bigint','deleted_at:timestamp with time zone','deleted_by:bigint',
        'depends_on_resource_entity_id:bigint','description:character varying(512)','id:bigint',
        'maintain_source:character varying(32)','owner_service_code:character varying(128)',
        'required_operation_bits:bigint','resource_entity_id:bigint','source_operation_bits:bigint',
        'sync_key:character varying(256)','tenant_id:bigint','updated_at:timestamp with time zone','updated_by:bigint'
    ]::text[] THEN
        RAISE EXCEPTION 'AUTO_GRANT_MIGRATION_SCHEMA_MISMATCH: resource_dependency columns differ';
    END IF;
    IF EXISTS (SELECT 1 FROM role_resource_permission WHERE delete_flag=0 AND grant_source='AUTO_DEP') THEN
        RAISE EXCEPTION 'AUTO_GRANT_MIGRATION_ACTIVE_AUTO_DEP: verify existing grants before upgrading';
    END IF;
    IF EXISTS (SELECT 1 FROM role_resource_permission WHERE delete_flag=0
               AND (grant_source IS NULL OR grant_source NOT IN ('MANUAL','AUTHORITY_ROOT','AUTO_DEP'))) THEN
        RAISE EXCEPTION 'AUTO_GRANT_MIGRATION_UNKNOWN_GRANT_SOURCE: verify existing grants before upgrading';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid='sync_metadata'::regclass
               AND attname IN ('last_publication_generation','last_publication_hash') AND NOT attisdropped) THEN
        RAISE EXCEPTION 'AUTO_GRANT_MIGRATION_SCHEMA_MISMATCH: publication columns already exist';
    END IF;
END
$migration$;

ALTER TABLE resource_dependency RENAME TO resource_dependency_legacy;
ALTER TABLE resource_dependency_legacy RENAME CONSTRAINT resource_dependency_pkey TO resource_dependency_legacy_pkey;
ALTER INDEX uk_resource_dependency RENAME TO uk_resource_dependency_legacy;
ALTER INDEX idx_resource_dependency_resource RENAME TO idx_resource_dependency_legacy_resource;
ALTER INDEX idx_resource_dependency_sync_owner RENAME TO idx_resource_dependency_legacy_sync_owner;
ALTER SEQUENCE resource_dependency_id_seq RENAME TO resource_dependency_legacy_id_seq;

ALTER TABLE sync_metadata ADD COLUMN last_publication_generation BIGINT;
ALTER TABLE sync_metadata ADD COLUMN last_publication_hash CHAR(64);

-- New structures match docs/design/schema/access-service.sql; old IDs and all audit data remain in legacy.
CREATE TABLE resource_dependency (
    id                            BIGSERIAL PRIMARY KEY,
    tenant_id                     BIGINT NOT NULL,
    resource_entity_id            BIGINT NOT NULL,
    depends_on_resource_entity_id BIGINT NOT NULL,
    source_operation_bits         BIGINT,
    required_operation_bits       BIGINT NOT NULL,
    declaration_id                BIGINT,
    owner_service_code            VARCHAR(128),
    maintain_source               VARCHAR(32) NOT NULL DEFAULT 'MANIFEST',
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
CREATE INDEX idx_resource_dependency_target ON resource_dependency (tenant_id, depends_on_resource_entity_id) WHERE delete_flag = 0;
CREATE INDEX idx_resource_dependency_sync_owner ON resource_dependency (tenant_id, owner_service_code, maintain_source) WHERE delete_flag = 0 AND owner_service_code IS NOT NULL;

COMMENT ON TABLE resource_dependency IS 'MANIFEST 声明的聚合编译图，旧规则仅保全到 resource_dependency_legacy；按角色完整重算物化消费（T-PERM-072 AutoGrantMaterializationDomainService，读取面经 loadCompiledEdges）';
COMMENT ON COLUMN resource_dependency.resource_entity_id IS '源资源ID（被授权资源）。授权该资源且满足 source_operation_bits 时触发依赖补全';
COMMENT ON COLUMN resource_dependency.depends_on_resource_entity_id IS '被依赖资源ID（自动补全目标资源），即被 resource_entity_id 依赖的资源';
COMMENT ON COLUMN resource_dependency.source_operation_bits IS '触发条件：源资源授权含这些bit时才触发依赖，NULL=任意操作都触发；唯一约束中按 COALESCE(source_operation_bits,0) 区分同一资源对下不同触发操作';
COMMENT ON COLUMN resource_dependency.required_operation_bits IS '被依赖目标资源需要自动补全的操作位';
COMMENT ON COLUMN resource_dependency.owner_service_code IS '声明所属服务，编译图按租户与服务替换';
COMMENT ON COLUMN resource_dependency.maintain_source IS '编译器固定为 MANIFEST';
COMMENT ON COLUMN resource_dependency.sync_key IS '保留诊断字段，编译图不以此键判断声明存续';

-- 依赖声明与发布状态（T-PERM-071；旧依赖保全由升级脚本处理）
CREATE TABLE permission_dependency_declaration (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    source_service VARCHAR(128) NOT NULL,
    declaration_key VARCHAR(128) NOT NULL,
    business_key TEXT NOT NULL,
    business_key_hash CHAR(64) NOT NULL,
    declaration_payload JSONB NOT NULL,
    semantic_hash CHAR(64) NOT NULL,
    compile_status VARCHAR(16) NOT NULL,
    reject_reason VARCHAR(64),
    source_resource_id BIGINT,
    target_resource_id BIGINT,
    source_operation_bits BIGINT,
    required_operation_bits BIGINT,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0,
    -- compile_status 值域 {RESOLVED, REJECTED}：代码侧唯一引用点为实体常量
    -- PermissionDependencyDeclaration.COMPILE_STATUS_*（T-PERM-079 收敛），改值须两处同批。
    CONSTRAINT ck_dependency_declaration_state CHECK (
        (compile_status = 'RESOLVED' AND reject_reason IS NULL
         AND source_resource_id IS NOT NULL AND target_resource_id IS NOT NULL
         AND required_operation_bits IS NOT NULL AND required_operation_bits > 0)
        OR (compile_status = 'REJECTED' AND reject_reason IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uk_dependency_declaration_key ON permission_dependency_declaration
    (tenant_id, source_service, business_key_hash) WHERE delete_flag = 0;
CREATE INDEX idx_dependency_declaration_source ON permission_dependency_declaration
    (tenant_id, source_resource_id) WHERE delete_flag = 0;
CREATE INDEX idx_dependency_declaration_target ON permission_dependency_declaration
    (tenant_id, target_resource_id) WHERE delete_flag = 0;
COMMENT ON TABLE permission_dependency_declaration IS '所属服务 MANIFEST 唯一写入的声明事实；每声明目标一项，失败保留诊断，RESOLVED 按编译键聚合；无逐路径 support';
COMMENT ON COLUMN permission_dependency_declaration.declaration_payload IS '规范化声明：稳定键、源/目标业务键、触发操作、目标操作集合和描述；不含来源路径';

CREATE TABLE service_manifest_sync (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    source_service VARCHAR(128) NOT NULL,
    publication_generation BIGINT NOT NULL CHECK (publication_generation > 0),
    revision VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    semantic_hash CHAR(64) NOT NULL,
    -- sync_status 值域 {SUCCESS, PARTIAL, FAILED}：代码侧唯一引用点为实体常量
    -- ServiceManifestSync.SYNC_STATUS_*（T-PERM-079 收敛；resource_publication_state.last_full_status
    -- 共用 SUCCESS/PARTIAL 子域），改值须两处同批。
    sync_status VARCHAR(16) NOT NULL CHECK (sync_status IN ('SUCCESS','PARTIAL','FAILED')),
    is_dirty BOOLEAN NOT NULL DEFAULT false,
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_service_manifest_sync ON service_manifest_sync (tenant_id, source_service) WHERE delete_flag = 0;
COMMENT ON TABLE service_manifest_sync IS '服务依赖 FULL 的代次/不可变请求指纹与编译状态；旧代次不得回退声明，PARTIAL 同事务推进，dirty 阻止历史成功短路';

CREATE TABLE resource_publication_state (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    source_service VARCHAR(128) NOT NULL,
    scope_key TEXT NOT NULL,
    scope_key_hash CHAR(64) NOT NULL,
    max_generation BIGINT NOT NULL CHECK (max_generation > 0),
    last_full_generation BIGINT,
    last_full_payload_hash CHAR(64),
    last_full_status VARCHAR(16) CHECK (last_full_status IN ('SUCCESS','PARTIAL')),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_resource_publication_full CHECK (
        (last_full_generation IS NULL AND last_full_payload_hash IS NULL)
        OR (last_full_generation > 0 AND last_full_payload_hash IS NOT NULL AND last_full_generation <= max_generation)
    )
);
CREATE UNIQUE INDEX uk_resource_publication_scope ON resource_publication_state
    (tenant_id, source_service, scope_key_hash) WHERE delete_flag = 0;
COMMENT ON TABLE resource_publication_state IS '资源 scope 切入共同顺序后持久保留；max 防旧 FULL，last_full 防旧增量，逐键顺序另存 sync_metadata；不自动退回旧协议';

COMMIT;
