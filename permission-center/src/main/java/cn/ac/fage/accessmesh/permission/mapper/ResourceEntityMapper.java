package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ResourceEntityMapper extends BaseMapper<ResourceEntity> {

    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Select all descendant IDs (excluding self) using PostgreSQL CTE recursive query.
     *
     * @param tenantId        tenant ID
     * @param resourceEntityId the resource entity ID to find descendants for
     * @return list of descendant IDs (empty if no descendants or resource not found)
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId,
                                   @Param("resourceEntityId") Long resourceEntityId);

    /**
     * Select all descendant IDs including self using PostgreSQL CTE recursive query.
     *
     * @param tenantId        tenant ID
     * @param resourceEntityId the resource entity ID to find descendants for
     * @return list of descendant IDs including the resource itself
     */
    List<Long> selectDescendantIdsIncludingSelf(@Param("tenantId") Long tenantId,
                                                @Param("resourceEntityId") Long resourceEntityId);
}