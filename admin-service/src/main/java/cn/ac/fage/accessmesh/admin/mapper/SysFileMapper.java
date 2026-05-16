package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.admin.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统文件数据访问接口
 * <p>
 * 提供文件表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /**
     * 根据ID查询有效文件（租户隔离+未删除）
     *
     * @param tenantId 租户ID
     * @param id       文件ID
     * @return 文件实体，不存在返回null
     */
    SysFile selectValidById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 批量查询有效文件（租户隔离+未删除）
     *
     * @param tenantId 租户ID
     * @param ids      文件ID列表
     * @return 有效文件列表
     */
    List<SysFile> selectValidByIds(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

    /**
     * 分页查询文件列表
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @param bizType  业务类型过滤条件，可选
     * @return 分页结果
     */
    Page<SysFile> paginateFiles(@Param("page") Page<SysFile> page,
                                @Param("tenantId") Long tenantId,
                                @Param("bizType") String bizType);

    /**
     * 批量软删除文件
     * <p>
     * 将指定文件的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID，用于数据隔离
     * @param ids       待删除的文件ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}