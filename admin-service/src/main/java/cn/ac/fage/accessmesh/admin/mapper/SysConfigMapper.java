package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    /**
     * Batch soft delete configs.
     * Sets delete_flag = 1 and deleted_at for each config.
     *
     * @param ids        the list of config IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
