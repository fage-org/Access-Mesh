package cn.ac.fage.accessmesh.access.sync.mapper;

import cn.ac.fage.accessmesh.access.sync.metadata.ResourcePublicationState;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface ResourcePublicationStateMapper extends BaseMapper<ResourcePublicationState> {
    ResourcePublicationState selectScope(@Param("tenantId") Long tenantId, @Param("service") String service,
                                         @Param("scopeHash") String scopeHash);
    int acceptSingle(@Param("tenantId") Long tenantId, @Param("service") String service,
                     @Param("scopeKey") String scopeKey, @Param("scopeHash") String scopeHash, @Param("generation") long generation);
    int acceptFull(@Param("tenantId") Long tenantId, @Param("service") String service,
                   @Param("scopeKey") String scopeKey, @Param("scopeHash") String scopeHash, @Param("generation") long generation,
                   @Param("payloadHash") String payloadHash, @Param("status") String status);
}
