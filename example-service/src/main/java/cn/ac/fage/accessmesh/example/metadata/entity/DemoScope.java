package cn.ac.fage.accessmesh.example.metadata.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class DemoScope {
    private Long id;
    private Long tenantId;
    private String resourceTypeCode;
    private String resourceCode;
    private String codeType;
    private String name;
    private Boolean enabled;
    private Long version;
    private String publicationStatus;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
