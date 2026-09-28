package cn.ac.fage.accessmesh.perm.common.dto.resp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 准入要求（requiredPermission 线格式，T-ACCESS-059，契约总册 §25.1/§25.2）。
 * <p>
 * 只表达业务类型与操作（接口→业务资源类型 REPORT→VIEW），不要求实例；
 * 大写裸值（§2 通用协议 raw 严格口径）；API:ACCESS 不得作为准入要求。
 * 服务端内部解析为操作 ID（resource_api_mapping.required_operation_id）。
 * </p>
 *
 * @param resourceTypeCode 业务资源类型码，大写
 * @param operationCode    操作码，大写
 */
public record AdmissionRequirement(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String resourceTypeCode,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String operationCode
) {}
