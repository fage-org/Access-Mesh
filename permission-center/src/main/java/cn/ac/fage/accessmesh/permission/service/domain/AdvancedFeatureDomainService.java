package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

public interface AdvancedFeatureDomainService {

    // PermissionCondition operations
    PermissionCondition selectConditionById(Long id);

    List<PermissionCondition> selectConditionsByQuery(QueryWrapper qw);

    PermissionCondition selectConditionByQuery(QueryWrapper qw);

    long countConditionsByQuery(QueryWrapper qw);

    void insertCondition(PermissionCondition entity);

    int updateCondition(PermissionCondition entity);

    int deleteCondition(Long id);

    int deleteConditionsByIds(List<Long> ids);

    // PermissionConflictRule operations
    PermissionConflictRule selectConflictRuleById(Long id);

    List<PermissionConflictRule> selectConflictRulesByQuery(QueryWrapper qw);

    PermissionConflictRule selectConflictRuleByQuery(QueryWrapper qw);

    long countConflictRulesByQuery(QueryWrapper qw);

    void insertConflictRule(PermissionConflictRule entity);

    int updateConflictRule(PermissionConflictRule entity);

    int deleteConflictRule(Long id);

    int deleteConflictRulesByIds(List<Long> ids);
}
