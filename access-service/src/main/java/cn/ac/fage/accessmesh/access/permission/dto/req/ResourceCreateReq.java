package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 资源创建请求体
 * <p>
 * 用于创建新的资源实体，包括父资源、类型和名称。
 * </p>
 *
 * @param parentId         父资源ID，可选
 * @param resourceTypeCode 资源类型编码，必填
 * @param code             资源编码，必填，唯一标识
 * @param codeType         编码类型，可选
 * @param name             资源名称，必填
 * @param path             资源路径，可选
 * @param status           资源状态，可选，0=禁用，1=启用
 * @param sortOrder        排序顺序，可选
 * @param extra            扩展属性JSON，可选
 */
public record ResourceCreateReq(
    Long parentId,
    String parentResourceTypeCode,
    String parentResourceCode,
    String parentCodeType,
    String parentDomainCode,
    @NotBlank String resourceTypeCode,
    @NotBlank String code,
    String codeType,
    @NotBlank String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra
) {}
