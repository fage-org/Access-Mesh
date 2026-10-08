package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessDeniedException;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard;
import cn.ac.fage.accessmesh.common.security.RedisTenantGateStore;
import cn.ac.fage.accessmesh.common.security.TenantGateSnapshot;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 模拟 Redis 快照恢复了完整旧会话：账号与租户代次仍相同，进程已改变。 */
class TenantNativeSessionTest {
    private SaTokenConfig previousConfig;
    private SaTokenDao previousDao;
    private SaTokenDaoDefaultImpl dao;
    private RedisTenantGateStore gates;
    private String token;

    @BeforeEach
    void setup() {
        previousConfig=SaManager.getConfig(); previousDao=SaManager.getSaTokenDao();
        SaManager.setConfig(new SaTokenConfig().setTimeout(3600).setActiveTimeout(-1).setIsPrint(false));
        dao=new SaTokenDaoDefaultImpl(); SaManager.setSaTokenDao(dao);
        token=StpUtil.getStpLogic().createLoginSession(42L);
        StpUtil.getSessionByLoginId(42L).set("tenantId",2L);
        StpUtil.getStpLogic().getTokenSessionByToken(token).set("tenantEpoch",7L)
            .set("forceResetPwd",false).set("redisProcessId","a".repeat(40));
        gates=mock(RedisTenantGateStore.class);
        when(gates.read(2L)).thenReturn(new TenantGateState(TenantGateState.Status.ENABLED,7));
    }

    @AfterEach
    void restore() {
        SaManager.setConfig(previousConfig); SaManager.setSaTokenDao(previousDao); dao.destroy();
    }

    @Test
    void restoredOldSessionCannotReviveAfterRedisProcessChanges() {
        when(gates.readForSession(2L)).thenReturn(new TenantGateSnapshot(
            new TenantGateState(TenantGateState.Status.ENABLED,7),"b".repeat(40)));
        assertThatThrownBy(()->new TenantAccessGuard(gates).requireNativeSession(2,42,token,"/api/access/notice/my-notices"))
            .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void sessionFromCurrentProcessRemainsUsable() {
        when(gates.readForSession(2L)).thenReturn(new TenantGateSnapshot(
            new TenantGateState(TenantGateState.Status.ENABLED,7),"a".repeat(40)));
        assertThatCode(()->new TenantAccessGuard(gates).requireNativeSession(2,42,token,"/api/access/notice/my-notices"))
            .doesNotThrowAnyException();
    }
}
