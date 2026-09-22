package cn.ac.fage.accessmesh.access.role.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.rule.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.access.rule.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.access.role.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.access.rule.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.access.rule.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.access.rule.service.ConflictRuleAppService;
import cn.ac.fage.accessmesh.access.user.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
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
 * 三面锁（+T-PERM-075 共同判定语义端到端）——①授予守卫：user-role/assign、batch-assign
 * 写路径事务内互斥校验，未过期原始持有窗口与新增窗口区间交命中互斥对整批原子拒绝 20062
 * 且零落库（旧实现直接落库，本类必红）；②存量守卫：conflict-rule/create 在 ROLE_MUTEX
 * 分支有用户持有窗口重叠时拒绝 20063，解绑后放行；③detect 角色对预检：conflictedUserIds
 * 回传存量持有清单；④判定入口一致：check/菜单对双持用户一致双删、撤销/规则软删恢复、
 * 禁用再启用触发双删（T-PERM-075）。
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
    private cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine permQueryEngine;
    @Autowired
    private cn.ac.fage.accessmesh.access.engine.service.PermissionQueryAppService permissionQueryAppService;
    @Autowired
    private cn.ac.fage.accessmesh.access.engine.service.PermissionViewAppService permissionViewAppService;
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
            .isEqualTo(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
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
            .isEqualTo(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
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
            .isEqualTo(AccessErrorCode.ROLE_MUTEX_EXISTING_HOLDERS.getCode());
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
            .isEqualTo(AccessErrorCode.ROLE_MUTEX_EXISTING_HOLDERS.getCode());
        assertThat(countRule(roleH, roleI)).isZero();
    }

    @Test
    @DisplayName("T-PERM-064：结构角色对立规拒绝——ORG 角色对 VALIDATION_FAILED（投影通道闭合），功能角色对放行")
    void createRuleShouldRejectStructuralRolePair() {
        Long operator = insertSubject("t064-op-structural");
        Long operatorRole = insertBasicRole("t064-holder-structural");
        insertUserRole(operator, operatorRole);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONFLICT_RULE, CREATE_BIT);
        bindOperator(operator);

        // role_type 种子：ORG=1、POSITION=2；抽象角色行经 jdbc 直插（结构角色无管理面写入口）
        Long orgRoleA = insertRoleOfType("t064-org-role-a", 1);
        Long orgRoleB = insertRoleOfType("t064-org-role-b", 1);

        assertThatThrownBy(() -> conflictRuleAppService.createConflictRule(
                TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, orgRoleA, orgRoleB, null), operator))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AccessErrorCode.VALIDATION_FAILED.getCode());
        assertThat(countRule(orgRoleA, orgRoleB)).isZero();

        // 功能角色对（BASIC_ROLE=6）不受影响
        Long basicA = insertBasicRole("t064-basic-a");
        Long basicB = insertBasicRole("t064-basic-b");
        ConflictRuleResp created = conflictRuleAppService.createConflictRule(
            TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, basicA, basicB, null), operator);
        assertThat(created.id()).isNotNull();
    }

    // ===== T-PERM-075：U002 写时候选扩展 + 共同判定语义端到端 =====

    /** U002-1：未来 valid_from 窗口与既有持有重叠 → 写时 20062 拒绝；真正不相交的未来窗口放行。 */
    @Test
    @DisplayName("U002-1 未来重叠：assign 未来生效 B 与持有 A 重叠 → 20062；不相交未来窗口放行（旧实现均放行，本用例必红）")
    void assignShouldRejectFutureWindowOverlap() {
        Long operator = insertSubject("t075-op-future");
        Long operatorRole = insertBasicRole("t075-holder-future");
        insertUserRole(operator, operatorRole);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_ROLE, MANAGE_BIT);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONFLICT_RULE, CREATE_BIT);
        bindOperator(operator);

        Long roleA = insertBasicRole("t075-fa");
        Long roleB = insertBasicRole("t075-fb");
        Long holder = insertSubject("t075-u-future");
        // A 有限期窗口 [-2d, +2d]：与 [+10d, +11d] 不相交、与 [明天, ∞) 重叠
        insertUserRoleWithValidity(holder, roleA, java.time.LocalDateTime.now().minusDays(2),
            java.time.LocalDateTime.now().plusDays(2));

        conflictRuleAppService.createConflictRule(
            TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, roleA, roleB, null), operator);

        // 重叠未来窗口（明天起无限期）→ 20062（旧口径「未来 validFrom 不进候选」放行落库，必红）
        assertThatThrownBy(() -> userManageAppService.assignRole(TENANT, new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "t075-u-future", null, "BASIC_ROLE", "t075-fb", null,
                java.time.LocalDateTime.now().plusDays(1), null)
        ))))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
        assertThat(countUserRole(holder, roleB)).isZero();

        // 不相交未来窗口 → 放行（守卫不误伤合法的错峰安排）
        userManageAppService.assignRole(TENANT, new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "t075-u-future", null, "BASIC_ROLE", "t075-fb", null,
                java.time.LocalDateTime.now().plusDays(10), java.time.LocalDateTime.now().plusDays(11))
        )));
        assertThat(countUserRole(holder, roleB)).isEqualTo(1);
    }

    /** U002-2：禁用通道双向堵死——绑禁用的互斥目标拒绝；持有禁用对端再绑启用角色同样拒绝。 */
    @Test
    @DisplayName("U002-2 禁用通道：绑定禁用 B（持 A）与持有禁用 B 再绑 A → 均 20062（旧实现均放行，本用例必红）")
    void assignShouldRejectDisabledTargetAndDisabledHolding() {
        Long operator = insertSubject("t075-op-dis");
        Long operatorRole = insertBasicRole("t075-holder-dis");
        insertUserRole(operator, operatorRole);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_ROLE, MANAGE_BIT);
        insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONFLICT_RULE, CREATE_BIT);
        bindOperator(operator);

        Long roleA = insertBasicRole("t075-da");
        Long roleB = insertDisabledBasicRole("t075-db");
        Long holder = insertSubject("t075-u-dis1");
        insertUserRole(holder, roleA);

        conflictRuleAppService.createConflictRule(
            TENANT, new ConflictRuleReq("ROLE_MUTEX", null, null, null, roleA, roleB, null), operator);

        // 正向：给持 A 的用户绑禁用的 B → 20062（旧口径「禁用目标收敛剔除」放行，必红）
        assertThatThrownBy(() -> userManageAppService.assignRole(TENANT, new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "t075-u-dis1", null, "BASIC_ROLE", "t075-db", null, null, null)
        ))))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());

        // 反向：持有禁用 B 的用户再绑启用的 A → 20062（旧口径「有效角色集看不到禁用持有」放行，必红）
        Long holder2 = insertSubject("t075-u-dis2");
        insertUserRole(holder2, roleB);
        assertThatThrownBy(() -> userManageAppService.assignRole(TENANT, new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "t075-u-dis2", null, "BASIC_ROLE", "t075-da", null, null, null)
        ))))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
        assertThat(countUserRole(holder2, roleA)).isZero();
    }

    /**
     * 共同判定语义（F004 主验收）：双持用户在引擎 check 与菜单/权限串视图一致双删；
     * 解绑一端（撤销失效）恢复；重新双持再拒；规则软删后恢复（模拟 TTL 过期）。
     */
    @Test
    @DisplayName("判定入口一致：双持用户 check/菜单一致双删；解绑恢复；规则软删恢复")
    void judgementEntriesShouldAgreeOnMutexDualHolding() {
        Long roleA = insertBasicRole("t075-ja");
        Long roleB = insertBasicRole("t075-jb");
        insertScopeAllRolePerm(roleA, RESOURCE_TYPE_ROLE, VIEW_BIT);
        Long holder = insertSubject("t075-u-judge");
        insertUserRole(holder, roleA);

        // 立规时无双持 → 放行；随后 jdbc 直插构造存量双持（绕过写守卫的通道只在测试存在）
        conflictRuleAppServiceInsertRuleQuietly(roleA, roleB);
        insertUserRole(holder, roleB);

        // 双持 → 引擎 check 双删（旧实现不过滤，必红）
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "ROLE", null, "VIEW")).isFalse();
        // 菜单/权限串视图同口径双删（旧实现不过滤，必红）
        bindOperator(holder); // 自查豁免 USER:VIEW
        assertThat(permissionViewAppService.getEffectivePermissionCodesForManage(TENANT,
            new cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq(
                "USER", "t075-u-judge", List.of("ROLE")))
        ).satisfiesAnyOf(
            resp -> assertThat(resp).isNull(),
            resp -> assertThat(resp.permissions()).doesNotContain("ROLE:VIEW"));

        // 撤销失效：解绑一端 + 失效缓存（jdbc 直改不经写路径 afterCommit）→ 判定恢复
        jdbc.update("DELETE FROM user_role WHERE tenant_id = ? AND abstract_user_id = ? AND target_id = ?",
            TENANT, holder, roleB);
        subjectDomainService.invalidateRoleCacheBatch(TENANT, Set.of(holder));
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "ROLE", null, "VIEW")).isTrue();

        // 重新双持（jdbc）→ 再次双删
        insertUserRole(holder, roleB);
        subjectDomainService.invalidateRoleCacheBatch(TENANT, Set.of(holder));
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "ROLE", null, "VIEW")).isFalse();

        // 规则软删 + 清 ROLE_MUTEX_RULE 缓存（模拟 10s TTL 过期后的形态）→ 判定恢复
        jdbc.update("UPDATE permission_conflict_rule SET delete_flag = id, deleted_at = NOW() "
            + "WHERE tenant_id = ? AND conflict_type = 'ROLE_MUTEX' AND delete_flag = 0 "
            + "AND first_abstract_role_id = ? AND second_abstract_role_id = ?",
            TENANT, Math.min(roleA, roleB), Math.max(roleA, roleB));
        cacheServiceEvictMutexRules();
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "ROLE", null, "VIEW")).isTrue();
    }

    /** 禁用后绑定再启用：启用瞬间（缓存失效后）判定双删——运行时兜底残余通道。 */
    @Test
    @DisplayName("禁用再启用：禁用持有期间判定正常，启用后（markRoles 失效）双删")
    void roleEnableShouldTriggerDualDrop() {
        Long roleA = insertBasicRole("t075-ea");
        Long roleB = insertDisabledBasicRole("t075-eb");
        insertScopeAllRolePerm(roleA, RESOURCE_TYPE_ROLE, VIEW_BIT);
        Long holder = insertSubject("t075-u-enable");
        insertUserRole(holder, roleA);

        conflictRuleAppServiceInsertRuleQuietly(roleA, roleB);
        // 禁用持有（jdbc 直插）：有效角色集过滤禁用 → 不双删
        insertUserRole(holder, roleB);
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "ROLE", null, "VIEW")).isTrue();

        // 启用 B + 模拟 markRoles afterCommit 失效（jdbc 直改不经写路径）→ 双删生效
        jdbc.update("UPDATE abstract_role SET status = 1 WHERE tenant_id = ? AND id = ?", TENANT, roleB);
        subjectDomainService.invalidateRoleCacheByRoles(TENANT, Set.of(roleB));
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "ROLE", null, "VIEW")).isFalse();
    }

    /** 无门禁直插规则（立规守卫会被本用例构造的双持拦住，绕经 jdbc 构造既有规则形态）。
     *  同步清 ROLE_MUTEX_RULE 缓存：类内共享租户库下前序用例已装载旧规则集（10s TTL 窗口），
     *  jdbc 直插不经写路径，不清理则本用例判定面读到不含新对的缓存。 */
    private void conflictRuleAppServiceInsertRuleQuietly(Long roleA, Long roleB) {
        jdbc.update(
            "INSERT INTO permission_conflict_rule (tenant_id, conflict_type, first_abstract_role_id, second_abstract_role_id) "
                + "VALUES (?, 'ROLE_MUTEX', ?, ?)",
            TENANT, Math.min(roleA, roleB), Math.max(roleA, roleB));
        cacheServiceEvictMutexRules();
    }

    @Autowired
    private cn.ac.fage.accessmesh.common.cache.CacheService cacheServiceRef;

    /** 清 ROLE_MUTEX_RULE 缓存模拟 TTL 过期（真实失效链=TTL，无主动 evict——见设计口径）。 */
    private void cacheServiceEvictMutexRules() {
        cacheServiceRef.evict(cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog.ROLE_MUTEX_RULE,
            TENANT, "all");
    }

    private void insertUserRoleWithValidity(Long abstractUserId, Long targetRoleId,
                                            java.time.LocalDateTime validFrom, java.time.LocalDateTime validTo) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id, valid_from, valid_to) "
                + "VALUES (?, ?, 'ROLE', ?, ?, ?)",
            TENANT, abstractUserId, targetRoleId, validFrom, validTo);
    }

    private Long insertDisabledBasicRole(String externalId) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, 0, NULL, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, externalId, externalId);
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

    /** T-PERM-064：按 role_type 值直插结构角色行（ORG=1/POSITION=2，无管理面写入口）。 */
    private Long insertRoleOfType(String externalId, int roleTypeValue) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, roleTypeValue, externalId, externalId);
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
