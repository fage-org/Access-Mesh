package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.model.permission.ResolvedRole;
import org.dromara.permission.service.RoleResolverService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleResolverServiceImpl implements RoleResolverService {

    private final PcUserRoleMapper userRoleMapper;
    private final PcAbstractRoleMapper abstractRoleMapper;

    @Override
    public List<ResolvedRole> resolve(Long tenantId, Long abstractUserId, Long bizDomainId) {
        LocalDateTime now = LocalDateTime.now();
        List<PcUserRole> userRoles = userRoleMapper.selectEffectiveByTenantAndUser(tenantId, abstractUserId, now);
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, PcUserRole> relationMap = userRoles.stream()
            .collect(Collectors.toMap(PcUserRole::getAbstractRoleId, Function.identity(), (a, b) -> a));
        List<PcAbstractRole> roles = abstractRoleMapper.selectList(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, tenantId)
            .in(PcAbstractRole::getId, relationMap.keySet())
            .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
        return roles.stream()
            .filter(role -> bizDomainId == null || role.getBizDomainId() == null || bizDomainId.equals(role.getBizDomainId()))
            .map(role -> {
                PcUserRole relation = relationMap.get(role.getId());
                ResolvedRole resolved = new ResolvedRole();
                resolved.setRoleId(role.getId());
                resolved.setBizDomainId(role.getBizDomainId());
                resolved.setRoleType(role.getRoleType());
                if (relation != null) {
                    resolved.setValidFrom(relation.getValidFrom());
                    resolved.setValidTo(relation.getValidTo());
                }
                return resolved;
            })
            .collect(Collectors.toList());
    }
}
