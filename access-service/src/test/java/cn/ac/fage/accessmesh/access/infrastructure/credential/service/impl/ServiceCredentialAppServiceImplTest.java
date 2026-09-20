package cn.ac.fage.accessmesh.access.infrastructure.credential.service.impl;

import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialCreateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialListReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialUpdateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialCreateResp;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialResp;
import cn.ac.fage.accessmesh.access.infrastructure.credential.entity.ServiceCredential;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务凭证管理应用服务回归锁（T-PERM-070）：门禁族（写 MANAGE/读 VIEW）、
 * 服务注册+启用前置校验、create 响应明文 secret 仅一次、空 patch 拒绝。
 * <p>写用例显式传 operatorId=100L（OperatorUtil 直返，不触上下文）；list 走
 * OperatorContext（mockStatic，BizDomainAppServiceImplTest 先例同款）。</p>
 */
class ServiceCredentialAppServiceImplTest {

    private static final Long TENANT = 7L;
    private static final Long OPERATOR = 100L;

    private ServiceCredentialDomainService domainService;
    private ServiceConfigDomainService serviceConfigDomainService;
    private PermQueryEngine engine;
    private ServiceCredentialAppServiceImpl appService;

    @BeforeEach
    void setUp() {
        domainService = mock(ServiceCredentialDomainService.class);
        serviceConfigDomainService = mock(ServiceConfigDomainService.class);
        engine = mock(PermQueryEngine.class);
        appService = new ServiceCredentialAppServiceImpl(domainService, serviceConfigDomainService, engine);
    }

    private void allowManage() {
        when(engine.hasPermissionByCode(TENANT, OPERATOR, ResourceTypeCode.SERVICE, null, OperationCode.MANAGE))
            .thenReturn(true);
    }

    private ServiceConfig activeService() {
        ServiceConfig config = new ServiceConfig();
        config.setStatus(1);
        return config;
    }

    private ServiceCredential sample() {
        ServiceCredential row = new ServiceCredential();
        row.setId(5L);
        row.setTenantId(TENANT);
        row.setServiceCode("example-service");
        row.setCredentialId("sc-abc");
        row.setStatus(1);
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    @Test
    @DisplayName("create：门禁 SERVICE:MANAGE + 服务注册启用校验 + 响应含明文 secret（仅一次）")
    void shouldCreateWithSecretEcho() {
        allowManage();
        when(serviceConfigDomainService.selectByTenantAndServiceCode(TENANT, "example-service")).thenReturn(activeService());
        when(domainService.issue(eq(TENANT), eq("example-service"), any(), eq(OPERATOR)))
            .thenReturn(new ServiceCredentialDomainService.IssuedCredential(sample(), "sk-plain-once"));

        ServiceCredentialCreateResp resp = appService.create(TENANT,
            new ServiceCredentialCreateReq("example-service", null), OPERATOR);

        assertThat(resp.credentialId()).isEqualTo("sc-abc");
        assertThat(resp.secret()).isEqualTo("sk-plain-once");
        assertThat(resp.status()).isEqualTo(1);
    }

    @Test
    @DisplayName("create：无 SERVICE:MANAGE → SecurityException 拒绝且不触签发")
    void shouldRejectCreateWithoutPermission() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> appService.create(TENANT,
            new ServiceCredentialCreateReq("example-service", null), OPERATOR))
            .isInstanceOf(SecurityException.class);
        verify(domainService, never()).issue(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("create：绑定服务未注册/停用 → 参数拒绝（防签发恒 20068 的死凭证）")
    void shouldRejectCreateWhenServiceInactive() {
        allowManage();
        ServiceConfig disabled = new ServiceConfig();
        disabled.setStatus(0);
        when(serviceConfigDomainService.selectByTenantAndServiceCode(TENANT, "ghost")).thenReturn(disabled);

        assertThatThrownBy(() -> appService.create(TENANT, new ServiceCredentialCreateReq("ghost", null), OPERATOR))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("未注册或已停用");
        verify(domainService, never()).issue(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("update：status+expiresAt 均缺失 → 空 patch 拒绝 90001")
    void shouldRejectEmptyPatch() {
        allowManage();
        assertThatThrownBy(() -> appService.update(TENANT, new ServiceCredentialUpdateReq(5L, null, null), OPERATOR))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("至少提供一项");
    }

    @Test
    @DisplayName("update：停用目标不存在 → 凭证不存在拒绝（rows=0）")
    void shouldRejectUpdateWhenCredentialMissing() {
        allowManage();
        when(domainService.changeStatus(TENANT, 5L, 0, OPERATOR)).thenReturn(0);

        assertThatThrownBy(() -> appService.update(TENANT, new ServiceCredentialUpdateReq(5L, 0, null), OPERATOR))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("凭证不存在");
    }

    @Test
    @DisplayName("update：改期成功 → 回读返回")
    void shouldUpdateExpiresAt() {
        allowManage();
        when(domainService.changeExpiresAt(eq(TENANT), eq(5L), any(), eq(OPERATOR))).thenReturn(1);
        when(domainService.findById(TENANT, 5L)).thenReturn(sample());

        ServiceCredentialResp resp = appService.update(TENANT,
            new ServiceCredentialUpdateReq(5L, null, LocalDateTime.now().plusDays(30)), OPERATOR);

        assertThat(resp.id()).isEqualTo(5L);
        assertThat(resp.credentialId()).isEqualTo("sc-abc");
    }

    @Test
    @DisplayName("remove：rows=0 → 凭证不存在拒绝")
    void shouldRejectRemoveWhenMissing() {
        allowManage();
        when(domainService.remove(TENANT, 5L, OPERATOR)).thenReturn(0);
        assertThatThrownBy(() -> appService.remove(TENANT, 5L, OPERATOR))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("凭证不存在");
    }

    @Test
    @DisplayName("list：门禁 SERVICE:VIEW + serviceCode 过滤透传")
    void shouldListWithViewPermission() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
            when(engine.hasPermissionByCode(TENANT, OPERATOR, ResourceTypeCode.SERVICE, null, OperationCode.VIEW))
                .thenReturn(true);
            when(domainService.list(TENANT, "example-service")).thenReturn(List.of(sample()));

            var resp = appService.list(TENANT, new ServiceCredentialListReq("example-service"));

            assertThat(resp.items()).hasSize(1);
            assertThat(resp.items().get(0).credentialId()).isEqualTo("sc-abc");
            verify(domainService).list(TENANT, "example-service");
        }
    }

    @Test
    @DisplayName("list：无 SERVICE:VIEW → SecurityException 且不触查询")
    void shouldRejectListWithoutView() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
            when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> appService.list(TENANT, new ServiceCredentialListReq(null)))
                .isInstanceOf(SecurityException.class);
            verify(domainService, never()).list(anyLong(), any());
        }
    }
}
