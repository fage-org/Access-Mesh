package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 资源类型级所有权守卫（T-PERM-052 定案 2026-09-05：类型级所有权取代行级判定）。
 * <p>
 * 每个 resource_type 类型声明唯一所有权模式，约定键位于 {@code type_definition.extra}：
 * {@code managedMode}（MANAGED=管理面维护，缺省值 / SYNC=外部同步维护）与
 * {@code syncSourceService}（mode=SYNC 时必填，声明来源服务）。判定规则：
 * </p>
 * <ul>
 *   <li>外部 {@code resource-entity/sync|full-sync}：类型必须声明 SYNC 且来源==调用服务身份；</li>
 *   <li>管理面 {@code resource-entity/create|batch-create|update|move|remove}（含级联删除的
 *       后代全集）：目标类型声明 SYNC 时一律拒绝（20055）；</li>
 *   <li>声明变更：类型下存在有效资源行时 managedMode/syncSourceService 有效值不得变更（20056，
 *       含删除键隐式切回 MANAGED）。</li>
 * </ul>
 * <p>
 * 保存边界（type-definition create/update）由 {@link #validateExtraDeclaration} 校验结构，
 * 防止合法 JSON 但错误结构（拼写错误键/非法值/缺来源）静默落库后在运行时表现为 MANAGED——
 * 对齐 SyncTypeGuard.validateSyncTypesExtra 先例。extra 损坏时读取侧按 MANAGED 处理
 * （管理面可写、外部同步拒绝，fail-closed 方向）并记 WARN。
 * </p>
 */
@Component
public class ResourceTypeOwnershipGuard {

    private static final Logger log = LoggerFactory.getLogger(ResourceTypeOwnershipGuard.class);

    public static final String TYPE_KEY_RESOURCE = "resource_type";
    public static final String EXTRA_KEY_MANAGED_MODE = "managedMode";
    public static final String EXTRA_KEY_SYNC_SOURCE_SERVICE = "syncSourceService";
    public static final String MODE_MANAGED = "MANAGED";
    public static final String MODE_SYNC = "SYNC";

    private final TypeDefinitionMapper typeDefinitionMapper;
    private final ServiceConfigMapper serviceConfigMapper;
    private final ResourceEntityDomainService resourceEntityDomainService;
    private final ObjectMapper objectMapper;

    public ResourceTypeOwnershipGuard(TypeDefinitionMapper typeDefinitionMapper,
                                      ServiceConfigMapper serviceConfigMapper,
                                      ResourceEntityDomainService resourceEntityDomainService,
                                      ObjectMapper objectMapper) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.serviceConfigMapper = serviceConfigMapper;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.objectMapper = objectMapper;
    }

    /**
     * 类型的有效所有权声明。缺省（无键/无 extra）= MANAGED。
     */
    public record Ownership(String managedMode, String syncSourceService) {

        public static final Ownership MANAGED = new Ownership(MODE_MANAGED, null);

        /** 本类型是否声明为由 {@code sourceService} 同步维护（mode=SYNC 且来源匹配）。 */
        public boolean syncOwnedBy(String sourceService) {
            return MODE_SYNC.equals(managedMode)
                    && syncSourceService != null && syncSourceService.equals(sourceService);
        }
    }

    /**
     * 解析 extra 中的所有权声明。键缺失/extra 为空 = MANAGED；JSON 损坏按 MANAGED 处理
     * （外部同步拒绝、管理面可写，fail-closed 方向）并记 WARN。
     */
    public Ownership parseOwnership(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return Ownership.MANAGED;
        }
        try {
            JsonNode root = objectMapper.readTree(extraJson);
            JsonNode mode = root.get(EXTRA_KEY_MANAGED_MODE);
            if (mode == null || mode.isNull()) {
                return Ownership.MANAGED;
            }
            if (!mode.isTextual()) {
                log.warn("resource type ownership: {} is not textual, fallback MANAGED: {}",
                        EXTRA_KEY_MANAGED_MODE, extraJson);
                return Ownership.MANAGED;
            }
            JsonNode source = root.get(EXTRA_KEY_SYNC_SOURCE_SERVICE);
            String sourceText = source != null && source.isTextual() ? source.asText() : null;
            return new Ownership(mode.asText(), sourceText);
        } catch (Exception e) {
            log.warn("resource type ownership: invalid extra json, fallback MANAGED: {}", extraJson, e);
            return Ownership.MANAGED;
        }
    }

    /**
     * 读取类型（tenant + resource_type + typeCode，仅有效行）的所有权声明。
     *
     * @return 声明；类型不存在返回 {@code null}
     */
    public Ownership resolveTypeOwnership(Long tenantId, String resourceTypeCode) {
        TypeDefinition td = typeDefinitionMapper.selectByTypeKeyAndCode(
                tenantId, TYPE_KEY_RESOURCE, resourceTypeCode);
        return td == null ? null : parseOwnership(td.getExtra());
    }

    /**
     * 保存边界校验（type-definition create/update 写入口）：
     * managedMode/syncSourceService 仅允许出现在 {@code type_key=resource_type} 的 extra 上；
     * mode 值域 {MANAGED, SYNC}；SYNC 必须携带非空白来源且来源须为已注册有效服务。
     *
     * @throws IllegalArgumentException 结构不合法（调用方转为 BizException 20044 返回，
     *                                  对齐 SyncTypeGuard.validateSyncTypesExtra 先例）
     */
    public void validateExtraDeclaration(Long tenantId, String typeKey, String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(extraJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("extra 不是合法 JSON", e);
        }
        JsonNode mode = root.get(EXTRA_KEY_MANAGED_MODE);
        JsonNode source = root.get(EXTRA_KEY_SYNC_SOURCE_SERVICE);
        if (mode == null && source == null) {
            return;
        }
        if (!TYPE_KEY_RESOURCE.equals(typeKey)) {
            throw new IllegalArgumentException(
                    EXTRA_KEY_MANAGED_MODE + "/" + EXTRA_KEY_SYNC_SOURCE_SERVICE
                            + " 仅适用于 type_key=resource_type 的类型");
        }
        String modeText = null;
        if (mode != null && !mode.isNull()) {
            if (!mode.isTextual()) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_MANAGED_MODE + " 必须为字符串");
            }
            modeText = mode.asText();
            if (!MODE_MANAGED.equals(modeText) && !MODE_SYNC.equals(modeText)) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_MANAGED_MODE
                        + " 仅允许 " + MODE_MANAGED + "/" + MODE_SYNC + ": " + modeText);
            }
        }
        String sourceText = null;
        if (source != null && !source.isNull()) {
            if (!source.isTextual()) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE + " 必须为字符串");
            }
            sourceText = source.asText().trim();
            if (sourceText.isEmpty()) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE + " 不能为空白");
            }
            if (sourceText.length() > 64) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE + " 长度超过 64");
            }
        }
        if (sourceText != null && !MODE_SYNC.equals(modeText)) {
            throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE
                    + " 仅在 " + EXTRA_KEY_MANAGED_MODE + "=" + MODE_SYNC + " 时允许携带");
        }
        if (MODE_SYNC.equals(modeText)) {
            if (sourceText == null) {
                throw new IllegalArgumentException(EXTRA_KEY_MANAGED_MODE + "=" + MODE_SYNC
                        + " 必须携带 " + EXTRA_KEY_SYNC_SOURCE_SERVICE);
            }
            if (serviceConfigMapper.selectByTenantAndServiceCode(tenantId, sourceText) == null) {
                throw new IllegalArgumentException("来源服务未注册: " + sourceText);
            }
        }
    }

    /**
     * 声明变更守卫（type-definition update）：resource_type 类型的所有权声明有效值变更
     * （含删除键隐式切回 MANAGED）且类型下仍存在有效资源行时拒绝（20056）。
     * 类型无行或声明未变时放行。
     */
    public void rejectIfDeclarationChangeBlocked(Long tenantId, TypeDefinition existingType, String newExtraJson) {
        if (!TYPE_KEY_RESOURCE.equals(existingType.getTypeKey())) {
            return;
        }
        Ownership oldDeclaration = parseOwnership(existingType.getExtra());
        Ownership newDeclaration = parseOwnership(newExtraJson);
        if (Objects.equals(oldDeclaration, newDeclaration)) {
            return;
        }
        if (resourceEntityDomainService.hasValidRowsOfType(tenantId, existingType.getTypeValue())) {
            throw new BizException(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(),
                    "类型所有权声明不可变更（类型下存在有效资源行）: " + existingType.getTypeCode()
                            + " " + oldDeclaration + " -> " + newDeclaration);
        }
    }

    /**
     * 管理面写入口守卫（create/update/move 单类型）：类型声明 SYNC 时拒绝（20055）。
     * 类型不存在不在此拦截（由既有类型解析/存在性校验负责）。
     */
    public void rejectIfSyncManagedType(Long tenantId, String resourceTypeCode) {
        Ownership ownership = resolveTypeOwnership(tenantId, resourceTypeCode);
        rejectIfSyncOwned(ownership, resourceTypeCode);
    }

    /**
     * 管理面写入口守卫（batch-create 按类型码集合一次批量查询，N+1 禁令）：
     * 任一类型声明 SYNC 时拒绝（20055）。
     */
    public void rejectIfAnySyncManagedByCodes(Long tenantId, Collection<String> resourceTypeCodes) {
        if (resourceTypeCodes == null || resourceTypeCodes.isEmpty()) {
            return;
        }
        List<TypeDefinition> types = typeDefinitionMapper.selectByTypeKeyAndCodes(
                tenantId, TYPE_KEY_RESOURCE, new java.util.LinkedHashSet<>(resourceTypeCodes));
        for (TypeDefinition td : types) {
            rejectIfSyncOwned(parseOwnership(td.getExtra()), td.getTypeCode());
        }
    }

    /**
     * 管理面级联删除守卫（remove 后代全集按类型值一次批量查询，N+1 禁令）：
     * 任一类型声明 SYNC 时拒绝（20055）。跨类型父子边（sync 通道允许）下，
     * MANAGED 根的子树可能含 SYNC 类型后代，删除前必须对全部待删 id 的类型判定。
     */
    public void rejectIfAnySyncManagedByValues(Long tenantId, Collection<Integer> resourceTypeValues) {
        if (resourceTypeValues == null || resourceTypeValues.isEmpty()) {
            return;
        }
        List<TypeDefinition> types = typeDefinitionMapper.selectByTypeKeyAndValues(
                tenantId, TYPE_KEY_RESOURCE, new java.util.LinkedHashSet<>(resourceTypeValues));
        for (TypeDefinition td : types) {
            rejectIfSyncOwned(parseOwnership(td.getExtra()), td.getTypeCode());
        }
    }

    private static void rejectIfSyncOwned(Ownership ownership, String resourceTypeCode) {
        if (ownership != null && MODE_SYNC.equals(ownership.managedMode())) {
            throw new BizException(PermissionErrorCode.RESOURCE_EXTERNALLY_MAINTAINED.getCode(),
                    "资源由外部来源维护，请到来源系统操作: resourceTypeCode=" + resourceTypeCode
                            + ", syncSourceService=" + ownership.syncSourceService());
        }
    }
}
