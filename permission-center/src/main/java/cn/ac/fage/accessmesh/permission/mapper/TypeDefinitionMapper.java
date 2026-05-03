package cn.ac.fage.accessmesh.permission.mapper;

import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TypeDefinitionMapper extends BaseMapper<TypeDefinition> {

    /**
     * Batch soft delete type definitions.
     * Sets delete_flag = id and deleted_at for each type definition.
     *
     * @param tenantId   the tenant ID
     * @param ids        the list of type definition IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
