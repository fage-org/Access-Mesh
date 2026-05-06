package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysJob;

import java.time.LocalDateTime;

/**
 * Job response DTO - hides sensitive fields like invokeTarget, runAsUserId, etc.
 */
public record JobResp(
    Long id,
    String jobName,
    String cronExpression,
    Integer status,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
    // Intentionally NOT exposed:
    // - invokeTarget: internal execution command (security risk)
    // - jobGroup: internal grouping
    // - misfirePolicy: internal policy
    // - runAsUserId: internal execution user
    // - remark: internal notes
    // - tenantId: multi-tenant isolation
    // - createdBy, updatedBy: audit fields
    // - deletedBy, deletedAt, deleteFlag: soft delete fields
) {
    public static JobResp from(SysJob entity) {
        if (entity == null) {
            return null;
        }
        return new JobResp(
            entity.getId(),
            entity.getJobName(),
            entity.getCronExpression(),
            entity.getStatus(),
            entity.getRemark(), // using remark as description
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
