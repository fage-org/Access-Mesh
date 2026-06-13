package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.RoleResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleProxyServiceImplTest {

    @Mock
    private PermissionFeignClient permissionFeignClient;
    @Mock
    private UserOrgDomainService userOrgDomainService;
    @Mock
    private OrgDomainService orgDomainService;
    @Mock
    private MenuDomainService menuDomainService;
    @Mock
    private AdminPermissionValidator permissionValidator;
    @Mock
    private CacheService cacheService;

    private RoleProxyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleProxyServiceImpl(
            permissionFeignClient,
            userOrgDomainService,
            orgDomainService,
            menuDomainService,
            permissionValidator,
            cacheService
        );
    }

    @Test
    @DisplayName("createRoleForOrg: orgType=2 maps to POSITION")
    void createRoleForOrgUsesPositionForPositionOrgType() {
        SysOrg org = new SysOrg();
        org.setId(20L);
        org.setOrgType("2");
        when(orgDomainService.selectValidById(1L, 20L)).thenReturn(org);
        when(permissionFeignClient.createRole(any(RoleCreateReq.class)))
            .thenReturn(PermResult.success(roleResp(99L, "POSITION", "20")));

        Long roleId = service.createRoleForOrg("position-owner", 20L, 1L);

        assertThat(roleId).isEqualTo(99L);
        ArgumentCaptor<RoleCreateReq> captor = ArgumentCaptor.forClass(RoleCreateReq.class);
        verify(permissionFeignClient).createRole(captor.capture());
        assertThat(captor.getValue().roleTypeCode()).isEqualTo("POSITION");
        assertThat(captor.getValue().externalId()).isEqualTo("20");
    }

    @Test
    @DisplayName("grantMenuToRole: uses role business key from permission-center detail")
    void grantMenuToRoleUsesRoleBusinessKeyFromDetail() {
        when(permissionFeignClient.getRole(any(IdReq.class)))
            .thenReturn(PermResult.success(roleResp(99L, "POSITION", "20")));
        when(permissionFeignClient.batchGrant(any(RoleGrantReq.class))).thenReturn(PermResult.success(null));

        SysMenu menu = new SysMenu();
        menu.setPermResourceId(300L);
        when(menuDomainService.selectValidById(1L, 10L)).thenReturn(menu);

        service.grantMenuToRole(1L, 99L, 10L, "VIEW");

        ArgumentCaptor<IdReq> detailReq = ArgumentCaptor.forClass(IdReq.class);
        verify(permissionFeignClient).getRole(detailReq.capture());
        assertThat(detailReq.getValue().id()).isEqualTo(99L);

        ArgumentCaptor<RoleGrantReq> grantReq = ArgumentCaptor.forClass(RoleGrantReq.class);
        verify(permissionFeignClient).batchGrant(grantReq.capture());
        assertThat(grantReq.getValue().roleTypeCode()).isEqualTo("POSITION");
        assertThat(grantReq.getValue().roleExternalId()).isEqualTo("20");
        assertThat(grantReq.getValue().add()).hasSize(1);
        assertThat(grantReq.getValue().add().get(0).resourceCode()).isEqualTo("10");
        assertThat(grantReq.getValue().add().get(0).operationCode()).isEqualTo("VIEW");
    }

    private RoleResp roleResp(Long id, String roleTypeCode, String externalId) {
        return new RoleResp(
            id,
            1L,
            null,
            roleTypeCode,
            roleTypeCode,
            externalId,
            "role-" + id,
            1,
            null,
            null,
            null,
            null
        );
    }
}
