package cn.ac.fage.accessmesh.access.platform.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;

import jakarta.validation.constraints.Pattern;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统配置保存请求体
 * <p>
 * 用于保存或更新系统配置，包括配置键、配置值和描述。
 * </p>
 *
 * @param configKey   配置键，必填，唯一标识
 * @param configValue 配置值，必填
 * @param description 配置描述，可选
 * @param descriptionClear 显式清空 description 为 NULL，与新值同传拒绝
 */
public record SystemConfigReq(
    @NotBlank String configKey,
    @NotBlank String configValue,
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "description 不能为空白")
    String description,
    Boolean descriptionClear
) {
    @AssertTrue(message = "description 与 descriptionClear 不能同时提供")
    @JsonIgnore
    public boolean isDescriptionConflictFree() {
        return description == null || !Boolean.TRUE.equals(descriptionClear);
    }
}
