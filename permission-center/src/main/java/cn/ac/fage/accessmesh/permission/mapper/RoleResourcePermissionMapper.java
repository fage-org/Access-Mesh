package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RoleResourcePermissionMapper extends BaseMapper<RoleResourcePermission> {

    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    int cascadeSoftDeleteChildren(@Param("tenantId") Long tenantId,
                                   @Param("parentIds") List<Long> parentIds,
                                   @Param("deletedAt") LocalDateTime deletedAt);
}