package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 资源更新请求体
 * <p>
 * 用于更新资源的信息，包括编码、名称、路径、状态等。
 * </p>
 *
 * @param id        资源ID，必填
 * @param code      资源编码，可选
 * @param name      资源名称，可选
 * @param path      资源路径，可选
 * @param status    资源状态，可选，0=禁用，1=启用
 * @param sortOrder 排序顺序，可选
 * @param extra     扩展属性JSON，可选
 */
public record ResourceUpdateReq(
    @NotNull Long id,
    String code,
    String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra
) {}