package cn.ac.fage.accessmesh.common.mybatis;

import java.sql.Connection;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Applies Spring's explicit isolation to the connection actually used by a Mapper.
 * FlexTransactionManager 1.11.8 binds connections itself but only propagates the timeout.
 * Keep that binding/commit protocol; prepare the connection before its first business SQL.
 * JDBC setters let the application's Hikari pool restore the state when Flex returns it.
 */
@Intercepts(@Signature(type=StatementHandler.class, method="prepare", args={Connection.class,Integer.class}))
public final class SpringTransactionIsolationInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Integer isolation=TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if(isolation!=null && TransactionSynchronizationManager.isActualTransactionActive()) {
            Connection connection=(Connection)invocation.getArgs()[0];
            // Subsequent statements (including joined REQUIRED calls) retain the same isolation.
            if(connection.getTransactionIsolation()!=isolation) connection.setTransactionIsolation(isolation);
        }
        return invocation.proceed();
    }
}
