package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户分配角色请求体（perm-common 共享）。
 * <p>
 * 用于分配用户与角色的关联关系，支持有效期配置。
 * perm-common 单源契约——服务端 Controller 与 SDK 消费方共用本类（T-PERM-065，PermCommonReqContractTest 快照守卫）。
 * </p>
 *
 * @param items 分配条目列表，必填且不能为空
 */
public record UserAssignRoleReq(
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<@NotNull @Valid AssignItem> items
) {
    /**
     * 分配条目
     *
     * <p>类型码/域码三标识字段带 @Pattern（T-PERM-104，Q-057 碰撞收口）：值域与建域/建类型
     * 入口同款锁死（不含分隔符），阻止 domainCode 含 ":" 经分组反解滑移构造静默错配；
     * externalId 为尾段自由文本（建入口无格式约束），中段锁定后无滑移面。</p>
     *
     * @param subjectTypeCode   用户类型编码，必填
     * @param subjectExternalId 用户外部标识，必填
     * @param domainCode        业务域编码：功能角色（BASIC_ROLE/PERSONAL）允许 null
     *                          表示全局域；ORG/POSITION 必填（由服务端跨字段业务校验保证，
     *                          见 access-service 的 UserManageAppServiceImpl 入口校验）；
     *                          GROUP_ROLE 为拒绝类型（T-PERM-097 绑定面收紧，20022）。
     *                          空串不合法（须传 null 表示全局域，T-API-004 空串拒先例）
     * @param roleTypeCode      角色类型编码，必填
     * @param roleExternalId    角色外部标识，必填
     * @param relationId        关系ID，可选；须为正整数——0 与 null 在 uk_user_role
     *                          （COALESCE(relation_id,0)）同槽位，0 入库会形成键槽错位
     *                          的持粘行，故入口拒绝（T-ADMIN-030 外评处置）
     * @param validFrom         有效期开始时间，可选
     * @param validTo           有效期结束时间，可选
     */
    public record AssignItem(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "主体类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String subjectTypeCode,
        @NotBlank String subjectExternalId,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "业务域编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String domainCode,
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String roleTypeCode,
        @NotBlank String roleExternalId,
        @Positive Long relationId,
        LocalDateTime validFrom,
        LocalDateTime validTo
    ) {}
}
