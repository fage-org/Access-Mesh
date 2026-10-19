package cn.ac.fage.accessmesh.example.infrastructure;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.UUID;

public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement statement,int index,UUID value,JdbcType type) throws SQLException { statement.setObject(index,value); }
    @Override public UUID getNullableResult(ResultSet rows,String column) throws SQLException { return rows.getObject(column,UUID.class); }
    @Override public UUID getNullableResult(ResultSet rows,int column) throws SQLException { return rows.getObject(column,UUID.class); }
    @Override public UUID getNullableResult(CallableStatement statement,int column) throws SQLException { return statement.getObject(column,UUID.class); }
}
