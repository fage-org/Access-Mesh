package cn.ac.fage.accessmesh.example.metadata.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class DemoReport {
    private Long id;
    private Long tenantId;
    private Long datasetId;
    private String reportCode;
    private String name;
    private String fieldResourceTypeCode;
    private Boolean enabled;
    private Long version;
    private Long catalogVersion;
    private String publicationStatus;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
