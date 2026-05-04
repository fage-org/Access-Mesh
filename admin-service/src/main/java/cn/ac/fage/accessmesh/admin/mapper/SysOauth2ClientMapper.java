package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysOauth2ClientMapper extends BaseMapper<SysOauth2Client> {

    /**
     * Batch soft delete OAuth2 clients.
     * Sets delete_flag = 1 and deleted_at for each client.
     *
     * @param ids        the list of client IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
