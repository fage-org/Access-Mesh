package cn.ac.fage.accessmesh.access.audit.mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
public interface AuditRetentionMapper {
    int deleteOperations(@Param("tenantId") Long tenantId, @Param("cutoff") LocalDateTime cutoff);
    int deleteChanges(@Param("tenantId") Long tenantId, @Param("cutoff") LocalDateTime cutoff);
    int deleteLogins(@Param("tenantId") Long tenantId, @Param("cutoff") LocalDateTime cutoff);
}
