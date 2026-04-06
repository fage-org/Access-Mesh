package org.dromara.permission.condition.builtin;

import org.dromara.permission.condition.PermissionConditionPresetHandler;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.model.permission.PermissionContext;
import org.springframework.stereotype.Component;

@Component
public class InternalIpConditionHandler implements PermissionConditionPresetHandler {

    @Override
    public String getCode() {
        return "INTERNAL_IP";
    }

    @Override
    public boolean evaluate(PcPermissionCondition condition, PermissionContext context) {
        Object ipValue = context.getEvalContext().getOrDefault("clientIp", context.getEvalContext().get("remoteIp"));
        if (!(ipValue instanceof String ip) || ip.isBlank()) {
            return false;
        }
        return isInternalIp(ip);
    }

    private boolean isInternalIp(String ip) {
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.equals("127.0.0.1") || ip.equals("::1")) {
            return true;
        }
        String[] segments = ip.split("\\.");
        if (segments.length < 2) {
            return false;
        }
        try {
            int first = Integer.parseInt(segments[0]);
            int second = Integer.parseInt(segments[1]);
            return first == 172 && second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
