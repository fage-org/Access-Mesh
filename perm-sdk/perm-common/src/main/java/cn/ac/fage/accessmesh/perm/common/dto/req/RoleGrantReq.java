package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Shared: batch grant permissions to a role.
 */
public record RoleGrantReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    List<GrantAddItem> add,
    List<GrantUpdateItem> update,
    List<Long> remove
) {
    public record GrantAddItem(
        @NotBlank String resourceTypeCode,
        String resourceCode,
        String codeType,
        @NotBlank String operationCode,
        Boolean scopeAll,
        Boolean canGrant,
        String conditionCode
    ) {}

    public record GrantUpdateItem(
        Long id,
        Boolean canGrant,
        String conditionCode
    ) {}
}
