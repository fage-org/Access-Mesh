package cn.ac.fage.accessmesh.access.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 接口准入要求；业务类型由持久化的操作引用唯一确定。 */
public record RequiredPermission(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String resourceTypeCode,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String operationCode
) {}
