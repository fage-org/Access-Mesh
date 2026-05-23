package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.TypeDefinitionResp;

import java.util.List;

/**
 * 类型定义应用服务接口
 * <p>
 * 提供类型定义的CRUD操作。
 * </p>
 */
public interface TypeDefinitionAppService {

    /**
     * 创建类型定义
     *
     * @param tenantId   租户ID
     * @param req        类型创建请求
     * @param operatorId 操作者ID
     * @return 创建的类型定义详情
     */
    TypeDefinitionResp createType(Long tenantId, TypeCreateReq req, Long operatorId);

    /**
     * 获取类型定义详情
     *
     * @param tenantId 租户ID
     * @param typeId   类型定义ID
     * @return 类型定义详情
     */
    TypeDefinitionResp getType(Long tenantId, Long typeId);

    /**
     * 查询类型定义列表
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码，可选
     * @return 类型定义列表
     */
    List<TypeDefinitionResp> listTypes(Long tenantId, String domainCode);

    /**
     * 更新类型定义
     *
     * @param tenantId   租户ID
     * @param req        类型更新请求
     * @param operatorId 操作者ID
     * @return 更新后的类型定义详情
     */
    TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId);

    /**
     * 批量删除类型定义
     *
     * @param tenantId   租户ID
     * @param ids        类型定义ID列表
     * @param operatorId 操作者ID
     */
    void deleteTypesByIds(Long tenantId, List<Long> ids, Long operatorId);
}
