package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.BrowserLoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformBrowserLoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.LoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginReq;
import cn.ac.fage.accessmesh.access.auth.security.PlatformSessionService;
import cn.ac.fage.accessmesh.access.auth.service.AuthAppService;
import cn.ac.fage.accessmesh.access.auth.service.PlatformAuthAppService;
import cn.ac.fage.accessmesh.access.auth.service.BrowserAuthAppService;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.Realm;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Base64;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy;

@Service
public class BrowserAuthAppServiceImpl implements BrowserAuthAppService {
    private final AuthAppService tenantAuth;
    private final PlatformAuthAppService platformAuth;
    private final PlatformSessionService platformSessions;
    private final SecureRandom random = new SecureRandom();
    public BrowserAuthAppServiceImpl(AuthAppService tenantAuth, PlatformAuthAppService platformAuth,
                                     PlatformSessionService platformSessions) {
        this.tenantAuth = tenantAuth; this.platformAuth = platformAuth;
        this.platformSessions = platformSessions;
    }
    @Override public Issued<BrowserLoginResp> login(LoginReq req) {
        var result = tenantAuth.login(req);
        String id = bind(Realm.TENANT, result.accessToken(), result.expiresIn());
        return new Issued<>(result.accessToken(), result.expiresIn(), new BrowserLoginResp(id, result.expiresIn(),
            result.userId(), result.username(), result.tenantId(), result.forceResetPwd()));
    }
    @Override public Issued<PlatformBrowserLoginResp> loginPlatform(PlatformLoginReq req) {
        var result = platformAuth.login(req);
        String id = bind(Realm.PLATFORM, result.accessToken(), result.expiresIn());
        return new Issued<>(result.accessToken(), result.expiresIn(), new PlatformBrowserLoginResp(id, result.expiresIn(), result.account()));
    }
    private String bind(Realm realm, String token, long ttl) {
        try {
            if (ttl <= 0) throw new IllegalStateException("浏览器会话必须具有有限期限");
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (realm == Realm.PLATFORM) platformSessions.bindBrowserSession(token, id);
            else StpUtil.getStpLogic().getTokenSessionByToken(token).set(BrowserSessionPolicy.SESSION_ATTRIBUTE, id);
            return id;
        }
        catch (RuntimeException failure) {
            try { revoke(realm, token); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void logout(Realm realm, String token) { revoke(realm, token); }
    private void revoke(Realm realm, String token) {
        if (realm == Realm.PLATFORM) platformSessions.logout(token);
        else StpUtil.logoutByTokenValue(token);
    }
}
