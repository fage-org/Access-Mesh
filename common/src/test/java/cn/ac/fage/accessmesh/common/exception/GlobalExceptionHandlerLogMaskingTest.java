package cn.ac.fage.accessmesh.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.model.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MVC 请求解析错误日志脱敏回归（T-PERM-110）。
 * <p>
 * HttpMessageNotReadableException 的 message 携带 Jackson 出错点附近的原始请求体
 * 片段（含「合法 JSON + 尾随 token」穿透形态）——密码/token 可能随 WARN 日志外泄，
 * 且该通道发生在进 Controller 之前，操作日志 AOP 的「不记录请求参数」覆盖不到。
 * 锁两件事：日志仍产生（可观测性不丢）但格式化文本不携带原始载荷；客户端响应体不变。
 * </p>
 */
class GlobalExceptionHandlerLogMaskingTest {

    private static final String SECRET_MARKER = "SECRET-PAYLOAD-eyJhbGciOiJIUzI1NiJ9";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldNotCarryRawPayloadWhenLoggingParseError() {
        String raw = "{\"password\":\"" + SECRET_MARKER + "\",\"typ";
        var cause = new RuntimeException("Unexpected character ('\"' (code 34)) ... at [Source: REDACTED]" + raw);
        var ex = new HttpMessageNotReadableException("JSON parse error: " + cause.getMessage(), (org.springframework.http.HttpInputMessage) null);

        R<Void> resp = new GlobalExceptionHandler().handleHttpMessageNotReadable(ex);

        assertThat(resp.getCode()).isEqualTo(GlobalErrorCode.VALIDATION_FAILED.code());
        assertThat(resp.getMessage()).isEqualTo("请求体格式错误");
        String logged = formattedLogs();
        assertThat(logged).contains("HttpMessageNotReadable");
        assertThat(logged).doesNotContain(SECRET_MARKER);
        assertThat(logged).doesNotContain(raw);
    }

    @Test
    void shouldNotCarryRawPayloadOnTrailingTokenPenetration() {
        // 尾随 token 穿透形态：首段为合法 JSON，尾随内容进入解析错误消息（readTree 语法校验
        // 不含尾随检查的同族通道）
        String trailing = "{} {\"access_token\":\"" + SECRET_MARKER + "\"}";
        var ex = new HttpMessageNotReadableException(
            "Unexpected token ({), possibly a trailing content: " + trailing,
            (org.springframework.http.HttpInputMessage) null);

        new GlobalExceptionHandler().handleHttpMessageNotReadable(ex);

        assertThat(formattedLogs()).doesNotContain(SECRET_MARKER);
    }

    @Test
    void shouldKeepWarnLevelSignalForOps() {
        var ex = new HttpMessageNotReadableException("JSON parse error", (org.springframework.http.HttpInputMessage) null);
        new GlobalExceptionHandler().handleHttpMessageNotReadable(ex);
        assertThat(appender.list)
            .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    private String formattedLogs() {
        return appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
    }
}
