package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.ConditionSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import com.mybatisflex.core.util.UpdateEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地权限投影写入。不写 sync_metadata，owner 固定 access-service。
 * <p>
 * 单条投影（用户/组织/菜单）与依赖解析在本类；user_role 绑定/迁移委托
 * {@link UserRoleProjectionWriter}，批量用户投影委托 {@link BatchAdminUserProjectionWriter}。
 * </p>
 */
@Slf4j
@Service
public class LocalProjectionDomainServiceImpl implements LocalProjectionDomainService {

    private static final String CODE_TYPE_DEFAULT = "default";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final TypeResolutionService typeResolutionService;
    private final AbstractUserMapper abstractUserMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final PermissionConditionMapper permissionConditionMapper;
    private final UserRoleProjectionWriter userRoleProjectionWriter;
    private final BatchAdminUserProjectionWriter batchAdminUserProjectionWriter;

    public LocalProjectionDomainServiceImpl(TypeResolutionService typeResolutionService,
                                            AbstractUserMapper abstractUserMapper,
                                            AbstractRoleMapper abstractRoleMapper,
                                            ResourceEntityMapper resourceEntityMapper,
                                            TypeDefinitionMapper typeDefinitionMapper,
                                            PermissionConditionMapper permissionConditionMapper,
                                            UserRoleMapper userRoleMapper) {
        this.typeResolutionService = typeResolutionService;
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.permissionConditionMapper = permissionConditionMapper;
        this.userRoleProjectionWriter = new UserRoleProjectionWriter(
            typeResolutionService, abstractUserMapper, abstractRoleMapper, userRoleMapper);
        this.batchAdminUserProjectionWriter = new BatchAdminUserProjectionWriter(
            typeResolutionService, abstractUserMapper, resourceEntityMapper, userRoleMapper);
    }

    @Override
    public Long createLocalUserSubject(Long tenantId, String name, boolean enabled, String extraJson) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        // T-ORG-001（§12.2）：abstract_user.id 序列预取主体 ID，external_id 终态 = 主体 ID 字符串化，
        // 不能先插行再回填——本地用户由调用方以同一 N 显式插 sys_user(id=N)
        Long subjectId = abstractUserMapper.nextSubjectId();
        String externalId = String.valueOf(subjectId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = new AbstractUser();
        user.setId(subjectId);
        user.setTenantId(tenantId);
        user.setUserType(userType);
        user.setExternalId(externalId);
        user.setName(name);
        user.setEnabled(enabled);
        user.setExtra(extraJson);
        user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleteFlag(0L);
        abstractUserMapper.insertWithExplicitId(user);

        upsertResource(tenantId, resourceType, externalId, name, null,
            enabled ? STATUS_ENABLED : STATUS_DISABLED, now);
        return subjectId;
    }

    @Override
    public Long upsertAdminUser(Long tenantId, Long sysUserId, String name, boolean enabled, String extraJson) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        String externalId = String.valueOf(sysUserId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
        if (user == null) {
            user = new AbstractUser();
            user.setTenantId(tenantId);
            user.setUserType(userType);
            user.setExternalId(externalId);
            user.setName(name);
            user.setEnabled(enabled);
            user.setExtra(extraJson);
            user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setDeleteFlag(0L);
            abstractUserMapper.insert(user);
        } else {
            user.setName(name);
            user.setEnabled(enabled);
            if (extraJson != null) {
                user.setExtra(extraJson);
            }
            user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            user.setUpdatedAt(now);
            abstractUserMapper.update(user);
        }

        upsertResource(tenantId, resourceType, externalId, name, null, enabled ? STATUS_ENABLED : STATUS_DISABLED, now);
        return user.getId();
    }

    @Override
    public void disableAdminUser(Long tenantId, Long sysUserId) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        String externalId = String.valueOf(sysUserId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
        if (user != null) {
            user.setEnabled(Boolean.FALSE);
            user.setUpdatedAt(now);
            abstractUserMapper.update(user);
        }
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        // T-PERM-052：USER 类型种子声明 SYNC+access-service 后行内只可能是本投影的行，
        // 原 isOwnResource 外部行过滤已无必要（类型门禁挡住外部写入）
        if (resource != null) {
            resource.setStatus(STATUS_DISABLED);
            resource.setUpdatedAt(now);
            resourceEntityMapper.update(resource);
        }
    }

    @Override
    public void deleteAdminUser(Long tenantId, Long sysUserId) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        String externalId = String.valueOf(sysUserId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
        if (user != null) {
            abstractUserMapper.softDeleteBatch(tenantId, List.of(user.getId()), now);
        }
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        if (resource != null) {
            resourceEntityMapper.softDeleteBatch(tenantId, List.of(resource.getId()), now);
        }
    }

    @Override
    public Long upsertAdminOrg(Long tenantId, Long sysOrgId, String orgType, String name,
                               Long parentOrgId, String parentOrgType, Integer status, Integer sortOrder,
                               String extraJson) {
        String roleTypeCode = resolveOrgRoleType(orgType);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.ORG);
        String externalId = String.valueOf(sysOrgId);
        LocalDateTime now = LocalDateTime.now();
        int statusVal = status != null ? status : STATUS_ENABLED;

        // 父角色类型按父节点实际 orgType 解析（ORG 父 + POSITION 子不再查不到）
        Long parentRoleId = resolveParentRoleId(tenantId, roleType, parentOrgId, parentOrgType);

        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(tenantId, roleType, externalId);
        if (role == null) {
            role = new AbstractRole();
            role.setTenantId(tenantId);
            role.setRoleType(roleType);
            role.setExternalId(externalId);
            role.setName(name);
            role.setParentId(parentRoleId);
            role.setStatus(statusVal);
            role.setSortOrder(sortOrder);
            role.setExtra(extraJson);
            role.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            role.setDeleteFlag(0L);
            abstractRoleMapper.insert(role);
        } else {
            role.setName(name);
            role.setParentId(parentRoleId);
            role.setStatus(statusVal);
            if (sortOrder != null) {
                role.setSortOrder(sortOrder);
            }
            if (extraJson != null) {
                role.setExtra(extraJson);
            }
            role.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            role.setUpdatedAt(now);
            abstractRoleMapper.update(role);
        }

        Long parentResourceId = resolveParentResourceId(tenantId, resourceType, parentOrgId);
        upsertResource(tenantId, resourceType, externalId, name, parentResourceId, statusVal, now);
        return role.getId();
    }

    @Override
    public void deleteAdminOrg(Long tenantId, Long sysOrgId, String orgType) {
        String roleTypeCode = resolveOrgRoleType(orgType);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.ORG);
        String externalId = String.valueOf(sysOrgId);
        LocalDateTime now = LocalDateTime.now();

        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(tenantId, roleType, externalId);
        if (role != null) {
            abstractRoleMapper.softDeleteBatch(tenantId, List.of(role.getId()), now);
        }
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        if (resource != null) {
            resourceEntityMapper.softDeleteBatch(tenantId, List.of(resource.getId()), now);
        }
    }

    @Override
    public Long upsertAdminMenu(Long tenantId, Long sysMenuId, String name, Long parentMenuId,
                                Integer status, Integer sortOrder) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.MENU);
        String externalId = String.valueOf(sysMenuId);
        LocalDateTime now = LocalDateTime.now();
        int statusVal = status != null ? status : STATUS_ENABLED;
        Long parentResourceId = resolveParentResourceId(tenantId, resourceType, parentMenuId);
        return upsertResource(tenantId, resourceType, externalId, name, parentResourceId, statusVal, now);
    }

    @Override
    public void deleteAdminMenu(Long tenantId, Long sysMenuId) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.MENU);
        String externalId = String.valueOf(sysMenuId);
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        if (resource != null) {
            resourceEntityMapper.softDeleteBatch(tenantId, List.of(resource.getId()), LocalDateTime.now());
        }
    }

    @Override
    public Long ensureAdminFileFolder(Long tenantId, String folderCode, String name) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.ADMIN_FILE);
        // insert-if-absent：有效行已存在直接返回（名称以首建为准——惰性登记传 code、预置传标签，
        // 回写会让后到者覆盖先建者）；软删墓碑不在有效行查询内，按缺行重新插入（部分唯一索引允许）
        ResourceEntity existing = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, folderCode, CODE_TYPE_DEFAULT);
        if (existing != null) {
            return existing.getId();
        }
        ResourceEntity resource = new ResourceEntity();
        resource.setTenantId(tenantId);
        resource.setResourceType(resourceType);
        resource.setCode(folderCode);
        resource.setCodeType(CODE_TYPE_DEFAULT);
        resource.setName(name);
        resource.setParentId(null);
        resource.setStatus(STATUS_ENABLED);
        resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        // DDL maintain_source NOT NULL：显式 NULL 会绕过列默认值触发约束（同 upsertResource）
        resource.setMaintainSource(PermConstants.MaintainSource.MANUAL);
        LocalDateTime now = LocalDateTime.now();
        resource.setCreatedAt(now);
        resource.setUpdatedAt(now);
        resource.setDeleteFlag(0L);
        resourceEntityMapper.insert(resource);
        return resource.getId();
    }

    @Override
    public Long bindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode,
                            Long relationSysOrgId) {
        return userRoleProjectionWriter.bindUserOrg(tenantId, sysUserId, sysOrgId, roleTypeCode, relationSysOrgId);
    }

    @Override
    public Long unbindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode,
                              Long relationSysOrgId) {
        return userRoleProjectionWriter.unbindUserOrg(tenantId, sysUserId, sysOrgId, roleTypeCode, relationSysOrgId);
    }

    @Override
    public Map<UserOrgBindKey, Long> batchBindUserOrg(Long tenantId, List<UserOrgBindKey> keys) {
        return userRoleProjectionWriter.batchBindUserOrg(tenantId, keys);
    }

    @Override
    public void batchUnbindUserOrg(Long tenantId, List<UserOrgBindKey> keys) {
        userRoleProjectionWriter.batchUnbindUserOrg(tenantId, keys);
    }

    @Override
    public Set<Long> migratePositionRelation(Long tenantId, Long sysPositionId,
                                             Long oldRelationOrgId, Long newRelationOrgId) {
        return userRoleProjectionWriter.migratePositionRelation(tenantId, sysPositionId, oldRelationOrgId, newRelationOrgId);
    }

    @Override
    public void batchDeleteAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        batchAdminUserProjectionWriter.batchDeleteAdminUsers(tenantId, sysUserIds);
    }

    @Override
    public void batchDisableAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        batchAdminUserProjectionWriter.batchDisableAdminUsers(tenantId, sysUserIds);
    }

    @Override
    public Map<Long, Long> batchUpsertAdminUsers(Long tenantId, List<UpsertUserKey> keys) {
        return batchAdminUserProjectionWriter.batchUpsertAdminUsers(tenantId, keys);
    }

    @Override
    public Map<Long, Long> batchFindAdminUserIds(Long tenantId, Set<Long> sysUserIds) {
        return batchAdminUserProjectionWriter.batchFindAdminUserIds(tenantId, sysUserIds);
    }

    @Override
    public Long findAdminUserId(Long tenantId, Long sysUserId) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(
            tenantId, userType, String.valueOf(sysUserId));
        return user == null ? null : user.getId();
    }

    @Override
    public Long findAdminOrgRoleId(Long tenantId, Long sysOrgId, String orgType) {
        Integer roleType = requireType(tenantId, "role_type", resolveOrgRoleType(orgType));
        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, roleType, String.valueOf(sysOrgId));
        return role == null ? null : role.getId();
    }

    @Override
    public Long findAdminMenuResourceId(Long tenantId, Long sysMenuId) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.MENU);
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, String.valueOf(sysMenuId), CODE_TYPE_DEFAULT);
        return resource == null ? null : resource.getId();
    }

    @Override
    public void upsertRoleResource(Long tenantId, Long roleId, String name, Integer status, Long parentRoleId) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.ROLE);
        Long parentResourceId = resolveParentResourceId(tenantId, resourceType, parentRoleId);
        // 仅 status=1 视启用（写入口未限定取值，非 0/1 值 fail-closed 落禁用，
        // 与组角色展开/基础角色 selectEnabledIdsByIds 口径对齐）
        upsertResource(tenantId, resourceType, String.valueOf(roleId), name, parentResourceId,
            status != null && status == 1 ? STATUS_ENABLED : STATUS_DISABLED, LocalDateTime.now());
    }

    @Override
    public void softDeleteRoleResources(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.ROLE);
        softDeleteOwnResources(tenantId, resourceType, roleIds.stream()
            .map(String::valueOf).collect(java.util.stream.Collectors.toSet()));
    }

    @Override
    public void upsertUserResource(Long tenantId, Long subjectId, String name, boolean enabled) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        upsertResource(tenantId, resourceType, String.valueOf(subjectId), name, null,
            enabled ? STATUS_ENABLED : STATUS_DISABLED, LocalDateTime.now());
    }

    @Override
    public void softDeleteUserResources(Long tenantId, Set<Long> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return;
        }
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.USER);
        softDeleteOwnResources(tenantId, resourceType, subjectIds.stream()
            .map(String::valueOf).collect(java.util.stream.Collectors.toSet()));
    }

    @Override
    public void upsertTypeDefinitionResource(Long tenantId, String typeKey, String typeCode, String name) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.TYPE_DEFINITION);
        upsertResource(tenantId, resourceType, BusinessKeys.typeInstanceBusinessKey(typeKey, typeCode),
            name, null, STATUS_ENABLED, LocalDateTime.now());
    }

    @Override
    public List<Long> findTypeDefinitionResourceIds(Long tenantId, Set<String> compositeKeys) {
        if (compositeKeys == null || compositeKeys.isEmpty()) {
            return List.of();
        }
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.TYPE_DEFINITION);
        return resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceType, compositeKeys, Set.of(CODE_TYPE_DEFAULT)).stream()
            .map(ResourceEntity::getId)
            .toList();
    }

    @Override
    public int backfillTypeDefinitionProjections(Long tenantId) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.TYPE_DEFINITION);
        List<TypeDefinition> validTypes = typeDefinitionMapper.selectValidByTenant(tenantId);
        if (validTypes.isEmpty()) {
            return 0;
        }
        Set<String> expectedCodes = validTypes.stream()
            .map(t -> BusinessKeys.typeInstanceBusinessKey(t.getTypeKey(), t.getTypeCode()))
            .collect(Collectors.toSet());
        Set<String> existingCodes = resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceType, expectedCodes, Set.of(CODE_TYPE_DEFAULT)).stream()
            .map(ResourceEntity::getCode)
            .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<ResourceEntity> toInsert = new ArrayList<>();
        for (TypeDefinition type : validTypes) {
            String code = BusinessKeys.typeInstanceBusinessKey(type.getTypeKey(), type.getTypeCode());
            if (existingCodes.contains(code)) {
                continue;
            }
            ResourceEntity resource = new ResourceEntity();
            resource.setTenantId(tenantId);
            resource.setResourceType(resourceType);
            resource.setCode(code);
            resource.setCodeType(CODE_TYPE_DEFAULT);
            resource.setName(type.getName());
            resource.setParentId(null);
            resource.setStatus(STATUS_ENABLED);
            resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            // DDL maintain_source NOT NULL：显式 NULL 会绕过列默认值触发约束（同 upsertResource）
            resource.setMaintainSource(PermConstants.MaintainSource.MANUAL);
            resource.setCreatedAt(now);
            resource.setUpdatedAt(now);
            resource.setDeleteFlag(0L);
            toInsert.add(resource);
        }
        if (toInsert.isEmpty()) {
            return 0;
        }
        resourceEntityMapper.insertBatch(toInsert);
        return toInsert.size();
    }

    @Override
    public void upsertConditionResource(Long tenantId, String code, String name, boolean enabled) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.CONDITION);
        // status 镜像条件 enabled：停用条件投影行 status=0，授权资源树 status=1 过滤自动隐出
        upsertResource(tenantId, resourceType, code, name, null,
            enabled ? STATUS_ENABLED : STATUS_DISABLED, LocalDateTime.now());
    }

    @Override
    public void softDeleteConditionResources(Long tenantId, Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.CONDITION);
        softDeleteOwnResources(tenantId, resourceType, codes);
    }

    @Override
    public List<Long> findConditionResourceIds(Long tenantId, Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.CONDITION);
        return resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceType, codes, Set.of(CODE_TYPE_DEFAULT)).stream()
            .map(ResourceEntity::getId)
            .toList();
    }

    @Override
    public int backfillConditionProjections(Long tenantId) {
        Integer resourceType = requireType(tenantId, "resource_type", ResourceTypeCode.CONDITION);
        // 仅 MANAGED 条件投影（T-PERM-048 定案⑤：INLINE 内联条件无资源身份消费者，不投影）
        List<PermissionCondition> validManaged = permissionConditionMapper.selectByTenantId(tenantId)
            .stream()
            .filter(condition -> ConditionSource.MANAGED.getValue().equals(condition.getSource()))
            .toList();
        Set<String> expectedCodes = validManaged.stream()
            .map(PermissionCondition::getCode)
            .collect(Collectors.toSet());
        List<ResourceEntity> existingRows = resourceEntityMapper
            .selectValidByResourceTypes(tenantId, Set.of(resourceType));
        // 野行告警：CONDITION 类型下 code 不匹配任何有效 MANAGED 条件的存量资源行——
        // 特性上线前 CONDITION 为 MANAGED 类型管理面手工可建，现入 SYNC 族 20055 只读成僵尸；
        // 不自动清理（软删行可能是有效授权目标），清理语句见 rebuild-runbook FAQ
        for (ResourceEntity row : existingRows) {
            if (CODE_TYPE_DEFAULT.equals(row.getCodeType()) && !expectedCodes.contains(row.getCode())) {
                log.warn("CONDITION 资源行无对应有效条件（存量手工行或孤儿投影，不自动清理）: "
                    + "tenantId={}, resourceEntityId={}, code={}", tenantId, row.getId(), row.getCode());
            }
        }
        if (validManaged.isEmpty()) {
            return 0;
        }
        // 已有投影判定严格限定 code_type=default（codex 外评 P2-5：与 upsert/softDelete 定位对称；
        // 按 selectValidByResourceTypes 裸 code 去重会被同 code 非 default 编码行误跳补种，
        // 对齐 backfillTypeDefinitionProjections 先例的 default 过滤查询）
        Set<String> existingCodes = resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceType, expectedCodes, Set.of(CODE_TYPE_DEFAULT)).stream()
            .map(ResourceEntity::getCode)
            .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<ResourceEntity> toInsert = new ArrayList<>();
        for (PermissionCondition condition : validManaged) {
            if (existingCodes.contains(condition.getCode())) {
                continue;
            }
            ResourceEntity resource = new ResourceEntity();
            resource.setTenantId(tenantId);
            resource.setResourceType(resourceType);
            resource.setCode(condition.getCode());
            resource.setCodeType(CODE_TYPE_DEFAULT);
            resource.setName(condition.getName());
            resource.setParentId(null);
            resource.setStatus(Boolean.TRUE.equals(condition.getEnabled())
                ? STATUS_ENABLED : STATUS_DISABLED);
            resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            // DDL maintain_source NOT NULL：显式 NULL 会绕过列默认值触发约束（同 upsertResource）
            resource.setMaintainSource(PermConstants.MaintainSource.MANUAL);
            resource.setCreatedAt(now);
            resource.setUpdatedAt(now);
            resource.setDeleteFlag(0L);
            toInsert.add(resource);
        }
        if (toInsert.isEmpty()) {
            return 0;
        }
        resourceEntityMapper.insertBatch(toInsert);
        return toInsert.size();
    }

    /** 批量按 code 软删本地投影资源行（一次批量加载 + 一次批量软删）。
     * 限定 code_type=default 与 upsert 定位对称（不误删同 code 非默认编码行）。
     * T-PERM-052：USER 类型种子声明 SYNC+access-service 后行内只可能是本投影的行，
     * 原 owner 过滤已无必要 */
    private void softDeleteOwnResources(Long tenantId, Integer resourceType, Set<String> codes) {
        List<Long> ids = resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceType, codes, Set.of(CODE_TYPE_DEFAULT)).stream()
            .map(ResourceEntity::getId)
            .toList();
        if (!ids.isEmpty()) {
            resourceEntityMapper.softDeleteBatch(tenantId, ids, LocalDateTime.now());
        }
    }

    private Long upsertResource(Long tenantId, Integer resourceType, String code, String name,
                                Long parentId, int status, LocalDateTime now) {
        ResourceEntity existing = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, code, CODE_TYPE_DEFAULT);
        // T-PERM-052：USER/ORG/MENU 类型种子声明 SYNC+access-service，外部同步/人工创建均被
        // 类型门禁拒绝（20055/RESOURCE_TYPE_OWNERSHIP_DENIED），命中行只可能是本投影的行，
        // 原 rejectIfForeignResource 接管防线已无必要
        if (existing == null) {
            ResourceEntity resource = new ResourceEntity();
            resource.setTenantId(tenantId);
            resource.setResourceType(resourceType);
            resource.setCode(code);
            resource.setCodeType(CODE_TYPE_DEFAULT);
            resource.setName(name);
            resource.setParentId(parentId);
            resource.setStatus(status);
            resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            // DDL maintain_source NOT NULL：显式 NULL 会绕过列默认值触发约束
            resource.setMaintainSource(PermConstants.MaintainSource.MANUAL);
            resource.setCreatedAt(now);
            resource.setUpdatedAt(now);
            resource.setDeleteFlag(0L);
            resourceEntityMapper.insert(resource);
            return resource.getId();
        }
        existing.setName(name);
        existing.setStatus(status);
        existing.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        existing.setUpdatedAt(now);
        // parentId 置 null（挂到根）须强制写列：update(entity) 忽略 null 字段（flex 语义），
        // 旧 parent 残留会让资源树错挂——UpdateEntity 显式更新列（moveResource 同款先例，
        // T-PERM-052 评审批次顺手修复）
        if (parentId == null) {
            ResourceEntity patch = UpdateEntity.of(ResourceEntity.class);
            patch.setId(existing.getId());
            patch.setParentId(null);
            patch.setName(name);
            patch.setStatus(status);
            patch.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            patch.setUpdatedAt(now);
            resourceEntityMapper.update(patch);
        } else {
            existing.setParentId(parentId);
            resourceEntityMapper.update(existing);
        }
        return existing.getId();
    }

    private Long resolveParentRoleId(Long tenantId, Integer childRoleType, Long parentOrgId,
                                     String parentOrgType) {
        if (parentOrgId == null || parentOrgId == 0L) {
            return null;
        }
        // 父角色类型按父节点实际 orgType 解析（ORG 父 + POSITION 子不再查不到）；
        // parentOrgType 缺失时回退用子节点 roleType，兼容未提供父类型的调用
        Integer parentRoleType = childRoleType;
        if (parentOrgType != null && !parentOrgType.isBlank()) {
            parentRoleType = requireType(tenantId, "role_type", resolveOrgRoleType(parentOrgType));
        }
        AbstractRole parent = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, parentRoleType, String.valueOf(parentOrgId));
        if (parent == null) {
            // 父角色投影缺失 = 依赖缺失，抛错回滚（不再静默写 parentId=null 脱离父树）
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                "父组织角色投影缺失: parentOrgId=" + parentOrgId);
        }
        return parent.getId();
    }

    private Long resolveParentResourceId(Long tenantId, Integer resourceType, Long parentExternalId) {
        if (parentExternalId == null || parentExternalId == 0L) {
            return null;
        }
        ResourceEntity parent = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, String.valueOf(parentExternalId), CODE_TYPE_DEFAULT);
        if (parent == null) {
            // 父资源投影缺失 = 依赖缺失，抛错回滚（与父角色同语义，不再静默写 parentId=null 脱离父树）
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                "父资源投影缺失: parentId=" + parentExternalId);
        }
        return parent.getId();
    }

    private Integer requireType(Long tenantId, String typeKey, String typeCode) {
        Integer value = typeResolutionService.resolveTypeValue(tenantId, typeKey, typeCode);
        if (value == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(),
                "Unknown " + typeKey + ": " + typeCode);
        }
        return value;
    }

    private static String resolveOrgRoleType(String orgType) {
        if ("2".equals(orgType) || LocalProjectionOwner.ROLE_POSITION.equalsIgnoreCase(orgType)) {
            return LocalProjectionOwner.ROLE_POSITION;
        }
        return LocalProjectionOwner.ROLE_ORG;
    }
}
