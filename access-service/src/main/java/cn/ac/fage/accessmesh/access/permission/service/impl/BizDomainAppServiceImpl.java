package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BizDomainResp;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.BizDomainAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.dao.DataIntegrityViolationException;
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
 * 所有操作均通过PermQueryEngine进行权限校验：
 * 读（detail/list/count）门禁 DOMAIN:VIEW 类型级；写（create/update/remove）门禁 SYSTEM_CONFIG:MANAGE。
 * T-PERM-026 收口：detail/update 按业务键 code 定位（uk_biz_domain）、list 服务端过滤分页、
 * Resp 返回 global、create 编码查重（预查 + uk_biz_domain DIVE 兜底）、
 * remove 删除保护（全局域不可删 + domain_config 引用检查拒删，20051）。
 * </p>
 */
@Service
public class BizDomainAppServiceImpl implements BizDomainAppService {

    private final BizDomainMapper bizDomainMapper;
    private final DomainConfigMapper domainConfigMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param bizDomainMapper    业务域数据访问层
     * @param domainConfigMapper 域配置数据访问层（remove 引用检查）
     * @param engine             权限查询引擎
     */
    public BizDomainAppServiceImpl(BizDomainMapper bizDomainMapper,
                                    DomainConfigMapper domainConfigMapper,
                                    PermQueryEngine engine) {
        this.bizDomainMapper = bizDomainMapper;
        this.domainConfigMapper = domainConfigMapper;
        this.engine = engine;
    }

    /**
     * 创建业务域
     * <p>
     * 创建新的业务域实体，设置编码、名称、描述等属性。
     * 编码租户内唯一：预查已占用拒绝 20052，check-then-insert 并发窗口由
     * uk_biz_domain 唯一索引兜底（DIVE 同映射 20052）。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含编码、名称、描述
     * @param operatorId 操作者ID，可选
     * @return 创建的业务域响应
     * @throws SecurityException          无权限时抛出
     * @throws BizException               编码重复（20052）时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "BIZ_DOMAIN_CREATE", targetType = "biz_domain", targetId = "#result.id()", summary = "'create biz domain ' + #req.code()")
    public BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to create biz domain");
        }

        if (bizDomainMapper.selectByCode(tenantId, req.code()) != null) {
            throw new BizException(PermissionErrorCode.DOMAIN_CODE_DUPLICATE.getCode(),
                "Biz domain code already exists: " + req.code());
        }

        BizDomain domain = new BizDomain();
        domain.setTenantId(tenantId);
        domain.setCode(req.code());
        domain.setName(req.name());
        domain.setDescription(req.description());
        domain.setGlobal(false);
        domain.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        domain.setCreatedAt(now);
        domain.setUpdatedAt(now);
        domain.setDeleteFlag(0L);
        try {
            bizDomainMapper.insert(domain);
        } catch (DataIntegrityViolationException e) {
            // DB 唯一索引兜底（TypeDefinition/ConflictRule 同模式）：并发窗口重复编码映射 20052 而非裸 99999
            if (isUniqueViolationOn(e, "uk_biz_domain")) {
                throw new BizException(PermissionErrorCode.DOMAIN_CODE_DUPLICATE.getCode(),
                    "Biz domain code already exists: " + req.code());
            }
            throw e;
        }
        return toBizDomainResp(domain);
    }

    /**
     * 获取业务域详情（业务键 code 定位）
     * <p>
     * 门禁 DOMAIN:VIEW 类型级（与 list 同级，先于查询避免存在性泄露）；
     * 未知编码与已删除域同口径返回 data=null 不抛错。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @return 业务域响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public BizDomainResp getBizDomain(Long tenantId, String domainCode) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DOMAIN, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN");
        }

        BizDomain domain = bizDomainMapper.selectByCode(tenantId, domainCode);
        return domain != null ? toBizDomainResp(domain) : null;
    }

    /**
     * 按条件统计有效业务域数量
     * <p>
     * 需要DOMAIN_VIEW权限（类型级）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（code/name/description LIKE，大小写敏感）
     * @return 有效行数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public long countBizDomains(Long tenantId, String keyword) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DOMAIN, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN");
        }
        return bizDomainMapper.countByCondition(tenantId, normalize(keyword));
    }

    /**
     * 按条件分页查询有效业务域
     * <p>
     * 需要DOMAIN_VIEW权限（类型级）。ORDER BY code, id。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（code/name/description LIKE，大小写敏感）
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 业务域响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public List<BizDomainResp> listBizDomains(Long tenantId, String keyword, int offset, int limit) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DOMAIN, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN");
        }

        return bizDomainMapper.selectPageByCondition(tenantId, normalize(keyword), limit, offset)
            .stream().map(this::toBizDomainResp).collect(Collectors.toList());
    }

    /**
     * 更新业务域（业务键 code 定位）
     * <p>
     * 更新业务域的名称、描述等属性。code 为唯一键不可改；
     * name/description 为 null 表示不更新，description 传空串表示显式清空。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含业务域编码和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的业务域响应
     * @throws SecurityException     无权限时抛出
     * @throws BizException          业务域不存在（20017）时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "BIZ_DOMAIN_UPDATE", targetType = "biz_domain", targetId = "#result.id()", summary = "'update biz domain ' + #req.domainCode()")
    public BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to update biz domain");
        }

        BizDomain domain = bizDomainMapper.selectByCode(tenantId, req.domainCode());
        if (domain == null) throw new BizException(PermissionErrorCode.DOMAIN_NOT_FOUND.getCode(), "BizDomain not found: " + req.domainCode());
        if (req.name() != null) domain.setName(req.name());
        if (req.description() != null) domain.setDescription(req.description());
        domain.setUpdatedAt(LocalDateTime.now());
        bizDomainMapper.update(domain);
        return toBizDomainResp(domain);
    }

    /**
     * 批量删除业务域（删除保护）
     * <p>
     * 批量软删除业务域。删除保护（20051）：
     * 目标域为全局域（global=true，每租户唯一）不可删；
     * 域下仍存在有效域配置（domain_config 引用）时拒删（schema 表注释「引用检查拒删」）。
     * 引用检查为一次批量查询（selectValidByDomainIds），满足 N+1 禁令。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        业务域ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     * @throws BizException      删除冲突（20051：全局域或存在域配置）时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "BIZ_DOMAIN_REMOVE", targetType = "biz_domain", targetId = "", summary = "'batch remove biz domains'")
    public void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
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

        List<String> globalCodes = entities.stream()
            .filter(d -> Boolean.TRUE.equals(d.getGlobal()))
            .map(BizDomain::getCode)
            .collect(Collectors.toList());
        if (!globalCodes.isEmpty()) {
            throw new BizException(PermissionErrorCode.DOMAIN_DELETE_CONFLICT.getCode(),
                "Global biz domain cannot be deleted: " + String.join(",", globalCodes));
        }

        Set<Long> validIds = entities.stream()
            .map(BizDomain::getId)
            .collect(Collectors.toSet());
        List<DomainConfig> referencedConfigs = domainConfigMapper.selectValidByDomainIds(tenantId, validIds);
        if (!referencedConfigs.isEmpty()) {
            Set<String> referencedDomainCodes = entities.stream()
                .map(BizDomain::getCode)
                .collect(Collectors.toSet());
            throw new BizException(PermissionErrorCode.DOMAIN_DELETE_CONFLICT.getCode(),
                "Biz domain has domain configs, remove them first: " + String.join(",", referencedDomainCodes));
        }

        LocalDateTime now = LocalDateTime.now();
        bizDomainMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " biz_domain row(s)");
    }

    /**
     * 过滤参数规整：空白串归一为 null（与 SQL <if> 判空语义一致，system-config 同范式）
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * PG 唯一约束违反消息含约束名，沿 cause 链匹配（TypeDefinition/ConflictRule 同模式）
     */
    private boolean isUniqueViolationOn(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains(constraintName)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
            d.getName(), d.getDescription(),
            Boolean.TRUE.equals(d.getGlobal()), d.getCreatedAt()
        );
    }
}
