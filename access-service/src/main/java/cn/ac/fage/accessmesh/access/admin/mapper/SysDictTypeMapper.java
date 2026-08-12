package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.access.admin.entity.SysDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统字典类型数据访问接口
 * <p>
 * 提供字典类型表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {

    /**
     * 根据ID和租户ID安全查询单条字典类型
     *
     * @param tenantId 租户ID
     * @param id       字典类型ID
     * @return 字典类型实体，未找到时返回null
     */
    SysDictType selectByIdSafe(@Param("tenantId") Long tenantId,
                               @Param("id") Long id);

    /**
     * 根据ID列表和租户ID查询字典类型列表
     *
     * @param tenantId 租户ID
     * @param ids      字典类型ID列表
     * @return 字典类型列表
     */
    List<SysDictType> selectByIdsAndTenant(@Param("tenantId") Long tenantId,
                                           @Param("ids") List<Long> ids);

    /**
     * 根据租户ID查询所有字典类型（按创建时间正序）
     *
     * @param tenantId 租户ID
     * @return 字典类型列表
     */
    List<SysDictType> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据租户ID分页查询字典类型（按创建时间正序）
     *
     * @param page     分页参数（MyBatis-Flex自动拦截）
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SysDictType> paginateByTenantId(Page<SysDictType> page,
                                         @Param("tenantId") Long tenantId);

    /**
     * 批量软删除字典类型
     * <p>
     * 将指定字典类型的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids      待删除的字典类型ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}