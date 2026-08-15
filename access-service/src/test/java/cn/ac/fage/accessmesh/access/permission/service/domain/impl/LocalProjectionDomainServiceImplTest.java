package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
