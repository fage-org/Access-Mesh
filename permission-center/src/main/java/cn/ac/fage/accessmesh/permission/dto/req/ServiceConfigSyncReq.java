package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ServiceConfigSyncReq(
    @NotBlank String serviceCode,
    String basePath,
    @NotBlank String syncMode,
    @NotNull List<@Valid GroupItem> groups
) {
    public record GroupItem(
        @NotBlank String groupCode,
        @NotBlank String groupName,
        @NotNull List<@Valid ApiItem> apis
    ) {}

    public record ApiItem(
        @NotBlank String name,
        @NotBlank String httpMethod,
        @NotBlank String path,
        @NotBlank String operationCode,
        @NotBlank String resourceCode,
        String description
    ) {}
}
