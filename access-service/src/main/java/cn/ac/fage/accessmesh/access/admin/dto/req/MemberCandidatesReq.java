package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 候选用户查询请求（添加组织/岗位成员时使用）。
 * <p>
 * 候选范围 = 默认组织树中操作者可见 ∩ 排除目标组织已有成员。
 * 与 {@link UserPageReq} 的区别：本接口服务"给非默认组织/岗位添加成员"场景，
 * 不承载身份目录列表查询语义。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.1.2
 *
 * @param targetOrgId 目标组织/岗位 ID（必填，用户即将被加入的组织）
 * @param pageNum     页码（默认 1）
 * @param pageSize    每页大小（默认 20，范围 1-100）
 * @param keyword     关键字（按 username/name/phone/email 模糊匹配）
 */
public record MemberCandidatesReq(
    @NotNull Long targetOrgId,
    @Min(1) Integer pageNum,
    @Min(1) @Max(100) Integer pageSize,
    String keyword
) {
    public int getPageNum() { return pageNum != null ? pageNum : 1; }
    public int getPageSize() { return pageSize != null ? pageSize : 20; }
}
