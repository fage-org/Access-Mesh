package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;

/**
 * 用户角色列表项响应（admin 代理查询 permission-center 后拼装）。
 * <p>
 * 包含全类型角色（ORG/POSITION/BASIC_ROLE/GROUP_ROLE/PERSONAL），
 * 前端按 roleTypeCode 区分展示区域。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.1
 *
 * @param roleId         角色 ID（permission-center abstract_role.id，用于 assign/revoke 链路）
 * @param roleName       角色名
 * @param roleTypeCode   角色类型码（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE）
 * @param roleTypeLabel  角色类型显示名
 * @param targetType     前端展示用，同 roleTypeCode
 * @param relationId     POSITION 角色对应的所属组织 abstract_role.id；其他类型为 null
 * @param relationOrgName POSITION 角色对应的所属组织名（代理层补）
 * @param validFrom      有效期起始
 * @param validTo        有效期截止
 */
public record UserRoleItemResp(
    Long roleId,
    String roleName,
    String roleTypeCode,
    String roleTypeLabel,
    String targetType,
    Long relationId,
    String relationOrgName,
    LocalDateTime validFrom,
    LocalDateTime validTo
) {}
