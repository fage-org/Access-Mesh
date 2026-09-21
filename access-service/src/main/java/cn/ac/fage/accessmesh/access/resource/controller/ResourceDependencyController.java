package cn.ac.fage.accessmesh.access.resource.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.resource.dto.req.AutoGrantExplainReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.DependencyDeclarationStatusReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.DependencyListReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.access.resource.dto.resp.AutoGrantExplainResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.DependencyCycleCheckResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.DependencyDeclarationStatusResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.access.resource.service.DependencyAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源依赖关系管理控制器
 * <p>
 * 提供依赖编译图的只读列表、图与循环检查；声明写入由所属服务 manifest 通道承担。
 * 依赖图表示授予源资源操作时需补全的目标操作；读接口不承担运行时鉴权。
 * 依赖关系可用于实现级联权限控制。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/access/resource-dependency")
public class ResourceDependencyController {

    private final DependencyAppService dependencyAppService;

    /**
     * 构造函数注入依赖
     *
     * @param dependencyAppService 依赖关系查询服务（管理面只读）
     */
    public ResourceDependencyController(DependencyAppService dependencyAppService) {
        this.dependencyAppService = dependencyAppService;
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
            dependencyAppService.listDependencies(TenantContextHolder.getTenantId(), req.resourceEntityId())
        ));
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
            ? dependencyAppService.listDependencies(tenantId, req.resourceEntityId())
            : dependencyAppService.listAllDependencies(tenantId);
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
        boolean hasCycle = dependencyAppService.hasDependencyCycle(TenantContextHolder.getTenantId(), req);
        return R.ok(new DependencyCycleCheckResp(
            hasCycle, req.sourceResourceTypeCode(), req.sourceResourceCode(),
            req.targetResourceTypeCode(), req.targetResourceCode()));
    }

    /**
     * 角色自动授权来源解释（T-PERM-073，总册 §12.3.1）。
     * <p>解释"为什么生成该角色的自动权限"：共享逻辑 DAG（节点=完整事实键、边=直接推导
     * 关系+声明引用）；desired/actual 漂移显式标识，输出限额只截断展示。</p>
     */
    @PostMapping("/explain")
    public R<AutoGrantExplainResp> explain(@Valid @RequestBody AutoGrantExplainReq req) {
        return R.ok(dependencyAppService.explainAutoGrant(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 依赖声明诊断（T-PERM-073，总册 §12.3 declaration-status）。
     * <p>每服务 manifest 发布状态 + 声明行（含 REJECTED 原因）；只读，变更由所属服务
     * manifest 发布承担。</p>
     */
    @PostMapping("/declaration-status")
    public R<DependencyDeclarationStatusResp> declarationStatus(
            @Valid @RequestBody DependencyDeclarationStatusReq req) {
        return R.ok(dependencyAppService.declarationStatus(TenantContextHolder.getTenantId(), req));
    }
}