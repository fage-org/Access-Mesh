package cn.ac.fage.accessmesh.access.tenant.service;

import cn.ac.fage.accessmesh.common.security.RedisTenantGateStore;
import cn.ac.fage.accessmesh.common.security.TenantGateSnapshot;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import cn.ac.fage.accessmesh.common.security.TenantSessionStamp;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class TenantAccessGuard {
    private final RedisTenantGateStore gates;

    public TenantAccessGuard(RedisTenantGateStore gates) {
        this.gates = gates;
    }

    public TenantGateState requireEnabled(long tenantId) {
        TenantGateState state;
        try {
            state = gates.read(tenantId);
        } catch (RuntimeException exception) {
            throw new TenantGateUnavailableException(exception);
        }
        if (state.status() == TenantGateState.Status.UNAVAILABLE) throw new TenantGateUnavailableException();
        if (state.status() == TenantGateState.Status.DISABLED) throw TenantAccessDeniedException.disabled();
        return state;
    }

    public void requireEpoch(long tenantId, Long epoch) {
        if (epoch == null || epoch <= 0) throw TenantAccessDeniedException.expired();
        if (!requireEnabled(tenantId).permitsSession(epoch)) throw TenantAccessDeniedException.expired();
    }

    /** 登录签发与原生请求均原子读取门禁和进程，防止旧持久化会话跨 Redis 重启复活。 */
    public String captureLoginProcess(long tenantId, Long epoch) {
        if (epoch == null || epoch <= 0) throw TenantAccessDeniedException.expired();
        var snapshot = requireSessionGate(tenantId);
        if (!snapshot.state().permitsSession(epoch)) throw TenantAccessDeniedException.expired();
        return snapshot.redisProcessId();
    }

    private TenantGateSnapshot requireSessionGate(long tenantId) {
        TenantGateSnapshot snapshot;
        try {
            snapshot = gates.readForSession(tenantId);
        } catch (RuntimeException exception) {
            throw new TenantGateUnavailableException(exception);
        }
        if (snapshot.state().status() == TenantGateState.Status.UNAVAILABLE) throw new TenantGateUnavailableException();
        if (snapshot.state().status() == TenantGateState.Status.DISABLED) throw TenantAccessDeniedException.disabled();
        return snapshot;
    }

    public void requireNativeSession(long tenantId, long userId, String token, String path) {
        if (token == null || token.isBlank()) throw TenantAccessDeniedException.expired();
        var logic = StpUtil.getStpLogic();
        Object actualUser = logic.getLoginIdByToken(token);
        if (actualUser == null || !String.valueOf(userId).equals(actualUser.toString())
            || logic.getTokenActiveTimeoutByToken(token) == SaTokenDao.NOT_VALUE_EXPIRE) {
            throw TenantAccessDeniedException.expired();
        }
        var account = logic.getSessionByLoginId(String.valueOf(userId), false);
        Object actualTenant = account == null ? null : account.get("tenantId");
        if (actualTenant == null || !String.valueOf(tenantId).equals(actualTenant.toString())) {
            throw TenantAccessDeniedException.expired();
        }
        var tokenSession = logic.getTokenSessionByToken(token, false);
        var stamp = tokenSession == null ? null : TenantSessionStamp.from(
            tokenSession.get(TenantSessionStamp.EPOCH), tokenSession.get(TenantSessionStamp.FORCE_RESET),
            tokenSession.get(TenantSessionStamp.PROCESS));
        if (stamp == null) throw TenantAccessDeniedException.expired();
        var snapshot = requireSessionGate(tenantId);
        if (!snapshot.state().permitsSession(stamp.epoch()) || !stamp.redisProcessId().equals(snapshot.redisProcessId())) {
            throw TenantAccessDeniedException.expired();
        }
        if (stamp.forceResetPwd() && !TenantSessionStamp.allowsForcedReset(path)) {
            throw TenantAccessDeniedException.passwordChangeRequired();
        }
    }

    public void requireSignedUser(long tenantId, long userId, String bearer, String path) {
        if (bearer == null) requireEnabled(tenantId);
        else requireNativeSession(tenantId, userId, bearer, path);
    }

    public long captureSessionEpoch(long tenantId) {
        String token = StpUtil.getTokenValue();
        requireNativeSession(tenantId, StpUtil.getLoginIdAsLong(), token, "/api/access/auth/oauth2/authorize");
        var session = StpUtil.getStpLogic().getTokenSession(false);
        var stamp = session == null ? null : TenantSessionStamp.from(
            session.get(TenantSessionStamp.EPOCH), session.get(TenantSessionStamp.FORCE_RESET),
            session.get(TenantSessionStamp.PROCESS));
        if (stamp == null) throw TenantAccessDeniedException.expired();
        return stamp.epoch();
    }
}
