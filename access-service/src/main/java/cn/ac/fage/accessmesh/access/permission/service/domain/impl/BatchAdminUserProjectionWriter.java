package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量用户投影写入组件：批量删除/启停/upsert 与批量解析 abstract_user.id。
 * <p>
 * 由 {@link LocalProjectionDomainServiceImpl} 内部装配（构造注入 mapper），不独立作为 Spring bean；
 * 事务边界由调用方 AppService 声明，本组件只承担领域规则与数据写入。
 * </p>
 * <p>
 * 资源行统一限定 {@code code_type = 'default'}（与单条路径 selectByTypeCodeAndCodeType 语义一致），
 * 避免误伤外部同步以其他 code_type 创建的 USER 资源行。
 * </p>
 */
public class BatchAdminUserProjectionWriter {

    private static final String CODE_TYPE_DEFAULT = "default";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final TypeResolutionService typeResolutionService;
    private final AbstractUserMapper abstractUserMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final UserRoleMapper userRoleMapper;
    /** 所有权防线（无状态）：公共类型下防本地投影接管外部同步行（T-ACCESS-018 评审 P1） */
    private final LocalProjectionGuard localProjectionGuard = new LocalProjectionGuard();

    public BatchAdminUserProjectionWriter(TypeResolutionService typeResolutionService,
                                          AbstractUserMapper abstractUserMapper,
                                          ResourceEntityMapper resourceEntityMapper,
                                          UserRoleMapper userRoleMapper) {
        this.typeResolutionService = typeResolutionService;
        this.abstractUserMapper = abstractUserMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public void batchDeleteAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        if (sysUserIds == null || sysUserIds.isEmpty()) {
            return;
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        Set<String> extIds = sysUserIds.stream().map(String::valueOf).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<AbstractUser> users = abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds);
        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(AbstractUser::getId).collect(Collectors.toList());
            // 同一事务级联软删该用户的全部 user_role（含功能角色，不再依赖延迟补偿）
            userRoleMapper.softDeleteByAbstractUserIds(tenantId, new java.util.HashSet<>(userIds), now);
            abstractUserMapper.softDeleteBatch(tenantId, userIds, now);
        }
        List<ResourceEntity> resources = resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceType, extIds, Set.of(CODE_TYPE_DEFAULT)).stream()
            .filter(BatchAdminUserProjectionWriter::isOwnResource)
            .collect(Collectors.toList());
        if (!resources.isEmpty()) {
            resourceEntityMapper.softDeleteBatch(tenantId,
                resources.stream().map(ResourceEntity::getId).collect(Collectors.toList()), now);
        }
    }

    public void batchDisableAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        if (sysUserIds == null || sysUserIds.isEmpty()) {
            return;
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        Set<String> extIds = sysUserIds.stream().map(String::valueOf).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<AbstractUser> users = abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds);
        if (!users.isEmpty()) {
            abstractUserMapper.batchDisable(tenantId,
                users.stream().map(AbstractUser::getId).collect(Collectors.toSet()), now);
        }
        List<ResourceEntity> resources = resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceType, extIds, Set.of(CODE_TYPE_DEFAULT)).stream()
            .filter(BatchAdminUserProjectionWriter::isOwnResource)
            .collect(Collectors.toList());
        if (!resources.isEmpty()) {
            resourceEntityMapper.batchDisableStatus(tenantId,
                resources.stream().map(ResourceEntity::getId).collect(Collectors.toSet()), now);
        }
    }

    public Map<Long, Long> batchUpsertAdminUsers(Long tenantId, List<LocalProjectionDomainService.UpsertUserKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        Set<String> extIds = keys.stream()
            .map(k -> String.valueOf(k.sysUserId())).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();

        // 批量加载已有投影（资源行限定 code_type=default，与单条路径语义一致）
        Map<String, AbstractUser> usersByExt = abstractUserMapper
            .selectByTypeAndExternalIds(tenantId, userType, extIds)
            .stream().collect(Collectors.toMap(AbstractUser::getExternalId, u -> u, (a, b) -> a));
        Map<String, ResourceEntity> resourcesByCode = resourceEntityMapper
            .selectByTypeAndCodesAndCodeTypes(tenantId, resourceType, extIds, Set.of(CODE_TYPE_DEFAULT))
            .stream()
            .collect(Collectors.toMap(ResourceEntity::getCode, r -> r, (a, b) -> a));

        // 逐 key 计算，新行 insertBatch / 已有行批量刷新（一次 UPDATE）
        List<AbstractUser> toInsertUsers = new ArrayList<>();
        List<AbstractUser> toUpdateUsers = new ArrayList<>();
        List<ResourceEntity> toInsertResources = new ArrayList<>();
        List<ResourceEntity> toUpdateResources = new ArrayList<>();
        for (LocalProjectionDomainService.UpsertUserKey key : keys) {
            String externalId = String.valueOf(key.sysUserId());
            int statusVal = key.enabled() ? STATUS_ENABLED : STATUS_DISABLED;
            AbstractUser user = usersByExt.get(externalId);
            if (user == null) {
                user = new AbstractUser();
                user.setTenantId(tenantId);
                user.setUserType(userType);
                user.setExternalId(externalId);
                user.setName(key.name());
                user.setEnabled(key.enabled());
                user.setExtra(key.extraJson());
                user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                user.setDeleteFlag(0L);
                toInsertUsers.add(user);
            } else {
                user.setName(key.name());
                user.setEnabled(key.enabled());
                if (key.extraJson() != null) {
                    user.setExtra(key.extraJson());
                }
                user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                user.setUpdatedAt(now);
                toUpdateUsers.add(user);
            }
            ResourceEntity resource = resourcesByCode.get(externalId);
            // 公共类型（USER）下命中行可能属外部同步：fail-closed 拒绝接管（评审 P1）
            localProjectionGuard.rejectIfForeignResource(resource);
            if (resource == null) {
                resource = new ResourceEntity();
                resource.setTenantId(tenantId);
                resource.setResourceType(resourceType);
                resource.setCode(externalId);
                resource.setCodeType(CODE_TYPE_DEFAULT);
                resource.setName(key.name());
                resource.setParentId(null);
                resource.setStatus(statusVal);
                resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                // DDL maintain_source NOT NULL：显式 NULL 会绕过列默认值触发约束
                resource.setMaintainSource(PermConstants.MaintainSource.MANUAL);
                resource.setCreatedAt(now);
                resource.setUpdatedAt(now);
                resource.setDeleteFlag(0L);
                toInsertResources.add(resource);
            } else {
                resource.setName(key.name());
                resource.setStatus(statusVal);
                resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                resource.setUpdatedAt(now);
                toUpdateResources.add(resource);
            }
        }
        if (!toInsertUsers.isEmpty()) {
            abstractUserMapper.insertBatch(toInsertUsers);
        }
        if (!toInsertResources.isEmpty()) {
            resourceEntityMapper.insertBatch(toInsertResources);
        }
        // 已有行统一批量刷新（每行值不同，PG VALUES 单条 SQL，替代循环 update）
        if (!toUpdateUsers.isEmpty()) {
            abstractUserMapper.batchUpdateValues(tenantId, LocalProjectionOwner.SERVICE_CODE, toUpdateUsers, now);
        }
        if (!toUpdateResources.isEmpty()) {
            resourceEntityMapper.batchUpdateValues(tenantId, LocalProjectionOwner.SERVICE_CODE, toUpdateResources, now);
        }
        // 统一回查全部 abstract_user.id（含 insertBatch 新行——JDBC batch 无法回填主键）
        Map<Long, Long> result = new HashMap<>();
        for (AbstractUser user : abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds)) {
            try {
                result.put(Long.valueOf(user.getExternalId()), user.getId());
            } catch (NumberFormatException ignored) {
                // 本地投影 externalId 恒为数字串
            }
        }
        return result;
    }

    public Map<Long, Long> batchFindAdminUserIds(Long tenantId, Set<Long> sysUserIds) {
        if (sysUserIds == null || sysUserIds.isEmpty()) {
            return Map.of();
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Set<String> extIds = sysUserIds.stream().map(String::valueOf).collect(Collectors.toSet());
        Map<Long, Long> result = new HashMap<>();
        for (AbstractUser user : abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds)) {
            try {
                result.put(Long.valueOf(user.getExternalId()), user.getId());
            } catch (NumberFormatException ignored) {
                // 本地投影 externalId 恒为 sys_*.id 数字串；非数字串（外部同步）不映射
            }
        }
        return result;
    }

    /** 仅 owner=access-service 的行才是本地投影可操作的行（禁用/删除路径跳过外部行） */
    private static boolean isOwnResource(ResourceEntity resource) {
        return resource != null && LocalProjectionOwner.isLocalOwner(resource.getOwnerServiceCode());
    }

    private Integer requireType(Long tenantId, String typeKey, String typeCode) {
        Integer value = typeResolutionService.resolveTypeValue(tenantId, typeKey, typeCode);
        if (value == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(),
                "Unknown " + typeKey + ": " + typeCode);
        }
        return value;
    }
}
