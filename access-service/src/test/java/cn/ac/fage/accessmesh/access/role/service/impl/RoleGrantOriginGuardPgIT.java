package cn.ac.fage.accessmesh.access.role.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.role.service.RoleManageAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-PERM-099 验收：deleteRoles 类型所有者引用守卫端到端回归锁（真实 PostgreSQL + Redis）。
 * <p>
 * 拍板（2026-10-03）：硬守卫整批拒绝 + 覆盖缺省引用。锁五面——①显式指针引用：删除被
 * {@code type_definition.extra.grantOriginRole} 指向的角色（含同批普通角色）→ 20073 整批
 * 回滚，角色行与 AUTHORITY_ROOT 种子行均不被动（旧实现删除成功且回收种子——本类用例
 * ①③④在旧实现下必红：资格基座被回收即「删除所有者→该类型首授/转授资格检查无人通过」）；
 * ②缺省引用：预置 is_system 类型 extra 无指针，运行时按 BASIC_ROLE/bootstrap-admin 缺省
 * 解析，删 bootstrap-admin 同样拒绝；③级联面：判定集=含级联子孙的最终删除集，删父级联
 * 带出所有者子孙同样整批拒绝；④恢复闭环：指针迁移到新所有者后旧所有者可删（恢复通道=
 * type-definition/update 迁移所有者，契约 §13.1）；⑤悬挂指针（合法 JSON 坏指针结构——
 * jsonb 列已挡非法 JSON）行不匹配任何角色、不拦无关删除（修复通道=updateType 覆盖合法 extra）。
 * 装配策略与 {@code RoleMutexGuardPgIT} 同款：jdbc 直插事实/授权先于首次引擎调用。
 * Docker 不可用时由 Testcontainers 自动跳过。
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
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class RoleGrantOriginGuardPgIT {

    private static final Long TENANT = 1L;

    /** type_definition 种子：role_type/BASIC_ROLE=6；resource_type：ROLE=5 */
    private static final int ROLE_TYPE_BASIC = 6;
    private static final int RESOURCE_TYPE_ROLE = 5;
    private static final long MANAGE_BIT = 16L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, RoleGrantOriginGuardPgIT.class);
    }

    @Autowired
    private RoleManageAppService roleManageAppService;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("①显式指针引用：批量删除含所有者角色 → 20073 整批拒绝，同批普通角色与 AUTHORITY_ROOT 种子行均不被动")
    void deleteRolesOfReferencedOwnerShouldRejectEntirely() {
        Long operator = prepareOperator("t099-op-a");
        Long ownerRole = insertBasicRole("t099-pm-admin");
        Long normalRole = insertBasicRole("t099-normal-a");
        int typeValue = insertResourceTypeWithOwner("T099_PROJ_A", "t099-pm-admin");
        insertAuthorityRoot(ownerRole, typeValue, 2L);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
            () -> roleManageAppService.deleteRoles(TENANT, List.of(ownerRole, normalRole), operator));
        // 20073 + message 指引恢复通道并携带命中角色与引用类型
        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(20073);
        assertThat(thrown.getMessage()).contains("t099-pm-admin").contains("T099_PROJ_A");
        // 整批拒绝：同批普通角色也未被软删；所有者角色与其授权根种子行原样保留
        assertThat(liveRoleCount("t099-pm-admin")).isEqualTo(1);
        assertThat(liveRoleCount("t099-normal-a")).isEqualTo(1);
        assertThat(liveAuthorityRootCount(ownerRole, typeValue)).isEqualTo(1);
    }

    @Test
    @DisplayName("②缺省引用：预置类型 extra 无指针（运行时缺省所有者=bootstrap-admin），删 bootstrap-admin → 20073")
    void deleteBootstrapAdminShouldRejectViaDefaultPointer() {
        Long operator = prepareOperator("t099-op-b");
        // 前提：种子 resource_type 行 extra 无指针（缺省解析面存在；-> 返回 SQL NULL 即键不存在）
        Long noPointerTypes = jdbc.queryForObject(
            "SELECT COUNT(*) FROM type_definition WHERE tenant_id = ? AND type_key = 'resource_type' "
                + "AND delete_flag = 0 AND (extra IS NULL OR extra -> 'grantOriginRole' IS NULL)",
            Long.class, TENANT);
        assertThat(noPointerTypes).as("预置 resource_type 必须存在无指针行（缺省引用面前提）").isPositive();
        Long bootstrapAdmin = insertBasicRole("bootstrap-admin");

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
            () -> roleManageAppService.deleteRoles(TENANT, List.of(bootstrapAdmin), operator));
        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(20073);
        assertThat(thrown.getMessage()).contains("bootstrap-admin");
        assertThat(liveRoleCount("bootstrap-admin")).isEqualTo(1);
    }

    @Test
    @DisplayName("③级联面：直接目标无引用但级联子孙是所有者 → 20073 整批拒绝，父子角色均保留")
    void cascadeDeleteShouldRejectWhenDescendantIsOwner() {
        Long operator = prepareOperator("t099-op-c");
        Long parent = insertBasicRole("t099-parent");
        Long childOwner = insertBasicRole("t099-child-owner");
        jdbc.update("UPDATE abstract_role SET parent_id = ? WHERE tenant_id = ? AND id = ?", parent, TENANT, childOwner);
        insertResourceTypeWithOwner("T099_PROJ_C", "t099-child-owner");

        assertThatThrownBy(() -> roleManageAppService.deleteRoles(TENANT, List.of(parent), operator))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(20073);
        assertThat(liveRoleCount("t099-parent")).isEqualTo(1);
        assertThat(liveRoleCount("t099-child-owner")).isEqualTo(1);
    }

    @Test
    @DisplayName("④恢复闭环：指针迁移到新所有者后，旧所有者可删（恢复通道=type-definition/update 迁移所有者）")
    void migrateOwnerThenDeleteShouldSucceed() {
        Long operator = prepareOperator("t099-op-d");
        Long oldOwner = insertBasicRole("t099-old-owner");
        insertBasicRole("t099-new-owner");
        int typeValue = insertResourceTypeWithOwner("T099_PROJ_D", "t099-old-owner");
        // 模拟 updateType 迁移结果（迁移本身的先清后种正确性由 CustomResourceTypeSlicePgIT 锁）
        jdbc.update(
            "UPDATE type_definition SET extra = ?::jsonb WHERE tenant_id = ? AND type_key = 'resource_type' "
                + "AND type_code = 'T099_PROJ_D' AND delete_flag = 0",
            "{\"grantOriginRole\":{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"t099-new-owner\"}}", TENANT);

        roleManageAppService.deleteRoles(TENANT, List.of(oldOwner), operator);

        assertThat(liveRoleCount("t099-old-owner")).isEqualTo(0);
        assertThat(liveRoleCount("t099-new-owner")).isEqualTo(1);
        assertThat(typeValue).isPositive();
    }

    @Test
    @DisplayName("⑤悬挂指针：坏结构 extra 行（jsonb 列已挡非法 JSON，现实悬挂=合法 JSON 坏指针）不匹配任何角色，不拦无关删除")
    void danglingPointerRowShouldNotBlockUnrelatedDelete() {
        Long operator = prepareOperator("t099-op-e");
        insertResourceTypeWithExtra("T099_BROKEN_A", "{\"grantOriginRole\":\"not-an-object\"}");
        insertResourceTypeWithExtra("T099_BROKEN_B", "{\"grantOriginRole\":null}");
        Long normalRole = insertBasicRole("t099-normal-e");
        Long innocentOwner = insertBasicRole("t099-innocent-owner");
        insertResourceTypeWithExtra("T099_PROJ_E",
            "{\"grantOriginRole\":{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"t099-innocent-owner\"}}");

        // 无引用普通角色照常删除（守卫不误伤；同时证明坏指针行未把任何角色误判为被引用）
        roleManageAppService.deleteRoles(TENANT, List.of(normalRole), operator);
        assertThat(liveRoleCount("t099-normal-e")).isEqualTo(0);
        // 好指针指向的角色不受本删除影响（该角色确被引用，本就不在删除集内）
        assertThat(liveRoleCount("t099-innocent-owner")).isEqualTo(1);
    }

    // ===== 数据装配（jdbc 直插事实/授权，先于相关主体首次引擎调用） =====

    private void bindOperator(Long operatorId) {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorId));
    }

    private Long prepareOperator(String externalId) {
        Long operator = jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, 1, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, externalId, externalId);
        Long operatorRole = insertBasicRole(externalId + "-role");
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, operator, operatorRole);
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL')",
            TENANT, operatorRole, MANAGE_BIT, RESOURCE_TYPE_ROLE);
        bindOperator(operator);
        return operator;
    }

    private Long insertBasicRole(String externalId) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, externalId, externalId);
    }

    /** 直插自定义 resource_type（type_value=现值 max+1）并显式指向所有者角色，返回 type_value。 */
    private int insertResourceTypeWithOwner(String typeCode, String ownerExternalId) {
        return insertResourceTypeWithExtra(typeCode,
            "{\"grantOriginRole\":{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"" + ownerExternalId + "\"}}");
    }

    private int insertResourceTypeWithExtra(String typeCode, String extraJson) {
        Integer typeValue = jdbc.queryForObject(
            "SELECT COALESCE(MAX(type_value), 0) + 1 FROM type_definition "
                + "WHERE tenant_id = ? AND type_key = 'resource_type'",
            Integer.class, TENANT);
        jdbc.update(
            "INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system, extra) "
                + "VALUES (?, 'resource_type', ?, ?, ?, false, ?::jsonb)",
            TENANT, typeCode, typeValue, typeCode, extraJson);
        return typeValue;
    }

    /** 直插 AUTHORITY_ROOT 种子行（scopeAll+canGrant+单 bit 无实例，DDL CHECK 形状）。 */
    private void insertAuthorityRoot(Long ownerRoleId, int typeValue, long bit) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, can_grant, grant_source) "
                + "VALUES (?, ?, NULL, ?, ?, true, true, 'AUTHORITY_ROOT')",
            TENANT, ownerRoleId, bit, typeValue);
    }

    private long liveRoleCount(String externalId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM abstract_role WHERE tenant_id = ? AND external_id = ? AND delete_flag = 0",
            Long.class, TENANT, externalId);
        return count == null ? 0 : count;
    }

    private long liveAuthorityRootCount(Long ownerRoleId, int typeValue) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_resource_permission WHERE tenant_id = ? AND abstract_role_id = ? "
                + "AND resource_type = ? AND grant_source = 'AUTHORITY_ROOT' AND delete_flag = 0",
            Long.class, TENANT, ownerRoleId, typeValue);
        return count == null ? 0 : count;
    }
}
