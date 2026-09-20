package cn.ac.fage.accessmesh.access.resource.mapper;

import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PermissionDependencyDeclarationMapper extends BaseMapper<PermissionDependencyDeclaration> {
    List<PermissionDependencyDeclaration> selectScopes(@Param("tenantId") Long tenantId, @Param("services") List<String> services);
    List<String> selectServicesByResourceIds(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);
    List<String> selectServicesByTypes(@Param("tenantId") Long tenantId, @Param("types") List<String> types);
    int softDeleteTypes(@Param("tenantId") Long tenantId, @Param("types") List<String> types, @Param("now") LocalDateTime now);
    List<PermissionDependencyDeclaration> selectScope(@Param("tenantId") Long tenantId, @Param("service") String service);
    int saveAll(@Param("rows") List<PermissionDependencyDeclaration> rows);
    int softDeleteIds(@Param("tenantId") Long tenantId, @Param("service") String service,
                      @Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
}
