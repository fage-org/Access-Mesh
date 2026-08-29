package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BizDomainResp;

import java.util.List;

/**
 * 业务域应用服务接口
 * <p>
 * 提供业务域的CRUD操作（T-PERM-026 收口：detail/update 切业务键 code 定位、
 * list 服务端 keyword 过滤 + 分页、Resp 返回 global、删除保护——全局域不可删 +
 * 域下存在有效域配置时引用检查拒删）。
 * </p>
 */
public interface BizDomainAppService {

    /**
     * 创建业务域
     *
     * @param tenantId   租户ID
     * @param req        业务域创建请求
     * @param operatorId 操作者ID
     * @return 创建的业务域详情
     */
    BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId);

    /**
     * 获取业务域详情（按业务键 code 定位）
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @return 业务域详情，不存在返回null
     */
    BizDomainResp getBizDomain(Long tenantId, String domainCode);

    /**
     * 按条件统计有效业务域数量
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（code/name/description LIKE）
     * @return 有效行数
     */
    long countBizDomains(Long tenantId, String keyword);

    /**
     * 按条件分页查询有效业务域（ORDER BY code, id）
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 业务域列表
     */
    List<BizDomainResp> listBizDomains(Long tenantId, String keyword, int offset, int limit);

    /**
     * 更新业务域（按业务键 code 定位；code 不可改，name/description null=不更新）
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含业务域编码和要更新的属性
     * @param operatorId 操作者ID
     * @return 更新后的业务域详情
     */
    BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId);

    /**
     * 批量删除业务域（删除保护：全局域不可删、域下存在有效域配置时拒删 20051）
     *
     * @param tenantId   租户ID
     * @param ids        业务域ID列表
     * @param operatorId 操作者ID
     */
    void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId);
}
