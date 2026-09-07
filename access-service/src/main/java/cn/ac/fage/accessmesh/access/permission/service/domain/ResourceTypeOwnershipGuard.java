package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
 *   <li>声明变更：系统预置类型（is_system=true）所有权声明一律不可变更（20056，
 *       codex 二轮复评 P1-1 定案——事实链路类型翻转后事实写入方照旧写即双 writer）；
 *       自定义类型在类型下存在有效资源行时有效值不得变更（20056，含删除键隐式切回
 *       MANAGED）。</li>
 *   <li>内部来源声明（2026-09-05 补充定案；T-ADMIN-025 增 ADMIN_FILE、T-PERM-051 增
 *       TYPE_DEFINITION）：事实链路类型（USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION）
 *       由种子声明 SYNC + syncSourceService=access-service——外部同步一律拒绝（来源不匹配）、
 *       管理面资源 CRUD 一律 20055（行由用户/组织/菜单/角色管理、文件夹预置/惰性登记、
 *       类型定义管理自动维护），收编原类型保留清单与行级 owner=access-service
 *       投影防线两套旧机制。</li>
 * </ul>
 * <p>
 * 保存边界（type-definition create/update）由 {@link #validateExtraDeclaration} 校验结构，
 * 防止合法 JSON 但错误结构（已知键非法值/缺来源/显式 null）静默落库后在运行时表现为
 * MANAGED——对齐 SyncTypeGuard.validateSyncTypesExtra 先例。extra 是开放扩展位：未知键
 * 不视为声明（拼错键=无声明，按缺省 MANAGED，与合法扩展键不可区分故不拒绝）。extra 损坏时
 * 读取侧按 MANAGED 处理：对外部同步 fail-closed（拒绝）、对管理面 fail-open（可写=可恢复
 * 方向，管理员可清理损坏声明后重新声明，WARN 日志定位）。
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
    /** 来源编码上限（对齐 service_config.service_code / sync_metadata.source_service 的 128 列宽，
     * 保存校验与读取回退共用——codex 五轮复评 P1：读取侧漏判超长来源会重现零 writer 锁死） */
    private static final int SOURCE_SERVICE_MAX_LENGTH = 128;

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
     * 解析 extra 中的所有权声明。键缺失/extra 为空 = MANAGED；结构损坏（非法 JSON、已知键
     * 显式 null、仅来源无 mode、mode 非文本或值域外、SYNC 缺失/非文本/空白/含首尾空白/超长
     * 来源、MANAGED 携带来源）一律 WARN 后按 MANAGED 处理（codex 四/五轮复评 P1/P2：读取侧
     * 执行与保存边界同构的结构校验——仅校验 JSON 合法性与 mode 文本性时，{@code SYNC+非文本
     * 来源}或超长来源会解析成无人可满足的 SYNC：外部同步被拒、管理面 20055，而 20056 行数
     * 守卫又阻止修复，存量类型被锁成零 writer。回退 MANAGED = fail-closed + 可恢复方向，
     * 与类注释/契约承诺一致）。
     */
    public Ownership parseOwnership(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return Ownership.MANAGED;
        }
        try {
            JsonNode root = objectMapper.readTree(extraJson);
            JsonNode mode = root.get(EXTRA_KEY_MANAGED_MODE);
            JsonNode source = root.get(EXTRA_KEY_SYNC_SOURCE_SERVICE);
            // codex 五轮复评 P2：与保存侧同构——已知键显式 null 是保存侧拒绝形态，读取侧记 WARN 回退
            if ((mode != null && mode.isNull()) || (source != null && source.isNull())) {
                log.warn("resource type ownership: explicit null on known keys, fallback MANAGED: {}", extraJson);
                return Ownership.MANAGED;
            }
            if (mode == null) {
                // 仅携带来源无 mode：保存侧拒绝（来源仅随 SYNC 声明），读取侧记 WARN 回退
                if (source != null) {
                    log.warn("resource type ownership: {} carried without {}, fallback MANAGED: {}",
                            EXTRA_KEY_SYNC_SOURCE_SERVICE, EXTRA_KEY_MANAGED_MODE, extraJson);
                }
                return Ownership.MANAGED;
            }
            if (!mode.isTextual()) {
                log.warn("resource type ownership: {} is not textual, fallback MANAGED: {}",
                        EXTRA_KEY_MANAGED_MODE, extraJson);
                return Ownership.MANAGED;
            }
            String modeText = mode.asText();
            if (!MODE_MANAGED.equals(modeText) && !MODE_SYNC.equals(modeText)) {
                log.warn("resource type ownership: invalid {} value, fallback MANAGED: {}",
                        EXTRA_KEY_MANAGED_MODE, extraJson);
                return Ownership.MANAGED;
            }
            if (MODE_MANAGED.equals(modeText)) {
                if (source != null) {
                    log.warn("resource type ownership: MANAGED declaration carries {}, fallback MANAGED: {}",
                            EXTRA_KEY_SYNC_SOURCE_SERVICE, extraJson);
                }
                return Ownership.MANAGED;
            }
            String sourceText = source != null && source.isTextual() ? source.asText() : null;
            if (sourceText == null || sourceText.isBlank() || !sourceText.equals(sourceText.trim())) {
                log.warn("resource type ownership: SYNC declaration missing/blank/padded source, "
                        + "fallback MANAGED: {}", extraJson);
                return Ownership.MANAGED;
            }
            if (sourceText.length() > SOURCE_SERVICE_MAX_LENGTH) {
                // codex 五轮复评 P1：超长来源不可能匹配任何注册服务（service_code 列宽 128）——
                // 视为有效 SYNC 会重现「同步拒+管理面 20055+20056 阻修复」零 writer 锁死
                log.warn("resource type ownership: SYNC source exceeds {} chars, fallback MANAGED: {}",
                        SOURCE_SERVICE_MAX_LENGTH, extraJson);
                return Ownership.MANAGED;
            }
            return new Ownership(modeText, sourceText);
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
     * sync/full-sync 入口门禁（评审 P1 补强，2026-09-05）：类型须声明 SYNC 且
     * syncSourceService==调用服务身份，且调用服务在 service_config 注册、未软删、
     * status=1 启用——对齐主体/角色/user_role 通道白名单语义（服务停用/注销即四通道
     * 一起断）。内部来源 access-service 天然不可达（rejectInternalSourceService 先拒
     * 外部冒充，类型声明 SYNC+access-service 对任何外部来源均不匹配），无需豁免分支。
     * 真实拒绝原因仅记内部日志（不向调用方泄露判定明细，SyncTypeGuard 先例）。
     *
     * @return true=放行；false=拒绝（统一 RESOURCE_TYPE_OWNERSHIP_DENIED）
     */
    public boolean isSyncEntranceAllowed(Long tenantId, String resourceTypeCode, String sourceService) {
        Ownership ownership = resolveTypeOwnership(tenantId, resourceTypeCode);
        if (ownership == null) {
            log.warn("resource type ownership gate: type not found, tenantId={}, typeCode={}",
                    tenantId, resourceTypeCode);
            return false;
        }
        if (!ownership.syncOwnedBy(sourceService)) {
            log.warn("resource type ownership gate: source not type owner, tenantId={}, typeCode={}, "
                    + "declaredSource={}, caller={}", tenantId, resourceTypeCode,
                    ownership.syncSourceService(), sourceService);
            return false;
        }
        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, sourceService);
        if (config == null || (config.getDeleteFlag() != null && config.getDeleteFlag() != 0L)) {
            log.warn("resource type ownership gate: source service not registered or deleted, "
                    + "tenantId={}, serviceCode={}", tenantId, sourceService);
            return false;
        }
        if (!Integer.valueOf(1).equals(config.getStatus())) {
            log.warn("resource type ownership gate: source service disabled, tenantId={}, serviceCode={}",
                    tenantId, sourceService);
            return false;
        }
        return true;
    }

    /**
     * 保存边界校验（type-definition create/update 写入口）：
     * managedMode/syncSourceService 仅允许出现在 {@code type_key=resource_type} 的 extra 上；
     * mode 值域 {MANAGED, SYNC}；SYNC 必须携带非空白来源（禁止首尾空白——校验 trim 后与落库
     * 原值不一致会造出无人可同步的锁死类型）且来源须为已注册、未软删、status=1 的服务——与
     * 运行时入口 {@link #isSyncEntranceAllowed} 同规则；保留内部来源（access-service 之外，
     * 如 admin-service）拒绝。唯一豁免：{@code syncSourceService=access-service}（内部来源
     * 声明，收编事实链路类型，仅 is_system=true 的系统预置类型可声明，防止自定义类型锁死成
     * 无人写入的孤岛）；API 类型禁止声明 SYNC（service-config 接口声明通道是其事实 writer，
     * 双 writer 口径会破坏类型级单来源不变量）。已知键显式 null 拒绝（清除声明=删除键）。
     *
     * @param typeCode     目标类型编码（API 类型拒绝 SYNC 声明用）
     * @param isSystemType 目标类型是否系统预置（create 恒 false——is_system 不可由 API 创建）
     * @throws IllegalArgumentException 结构不合法（调用方转为 BizException 20044 返回，
     *                                  对齐 SyncTypeGuard.validateSyncTypesExtra 先例）
     */
    public void validateExtraDeclaration(Long tenantId, String typeKey, String typeCode,
                                         String extraJson, boolean isSystemType) {
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
        // codex 二轮复评定案（2026-09-05）：已知键显式 null 拒绝——null 与「清除声明=删除键」
        // 语义歧义（落库后运行时按缺省 MANAGED 判定），fail-closed 在保存边界拦下
        if ((mode != null && mode.isNull()) || (source != null && source.isNull())) {
            throw new IllegalArgumentException(EXTRA_KEY_MANAGED_MODE + "/" + EXTRA_KEY_SYNC_SOURCE_SERVICE
                    + " 不接受显式 null（清除声明请删除键）");
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
        if (MODE_SYNC.equals(modeText) && ResourceTypeCode.API.equals(typeCode)) {
            throw new IllegalArgumentException("API 类型由 service-config 接口声明通道维护，禁止声明 SYNC");
        }
        String sourceText = null;
        if (source != null && !source.isNull()) {
            if (!source.isTextual()) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE + " 必须为字符串");
            }
            String raw = source.asText();
            sourceText = raw.trim();
            if (sourceText.isEmpty()) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE + " 不能为空白");
            }
            if (!sourceText.equals(raw)) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE
                        + " 不能含首尾空白（校验按 trim 值、运行时按原值精确匹配，会造出无人可同步的类型）");
            }
            // 长度对齐 service_config.service_code / sync_metadata.source_service 的 128 列宽
            // （codex 复评 P2：65~128 字符的合法注册服务不应被声明校验拒绝）
            if (sourceText.length() > SOURCE_SERVICE_MAX_LENGTH) {
                throw new IllegalArgumentException("extra." + EXTRA_KEY_SYNC_SOURCE_SERVICE
                        + " 长度超过 " + SOURCE_SERVICE_MAX_LENGTH);
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
            // 内部来源豁免：access-service 不是 service_config 注册行（rejectInternalSourceService
            // 同时禁止外部 sync 冒充），仅系统预置类型可声明——事实链路类型（含 T-ADMIN-025 增的 ADMIN_FILE）
            if (LocalProjectionOwner.SERVICE_CODE.equals(sourceText)) {
                if (!isSystemType) {
                    throw new IllegalArgumentException("内部来源 " + LocalProjectionOwner.SERVICE_CODE
                            + " 仅系统预置类型可声明");
                }
                return;
            }
            // codex 二轮复评 P2：保留内部来源（admin-service 等退役内部同步身份）一律拒绝——
            // 运行时 rejectInternalSourceService 拒绝其冒充，声明它=保存出无人可写的锁死类型
            if (LocalProjectionOwner.isInternalSourceService(sourceText)) {
                throw new IllegalArgumentException("保留内部来源不可声明为同步来源: " + sourceText);
            }
            // codex 二轮复评 P2：保存侧与运行时入口（isSyncEntranceAllowed）同规则——
            // 已注册、未软删且 status=1 启用；仅查注册非空会保存出「管理面 20055、
            // 同步入口 status 拒绝」的无人可写类型
            ServiceConfig sourceService = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, sourceText);
            if (sourceService == null || (sourceService.getDeleteFlag() != null && sourceService.getDeleteFlag() != 0L)
                    || !Integer.valueOf(1).equals(sourceService.getStatus())) {
                throw new IllegalArgumentException("来源服务未注册或未启用: " + sourceText);
            }
        }
    }

    /**
     * 声明变更守卫（type-definition update）：resource_type 类型的所有权声明有效值变更
     * （含删除键隐式切回 MANAGED）时——系统预置类型（is_system）一律拒绝；自定义类型在
     * 类型下仍存在有效资源行时拒绝（20056）。声明未变时放行。
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
        // codex 二轮复评 P1-1 定案（2026-09-05）：系统预置类型所有权声明钉死——事实链路类型
        // （事实链路类型=SYNC+access-service）即使零行翻转为 MANAGED/外部来源，事实链路
        // 照旧无条件投影写入即成双 writer（顺序性破坏，无需并发）；先例：is_system 类型禁止删除
        if (Boolean.TRUE.equals(existingType.getIsSystem())) {
            throw new BizException(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(),
                    "系统预置类型所有权声明不可变更: " + existingType.getTypeCode()
                            + " " + oldDeclaration + " -> " + newDeclaration);
        }
        if (resourceEntityDomainService.hasValidRowsOfType(tenantId, existingType.getTypeValue())) {
            throw new BizException(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(),
                    "类型所有权声明不可变更（类型下存在有效资源行）: " + existingType.getTypeCode()
                            + " " + oldDeclaration + " -> " + newDeclaration);
        }
    }

    /**
     * 管理面写入口守卫（create/update/move 单类型）：类型声明 SYNC 时拒绝（20055）。
     * 类型不存在不在此拦截（由调用方存在性校验负责）。
     * <p>
     * codex 三轮复评 P1-2（写路径权威化）：门禁本身即库内直查 type_definition，通过时返回
     * 类型权威行供 create 直接消费 typeValue——写路径不再经 TYPE_VALUE 类型缓存（10s L2 且
     * 历史上删除类型无失效，陈旧缓存会产出引用已删类型值的孤儿资源行）。
     *
     * @return 类型权威行；类型不存在返回 {@code null}
     */
    public TypeDefinition rejectIfSyncManagedType(Long tenantId, String resourceTypeCode) {
        TypeDefinition td = typeDefinitionMapper.selectByTypeKeyAndCode(
                tenantId, TYPE_KEY_RESOURCE, resourceTypeCode);
        if (td != null) {
            rejectIfSyncOwned(parseOwnership(td.getExtra()), td.getTypeCode());
        }
        return td;
    }

    /**
     * 管理面写入口守卫（batch-create 按类型码集合一次批量查询，N+1 禁令）：
     * 任一类型声明 SYNC 时拒绝（20055）。返回码→类型权威行映射供批量创建直接消费
     * typeValue（同 {@link #rejectIfSyncManagedType} 写路径权威化口径）。
     */
    public Map<String, TypeDefinition> rejectIfAnySyncManagedByCodes(Long tenantId, Collection<String> resourceTypeCodes) {
        if (resourceTypeCodes == null || resourceTypeCodes.isEmpty()) {
            return Map.of();
        }
        List<TypeDefinition> types = typeDefinitionMapper.selectByTypeKeyAndCodes(
                tenantId, TYPE_KEY_RESOURCE, new LinkedHashSet<>(resourceTypeCodes));
        for (TypeDefinition td : types) {
            rejectIfSyncOwned(parseOwnership(td.getExtra()), td.getTypeCode());
        }
        return types.stream().collect(Collectors.toMap(TypeDefinition::getTypeCode, td -> td));
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
                tenantId, TYPE_KEY_RESOURCE, new LinkedHashSet<>(resourceTypeValues));
        for (TypeDefinition td : types) {
            rejectIfSyncOwned(parseOwnership(td.getExtra()), td.getTypeCode());
        }
    }

    private static void rejectIfSyncOwned(Ownership ownership, String resourceTypeCode) {
        if (ownership != null && MODE_SYNC.equals(ownership.managedMode())) {
            // 内部来源（事实链路类型：USER/ORG/MENU/ROLE、ADMIN_FILE 文件夹（T-ADMIN-025）及
            // TYPE_DEFINITION 类型定义实例投影（T-PERM-051））
            if (LocalProjectionOwner.SERVICE_CODE.equals(ownership.syncSourceService())) {
                throw new BizException(PermissionErrorCode.RESOURCE_EXTERNALLY_MAINTAINED.getCode(),
                        "资源由系统事实链路维护（用户/组织/菜单/角色/类型定义管理、文件上传/预置），资源管理面只读: resourceTypeCode="
                                + resourceTypeCode);
            }
            throw new BizException(PermissionErrorCode.RESOURCE_EXTERNALLY_MAINTAINED.getCode(),
                    "资源由外部来源维护，请到来源系统操作: resourceTypeCode=" + resourceTypeCode
                            + ", syncSourceService=" + ownership.syncSourceService());
        }
    }
}
