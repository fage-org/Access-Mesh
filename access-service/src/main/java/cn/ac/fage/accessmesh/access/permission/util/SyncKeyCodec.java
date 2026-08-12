package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;

/**
 * 同步键编码工具
 * <p>
 * 严格遵循 docs/design/permission-center/api-contract.md §6.2.2.4 业务键与 scopeKey 规范：
 * <ul>
 *   <li>使用 {@code key=value&key=value} 的有序参数串；</li>
 *   <li>参数名 camelCase，顺序由本节样例固定，不省略缺省字段；</li>
 *   <li>参数值进行 URL percent-encoding，例如 {@code relationKey=ORG:2001} 编码为 {@code relationKey=ORG%3A2001}；</li>
 *   <li>key 字符串不包含 {@code tenantId/sourceService/entityKind}，由表字段或 scope 单独承载；</li>
 *   <li>{@code sync_key} 使用 {@code sourceService|entityKind|businessKey} 的来源内稳定定位串；</li>
 *   <li>关系库同时保存原文和 SHA-256 lowercase hex，hash 列用于唯一约束与高频查询。</li>
 * </ul>
 * 本类只负责字符串编码，不感知任何业务上下文（不持有 tenantId/sourceService）。
 * </p>
 */
public final class SyncKeyCodec {

    private SyncKeyCodec() {
    }

    /**
     * businessKey 编码：按入参 LinkedHashMap 顺序拼成 percent-encoded 的 {@code k=v&k=v}。
     */
    public static String encodeBusinessKey(LinkedHashMap<String, String> orderedFields) {
        return encodeOrderedMap(orderedFields);
    }

    /**
     * scopeKey 编码：与 businessKey 同算法，区分语义而已。
     */
    public static String encodeScopeKey(LinkedHashMap<String, String> orderedFields) {
        return encodeOrderedMap(orderedFields);
    }

    /**
     * 计算字符串的 SHA-256 lowercase hex（长度 64）。
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("sha256Hex input must not be null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new SystemException(PermissionErrorCode.SYSTEM_INIT_FAILED.getCode(),
                    "SHA-256 algorithm not available", e);
        }
    }

    // ---------------------------------------------------------------------
    // businessKey 静态构造方法（顺序与 api-contract §6.2.2.4 表格一致）
    // ---------------------------------------------------------------------

    /**
     * ABSTRACT_USER businessKey：{@code subjectTypeCode={...}&subjectExternalId={...}}。
     */
    public static String abstractUserBusinessKey(String subjectTypeCode, String subjectExternalId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("subjectTypeCode", subjectTypeCode);
        map.put("subjectExternalId", subjectExternalId);
        return encodeBusinessKey(map);
    }

    /**
     * ABSTRACT_ROLE businessKey：{@code roleTypeCode={...}&roleExternalId={...}}。
     */
    public static String abstractRoleBusinessKey(String roleTypeCode, String roleExternalId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("roleTypeCode", roleTypeCode);
        map.put("roleExternalId", roleExternalId);
        return encodeBusinessKey(map);
    }

    /**
     * RESOURCE_ENTITY businessKey：{@code resourceTypeCode={...}&resourceCode={...}&codeType={...}}。
     */
    public static String resourceEntityBusinessKey(String resourceTypeCode, String resourceCode, String codeType) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("resourceTypeCode", resourceTypeCode);
        map.put("resourceCode", resourceCode);
        map.put("codeType", codeType);
        return encodeBusinessKey(map);
    }

    /**
     * USER_ROLE businessKey：
     * {@code subjectTypeCode={...}&subjectExternalId={...}&roleTypeCode={...}&roleExternalId={...}&relationKey={...}}。
     * <p>
     * {@code relationKey} 入参为业务原文（如 {@code ORG:2001}），编码后会自动转为 {@code ORG%3A2001}。
     * </p>
     */
    public static String userRoleBusinessKey(String subjectTypeCode, String subjectExternalId,
                                             String roleTypeCode, String roleExternalId,
                                             String relationKey) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("subjectTypeCode", subjectTypeCode);
        map.put("subjectExternalId", subjectExternalId);
        map.put("roleTypeCode", roleTypeCode);
        map.put("roleExternalId", roleExternalId);
        map.put("relationKey", relationKey);
        return encodeBusinessKey(map);
    }

    // ---------------------------------------------------------------------
    // scopeKey 静态构造方法（顺序与 api-contract §6.2.2.4 表格一致）
    // ---------------------------------------------------------------------

    /**
     * abstract-user/full-sync scopeKey：{@code subjectTypeCode={...}}。
     */
    public static String abstractUserScopeKey(String subjectTypeCode) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("subjectTypeCode", subjectTypeCode);
        return encodeScopeKey(map);
    }

    /**
     * abstract-role/full-sync scopeKey：{@code roleTypeCode={...}&treeRootExternalId={...}}。
     */
    public static String abstractRoleScopeKey(String roleTypeCode, String treeRootExternalId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("roleTypeCode", roleTypeCode);
        map.put("treeRootExternalId", treeRootExternalId);
        return encodeScopeKey(map);
    }

    /**
     * resource-entity/full-sync scopeKey：{@code resourceTypeCode={...}}。
     */
    public static String resourceEntityScopeKey(String resourceTypeCode) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("resourceTypeCode", resourceTypeCode);
        return encodeScopeKey(map);
    }

    /**
     * user-role/full-sync scopeKey：固定前缀 {@code sourceType=SYS_USER_ORG}，
     * 拼 {@code roleTypeCode={...}&treeRootExternalId={...}}。
     */
    public static String userRoleScopeKey(String roleTypeCode, String treeRootExternalId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("sourceType", "SYS_USER_ORG");
        map.put("roleTypeCode", roleTypeCode);
        map.put("treeRootExternalId", treeRootExternalId);
        return encodeScopeKey(map);
    }

    // ---------------------------------------------------------------------
    // private helpers
    // ---------------------------------------------------------------------

    private static String encodeOrderedMap(LinkedHashMap<String, String> orderedFields) {
        if (orderedFields == null || orderedFields.isEmpty()) {
            throw new IllegalArgumentException("ordered fields must not be empty");
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var entry : orderedFields.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("field name must not be empty");
            }
            if (value == null) {
                throw new IllegalArgumentException("field value must not be null: " + name);
            }
            if (!first) {
                sb.append('&');
            }
            // 字段名按规范使用 camelCase ASCII，无需 encode；值按 RFC3986 风格 percent-encode
            sb.append(name).append('=').append(percentEncode(value));
            first = false;
        }
        return sb.toString();
    }

    /**
     * 对值进行 percent-encoding：基于 {@link URLEncoder}（application/x-www-form-urlencoded），
     * 再把空格 {@code +} 还原为 {@code %20}，使其符合通用 URL percent-encoding 语义。
     */
    private static String percentEncode(String value) {
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        // URLEncoder 把空格转成 '+', 把 '*' 保留, 把 '~' 保留；统一改为 percent-encoding
        return encoded.replace("+", "%20");
    }
}
