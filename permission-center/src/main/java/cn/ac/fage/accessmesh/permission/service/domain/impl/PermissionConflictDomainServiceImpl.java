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
import cn.ac.fage.accessmesh.permission.entity.table.PermissionConflictRuleTableDef;

/**
 * 权限冲突领域服务实现类
 * <p>
 * 提供权限冲突检测和处理功能。
 * 支持两种冲突类型：
 * - ROLE_MUTEX（角色互斥）：两个角色不能同时拥有，发生冲突时同时移除
 * - PERM_MUTEX（权限互斥）：两个操作权限不能同时授予，发生冲突时同时移除
 * 角色互斥规则使用Redis缓存提高查询性能。
 * 检测到权限冲突时，异步记录操作日志并发出通知。
 * </p>
 */
@Service
public class PermissionConflictDomainServiceImpl implements PermissionConflictDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionConflictDomainServiceImpl.class);
    private static final String ROLE_MUTEX_KEY = "perm:conflict-rule:role-mutex:";
    private static final long CACHE_TTL_MINUTES = 5;

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OperationLogDomainService operationLogDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param conflictRuleMapper        权限冲突规则数据访问层
     * @param redisTemplate             Redis模板，用于缓存角色互斥规则
     * @param operationLogDomainService 操作日志领域服务，用于记录冲突通知
     */
    public PermissionConflictDomainServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                                RedisTemplate<String, Object> redisTemplate,
                                                OperationLogDomainService operationLogDomainService) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.redisTemplate = redisTemplate;
        this.operationLogDomainService = operationLogDomainService;
    }

    /**
     * 过滤角色互斥冲突
     * <p>
     * 根据角色互斥规则过滤有效角色集合。
     * 如果用户同时拥有互斥的两个角色，则同时移除这两个角色。
     * 角色互斥规则从Redis缓存加载，缓存不存在时从数据库查询并缓存。
     * TODO: Redis操作竞态条件风险，建议使用分布式锁或singleflight模式合并并发请求。
     * </p>
     *
     * @param tenantId        租户ID
     * @param effectiveRoleIds 有效角色ID集合
     * @return 过滤后的有效角色ID集合（移除互斥角色）
     */
    @Override
    public Set<Long> filterRoleMutex(Long tenantId, Set<Long> effectiveRoleIds) {
        // TODO: Redis 操作竞态条件风险
        // 问题：当前 get + set 操作不具备原子性，并发请求可能导致缓存穿透
        // 建议：使用分布式锁或 singleflight 模式合并并发请求
        // 优先级：P2（性能优化，可关注但不强制整改）
        // 从缓存获取角色互斥规则
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
                    .where(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                    .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.CONFLICT_TYPE.eq(ConflictType.ROLE_MUTEX.getValue()))
                    .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
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

    /**
     * 过滤权限互斥冲突
     * <p>
     * 根据权限互斥规则过滤权限条目列表。
     * 如果用户同时拥有互斥的两个操作权限，则同时移除这两个权限。
     * 检测到权限冲突时，异步发出通知并记录操作日志。
     * </p>
     *
     * @param tenantId     租户ID
     * @param passedEntries 通过初步检查的权限条目列表
     * @return 过滤后的权限条目列表（移除互斥权限）
     */
    @Override
    public List<RolePermSnapshot.RolePermEntry> filterPermMutex(Long tenantId, List<RolePermSnapshot.RolePermEntry> passedEntries) {
        List<PermissionConflictRule> rules = conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.CONFLICT_TYPE.eq(ConflictType.PERM_MUTEX.getValue()))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
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

        // 检测到权限冲突时触发异步通知
        if (!conflictingOps.isEmpty()) {
            notifyPermConflict(tenantId, conflictingOps, rules);
        }
        return passedEntries.stream()
            .filter(e -> !conflictingOps.contains(e.operationPermissionId()))
            .collect(Collectors.toList());
    }

    /**
     * 角色互斥对内部记录类
     * <p>
     * 用于存储互斥的两个角色ID。
     * </p>
     */
    private record RoleMutexPair(Long first, Long second) {}

    /**
     * 异步通知权限冲突
     * <p>
     * 检测到权限互斥冲突时，异步记录操作日志。
     * 记录冲突的租户ID、冲突的操作权限ID集合、触发的冲突规则详情。
     * 使用@Async注解异步执行，不阻塞主流程。
     * </p>
     *
     * @param tenantId         租户ID
     * @param conflictingOpIds 冲突的操作权限ID集合
     * @param triggeredRules   触发的冲突规则列表
     */
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