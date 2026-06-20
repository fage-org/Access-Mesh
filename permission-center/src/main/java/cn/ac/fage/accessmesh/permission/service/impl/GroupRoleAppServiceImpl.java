package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleSummaryResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.GroupRoleAppService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import cn.ac.fage.accessmesh.permission.aop.PermissionChange;
import cn.ac.fage.accessmesh.permission.cache.PermissionChangeContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组角色管理服务实现类
 * <p>
 * 提供组角色（GROUP_ROLE）的额外角色管理功能。
 * 组角色是一种特殊角色类型，可以包含其他角色作为其"额外角色"，
 * 通过UserRole表记录组角色与基础角色的关联关系（targetType=GROUP_ROLE）。
 * 当用户拥有组角色时，自动继承组角色的额外角色权限。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 缓存失效操作在事务提交后执行，防止缓存被回滚数据污染。
 * </p>
 */
@Service
public class GroupRoleAppServiceImpl implements GroupRoleAppService {

    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param abstractRoleMapper    抽象角色数据访问层
     * @param userRoleMapper        用户角色数据访问层
     * @param typeResolutionService 类型解析服务
     * @param engine                权限查询引擎
     */
    public GroupRoleAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                       UserRoleMapper userRoleMapper,
                                       TypeResolutionService typeResolutionService,
                                       PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleMapper = userRoleMapper;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
    }

    /**
     * 为组角色添加额外角色
     * <p>
     * 将基础角色添加为组角色的额外角色。当用户拥有该组角色时，
     * 会自动继承该基础角色的权限。需要ROLE_ASSIGN权限。
     * 通过UserRole表记录关联关系：
     * - targetType = GROUP_ROLE
     * - targetId = 组角色ID
     * - relationId = 基础角色ID
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        添加额外角色请求，包含组角色标识和基础角色标识
     * @param operatorId 操作者ID，可选
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 组角色或基础角色不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "group-role-extra-add", targetType = "abstract_role", targetId = "#req.groupRoleExternalId()", summary = "'add extra role to group role ' + #req.groupRoleExternalId()")
    @PermissionChange
    public void addGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.groupDomainCode());
        if (groupId == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Group role not found: " + req.groupRoleExternalId());
        }
        Long basicRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.basicRoleTypeCode(), req.basicRoleExternalId(), req.basicDomainCode());
        if (basicRoleId == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Basic role not found: " + req.basicRoleExternalId());
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, groupId, OperationCodeConstants.ASSIGN)) {
            throw new SecurityException("Permission denied: ASSIGN on ROLE:" + groupId);
        }

        AbstractRole groupRole = abstractRoleMapper.selectValidById(groupId, tenantId);
        Integer groupRoleTypeValue = typeResolutionService.resolveTypeValue(tenantId, "role_type", PermConstants.TargetType.GROUP_ROLE);
        if (groupRole == null || groupRoleTypeValue == null || !groupRoleTypeValue.equals(groupRole.getRoleType())) {
            throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(), "Not a valid GROUP_ROLE: " + req.groupRoleExternalId());
        }

        AbstractRole basicRole = abstractRoleMapper.selectValidById(basicRoleId, tenantId);
        if (basicRole == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Basic role not found: " + req.basicRoleExternalId());
        }

        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(null);
        ur.setTargetType(PermConstants.TargetType.GROUP_ROLE);
        ur.setTargetId(groupId);
        ur.setRelationId(basicRoleId);
        LocalDateTime now = LocalDateTime.now();
        ur.setCreatedAt(now);
        ur.setUpdatedAt(now);
        ur.setDeleteFlag(0L);
        userRoleMapper.insert(ur);

        // 登记受影响角色，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        PermissionChangeContext.markRoles(tenantId, groupId);
    }

    /**
     * 移除组角色的额外角色
     * <p>
     * 将基础角色从组角色的额外角色列表中移除。
     * 需要ROLE_REVOKE权限。软删除UserRole关联记录。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        移除额外角色请求，包含组角色标识和基础角色标识
     * @param operatorId 操作者ID，可选
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 组角色或基础角色不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "group-role-extra-remove", targetType = "abstract_role", targetId = "#req.groupRoleExternalId()", summary = "'remove extra role from group role ' + #req.groupRoleExternalId()")
    @PermissionChange
    public void removeGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.groupDomainCode());
        if (groupId == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Group role not found: " + req.groupRoleExternalId());
        }
        Long basicRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.basicRoleTypeCode(), req.basicRoleExternalId(), req.basicDomainCode());
        if (basicRoleId == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Basic role not found: " + req.basicRoleExternalId());
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, groupId, OperationCodeConstants.REVOKE)) {
            throw new SecurityException("Permission denied: REVOKE on ROLE:" + groupId);
        }

        UserRole ur = userRoleMapper.selectValidByTargetAndRelation(tenantId, PermConstants.TargetType.GROUP_ROLE, groupId, basicRoleId);
        if (ur == null) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        ur.setDeleteFlag(ur.getId());
        ur.setDeletedAt(LocalDateTime.now());
        userRoleMapper.update(ur);
        // 登记受影响角色，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        PermissionChangeContext.markRoles(tenantId, groupId);
    }

    /**
     * 查询组角色的额外角色列表
     * <p>
     * 查询指定组角色包含的所有额外角色。
     * 通过UserRole表查询targetType=GROUP_ROLE的记录，
     * relationId字段即为额外角色的ID。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      查询请求，包含组角色标识
     * @return 角色摘要响应列表
     */
    @Override
    public List<RoleSummaryResp> listGroupRoleExtraRoles(Long tenantId, GroupRoleExtraRolesListReq req) {
        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.domainCode());
        if (groupId == null) {
            return List.of();
        }
        Set<Long> basicRoleIds = userRoleMapper.selectByTargetTypeAndTargetId(tenantId, PermConstants.TargetType.GROUP_ROLE, groupId)
            .stream()
            .map(UserRole::getRelationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (basicRoleIds.isEmpty()) {
            return List.of();
        }
        return abstractRoleMapper.selectValidByIds(tenantId, basicRoleIds).stream()
            .map(r -> new RoleSummaryResp(
                r.getId(),
                typeResolutionService.resolveTypeCode(tenantId, "role_type", r.getRoleType()),
                r.getExternalId(),
                r.getName()))
            .collect(Collectors.toList());
    }
}