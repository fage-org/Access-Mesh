package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import org.springframework.stereotype.Service;

import java.util.List;

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
        return orgTreeConfigMapper.selectDefaultConfigs(tenantId);
    }

    }
