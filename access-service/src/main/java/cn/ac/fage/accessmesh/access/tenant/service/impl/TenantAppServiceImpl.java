package cn.ac.fage.accessmesh.access.tenant.service.impl;

import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.bootstrap.BootstrapGraphDefinition;
import cn.ac.fage.accessmesh.access.bootstrap.TenantBaselineInitializer;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.CredentialPasswords;
import cn.ac.fage.accessmesh.access.infrastructure.util.PageUtil;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantAdminPasswordResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantCreateReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantCreatedResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantNameReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantPageReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantStatusReq;
import cn.ac.fage.accessmesh.access.tenant.entity.SysTenant;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAppService;
import cn.ac.fage.accessmesh.access.tenant.service.TenantGateUnavailableException;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.security.RedisTenantGateStore;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.mybatisflex.core.util.UpdateEntity;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 开通和状态变更拥有显式提交边界，数据库与审计先提交，再发布运行时门禁。 */
@Service
public class TenantAppServiceImpl implements TenantAppService {
    private static final Logger log = LoggerFactory.getLogger(TenantAppServiceImpl.class);

    /** 首管理员重置的审计 updated_by：平台编排经种子作用域写目标租户，非租户用户 */
    private static final long PLATFORM_ACTOR_ID = 0L;

    private final TenantDomainService tenants;
    private final PlatformAccountGuard guard;
    private final UserDomainService users;
    private final TenantBaselineInitializer baseline;
    private final AccessBootstrapInitializer graph;
    private final PlatformAuditDomainService audit;
    private final RedisTenantGateStore gates;
    private final LoginFailureStore failures;
    private final TransactionTemplate transaction;

    public TenantAppServiceImpl(TenantDomainService tenants, PlatformAccountGuard guard, UserDomainService users,
                                TenantBaselineInitializer baseline, AccessBootstrapInitializer graph,
                                PlatformAuditDomainService audit, RedisTenantGateStore gates,
                                LoginFailureStore failures, PlatformTransactionManager manager) {
        this.tenants = tenants;
        this.guard = guard;
        this.users = users;
        this.baseline = baseline;
        this.graph = graph;
        this.audit = audit;
        this.gates = gates;
        this.failures = failures;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public PageResp<TenantResp> page(TenantPageReq req) {
        guard.requireOperator();
        int number = PageUtil.pageNum(req.pageNum()), size = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(number, size);
        List<SysTenant> rows = tenants.page(req.keyword(), offset, size);
        Map<Long, TenantGateState> states = readStates(rows.stream().map(SysTenant::getId).toList());
        var items = rows.stream()
            .map(row -> TenantResp.from(row, states.getOrDefault(row.getId(), TenantGateState.unavailable())))
            .toList();
        long total = tenants.count(req.keyword());
        return new PageResp<>(items, total, number, size, PageUtil.hasNext(offset, items.size(), total));
    }

    @Override
    public TenantResp detail(long id) {
        guard.requireOperator();
        SysTenant row = requireTenant(id);
        return TenantResp.from(row, readStates(List.of(id)).getOrDefault(id, TenantGateState.unavailable()));
    }

    @Override
    @OperationLog(module = "ACCESS", action = "TENANT_CREATE", targetType = "sys_tenant",
        targetId = "#req.code()", summary = "'平台操作'")
    public TenantCreatedResp create(TenantCreateReq req) {
        requireOwnTransaction();
        guard.requireOperator();
        String password = CredentialPasswords.generate();
        AtomicReference<RedisTenantGateStore.Publication> reservation = new AtomicReference<>();
        Change change;
        try {
            change = Objects.requireNonNull(transaction.execute(status -> {
                var actor = guard.requireOperator();
                if (tenants.codeExists(req.code())) throw error(AccessErrorCode.TENANT_CODE_EXISTS);
                SysTenant row = new SysTenant();
                row.setCode(req.code());
                row.setName(req.name());
                row.setCreatedBy(actor.id());
                row.setUpdatedBy(actor.id());
                tenants.insert(row);
                reservation.set(reserve(row.getId()));
                baseline.initialize(row.getId());
                graph.initialize(row.getId(), password);
                SysUser admin = users.lockValidByUsername(row.getId(), BootstrapGraphDefinition.ADMIN_USERNAME);
                if (admin == null) throw new IllegalStateException("tenant bootstrap did not create administrator");
                tenants.attachAdmin(row.getId(), admin.getId());
                audit.record(actor, row.getId(), "sys_tenant", String.valueOf(row.getId()),
                    "TENANT_CREATE", "SUCCESS", "租户标准图创建完成");
                return new Change(requireTenant(row.getId()), reservation.get());
            }));
        } catch (RuntimeException exception) {
            if (reservation.get() != null) {
                try {
                    gates.discard(reservation.get());
                } catch (RuntimeException cleanup) {
                    exception.addSuppressed(cleanup);
                }
            }
            if (isTenantCodeConflict(exception)) throw error(AccessErrorCode.TENANT_CODE_EXISTS);
            throw exception;
        }
        publish(change);
        return new TenantCreatedResp(TenantResp.from(change.tenant(), state(change.tenant())),
            BootstrapGraphDefinition.ADMIN_USERNAME, password);
    }

    @Override
    @OperationLog(module = "ACCESS", action = "TENANT_UPDATE", targetType = "sys_tenant",
        targetId = "#req.id()", summary = "'平台操作'")
    public void updateName(TenantNameReq req) {
        requireOwnTransaction();
        guard.requireOperator();
        transaction.executeWithoutResult(status -> {
            tenants.lock(req.id());
            var actor = guard.requireOperator();
            tenants.updateName(req.id(), req.name(), actor.id());
            audit.record(actor, req.id(), "sys_tenant", String.valueOf(req.id()),
                "TENANT_UPDATE", "SUCCESS", "更新租户名称");
        });
    }

    @Override
    @OperationLog(module = "ACCESS", action = "TENANT_STATUS_CHANGE", targetType = "sys_tenant",
        targetId = "#req.id()", summary = "'平台操作'")
    public TenantResp updateStatus(TenantStatusReq req) {
        requireOwnTransaction();
        guard.requireOperator();
        if (req.status() == null || (req.status() != 0 && req.status() != 1)) {
            throw error(AccessErrorCode.ADMIN_INVALID_PARAM);
        }
        Change change = Objects.requireNonNull(transaction.execute(status -> {
            tenants.lock(req.id());
            var actor = guard.requireOperator();
            var publication = reserve(req.id());
            // reserve 后才读取事实，锁查询已清理 MyBatis 会话缓存。
            requireTenant(req.id());
            tenants.updateStatus(req.id(), req.status(), actor.id());
            audit.record(actor, req.id(), "sys_tenant", String.valueOf(req.id()),
                req.status() == 0 ? "TENANT_DISABLE" : "TENANT_ENABLE_REQUESTED", "SUCCESS",
                req.status() == 0 ? "租户停用已提交" : "租户启用已登记，待门禁就绪");
            return new Change(requireTenant(req.id()), publication);
        }));
        publish(change);
        return TenantResp.from(change.tenant(), state(change.tenant()));
    }

    @Override
    @OperationLog(module = "ACCESS", action = "TENANT_ADMIN_PASSWORD_RESET", targetType = "sys_tenant",
        targetId = "#id", summary = "'平台操作'")
    public TenantAdminPasswordResp resetAdminPassword(long id) {
        requireOwnTransaction();
        guard.requireOperator();
        var reset = Objects.requireNonNull(transaction.execute(status -> {
            tenants.lock(id);
            var actor = guard.requireOperator();
            SysTenant tenant = requireTenant(id);
            SysUser admin = tenant.getAdminUserId() == null ? null : users.lockValidById(id, tenant.getAdminUserId());
            if (admin == null) throw error(AccessErrorCode.ADMIN_USER_NOT_FOUND);
            String password = CredentialPasswords.generate();
            var previous = AccessRequestContext.snapshot();
            try {
                AccessRequestContext.bind(
                    RequestContext.task(id).withRequestId(previous == null ? null : previous.requestId()));
                SysUser patch = UpdateEntity.of(SysUser.class);
                patch.setId(admin.getId());
                patch.setPassword(BCrypt.hashpw(password));
                patch.setForceResetPwd(true);
                patch.setUpdatedAt(LocalDateTime.now());
                patch.setUpdatedBy(PLATFORM_ACTOR_ID);
                users.update(patch);
                StpUtil.logout(admin.getId());
            } finally {
                AccessRequestContext.restore(previous);
            }
            audit.record(actor, id, "sys_user", String.valueOf(admin.getId()),
                "TENANT_ADMIN_PASSWORD_RESET", "SUCCESS", "重置租户首管理员凭据");
            return new TenantAdminPasswordResp(admin.getUsername(), password, admin.getStatus());
        }));
        failures.clearTenant(id, reset.username());
        return reset;
    }

    private record Change(SysTenant tenant, RedisTenantGateStore.Publication publication) {}

    private SysTenant requireTenant(long id) {
        SysTenant row = tenants.findById(id);
        if (row == null) throw error(AccessErrorCode.TENANT_NOT_FOUND);
        return row;
    }

    private RedisTenantGateStore.Publication reserve(long id) {
        try {
            return gates.reserve(id);
        } catch (RuntimeException exception) {
            throw new TenantGateUnavailableException(exception);
        }
    }

    private void publish(Change change) {
        TenantGateState expected = state(change.tenant());
        try {
            if (!gates.publish(change.publication(), expected)
                && !gates.read(change.tenant().getId()).equals(expected)) {
                throw new TenantGateUnavailableException();
            }
        } catch (TenantGateUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TenantGateUnavailableException(exception);
        }
    }

    private Map<Long, TenantGateState> readStates(List<Long> ids) {
        try {
            return gates.readBatch(ids);
        } catch (RuntimeException exception) {
            log.warn("Tenant runtime state unavailable for platform view", exception);
            return Map.of();
        }
    }

    private static TenantGateState state(SysTenant row) {
        return new TenantGateState(row.getStatus() == 1 ? TenantGateState.Status.ENABLED : TenantGateState.Status.DISABLED,
            row.getSessionEpoch());
    }

    private static void requireOwnTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("tenant operation must own its commit boundary");
        }
    }

    private static boolean isTenantCodeConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException sql && sql.getServerErrorMessage() != null
                && "uk_sys_tenant_code".equals(sql.getServerErrorMessage().getConstraint())) return true;
        }
        return false;
    }

    private static BizException error(AccessErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }
}
