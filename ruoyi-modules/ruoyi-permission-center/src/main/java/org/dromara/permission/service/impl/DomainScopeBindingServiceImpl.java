package org.dromara.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.DomainBindingListReq;
import org.dromara.permission.domain.dto.DomainBindingSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.DomainScopeBindingVo;
import org.dromara.permission.mapper.*;
import org.dromara.permission.service.DomainScopeBindingService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomainScopeBindingServiceImpl implements DomainScopeBindingService {

    private final PcDomainScopeBindingMapper mapper;
    private final PcAbstractRoleMapper abstractRoleMapper;
    private final PcResourceEntityMapper resourceEntityMapper;
    private final PcOperationPermissionMapper operationPermissionMapper;

    @Override
    public List<DomainScopeBindingVo> list(DomainBindingListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcDomainScopeBinding> q = new LambdaQueryWrapper<PcDomainScopeBinding>()
            .eq(PcDomainScopeBinding::getTenantId, req.getTenantId())
            .eq(PcDomainScopeBinding::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getBizDomainId() != null) {
            q.eq(PcDomainScopeBinding::getBizDomainId, req.getBizDomainId());
        }
        q.orderByAsc(PcDomainScopeBinding::getId);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(DomainBindingSaveReq req) {
        if (req == null || CollUtil.isEmpty(req.getItems())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DomainBindingSaveReq.DomainBindingItem item : req.getItems()) {
            if (item.getTenantId() == null || item.getBizDomainId() == null || item.getBoundType() == null || item.getBoundEntityId() == null) {
                continue;
            }
            if (!isGlobalEntity(item.getTenantId(), item.getBoundType(), item.getBoundEntityId())) {
                throw new IllegalArgumentException("boundEntityId 须为全局实体(biz_domain_id 为 NULL): " + item.getBoundType() + " id=" + item.getBoundEntityId());
            }
            if (item.getId() != null) {
                PcDomainScopeBinding entity = mapper.selectOne(new LambdaQueryWrapper<PcDomainScopeBinding>()
                    .eq(PcDomainScopeBinding::getTenantId, item.getTenantId())
                    .eq(PcDomainScopeBinding::getId, item.getId())
                    .eq(PcDomainScopeBinding::getDeleteFlag, PermissionConstants.NOT_DELETED));
                if (entity != null) {
                    entity.setBizDomainId(item.getBizDomainId());
                    entity.setBoundType(item.getBoundType());
                    entity.setBoundEntityId(item.getBoundEntityId());
                    entity.setUpdatedAt(now);
                    mapper.updateById(entity);
                }
            } else {
                PcDomainScopeBinding entity = new PcDomainScopeBinding();
                entity.setTenantId(item.getTenantId());
                entity.setBizDomainId(item.getBizDomainId());
                entity.setBoundType(item.getBoundType());
                entity.setBoundEntityId(item.getBoundEntityId());
                entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                mapper.insert(entity);
            }
        }
    }

    private boolean isGlobalEntity(Long tenantId, String boundType, Long boundEntityId) {
        switch (boundType) {
            case "ROLE":
                PcAbstractRole role = abstractRoleMapper.selectOne(new LambdaQueryWrapper<PcAbstractRole>()
                    .eq(PcAbstractRole::getTenantId, tenantId)
                    .eq(PcAbstractRole::getId, boundEntityId)
                    .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
                return role != null && role.getBizDomainId() == null;
            case "RESOURCE":
                PcResourceEntity res = resourceEntityMapper.selectOne(new LambdaQueryWrapper<PcResourceEntity>()
                    .eq(PcResourceEntity::getTenantId, tenantId)
                    .eq(PcResourceEntity::getId, boundEntityId)
                    .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED));
                return res != null && res.getBizDomainId() == null;
            case "OPERATION":
                PcOperationPermission op = operationPermissionMapper.selectOne(new LambdaQueryWrapper<PcOperationPermission>()
                    .eq(PcOperationPermission::getTenantId, tenantId)
                    .eq(PcOperationPermission::getId, boundEntityId)
                    .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
                return op != null;
            default:
                return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.getIds()) {
            PcDomainScopeBinding entity = mapper.selectOne(new LambdaQueryWrapper<PcDomainScopeBinding>()
                .eq(PcDomainScopeBinding::getTenantId, req.getTenantId())
                .eq(PcDomainScopeBinding::getId, id)
                .eq(PcDomainScopeBinding::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())) {
                PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
                mapper.updateById(entity);
            }
        }
    }

    private DomainScopeBindingVo toVo(PcDomainScopeBinding e) {
        DomainScopeBindingVo vo = new DomainScopeBindingVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
