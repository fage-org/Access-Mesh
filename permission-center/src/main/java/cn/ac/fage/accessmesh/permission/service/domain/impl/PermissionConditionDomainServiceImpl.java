package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionConditionDomainServiceImpl implements PermissionConditionDomainService {

    private final PermissionConditionMapper conditionMapper;
    private final ObjectMapper objectMapper;

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
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition == null || !Boolean.TRUE.equals(condition.getEnabled())) {
            return false;
        }

        try {
            JsonNode rules = objectMapper.readTree(condition.getConditionRules());
            String logic = rules.has("logic") ? rules.get("logic").asText() : "AND";
            JsonNode items = rules.get("items");
            if (items == null || !items.isArray()) return false;

            boolean allMatch = logic.equals("AND");
            for (JsonNode item : items) {
                boolean matched = evaluateItem(item, context);
                if (allMatch && !matched) return false;
                if (!allMatch && matched) return true;
            }
            return allMatch;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean evaluateItem(JsonNode item, Map<String, Object> context) {
        String type = item.has("type") ? item.get("type").asText() : "";
        JsonNode params = item.get("params");
        if (params == null) return false;

        return switch (type) {
            case "DATE_RANGE" -> evaluateDateRange(params);
            case "TIME_RANGE" -> evaluateTimeRange(params);
            case "IP_WHITELIST" -> evaluateIpWhitelist(params, context);
            case "IP_BLACKLIST" -> !evaluateIpWhitelist(params, context);
            default -> false;
        };
    }

    private boolean evaluateDateRange(JsonNode params) {
        if (!params.has("start") || !params.has("end")) return false;
        try {
            LocalDate now = LocalDate.now();
            LocalDate start = LocalDate.parse(params.get("start").asText());
            LocalDate end = LocalDate.parse(params.get("end").asText());
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean evaluateTimeRange(JsonNode params) {
        if (!params.has("start") || !params.has("end")) return false;
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(params.get("start").asText());
            LocalTime end = LocalTime.parse(params.get("end").asText());
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (Exception e) {
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
        } catch (Exception e) {
            return false;
        }
    }
}
