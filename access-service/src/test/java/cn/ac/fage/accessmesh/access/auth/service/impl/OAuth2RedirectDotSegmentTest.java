package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.AuthorizeReq;
import cn.ac.fage.accessmesh.access.auth.dto.AuthorizeResp;
import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * redirect_uri 点段逃逸回归锁（2026-10-06 逐任务评审 P2）。
 * <p>
 * 缺陷：getPath() 解码 %2e 且不归一——/cb/%2e%2e/%2e%2e/evil 解码为 /cb/../../evil，
 * 纯 startsWith 前缀匹配放行；前端 new URL 归一后 href=https://app.example/evil?code=X，
 * 授权码投递到注册前缀之外（本机探针实证）。修复=isPathAllowed 对请求与注册路径的
 * "."/".." 段一律拒绝（归一不变式）；旧实现下「点段拒绝」两用例必红。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2RedirectDotSegmentTest {

    private static final String CLIENT_ID = "example-web";
    private static final String REGISTERED = "https://web.example.com/cb";

    @Mock
    private OAuth2ClientDomainService oauth2ClientDomainService;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private LoginLogDomainService loginLogDomainService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private OAuth2AppServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OAuth2AppServiceImpl(oauth2ClientDomainService, userDomainService,
            loginLogDomainService, redisTemplate, objectMapper, cn.ac.fage.accessmesh.access.it.TenantTestSupport.activeGuard());
        ReflectionTestUtils.setField(service, "jwtSecretKey", "redirect-dot-segment-test-0123456789");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SysOauth2Client client = new SysOauth2Client();
        client.setId(1L);
        client.setTenantId(1L);
        client.setClientId(CLIENT_ID);
        client.setGrantTypes("authorization_code,refresh_token");
        client.setRedirectUris(REGISTERED);
        client.setStatus(1);
        client.setClientSecret(cn.dev33.satoken.secure.BCrypt.hashpw("secret"));
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client);

        var user = new cn.ac.fage.accessmesh.access.user.entity.SysUser();
        user.setId(100L);
        user.setStatus(1);
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw("initial-password"));
        lenient().when(userDomainService.lockValidById(1L, 100L)).thenReturn(user);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private AuthorizeResp authorize(String redirectUri) {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            return service.authorize(new AuthorizeReq(CLIENT_ID, "code", redirectUri, null,
                null, null, null));
        }
    }

    @Test
    @DisplayName("点段逃逸拒绝：%2e%2e 编码形态（旧实现前缀匹配放行必红）")
    void encodedDotDotSegmentRejected() {
        assertThatThrownBy(() -> authorize("https://web.example.com/cb/%2e%2e/%2e%2e/evil"))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting(e -> ((cn.ac.fage.accessmesh.common.exception.BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode());
    }

    @Test
    @DisplayName("点段逃逸拒绝：字面 /../ 形态（旧实现必红）")
    void literalDotDotSegmentRejected() {
        assertThatThrownBy(() -> authorize("https://web.example.com/cb/../../evil"))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting(e -> ((cn.ac.fage.accessmesh.common.exception.BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode());
    }

    @Test
    @DisplayName("阳性对照：注册前缀内正常子路径仍放行（防过收紧）")
    void normalSubPathStillAllowed() {
        assertThat(authorize("https://web.example.com/cb/callback").code()).isNotBlank();
        assertThat(authorize("https://web.example.com/cb").code()).isNotBlank();
    }
}
