package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeysReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.access.permission.service.OperationAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作权限管理控制器
 * <p>
 * 提供操作权限的CRUD操作和查询功能。
 * 操作权限定义了可执行的动作类型，如查看、编辑、删除等。
 * 采用位运算设计，支持权限继承和组合。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/operation-permission")
public class OperationController {

    private final OperationAppService operationAppService;

    /**
     * 构造函数注入依赖
     *
     * @param operationAppService 操作权限管理服务
     */
    public OperationController(OperationAppService operationAppService) {
        this.operationAppService = operationAppService;
    }

    /**
     * 创建操作权限
     * <p>
     * 创建新的操作权限定义，如查看(VIEW)、编辑(EDIT)、删除(DELETE)等。
     * 需指定资源类型、操作编码、二进制位和继承掩码。
     * </p>
     *
     * @param req 操作创建请求，包含资源类型编码、操作编码、名称、二进制位、继承掩码
     * @return 创建成功的操作权限详情
     */
    @PostMapping("/create")
    public PermResult<OperationPermissionResp> createOperation(@Valid @RequestBody OperationCreateReq req) {
        return PermResult.success(operationAppService.createOperation(
                TenantContextHolder.getTenantId(), req.resourceTypeCode(), req.code(), req.name(), req.binaryBit(), req.inheritMask(), null));
    }

    /**
     * 获取操作权限详情
     * <p>
     * 以业务键 (resourceTypeCode, code) 查询操作的完整信息（T-PERM-028；
     * resourceTypeCode 为 null/空白表示全局操作）。
     * </p>
     *
     * @param req 操作业务键请求
     * @return 操作权限详情信息
     */
    @PostMapping("/detail")
    public PermResult<OperationPermissionResp> getOperation(@Valid @RequestBody OperationKeyReq req) {
        return PermResult.success(operationAppService.getOperation(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询操作权限列表
     * <p>
     * 返回指定资源类型和业务域下的操作权限列表。
     * 可用于前端权限配置选择。
     * </p>
     *
     * @param req 操作列表查询请求，包含资源类型编码和域编码过滤条件
     * @return 操作权限列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<OperationPermissionResp>> listOperations(@Valid @RequestBody OperationListReq req) {
        return PermResult.success(new ItemsResp<>(
            operationAppService.listOperations(TenantContextHolder.getTenantId(), req.resourceTypeCode(), req.domainCode(),
                req.includeGlobalFallback())
        ));
    }

    /**
     * 更新操作权限信息
     * <p>
     * 以业务键定位后更新操作的名称、二进制位、继承掩码等属性（编码为业务键不可更新）。
     * </p>
     *
     * @param req 操作更新请求，包含业务键和新属性值
     * @return 更新后的操作权限详情
     */
    @PostMapping("/update")
    public PermResult<OperationPermissionResp> updateOperation(@Valid @RequestBody OperationUpdateReq req) {
        return PermResult.success(operationAppService.updateOperation(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 删除操作权限
     * <p>
     * 以业务键批量软删除操作权限，会同时处理操作权限关联的数据。
     * </p>
     *
     * @param req 业务键集合请求，包含待删除的操作权限业务键列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteOperation(@Valid @RequestBody OperationKeysReq req) {
        operationAppService.deleteOperations(TenantContextHolder.getTenantId(), req.items(), null);
        return PermResult.success();
    }
}