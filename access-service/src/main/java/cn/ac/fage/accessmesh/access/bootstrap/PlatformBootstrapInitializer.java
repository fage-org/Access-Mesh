package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.util.CredentialPasswords;
import cn.dev33.satoken.secure.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 首次安装仅创建独立平台账号；不创建租户或覆盖存量凭据。 */
@Component
public class PlatformBootstrapInitializer {
    private final PlatformAccountDomainService accounts;
    private final PlatformAuditDomainService audit;
    public PlatformBootstrapInitializer(PlatformAccountDomainService accounts, PlatformAuditDomainService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void initialize(String username, String password) {
        accounts.lockManagement();
        if (accounts.count() > 0) return;
        if (username == null || username.isBlank() || username.length() > 64) {
            throw new IllegalStateException("ACCESS_PLATFORM_ADMIN_USERNAME must contain 1-64 characters");
        }
        if (!CredentialPasswords.isValid(password)) {
            throw new IllegalStateException("ACCESS_PLATFORM_ADMIN_PASSWORD must contain 8-32 characters, including letters and digits");
        }
        PlatformAccount account = new PlatformAccount();
        account.setUsername(username);
        account.setName(username);
        account.setPassword(BCrypt.hashpw(password));
        account.setForceResetPwd(false);
        accounts.insert(account);
        audit.record(null, null, "platform_account", String.valueOf(account.getId()),
            "PLATFORM_BOOTSTRAP", "SUCCESS", "首次安装创建平台管理员");
    }
}
