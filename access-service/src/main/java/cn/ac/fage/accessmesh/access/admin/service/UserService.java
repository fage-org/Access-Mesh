package cn.ac.fage.accessmesh.access.admin.service;

import cn.ac.fage.accessmesh.access.admin.dto.req.MemberCandidatesReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.MemberCandidateItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.ResetPasswordResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

/**
 * 用户服务接口
 * <p>
 * 提供用户管理相关的服务方法，包括用户的创建、更新、删除、查询等。
 * 支持用户的启停和密码重置。
 * </p>
 */
public interface UserService {

    /**
     * 创建用户
     * <p>
     * 创建单个用户账号。
     * 设置用户基本信息、组织分配等。
     * 系统自动生成随机初始密码，通过响应返回。
     * </p>
     *
     * @param req 用户创建请求
     * @return 用户创建响应，包含用户ID和初始密码
     */
    UserCreateResp createUser(UserCreateReq req);

    /**
     * 更新用户
     * <p>
     * 更新指定用户的基本信息。
     * 包括用户名称、联系方式、角色等属性。
     * </p>
     *
     * @param req 用户更新请求
     */
    void updateUser(UserUpdateReq req);

    /**
     * 删除用户
     * <p>
     * 批量删除多个用户账号。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param req 待删除的用户ID列表请求
     */
    void deleteUser(IdsReq req);

    /**
     * 批量启用/禁用用户
     * <p>
     * 根据请求中的 status 字段批量启用或禁用用户账号。
     * status=1 启用，status=0 禁用。
     * </p>
     *
     * @param req 用户状态变更请求，包含用户ID列表和目标状态
     */
    void updateStatus(UserUpdateStatusReq req);

    /**
     * 获取用户详情
     * <p>
     * 根据ID查询用户详细信息。
     * 包含用户基本信息、角色分配等。
     * </p>
     *
     * @param id 用户ID
     * @return 用户详情响应
     */
    UserResp getUser(Long id);

    /**
     * 分页查询用户列表
     * <p>
     * 根据条件分页查询用户列表。
     * 支持按用户名、状态等条件筛选。
     * </p>
     *
     * @param req 分页查询请求
     * @return 分页用户列表结果
     */
    PaginatedResult<UserPageItemResp> pageUsers(UserPageReq req);

    /**
     * 重置用户密码
     * <p>
     * 重置指定用户的密码。
     * 如果 newPassword 为空，系统自动生成随机密码。
     * 响应中返回生效的密码明文（仅本次返回）。
     * </p>
     *
     * @param userId      用户ID
     * @param newPassword 新密码（可为空，空时自动生成）
     * @return 重置密码响应，包含生效的密码
     */
    ResetPasswordResp resetPassword(Long userId, String newPassword);

    /**
     * 查询候选用户（添加组织/岗位成员时使用）
     * <p>
     * 候选范围 = 默认组织树中操作者可见 ∩ 排除目标组织已有成员。
     * 门禁：ORG:UPDATE@targetOrgId（校验能管理目标组织成员）。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.1.2
     *
     * @param req 候选用户查询请求（含 targetOrgId）
     * @return 分页候选用户列表
     */
    PaginatedResult<MemberCandidateItemResp> memberCandidates(MemberCandidatesReq req);
}
