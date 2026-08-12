package cn.ac.fage.accessmesh.access.admin.sync.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P6 防回归：验证 {@link SyncSchedulerProperties#getTimeoutFor(String)} 的回退顺序：
 * <p>
 * syncAction → "default" → 60s。
 * </p>
 */
class SyncSchedulerPropertiesTest {

    @Test
    @DisplayName("配置 default=60, PERM_USER_ROLE_SYNC=120 → getTimeoutFor 返回 120s")
    void getTimeoutFor_resolvesPerActionValue() {
        SyncSchedulerProperties props = new SyncSchedulerProperties();
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("default", 60L);
        map.put("PERM_USER_ROLE_SYNC", 120L);
        props.setStaleLockTimeout(map);

        assertThat(props.getTimeoutFor("PERM_USER_ROLE_SYNC")).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("未配置某 syncAction → 回退 default=60s")
    void getTimeoutFor_fallsBackToDefault() {
        SyncSchedulerProperties props = new SyncSchedulerProperties();
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("default", 60L);
        map.put("PERM_USER_ROLE_SYNC", 120L);
        props.setStaleLockTimeout(map);

        assertThat(props.getTimeoutFor("UNKNOWN_ACTION")).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("内置默认值包含 default=60s 与 full-sync=300s")
    void getTimeoutFor_usesBuiltInDefaults() {
        SyncSchedulerProperties props = new SyncSchedulerProperties();
        // staleLockTimeout 默认空 map
        assertThat(props.getStaleLockTimeout())
            .containsEntry(SyncSchedulerProperties.DEFAULT_KEY, 60L)
            .containsEntry(SyncSchedulerProperties.FULL_SYNC_KEY, 300L);

        assertThat(props.getTimeoutFor("ANY_ACTION")).isEqualTo(Duration.ofSeconds(60));
        assertThat(props.getTimeoutFor(null)).isEqualTo(Duration.ofSeconds(60));
        assertThat(props.getTimeoutFor(SyncSchedulerProperties.FULL_SYNC_KEY)).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("getTimeoutFor(null) 在配置 default 时回退 default")
    void getTimeoutFor_nullAction_usesDefault() {
        SyncSchedulerProperties props = new SyncSchedulerProperties();
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("default", 90L);
        props.setStaleLockTimeout(map);

        assertThat(props.getTimeoutFor(null)).isEqualTo(Duration.ofSeconds(90));
    }
}
