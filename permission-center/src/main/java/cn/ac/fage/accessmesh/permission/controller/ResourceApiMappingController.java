package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingListReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.service.ResourceManageAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源API映射管理控制器
 * <p>
 * 提供资源与API接口的映射关系管理功能。
 * API映射将资源实体与具体的HTTP接口关联，用于Gateway进行接口级权限校验。
 * 例如：菜单资源可以映射到多个API接口，访问菜单权限才能调用这些接口。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/resource-api-mapping")
public class ResourceApiMappingController {

    private final ResourceManageAppService resourceManageService;

    /**
     * 构造函数注入依赖
     *
     * @param resourceManageService 资源管理服务
     */
    public ResourceApiMappingController(ResourceManageAppService resourceManageService) {
        this.resourceManageService = resourceManageService;
    }

    /**
     * 创建API映射
     * <p>
     * 将资源实体与API接口建立映射关系。
     * 支持指定服务编码、HTTP方法、路径等接口属性。
     * </p>
     *
     * @param req API映射创建请求，包含资源ID、服务编码、HTTP方法、路径
     * @return 创建成功的映射详情
     */
    @PostMapping("/create")
    public PermResult<ApiMappingResp> addApiMapping(@Valid @RequestBody ApiMappingAddReq req) {
        return PermResult.success(resourceManageService.addApiMapping(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 更新API映射信息
     * <p>
     * 更新映射的HTTP方法、路径、权限要求等属性。
     * </p>
     *
     * @param req API映射更新请求，包含映射ID和新属性值
     * @return 更新后的映射详情
     */
    @PostMapping("/update")
    public PermResult<ApiMappingResp> updateApiMapping(@Valid @RequestBody ApiMappingUpdateReq req) {
        return PermResult.success(resourceManageService.updateApiMapping(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 删除API映射
     * <p>
     * 批量删除资源与API接口的映射关系。
     * </p>
     *
     * @param req ID集合请求，包含待删除的映射ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> removeApiMapping(@Valid @RequestBody IdsReq req) {
        resourceManageService.removeApiMappingsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    /**
     * 查询API映射列表
     * <p>
     * 查询指定资源或服务的所有API映射关系。
     * 用于查看资源配置的接口权限范围。
     * </p>
     *
     * @param req API映射列表查询请求，包含资源ID和服务编码过滤条件
     * @return API映射列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<ApiMappingResp>> listApiMappings(@Valid @RequestBody ApiMappingListReq req) {
        return PermResult.success(new ItemsResp<>(
            resourceManageService.listApiMappings(TenantContextHolder.getTenantId(), req.resourceId(), req.serviceCode())
        ));
    }
}