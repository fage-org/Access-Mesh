package cn.ac.fage.accessmesh.access.auth.security;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

/** 平台与租户登录共用一次性验证码消费，不改变现有验证码生成方式。 */
public final class LoginChallengeSupport {
    public static final String CAPTCHA_PREFIX = "captcha:";
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
        "local value=redis.call('GET',KEYS[1]); if value then redis.call('DEL',KEYS[1]); end; return value", String.class);
    private LoginChallengeSupport() {}
    public static void validate(StringRedisTemplate redis, String captchaId, String code) {
        if (captchaId == null || code == null) throw new BizException(AccessErrorCode.CAPTCHA_INCORRECT.getCode(), "验证码参数缺失");
        String stored = redis.execute(CONSUME, List.of(CAPTCHA_PREFIX + captchaId));
        if (stored == null || !stored.equalsIgnoreCase(code)) {
            throw new BizException(AccessErrorCode.CAPTCHA_INCORRECT.getCode(), AccessErrorCode.CAPTCHA_INCORRECT.getMessage());
        }
    }
}
