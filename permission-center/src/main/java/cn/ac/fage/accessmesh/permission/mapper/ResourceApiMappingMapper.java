package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源API映射数据访问接口
 * <p>
 * 提供资源API映射表的基础CRUD操作和自定义查询方法。
 * 资源API映射定义了资源与API接口的对应关系，用于Gateway权限校验。
 * 支持批量软删除操作。
 * </p>
 */
public interface ResourceApiMappingMapper extends BaseMapper<ResourceApiMapping> {

    /**
     * 批量软删除资源API映射
     * <p>
     * 将指定映射的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的映射ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}