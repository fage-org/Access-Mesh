package cn.ac.fage.accessmesh.access.platform.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.platform.entity.SysUserNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户通知关联数据访问接口
 * <p>
 * 提供用户通知关联表的基础CRUD操作。
 * 用户通知关联定义用户与通知公告的阅读关系。
 * </p>
 */
@Mapper
public interface SysUserNoticeMapper extends BaseMapper<SysUserNotice> {

    /**
     * 根据通知ID和用户ID查询用户通知记录（租户隔离）
     *
     * @param noticeId 通知ID
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return 用户通知关联实体，不存在返回null
     */
    SysUserNotice selectByNoticeAndUser(@Param("noticeId") Long noticeId,
                                        @Param("userId") Long userId,
                                        @Param("tenantId") Long tenantId);

    /**
     * 根据用户ID和通知ID列表批量查询用户通知记录（租户隔离）
     *
     * @param userId    用户ID
     * @param noticeIds 通知ID列表
     * @param tenantId  租户ID
     * @return 用户通知关联实体列表
     */
    List<SysUserNotice> selectByUserAndNoticeIds(@Param("userId") Long userId,
                                                  @Param("noticeIds") List<Long> noticeIds,
                                                  @Param("tenantId") Long tenantId);

    /**
     * 按通知ID批量物理删除已读记录（租户隔离）
     * <p>
     * sys_user_notice 无 delete_flag（DDL 即物理删除形态）；公告删除级联清理
     * （T-ADMIN-029，兑现 NoticeController 历来 javadoc 声称的「同时处理用户已读记录」）。
     * </p>
     *
     * @param tenantId  租户ID
     * @param noticeIds 通知ID列表
     * @return 删除的行数
     */
    int deleteByNoticeIds(@Param("tenantId") Long tenantId, @Param("noticeIds") List<Long> noticeIds);

    /**
     * 标已读幂等 upsert（T-ADMIN-029）
     * <p>
     * 消除并发双 read 先查后插撞 uk_user_notice 唯一键的 500 窗口
     * （ON CONFLICT DO UPDATE 仓库成熟模式）；重复标已读刷新 read_at。
     * </p>
     *
     * @param tenantId 租户ID
     * @param noticeId 通知ID
     * @param userId   用户ID
     * @param readAt   阅读时间
     * @return 影响行数（插入=1/更新=1，PG upsert 恒 1）
     */
    int upsertRead(@Param("tenantId") Long tenantId, @Param("noticeId") Long noticeId,
                   @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}