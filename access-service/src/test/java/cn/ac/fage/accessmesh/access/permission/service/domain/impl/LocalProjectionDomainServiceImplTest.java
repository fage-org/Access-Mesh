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
    @Mock private UserRoleMapper userRoleMapper;

    private LocalProjectionDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LocalProjectionDomainServiceImpl(
            typeResolutionService, abstractUserMapper, abstractRoleMapper,
            resourceEntityMapper, userRoleMapper);
    }

    @Test
    @DisplayName("upsertAdminUser 插入 abstract_user 与 ADMIN_USER 资源，owner=access-service")
    void upsertAdminUser_insertsOwnedProjection() {
        when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "ADMIN_USER")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "ADMIN_USER")).thenReturn(16);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "10")).thenReturn(null);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, 16, "10", "default")).thenReturn(null);
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
        when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "ADMIN_USER")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "ADMIN_USER")).thenReturn(16);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT, 3, "10")).thenReturn(null);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, 16, "10", "default")).thenReturn(null);

        service.deleteAdminUser(TENANT, 10L);

        verify(abstractUserMapper, never()).softDeleteBatch(any(), any(), any());
        verify(resourceEntityMapper, never()).softDeleteBatch(any(), any(), any());
    }

    // ===== 十轮评审 P2：批量绑定 / POSITION relation / 依赖缺失 / 父角色类型 / 岗位迁移 =====

    /** requireType mock：按 (typeKey, typeCode) 返回稳定值（lenient——各用例只用到子集）。 */
    private void mockTypes() {
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "user_type", "ADMIN_USER")).thenReturn(3);
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "role_type", "ORG")).thenReturn(10);
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "role_type", "POSITION")).thenReturn(11);
        org.mockito.Mockito.lenient()
            .when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "ADMIN_ORG")).thenReturn(16);
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

    @Test
    @DisplayName("batchBind：已有投影行更新携带主键（十轮 P1 回归：不再重建无 id 实体）")
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
        ArgumentCaptor<UserRole> updateCap = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).update(updateCap.capture());
        assertThat(updateCap.getValue().getId()).isEqualTo(555L); // 主键保留
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
    @DisplayName("batchBind：POSITION 所属组织角色投影缺失 → 抛依赖缺失（十轮 P1：不再回退 targetRole）")
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
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(any(), any(), any(), any())).thenReturn(null);
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
    @DisplayName("upsertAdminOrg：父角色投影缺失 → 抛依赖缺失（十轮 P1：不再静默 parentId=null）")
    void upsertAdminOrg_missingParentRoleFailsClosed() {
        mockTypes();
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT, 10, "100")).thenReturn(null);

        assertThatThrownBy(() -> service.upsertAdminOrg(TENANT, 200L, "2", "岗位", 100L, "1", 1, 1, "{}"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("父组织角色投影缺失");
        verify(abstractRoleMapper, never()).insert(any(AbstractRole.class));
    }

    @Test
    @DisplayName("migratePositionRelation：岗位移动后成员 relation 从旧所属组织迁到新所属组织")
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
        ArgumentCaptor<UserRole> updateCap = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).update(updateCap.capture());
        assertThat(updateCap.getValue().getRelationId()).isEqualTo(400L); // 迁到新所属组织角色
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
        verify(userRoleMapper, never()).update(any(UserRole.class));
    }
}
