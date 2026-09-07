package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 类型定义创建请求体
 * <p>
 * 用于创建新的类型定义。typeValue 由服务端在 tenant+typeKey 内自动分配
 * （全量行 max+1，软删不复用）；typeCode 可选，留空时服务端按
 * {@code <TYPEKEY大写>_<typeValue>} 生成（如 RESOURCE_TYPE_12）；isSystem 不可由 API 创建（系统预置仅走租户初始化种子）。
 * 长度约束对齐 schema type_definition 列宽。
 * </p>
 *
 * @param typeKey     类型键，必填，用于分类（≤64）
 * @param typeCode    类型编码，可选，对外稳定编码（留空则服务端生成，≤64）
 * @param name        类型名称，必填（≤128）
 * @param description 类型描述，可选（≤512）
 * @param sortOrder   排序顺序，可选
 * @param extra       扩展属性JSON，可选
 */
public record TypeCreateReq(
    @NotBlank @Size(max = 64) String typeKey,
    @Size(max = 64) String typeCode,
    @NotBlank @Size(max = 128) String name,
    @Size(max = 512) String description,
    Integer sortOrder,
    String extra
) {}
