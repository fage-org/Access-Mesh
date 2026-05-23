package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceDependencyResp;

import java.util.List;

/**
 * 资源依赖关系管理服务接口
 * <p>
 * 提供资源依赖关系的CRUD操作、循环依赖检测和批量同步功能。
 * 资源依赖关系定义资源之间的依赖层级，影响权限的继承和传播。
 * </p>
 */
public interface DependencyManageService {

    /**
     * 创建资源依赖关系
     * <p>
     * 创建新的资源依赖关系，设置依赖类型、父资源、子资源等属性。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        依赖关系创建请求
     * @param operatorId 操作者ID
     * @return 创建的依赖关系详情
     */
    ResourceDependencyResp createDependency(Long tenantId, ResourceDependencyCreateReq req, Long operatorId);

    /**
     * 查询资源的依赖列表
     * <p>
     * 获取指定资源的所有依赖关系列表。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 依赖关系列表
     */
    List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId);

    /**
     * 查询所有依赖关系
     * <p>
     * 获取租户的所有资源依赖关系列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 所有依赖关系列表
     */
    List<ResourceDependencyResp> listAllDependencies(Long tenantId);

    /**
     * 更新资源依赖关系
     * <p>
     * 更新依赖关系的基本信息。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        依赖关系更新请求
     * @param operatorId 操作者ID
     * @return 更新后的依赖关系详情
     */
    ResourceDependencyResp updateDependency(Long tenantId, ResourceDependencyUpdateReq req, Long operatorId);

    /**
     * 检测是否存在依赖循环
     * <p>
     * 检测添加依赖关系后是否会产生循环依赖。
     * 循环依赖会导致权限计算异常。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      循环依赖检测请求
     * @return 存在循环依赖返回true，否则返回false
     */
    boolean hasDependencyCycle(Long tenantId, ResourceDependencyCheckReq req);

    /**
     * 批量删除依赖关系
     * <p>
     * 批量删除多个资源依赖关系。
     * </p>
     *
     * @param tenantId      租户ID
     * @param dependencyIds 依赖关系ID列表
     * @param operatorId    操作者ID
     */
    void deleteDependencies(Long tenantId, List<Long> dependencyIds, Long operatorId);

    /**
     * 批量同步依赖关系
     * <p>
     * 批量同步资源的依赖关系，用于数据导入或迁移场景。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        批量同步请求
     * @param operatorId 操作者ID
     */
    void batchSyncDependencies(Long tenantId, DependencyBatchSyncReq req, Long operatorId);
}