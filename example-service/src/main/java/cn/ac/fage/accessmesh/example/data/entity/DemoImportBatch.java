package cn.ac.fage.accessmesh.example.data.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class DemoImportBatch {
    private Long id;
    private Long tenantId;
    private Long reportId;
    private java.util.UUID importKey;
    private String fileSha256;
    private String importedFields;
    private Long recordCount;
    private Long policyVersion;
    private Long catalogVersion;
    private String status;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
