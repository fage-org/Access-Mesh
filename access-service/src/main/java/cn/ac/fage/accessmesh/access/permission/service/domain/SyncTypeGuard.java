package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 服务-类型同步白名单守卫。
 * <p>
 * 外部 sync/full-sync 只能写入本服务在 {@code service_config.extra.syncTypes} 中声明的类型命名空间，
 * 任意已认证服务不得使用其他服务的类型空间。校验使用经过认证的服务身份查询配置（不重新信任 payload）；
 * 服务不存在/已删除/禁用/配置缺失或损坏/类型未声明统一按无权限处理（SECURITY_DENIED），
 * 内部日志记录真实原因，不向调用方返回白名单明细。
 * </p>
 * <p>
 * extra 约定：{@code {"syncTypes": {"subjectTypeCodes": [...], "roleTypeCodes": [...],
 * "resourceTypeCodes": [...], "sourceTypes": [...]}}}。缺失分类按「该分类无任何权限」处理，
 * 不做大小写转换、不支持通配/正则/前缀/继承；保留键拒绝（LocalProjectionGuard）作为独立纵深防护继续生效。
 * </p>
 * <p>
 * 配置约定：上线前先为各同步服务补齐 syncTypes 声明，再部署严格校验（fail-closed 发布顺序，
 * 无长期宽松分支）。保存边界由 {@link #validateSyncTypesExtra} 校验结构，防止合法 JSON 但错误结构
 * 被保存后在运行时表现为空白名单。
 * </p>
 */
@Component
public class SyncTypeGuard {

    private static final Logger log = LoggerFactory.getLogger(SyncTypeGuard.class);

    private static final String SYNC_TYPES_KEY = "syncTypes";
    private static final String KEY_SUBJECT_TYPES = "subjectTypeCodes";
    private static final String KEY_ROLE_TYPES = "roleTypeCodes";
    private static final String KEY_RESOURCE_TYPES = "resourceTypeCodes";
    private static final String KEY_SOURCE_TYPES = "sourceTypes";

    private final ServiceConfigMapper serviceConfigMapper;
    private final ObjectMapper objectMapper;

    public SyncTypeGuard(ServiceConfigMapper serviceConfigMapper, ObjectMapper objectMapper) {
        this.serviceConfigMapper = serviceConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验请求写入事实的类型是否全部属于该服务声明的同步类型白名单。
     * <p>
     * {@code requested} 中为空的分类表示该链路无对应类型要求（如父资源/父角色等仅引用类型，
     * 由依赖解析负责校验），不做白名单比对。
     * </p>
     *
     * @param tenantId                租户 ID
     * @param authenticatedServiceCode 已验证服务身份（凭证通过后绑定的 X-Service-Code）
     * @param requested                本次写入事实涉及的类型集合
     * @return true=允许；false=拒绝（服务未注册/禁用/配置缺失损坏/类型未声明）
     */
    public boolean validate(Long tenantId, String authenticatedServiceCode, SyncTypes requested) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, authenticatedServiceCode);
        if (config == null || config.getDeleteFlag() != null && config.getDeleteFlag() != 0L) {
            log.warn("sync type guard: service not registered or deleted, tenantId={}, serviceCode={}",
                    tenantId, authenticatedServiceCode);
            return false;
        }
        if (!Integer.valueOf(1).equals(config.getStatus())) {
            log.warn("sync type guard: service disabled, tenantId={}, serviceCode={}",
                    tenantId, authenticatedServiceCode);
            return false;
        }
        SyncTypes declared = parseDeclared(config.getExtra());
        boolean allowed = declared.covers(requested);
        if (!allowed) {
            log.warn("sync type guard: requested types not declared, tenantId={}, serviceCode={}, requested={}",
                    tenantId, authenticatedServiceCode, requested);
        }
        return allowed;
    }

    /**
     * 保存边界校验（service-config/save 写入入口）：extra 含 {@code syncTypes} 时必须为对象，
     * 四个分类如存在必须为字符串数组，拒绝 null、空白项与非字符串元素。
     * <p>
     * 配置结构损坏虽不构成安全风险，但会在运行时被解释为空白名单导致同步全部 SECURITY_DENIED，
     * 属难排查的运行故障，应在配置写入时尽早暴露。
     * </p>
     *
     * @throws IllegalArgumentException 结构不合法（调用方转为 BizException 返回）
     */
    public void validateSyncTypesExtra(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(extraJson);
            JsonNode syncTypes = root.get(SYNC_TYPES_KEY);
            if (syncTypes == null) {
                return;
            }
            if (!syncTypes.isObject()) {
                throw new IllegalArgumentException("extra.syncTypes 必须为对象");
            }
            requireStringArray(syncTypes, KEY_SUBJECT_TYPES);
            requireStringArray(syncTypes, KEY_ROLE_TYPES);
            requireStringArray(syncTypes, KEY_RESOURCE_TYPES);
            requireStringArray(syncTypes, KEY_SOURCE_TYPES);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("extra 不是合法 JSON", e);
        }
    }

    private void requireStringArray(JsonNode syncTypes, String field) {
        JsonNode arr = syncTypes.get(field);
        if (arr == null) {
            return;
        }
        if (!arr.isArray()) {
            throw new IllegalArgumentException("extra.syncTypes." + field + " 必须为字符串数组");
        }
        for (JsonNode v : arr) {
            if (!v.isTextual() || v.asText().isBlank()) {
                throw new IllegalArgumentException("extra.syncTypes." + field + " 只允许非空白字符串元素");
            }
        }
    }

    /** 解析 extra.syncTypes；缺失/损坏/分类缺失一律按无权限（NONE）处理。 */
    private SyncTypes parseDeclared(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return SyncTypes.NONE;
        }
        try {
            JsonNode syncTypes = objectMapper.readTree(extraJson).path(SYNC_TYPES_KEY);
            if (syncTypes.isMissingNode() || syncTypes.isNull()) {
                return SyncTypes.NONE;
            }
            return new SyncTypes(
                    stringSet(syncTypes, KEY_SUBJECT_TYPES),
                    stringSet(syncTypes, KEY_ROLE_TYPES),
                    stringSet(syncTypes, KEY_RESOURCE_TYPES),
                    stringSet(syncTypes, KEY_SOURCE_TYPES));
        } catch (Exception e) {
            log.warn("sync type guard: invalid extra json, service extra rejected", e);
            return SyncTypes.NONE;
        }
    }

    private static Set<String> stringSet(JsonNode node, String field) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (JsonNode v : arr) {
            if (v.isTextual() && !v.asText().isBlank()) {
                result.add(v.asText().trim());
            }
        }
        return result;
    }

    /**
     * 请求写入事实的类型集合；空集合表示该分类无类型要求。
     */
    public record SyncTypes(Set<String> subjectTypeCodes, Set<String> roleTypeCodes,
                            Set<String> resourceTypeCodes, Set<String> sourceTypes) {

        /** 无任何类型权限（配置缺失/损坏时的声明结果）。 */
        public static final SyncTypes NONE = new SyncTypes(Set.of(), Set.of(), Set.of(), Set.of());

        public static SyncTypes subject(String subjectTypeCode) {
            return new SyncTypes(Set.of(subjectTypeCode), Set.of(), Set.of(), Set.of());
        }

        public static SyncTypes role(String roleTypeCode) {
            return new SyncTypes(Set.of(), Set.of(roleTypeCode), Set.of(), Set.of());
        }

        public static SyncTypes resource(String resourceTypeCode) {
            return new SyncTypes(Set.of(), Set.of(), Set.of(resourceTypeCode), Set.of());
        }

        public static SyncTypes userRole(Set<String> subjectTypeCodes, Set<String> roleTypeCodes,
                                         Set<String> sourceTypes) {
            return new SyncTypes(subjectTypeCodes, roleTypeCodes, Set.of(), sourceTypes);
        }

        boolean isEmpty() {
            return subjectTypeCodes.isEmpty() && roleTypeCodes.isEmpty()
                    && resourceTypeCodes.isEmpty() && sourceTypes.isEmpty();
        }

        /** 请求的每个非空分类必须全部包含于声明的对应分类（精确匹配，不做通配/继承）。 */
        boolean covers(SyncTypes requested) {
            return containsAll(subjectTypeCodes, requested.subjectTypeCodes)
                    && containsAll(roleTypeCodes, requested.roleTypeCodes)
                    && containsAll(resourceTypeCodes, requested.resourceTypeCodes)
                    && containsAll(sourceTypes, requested.sourceTypes);
        }

        private static boolean containsAll(Set<String> declared, Set<String> requested) {
            return requested.isEmpty() || declared.containsAll(requested);
        }
    }
}
