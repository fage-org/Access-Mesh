package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.*;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp;
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源实体管理控制器
 * <p>
 * 提供资源实体的CRUD操作、树结构查询、批量创建等功能。
 * 资源实体是权限系统中的受保护对象，如菜单、按钮、API等。
 * 所有接口采用POST + JSON Body方式。
 * 租户ID通过TenantContextHolder从X-Tenant-Id请求头获取。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/resource-entity")
public class ResourceManageController {

    private final ResourceManageService resourceManageService;

    /**
     * 构造函数注入依赖
     *
     * @param resourceManageService 资源管理服务
     */
    public ResourceManageController(ResourceManageService resourceManageService) {
        this.resourceManageService = resourceManageService;
    }

    /**
     * 创建单个资源
     * <p>
     * 在指定域下创建新的资源实体，如菜单、按钮、API接口等。
     * </p>
     *
     * @param req 资源创建请求，包含资源基本信息
     * @return 创建成功的资源详情
     */
    @PostMapping("/create")
    public PermResult<ResourceResp> createResource(@Valid @RequestBody ResourceCreateReq req) {
        return PermResult.success(resourceManageService.createResource(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 批量创建资源
     * <p>
     * 批量创建多个资源实体，用于快速导入大量资源配置。
     * </p>
     *
     * @param req 批量资源创建请求，包含资源列表
     * @return 创建成功的资源列表
     */
    @PostMapping("/batch-create")
    public PermResult<ItemsResp<ResourceResp>> batchCreateResources(@Valid @RequestBody ResourceBatchCreateReq req) {
        return PermResult.success(new ItemsResp<>(
            resourceManageService.batchCreateResources(TenantContextHolder.getTenantId(), req.items(), null)
        ));
    }

    /**
     * 获取资源详情
     * <p>
     * 根据资源ID查询资源的完整信息。
     * </p>
     *
     * @param req ID请求，包含资源ID
     * @return 资源详情信息
     */
    @PostMapping("/detail")
    public PermResult<ResourceResp> getResource(@Valid @RequestBody IdReq req) {
        return PermResult.success(resourceManageService.getResource(TenantContextHolder.getTenantId(), req.id()));
    }

    /**
     * 更新资源信息
     * <p>
     * 更新资源的名称、编码、状态等属性。
     * </p>
     *
     * @param req 资源更新请求，包含待更新的资源ID和新属性值
     * @return 更新后的资源详情
     */
    @PostMapping("/update")
    public PermResult<ResourceResp> updateResource(@Valid @RequestBody ResourceUpdateReq req) {
        return PermResult.success(resourceManageService.updateResource(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 移动资源
     * <p>
     * 将资源移动到新的父资源下，调整资源在树结构中的位置。
     * </p>
     *
     * @param req 资源移动请求，包含资源ID和目标父资源ID
     * @return 操作成功结果
     */
    @PostMapping("/move")
    public PermResult<Void> moveResource(@Valid @RequestBody ResourceMoveReq req) {
        resourceManageService.moveResource(TenantContextHolder.getTenantId(), req.resourceId(), req.parentId(), null);
        return PermResult.success();
    }

    /**
     * 删除资源
     * <p>
     * 批量删除资源实体，会同时处理资源下的权限配置。
     * </p>
     *
     * @param req ID集合请求，包含待删除的资源ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteResource(@Valid @RequestBody IdsReq req) {
        resourceManageService.deleteResources(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    /**
     * 查询资源树
     * <p>
     * 返回指定资源类型和域下的资源层级树结构，用于前端展示资源组织关系。
     * </p>
     *
     * @param req 资源树查询请求，包含资源类型编码和域编码
     * @return 资源树结构列表
     */
    @PostMapping("/tree")
    public PermResult<ItemsResp<ResourceTreeResp>> getResourceTree(@Valid @RequestBody ResourceTreeReq req) {
        return PermResult.success(new ItemsResp<>(
            resourceManageService.getResourceTree(TenantContextHolder.getTenantId(), req.resourceTypeCode(), req.domainCode())
        ));
    }

    /**
     * 分页查询资源列表
     * <p>
     * 支持按资源类型、域过滤，返回分页结果。
     * </p>
     *
     * @param req 资源列表查询请求，包含分页参数和过滤条件
     * @return 分页资源列表结果
     */
    @PostMapping("/list")
    public PermResult<PaginatedResp<ResourceResp>> listResources(@Valid @RequestBody ResourceListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = resourceManageService.countResources(tenantId, req.resourceTypeCode(), req.domainCode());
        List<ResourceResp> items = resourceManageService.listResources(tenantId, req.resourceTypeCode(), req.domainCode(), offset, pageSize);
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }
}