package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PermissionConditionDomainServiceImpl implements PermissionConditionDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionConditionDomainServiceImpl.class);

    private final PermissionConditionMapper conditionMapper;
    private final ObjectMapper objectMapper;

    /**
     * Instance-level cache for parsed condition rules (JsonNode).
     * Key: conditionId, Value: parsed JsonNode (only the rules, not the evaluation result)
     */
    private final Map<Long, JsonNode> rulesCache = new ConcurrentHashMap<>();

    public PermissionConditionDomainServiceImpl(PermissionConditionMapper conditionMapper,
                                                 ObjectMapper objectMapper) {
        this.conditionMapper = conditionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RolePermSnapshot.RolePermEntry> evaluate(Long tenantId, List<RolePermSnapshot.RolePermEntry> entries,
                                                          Map<String, Object> context) {
        Map<Long, Boolean> conditionCache = new HashMap<>();

        return entries.stream()
            .filter(entry -> {
                if (entry.conditionId() == null || !entry.hasCondition()) {
                    return true;
                }
                return conditionCache.computeIfAbsent(entry.conditionId(),
                    id -> evaluateCondition(id, context));
            })
            .collect(Collectors.toList());
    }

    private boolean evaluateCondition(Long conditionId, Map<String, Object> context) {
        // Try to get parsed rules from instance-level cache first
        JsonNode rules = rulesCache.get(conditionId);

        if (rules == null) {
            // Cache miss: fetch from database and parse JSON
            PermissionCondition condition = conditionMapper.selectOneById(conditionId);
            if (condition == null || !Boolean.TRUE.equals(condition.getEnabled())) {
                return false;
            }

            try {
                rules = objectMapper.readTree(condition.getConditionRules());
                // Cache the parsed JsonNode for future use
                rulesCache.put(conditionId, rules);
            } catch (JsonProcessingException e) {
                log.warn("Invalid conditionRules JSON format, conditionId: {}, error: {}",
                    conditionId, e.getMessage());
                return false;
            } catch (Exception e) {
                log.error("Unexpected error parsing conditionRules, conditionId: {}", conditionId, e);
                return false;
            }
        }

        try {
            String logic = rules.has("logic") ? rules.get("logic").asText() : PermConstants.ConditionLogic.AND;
            JsonNode items = rules.get("items");
            if (items == null || !items.isArray()) return false;

            boolean allMatch = logic.equals(PermConstants.ConditionLogic.AND);
            for (JsonNode item : items) {
                boolean matched = evaluateItem(item, context);
                if (allMatch && !matched) return false;
                if (!allMatch && matched) return true;
            }
            return allMatch;
        } catch (Exception e) {
            log.error("Unexpected error evaluating condition, conditionId: {}", conditionId, e);
            return false;
        }
    }

    private boolean evaluateItem(JsonNode item, Map<String, Object> context) {
        String type = item.has("type") ? item.get("type").asText() : "";
        JsonNode params = item.get("params");
        if (params == null) return false;

        return switch (type) {
            case PermConstants.ConditionType.DATE_RANGE -> evaluateDateRange(params);
            case PermConstants.ConditionType.TIME_RANGE -> evaluateTimeRange(params);
            case PermConstants.ConditionType.IP_WHITELIST -> evaluateIpWhitelist(params, context);
            case PermConstants.ConditionType.IP_BLACKLIST -> !evaluateIpWhitelist(params, context);
            default -> false;
        };
    }

    private boolean evaluateDateRange(JsonNode params) {
        if (!params.has("start") || !params.has("end")) return false;
        String startDate = params.get("start").asText();
        String endDate = params.get("end").asText();
        try {
            LocalDate now = LocalDate.now();
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date range format, startDate: {}, endDate: {}, error: {}",
                startDate, endDate, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error evaluating date range, startDate: {}, endDate: {}", startDate, endDate, e);
            return false;
        }
    }

    private boolean evaluateTimeRange(JsonNode params) {
        if (!params.has("start") || !params.has("end")) return false;
        String startTime = params.get("start").asText();
        String endTime = params.get("end").asText();
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("Invalid time range format, startTime: {}, endTime: {}, error: {}",
                startTime, endTime, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error evaluating time range, startTime: {}, endTime: {}", startTime, endTime, e);
            return false;
        }
    }

    private boolean evaluateIpWhitelist(JsonNode params, Map<String, Object> context) {
        String clientIp = (String) context.get("clientIp");
        if (clientIp == null) return false;
        JsonNode cidrs = params.get("cidrs");
        if (cidrs == null || !cidrs.isArray()) return false;
        for (JsonNode cidr : cidrs) {
            if (ipMatchesCidr(clientIp, cidr.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean ipMatchesCidr(String ip, String cidr) {
        try {
            if (!cidr.contains("/")) {
                // Plain IP — exact match
                return cidr.equals(ip);
            }
            String[] parts = cidr.split("/");
            String networkIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            InetAddress clientAddr = InetAddress.getByName(ip);
            InetAddress networkAddr = InetAddress.getByName(networkIp);

            byte[] clientBytes = clientAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();

            // Only compare same-family addresses (both IPv4 or both IPv6)
            if (clientBytes.length != networkBytes.length) return false;

            int totalBits = clientBytes.length * 8;
            if (prefixLength > totalBits) return false;

            // Compare bit-by-bit up to prefix length
            for (int i = 0; i < prefixLength; i++) {
                int byteIndex = i / 8;
                int bitIndex = 7 - (i % 8);
                int clientBit = (clientBytes[byteIndex] >> bitIndex) & 1;
                int networkBit = (networkBytes[byteIndex] >> bitIndex) & 1;
                if (clientBit != networkBit) return false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid CIDR format: {}, error: {}", cidr, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error matching IP against CIDR: {}", cidr, e);
            return false;
        }
    }
}
