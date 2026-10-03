package cn.ac.fage.accessmesh.access.role.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 用户角色批量分配请求体
 * <p>
 * 用于批量分配用户与角色的关联关系。
 * 将多个用户分配到同一个角色。
 * </p>
 * <p>类型码/域码三标识字段带 @Pattern（T-PERM-104，Q-057 碰撞收口，与
 * UserAssignRoleReq.AssignItem 同款；本端点请求级单值直传无拼接反解，注解为
 * 入口族一致性对齐——同族三端点格式口径统一）。</p>
 *
 * @param subjectExternalIds 用户外部标识列表，必填且不能为空
 * @param subjectTypeCode    用户类型编码，必填
 * @param domainCode         业务域编码：功能角色（BASIC_ROLE/PERSONAL）允许 null
 *                           表示全局域；ORG/POSITION 必填（由 UserManageAppServiceImpl 入口
 *                           跨字段业务校验保证）；GROUP_ROLE 为拒绝类型（T-PERM-097
 *                           绑定面收紧，20022）。空串不合法（须传 null，T-API-004 先例）
 * @param roleTypeCode       角色类型编码，必填
 * @param roleExternalId     角色外部标识，必填
 * @param relationId         关系ID，可选，用于指定关联记录；须为正整数（0 拒绝——
 *                           与 null 在 uk_user_role 同槽位，T-ADMIN-030 外评处置）
 */
public record UserRoleBatchAssignReq(
    @NotEmpty List<String> subjectExternalIds,
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "主体类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String subjectTypeCode,
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "业务域编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String domainCode,
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String roleTypeCode,
    @NotBlank String roleExternalId,
    @Positive Long relationId
) {}