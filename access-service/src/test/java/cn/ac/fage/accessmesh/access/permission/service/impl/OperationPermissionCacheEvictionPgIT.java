package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.OperationAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-PERM-047 行为回归锁（真实 PostgreSQL + Redis + 真实 bean 链路）：
 * OPERATION_PERMISSIONS_BY_TYPE（L1 60m/L2 120m）在 create/update/deleteOperations
 * 写路径提交后失效——引擎（位掩码覆盖判定）的判定随写操作即时翻转。
 * <p>
 * 旧实现（写路径无 evict，靠 TTL 兜底）下三个场景的「写后判定」断言均失败：
 * 覆盖掩码由缓存的 per-type 全量操作 Map 计算（effectiveBits = binaryBit|inheritMask），
 * 陈旧 Map 让引擎按旧位值/已删操作/缺新增操作判定。写走真实 OperationAppService bean
 * （@Transactional + afterCommit 失效真实生效）；判定走真实 PermQueryEngine（scopeAll
 * 类型级授权 + computeCoveringBitMask）。三场景各用独立资源类型 + 独立位段 + 独立主体，
 * 互不共享缓存键与 EFFECTIVE_ROLES 键。
 * </p>
 * <p>
 * Docker 不可用时由 Testcontainers 自动跳过（容器轨道，testing-standards §10）。
 * </p>
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class OperationPermissionCacheEvictionPgIT {

    private static final Long TENANT = 1L;

    private static final int USER_TYPE_LOCAL = 3;
    private static final int ROLE_TYPE_BASIC = 6;
    /** resource_type 种子值：DATA=4 / RESOURCE=7 / SERVICE=8 / OPERATION=12 */
    private static final String TYPE_DATA = "DATA";
    private static final String TYPE_RESOURCE = "RESOURCE";
    private static final String TYPE_SERVICE = "SERVICE";
    /** OPERATION(12) 种子操作位：CREATE=1、MANAGE=16（操作者门禁；MANUAL 授权行单操作位，两行各一） */
    private static final int TYPE_OPERATION_VALUE = 12;
    private static final long OPERATION_CREATE_BIT = 1L;
    private static final long OPERATION_MANAGE_BIT = 16L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, OperationPermissionCacheEvictionPgIT.class);
    }

    @Autowired
    private OperationAppService operationAppService;
    @Autowired
    private PermQueryEngine permQueryEngine;
    @Autowired
    private OperationPermissionMapper operationPermissionMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDateTime now = LocalDateTime.now();

    // ===== 种子 helper（AuthorizationChangeInvalidationPgIT 同款） =====

    private Long insertUser(Long id, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, id, TENANT, USER_TYPE_LOCAL, String.valueOf(id), name);
    }

    private Long insertRole(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, extra) "
                + "VALUES (?, ?, ?, ?, 1, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, externalId, name);
    }

    private void insertUserRole(Long userId, Long roleId) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, userId, roleId);
    }

    private void insertRolePerm(Long roleId, int resourceType, long grantedBits) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL')",
            TENANT, roleId, grantedBits, resourceType);
    }

    private OperationPermission insertOperation(int resourceType, String code, long bit, long mask) {
        OperationPermission op = new OperationPermission();
        op.setTenantId(TENANT);
        op.setResourceType(resourceType);
        op.setCode(code);
        op.setName(code);
        op.setBinaryBit(bit);
        op.setInheritMask(mask);
        op.setCreatedAt(now);
        op.setUpdatedAt(now);
        op.setDeleteFlag(0L);
        operationPermissionMapper.insert(op);
        return op;
    }

    /** 操作者（OPERATION:CREATE + OPERATION:MANAGE 类型级门禁）+ 受判定主体（scopeAll 授权） */
    private Long[] seedOperatorAndSubject(long operatorUserId, long subjectUserId, int subjectGrantType, long subjectBits) {
        Long operatorRole = insertRole("pgit47-op-role-" + operatorUserId, "T-PERM-047 操作者角色");
        insertUserRole(insertUser(operatorUserId, "T-PERM-047 操作者"), operatorRole);
        insertRolePerm(operatorRole, TYPE_OPERATION_VALUE, OPERATION_CREATE_BIT);
        insertRolePerm(operatorRole, TYPE_OPERATION_VALUE, OPERATION_MANAGE_BIT);

        Long subjectRole = insertRole("pgit47-sub-role-" + subjectUserId, "T-PERM-047 受判定角色");
        insertUserRole(insertUser(subjectUserId, "T-PERM-047 受判定主体"), subjectRole);
        insertRolePerm(subjectRole, subjectGrantType, subjectBits);
        return new Long[] {operatorUserId, subjectUserId};
    }

    @Test
    @DisplayName("update 位值变更即时生效：继承掩码撤销后引擎判定翻转（旧实现 TTL 窗口内仍按旧掩码放行）")
    void updateInheritMaskShouldFlipEngineJudgmentImmediately() {
        // DATA(4)：MAIN_A(128) 可被 HELPER_A(64, inherit 128) 覆盖——主体只授 HELPER_A 位
        insertOperation(4, "PGIT47_A_MAIN", 128L, 0L);
        insertOperation(4, "PGIT47_A_HELPER", 64L, 128L);
        Long[] ids = seedOperatorAndSubject(940001L, 940002L, 4, 64L);

        // 预热：缓存 op_perm:4（含 HELPER_A 的继承掩码 128），覆盖掩码 128|64=192 命中授位 64 → 放行
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, ids[1], TYPE_DATA, null, "PGIT47_A_MAIN")).isTrue();

        // 真实写路径：HELPER_A 继承掩码 128 → 0（不再覆盖 MAIN_A）
        AccessRequestContext.bind(RequestContext.user(TENANT, ids[0]));
        try {
            operationAppService.updateOperation(TENANT,
                new OperationUpdateReq(TYPE_DATA, "PGIT47_A_HELPER", "撤销继承", 64L, 0L), ids[0]);
        } finally {
            AccessRequestContext.clear();
        }

        // 提交后失效即时生效：覆盖掩码回到 128，授位 64 不再命中 → 拒绝。
        // 旧实现（无 evict）下陈旧 Map 仍算出 192 → 放行 → 断言失败
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, ids[1], TYPE_DATA, null, "PGIT47_A_MAIN")).isFalse();
    }

    @Test
    @DisplayName("delete 已删操作即时退出判定：删除覆盖操作后引擎判定翻转（旧实现 TTL 窗口内仍按已删操作放行）")
    void deleteCoveringOperationShouldFlipEngineJudgmentImmediately() {
        // RESOURCE(7)：MAIN_B(1024) 被 HELPER_B(2048, inherit 1024) 覆盖——主体只授 HELPER_B 位
        insertOperation(7, "PGIT47_B_MAIN", 1024L, 0L);
        insertOperation(7, "PGIT47_B_HELPER", 2048L, 1024L);
        Long[] ids = seedOperatorAndSubject(940011L, 940012L, 7, 2048L);

        // 预热：缓存 op_perm:7，覆盖掩码 1024|2048 命中授位 2048 → 放行
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, ids[1], TYPE_RESOURCE, null, "PGIT47_B_MAIN")).isTrue();

        // 真实写路径：软删 HELPER_B
        AccessRequestContext.bind(RequestContext.user(TENANT, ids[0]));
        try {
            operationAppService.deleteOperations(TENANT,
                List.of(new OperationKeyReq(TYPE_RESOURCE, "PGIT47_B_HELPER")), ids[0]);
        } finally {
            AccessRequestContext.clear();
        }

        // 提交后失效即时生效：陈旧 Map 含已删行才会继续放行——旧实现（无 evict）下断言失败
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, ids[1], TYPE_RESOURCE, null, "PGIT47_B_MAIN")).isFalse();
    }

    @Test
    @DisplayName("create 新增覆盖操作即时生效：新操作位参与覆盖判定（旧实现 TTL 窗口内缓存缺行仍拒绝）")
    void createCoveringOperationShouldFlipEngineJudgmentImmediately() {
        // SERVICE(8)：仅 MAIN_C(4096)，主体授 HELPER_C 位(8192)——无覆盖操作时不放行
        insertOperation(8, "PGIT47_C_MAIN", 4096L, 0L);
        Long[] ids = seedOperatorAndSubject(940021L, 940022L, 8, 8192L);

        // 预热：缓存 op_perm:8（缺 HELPER_C），覆盖掩码 4096 不含授位 → 拒绝
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, ids[1], TYPE_SERVICE, null, "PGIT47_C_MAIN")).isFalse();

        // 真实写路径：新增 HELPER_C(8192, inherit 4096)——覆盖 MAIN_C
        AccessRequestContext.bind(RequestContext.user(TENANT, ids[0]));
        try {
            operationAppService.createOperation(TENANT, TYPE_SERVICE, "PGIT47_C_HELPER", "新增覆盖操作", 8192L, 4096L, ids[0]);
        } finally {
            AccessRequestContext.clear();
        }

        // 提交后失效即时生效：覆盖掩码 4096|8192 命中授位 8192 → 放行。
        // 旧实现（无 evict）下陈旧 Map 缺 HELPER_C → 仍拒绝 → 断言失败
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, ids[1], TYPE_SERVICE, null, "PGIT47_C_MAIN")).isTrue();
    }
}
