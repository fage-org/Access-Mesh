package cn.ac.fage.accessmesh.access.audit.util;

import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class JsonValidationUtilsTest {
    @ParameterizedTest
    @ValueSource(strings = {"{} {}", "{} trailing", "{", "   "})
    void invalidJsonHasPublicParameterCode(String value) {
        assertThatThrownBy(() -> JsonValidationUtils.validateJson(value))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(20044));
    }

    @Test
    void inputLimitCountsUtf8Bytes() {
        assertThatCode(() -> JsonValidationUtils.validateJson("\"" + "a".repeat(65534) + "\""))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> JsonValidationUtils.validateJson("\"" + "a".repeat(65535) + "\""))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> JsonValidationUtils.validateJson("\"" + "中".repeat(21845) + "\""))
            .isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "true", "null", "123", "\"value\""})
    void acceptsExactlyOneLegalJsonValue(String value) {
        assertThatCode(() -> JsonValidationUtils.validateJson(value)).doesNotThrowAnyException();
    }
    @Test
    void internalSnapshotCanExceedInputLimitButStillRejectsTrailingValues() {
        assertThatCode(() -> JsonValidationUtils.validateSnapshot("\"" + "a".repeat(65536) + "\""))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> JsonValidationUtils.validateSnapshot("{} {}"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
