package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.auth.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import com.mybatisflex.core.update.UpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2 客户端更新 Clear 分支的最小列写入锁（外部评审 P2 处置）。
 * <p>
 * Clear 请求的 UpdateEntity 只允许携带请求触达列（Clear 显式 NULL + 同请求新值列 +
 * updatedAt），不得回写读取快照中请求未提供的列——否则「A 清空 scopes、B 并发设置
 * audiences」交错下 A 提交会把快照里的旧 audiences（含 NULL）一并写回，覆盖 B 已
 * 提交的值。旧实现全快照复制，本组断言键集时失败，构成行为锁（§2.7 null 不更新）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class Oauth2ClientAppServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long CLIENT_ROW_ID = 5L;

    @Mock
    private SysOauth2ClientMapper oauth2ClientMapper;
    @Mock
    private AdminPermissionValidator permissionValidator;

    private Oauth2ClientAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new Oauth2ClientAppServiceImpl(oauth2ClientMapper, permissionValidator);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("scopesClear 最小列写入：仅 scopes=NULL 与 updatedAt，不回写未触达列")
    void scopesClear_writesOnlyTouchedColumns() {
        SysOauth2Client existing = existingClient();
        when(oauth2ClientMapper.selectByIdSafe(TENANT, CLIENT_ROW_ID)).thenReturn(existing);

        service.updateClient(new Oauth2ClientUpdateReq(CLIENT_ROW_ID,
            null, null, null, null, null, null, null, null, null,
            null, true, null));

        ArgumentCaptor<SysOauth2Client> captor = ArgumentCaptor.forClass(SysOauth2Client.class);
        verify(oauth2ClientMapper).update(captor.capture());
        Map<String, Object> updates = UpdateWrapper.of(captor.getValue()).getUpdates();
        assertThat(updates).containsOnlyKeys("id", "scopes", "updatedAt");
        assertThat(updates.get("scopes")).isNull();
    }

    @Test
    @DisplayName("清空与同请求新值并存：触达列=新值列+Clear 列+updatedAt")
    void clearWithNewValues_writesOnlyTouchedColumns() {
        SysOauth2Client existing = existingClient();
        when(oauth2ClientMapper.selectByIdSafe(TENANT, CLIENT_ROW_ID)).thenReturn(existing);

        service.updateClient(new Oauth2ClientUpdateReq(CLIENT_ROW_ID,
            null, "新名称", null, null, null, null, null, null, null,
            true, null, null));

        ArgumentCaptor<SysOauth2Client> captor = ArgumentCaptor.forClass(SysOauth2Client.class);
        verify(oauth2ClientMapper).update(captor.capture());
        Map<String, Object> updates = UpdateWrapper.of(captor.getValue()).getUpdates();
        assertThat(updates).containsOnlyKeys("id", "clientName", "redirectUris", "updatedAt");
        assertThat(updates.get("clientName")).isEqualTo("新名称");
        assertThat(updates.get("redirectUris")).isNull();
    }

    @Test
    void publicClientRegistrationHasNoStoredSecret() {
        var req = new cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientCreateReq(
            "public-spa", null, "Public app", "authorization_code,refresh_token",
            "https://app.example/cb", "profile", null, 3600, 604800, 1, "PUBLIC");
        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(validation.getValidator().validate(req)).isEmpty();
        }
        service.createClient(req);
        var captor = ArgumentCaptor.forClass(SysOauth2Client.class);
        verify(oauth2ClientMapper).insert(captor.capture());
        assertThat(captor.getValue().getClientType()).isEqualTo("PUBLIC");
        assertThat(captor.getValue().getClientSecret()).isNull();
    }

    @Test
    void confidentialStillRequiresSecretAndPublicCannotAcquireOne() {
        var req = new cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientCreateReq(
            "private-app", null, "Private app", "authorization_code", null, null, null, null, null, 1, null);
        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(validation.getValidator().validate(req)).isNotEmpty();
        }
        var client = existingClient();
        client.setClientType("PUBLIC");
        when(oauth2ClientMapper.selectByIdSafe(TENANT, CLIENT_ROW_ID)).thenReturn(client);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateClient(new Oauth2ClientUpdateReq(
            CLIENT_ROW_ID, "secret", null, null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        org.mockito.Mockito.verify(oauth2ClientMapper, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.any(SysOauth2Client.class));
    }

    private SysOauth2Client existingClient() {
        SysOauth2Client existing = new SysOauth2Client();
        existing.setId(CLIENT_ROW_ID);
        existing.setTenantId(TENANT);
        existing.setClientName("console");
        existing.setGrantTypes("authorization_code");
        existing.setRedirectUris("https://console.example.com/cb");
        existing.setScopes("profile");
        existing.setAudiences("access-service");
        existing.setStatus(1);
        return existing;
    }
}
