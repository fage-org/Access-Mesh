package cn.ac.fage.accessmesh.example.metadata.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Persisted event and delivery state; the payload is frozen by the domain write path. */
@Getter
@Setter
public class DemoPublication {
    private Long id;
    private Long tenantId;
    private String resourceTypeCode;
    private String resourceCode;
    private String codeType;
    private Long sourceVersion;
    private LocalDateTime occurredAt;
    private String payload;
    private String status;
    private Integer attempt;
    private LocalDateTime nextAttemptAt;
    private String failureReason;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public DemoPublication() {}

    public DemoPublication(Long id, long tenantId, String resourceTypeCode, String resourceCode,
                           String codeType, long sourceVersion, LocalDateTime occurredAt, String payload,
                           String status, int attempt, LocalDateTime nextAttemptAt, String failureReason, Long createdBy) {
        this.id=id; this.tenantId=tenantId; this.resourceTypeCode=resourceTypeCode;
        this.resourceCode=resourceCode; this.codeType=codeType; this.sourceVersion=sourceVersion;
        this.occurredAt=occurredAt; this.payload=payload; this.status=status; this.attempt=attempt;
        this.nextAttemptAt=nextAttemptAt; this.failureReason=failureReason; this.createdBy=createdBy;
    }
}
