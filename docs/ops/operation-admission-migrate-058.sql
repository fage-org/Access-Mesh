-- T-ACCESS-058：映射独立来源与业务操作引用。执行前备份；不切换服务模式。
-- 存量来源无法可靠从 extra/绑定资源还原，统一 MANUAL；来源订正与操作补齐由 T-ACCESS-061 盘点处理。
BEGIN;

ALTER TABLE resource_api_mapping
    ADD COLUMN required_operation_id BIGINT,
    ADD COLUMN maintain_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL'
        CHECK (maintain_source IN ('MANUAL', 'SERVICE_SYNC', 'BOOTSTRAP'));
CREATE INDEX idx_resource_api_mapping_operation ON resource_api_mapping (tenant_id, required_operation_id)
    WHERE delete_flag = 0 AND required_operation_id IS NOT NULL;
ALTER TABLE service_config
    ADD COLUMN api_auth_mode VARCHAR(32) NOT NULL DEFAULT 'LEGACY_API'
        CHECK (api_auth_mode IN ('LEGACY_API', 'OPERATION_ADMISSION'));

-- 以固定 SERVICE 资源业务键补齐旧 bootstrap 缺失的服务配置；已登记配置不覆盖。
INSERT INTO service_config (tenant_id, service_code, name, status, api_auth_mode)
SELECT DISTINCT r.tenant_id, 'access-service', r.name, 1, 'LEGACY_API'
FROM resource_entity r
JOIN type_definition t ON t.tenant_id = r.tenant_id AND t.type_key = 'resource_type'
    AND t.type_code = 'SERVICE' AND t.type_value = r.resource_type AND t.delete_flag = 0
WHERE r.code = 'access-service' AND r.code_type = 'default' AND r.delete_flag = 0
ON CONFLICT (tenant_id, service_code) WHERE delete_flag = 0 DO NOTHING;

COMMENT ON TABLE resource_api_mapping IS '接口登记映射：resource_entity_id 引用 API 登记实体，required_operation_id 引用业务准入操作；LEGACY_API 沿用共同候选鉴权，OPERATION_ADMISSION 完整匹配路由后同要求去重、不同要求报配置故障';
COMMENT ON COLUMN resource_api_mapping.required_operation_id IS '业务准入操作 ID，类型从操作定义取得；LEGACY_API 存量可为空，不得将 API:ACCESS 作为准入要求；有效引用阻止操作删除和位变更';
COMMENT ON COLUMN resource_api_mapping.maintain_source IS '映射独立维护来源 MANUAL/SERVICE_SYNC/BOOTSTRAP；FULL 仅清所属服务的 SERVICE_SYNC 映射，不从资源来源或 extra 推断；存量迁移保守回填 MANUAL';
COMMENT ON COLUMN service_config.api_auth_mode IS '可信服务配置控制的鉴权模式 LEGACY_API/OPERATION_ADMISSION；不接受客户端模式头，切换须满足逐路由业务最终检查与反向拒绝测试的迁移门槛';
COMMENT ON COLUMN service_config.status IS '状态：0=停用 1=启用。停用后该服务的接口不参与授权；资源/依赖同步通道（sync/full-sync、manifest）拒绝（SECURITY_DENIED），接口声明同步（service-config/sync、sync-v2）拒绝 20025（T-ACCESS-058）';

COMMIT;
