package org.dromara.permission.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PermissionTreePathManager {

    private final PcAbstractRoleMapper abstractRoleMapper;
    private final PcResourceEntityMapper resourceEntityMapper;

    public String buildPath(String parentPath, Long id) {
        return parentPath == null || parentPath.isBlank() ? "/" + id : parentPath + "/" + id;
    }

    public void assertRoleHasNoChildren(Long tenantId, Long roleId) {
        Long count = abstractRoleMapper.selectCount(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, tenantId)
            .eq(PcAbstractRole::getParentId, roleId)
            .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new IllegalStateException("存在未删除的子角色，禁止删除");
        }
    }

    public void assertResourceHasNoChildren(Long tenantId, Long resourceId) {
        Long count = resourceEntityMapper.selectCount(new LambdaQueryWrapper<PcResourceEntity>()
            .eq(PcResourceEntity::getTenantId, tenantId)
            .eq(PcResourceEntity::getParentId, resourceId)
            .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new IllegalStateException("存在未删除的子资源，禁止删除");
        }
    }

    public void refreshRoleSubtreePaths(Long tenantId, String oldPath, String newPath) {
        if (oldPath == null || newPath == null || oldPath.equals(newPath)) {
            return;
        }
        List<PcAbstractRole> descendants = abstractRoleMapper.selectList(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, tenantId)
            .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED)
            .likeRight(PcAbstractRole::getPath, oldPath + "/"));
        for (PcAbstractRole descendant : descendants) {
            descendant.setPath(newPath + descendant.getPath().substring(oldPath.length()));
            abstractRoleMapper.updateById(descendant);
        }
    }

    public void refreshResourceSubtreePaths(Long tenantId, String oldPath, String newPath) {
        if (oldPath == null || newPath == null || oldPath.equals(newPath)) {
            return;
        }
        List<PcResourceEntity> descendants = resourceEntityMapper.selectList(new LambdaQueryWrapper<PcResourceEntity>()
            .eq(PcResourceEntity::getTenantId, tenantId)
            .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED)
            .likeRight(PcResourceEntity::getPath, oldPath + "/"));
        for (PcResourceEntity descendant : descendants) {
            descendant.setPath(newPath + descendant.getPath().substring(oldPath.length()));
            resourceEntityMapper.updateById(descendant);
        }
    }
}
