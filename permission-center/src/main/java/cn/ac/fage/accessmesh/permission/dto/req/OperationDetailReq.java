package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 操作权限详情查询请求体
 * <p>
 * 用于查询操作权限的详细信息，使用资源类型编码和操作编码标识。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，必填，最大64字符
 * @param operationCode    操作编码，必填，最大64字符
 */
public record OperationDetailReq(
    @NotBlank(message = "资源类型编码不能为空") @Size(max = 64) String resourceTypeCode,
    @NotBlank(message = "操作编码不能为空") @Size(max = 64) String operationCode
) {}