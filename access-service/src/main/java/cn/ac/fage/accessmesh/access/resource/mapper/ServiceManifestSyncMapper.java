package cn.ac.fage.accessmesh.access.resource.mapper;

import cn.ac.fage.accessmesh.access.resource.entity.ServiceManifestSync;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface ServiceManifestSyncMapper extends BaseMapper<ServiceManifestSync> {
    int markDirtyScopes(@Param("tenantId") Long tenantId, @Param("services") java.util.List<String> services);
    ServiceManifestSync selectScope(@Param("tenantId") Long tenantId, @Param("service") String service);
    int save(@Param("state") ServiceManifestSync state);
}
