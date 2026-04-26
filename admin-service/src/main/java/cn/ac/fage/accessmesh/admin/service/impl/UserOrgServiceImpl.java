package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
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
import java.util.stream.Collectors;

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
    @Transactional
    public void setPrimaryOrg(Long userId, Long orgId) {
        List<SysUserOrg> all = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        );
        for (SysUserOrg uo : all) {
            uo.setIsPrimary(uo.getOrgId().equals(orgId));
            uo.setUpdatedAt(LocalDateTime.now());
            userOrgMapper.update(uo);
        }
    }

    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId) {
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        );
        return userOrgs.stream().map(uo -> {
            SysOrg org = orgMapper.selectOneById(uo.getOrgId());
            String orgName = org != null ? org.getName() : null;
            String orgType = org != null ? org.getOrgType() : null;
            return new UserPageItemResp.OrgBrief(uo.getOrgId(), orgName, orgType, Boolean.TRUE.equals(uo.getIsPrimary()));
        }).collect(Collectors.toList());
    }
}
