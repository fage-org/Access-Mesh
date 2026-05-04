package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTreeConfigTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef.SYS_ORG;
import static cn.ac.fage.accessmesh.admin.entity.table.SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG;
import static cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;

@Service
public class UserOrgServiceImpl implements UserOrgService {

    private final SysUserOrgMapper userOrgMapper;
    private final SysOrgMapper orgMapper;
    private final SysOrgTreeConfigMapper orgTreeConfigMapper;

    public UserOrgServiceImpl(SysUserOrgMapper userOrgMapper, SysOrgMapper orgMapper,
                              SysOrgTreeConfigMapper orgTreeConfigMapper) {
        this.userOrgMapper = userOrgMapper;
        this.orgMapper = orgMapper;
        this.orgTreeConfigMapper = orgTreeConfigMapper;
    }

    @Override
    @Transactional
    public void assignUserToOrgs(UserOrgAssignReq req) {
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_ORG_TREE_CONFIG.DELETE_FLAG.eq(0))
                .and(SYS_ORG_TREE_CONFIG.IS_DEFAULT.eq(true))
        );
        for (SysOrgTreeConfig config : defaultConfigs) {
            if (Boolean.TRUE.equals(config.getSingleAssoc()) && req.orgIds().size() > 1) {
                throw new BizException(AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getCode(),
                    AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getMessage());
            }
        }

        userOrgMapper.deleteByQuery(
            QueryWrapper.create().where(SYS_USER_ORG.USER_ID.eq(req.userId()))
        );

        LocalDateTime now = LocalDateTime.now();
        for (Long orgId : req.orgIds()) {
            SysUserOrg assoc = new SysUserOrg();
            assoc.setUserId(req.userId());
            assoc.setOrgId(orgId);
            assoc.setIsPrimary(orgId.equals(req.primaryOrgId()));
            assoc.setCreatedAt(now);
            assoc.setUpdatedAt(now);
            assoc.setDeleteFlag(0L);
            userOrgMapper.insert(assoc);
        }
    }

    @Override
    @Transactional
    public void removeUserFromOrg(Long userId, Long orgId) {
        userOrgMapper.deleteByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.ORG_ID.eq(orgId))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimaryOrg(Long userId, Long orgId) {
        LocalDateTime now = LocalDateTime.now();

        // 查询所有用户组织关联
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        // 批量更新：先全部设为非主组织
        for (SysUserOrg uo : userOrgs) {
            SysUserOrg update = new SysUserOrg();
            update.setId(uo.getId());
            update.setIsPrimary(uo.getOrgId().equals(orgId));
            update.setUpdatedAt(now);
            userOrgMapper.update(update);
        }
    }

    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId) {
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        if (userOrgs.isEmpty()) {
            return List.of();
        }

        // 批量查询组织信息（避免N+1问题）
        Set<Long> orgIds = userOrgs.stream().map(SysUserOrg::getOrgId).collect(Collectors.toSet());
        List<SysOrg> orgs = orgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_ORG.ID.in(orgIds))
                .and(SYS_ORG.DELETE_FLAG.eq(0))
        );
        Map<Long, SysOrg> orgMap = orgs.stream()
            .collect(Collectors.toMap(SysOrg::getId, o -> o));

        return userOrgs.stream().map(uo -> {
            SysOrg org = orgMap.get(uo.getOrgId());
            String orgName = org != null ? org.getName() : null;
            String orgType = org != null ? org.getOrgType() : null;
            return new UserPageItemResp.OrgBrief(uo.getOrgId(), orgName, orgType, Boolean.TRUE.equals(uo.getIsPrimary()));
        }).collect(Collectors.toList());
    }
}
