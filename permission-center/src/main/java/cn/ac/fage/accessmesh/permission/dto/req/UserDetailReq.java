package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户详情查询请求体
 * <p>
 * 用于查询用户的详细信息，使用稳定的业务键标识用户。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填，最大64字符
 * @param subjectExternalId 用户外部标识，必填，最大128字符
 */
public record UserDetailReq(
    @NotBlank(message = "主体类型编码不能为空") @Size(max = 64) String subjectTypeCode,
    @NotBlank(message = "主体外部标识不能为空") @Size(max = 128) String subjectExternalId
) {}