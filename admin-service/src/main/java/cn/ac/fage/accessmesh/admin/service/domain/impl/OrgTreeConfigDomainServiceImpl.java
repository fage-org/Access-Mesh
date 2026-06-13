package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织树配置领域服务实现类
 * <p>
 * 封装组织树配置的数据访问逻辑，提供默认配置查询、根解析等方法。
 * 组织树配置用于定义组织树的展示规则与归属边界。
 * 所有查询均带有租户隔离和删除标记过滤，确保数据安全。
 * </p>
 */
@Service
public class OrgTreeConfigDomainServiceImpl implements OrgTreeConfigDomainService {

    private final SysOrgTreeConfigMapper orgTreeConfigMapper;
    private final OrgDomainService orgDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param orgTreeConfigMapper 组织树配置数据访问层
     * @param orgDomainService    组织领域服务，用于祖先链解析
     */
    public OrgTreeConfigDomainServiceImpl(SysOrgTreeConfigMapper orgTreeConfigMapper,
                                          OrgDomainService orgDomainService) {
        this.orgTreeConfigMapper = orgTreeConfigMapper;
        this.orgDomainService = orgDomainService;
    }

    /**
     * 查询租户的默认组织树配置
     */
    @Override
    public List<SysOrgTreeConfig> findDefaultConfigs(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return orgTreeConfigMapper.selectDefaultConfigs(tenantId);
    }

    @Override
    public String resolveTreeRootExternalId(Long tenantId, Long orgId) {
        if (tenantId == null || orgId == null) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "tenantId/orgId required for tree root resolution");
        }
        // 1. 加载 org 自身（不存在则视为业务失败）
        SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        // 2. 加载租户全部 SysOrgTreeConfig（不限 isDefault；user-org 关系适用于任何已配置树）
        List<SysOrgTreeConfig> configs = orgTreeConfigMapper.selectAllValid(tenantId);
        if (configs == null || configs.isEmpty()) {
            throw new BizException(AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
                "ORG_TREE_ROOT_NOT_RESOLVED: no tree config for tenantId=" + tenantId
                    + ", orgId=" + orgId);
        }
        Set<Long> rootOrgIdSet = new HashSet<>();
        for (SysOrgTreeConfig cfg : configs) {
            if (cfg.getRootOrgId() != null) {
                rootOrgIdSet.add(cfg.getRootOrgId());
            }
        }
        // 3. orgId 自身命中 rootOrgId 集合
        if (rootOrgIdSet.contains(orgId)) {
            return String.valueOf(orgId);
        }
        // 4. 沿祖先链查找命中
        List<Long> ancestorIds = orgDomainService.getAncestorIds(tenantId, orgId);
        if (ancestorIds != null) {
            for (Long ancestorId : ancestorIds) {
                if (ancestorId != null && rootOrgIdSet.contains(ancestorId)) {
                    return String.valueOf(ancestorId);
                }
            }
        }
        // 5. 未命中：游离 org，禁止参与 user-org / user_role 同步，禁止 fallback "1"
        throw new BizException(AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
            "ORG_TREE_ROOT_NOT_RESOLVED: orgId=" + orgId
                + " does not belong to any configured tree (tenantId=" + tenantId + ")");
    }

    @Override
    public Map<Long, String> resolveTreeRootExternalIds(Long tenantId, Collection<Long> orgIds) {
        if (tenantId == null) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "tenantId required for batch tree root resolution");
        }
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 1. 去重并过滤 null
        Set<Long> distinctOrgIds = new LinkedHashSet<>();
        for (Long id : orgIds) {
            if (id != null) {
                distinctOrgIds.add(id);
            }
        }
        if (distinctOrgIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 一次性加载租户全部 SysOrgTreeConfig，构建 rootOrgId 集合
        List<SysOrgTreeConfig> configs = orgTreeConfigMapper.selectAllValid(tenantId);
        Set<Long> rootOrgIdSet = new HashSet<>();
        if (configs != null) {
            for (SysOrgTreeConfig cfg : configs) {
                if (cfg.getRootOrgId() != null) {
                    rootOrgIdSet.add(cfg.getRootOrgId());
                }
            }
        }

        // 3. 一次性批量加载 orgId 自身校验存在性（任一不存在 → miss 集合）
        Map<Long, SysOrg> orgMap = orgDomainService.batchSelectValidByIdsMap(tenantId, distinctOrgIds);

        // 4. 一次性批量加载祖先链
        Map<Long, List<Long>> ancestorMap = orgDomainService.batchGetAncestorIds(tenantId, distinctOrgIds);

        Map<Long, String> result = new LinkedHashMap<>();
        Set<Long> missing = new LinkedHashSet<>();
        for (Long orgId : distinctOrgIds) {
            // org 不存在视为 miss
            if (orgMap == null || !orgMap.containsKey(orgId)) {
                missing.add(orgId);
                continue;
            }
            // 无任何 tree config → 全部 miss
            if (rootOrgIdSet.isEmpty()) {
                missing.add(orgId);
                continue;
            }
            // org 自身命中
            if (rootOrgIdSet.contains(orgId)) {
                result.put(orgId, String.valueOf(orgId));
                continue;
            }
            // 祖先链命中
            List<Long> ancestors = ancestorMap == null ? null : ancestorMap.get(orgId);
            Long hit = null;
            if (ancestors != null) {
                for (Long a : ancestors) {
                    if (a != null && rootOrgIdSet.contains(a)) {
                        hit = a;
                        break;
                    }
                }
            }
            if (hit != null) {
                result.put(orgId, String.valueOf(hit));
            } else {
                missing.add(orgId);
            }
        }

        if (!missing.isEmpty()) {
            // 禁止 fallback "1"，禁止部分返回；message 含全部缺失项
            throw new BizException(AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
                "ORG_TREE_ROOT_NOT_RESOLVED: missing tree root for orgIds=" + missing
                    + " (tenantId=" + tenantId + ")");
        }
        return result;
    }
}
