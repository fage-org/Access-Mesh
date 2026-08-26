package cn.ac.fage.accessmesh.access.infrastructure;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * LocalDateTime ↔ TIMESTAMPTZ 列映射，显式按 UTC 换算（T-ACCESS-024）。
 * <p>
 * 权威 DDL（docs/design/schema/access-service.sql）全部时间列为 TIMESTAMPTZ，
 * 而 pgjdbc 不支持 {@code rs.getObject(col, LocalDateTime.class)} 读取 TIMESTAMPTZ
 * （抛 "Cannot convert the column of type TIMESTAMPTZ to requested type
 * java.time.LocalDateTime"），默认处理器在该 DDL 上所有实体查询都会失败。
 * 由 {@link MybatisFlexTypeHandlerConfig} 全局注册为 LocalDateTime 的默认处理器。
 * </p>
 * <p>
 * 时间语义：{@code LocalDateTime} 携带 UTC 墙钟。写入按 {@code atOffset(UTC)} 得到
 * 唯一瞬时，读取把瞬时归一化回 UTC 墙钟——全程不经过 {@code java.sql.Timestamp}
 * 中转（规范 §7.4 禁用，且其 valueOf/toLocalDateTime 会按 JVM 默认时区换算，
 * 使 TIMESTAMPTZ 语义随部署环境漂移）。pgjdbc 对 TIMESTAMPTZ 原生支持
 * OffsetDateTime 双向映射，会话/服务器时区不参与语义。生产点（审计字段等
 * {@code LocalDateTime.now()}）的墙钟正确性由 common 的
 * {@code UtcTimezoneEnvironmentPostProcessor} 强制 JVM 默认时区 UTC 保证。
 * </p>
 */
@MappedTypes(LocalDateTime.class)
public class TimestamptzLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter.atOffset(ZoneOffset.UTC));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLocalDateTime(rs.getObject(columnName, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLocalDateTime(rs.getObject(columnIndex, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLocalDateTime(cs.getObject(columnIndex, OffsetDateTime.class));
    }

    private static LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value != null ? value.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
    }
}
