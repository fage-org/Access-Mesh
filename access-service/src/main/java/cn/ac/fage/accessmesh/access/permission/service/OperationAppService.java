package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationPermissionResp;

import java.util.List;

/**
 * 操作权限管理服务接口
 * <p>
 * 提供操作权限的CRUD操作。
 * 操作权限定义了系统支持的各种操作类型，如查看、编辑、删除、管理等。
 * </p>
 */
public interface OperationAppService {

    /**
     * 创建操作权限
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码
     * @param code             操作权限编码
     * @param name             操作权限名称
     * @param binaryBit        二进制位，用于位运算权限匹配
     * @param inheritMask      继承掩码，用于权限继承计算
     * @param operatorId       操作者ID
     * @return 创建的操作权限详情
     */
    OperationPermissionResp createOperation(Long tenantId, String resourceTypeCode, String code, String name, Long binaryBit, Long inheritMask, Long operatorId);

    /**
     * 获取操作权限详情
     * <p>
     * 以业务键 (resourceTypeCode, code) 查询操作权限详情（T-PERM-028；
     * 全局操作概念已退役，resourceTypeCode 必填）。
     * 类型级 OPERATION:VIEW 门禁；业务键查不到抛 20005。
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      操作权限业务键
     * @return 操作权限详情
     */
    OperationPermissionResp getOperation(Long tenantId, OperationKeyReq key);

    /**
     * 查询操作权限列表
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码，可选；指定但类型不存在时返回空列表
     * @return 操作权限列表
     */
    List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode);

    /**
     * 更新操作权限
     * <p>
     * 以业务键 (resourceTypeCode, code) 定位后更新名称、二进制位、继承掩码。
     * 业务键字段不可更新（T-PERM-028）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        操作更新请求（业务键 + 可编辑字段）
     * @param operatorId 操作者ID
     * @return 更新后的操作权限详情
     */
    OperationPermissionResp updateOperation(Long tenantId, OperationUpdateReq req, Long operatorId);

    /**
     * 批量删除操作权限
     * <p>
     * 以业务键批量定位后批量软删除（T-PERM-028）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param keys       操作权限业务键列表
     * @param operatorId 操作者ID
     */
    void deleteOperations(Long tenantId, List<OperationKeyReq> keys, Long operatorId);
}