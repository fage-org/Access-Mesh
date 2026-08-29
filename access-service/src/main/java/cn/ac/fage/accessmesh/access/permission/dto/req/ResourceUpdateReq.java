package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源更新请求体
 * <p>
 * 以业务键 (resourceTypeCode, code, codeType) 定位待更新资源（T-PERM-028），
 * 业务键字段不可更新（编码为稳定标识，原 id 定位 + code 可更新形态已删除）。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，必填（定位键）
 * @param code             资源编码，必填（定位键）
 * @param codeType         编码类型，可选；缺省 default（定位键）
 * @param name             资源名称，可选
 * @param path             资源路径，可选
 * @param status           资源状态，可选，0=禁用，1=启用
 * @param sortOrder        排序顺序，可选
 * @param extra            扩展属性 JSON，可选；null=不更新
 * @param extraClear       清空 extra 为 null 的显式标志，可选；true 时优先于 extra
 *                        （JSON null 无法区分「未传」与「清空」，T-PERM-028 设计定案）
 */
public record ResourceUpdateReq(
    @NotBlank String resourceTypeCode,
    @NotBlank String code,
    String codeType,
    String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra,
    Boolean extraClear
) {}
