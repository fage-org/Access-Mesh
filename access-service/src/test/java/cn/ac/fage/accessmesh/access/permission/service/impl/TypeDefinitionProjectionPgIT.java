package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * T-PERM-051 验收：TYPE_DEFINITION 实例投影与复合业务键统一（真实 PostgreSQL + Redis，
 * Testcontainers）。覆盖：
 * <ol>
 *   <li>bootstrap 自愈补种（2026-09-07 用户定案）：存量有效类型行幂等补投影；</li>
 *   <li>写路径同生共死：createType 同事务落投影（code={typeKey}:{typeCode}）、投影失败整体回滚；</li>
 *   <li>实例级门禁 DB 级通路：list 门禁第二段（2026-09-03 放宽）与 detail/update/remove 复合键
 *       判定经真实投影命中/拒绝——四处消费方自裸 typeCode / id 串 / 实体轨迁移（ID 空间错位修复）；</li>
 *   <li>删除级联（2026-09-07 用户定案）：类型软删同事务级联投影行 + 投影行下授权行；</li>
 *   <li>TYPE_DEFINITION 种子声明 SYNC+access-service：资源管理面只读 20055。</li>
 * </ol>
 * 装配策略对齐 {@code UserRoleWriteProjectionPgIT}（T-ACCESS-019）：授权行经 jdbc 直插、
 * 先于相关主体首次引擎调用；Docker 不可用时由 Testcontainers 自动跳过。
 */
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
class TypeDefinitionProjectionPgIT {

    private static final Long TENANT = 1L;

    /** resource_type 种子：TYPE_DEFINITION=10；操作位 CRUD 预置 CREATE=1/VIEW=2 + 扩展 MANAGE=16 */
    private static final int RESOURCE_TYPE_TYPE_DEFINITION = 10;
    private static final long CREATE_BIT = 1L;
    private static final long VIEW_BIT = 2L;
    private static final long MANAGE_BIT = 16L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, TypeDefinitionProjectionPgIT.class);
    }

    @Autowired
    private TypeDefinitionAppService typeDefinitionAppService;
    @Autowired
    private LocalProjectionDomainService localProjectionDomainService;
    @Autowired
    private ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    @Autowired
    private PermQueryEngine permQueryEngine;
    @Autowired
    private JdbcTemplate jdbc;

    /** 投影层 spy：回滚用例仅指定方法注入故障，其余真实（成功场景全链路落库）。 */
    @SpyBean
    private LocalProjectionDomainService localProjectionSpy;

    /** 操作行级联 spy（T-PERM-050）：回滚用例仅 softDeleteBatch 注入故障，其余真实。 */
    @SpyBean
    private OperationPermissionMapper operationPermissionSpy;

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("bootstrap 自愈补种：存量有效类型行幂等补投影（复合键、owner=access-service），重跑返回 0")
    void backfillShouldSeedProjectionsForExistingTypeRowsAndBeIdempotent() {
        int missingBefore = countTypesWithoutProjection();
        assertThat(missingBefore).isPositive();

        int inserted = localProjectionDomainService.backfillTypeDefinitionProjections(TENANT);

        assertThat(inserted).isEqualTo(missingBefore);
        assertThat(countTypesWithoutProjection()).isZero();
        // 投影行属性：code_type=default、parent 根、启用、owner=access-service
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT code_type, parent_id, status, owner_service_code FROM resource_entity "
                + "WHERE tenant_id = ? AND resource_type = ? AND code = ? AND code_type = 'default' AND delete_flag = 0",
            TENANT, RESOURCE_TYPE_TYPE_DEFINITION, "resource_type:TYPE_DEFINITION");
        assertThat(row)
            .containsEntry("code_type", "default")
            .containsEntry("parent_id", null)
            .containsEntry("status", 1)
            .containsEntry("owner_service_code", "access-service");

        // 幂等重跑：已齐备返回 0，不重复插入
        assertThat(localProjectionDomainService.backfillTypeDefinitionProjections(TENANT)).isZero();
    }

    @Test
    @DisplayName("createType 同事务落投影（复合键 code={typeKey}:{typeCode}）；投影失败整体回滚")
    void createTypeShouldProjectAndRollbackWithProjectionFailure() {
        Long creator = insertSubject("t051-op-create", "类型创建者");
        Long creatorRole = insertBasicRole("t051-role-create", "创建者角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_TYPE_DEFINITION, CREATE_BIT);
        bindOperator(creator);

        TypeDefinitionResp created = typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("group_type", "PGIT051_TYPE", "投影联调类型", null, null, null), creator);
        assertThat(created.typeCode()).isEqualTo("PGIT051_TYPE");

        Map<String, Object> projection = projectionRow("group_type:PGIT051_TYPE");
        assertThat(((Number) projection.get("resource_type")).intValue()).isEqualTo(RESOURCE_TYPE_TYPE_DEFINITION);
        assertThat(projection.get("name")).isEqualTo("投影联调类型");
        assertThat(projection.get("owner_service_code")).isEqualTo("access-service");

        // 投影写入失败：类型行同事务回滚（强事务投影 fail-closed，T-ACCESS-019 同款验收）
        doThrow(new RuntimeException("projection boom"))
            .when(localProjectionSpy).upsertTypeDefinitionResource(anyLong(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("group_type", "PGIT051_ROLLBACK", "回滚类型", null, null, null), creator))
            .hasMessageContaining("projection boom");
        Long rolledBack = jdbc.queryForObject(
            "SELECT COUNT(*) FROM type_definition WHERE tenant_id = ? AND type_key = 'group_type' "
                + "AND type_code = 'PGIT051_ROLLBACK'", Long.class, TENANT);
        assertThat(rolledBack).isZero();
    }

    @Test
    @DisplayName("list/count 门禁实例级通路：仅实例级 TYPE_DEFINITION:VIEW 可查询，零授权全拒 403")
    void instanceLevelViewShouldUnlockListGate() {
        // 先建一个带投影的自有类型（创建者走 scopeAll CREATE）
        Long creator = insertSubject("t051-op-list", "列表创建者");
        Long creatorRole = insertBasicRole("t051-role-list", "列表创建角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_TYPE_DEFINITION, CREATE_BIT);
        bindOperator(creator);
        TypeDefinitionResp grantedType = typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("group_type", "PGIT051_LIST_A", "列表授权类型", null, null, null), creator);
        long grantedProjectionId = projectionId("group_type:PGIT051_LIST_A");

        // viewer：仅对这一个实例有 VIEW（无类型级授权）——list 门禁第二段经真实投影命中
        Long viewer = insertSubject("t051-op-viewer", "实例查看者");
        Long viewerRole = insertBasicRole("t051-role-viewer", "实例查看角色");
        insertUserRole(viewer, viewerRole);
        insertInstanceRolePerm(viewerRole, RESOURCE_TYPE_TYPE_DEFINITION, VIEW_BIT, grantedProjectionId);
        bindOperator(viewer);

        assertThat(typeDefinitionAppService.listTypes(TENANT, null, null, 0, 200))
            .extracting(TypeDefinitionResp::typeCode)
            .contains(grantedType.typeCode());
        assertThat(typeDefinitionAppService.countTypes(TENANT, null, null)).isPositive();

        // 零授权账号：类型级与任一实例级皆无 → 全拒 fail-closed
        Long bare = insertSubject("t051-op-bare", "零授权用户");
        bindOperator(bare);
        assertThatThrownBy(() -> typeDefinitionAppService.listTypes(TENANT, null, null, 0, 200))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> typeDefinitionAppService.countTypes(TENANT, null, null))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("detail/update 门禁按复合键经真实投影命中：授权实例放行、未授权实例拒绝，name 同步投影")
    void detailAndUpdateShouldGateByCompositeKey() {
        Long creator = insertSubject("t051-op-manage", "管理创建者");
        Long creatorRole = insertBasicRole("t051-role-manage-create", "管理创建角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_TYPE_DEFINITION, CREATE_BIT);
        bindOperator(creator);
        TypeDefinitionResp target = typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("group_type", "PGIT051_MGR_A", "被管类型", null, null, null), creator);
        TypeDefinitionResp other = typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("group_type", "PGIT051_MGR_B", "旁观类型", null, null, null), creator);
        long targetProjectionId = projectionId("group_type:PGIT051_MGR_A");

        // manager：仅对 target 实例有 VIEW 与 MANAGE（无类型级授权）——MANUAL 授权一行单操作位
        // （ck_role_resource_permission_manual_single_operation），两操作拆两行
        Long manager = insertSubject("t051-op-manager", "实例管理员");
        Long managerRole = insertBasicRole("t051-role-manager", "实例管理角色");
        insertUserRole(manager, managerRole);
        insertInstanceRolePerm(managerRole, RESOURCE_TYPE_TYPE_DEFINITION, VIEW_BIT, targetProjectionId);
        insertInstanceRolePerm(managerRole, RESOURCE_TYPE_TYPE_DEFINITION, MANAGE_BIT, targetProjectionId);
        bindOperator(manager);

        // detail 按复合键命中（旧实现传 type_definition.id 串，ID 空间错位下本组断言必红）
        assertThat(typeDefinitionAppService.getType(TENANT, target.id())).isNotNull();
        assertThatThrownBy(() -> typeDefinitionAppService.getType(TENANT, other.id()))
            .isInstanceOf(SecurityException.class);

        // update 命中授权实例，name 同步投影展示名
        typeDefinitionAppService.updateType(TENANT,
            new TypeUpdateReq(target.id(), "被管类型改名", null, null, null), manager);
        assertThat(projectionRow("group_type:PGIT051_MGR_A").get("name")).isEqualTo("被管类型改名");
        assertThatThrownBy(() -> typeDefinitionAppService.updateType(TENANT,
            new TypeUpdateReq(other.id(), "越权改名", null, null, null), manager))
            .isInstanceOf(SecurityException.class);
        assertThat(projectionRow("group_type:PGIT051_MGR_B").get("name")).isEqualTo("旁观类型");
    }

    @Test
    @DisplayName("remove 批量门禁按复合键 + 删除级联：类型行/投影行/投影行下授权行同事务软删")
    void deleteShouldCascadeProjectionAndGrantRows() {
        Long creator = insertSubject("t051-op-del", "删除创建者");
        Long creatorRole = insertBasicRole("t051-role-del-create", "删除创建角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_TYPE_DEFINITION, CREATE_BIT);
        bindOperator(creator);
        TypeDefinitionResp doomed = typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("group_type", "PGIT051_DEL_A", "待删类型", null, null, null), creator);
        long doomedProjectionId = projectionId("group_type:PGIT051_DEL_A");

        // holder 角色持有该实例的 VIEW——删除后授权行须级联软删
        Long holder = insertSubject("t051-op-holder", "持权用户");
        Long holderRole = insertBasicRole("t051-role-holder", "持权角色");
        insertUserRole(holder, holderRole);
        insertInstanceRolePerm(holderRole, RESOURCE_TYPE_TYPE_DEFINITION, VIEW_BIT, doomedProjectionId);
        bindOperator(holder);
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "TYPE_DEFINITION",
            "group_type:PGIT051_DEL_A", "VIEW")).isTrue();

        // deleter：类型级 MANAGE（批删走 getDeniedResourceCodes 复合键轨，类型级 scopeAll 放行）
        Long deleter = insertSubject("t051-op-deleter", "删除者");
        Long deleterRole = insertBasicRole("t051-role-deleter", "删除角色");
        insertUserRole(deleter, deleterRole);
        insertScopeAllRolePerm(deleterRole, RESOURCE_TYPE_TYPE_DEFINITION, MANAGE_BIT);
        bindOperator(deleter);
        typeDefinitionAppService.deleteTypesByIds(TENANT, List.of(doomed.id()), deleter);

        // 同生共死：类型行、投影行、投影行下授权行三表软删
        assertThat(countValidRows("type_definition",
            "type_key = 'group_type' AND type_code = 'PGIT051_DEL_A'")).isZero();
        assertThat(countValidRows("resource_entity",
            "resource_type = 10 AND code = 'group_type:PGIT051_DEL_A' AND code_type = 'default'")).isZero();
        assertThat(countValidRows("role_resource_permission",
            "resource_entity_id = " + doomedProjectionId)).isZero();
        // 持权用户对已删实例的判定 fail-closed（投影行软删后编码不可解析）
        bindOperator(holder);
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, holder, "TYPE_DEFINITION",
            "group_type:PGIT051_DEL_A", "VIEW")).isFalse();
    }

    @Test
    @DisplayName("TYPE_DEFINITION 种子声明 SYNC+access-service：资源管理面写入口只读 20055")
    void typeDefinitionResourceManageShouldBeReadOnly() {
        assertThatThrownBy(() -> resourceTypeOwnershipGuard.rejectIfSyncManagedType(TENANT, "TYPE_DEFINITION"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("资源由系统事实链路维护")
            .hasMessageContaining("类型定义")
            .hasMessageContaining("TYPE_DEFINITION");
    }

    @Test
    @DisplayName("T-PERM-050 级联：resource_type 删除同事务清预置操作行 + 类型级授权行；级联失败整体回滚")
    void deleteShouldCascadeOperationsAndTypeLevelGrantsAtomically() {
        // 创建者：类型级 CREATE——resource_type 创建触发预置 CRUD 四操作位（T-PERM-028 联动）
        Long creator = insertSubject("t050-op-create", "资源类型创建者");
        Long creatorRole = insertBasicRole("t050-role-create", "资源类型创建角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_TYPE_DEFINITION, CREATE_BIT);
        bindOperator(creator);
        TypeDefinitionResp doomed = typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("resource_type", "PGIT050_RT", "待删资源类型", null, null, null), creator);
        int doomedTypeValue = doomed.typeValue();
        assertThat(countValidRows("operation_permission", "resource_type = " + doomedTypeValue))
            .as("创建 resource_type 联动预置 CRUD 四操作位").isEqualTo(4);

        // holder 角色持有该类型级 VIEW 授权（scope_all）——删除后须级联软删（T-PERM-050 定案并入）
        Long holder = insertSubject("t050-op-holder", "类型持权用户");
        Long holderRole = insertBasicRole("t050-role-holder", "类型持权角色");
        insertUserRole(holder, holderRole);
        insertScopeAllRolePerm(holderRole, doomedTypeValue, VIEW_BIT);

        // deleter：类型级 MANAGE（批删走 getDeniedResourceCodes 复合键轨，类型级 scopeAll 放行）
        Long deleter = insertSubject("t050-op-deleter", "资源类型删除者");
        Long deleterRole = insertBasicRole("t050-role-deleter", "资源类型删除角色");
        insertUserRole(deleter, deleterRole);
        insertScopeAllRolePerm(deleterRole, RESOURCE_TYPE_TYPE_DEFINITION, MANAGE_BIT);
        bindOperator(deleter);
        typeDefinitionAppService.deleteTypesByIds(TENANT, List.of(doomed.id()), deleter);

        // 同生共死：类型行 + 该类型全部操作行 + 该类型下授权行三面软删（旧实现仅删类型行，本组断言必红）
        assertThat(countValidRows("type_definition",
            "type_key = 'resource_type' AND type_code = 'PGIT050_RT'")).isZero();
        assertThat(countValidRows("operation_permission",
            "resource_type = " + doomedTypeValue)).isZero();
        assertThat(countValidRows("role_resource_permission",
            "resource_type = " + doomedTypeValue)).isZero();

        // 原子性：级联中段（操作行软删）失败 → 类型行/投影行/操作行整体回滚，不留半删状态
        bindOperator(creator);
        TypeDefinitionResp doomed2 = typeDefinitionAppService.createType(TENANT,
            new TypeCreateReq("resource_type", "PGIT050_RB", "回滚资源类型", null, null, null), creator);
        doThrow(new RuntimeException("op cascade boom"))
            .when(operationPermissionSpy).softDeleteBatch(anyLong(), anyList(), any());
        bindOperator(deleter);
        assertThatThrownBy(() -> typeDefinitionAppService.deleteTypesByIds(TENANT, List.of(doomed2.id()), deleter))
            .hasMessageContaining("op cascade boom");
        assertThat(countValidRows("type_definition",
            "type_key = 'resource_type' AND type_code = 'PGIT050_RB'")).isEqualTo(1);
        assertThat(countValidRows("operation_permission",
            "resource_type = " + doomed2.typeValue())).isEqualTo(4);
        assertThat(countValidRows("resource_entity",
            "resource_type = 10 AND code = 'resource_type:PGIT050_RB' AND code_type = 'default'")).isEqualTo(1);
    }

    // ===== 数据装配（jdbc 直插事实/授权，先于相关主体首次引擎调用） =====

    private void bindOperator(Long operatorId) {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorId));
    }

    private Long insertSubject(String externalId, String name) {
        // user_type 种子：LOCAL_USER=3
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, 3, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, externalId, name);
    }

    private Long insertBasicRole(String externalId, String name) {
        // role_type 种子：BASIC_ROLE=6
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, externalId, name);
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

    private void insertInstanceRolePerm(Long roleId, int resourceType, long grantedBits, long resourceEntityId) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, ?, ?, ?, false, 'MANUAL')",
            TENANT, roleId, resourceEntityId, grantedBits, resourceType);
    }

    private Map<String, Object> projectionRow(String code) {
        return jdbc.queryForMap(
            "SELECT id, name, status, parent_id, resource_type, owner_service_code, delete_flag FROM resource_entity "
                + "WHERE tenant_id = ? AND resource_type = ? AND code = ? AND code_type = 'default' AND delete_flag = 0",
            TENANT, RESOURCE_TYPE_TYPE_DEFINITION, code);
    }

    private long projectionId(String code) {
        return ((Number) projectionRow(code).get("id")).longValue();
    }

    private long countValidRows(String table, String condition) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND delete_flag = 0 AND " + condition,
            Long.class, TENANT);
        return count == null ? 0 : count;
    }

    /** 有效类型行中缺少 TYPE_DEFINITION 投影的数量（复合键关联，自愈补种验收口径） */
    private int countTypesWithoutProjection() {
        Integer missing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM type_definition td WHERE td.tenant_id = ? AND td.delete_flag = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM resource_entity re WHERE re.tenant_id = td.tenant_id "
                + "AND re.resource_type = 10 AND re.code_type = 'default' AND re.delete_flag = 0 "
                + "AND re.code = td.type_key || ':' || td.type_code)",
            Integer.class, TENANT);
        return missing == null ? 0 : missing;
    }
}
