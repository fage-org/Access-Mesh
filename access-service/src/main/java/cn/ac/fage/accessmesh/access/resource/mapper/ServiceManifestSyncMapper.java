package cn.ac.fage.accessmesh.access.resource.mapper;

import cn.ac.fage.accessmesh.access.resource.entity.ServiceManifestSync;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface ServiceManifestSyncMapper extends BaseMapper<ServiceManifestSync> {
    ServiceManifestSync selectScope(@Param("tenantId") Long tenantId, @Param("service") String service);
    int save(@Param("state") ServiceManifestSync state);
    int markDirty(@Param("tenantId") Long tenantId, @Param("service") String service);
}
