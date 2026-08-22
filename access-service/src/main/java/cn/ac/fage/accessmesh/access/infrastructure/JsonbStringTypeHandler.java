package cn.ac.fage.accessmesh.access.infrastructure;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.StringTypeHandler;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * String ↔ JSONB 列映射（2026-08-22，用户决策全局 TypeHandler 方案）。
 * <p>
 * 权威 DDL 的 extra/snapshot/config_value 等列为 JSONB；MyBatis-Flex 参数绑定以
 * varchar 类型发送 String，PG 对 jsonb 列拒绝 varchar 赋值（42804：
 * "column ... is of type jsonb but expression is of type character varying"），
 * 且 stringtype=unspecified 对 flex 的 setObject 绑定不生效。
 * 本处理器以 {@code Types.OTHER}（未指定类型）发送，PG 按目标列 jsonb 解析；
 * 读取沿用 StringTypeHandler 的 getString（jsonb 列兼容）。
 * 经实体字段 {@code @Column(typeHandler = ...)} 按需挂载，不全局注册。
 * </p>
 */
@MappedTypes(String.class)
public class JsonbStringTypeHandler extends StringTypeHandler {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }
}
