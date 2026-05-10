package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 权限最近变更查询请求体
 * <p>
 * 用于查询用户或角色的权限最近变更历史。
 * 支持按时间范围和事件类型过滤。
 * </p>
 *
 * @param targetType        目标类型，必填（USER/ROLE）
 * @param subjectTypeCode   用户类型编码，目标类型为USER时使用
 * @param subjectExternalId 用户外部标识，目标类型为USER时使用
 * @param roleTypeCode      角色类型编码，目标类型为ROLE时使用
 * @param roleExternalId    角色外部标识，目标类型为ROLE时使用
 * @param domainCode        业务域编码，可选
 * @param since             开始时间，可选
 * @param until             结束时间，可选
 * @param eventTypes        事件类型列表，可选
 * @param pageNum           页码，可选
 * @param pageSize          每页条数，可选
 */
public record PermissionRecentChangesReq(
    @NotBlank String targetType,
    String subjectTypeCode,
    String subjectExternalId,
    String roleTypeCode,
    String roleExternalId,
    String domainCode,
    java.time.LocalDateTime since,
    java.time.LocalDateTime until,
    List<String> eventTypes,
    Integer pageNum,
    Integer pageSize
) {}