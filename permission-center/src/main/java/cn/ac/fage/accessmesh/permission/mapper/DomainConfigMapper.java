package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 域配置数据访问接口
 * <p>
 * 提供域配置表的基础CRUD操作和自定义查询方法。
 * 域配置存储各业务域的特定配置项，如权限策略、同步规则等。
 * 支持批量软删除操作。
 * </p>
 */
public interface DomainConfigMapper extends BaseMapper<DomainConfig> {

    /**
     * 批量软删除域配置
     * <p>
     * 将指定域配置的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的域配置ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}