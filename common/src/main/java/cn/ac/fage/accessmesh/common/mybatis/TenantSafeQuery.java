package cn.ac.fage.accessmesh.common.mybatis;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

/**
 * Safe query utility that always explicitly includes tenant_id and delete_flag
 * conditions for defense-in-depth multi-tenant isolation.
 *
 * <p>These methods supplement MyBatis-Flex's auto-tenant-filter by adding explicit
 * WHERE clauses, ensuring tenant isolation even when TenantContextHolder is empty
 * (e.g., in scheduled tasks or background threads).
 */
public final class TenantSafeQuery {

    private TenantSafeQuery() {
        // utility class
    }

    /**
     * Select one entity by ID with explicit tenant_id + delete_flag conditions.
     * Replaces unsafe {@code mapper.selectOneById(id)} calls on multi-tenant tables.
     *
     * @param mapper       the MyBatis-Flex mapper
     * @param idColumn     the table's ID column (e.g., SYS_CONFIG.ID)
     * @param tenantColumn the table's tenant_id column (e.g., SYS_CONFIG.TENANT_ID)
     * @param delColumn    the table's delete_flag column (e.g., SYS_CONFIG.DELETE_FLAG)
     * @param tenantId     the tenant ID
     * @param id           the entity ID
     * @param <T>          entity type
     * @return the entity, or null if not found
     */
    public static <T> T selectOneByIdSafe(
            BaseMapper<T> mapper,
            QueryColumn idColumn,
            QueryColumn tenantColumn,
            QueryColumn delColumn,
            Long tenantId,
            Long id) {
        if (tenantId == null || id == null) {
            return null;
        }
        return mapper.selectOneByQuery(
            QueryWrapper.create()
                .where(idColumn.eq(id))
                .and(tenantColumn.eq(tenantId))
                .and(delColumn.eq(0))
        );
    }

    /**
     * Select list by IDs with explicit tenant_id + delete_flag conditions.
     * Replaces unsafe batch queries on multi-tenant tables.
     *
     * @param mapper       the MyBatis-Flex mapper
     * @param idColumn     the table's ID column
     * @param tenantColumn the table's tenant_id column
     * @param delColumn    the table's delete_flag column
     * @param tenantId     the tenant ID
     * @param ids          the entity IDs
     * @param <T>          entity type
     * @return list of entities
     */
    public static <T> List<T> selectListByIdsSafe(
            BaseMapper<T> mapper,
            QueryColumn idColumn,
            QueryColumn tenantColumn,
            QueryColumn delColumn,
            Long tenantId,
            List<Long> ids) {
        if (tenantId == null || ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectListByQuery(
            QueryWrapper.create()
                .where(idColumn.in(ids))
                .and(tenantColumn.eq(tenantId))
                .and(delColumn.eq(0))
        );
    }

    /**
     * Ensure a QueryWrapper has explicit tenant_id and delete_flag conditions.
     * Use this to augment an existing QueryWrapper with tenant isolation conditions.
     *
     * @param qw           the existing QueryWrapper
     * @param tenantColumn the table's tenant_id column
     * @param delColumn    the table's delete_flag column
     * @param tenantId     the tenant ID
     * @return the augmented QueryWrapper (same instance, for chaining)
     */
    public static QueryWrapper ensureTenantFilter(
            QueryWrapper qw,
            QueryColumn tenantColumn,
            QueryColumn delColumn,
            Long tenantId) {
        if (tenantId != null) {
            qw.and(tenantColumn.eq(tenantId));
        }
        qw.and(delColumn.eq(0));
        return qw;
    }
}
