package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionVersionQueryReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionVersionResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionVersionService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限版本服务实现类
 * <p>
 * 提供权限版本查询功能。权限版本用于实现权限缓存失效策略，
 * 通过计算角色权限的最后更新时间戳作为版本号。
 * 版本号用于判断客户端缓存的权限数据是否过期，需要重新获取。
 * </p>
 */
@Service
public class PermissionVersionServiceImpl implements PermissionVersionService {

    private final TypeResolutionService typeResolutionService;
    private final AbstractRoleMapper abstractRoleMapper;
    private final RoleResourcePermissionMapper rolePermMapper;

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService 类型解析服务
     * @param abstractRoleMapper    抽象角色数据访问层
     * @param rolePermMapper        角色资源权限数据访问层
     */
    public PermissionVersionServiceImpl(TypeResolutionService typeResolutionService,
                                        AbstractRoleMapper abstractRoleMapper,
                                        RoleResourcePermissionMapper rolePermMapper) {
        this.typeResolutionService = typeResolutionService;
        this.abstractRoleMapper = abstractRoleMapper;
        this.rolePermMapper = rolePermMapper;
    }

    /**
     * 查询权限版本
     * <p>
     * 查询指定角色的权限版本号。版本号基于角色所有权限的最后更新时间戳计算，
     * 取所有权限记录中updatedAt字段的最大值转换为Unix时间戳。
     * 如果角色无权限记录，版本号为0。
     * 用于客户端缓存校验，判断权限数据是否需要更新。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限版本查询请求，包含角色类型编码、角色外部ID、业务域编码
     * @return 权限版本响应，包含角色ID、角色标识和版本号
     */
    @Override
    @Transactional(readOnly = true)
    public PermissionVersionResp queryVersion(Long tenantId, PermissionVersionQueryReq req) {
        Long roleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId == null) {
            return new PermissionVersionResp(null, req.roleTypeCode(), req.roleExternalId(), 0L);
        }

        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
        String roleTypeCode = req.roleTypeCode();
        String roleExternalId = req.roleExternalId();

        // 计算版本号：取该角色所有活跃权限的updatedAt的最大值转换为Unix时间戳
        // 如果没有权限记录，版本号为0
        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleId(tenantId, roleId);

        long version = perms.stream()
            .filter(p -> p.getUpdatedAt() != null)
            .mapToLong(p -> p.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC))
            .max()
            .orElse(0L);

        return new PermissionVersionResp(roleId, roleTypeCode, roleExternalId, version);
    }
}