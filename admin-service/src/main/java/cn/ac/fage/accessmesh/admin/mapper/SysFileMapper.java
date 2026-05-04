package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /**
     * Batch soft delete files.
     * Sets delete_flag = 1 and deleted_at for each file.
     *
     * @param tenantId   the tenant ID for isolation
     * @param ids        the list of file IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
