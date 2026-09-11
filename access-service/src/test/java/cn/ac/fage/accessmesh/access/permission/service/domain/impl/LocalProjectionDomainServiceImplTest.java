package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalProjectionDomainServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private AbstractUserMapper abstractUserMapper;
    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper typeDefinitionMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper permissionConditionMapper;

    private LocalProjectionDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LocalProjectionDomainServiceImpl(
            typeResolutionService, abstractUserMapper, abstractRoleMapper,
            resourceEntityMapper, typeDefinitionMapper, permissionConditionMapper, userRoleMapper);
    }

    @Test
    @DisplayName("upsertAdminUser 插入 abstract_user 与 USER 资源，owner=access-service")
    void upsertAdminUser_insertsOwnedProjection() {
        when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "LOCAL_USER")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "USER")).thenReturn(6);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "10")).thenReturn(null);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, 6, "10", "default")).thenReturn(null);
        when(abstractUserMapper.insert(any(AbstractUser.class))).thenAnswer(inv -> {
            AbstractUser u = inv.getArgument(0);
            u.setId(100L);
            return 1;
        });

        Long id = service.upsertAdminUser(TENANT, 10L, "张三", true, "{\"username\":\"zhang\"}");

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<AbstractUser> userCap = ArgumentCaptor.forClass(AbstractUser.class);
        verify(abstractUserMapper).insert(userCap.capture());
        assertThat(userCap.getValue().getOwnerServiceCode()).isEqualTo(LocalProjectionOwner.SERVICE_CODE);
        assertThat(userCap.getValue().getExternalId()).isEqualTo("10");
        assertThat(userCap.getValue().getEnabled()).isTrue();

        ArgumentCaptor<ResourceEntity> resCap = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).insert(resCap.capture());
        assertThat(resCap.getValue().getOwnerServiceCode()).isEqualTo(LocalProjectionOwner.SERVICE_CODE);
        assertThat(resCap.getValue().getCode()).isEqualTo("10");
    }

    @Test
    @DisplayName("deleteAdminUser 软删除已有投影，缺失时不报错")
    void deleteAdminUser_softDeletesWhenPresent() {
        when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "LOCAL_USER")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "USER")).thenReturn(6);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "10")).thenReturn(null);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, 6, "10", "default")).thenReturn(null);

        service.deleteAdminUser(TENANT, 10L);

        verify(abstractUserMapper, never()).softDeleteBatch(any(), any(), any());
        verify(resourceEntityMapper, never()).softDeleteBatch(any(), any(), any());
    }

    // ===== 批量绑定 / POSITION relation / 依赖缺失 / 父角色类型 / 岗位迁移 =====

    /** requireType mock：按 (typeKey, typeCode) 返回稳定值（lenient——各用例只用到子集）。 */
    private void mockTypes() {
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "LOCAL_USER")).thenReturn(3);
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "role_type", "ORG")).thenReturn(10);
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "role_type", "POSITION")).thenReturn(11);
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "ORG")).thenReturn(29);
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "USER")).thenReturn(6);
    }

    private AbstractUser user(long id, String extId) {
        AbstractUser u = new AbstractUser();
        u.setId(id);
        u.setExternalId(extId);
        return u;
    }

    private AbstractRole role(long id, String extId) {
        AbstractRole r = new AbstractRole();
        r.setId(id);
        r.setExternalId(extId);
        return r;
    }


    // ===== 反向所有权碰撞（T-ACCESS-018 评审 P1）：USER/ORG/MENU 为公共类型，
    // 按 (type, code, default) 命中的既有行可能属外部同步——本地投影只维护自己的行 =====

    private ResourceEntity foreignResource(long id, String code) {
        ResourceEntity r = new ResourceEntity();
        r.setId(id);
        r.setCode(code);
        r.setCodeType("default");
        r.setOwnerServiceCode(null); // 外部同步行 owner 保持 NULL
        return r;
    }

    // ------------------------------------------------------------------
    // T-PERM-052 内部来源统一（2026-09-05）：USER 类型种子声明 SYNC+access-service，
    // 外部同步/人工创建均被类型门禁拒绝，USER 类型行只可能来自本投影——原行级防线
    // （rejectIfForeignResource 接管拒绝 / isOwnResource 外部行跳过）已收编删除，
    // 本组用例改锁「命中行一律按本投影行处理」的新语义。
    // ------------------------------------------------------------------

    @Test
    @DisplayName("upsertAdminUser 命中行且 parent 置空：UpdateEntity 强制清 parent 列（评审批次顺手修复回归锁）")
    void upsertAdminUser_clearsParentColumnWhenNull() {
        when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "LOCAL_USER")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "USER")).thenReturn(6);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "123")).thenReturn(null);
        ResourceEntity existing = foreignResource(900L, "123");
        existing.setParentId(555L); // 旧 parent 残留场景
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, 6, "123", "default"))
            .thenReturn(existing);

        service.upsertAdminUser(TENANT, 123L, "张三", true, null);

        // parentId=null 须走 UpdateEntity 显式清列（update(entity) 忽略 null 字段，flex 语义）。
        // codex 复评 P2：只断言 Java 字段 null 在旧实现下同样通过（旧实现 set null 但 SQL 忽略该列），
        // 须断言 updates map 显式含 parentId=null（Dependency 先例写法）——旧实现下必败
        org.mockito.ArgumentCaptor<ResourceEntity> captor =
            org.mockito.ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).update(captor.capture());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> updates =
            ((com.mybatisflex.core.update.UpdateWrapper<ResourceEntity>) captor.getValue()).getUpdates();
        org.assertj.core.api.Assertions.assertThat(updates).containsKey("parentId");
        org.assertj.core.api.Assertions.assertThat(updates.get("parentId")).isNull();
        org.junit.jupiter.api.Assertions.assertEquals(900L, captor.getValue().getId());
    }

    @Test
    @DisplayName("upsertAdminUser 命中已有 USER 资源行：直接更新（不再行级归属判定）")
    void upsertAdminUser_updatesExistingRow_withoutRowLevelOwnershipCheck() {
        when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "LOCAL_USER")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "USER")).thenReturn(6);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "123")).thenReturn(null);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, 6, "123", "default"))
            .thenReturn(foreignResource(900L, "123"));

        service.upsertAdminUser(TENANT, 123L, "张三", true, null);

        verify(resourceEntityMapper).update(any(ResourceEntity.class));
        verify(resourceEntityMapper, never()).insert(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("deleteAdminUser 命中 USER 资源行：连同主体一并软删（投影是类型唯一 writer）")
    void deleteAdminUser_softDeletesResourceRow() {
        when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "LOCAL_USER")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "USER")).thenReturn(6);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "123"))
            .thenReturn(user(100L, "123"));
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, 6, "123", "default"))
            .thenReturn(foreignResource(900L, "123"));

        service.deleteAdminUser(TENANT, 123L);

        verify(abstractUserMapper).softDeleteBatch(eq(TENANT), eq(List.of(100L)), any());
        verify(resourceEntityMapper).softDeleteBatch(eq(TENANT), eq(List.of(900L)), any());
    }

    @Test
    @DisplayName("batchUpsertAdminUsers 命中已有 USER 资源行：批量更新照常（不再整批 20045 拒绝）")
    void batchUpsertAdminUsers_updatesExistingResourceRows() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("123")))
            .thenReturn(List.of(user(100L, "123")));
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(TENANT, 6, Set.of("123"), Set.of("default")))
            .thenReturn(List.of(foreignResource(900L, "123")));

        service.batchUpsertAdminUsers(TENANT,
                List.of(new LocalProjectionDomainService.UpsertUserKey(123L, "张三", true, null)));

        // 已有资源行进入批量刷新，不再因行级归属判定整批回滚
        verify(resourceEntityMapper).batchUpdateValues(eq(TENANT), eq(LocalProjectionOwner.SERVICE_CODE),
            org.mockito.ArgumentMatchers.<java.util.List<ResourceEntity>>argThat(
                list -> list.size() == 1 && list.get(0).getId().equals(900L)),
            any());
    }

    @Test
    @DisplayName("batchDeleteAdminUsers：命中 USER 资源行全部软删（owner 过滤已无必要）")
    void batchDeleteAdminUsers_deletesAllFoundResourceRows() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("123", "456")))
            .thenReturn(List.of(user(100L, "123"), user(101L, "456")));
        ResourceEntity own = new ResourceEntity();
        own.setId(800L);
        own.setCode("456");
        own.setCodeType("default");
        own.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(TENANT, 6, Set.of("123", "456"), Set.of("default")))
            .thenReturn(List.of(foreignResource(900L, "123"), own));

        service.batchDeleteAdminUsers(TENANT, Set.of(123L, 456L));

        verify(abstractUserMapper).softDeleteBatch(eq(TENANT), eq(List.of(100L, 101L)), any());
        // 命中的两行资源全部软删（不再按 owner 过滤）
        verify(resourceEntityMapper).softDeleteBatch(eq(TENANT), eq(List.of(900L, 800L)), any());
    }

    @Test
    @DisplayName("batchBind：已有投影行更新携带主键（回归：不再重建无 id 实体）")
    void batchBind_updatesExistingWithPrimaryKey() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("20"))).thenReturn(List.of(role(200L, "20")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 11, Set.of("20"))).thenReturn(List.of());
        // 已有 user_role（id=555，三元组 100|200|200）
        UserRole existing = new UserRole();
        existing.setId(555L);
        existing.setAbstractUserId(100L);
        existing.setTargetId(200L);
        existing.setRelationId(200L);
        when(userRoleMapper.selectValidByUserTargetRelation(TENANT, Set.of(100L), Set.of(200L), Set.of(200L), ResourceTypeCode.ROLE))
            .thenReturn(List.of(existing));

        Map<LocalProjectionDomainService.UserOrgBindKey, Long> result = service.batchBindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 20L, "ORG", null)));

        assertThat(result).containsEntry(new LocalProjectionDomainService.UserOrgBindKey(10L, 20L, "ORG", null), 555L);
        verify(userRoleMapper, never()).insertBatch(any());
        // 已有行统一批量刷新（单条 SQL），不再循环单条 update
        verify(userRoleMapper, never()).update(any(UserRole.class));
        verify(userRoleMapper).batchRefreshOwner(eq(TENANT), anyString(), eq(List.of(555L)), any());
    }

    @Test
    @DisplayName("batchBind：POSITION 绑定 relation 指向所属组织角色（relationOrgId 语义）")
    void batchBind_positionRelationPointsToOwningOrg() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 11, Set.of("3001"))).thenReturn(List.of(role(300L, "3001")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("3001"))).thenReturn(List.of());
        // 所属组织角色 ORG:2001（relationSysOrgId）
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("2001"))).thenReturn(List.of(role(200L, "2001")));
        when(userRoleMapper.selectValidByUserTargetRelation(TENANT, Set.of(100L), Set.of(300L), Set.of(200L, 300L), ResourceTypeCode.ROLE))
            .thenReturn(List.of());

        service.batchBindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 3001L, "POSITION", 2001L)));

        ArgumentCaptor<List<UserRole>> insertCap = ArgumentCaptor.forClass(List.class);
        verify(userRoleMapper).insertBatch(insertCap.capture());
        UserRole inserted = insertCap.getValue().get(0);
        assertThat(inserted.getTargetId()).isEqualTo(300L);      // POSITION:3001
        assertThat(inserted.getRelationId()).isEqualTo(200L);    // ORG:2001（所属组织，非岗位自身）
    }

    @Test
    @DisplayName("batchBind：POSITION 所属组织角色投影缺失 → 抛依赖缺失（不再回退 targetRole）")
    void batchBind_positionMissingOwningOrgRoleFailsClosed() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 11, Set.of("3001"))).thenReturn(List.of(role(300L, "3001")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("3001"))).thenReturn(List.of());
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("2001"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.batchBindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 3001L, "POSITION", 2001L))))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("所属组织角色投影缺失");
        verify(userRoleMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("upsertAdminOrg：父角色按父节点实际 orgType 解析（ORG 父 + POSITION 子）")
    void upsertAdminOrg_resolvesParentRoleByParentOrgType() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "100")).thenReturn(role(110L, "100")); // ORG:100 父角色
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 11, "200")).thenReturn(null);              // 岗位自身无投影
        // 父 ORG 资源投影存在（fail-closed 前提），子资源无投影 → 走 insert
        ResourceEntity parentRes = new ResourceEntity();
        parentRes.setId(120L);
        parentRes.setCode("100");
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(eq(TENANT), eq(29), eq("100"), eq("default")))
            .thenReturn(parentRes);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(eq(TENANT), eq(29), eq("200"), eq("default")))
            .thenReturn(null);
        when(abstractRoleMapper.insert(any(AbstractRole.class))).thenAnswer(inv -> {
            AbstractRole r = inv.getArgument(0);
            r.setId(300L);
            return 1;
        });
        when(resourceEntityMapper.insert(any(ResourceEntity.class))).thenReturn(1);

        Long id = service.upsertAdminOrg(TENANT, 200L, "2", "岗位", 100L, "1", 1, 1, "{}");

        assertThat(id).isEqualTo(300L);
        ArgumentCaptor<AbstractRole> roleCap = ArgumentCaptor.forClass(AbstractRole.class);
        verify(abstractRoleMapper).insert(roleCap.capture());
        assertThat(roleCap.getValue().getParentId()).isEqualTo(110L); // ORG 父角色，非岗位类型查询
    }

    @Test
    @DisplayName("upsertAdminOrg：父角色投影缺失 → 抛依赖缺失（不再静默 parentId=null）")
    void upsertAdminOrg_missingParentRoleFailsClosed() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "100")).thenReturn(null);

        assertThatThrownBy(() -> service.upsertAdminOrg(TENANT, 200L, "2", "岗位", 100L, "1", 1, 1, "{}"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("父组织角色投影缺失");
        verify(abstractRoleMapper, never()).insert(any(AbstractRole.class));
    }

    @Test
    @DisplayName("migratePositionRelation：岗位移动后成员 relation 从旧所属组织迁到新所属组织（单条 SQL 批量迁移）")
    void migratePositionRelation_movesMemberRelation() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 11, "3001")).thenReturn(role(300L, "3001"));
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "2001")).thenReturn(role(200L, "2001")); // 旧所属
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "4001")).thenReturn(role(400L, "4001")); // 新所属
        UserRole member = new UserRole();
        member.setId(555L);
        member.setAbstractUserId(100L);
        member.setTargetId(300L);
        member.setRelationId(200L); // 旧 relation
        when(userRoleMapper.selectValidByTargetIdAndType(TENANT, 300L, ResourceTypeCode.ROLE))
            .thenReturn(List.of(member));

        Set<Long> affected = service.migratePositionRelation(TENANT, 3001L, 2001L, 4001L);

        assertThat(affected).containsExactly(100L);
        // 单条 SQL 批量迁移，不再循环单条 update
        verify(userRoleMapper, never()).update(any(UserRole.class));
        verify(userRoleMapper).batchUpdateRelationByIds(eq(TENANT), eq(400L), eq(List.of(555L)), any());
    }

    @Test
    @DisplayName("migratePositionRelation：新所属组织角色缺失 → 抛依赖缺失（整体回滚）")
    void migratePositionRelation_missingNewOrgFailsClosed() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 11, "3001")).thenReturn(role(300L, "3001"));
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "2001")).thenReturn(role(200L, "2001"));
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "4001")).thenReturn(null);

        assertThatThrownBy(() -> service.migratePositionRelation(TENANT, 3001L, 2001L, 4001L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("新所属组织角色投影缺失");
        verify(userRoleMapper, never()).batchUpdateRelationByIds(any(), any(), any(), any());
    }

    @Test
    @DisplayName("migratePositionRelation：岗位角色投影缺失 → 抛依赖缺失（不再静默 Set.of()）")
    void migratePositionRelation_missingPositionRoleFailsClosed() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 11, "3001")).thenReturn(null);

        assertThatThrownBy(() -> service.migratePositionRelation(TENANT, 3001L, 2001L, 4001L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("岗位角色投影缺失");
        verify(userRoleMapper, never()).selectValidByTargetIdAndType(any(), any(), any());
        verify(userRoleMapper, never()).batchUpdateRelationByIds(any(), any(), any(), any());
    }

    @Test
    @DisplayName("migratePositionRelation：旧所属组织角色投影缺失 → 抛依赖缺失（不再静默 Set.of()）")
    void migratePositionRelation_missingOldOrgFailsClosed() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 11, "3001")).thenReturn(role(300L, "3001"));
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "2001")).thenReturn(null); // 旧所属缺失
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "4001")).thenReturn(role(400L, "4001")); // 新所属存在

        assertThatThrownBy(() -> service.migratePositionRelation(TENANT, 3001L, 2001L, 4001L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("旧所属组织角色投影缺失");
        verify(userRoleMapper, never()).batchUpdateRelationByIds(any(), any(), any(), any());
    }

    @Test
    @DisplayName("upsertAdminOrg：父 resource_entity 投影缺失 → 抛依赖缺失（不再静默 parentId=null）")
    void upsertAdminOrg_missingParentResourceFailsClosed() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "100")).thenReturn(role(110L, "100")); // ORG:100 父角色存在
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 11, "200")).thenReturn(null);              // 岗位自身无投影
        // 父 ORG 资源投影缺失（父角色存在但资源缺失的异常状态）
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(any(), any(), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.upsertAdminOrg(TENANT, 200L, "2", "岗位", 100L, "1", 1, 1, "{}"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("父资源投影缺失");
        // 角色侧 insert 已发生、资源侧 fail-closed 抛错——整体回滚由 AppService 事务保证（强事务投影）
        verify(abstractRoleMapper).insert(any(AbstractRole.class));
    }

    @Test
    @DisplayName("batchUpsert：已有行统一批量刷新（单条 SQL），不再循环单条 update")
    void batchUpsert_updatesExistingInSingleSql() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        ResourceEntity res = new ResourceEntity();
        res.setId(50L);
        res.setCode("10");
        res.setCodeType("default");
        res.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(TENANT, 6, Set.of("10"), Set.of("default")))
            .thenReturn(List.of(res));

        Map<Long, Long> result = service.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(10L, "张三", true, null)));

        assertThat(result).containsEntry(10L, 100L);
        verify(abstractUserMapper, never()).update(any(AbstractUser.class));
        verify(resourceEntityMapper, never()).update(any(ResourceEntity.class));
        verify(abstractUserMapper).batchUpdateValues(eq(TENANT), anyString(),
            org.mockito.ArgumentMatchers.argThat(users -> users.size() == 1 && users.get(0).getId() == 100L),
            any());
        verify(resourceEntityMapper).batchUpdateValues(eq(TENANT), anyString(),
            org.mockito.ArgumentMatchers.argThat(resources -> resources.size() == 1 && resources.get(0).getId() == 50L),
            any());
    }

    @Test
    @DisplayName("batchDelete：资源行限定 code_type=default（不再匹配全部 code_type）")
    void batchDelete_limitsToCodeTypeDefault() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        ResourceEntity res = new ResourceEntity();
        res.setId(50L);
        res.setCode("10");
        res.setCodeType("default");
        res.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(TENANT, 6, Set.of("10"), Set.of("default")))
            .thenReturn(List.of(res));

        service.batchDeleteAdminUsers(TENANT, Set.of(10L));

        verify(resourceEntityMapper, never()).selectByTypeAndCodes(any(), any(), any());
        verify(resourceEntityMapper).selectByTypeAndCodesAndCodeTypes(
            eq(TENANT), eq(6), eq(Set.of("10")), eq(Set.of("default")));
        // 同一事务级联软删该用户全部 user_role（含功能角色，不再依赖延迟补偿）
        verify(userRoleMapper).softDeleteByAbstractUserIds(eq(TENANT), eq(Set.of(100L)), any());
        verify(abstractUserMapper).softDeleteBatch(eq(TENANT), eq(List.of(100L)), any());
        verify(resourceEntityMapper).softDeleteBatch(eq(TENANT), eq(List.of(50L)), any());
    }

    @Test
    @DisplayName("batchBind：同一 externalId 的 ORG/POSITION 双投影并存时，按请求 roleTypeCode 精确取值")
    void batchBind_resolvesRoleByRoleTypeCode() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        // 同一 externalId=3001 同时存在 ORG 与 POSITION 投影（存量漂移场景）
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("3001")))
            .thenReturn(List.of(role(300L, "3001")));   // ORG:3001
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 11, Set.of("3001")))
            .thenReturn(List.of(role(301L, "3001")));   // POSITION:3001
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("2001")))
            .thenReturn(List.of(role(200L, "2001")));   // 所属组织 ORG:2001
        when(userRoleMapper.selectValidByUserTargetRelation(TENANT, Set.of(100L), Set.of(300L, 301L),
            Set.of(200L, 300L, 301L), ResourceTypeCode.ROLE)).thenReturn(List.of());

        service.batchBindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 3001L, "POSITION", 2001L)));

        // POSITION 请求必须命中 POSITION:3001（id=301），而非 ORG:3001（id=300）
        ArgumentCaptor<List<UserRole>> insertCap = ArgumentCaptor.forClass(List.class);
        verify(userRoleMapper).insertBatch(insertCap.capture());
        UserRole inserted = insertCap.getValue().get(0);
        assertThat(inserted.getTargetId()).isEqualTo(301L);
        assertThat(inserted.getRelationId()).isEqualTo(200L); // 所属组织 ORG:2001
    }

    @Test
    @DisplayName("batchBind：请求类型投影缺失（仅 ORG 存在但请求 POSITION）→ fail-closed")
    void batchBind_missingRequestedRoleTypeFailsClosed() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("3001")))
            .thenReturn(List.of(role(300L, "3001")));   // 仅 ORG 投影
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 11, Set.of("3001")))
            .thenReturn(List.of());
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("2001")))
            .thenReturn(List.of(role(200L, "2001")));

        assertThatThrownBy(() -> service.batchBindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 3001L, "POSITION", 2001L))))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("local projection missing");
        verify(userRoleMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("batchUnbind：请求类型角色投影缺失 → 抛依赖缺失整体回滚（不再静默跳过）")
    void batchUnbind_missingRequestedRoleTypeFailsClosed() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of(user(100L, "10")));
        // 仅 ORG 投影，请求 POSITION → 角色投影缺失
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("3001")))
            .thenReturn(List.of(role(300L, "3001")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 11, Set.of("3001")))
            .thenReturn(List.of());
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("2001")))
            .thenReturn(List.of(role(200L, "2001")));

        assertThatThrownBy(() -> service.batchUnbindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 3001L, "POSITION", 2001L))))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("local projection missing for user-org unbind");
        // 管理事实删除被回滚：不执行任何软删
        verify(userRoleMapper, never()).softDeleteBatch(any(), any(), any());
    }

    @Test
    @DisplayName("batchUnbind：用户投影缺失 → 抛依赖缺失（同 fail-closed 语义）")
    void batchUnbind_missingUserProjectionFailsClosed() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalIds(TENANT, 3, Set.of("10"))).thenReturn(List.of());
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 10, Set.of("20")))
            .thenReturn(List.of(role(200L, "20")));
        when(abstractRoleMapper.selectByTypeAndExternalIds(TENANT, 11, Set.of("20")))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.batchUnbindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 20L, "ORG", null))))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("local projection missing for user-org unbind");
        verify(userRoleMapper, never()).softDeleteBatch(any(), any(), any());
    }

    @Test
    @DisplayName("单条 unbind：用户投影缺失 → 抛依赖缺失（与 bind 对称，不再静默返回 null）")
    void unbind_missingUserProjectionFailsClosed() {
        mockTypes();
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "10")).thenReturn(null);
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "20")).thenReturn(role(200L, "20"));

        assertThatThrownBy(() -> service.unbindUserOrg(TENANT, 10L, 20L, "ORG", null))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("local projection missing for user-org unbind");
        verify(userRoleMapper, never()).softDeleteBatch(any(), any(), any());
    }

    @Test
    @DisplayName("batchBind：POSITION 缺 relationSysOrgId → 抛依赖缺失（与单条路径一致，不再回退岗位自身）")
    void batchBind_positionWithoutRelationOrgFailsClosed() {
        mockTypes();
        assertThatThrownBy(() -> service.batchBindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 3001L, "POSITION", null))))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("缺少所属组织上下文");
        verify(userRoleMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("batchUnbind：POSITION 缺 relationSysOrgId → 抛依赖缺失（与单条路径一致，不再回退岗位自身）")
    void batchUnbind_positionWithoutRelationOrgFailsClosed() {
        mockTypes();
        assertThatThrownBy(() -> service.batchUnbindUserOrg(TENANT,
            List.of(new LocalProjectionDomainService.UserOrgBindKey(10L, 3001L, "POSITION", null))))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("缺少所属组织上下文");
        verify(userRoleMapper, never()).softDeleteBatch(any(), any(), any());
    }
}
