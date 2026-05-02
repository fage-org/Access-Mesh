package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PermissionConditionMapper extends BaseMapper<PermissionCondition> {

    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}
