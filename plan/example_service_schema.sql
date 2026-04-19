-- =============================================================================
-- 演示服务 (example-service) - PostgreSQL 表结构（4 张表）
-- 企业 BI 平台演示业务模型
-- 无外键，逻辑关联由应用保证
-- =============================================================================
-- 软删约定（与权限中心一致）：
--   delete_flag BIGINT：0 = 未删除，删除时填本行 id
--   demo_task_log 不做软删除
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. demo_datasource - 数据源
-- -----------------------------------------------------------------------------
CREATE TABLE demo_datasource (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    name        VARCHAR(128) NOT NULL,
    ds_type     VARCHAR(32) NOT NULL,
    config      JSONB NOT NULL DEFAULT '{}',
    project_id  BIGINT NOT NULL,
    status      SMALLINT NOT NULL DEFAULT 1,
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    delete_flag BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_datasource_tenant ON demo_datasource (tenant_id, project_id) WHERE delete_flag = 0;

COMMENT ON TABLE demo_datasource IS '数据源：数据库连接/API 端点配置';
COMMENT ON COLUMN demo_datasource.ds_type IS '类型：MYSQL/POSTGRESQL/API/CSV';
COMMENT ON COLUMN demo_datasource.config IS '连接配置（加密存储，host/port/db 等）';
COMMENT ON COLUMN demo_datasource.project_id IS '所属项目组ID（用于数据权限演示）';
COMMENT ON COLUMN demo_datasource.status IS '状态：0=不可用，1=可用';
COMMENT ON COLUMN demo_datasource.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 2. demo_report - 报表
-- -----------------------------------------------------------------------------
CREATE TABLE demo_report (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    datasource_id BIGINT,
    query_config  JSONB DEFAULT '{}',
    project_id    BIGINT NOT NULL,
    status        SMALLINT NOT NULL DEFAULT 0,
    shared        BOOLEAN NOT NULL DEFAULT false,
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    delete_flag   BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_report_tenant ON demo_report (tenant_id, project_id) WHERE delete_flag = 0;
CREATE INDEX idx_report_status ON demo_report (tenant_id, status) WHERE delete_flag = 0;

COMMENT ON TABLE demo_report IS '报表定义';
COMMENT ON COLUMN demo_report.datasource_id IS '关联数据源 demo_datasource.id';
COMMENT ON COLUMN demo_report.query_config IS '查询配置（SQL/聚合条件等）';
COMMENT ON COLUMN demo_report.project_id IS '所属项目组ID（用于数据权限演示）';
COMMENT ON COLUMN demo_report.status IS '状态：0=草稿，1=已发布，2=已归档';
COMMENT ON COLUMN demo_report.shared IS '是否已分享';
COMMENT ON COLUMN demo_report.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 3. demo_task - 数据任务
-- -----------------------------------------------------------------------------
CREATE TABLE demo_task (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    name            VARCHAR(128) NOT NULL,
    task_type       VARCHAR(32) NOT NULL,
    datasource_id   BIGINT,
    task_config     JSONB DEFAULT '{}',
    cron_expression VARCHAR(128),
    project_id      BIGINT NOT NULL,
    status          SMALLINT NOT NULL DEFAULT 0,
    last_run_at     TIMESTAMPTZ,
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    delete_flag     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_task_tenant ON demo_task (tenant_id, project_id) WHERE delete_flag = 0;

COMMENT ON TABLE demo_task IS '数据任务：ETL/数据处理任务定义';
COMMENT ON COLUMN demo_task.task_type IS '任务类型：ETL/SYNC/AGGREGATE';
COMMENT ON COLUMN demo_task.datasource_id IS '关联数据源 demo_datasource.id';
COMMENT ON COLUMN demo_task.task_config IS '任务配置（脚本/步骤定义）';
COMMENT ON COLUMN demo_task.cron_expression IS 'Cron 表达式（注册到 admin-service A-10 任务调度中心执行）';
COMMENT ON COLUMN demo_task.project_id IS '所属项目组ID（用于数据权限演示）';
COMMENT ON COLUMN demo_task.status IS '状态：0=停用，1=启用，2=运行中';
COMMENT ON COLUMN demo_task.delete_flag IS '逻辑删除：0=未删除，删除时填本行id';

-- -----------------------------------------------------------------------------
-- 4. demo_task_log - 任务执行日志（不做软删除）
-- -----------------------------------------------------------------------------
CREATE TABLE demo_task_log (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    task_id     BIGINT NOT NULL,
    status      SMALLINT NOT NULL DEFAULT 0,
    message     TEXT,
    cost_time   INT,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_task_log_task ON demo_task_log (tenant_id, task_id, started_at DESC);

COMMENT ON TABLE demo_task_log IS '任务执行日志，不做软删除';
COMMENT ON COLUMN demo_task_log.status IS '执行结果：0=失败，1=成功';
COMMENT ON COLUMN demo_task_log.cost_time IS '耗时（毫秒）';
