-- 仅限停流的成套版本回退；禁止新旧版本并行。先核对备份，不覆盖迁移后已改动的授权。
BEGIN;
LOCK TABLE service_config, role_resource_permission IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF (SELECT rolled_back_at IS NOT NULL FROM accessmesh_retirement_062.manifest) THEN
        RAISE EXCEPTION 'T062_ALREADY_ROLLED_BACK';
    END IF;
    IF EXISTS (SELECT 1 FROM accessmesh_retirement_062.grants b
        LEFT JOIN role_resource_permission p ON p.id=b.id
        WHERE p.id IS NULL OR to_jsonb(p) IS DISTINCT FROM b.retired_row) THEN
        RAISE EXCEPTION 'T062_ROLLBACK_ROW_CHANGED: 迁移后授权被改动，停止并人工核对';
    END IF;
    IF EXISTS (SELECT 1 FROM service_config c FULL JOIN accessmesh_retirement_062.service_modes b USING(id)
        WHERE c.id IS NULL OR b.id IS NULL) THEN
        RAISE EXCEPTION 'T062_ROLLBACK_SERVICE_SET_CHANGED: 服务集合变化，停止并人工核对';
    END IF;
END $$;

ALTER TABLE service_config ADD COLUMN api_auth_mode varchar(32) NOT NULL DEFAULT 'OPERATION_ADMISSION'
    CHECK (api_auth_mode IN ('LEGACY_API','OPERATION_ADMISSION'));
UPDATE service_config c SET api_auth_mode=b.api_auth_mode FROM accessmesh_retirement_062.service_modes b WHERE c.id=b.id;
COMMENT ON COLUMN service_config.api_auth_mode IS '可信服务配置控制的鉴权模式 LEGACY_API/OPERATION_ADMISSION；不接受客户端模式头。T-ACCESS-059 拍板无迁移期统一上线：全部服务（含 access-service 自身）默认 OPERATION_ADMISSION，LEGACY_API 值仅作 062 退役前的版本回退部署形态（服务端准入端点对 LEGACY_API 服务按配置故障拒绝）';

-- 仅还原迁移修改的审计/软删字段；整行 retired_row 比对保证其余字段仍等于原始快照。
UPDATE role_resource_permission p SET delete_flag=r.delete_flag, deleted_by=r.deleted_by, deleted_at=r.deleted_at,
    updated_by=r.updated_by, updated_at=r.updated_at
FROM accessmesh_retirement_062.grants b,
    LATERAL jsonb_populate_record(NULL::role_resource_permission,b.original_row) r
WHERE p.id=b.id;
COMMENT ON COLUMN service_config.config_generation IS '准入快照配置代次（T-ACCESS-059 计数列载体，2026-09-28 拍板）：该服务映射写路径（共用保存入口/删除/FULL 清理）与模式切换同事务 +1；准入快照构建事务内先读代次→构建→复读比对（复读语句 flushCache 强制落库，绕开会话一级缓存），变更即废弃重建（2026-09-25 拍板限定语义：构建期自一致校验；T-ACCESS-060 边界推导裁定 2026-09-28：接收侧不单独消费代次，由失效代际 epoch＋快照 TTL 承载，不承诺跨节点强一致）';
UPDATE accessmesh_retirement_062.manifest SET rolled_back_at=now();
COMMIT;
