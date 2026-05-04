package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysOrgTreeConfigMapper extends BaseMapper<SysOrgTreeConfig> {

    /**
     * Batch soft delete org tree configs.
     * Sets delete_flag = id (row's own ID) and deleted_at for each config.
     *
     * @param tenantId   the tenant ID (optional, may be null for global configs)
     * @param ids        the list of config IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
