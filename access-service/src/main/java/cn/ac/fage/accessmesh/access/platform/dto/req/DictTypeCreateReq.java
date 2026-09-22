package cn.ac.fage.accessmesh.access.platform.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 字典类型创建请求记录类
 * <p>
 * 用于创建字典类型的请求参数。
 * 包含字典名称、字典类型、状态、备注。
 * </p>
 *
 * @param dictName 字典名称（必填，显示名称）
 * @param dictType 字典类型（必填，唯一标识）
 * @param status   状态（可选，缺省 1=启用；0=停用，1=启用——与 DDL sys_dict_type.status 一致）
 * @param remark   备注（可选）
 */
public record DictTypeCreateReq(
    /**
     * 字典名称（显示名称）
     */
    @NotBlank(message = "字典类型名称不能为空")
    String dictName,

    /**
     * 字典类型（唯一标识）
     */
    @NotBlank(message = "字典类型不能为空")
    String dictType,

    /**
     * 状态（0=停用，1=启用）
     */
    Integer status,

    /**
     * 备注
     */
    String remark
) {}