package cn.ac.fage.accessmesh.access.infrastructure;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * LocalDateTime ↔ TIMESTAMPTZ 列映射（2026-08-22，用户决策全局 TypeHandler 方案）。
 * <p>
 * 权威 DDL（docs/design/schema/access-service.sql）全部时间列为 TIMESTAMPTZ，
 * 而 pgjdbc 不支持 {@code rs.getObject(col, LocalDateTime.class)} 读取 TIMESTAMPTZ
 * （抛 "Cannot convert the column of type TIMESTAMPTZ to requested type
 * java.time.LocalDateTime"），默认处理器在该 DDL 上所有实体查询都会失败。
 * 本处理器经 {@link Timestamp} 中转（JVM 默认时区换算，与 MyBatis 传统行为一致），
 * 由 {@link MybatisFlexTypeHandlerConfig} 全局注册为 LocalDateTime 的默认处理器。
 * </p>
 */
@MappedTypes(LocalDateTime.class)
public class TimestamptzLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTimestamp(i, Timestamp.valueOf(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLocalDateTime(rs.getTimestamp(columnName));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLocalDateTime(rs.getTimestamp(columnIndex));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLocalDateTime(cs.getTimestamp(columnIndex));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
