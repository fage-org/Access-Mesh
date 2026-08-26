package cn.ac.fage.accessmesh.common.timezone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UtcTimezoneEnvironmentPostProcessorTest {

    private final UtcTimezoneEnvironmentPostProcessor processor = new UtcTimezoneEnvironmentPostProcessor();

    private TimeZone originalZone;

    @BeforeEach
    void saveDefaultZone() {
        originalZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void shouldForceNonUtcDefaultZoneToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        processor.postProcessEnvironment(new StandardEnvironment(), new SpringApplication());
        assertEquals("UTC", TimeZone.getDefault().getID());
    }

    @Test
    void shouldBeIdempotentWhenAlreadyUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        assertDoesNotThrow(() ->
            processor.postProcessEnvironment(new StandardEnvironment(), new SpringApplication()));
        assertEquals("UTC", TimeZone.getDefault().getID());
    }
}
