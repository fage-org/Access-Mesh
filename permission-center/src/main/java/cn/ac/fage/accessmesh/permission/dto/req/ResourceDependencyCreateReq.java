package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 资源依赖创建请求体
 * <p>
 * 用于创建资源依赖关系，使用稳定的业务键标识资源。
 * 与批量同步接口保持一致的设计。
 * </p>
 *
 * @param sourceResourceTypeCode 源资源类型编码，必填
 * @param sourceResourceCode     源资源编码，必填
 * @param sourceCodeType         源编码类型，可选
 * @param sourceOperationCodes   源操作编码列表，可选
 * @param targetResourceTypeCode 目标资源类型编码，必填
 * @param targetResourceCode     目标资源编码，必填
 * @param targetCodeType         目标编码类型，可选
 * @param requiredOperationCodes 要求操作编码列表，必填且不能为空
 * @param autoGrant              是否自动授权，可选
 * @param description            依赖描述，可选
 */
public record ResourceDependencyCreateReq(
    @NotBlank String sourceResourceTypeCode,
    @NotBlank String sourceResourceCode,
    String sourceCodeType,
    List<String> sourceOperationCodes,
    @NotBlank String targetResourceTypeCode,
    @NotBlank String targetResourceCode,
    String targetCodeType,
    @NotEmpty List<String> requiredOperationCodes,
    Boolean autoGrant,
    String description
) {}