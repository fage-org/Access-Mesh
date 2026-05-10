package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 字典数据创建请求记录类
 * <p>
 * 用于创建字典数据项的请求参数。
 * 包含字典类型ID、标签、值、排序、状态、备注。
 * </p>
 *
 * @param dictTypeId 字典类型ID（必填）
 * @param dictLabel  字典标签（必填，显示值）
 * @param dictValue  字典值（必填，实际值）
 * @param sort       排序号（可选）
 * @param status     状态（可选，默认0=正常）
 * @param remark     备注（可选）
 */
public record DictDataCreateReq(
    /**
     * 字典类型ID
     */
    @NotNull(message = "字典类型ID不能为空")
    Long dictTypeId,

    /**
     * 字典标签（显示值）
     */
    @NotBlank(message = "字典标签不能为空")
    String dictLabel,

    /**
     * 字典值（实际值）
     */
    @NotBlank(message = "字典键值不能为空")
    String dictValue,

    /**
     * 排序号
     */
    Integer sort,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status,

    /**
     * 备注
     */
    String remark
) {}