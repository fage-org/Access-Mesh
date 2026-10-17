package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.auth.controller.BrowserAuthController;
import cn.ac.fage.accessmesh.access.auth.dto.BrowserLoginResp;
import cn.ac.fage.accessmesh.access.infrastructure.config.BrowserSessionFilter;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.Realm;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BrowserAuthenticationTest {
    private SaTokenConfig oldConfig;
    private SaTokenDao oldDao;
    private SaTokenDaoDefaultImpl dao;
    private StpLogic logic;
    private StpLogic oldLogic;
    private BrowserAuthAppService service;
    private MockMvc mvc;
    private final String marker = "a".repeat(43);
    private String oldToken;
    @BeforeEach void setup() {
        oldConfig = SaManager.getConfig(); oldDao = SaManager.getSaTokenDao();
        oldLogic = SaManager.stpLogicMap.get("login");
        SaManager.setConfig(new SaTokenConfig().setIsReadCookie(false).setIsShare(false).setIsPrint(false));
        dao = new SaTokenDaoDefaultImpl(); SaManager.setSaTokenDao(dao);
        logic = new StpLogic("login");
        oldToken = logic.createLoginSession(1L);
        logic.getTokenSessionByToken(oldToken).set(BrowserSessionPolicy.SESSION_ATTRIBUTE, marker);
        service = mock(BrowserAuthAppService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BrowserAuthController(service))
            .addFilters(new BrowserSessionFilter("https://admin.example.test", new ObjectMapper())).build();
    }
    @AfterEach void cleanup() {
        dao.destroy(); SaManager.setConfig(oldConfig); SaManager.setSaTokenDao(oldDao);
        SaManager.removeStpLogic("login"); if (oldLogic != null) SaManager.putStpLogic(oldLogic);
    }
    @Test void browserLoginOnlyExposesMetadataAndSetsAProtectedCookie() throws Exception {
        when(service.login(any())).thenReturn(new BrowserAuthAppService.Issued<>(oldToken, 7200,
            new BrowserLoginResp(marker, 7200, 1L, "admin", 2L, false)));
        var response = mvc.perform(post("/api/access/auth/browser-login").contentType(MediaType.APPLICATION_JSON)
            .header("Origin", "https://admin.example.test").header(BrowserSessionPolicy.REQUEST_HEADER, "1")
            .content("{\"tenantCode\":\"demo\",\"username\":\"admin\",\"password\":\"test-password\",\"captchaId\":\"id\",\"captchaCode\":\"1234\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value(marker))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(oldToken);
        assertThat(response.getHeader("Set-Cookie")).contains("__Host-am-tenant=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/").doesNotContain("Domain=");
    }
    @Test void delayedLogoutHasNoCookieOrNewIdentitySideEffects() throws Exception {
        mvc.perform(post("/api/access/auth/browser-logout").contentType(MediaType.APPLICATION_JSON).content("{}")
            .header("Origin", "https://admin.example.test").header(BrowserSessionPolicy.REQUEST_HEADER, "1")
            .header(BrowserSessionPolicy.SESSION_HEADER, marker)
            .cookie(new Cookie("__Host-am-tenant", oldToken)))
            .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).logout(Realm.TENANT, oldToken);
    }
    @Test void anOldLogoutCannotRevokeTheNewCookieSession() throws Exception {
        String newToken = logic.createLoginSession(2L);
        logic.getTokenSessionByToken(newToken).set(BrowserSessionPolicy.SESSION_ATTRIBUTE, "b".repeat(43));
        mvc.perform(post("/api/access/auth/browser-logout").contentType(MediaType.APPLICATION_JSON).content("{}")
            .header("Origin", "https://admin.example.test").header(BrowserSessionPolicy.REQUEST_HEADER, "1")
            .header(BrowserSessionPolicy.SESSION_HEADER, marker).cookie(new Cookie("__Host-am-tenant", newToken)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(90002))
            .andExpect(header().doesNotExist("Set-Cookie"));
        verifyNoInteractions(service);
        assertThat(logic.getLoginIdByToken(newToken)).isNotNull();
    }
    @Test void crossSiteLoginIsRejectedBeforeCredentialsAreExamined() throws Exception {
        mvc.perform(post("/api/access/auth/browser-login").contentType(MediaType.APPLICATION_JSON).content("{}")
            .header("Origin", "https://attacker.example.test").header(BrowserSessionPolicy.REQUEST_HEADER, "1"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
