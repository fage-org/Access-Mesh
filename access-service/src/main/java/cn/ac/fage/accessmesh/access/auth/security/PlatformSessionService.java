package cn.ac.fage.accessmesh.access.auth.security;

import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.dao.SaTokenDao;
import org.springframework.stereotype.Component;

/** 平台独立认证域，不消费租户用户的会话映射。 */
@Component
public class PlatformSessionService {
    public static final String LOGIN_TYPE = "platform-operator";
    private final StpLogic logic;
    private final PlatformAccountDomainService accounts;

    public PlatformSessionService(SaTokenConfig config, PlatformAccountDomainService accounts) {
        this.accounts = accounts;
        SaTokenConfig platformConfig = new SaTokenConfig()
            .setTokenName(config.getTokenName()).setTokenPrefix(config.getTokenPrefix())
            .setTimeout(config.getTimeout()).setActiveTimeout(config.getActiveTimeout())
            .setIsConcurrent(config.getIsConcurrent()).setIsShare(false).setTokenStyle("uuid")
            .setIsReadBody(false).setIsReadCookie(false).setIsReadHeader(true).setIsWriteHeader(false)
            .setAutoRenew(config.getAutoRenew()).setIsPrint(false);
        this.logic = new StpLogic(LOGIN_TYPE).setConfig(platformConfig);
    }

    public String issue(PlatformAccount account) {
        String token = logic.createLoginSession(account.getId());
        logic.getTokenSessionByToken(token).set("credentialVersion", account.getCredentialVersion());
        return token;
    }

    public PlatformAccount authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Object accountId = logic.getLoginIdByToken(token);
            if (accountId == null) return null;
            if (logic.getTokenActiveTimeoutByToken(token) == SaTokenDao.NOT_VALUE_EXPIRE) return null;
            var session = logic.getTokenSessionByToken(token, false);
            Object version = session == null ? null : session.get("credentialVersion");
            if (version == null) return null;
            PlatformAccount account = accounts.findById(Long.parseLong(accountId.toString()));
            if (account == null || !Integer.valueOf(1).equals(account.getStatus())
                || !Long.valueOf(version.toString()).equals(account.getCredentialVersion())) return null;
            logic.updateLastActiveToNow(token);
            return account;
        } catch (NotLoginException | NumberFormatException exception) {
            return null;
        }
    }

    public long expiresIn(String token) { return logic.getTokenTimeout(token); }
    public void logout(String token) { if (token != null && !token.isBlank()) logic.logoutByTokenValue(token); }
}
