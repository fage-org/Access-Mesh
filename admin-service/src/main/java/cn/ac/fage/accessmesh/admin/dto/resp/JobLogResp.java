package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysJobLog;

import java.time.LocalDateTime;

/**
 * Job log response DTO - hides sensitive fields like invokeTarget, message (error stack traces), etc.
 */
public record JobLogResp(
    Long id,
    Long jobId,
    String jobName,
    Integer status,
    Integer costTime,
    LocalDateTime createdAt
    // Intentionally NOT exposed:
    // - invokeTarget: internal execution command (security risk)
    // - message: may contain error stack traces (security risk)
    // - tenantId: multi-tenant isolation
) {
    public static JobLogResp from(SysJobLog entity) {
        if (entity == null) {
            return null;
        }
        return new JobLogResp(
            entity.getId(),
            entity.getJobId(),
            entity.getJobName(),
            entity.getStatus(),
            entity.getCostTime(),
            entity.getCreatedAt()
        );
    }
}
