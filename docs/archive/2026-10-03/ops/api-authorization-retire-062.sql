-- T-ACCESS-062：停流、停止所有旧写者并完成整库备份后执行。psql -v ON_ERROR_STOP=1 -f ...
-- 不删除 API 登记实体/映射，不修改非 API 授权。备份账本独立于产品 schema。
BEGIN;
LOCK TABLE service_config, type_definition, role_resource_permission IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF to_regnamespace('accessmesh_retirement_062') IS NOT NULL THEN
        RAISE EXCEPTION 'T062_ALREADY_RECORDED: 不覆盖已有迁移账本，请核对其状态';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
        AND table_name='service_config' AND column_name='api_auth_mode') THEN
        RAISE EXCEPTION 'T062_SCHEMA_MISMATCH: 仅适用于已完成 059/061、仍有模式列的存量库';
    END IF;
    IF EXISTS (SELECT 1 FROM service_config WHERE delete_flag=0 AND api_auth_mode <> 'OPERATION_ADMISSION') THEN
        RAISE EXCEPTION 'T062_UNMIGRATED_SERVICE: 仍有未迁完服务';
    END IF;
    IF EXISTS (
        SELECT 1 FROM role_resource_permission child
        JOIN role_resource_permission parent ON child.depend_on=parent.id AND child.delete_flag=0
        JOIN type_definition t ON t.tenant_id=parent.tenant_id AND t.type_key='resource_type'
            AND t.type_code='API' AND t.delete_flag=0 AND parent.resource_type=t.type_value
        WHERE parent.delete_flag=0
            AND (child.tenant_id<>parent.tenant_id OR child.resource_type<>parent.resource_type)
    ) THEN
        RAISE EXCEPTION 'T062_BUSINESS_CHILD_REFERENCE: 业务子授权或跨租户子行引用 API 授权，停止并报告';
    END IF;
END $$;

CREATE SCHEMA accessmesh_retirement_062;
CREATE TABLE accessmesh_retirement_062.manifest (
    applied_at timestamptz NOT NULL DEFAULT now(),
    applied_by text NOT NULL DEFAULT current_user,
    rolled_back_at timestamptz
);
INSERT INTO accessmesh_retirement_062.manifest DEFAULT VALUES;
CREATE TABLE accessmesh_retirement_062.grants (id bigint PRIMARY KEY, original_row jsonb NOT NULL, retired_row jsonb);
INSERT INTO accessmesh_retirement_062.grants(id, original_row)
SELECT p.id,to_jsonb(p) FROM role_resource_permission p
JOIN type_definition t ON t.tenant_id=p.tenant_id AND t.type_key='resource_type' AND t.type_code='API'
    AND t.delete_flag=0 AND t.type_value=p.resource_type
WHERE p.delete_flag=0;

-- 模式列包含软删服务，回滚保留其原值；新代码不读取本账本。
CREATE TABLE accessmesh_retirement_062.service_modes AS SELECT id,api_auth_mode FROM service_config;
ALTER TABLE accessmesh_retirement_062.service_modes ADD PRIMARY KEY(id);
UPDATE role_resource_permission p SET delete_flag=p.id, deleted_by=0, deleted_at=now(), updated_by=0, updated_at=now()
FROM accessmesh_retirement_062.grants b WHERE p.id=b.id;
UPDATE accessmesh_retirement_062.grants b SET retired_row=to_jsonb(p)
FROM role_resource_permission p WHERE p.id=b.id;
ALTER TABLE service_config DROP COLUMN api_auth_mode;
COMMENT ON COLUMN service_config.config_generation IS '准入快照配置代次（T-ACCESS-059 计数列载体，2026-09-28 拍板）：该服务映射写路径（共用保存入口/删除/FULL 清理）与服务启停同事务 +1；准入快照构建事务内先读代次→构建→复读比对（复读语句 flushCache 强制落库，绕开会话一级缓存），变更即废弃重建（2026-09-25 拍板限定语义：构建期自一致校验；T-ACCESS-060 边界推导裁定 2026-09-28：接收侧不单独消费代次，由失效代际 epoch＋快照 TTL 承载，不承诺跨节点强一致）';
COMMIT;

SELECT count(*) AS retired_api_grants FROM accessmesh_retirement_062.grants;
