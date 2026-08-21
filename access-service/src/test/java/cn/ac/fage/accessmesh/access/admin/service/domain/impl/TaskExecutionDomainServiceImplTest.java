package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.service.domain.TaskExecutionDomainService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaskExecutionDomainServiceImpl} 执行键构建/反解单元测试（T-ACCESS-009）。
 * <p>
 * 键格式与解析不触库（mapper 传 null），覆盖：秒级截断、jobId 解析、
 * 计划时刻从执行键反解（接管重试与首次执行的 scheduledTime 语义一致）、
 * 手动键的 null 语义与唯一性。
 * </p>
 */
class TaskExecutionDomainServiceImplTest {

    private final TaskExecutionDomainService service = new TaskExecutionDomainServiceImpl(null);

    @Test
    void scheduledKeyTruncatesToSeconds() {
        String key = service.buildScheduledKey(5L,
            LocalDateTime.of(2026, 8, 21, 12, 0, 0, 123_000_000));
        assertThat(key).isEqualTo("job:5:20260821T120000");
    }

    @Test
    void parseJobIdHandlesScheduledAndManualKeys() {
        assertThat(service.parseJobId("job:5:20260821T120000")).isEqualTo(5L);
        assertThat(service.parseJobId("job:5:manual:1693000000000-uuid")).isEqualTo(5L);
        assertThat(service.parseJobId("other:5:20260821T120000")).isNull();
        assertThat(service.parseJobId("job:abc:20260821T120000")).isNull();
        assertThat(service.parseJobId(null)).isNull();
    }

    @Test
    void parseScheduledTimeRoundTripsScheduledKey() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 21, 13, 0, 0);
        String key = service.buildScheduledKey(7L, time);
        assertThat(service.parseScheduledTime(key)).isEqualTo(time);
    }

    @Test
    void parseScheduledTimeReturnsNullForManualAndGarbage() {
        assertThat(service.parseScheduledTime("job:7:manual:1693000000000-uuid")).isNull();
        assertThat(service.parseScheduledTime("job:7:not-a-time")).isNull();
        assertThat(service.parseScheduledTime(null)).isNull();
    }

    @Test
    void manualKeysAreUnique() {
        String first = service.buildManualKey(9L);
        String second = service.buildManualKey(9L);
        assertThat(first).isNotEqualTo(second).startsWith("job:9:manual:");
    }
}
