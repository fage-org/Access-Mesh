package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.access.permission.service.ConflictRuleAppService;
import cn.ac.fage.accessmesh.access.permission.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-PERM-063 验收：角色互斥授权时校验端到端回归锁（真实 PostgreSQL + Redis）。
 * <p>
 * 三面锁——①授予守卫：user-role/assign、batch-assign 写路径事务内互斥校验，
 * 授予后有效角色集命中互斥对整批原子拒绝 20062 且零落库（旧实现直接落库，本类必红）；
 * ②存量守卫：conflict-rule/create 在 ROLE_MUTEX 分支有用户同时持有两角色时拒绝 20063，
 * 解绑后放行；③detect 角色对预检：conflictedUserIds 回传存量持有清单。
 * 装配策略与 {@code UserRoleWriteProjectionPgIT} 同款：jdbc 直插事实/授权先于首次引擎调用。
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
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class RoleMutexGuardPgIT {

    private static final Long TENANT = 1L;

    /** type_definition 种子：user_type/USER=1；role_type/BASIC_ROLE=6；resource_type：ROLE=5、CONFLICT_RULE=14 */
    private static final int USER_TYPE_EXTERNAL = 1;
    private static final int ROLE_TYPE_BASIC = 6;
    private static final int RESOURCE_TYPE_ROLE = 5;
    private static final int RESOURCE_TYPE_CONFLICT_RULE = 14;
    private static final long VIEW_BIT = 2L;
    private static final long CREATE_BIT = 1L;
    private static final long MANAGE_BIT = 16L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, RoleMutexGuardPgIT.class);
    }

    @Autowired
    private ConflictRuleAppService conflictRuleAppService;
    @Autowired
    private UserManageAppService userManageAppService;
    @Autowired
    private SubjectDomainService subjectDomainService;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("授予守卫：互斥对立规后，assign/batch-assign 给已持对端角色的用户 → 20062 整批拒绝零落库；无冲突用户照常落库")
    void assignShouldRejectWhenPostStateHitsMutexPair() {
        Long operator = insertSubject("t063-op-assign");
        Long operatorRole = insertBasicRole("t063-holder-assign");
        insertUserRole(operator, operatorRole);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_ROLE, MANAGE_BIT);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONFLICT_RULE, CREATE_BIT);
        bindOperator(operator);

        Long roleA = insertBasicRole("t063-role-a");
        Long roleB = insertBasicRole("t063-role-b");
        Long holder = insertSubject("t063-u-holder");
        insertUserRole(holder, roleA);
        Long bystander = insertSubject("t063-u-bystander");

        // 立规时无人双持 → 放行
        conflictRuleAppService.createConflictRule(
            TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, roleA, roleB, null), operator);

        // 已持 A 的用户再授 B：授予后状态命中互斥对 → 20062 整批拒绝（旧实现直接落库）
        assertThatThrownBy(() -> userManageAppService.assignRole(TENANT, new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "t063-u-holder", null, "BASIC_ROLE", "t063-role-b", null, null, null)
        ))))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
        assertThat(countUserRole(holder, roleB)).isZero();

        // 无冲突用户照常落库（守卫不误伤正常授予）
        userManageAppService.assignRole(TENANT, new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "t063-u-bystander", null, "BASIC_ROLE", "t063-role-b", null, null, null)
        )));
        assertThat(countUserRole(bystander, roleB)).isEqualTo(1);

        // batch-assign（单角色×多用户）同款守卫：batch 内含已持 A 的用户 → 20062 整批拒绝；
        // 混合批原子性（评审 P2-3）：批内全新用户 fresh 同批被拒零落库（部分成功形态在此必红）
        Long fresh = insertSubject("t063-u-fresh");
        assertThatThrownBy(() -> userManageAppService.assignRolesBatch(TENANT, new UserRoleBatchAssignReq(
            List.of("t063-u-holder", "t063-u-fresh"), "USER", null, "BASIC_ROLE", "t063-role-b", null)))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
        assertThat(countUserRole(holder, roleB)).isZero();
        assertThat(countUserRole(fresh, roleB)).isZero();
    }

    @Test
    @DisplayName("存量守卫：立规时已有用户双持 → 20063 拒绝且零规则行；解绑后立规放行")
    void createRuleShouldRejectWhenUsersHoldBothRoles() {
        Long operator = insertSubject("t063-op-rule");
        Long operatorRole = insertBasicRole("t063-holder-rule");
        insertUserRole(operator, operatorRole);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONFLICT_RULE, CREATE_BIT);
        bindOperator(operator);

        Long roleC = insertBasicRole("t063-role-c");
        Long roleD = insertBasicRole("t063-role-d");
        Long holder = insertSubject("t063-u-dual");
        insertUserRole(holder, roleC);
        insertUserRole(holder, roleD);

        // 存量双持 → 20063（旧实现直接立规，本断言必红）；message 含冲突用户清单
        assertThatThrownBy(() -> conflictRuleAppService.createConflictRule(
                TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, roleC, roleD, null), operator))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.ROLE_MUTEX_EXISTING_HOLDERS.getCode());
        assertThatThrownBy(() -> conflictRuleAppService.createConflictRule(
                TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, roleC, roleD, null), operator))
            .hasMessageContaining(String.valueOf(holder));
        assertThat(countRule(roleC, roleD)).isZero();

        // 解绑一端 + 失效该用户有效角色缓存（jdbc 直改不经写路径 afterCommit）→ 立规放行
        jdbc.update("DELETE FROM user_role WHERE tenant_id = ? AND abstract_user_id = ? AND target_id = ?",
            TENANT, holder, roleD);
        subjectDomainService.invalidateRoleCacheBatch(TENANT, Set.of(holder));
        ConflictRuleResp created = conflictRuleAppService.createConflictRule(
            TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, roleC, roleD, null), operator);
        assertThat(created.id()).isNotNull();
        assertThat(countRule(roleC, roleD)).isEqualTo(1);
    }

    @Test
    @DisplayName("detect 角色对预检：双持用户回传 conflictedUserIds 且 conflictDetected=true；无双持为 false")
    void detectRolePairShouldReturnExistingHolders() {
        Long operator = insertSubject("t063-op-detect");
        Long operatorRole = insertBasicRole("t063-holder-detect");
        insertUserRole(operator, operatorRole);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONFLICT_RULE, VIEW_BIT);
        bindOperator(operator);

        Long roleE = insertBasicRole("t063-role-e");
        Long roleF = insertBasicRole("t063-role-f");
        Long holder = insertSubject("t063-u-detect");
        insertUserRole(holder, roleE);
        insertUserRole(holder, roleF);

        ConflictDetectResp hit = conflictRuleAppService.detectConflictRule(
            TENANT, new ConflictRuleDetectReq(null, null, null, roleE, roleF));
        assertThat(hit.conflictDetected()).isTrue();
        assertThat(hit.conflictedUserIds()).containsExactly(holder);

        Long roleG = insertBasicRole("t063-role-g");
        ConflictDetectResp miss = conflictRuleAppService.detectConflictRule(
            TENANT, new ConflictRuleDetectReq(null, null, null, roleE, roleG));
        assertThat(miss.conflictDetected()).isFalse();
        assertThat(miss.conflictedUserIds()).isEmpty();
    }

    @Test
    @DisplayName("存量守卫覆盖组角色间接持有：经 GROUP_ROLE 展开持有对端角色同样计双持（评审 P1-1，直授行候选的旧实现必红）")
    void createRuleShouldRejectWhenHolderViaGroupRoleExpansion() {
        Long operator = insertSubject("t063-op-group");
        Long operatorRole = insertBasicRole("t063-holder-group");
        insertUserRole(operator, operatorRole);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONFLICT_RULE, CREATE_BIT);
        bindOperator(operator);

        Long roleH = insertBasicRole("t063-role-h");
        Long roleI = insertBasicRole("t063-role-i");
        // 组角色 G（写入口已随 T-PERM-043 删除，读模型冻结保留）挂组内基础角色 H
        Long groupId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, 5, "t063-group", "组角色");
        jdbc.update("UPDATE abstract_role SET parent_id = ? WHERE id = ? AND tenant_id = ?", groupId, roleH, TENANT);

        // 用户经 GROUP_ROLE 绑定 G（展开得 H）+ 直授 I：有效角色集 = {H, I}，无 (H,I) 直授行
        Long holder = insertSubject("t063-u-group");
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'GROUP_ROLE', ?)",
            TENANT, holder, groupId);
        insertUserRole(holder, roleI);

        // 对 (H, I) 立规 → 20063：候选必须经按角色反查（含组路径）才会包含该用户
        assertThatThrownBy(() -> conflictRuleAppService.createConflictRule(
                TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, roleH, roleI, null), operator))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.ROLE_MUTEX_EXISTING_HOLDERS.getCode());
        assertThat(countRule(roleH, roleI)).isZero();
    }

    // ===== 数据装配（jdbc 直插事实/授权，先于相关主体首次引擎调用） =====

    private void bindOperator(Long operatorId) {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorId));
    }

    private Long insertSubject(String externalId) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, USER_TYPE_EXTERNAL, externalId, externalId);
    }

    private Long insertBasicRole(String externalId) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, externalId, externalId);
    }

    private void insertUserRole(Long abstractUserId, Long targetRoleId) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, abstractUserId, targetRoleId);
    }

    private void insertScopeAllRolePerm(Long roleId, int resourceType, long grantedBits) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL')",
            TENANT, roleId, grantedBits, resourceType);
    }

    private long countUserRole(Long abstractUserId, Long targetRoleId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE tenant_id = ? AND abstract_user_id = ? AND target_id = ? "
                + "AND target_type = 'ROLE' AND delete_flag = 0",
            Long.class, TENANT, abstractUserId, targetRoleId);
        return count == null ? 0 : count;
    }

    private long countRule(Long firstRoleId, Long secondRoleId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permission_conflict_rule WHERE tenant_id = ? AND conflict_type = 'ROLE_MUTEX' "
                + "AND first_abstract_role_id = ? AND second_abstract_role_id = ? AND delete_flag = 0",
            Long.class, TENANT, Math.min(firstRoleId, secondRoleId), Math.max(firstRoleId, secondRoleId));
        return count == null ? 0 : count;
    }
}
