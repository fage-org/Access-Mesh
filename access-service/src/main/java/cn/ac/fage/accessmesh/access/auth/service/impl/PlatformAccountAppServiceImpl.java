package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformStatusReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountCreateReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountCreatedResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountNameReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountResp;
import cn.ac.fage.accessmesh.access.auth.dto.IssuedPasswordResp;
import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.security.PlatformActor;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.auth.service.PlatformAccountAppService;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.CredentialPasswords;
import cn.ac.fage.accessmesh.access.infrastructure.util.PageUtil;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.dev33.satoken.secure.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PlatformAccountAppServiceImpl implements PlatformAccountAppService {
    private final PlatformAccountDomainService accounts;
    private final PlatformAccountGuard guard;
    private final PlatformAuditDomainService audit;
    private final LoginFailureStore failures;
    private final TransactionTemplate recoveryTransaction;

    public PlatformAccountAppServiceImpl(PlatformAccountDomainService accounts, PlatformAccountGuard guard,
                                         PlatformAuditDomainService audit, LoginFailureStore failures,
                                         PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.guard = guard;
        this.audit = audit;
        this.failures = failures;
        this.recoveryTransaction = new TransactionTemplate(transactionManager);
        this.recoveryTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @cn.ac.fage.accessmesh.access.audit.aop.OperationLog(module="ACCESS",action="PLATFORM_ACCOUNT_STATUS_CHANGE",targetType="platform_account",targetId="#req.id()",summary="'平台操作'")
    public void updateStatus(PlatformStatusReq req) {
        var actor = lockAndRequireOperator();
        if (req.status() == null || (req.status() != 0 && req.status() != 1)) throw error(AccessErrorCode.ADMIN_INVALID_PARAM);
        PlatformAccount target = requireTarget(req.id());
        if (req.status().equals(target.getStatus())) return;
        if (req.status() == 0 && Integer.valueOf(1).equals(target.getStatus()) && accounts.countEnabled() <= 1) {
            throw error(AccessErrorCode.PLATFORM_LAST_ADMIN);
        }
        accounts.updateStatus(req.id(), req.status(), actor.id());
        audit.record(actor, null, "platform_account", String.valueOf(req.id()),
            "PLATFORM_ACCOUNT_STATUS_CHANGE", "SUCCESS", "平台账号状态变更");
    }

    @Override
    public PageResp<PlatformAccountResp> page(PageReq req) {
        guard.requireOperator();
        if (req.sort() != null && !req.sort().isBlank()) throw error(AccessErrorCode.ADMIN_INVALID_PARAM);
        int number = PageUtil.pageNum(req.pageNum()), size = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(number, size);
        var items = accounts.page(offset, size).stream().map(PlatformAccountResp::from).toList();
        long total = accounts.count();
        return new PageResp<>(items, total, number, size, PageUtil.hasNext(offset, items.size(), total));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @cn.ac.fage.accessmesh.access.audit.aop.OperationLog(module="ACCESS",action="PLATFORM_ACCOUNT_CREATE",targetType="platform_account",targetId="#req.username()",summary="'平台操作'")
    public PlatformAccountCreatedResp create(PlatformAccountCreateReq req) {
        var actor = lockAndRequireOperator();
        if (accounts.findByUsername(req.username()) != null) throw error(AccessErrorCode.PLATFORM_ACCOUNT_EXISTS);
        String password = CredentialPasswords.generate();
        PlatformAccount account = new PlatformAccount();
        account.setUsername(req.username());
        account.setName(req.name());
        account.setPassword(BCrypt.hashpw(password));
        account.setForceResetPwd(true);
        account.setCreatedBy(actor.id());
        account.setUpdatedBy(actor.id());
        accounts.insert(account);
        audit.record(actor, null, "platform_account", String.valueOf(account.getId()),
            "PLATFORM_ACCOUNT_CREATE", "SUCCESS", "创建平台账号");
        return new PlatformAccountCreatedResp(PlatformAccountResp.from(requireTarget(account.getId())), password);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @cn.ac.fage.accessmesh.access.audit.aop.OperationLog(module="ACCESS",action="PLATFORM_ACCOUNT_UPDATE",targetType="platform_account",targetId="#req.id()",summary="'平台操作'")
    public void updateName(PlatformAccountNameReq req) {
        var actor = lockAndRequireOperator();
        requireTarget(req.id());
        accounts.updateName(req.id(), req.name(), actor.id());
        audit.record(actor, null, "platform_account", String.valueOf(req.id()),
            "PLATFORM_ACCOUNT_UPDATE", "SUCCESS", "更新平台账号名称");
    }

    @Override
    @cn.ac.fage.accessmesh.access.audit.aop.OperationLog(module="ACCESS",action="PLATFORM_ACCOUNT_PASSWORD_RESET",targetType="platform_account",targetId="#accountId",summary="'平台操作'")
    public IssuedPasswordResp resetPassword(long accountId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("password recovery must own its commit boundary");
        }
        var result = recoveryTransaction.execute(status -> {
            var actor = lockAndRequireOperator();
            PlatformAccount target = requireTarget(accountId);
            String password = CredentialPasswords.generate();
            accounts.updatePassword(accountId, BCrypt.hashpw(password), true, actor.id());
            audit.record(actor, null, "platform_account", String.valueOf(accountId),
                "PLATFORM_ACCOUNT_PASSWORD_RESET", "SUCCESS", "重置平台账号密码");
            return new Recovery(target.getUsername(), password);
        });
        java.util.Objects.requireNonNull(result, "password recovery result");
        // 必须等数据库与审计提交；解除临时锁失败时向调用方报告失败，允许显式重试恢复。
        failures.clearPlatform(result.username());
        return new IssuedPasswordResp(result.password());
    }

    private record Recovery(String username, String password) {}

    private PlatformActor lockAndRequireOperator() {
        guard.requireOperator();
        accounts.lockManagement();
        // 等锁期间操作者可能已被另一管理员停用或重置，不能继续沿用锁前状态。
        return guard.requireOperator();
    }

    private PlatformAccount requireTarget(long id) {
        PlatformAccount target = accounts.findById(id);
        if (target == null) throw error(AccessErrorCode.PLATFORM_ACCOUNT_NOT_FOUND);
        return target;
    }

    private static BizException error(AccessErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }
}
