package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 权限条件详情查询请求体
 * <p>
 * 用于查询权限条件的详细信息，使用条件编码标识。
 * </p>
 *
 * @param conditionCode 条件编码，必填，最大64字符
 */
public record ConditionDetailReq(
    @NotBlank(message = "条件编码不能为空") @Size(max = 64) String conditionCode
) {}