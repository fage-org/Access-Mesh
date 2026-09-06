package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.DependencyListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.DependencyCycleCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.access.permission.service.DependencyAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源依赖关系管理控制器
 * <p>
 * 提供资源依赖关系的CRUD操作、批量同步、循环检测等功能。
 * 资源依赖关系定义了资源之间的访问依赖，例如访问父资源权限才能访问子资源。
 * 依赖关系可用于实现级联权限控制。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/resource-dependency")
public class ResourceDependencyController {

    private final DependencyAppService dependencyManageService;

    /**
     * 构造函数注入依赖
     *
     * @param dependencyManageService 依赖关系管理服务
     */
    public ResourceDependencyController(DependencyAppService dependencyManageService) {
        this.dependencyManageService = dependencyManageService;
    }

    /**
     * 创建资源依赖关系
     * <p>
     * 创建源资源到目标资源的依赖关系。
     * 访问源资源时需要先获得目标资源的权限。
     * </p>
     *
     * @param req 依赖创建请求，包含源资源、目标资源、依赖类型
     * @return 创建成功的依赖关系详情
     */
    @PostMapping("/create")
    public R<ResourceDependencyResp> createDependency(@Valid @RequestBody ResourceDependencyCreateReq req) {
        return R.ok(dependencyManageService.createDependency(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 查询资源依赖关系列表
     * <p>
     * 查询指定资源的所有依赖关系。
     * </p>
     *
     * @param req 依赖列表查询请求，包含资源实体ID
     * @return 依赖关系列表
     */
    @PostMapping("/list")
    public R<ItemsResp<ResourceDependencyResp>> listDependencies(@Valid @RequestBody DependencyListReq req) {
        return R.ok(new ItemsResp<>(
            dependencyManageService.listDependencies(TenantContextHolder.getTenantId(), req.resourceEntityId())
        ));
    }

    /**
     * 删除资源依赖关系
     * <p>
     * 批量删除资源依赖关系。
     * </p>
     *
     * @param req ID集合请求，包含待删除的依赖关系ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public R<Void> deleteDependency(@Valid @RequestBody IdsReq req) {
        dependencyManageService.deleteDependencies(TenantContextHolder.getTenantId(), req.ids(), null);
        return R.ok();
    }

    /**
     * 批量同步资源依赖关系
     * <p>
     * 批量更新资源的依赖关系配置。
     * 用于导入或更新大量依赖关系。
     * </p>
     *
     * @param req 批量同步请求，包含依赖关系列表
     * @return 操作成功结果
     */
    @PostMapping("/batch-sync")
    public R<Void> batchSyncDependencies(@Valid @RequestBody DependencyBatchSyncReq req) {
        dependencyManageService.batchSyncDependencies(TenantContextHolder.getTenantId(), req, null);
        return R.ok();
    }

    /**
     * 更新资源依赖关系信息
     * <p>
     * 更新依赖关系的类型、目标资源等属性。
     * </p>
     *
     * @param req 依赖更新请求，包含依赖关系ID和新属性值
     * @return 更新后的依赖关系详情
     */
    @PostMapping("/update")
    public R<ResourceDependencyResp> updateDependency(@Valid @RequestBody ResourceDependencyUpdateReq req) {
        return R.ok(dependencyManageService.updateDependency(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 查询依赖关系图
     * <p>
     * 查询资源的依赖关系图结构，返回所有依赖关系。
     * 用于前端展示依赖关系的拓扑图。
     * </p>
     *
     * @param req 依赖图查询请求，可选指定资源实体ID
     * @return 依赖关系列表（完整图）
     */
    @PostMapping("/graph")
    public R<ItemsResp<ResourceDependencyResp>> graph(@Valid @RequestBody DependencyListReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<ResourceDependencyResp> items = req.resourceEntityId() != null
            ? dependencyManageService.listDependencies(tenantId, req.resourceEntityId())
            : dependencyManageService.listAllDependencies(tenantId);
        return R.ok(new ItemsResp<>(items));
    }

    /**
     * 检测依赖循环
     * <p>
     * 检测资源依赖关系中是否存在循环依赖。
     * 循环依赖会导致权限判定死循环，需要避免。
     * </p>
     *
     * @param req 循环检测请求，包含源资源和目标资源
     * @return 循环检测结果，包含是否存在循环、涉及的资源信息
     */
    @PostMapping("/check")
    public R<DependencyCycleCheckResp> check(@Valid @RequestBody ResourceDependencyCheckReq req) {
        boolean hasCycle = dependencyManageService.hasDependencyCycle(TenantContextHolder.getTenantId(), req);
        return R.ok(new DependencyCycleCheckResp(
            hasCycle, req.sourceResourceTypeCode(), req.sourceResourceCode(),
            req.targetResourceTypeCode(), req.targetResourceCode()));
    }
}