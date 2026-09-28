package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.enums.ApiMappingSource;
import cn.ac.fage.accessmesh.access.resource.enums.ApiAuthMode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ApiMappingWriteDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.TypeDefinitionDomainService;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.entity.TypeDefinition;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.mybatisflex.core.util.UpdateEntity;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 共同写侧：新鲜引用校验、维护来源隔离与整批保存。 */
@Service
public class ApiMappingWriteDomainServiceImpl implements ApiMappingWriteDomainService {
    private final ResourceApiMappingMapper mappings;
    private final ResourceEntityMapper resources;
    private final ServiceConfigMapper services;
    private final TypeDefinitionDomainService types;
    private final OperationPermissionDomainService operations;
    private final TreeWriteLockSupport locks;

    public ApiMappingWriteDomainServiceImpl(ResourceApiMappingMapper mappings, ResourceEntityMapper resources,
            ServiceConfigMapper services, TypeDefinitionDomainService types,
            OperationPermissionDomainService operations, TreeWriteLockSupport locks) {
        this.mappings = mappings;
        this.resources = resources;
        this.services = services;
        this.types = types;
        this.operations = operations;
        this.locks = locks;
    }

    @Override
    public void saveAll(Long tenantId, String serviceCode, ApiMappingSource source, List<Write> writes) {
        if (writes.isEmpty()) return;
        // 与操作/类型删除及资源同步共锁，校验到写入期间引用不能被另一写事务删除。
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        var config = services.selectByTenantAndServiceCode(tenantId, serviceCode);
        if (config == null) throw invalid("服务未登记: " + serviceCode);
        String mode = config.getApiAuthMode();
        if (!ApiAuthMode.LEGACY_API.name().equals(mode) && !ApiAuthMode.OPERATION_ADMISSION.name().equals(mode)) {
            throw invalid("未知服务鉴权模式");
        }

        Map<String, Integer> typeValues = types.selectByTenantAndTypeKey(tenantId, "resource_type").stream()
            .collect(Collectors.toMap(TypeDefinition::getTypeCode, TypeDefinition::getTypeValue));
        Set<Long> resourceIds = writes.stream().map(w -> w.mapping().getResourceEntityId()).collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceById = resources.selectValidByIds(tenantId, resourceIds).stream()
            .collect(Collectors.toMap(ResourceEntity::getId, r -> r));
        Set<Integer> requiredTypes = writes.stream().map(Write::requiredPermission).filter(Objects::nonNull)
            .map(p -> typeValues.get(p.resourceTypeCode())).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<RequiredPermission, OperationPermission> byRequirement = new HashMap<>();
        Map<Integer, String> typeCodes = typeValues.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        if (!requiredTypes.isEmpty()) {
            operations.selectByTenantAndResourceTypes(tenantId, requiredTypes).forEach(op ->
                byRequirement.put(new RequiredPermission(typeCodes.get(op.getResourceType()), op.getCode()), op));
        }
        Set<Long> retainedIds = writes.stream().filter(w -> w.requiredPermission() == null)
            .map(w -> w.mapping().getRequiredOperationId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, OperationPermission> retained = retainedIds.isEmpty() ? Map.of()
            : operations.selectValidByIds(tenantId, retainedIds).stream()
                .collect(Collectors.toMap(OperationPermission::getId, op -> op));
        List<ResourceApiMapping> existing = mappings.selectByTenantAndServiceCode(tenantId, serviceCode);
        Map<Long, ResourceApiMapping> existingById = existing.stream()
            .collect(Collectors.toMap(ResourceApiMapping::getId, row -> row));

        for (Write write : writes) {
            ResourceApiMapping row = write.mapping();
            // 方法名统一大写（与同步侧/网关匹配语义同构），否则小写手工行绕过同路由占用检查
            row.setHttpMethod(row.getHttpMethod().toUpperCase(java.util.Locale.ROOT));
            if (!Objects.equals(row.getTenantId(), tenantId) || !Objects.equals(row.getServiceCode(), serviceCode)) {
                throw invalid("映射租户或服务不匹配");
            }
            ResourceEntity api = resourceById.get(row.getResourceEntityId());
            if (api == null || typeValues.get("API") == null || !Objects.equals(api.getResourceType(), typeValues.get("API"))) {
                throw invalid("映射必须引用本租户有效的 API 登记实体");
            }
            if (row.getId() != null) {
                ResourceApiMapping old = existingById.get(row.getId());
                if (old == null || !source.name().equals(old.getMaintainSource())) throw conflict();
            }
            for (ResourceApiMapping old : existing) {
                if (Objects.equals(old.getHttpMethod(), row.getHttpMethod())
                    && Objects.equals(old.getPathPattern(), row.getPathPattern())
                    && !source.name().equals(old.getMaintainSource())) throw conflict();
            }
            OperationPermission op = write.requiredPermission() == null
                ? (row.getRequiredOperationId() == null ? null : retained.get(row.getRequiredOperationId()))
                : byRequirement.get(write.requiredPermission());
            boolean required = write.requiredPermission() != null || row.getRequiredOperationId() != null
                || ApiAuthMode.OPERATION_ADMISSION.name().equals(mode);
            if (required) {
                if (op == null || op.getBinaryBit() == null || op.getBinaryBit() <= 0
                    || Long.bitCount(op.getBinaryBit()) != 1 || op.getInheritMask() == null
                    || !typeCodes.containsKey(op.getResourceType())
                    || (Objects.equals(op.getResourceType(), typeValues.get("API")) && "ACCESS".equals(op.getCode()))) {
                    throw invalid("业务准入操作不存在、损坏或为 API:ACCESS");
                }
                row.setRequiredOperationId(op.getId());
            }
            row.setMaintainSource(source.name());
        }
        // 全批校验成功后才写；入口事务保证数据库异常也整批回滚。
        for (Write write : writes) {
            ResourceApiMapping row = write.mapping();
            row.setUpdatedAt(LocalDateTime.now());
            if (row.getId() == null) {
                if (row.getCreatedAt() == null) row.setCreatedAt(row.getUpdatedAt());
                row.setDeleteFlag(0L);
                if (row.getMatchOrder() == null) row.setMatchOrder(0);
                if (row.getEnabled() == null) row.setEnabled(true);
                mappings.insert(row);
            } else {
                ResourceApiMapping patch = UpdateEntity.of(ResourceApiMapping.class);
                patch.setId(row.getId());
                patch.setHttpMethod(row.getHttpMethod());
                patch.setPathPattern(row.getPathPattern());
                patch.setMatchOrder(row.getMatchOrder());
                patch.setEnabled(row.getEnabled());
                patch.setExtra(row.getExtra());
                patch.setRequiredOperationId(row.getRequiredOperationId());
                patch.setMaintainSource(row.getMaintainSource());
                // 服务凭证同步（operatorId=null）不显式写列——set(null) 会把库内 updated_by 抹空
                if (row.getUpdatedBy() != null) {
                    patch.setUpdatedBy(row.getUpdatedBy());
                }
                patch.setUpdatedAt(row.getUpdatedAt());
                mappings.update(patch);
            }
        }
        // T-ACCESS-059：映射写入完成同事务递增配置代次（准入快照构建期自一致校验消费；
        // 手工/同步/bootstrap 三来源共用本入口，单点覆盖）
        services.incrementConfigGeneration(tenantId, serviceCode);
    }

    private BizException invalid(String message) {
        return new BizException(AccessErrorCode.ADMISSION_CONFIG_FAULT.getCode(), message);
    }

    private BizException conflict() {
        return new BizException(AccessErrorCode.RESOURCE_STATE_CONFLICT.getCode(), "映射由其他来源维护；请通过原维护入口修改");
    }
}
