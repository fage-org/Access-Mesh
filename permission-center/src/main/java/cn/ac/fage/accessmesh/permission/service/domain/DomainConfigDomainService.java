package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

public interface DomainConfigDomainService {

    DomainConfig selectOneById(Long id);

    List<DomainConfig> selectListByQuery(QueryWrapper qw);

    DomainConfig selectOneByQuery(QueryWrapper qw);

    long selectCountByQuery(QueryWrapper qw);

    void insert(DomainConfig entity);

    int update(DomainConfig entity);

    // BizDomain operations
    BizDomain selectBizDomainById(Long id);

    List<BizDomain> selectBizDomainsByQuery(QueryWrapper qw);

    BizDomain selectBizDomainByQuery(QueryWrapper qw);

    void insertBizDomain(BizDomain entity);

    int updateBizDomain(BizDomain entity);

    // TypeDefinition operations
    TypeDefinition selectTypeDefinitionById(Long id);

    List<TypeDefinition> selectTypeDefinitionsByQuery(QueryWrapper qw);

    TypeDefinition selectTypeDefinitionByQuery(QueryWrapper qw);

    void insertTypeDefinition(TypeDefinition entity);

    int updateTypeDefinition(TypeDefinition entity);
}
