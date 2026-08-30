package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 资源依赖更新请求体（PUT 全量覆盖语义）
 * <p>
 * ID用于定位记录；资源对与操作编码使用稳定的业务键，全部字段按提交值
 * 全量覆盖（资源对可改、sourceOperationCodes=null 表示清空为"任意操作触发"、
 * description=null 清空），对齐 conflict-rule 更新范式。
 * </p>
 *
 * @param id                     依赖关系ID，必填
 * @param sourceResourceTypeCode 源资源类型编码，必填
 * @param sourceResourceCode     源资源编码，必填
 * @param sourceCodeType         源编码类型，可选
 * @param sourceOperationCodes   源操作编码列表，可空（null/空=任意操作触发）
 * @param targetResourceTypeCode 目标资源类型编码，必填
 * @param targetResourceCode     目标资源编码，必填
 * @param targetCodeType         目标编码类型，可选
 * @param requiredOperationCodes 要求操作编码列表，必填且不能为空
 * @param autoGrant              预留未实现：自动授权暂缓（T-PERM-035），仅接受 false/省略，传 true 返回 20048
 * @param description            依赖描述，可选（≤512）
 */
public record ResourceDependencyUpdateReq(
    @NotNull Long id,
    @NotBlank String sourceResourceTypeCode,
    @NotBlank String sourceResourceCode,
    String sourceCodeType,
    List<String> sourceOperationCodes,
    @NotBlank String targetResourceTypeCode,
    @NotBlank String targetResourceCode,
    String targetCodeType,
    @NotEmpty List<String> requiredOperationCodes,
    Boolean autoGrant,
    @Size(max = 512) String description
) {}
