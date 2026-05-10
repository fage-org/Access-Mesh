package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeListReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 类型定义管理控制器
 * <p>
 * 提供类型定义的CRUD操作和查询功能。
 * 类型定义用于规范化资源类型、主体类型、角色类型等枚举值。
 * 通过类型定义可以统一管理系统中使用的编码和名称映射。
 * 所有接口采用POST + JSON Body方式。
 * 租户ID通过TenantContextHolder从X-Tenant-Id请求头获取。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/type-definition")
public class ConfigManageController {

    private final ConfigManageService configManageService;

    /**
     * 构造函数注入依赖
     *
     * @param configManageService 配置管理服务
     */
    public ConfigManageController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    /**
     * 创建类型定义
     * <p>
     * 创建新的类型定义，用于规范系统中的枚举编码。
     * </p>
     *
     * @param req 类型创建请求，包含类型分类、编码、名称等
     * @return 创建成功的类型定义详情
     */
    @PostMapping("/create")
    public PermResult<TypeDefinitionResp> createType(@Valid @RequestBody TypeCreateReq req) {
        return PermResult.success(configManageService.createType(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 获取类型定义详情
     * <p>
     * 根据类型定义ID查询类型的完整信息。
     * </p>
     *
     * @param req ID请求，包含类型定义ID
     * @return 类型定义详情信息
     */
    @PostMapping("/detail")
    public PermResult<TypeDefinitionResp> getType(@Valid @RequestBody IdReq req) {
        return PermResult.success(configManageService.getType(TenantContextHolder.getTenantId(), req.id()));
    }

    /**
     * 查询类型定义列表
     * <p>
     * 返回指定业务域下的类型定义列表。
     * </p>
     *
     * @param req 类型列表查询请求，包含域编码过滤条件
     * @return 类型定义列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<TypeDefinitionResp>> listTypes(@Valid @RequestBody TypeListReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listTypes(TenantContextHolder.getTenantId(), req.domainCode())
        ));
    }

    /**
     * 删除类型定义
     * <p>
     * 批量删除类型定义，会同时处理类型关联的数据。
     * </p>
     *
     * @param req ID集合请求，包含待删除的类型定义ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteType(@Valid @RequestBody IdsReq req) {
        configManageService.deleteTypesByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    /**
     * 更新类型定义信息
     * <p>
     * 更新类型定义的名称、编码、描述等属性。
     * </p>
     *
     * @param req 类型更新请求，包含类型定义ID和新属性值
     * @return 更新后的类型定义详情
     */
    @PostMapping("/update")
    public PermResult<TypeDefinitionResp> updateType(@Valid @RequestBody TypeUpdateReq req) {
        return PermResult.success(configManageService.updateType(TenantContextHolder.getTenantId(), req, null));
    }
}