package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.dto.CaptchaResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformPasswordReq;
import cn.ac.fage.accessmesh.access.auth.security.LoginChallengeSupport;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.auth.security.PlatformActor;
import cn.ac.fage.accessmesh.access.auth.security.PlatformSessionService;
import cn.ac.fage.accessmesh.access.auth.service.AuthAppService;
import cn.ac.fage.accessmesh.access.auth.service.PlatformAuthAppService;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.CredentialPasswords;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAuthAppServiceImpl implements PlatformAuthAppService {
    private static final Logger log = LoggerFactory.getLogger(PlatformAuthAppServiceImpl.class);
    private final PlatformAccountDomainService accounts;
    private final PlatformAccountGuard guard;
    private final PlatformSessionService sessions;
    private final PlatformAuditDomainService audit;
    private final LoginFailureStore failures;
    private final StringRedisTemplate redis;
    private final AuthAppService authentication;
    public PlatformAuthAppServiceImpl(PlatformAccountDomainService accounts, PlatformAccountGuard guard,
        PlatformSessionService sessions, PlatformAuditDomainService audit, LoginFailureStore failures,
        StringRedisTemplate redis, AuthAppService authentication) {
        this.accounts = accounts;
        this.guard = guard;
        this.sessions = sessions;
        this.audit = audit;
        this.failures = failures;
        this.redis = redis;
        this.authentication = authentication;
    }
    public CaptchaResp captcha() { return authentication.generateCaptcha(); }

    /** 无外层数据库事务；令牌携带本次验密的代次，交错重置不会使旧凭据获权。 */
    public PlatformLoginResp login(PlatformLoginReq req) {
        try {
            LoginChallengeSupport.validate(redis, req.captchaId(), req.captchaCode());
            var account = accounts.findByUsername(req.username());
            if (account == null || account.getPassword() == null || !BCrypt.checkpw(req.password(), account.getPassword())) {
                failures.recordPlatformFailure(req.username());
                throw error(AccessErrorCode.PASSWORD_INCORRECT);
            }
            if (!Integer.valueOf(1).equals(account.getStatus())) throw error(AccessErrorCode.USER_DISABLED);
            if (failures.isPlatformLocked(req.username())) throw error(AccessErrorCode.USER_LOCKED);
            failures.clearPlatform(req.username());
            String token = sessions.issue(account);
            try {
                audit.record(new PlatformActor(account.getId(), account.getUsername()), null, "platform_account",
                    String.valueOf(account.getId()), "PLATFORM_LOGIN", "SUCCESS", "平台登录成功");
            } catch (RuntimeException exception) {
                try { sessions.logout(token); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
                throw exception;
            }
            return new PlatformLoginResp(token, sessions.expiresIn(token), PlatformAccountResp.from(account));
        } catch (BizException exception) {
            try {
                audit.record(null, null, "platform_login", req.username(), "PLATFORM_LOGIN", "FAILURE",
                    "平台登录失败(code=" + exception.getErrorCode() + ")");
            } catch (RuntimeException auditFailure) {
                log.warn("Platform login failure audit unavailable", auditFailure);
            }
            throw exception;
        }
    }
    public void logout(String token) { sessions.logout(token); }
    public PlatformAccountResp me() { return PlatformAccountResp.from(guard.requireAccount(true)); }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @OperationLog(module = "ACCESS", action = "PLATFORM_PASSWORD_CHANGE", targetType = "platform_account",
        targetId = "'self'", summary = "'平台操作'")
    public void changePassword(PlatformPasswordReq req) {
        guard.requireAccount(true);
        accounts.lockManagement();
        var account = guard.requireAccount(true);
        if (!BCrypt.checkpw(req.oldPassword(), account.getPassword())) throw error(AccessErrorCode.PASSWORD_INCORRECT);
        if (!CredentialPasswords.isValid(req.newPassword())) throw error(AccessErrorCode.PASSWORD_TOO_WEAK);
        if (Boolean.TRUE.equals(account.getForceResetPwd()) && BCrypt.checkpw(req.newPassword(), account.getPassword())) {
            throw error(AccessErrorCode.PASSWORD_UNCHANGED);
        }
        accounts.updatePassword(account.getId(), BCrypt.hashpw(req.newPassword()), false, account.getId());
        audit.record(new PlatformActor(account.getId(), account.getUsername()), null, "platform_account",
            String.valueOf(account.getId()), "PLATFORM_PASSWORD_CHANGE", "SUCCESS", "平台账号自助改密");
    }
    private static BizException error(AccessErrorCode code) { return new BizException(code.getCode(), code.getMessage()); }
}
