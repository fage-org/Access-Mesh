package cn.ac.fage.accessmesh.example.data.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class DemoPolicy {
    private Long id;
    private Long tenantId;
    private Long datasetId;
    private Long version;
    private String configuration;
    private String changeSummary;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
