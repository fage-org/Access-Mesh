package cn.ac.fage.accessmesh.access.support;

import cn.ac.fage.accessmesh.access.auth.service.impl.OAuth2AppServiceImpl.AuthCodeData;
import cn.ac.fage.accessmesh.access.auth.service.impl.OAuth2AppServiceImpl.RefreshTokenData;
import cn.dev33.satoken.secure.BCrypt;
import java.util.HashMap;
import java.util.Map;

/** 测试有效凭据的公共前置数据；业务身份与待测授权字段由各用例自行设置。 */
public final class OAuth2CredentialFixtures {
    public static final String PASSWORD_HASH = BCrypt.hashpw("test-user-password");
    private OAuth2CredentialFixtures() { }

    public static AuthCodeData authCode() {
        var data = new AuthCodeData();
        data.setTenantEpoch(1L);
        data.setPasswordFingerprint(PASSWORD_HASH.substring(0, 29));
        data.setChainIssuedAt(System.currentTimeMillis() / 1000);
        data.setChainExpiresAt(data.getChainIssuedAt() + 604800);
        return data;
    }

    public static RefreshTokenData refreshToken() {
        var data = new RefreshTokenData();
        data.setTenantEpoch(1L);
        data.setPasswordFingerprint(PASSWORD_HASH.substring(0, 29));
        data.setChainIssuedAt(System.currentTimeMillis() / 1000);
        data.setChainExpiresAt(data.getChainIssuedAt() + 604800);
        return data;
    }

    public static Map<String, Object> claims(Map<String, Object> claims) {
        Map<String, Object> result = new HashMap<>(claims);
        long now = System.currentTimeMillis() / 1000;
        result.put("tenant_epoch", "1");
        result.put("pwd_generation", PASSWORD_HASH.substring(0, 29));
        result.put("chain_iat", now);
        result.put("chain_exp", now + 604800);
        return result;
    }
}
