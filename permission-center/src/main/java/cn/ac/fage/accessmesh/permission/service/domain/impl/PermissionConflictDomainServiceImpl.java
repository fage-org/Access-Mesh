package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.permission.enums.ConflictType;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE;

@Service
public class PermissionConflictDomainServiceImpl implements PermissionConflictDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionConflictDomainServiceImpl.class);
    private static final String ROLE_MUTEX_KEY = "perm:conflict-rule:role-mutex:";
    private static final long CACHE_TTL_MINUTES = 5;

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OperationLogDomainService operationLogDomainService;

    public PermissionConflictDomainServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                                RedisTemplate<String, Object> redisTemplate,
                                                OperationLogDomainService operationLogDomainService) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.redisTemplate = redisTemplate;
        this.operationLogDomainService = operationLogDomainService;
    }

    @Override
    public Set<Long> filterRoleMutex(Long tenantId, Set<Long> effectiveRoleIds) {
        // Get cached mutex rules
        String cacheKey = ROLE_MUTEX_KEY + tenantId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        List<RoleMutexPair> mutexPairs;
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<RoleMutexPair> list = (List<RoleMutexPair>) cached;
            mutexPairs = list;
        } else {
            List<PermissionConflictRule> rules = conflictRuleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                    .and(PERMISSION_CONFLICT_RULE.CONFLICT_TYPE.eq(ConflictType.ROLE_MUTEX.getValue()))
                    .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
            );
            mutexPairs = rules.stream()
                .map(r -> new RoleMutexPair(r.getFirstAbstractRoleId(), r.getSecondAbstractRoleId()))
                .collect(Collectors.toList());
            redisTemplate.opsForValue().set(cacheKey, mutexPairs, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }

        Set<Long> result = new HashSet<>(effectiveRoleIds);
        for (RoleMutexPair pair : mutexPairs) {
            if (result.contains(pair.first) && result.contains(pair.second)) {
                result.remove(pair.first);
                result.remove(pair.second);
            }
        }
        return result;
    }

    @Override
    public List<RolePermSnapshot.RolePermEntry> filterPermMutex(Long tenantId, List<RolePermSnapshot.RolePermEntry> passedEntries) {
        List<PermissionConflictRule> rules = conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONFLICT_RULE.CONFLICT_TYPE.eq(ConflictType.PERM_MUTEX.getValue()))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );

        Set<Long> opIds = passedEntries.stream()
            .map(RolePermSnapshot.RolePermEntry::operationPermissionId)
            .collect(Collectors.toSet());

        Set<Long> conflictingOps = new HashSet<>();
        for (PermissionConflictRule rule : rules) {
            if (rule.getFirstOperationPermissionId() != null && rule.getSecondOperationPermissionId() != null) {
                if (opIds.contains(rule.getFirstOperationPermissionId()) && opIds.contains(rule.getSecondOperationPermissionId())) {
                    conflictingOps.add(rule.getFirstOperationPermissionId());
                    conflictingOps.add(rule.getSecondOperationPermissionId());
                }
            }
        }

        // Trigger async notification for perm conflicts
        if (!conflictingOps.isEmpty()) {
            notifyPermConflict(tenantId, conflictingOps, rules);
        }
        return passedEntries.stream()
            .filter(e -> !conflictingOps.contains(e.operationPermissionId()))
            .collect(Collectors.toList());
    }

    private record RoleMutexPair(Long first, Long second) {}

    @Async
    void notifyPermConflict(Long tenantId, Set<Long> conflictingOpIds, List<PermissionConflictRule> triggeredRules) {
        try {
            String detail = triggeredRules.stream()
                .filter(r -> conflictingOpIds.contains(r.getFirstOperationPermissionId())
                          || conflictingOpIds.contains(r.getSecondOperationPermissionId()))
                .map(r -> String.format("rule[%d]: op%d vs op%d", r.getId(),
                    r.getFirstOperationPermissionId(), r.getSecondOperationPermissionId()))
                .collect(Collectors.joining("; "));
            log.warn("Permission conflict detected: tenantId={}, conflictingOps={}, rules={}",
                tenantId, conflictingOpIds, detail);
            operationLogDomainService.asyncRecord(
                "PERMISSION", "CONFLICT_DETECTED", "PERMISSION", null,
                String.format("Perm conflict blocked: tenantId=%d, ops=%s", tenantId, conflictingOpIds),
                null, null, null, tenantId
            );
        } catch (Exception e) {
            log.error("Failed to record permission conflict notification: tenantId={}", tenantId, e);
        }
    }
}
