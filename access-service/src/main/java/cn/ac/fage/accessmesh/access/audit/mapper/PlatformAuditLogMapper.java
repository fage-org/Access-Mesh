package cn.ac.fage.accessmesh.access.audit.mapper;

import cn.ac.fage.accessmesh.access.audit.entity.PlatformAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PlatformAuditLogMapper {
    int insert(PlatformAuditLog log);
    List<PlatformAuditLog> page(@Param("tenantId") Long tenantId, @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("tenantId") Long tenantId);
}
