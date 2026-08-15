package cn.ac.fage.accessmesh.access.application.query.projection;

/**
 * 组织简要投影（跨域只读查询用）。
 * <p>
 * 对应 sys_org 有效行的 ID 与名称，用于角色列表补岗位所属组织名等展示场景。
 * </p>
 *
 * @param id   组织 ID
 * @param name 组织名
 */
public record OrgBriefProjection(
    Long id,
    String name
) {}
