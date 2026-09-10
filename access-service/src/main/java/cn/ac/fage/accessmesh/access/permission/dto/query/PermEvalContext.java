package cn.ac.fage.accessmesh.access.permission.dto.query;

import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 权限条件评估上下文（T-PERM-057 定案：多层上下文对象——用户环境 + 服务器环境 + 调用方上下文）。
 * <p>
 * 条件评估三态中的「评估」态使用本对象；无请求上下文的内部调用（bootstrap/同步/定时任务）
 * 用户环境为空——IP 类条件按 {@code ConditionEvalUtils.evalIpList} 既有 fail-closed 行为拒绝。
 * 展平为评估 Map 时键与既有契约一致（{@code clientIp}；{@code evaluatedAt} 为新增服务器环境键，
 * 时间类条件优先消费、缺省回退评估方本机时钟）。
 * </p>
 *
 * @param clientIp    用户环境：客户端 IP（经 Gateway 重建的 X-Forwarded-For，值=Gateway 观测的 remoteAddr，T-GW-008；直连时为请求方可伪造声明）；null=无请求上下文
 * @param evaluatedAt 服务器环境：评估时间；null=展平时取当前时钟
 * @param attributes  调用方提供的上下文（SDK/管理端模拟等扩展键值）
 */
public record PermEvalContext(String clientIp, LocalDateTime evaluatedAt, Map<String, Object> attributes) {

    /** 评估 Map 键：客户端 IP（与既有条件评估契约一致）。 */
    public static final String KEY_CLIENT_IP = "clientIp";

    /** 评估 Map 键：评估时间（服务器环境新增键，ISO-8601 字符串；单一来源 perm-common）。 */
    public static final String KEY_EVALUATED_AT = ConditionEvalUtils.CONTEXT_KEY_EVALUATED_AT;

    public PermEvalContext {
        // 防御性复制过滤 null 键值（Map.copyOf 禁 null——SDK context Map 经 JSON 反序列化可含
        // null 值，直接复制会在权限判定前抛 NPE 变 500；codex 外评 P2 修复）
        attributes = filterValid(attributes);
    }

    private static Map<String, Object> filterValid(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }

    /**
     * 从既有调用方 Map 上下文构造（SDK 契约 {@code context.clientIp} 键提取为用户环境，
     * 其余键归调用方上下文）。
     */
    public static PermEvalContext fromCallerMap(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return new PermEvalContext(null, null, Map.of());
        }
        Map<String, Object> rest = new LinkedHashMap<>();
        String clientIp = null;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (KEY_CLIENT_IP.equals(entry.getKey())) {
                clientIp = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            } else {
                rest.put(entry.getKey(), entry.getValue());
            }
        }
        return new PermEvalContext(clientIp, null, rest);
    }

    /**
     * 展平为条件评估 Map（{@link cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService#evaluate} 入参形态）。
     * <p>
     * evaluatedAt 为 null 时按展平时钟填充（调用方单次评估内时钟一致）。
     * 保留键（clientIp/evaluatedAt）后写——attributes 同名键不可覆盖用户/服务器环境。
     * </p>
     */
    public Map<String, Object> toEvalMap() {
        Map<String, Object> eval = new LinkedHashMap<>();
        eval.putAll(attributes);
        if (clientIp != null) {
            eval.put(KEY_CLIENT_IP, clientIp);
        }
        LocalDateTime at = evaluatedAt != null ? evaluatedAt : LocalDateTime.now();
        eval.put(KEY_EVALUATED_AT, at.toString());
        return eval;
    }
}
