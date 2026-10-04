package cn.ac.fage.accessmesh.access.it;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** 测试独立实现 Gateway 签名；不封装身份头、Bearer 令牌或请求动作。 */
public final class GatewayTestSignatures {
    private GatewayTestSignatures() {}

    public static String hmac(String secret, String userId, String tenantId, long timestamp) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
