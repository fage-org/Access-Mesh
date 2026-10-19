-- example-service 独立业务库的唯一可执行 DDL（T-ACCESS-137）。
-- 仅空库初始化；不清库、不自动迁移旧的未使用 BI schema，不创建 IAM 表或业务样本。
-- 所有关联为逻辑 ID，应用写入口同时验证租户与归属，不创建物理外键。
-- 审计瞬时使用 TIMESTAMPTZ / UTC；sale_date 是业务日历日期，不是审计瞬时。
-- import 批次、发布与执行记录保留身份；撤销/过期使用状态，不删除幂等键。

-- 固定数据集控制行：策略生效引用与本地写入串行边界
CREATE TABLE demo_dataset (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    dataset_code VARCHAR(64) NOT NULL CHECK (dataset_code = 'sales-records'),
    active_policy_version BIGINT NOT NULL DEFAULT 0 CHECK (active_policy_version >= 0),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_dataset_tenant_code ON demo_dataset(tenant_id, dataset_code) WHERE delete_flag = 0;
COMMENT ON TABLE demo_dataset IS '固定数据集控制行：策略生效引用与本地写入串行边界';

-- 报表元数据：主类型固定为 DEMO_REPORT，字段目录独立版本
CREATE TABLE demo_report (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    dataset_id BIGINT NOT NULL,
    report_code VARCHAR(64) NOT NULL CHECK (length(btrim(report_code)) > 0),
    name VARCHAR(128) NOT NULL,
    field_resource_type_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    catalog_version BIGINT NOT NULL DEFAULT 0 CHECK (catalog_version >= 0),
    publication_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (publication_status IN ('PENDING','PUBLISHED','FAILED')),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_report_tenant_code ON demo_report(tenant_id, report_code) WHERE delete_flag = 0;
CREATE INDEX idx_demo_report_tenant_dataset ON demo_report(tenant_id, dataset_id) WHERE delete_flag = 0;
COMMENT ON TABLE demo_report IS '报表元数据：主类型固定为 DEMO_REPORT，字段目录独立版本';

-- 报表字段身份：资源编码由不可复用主键派生，软删不复用主键或编码
CREATE TABLE demo_report_field (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    report_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL CHECK (field_key IN ('recordCode','ownerUserId','teamCode','departmentCode','regionCode','saleDate','productName','amount','remark')),
    resource_code VARCHAR(64) GENERATED ALWAYS AS (id::text) STORED,
    enabled BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    publication_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (publication_status IN ('PENDING','PUBLISHED','FAILED')),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_report_field_tenant_report_key ON demo_report_field(tenant_id, report_id, field_key) WHERE delete_flag = 0;
CREATE INDEX idx_demo_report_field_tenant_resource ON demo_report_field(tenant_id, resource_code);
COMMENT ON TABLE demo_report_field IS '报表字段身份：资源编码由不可复用主键派生，软删不复用主键或编码';

-- 范围资源元数据：业务谓词归策略版本，不写入远端资源
CREATE TABLE demo_scope (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    resource_type_code VARCHAR(64) NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    code_type VARCHAR(32) NOT NULL DEFAULT 'default',
    name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    publication_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (publication_status IN ('PENDING','PUBLISHED','FAILED')),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_scope_tenant_type_code ON demo_scope(tenant_id, resource_type_code, resource_code, code_type) WHERE delete_flag = 0;
COMMENT ON TABLE demo_scope IS '范围资源元数据：业务谓词归策略版本，不写入远端资源';

-- 完整策略历史：发布后内容不可改写，恢复历史生成新版本
CREATE TABLE demo_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    dataset_id BIGINT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    configuration JSONB NOT NULL CHECK (jsonb_typeof(configuration) = 'object' AND octet_length(configuration::text) <= 262144),
    change_summary VARCHAR(512) NOT NULL,
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_policy_tenant_dataset_version ON demo_policy(tenant_id, dataset_id, version) WHERE delete_flag = 0;
COMMENT ON TABLE demo_policy IS '完整策略历史：发布后内容不可改写，恢复历史生成新版本';

-- 导入幂等批次：撤销只变状态，保留 delete_flag=0 与请求号，不长期保存文件
CREATE TABLE demo_import_batch (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    report_id BIGINT NOT NULL,
    import_key UUID NOT NULL,
    file_sha256 CHAR(64) NOT NULL CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    imported_fields JSONB NOT NULL CHECK (jsonb_typeof(imported_fields) = 'array' AND jsonb_array_length(imported_fields) > 0),
    record_count BIGINT NOT NULL CHECK (record_count BETWEEN 1 AND 10000),
    policy_version BIGINT NOT NULL CHECK (policy_version > 0),
    catalog_version BIGINT NOT NULL CHECK (catalog_version >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'IMPORTED' CHECK (status IN ('IMPORTED','REVOKED')),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_import_batch_tenant_key ON demo_import_batch(tenant_id, import_key) WHERE delete_flag = 0;
CREATE INDEX idx_demo_import_batch_tenant_report ON demo_import_batch(tenant_id, report_id) WHERE delete_flag = 0;
COMMENT ON TABLE demo_import_batch IS '导入幂等批次：撤销只变状态，保留 delete_flag=0 与请求号，不长期保存文件';

-- 销售记录：业务归属导入时固定，普通业务写只更新备注
CREATE TABLE demo_record (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    batch_id BIGINT NOT NULL,
    record_code VARCHAR(64) NOT NULL CHECK (length(btrim(record_code)) > 0),
    owner_user_id BIGINT NOT NULL CHECK (owner_user_id > 0),
    team_code VARCHAR(64) NOT NULL,
    department_code VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    sale_date DATE NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK (amount <> 'NaN'::numeric),
    remark VARCHAR(1000),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_record_tenant_code ON demo_record(tenant_id, record_code) WHERE delete_flag = 0;
CREATE INDEX idx_demo_record_tenant_batch ON demo_record(tenant_id, batch_id) WHERE delete_flag = 0;
CREATE INDEX idx_demo_record_tenant_owner ON demo_record(tenant_id, owner_user_id) WHERE delete_flag = 0;
CREATE INDEX idx_demo_record_tenant_scope ON demo_record(tenant_id, team_code, department_code, region_code) WHERE delete_flag = 0;
COMMENT ON TABLE demo_record IS '销售记录：业务归属导入时固定，普通业务写只更新备注';

-- 增量资源待发布事实：业务事务写入，事务外按冻结载荷与版本发送
CREATE TABLE demo_publication (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    resource_type_code VARCHAR(64) NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    code_type VARCHAR(32) NOT NULL DEFAULT 'default',
    source_version BIGINT NOT NULL CHECK (source_version > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    attempt INTEGER NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    next_attempt_at TIMESTAMPTZ,
    failure_reason VARCHAR(128),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_publication_tenant_resource_version ON demo_publication(tenant_id, resource_type_code, resource_code, code_type, source_version) WHERE delete_flag = 0;
CREATE INDEX idx_demo_publication_status_due ON demo_publication(status, next_attempt_at) WHERE delete_flag = 0;
COMMENT ON TABLE demo_publication IS '增量资源待发布事实：业务事务写入，事务外按冻结载荷与版本发送';

-- 内部快照任务：执行主体固定为 created_by，UTC 单实例调度
CREATE TABLE demo_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    name VARCHAR(128) NOT NULL,
    cron VARCHAR(128) NOT NULL,
    selection JSONB NOT NULL CHECK (jsonb_typeof(selection) = 'object'),
    enabled BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    next_scheduled_time TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_demo_task_tenant_creator ON demo_task(tenant_id, created_by) WHERE delete_flag = 0;
CREATE INDEX idx_demo_task_enabled_due ON demo_task(enabled, next_scheduled_time) WHERE delete_flag = 0;
COMMENT ON TABLE demo_task IS '内部快照任务：执行主体固定为 created_by，UTC 单实例调度';

-- 执行状态与有界产物：结果和 SUCCESS 同事务，过期仅清除字节，保留执行键
CREATE TABLE demo_execution (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    execution_key UUID NOT NULL DEFAULT gen_random_uuid(),
    task_id BIGINT,
    task_version BIGINT,
    trigger_kind VARCHAR(16) NOT NULL CHECK (trigger_kind IN ('CRON','MANUAL','ONCE')),
    trigger_key UUID,
    scheduled_time TIMESTAMPTZ,
    request_payload JSONB NOT NULL CHECK (jsonb_typeof(request_payload) = 'object'),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','SUCCESS','DENIED','EMPTY','SKIPPED','FAILED','EXPIRED')),
    attempt INTEGER NOT NULL DEFAULT 0 CHECK (attempt BETWEEN 0 AND 3),
    next_attempt_at TIMESTAMPTZ,
    policy_version BIGINT,
    catalog_version BIGINT,
    output_columns JSONB,
    result_row_count BIGINT,
    result_bytes BYTEA,
    expires_at TIMESTAMPTZ,
    failure_reason VARCHAR(128),
    CHECK ((trigger_kind = 'CRON' AND task_id IS NOT NULL AND task_version IS NOT NULL AND task_version > 0 AND scheduled_time IS NOT NULL AND trigger_key IS NULL)
        OR (trigger_kind = 'MANUAL' AND task_id IS NOT NULL AND task_version IS NOT NULL AND task_version > 0 AND scheduled_time IS NULL AND trigger_key IS NOT NULL)
        OR (trigger_kind = 'ONCE' AND task_id IS NULL AND task_version IS NULL AND scheduled_time IS NULL AND trigger_key IS NOT NULL)),
    CHECK ((status = 'SUCCESS' AND result_bytes IS NOT NULL AND octet_length(result_bytes) BETWEEN 1 AND 1048576
        AND result_row_count IS NOT NULL AND result_row_count BETWEEN 1 AND 1000 AND expires_at IS NOT NULL)
        OR (status <> 'SUCCESS' AND result_bytes IS NULL)),
    delete_flag BIGINT NOT NULL DEFAULT 0 CHECK (delete_flag = 0 OR delete_flag = id),
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uniq_demo_execution_tenant_key ON demo_execution(tenant_id, execution_key) WHERE delete_flag = 0;
CREATE UNIQUE INDEX uniq_demo_execution_tenant_trigger ON demo_execution(tenant_id, trigger_key) WHERE delete_flag = 0 AND trigger_key IS NOT NULL;
CREATE UNIQUE INDEX uniq_demo_execution_tenant_task_time ON demo_execution(tenant_id, task_id, scheduled_time) WHERE delete_flag = 0 AND trigger_kind = 'CRON';
CREATE INDEX idx_demo_execution_status_due ON demo_execution(status, next_attempt_at) WHERE delete_flag = 0;
COMMENT ON TABLE demo_execution IS '执行状态与有界产物：结果和 SUCCESS 同事务，过期仅清除字节，保留执行键';
