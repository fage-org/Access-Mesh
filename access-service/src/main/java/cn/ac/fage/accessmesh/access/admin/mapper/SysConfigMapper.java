package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.access.admin.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统配置数据访问接口
 * <p>
 * 提供系统配置表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    /**
     * 批量软删除配置
     *
     * @param tenantId  租户ID
     * @param ids       待删除的配置ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 分页查询租户下的配置列表
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SysConfig> selectPageByTenantId(Page<SysConfig> page,
                                         @Param("tenantId") Long tenantId);

    /**
     * 根据ID列表和租户ID查询配置列表
     *
     * @param tenantId 租户ID
     * @param ids      配置ID列表
     * @return 配置列表
     */
    List<SysConfig> selectListByIdsAndTenantId(@Param("tenantId") Long tenantId,
                                               @Param("ids") List<Long> ids);

    /**
     * 根据ID和租户ID查询单条配置
     *
     * @param tenantId 租户ID
     * @param id       配置ID
     * @return 配置实体
     */
    SysConfig selectOneByIdAndTenantId(@Param("tenantId") Long tenantId,
                                       @Param("id") Long id);
}