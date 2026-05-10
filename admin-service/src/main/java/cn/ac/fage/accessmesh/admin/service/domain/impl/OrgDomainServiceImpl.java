package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper.DescendantResult;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;

/**
 * 组织领域服务实现类
 * <p>
 * 封装组织树遍历、批量查询等核心领域逻辑。
 * 使用PostgreSQL CTE递归查询实现高效的树结构操作（子孙查询、祖先查询）。
 * 所有查询均带有租户隔离和删除标记过滤，确保数据安全。
 * 提供批量操作方法优化性能，避免N+1查询问题。
 * </p>
 */
@Service
public class OrgDomainServiceImpl implements OrgDomainService {

    private final SysOrgMapper orgMapper;

    /**
     * 构造函数注入依赖
     *
     * @param orgMapper 组织数据访问层
     */
    public OrgDomainServiceImpl(SysOrgMapper orgMapper) {
        this.orgMapper = orgMapper;
    }

    /**
     * 获取指定组织的所有子孙组织ID（不包括自身）
     * <p>
     * 使用PostgreSQL CTE递归查询，性能高效。
     * 用于删除组织时的级联删除校验。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgId    组织ID
     * @return 子孙组织ID列表
     */
    @Override
    public List<Long> getDescendantIds(Long tenantId, Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        List<Long> ids = orgMapper.selectDescendantIds(tenantId, orgId);
        return ids != null ? ids : List.of();
    }

    /**
     * 获取指定组织的所有子孙组织ID（包括自身）
     * <p>
     * 使用PostgreSQL CTE递归查询，性能高效。
     * 用于删除组织时的级联删除操作。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgId    组织ID
     * @return 子孙组织ID列表（包含自身）
     */
    @Override
    public List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        List<Long> ids = orgMapper.selectDescendantIdsIncludingSelf(tenantId, orgId);
        return ids != null ? ids : List.of();
    }

    /**
     * 批量获取多个组织的子孙ID
     * <p>
     * 使用单次批量CTE查询替代N+1查询，性能优化。
     * 为每个输入ID初始化空列表，确保结果完整性。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgIds   组织ID集合
     * @return orgId到子孙ID列表的映射
     */
    @Override
    public Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 为每个输入ID初始化空列表
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long id : orgIds) {
            result.put(id, new ArrayList<>());
        }

        // 性能优化：使用单次批量CTE查询替代N+1查询
        List<DescendantResult> descendants = orgMapper.selectBatchDescendantIds(tenantId, orgIds);
        for (DescendantResult dr : descendants) {
            Long rootId = dr.getOrgId();
            Long descId = dr.getDescendantId();
            if (rootId != null && descId != null) {
                result.computeIfAbsent(rootId, k -> new ArrayList<>()).add(descId);
            }
        }

        return result;
    }

    /**
     * 获取指定组织的祖先组织ID
     * <p>
     * 使用批量加载模式避免N+1查询。
     * 从当前组织向上遍历到根组织。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgId    组织ID
     * @return 祖先组织ID列表
     */
    @Override
    public List<Long> getAncestorIds(Long tenantId, Long orgId) {
        // 性能优化：委托给批量方法避免N+1查询
        Map<Long, List<Long>> ancestorMap = batchGetAncestorIds(tenantId, Set.of(orgId));
        return ancestorMap.getOrDefault(orgId, List.of());
    }

    /**
     * 批量获取多个组织的祖先ID
     * <p>
     * 使用批量加载模式，一次性加载所有组织及其祖先链。
     * 避免递归调用导致的N+1查询问题。
     * 采用循环加载策略：先加载输入ID，再加载其父ID，直到所有祖先加载完毕。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgIds   组织ID集合
     * @return orgId到祖先ID列表的映射
     */
    @Override
    public Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, SysOrg> entityMap = new HashMap<>();
        Set<Long> toLoad = new HashSet<>(orgIds);

        // 循环加载所有组织及其祖先
        while (!toLoad.isEmpty()) {
            List<SysOrg> loaded = orgMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                    .and(SysOrgTableDef.SYS_ORG.ID.in(toLoad))
                    .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
            );
            toLoad.clear();
            for (SysOrg org : loaded) {
                entityMap.put(org.getId(), org);
                // 父ID不为null且不为0（根级）且未加载时，加入待加载集合
                if (org.getParentId() != null && org.getParentId() != 0L
                    && !entityMap.containsKey(org.getParentId())) {
                    toLoad.add(org.getParentId());
                }
            }
        }

        // 为每个输入orgId构建祖先链
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long orgId : orgIds) {
            List<Long> ancestors = new ArrayList<>();
            Long current = orgId;
            // 从当前组织向上遍历到根组织
            while (current != null) {
                SysOrg org = entityMap.get(current);
                if (org == null) {
                    break;
                }
                if (org.getParentId() != null && org.getParentId() != 0L) {
                    ancestors.add(org.getParentId());
                    current = org.getParentId();
                } else {
                    break;
                }
            }
            result.put(orgId, ancestors);
        }
        return result;
    }

    /**
     * 查询有效的组织
     * <p>
     * 根据组织ID查询组织，带租户隔离和删除标记过滤。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgId    组织ID
     * @return 组织实体，不存在返回null
     */
    @Override
    public SysOrg selectValidById(Long tenantId, Long orgId) {
        if (orgId == null) {
            return null;
        }
        return orgMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysOrgTableDef.SYS_ORG.ID.eq(orgId))
                .and(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 批量查询有效的组织
     * <p>
     * 根据组织ID集合批量查询组织，带租户隔离和删除标记过滤。
     * 使用单次SQL查询，避免N+1问题。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgIds   组织ID集合
     * @return 组织实体列表
     */
    @Override
    public List<SysOrg> selectValidByIds(Long tenantId, Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return List.of();
        }
        return orgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SysOrgTableDef.SYS_ORG.ID.in(orgIds))
                .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 批量软删除组织
     * <p>
     * 将指定组织标记为已删除（deleteFlag设置为当前时间戳）。
     * 使用单条批量SQL优化性能。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgIds   组织ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return;
        }
        orgMapper.softDeleteBatch(tenantId, orgIds, LocalDateTime.now());
    }

    /**
     * 删除组织及其所有子孙组织
     * <p>
     * 先查询所有子孙ID（包括自身），然后批量软删除。
     * 使用CTE递归查询确保完整的树结构删除。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgId    组织ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithChildren(Long tenantId, Long orgId) {
        SysOrg org = selectValidById(tenantId, orgId);
        if (org == null) return;

        List<Long> allIds = orgMapper.selectDescendantIdsIncludingSelf(tenantId, orgId);
        orgMapper.softDeleteBatch(tenantId, allIds, LocalDateTime.now());
    }

    /**
     * 检查组织是否有子组织
     * <p>
     * 用于删除组织前的校验，判断是否存在下级组织。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param orgId    组织ID
     * @return 是否有子组织
     */
    @Override
    public boolean hasChildren(Long tenantId, Long orgId) {
        long count = orgMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SysOrgTableDef.SYS_ORG.PARENT_ID.eq(orgId))
                .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
        );
        return count > 0;
    }

    /**
     * 根据组织编码查询组织
     * <p>
     * 用于创建组织时的编码唯一性校验。
     * 带租户隔离和删除标记过滤。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param code     组织编码
     * @return 组织实体，不存在返回null
     */
    @Override
    public SysOrg findByCode(Long tenantId, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return orgMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SysOrgTableDef.SYS_ORG.CODE.eq(code))
                .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 批量查询已存在的组织编码
     * <p>
     * 用于批量创建组织时的编码唯一性校验。
     * 返回已存在的编码集合，便于业务层判断冲突。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param codes    组织编码集合
     * @return 已存在的组织编码集合
     */
    @Override
    public Set<String> findExistingCodes(Long tenantId, Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        List<SysOrg> existingOrgs = orgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SysOrgTableDef.SYS_ORG.CODE.in(codes))
                .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
        );
        return existingOrgs.stream()
            .map(SysOrg::getCode)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());
    }

    /**
     * 批量查询组织并返回ID到实体的映射
     * <p>
     * 用于用户组织关联查询时的组织信息批量加载，避免N+1问题。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param ids      组织ID集合
     * @return 组织ID到组织实体的映射
     */
    @Override
    public Map<Long, SysOrg> batchSelectValidByIdsMap(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<SysOrg> orgs = orgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SysOrgTableDef.SYS_ORG.ID.in(ids))
                .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
        );
        return orgs.stream().collect(Collectors.toMap(SysOrg::getId, o -> o));
    }

    /**
     * 批量插入组织
     * <p>
     * 用于批量创建组织场景，使用单条批量SQL优化性能。
     * </p>
     *
     * @param orgs 组织实体列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertBatch(List<SysOrg> orgs) {
        if (orgs == null || orgs.isEmpty()) {
            return;
        }
        orgMapper.insertBatch(orgs);
    }
}