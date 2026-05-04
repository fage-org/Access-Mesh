package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {

    /**
     * Batch soft delete dict types.
     * Sets delete_flag = 1 and deleted_at for each dict type.
     *
     * @param ids        the list of dict type IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
