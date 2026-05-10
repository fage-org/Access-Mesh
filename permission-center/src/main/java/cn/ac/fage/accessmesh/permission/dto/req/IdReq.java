package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 单ID请求体
 * <p>
 * 用于需要单个ID作为请求参数的API接口。
 * 例如：查询详情、删除单个记录等操作。
 * </p>
 *
 * @param id 实体ID，必填
 */
public record IdReq(
    @NotNull Long id
) {}