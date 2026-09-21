package cn.ac.fage.accessmesh.access.resource.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 服务依赖 FULL 的发布状态：tenant+source 单行，持代次/不可变请求指纹/语义 hash 与 dirty 标记，由清单发布事务维护。 */
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
