package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UserResourceTreeReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    String domainCode,
    List<String> resourceTypeCodes,
    List<String> operationCodes,
    String resourceKeyword
) {}
