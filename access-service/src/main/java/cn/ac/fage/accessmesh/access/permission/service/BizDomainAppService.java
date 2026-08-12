package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BizDomainResp;

import java.util.List;

/**
 * 业务域应用服务接口
 * <p>
 * 提供业务域的CRUD操作。
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
     * 获取业务域详情
     *
     * @param tenantId 租户ID
     * @param domainId 业务域ID
     * @return 业务域详情
     */
    BizDomainResp getBizDomain(Long tenantId, Long domainId);

    /**
     * 查询业务域列表
     *
     * @param tenantId 租户ID
     * @return 业务域列表
     */
    List<BizDomainResp> listBizDomains(Long tenantId);

    /**
     * 更新业务域
     *
     * @param tenantId   租户ID
     * @param req        业务域更新请求
     * @param operatorId 操作者ID
     * @return 更新后的业务域详情
     */
    BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId);

    /**
     * 批量删除业务域
     *
     * @param tenantId   租户ID
     * @param ids        业务域ID列表
     * @param operatorId 操作者ID
     */
    void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId);
}
