package cn.ac.fage.accessmesh.access.resource.mapper;

import cn.ac.fage.accessmesh.access.resource.entity.ServiceManifestSync;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface ServiceManifestSyncMapper extends BaseMapper<ServiceManifestSync> {
    int markDirtyScopes(@Param("tenantId") Long tenantId, @Param("services") java.util.List<String> services);
    ServiceManifestSync selectScope(@Param("tenantId") Long tenantId, @Param("service") String service);
    int save(@Param("state") ServiceManifestSync state);

    /** 租户全部有效发布状态行（T-PERM-073 声明诊断 declaration-status）。 */
    java.util.List<ServiceManifestSync> selectByTenantId(@Param("tenantId") Long tenantId);
}
