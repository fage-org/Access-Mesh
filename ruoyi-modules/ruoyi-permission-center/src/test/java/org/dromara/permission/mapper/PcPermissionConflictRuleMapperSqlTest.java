package org.dromara.permission.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("dev")
class PcPermissionConflictRuleMapperSqlTest {

    @Test
    void pagedConflictQueries_executeAgainstRealMapperSql() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:perm-conflict-sql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE permission_conflict_rule (" +
                "id BIGINT PRIMARY KEY, tenant_id BIGINT, biz_domain_id BIGINT, first_operation_permission_id BIGINT, " +
                "second_operation_permission_id BIGINT, resource_type_value INTEGER, delete_flag BIGINT)");
            statement.execute("CREATE TABLE role_resource_permission (" +
                "id BIGINT PRIMARY KEY, tenant_id BIGINT, abstract_role_id BIGINT, resource_entity_id BIGINT, " +
                "operation_permission_id BIGINT, delete_flag BIGINT)");
            statement.execute("CREATE TABLE user_role (" +
                "id BIGINT PRIMARY KEY, tenant_id BIGINT, abstract_user_id BIGINT, abstract_role_id BIGINT, " +
                "valid_from TIMESTAMP NULL, valid_to TIMESTAMP NULL, delete_flag BIGINT)");
            statement.execute("CREATE TABLE abstract_role (" +
                "id BIGINT PRIMARY KEY, tenant_id BIGINT, biz_domain_id BIGINT, delete_flag BIGINT)");
            statement.execute("CREATE TABLE resource_entity (" +
                "id BIGINT PRIMARY KEY, tenant_id BIGINT, biz_domain_id BIGINT, resource_type INTEGER, delete_flag BIGINT)");

            statement.execute("INSERT INTO permission_conflict_rule VALUES (1, 1, 10, 101, 102, 7, 0)");
            statement.execute("INSERT INTO role_resource_permission VALUES (11, 1, 30, 200, 101, 0)");
            statement.execute("INSERT INTO role_resource_permission VALUES (12, 1, 30, 200, 102, 0)");
            statement.execute("INSERT INTO user_role VALUES (21, 1, 100, 30, NULL, NULL, 0)");
            statement.execute("INSERT INTO abstract_role VALUES (30, 1, 10, 0)");
            statement.execute("INSERT INTO resource_entity VALUES (200, 1, 10, 7, 0)");
        }

        JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("test", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(PcPermissionConflictRuleMapper.class);
        try (Reader reader = Resources.getResourceAsReader("mapper/permission/PcPermissionConflictRuleMapper.xml")) {
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(
                reader,
                configuration,
                "mapper/permission/PcPermissionConflictRuleMapper.xml",
                configuration.getSqlFragments()
            );
            xmlMapperBuilder.parse();
        }

        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PcPermissionConflictRuleMapper mapper = session.getMapper(PcPermissionConflictRuleMapper.class);
            long total = mapper.countPagedConflictViolations(1L, 10L, 200L);
            List<ConflictViolationVo> rows = mapper.selectPagedConflictViolations(1L, 10L, 200L, 0L, 10L);

            assertEquals(2L, total);
            assertEquals(2, rows.size());
            assertEquals(100L, rows.get(0).getAbstractUserId());
            assertEquals(30L, rows.get(1).getAbstractRoleId());
            assertEquals(101L, rows.get(0).getFirstOperationPermissionId());
            assertEquals(102L, rows.get(0).getSecondOperationPermissionId());
            assertNotNull(rows.get(0).getDescription());
        }
    }
}
