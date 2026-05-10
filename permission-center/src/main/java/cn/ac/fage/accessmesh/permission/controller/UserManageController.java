package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserListReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.service.UserManageService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 抽象用户（主体）管理控制器
 * <p>
 * 提供用户的同步、创建、更新、删除、查询等功能。
 * 用户是权限系统中的主体，可以是个人用户、组织用户等类型。
 * 所有接口采用POST + JSON Body方式。
 * 租户ID通过TenantContextHolder从X-Tenant-Id请求头获取。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/abstract-user")
public class UserManageController {

    private final UserManageService userManageService;

    /**
     * 构造函数注入依赖
     *
     * @param userManageService 用户管理服务
     */
    public UserManageController(UserManageService userManageService) {
        this.userManageService = userManageService;
    }

    /**
     * 同步用户信息
     * <p>
     * 从外部系统同步用户信息到权限中心。
     * 如果用户已存在则更新，不存在则创建。
     * </p>
     *
     * @param req 用户同步请求，包含外部用户ID、用户名、类型等
     * @return 同步后的用户详情
     */
    @PostMapping("/sync")
    public PermResult<UserResp> syncUser(@Valid @RequestBody UserSyncReq req) {
        return PermResult.success(userManageService.syncUser(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 创建用户
     * <p>
     * 在权限中心直接创建新用户。
     * </p>
     *
     * @param req 用户创建请求，包含用户基本信息
     * @return 创建成功的用户详情
     */
    @PostMapping("/create")
    public PermResult<UserResp> createUser(@Valid @RequestBody UserCreateReq req) {
        return PermResult.success(userManageService.createUser(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 更新用户信息
     * <p>
     * 更新用户的名称、状态、类型等属性。
     * </p>
     *
     * @param req 用户更新请求，包含用户ID和新属性值
     * @return 更新后的用户详情
     */
    @PostMapping("/update")
    public PermResult<UserResp> updateUser(@Valid @RequestBody UserUpdateReq req) {
        return PermResult.success(userManageService.updateUser(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 获取用户详情
     * <p>
     * 根据用户ID查询用户的完整信息。
     * </p>
     *
     * @param req ID请求，包含用户ID
     * @return 用户详情信息
     */
    @PostMapping("/detail")
    public PermResult<UserResp> getUser(@Valid @RequestBody IdReq req) {
        return PermResult.success(userManageService.getUser(TenantContextHolder.getTenantId(), req.id()));
    }

    /**
     * 删除用户
     * <p>
     * 批量删除用户，会同时处理用户的角色关联和权限配置。
     * </p>
     *
     * @param req ID集合请求，包含待删除的用户ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteUser(@Valid @RequestBody IdsReq req) {
        userManageService.deleteUsers(TenantContextHolder.getTenantId(), req.ids());
        return PermResult.success();
    }

    /**
     * 分页查询用户列表
     * <p>
     * 支持按主体类型、域、关键字过滤，返回分页结果。
     * </p>
     *
     * @param req 用户列表查询请求，包含分页参数和过滤条件
     * @return 分页用户列表结果
     */
    @PostMapping("/list")
    public PermResult<PaginatedResp<UserResp>> listUsers(@Valid @RequestBody UserListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = userManageService.countUsers(tenantId, req.subjectTypeCode(), req.domainCode(), req.keyword());
        List<UserResp> items = userManageService.listUsers(
            tenantId, req.subjectTypeCode(), req.domainCode(), req.keyword(), offset, pageSize
        );
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }
}