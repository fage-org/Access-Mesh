package cn.ac.fage.accessmesh.perm.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/**
 * 权限条件评估工具类（T-PERM-017 搬入 perm-common，供 Gateway 与 access-service 共享）
 * <p>
 * 提供权限条件项评估的静态工具方法。纯静态、无 DB / Spring 依赖，可在任意 JVM 进程内复用。
 * 支持日期范围、时间范围、IP白名单/黑名单等条件类型的评估。
 * </p>
 * <p>
 * 时钟语义说明（T-PERM-017）：
 * <ul>
 *   <li>{@link #evalDateRange} / {@link #evalTimeRange} 使用调用方进程的系统时钟
 *       {@code LocalDate.now()} / {@code LocalTime.now()}。</li>
 *   <li>Gateway 与 access-service 可能运行在不同进程。本项目面向中小型企业部署，
 *       Gateway 与 access-service 通常同机房 / 同云区域，跨进程时钟一致性由 NTP
 *       同步保证（亚秒级）。条件规则的业务粒度（DATE_RANGE 按天，TIME_RANGE 通常按
 *       小时级如 09:00-18:00）远大于 NTP 漂移，因此 4 类条件均可下发 Gateway。</li>
 *   <li>评估上下文 {@link Map} 当前仅承载 {@code clientIp}，不传递 {@code timestamp}：
 *       一是 NTP 已能解决；二是引入 context 时钟传递会显著增加复杂度（ISO 解析、时区
 *       约定、fail-close 策略），收益与中小企业部署场景不匹配。</li>
 * </ul>
 * </p>
 */
public final class ConditionEvalUtils {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvalUtils.class);

    /**
     * Gateway 可下发评估的条件类型白名单（T-PERM-017）
     * <p>
     * 当 {@code permission_condition.gateway_evaluable=true} 时，{@code conditionRules.items[].type}
     * 必须全部在本集合内才允许标记；含集合外类型（含未来未知类型）→ 拒绝下发，走 fallback 实时鉴权。
     * </p>
     * <p>
     * 集合范围由 T-PERM-017 决策确定：4 个已知类型全部在内（IP_WHITELIST / IP_BLACKLIST /
     * DATE_RANGE / TIME_RANGE）。跨进程时钟一致性由 NTP 同步保证，业务粒度（小时级）
     * 容忍亚秒漂移，故 TIME_RANGE 也可下发。未知类型默认 fail-close，便于后续新增类型
     * 时强制走显式审批流程。
     * </p>
     */
    public static final Set<String> GATEWAY_PUSHABLE_TYPES = Set.of(
        "IP_WHITELIST",
        "IP_BLACKLIST",
        "DATE_RANGE",
        "TIME_RANGE"
    );

    /**
     * 合法的条件规则顶层 {@code logic} 取值集合（T-PERM-017 评审反馈 P2-B）。
     * <p>
     * 历史行为：{@code logic.equals("AND")} 走 AND，**任何其他值（含拼写错 "ANDD" / 大小写 "and" /
     * 未来扩展未知值）一律按 OR 处理**，可能放宽权限。
     * </p>
     * <p>
     * 收口策略（fail-close）：
     * <ul>
     *   <li>{@code logic} 缺省 → 默认 AND（与历史语义一致，向后兼容）</li>
     *   <li>{@code logic} 显式声明且非 {@code AND} / {@code OR}（含 null / 空串） → 写入门禁拒绝；
     *       两端 evaluate 函数防御性 fail-close 返回 false</li>
     * </ul>
     * </p>
     */
    public static final Set<String> VALID_LOGIC = Set.of("AND", "OR");

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private ConditionEvalUtils() {}

    // ===== 日期 / 时间范围评估 =====

    /**
     * 评估日期范围条件
     * <p>
     * 判断当前日期是否在指定的日期范围内（包含边界）。
     * 日期格式为 ISO 8601 格式（yyyy-MM-dd）。使用调用方进程的系统时钟。
     * </p>
     *
     * @param startDate 开始日期字符串
     * @param endDate   结束日期字符串
     * @return 当前日期在范围内返回true，否则返回false
     */
    public static boolean evalDateRange(String startDate, String endDate) {
        if (startDate == null || endDate == null) return false;
        try {
            LocalDate now = LocalDate.now();
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("无效的日期范围: [{}, {}] — {}", startDate, endDate, e.getMessage());
            return false;
        }
    }

    /**
     * 评估时间范围条件
     * <p>
     * 判断当前时间是否在指定的时间范围内（包含边界）。
     * 时间格式为 ISO 8601 格式（HH:mm:ss）。使用调用方进程的系统时钟。
     * </p>
     *
     * @param startTime 开始时间字符串
     * @param endTime   结束时间字符串
     * @return 当前时间在范围内返回true，否则返回false
     */
    public static boolean evalTimeRange(String startTime, String endTime) {
        if (startTime == null || endTime == null) return false;
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);
            if (end.isBefore(start)) {
                // 跨午夜时间范围（如 22:00-06:00）：当前时间 >= 开始时间 或 <= 结束时间 时在范围内
                return !now.isBefore(start) || !now.isAfter(end);
            }
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("无效的时间范围: [{}, {}] — {}", startTime, endTime, e.getMessage());
            return false;
        }
    }

    // ===== IP / CIDR 评估 =====

    /**
     * 评估IP列表条件
     * <p>
     * 判断客户端IP是否匹配CIDR列表中的任一地址。
     * 支持IPv4和IPv6地址格式。
     * </p>
     *
     * @param cidrs     CIDR地址列表（JSON数组）
     * @param clientIp  客户端IP地址
     * @param whitelist 是否为白名单模式（true：白名单匹配返回true，false：黑名单匹配返回false）
     * @return 评估结果
     */
    public static boolean evalIpList(JsonNode cidrs, String clientIp, boolean whitelist) {
        if (clientIp == null) return false;
        if (cidrs == null || !cidrs.isArray()) return false;
        for (JsonNode cidr : cidrs) {
            if (ipMatchesCidr(clientIp, cidr.asText())) {
                return whitelist;
            }
        }
        return !whitelist;
    }

    /**
     * 判断IP是否在CIDR范围内
     * <p>
     * 支持IPv4和IPv6地址的CIDR匹配。
     * CIDR格式为 network/prefixLength，如 192.168.1.0/24。
     * </p>
     *
     * @param ip   IP地址
     * @param cidr CIDR范围
     * @return IP在范围内返回true，否则返回false
     */
    public static boolean ipMatchesCidr(String ip, String cidr) {
        try {
            if (!cidr.contains("/")) {
                return cidr.equals(ip);
            }
            String[] parts = cidr.split("/");
            String networkIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            InetAddress clientAddr = InetAddress.getByName(ip);
            InetAddress networkAddr = InetAddress.getByName(networkIp);

            byte[] clientBytes = clientAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();
            if (clientBytes.length != networkBytes.length) return false;

            int totalBits = clientBytes.length * 8;
            if (prefixLength > totalBits) return false;

            for (int i = 0; i < prefixLength; i++) {
                int byteIndex = i / 8;
                int bitIndex = 7 - (i % 8);
                if (((clientBytes[byteIndex] >> bitIndex) & 1) != ((networkBytes[byteIndex] >> bitIndex) & 1)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("CIDR匹配失败: ip={} cidr={} — {}", ip, cidr, e.getMessage());
            return false;
        }
    }

    // ===== 条件项评估 =====

    /**
     * 判定一组条件规则是否可下发 Gateway 评估（T-PERM-017）
     * <p>
     * 用于 {@code ConditionAppService} 写入校验 + {@code SnapshotAssembler} 内联前防御过滤。
     * 两处共用同一份判定，避免管理面接受却下发面拒绝的不一致。
     * </p>
     * <p>
     * 判定规则（fail-close）：
     * <ul>
     *   <li>{@code conditionRules} 为 null / 非对象 → false</li>
     *   <li>{@code logic} 字段存在但不在 {@link #VALID_LOGIC}（AND/OR） → false（T-PERM-017 P2-B 收口）</li>
     *   <li>{@code items} 缺失 / 非数组 / 为空 → false（空规则视为无意义，不允许下发）</li>
     *   <li>任一 item 缺 {@code type} 字段或 type 不在 {@link #GATEWAY_PUSHABLE_TYPES} → false</li>
     *   <li>全部 item 的 type 都在白名单 → true</li>
     * </ul>
     * </p>
     *
     * @param conditionRules 条件规则根 JsonNode（含 logic + items）
     * @return true 表示可下发 Gateway，false 表示必须走 fallback 实时鉴权
     */
    public static boolean isGatewayPushable(JsonNode conditionRules) {
        if (conditionRules == null || !conditionRules.isObject()) {
            return false;
        }
        // T-PERM-017 P2-B/P3：logic 显式声明时必须在 VALID_LOGIC；缺省放行（兼容默认 AND 语义），null/空串拒绝。
        JsonNode logicNode = conditionRules.get("logic");
        if (logicNode != null && !VALID_LOGIC.contains(logicNode.asText())) {
            return false;
        }
        JsonNode items = conditionRules.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return false;
        }
        for (JsonNode item : items) {
            if (item == null || !item.has("type")) {
                return false;
            }
            String type = item.get("type").asText();
            if (!GATEWAY_PUSHABLE_TYPES.contains(type)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估单个条件项
     * <p>
     * 根据条件类型评估单个条件项JSON节点。
     * 支持的类型：DATE_RANGE、TIME_RANGE、IP_WHITELIST、IP_BLACKLIST。
     * </p>
     *
     * @param item             条件项JSON节点
     * @param context          评估上下文（包含clientIp等信息）
     * @param dateRangeType    日期范围类型标识
     * @param timeRangeType    时间范围类型标识
     * @param ipWhitelistType  IP白名单类型标识
     * @param ipBlacklistType  IP黑名单类型标识
     * @return 条件满足返回true，否则返回false
     */
    public static boolean evalItem(JsonNode item, Map<String, Object> context,
                                   String dateRangeType, String timeRangeType,
                                   String ipWhitelistType, String ipBlacklistType) {
        if (item == null) return false;
        String type = item.has("type") ? item.get("type").asText() : "";
        JsonNode params = item.get("params");
        if (params == null) return false;

        if (dateRangeType.equals(type)) {
            return evalDateRange(
                params.has("start") ? params.get("start").asText() : null,
                params.has("end") ? params.get("end").asText() : null);
        }
        if (timeRangeType.equals(type)) {
            return evalTimeRange(
                params.has("start") ? params.get("start").asText() : null,
                params.has("end") ? params.get("end").asText() : null);
        }
        if (ipWhitelistType.equals(type)) {
            String clientIp = (String) context.get("clientIp");
            return evalIpList(params.get("cidrs"), clientIp, true);
        }
        if (ipBlacklistType.equals(type)) {
            String clientIp = (String) context.get("clientIp");
            return evalIpList(params.get("cidrs"), clientIp, false);
        }
        return false;
    }
}
