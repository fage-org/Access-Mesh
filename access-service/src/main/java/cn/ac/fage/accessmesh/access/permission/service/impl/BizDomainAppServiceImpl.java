package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BizDomainResp;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.service.BizDomainAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorSubjectResolver;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务域应用服务实现类
 * <p>
 * 提供业务域的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class BizDomainAppServiceImpl implements BizDomainAppService {

    private final BizDomainMapper bizDomainMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param bizDomainMapper 业务域数据访问层
     * @param engine          权限查询引擎
     */
    public BizDomainAppServiceImpl(BizDomainMapper bizDomainMapper,
                                    PermQueryEngine engine) {
        this.bizDomainMapper = bizDomainMapper;
        this.engine = engine;
    }

    /**
     * 创建业务域
     * <p>
     * 创建新的业务域实体，设置编码、名称、描述等属性。
     * 业务域用于划分系统的业务范围，实现多业务域的权限隔离。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含编码、名称、描述
     * @param operatorId 操作者ID，可选
     * @return 创建的业务域响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "BIZ_DOMAIN_CREATE", targetType = "biz_domain", targetId = "#result.id()", summary = "'create biz domain ' + #req.code()")
    public BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        Long operatorSubjectId = OperatorSubjectResolver.requireSubjectId(tenantId, operatorId, engine);

        if (!engine.hasPermission(tenantId, operatorSubjectId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to create biz domain");
        }

        BizDomain domain = new BizDomain();
        domain.setTenantId(tenantId);
        domain.setCode(req.code());
        domain.setName(req.name());
        domain.setDescription(req.description());
        domain.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        domain.setCreatedAt(now);
        domain.setUpdatedAt(now);
        domain.setDeleteFlag(0L);
        bizDomainMapper.insert(domain);
        return toBizDomainResp(domain);
    }

    /**
     * 获取业务域详情
     * <p>
     * 根据业务域ID查询业务域的完整信息。
     * 需要DOMAIN_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param domainId 业务域ID
     * @return 业务域响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public BizDomainResp getBizDomain(Long tenantId, Long domainId) {
        Long operatorSubjectId = OperatorSubjectResolver.requireSubjectId(
            tenantId, OperatorContext.getOperatorId(), engine);
        if (!engine.hasPermission(tenantId, operatorSubjectId, ResourceTypeCode.DOMAIN, domainId, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN:" + domainId);
        }

        BizDomain domain = bizDomainMapper.selectValidById(domainId, tenantId);
        return domain != null ? toBizDomainResp(domain) : null;
    }

    /**
     * 查询业务域列表
     * <p>
     * 查询租户下所有活跃的业务域。
     * 需要DOMAIN_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 业务域响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public List<BizDomainResp> listBizDomains(Long tenantId) {
        Long operatorSubjectId = OperatorSubjectResolver.requireSubjectId(
            tenantId, OperatorContext.getOperatorId(), engine);
        if (!engine.hasPermission(tenantId, operatorSubjectId, ResourceTypeCode.DOMAIN, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN");
        }

        return bizDomainMapper.selectByTenantId(tenantId).stream().map(this::toBizDomainResp).collect(Collectors.toList());
    }

    /**
     * 更新业务域
     * <p>
     * 更新业务域的名称、描述等属性。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含业务域ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的业务域响应
     * @throws SecurityException     无权限时抛出
     * @throws BizException          业务域不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "BIZ_DOMAIN_UPDATE", targetType = "biz_domain", targetId = "#req.domainId()", summary = "'update biz domain ' + #req.domainId()")
    public BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        Long operatorSubjectId = OperatorSubjectResolver.requireSubjectId(tenantId, operatorId, engine);

        if (!engine.hasPermission(tenantId, operatorSubjectId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to update biz domain");
        }

        BizDomain domain = bizDomainMapper.selectValidById(req.domainId(), tenantId);
        if (domain == null) throw new BizException(PermissionErrorCode.DOMAIN_NOT_FOUND.getCode(), "BizDomain not found: " + req.domainId());
        if (req.name() != null) domain.setName(req.name());
        if (req.description() != null) domain.setDescription(req.description());
        domain.setUpdatedAt(LocalDateTime.now());
        bizDomainMapper.update(domain);
        return toBizDomainResp(domain);
    }

    /**
     * 批量删除业务域
     * <p>
     * 批量软删除业务域。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        业务域ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "BIZ_DOMAIN_REMOVE", targetType = "biz_domain", targetId = "", summary = "'batch remove biz domains'")
    public void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        Long operatorSubjectId = OperatorSubjectResolver.requireSubjectId(tenantId, operatorId, engine);

        if (!engine.hasPermission(tenantId, operatorSubjectId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete biz domains");
        }

        if (ids == null || ids.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<BizDomain> entities = bizDomainMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream()
            .map(BizDomain::getId)
            .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        bizDomainMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " biz_domain row(s)");
    }

    /**
     * 将BizDomain实体转换为响应对象
     *
     * @param d 业务域实体
     * @return 业务域响应对象
     */
    private BizDomainResp toBizDomainResp(BizDomain d) {
        return new BizDomainResp(
            d.getId(), d.getTenantId(), d.getCode(),
            d.getName(), d.getDescription(), d.getCreatedAt()
        );
    }
}
