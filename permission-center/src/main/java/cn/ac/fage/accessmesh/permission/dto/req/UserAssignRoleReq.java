package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 用户分配角色请求体
 * <p>
 * 用于分配用户与角色的关联关系，支持有效期配置。
 * </p>
 *
 * @param items 分配条目列表，必填且不能为空
 */
public record UserAssignRoleReq(
    @NotEmpty List<@Valid AssignItem> items
) {
    /**
     * 分配条目
     * <p>
     * 表示单个用户角色关联关系的分配参数，包括有效期。
     * </p>
     *
     * @param subjectTypeCode   用户类型编码，必填
     * @param subjectExternalId 用户外部标识，必填
     * @param domainCode        业务域编码：功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）允许 null
     *                          表示全局域；ORG/POSITION 必填（由 UserManageAppServiceImpl 入口
     *                          跨字段业务校验保证）
     * @param roleTypeCode      角色类型编码，必填
     * @param roleExternalId    角色外部标识，必填
     * @param relationId        关系ID，可选
     * @param validFrom         有效期开始时间，可选
     * @param validTo           有效期结束时间，可选
     */
    public record AssignItem(
        @NotBlank String subjectTypeCode,
        @NotBlank String subjectExternalId,
        String domainCode,
        @NotBlank String roleTypeCode,
        @NotBlank String roleExternalId,
        Long relationId,
        java.time.LocalDateTime validFrom,
        java.time.LocalDateTime validTo
    ) {}
}