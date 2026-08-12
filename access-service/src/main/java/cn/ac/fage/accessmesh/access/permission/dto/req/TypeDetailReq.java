package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 类型定义详情查询请求体
 * <p>
 * 用于查询类型定义的详细信息，使用类型键标识。
 * </p>
 *
 * @param typeKey 类型键，必填，最大64字符
 */
public record TypeDetailReq(
    @NotBlank(message = "类型键不能为空") @Size(max = 64) String typeKey
) {}