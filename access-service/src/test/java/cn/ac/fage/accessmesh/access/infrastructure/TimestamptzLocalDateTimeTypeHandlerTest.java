package cn.ac.fage.accessmesh.access.infrastructure;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-024：LocalDateTime ↔ TIMESTAMPTZ 显式 UTC 换算的单测（Mockito 层）。
 * <p>
 * 写入方向断言参数即 {@code atOffset(UTC)} 的唯一瞬时；读取方向断言任意服务器偏移
 * （如 +08:00 渲染）都归一化回 UTC 墙钟。JVM 默认时区换算路径（旧实现的漂移源）
 * 不在本层出现——跨 JVM 时区的一致性由容器轨道 TimestamptzDualTimezonePgIT 验证。
 * </p>
 */
class TimestamptzLocalDateTimeTypeHandlerTest {

    private static final LocalDateTime MARKER =
        LocalDateTime.of(2026, 8, 25, 12, 34, 56, 789_000_000);

    private final TimestamptzLocalDateTimeTypeHandler handler = new TimestamptzLocalDateTimeTypeHandler();

    @Test
    @DisplayName("写入：LocalDateTime 以偏移 UTC 的 OffsetDateTime 传参（唯一瞬时，不经 JVM 时区）")
    void writeBindsUtcOffsetInstant() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 3, MARKER, JdbcType.TIMESTAMP);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(3), captor.capture());
        assertThat(captor.getValue()).isEqualTo(MARKER.atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("读取（列名）：+08:00 偏移渲染的 OffsetDateTime 归一化回 UTC 墙钟")
    void readByColumnNameNormalizesToUtcWallClock() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("created_at", OffsetDateTime.class))
            .thenReturn(MARKER.atOffset(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8)));
        assertThat(handler.getNullableResult(rs, "created_at")).isEqualTo(MARKER);
    }

    @Test
    @DisplayName("读取（列序）：UTC 偏移原样映射墙钟")
    void readByColumnIndexNormalizesToUtcWallClock() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(2, OffsetDateTime.class)).thenReturn(MARKER.atOffset(ZoneOffset.UTC));
        assertThat(handler.getNullableResult(rs, 2)).isEqualTo(MARKER);
    }

    @Test
    @DisplayName("读取（存储过程出参）：任意偏移归一化回 UTC 墙钟")
    void readFromCallableStatementNormalizesToUtcWallClock() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getObject(1, OffsetDateTime.class))
            .thenReturn(MARKER.atOffset(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(-5)));
        assertThat(handler.getNullableResult(cs, 1)).isEqualTo(MARKER);
    }

    @Test
    @DisplayName("读取：NULL 列返回 null")
    void readNullReturnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("deleted_at", OffsetDateTime.class)).thenReturn(null);
        assertThat(handler.getNullableResult(rs, "deleted_at")).isNull();
    }
}
