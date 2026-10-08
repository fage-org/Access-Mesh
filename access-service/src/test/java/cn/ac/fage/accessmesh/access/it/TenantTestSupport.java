package cn.ac.fage.accessmesh.access.it;

import cn.ac.fage.accessmesh.access.tenant.entity.SysTenant;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard;
import cn.ac.fage.accessmesh.common.security.TenantGateState;

/** 非租户生命周期测试的明确前提：已开通、启用且代次为 1；生命周期本身由真实链验证。 */
public final class TenantTestSupport {
    public static final String CODE="fixture-tenant";
    private TenantTestSupport() {}
    public static TenantAccessGuard activeGuard() {
        var guard=org.mockito.Mockito.mock(TenantAccessGuard.class);
        org.mockito.Mockito.lenient().when(guard.requireEnabled(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(new TenantGateState(TenantGateState.Status.ENABLED,1));
        org.mockito.Mockito.lenient().when(guard.captureSessionEpoch(org.mockito.ArgumentMatchers.anyLong())).thenReturn(1L);
        return guard;
    }
    public static SysTenant activeTenant() {
        SysTenant tenant=new SysTenant();
        tenant.setId(1L); tenant.setCode(CODE); tenant.setName("Fixture tenant"); tenant.setStatus(1); tenant.setSessionEpoch(1L);
        return tenant;
    }
    /** 非生命周期 PG 测试显式登记的已启用租户前提，不修改生产兜底。 */
    public static void enableFixture(org.springframework.jdbc.core.JdbcTemplate jdbc,
                                     org.springframework.data.redis.core.StringRedisTemplate redis,long tenantId) {
        String code=tenantId==1?CODE:CODE+"-"+tenantId;
        jdbc.update("INSERT INTO sys_tenant(id,code,name,status,session_epoch) VALUES (?,?,?,1,1) ON CONFLICT (id) DO NOTHING",tenantId,code,"Fixture");
        var gates=new cn.ac.fage.accessmesh.common.security.RedisTenantGateStore(redis);
        var publication=gates.reserve(tenantId);
        if(!gates.publish(publication,new TenantGateState(TenantGateState.Status.ENABLED,1))) throw new IllegalStateException("fixture gate publication failed");
    }
    public static void stampSession(String token,org.springframework.data.redis.core.StringRedisTemplate redis,long tenantId) {
        cn.dev33.satoken.stp.StpUtil.getStpLogic().getTokenSessionByToken(token)
            .set("tenantEpoch",1L).set("forceResetPwd",false)
            .set("redisProcessId",new cn.ac.fage.accessmesh.common.security.RedisTenantGateStore(redis).readForSession(tenantId).redisProcessId());
    }
}
