package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BizDomainMapper extends BaseMapper<BizDomain> {

    /**
     * Batch soft delete biz domains.
     * Sets delete_flag = id and deleted_at for each biz domain.
     *
     * @param tenantId   the tenant ID
     * @param ids        the list of biz domain IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
