package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}