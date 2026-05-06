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
 * Static utilities for evaluating individual permission condition items.
 *
 * <p>Extracted from {@code PermissionConditionDomainServiceImpl} to eliminate
 * inline evaluation logic and provide reusable static methods.
 */
public final class ConditionEvalUtils {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvalUtils.class);

    private ConditionEvalUtils() {}

    // ===== Date / Time ranges =====

    /** Returns true if today's date falls in [start, end] inclusive. */
    public static boolean evalDateRange(String startDate, String endDate) {
        if (startDate == null || endDate == null) return false;
        try {
            LocalDate now = LocalDate.now();
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date range: [{}, {}] — {}", startDate, endDate, e.getMessage());
            return false;
        }
    }

    /** Returns true if current time falls in [start, end] inclusive. */
    public static boolean evalTimeRange(String startTime, String endTime) {
        if (startTime == null || endTime == null) return false;
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("Invalid time range: [{}, {}] — {}", startTime, endTime, e.getMessage());
            return false;
        }
    }

    // ===== IP / CIDR =====

    /** Returns true if clientIp matches any CIDR in the list. */
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

    /** Check if an IP falls within a CIDR range. Supports both IPv4 and IPv6. */
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
            log.warn("CIDR match failed: ip={} cidr={} — {}", ip, cidr, e.getMessage());
            return false;
        }
    }

    // ===== item evaluation =====

    /**
     * Evaluate a single condition item JSON node against a context map.
     * Supported types: DATE_RANGE, TIME_RANGE, IP_WHITELIST, IP_BLACKLIST.
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
