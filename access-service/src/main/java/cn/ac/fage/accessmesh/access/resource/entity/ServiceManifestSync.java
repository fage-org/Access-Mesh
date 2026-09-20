package cn.ac.fage.accessmesh.access.resource.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 依赖声明发布持久事实，事务由应用服务管理。 */
@Getter
@Setter
@Table("service_manifest_sync")
public class ServiceManifestSync {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private String sourceService;
    private Long publicationGeneration;
    private String revision;
    private String payloadHash;
    private String semanticHash;
    private String syncStatus;
    private Boolean isDirty;
    private LocalDateTime lastSyncedAt;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deleteFlag;
}
