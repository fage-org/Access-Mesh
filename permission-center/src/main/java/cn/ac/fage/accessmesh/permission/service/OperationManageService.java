package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;

import java.util.List;

/**
 * 操作权限管理服务接口
 * <p>
 * 提供操作权限的CRUD操作。
 * 操作权限定义了系统支持的各种操作类型，如查看、编辑、删除、管理等。
 * </p>
 */
public interface OperationManageService {

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
     *
     * @param tenantId   租户ID
     * @param operationId 操作权限ID
     * @return 操作权限详情
     */
    OperationPermissionResp getOperation(Long tenantId, Long operationId);

    /**
     * 查询操作权限列表
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码，可选
     * @param domainCode       业务域编码，可选
     * @return 操作权限列表
     */
    List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode, String domainCode);

    /**
     * 更新操作权限
     *
     * @param tenantId   租户ID
     * @param operationId 操作权限ID
     * @param name       操作权限名称
     * @param binaryBit  二进制位
     * @param inheritMask 继承掩码
     * @param operatorId 操作者ID
     * @return 更新后的操作权限详情
     */
    OperationPermissionResp updateOperation(Long tenantId, Long operationId, String name, Long binaryBit, Long inheritMask, Long operatorId);

    /**
     * 批量删除操作权限
     *
     * @param tenantId    租户ID
     * @param operationIds 操作权限ID列表
     * @param operatorId  操作者ID
     */
    void deleteOperations(Long tenantId, List<Long> operationIds, Long operatorId);
}