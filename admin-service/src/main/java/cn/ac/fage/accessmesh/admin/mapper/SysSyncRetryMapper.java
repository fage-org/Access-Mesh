package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 根据消息键查询同步重试记录
     *
     * @param tenantId  租户ID
     * @param messageKey 消息键
     * @return 同步重试记录，未找到时返回null
     */
    SysSyncRetry selectByMessageKey(@Param("tenantId") Long tenantId,
                                    @Param("messageKey") String messageKey);

    /**
     * 根据ID安全查询同步重试记录（含租户隔离和删除标记过滤）
     *
     * @param id       主键ID
     * @param tenantId 租户ID
     * @return 同步重试记录，未找到时返回null
     */
    SysSyncRetry selectByIdSafe(@Param("id") Long id,
                                @Param("tenantId") Long tenantId);

    /**
     * 查询待重试的任务列表
     *
     * @param tenantId 租户ID
     * @param now      当前时间
     * @return 待重试任务列表
     */
    List<SysSyncRetry> selectPendingRetries(@Param("tenantId") Long tenantId,
                                            @Param("now") LocalDateTime now);

    /**
     * 分页查询指定租户的同步重试记录，按创建时间倒序排列
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SysSyncRetry> paginateByTenantId(@Param("page") Page<SysSyncRetry> page,
                                          @Param("tenantId") Long tenantId);
}