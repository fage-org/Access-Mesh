package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ResourceDependencyMapper extends BaseMapper<ResourceDependency> {

    /**
     * Batch soft delete resource dependencies.
     * Sets delete_flag = id and deleted_at for each resource dependency.
     *
     * @param tenantId   the tenant ID
     * @param ids        the list of resource dependency IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Batch soft delete by owner service code and maintain source (for full sync).
     * Sets delete_flag = id and deleted_at for each matching resource dependency.
     *
     * @param tenantId        the tenant ID
     * @param ownerServiceCode the owner service code
     * @param maintainSource   the maintain source
     * @param deletedAt        the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatchByOwnerService(@Param("tenantId") Long tenantId,
                                       @Param("ownerServiceCode") String ownerServiceCode,
                                       @Param("maintainSource") String maintainSource,
                                       @Param("deletedAt") LocalDateTime deletedAt);
}
