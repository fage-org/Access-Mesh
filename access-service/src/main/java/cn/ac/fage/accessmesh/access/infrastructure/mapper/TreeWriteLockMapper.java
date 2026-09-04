package cn.ac.fage.accessmesh.access.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 树写路径 advisory lock 语句（T-PERM-044，供 {@code TreeWriteLockSupport} 调用）。
 * <p>
 * 经 MyBatis 执行而非 JdbcTemplate：锁语句必须与业务 SQL 共享同一事务连接（MyBatis 的
 * SpringManagedTransaction 复用事务绑定连接），advisory xact 锁才能挂到业务事务上、
 * 随其 commit/rollback 原子释放（JdbcTemplate 直连在事务内拿到的连接 autocommit 未随
 * 事务关闭，锁语句结束即释放——实测互斥失效，故必须走 mapper 与业务 SQL 同连接）。
 * </p>
 */
public interface TreeWriteLockMapper {

    /**
     * 阻塞获取事务级 advisory lock（双 int 键：树段 + 租户段），事务结束由数据库自动释放。
     * 返回值恒为 1（外层 count 仅用于丢弃锁函数的 void 结果列，调用方忽略返回值——
     * MyBatis 对 void 返回类型的 SELECT 会在结果映射时找 void 构造器而失败）。
     */
    @Select("SELECT count(*) FROM (SELECT pg_advisory_xact_lock(#{treeKey}, #{tenantKey})) t")
    Long lockTreeWrites(@Param("treeKey") int treeKey, @Param("tenantKey") int tenantKey);
}
