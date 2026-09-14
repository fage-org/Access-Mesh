package cn.ac.fage.accessmesh.access.resource.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
 * @param autoGrant              预留未实现：自动授权暂缓（T-PERM-035），仅接受 false/省略，传 true 返回 20048
 * @param description            依赖描述，可选（≤512）
 */
public record ResourceDependencyCreateReq(
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "资源类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String sourceResourceTypeCode,
    @NotBlank String sourceResourceCode,
    String sourceCodeType,
    List<@Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "操作编码必须以大写字母开头，仅含大写字母/数字/下划线") String> sourceOperationCodes,
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "资源类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String targetResourceTypeCode,
    @NotBlank String targetResourceCode,
    String targetCodeType,
    @NotEmpty List<@Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "操作编码必须以大写字母开头，仅含大写字母/数字/下划线") String> requiredOperationCodes,
    Boolean autoGrant,
    @Size(max = 512) String description
) {}