package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ServiceConfigMapper extends BaseMapper<ServiceConfig> {

    /**
     * Batch soft delete service configs.
     * Sets delete_flag = id and deleted_at for each service config.
     *
     * @param tenantId   the tenant ID
     * @param ids        the list of service config IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
