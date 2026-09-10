package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 用户角色批量撤销请求体
 * <p>
 * 用于批量撤销用户与角色的关联关系。
 * 每个条目使用稳定的业务键标识用户和角色。
 * </p>
 *
 * @param items 撤销条目列表，必填且不能为空
 */
public record UserRoleBatchRevokeReq(
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    @Valid List<RevokeItem> items
) {
    /**
     * 撤销条目
     * <p>
     * 表示单个用户角色关联关系的撤销参数。
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
     */
    public record RevokeItem(
        @NotBlank String subjectTypeCode,
        @NotBlank String subjectExternalId,
        String domainCode,
        @NotBlank String roleTypeCode,
        @NotBlank String roleExternalId,
        Long relationId
    ) {}
}