package cn.ac.fage.accessmesh.access.auth.security;

import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.CallerType;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class PlatformAccountGuard {
    private final PlatformAccountDomainService accounts;
    public PlatformAccountGuard(PlatformAccountDomainService accounts) { this.accounts = accounts; }

    public PlatformActor requireOperator() {
        PlatformAccount account = requireAccount(false);
        return new PlatformActor(account.getId(), account.getUsername());
    }

    public PlatformAccount requireAccount(boolean allowForcedReset) {
        var context = AccessRequestContext.get();
        if (context == null || context.callerType() != CallerType.PLATFORM
            || context.tenantId() != null || context.operatorId() == null) {
            throw new SecurityException("platform identity required");
        }
        PlatformAccount account = accounts.findById(context.operatorId());
        if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
            throw new SecurityException("platform account inactive");
        }
        if (!allowForcedReset && Boolean.TRUE.equals(account.getForceResetPwd())) {
            throw new BizException(AccessErrorCode.PLATFORM_PASSWORD_RESET_REQUIRED.getCode(),
                AccessErrorCode.PLATFORM_PASSWORD_RESET_REQUIRED.getMessage());
        }
        return account;
    }
}
