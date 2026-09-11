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
 * 管理端点定位一律使用业务键 code（uk tenant+code，T-PERM-029 从内部主键切换）；
 * 内部主键 id 仅在授权链路（role_resource_permission.condition_id）中引用。
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
     * 根据条件编码查询权限条件详情。
     * </p>
     *
     * @param tenantId       租户ID
     * @param conditionCode  条件编码（业务键）
     * @return 条件详情
     * @throws cn.ac.fage.accessmesh.common.exception.BizException 条件不存在（20006）
     */
    ConditionResp getCondition(Long tenantId, String conditionCode);

    /**
     * 更新权限条件
     * <p>
     * 以业务键 code 定位并更新权限条件的基本信息和规则（code 本身不可更新）。
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
     * 获取租户的所有权限条件列表（条件模板数量有界，全量返回不分页，
     * 与 domain-config/service-config 同款定案）。
     * 双轨制（T-PERM-048）：缺省只返回 MANAGED 管理页条件（权限条件页口径）；
     * includeInline=true 时含授权页内联条件（授权页回显用）。
     * </p>
     *
     * @param tenantId      租户ID
     * @param includeInline 是否包含内联条件
     * @return 条件列表
     */
    List<ConditionResp> listConditions(Long tenantId, Boolean includeInline);

    /**
     * 按业务键批量删除权限条件
     * <p>
     * 按条件编码集合批量软删除权限条件；请求中不存在的编码静默跳过（幂等语义）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param codes      条件编码列表
     * @param operatorId 操作者ID
     */
    void deleteConditionsByCodes(Long tenantId, List<String> codes, Long operatorId);
}
