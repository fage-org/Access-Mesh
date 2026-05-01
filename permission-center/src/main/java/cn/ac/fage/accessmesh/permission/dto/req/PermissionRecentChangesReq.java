package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PermissionRecentChangesReq(
    @NotBlank String targetType,
    String subjectTypeCode,
    String subjectExternalId,
    String roleTypeCode,
    String roleExternalId,
    String domainCode,
    java.time.LocalDateTime since,
    java.time.LocalDateTime until,
    List<String> eventTypes,
    Integer pageNum,
    Integer pageSize
) {}
