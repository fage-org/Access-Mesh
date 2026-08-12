package cn.ac.fage.accessmesh.access.admin.dto.resp;

/**
 * 字典数据响应记录类
 * <p>
 * 用于返回字典数据项信息。
 * 包含字典标签、字典值、排序、状态、备注等。
 * </p>
 *
 * @param id          字典数据ID
 * @param dictTypeId  字典类型ID
 * @param dictLabel   字典标签（显示值）
 * @param dictValue   字典值（实际值）
 * @param sort        排序号
 * @param status      状态（0=正常，1=禁用）
 * @param remark      备注
 */
public record DictDataResp(
    /**
     * 字典数据ID
     */
    Long id,

    /**
     * 字典类型ID
     */
    Long dictTypeId,

    /**
     * 字典标签（显示值）
     */
    String dictLabel,

    /**
     * 字典值（实际值）
     */
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