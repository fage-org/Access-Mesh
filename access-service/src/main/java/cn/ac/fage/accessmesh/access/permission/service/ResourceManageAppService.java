package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceTreeResp;

import java.util.List;

/**
 * 资源管理服务接口
 * <p>
 * 提供资源实体的CRUD操作、树结构管理和API映射管理功能。
 * 资源是权限系统中的被保护对象，如文件、项目、数据等。
 * </p>
 */
public interface ResourceManageAppService {

    /**
     * 创建资源实体
     * <p>
     * 创建新的资源实体，设置资源类型、编码、名称等属性。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        资源创建请求
     * @param operatorId 操作者ID
     * @return 创建的资源详情
     */
    ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId);

    /**
     * 批量创建资源实体
     * <p>
     * 批量创建多个资源实体，提高创建效率。
     * </p>
     *
     * @param tenantId   租户ID
     * @param reqs       资源创建请求列表
     * @param operatorId 操作者ID
     * @return 创建的资源列表
     */
    List<ResourceResp> batchCreateResources(Long tenantId, List<ResourceCreateReq> reqs, Long operatorId);

    /**
     * 获取资源详情
     * <p>
     * 以业务键 (resourceTypeCode, code, codeType) 查询资源实体详情（T-PERM-028）。
     * 类型级 RESOURCE:VIEW 门禁；业务键查不到抛 20004。
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      资源业务键
     * @return 资源详情
     */
    ResourceResp getResource(Long tenantId, ResourceKeyReq key);

    /**
     * 更新资源
     * <p>
     * 以业务键定位后更新资源实体的可编辑字段（name/path/status/sortOrder/extra）。
     * 业务键字段不可更新；extraClear=true 显式清空 extra（T-PERM-028）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        资源更新请求（业务键 + 可编辑字段）
     * @param operatorId 操作者ID
     * @return 更新后的资源详情
     */
    ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId);

    /**
     * 移动资源
     * <p>
     * 以业务键定位被移动资源与目标父资源，调整资源的层级位置。
     * parent 为 null 移动到顶层；跨类型与自身/子孙目标抛 20053（T-PERM-028）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        资源移动请求（业务键对）
     * @param operatorId 操作者ID
     */
    void moveResource(Long tenantId, ResourceMoveReq req, Long operatorId);

    /**
     * 批量删除资源
     * <p>
     * 以业务键批量定位后软删除资源实体（级联子孙与关联权限，T-PERM-028）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param keys       资源业务键列表
     * @param operatorId 操作者ID
     */
    void deleteResources(Long tenantId, List<ResourceKeyReq> keys, Long operatorId);

    /**
     * 获取资源树
     * <p>
     * 获取租户的资源树结构，可限定资源类型和业务域。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码，可选
     * @param domainCode       业务域编码，可选
     * @return 资源树响应列表
     */
    List<ResourceTreeResp> getResourceTree(Long tenantId, String resourceTypeCode, String domainCode);

    /**
     * 查询资源列表
     * <p>
     * 获取租户的资源扁平列表，可按资源类型和业务域筛选，支持分页。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码，可选
     * @param domainCode       业务域编码，可选
     * @param offset           分页偏移量
     * @param limit            每页条数
     * @return 资源列表
     */
    List<ResourceResp> listResources(Long tenantId, String resourceTypeCode, String domainCode, int offset, int limit);

    /**
     * 统计资源数量
     * <p>
     * 统计满足条件的资源总数，用于分页计算。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码，可选
     * @param domainCode       业务域编码，可选
     * @return 资源数量
     */
    long countResources(Long tenantId, String resourceTypeCode, String domainCode);

    /**
     * 添加API映射
     * <p>
     * 为资源添加API映射关系，关联HTTP接口与资源。
     * 用于Gateway的接口权限检查。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      API映射添加请求
     * @return 创建的API映射详情
     */
    ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req);

    /**
     * 移除API映射
     * <p>
     * 根据映射ID批量移除API映射关系（租户隔离的软删除）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param mappingIds 映射ID列表
     * @param operatorId 操作者ID
     */
    void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId);

    /**
     * 查询API映射列表
     * <p>
     * 查询API映射列表，可按资源ID和服务编码筛选（AND语义）。
     * </p>
     *
     * @param tenantId    租户ID
     * @param resourceId  资源ID，可选
     * @param serviceCode 服务编码，可选
     * @return API映射列表
     */
    List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId, String serviceCode);

    /**
     * 更新API映射
     * <p>
     * 更新API映射的基本信息。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      API映射更新请求
     * @return 更新后的API映射详情
     */
    ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req);
}