package cn.ac.fage.accessmesh.access.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 字典类型响应记录类
 * <p>
 * 用于返回字典类型及其关联的字典数据列表。
 * 包含字典名称、字典类型、状态、备注、创建时间、数据列表。
 * </p>
 *
 * @param id         字典类型ID
 * @param dictName   字典名称（显示名称）
 * @param dictType   字典类型（唯一标识）
 * @param status     状态（0=正常，1=禁用）
 * @param remark     备注
 * @param createdAt  创建时间
 * @param data       字典数据列表
 */
public record DictTypeResp(
    /**
     * 字典类型ID
     */
    Long id,

    /**
     * 字典名称（显示名称）
     */
    String dictName,

    /**
     * 字典类型（唯一标识）
     */
    String dictType,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status,

    /**
     * 备注
     */
    String remark,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 字典数据列表
     */
    List<DictDataResp> data
) {}