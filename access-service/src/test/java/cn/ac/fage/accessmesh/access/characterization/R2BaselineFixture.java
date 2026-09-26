package cn.ac.fage.accessmesh.access.characterization;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * R2 差分基线夹具（T-PERM-081）——固定事实集的唯一种子源（共享 Java 夹具类，2026-09-25 拍板）。
 * <p>
 * 固定 seed 口径（设计 §9.3：离线差分固定 seed／时钟／规则／读入事实）：
 * </p>
 * <ul>
 *   <li>类型段 941~949（type_definition resource_type）；缺陷反例类型段 951~954；</li>
 *   <li>资源实体 9_641_000+；角色 9_642_000+；主体 9_643_000+；API 映射 9_644_000+；
 *       授权行 9_645_000+；条件 9_646_000+；冲突规则 9_647_000+；操作位 9_649_000+；</li>
 *   <li>基线图全部显式 id + {@code ON CONFLICT (id) DO NOTHING} 幂等，跨用例重复种安全；</li>
 *   <li>唯一条件 COND_UNSAT 为恒不满足 DATE_RANGE（2000-01-01~2001-01-01），
 *       全家族期望输出不依赖运行时钟漂移；查询面时钟由各测试按入口能力钉住
 *       （queryBatch 钉 PermEvalContext；AppService 入口 evaluatedAt 由引擎入口统一钉一次）。</li>
 * </ul>
 * <p>
 * 消费方：{@code QuerySemanticsBaselinePgIT}（正常语义基线——check/batch-check/范围四态/快照投影
 * 四族 X03 差分锚）与 {@code MutexSemanticsCharacterizationPgIT}（PQ-01/06 反例）；T-PERM-089/090
 * X03 新旧等价差分直接复用本类构造同一事实集。
 * </p>
 */
public final class R2BaselineFixture {

    public static final Long TENANT = 1L;

    // ===== 基线图固定常量（tenant 1）=====

    /** resource_type 类型值：T1=主实例族 / T2=scopeAll 族 / T3=scopeAll+条件摘光族 */
    public static final int TYPE_T1 = 941;
    public static final int TYPE_T2 = 942;
    public static final int TYPE_T3 = 943;
    public static final String TYPE_T1_CODE = "R2BT1";
    public static final String TYPE_T2_CODE = "R2BT2";
    public static final String TYPE_T3_CODE = "R2BT3";

    /** T1 操作位（VIEW=2 / UPDATE=4 mask 覆盖 VIEW / CREATE=1 / DELETE=8 mask 覆盖 VIEW） */
    public static final long T1_VIEW_BIT = 2L;
    public static final long T1_UPDATE_BIT = 4L;
    public static final long T1_CREATE_BIT = 1L;
    public static final long T1_DELETE_BIT = 8L;

    /** 资源实体：r1=父（挂授权）/ r2=r1 子（挂授权）/ r3=r1 子（无授权，闭包目标）/ r4=条件行载体 */
    public static final long RES_R1 = 9_641_001L;
    public static final long RES_R2 = 9_641_002L;
    public static final long RES_R3 = 9_641_003L;
    public static final long RES_R4 = 9_641_004L;
    public static final String CODE_R1 = "r2b-r1";
    public static final String CODE_R2 = "r2b-r2";
    public static final String CODE_R3 = "r2b-r3";
    public static final String CODE_R4 = "r2b-r4";
    /** T2 实例（scopeAll 族对照实体） */
    public static final long RES_S1 = 9_641_011L;
    public static final String CODE_S1 = "r2b-s1";
    /** API 类型（种子 type_value=3，ACCESS bit=16）实例实体与映射承载实体 */
    public static final long RES_API_E1 = 9_641_021L;
    public static final long RES_API_E2 = 9_641_022L;

    /** 角色（BASIC_ROLE=6）：A=实例授权 / B=T2 scopeAll / C=T3 scopeAll+条件 / X⊥Y=角色互斥两端 / S=API 实例 / S2=API scopeAll */
    public static final long ROLE_A = 9_642_001L;
    public static final long ROLE_B = 9_642_002L;
    public static final long ROLE_C = 9_642_003L;
    public static final long ROLE_X = 9_642_004L;
    public static final long ROLE_Y = 9_642_005L;
    public static final long ROLE_S = 9_642_006L;
    public static final long ROLE_S2 = 9_642_007L;

    /** 主体（subject_type USER=1，externalId=id 字符串） */
    public static final long USER_INST = 9_643_001L;
    public static final long USER_ALL = 9_643_002L;
    public static final long USER_EMPTY = 9_643_003L;
    public static final long USER_MUTEX = 9_643_004L;
    public static final long USER_SNAP = 9_643_005L;
    public static final long USER_NONE = 9_643_006L;
    public static final long USER_SNAP_ALL = 9_643_007L;

    /** API 映射：svc-a=实例轨单映射 / svc-b=scopeAll 展开双映射＋一条禁用映射（锁 enabled 过滤回归）；
     *  承载服务行（生产形态=映射挂已声明服务，claude 外评 P3 补种） */
    public static final long SVC_CFG_A = 9_648_001L;
    public static final long SVC_CFG_B = 9_648_002L;
    public static final long MAP_INST = 9_644_001L;
    public static final long MAP_ALL_1 = 9_644_002L;
    public static final long MAP_ALL_2 = 9_644_003L;
    public static final long MAP_ALL_DISABLED = 9_644_004L;
    public static final String SVC_INST = "r2b-svc-a";
    public static final String SVC_ALL = "r2b-svc-b";

    /** 授权行（grant_source=MANUAL，全部单位——ck_manual_single_operation） */
    public static final long PERM_R1_VIEW = 9_645_001L;
    public static final long PERM_R1_UPDATE = 9_645_002L;
    public static final long PERM_R2_VIEW = 9_645_003L;
    public static final long PERM_R4_DELETE_COND = 9_645_004L;
    public static final long PERM_T2_SCOPE_ALL = 9_645_005L;
    public static final long PERM_T3_SCOPE_ALL_COND = 9_645_006L;
    public static final long PERM_RX_R2_VIEW = 9_645_007L;
    public static final long PERM_RY_R2_VIEW = 9_645_008L;
    public static final long PERM_API_E1_ACCESS = 9_645_009L;
    public static final long PERM_API_SCOPE_ALL = 9_645_010L;

    /** 恒不满足条件（DATE_RANGE 2000~2001）与角色互斥规则（X⊥Y） */
    public static final long COND_UNSAT = 9_646_001L;
    public static final long RULE_ROLE_MUTEX_XY = 9_647_001L;

    /** 基线操作位行 id */
    public static final long OP_T1_VIEW = 9_649_001L;
    public static final long OP_T1_UPDATE = 9_649_002L;
    public static final long OP_T1_CREATE = 9_649_003L;
    public static final long OP_T1_DELETE = 9_649_004L;
    public static final long OP_T2_VIEW = 9_649_011L;
    public static final long OP_T3_VIEW = 9_649_021L;

    /** API 类型种子值与 ACCESS 位（docs/design/schema/access-service.sql 预置） */
    public static final int TYPE_API = 3;
    public static final long API_ACCESS_BIT = 16L;

    /** R01/R02 顺序敏感性专用租户（互斥规则集隔离，避开基线图租户的 ROLE_MUTEX_RULE 缓存键） */
    public static final long TENANT_R1A = 90_941L;
    public static final long TENANT_R1B = 90_942L;
    public static final long TENANT_R2 = 90_943L;

    private static final String COND_UNSAT_RULES =
        "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":"
            + "{\"start\":\"2000-01-01\",\"end\":\"2001-01-01\"}}]}";

    private final JdbcTemplate jdbc;

    public R2BaselineFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 种基线图（幂等：全部显式 id + ON CONFLICT (id) DO NOTHING，重复调用零副作用）。
     * <p>
     * 必须在任何引擎判定之前完成种入——OPERATION_PERMISSIONS_BY_TYPE 缓存按类型首载合并快照，
     * 后补操作会读到陈旧缓存（GoldenFixturePgIT 先例注记）。
     * </p>
     */
    public void seedBaselineGraph() {
        insertType(TYPE_T1, TYPE_T1_CODE);
        insertType(TYPE_T2, TYPE_T2_CODE);
        insertType(TYPE_T3, TYPE_T3_CODE);
        insertOp(OP_T1_VIEW, TYPE_T1, "VIEW", T1_VIEW_BIT, 0L);
        insertOp(OP_T1_UPDATE, TYPE_T1, "UPDATE", T1_UPDATE_BIT, T1_VIEW_BIT);
        insertOp(OP_T1_CREATE, TYPE_T1, "CREATE", T1_CREATE_BIT, 0L);
        insertOp(OP_T1_DELETE, TYPE_T1, "DELETE", T1_DELETE_BIT, T1_VIEW_BIT);
        insertOp(OP_T2_VIEW, TYPE_T2, "VIEW", T1_VIEW_BIT, 0L);
        insertOp(OP_T3_VIEW, TYPE_T3, "VIEW", T1_VIEW_BIT, 0L);

        insertResource(RES_R1, TYPE_T1, CODE_R1, null);
        insertResource(RES_R2, TYPE_T1, CODE_R2, RES_R1);
        insertResource(RES_R3, TYPE_T1, CODE_R3, RES_R1);
        insertResource(RES_R4, TYPE_T1, CODE_R4, null);
        insertResource(RES_S1, TYPE_T2, CODE_S1, null);
        // API 资源实体（code={METHOD}:{path}，bootstrap 固定图口径；测试直插不涉写路径门禁）
        insertResource(RES_API_E1, TYPE_API, "POST:/r2b/inst", null);
        insertResource(RES_API_E2, TYPE_API, "POST:/r2b/all", null);

        insertRole(ROLE_A, "r2b-role-a");
        insertRole(ROLE_B, "r2b-role-b");
        insertRole(ROLE_C, "r2b-role-c");
        insertRole(ROLE_X, "r2b-role-x");
        insertRole(ROLE_Y, "r2b-role-y");
        insertRole(ROLE_S, "r2b-role-s");
        insertRole(ROLE_S2, "r2b-role-s2");

        insertUser(USER_INST, "r2b-u-inst");
        insertUser(USER_ALL, "r2b-u-all");
        insertUser(USER_EMPTY, "r2b-u-empty");
        insertUser(USER_MUTEX, "r2b-u-mutex");
        insertUser(USER_SNAP, "r2b-u-snap");
        insertUser(USER_NONE, "r2b-u-none");
        insertUser(USER_SNAP_ALL, "r2b-u-snap-all");
        bindRole(USER_INST, ROLE_A);
        bindRole(USER_ALL, ROLE_B);
        bindRole(USER_EMPTY, ROLE_C);
        bindRole(USER_MUTEX, ROLE_X);
        bindRole(USER_MUTEX, ROLE_Y);
        bindRole(USER_SNAP, ROLE_S);
        bindRole(USER_SNAP_ALL, ROLE_S2);

        insertPerm(PERM_R1_VIEW, ROLE_A, TYPE_T1, RES_R1, T1_VIEW_BIT, false, null);
        insertPerm(PERM_R1_UPDATE, ROLE_A, TYPE_T1, RES_R1, T1_UPDATE_BIT, false, null);
        insertPerm(PERM_R2_VIEW, ROLE_A, TYPE_T1, RES_R2, T1_VIEW_BIT, false, null);
        insertPerm(PERM_R4_DELETE_COND, ROLE_A, TYPE_T1, RES_R4, T1_DELETE_BIT, false, COND_UNSAT);
        insertPerm(PERM_T2_SCOPE_ALL, ROLE_B, TYPE_T2, null, T1_VIEW_BIT, true, null);
        insertPerm(PERM_T3_SCOPE_ALL_COND, ROLE_C, TYPE_T3, null, T1_VIEW_BIT, true, COND_UNSAT);
        insertPerm(PERM_RX_R2_VIEW, ROLE_X, TYPE_T1, RES_R2, T1_VIEW_BIT, false, null);
        insertPerm(PERM_RY_R2_VIEW, ROLE_Y, TYPE_T1, RES_R2, T1_VIEW_BIT, false, null);
        insertPerm(PERM_API_E1_ACCESS, ROLE_S, TYPE_API, RES_API_E1, API_ACCESS_BIT, false, null);
        insertPerm(PERM_API_SCOPE_ALL, ROLE_S2, TYPE_API, null, API_ACCESS_BIT, true, null);

        jdbc.update(
            "INSERT INTO permission_condition (id, tenant_id, code, name, condition_rules, enabled, "
                + "gateway_evaluable, source, created_at, updated_at) VALUES "
                + "(?, ?, 'r2b-cond-unsat', 'r2b-恒不满足', ?, true, false, 'MANAGED', now(), now()) "
                + "ON CONFLICT (id) DO NOTHING",
            COND_UNSAT, TENANT, COND_UNSAT_RULES);
        jdbc.update(
            "INSERT INTO permission_conflict_rule (id, tenant_id, conflict_type, first_abstract_role_id, "
                + "second_abstract_role_id, description) VALUES (?, ?, 'ROLE_MUTEX', ?, ?, 'r2b-baseline') "
                + "ON CONFLICT (id) DO NOTHING",
            RULE_ROLE_MUTEX_XY, TENANT, ROLE_X, ROLE_Y);

        // 服务配置行（生产契约：resource_api_mapping 挂已声明服务——SERVICE 行唯一通道=管理面手工建行；
        // 引擎快照链当前不读该表，此为事实形态保真补种，沿 DelegatedDirectoryClosurePgIT 先例）
        jdbc.update(
            "INSERT INTO service_config (id, tenant_id, service_code, name, base_path, status, delete_flag) "
                + "VALUES (?, ?, ?, 'r2b-服务A', '/api', 1, 0) ON CONFLICT (id) DO NOTHING",
            SVC_CFG_A, TENANT, SVC_INST);
        jdbc.update(
            "INSERT INTO service_config (id, tenant_id, service_code, name, base_path, status, delete_flag) "
                + "VALUES (?, ?, ?, 'r2b-服务B', '/api', 1, 0) ON CONFLICT (id) DO NOTHING",
            SVC_CFG_B, TENANT, SVC_ALL);

        jdbc.update(
            "INSERT INTO resource_api_mapping (id, tenant_id, resource_entity_id, service_code, http_method, "
                + "path_pattern, match_order, enabled) VALUES (?, ?, ?, ?, 'POST', ?, 0, true) "
                + "ON CONFLICT (id) DO NOTHING",
            MAP_INST, TENANT, RES_API_E1, SVC_INST, "/r2b/inst");
        jdbc.update(
            "INSERT INTO resource_api_mapping (id, tenant_id, resource_entity_id, service_code, http_method, "
                + "path_pattern, match_order, enabled) VALUES (?, ?, ?, ?, 'POST', ?, 0, true) "
                + "ON CONFLICT (id) DO NOTHING",
            MAP_ALL_1, TENANT, RES_API_E2, SVC_ALL, "/r2b/all-1");
        jdbc.update(
            "INSERT INTO resource_api_mapping (id, tenant_id, resource_entity_id, service_code, http_method, "
                + "path_pattern, match_order, enabled) VALUES (?, ?, ?, ?, 'POST', ?, 0, true) "
                + "ON CONFLICT (id) DO NOTHING",
            MAP_ALL_2, TENANT, RES_API_E2, SVC_ALL, "/r2b/all-2");
        jdbc.update(
            "INSERT INTO resource_api_mapping (id, tenant_id, resource_entity_id, service_code, http_method, "
                + "path_pattern, match_order, enabled) VALUES (?, ?, ?, ?, 'POST', ?, 0, false) "
                + "ON CONFLICT (id) DO NOTHING",
            MAP_ALL_DISABLED, TENANT, RES_API_E2, SVC_ALL, "/r2b/all-off");
    }

    // ===== 缺陷反例 builders（RETURNING id，供各反例用例自建隔离事实）=====
    // 带 tenantId 参数的 builder 供 R01/R02 跨租户互斥图使用；其余 builder 固定租户 1。

    /** 新建 resource_type 类型定义（缺陷反例用 951+ 段）。 */
    public void newType(int typeValue, String typeCode) {
        jdbc.update(
            "INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system, sort_order) "
                + "VALUES (?, 'resource_type', ?, ?, ?, false, 99)",
            TENANT, typeCode, typeValue, "r2b-" + typeCode);
    }

    /** 新建操作位（RETURNING id）。 */
    public long insertOperation(int typeValue, String code, long bit, long inheritMask) {
        return jdbc.queryForObject(
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, delete_flag) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0) RETURNING id",
            Long.class, TENANT, typeValue, code, "r2b-" + code, bit, inheritMask);
    }

    /** 新建资源实体（RETURNING id；反例目标均独立无树）。 */
    public long insertResourceRow(int typeValue, String code) {
        return jdbc.queryForObject(
            "INSERT INTO resource_entity (tenant_id, parent_id, resource_type, code, code_type, name, status) "
                + "VALUES (?, NULL, ?, ?, 'default', ?, 1) RETURNING id",
            Long.class, TENANT, typeValue, code, "r2b-" + code);
    }

    /** 新建 BASIC_ROLE（RETURNING id）。 */
    public long insertRoleRow(Long tenantId, String tag) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, extra) "
                + "VALUES (?, 6, ?, ?, 1, '{}') RETURNING id",
            Long.class, tenantId, "r2b-role-" + tag + "-" + System.nanoTime(), "r2b-" + tag);
    }

    /**
     * 新建主体并绑定角色（user_type=1；缺陷反例链路全部按内部 subjectId 直连引擎/领域服务，
     * externalId 仅需满足唯一约束，不参与解析）。
     */
    public long insertUserWithRoles(Long tenantId, String tag, Long... roleIds) {
        String externalId = "r2b-" + tag + "-" + System.nanoTime();
        long userId = jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, 1, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, tenantId, externalId, "r2b-" + tag);
        for (Long roleId : roleIds) {
            jdbc.update(
                "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
                tenantId, userId, roleId);
        }
        return userId;
    }

    /** 新建授权行（RETURNING id；grant_source=MANUAL 须单位）。 */
    public long insertPermRow(Long roleId, int typeValue, Long entityId, long bit, boolean scopeAll, Long conditionId) {
        return jdbc.queryForObject(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, condition_id, grant_source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'MANUAL') RETURNING id",
            Long.class, TENANT, roleId, entityId, bit, typeValue, scopeAll, conditionId);
    }

    /** 新建 PERM_MUTEX 规则（两个操作位端点，RETURNING id）。 */
    public long insertPermMutexRule(long firstOpId, long secondOpId) {
        return jdbc.queryForObject(
            "INSERT INTO permission_conflict_rule (tenant_id, conflict_type, first_operation_permission_id, "
                + "second_operation_permission_id, description) VALUES (?, 'PERM_MUTEX', ?, ?, 'r2b-mx') RETURNING id",
            Long.class, TENANT, firstOpId, secondOpId);
    }

    /** 新建 ROLE_MUTEX 规则（两个角色端点；插入顺序=物理序，R01 顺序敏感性控制点）。 */
    public void insertRoleMutexRule(Long tenantId, long firstRoleId, long secondRoleId) {
        jdbc.update(
            "INSERT INTO permission_conflict_rule (tenant_id, conflict_type, first_abstract_role_id, "
                + "second_abstract_role_id, description) VALUES (?, 'ROLE_MUTEX', ?, ?, 'r2b-mx')",
            tenantId, firstRoleId, secondRoleId);
    }

    // ===== 基线图幂等插入 =====

    private void insertType(int typeValue, String typeCode) {
        jdbc.update(
            "INSERT INTO type_definition (id, tenant_id, type_key, type_code, type_value, name, is_system, sort_order) "
                + "VALUES (?, ?, 'resource_type', ?, ?, ?, false, 99) ON CONFLICT (id) DO NOTHING",
            typeValue, TENANT, typeCode, typeValue, "r2b-" + typeCode);
    }

    private void insertOp(long opId, int typeValue, String code, long bit, long inheritMask) {
        jdbc.update(
            "INSERT INTO operation_permission (id, tenant_id, resource_type, code, name, binary_bit, inherit_mask, delete_flag) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0) ON CONFLICT (id) DO NOTHING",
            opId, TENANT, typeValue, code, "r2b-" + code, bit, inheritMask);
    }

    private void insertResource(long id, int typeValue, String code, Long parentId) {
        jdbc.update(
            "INSERT INTO resource_entity (id, tenant_id, parent_id, resource_type, code, code_type, name, status) "
                + "VALUES (?, ?, ?, ?, ?, 'default', ?, 1) ON CONFLICT (id) DO NOTHING",
            id, TENANT, parentId, typeValue, code, "r2b-" + code);
    }

    private void insertRole(long id, String tag) {
        jdbc.update(
            "INSERT INTO abstract_role (id, tenant_id, role_type, external_id, name, status, extra) "
                + "VALUES (?, ?, 6, ?, ?, 1, '{}') ON CONFLICT (id) DO NOTHING",
            id, TENANT, tag, "r2b-" + tag);
    }

    private void insertUser(long id, String name) {
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, 1, ?, ?, true, '{}', NULL) ON CONFLICT (id) DO NOTHING",
            id, TENANT, String.valueOf(id), name);
    }

    /** 重复种入幂等（uk_user_role 含 COALESCE(relation_id,0) 表达式键，ON CONFLICT 不指定目标覆盖任意唯一冲突）。 */
    private void bindRole(long userId, long roleId) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) "
                + "VALUES (?, ?, 'ROLE', ?) ON CONFLICT DO NOTHING",
            TENANT, userId, roleId);
    }

    private void insertPerm(long id, long roleId, int typeValue, Long entityId, long bit,
                            boolean scopeAll, Long conditionId) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(id, tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, condition_id, grant_source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL') ON CONFLICT (id) DO NOTHING",
            id, TENANT, roleId, entityId, bit, typeValue, scopeAll, conditionId);
    }
}
