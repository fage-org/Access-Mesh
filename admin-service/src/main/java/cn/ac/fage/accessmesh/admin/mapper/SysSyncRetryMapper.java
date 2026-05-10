package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步重试数据访问接口
 * <p>
 * 提供同步重试表的基础CRUD操作。
 * 同步重试记录跨服务数据同步失败的任务，支持后续重试。
 * 用于保证分布式系统中数据的一致性。
 * </p>
 */
@Mapper
public interface SysSyncRetryMapper extends BaseMapper<SysSyncRetry> {
}