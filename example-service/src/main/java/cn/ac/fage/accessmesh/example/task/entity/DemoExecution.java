package cn.ac.fage.accessmesh.example.task.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class DemoExecution {
    private Long id;
    private Long tenantId;
    private java.util.UUID executionKey;
    private Long taskId;
    private Long taskVersion;
    private String triggerKind;
    private java.util.UUID triggerKey;
    private LocalDateTime scheduledTime;
    private String requestPayload;
    private String status;
    private Integer attempt;
    private LocalDateTime nextAttemptAt;
    private Long policyVersion;
    private Long catalogVersion;
    private String outputColumns;
    private Long resultRowCount;
    private byte[] resultBytes;
    private LocalDateTime expiresAt;
    private String failureReason;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
