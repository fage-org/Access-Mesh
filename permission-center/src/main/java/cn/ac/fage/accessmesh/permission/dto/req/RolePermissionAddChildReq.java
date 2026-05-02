package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

public record RolePermissionAddChildReq(
    @NotNull Long parentPermissionId,
    @NotEmpty List<@Valid ChildItem> children
) {
    public record ChildItem(
        @NotBlank String resourceTypeCode,
        String resourceCode,
        String codeType,
        @NotBlank String operationCode,
        Boolean scopeAll,
        Boolean canGrant,
        String conditionCode
    ) {}
}
