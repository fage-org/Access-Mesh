package cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.ServicePrincipal;
import cn.ac.fage.accessmesh.access.infrastructure.credential.entity.ServiceCredential;
import cn.ac.fage.accessmesh.access.infrastructure.credential.mapper.ServiceCredentialMapper;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import cn.dev33.satoken.secure.BCrypt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 服务凭证领域服务回归锁（T-PERM-070）：验证顺序（secret 比对先于状态细分——
 * 三态仅对持有正确 secret 者暴露）、生成格式（sc-/sk- 前缀 base64url）、
 * 库内只存哈希、签发撞全局唯一索引 fail-fast（2026-09-20 评审 P3-6 修正，同事务 abort 下重试不可达）。
 */
class ServiceCredentialDomainServiceImplTest {

    private ServiceCredentialMapper mapper;
    private ServiceConfigDomainService serviceConfigDomainService;
    private ServiceCredentialDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ServiceCredentialMapper.class);
        serviceConfigDomainService = mock(ServiceConfigDomainService.class);
        service = new ServiceCredentialDomainServiceImpl(mapper, serviceConfigDomainService);
    }

    private ServiceCredential row(String credentialId, String plainSecret, int status, LocalDateTime expiresAt) {
        ServiceCredential row = new ServiceCredential();
        row.setId(1L);
        row.setTenantId(7L);
        row.setServiceCode("example-service");
        row.setCredentialId(credentialId);
        row.setSecretHash(expectedHash(plainSecret));
        row.setStatus(status);
        row.setExpiresAt(expiresAt);
        return row;
    }

    private static String expectedHash(String secret) {
        try {
            return "sha256-v1$" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private ServiceConfig activeService() {
        ServiceConfig config = new ServiceConfig();
        config.setTenantId(7L);
        config.setServiceCode("example-service");
        config.setStatus(1);
        return config;
    }

    @Test
    @DisplayName("verify 全绿：有效凭证+启用服务 → principal(tenantId/serviceCode 凭证行派生)")
    void shouldReturnPrincipal_whenValidCredential() {
        when(mapper.selectByCredentialId("sc-real")).thenReturn(row("sc-real", "sk-real", 1, null));
        when(serviceConfigDomainService.selectByTenantAndServiceCode(7L, "example-service")).thenReturn(activeService());

        ServiceCredentialDomainService.VerifyResult result = service.verify("sc-real", "sk-real");

        assertThat(result.success()).isTrue();
        assertThat(result.principal()).isEqualTo(new ServicePrincipal(7L, "example-service", "sc-real"));
    }

    @Test
    @DisplayName("验证顺序锁：错误 secret + 停用凭证 → 20065（不是 20067——状态细分仅对正确 secret 暴露）")
    void shouldMaskStatus_whenSecretWrong() {
        when(mapper.selectByCredentialId("sc-real")).thenReturn(row("sc-real", "sk-real", 0, null));

        ServiceCredentialDomainService.VerifyResult result = service.verify("sc-real", "sk-wrong");

        assertThat(result.failure()).isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
    }

    @Test
    @DisplayName("正确 secret + 停用 → 20067；正确 secret + 过期 → 20066；服务停用 → 20068")
    void shouldExposeDistinctStates_whenSecretCorrect() {
        when(mapper.selectByCredentialId("sc-a")).thenReturn(row("sc-a", "sk-a", 0, null));
        assertThat(service.verify("sc-a", "sk-a").failure()).isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_DISABLED);

        when(mapper.selectByCredentialId("sc-b")).thenReturn(row("sc-b", "sk-b", 1, LocalDateTime.now().minusMinutes(1)));
        assertThat(service.verify("sc-b", "sk-b").failure()).isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_EXPIRED);

        when(mapper.selectByCredentialId("sc-c")).thenReturn(row("sc-c", "sk-c", 1, null));
        ServiceConfig disabled = activeService();
        disabled.setStatus(0);
        when(serviceConfigDomainService.selectByTenantAndServiceCode(anyLong(), any())).thenReturn(disabled);
        assertThat(service.verify("sc-c", "sk-c").failure()).isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_SERVICE_INACTIVE);
    }

    @Test
    @DisplayName("凭证定位失败/入参空白 → 20065 且不触服务配置查询")
    void shouldRejectInvalid_whenNotFoundOrBlank() {
        when(mapper.selectByCredentialId("sc-none")).thenReturn(null);
        assertThat(service.verify("sc-none", "sk-x").failure()).isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        assertThat(service.verify(null, "sk-x").failure()).isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        assertThat(service.verify("sc-x", " ").failure()).isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        verify(serviceConfigDomainService, times(0)).selectByTenantAndServiceCode(anyLong(), any());
    }

    @Test
    @DisplayName("issue：随机标识与 secret 格式保持，库内只存带版本的 SHA-256 摘要")
    void shouldIssueWithCanonicalFormat() {
        ServiceCredentialDomainService.IssuedCredential issued =
            service.issue(7L, "example-service", null, 100L);

        assertThat(issued.entity().getCredentialId()).matches("sc-[A-Za-z0-9_-]{22}");
        assertThat(issued.plainSecret()).matches("sk-[A-Za-z0-9_-]{43}");
        assertThat(issued.plainSecret()).doesNotContain("+").doesNotContain("/");
        // 库内只存哈希且可验证明文
        assertThat(issued.entity().getSecretHash()).isNotEqualTo(issued.plainSecret());
        assertThat(issued.entity().getSecretHash()).isEqualTo(expectedHash(issued.plainSecret()));
        assertThat(issued.entity().getTenantId()).isEqualTo(7L);
        assertThat(issued.entity().getServiceCode()).isEqualTo("example-service");
        assertThat(issued.entity().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("issue 随机性锁：连续两次签发凭证标识与 secret 均不同（无固定种子）")
    void shouldIssueDistinctCredentials() {
        ServiceCredentialDomainService.IssuedCredential first = service.issue(7L, "example-service", null, 100L);
        ServiceCredentialDomainService.IssuedCredential second = service.issue(7L, "example-service", null, 100L);
        assertThat(first.entity().getCredentialId()).isNotEqualTo(second.entity().getCredentialId());
        assertThat(first.plainSecret()).isNotEqualTo(second.plainSecret());
    }

    @Test
    @DisplayName("issue 撞全局唯一索引 → 立即 fail-fast（同事务 abort 下重试不可达，2026-09-20 评审 P3-6 修正）")
    void shouldFailFast_onDuplicateKey() {
        when(mapper.insert(any(ServiceCredential.class)))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_service_credential"));
        assertThatThrownBy(() -> service.issue(7L, "example-service", null, 100L))
            .isInstanceOf(IllegalStateException.class);
        verify(mapper, times(1)).insert(any(ServiceCredential.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "", "not-a-hash", "sha256-v2$0000000000000000000000000000000000000000000000000000000000000000",
        "sha256-v1$", "sha256-v1$00", "sha256-v1$00000000000000000000000000000000000000000000000000000000000000000",
        "sha256-v1$000000000000000000000000000000000000000000000000000000000000000000000",
        "sha256-v1$gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg",
        "sha256-v1$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "0000000000000000000000000000000000000000000000000000000000000000"
    })
    @DisplayName("verify：未知版本、非规范或损坏摘要 → 20065，不进入服务状态查询")
    void shouldNormalizeCorruptedHash(String storedHash) {
        ServiceCredential corrupted = row("sc-x", "sk-x", 1, null);
        corrupted.setSecretHash(storedHash);
        when(mapper.selectByCredentialId("sc-x")).thenReturn(corrupted);

        assertThat(service.verify("sc-x", "sk-x").failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        verifyNoInteractions(serviceConfigDomainService);
    }

    @Test
    @DisplayName("旧 BCrypt 凭证即使 secret 正确也拒绝，无兼容或自动迁移")
    void shouldRejectLegacyBcryptHash() {
        ServiceCredential legacy = row("sc-old", "sk-old", 1, null);
        legacy.setSecretHash(BCrypt.hashpw("sk-old"));
        when(mapper.selectByCredentialId("sc-old")).thenReturn(legacy);

        assertThat(service.verify("sc-old", "sk-old").failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        verifyNoInteractions(serviceConfigDomainService);
        verify(mapper).selectByCredentialId("sc-old");
        org.mockito.Mockito.verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {" sk-real", "sk-real ", "SK-REAL", "sk-real\u0000", "sk-other"})
    @DisplayName("secret 按完整字节比较，不接受空白归一、大小写归一或附加字节")
    void shouldRejectModifiedSecret(String candidate) {
        when(mapper.selectByCredentialId("sc-real")).thenReturn(row("sc-real", "sk-real", 1, null));
        assertThat(service.verify("sc-real", candidate).failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        verifyNoInteractions(serviceConfigDomainService);
    }

    @Test
    @DisplayName("完整 UTF-8 字节参与验密，72 字节后的差异不能被截断")
    void shouldVerifyEntireUtf8Secret() {
        String secret = "sk-" + "a".repeat(72) + "尾";
        when(mapper.selectByCredentialId("sc-long")).thenReturn(row("sc-long", secret, 1, null));
        when(serviceConfigDomainService.selectByTenantAndServiceCode(7L, "example-service")).thenReturn(activeService());
        assertThat(service.verify("sc-long", secret).success()).isTrue();
        assertThat(service.verify("sc-long", "sk-" + "a".repeat(72) + "改").failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        verify(serviceConfigDomainService, times(1)).selectByTenantAndServiceCode(7L, "example-service");
    }

    @Test
    @DisplayName("数据库摘要与其他凭证的 secret 都不能冒充原凭证")
    void shouldRejectDigestAsSecretOrOtherCredentialSecret() {
        ServiceCredentialDomainService.IssuedCredential first = service.issue(7L, "example-service", null, 100L);
        ServiceCredentialDomainService.IssuedCredential second = service.issue(8L, "other-service", null, 100L);
        when(mapper.selectByCredentialId(first.entity().getCredentialId())).thenReturn(first.entity());
        for (String candidate : List.of(first.entity().getSecretHash(),
                first.entity().getSecretHash().substring("sha256-v1$".length()), second.plainSecret())) {
            assertThat(service.verify(first.entity().getCredentialId(), candidate).failure())
                .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
        }
        verifyNoInteractions(serviceConfigDomainService);
    }

    @Test
    @DisplayName("changeStatus 停用带 rotatedAt、启用不带（停用时刻审计）")
    void shouldMarkRotatedAtOnlyOnDisable() {
        service.changeStatus(7L, 1L, 0, 100L);
        verify(mapper).updateStatus(eq(7L), eq(1L), eq(0), any(LocalDateTime.class), eq(100L), any(LocalDateTime.class));

        service.changeStatus(7L, 1L, 1, 100L);
        verify(mapper).updateStatus(eq(7L), eq(1L), eq(1), eq((LocalDateTime) null), eq(100L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("changeExpiresAt：null=不改（清除语义不提供，返回 0 不触库）")
    void shouldSkip_whenExpiresAtNull() {
        assertThat(service.changeExpiresAt(7L, 1L, null, 100L)).isZero();
        verify(mapper, times(0)).updateExpiresAt(anyLong(), anyLong(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("list：serviceCode 空白走全租户查询")
    void shouldListAll_whenServiceCodeBlank() {
        when(mapper.selectByTenantId(7L)).thenReturn(List.of());
        service.list(7L, " ");
        verify(mapper).selectByTenantId(7L);
    }

    @Test
    @DisplayName("issue 落库字段：expiresAt 透传、deleteFlag 初始 0、审计列写入")
    void shouldPersistAuditColumns() {
        LocalDateTime expires = LocalDateTime.now().plusDays(90);
        ArgumentCaptor<ServiceCredential> captor = ArgumentCaptor.forClass(ServiceCredential.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        service.issue(7L, "example-service", expires, 100L);

        ServiceCredential saved = captor.getValue();
        assertThat(saved.getExpiresAt()).isEqualTo(expires);
        assertThat(saved.getCreatedBy()).isEqualTo(100L);
        assertThat(saved.getDeleteFlag()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
