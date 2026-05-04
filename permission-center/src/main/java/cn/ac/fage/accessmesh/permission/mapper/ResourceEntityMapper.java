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

    /**
     * Select all ancestor IDs using PostgreSQL CTE recursive query.
     *
     * @param tenantId        tenant ID
     * @param resourceEntityId the resource entity ID to find ancestors for
     * @return list of ancestor IDs (from parent to root, empty if no ancestors or resource not found)
     */
    List<Long> selectAncestorIds(@Param("tenantId") Long tenantId,
                                  @Param("resourceEntityId") Long resourceEntityId);

    /**
     * Batch select all ancestor IDs for multiple resource entities using PostgreSQL CTE recursive query.
     *
     * @param tenantId       tenant ID
     * @param resourceEntityIds the resource entity IDs to find ancestors for
     * @return list of ancestor results with resourceId and ancestorId pairs
     */
    List<AncestorResult> selectAncestorIdsBatch(@Param("tenantId") Long tenantId,
                                                 @Param("resourceEntityIds") java.util.Set<Long> resourceEntityIds);

    /**
     * Batch select all descendant IDs for multiple resource entities using PostgreSQL CTE recursive query.
     *
     * @param tenantId       tenant ID
     * @param resourceEntityIds the resource entity IDs to find descendants for
     * @return list of descendant results with resourceId and descendantId pairs
     */
    List<DescendantResult> selectDescendantIdsBatch(@Param("tenantId") Long tenantId,
                                                     @Param("resourceEntityIds") java.util.Set<Long> resourceEntityIds);

    /**
     * Result class for batch ancestor query
     */
    class AncestorResult {
        private Long resourceId;
        private Long ancestorId;

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public Long getAncestorId() { return ancestorId; }
        public void setAncestorId(Long ancestorId) { this.ancestorId = ancestorId; }
    }

    /**
     * Result class for batch descendant query
     */
    class DescendantResult {
        private Long resourceId;
        private Long descendantId;

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public Long getDescendantId() { return descendantId; }
        public void setDescendantId(Long descendantId) { this.descendantId = descendantId; }
    }
}