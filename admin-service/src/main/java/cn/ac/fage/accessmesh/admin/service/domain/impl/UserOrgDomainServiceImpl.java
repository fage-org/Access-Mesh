package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;

/**
 * 用户组织关联领域服务实现类
 * <p>
 * 封装用户与组织关联的数据访问逻辑，包括查询、删除、批量插入、主组织设置等。
 * 所有查询均带有租户隔离和删除标记过滤，确保数据安全。
 * 提供批量操作方法优化性能，避免N+1查询问题。
 * </p>
 */
@Service
public class UserOrgDomainServiceImpl implements UserOrgDomainService {

    private final SysUserOrgMapper userOrgMapper;
    private final OrgDomainService orgDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param userOrgMapper   用户组织关联数据访问层
     * @param orgDomainService 组织领域服务，用于批量查询组织信息
     */
    public UserOrgDomainServiceImpl(SysUserOrgMapper userOrgMapper, OrgDomainService orgDomainService) {
        this.userOrgMapper = userOrgMapper;
        this.orgDomainService = orgDomainService;
    }

    /**
     * 查询用户的所有组织关联
     * <p>
     * 根据用户ID查询用户与组织的所有关联记录。
     * 带租户隔离和删除标记过滤。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userId   用户ID
     * @return 用户组织关联列表
     */
    @Override
    public List<SysUserOrg> findByUserId(Long tenantId, Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 删除用户的所有组织关联
     * <p>
     * 用于重新分配用户组织时，先删除旧关联再创建新关联。
     * 直接删除记录（非软删除），因为会立即创建新关联。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userId   用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUserId(Long tenantId, Long userId) {
        if (userId == null) {
            return;
        }
        userOrgMapper.deleteByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
        );
    }

    /**
     * 删除用户与指定组织的关联
     * <p>
     * 用于移除单个用户组织关联。
     * 直接删除记录（非软删除）。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userId   用户ID
     * @param orgId    组织ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUserIdAndOrgId(Long tenantId, Long userId, Long orgId) {
        if (userId == null || orgId == null) {
            return;
        }
        userOrgMapper.deleteByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.ORG_ID.eq(orgId))
        );
    }

    /**
     * 批量插入用户组织关联
     * <p>
     * 用于批量分配用户到组织场景，使用单条批量SQL优化性能。
     * </p>
     *
     * @param userOrgs 用户组织关联列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertBatch(List<SysUserOrg> userOrgs) {
        if (userOrgs == null || userOrgs.isEmpty()) {
            return;
        }
        userOrgMapper.insertBatch(userOrgs);
    }

    /**
     * 设置用户的主组织
     * <p>
     * 将指定组织设为用户的主组织，同时将其他组织设为非主组织。
     * 使用两条批量更新SQL，确保只有一个主组织。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userId   用户ID
     * @param orgId    组织ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimaryOrg(Long tenantId, Long userId, Long orgId) {
        if (userId == null || orgId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        // 将目标组织设为主组织
        SysUserOrg updatePrimary = new SysUserOrg();
        updatePrimary.setIsPrimary(true);
        updatePrimary.setUpdatedAt(now);
        userOrgMapper.updateByQuery(updatePrimary, QueryWrapper.create()
            .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.ORG_ID.eq(orgId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        // 将其他组织设为非主组织
        SysUserOrg updateNonPrimary = new SysUserOrg();
        updateNonPrimary.setIsPrimary(false);
        updateNonPrimary.setUpdatedAt(now);
        userOrgMapper.updateByQuery(updateNonPrimary, QueryWrapper.create()
            .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.ORG_ID.ne(orgId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 获取用户关联的组织简要信息列表
     * <p>
     * 查询用户关联的所有组织，返回组织简要信息（ID、名称、类型、是否主组织）。
     * 批量查询组织信息避免N+1问题。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userId   用户ID
     * @return 组织简要信息列表
     */
    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgBriefs(Long tenantId, Long userId) {
        List<SysUserOrg> userOrgs = findByUserId(tenantId, userId);
        if (userOrgs.isEmpty()) {
            return List.of();
        }

        // 批量查询组织信息（避免N+1问题）
        Set<Long> orgIds = userOrgs.stream().map(SysUserOrg::getOrgId).collect(Collectors.toSet());
        Map<Long, SysOrg> orgMap = orgDomainService.batchSelectValidByIdsMap(tenantId, orgIds);

        return userOrgs.stream().map(uo -> {
            SysOrg org = orgMap.get(uo.getOrgId());
            String orgName = org != null ? org.getName() : null;
            String orgType = org != null ? org.getOrgType() : null;
            return new UserPageItemResp.OrgBrief(uo.getOrgId(), orgName, orgType, Boolean.TRUE.equals(uo.getIsPrimary()));
        }).collect(Collectors.toList());
    }

    /**
     * 批量查询多个用户的组织关联
     * <p>
     * 根据用户ID集合批量查询用户组织关联。
     * 使用单次SQL查询所有关联，再按用户ID分组。
     * 为每个输入用户ID初始化空列表，确保结果完整性。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userIds  用户ID集合
     * @return 用户ID到组织关联列表的映射
     */
    @Override
    public Map<Long, List<SysUserOrg>> batchFindByUserIds(Long tenantId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<SysUserOrg> allUserOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.in(userIds))
                .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        // 为每个用户ID初始化空列表
        Map<Long, List<SysUserOrg>> result = new HashMap<>();
        for (Long userId : userIds) {
            result.put(userId, new java.util.ArrayList<>());
        }
        // 按用户ID分组填充结果
        for (SysUserOrg uo : allUserOrgs) {
            result.computeIfAbsent(uo.getUserId(), k -> new java.util.ArrayList<>()).add(uo);
        }
        return result;
    }
}