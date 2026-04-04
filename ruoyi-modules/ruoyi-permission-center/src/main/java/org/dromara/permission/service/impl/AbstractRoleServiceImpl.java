package org.dromara.permission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.RoleListReq;
import org.dromara.permission.domain.dto.RoleSaveReq;
import org.dromara.permission.domain.vo.AbstractRoleVo;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.service.AbstractRoleService;
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
public class AbstractRoleServiceImpl implements AbstractRoleService {

    private final PcAbstractRoleMapper mapper;
    private final PcUserRoleMapper userRoleMapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final TypeDefinitionReader typeDefinitionReader;
    private final PermissionTreePathManager treePathManager;

    @Override
    public List<AbstractRoleVo> list(RoleListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcAbstractRole> q = new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, req.getTenantId())
            .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getBizDomainId() != null) {
            q.eq(PcAbstractRole::getBizDomainId, req.getBizDomainId());
        }
        if (req.getParentId() != null) {
            q.eq(PcAbstractRole::getParentId, req.getParentId());
            q.orderByAsc(PcAbstractRole::getSortOrder).orderByAsc(PcAbstractRole::getId);
            List<PcAbstractRole> list = mapper.selectList(q);
            return list.stream().map(this::toVo).collect(Collectors.toList());
        }
        q.orderByAsc(PcAbstractRole::getSortOrder).orderByAsc(PcAbstractRole::getId);
        List<PcAbstractRole> list = mapper.selectList(q);
        List<AbstractRoleVo> voList = list.stream().map(this::toVo).collect(Collectors.toList());
        return buildTree(voList, null);
    }

    private List<AbstractRoleVo> buildTree(List<AbstractRoleVo> all, Long parentId) {
        List<AbstractRoleVo> children = new ArrayList<>();
        for (AbstractRoleVo vo : all) {
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
    public void save(RoleSaveReq req) {
        if (req == null || req.getTenantId() == null) {
            return;
        }
        typeDefinitionReader.assertTypeValueExists(req.getTenantId(), req.getBizDomainId(), "role_type", req.getRoleType(), "无效的角色类型");
        LocalDateTime now = LocalDateTime.now();
        String parentPath = null;
        if (req.getParentId() != null && req.getParentId() != 0) {
            PcAbstractRole parent = mapper.selectOne(new LambdaQueryWrapper<PcAbstractRole>()
                .eq(PcAbstractRole::getId, req.getParentId())
                .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (parent != null) {
                parentPath = parent.getPath();
            }
        }
        if (req.getId() != null) {
            PcAbstractRole entity = mapper.selectOne(new LambdaQueryWrapper<PcAbstractRole>()
                .eq(PcAbstractRole::getId, req.getId())
                .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                String oldPath = entity.getPath();
                entity.setBizDomainId(req.getBizDomainId());
                entity.setRoleType(req.getRoleType());
                entity.setParentId(req.getParentId() != null && req.getParentId() != 0 ? req.getParentId() : null);
                entity.setExternalId(req.getExternalId());
                entity.setName(req.getName());
                entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
                entity.setExtra(StrUtil.isNotBlank(req.getExtra()) ? req.getExtra() : "{}");
                entity.setUpdatedAt(now);
                entity.setPath(treePathManager.buildPath(parentPath, entity.getId()));
                mapper.updateById(entity);
                treePathManager.refreshRoleSubtreePaths(req.getTenantId(), oldPath, entity.getPath());
            }
        } else {
            PcAbstractRole entity = new PcAbstractRole();
            entity.setTenantId(req.getTenantId());
            entity.setBizDomainId(req.getBizDomainId());
            entity.setRoleType(req.getRoleType());
            entity.setParentId(req.getParentId() != null && req.getParentId() != 0 ? req.getParentId() : null);
            entity.setExternalId(req.getExternalId());
            entity.setName(req.getName());
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
        List<PcAbstractRole> entities = mapper.selectList(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, req.getTenantId())
            .in(PcAbstractRole::getId, req.getIds())
            .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
        List<Long> roleIds = new ArrayList<>();
        for (PcAbstractRole entity : entities) {
            treePathManager.assertRoleHasNoChildren(req.getTenantId(), entity.getId());
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            mapper.updateById(entity);
            roleIds.add(entity.getId());
        }
        if (!roleIds.isEmpty()) {
            cascadeDeleteByRoleIds(req.getTenantId(), roleIds, now);
        }
    }

    private void cascadeDeleteByRoleIds(Long tenantId, List<Long> roleIds, LocalDateTime now) {
        List<PcUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
            .eq(PcUserRole::getTenantId, tenantId)
            .in(PcUserRole::getAbstractRoleId, roleIds)
            .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcUserRole ur : userRoles) {
            PermissionAuditSupport.markDeleted(ur, ur.getId(), now);
            userRoleMapper.updateById(ur);
        }
        List<PcRoleResourcePermission> rrps = roleResourcePermissionMapper.selectList(new LambdaQueryWrapper<PcRoleResourcePermission>()
            .eq(PcRoleResourcePermission::getTenantId, tenantId)
            .in(PcRoleResourcePermission::getAbstractRoleId, roleIds)
            .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcRoleResourcePermission rrp : rrps) {
            PermissionAuditSupport.markDeleted(rrp, rrp.getId(), now);
            roleResourcePermissionMapper.updateById(rrp);
        }
    }

    private AbstractRoleVo toVo(PcAbstractRole e) {
        AbstractRoleVo vo = new AbstractRoleVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
