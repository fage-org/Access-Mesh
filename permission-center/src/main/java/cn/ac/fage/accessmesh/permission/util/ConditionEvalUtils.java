package cn.ac.fage.accessmesh.permission.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 权限条件评估工具类
 * <p>
 * 提供权限条件项评估的静态工具方法。
 * 从 PermissionConditionDomainServiceImpl 中提取，提供可复用的评估逻辑。
 * 支持日期范围、时间范围、IP白名单/黑名单等条件类型的评估。
 * </p>
 */
public final class ConditionEvalUtils {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvalUtils.class);

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
     * 日期格式为 ISO 8601 格式（yyyy-MM-dd）。
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
     * 时间格式为 ISO 8601 格式（HH:mm:ss）。
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