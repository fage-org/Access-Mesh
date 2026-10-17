package cn.ac.fage.accessmesh.access.audit.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.CronExpression;
import java.time.LocalDateTime;

/** Immutable startup snapshot: changing deployment settings requires restarting every instance. */
@Component
public final class AuditLogSettings {
    private final int maxRows;
    private final int maxBytes;
    private final boolean retentionEnabled;
    private final String cron;
    private final Integer operationDays;
    private final Integer changeDays;
    private final Integer loginDays;

    public AuditLogSettings(Environment env) {
        maxRows = env.getProperty("access.audit.export.max-rows", Integer.class, 10000);
        maxBytes = env.getProperty("access.audit.export.max-bytes", Integer.class, 20 * 1024 * 1024);
        if (maxRows < 1 || maxRows == Integer.MAX_VALUE || maxBytes < 3)
            throw new IllegalArgumentException("Invalid access.audit.export limits");
        retentionEnabled = env.getProperty("access.audit.retention.enabled", Boolean.class, false);
        cron = env.getProperty("access.audit.retention.cron", "0 0 18 * * *");
        CronExpression.parse(cron);
        operationDays = days(env, "operation-days");
        changeDays = days(env, "change-days");
        loginDays = days(env, "login-days");
    }
    private Integer days(Environment env, String key) {
        Integer value = env.getProperty("access.audit.retention." + key, Integer.class);
        if (value != null) {
            if (value < 1) throw new IllegalArgumentException("Retention days must be positive: " + key);
            LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(value);
        }
        return value;
    }
    public int maxRows() { return maxRows; }
    public int maxBytes() { return maxBytes; }
    public boolean retentionEnabled() { return retentionEnabled; }
    public boolean cleanupEnabled() { return retentionEnabled && (operationDays != null || changeDays != null || loginDays != null); }
    public String cron() { return cron; }
    public Integer operationDays() { return operationDays; }
    public Integer changeDays() { return changeDays; }
    public Integer loginDays() { return loginDays; }
}
