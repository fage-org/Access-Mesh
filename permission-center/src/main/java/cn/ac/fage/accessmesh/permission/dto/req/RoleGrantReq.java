package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record RoleGrantReq(
    String domainCode,
    @NotBlank(message = "角色类型不能为空")
    String roleTypeCode,
    @NotBlank(message = "角色外部标识不能为空")
    String roleExternalId,
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
