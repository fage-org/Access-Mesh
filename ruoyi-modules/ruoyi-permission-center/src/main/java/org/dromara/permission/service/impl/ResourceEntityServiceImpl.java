package org.dromara.permission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.ResourceListReq;
import org.dromara.permission.domain.dto.ResourceSaveReq;
import org.dromara.permission.domain.vo.ResourceEntityVo;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.service.ResourceEntityService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.dromara.permission.service.support.PermissionTreePathManager;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceEntityServiceImpl implements ResourceEntityService {

    private final PcResourceEntityMapper mapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final TypeDefinitionReader typeDefinitionReader;
    private final PermissionTreePathManager treePathManager;

    @Override
    public List<ResourceEntityVo> list(ResourceListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcResourceEntity> q = new LambdaQueryWrapper<PcResourceEntity>()
            .eq(PcResourceEntity::getTenantId, req.getTenantId())
            .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getBizDomainId() != null) {
            q.eq(PcResourceEntity::getBizDomainId, req.getBizDomainId());
        }
        if (req.getResourceType() != null) {
            q.eq(PcResourceEntity::getResourceType, req.getResourceType());
        }
        if (req.getParentId() != null) {
            q.eq(PcResourceEntity::getParentId, req.getParentId());
            q.orderByAsc(PcResourceEntity::getSortOrder).orderByAsc(PcResourceEntity::getId);
            List<PcResourceEntity> list = mapper.selectList(q);
            return list.stream().map(this::toVo).collect(Collectors.toList());
        }
        q.orderByAsc(PcResourceEntity::getSortOrder).orderByAsc(PcResourceEntity::getId);
        List<PcResourceEntity> list = mapper.selectList(q);
        List<ResourceEntityVo> voList = list.stream().map(this::toVo).collect(Collectors.toList());
        return buildTree(voList, null);
    }

    private List<ResourceEntityVo> buildTree(List<ResourceEntityVo> all, Long parentId) {
        List<ResourceEntityVo> children = new ArrayList<>();
        for (ResourceEntityVo vo : all) {
            Long pid = vo.getParentId();
            if ((parentId == null && (pid == null || Long.valueOf(0L).equals(pid))) || (parentId != null && parentId.equals(pid))) {
                vo.setChildren(buildTree(all, vo.getId()));
                children.add(vo);
            }
        }
        return children;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ResourceSaveReq req) {
        if (req == null || req.getTenantId() == null) {
            return;
        }
        typeDefinitionReader.assertTypeValueExists(req.getTenantId(), req.getBizDomainId(), "resource_type", req.getResourceType(), "无效的资源类型");
        LocalDateTime now = LocalDateTime.now();
        String parentPath = null;
        if (req.getParentId() != null && req.getParentId() != 0) {
            PcResourceEntity parent = mapper.selectOne(new LambdaQueryWrapper<PcResourceEntity>()
                .eq(PcResourceEntity::getId, req.getParentId())
                .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (parent != null) {
                parentPath = parent.getPath();
            }
        }
        if (req.getId() != null) {
            PcResourceEntity entity = mapper.selectOne(new LambdaQueryWrapper<PcResourceEntity>()
                .eq(PcResourceEntity::getId, req.getId())
                .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                String oldPath = entity.getPath();
                entity.setBizDomainId(req.getBizDomainId());
                entity.setParentId(req.getParentId() != null && req.getParentId() != 0 ? req.getParentId() : null);
                entity.setCode(req.getCode());
                entity.setName(req.getName());
                entity.setResourceType(req.getResourceType());
                entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
                entity.setExtra(StrUtil.isNotBlank(req.getExtra()) ? req.getExtra() : "{}");
                entity.setUpdatedAt(now);
                entity.setPath(treePathManager.buildPath(parentPath, entity.getId()));
                mapper.updateById(entity);
                treePathManager.refreshResourceSubtreePaths(req.getTenantId(), oldPath, entity.getPath());
            }
        } else {
            PcResourceEntity entity = new PcResourceEntity();
            entity.setTenantId(req.getTenantId());
            entity.setBizDomainId(req.getBizDomainId());
            entity.setParentId(req.getParentId() != null && req.getParentId() != 0 ? req.getParentId() : null);
            entity.setCode(req.getCode());
            entity.setName(req.getName());
            entity.setResourceType(req.getResourceType());
            entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
            entity.setExtra(StrUtil.isNotBlank(req.getExtra()) ? req.getExtra() : "{}");
            entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insert(entity);
            entity.setPath(treePathManager.buildPath(parentPath, entity.getId()));
            mapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PcResourceEntity> entities = mapper.selectList(new LambdaQueryWrapper<PcResourceEntity>()
            .eq(PcResourceEntity::getTenantId, req.getTenantId())
            .in(PcResourceEntity::getId, req.getIds())
            .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED));
        List<Long> resourceIds = new ArrayList<>();
        for (PcResourceEntity entity : entities) {
            treePathManager.assertResourceHasNoChildren(req.getTenantId(), entity.getId());
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            mapper.updateById(entity);
            resourceIds.add(entity.getId());
        }
        if (!resourceIds.isEmpty()) {
            cascadeDeleteByResourceIds(req.getTenantId(), resourceIds, now);
        }
    }

    private void cascadeDeleteByResourceIds(Long tenantId, List<Long> resourceIds, LocalDateTime now) {
        List<PcRoleResourcePermission> rrps = roleResourcePermissionMapper.selectList(new LambdaQueryWrapper<PcRoleResourcePermission>()
            .eq(PcRoleResourcePermission::getTenantId, tenantId)
            .in(PcRoleResourcePermission::getResourceEntityId, resourceIds)
            .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcRoleResourcePermission rrp : rrps) {
            PermissionAuditSupport.markDeleted(rrp, rrp.getId(), now);
            roleResourcePermissionMapper.updateById(rrp);
        }
    }

    private ResourceEntityVo toVo(PcResourceEntity e) {
        ResourceEntityVo vo = new ResourceEntityVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
