package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.auth.dto.BrowserLoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformBrowserLoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.LoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginReq;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.Realm;

public interface BrowserAuthAppService {
    /** Internal transport result; controllers return only metadata, never this object. */
    record Issued<T>(String token, long expiresIn, T metadata) {
        @Override public String toString() { return "IssuedBrowserSession[redacted]"; }
    }
    Issued<BrowserLoginResp> login(LoginReq req);
    Issued<PlatformBrowserLoginResp> loginPlatform(PlatformLoginReq req);
    void logout(Realm realm, String token);
}
