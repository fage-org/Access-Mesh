package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源依赖循环检查请求体
 * <p>
 * 用于检查添加依赖关系是否会创建循环引用。
 * 使用稳定的业务键标识资源，与批量同步接口保持一致。
 * </p>
 *
 * @param sourceResourceTypeCode 源资源类型编码，必填
 * @param sourceResourceCode     源资源编码，必填
 * @param sourceCodeType         源编码类型，可选
 * @param targetResourceTypeCode 目标资源类型编码，必填
 * @param targetResourceCode     目标资源编码，必填
 * @param targetCodeType         目标编码类型，可选
 */
public record ResourceDependencyCheckReq(
    @NotBlank String sourceResourceTypeCode,
    @NotBlank String sourceResourceCode,
    String sourceCodeType,
    @NotBlank String targetResourceTypeCode,
    @NotBlank String targetResourceCode,
    String targetCodeType
) {}