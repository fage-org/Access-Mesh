package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceTreeResp;
import cn.ac.fage.accessmesh.access.permission.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.permission.util.PageUtil;
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
public class ResourceController {

    private final ResourceManageAppService resourceManageAppService;

    /**
     * 构造函数注入依赖
     *
     * @param resourceManageAppService 资源管理服务
     */
    public ResourceController(ResourceManageAppService resourceManageAppService) {
        this.resourceManageAppService = resourceManageAppService;
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
    public R<ResourceResp> createResource(@Valid @RequestBody ResourceCreateReq req) {
        return R.ok(resourceManageAppService.createResource(TenantContextHolder.getTenantId(), req, null));
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
    public R<ItemsResp<ResourceResp>> batchCreateResources(@Valid @RequestBody ResourceBatchCreateReq req) {
        return R.ok(new ItemsResp<>(
            resourceManageAppService.batchCreateResources(TenantContextHolder.getTenantId(), req.items(), null)
        ));
    }

    /**
     * 获取资源详情
     * <p>
     * 以业务键 (resourceTypeCode, code, codeType) 查询资源的完整信息（T-PERM-028）。
     * </p>
     *
     * @param req 资源业务键请求
     * @return 资源详情信息
     */
    @PostMapping("/detail")
    public R<ResourceResp> getResource(@Valid @RequestBody ResourceKeyReq req) {
        return R.ok(resourceManageAppService.getResource(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 更新资源信息
     * <p>
     * 以业务键定位后更新资源的名称、状态等可编辑属性（编码为业务键不可更新）。
     * </p>
     *
     * @param req 资源更新请求，包含业务键和新属性值
     * @return 更新后的资源详情
     */
    @PostMapping("/update")
    public R<ResourceResp> updateResource(@Valid @RequestBody ResourceUpdateReq req) {
        return R.ok(resourceManageAppService.updateResource(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 移动资源
     * <p>
     * 以业务键定位资源与目标父资源，调整资源在树结构中的位置。
     * </p>
     *
     * @param req 资源移动请求，包含业务键对（parent 为 null 移动到顶层）
     * @return 操作成功结果
     */
    @PostMapping("/move")
    public R<Void> moveResource(@Valid @RequestBody ResourceMoveReq req) {
        resourceManageAppService.moveResource(TenantContextHolder.getTenantId(), req, null);
        return R.ok();
    }

    /**
     * 删除资源
     * <p>
     * 以业务键批量删除资源实体，会同时处理资源下的权限配置。
     * </p>
     *
     * @param req 业务键集合请求，包含待删除的资源业务键列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public R<Void> deleteResource(@Valid @RequestBody ResourceKeysReq req) {
        resourceManageAppService.deleteResources(TenantContextHolder.getTenantId(), req.items(), null);
        return R.ok();
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
    public R<ItemsResp<ResourceTreeResp>> getResourceTree(@Valid @RequestBody ResourceTreeReq req) {
        return R.ok(new ItemsResp<>(
            resourceManageAppService.getResourceTree(TenantContextHolder.getTenantId(), req.resourceTypeCode(), req.domainCode())
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
    public R<PageResp<ResourceResp>> listResources(@Valid @RequestBody ResourceListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = resourceManageAppService.countResources(tenantId, req.resourceTypeCode(), req.domainCode());
        List<ResourceResp> items = resourceManageAppService.listResources(tenantId, req.resourceTypeCode(), req.domainCode(), offset, pageSize);
        return R.ok(new PageResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }
}