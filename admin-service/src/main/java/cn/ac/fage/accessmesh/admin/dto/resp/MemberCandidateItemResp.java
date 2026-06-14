package cn.ac.fage.accessmesh.admin.dto.resp;

/**
 * 候选用户列表项响应（添加组织/岗位成员时使用）。
 * <p>
 * 候选用户已由服务端过滤（仅默认树可见 + 排除目标组织已有成员），
 * {@code alreadyAssigned} 固定为 false，保留字段用于一致性。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.1.2
 *
 * @param id              用户 ID
 * @param username        登录账号
 * @param name            显示名
 * @param avatar          头像 URL（可选）
 * @param primaryOrgName  默认树主归属组织名，便于识别
 * @param alreadyAssigned 固定 false（服务端已过滤）
 */
public record MemberCandidateItemResp(
    Long id,
    String username,
    String name,
    String avatar,
    String primaryOrgName,
    Boolean alreadyAssigned
) {}
