package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTreeConfigTableDef;

/**
 * 组织树配置领域服务实现类
 * <p>
 * 封装组织树配置的数据访问逻辑，提供默认配置查询、全部配置查询、按主键查询等方法。
 * 组织树配置用于定义组织树的展示规则，如过滤条件、排序方式、单组织关联限制等。
 * 所有查询均带有租户隔离和删除标记过滤，确保数据安全。
 * </p>
 */
@Service
public class OrgTreeConfigDomainServiceImpl implements OrgTreeConfigDomainService {

    private final SysOrgTreeConfigMapper orgTreeConfigMapper;

    /**
     * 构造函数注入依赖
     *
     * @param orgTreeConfigMapper 组织树配置数据访问层
     */
    public OrgTreeConfigDomainServiceImpl(SysOrgTreeConfigMapper orgTreeConfigMapper) {
        this.orgTreeConfigMapper = orgTreeConfigMapper;
    }

    /**
     * 查询租户的默认组织树配置
     * <p>
     * 获取指定租户下标记为默认的组织树配置列表。
     * 用于用户组织关联时的单组织限制校验。
     * 系统通常只有一个默认配置，但返回列表以兼容多默认场景。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @return 默认组织树配置列表
     */
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

    /**
     * 查询租户的所有组织树配置
     * <p>
     * 获取指定租户下所有的组织树配置方案。
     * 用于组织树配置管理页面的列表展示。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @return 组织树配置列表
     */
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

    /**
     * 查询有效的组织树配置
     * <p>
     * 根据主键ID查询配置，带租户隔离和删除标记过滤。
     * 用于配置更新、设置默认等操作前的校验。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param id       配置主键ID
     * @return 组织树配置实体，不存在返回null
     */
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