package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.access.admin.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统通知公告数据访问接口
 * <p>
 * 提供通知公告表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 根据ID查询未删除的通知（租户隔离）
     *
     * @param tenantId 租户ID
     * @param id       通知ID
     * @return 通知实体，不存在返回null
     */
    SysNotice selectByIdSafe(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 批量查询未删除的通知（租户隔离）
     *
     * @param tenantId 租户ID
     * @param ids      通知ID列表
     * @return 通知实体列表
     */
    List<SysNotice> selectByIdsSafe(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

    /**
     * 分页查询未删除的通知（租户隔离），按创建时间倒序
     *
     * @param page     分页参数（MyBatis-Flex自动拦截）
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SysNotice> paginateByTenant(@Param("page") Page<SysNotice> page, @Param("tenantId") Long tenantId);

    /**
     * 查询已发布且未删除的通知列表（租户隔离），按创建时间倒序
     *
     * @param tenantId 租户ID
     * @return 通知实体列表
     */
    List<SysNotice> selectPublishedByTenant(@Param("tenantId") Long tenantId);

    /**
     * 批量软删除通知公告
     * <p>
     * 将指定通知的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID，用于数据隔离
     * @param ids       待删除的通知ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}