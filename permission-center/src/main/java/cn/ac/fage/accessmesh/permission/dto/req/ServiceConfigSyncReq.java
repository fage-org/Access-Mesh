package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
        @NotBlank(message = "name cannot be blank")
        @Size(max = 256, message = "name too long")
        String name,

        @NotBlank(message = "httpMethod cannot be blank")
        @Pattern(regexp = "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)$",
                 message = "Invalid httpMethod")
        String httpMethod,

        @NotBlank(message = "path cannot be blank")
        @Size(max = 512, message = "path too long")
        @Pattern(regexp = "^/[a-zA-Z0-9_/.\\-{}]*$",
                 message = "Invalid path format")
        String path,

        @NotBlank(message = "operationCode cannot be blank")
        @Size(max = 128, message = "operationCode too long")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                 message = "Invalid operationCode format")
        String operationCode,

        @NotBlank(message = "resourceCode cannot be blank")
        @Size(max = 128, message = "resourceCode too long")
        @Pattern(regexp = "^[a-zA-Z0-9_:.-]+$",
                 message = "Invalid resourceCode format")
        String resourceCode,

        @Size(max = 512, message = "description too long")
        String description
    ) {}
}
