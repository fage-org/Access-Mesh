package cn.ac.fage.accessmesh.example.infrastructure;

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

/** LocalDateTime values represent UTC; JDBC maps TIMESTAMPTZ through OffsetDateTime. */
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
