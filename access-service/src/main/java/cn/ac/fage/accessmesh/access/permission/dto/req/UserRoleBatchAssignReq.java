package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 用户角色批量分配请求体
 * <p>
 * 用于批量分配用户与角色的关联关系。
 * 将多个用户分配到同一个角色。
 * </p>
 *
 * @param subjectExternalIds 用户外部标识列表，必填且不能为空
 * @param subjectTypeCode    用户类型编码，必填
 * @param domainCode         业务域编码：功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）允许 null
 *                           表示全局域；ORG/POSITION 必填（由 UserManageAppServiceImpl 入口
 *                           跨字段业务校验保证）
 * @param roleTypeCode       角色类型编码，必填
 * @param roleExternalId     角色外部标识，必填
 * @param relationId         关系ID，可选，用于指定关联记录
 */
public record UserRoleBatchAssignReq(
    @NotEmpty List<String> subjectExternalIds,
    @NotBlank String subjectTypeCode,
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    Long relationId
) {}