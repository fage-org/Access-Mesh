package cn.ac.fage.accessmesh.access.resource.mapper;

import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PermissionDependencyDeclarationMapper extends BaseMapper<PermissionDependencyDeclaration> {
    List<PermissionDependencyDeclaration> selectScope(@Param("tenantId") Long tenantId, @Param("service") String service);
    int saveAll(@Param("rows") List<PermissionDependencyDeclaration> rows);
    int softDeleteIds(@Param("tenantId") Long tenantId, @Param("service") String service,
                      @Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
}
