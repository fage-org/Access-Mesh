package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 服务配置同步请求体
 * <p>
 * 用于从服务实例同步API接口列表到权限中心。
 * 支持按组组织API接口，每个接口包含名称、方法、路径等信息。
 * </p>
 *
 * @param serviceCode 服务编码，必填
 * @param basePath    服务基础路径，可选
 * @param syncMode    同步模式，必填。权威契约 §6.3 首期仅允许 FULL
 *                    （T-PERM-027 收口：校验层拒绝其他值，IncrementalSyncStrategy 已删除）
 * @param groups      API分组列表，必填且不能为空
 */
public record ServiceConfigSyncReq(
    @NotBlank String serviceCode,
    String basePath,
    @NotBlank(message = "syncMode cannot be blank")
    @Pattern(regexp = "FULL", message = "syncMode only supports FULL")
    String syncMode,
    @NotNull List<@Valid GroupItem> groups
) {
    /**
     * API分组条目
     * <p>
     * 表示一组API接口的组织结构。
     * </p>
     *
     * @param groupCode 分组编码，必填
     * @param groupName 分组名称，必填
     * @param apis      API接口列表，必填且不能为空
     */
    public record GroupItem(
        @NotBlank String groupCode,
        @NotBlank String groupName,
        @NotNull List<@Valid ApiItem> apis
    ) {}

    /**
     * API接口条目
     * <p>
     * 表示单个API接口的详细信息，包括方法、路径、权限关联等。
     * </p>
     * <p>
     * operationCode 已删除（T-PERM-053，2026-09-05）：接口权限模型为「API 资源实例 ×
     * ACCESS 单操作」，无操作粒度，运行时固定按 ACCESS 判定；原字段既不落库也不参与
     * 鉴权，属契约性空壳。仍携带该字段的旧请求体经全局严格 ObjectMapper 反序列化 400。
     * </p>
     *
     * @param name         接口名称，必填，最大256字符
     * @param httpMethod   HTTP方法，必填，必须是有效HTTP方法
     * @param path         路径，必填，最大512字符，必须以/开头
     * @param resourceCode 资源编码，必填，最大128字符
     * @param description  接口描述，可选，最大512字符
     */
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

        @NotBlank(message = "resourceCode cannot be blank")
        @Size(max = 128, message = "resourceCode too long")
        @Pattern(regexp = "^[a-zA-Z0-9_:.-]+$",
                 message = "Invalid resourceCode format")
        String resourceCode,

        @Size(max = 512, message = "description too long")
        String description
    ) {}
}