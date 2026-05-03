package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ResourceApiMappingMapper extends BaseMapper<ResourceApiMapping> {

    /**
     * Batch soft delete resource API mappings.
     * Sets delete_flag = id and deleted_at for each mapping.
     *
     * @param tenantId   the tenant ID
     * @param ids        the list of mapping IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
