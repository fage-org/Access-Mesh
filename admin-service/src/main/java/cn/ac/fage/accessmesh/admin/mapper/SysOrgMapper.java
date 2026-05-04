package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /**
     * Batch soft delete organizations.
     * Sets delete_flag = id (row's own ID) and deleted_at for each organization.
     *
     * @param tenantId  the tenant ID
     * @param ids       the list of organization IDs to delete
     * @param deletedAt the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Use PostgreSQL CTE recursive query to get all descendant organization IDs (excluding self).
     *
     * @param tenantId tenant ID
     * @param orgId    organization ID
     * @return descendant organization ID list
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId,
                                   @Param("orgId") Long orgId);

    /**
     * Use PostgreSQL CTE recursive query to get all descendant organization IDs (including self).
     *
     * @param tenantId tenant ID
     * @param orgId    organization ID
     * @return descendant organization ID list (including self)
     */
    List<Long> selectDescendantIdsIncludingSelf(@Param("tenantId") Long tenantId,
                                                @Param("orgId") Long orgId);

    /**
     * Use PostgreSQL CTE recursive query to get all descendant organization IDs for multiple starting points.
     * Returns a list of DescendantResult objects mapping each orgId to its descendants.
     *
     * @param tenantId tenant ID
     * @param orgIds   set of organization IDs to find descendants for
     * @return list of descendant results (org_id, descendant_id pairs)
     */
    List<DescendantResult> selectBatchDescendantIds(@Param("tenantId") Long tenantId,
                                                    @Param("orgIds") Set<Long> orgIds);

    /**
     * Result class for batch descendant query.
     */
    class DescendantResult {
        private Long orgId;
        private Long descendantId;

        public Long getOrgId() { return orgId; }
        public void setOrgId(Long orgId) { this.orgId = orgId; }
        public Long getDescendantId() { return descendantId; }
        public void setDescendantId(Long descendantId) { this.descendantId = descendantId; }
    }
}