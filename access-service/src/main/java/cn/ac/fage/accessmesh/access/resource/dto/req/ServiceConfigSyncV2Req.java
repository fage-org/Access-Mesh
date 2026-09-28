package cn.ac.fage.accessmesh.access.resource.dto.req;

import cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** sync-v2 独立协议；每条声明必须携带业务准入要求，不改变服务鉴权模式。 */
public record ServiceConfigSyncV2Req(
    @NotBlank String serviceCode,
    String basePath,
    @NotBlank @Pattern(regexp = "FULL") String syncMode,
    @NotNull List<@NotNull @Valid GroupItem> groups
) {
    public record GroupItem(
        @NotBlank String groupCode,
        @NotBlank String groupName,
        @NotNull List<@NotNull @Valid ApiItem> apis
    ) {}

    public record ApiItem(
        @NotBlank @Size(max = 256) String name,
        @NotBlank @Pattern(regexp = "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)$") String httpMethod,
        @NotBlank @Size(max = 512) @Pattern(regexp = "^/[a-zA-Z0-9_/.\\-{}]*$") String path,
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[a-zA-Z0-9_:.-]+$") String resourceCode,
        @Size(max = 512) String description,
        @NotNull @Valid RequiredPermission requiredPermission
    ) {}
}
