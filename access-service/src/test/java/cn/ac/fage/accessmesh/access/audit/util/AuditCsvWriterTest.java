package cn.ac.fage.accessmesh.access.audit.util;

import cn.ac.fage.accessmesh.access.audit.dto.resp.LoginLogResp;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class AuditCsvWriterTest {
    @Test
    void shouldEscapeCsvAndNeutralizeSpreadsheetFormulas() {
        var writer = new AuditCsvWriter(2, 1024);
        writer.header("动作", "内容", "空值");
        writer.row("USER_ROLE_REVOKE", "a,\"b\"\n下一行", null);
        writer.row("=1+1", " \t@SUM(A1)", 123L);
        assertThat(new String(writer.bytes(), StandardCharsets.UTF_8)).isEqualTo(
            "\uFEFF\"动作\",\"内容\",\"空值\"\r\n"
                + "\"USER_ROLE_REVOKE\",\"a,\"\"b\"\"\n下一行\",\"\"\r\n"
                + "\"'=1+1\",\"' \t@SUM(A1)\",\"123\"\r\n");
    }

    @Test
    void shouldRejectExtraRowInsteadOfReturningTruncatedFile() {
        var writer = new AuditCsvWriter(1, 1024);
        writer.header("id");
        writer.row(1L);
        assertThatThrownBy(() -> writer.row(2L)).isInstanceOf(BizException.class);
    }

    @Test
    void shouldEnforceUtf8ByteLimitIncludingHeader() {
        var writer = new AuditCsvWriter(3, 16);
        writer.header("id");
        assertThatThrownBy(() -> writer.row("中文中文")).isInstanceOf(BizException.class);
    }

    @Test
    void loginResponseShouldNotAdvertiseUnimplementedLocation() {
        assertThat(Arrays.stream(LoginLogResp.class.getRecordComponents()).map(component -> component.getName()))
            .doesNotContain("location");
    }
}
