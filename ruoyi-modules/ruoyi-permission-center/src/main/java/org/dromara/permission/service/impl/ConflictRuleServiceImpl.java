package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.ConflictRuleListReq;
import org.dromara.permission.domain.dto.ConflictRuleSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.ConflictRuleVo;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.mapper.*;
import org.dromara.permission.service.ConflictRuleService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConflictRuleServiceImpl implements ConflictRuleService {

    private final PcPermissionConflictRuleMapper conflictRuleMapper;
    private final PcUserRoleMapper userRoleMapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PcResourceEntityMapper resourceEntityMapper;

    @Override
    public List<ConflictRuleVo> list(ConflictRuleListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcPermissionConflictRule> q = new LambdaQueryWrapper<PcPermissionConflictRule>()
            .eq(PcPermissionConflictRule::getTenantId, req.getTenantId())
            .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getBizDomainId() != null) {
            q.and(w -> w.eq(PcPermissionConflictRule::getBizDomainId, req.getBizDomainId()).or().isNull(PcPermissionConflictRule::getBizDomainId));
        }
        q.orderByAsc(PcPermissionConflictRule::getId);
        return conflictRuleMapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ConflictRuleSaveReq req) {
        if (req == null || req.getTenantId() == null || req.getFirstOperationPermissionId() == null || req.getSecondOperationPermissionId() == null) {
            return;
        }
        long first = req.getFirstOperationPermissionId();
        long second = req.getSecondOperationPermissionId();
        if (first > second) {
            long t = first;
            first = second;
            second = t;
        }
        if (first == second) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcPermissionConflictRule entity = conflictRuleMapper.selectOne(new LambdaQueryWrapper<PcPermissionConflictRule>()
                .eq(PcPermissionConflictRule::getId, req.getId())
                .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                entity.setBizDomainId(req.getBizDomainId());
                entity.setFirstOperationPermissionId(first);
                entity.setSecondOperationPermissionId(second);
                entity.setResourceTypeValue(req.getResourceTypeValue());
                entity.setUpdatedAt(now);
                conflictRuleMapper.updateById(entity);
            }
        } else {
            PcPermissionConflictRule entity = new PcPermissionConflictRule();
            entity.setTenantId(req.getTenantId());
            entity.setBizDomainId(req.getBizDomainId());
            entity.setFirstOperationPermissionId(first);
            entity.setSecondOperationPermissionId(second);
            entity.setResourceTypeValue(req.getResourceTypeValue());
            entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            conflictRuleMapper.insert(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PcPermissionConflictRule> entities = conflictRuleMapper.selectList(new LambdaQueryWrapper<PcPermissionConflictRule>()
            .eq(PcPermissionConflictRule::getTenantId, req.getTenantId())
            .in(PcPermissionConflictRule::getId, req.getIds())
            .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcPermissionConflictRule entity : entities) {
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            conflictRuleMapper.updateById(entity);
        }
    }

    @Override
    public List<ConflictViolationVo> detect(ConflictDetectReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        List<PcPermissionConflictRule> rules = conflictRuleMapper.selectList(new LambdaQueryWrapper<PcPermissionConflictRule>()
            .eq(PcPermissionConflictRule::getTenantId, req.getTenantId())
            .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (rules.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> pairSet = new HashSet<>();
        for (PcPermissionConflictRule r : rules) {
            pairSet.add(r.getFirstOperationPermissionId() + "_" + r.getSecondOperationPermissionId());
        }
        List<ConflictViolationVo> violations = new ArrayList<>();
        if (req.getAbstractUserId() != null) {
            List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
                .eq(PcUserRole::getTenantId, req.getTenantId())
                .eq(PcUserRole::getAbstractUserId, req.getAbstractUserId())
                .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED))
                .stream().map(PcUserRole::getAbstractRoleId).distinct().collect(Collectors.toList());
            if (!roleIds.isEmpty()) {
                List<PcRoleResourcePermission> rrps = roleResourcePermissionMapper.selectList(new LambdaQueryWrapper<PcRoleResourcePermission>()
                    .eq(PcRoleResourcePermission::getTenantId, req.getTenantId())
                    .in(PcRoleResourcePermission::getAbstractRoleId, roleIds)
                    .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
                Map<Long, Set<Long>> resourceToOps = new HashMap<>();
                for (PcRoleResourcePermission rrp : rrps) {
                    if (req.getResourceEntityId() != null && !req.getResourceEntityId().equals(rrp.getResourceEntityId())) {
                        continue;
                    }
                    resourceToOps.computeIfAbsent(rrp.getResourceEntityId(), k -> new HashSet<>()).add(rrp.getOperationPermissionId());
                }
                for (Map.Entry<Long, Set<Long>> e : resourceToOps.entrySet()) {
                    List<Long> ops = new ArrayList<>(e.getValue());
                    for (int i = 0; i < ops.size(); i++) {
                        for (int j = i + 1; j < ops.size(); j++) {
                            long a = ops.get(i);
                            long b = ops.get(j);
                            if (a > b) { long t = a; a = b; b = t; }
                            if (pairSet.contains(a + "_" + b)) {
                                ConflictViolationVo v = new ConflictViolationVo();
                                v.setAbstractUserId(req.getAbstractUserId());
                                v.setResourceEntityId(e.getKey());
                                v.setOperationPermissionId(a);
                                v.setDescription("用户同时拥有互斥操作 " + a + " 与 " + b);
                                violations.add(v);
                            }
                        }
                    }
                }
            }
        }
        if (req.getAbstractRoleId() != null) {
            List<PcRoleResourcePermission> rrps = roleResourcePermissionMapper.selectList(new LambdaQueryWrapper<PcRoleResourcePermission>()
                .eq(PcRoleResourcePermission::getTenantId, req.getTenantId())
                .eq(PcRoleResourcePermission::getAbstractRoleId, req.getAbstractRoleId())
                .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
            Map<Long, Set<Long>> resourceToOps = new HashMap<>();
            for (PcRoleResourcePermission rrp : rrps) {
                if (req.getResourceEntityId() != null && !req.getResourceEntityId().equals(rrp.getResourceEntityId())) {
                    continue;
                }
                resourceToOps.computeIfAbsent(rrp.getResourceEntityId(), k -> new HashSet<>()).add(rrp.getOperationPermissionId());
            }
            for (Map.Entry<Long, Set<Long>> e : resourceToOps.entrySet()) {
                List<Long> ops = new ArrayList<>(e.getValue());
                for (int i = 0; i < ops.size(); i++) {
                    for (int j = i + 1; j < ops.size(); j++) {
                        long a = ops.get(i);
                        long b = ops.get(j);
                        if (a > b) { long t = a; a = b; b = t; }
                        if (pairSet.contains(a + "_" + b)) {
                            ConflictViolationVo v = new ConflictViolationVo();
                            v.setAbstractRoleId(req.getAbstractRoleId());
                            v.setResourceEntityId(e.getKey());
                            v.setOperationPermissionId(a);
                            v.setDescription("角色同时拥有互斥操作 " + a + " 与 " + b);
                            violations.add(v);
                        }
                    }
                }
            }
        }
        return violations;
    }

    private ConflictRuleVo toVo(PcPermissionConflictRule e) {
        ConflictRuleVo vo = new ConflictRuleVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
