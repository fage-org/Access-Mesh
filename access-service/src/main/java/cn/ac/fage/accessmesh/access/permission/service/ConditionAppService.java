package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConditionResp;

import java.util.List;

/**
 * 权限条件管理服务接口
 * <p>
 * 提供权限条件的CRUD操作。
 * 权限条件用于限定权限的生效范围，如时间范围、数据属性等。
 * </p>
 */
public interface ConditionAppService {

    /**
     * 创建权限条件
     * <p>
     * 创建新的权限条件实体，设置条件类型、规则等属性。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        条件创建请求
     * @param operatorId 操作者ID
     * @return 创建的条件详情
     */
    ConditionResp createCondition(Long tenantId, ConditionCreateReq req, Long operatorId);

    /**
     * 获取权限条件详情
     * <p>
     * 根据条件ID查询权限条件详情。
     * </p>
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     * @return 条件详情
     */
    ConditionResp getCondition(Long tenantId, Long conditionId);

    /**
     * 更新权限条件
     * <p>
     * 更新权限条件的基本信息和规则。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        条件更新请求
     * @param operatorId 操作者ID
     * @return 更新后的条件详情
     */
    ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId);

    /**
     * 查询权限条件列表
     * <p>
     * 获取租户的所有权限条件列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 条件列表
     */
    List<ConditionResp> listConditions(Long tenantId);

    /**
     * 删除权限条件
     * <p>
     * 删除指定的权限条件实体。
     * </p>
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     * @param operatorId  操作者ID
     */
    void deleteCondition(Long tenantId, Long conditionId, Long operatorId);

    /**
     * 批量删除权限条件
     * <p>
     * 批量删除多个权限条件实体。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        条件ID列表
     * @param operatorId 操作者ID
     */
    void deleteConditionsByIds(Long tenantId, List<Long> ids, Long operatorId);
}