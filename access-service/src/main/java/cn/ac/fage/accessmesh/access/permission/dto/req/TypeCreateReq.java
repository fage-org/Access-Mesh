package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 类型定义创建请求体
 * <p>
 * 用于创建新的类型定义。typeValue 由服务端在 tenant+typeKey 内自动分配
 * （全量行 max+1，软删不复用）；typeCode 可选，留空时服务端按
 * {@code TYPEKEY_<typeValue>} 生成；isSystem 不可由 API 创建（系统预置仅走租户初始化种子）。
 * </p>
 *
 * @param typeKey     类型键，必填，用于分类
 * @param typeCode    类型编码，可选，对外稳定编码（留空则服务端生成）
 * @param name        类型名称，必填
 * @param description 类型描述，可选
 * @param sortOrder   排序顺序，可选
 * @param extra       扩展属性JSON，可选
 */
public record TypeCreateReq(
    @NotBlank String typeKey,
    String typeCode,
    @NotBlank String name,
    String description,
    Integer sortOrder,
    String extra
) {}
