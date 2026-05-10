package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp;

import java.util.List;

/**
 * 用户管理服务接口
 * <p>
 * 提供用户的同步、CRUD操作和角色分配功能。
 * 支持从外部系统同步用户，以及用户的批量角色分配和撤销。
 * </p>
 */
public interface UserManageService {

    /**
     * 从外部系统同步用户
     * <p>
     * 根据tenantId+userType+externalId进行用户同步（upsert操作）。
     * 如果用户不存在则创建，存在则更新。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户同步请求，包含外部系统用户信息
     * @return 同步后的用户详情
     */
    UserResp syncUser(Long tenantId, UserSyncReq req);

    /**
     * 创建用户
     * <p>
     * 创建新的用户实体。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户创建请求
     * @return 创建的用户详情
     */
    UserResp createUser(Long tenantId, UserCreateReq req);

    /**
     * 更新用户
     * <p>
     * 更新用户的基本信息。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户更新请求
     * @return 更新后的用户详情
     */
    UserResp updateUser(Long tenantId, UserUpdateReq req);

    /**
     * 获取用户详情
     * <p>
     * 根据用户ID查询用户实体详情。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户详情
     */
    UserResp getUser(Long tenantId, Long userId);

    /**
     * 删除用户
     * <p>
     * 软删除用户实体。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    void deleteUser(Long tenantId, Long userId);

    /**
     * 批量删除用户
     * <p>
     * 批量软删除多个用户实体。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID列表
     */
    void deleteUsers(Long tenantId, List<Long> userIds);

    /**
     * 为用户分配角色
     * <p>
     * 将指定角色分配给用户。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户分配角色请求
     */
    void assignRole(Long tenantId, UserAssignRoleReq req);

    /**
     * 批量分配用户角色
     * <p>
     * 批量为多个用户分配角色。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量分配请求
     */
    void assignRolesBatch(Long tenantId, UserRoleBatchAssignReq req);

    /**
     * 批量撤销用户角色
     * <p>
     * 批量撤销用户与角色的关联关系。
     * 每项使用业务键标识。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量撤销请求
     */
    void revokeRolesBatch(Long tenantId, UserRoleBatchRevokeReq req);

    /**
     * 获取用户的角色列表
     * <p>
     * 通过主体业务键查询用户被分配的角色。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户角色列表查询请求
     * @return 用户角色响应
     */
    UserRolesResp getUserRoles(Long tenantId, UserRoleListReq req);

    /**
     * 查询用户列表
     * <p>
     * 获取租户的用户列表，可按主体类型、业务域和关键词筛选，支持简单分页。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectTypeCode  主体类型编码，可选
     * @param domainCode       业务域编码，可选
     * @param keyword          关键词，可选
     * @param offset           分页偏移量
     * @param limit            每页条数
     * @return 用户列表
     */
    List<UserResp> listUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword, int offset, int limit);

    /**
     * 统计用户数量
     * <p>
     * 统计满足条件的用户总数，用于分页计算。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectTypeCode  主体类型编码，可选
     * @param domainCode       业务域编码，可选
     * @param keyword          关键词，可选
     * @return 用户数量
     */
    long countUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword);
}