package cn.ac.fage.accessmesh.access.org.service.domain.impl;

import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.org.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
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
import java.util.stream.Collectors;

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
    private final UserOrgDomainService userOrgDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param orgTreeConfigMapper 组织树配置数据访问层
     * @param orgDomainService    组织领域服务，用于祖先链解析
     * @param userOrgDomainService 用户-组织关系领域服务，用于身份目录归属守卫（T-ORG-002）
     */
    public OrgTreeConfigDomainServiceImpl(SysOrgTreeConfigMapper orgTreeConfigMapper,
                                          OrgDomainService orgDomainService,
                                          UserOrgDomainService userOrgDomainService) {
        this.orgTreeConfigMapper = orgTreeConfigMapper;
        this.orgDomainService = orgDomainService;
        this.userOrgDomainService = userOrgDomainService;
    }

    /**
     * 写入组织树配置（bootstrap 固定图专用；调用方负责装配完整字段与幂等检测）。
     */
    @Override
    public Long insert(SysOrgTreeConfig config) {
        orgTreeConfigMapper.insert(config);
        return config.getId();
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

    /**
     * 解析默认组织树的全部组织 ID（根 + 全部有效后代）。
     * 无默认配置的租户返回空列表——身份目录守卫按「无默认树语义」放行。
     */
    @Override
    public List<Long> resolveDefaultTreeOrgIds(Long tenantId) {
        List<SysOrgTreeConfig> defaultConfigs = findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            return List.of();
        }
        Long rootOrgId = defaultConfigs.get(0).getRootOrgId();
        if (rootOrgId == null) {
            return List.of();
        }
        return orgDomainService.getDescendantIdsIncludingSelf(tenantId, rootOrgId);
    }

    /**
     * 共享守卫核心（T-ORG-002 U001 拍板：拒绝并提示受影响人数）：
     * 默认树范围 old → new 收窄后失去最后归属的用户集合。
     * 两次批量查询：被移除范围（old - new）内组织的一次成员加载 +
     * 候选用户的一次全量归属加载，内存内过滤，无逐用户 SQL。
     */
    @Override
    public Set<Long> findUsersLosingDefaultHome(Long tenantId, Set<Long> oldDefaultOrgIds,
                                                Set<Long> newDefaultOrgIds) {
        if (tenantId == null || oldDefaultOrgIds == null || oldDefaultOrgIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> retainedOrgIds = newDefaultOrgIds == null ? Set.of() : newDefaultOrgIds;
        Set<Long> removedOrgIds = new HashSet<>(oldDefaultOrgIds);
        removedOrgIds.removeAll(retainedOrgIds);
        if (removedOrgIds.isEmpty()) {
            return Set.of();
        }
        List<SysUserOrg> removedMembers = userOrgDomainService.findByOrgIds(tenantId, List.copyOf(removedOrgIds));
        if (removedMembers == null || removedMembers.isEmpty()) {
            return Set.of();
        }
        Set<Long> candidateUserIds = removedMembers.stream()
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());
        // 候选用户全部归属一次加载，newDefaultOrgIds 内仍有归属者即保留
        Set<Long> retainedUserIds = userOrgDomainService.findByUserIds(tenantId, List.copyOf(candidateUserIds))
            .stream()
            .filter(uo -> retainedOrgIds.contains(uo.getOrgId()))
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());
        Set<Long> losingUserIds = new HashSet<>(candidateUserIds);
        losingUserIds.removeAll(retainedUserIds);
        return losingUserIds;
    }

    @Override
    public String resolveTreeRootExternalId(Long tenantId, Long orgId) {
        if (tenantId == null || orgId == null) {
            throw new BizException(AccessErrorCode.ADMIN_INVALID_PARAM.getCode(),
                "tenantId/orgId required for tree root resolution");
        }
        // 1. 加载 org 自身（不存在则视为业务失败）
        SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AccessErrorCode.ORG_NOT_FOUND.getCode(),
                AccessErrorCode.ORG_NOT_FOUND.getMessage());
        }
        // 2. 加载租户全部 SysOrgTreeConfig（不限 isDefault；user-org 关系适用于任何已配置树）
        List<SysOrgTreeConfig> configs = orgTreeConfigMapper.selectAllValid(tenantId);
        if (configs == null || configs.isEmpty()) {
            throw new BizException(AccessErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
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
        throw new BizException(AccessErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
            "ORG_TREE_ROOT_NOT_RESOLVED: orgId=" + orgId
                + " does not belong to any configured tree (tenantId=" + tenantId + ")");
    }

    @Override
    public Map<Long, String> resolveTreeRootExternalIds(Long tenantId, Collection<Long> orgIds) {
        if (tenantId == null) {
            throw new BizException(AccessErrorCode.ADMIN_INVALID_PARAM.getCode(),
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
            throw new BizException(AccessErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
                "ORG_TREE_ROOT_NOT_RESOLVED: missing tree root for orgIds=" + missing
                    + " (tenantId=" + tenantId + ")");
        }
        return result;
    }
}
