package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.security.PlatformSessionService;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlatformOperatorSessionTest {
    private SaTokenConfig previousConfig;
    private SaTokenDao previousDao;
    private StpLogic previousPlatformLogic;
    private SaTokenDaoDefaultImpl dao;
    private PlatformSessionService sessions;
    private PlatformAccount account;

    @BeforeEach
    void setup() {
        previousConfig = SaManager.getConfig();
        previousDao = SaManager.getSaTokenDao();
        previousPlatformLogic = SaManager.stpLogicMap.get(PlatformSessionService.LOGIN_TYPE);
        var config = new SaTokenConfig().setTimeout(3600).setActiveTimeout(1800)
            .setIsConcurrent(true).setIsShare(true).setIsPrint(false);
        dao = new SaTokenDaoDefaultImpl();
        SaManager.setConfig(config);
        SaManager.setSaTokenDao(dao);
        var accounts = mock(PlatformAccountDomainService.class);
        account = new PlatformAccount();
        account.setId(17L);
        account.setUsername("operator");
        account.setStatus(1);
        account.setCredentialVersion(1L);
        when(accounts.findById(17L)).thenAnswer(invocation -> account);
        sessions = new PlatformSessionService(config, accounts);
    }

    @AfterEach
    void restore() {
        SaManager.setConfig(previousConfig);
        SaManager.setSaTokenDao(previousDao);
        SaManager.removeStpLogic(PlatformSessionService.LOGIN_TYPE);
        if (previousPlatformLogic != null) SaManager.putStpLogic(previousPlatformLogic);
        if (dao != null) dao.destroy();
    }

    @Test
    void shouldSeparatePlatformAndTenantTokensForTheSameNumericId() {
        String tenantToken = StpUtil.getStpLogic().createLoginSession(17L);
        String platformToken = sessions.issue(account);
        assertThat(sessions.authenticate(platformToken)).isNotNull();
        assertThat(sessions.authenticate(tenantToken)).isNull();
        assertThat(StpUtil.getLoginIdByToken(platformToken)).isNull();
        sessions.logout(platformToken);
        assertThat(sessions.authenticate(platformToken)).isNull();
        assertThat(StpUtil.getLoginIdByToken(tenantToken)).isNotNull();
    }

    @Test
    void shouldNeverReviveAnOldTokenAfterResetAndLoginAgain() {
        String old = sessions.issue(account);
        account.setCredentialVersion(2L);
        String current = sessions.issue(account);
        assertThat(current).isNotEqualTo(old);
        assertThat(sessions.authenticate(current)).isNotNull();
        assertThat(sessions.authenticate(old)).isNull();
        account.setStatus(0);
        account.setCredentialVersion(3L);
        assertThat(sessions.authenticate(current)).isNull();
        account.setStatus(1);
        assertThat(sessions.authenticate(current)).isNull();
        assertThat(sessions.authenticate(sessions.issue(account))).isNotNull();
    }
}
