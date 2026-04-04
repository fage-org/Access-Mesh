package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.ResourceDependencyListReq;
import org.dromara.permission.domain.dto.ResourceDependencySaveReq;
import org.dromara.permission.domain.vo.ResourceDependencyVo;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.service.ResourceDependencyService;
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
public class ResourceDependencyServiceImpl implements ResourceDependencyService {

    private final PcResourceDependencyMapper mapper;

    @Override
    public List<ResourceDependencyVo> list(ResourceDependencyListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcResourceDependency> q = new LambdaQueryWrapper<PcResourceDependency>()
            .eq(PcResourceDependency::getTenantId, req.getTenantId())
            .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getResourceEntityId() != null) {
            q.eq(PcResourceDependency::getResourceEntityId, req.getResourceEntityId());
        }
        q.orderByAsc(PcResourceDependency::getId);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ResourceDependencySaveReq req) {
        if (req == null || req.getTenantId() == null || req.getResourceEntityId() == null || req.getDependsOnResourceEntityId() == null || req.getRequiredOperationPermissionId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcResourceDependency entity = mapper.selectOne(new LambdaQueryWrapper<PcResourceDependency>()
                .eq(PcResourceDependency::getTenantId, req.getTenantId())
                .eq(PcResourceDependency::getId, req.getId())
                .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                entity.setResourceEntityId(req.getResourceEntityId());
                entity.setDependsOnResourceEntityId(req.getDependsOnResourceEntityId());
                entity.setSourceOperationPermissionId(req.getSourceOperationPermissionId());
                entity.setRequiredOperationPermissionId(req.getRequiredOperationPermissionId());
                entity.setUpdatedAt(now);
                mapper.updateById(entity);
            }
        } else {
            PcResourceDependency entity = new PcResourceDependency();
            entity.setTenantId(req.getTenantId());
            entity.setResourceEntityId(req.getResourceEntityId());
            entity.setDependsOnResourceEntityId(req.getDependsOnResourceEntityId());
            entity.setSourceOperationPermissionId(req.getSourceOperationPermissionId());
            entity.setRequiredOperationPermissionId(req.getRequiredOperationPermissionId());
            entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insert(entity);
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
            PcResourceDependency entity = mapper.selectOne(new LambdaQueryWrapper<PcResourceDependency>()
                .eq(PcResourceDependency::getTenantId, req.getTenantId())
                .eq(PcResourceDependency::getId, id)
                .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())) {
                PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
                mapper.updateById(entity);
            }
        }
    }

    private ResourceDependencyVo toVo(PcResourceDependency e) {
        ResourceDependencyVo vo = new ResourceDependencyVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
