package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTreeConfigTableDef;

@Service
public class OrgTreeConfigDomainServiceImpl implements OrgTreeConfigDomainService {

    private final SysOrgTreeConfigMapper orgTreeConfigMapper;

    public OrgTreeConfigDomainServiceImpl(SysOrgTreeConfigMapper orgTreeConfigMapper) {
        this.orgTreeConfigMapper = orgTreeConfigMapper;
    }

    @Override
    public List<SysOrgTreeConfig> findDefaultConfigs(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return orgTreeConfigMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG.eq(0))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.IS_DEFAULT.eq(true))
        );
    }

    @Override
    public List<SysOrgTreeConfig> findAllByTenantId(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return orgTreeConfigMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public SysOrgTreeConfig selectValidById(Long tenantId, Long id) {
        if (id == null) {
            return null;
        }
        return orgTreeConfigMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.ID.eq(id))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG.eq(0))
        );
    }
}