package cn.ac.fage.accessmesh.common.mybatis;

import java.util.Set;

/**
 * 租户ID提供者接口
 * <p>
 * 为租户感知的定时任务提供活跃租户ID集合。
 * 每个服务模块应注册一个实现此接口的Bean，
 * 通常通过查询已知表获取租户列表（如SELECT DISTINCT tenant_id FROM sys_user）。
 * </p>
 */
@FunctionalInterface
public interface TenantIdProvider {

    /**
     * 获取所有活跃租户ID
     * <p>
     * 返回系统中当前活跃的所有租户ID集合。
     * 用于租户感知定时任务为每个租户执行一次业务逻辑。
     * </p>
     *
     * @return 租户ID集合，无租户时返回空集合
     */
    Set<Long> getTenantIds();
}