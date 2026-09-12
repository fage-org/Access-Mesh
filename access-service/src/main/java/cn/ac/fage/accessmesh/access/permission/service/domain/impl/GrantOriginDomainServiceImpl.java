package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.GrantOriginDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantPlanDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link GrantOriginDomainService} 实现（T-PERM-062）。
 * <p>
 * 种子写入复用 {@link PermissionGrantPlanDomainService#seedGrants}（bootstrap 直写通道下沉，
 * 跳过委托校验、保留领域校验、幂等 insert-if-absent）；本实现只负责指针解析、角色解析
 * 与种子行构造/迁移编排，权限判定不经本类（管理面门禁在 AppService 入口）。
 * </p>
 */
@Service
public class GrantOriginDomainServiceImpl implements GrantOriginDomainService {

    private static final Logger log = LoggerFactory.getLogger(GrantOriginDomainServiceImpl.class);

    private static final String FIELD_ROLE_TYPE_CODE = "roleTypeCode";
    private static final String FIELD_ROLE_EXTERNAL_ID = "roleExternalId";

    private final ObjectMapper objectMapper;
    private final TypeResolutionService typeResolutionService;
    private final AbstractRoleMapper abstractRoleMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final PermissionGrantPlanDomainService permissionGrantPlanDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param objectMapper                   JSON 处理（extra 指针解析/注入）
     * @param typeResolutionService          类型解析（角色业务键 → 角色行）
     * @param abstractRoleMapper             角色数据访问层（所有者有效性/启用态）
     * @param roleResourcePermissionMapper   授权数据访问层（迁移清理面查询）
     * @param operationPermissionMapper      操作定义数据访问层（迁移补种全操作位）
     * @param permissionGrantPlanDomainService 授权计划领域服务（种子直写通道）
     */
    public GrantOriginDomainServiceImpl(ObjectMapper objectMapper,
                                        TypeResolutionService typeResolutionService,
                                        AbstractRoleMapper abstractRoleMapper,
                                        RoleResourcePermissionMapper roleResourcePermissionMapper,
                                        OperationPermissionMapper operationPermissionMapper,
                                        PermissionGrantPlanDomainService permissionGrantPlanDomainService) {
        this.objectMapper = objectMapper;
        this.typeResolutionService = typeResolutionService;
        this.abstractRoleMapper = abstractRoleMapper;
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.permissionGrantPlanDomainService = permissionGrantPlanDomainService;
    }

    @Override
    public GrantOriginRole parseGrantOriginPointer(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return null;
        }
        JsonNode root = readTree(extraJson);
        JsonNode pointer = root.get(EXTRA_KEY_GRANT_ORIGIN_ROLE);
        if (pointer == null) {
            return null;
        }
        // 显式 null 与缺键语义歧义（对齐 managedMode/syncSourceService 已知键显式 null 拒绝先例）；
        // 指针本无清除语义，结构坏指针一律 fail-closed 拒绝而非静默按缺省处理
        if (pointer.isNull()) {
            throw invalidPointer("不接受显式 null（缺省所有者请直接省略该键）");
        }
        if (!pointer.isObject()) {
            throw invalidPointer("必须为对象 {roleTypeCode, roleExternalId}");
        }
        JsonNode typeCode = pointer.get(FIELD_ROLE_TYPE_CODE);
        JsonNode externalId = pointer.get(FIELD_ROLE_EXTERNAL_ID);
        if (typeCode == null || !typeCode.isTextual() || typeCode.asText().isBlank()
            || externalId == null || !externalId.isTextual() || externalId.asText().isBlank()) {
            throw invalidPointer("缺少非空白文本字段 " + FIELD_ROLE_TYPE_CODE + "/" + FIELD_ROLE_EXTERNAL_ID);
        }
        return new GrantOriginRole(typeCode.asText().trim(), externalId.asText().trim());
    }

    @Override
    public Long resolveOwnerRoleId(Long tenantId, String extraJson) {
        GrantOriginRole pointer = parseGrantOriginPointer(extraJson);
        if (pointer == null) {
            pointer = new GrantOriginRole(DEFAULT_OWNER_ROLE_TYPE_CODE, DEFAULT_OWNER_ROLE_EXTERNAL_ID);
        }
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, pointer.roleTypeCode(), pointer.roleExternalId(), null);
        if (roleId == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(),
                "类型授权根角色不存在: " + pointer.roleTypeCode() + "/" + pointer.roleExternalId());
        }
        // 有效性/启用态核验（applyGrantPlan 目标角色同序列先例：20001 → 20003）
        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
        if (role == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(),
                "类型授权根角色不存在: " + pointer.roleTypeCode() + "/" + pointer.roleExternalId());
        }
        if (!Integer.valueOf(PermissionConstants.ENABLED_STATUS).equals(role.getStatus())) {
            throw new BizException(PermissionErrorCode.ROLE_DISABLED.getCode(),
                "类型授权根角色已停用: " + pointer.roleTypeCode() + "/" + pointer.roleExternalId());
        }
        return roleId;
    }

    @Override
    public String mergeGrantOriginPointer(String clientExtraJson, String roleTypeCode, String roleExternalId) {
        ObjectNode root;
        if (clientExtraJson == null || clientExtraJson.isBlank()) {
            root = objectMapper.createObjectNode();
        } else {
            JsonNode parsed = readTree(clientExtraJson);
            if (parsed.has(EXTRA_KEY_GRANT_ORIGIN_ROLE)) {
                throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                    PermissionErrorCode.INVALID_PARAM.getMessage()
                        + ": extra." + EXTRA_KEY_GRANT_ORIGIN_ROLE + " 由服务端维护，请使用请求字段 ownerRoleTypeCode/ownerRoleExternalId");
            }
            if (!parsed.isObject()) {
                throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                    PermissionErrorCode.INVALID_PARAM.getMessage() + ": extra 必须为 JSON 对象");
            }
            root = (ObjectNode) parsed;
        }
        ObjectNode pointer = objectMapper.createObjectNode();
        pointer.put(FIELD_ROLE_TYPE_CODE, roleTypeCode);
        pointer.put(FIELD_ROLE_EXTERNAL_ID, roleExternalId);
        root.set(EXTRA_KEY_GRANT_ORIGIN_ROLE, pointer);
        return root.toString();
    }

    @Override
    public boolean hasGrantOriginPointerKey(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(extraJson);
            return root != null && root.isObject() && root.has(EXTRA_KEY_GRANT_ORIGIN_ROLE);
        } catch (Exception e) {
            // 存在性探测非校验：坏 JSON 由 validateExtraDeclaration 先行拒绝（20044）
            return false;
        }
    }

    @Override
    public void seedAuthorityRootGrants(Long tenantId, Long ownerRoleId, Integer typeValue,
                                        Collection<Long> operationBits, Long operatorId) {
        if (operationBits == null || operationBits.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RoleResourcePermission> grants = new ArrayList<>(operationBits.size());
        for (Long bit : operationBits) {
            if (bit == null) {
                continue;
            }
            RoleResourcePermission grant = new RoleResourcePermission();
            grant.setTenantId(tenantId);
            grant.setAbstractRoleId(ownerRoleId);
            grant.setResourceEntityId(null);
            grant.setGrantedBits(bit);
            grant.setResourceType(typeValue);
            grant.setDependOn(null);
            grant.setScopeAll(true);
            grant.setCanGrant(true);
            grant.setConditionId(null);
            grant.setGrantSource(GrantSource.AUTHORITY_ROOT.getValue());
            grant.setCreatedBy(operatorId);
            grant.setCreatedAt(now);
            grant.setUpdatedAt(now);
            grant.setDeleteFlag(0L);
            grants.add(grant);
        }
        permissionGrantPlanDomainService.seedGrants(tenantId, ownerRoleId, grants);
    }

    @Override
    public Set<Long> rematerializeAuthorityRootGrants(Long tenantId, Integer typeValue,
                                                      Long newOwnerRoleId, Long operatorId) {
        List<RoleResourcePermission> existingRoots = roleResourcePermissionMapper
            .selectValidAuthorityRootsByTypes(tenantId, Set.of(typeValue));
        Set<Long> affectedRoleIds = existingRoots.stream()
            .map(RoleResourcePermission::getAbstractRoleId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!existingRoots.isEmpty()) {
            roleResourcePermissionMapper.softDeleteBatch(tenantId,
                existingRoots.stream().map(RoleResourcePermission::getId).toList(), LocalDateTime.now());
            log.info("T-PERM-062 owner migration: cleared {} AUTHORITY_ROOT row(s) of typeValue={} "
                + "(affected roles: {})", existingRoots.size(), typeValue, affectedRoleIds);
        }
        // 向新所有者补齐该类型全部有效操作位（幂等；操作位唯一性由 uk_operation_permission_typed 保证）
        Set<Long> operationBits = operationPermissionMapper
            .selectByTenantAndResourceTypes(tenantId, Set.of(typeValue)).stream()
            .map(OperationPermission::getBinaryBit)
            .filter(bit -> bit != null && bit > 0)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        seedAuthorityRootGrants(tenantId, newOwnerRoleId, typeValue, operationBits, operatorId);
        return affectedRoleIds;
    }

    private JsonNode readTree(String extraJson) {
        try {
            return objectMapper.readTree(extraJson);
        } catch (Exception e) {
            throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                PermissionErrorCode.INVALID_PARAM.getMessage() + ": extra 不是合法 JSON");
        }
    }

    private BizException invalidPointer(String detail) {
        return new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
            PermissionErrorCode.INVALID_PARAM.getMessage() + ": extra." + EXTRA_KEY_GRANT_ORIGIN_ROLE + " " + detail);
    }
}
