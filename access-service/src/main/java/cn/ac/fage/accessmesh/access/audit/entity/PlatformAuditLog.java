package cn.ac.fage.accessmesh.access.audit.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

@Getter
@Setter
@Table("platform_audit_log")
public class PlatformAuditLog {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long operatorId;
    private String operatorName;
    private Long targetTenantId;
    private String targetType;
    private String targetId;
    private String action;
    private String outcome;
    private String summary;
    private String requestId;
    private String ipAddress;
    private String requestUrl;
    private LocalDateTime createdAt;
}
