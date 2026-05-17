package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

/**
 * 用户服务接口
 * <p>
 * 提供用户管理相关的服务方法，包括用户的创建、更新、删除、查询等。
 * 支持用户的启用和密码重置。
 * </p>
 */
public interface UserService {

    /**
     * 创建用户
     * <p>
     * 创建单个用户账号。
     * 设置用户基本信息、角色分配等。
     * </p>
     *
     * @param req 用户创建请求
     * @return 创建的用户ID
     */
    Long createUser(UserCreateReq req);

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
     * 启用用户
     * <p>
     * 批量启用多个用户账号。
     * 恢复用户的登录和操作权限。
     * </p>
     *
     * @param req 待启用的用户ID列表请求
     */
    void enableUser(IdsReq req);

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
     * 密码需要加密后存储。
     * </p>
     *
     * @param userId      用户ID
     * @param newPassword 新密码
     */
    void resetPassword(Long userId, String newPassword);
}